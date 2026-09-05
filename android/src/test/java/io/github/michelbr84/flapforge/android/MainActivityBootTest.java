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
import io.github.michelbr84.flapforge.persistence.SettingsStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import jssound.AudioSystem;
import org.junit.After;
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
 * — the loop thread ends, and the two files the session wrote (the first-run settings, seeded
 * by the boot thread, and the profile, saved by the startup grant of what a fresh profile
 * already owns) sit under the files dir.
 *
 * <p>The desktop's {@code ~/.flapforge} is fingerprinted around the whole boot
 * ({@link DesktopProfileGuard}).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityBootTest {

    private static final int WIDTH = 840;
    private static final int HEIGHT = 1280;
    private static final long BOOT_TIMEOUT_MS = 10_000L;
    private static final long LOOP_END_TIMEOUT_MS = 5_000L;

    /**
     * The activity's {@code onPause} suspends the shim's audio output, a static flag the
     * sandbox class loader shares with every other test class of this configuration.
     */
    @After
    public void resumeAudioOutput() {
        AudioSystem.resumeOutput();
    }

    @Test
    public void bootsOnTheFirstSizedSurfaceAndShutsDownOnDestroy() throws Exception {
        Map<String, String> desktopBefore = DesktopProfileGuard.fingerprint();

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        GameApplication app;
        try {
            Path filesDir = activity.getFilesDir().toPath().toAbsolutePath().normalize();
            assertEquals("saves are routed before anything else", filesDir,
                    SavePaths.overrideDir());
            assertEquals(filesDir, SavePaths.profileDir());
            assertFalse("never the desktop profile",
                    filesDir.startsWith(DesktopProfileGuard.DESKTOP_PROFILE_DIR));
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

        // What the real path writes: the boot thread seeds settings.json with the Android
        // first-run default (hold-to-flap on) before the game reads it, and the startup pass
        // grants what a fresh profile already satisfies (the base modifiers) and saves it once
        // (GameApplication's grantWhatIsAlreadyEarned, D13/D14). Both land under the files dir,
        // never under ~/.flapforge.
        Path filesDir = activity.getFilesDir().toPath().toAbsolutePath().normalize();
        assertEquals(filesDir, SavePaths.profileDir());
        assertTrue("the startup grant wrote the profile into the files dir",
                Files.isRegularFile(filesDir.resolve(SavePaths.SAVE_FILE)));
        Path settings = filesDir.resolve(SavePaths.SETTINGS_FILE);
        assertTrue("the first run seeded the settings into the files dir",
                Files.isRegularFile(settings));
        assertTrue("Android first-run default: hold-to-flap on",
                new SettingsStore(Runnable::run, settings).load().settings().holdToFlap);

        assertEquals("the desktop profile is untouched", desktopBefore,
                DesktopProfileGuard.fingerprint());
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
