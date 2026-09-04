package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.core.MathUtil;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * Paces the game loop to a target frame rate (D1, E30.f).
 *
 * <p>{@link #pace()} blocks until the next deadline minus a self-calibrated overshoot margin and
 * then spins for the remainder. {@link #calibrate()} measures, at startup, how far each
 * candidate {@link Waiter} overshoots a 1 ms wait and picks the first one that stays within
 * {@value #ACCEPTABLE_OVERSHOOT_NS} ns (with 25 % headroom): {@code LockSupport.parkNanos} is
 * accurate to well under a millisecond on Linux and macOS, but on Windows it inherits the
 * default 15.6 ms timer period, in which case the limiter switches to 1 ms
 * {@code Thread.sleep} chunks (for which HotSpot raises the timer resolution). Whatever is
 * chosen, the measured overshoot becomes the margin; if even the best candidate overshoots by
 * more than {@value #MAX_MARGIN_NS} ns the margin is capped there and the loop spins the rest,
 * keeping the frame rate at the cost of CPU. Targets are clamped to {@code [30, 240]}; a target
 * of {@code 0} disables pacing.
 */
public final class FrameLimiter {

    /** Lowest accepted target. */
    public static final int MIN_FPS = 30;
    /** Highest accepted target. */
    public static final int MAX_FPS = 240;
    /** Default target when nothing is configured or the refresh rate is unknown. */
    public static final int DEFAULT_FPS = 60;
    /** Target value meaning "no pacing". */
    public static final int UNCAPPED = 0;

    /** Smallest margin ever used. */
    public static final long MIN_MARGIN_NS = 100_000L;
    /** Largest margin ever used; beyond it the loop spins rather than dropping frames. */
    public static final long MAX_MARGIN_NS = 20_000_000L;
    /** Worst overshoot (plus headroom) a waiter may show to be selected without trying the next. */
    public static final long ACCEPTABLE_OVERSHOOT_NS = 2_000_000L;

    private static final long DEFAULT_MARGIN_NS = 1_000_000L;
    private static final long CALIBRATION_WAIT_NS = 1_000_000L;
    private static final int CALIBRATION_SAMPLES = 8;

    /**
     * A blocking wait primitive. {@link #waitNanos(long)} may return early or late; the limiter
     * measures the lateness and spins the remainder.
     */
    public interface Waiter {

        /**
         * Shortest wait worth requesting; shorter remainders are spun instead.
         *
         * @return nanoseconds, positive
         */
        long minimumNs();

        /**
         * Blocks for about the given time.
         *
         * @param nanos the requested wait
         * @throws InterruptedException when the thread is interrupted
         */
        void waitNanos(long nanos) throws InterruptedException;

        /**
         * Short name for diagnostics.
         *
         * @return the name
         */
        String name();
    }

    /** {@code LockSupport.parkNanos}: sub-millisecond on Linux/macOS, 15.6 ms on Windows. */
    public static final Waiter PARK = new Waiter() {
        @Override
        public long minimumNs() {
            return 1L;
        }

        @Override
        public void waitNanos(long nanos) {
            LockSupport.parkNanos(nanos);
        }

        @Override
        public String name() {
            return "park";
        }
    };

    /**
     * {@code Thread.sleep(1)} chunks: HotSpot on Windows raises the timer resolution to 1 ms
     * around sleeps that are not a multiple of 10 ms.
     */
    public static final Waiter SLEEP_MILLIS = new Waiter() {
        @Override
        public long minimumNs() {
            return 1_000_000L;
        }

        @Override
        public void waitNanos(long nanos) throws InterruptedException {
            Thread.sleep(1L);
        }

        @Override
        public String name() {
            return "sleep";
        }
    };

    private final Clock clock;
    private final List<Waiter> candidates;
    private Waiter waiter;
    private int targetFps;
    private long periodNs;
    private long nextDeadline = -1;
    private long marginNs = DEFAULT_MARGIN_NS;

    /**
     * Creates a limiter with the production wait primitives ({@link #PARK}, then
     * {@link #SLEEP_MILLIS}).
     *
     * @param clock the monotonic clock
     * @param targetFps the target frame rate ({@link #UNCAPPED} disables pacing)
     */
    public FrameLimiter(Clock clock, int targetFps) {
        this(clock, targetFps, List.of(PARK, SLEEP_MILLIS));
    }

    /**
     * Creates a limiter choosing among the given wait primitives, in order of preference.
     *
     * @param clock the monotonic clock
     * @param targetFps the target frame rate ({@link #UNCAPPED} disables pacing)
     * @param candidates the wait primitives {@link #calibrate()} may pick from (non-empty)
     */
    public FrameLimiter(Clock clock, int targetFps, List<Waiter> candidates) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.candidates = List.copyOf(candidates);
        if (this.candidates.isEmpty()) {
            throw new IllegalArgumentException("at least one waiter is required");
        }
        this.waiter = this.candidates.get(0);
        setTargetFps(targetFps);
    }

    /**
     * A limiter that never waits (tests, headless runs).
     *
     * @param clock the clock
     * @return the limiter
     */
    public static FrameLimiter uncapped(Clock clock) {
        return new FrameLimiter(clock, UNCAPPED);
    }

    /**
     * Clamps a requested target into the accepted range.
     *
     * @param fps the requested target
     * @return the clamped value
     */
    public static int clampFps(int fps) {
        return MathUtil.clamp(fps, MIN_FPS, MAX_FPS);
    }

    /**
     * Resolves the "match refresh rate" option.
     *
     * @param reportedHz the refresh rate reported by the display, {@code 0} or negative when
     *     unknown
     * @return the clamped rate, or {@link #DEFAULT_FPS} when unknown
     */
    public static int refreshRateOrDefault(int reportedHz) {
        return reportedHz <= 0 ? DEFAULT_FPS : clampFps(reportedHz);
    }

    /**
     * Changes the target frame rate.
     *
     * @param fps the target ({@link #UNCAPPED} disables pacing; other values are clamped)
     */
    public void setTargetFps(int fps) {
        if (fps <= UNCAPPED) {
            targetFps = UNCAPPED;
            periodNs = 0;
        } else {
            targetFps = clampFps(fps);
            periodNs = 1_000_000_000L / targetFps;
        }
        nextDeadline = -1;
    }

    /**
     * Current target.
     *
     * @return frames per second, or {@link #UNCAPPED}
     */
    public int targetFps() {
        return targetFps;
    }

    /**
     * Overshoot margin subtracted from the wait deadline.
     *
     * @return nanoseconds
     */
    public long marginNs() {
        return marginNs;
    }

    /**
     * The wait primitive in use (the first candidate until {@link #calibrate()} runs).
     *
     * @return the waiter
     */
    public Waiter waiter() {
        return waiter;
    }

    /**
     * Measures how far each candidate wait primitive overshoots on this machine, selects the
     * first acceptable one (or the least bad) and stores its overshoot plus 25 % as the margin.
     * Takes a few milliseconds on Linux and about 150 ms on Windows.
     */
    public void calibrate() {
        Waiter best = null;
        long bestOvershoot = Long.MAX_VALUE;
        for (Waiter candidate : candidates) {
            long overshoot = measureOvershoot(candidate);
            long withHeadroom = overshoot + overshoot / 4;
            if (withHeadroom <= ACCEPTABLE_OVERSHOOT_NS) {
                best = candidate;
                bestOvershoot = overshoot;
                break;
            }
            if (overshoot < bestOvershoot) {
                best = candidate;
                bestOvershoot = overshoot;
            }
        }
        waiter = best;
        marginNs = MathUtil.clamp(bestOvershoot + bestOvershoot / 4, MIN_MARGIN_NS, MAX_MARGIN_NS);
    }

    private long measureOvershoot(Waiter candidate) {
        long worst = 0;
        for (int i = 0; i < CALIBRATION_SAMPLES; i++) {
            long start = clock.nanos();
            try {
                candidate.waitNanos(CALIBRATION_WAIT_NS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Long.MAX_VALUE / 2;
            }
            long overshoot = clock.nanos() - start - CALIBRATION_WAIT_NS;
            if (overshoot > worst) {
                worst = overshoot;
            }
        }
        return worst;
    }

    /** Waits until the next frame deadline, then schedules the following one. */
    public void pace() {
        if (targetFps == UNCAPPED) {
            return;
        }
        long now = clock.nanos();
        if (nextDeadline < 0) {
            nextDeadline = now + periodNs;
            return;
        }
        long deadline = nextDeadline;
        long waitUntil = deadline - marginNs;
        long remaining = waitUntil - now;
        long minimum = Math.max(1L, waiter.minimumNs());
        while (remaining >= minimum) {
            try {
                waiter.waitNanos(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            remaining = waitUntil - clock.nanos();
        }
        while (clock.nanos() < deadline) {
            Thread.onSpinWait();
        }
        now = clock.nanos();
        nextDeadline = deadline + periodNs;
        if (nextDeadline < now) {
            nextDeadline = now + periodNs;
        }
    }
}
