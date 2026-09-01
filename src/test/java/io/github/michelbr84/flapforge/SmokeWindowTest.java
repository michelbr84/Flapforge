package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

import io.github.michelbr84.flapforge.app.AwtInputBridge;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.audio.NullAudio;
import io.github.michelbr84.flapforge.app.BufferStrategyPresenter;
import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameApplication;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.GameWindow;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import io.github.michelbr84.flapforge.app.SystemClock;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.app.Threads;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.persistence.SaveManager;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.persistence.Settings;
import io.github.michelbr84.flapforge.persistence.SettingsStore;
import io.github.michelbr84.flapforge.render.DebugOverlay;
import io.github.michelbr84.flapforge.render.HudRenderer;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Toggle;
import io.github.michelbr84.flapforge.ui.screens.BirdSelectionScreen;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import io.github.michelbr84.flapforge.ui.screens.RunSummaryScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
import io.github.michelbr84.flapforge.ui.screens.ShopScreen;
import io.github.michelbr84.flapforge.ui.screens.UpgradeTreeScreen;
import java.awt.AWTError;
import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Real-window smoke tests (M0; E30.b), run only through {@code ./gradlew smokeTest} with a
 * display and skipped (not failed) without one:
 * <ul>
 *   <li>window, loop, fullscreen toggled twice, backbuffer capture, clean close;</li>
 *   <li>a window started fullscreen leaves it on the first toggle;</li>
 *   <li>menu navigation with real {@link Robot} keys and clicks through the toolkit, the input
 *       bridge and the viewport transform, including a held {@code F11} that must toggle
 *       fullscreen exactly once (the handshake disposes the window, so the key's auto-repeat
 *       resumes after a focus loss). Every Robot event must take effect; when the canvas cannot
 *       obtain keyboard focus at all (another window steals it) the test is aborted, never
 *       passed. The same navigation through the queue alone is {@code MenuNavigationTest};</li>
 *   <li>the M4 meta screens: Birds, Upgrades and Shop opened from the menu with real clicks, the
 *       cheapest bird and the first upgrade node bought through the toolkit, and the wallet
 *       dropping by what they cost;</li>
 *   <li>the real quit path of {@link GameApplication}: {@code CloseRequested} ends the loop
 *       thread, disposes the frame and finishes long before the exit watchdog.</li>
 * </ul>
 * Screenshots go to {@code build/smoke/<name>-capture.png} (Robot capture) and
 * {@code <name>-render.png} (the same frame through the presenter); the assertion uses the
 * capture when it is not uniform (Wayland can return black) and the render otherwise.
 */
@Tag("gui")
class SmokeWindowTest {

    private static final Path OUT_DIR = Path.of("build", "smoke");
    /** Height the smoke flight keeps the bird around: clear of the ceiling and of the ground. */
    private static final int HOLD_Y = 300;
    /**
     * How often a Robot <em>click</em> is repeated when the desktop swallowed it (a window sitting
     * over the point receives the press instead), and how often a key tap is re-sent for the same
     * reason. A retry only happens when the expectation is still unmet after
     * {@link #DELIVERY_TIMEOUT_MS}, i.e. the event had no effect at all, so it cannot activate a
     * button or toggle a switch twice.
     */
    private static final int DELIVERY_ATTEMPTS = 3;

    /**
     * How long a Robot event may take to come back through the toolkit. The rig drives the loop
     * as fast as it can, so a frame budget is not a time budget: 90 uncapped frames can elapse in
     * well under a millisecond while X still has the event in flight on a loaded machine.
     */
    private static final long DELIVERY_TIMEOUT_MS = 2_000L;
    /** How long the window manager may take to grant the canvas keyboard focus. */
    private static final long FOCUS_TIMEOUT_MS = 1_000L;
    /**
     * Flaps the scripted flight performs before the frame is captured. One flap per ~27 ticks
     * holds the bird around {@link #HOLD_Y}, so five of them are ~110 ticks — comfortably short of
     * the ~146 ticks the first gate needs to reach the bird.
     */
    private static final int FLIGHT_FLAPS = 5;
    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 5;

    /**
     * Overlay recording the frames it sees, pushed on top of the menu for input checks.
     *
     * <p>It also records <em>what</em> produced each flap edge. FLAP is bound to Space, Up and
     * (hard-mapped) the left mouse button, so a spurious edge around the fullscreen handshake
     * could come from any of the three; {@link #edges()} names the source in the failure message
     * instead of leaving a bare count.
     */
    static final class SpyOverlay implements Screen {
        int flapPresses;
        private final List<String> edges = new ArrayList<>();

        @Override
        public void tick(InputFrame input) {
            if (input.isJustPressed(InputAction.FLAP)) {
                flapPresses++;
                edges.add("flap#" + flapPresses + " rawKeyDowns=" + input.rawKeyDowns()
                        + " mouseLeft=" + input.isMouseJustPressed(Keys.BUTTON_LEFT)
                        + " systemEvents=" + input.systemEvents());
            }
        }

        /**
         * A description of every flap edge seen, for a failure message.
         *
         * @return the descriptions in order
         */
        String edges() {
            return String.join("; ", edges);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public boolean isOverlay() {
            return true;
        }
    }

    /**
     * Overlay recording the raw key codes it sees, used by the input-delivery canary. It reads
     * {@link InputFrame#rawKeyDowns()} rather than an action, so the probe key needs no binding.
     */
    static final class KeyCanary implements Screen {
        private final Set<Integer> seen = new HashSet<>();

        @Override
        public void tick(InputFrame input) {
            seen.addAll(input.rawKeyDowns());
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public boolean isOverlay() {
            return true;
        }

        boolean saw(int keyCode) {
            return seen.contains(keyCode);
        }
    }

    /** Window + loop wiring shared by the tests (the same objects {@code GameApplication} wires). */
    static final class Rig implements AutoCloseable {
        final InputQueue input = new InputQueue(KeyBindings.defaults());
        final GameWindow window;
        final Viewport viewport;
        final ScreenManager screens;
        final DebugOverlay overlay;
        final BufferStrategyPresenter presenter;
        final AwtInputBridge bridge;
        final GameLoop loop;
        final FrameLimiter limiter;
        final SettingsStore store;
        final EventBus events = new EventBus();
        final ToastLayer toasts = new ToastLayer();
        final AudioManager audio;
        final SaveManager save;
        final GameContext context;
        boolean closed;

        Rig(String title, boolean startFullscreen) {
            this(title, startFullscreen, false);
        }

