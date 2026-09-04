package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.core.TimeSource;

/** {@link TimeSource} backed by {@link System#currentTimeMillis()} (D23). */
public final class SystemTimeSource implements TimeSource {

    @Override
    public long epochMillis() {
        return System.currentTimeMillis();
    }
}
