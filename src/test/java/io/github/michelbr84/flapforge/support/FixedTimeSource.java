package io.github.michelbr84.flapforge.support;

import io.github.michelbr84.flapforge.core.TimeSource;

/** {@link TimeSource} returning a value tests control. */
public final class FixedTimeSource implements TimeSource {

    private long epochMillis;

    /**
     * Creates the source.
     *
     * @param epochMillis the initial reading
     */
    public FixedTimeSource(long epochMillis) {
        this.epochMillis = epochMillis;
    }

    @Override
    public long epochMillis() {
        return epochMillis;
    }

    /**
     * Sets the reading.
     *
     * @param epochMillis the new reading
     */
    public void set(long epochMillis) {
        this.epochMillis = epochMillis;
    }

    /**
     * Moves the reading forward.
     *
     * @param deltaMillis milliseconds to add
     */
    public void advance(long deltaMillis) {
        this.epochMillis += deltaMillis;
    }
}
