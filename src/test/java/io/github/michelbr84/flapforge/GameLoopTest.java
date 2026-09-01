package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameLoopTest {

    private static final long MS = 1_000_000L;

    /** Screen that records every frame it is ticked with. */
    static final class RecordingScreen implements Screen {
        final List<InputFrame> frames = new ArrayList<>();
        int renders;

        @Override
        public void tick(InputFrame input) {
            frames.add(input);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            renders++;
        }
    }

    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private RecordingScreen screen;
    private NullPresenter presenter;
    private GameLoop loop;
    private boolean closeHandled;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        screens = new ScreenManager(new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false));
        screen = new RecordingScreen();
        screens.push(screen);
        screens.applyPending();
        presenter = new NullPresenter();
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(() -> {
            closeHandled = true;
            loop.stop();
        });
        loop.start();
    }

    /** Runs one tick so the post-transition edge strip of the initial push is over. */
    private void warmUp() {
        clock.advance(Playfield.TICK_NS);
        loop.frame();
        screen.frames.clear();
    }

    @Test
    void stallRunsAtMostSixTicks() {
        clock.advance(500 * MS);
        loop.frame();
        assertTrue(loop.lastTicks() <= GameLoop.MAX_TICKS_PER_FRAME);
        assertEquals(5, loop.lastTicks(), "a 100 ms cap holds five full ticks");
        assertEquals(5, screen.frames.size());
        assertTrue(loop.accumulatorNs() < Playfield.TICK_NS);
        assertEquals(1, presenter.presentCount());

        assertEquals(100 * MS - 5 * Playfield.TICK_NS, loop.accumulatorNs(),
                "only the capped 100 ms is credited; the remainder of the stall is discarded");
    }

    @Test
    void sixthTickResetsTheAccumulator() {
        clock.advance(10 * MS);
        loop.frame();
        assertEquals(0, loop.lastTicks());
        assertEquals(10 * MS, loop.accumulatorNs());

        clock.advance(500 * MS);
        loop.frame();
        assertEquals(GameLoop.MAX_TICKS_PER_FRAME, loop.lastTicks());
        assertEquals(GameLoop.MAX_TICKS_PER_FRAME, screen.frames.size());
        assertEquals(0, loop.accumulatorNs(), "hitting the cap zeroes the accumulator");
        assertEquals(0.0, loop.lastAlpha(), 0.0);

        clock.advance(Playfield.TICK_NS);
        loop.frame();
        assertEquals(1, loop.lastTicks());
    }

    @Test
    void frameDeltaIsCappedAtHundredMilliseconds() {
        clock.advance(90 * MS);
        loop.frame();
        assertEquals(5, loop.lastTicks());
        assertEquals(90 * MS - 5 * Playfield.TICK_NS, loop.accumulatorNs());
    }

    @Test
    void zeroTickFrameKeepsQueuedEdgesForTheNextTick() {
        warmUp();
        input.offer(new RawInput.KeyDown(Keys.SPACE, 1));
        clock.advance(5 * MS);
        loop.frame();
        assertEquals(0, loop.lastTicks());
        assertTrue(screen.frames.isEmpty());
        assertEquals(1, input.pendingCount());
        assertEquals(2, presenter.presentCount(), "a zero-tick frame still presents");

        clock.advance(12 * MS);
        loop.frame();
        assertEquals(1, loop.lastTicks());
        assertEquals(1, screen.frames.size());
        assertTrue(screen.frames.get(0).isJustPressed(InputAction.FLAP));
    }

    @Test
    void alphaStaysInUnitIntervalAndReachesThePresenter() {
        long[] deltas = {7 * MS, 9 * MS, 16 * MS, 17 * MS, 33 * MS, 1 * MS, 40 * MS, 16_666_667L};
        for (long dt : deltas) {
            clock.advance(dt);
            loop.frame();
            assertTrue(loop.lastAlpha() >= 0.0, "alpha >= 0");
            assertTrue(loop.lastAlpha() < 1.0, "alpha < 1");
            assertEquals(loop.lastAlpha(), presenter.lastAlpha(), 0.0);
        }
        assertEquals(deltas.length, presenter.presentCount());
        assertEquals(loop.tickCount(), screen.frames.size());
    }

    @Test
    void steadySixtyHertzFramesRunOneTickEach() {
        for (int i = 0; i < 100; i++) {
            clock.advance(Playfield.TICK_NS);
            loop.frame();
            assertEquals(1, loop.lastTicks(), "frame " + i);
        }
        assertEquals(100, loop.tickCount());
        assertEquals(100, loop.frameCount());
        assertEquals(100, screen.frames.size());
        assertEquals(0.0, loop.lastAlpha(), 0.0);
    }

    @Test
    void firstFrameWithoutStartCreditsNoTime() {
        GameLoop fresh = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        clock.advance(3_000 * MS);
        fresh.frame();
        assertEquals(0, fresh.lastTicks());
        assertEquals(0, fresh.accumulatorNs());
    }

    @Test
    void closeRequestedStopsTheLoopThroughTheScreenManager() {
        input.offer(new RawInput.CloseRequested());
        clock.advance(Playfield.TICK_NS);
        loop.frame();
        assertTrue(closeHandled);
        assertTrue(screens.isCloseRequested());
        assertFalse(loop.isRunning());
    }

    @Test
    void resizeEventUpdatesViewportAndPresenter() {
        input.offer(new RawInput.Resized(840, 1280));
        clock.advance(Playfield.TICK_NS);
        loop.frame();
        assertEquals(2.0, screens.viewport().scale(), 1e-9);
        assertEquals(1, presenter.resizes());
    }

    @Test
    void fullscreenActionTogglesThePresenter() {
        warmUp();
        input.offer(new RawInput.KeyDown(Keys.F11, 1));
        clock.advance(Playfield.TICK_NS);
        loop.frame();
        assertTrue(presenter.isFullscreen());
        assertTrue(screens.isFullscreen());
        input.offer(new RawInput.KeyUp(Keys.F11, 2));
        input.offer(new RawInput.FullscreenToggled());
        clock.advance(Playfield.TICK_NS);
        loop.frame();
        assertFalse(presenter.isFullscreen());
        assertEquals(2, presenter.fullscreenToggles());
    }

    @Test
    void windowStartedFullscreenLeavesItOnTheFirstToggle() {
        warmUp();
        presenter.setFullscreen(true);
        assertTrue(screens.isFullscreen(), "the presenter is the source of truth");
        input.offer(new RawInput.KeyDown(Keys.F11, 1));
        clock.advance(Playfield.TICK_NS);
        loop.frame();
        assertFalse(presenter.isFullscreen(), "one F11 leaves fullscreen");
        assertFalse(screens.isFullscreen());
        assertEquals(2, presenter.fullscreenToggles());
    }

    @Test
    void transitionsStripEdgesAndIgnoreConfirmForNineTicks() {
        RecordingScreen next = new RecordingScreen();
        screens.push(next);
        input.offer(new RawInput.KeyDown(Keys.ENTER, 1));
        for (int i = 0; i < ScreenManager.TRANSITION_GRACE_TICKS + 2; i++) {
            input.offer(new RawInput.KeyUp(Keys.ENTER, 10 + 2 * i));
            input.offer(new RawInput.KeyDown(Keys.ENTER, 11 + 2 * i));
            clock.advance(Playfield.TICK_NS);
            loop.frame();
        }
        assertEquals(ScreenManager.TRANSITION_GRACE_TICKS + 2, next.frames.size());
        for (int i = 0; i < ScreenManager.TRANSITION_GRACE_TICKS; i++) {
            assertFalse(next.frames.get(i).isJustPressed(InputAction.CONFIRM), "tick " + i);
        }
        assertTrue(next.frames.get(ScreenManager.TRANSITION_GRACE_TICKS).isJustPressed(
                InputAction.CONFIRM));
        assertTrue(screen.frames.isEmpty(), "overlays and pushed screens swallow input");
        assertNotNull(screens.top());
        assertEquals(2, screens.depth());
    }

    @Test
    void nullPresenterRendersIntoAnImageWhenGivenARenderer() {
        NullPresenter drawing = new NullPresenter(screens, screens.viewport(), Playfield.WIDTH,
                Playfield.HEIGHT);
        drawing.present(0.25);
        assertNotNull(drawing.image());
        assertEquals(Playfield.WIDTH, drawing.image().getWidth());
        assertEquals(1, screen.renders);
    }
}
