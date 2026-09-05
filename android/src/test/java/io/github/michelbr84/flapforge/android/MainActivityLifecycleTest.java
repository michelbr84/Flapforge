package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.PixelFormat;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import io.github.michelbr84.flapforge.app.GameApplication;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.persistence.Settings;
import io.github.michelbr84.flapforge.persistence.SettingsStore;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import java.lang.reflect.Method;
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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proofs of the Android lifecycle (M10, P3), against the real game: the boot seeds
 * the first-run settings (hold-to-flap on) and never rewrites an existing file; a tap on the
 * menu's Play button through the real touch path starts a run and switches the ability badge
 * zone on; {@code onPause} pauses the live run (its {@code FocusLost}) and suspends the audio
 * output, {@code onResume} resumes the output; {@code onStop} / {@code onStart} reach the
 * screen manager as {@code Iconified}; the back gesture is the {@code ESCAPE} that quits the
 * pause overlay to the menu. The run itself is real — the bird would crash within seconds if
 * left flying — so the activity is paused the moment the run is live and the run is never
 * resumed.
 *
 * <p>The desktop's {@code ~/.flapforge} is fingerprinted around every boot
 * ({@link DesktopProfileGuard}). Loop-owned state (the screen stack, the run phase, the
 * iconified flag) is polled from the test thread the way the boot test polls the presenter:
 * without a lock, until it shows the expected value or the timeout says it never will.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityLifecycleTest {

    private static final int WIDTH = 840;
    private static final int HEIGHT = 1280;
    private static final long BOOT_TIMEOUT_MS = 10_000L;
    /** The boot screen runs the real boot sequence (content, fonts, the mixer line) first. */
    private static final long MENU_TIMEOUT_MS = 30_000L;
    private static final long STEP_TIMEOUT_MS = 5_000L;
    private static final long LOOP_END_TIMEOUT_MS = 5_000L;
    private static final long POLL_MS = 20L;

    /**
     * The activity's {@code onPause} suspends the shim's audio output, a static flag the
     * sandbox class loader shares with every other test class of this configuration.
     */
    @After
    public void resumeAudioOutput() {
        AudioSystem.resumeOutput();
    }

    @Test
    public void pauseStopAndBackReachTheRunAndTheAudioGate() throws Exception {
        Map<String, String> desktopBefore = DesktopProfileGuard.fingerprint();
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        GameApplication app;
        try {
            app = boot(activity);
            GameContext context = app.context();
            ScreenManager screens = context.screens();
            AndroidInputBridge bridge = activity.host().inputBridge();
            assertNotNull(bridge);
            assertTrue("Android first run: hold-to-flap on", context.settings().holdToFlap);
            assertFalse(outputSuspended());

            // Boot screen -> main menu; Play through the real touch path, at the button's
            // centre mapped through the game's own viewport.
            MainMenuScreen menu = awaitScreen(screens, MainMenuScreen.class, MENU_TIMEOUT_MS);
            assertFalse("no run on top: the badge zone is off", bridge.isRunActive());
            Button play = menu.playButton();
            tap(activity.surfaceView(), context.viewport().toWindow(play.centerX(),
                    play.centerY()));
            GameScreen game = awaitScreen(screens, GameScreen.class, STEP_TIMEOUT_MS);
            assertTrue("the run took the seeded default", game.isHoldToFlap());
            await("the host sampled the run on top", bridge::isRunActive, STEP_TIMEOUT_MS);

            // A flap takes the run from READY to FLYING. The tick after a screen transition
            // strips key edges, so the tap is repeated until the run is flying.
            awaitWithRetry("the flap started the run", () -> bridge.key(Keys.SPACE),
                    () -> game.run().phase() == RunPhase.FLYING, STEP_TIMEOUT_MS);

            // onPause: FocusLost pauses the live run; the audio output is suspended.
            controller.pause();
            awaitScreen(screens, PauseOverlay.class, STEP_TIMEOUT_MS);
            assertTrue("onPause suspended the audio output", outputSuspended());
            await("the overlay on top switches the badge zone off", () -> !bridge.isRunActive(),
                    STEP_TIMEOUT_MS);

            // onResume: the output plays again; the game stays paused until the player resumes.
            controller.resume();
            assertFalse("onResume resumed the audio output", outputSuspended());
            assertTrue(topOf(screens) instanceof PauseOverlay);

            // onStop / onStart: Iconified(true) / Iconified(false), as the screen manager sees it.
            controller.pause().stop();
            await("onStop iconified the game", screens::isIconified, STEP_TIMEOUT_MS);
            assertTrue(outputSuspended());
            controller.restart().resume();
            await("onStart restored the game", () -> !screens.isIconified(), STEP_TIMEOUT_MS);
            assertFalse(outputSuspended());
            assertTrue(topOf(screens) instanceof PauseOverlay);

            // Back is ESCAPE: on the pause overlay that is "quit to menu", never a finish().
            activity.onBackInvoked();
            awaitScreen(screens, MainMenuScreen.class, STEP_TIMEOUT_MS);
            assertFalse(activity.isFinishing());
        } finally {
            controller.pause().stop().destroy();
        }
        settle(app);

        Path settings = activity.getFilesDir().toPath().resolve(SavePaths.SETTINGS_FILE);
        assertTrue("the first boot wrote settings.json", Files.isRegularFile(settings));
        assertTrue(new SettingsStore(Runnable::run, settings).load().settings().holdToFlap);
        assertEquals("the desktop profile is untouched", desktopBefore,
                DesktopProfileGuard.fingerprint());
    }

    @Test
    public void anExistingSettingsFileIsNeverRewrittenByTheBoot() throws Exception {
        Map<String, String> desktopBefore = DesktopProfileGuard.fingerprint();
        Path filesDir = RuntimeEnvironment.getApplication().getFilesDir().toPath()
                .toAbsolutePath().normalize();
        Path file = filesDir.resolve(SavePaths.SETTINGS_FILE);
        Settings chosen = Settings.defaults();
        chosen.holdToFlap = false;
        SettingsStore store = new SettingsStore(Runnable::run, file);
        store.save(chosen);
        assertTrue(store.lastWrite().ok());
        byte[] before = Files.readAllBytes(file);

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        GameApplication app;
        try {
            app = boot(activity);
            assertEquals(filesDir, SavePaths.profileDir());
            assertFalse("the player's choice stands", app.context().settings().holdToFlap);
        } finally {
            controller.pause().stop().destroy();
        }
        settle(app);

        assertArrayEquals("settings.json was left byte for byte", before,
                Files.readAllBytes(file));
        assertEquals("the desktop profile is untouched", desktopBefore,
                DesktopProfileGuard.fingerprint());
    }

    @Test
    public void lifecycleBeforeTheBootIsHarmlessAndLeavesTheOutputPlaying() throws Exception {
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        try {
            assertNull(activity.application());
            controller.pause();
            assertTrue("a pause before the boot still suspends", outputSuspended());
            controller.resume();
            assertFalse("and the matching resume lifts it, game or no game", outputSuspended());
            controller.pause().stop();
            controller.restart().resume();
            controller.windowFocusChanged(true);
            controller.windowFocusChanged(false);
            assertFalse(outputSuspended());
            assertFalse(activity.isFinishing());
            assertNull("nothing here starts the game", activity.application());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    /** Sizes the surface, which starts the boot thread, and waits for the started game. */
    private static GameApplication boot(MainActivity activity) throws InterruptedException {
        GameSurfaceView view = activity.surfaceView();
        SurfaceHolder holder = view.getHolder();
        view.surfaceCreated(holder);
        view.surfaceChanged(holder, PixelFormat.RGBA_8888, WIDTH, HEIGHT);
        GameApplication app = awaitNonNull("the boot thread started the game",
                activity::application, BOOT_TIMEOUT_MS);
        assertNotNull(app.loopThread());
        return app;
    }

    /** Waits for the loop thread the destroy asked to stop, then runs the posted finish(). */
    private static void settle(GameApplication app) throws InterruptedException {
        Thread loop = app.loopThread();
        loop.join(LOOP_END_TIMEOUT_MS);
        assertFalse("onDestroy queued CloseRequested and the loop ended", loop.isAlive());
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** A press and a release at a surface point, as the platform delivers a tap. */
    private static void tap(GameSurfaceView view, Vec2 at) {
        touch(view, MotionEvent.ACTION_DOWN, at);
        touch(view, MotionEvent.ACTION_UP, at);
    }

    private static void touch(GameSurfaceView view, int action, Vec2 at) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, (float) at.x(), (float) at.y(),
                0);
        try {
            assertTrue("the bridge consumed the touch", view.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    /**
     * {@link AudioSystem#suspendOutput()} in effect, read through the shim's own test accessor
     * ({@code isOutputSuspended} is package-private infrastructure of {@code jssound}).
     */
    private static boolean outputSuspended() throws ReflectiveOperationException {
        Method probe = AudioSystem.class.getDeclaredMethod("isOutputSuspended");
        probe.setAccessible(true);
        return (Boolean) probe.invoke(null);
    }

    /**
     * The top of the loop-owned stack, or {@code null} when a poll lands in the middle of a
     * pop; the caller polls again.
     */
    private static Screen topOf(ScreenManager screens) {
        try {
            return screens.top();
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static <T extends Screen> T awaitScreen(ScreenManager screens, Class<T> type,
            long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            Screen top = topOf(screens);
            if (type.isInstance(top)) {
                return type.cast(top);
            }
            Thread.sleep(POLL_MS);
        }
        fail(type.getSimpleName() + " on top within " + timeoutMs + " ms (top: "
                + topOf(screens) + ")");
        return null;
    }

    private static <T> T awaitNonNull(String what, Supplier<T> value, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            T v = value.get();
            if (v != null) {
                return v;
            }
            Thread.sleep(POLL_MS);
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
            Thread.sleep(POLL_MS);
        }
        fail(what + " within " + timeoutMs + " ms");
    }

    /** Runs the action, then polls; the action is repeated every few polls until it took. */
    private static void awaitWithRetry(String what, Runnable action, BooleanSupplier condition,
            long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            action.run();
            for (int poll = 0; poll < 5; poll++) {
                Thread.sleep(POLL_MS);
                if (condition.getAsBoolean()) {
                    return;
                }
            }
        }
        fail(what + " within " + timeoutMs + " ms");
    }
}
