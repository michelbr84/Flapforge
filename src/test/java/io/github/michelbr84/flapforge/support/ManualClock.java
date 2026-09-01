package io.github.michelbr84.flapforge.support;

import io.github.michelbr84.flapforge.app.Clock;

/** {@link Clock} that advances only when a test says so. */
public final class ManualClock implements Clock {

    private long nanos;

    /** Creates a clock at zero. */
    public ManualClock() {
        this(0);
    }

    /**
     * Creates a clock at the given time.
     *
     * @param startNanos the initial reading
     */
    public ManualClock(long startNanos) {
        this.nanos = startNanos;
    }

    @Override
    public long nanos() {
        return nanos;
    }

    /**
     * Moves the clock forward.
     *
     * @param deltaNanos nanoseconds to add
     */
    public void advance(long deltaNanos) {
        nanos += deltaNanos;
    }

    /**
     * Moves the clock forward by milliseconds.
     *
     * @param millis milliseconds to add
     */
    public void advanceMillis(long millis) {
        nanos += millis * 1_000_000L;
    }

    /**
     * Sets the reading.
     *
     * @param nanos the new reading
     */
    public void set(long nanos) {
        this.nanos = nanos;
    }
}
