package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

import io.github.michelbr84.flapforge.app.AwtInputBridge;
import io.github.michelbr84.flapforge.app.BufferStrategyPresenter;
import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameApplication;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.GameWindow;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import io.github.michelbr84.flapforge.app.SystemClock;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.DebugOverlay;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.screens.GameStubScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
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
    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 5;

    /** Overlay recording the frames it sees, pushed on top of the menu for input checks. */
    static final class SpyOverlay implements Screen {
        int flapPresses;

        @Override
        public void tick(InputFrame input) {
            if (input.isJustPressed(InputAction.FLAP)) {
                flapPresses++;
            }
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public boolean isOverlay() {
            return true;
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
        boolean closed;

        Rig(String title, boolean startFullscreen) {
            window = GameWindow.create(title, 1, startFullscreen);
            window.setIcons(ProceduralArt.icons());
            viewport = new Viewport(window.canvasWidth(), window.canvasHeight(), false);
            screens = new ScreenManager(viewport);
            SystemClock clock = new SystemClock();
            overlay = new DebugOverlay(screens, clock::nanos);
            presenter = new BufferStrategyPresenter(window, viewport, overlay);
            screens.setPresenter(presenter);
            bridge = new AwtInputBridge(input);
            loop = new GameLoop(clock, input, screens, presenter,
                    new FrameLimiter(clock, FrameLimiter.DEFAULT_FPS));
            overlay.setSource(new GameApplication.DebugSource(screens, loop));
            screens.setCloseHandler(() -> {
                closed = true;
                loop.stop();
            });
            bridge.attach(window);
            assertTrue(bridge.isAttached());
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
        }

        void tap(int keyCode, BooleanSupplier expected) {
            String text = KeyEvent.getKeyText(keyCode);
            assertTrue(rig.window.canvas().isFocusOwner(), "canvas lost keyboard focus before " + text);
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            rig.until(expected, 90);
            assertTrue(expected.getAsBoolean(),
                    "Robot key " + text + " was not delivered through the toolkit");
        }

        void click(UiNode node, BooleanSupplier expected) {
            Vec2 w = rig.viewport.toWindow(node.centerX(), node.centerY());
            int wx = (int) Math.round(w.x());
            int wy = (int) Math.round(w.y());
            Canvas canvas = rig.window.canvas();
            assertTrue(canvas.isShowing(), "canvas showing");
            Point origin = canvas.getLocationOnScreen();
            robot.mouseMove(origin.x + wx, origin.y + wy);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            rig.until(expected, 90);
            assertTrue(expected.getAsBoolean(), "Robot click at " + wx + "," + wy
                    + " was not delivered through the toolkit");
        }

        /**
         * Holds {@code F11} for the given time while running frames, releases it, runs a few
         * more frames and returns how many times the fullscreen state changed.
         */
        int holdFullscreenKey(long holdMs) {
            assertTrue(rig.window.canvas().isFocusOwner(), "canvas lost keyboard focus before F11");
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
            assertTrue(rig.until(() -> rig.window.canvas().isFocusOwner(), 60),
                    "focus returned to the recreated fullscreen window");
            assertFalse(rig.input.heldCodes().contains(Keys.F11),
                    "the F11 release was delivered after the handshake");
            driver.tap(KeyEvent.VK_F11, () -> !rig.window.isFullscreen());
            rig.frames(15);
            assertFalse(rig.window.isFullscreen(), "a tap leaves fullscreen");
            assertEquals(1, spy.flapPresses, "no flap edge from the fullscreen handshake");
            rig.screens.pop();
            rig.frames(GRACE);
            logSizes("after F11 twice", rig.window);
            focusCanvasOrAbort(rig);

            driver.tap(KeyEvent.VK_DOWN, () -> menu.focusRing().focused() == menu.settingsButton());
            assertSame(menu.settingsButton(), menu.focusRing().focused(), "Down moves to Settings");
            driver.tap(KeyEvent.VK_UP, () -> menu.focusRing().focused() == menu.playButton());
            assertSame(menu.playButton(), menu.focusRing().focused(), "Up moves focus back");
            driver.tap(KeyEvent.VK_ENTER, () -> rig.screens.top() instanceof GameStubScreen);
            rig.frames(20);
            assertTrue(saveShot("game-stub", rig) >= 2, "game stub is uniform");
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
            rig.frames(30);
            assertTrue(rig.overlay.fps() > 0, "overlay measured fps");
            assertTrue(rig.overlay.tps() > 0, "overlay measured tps");
            assertTrue(saveShot("debug-overlay", rig) >= 2, "overlay frame is uniform");

            rig.requestCloseAndVerify();
        }
    }

    @Test
    void quitPathThroughGameApplicationEndsBeforeTheWatchdog() throws Exception {
        requireDisplay();
        GameApplication app = GameApplication.start(LaunchOptions.parse(new String[] {"--scale", "1"}));
        long started = System.nanoTime();
        try {
            assertNotNull(app.context(), "windowed launch wired a context");
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
        }
    }

    private static void focusCanvasOrAbort(Rig rig) {
        for (int attempt = 0; attempt < 3 && !rig.window.canvas().isFocusOwner(); attempt++) {
            GameWindow.onEdt(() -> {
                rig.window.frame().toFront();
                rig.window.canvas().requestFocusInWindow();
            });
            rig.until(() -> rig.window.canvas().isFocusOwner(), 30);
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