        /**
         * @param withProfile wires the save layer and the progression pipeline, so a finished run
         *     is written into a profile and the game-over strip carries what it paid (D14, D29).
         *     The profile goes to {@code build/smoke/home}, never to the player's own
         *     {@code ~/.flapforge}.
         */
        Rig(String title, boolean startFullscreen, boolean withProfile) {
            window = GameWindow.create(title, 1, startFullscreen);
            window.setIcons(ProceduralArt.icons());
            viewport = new Viewport(window.canvasWidth(), window.canvasHeight(), false);
            screens = new ScreenManager(viewport);
            SystemClock clock = new SystemClock();
            overlay = new DebugOverlay(screens, clock::nanos);
            presenter = new BufferStrategyPresenter(window, viewport, overlay);
            screens.setPresenter(presenter);
            bridge = new AwtInputBridge(input);
            limiter = new FrameLimiter(clock, FrameLimiter.DEFAULT_FPS);
            loop = new GameLoop(clock, input, screens, presenter, limiter);
            // The settings the smoke run writes go to build/smoke/home, never to the player's
            // real ~/.flapforge. The file is cleared first: a previous test in this class leaves
            // its own language and fullscreen state behind, and a rig that adopted them would
            // depend on the order the tests ran in.
            Path settingsFile = OUT_DIR.resolve("home").resolve("settings.json");
            try {
                Files.createDirectories(settingsFile.getParent());
                Files.deleteIfExists(settingsFile);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("cannot reset " + settingsFile, e);
            }
            store = new SettingsStore(Runnable::run, settingsFile);
            store.load();
            Strings strings = Strings.load("en");
            Strings.use(strings);
            // E30.j: the smoke rig never opens a sound device.
            audio = new AudioManager(new NullAudio());
            GameContent content = null;
            ProgressionManager progression = null;
            ProgressionRules progressionRules = null;
            if (withProfile) {
                Path saveFile = OUT_DIR.resolve("home").resolve("save.json");
                try {
                    Files.deleteIfExists(saveFile);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("cannot reset " + saveFile, e);
                }
                content = GameContent.load();
                // Runnable::run: the write finishes before save() returns, so the assertions can
                // read the file without waiting for a background thread.
                save = new SaveManager(Runnable::run, () -> 0L, saveFile);
                save.load();
                // The wiring GameApplication uses (D14): the unlock evaluator is the pipeline's
                // unlock step, so a purchase grants what it implies.
                progression = new ProgressionManager(() -> 0L,
                        ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
                progressionRules = ProgressionRules.fromEconomy(content.economy());
            } else {
                save = null;
            }
            context = new GameContext(LaunchOptions.DEFAULTS, clock, () -> 0L, new Threads(),
                    input, viewport, screens, presenter, window, loop, limiter, store, events,
                    audio, strings, toasts, content, save, progression, progressionRules);
            audio.attach(events);
            screens.setEvents(events);
            // The same handlers GameApplication installs, so F11, F3 and M take the real path:
            // the setting changes, and applying the settings pushes it into the engine.
            screens.setMuteHandler(context::toggleMute);
            screens.setFullscreenHandler(context::toggleFullscreen);
            screens.setDebugOverlayHandler(context::toggleDebugOverlay);
            screens.setTickTask(context::drainSaveResults);
            overlay.setSource(new GameApplication.DebugSource(screens, loop));
            screens.setCloseHandler(() -> {
                closed = true;
                loop.stop();
            });
            bridge.attach(window);
            assertTrue(bridge.isAttached());
            // This runs on a live desktop: without always-on-top another window can cover the
            // point a Robot click targets, and X delivers the press to whatever is topmost there.
            GameWindow.onEdt(() -> window.frame().setAlwaysOnTop(true));
        }

        void start(Screen root) {
            screens.push(root);
            screens.applyPending();
            loop.start();
        }

        void frames(int n) {
            for (int i = 0; i < n; i++) {
                loop.frame();
            }
        }

        boolean until(BooleanSupplier done, int maxFrames) {
            for (int i = 0; i < maxFrames && !done.getAsBoolean(); i++) {
                loop.frame();
            }
            return done.getAsBoolean();
        }

        /**
         * Drives frames until {@code done} holds or the deadline passes, yielding between frames
         * so the toolkit thread can actually deliver what the {@link Robot} posted. Used wherever
         * the test waits for something that has to travel through X.
         */
        boolean untilDeadline(BooleanSupplier done, long timeoutMs) {
            long end = System.nanoTime() + timeoutMs * 1_000_000L;
            while (!done.getAsBoolean() && System.nanoTime() < end) {
                loop.frame();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return done.getAsBoolean();
        }

        void assertViewportMatchesCanvas(String when) {
            assertEquals(window.canvasWidth(), viewport.windowWidth(), "viewport width " + when);
            assertEquals(window.canvasHeight(), viewport.windowHeight(), "viewport height " + when);
        }

        void requestCloseAndVerify() {
            input.offer(new RawInput.CloseRequested());
            until(() -> closed, 30);
            assertTrue(closed, "close request reached the loop");
            assertTrue(screens.isCloseRequested());
        }

        @Override
        public void close() {
            presenter.dispose();
            bridge.detach();
            window.disposeAndWait();
            assertFalse(window.frame().isDisplayable(), "frame disposed");
            assertFalse(bridge.isAttached());
        }
    }

    /** Drives the window with a {@link Robot}; every event must take effect through the toolkit. */
    static final class Driver {
        private final Rig rig;
        private final Robot robot;

        Driver(Rig rig) throws Exception {
            this.rig = rig;
            this.robot = new Robot();
            robot.setAutoDelay(20);
            assumeTheSessionDeliversInput();
        }

        /**
         * Skips the test when the desktop cannot deliver synthetic input at all — a locked screen,
         * a screensaver or any other client holding a keyboard grab swallows every XTEST event
         * while the canvas still reports focus, so each later assertion would report a broken game
         * instead of an unusable session. {@code VK_CONTROL} is mapped on every keymap and bound
         * to nothing, so the probe changes no state. A session that delivers the canary and then
         * drops an event is a real failure and still fails.
         */
        private void assumeTheSessionDeliversInput() {
            KeyCanary canary = new KeyCanary();
            rig.screens.push(canary);
            try {
                focusCanvasOrAbort(rig);
                robot.waitForIdle();
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyRelease(KeyEvent.VK_CONTROL);
                if (!rig.untilDeadline(() -> canary.saw(KeyEvent.VK_CONTROL), DELIVERY_TIMEOUT_MS)) {
                    abort("the desktop session does not deliver synthetic input (a locked screen, "
                            + "a screensaver or a keyboard grab): run the gui suite on an unlocked "
                            + "session or a nested X server such as Xephyr");
                }
            } finally {
                rig.screens.pop();
                rig.frames(2);
            }
        }

        void tap(int keyCode, BooleanSupplier expected) {
            String text = KeyEvent.getKeyText(keyCode);
            // Reclaim focus first: on a live desktop another window can take it between two steps,
            // and the key press then goes there instead. A no-op while we still own it; when focus
            // cannot be reclaimed at all the run is aborted, never passed. waitForIdle() lets a
            // just-granted focus change settle before the press.
            //
            // A key is re-sent only while `expected` is still false, i.e. the press had no effect
            // whatsoever after a full DELIVERY_TIMEOUT_MS of real time -- the desktop swallowed it,
            // which is what a window manager re-mapping a window after a fullscreen handshake does.
            // The "exactly one edge" assertions live in the tests, after the loop, so a key that
            // was delivered twice is still caught.
            for (int attempt = 0; attempt < DELIVERY_ATTEMPTS; attempt++) {
                focusCanvasOrAbort(rig);
                robot.waitForIdle();
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
                rig.untilDeadline(expected, DELIVERY_TIMEOUT_MS);
                if (expected.getAsBoolean()) {
                    return;
                }
                System.out.println("[smoke] key " + text + " was not delivered, retrying");
            }
            assertTrue(expected.getAsBoolean(),
                    "Robot key " + text + " was not delivered through the toolkit");
        }

        void click(UiNode node, BooleanSupplier expected) {
            int wx = 0;
            int wy = 0;
            for (int attempt = 0; attempt < DELIVERY_ATTEMPTS; attempt++) {
                // Raise the window first: on a busy desktop another window can sit over the point
                // we are about to click, and X delivers the press to whatever is topmost there.
                focusCanvasOrAbort(rig);
                Vec2 w = rig.viewport.toWindow(node.centerX(), node.centerY());
                wx = (int) Math.round(w.x());
                wy = (int) Math.round(w.y());
                Canvas canvas = rig.window.canvas();
                assertTrue(canvas.isShowing(), "canvas showing");
                Point origin = canvas.getLocationOnScreen();
                robot.mouseMove(origin.x + wx, origin.y + wy);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                rig.untilDeadline(expected, DELIVERY_TIMEOUT_MS);
                if (expected.getAsBoolean()) {
                    return;
                }
                System.out.println("[smoke] click at " + wx + "," + wy
                        + " was not delivered, retrying");
            }
            assertTrue(expected.getAsBoolean(), "Robot click at " + wx + "," + wy
                    + " was not delivered through the toolkit");
        }

        /**
         * Parks the pointer on a point in logical playfield coordinates, so that a following
         * keyboard step cannot have its focus stolen by whatever the pointer was resting on.
         */
        void parkPointerAt(double logicalX, double logicalY) {
            Vec2 w = rig.viewport.toWindow(logicalX, logicalY);
            Point origin = rig.window.canvas().getLocationOnScreen();
            robot.mouseMove(origin.x + (int) Math.round(w.x()),
                    origin.y + (int) Math.round(w.y()));
            rig.frames(4);
        }

        /**
         * Clicks a point in logical playfield coordinates. Used for the settings rows, whose
         * own {@code y} is a position in the scrolled content rather than on the screen.
         */
        void clickAt(double logicalX, double logicalY, BooleanSupplier expected) {
            int wx = 0;
            int wy = 0;
            for (int attempt = 0; attempt < DELIVERY_ATTEMPTS; attempt++) {
                focusCanvasOrAbort(rig);
                Vec2 w = rig.viewport.toWindow(logicalX, logicalY);
                wx = (int) Math.round(w.x());
                wy = (int) Math.round(w.y());
                Canvas canvas = rig.window.canvas();
                assertTrue(canvas.isShowing(), "canvas showing");
                Point origin = canvas.getLocationOnScreen();
                robot.mouseMove(origin.x + wx, origin.y + wy);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                rig.untilDeadline(expected, DELIVERY_TIMEOUT_MS);
                if (expected.getAsBoolean()) {
                    return;
                }
                System.out.println("[smoke] click at " + wx + "," + wy
                        + " was not delivered, retrying");
            }
            assertTrue(expected.getAsBoolean(), "Robot click at " + wx + "," + wy
                    + " was not delivered through the toolkit");
        }

        /**
         * Holds {@code F11} for the given time while running frames, releases it, runs a few
         * more frames and returns how many times the fullscreen state changed.
         */
        int holdFullscreenKey(long holdMs) {
            focusCanvasOrAbort(rig);
            int transitions = 0;
            boolean last = rig.window.isFullscreen();
            robot.keyPress(KeyEvent.VK_F11);
            try {
                long end = System.nanoTime() + holdMs * 1_000_000L;
                while (System.nanoTime() < end) {
                    rig.loop.frame();
                    if (rig.window.isFullscreen() != last) {
                        last = !last;
                        transitions++;
                    }
                }
            } finally {
                robot.keyRelease(KeyEvent.VK_F11);
            }
            for (int i = 0; i < 40; i++) {
                rig.loop.frame();
                if (rig.window.isFullscreen() != last) {
                    last = !last;
                    transitions++;
                }
            }
            return transitions;
        }
    }

    private static void requireDisplay() throws Exception {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                abort("needs a display (headless environment)");
            }
            GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        } catch (HeadlessException | AWTError | LinkageError e) {
            abort("needs a display: " + e);
        }
        Files.createDirectories(OUT_DIR);
    }

