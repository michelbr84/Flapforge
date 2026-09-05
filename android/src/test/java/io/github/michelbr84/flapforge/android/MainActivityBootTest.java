package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.PixelFormat;
import android.os.Looper;
import android.view.SurfaceHolder;
import io.github.michelbr84.flapforge.app.GameApplication;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proof of the Android boot (M10, P2): the activity routes every save into its own
 * files directory before anything else, starts the real {@link GameApplication} on the boot
 * thread once the surface has a size, runs the loop against the surface (Robolectric's fake
 * holder has no canvas, so every frame is a counted skip), and shuts the game down on destroy
 * — the loop thread ends, and the one file the session wrote (the profile, saved by the
 * startup grant of what a fresh profile already owns) sits under the files dir.
 *
 * <p>This test is also the guard for the developer's real profile: the desktop's
 * {@code ~/.flapforge} is fingerprinted before the activity is created and again after the game
 * has shut down, and the two must be identical. The boot is the real one — content, settings,
 * profile, fonts, the audio warm-up over Robolectric's {@code ShadowAudioTrack}, the boot
 * screen — so a regression that let the Android path read or write the desktop directory
 * would show up here rather than on a device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityBootTest {

    private static final int WIDTH = 840;
    private static final int HEIGHT = 1280;
    private static final long BOOT_TIMEOUT_MS = 10_000L;
    private static final long LOOP_END_TIMEOUT_MS = 5_000L;

    /** The desktop's Linux profile directory, which this test must never touch. */
    private static final Path DESKTOP_PROFILE_DIR =
            Path.of(System.getProperty("user.home", ".")).resolve(SavePaths.DOT_DIR_NAME)
                    .toAbsolutePath().normalize();
    private static final List<String> GUARDED_FILES = List.of(SavePaths.SAVE_FILE,
            SavePaths.SAVE_BACKUP_FILE, SavePaths.SETTINGS_FILE);

    @Test
    public void bootsOnTheFirstSizedSurfaceAndShutsDownOnDestroy() throws Exception {
        Map<String, String> desktopBefore = fingerprintDesktopProfile();

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        GameApplication app;
        try {
            Path filesDir = activity.getFilesDir().toPath().toAbsolutePath().normalize();
            assertEquals("saves are routed before anything else", filesDir,
                    SavePaths.overrideDir());
            assertEquals(filesDir, SavePaths.profileDir());
            assertFalse("never the desktop profile", filesDir.startsWith(DESKTOP_PROFILE_DIR));
            assertNull("the game waits for a sized surface", activity.application());

            // Back before the game is up is ignored, never a finish().
            activity.onBackInvoked();
            assertFalse(activity.isFinishing());

            GameSurfaceView view = activity.surfaceView();
            SurfaceHolder holder = view.getHolder();
            view.surfaceCreated(holder);
            view.surfaceChanged(holder, PixelFormat.RGBA_8888, WIDTH, HEIGHT);

            app = awaitNonNull("the boot thread started the game", activity::application,
                    BOOT_TIMEOUT_MS);
            GameContext context = app.context();
            assertNotNull("windowed launch produced a context", context);
            Thread loop = app.loopThread();
            assertNotNull("windowed launch has a loop thread", loop);
            assertTrue(loop.isAlive());
            assertNotNull(context.window());
            assertEquals(WIDTH, context.window().canvasWidth());
            assertEquals(HEIGHT, context.window().canvasHeight());
            assertTrue(context.presenter() instanceof SurfacePresenter);
            assertNotNull(activity.host().inputBridge());
            assertTrue(activity.host().inputBridge().isAttached());

            // The loop presents every frame; the fake holder has no canvas, so each is a skip.
            SurfacePresenter presenter = (SurfacePresenter) context.presenter();
            await("the loop reached the presenter", () -> presenter.skippedCount() > 0,
                    LOOP_END_TIMEOUT_MS);

            // A second sized surface must not start a second game.
            view.surfaceChanged(holder, PixelFormat.RGBA_8888, WIDTH, HEIGHT);
            assertSame(app, activity.application());
        } finally {
            controller.pause().stop().destroy();
        }

        Thread loop = app.loopThread();
        loop.join(LOOP_END_TIMEOUT_MS);
        assertFalse("onDestroy queued CloseRequested and the loop ended", loop.isAlive());
        assertFalse(activity.host().inputBridge().isAttached());

        // The boot thread's finish() was posted to the main looper once the loop ended; run it
        // (the activity is already destroyed, so it is a no-op) rather than leave it queued.
        shadowOf(Looper.getMainLooper()).idle();

        // What the real path writes: the startup pass grants what a fresh profile already
        // satisfies (the base modifiers) and saves it once (GameApplication's
        // grantWhatIsAlreadyEarned, D13/D14); nothing touched the settings, so settings.json is
        // never written. Both would land under the files dir, never under ~/.flapforge.
        Path filesDir = activity.getFilesDir().toPath().toAbsolutePath().normalize();
        assertEquals(filesDir, SavePaths.profileDir());
        assertTrue("the startup grant wrote the profile into the files dir",
                Files.isRegularFile(filesDir.resolve(SavePaths.SAVE_FILE)));
        assertFalse(Files.exists(filesDir.resolve(SavePaths.SETTINGS_FILE)));

        assertEquals("the desktop profile is untouched", desktopBefore,
                fingerprintDesktopProfile());
    }

    /**
     * MD5 of each guarded file under {@code ~/.flapforge}, or {@code "absent"}; an empty map
     * when the directory does not exist (CI).
     */
    private static Map<String, String> fingerprintDesktopProfile() throws IOException {
        Map<String, String> fingerprint = new LinkedHashMap<>();
        if (!Files.isDirectory(DESKTOP_PROFILE_DIR)) {
            return fingerprint;
        }
        for (String name : GUARDED_FILES) {
            Path file = DESKTOP_PROFILE_DIR.resolve(name);
            fingerprint.put(name, Files.isRegularFile(file) ? md5(file) : "absent");
        }
        return fingerprint;
    }

    private static String md5(Path file) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T awaitNonNull(String what, Supplier<T> value, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            T v = value.get();
            if (v != null) {
                return v;
            }
            Thread.sleep(20L);
        }
        fail(what + " within " + timeoutMs + " ms");
        return null;
    }

    private static void await(String what, BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }
        fail(what + " within " + timeoutMs + " ms");
    }
}
