package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.AwtInputBridge;
import io.github.michelbr84.flapforge.app.BufferStrategyPresenter;
import io.github.michelbr84.flapforge.app.Clock;
import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.GameWindow;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import io.github.michelbr84.flapforge.app.Threads;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import io.github.michelbr84.flapforge.support.CaptureAudioBackend;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The attract mode against a real window (M9): the menu idles, the demo takes over behind it,
 * input yields it back, and the whole thing is photographed into {@code build/smoke/}.
 *
 * <p>The loop is driven by hand on a clock that advances one tick per read, and the limiter is
 * uncapped, so the twenty idle seconds cost twenty hundred frames instead of twenty seconds of
 * wall time. Input goes through the {@link InputQueue} the {@code AwtInputBridge} feeds, so the
 * frames the menu sees are the frames a player's window delivers.
 */
@Tag("gui")
final class AttractModeSmokeTest {

    /** Path the smoke screenshots go to (the same directory the other smoke suites use). */
    private static final Path OUT_DIR = Path.of("build", "smoke");

    /** A clock that advances exactly one tick on every read: one frame, one tick, no waiting. */
    private static final class SteppedClock implements Clock {
        private long now;

        @Override
        public long nanos() {
            now += Playfield.TICK_NS;
            return now;
        }
    }

    @Test
    void theAttractModeTakesOverTheIdleMenuAndYieldsToInput() throws Exception {
        Files.createDirectories(OUT_DIR);
        SteppedClock clock = new SteppedClock();

        GameWindow window = GameWindow.create("Flapforge attract smoke", 1, false);
        window.setIcons(ProceduralArt.icons());
        Viewport viewport = new Viewport(window.canvasWidth(), window.canvasHeight(), false);
        ScreenManager screens = new ScreenManager(viewport);
        InputQueue input = new InputQueue(KeyBindings.defaults());
        BufferStrategyPresenter presenter = new BufferStrategyPresenter(window, viewport, screens);
        screens.setPresenter(presenter);
        AwtInputBridge bridge = new AwtInputBridge(input);
        EventBus events = new EventBus();
        AudioManager audio = new AudioManager(new CaptureAudioBackend());
        Strings strings = Strings.load("en");
        Strings.use(strings);
        ToastLayer toasts = new ToastLayer();
        GameContext context = new GameContext(LaunchOptions.DEFAULTS, clock, () -> 0L,
                new Threads(), input, viewport, screens, presenter, window, null, null, null,
                events, audio, strings, toasts, null, null, null, null);
        audio.attach(events);
        screens.setEvents(events);

        GameLoop loop = new GameLoop(clock, input, screens, presenter,
                new FrameLimiter(clock, FrameLimiter.UNCAPPED));
        loop.start();
        bridge.attach(window);
        assertTrue(bridge.isAttached());
        GameWindow.onEdt(() -> window.frame().setAlwaysOnTop(true));

        try {
            MainMenuScreen menu =
                    new MainMenuScreen(context, new ClassicRunFactory(), SeedSequence.random());
            screens.push(menu);
            screens.applyPending();

            // Twenty idle seconds at one tick per frame.
            assertTrue(framesUntil(loop, menu::attractActive,
                            MainMenuScreen.ATTRACT_DELAY_TICKS + 200),
                    "the attract mode fired after the idle delay");
            assertNotNull(menu.demo());
            assertNotNull(menu.demo().run(), "the demo run exists once the attract shows");
            long ticksBefore = menu.demo().run().tick();
            // A second and a half into the demo, so the shot shows a gate mid-flight, not the
            // empty first instant of the run.
            for (int i = 0; i < 90; i++) {
                loop.frame();
            }
            assertTrue(menu.demo().run().tick() > ticksBefore,
                    "the demo flies on behind the menu");

            int activeColours = shot(window, presenter, loop, "attract-active");
            assertTrue(activeColours >= 2, "the attract frame is not uniform");

            // Any input hands the menu back.
            input.offer(new RawInput.Wheel(1));
            assertTrue(framesUntil(loop, () -> !menu.attractActive(), 30),
                    "input ended the attract within a frame or two");
            int menuColours = shot(window, presenter, loop, "attract-after-input");
            assertTrue(menuColours >= 2, "the returned menu frame is not uniform");
            long seedBefore = menu.demo().seed();

            // Left alone again, it comes back — on the next seed of the attract stream.
            assertTrue(framesUntil(loop, menu::attractActive,
                            MainMenuScreen.ATTRACT_DELAY_TICKS + 200),
                    "the idle timer restarted and fired again");
            assertNotEquals(seedBefore, menu.demo().seed(),
                    "the second showing cycles to the next attract seed");
        } finally {
            bridge.detach();
            presenter.dispose();
            window.disposeAndWait();
        }
    }

    /** Drives frames until {@code done} holds or the budget runs out. */
    private static boolean framesUntil(GameLoop loop, java.util.function.BooleanSupplier done,
            int maxFrames) {
        for (int i = 0; i < maxFrames && !done.getAsBoolean(); i++) {
            loop.frame();
        }
        return done.getAsBoolean();
    }

    /**
     * Photographs the current frame twice: the presenter's own paint of the frame into an
     * offscreen image (always available), and a Robot capture of the on-screen canvas (best
     * effort — a locked or absent desktop yields nothing and the render carries the assertion).
     */
    private static int shot(GameWindow window, BufferStrategyPresenter presenter, GameLoop loop,
            String name) throws Exception {
        int w = window.canvasWidth();
        int h = window.canvasHeight();
        BufferedImage render = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = render.createGraphics();
        try {
            presenter.paint(g, w, h, loop.lastAlpha());
        } finally {
            g.dispose();
        }
        ImageIO.write(render, "png", OUT_DIR.resolve(name + "-render.png").toFile());

        BufferedImage capture = null;
        try {
            Robot robot = new Robot();
            Point p = window.canvas().getLocationOnScreen();
            capture = robot.createScreenCapture(
                    new Rectangle(p.x, p.y, window.canvas().getWidth(), window.canvas().getHeight()));
        } catch (Exception e) {
            System.out.println("[smoke] attract screen capture unavailable: " + e);
        }
        int captureDistinct = 0;
        if (capture != null) {
            ImageIO.write(capture, "png", OUT_DIR.resolve(name + "-capture.png").toFile());
            captureDistinct = distinctColours(capture);
        }
        if (captureDistinct >= 2) {
            return captureDistinct;
        }
        System.out.println("[smoke] " + name + ": asserting on the presenter render "
                + "(capture " + (capture == null ? "unavailable" : "uniform") + ")");
        return distinctColours(render);
    }

    private static int distinctColours(BufferedImage img) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += 4) {
            for (int x = 0; x < img.getWidth(); x += 4) {
                colours.add(img.getRGB(x, y));
                if (colours.size() >= 2) {
                    return colours.size();
                }
            }
        }
        return colours.size();
    }
}
