package io.github.michelbr84.flapforge.app;

/** {@link Clock} backed by {@link System#nanoTime()}. */
public final class SystemClock implements Clock {

    @Override
    public long nanos() {
        return System.nanoTime();
    }
}
