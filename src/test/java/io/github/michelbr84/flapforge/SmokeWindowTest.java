package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

import io.github.michelbr84.flapforge.app.AwtInputBridge;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.audio.MusicSequencer;
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
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.content.ContentKind;
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
import io.github.michelbr84.flapforge.render.WorldStyle;
import io.github.michelbr84.flapforge.support.CaptureAudioBackend;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.DraftRuns;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Toggle;
import io.github.michelbr84.flapforge.ui.screens.AchievementsScreen;
import io.github.michelbr84.flapforge.ui.screens.BirdSelectionScreen;
import io.github.michelbr84.flapforge.ui.screens.BossBanner;
import io.github.michelbr84.flapforge.ui.screens.ChallengesScreen;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.ContentRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import io.github.michelbr84.flapforge.ui.screens.RuleShiftBanner;
import io.github.michelbr84.flapforge.ui.screens.RunSummaryScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.ModifierChoiceOverlay;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
import io.github.michelbr84.flapforge.ui.screens.ShopScreen;
import io.github.michelbr84.flapforge.ui.screens.UpgradeTreeScreen;
import java.awt.AWTError;
import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
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
 *   <li>the M5 loadout: a passive equipped in the bird selection with a real click, a run built
 *       from it and the active ability used with a real {@code X}, with the ability HUD
 *       screenshotted;</li>
 *   <li>the M7 worlds: the world picker stepped with real arrow keys, then Iron Forge, Storm
 *       Sky and the Void each flown for {@value #WORLD_FRAMES}+ frames on the perfect bot's
 *       decisions delivered as queued taps, with a gear or piston, a lightning warning and the
 *       Void's rule-shift banner captured;</li>
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
    private static final int DELIVERY_ATTEMPTS = 5;

    /**
     * How long a Robot event may take to come back through the toolkit. The rig drives the loop
     * as fast as it can, so a frame budget is not a time budget: 90 uncapped frames can elapse in
     * well under a millisecond while X still has the event in flight on a loaded machine. The
     * 4 s here was 2 s until the first-click flake showed that a burst of parallel CI Test runs
     * can keep X slow for seconds at a time (M10-pre).
     */
    private static final long DELIVERY_TIMEOUT_MS = 4_000L;
    /**
     * How long the pointer warp issued by {@code Robot.mouseMove} may take to actually arrive at
     * the target, read back from the X server via {@link java.awt.MouseInfo}. Robot synthesises
     * press/release immediately after the move, but the warp itself is asynchronous: under a
     * loaded Xvfb the press can land at the <em>old</em> pointer position, i.e. in whatever
     * window is topmost there, and the click silently goes nowhere. Every attempt of the click
     * helpers waits for the arrival before pressing, which is what closes the race.
     */
    private static final long POINTER_ARRIVAL_TIMEOUT_MS = 750L;
    /** How long the window manager may take to grant the canvas keyboard focus. */
    private static final long FOCUS_TIMEOUT_MS = 1_000L;
    /**
     * Flaps the scripted flight performs before the frame is captured. One flap per ~27 ticks
     * holds the bird around {@link #HOLD_Y}, so five of them are ~110 ticks — comfortably short of
     * the ~146 ticks the first gate needs to reach the bird.
     */
    private static final int FLIGHT_FLAPS = 5;
    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 5;
    /** Gate the M6 smoke draft opens at; the shipped schedule starts at 10 (E17). */
    private static final int DRAFT_GATE = 1;
    /** Frames each M7 world is flown for, at least, by the bot-driven smoke flight. */
    private static final int WORLD_FRAMES = 600;
    /** Frame budget for one capture the world flight waits for (a hazard, a warning, a shift). */
    private static final int WORLD_WAIT_FRAMES = 7200;

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
            // E30.j: the smoke rig never opens a sound device — the capture backend records and
            // mixes exactly what a real mixer would play, with no thread and no line (M8, D19:
            // the run-music assertions below listen to it).
            audio = new AudioManager(new CaptureAudioBackend());
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
                if (attempt < DELIVERY_ATTEMPTS - 1) {
                    System.out.println("[smoke] key " + text + " was not delivered, retrying (attempt "
                            + (attempt + 2) + "/" + DELIVERY_ATTEMPTS + ")");
                }
            }
            assertTrue(expected.getAsBoolean(), "Robot key " + text
                    + " was not delivered through the toolkit after " + DELIVERY_ATTEMPTS
                    + " attempts");
        }

        /**
         * Waits until the X pointer is truly at {@code target} (screen coordinates) before the
         * caller presses a button. {@code Robot.mouseMove} only <em>requests</em> a warp; under a
         * loaded X server the press synthesised right after it can still be delivered to the
         * window under the pointer's previous position. Reading the position back through
         * {@link MouseInfo} queries the server itself, so this is a real arrival gate, not a
         * sleep. A timeout is only logged: the click attempt below is retried by
         * {@link #DELIVERY_ATTEMPTS} anyway, and {@code waitForIdle} still lets whatever motion
         * is in flight settle before the press.
         */
        private void awaitPointerAt(Point target) {
            boolean arrived = rig.untilDeadline(() -> {
                // getPointerInfo() can throw HeadlessException without a display or return null
                // on some X servers; both keep the wait bounded instead of failing the click
                // with an unrelated error (pointerNow() guards the same call the same way).
                try {
                    PointerInfo info = MouseInfo.getPointerInfo();
                    return info != null && target.equals(info.getLocation());
                } catch (RuntimeException e) {
                    return false;
                }
            }, POINTER_ARRIVAL_TIMEOUT_MS);
            if (!arrived) {
                System.out.println("[smoke] pointer did not reach " + target + " within "
                        + POINTER_ARRIVAL_TIMEOUT_MS + " ms, clicking anyway");
            }
            robot.waitForIdle();
        }

        void click(UiNode node, BooleanSupplier expected) {
            int wx = 0;
            int wy = 0;
            for (int attempt = 1; attempt <= DELIVERY_ATTEMPTS; attempt++) {
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
                awaitPointerAt(new Point(origin.x + wx, origin.y + wy));
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                rig.untilDeadline(expected, DELIVERY_TIMEOUT_MS);
                if (expected.getAsBoolean()) {
                    return;
                }
                // The expectation may simply have been slow: flush what is in flight and run a
                // couple of frames before re-sending, so a late delivery is counted by the
                // recheck instead of surfacing AFTER the re-sent press in the FIFO input queue
                // (a duplicate press would toggle a switch back off and leave the retry loop
                // chasing an unsatisfiable state). waitForIdle deterministically dispatches an
                // EDT-stalled press; Toolkit.sync() flushes the native side.
                rig.frames(2);
                robot.waitForIdle();
                Toolkit.getDefaultToolkit().sync();
                if (expected.getAsBoolean()) {
                    return;
                }
                if (attempt < DELIVERY_ATTEMPTS) {
                    System.out.println("[smoke] click at " + wx + "," + wy
                            + " was not delivered, retrying (attempt " + attempt + "/"
                            + DELIVERY_ATTEMPTS + ")");
                }
            }
            assertTrue(expected.getAsBoolean(), "Robot click at " + wx + "," + wy
                    + " was not delivered through the toolkit after " + DELIVERY_ATTEMPTS
                    + " attempts" + pointerNow());
        }

        /** Current X pointer position, for failure diagnostics (never throws). */
        private static String pointerNow() {
            try {
                PointerInfo info = MouseInfo.getPointerInfo();
                return info == null ? " (pointer unknown)" : " (pointer now at " + info.getLocation() + ")";
            } catch (RuntimeException e) {
                return " (pointer unknown)";
            }
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
            // Same arrival gate the click helpers use: the park exists so a following keyboard
            // step cannot have its focus stolen, which only holds once the pointer is there.
            awaitPointerAt(new Point(origin.x + (int) Math.round(w.x()),
                    origin.y + (int) Math.round(w.y())));
            rig.frames(2);
        }

        /**
         * Clicks a point in logical playfield coordinates. Used for the settings rows, whose
         * own {@code y} is a position in the scrolled content rather than on the screen.
         */
        void clickAt(double logicalX, double logicalY, BooleanSupplier expected) {
            int wx = 0;
            int wy = 0;
            for (int attempt = 1; attempt <= DELIVERY_ATTEMPTS; attempt++) {
                focusCanvasOrAbort(rig);
                Vec2 w = rig.viewport.toWindow(logicalX, logicalY);
                wx = (int) Math.round(w.x());
                wy = (int) Math.round(w.y());
                Canvas canvas = rig.window.canvas();
                assertTrue(canvas.isShowing(), "canvas showing");
                Point origin = canvas.getLocationOnScreen();
                robot.mouseMove(origin.x + wx, origin.y + wy);
                awaitPointerAt(new Point(origin.x + wx, origin.y + wy));
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                rig.untilDeadline(expected, DELIVERY_TIMEOUT_MS);
                if (expected.getAsBoolean()) {
                    return;
                }
                // Late-delivery recheck before a re-send: see click(). A duplicate click here
                // would toggle a switch back and make the expectation unsatisfiable for ever.
                rig.frames(2);
                robot.waitForIdle();
                Toolkit.getDefaultToolkit().sync();
                if (expected.getAsBoolean()) {
                    return;
                }
                if (attempt < DELIVERY_ATTEMPTS) {
                    System.out.println("[smoke] click at " + wx + "," + wy
                            + " was not delivered, retrying (attempt " + attempt + "/"
                            + DELIVERY_ATTEMPTS + ")");
                }
            }
            assertTrue(expected.getAsBoolean(), "Robot click at " + wx + "," + wy
                    + " was not delivered through the toolkit after " + DELIVERY_ATTEMPTS
                    + " attempts" + pointerNow());
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
     * The M5 loop through the toolkit: equip a passive in the bird selection, start a run with the
     * loadout the profile now carries, and use the active ability with a real {@code X}.
     *
     * <p>It is the one place where the whole chain is exercised at once — chip, profile, save,
     * {@code RunLoadout}, {@code AbilityManager}, HUD — and the screenshots in
     * {@code build/smoke/} are what a reviewer looks at to see the cooldown ring, the charge pips
     * and the shield icon actually drawn on a real window.
     */
    @Test
    void anEquippedAbilityIsUsedInARunAndShownOnTheHud() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test (abilities)", false, true)) {
            PlayerProfile profile = rig.save.profile();
            // The shield is unlocked by playing five runs (§4); this smoke run is about the HUD,
            // not the economy, so it is granted outright.
            profile.unlock("ability:shield");
            GameContent content = GameContent.load();
            MainMenuScreen menu = new MainMenuScreen(rig.context,
                    new ContentRunFactory(content, RunMode.SEEDED, () -> profile),
                    SeedSequence.of(42));
            rig.start(menu);
            rig.frames(30);
            focusCanvasOrAbort(rig);
            Driver driver = new Driver(rig);

            // Menu -> Birds, then equip the shield in the first passive slot with a real click.
            driver.click(menu.birdsButton(),
                    () -> rig.screens.top() instanceof BirdSelectionScreen);
            BirdSelectionScreen birds = (BirdSelectionScreen) rig.screens.top();
            rig.frames(GRACE);
            BirdSelectionScreen.AbilitySlot passive =
                    birds.slot(BirdSelectionScreen.SlotRole.PASSIVE, 0);
            assertNotNull(passive, "Forgewing carries two passive slots");
            driver.click(passive, () -> "shield".equals(passive.abilityId()));
            rig.frames(4);
            assertEquals(List.of("shield"), profile.selected.passiveAbilityIds,
                    "the chip wrote the loadout into the profile");
            assertEquals("double_flap", profile.selected.activeAbilityId, "the E18 default");
            assertTrue(saveShot("loadout", rig) >= 2, "the loadout row is uniform");

            // Back to the menu and into a run built from that loadout.
            driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);
            rig.frames(GRACE);
            // Play is clicked rather than tapped: the menu keeps the focus on the Birds button
            // this test just came back from, so Enter would open the bird selection again.
            driver.click(menu.playButton(), () -> rig.screens.top() instanceof GameScreen);
            GameScreen game = (GameScreen) rig.screens.top();
            assertEquals("double_flap", game.run().simulation().abilities().active().id(),
                    "the run carries the equipped active ability");
            assertEquals(1, game.run().simulation().shield().maxCharges(),
                    "and the equipped shield's charge (D9)");

            driver.tap(KeyEvent.VK_SPACE, () -> game.run().phase() == RunPhase.FLYING);
            holdAltitude(rig, game, FLIGHT_FLAPS, 600);
            assertEquals(RunPhase.FLYING, game.run().phase(), "the bird survived the flight");

            // A real X through the toolkit reaches the simulation as an activation.
            driver.tap(KeyEvent.VK_X,
                    () -> game.run().stats().abilitiesUsed().containsKey("double_flap"));
            assertEquals(1, game.run().simulation().abilities().active().charges(),
                    "the press spent one of the two charges");
            rig.frames(6);
            assertTrue(saveShot("ability-hud", rig) >= 2, "the ability HUD frame is uniform");

            rig.requestCloseAndVerify();
        }
    }

    /**
     * The M6 loop through the toolkit: fly a real run until the draft opens, read the cards, take
     * one with a real click and watch the build appear on the HUD.
     *
     * <p>The schedule is shortened to one draft at gate {@value #DRAFT_GATE} on a flat corridor
     * (E17) — a smoke test cannot spend the ten gates the shipped schedule asks for — but the
     * cards, the rarities, the synergies and every string on them are the shipped ones. The
     * screenshots in {@code build/smoke/} are what a reviewer looks at to see the three panels and
     * the build strip drawn on a real window.
     */
    @Test
    void aMidRunDraftIsTakenWithTheRobotAndShownOnTheHud() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test (draft)", false)) {
            MainMenuScreen menu = new MainMenuScreen(rig.screens,
                    DraftRuns.source(DraftRuns.catalog(GameContent.load(), DRAFT_GATE, 3)),
                    SeedSequence.of(42));
            rig.start(menu);
            rig.frames(30);
            focusCanvasOrAbort(rig);
            Driver driver = new Driver(rig);

            driver.tap(KeyEvent.VK_ENTER, () -> rig.screens.top() instanceof GameScreen);
            GameScreen game = (GameScreen) rig.screens.top();
            driver.tap(KeyEvent.VK_SPACE, () -> game.run().phase() == RunPhase.FLYING);
            flyToDraft(rig, game, 6000);
            assertTrue(rig.screens.top() instanceof ModifierChoiceOverlay,
                    () -> "no draft after gate " + game.run().stats().gatesPassed()
                            + "; the run is in " + game.run().phase());
            ModifierChoiceOverlay overlay = (ModifierChoiceOverlay) rig.screens.top();
            rig.frames(GRACE);
            assertEquals(3, overlay.cards().size(), "three cards are on the table");
            assertFalse(overlay.cards().get(0).name().isEmpty(), "and they are named");
            assertTrue(saveShot("draft", rig) >= 2, "the draft frame is uniform");

            // A real click on the middle card takes it.
            ModifierChoiceOverlay.Card card = overlay.cards().get(1);
            driver.click(card, () -> !game.run().stats().modifiersTaken().isEmpty());
            assertEquals(List.of(card.id()), game.run().stats().modifiersTaken(),
                    "the Robot click took the card it was over");
            assertEquals(RunPhase.RESUME_HOLD, game.run().phase());
            rig.frames(6);
            assertTrue(saveShot("draft-countdown", rig) >= 2, "the countdown frame is uniform");

            // The countdown runs out, the overlay leaves and the HUD carries the build.
            assertTrue(rig.until(() -> rig.screens.top() == game,
                    ModifierDirector.RESUME_HOLD_TICKS + 120), "the run resumed");
            assertEquals(RunPhase.FLYING, game.run().phase());
            assertEquals(List.of(card.name()), game.renderer().hud().buildChips(),
                    "the taken card is on the HUD");
            rig.frames(6);
            assertTrue(saveShot("draft-hud", rig) >= 2, "the HUD build frame is uniform");

            rig.requestCloseAndVerify();
        }
    }

    /**
     * The M7 loop through the toolkit: the world picker stepped with real arrow keys past a
     * world the profile owns, then a run in Iron Forge, one in Storm Sky and one in the Void,
     * each flown for at least {@value #WORLD_FRAMES} frames on the perfect bot's decisions
     * delivered as queued Space taps. The frames a reviewer looks at go to {@code build/smoke/}:
     * the picker with the forge selected, a gear or a piston on screen in the forge, a lightning
     * column in its warning (the fairness cue) in the storm, and the Void's rule-shift banner
     * over a live run — the {@code -render.png} of each is the {@link BufferedImage} the
     * presenter painted, next to the Robot capture.
     *
     * <p>A bot flying through the queue is a tick behind its own decision (the tap is drained
     * on the next tick), so it dies now and then; the flight retries on the game-over strip
     * (D29, the next seed) and keeps counting frames, so every capture waits for the real thing
     * rather than for one lucky seed.
     */
    @Test
    void theWorldsArePickedWithTheToolkitAndPlayedWithTheirHazardsOnScreen() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test (worlds)", false, true)) {
            PlayerProfile profile = rig.save.profile();
            // The worlds are earned by clearing bosses (M8) or bought (D13); this smoke run is
            // about the picker and the hazards, so they are granted outright in the test's home.
            for (String id : List.of("wind_valley", "iron_forge", "storm_sky", "void")) {
                profile.unlock("world:" + id);
            }
            GameContent content = GameContent.load();
            Strings strings = Strings.active();
            MainMenuScreen menu = new MainMenuScreen(rig.context,
                    new ContentRunFactory(content, RunMode.SEEDED, () -> profile),
                    SeedSequence.of(42));
            rig.start(menu);
            rig.frames(30);
            focusCanvasOrAbort(rig);
            Driver driver = new Driver(rig);

            // Menu -> Birds; the world row is stepped with real arrow keys, Wind Valley then the
            // forge, both owned. The pointer is parked over the title first so no row takes the
            // focus back the moment it moves.
            driver.click(menu.birdsButton(), () -> rig.screens.top() instanceof BirdSelectionScreen);
            BirdSelectionScreen birds = (BirdSelectionScreen) rig.screens.top();
            rig.frames(GRACE);
            assertEquals("green_fields", birds.currentWorldId(), "a fresh profile flies the fields");
            driver.parkPointerAt(Playfield.WIDTH / 2.0, 30);
            birds.focusRing().focus(birds.worldList());
            rig.frames(3);
            driver.tap(KeyEvent.VK_RIGHT, () -> "wind_valley".equals(profile.selected.worldId));
            driver.tap(KeyEvent.VK_RIGHT, () -> "iron_forge".equals(profile.selected.worldId));
            rig.frames(4);
            assertEquals("iron_forge", birds.currentWorldId(), "the row shows the selection");
            assertFalse(birds.worldList().isLocked(), "an owned world is not marked locked");
            assertTrue(birds.worldDetail().contains(strings.get(StringKey.OBSTACLE_GEAR)),
                    "the forge lists its gears: " + birds.worldDetail());
            assertTrue(Files.exists(rig.save.file()), "the selection reached the disk");
            assertTrue(saveShot("world-picker", rig) >= 2, "the picker frame is uniform");
            driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);
            rig.frames(GRACE);
            String forgeName = ProgressionText.name(strings, ContentKind.WORLD, "iron_forge");
            assertTrue(menu.worldLine().contains(forgeName),
                    "the menu names the selected world: " + menu.worldLine());
            assertTrue(saveShot("menu-world", rig) >= 2, "the menu frame is uniform");

            // Iron Forge: fly until a gear or a piston is on screen, capture it, keep flying.
            GameScreen forge = play(rig, driver, menu, "iron_forge", WorldStyle.FACTORY);
            // M8: the run started its world loop. The capture backend is the mix, so the loop
            // being audible costs no sound device.
            CaptureAudioBackend music = (CaptureAudioBackend) rig.audio.backend();
            assertTrue(music.loopPlayList().stream().anyMatch(played ->
                            played.id().equals(MusicSequencer.idForWorld("iron_forge"))
                                    && played.gain() > 0.0f),
                    "the forge run plays its world loop: " + music.loopPlayList());
            assertTrue(rms(music.mixedLoopSeconds(0.25)) > 0.01,
                    "the forge loop is audible in the rig's mix");
            WorldFlight forgeFlight = new WorldFlight(rig, forge);
            assertTrue(forgeFlight.fly(WORLD_WAIT_FRAMES, () -> onScreen(forge, ObstacleKind.GEAR)
                    || onScreen(forge, ObstacleKind.PISTON)),
                    () -> "no gear or piston reached the screen in " + forgeFlight.frames
                            + " frames (" + forgeFlight.retries + " retries)");
            assertTrue(saveShot("iron-forge-hazards", rig) >= 2, "the forge frame is uniform");
            forgeFlight.fly(WORLD_FRAMES, () -> forgeFlight.frames >= WORLD_FRAMES);
            assertTrue(forgeFlight.frames >= WORLD_FRAMES, "the forge was flown for "
                    + forgeFlight.frames + " frames");
            assertTrue(forgeFlight.ticks > 0, "the run advanced");
            System.out.println("[smoke] iron_forge: " + forgeFlight);
            leaveRun(rig, driver, menu);

            // Storm Sky: the next world in the row. Fly until a lightning column is warning —
            // the marker over its side and extent is the fairness cue — and capture it while the
            // warning is up; the sky flash (E8) is captured too when a run lasts three gates.
            select(rig, driver, menu, "storm_sky");
            GameScreen storm = play(rig, driver, menu, "storm_sky", WorldStyle.STORM);
            assertEquals(0.5, storm.run().simulation().darkness(), 0.0, "the storm is half dark");
            WorldFlight stormFlight = new WorldFlight(rig, storm);
            assertTrue(stormFlight.fly(WORLD_WAIT_FRAMES, () -> warningOnScreen(storm)),
                    () -> "no lightning warning reached the screen in " + stormFlight.frames
                            + " frames (" + stormFlight.retries + " retries)");
            assertTrue(saveShot("storm-sky-warning", rig) >= 2, "the storm frame is uniform");
            if (stormFlight.fly(WORLD_WAIT_FRAMES / 4, () -> storm.renderer().isFlashing())) {
                assertTrue(saveShot("storm-sky-flash", rig) >= 2, "the flash frame is uniform");
            } else {
                System.out.println("[smoke] storm_sky: no ambient flash within the budget, "
                        + "the flash capture is skipped");
            }
            stormFlight.fly(WORLD_FRAMES, () -> stormFlight.frames >= WORLD_FRAMES);
            assertTrue(stormFlight.frames >= WORLD_FRAMES, "the storm was flown for "
                    + stormFlight.frames + " frames");
            System.out.println("[smoke] storm_sky: " + stormFlight);
            leaveRun(rig, driver, menu);

            // The Void: its rule shift is announced five gates in; capture the banner over the
            // live run, telegraph first and then the "in effect" flash.
            select(rig, driver, menu, "void");
            GameScreen voidRun = play(rig, driver, menu, "void", WorldStyle.VOID);
            WorldFlight voidFlight = new WorldFlight(rig, voidRun);
            assertTrue(voidFlight.fly(WORLD_WAIT_FRAMES, () -> voidRun.banner().isVisible()),
                    () -> "no rule shift was announced in " + voidFlight.frames + " frames ("
                            + voidFlight.retries + " retries, best "
                            + voidFlight.bestGates + " gates)");
            assertEquals(RuleShiftBanner.Phase.TELEGRAPH, voidRun.banner().phase());
            assertFalse(voidRun.banner().line().isEmpty(), "the banner names the rule");
            assertTrue(saveShot("void-rule-shift", rig) >= 2, "the banner frame is uniform");
            if (voidFlight.fly(RuleShiftBanner.IN_EFFECT_TICKS + 90 + 300,
                    () -> voidRun.banner().phase() == RuleShiftBanner.Phase.IN_EFFECT)) {
                assertTrue(saveShot("void-rule-in-effect", rig) >= 2,
                        "the in-effect frame is uniform");
            }
            voidFlight.fly(WORLD_FRAMES, () -> voidFlight.frames >= WORLD_FRAMES);
            assertTrue(voidFlight.frames >= WORLD_FRAMES, "the void was flown for "
                    + voidFlight.frames + " frames");
            System.out.println("[smoke] void: " + voidFlight);
            leaveRun(rig, driver, menu);

            rig.requestCloseAndVerify();
        }
    }

    /**
     * The M8 loop through the toolkit: the Challenges screen opened from the menu with a real
     * click, its list stepped with real arrow keys to the corridor challenge, and Play starting
     * that challenge's run — flown on the perfect bot's decisions until the boss warns, fights
     * and is cleared, with the banner state, the HUD countdown and the objective line captured at
     * each step; then the Achievements screen opened and its three tabs stepped with real arrow
     * keys. The frames a reviewer looks at go to {@code build/smoke/}.
     *
     * <p>The challenge and a starter achievement set are granted outright in the test's home, so
     * the screens have something to show; the boss flight retries on the game-over strip like the
     * M7 flight does.
     */
    @Test
    void theChallengesAndAchievementsShowWithTheToolkitAndAChallengeBossIsFlown() throws Exception {
        requireDisplay();
        try (Rig rig = new Rig("Flapforge smoke test (m8)", false, true)) {
            PlayerProfile profile = rig.save.profile();
            // What the smoke run is about to show: a playable challenge and a few held
            // achievements, granted outright in the test's own home.
            profile.unlock("challenge:boss_corridor_1");
            profile.unlock("cosmetic:classic:ember");
            profile.achievements.put("first_flight", new PlayerProfile.AchievementRecord(0L));
            profile.achievements.put("gates_25", new PlayerProfile.AchievementRecord(0L));
            MainMenuScreen menu = new MainMenuScreen(rig.context,
                    new ContentRunFactory(GameContent.load(), RunMode.SEEDED, () -> profile),
                    SeedSequence.of(42));
            rig.start(menu);
            rig.frames(30);
            focusCanvasOrAbort(rig);
            Driver driver = new Driver(rig);

            // Menu -> Challenges; step the list to the corridor challenge with real arrow keys,
            // one Right per challenge like the world picker is stepped.
            driver.click(menu.challengesButton(),
                    () -> rig.screens.top() instanceof ChallengesScreen);
            ChallengesScreen challenges = (ChallengesScreen) rig.screens.top();
            rig.frames(GRACE);
            driver.parkPointerAt(Playfield.WIDTH / 2.0, 30);
            List<String> challengeIds = GameContent.load().challenges().ids();
            while (!"boss_corridor_1".equals(challenges.selected().id())) {
                String next = challengeIds.get(challengeIds.indexOf(challenges.selected().id())
                        + 1);
                driver.tap(KeyEvent.VK_RIGHT, () -> next.equals(challenges.selected().id()));
            }
            assertNotNull(challenges.playSource(), "the granted challenge offers a run");
            assertTrue(saveShot("challenges", rig) >= 2, "the challenges frame is uniform");

            // A real click on Play starts the challenge's own run.
            driver.click(challenges.playButton(), () -> rig.screens.top() instanceof GameScreen);
            GameScreen game = (GameScreen) rig.screens.top();
            rig.frames(GRACE);
            assertEquals(RunMode.CHALLENGE, game.run().config().mode());
            assertEquals("boss_corridor_1", game.run().config().challengeId());
            assertEquals("green_fields", game.run().config().worldId());

            // Fly until the banner telegraphs, fights and clears, capturing each state; the HUD
            // countdown and the objective line ride along.
            WorldFlight flight = new WorldFlight(rig, game);
            BossBanner banner = game.bossBanner();
            assertTrue(flight.fly(WORLD_WAIT_FRAMES,
                            () -> banner.phase() == BossBanner.Phase.WARNING),
                    () -> "no boss warning: " + flight);
            assertTrue(saveShot("challenge-boss-warning", rig) >= 2,
                    "the warning frame is uniform");
            assertFalse(game.renderer().hud().bossText().isEmpty(),
                    "the HUD runs its countdown beside the banner");
            assertFalse(game.renderer().hud().objectiveText().isEmpty(),
                    "the objective line is up during a challenge");

            assertTrue(flight.fly(WORLD_WAIT_FRAMES,
                            () -> banner.phase() == BossBanner.Phase.ACTIVE),
                    () -> "the fight never started: " + flight);
            assertTrue(saveShot("challenge-boss", rig) >= 2, "the boss frame is uniform");

            assertTrue(flight.fly(WORLD_WAIT_FRAMES,
                            () -> banner.phase() == BossBanner.Phase.CLEARED),
                    () -> "the boss never cleared: " + flight);
            assertTrue(saveShot("challenge-boss-cleared", rig) >= 2,
                    "the cleared frame is uniform");
            assertEquals(Strings.active().get(StringKey.HUD_OBJECTIVE_COMPLETE),
                    game.renderer().hud().objectiveText(), "the objective latched on the clear");
            System.out.println("[smoke] boss_corridor_1: " + flight);

            leaveRun(rig, driver, menu);

            // Menu -> Achievements: the three tabs stepped with real arrow keys.
            driver.click(menu.achievementsButton(),
                    () -> rig.screens.top() instanceof AchievementsScreen);
            AchievementsScreen achievements = (AchievementsScreen) rig.screens.top();
            rig.frames(GRACE);
            driver.parkPointerAt(Playfield.WIDTH / 2.0, 30);
            assertEquals(AchievementsScreen.TAB_ACHIEVEMENTS, achievements.tabBar().selectedId());
            assertTrue(saveShot("achievements", rig) >= 2, "the achievements frame is uniform");
            driver.tap(KeyEvent.VK_RIGHT, () -> achievements.tabBar().selectedId()
                    .equals(AchievementsScreen.TAB_MILESTONES));
            assertTrue(saveShot("achievements-milestones", rig) >= 2,
                    "the milestones frame is uniform");
            driver.tap(KeyEvent.VK_RIGHT, () -> achievements.tabBar().selectedId()
                    .equals(AchievementsScreen.TAB_COLLECTIONS));
            assertTrue(saveShot("achievements-collections", rig) >= 2,
                    "the collections frame is uniform");
            assertEquals(8, achievements.bars().size(), "one bar per collection category");

            driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);
            rig.frames(GRACE);
            rig.requestCloseAndVerify();
        }
    }

    /**
     * Steps the world row to a world with real arrow keys: menu → Birds, one {@code Right} per
     * world from the selected one, back to the menu.
     */
    private static void select(Rig rig, Driver driver, MainMenuScreen menu, String worldId) {
        driver.click(menu.birdsButton(), () -> rig.screens.top() instanceof BirdSelectionScreen);
        BirdSelectionScreen birds = (BirdSelectionScreen) rig.screens.top();
        rig.frames(GRACE);
        driver.parkPointerAt(Playfield.WIDTH / 2.0, 30);
        birds.focusRing().focus(birds.worldList());
        rig.frames(3);
        List<String> ids = birds.worldIds();
        int target = ids.indexOf(worldId);
        assertTrue(target >= 0, worldId + " is in the picker: " + ids);
        while (ids.indexOf(birds.currentWorldId()) < target) {
            String next = ids.get(ids.indexOf(birds.currentWorldId()) + 1);
            driver.tap(KeyEvent.VK_RIGHT, () -> next.equals(birds.currentWorldId()));
        }
        assertEquals(worldId, birds.currentWorldId());
        driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);
        rig.frames(GRACE);
    }

    /**
     * Menu → game with a real click on Play (the menu keeps its focus on the Birds button the
     * test just came back from, so Enter would open the bird selection again) and checks the run
     * is in the world with the world's look.
     */
    private static GameScreen play(Rig rig, Driver driver, MainMenuScreen menu, String worldId,
            WorldStyle style) {
        driver.click(menu.playButton(), () -> rig.screens.top() instanceof GameScreen);
        GameScreen game = (GameScreen) rig.screens.top();
        assertEquals(worldId, game.run().config().worldId(), "the run is played in the world");
        assertEquals(style, game.renderer().style(), "the renderer took the world's style");
        assertTrue(game.renderer().hud().worldNameVisible(game.run().phase()),
                "the HUD names the world on the READY screen");
        rig.frames(HudRenderer.BLINK_HALF_TICKS + 10);
        return game;
    }

    /** Pauses the run with a real {@code Esc}, then leaves it for the menu with a second one. */
    private static void leaveRun(Rig rig, Driver driver, MainMenuScreen menu) {
        focusCanvasOrAbort(rig);
        if (rig.screens.top() instanceof GameOverOverlay) {
            driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);
        } else {
            if (!(rig.screens.top() instanceof PauseOverlay)) {
                driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() instanceof PauseOverlay);
            }
            driver.tap(KeyEvent.VK_ESCAPE, () -> rig.screens.top() == menu);
        }
        rig.frames(GRACE);
    }

    /** Whether an obstacle of a kind is wholly inside the playfield. */
    private static boolean onScreen(GameScreen game, ObstacleKind kind) {
        for (Obstacle o : game.run().simulation().obstacles().obstacles()) {
            if (o.kind() == kind && o.x() >= 0 && o.x() + o.width() <= Playfield.WIDTH) {
                return true;
            }
        }
        return false;
    }

    /** Whether a lightning column is in its warning inside the playfield. */
    private static boolean warningOnScreen(GameScreen game) {
        for (Obstacle o : game.run().simulation().obstacles().obstacles()) {
            if (o instanceof LightningStrike bolt
                    && bolt.state() == LightningStrike.State.WARNING
                    && bolt.x() < Playfield.WIDTH - 24) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flies one game screen on the perfect bot's decisions, delivered as queued Space taps: one
     * decision per simulation tick the screen has advanced, a tap to start a READY run, a tap to
     * resume after a stolen focus, and a tap on the game-over strip to retry with the next seed
     * (D29). A fresh bot per attempt, so no target from a dead run survives into the next.
     */
    private static final class WorldFlight {
        private final Rig rig;
        private final GameScreen game;
        private BotPilot pilot;
        private long stamp = 1;
        private int decidedTick = -1;
        private int lastStartFrame = -1000;
        int frames;
        int ticks;
        int retries;
        int bestGates;

        WorldFlight(Rig rig, GameScreen game) {
            this.rig = rig;
            this.game = game;
            this.pilot = new BotPilot(BotPilot.Preset.PERFECT, 42);
        }

        /**
         * Runs frames until {@code until} holds or the budget is spent.
         *
         * @return whether {@code until} holds
         */
        boolean fly(int maxFrames, BooleanSupplier until) {
            for (int i = 0; i < maxFrames && !until.getAsBoolean(); i++) {
                step();
            }
            return until.getAsBoolean();
        }

        private void step() {
            Screen top = rig.screens.top();
            if (top instanceof PauseOverlay) {
                System.out.println("[smoke] focus was stolen mid-flight; resuming the run");
                focusCanvasOrAbort(rig);
                tap();
            } else if (top instanceof GameOverOverlay) {
                if (frames - lastStartFrame > 10) {
                    retries++;
                    lastStartFrame = frames;
                    decidedTick = -1;
                    pilot = new BotPilot(BotPilot.Preset.PERFECT, 42 + retries);
                    tap();
                }
            } else if (top == game) {
                Run run = game.run();
                bestGates = Math.max(bestGates, run.stats().gatesPassed());
                if (run.phase() == RunPhase.READY) {
                    if (frames - lastStartFrame > 10) {
                        lastStartFrame = frames;
                        tap();
                    }
                } else if (flies(run.phase()) && run.tick() != decidedTick) {
                    decidedTick = run.tick();
                    if (pilot.decide(run).flap()) {
                        tap();
                    }
                }
            }
            int before = game.run().tick();
            rig.loop.frame();
            frames++;
            if (rig.screens.top() == game && game.run().tick() > before) {
                ticks += game.run().tick() - before;
            }
        }

        private void tap() {
            rig.input.offer(new RawInput.KeyDown(Keys.SPACE, stamp++));
            rig.input.offer(new RawInput.KeyUp(Keys.SPACE, stamp++));
        }

        @Override
        public String toString() {
            return "frames " + frames + ", ticks " + ticks + ", retries " + retries
                    + ", best gates " + bestGates + ", now " + game.run().phase() + " at gate "
                    + game.run().stats().gatesPassed();
        }
    }

    /**
     * Whether a run phase has the bird airborne and flying: every phase that ticks the
     * simulation like {@code FLYING} (D11), which includes the boss countdown and the fight
     * itself — a bot that only flaps in {@code FLYING} falls to the ground during
     * {@code BOSS_WARNING}'s suppressed spawns and never reaches the fight.
     *
     * @param phase the phase to test
     * @return {@code true} when the phase flies the bird
     */
    private static boolean flies(RunPhase phase) {
        return phase == RunPhase.FLYING || phase == RunPhase.BREATHER
                || phase == RunPhase.BOSS_WARNING || phase == RunPhase.BOSS;
    }

    /**
     * Flies the flat corridor until the draft opens, queueing a flap whenever the bird has sunk
     * below the gap centre and resuming the run if the desktop steals the window's focus.
     *
     * <p>It stops the moment the run enters {@code CHOOSING_MODIFIER}: Space is the confirm key on
     * every screen, so one more queued flap over the open cards would take one of them before the
     * test has looked at it.
     *
     * @param rig the window and loop
     * @param game the screen being played
     * @param maxFrames the frame budget
     */
    private static void flyToDraft(Rig rig, GameScreen game, int maxFrames) {
        long stamp = 1;
        for (int i = 0; i < maxFrames && !(rig.screens.top() instanceof ModifierChoiceOverlay)
                && game.run().phase() != RunPhase.CHOOSING_MODIFIER; i++) {
            if (rig.screens.top() instanceof PauseOverlay) {
                System.out.println("[smoke] focus was stolen mid-flight; resuming the run");
                focusCanvasOrAbort(rig);
                rig.input.offer(new RawInput.KeyDown(Keys.SPACE, stamp++));
                rig.input.offer(new RawInput.KeyUp(Keys.SPACE, stamp++));
                rig.loop.frame();
                continue;
            }
            if (game.run().simulation().bird().y() > Playfield.BIRD_START_Y + 10) {
                rig.input.offer(new RawInput.KeyDown(Keys.SPACE, stamp++));
                rig.input.offer(new RawInput.KeyUp(Keys.SPACE, stamp++));
            }
            rig.loop.frame();
        }
        rig.until(() -> rig.screens.top() instanceof ModifierChoiceOverlay, 10);
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
    /** Root-mean-square level of an interleaved sample buffer (the music-audible check). */
    private static double rms(float[] samples) {
        if (samples.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (float sample : samples) {
            sum += (double) sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }

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
