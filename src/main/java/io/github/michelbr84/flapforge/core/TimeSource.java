package io.github.michelbr84.flapforge.core;

/**
 * The only wall clock visible to the pure packages (D23). Production injects
 * {@code app.SystemTimeSource}; tests inject a fixed source.
 */
@FunctionalInterface
public interface TimeSource {

    /**
     * Current time in milliseconds since the Unix epoch.
     *
     * @return epoch milliseconds
     */
    long epochMillis();
}
