package io.github.michelbr84.flapforge.app;

/**
 * Monotonic nanosecond clock used by the game loop and the frame limiter (D1).
 *
 * <p>Production uses {@link SystemClock}; tests drive the loop with a manual clock.
 */
@FunctionalInterface
public interface Clock {

    /**
     * Monotonic time in nanoseconds; only differences are meaningful.
     *
     * @return nanoseconds
     */
    long nanos();
}