    @Test
    void windowLoopFullscreenCaptureAndCleanClose() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test", false)) {
            rig.start(new MainMenuScreen(rig.screens));
            logSizes("after create", rig.window);
            rig.until(() -> rig.loop.tickCount() >= 30 && rig.presenter.presentCount() >= 30, 300);
            assertTrue(rig.loop.tickCount() >= 30, "ticks over paced frames: " + rig.loop.tickCount());
            assertTrue(rig.presenter.presentCount() >= 30, "frames presented: "
                    + rig.presenter.presentCount());
            rig.assertViewportMatchesCanvas("after the synthetic Resized of attach");
            logSizes("after warm-up", rig.window);

            int windowedWidth = rig.window.canvasWidth();
            int windowedHeight = rig.window.canvasHeight();
            rig.input.offer(new RawInput.FullscreenToggled());
            rig.until(rig.window::isFullscreen, 90);
            assertTrue(rig.window.isFullscreen(), "entered fullscreen");
            assertTrue(rig.screens.isFullscreen(), "the manager reads the presenter state");
            assertTrue(rig.window.frame().isUndecorated());
            rig.frames(15);
            rig.assertViewportMatchesCanvas("in fullscreen");
            assertTrue(rig.window.canvasWidth() > windowedWidth, "fullscreen canvas is larger");

