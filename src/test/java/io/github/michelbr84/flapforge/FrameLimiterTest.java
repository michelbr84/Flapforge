package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.Clock;
import io.github.michelbr84.flapforge.app.FrameLimiter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link FrameLimiter} calibration and pacing against a simulated clock (D1, E30.f): the
 * 15.6 ms Windows timer period must not drag a 60 fps target down to 36 fps.
 */
class FrameLimiterTest {

    private static final long MS = 1_000_000L;
    private static final long US = 1_000L;

    /** Clock that creeps forward on every read so spin loops terminate. */
    static final class CreepingClock implements Clock {
        long nanos;
        final long creepNs;

        CreepingClock(long creepNs) {
            this.creepNs = creepNs;
        }

        @Override
        public long nanos() {
            nanos += creepNs;
            return nanos;
        }
    }

    /**
     * Wait primitive that advances the clock by the request (or by a fixed chunk, like
     * {@code Thread.sleep(1)}) plus a fixed overshoot.
     */
    static final class FakeWaiter implements FrameLimiter.Waiter {
        final CreepingClock clock;
        final String name;
        final long minimumNs;
        final long chunkNs;
        final long overshootNs;
        int calls;

        FakeWaiter(CreepingClock clock, String name, long minimumNs, long chunkNs, long overshootNs) {
            this.clock = clock;
            this.name = name;
            this.minimumNs = minimumNs;
            this.chunkNs = chunkNs;
            this.overshootNs = overshootNs;
        }

        @Override
        public long minimumNs() {
            return minimumNs;
        }

        @Override
        public void waitNanos(long nanos) {
            calls++;
            clock.nanos += (chunkNs > 0 ? chunkNs : nanos) + overshootNs;
        }

        @Override
        public String name() {
            return name;
        }
    }

    @Test
    void refreshRateFallsBackToSixtyAndIsClamped() {
        assertEquals(60, FrameLimiter.refreshRateOrDefault(0));
        assertEquals(60, FrameLimiter.refreshRateOrDefault(-1));
        assertEquals(60, FrameLimiter.refreshRateOrDefault(60));
        assertEquals(144, FrameLimiter.refreshRateOrDefault(144));
        assertEquals(240, FrameLimiter.refreshRateOrDefault(300));
        assertEquals(30, FrameLimiter.clampFps(10));
        assertEquals(240, FrameLimiter.clampFps(1_000));
        assertEquals(75, FrameLimiter.clampFps(75));
    }

    @Test
    void targetIsClampedAndZeroMeansUncapped() {
        CreepingClock clock = new CreepingClock(10 * US);
        FrameLimiter limiter = new FrameLimiter(clock, 500);
        assertEquals(240, limiter.targetFps());
        limiter.setTargetFps(-5);
        assertEquals(FrameLimiter.UNCAPPED, limiter.targetFps());
        long before = clock.nanos;
        limiter.pace();
        assertTrue(clock.nanos - before <= 20 * US, "uncapped pace does not wait");
    }

    @Test
    void accurateParkIsKeptWithASmallMargin() {
        CreepingClock clock = new CreepingClock(10 * US);
        FakeWaiter park = new FakeWaiter(clock, "park", 1, 0, 60 * US);
        FakeWaiter sleep = new FakeWaiter(clock, "sleep", 1 * MS, 1 * MS, 300 * US);
        FrameLimiter limiter = new FrameLimiter(clock, 60, List.of(park, sleep));
        limiter.calibrate();
        assertSame(park, limiter.waiter());
        assertEquals(0, sleep.calls, "the second candidate is not even tried");
        assertTrue(limiter.marginNs() >= FrameLimiter.MIN_MARGIN_NS);
        assertTrue(limiter.marginNs() < 200 * US, "margin ~ 1.25 x overshoot: " + limiter.marginNs());
    }

    @Test
    void fifteenMillisecondParkOvershootSwitchesToSleepChunksAndHoldsSixtyFps() {
        CreepingClock clock = new CreepingClock(10 * US);
        FakeWaiter park = new FakeWaiter(clock, "park", 1, 0, 15 * MS);
        FakeWaiter sleep = new FakeWaiter(clock, "sleep", 1 * MS, 1 * MS, 300 * US);
        FrameLimiter limiter = new FrameLimiter(clock, 60, List.of(park, sleep));
        limiter.calibrate();
        assertSame(sleep, limiter.waiter(), "park overshoots too much; sleep chunks are used");
        assertTrue(limiter.marginNs() < 1 * MS, "margin follows the sleep overshoot: "
                + limiter.marginNs());

        long period = 1_000_000_000L / 60;
        limiter.pace();
        long last = clock.nanos;
        long worst = 0;
        long total = 0;
        int frames = 120;
        for (int i = 0; i < frames; i++) {
            clock.nanos += 2 * MS; // simulated tick + render work
            limiter.pace();
            long dt = clock.nanos - last;
            last = clock.nanos;
            total += dt;
            worst = Math.max(worst, Math.abs(dt - period));
        }
        long mean = total / frames;
        assertTrue(Math.abs(mean - period) <= 1 * MS, "mean period " + mean + " ns vs " + period);
        assertTrue(worst <= 1 * MS, "worst deviation " + worst + " ns");
    }

    @Test
    void leastBadWaiterIsChosenWhenNoneIsAcceptableAndTheMarginIsCapped() {
        CreepingClock clock = new CreepingClock(10 * US);
        FakeWaiter park = new FakeWaiter(clock, "park", 1, 0, 25 * MS);
        FakeWaiter sleep = new FakeWaiter(clock, "sleep", 1 * MS, 1 * MS, 30 * MS);
        FrameLimiter limiter = new FrameLimiter(clock, 60, List.of(park, sleep));
        limiter.calibrate();
        assertSame(park, limiter.waiter(), "the least bad candidate");
        assertEquals(FrameLimiter.MAX_MARGIN_NS, limiter.marginNs(), "25 ms x 1.25 is capped");
        long period = 1_000_000_000L / 60;
        limiter.pace();
        long last = clock.nanos;
        for (int i = 0; i < 30; i++) {
            clock.nanos += 2 * MS;
            limiter.pace();
            long dt = clock.nanos - last;
            last = clock.nanos;
            assertTrue(Math.abs(dt - period) <= 1 * MS, "frame " + i + " period " + dt);
        }
    }
}
