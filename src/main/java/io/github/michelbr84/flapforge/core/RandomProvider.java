package io.github.michelbr84.flapforge.core;

import java.util.Random;

/**
 * Deterministic per-purpose random streams derived from a single run seed (D12).
 *
 * <p>Each named stream is {@code new Random(seed ^ fnv1a64(name))}. Because
 * {@link java.util.Random} specifies its generator exactly, sequences are identical on every
 * platform. Rendering-only randomness (particles, clouds) must never use these streams.
 */
public final class RandomProvider {

    /** Stream used by the obstacle spawner for kind/moving decisions. */
    public static final String SPAWN = "spawn";
    /** Stream used by individual obstacles for their layout parameters. */
    public static final String OBSTACLE = "obstacle";
    /** Stream used for coin placement. */
    public static final String COINS = "coins";
    /** Stream used for modifier offers. */
    public static final String OFFERS = "offers";
    /** Stream used by the pattern streamer. */
    public static final String PATTERNS = "patterns";
    /** Stream used by rule cycles. */
    public static final String CYCLES = "cycles";
    /** Stream used by the daily challenge picker. */
    public static final String DAILY = "daily";

    private final long seed;

    /**
     * Creates a provider for the given seed.
     *
     * @param seed the run seed
     */
    public RandomProvider(long seed) {
        this.seed = seed;
    }

    /**
     * Returns the run seed.
     *
     * @return the seed
     */
    public long seed() {
        return seed;
    }

    /**
     * Creates a fresh generator for the named stream. Calling this twice with the same name
     * yields two generators producing the same sequence.
     *
     * @param name the stream name (one of the constants of this class or any stable string)
     * @return a new generator
     */
    public Random stream(String name) {
        return new Random(seed ^ MathUtil.fnv1a64(name));
    }
}