            rig.input.offer(new RawInput.FullscreenToggled());
            rig.until(() -> !rig.window.isFullscreen(), 90);
            assertFalse(rig.window.isFullscreen(), "left fullscreen");
            assertFalse(rig.screens.isFullscreen());
            assertFalse(rig.window.frame().isUndecorated());
            rig.frames(15);
            rig.assertViewportMatchesCanvas("after restore");
            assertEquals(windowedWidth, rig.window.canvasWidth(), "windowed width restored");
            assertEquals(windowedHeight, rig.window.canvasHeight(), "windowed height restored");
            logSizes("after restore", rig.window);

            rig.frames(45); // give the compositor time to show the recreated window
            int distinct = saveShot("window", rig);
            assertTrue(distinct >= 2, "rendered frame is uniform (" + distinct + " colour)");

            rig.requestCloseAndVerify();
        }
    }

    @Test
    void windowStartedFullscreenLeavesItOnTheFirstToggle() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test (fullscreen start)", true)) {
            rig.start(new MainMenuScreen(rig.screens));
            rig.frames(10);
            assertTrue(rig.window.isFullscreen(), "created fullscreen");
            assertTrue(rig.screens.isFullscreen(), "the manager sees the initial fullscreen state");
            rig.assertViewportMatchesCanvas("fullscreen start");
            assertTrue(saveShot("fullscreen", rig) >= 2, "fullscreen frame is uniform");

            rig.input.offer(new RawInput.FullscreenToggled());
            rig.until(() -> !rig.window.isFullscreen(), 90);
            assertFalse(rig.window.isFullscreen(), "one toggle leaves fullscreen");
            assertFalse(rig.window.frame().isUndecorated());
            rig.frames(15);
            rig.assertViewportMatchesCanvas("after leaving fullscreen");
            assertEquals(Playfield.WIDTH, rig.window.canvasWidth(), "windowed width at scale 1");
            assertEquals(Playfield.HEIGHT, rig.window.canvasHeight(), "windowed height at scale 1");
            logSizes("after leaving fullscreen", rig.window);

