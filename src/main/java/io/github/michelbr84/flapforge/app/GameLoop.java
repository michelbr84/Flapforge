package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import java.util.Objects;

/**
 * Fixed-timestep game loop (D1).
 *
 * <p>Each {@link #frame()}: {@code acc += min(dt, 100 ms)}; up to {@value #MAX_TICKS_PER_FRAME}
 * ticks of {@link Playfield#TICK_NS} are run, each with the input drained for that tick; if the
 * cap was hit the accumulator is reset so a stall never kills the player; then the presenter
 * shows a frame with {@code alpha = acc / TICK_NS} and the limiter paces. The loop depends only on
 * {@link Clock}, {@link InputQueue}, {@link ScreenManager}, {@link FramePresenter} and
 * {@link FrameLimiter}; it never touches the window toolkit so tests, the smoke test and
 * {@code --headless-run} can drive {@link #frame()} directly.
 *
 * <p>After the tick loop the frame honours {@link ScreenManager#consumeAccumulatorReset()}: a
 * screen that resumed from a pause asks for the banked time to be dropped, so the first frame
 * back never replays it as a burst of ticks (D2).
 */
public final class GameLoop {

    /** Longest frame delta credited to the accumulator. */
    public static final long MAX_FRAME_NS = 100_000_000L;
    /** Maximum simulation ticks per frame. */
    public static final int MAX_TICKS_PER_FRAME = 6;

    private final Clock clock;
    private final InputQueue input;
    private final ScreenManager screens;
    private final FramePresenter presenter;
    private final FrameLimiter limiter;

    private volatile boolean running;
    private boolean primed;
    private long previousNanos;
    private long accumulatorNs;
    private long tickCount;
    private long frameCount;
    private int lastTicks;
    private double lastAlpha;
    private long lastFrameNs;

    /**
     * Creates a loop.
     *
     * @param clock the monotonic clock
     * @param input the input queue drained once per tick
     * @param screens the screen stack ticked every tick
     * @param presenter the presenter called once per frame
     * @param limiter the frame limiter paced once per frame
     */
    public GameLoop(Clock clock, InputQueue input, ScreenManager screens, FramePresenter presenter,
            FrameLimiter limiter) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.input = Objects.requireNonNull(input, "input");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
    }

    /**
     * Primes the clock so the first frame credits no time. Called by {@link #run()}; optional
     * before driving {@link #frame()} manually (the first frame primes itself otherwise).
     */
    public void start() {
        previousNanos = clock.nanos();
        primed = true;
    }

    /** Runs frames until {@link #stop()} is called. */
    public void run() {
        running = true;
        start();
        while (running) {
            frame();
        }
    }

    /** Asks {@link #run()} to return after the current frame. Safe from any thread. */
    public void stop() {
        running = false;
    }

    /**
     * Whether {@link #run()} is active.
     *
     * @return {@code true} while running
     */
    public boolean isRunning() {
        return running;
    }

    /** Discards accumulated time (used when resuming after a pause so no ticks are replayed). */
    public void resetAccumulator() {
        accumulatorNs = 0;
        if (primed) {
            previousNanos = clock.nanos();
        }
    }

    /** Runs one frame: zero or more ticks, one present, one pace. */
    public void frame() {
        long now = clock.nanos();
        if (!primed) {
            previousNanos = now;
            primed = true;
        }
        long dt = now - previousNanos;
        previousNanos = now;
        if (dt < 0) {
            dt = 0;
        }
        lastFrameNs = dt;
        accumulatorNs += Math.min(dt, MAX_FRAME_NS);

        int ticks = 0;
        while (accumulatorNs >= Playfield.TICK_NS && ticks < MAX_TICKS_PER_FRAME) {
            screens.tick(input.nextTick());
            accumulatorNs -= Playfield.TICK_NS;
            ticks++;
        }
        if (ticks == MAX_TICKS_PER_FRAME) {
            accumulatorNs = 0;
        }
        if (screens.consumeAccumulatorReset()) {
            // A screen resumed from a pause: drop the time the loop banked while it was stopped
            // so the player does not get a burst of catch-up ticks (D2).
            accumulatorNs = 0;
        }
        tickCount += ticks;
        lastTicks = ticks;
        frameCount++;

        lastAlpha = (double) accumulatorNs / Playfield.TICK_NS;
        presenter.present(lastAlpha);
        limiter.pace();
    }

    /**
     * Total ticks run.
     *
     * @return the count
     */
    public long tickCount() {
        return tickCount;
    }

    /**
     * Total frames run.
     *
     * @return the count
     */
    public long frameCount() {
        return frameCount;
    }

    /**
     * Ticks run by the last frame.
     *
     * @return the count
     */
    public int lastTicks() {
        return lastTicks;
    }

    /**
     * Alpha passed to the presenter by the last frame.
     *
     * @return a value in {@code [0, 1)}
     */
    public double lastAlpha() {
        return lastAlpha;
    }

    /**
     * Wall time credited by the last frame before capping.
     *
     * @return nanoseconds
     */
    public long lastFrameNs() {
        return lastFrameNs;
    }

    /**
     * Time currently in the accumulator.
     *
     * @return nanoseconds
     */
    public long accumulatorNs() {
        return accumulatorNs;
    }
}
