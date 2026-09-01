package io.github.michelbr84.flapforge.gameplay.run;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of a run (D11): the configuration, a frozen copy of the stats and named counters for
 * achievements and statistics.
 *
 * @param config the run configuration
 * @param stats a snapshot of the stats
 * @param counters named counters ({@code gates}, {@code points}, {@code flaps},
 *     {@code flapsRefused}, {@code ticks}, {@code nearMisses}, {@code obstaclesSpawned} ...)
 */
public record RunResult(RunConfig config, RunStats stats, Map<String, Long> counters) {

    /**
     * Copies the counters into a deterministic, unmodifiable map.
     *
     * @param config the run configuration
     * @param stats the stats snapshot
     * @param counters the counters
     */
    public RunResult {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(stats, "stats");
        counters = Collections.unmodifiableMap(new LinkedHashMap<>(counters));
    }

    /**
     * Reads a counter.
     *
     * @param name the counter name
     * @return the value, 0 when absent
     */
    public long counter(String name) {
        Long v = counters.get(name);
        return v == null ? 0 : v;
    }

    /**
     * Gates passed (shortcut).
     *
     * @return the count
     */
    public int gatesPassed() {
        return stats.gatesPassed();
    }
}