            rig.requestCloseAndVerify();
        }
    }

    @Test
    void menuNavigationByKeyboardAndMouseThroughTheToolkit() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test (menu)", false)) {
            MainMenuScreen menu = new MainMenuScreen(rig.screens);
            rig.start(menu);
            rig.frames(30);
            focusCanvasOrAbort(rig);
            assertSame(menu, rig.screens.top());
            assertSame(menu.playButton(), menu.focusRing().focused(), "Play focused on entry");
            assertTrue(saveShot("menu", rig) >= 2, "menu is uniform");
            Driver driver = new Driver(rig);

            // One Space tap through the key dispatcher is exactly one FLAP edge.
            SpyOverlay spy = new SpyOverlay();
            rig.screens.push(spy);
            rig.frames(GRACE);
            driver.tap(KeyEvent.VK_SPACE, () -> spy.flapPresses > 0);
            rig.frames(10);
            assertEquals(1, spy.flapPresses, "one Space tap = one flap edge");

            // A held F11 toggles exactly once although the handshake loses and regains focus.
            int transitions = driver.holdFullscreenKey(400);
            assertEquals(1, transitions, "a held F11 toggles fullscreen exactly once");
            assertTrue(rig.window.isFullscreen());
            assertTrue(rig.store.settings().fullscreen, "F11 wrote the setting it changed");
            // Waited for in wall-clock time, not in paced frames: 60 frames can elapse in under a
            // millisecond while the window manager is still re-mapping the recreated window.
            assertTrue(rig.untilDeadline(() -> rig.window.canvas().isFocusOwner()
                    && !rig.screens.isFullscreenHandshake(), DELIVERY_TIMEOUT_MS),
                    "focus returned to the recreated fullscreen window");
            assertFalse(rig.input.heldCodes().contains(Keys.F11),
                    "the F11 release was delivered after the handshake");
            driver.tap(KeyEvent.VK_F11, () -> !rig.window.isFullscreen());
            rig.frames(15);
            assertFalse(rig.window.isFullscreen(), "a tap leaves fullscreen");
            assertFalse(rig.store.settings().fullscreen, "and the setting followed it back");
            assertEquals(1, spy.flapPresses,
                    () -> "no flap edge from the fullscreen handshake: " + spy.edges());
            rig.screens.pop();
            rig.frames(GRACE);
            logSizes("after F11 twice", rig.window);
            focusCanvasOrAbort(rig);

            driver.tap(KeyEvent.VK_DOWN, () -> menu.focusRing().focused() == menu.settingsButton());
            assertSame(menu.settingsButton(), menu.focusRing().focused(), "Down moves to Settings");
            driver.tap(KeyEvent.VK_UP, () -> menu.focusRing().focused() == menu.playButton());
            assertSame(menu.playButton(), menu.focusRing().focused(), "Up moves focus back");
            driver.tap(KeyEvent.VK_ENTER, () -> rig.screens.top() instanceof GameScreen);
            rig.frames(20);
            assertTrue(saveShot("game", rig) >= 2, "game screen is uniform");
            driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);
            rig.frames(GRACE);

            driver.click(menu.settingsButton(), () -> rig.screens.top() instanceof SettingsScreen);
            SettingsScreen settings = (SettingsScreen) rig.screens.top();
            rig.frames(20);
            assertTrue(saveShot("settings", rig) >= 2, "settings stub is uniform");
            driver.click(settings.backButton(), () -> rig.screens.top() == menu);

            rig.input.offer(new RawInput.KeyDown(Keys.F3, 1));
            rig.input.offer(new RawInput.KeyUp(Keys.F3, 2));
            rig.until(rig.screens::isDebugOverlayVisible, 30);
            assertTrue(rig.screens.isDebugOverlayVisible(), "F3 toggles the debug overlay");
            assertTrue(rig.store.settings().showFps, "F3 wrote the setting it changed");
            rig.frames(30);
            assertTrue(rig.overlay.fps() > 0, "overlay measured fps");
            assertTrue(rig.overlay.tps() > 0, "overlay measured tps");
            assertTrue(saveShot("debug-overlay", rig) >= 2, "overlay frame is uniform");

            rig.requestCloseAndVerify();
        }
    }

    @Test
    void theSettingsScreenThroughTheToolkit() throws Exception {
        requireDisplay();
        String language = Strings.active().language();
        try (Rig rig = new Rig("Flapforge smoke test (settings)", false)) {
            MainMenuScreen menu = new MainMenuScreen(rig.context, new ClassicRunFactory(),
                    SeedSequence.of(7));
            rig.start(menu);
            rig.frames(30);
            focusCanvasOrAbort(rig);
            Driver driver = new Driver(rig);
            assertTrue(saveShot("settings-menu", rig) >= 2, "menu is uniform");

            // Menu -> settings, with a real click through the toolkit.
            driver.click(menu.settingsButton(), () -> rig.screens.top() instanceof SettingsScreen);
            SettingsScreen settings = (SettingsScreen) rig.screens.top();
            rig.frames(GRACE);
            assertTrue(saveShot("settings-open", rig) >= 2, "settings is uniform");

            // A toggle, clicked where it actually sits on screen, must reach the engine object
            // that owns the behaviour -- here the loop-owned viewport.
            Toggle integerScaling = settings.toggle("integerScaling");
            settings.focusRow(integerScaling);
            rig.frames(3);
            assertTrue(settings.isRowVisible(integerScaling), "the row is on screen");
            // Counted through the bus rather than compared: the click helper retries when the
            // desktop swallows a press, and a second delivered click would flip the switch back
            // and make an "is it different now" expectation unsatisfiable for ever. The counter
            // is a bus subscriber so the screen's own change handler stays in place.
            int[] applied = {0};
            rig.events.subscribe(GameEvent.SettingsChanged.class, e -> applied[0]++);
            driver.clickAt(integerScaling.centerX(), settings.screenY(integerScaling),
                    () -> applied[0] > 0);
            rig.frames(10);
            assertEquals(integerScaling.value(), rig.viewport.isIntegerScaling(),
                    "the toggle reached the viewport");
            assertTrue(saveShot("settings-toggle", rig) >= 2, "toggled settings is uniform");

            // The language row, driven with the arrow keys, must relabel the whole screen. The
            // pointer is parked over the title first: a pointer left resting on a row would take
            // the focus back the moment anything moves it.
            driver.parkPointerAt(Playfield.WIDTH / 2.0, 30);
            settings.focusRow(settings.languageList());
            rig.frames(3);
            int target = Settings.LANGUAGES.indexOf("pt_BR");
            while (settings.languageList().selectedIndex() < target) {
                int index = settings.languageList().selectedIndex();
                driver.tap(KeyEvent.VK_RIGHT,
                        () -> settings.languageList().selectedIndex() > index);
            }
            rig.frames(5);
            assertEquals("pt_BR", Strings.active().language());
            assertEquals("Voltar", settings.backButton().text(), "every label followed");
            assertTrue(saveShot("settings-language", rig) >= 2, "translated settings is uniform");

            // A rebind: the capture takes the next key the toolkit delivers (E29).
            settings.startCapture(InputAction.MUTE);
            rig.frames(3);
            assertTrue(saveShot("settings-capture", rig) >= 2, "the capture prompt is uniform");
            driver.tap(KeyEvent.VK_Z, () -> settings.capturingAction() == null);
            rig.frames(5);
            assertEquals(List.of(Keys.Z),
                    settings.settings().bindings().keysFor(InputAction.MUTE));
            assertEquals(List.of(Keys.Z), rig.input.bindings().keysFor(InputAction.MUTE),
                    "the queue was rebound on the loop thread");
            assertTrue(saveShot("settings-rebind", rig) >= 2, "rebound settings is uniform");

            // Back to the menu, which must flush the pending write.
            driver.click(settings.backButton(), () -> rig.screens.top() == menu);
            rig.frames(GRACE);
            assertFalse(settings.isDirty(), "leaving the screen wrote the file");
            assertTrue(Files.exists(rig.store.file()), "settings.json was written");
            assertEquals(Strings.load("pt_BR").get(StringKey.MENU_PLAY), menu.playButton().text(),
                    "the menu behind the settings screen followed the language too");
            assertEquals("pt_BR", rig.store.settings().language, "the language was persisted");
            assertTrue(saveShot("settings-back", rig) >= 2, "menu after settings is uniform");

            rig.requestCloseAndVerify();
        } finally {
            Strings.use(Strings.load(language));
        }
    }

    @Test
    void aRunPlayedWithTheRobotCapturesEveryPhase() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test (game)", false)) {
            MainMenuScreen menu = new MainMenuScreen(rig.screens,
                    new ClassicRunFactory(RunMode.SEEDED), SeedSequence.of(42));
            rig.start(menu);
            rig.frames(30);
            focusCanvasOrAbort(rig);
            Driver driver = new Driver(rig);

            // Menu -> game. The run waits for the first flap: capture READY past the first blink.
            driver.tap(KeyEvent.VK_ENTER, () -> rig.screens.top() instanceof GameScreen);
            GameScreen game = (GameScreen) rig.screens.top();
            assertEquals(42L, game.seed(), "--seed reached the first run");
            assertEquals(RunPhase.READY, game.run().phase());
            rig.frames(HudRenderer.BLINK_HALF_TICKS + 10);
            assertTrue(game.renderer().hud().promptVisible(), "the READY hint is blinking");
            assertTrue(saveShot("ready", rig) >= 2, "READY frame is uniform");

            // A real Space tap through the toolkit starts the run.
            driver.tap(KeyEvent.VK_SPACE, () -> game.run().phase() == RunPhase.FLYING);
            assertEquals(1, game.run().simulation().flaps(), "the Robot tap reached the run");

            // Then hold altitude with queued flaps. Robot taps cannot set the cadence (each one
            // waits for its own effect, so the interval varies), and a bird flapped too often
            // climbs into the ceiling gate at y <= 32 where flaps are refused by design. Flapping
            // on the bird's own height keeps it in the middle of the screen and away from both
            // the ceiling and the ground; the first gate only reaches it after ~146 ticks.
            holdAltitude(rig, game, FLIGHT_FLAPS, 600);
            assertEquals(RunPhase.FLYING, game.run().phase(), "the bird survived the flight");
            assertEquals(FLIGHT_FLAPS, game.run().simulation().flaps(),
                    "the flight flapped until the target count");
            assertFalse(game.run().simulation().obstacles().isEmpty(), "a gate is on screen");
            assertTrue(game.renderer().background().distance() > 0, "the ground scrolled");
            assertTrue(saveShot("flying", rig) >= 2, "FLYING frame is uniform");

            // A queued focus loss pauses; an explicit key resumes.
            rig.input.offer(new RawInput.FocusLost(RawInput.FocusLost.UNKNOWN_WHEN));
            rig.until(() -> rig.screens.top() instanceof PauseOverlay, 30);
            assertTrue(rig.screens.top() instanceof PauseOverlay, "focus loss paused the run");
            rig.frames(20);
            assertTrue(saveShot("paused", rig) >= 2, "PAUSE frame is uniform");
            int frozenTick = game.run().tick();
            rig.frames(20);
            assertEquals(frozenTick, game.run().tick(), "the run is frozen while paused");
            focusCanvasOrAbort(rig);
            driver.tap(KeyEvent.VK_SPACE, () -> rig.screens.top() == game);

            // Scripted death: no more flaps, so the bird falls to the ground line.
            diveToGameOver(rig, 900);
            assertTrue(rig.screens.top() instanceof GameOverOverlay, "the dive ended the run");
            assertEquals(RunPhase.FINISHED, game.run().phase());
            rig.frames(HudRenderer.BLINK_HALF_TICKS + 10);
            assertTrue(saveShot("gameover", rig) >= 2, "GAME OVER frame is uniform");

            // Instant retry with a new seed (D29).
            focusCanvasOrAbort(rig);
            driver.tap(KeyEvent.VK_SPACE, () -> rig.screens.top() == game);
            assertEquals(43L, game.seed(), "the retry used the next seed");
            assertEquals(RunPhase.READY, game.run().phase());

            rig.requestCloseAndVerify();
        }
    }

    @Test
    void aRunWithAProfilePaysAndShowsTheRewardStripAndTheSummary() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test (rewards)", false, true)) {
            MainMenuScreen menu = new MainMenuScreen(rig.context,
                    new ClassicRunFactory(RunMode.SEEDED), SeedSequence.of(42));
            rig.start(menu);
            rig.frames(30);
            focusCanvasOrAbort(rig);
            Driver driver = new Driver(rig);
            assertEquals(0, walletOf(rig), "a fresh profile starts empty");

            // Menu -> game, then the scripted flight of the phase test: a few flaps to hold
            // altitude, then no flap at all, so the bird falls to the ground line and the run ends.
            driver.tap(KeyEvent.VK_ENTER, () -> rig.screens.top() instanceof GameScreen);
            GameScreen game = (GameScreen) rig.screens.top();
            driver.tap(KeyEvent.VK_SPACE, () -> game.run().phase() == RunPhase.FLYING);
            holdAltitude(rig, game, FLIGHT_FLAPS, 600);
            diveToGameOver(rig, 900);
            assertTrue(rig.screens.top() instanceof GameOverOverlay, "the dive ended the run");
            assertEquals(RunPhase.FINISHED, game.run().phase());

            // D14/D29: the run is written into the profile and queued for the disk before the
            // strip is pushed, so by now the wallet and the file are already ahead.
            GameOverOverlay strip = (GameOverOverlay) rig.screens.top();
            assertNotNull(strip.outcome(), "the strip carries what the run paid");
            long paid = strip.outcome().rewardSummary().coins();
            // A profile's very first run always pays: firstRunBonus is not gated on gates or on
            // ticks (E32.a), so this holds however short the scripted dive turns out to be.
            assertTrue(paid > 0, "the first run of a profile pays at least the bonus, got "
                    + paid);
            assertTrue(walletOf(rig) >= paid, "the wallet was credited: " + walletOf(rig));
            assertTrue(Files.exists(rig.save.file()), "the profile was written to "
                    + rig.save.file());
            String coinsRow = Strings.active().get(StringKey.STAT_COINS);
            assertTrue(strip.rowTexts().stream().anyMatch(row -> row.startsWith(coinsRow)),
                    () -> "the reward strip shows the coins: " + strip.rowTexts());
            rig.frames(HudRenderer.BLINK_HALF_TICKS + 10);
            assertTrue(saveShot("reward-strip", rig) >= 2, "reward strip frame is uniform");

            // Enter opens the full breakdown (D29).
            focusCanvasOrAbort(rig);
            driver.tap(KeyEvent.VK_ENTER, () -> rig.screens.top() instanceof RunSummaryScreen);
            RunSummaryScreen summary = (RunSummaryScreen) rig.screens.top();
            rig.frames(20);
            assertNotNull(summary.row("participation"), "every reward term has a row");
            assertNotNull(summary.row("coins"));
            assertNotNull(summary.levelBar(), "the level bar needs the profile");
            assertTrue(saveShot("run-summary", rig) >= 2, "run summary frame is uniform");

            rig.requestCloseAndVerify();
        }
    }

    @Test
    void theMetaScreensThroughTheToolkitBuyTheCheapestBird() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test (meta)", false, true)) {
            // Coins the player would have earned in a few runs, so the shop has something to sell.
            Wallet.of(rig.save.profile()).add(PlayerProfile.CURRENCY_COINS, 500);
            MainMenuScreen menu = new MainMenuScreen(rig.context, new ClassicRunFactory(),
                    SeedSequence.of(11));
            rig.start(menu);
            rig.frames(30);
            focusCanvasOrAbort(rig);
            Driver driver = new Driver(rig);
            assertNotNull(menu.birdsButton(), "a session with a profile offers the M4 screens");
            assertEquals(500, walletOf(rig));
            assertTrue(saveShot("menu-meta", rig) >= 2, "the seven-entry menu is uniform");

            // Menu -> Birds, with a real click through the toolkit.
            driver.click(menu.birdsButton(), () -> rig.screens.top() instanceof BirdSelectionScreen);
            BirdSelectionScreen birds = (BirdSelectionScreen) rig.screens.top();
            rig.frames(GRACE);
            assertTrue(saveShot("birds", rig) >= 2, "bird selection is uniform");

            // Buy the cheapest bird that is for sale: focus its card, then press Buy.
            driver.click(birds.roster().card("guardian"), () -> "guardian".equals(
                    birds.currentBirdId()));
            rig.frames(4);
            assertTrue(birds.buyButton().isEnabled(), "150 coins of 500 is affordable");
            long before = walletOf(rig);
            driver.click(birds.buyButton(), () -> walletOf(rig) < before);
            rig.frames(10);
            assertEquals(350, walletOf(rig), "the wallet dropped by the price of the bird");
            assertTrue(rig.save.profile().isUnlocked("bird:guardian"), "and the bird is owned");
            assertTrue(Files.exists(rig.save.file()), "the purchase reached the disk");
            assertTrue(saveShot("birds-bought", rig) >= 2, "bird selection is uniform");

            // Menu -> Upgrades: buy the first node and see the live stat panel move.
            driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);
            rig.frames(GRACE);
            driver.click(menu.upgradesButton(),
                    () -> rig.screens.top() instanceof UpgradeTreeScreen);
            UpgradeTreeScreen trees = (UpgradeTreeScreen) rig.screens.top();
            rig.frames(GRACE);
            assertTrue(saveShot("upgrades", rig) >= 2, "upgrade trees are uniform");
            long beforeNode = walletOf(rig);
            driver.click(trees.nodeGrid().card("feather_1"), () -> walletOf(rig) < beforeNode);
            rig.frames(10);
            assertEquals(1, rig.save.profile().upgradeLevel("feather_1"), "the node was bought");
            assertEquals(beforeNode - 50, walletOf(rig));

            // Menu -> Shop.
            driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);
            rig.frames(GRACE);
            driver.click(menu.shopButton(), () -> rig.screens.top() instanceof ShopScreen);
            ShopScreen shop = (ShopScreen) rig.screens.top();
            rig.frames(GRACE);
            assertFalse(shop.offers().isEmpty(), "the shop still has something to sell");
            assertTrue(saveShot("shop", rig) >= 2, "shop is uniform");
            driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);

            rig.requestCloseAndVerify();
        }
    }

    /**
     * The coins of the rig's profile.
     *
     * @param rig the window and loop
     * @return the balance, 0 when the wallet has no coin entry yet
     */
    private static long walletOf(Rig rig) {
        Long coins = rig.save.profile().wallet.get(PlayerProfile.CURRENCY_COINS);
        return coins == null ? 0 : coins;
    }

    /**
     * D13/D14: the unlock evaluator otherwise runs only at the end of a run and after a purchase,
     * so a profile written before an unlockable existed — every profile carried over from M3 into
     * M4 — would open the game with what it has already earned still locked, and the shop would
     * sell it back. The launch runs the evaluator once and writes the result.
     */
    @Test
    void aLaunchGrantsTheUnlocksTheSavedProfileHasAlreadyEarned() throws Exception {
        requireDisplay();
        Path appHome = OUT_DIR.resolve("catch-up-home");
        deleteRecursively(appHome);
        Files.createDirectories(appHome);
        SavePaths.override(appHome);
        SaveManager seed = new SaveManager(new DirectExecutor(), new FixedTimeSource(0));
        PlayerProfile saved = PlayerProfile.fresh(0).normalize();
        // What an M3 profile looks like on the day M4 ships: four runs played, level 3, and only
        // the six default unlocks, because no unlock table existed while it was being written.
        saved.statistics.totalRuns = 4;
        saved.level = 3;
        assertFalse(saved.isUnlocked("bird:guardian"));
        assertTrue(seed.save(saved), "the seed profile was written");
        assertTrue(seed.flush(4_000L), "and reached the disk");

        GameApplication app = GameApplication.start(LaunchOptions.parse(
                new String[] {"--scale", "1", "--home", appHome.toString()}));
        try {
            assertNotNull(app.context(), "windowed launch wired a context");
            PlayerProfile loaded = app.context().profile();
            assertEquals(4, loaded.statistics.totalRuns, "the saved profile was the one loaded");
            assertTrue(loaded.isUnlocked("bird:guardian"),
                    () -> "runs 3 was already satisfied: " + loaded.unlocked);
            assertTrue(loaded.isUnlocked("bird:heavy"), () -> "runs 4: " + loaded.unlocked);
            assertTrue(loaded.isUnlocked("tree:economy"), () -> "level 3: " + loaded.unlocked);
            waitFor(() -> app.context().loop().isRunning(), 5_000L);
            app.context().input().offer(new RawInput.CloseRequested());
            assertTrue(app.awaitShutdown(4_000L), "loop thread ended after CloseRequested");
        } finally {
            if (app.context() != null && app.context().loop().isRunning()) {
                app.context().loop().stop();
            }
            app.awaitShutdown(4_000L);
            GameWindow.onEdt(() -> { });
        }

        try {
            SaveManager reader = new SaveManager(new DirectExecutor(), new FixedTimeSource(0));
            reader.load();
            assertTrue(reader.profile().isUnlocked("bird:guardian"),
                    "and the grant was persisted, so the shop cannot sell it on the next launch");
        } finally {
            SavePaths.clearOverride();
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void quitPathThroughGameApplicationEndsBeforeTheWatchdog() throws Exception {
        requireDisplay();
        // --home is not optional here: without it the real application would read and rewrite the
        // developer's own ~/.flapforge/settings.json, and the window this test asserts on would
        // depend on whoever ran it.
        Path appHome = OUT_DIR.resolve("app-home");
        GameApplication app = GameApplication.start(LaunchOptions.parse(
                new String[] {"--scale", "1", "--home", appHome.toString()}));
        long started = System.nanoTime();
        try {
            assertNotNull(app.context(), "windowed launch wired a context");
            assertTrue(SavePaths.profileDir().toAbsolutePath()
                            .startsWith(Path.of("build").toAbsolutePath()),
                    "the test suite must never write to the real profile directory, but wrote to "
                            + SavePaths.profileDir());
            assertNotNull(app.loopThread(), "loop thread created");
            waitFor(() -> app.context().loop().isRunning(), 5_000L);
            assertTrue(app.context().loop().isRunning(), "loop running");
            Thread.sleep(200);

            app.context().input().offer(new RawInput.CloseRequested());
            assertTrue(app.awaitShutdown(4_000L), "loop thread ended after CloseRequested");
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            assertTrue(elapsedMs < GameApplication.WATCHDOG_MS, "shutdown took " + elapsedMs + " ms");
            GameWindow.onEdt(() -> { }); // flush the deferred frame.dispose()
            assertFalse(app.context().window().frame().isDisplayable(), "frame disposed");
            assertFalse(app.loopThread().isAlive());
            assertFalse(app.context().loop().isRunning());
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                assertFalse(t.isAlive() && t.getName().startsWith("flapforge-"),
                        "lingering thread: " + t.getName());
            }
        } finally {
            if (app.context() != null && app.context().loop().isRunning()) {
                app.context().loop().stop();
                app.awaitShutdown(4_000L);
            }
            SavePaths.clearOverride();
        }
    }

    /**
     * Flies the bird until it has flapped {@code minFlaps} times, queueing a Space tap whenever it
     * has sunk below {@value #HOLD_Y}. Self-correcting: the bird oscillates around that height and
     * can reach neither the ceiling flap gate (where flaps are refused by design) nor the ground.
     * Driven by the flap count rather than by a frame count, because a paced frame may run zero or
     * several ticks and the desktop may pause the run in between.
     */
    private static void holdAltitude(Rig rig, GameScreen game, int minFlaps, int maxFrames) {
        long stamp = 1;
        for (int i = 0; i < maxFrames && game.run().simulation().flaps() < minFlaps; i++) {
            if (rig.screens.top() instanceof PauseOverlay) {
                // The desktop stole the window's focus and the run paused — which is exactly the
                // behaviour this test checks deliberately further down. Resume and keep flying;
                // the world is frozen meanwhile, so no obstacle creeps up on the bird.
                System.out.println("[smoke] focus was stolen mid-flight; resuming the run");
                focusCanvasOrAbort(rig);
                rig.input.offer(new RawInput.KeyDown(Keys.SPACE, stamp++));
                rig.input.offer(new RawInput.KeyUp(Keys.SPACE, stamp++));
                rig.loop.frame();
                continue;
            }
            if (game.run().phase() == RunPhase.FLYING
                    && game.run().simulation().bird().y() > HOLD_Y) {
                rig.input.offer(new RawInput.KeyDown(Keys.SPACE, stamp++));
                rig.input.offer(new RawInput.KeyUp(Keys.SPACE, stamp++));
            }
            rig.loop.frame();
        }
    }

    /**
     * Runs frames without any flap until the game-over overlay appears, resuming the run if the
     * desktop steals the window's focus on the way down (which pauses it, freezing the fall).
     *
     * @param rig the window and loop
     * @param maxFrames the frame budget
     */
    private static void diveToGameOver(Rig rig, int maxFrames) {
        long stamp = 1;
        for (int i = 0; i < maxFrames && !(rig.screens.top() instanceof GameOverOverlay); i++) {
            if (rig.screens.top() instanceof PauseOverlay) {
                System.out.println("[smoke] focus was stolen mid-dive; resuming the run");
                focusCanvasOrAbort(rig);
                rig.input.offer(new RawInput.KeyDown(Keys.SPACE, stamp++));
                rig.input.offer(new RawInput.KeyUp(Keys.SPACE, stamp++));
            }
            rig.loop.frame();
        }
    }

    /**
     * Raises the window and waits — in wall-clock time — for the canvas to own the keyboard.
     *
     * <p>A frame budget is not a time budget here: the rig drives the loop uncapped, so 30 frames
     * can pass in well under a millisecond while the window manager has not even processed the
     * focus request. Every wait for something that has to travel through X is a real deadline.
     */
    private static void focusCanvasOrAbort(Rig rig) {
        for (int attempt = 0; attempt < 3 && !rig.window.canvas().isFocusOwner(); attempt++) {
            GameWindow.onEdt(() -> {
                rig.window.frame().toFront();
                rig.window.canvas().requestFocusInWindow();
            });
            rig.untilDeadline(() -> rig.window.canvas().isFocusOwner(), FOCUS_TIMEOUT_MS);
        }
        if (!rig.window.canvas().isFocusOwner()) {
            abort("the canvas never obtained keyboard focus (another window is stealing it)");
        }
    }

    private static void waitFor(BooleanSupplier done, long timeoutMs) throws InterruptedException {
        long end = System.nanoTime() + timeoutMs * 1_000_000L;
        while (!done.getAsBoolean() && System.nanoTime() < end) {
            Thread.sleep(10);
        }
    }

    private static void logSizes(String label, GameWindow window) {
        System.out.println("[smoke] " + label + ": canvas=" + window.canvasWidth() + "x"
                + window.canvasHeight() + " frame=" + window.frame().getSize().width + "x"
                + window.frame().getSize().height + " insets=" + window.frame().getInsets()
                + " state=" + window.frame().getExtendedState());
    }

    /**
     * Writes {@code <name>-capture.png} (Robot capture of the canvas, E30.b) and
     * {@code <name>-render.png} (the same frame through the presenter) and returns the number
     * of distinct colours of the capture, or of the render when the capture is uniform or
     * unavailable (logged as a warning: the backbuffer could not be verified).
     */
    private static int saveShot(String name, Rig rig) throws Exception {
        int w = rig.window.canvasWidth();
        int h = rig.window.canvasHeight();
        BufferedImage render = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = render.createGraphics();
        try {
            rig.presenter.paint(g, w, h, rig.loop.lastAlpha());
        } finally {
            g.dispose();
        }
        ImageIO.write(render, "png", OUT_DIR.resolve(name + "-render.png").toFile());

        BufferedImage capture = captureCanvas(rig.window.canvas());
        int captureDistinct = 0;
        if (capture != null) {
            ImageIO.write(capture, "png", OUT_DIR.resolve(name + "-capture.png").toFile());
            captureDistinct = distinctColours(capture);
        }
        if (captureDistinct >= 2) {
            return captureDistinct;
        }
        System.out.println("[smoke] WARNING " + name + ": the screen capture is "
                + (capture == null ? "unavailable" : "uniform") + "; the backbuffer could not be"
                + " verified, asserting on the presenter render instead");
        return distinctColours(render);
    }

    private static BufferedImage captureCanvas(Canvas canvas) {
        try {
            Robot robot = new Robot();
            Point p = canvas.getLocationOnScreen();
            Rectangle area = new Rectangle(p.x, p.y, canvas.getWidth(), canvas.getHeight());
            return robot.createScreenCapture(area);
        } catch (Exception e) {
            System.out.println("[smoke] screen capture unavailable: " + e);
            return null;
        }
    }

    private static int distinctColours(BufferedImage img) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += 4) {
            for (int x = 0; x < img.getWidth(); x += 4) {
                colours.add(img.getRGB(x, y) & 0xffffff);
                if (colours.size() > 8) {
                    return colours.size();
                }
            }
        }
        return colours.size();
    }
}
