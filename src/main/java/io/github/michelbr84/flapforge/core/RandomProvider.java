package io.github.michelbr84.flapforge.core;

import java.util.Random;

/**
 * Deterministic per-purpose random streams derived from a single run seed (D12).
 *
 * <p>Each named stream is {@code new Random(mix64(seed ^ fnv1a64(name)))}. Because
 * {@link java.util.Random} specifies its generator exactly, sequences are identical on every
 * platform. Rendering-only randomness (particles, clouds) must never use these streams.
 *
 * <p>The {@link #mix64(long) SplitMix64 finaliser} is not cosmetic. {@link java.util.Random}
 * scrambles its seed with {@code (s ^ 0x5DEECE66D) & ((1L << 48) - 1)} and nothing else, so two
 * seeds that differ only in their low bits produce first outputs that are almost a linear
 * function of the seed: over the 2000 consecutive seeds {@code 42..2041} the first
 * {@code nextDouble()} of the unmixed {@code spawn} stream fell inside {@code [0.31, 0.50)} every
 * single time, which pinned the first gate's {@code moving} roll to {@code false} for all of
 * them. Seeds <em>are</em> consecutive in practice — an instant retry walks {@code N, N+1,
 * N+2 …} and the balancing tool sweeps {@code seed0 + i} — so the first decision of every run
 * would carry that bias. Running the composed seed through a bijection removes it without
 * costing determinism.
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
        return new Random(streamSeed(name));
    }

    /**
     * The seed handed to {@link Random} for a named stream.
     *
     * @param name the stream name
     * @return the mixed seed
     */
    public long streamSeed(String name) {
        return mix64(seed ^ MathUtil.fnv1a64(name));
    }

    /**
     * SplitMix64 finaliser: a bijection on 64 bits that spreads a small change in the input over
     * the whole output. Multiplication and shifts only, so it is bit-exact on every platform.
     *
     * @param value the value to mix
     * @return the mixed value
     */
    public static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
