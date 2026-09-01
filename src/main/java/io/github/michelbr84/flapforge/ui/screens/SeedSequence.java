package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.core.MathUtil;

/**
 * Source of the run seeds a {@link GameScreen} plays (D12, D29).
 *
 * <ul>
 *   <li>With {@code --seed N} the sequence is {@code N, N + 1, N + 2, ...}: the first run is
 *       exactly the requested one and every instant retry is still reproducible, printed by the
 *       HUD and quotable in a bug report.</li>
 *   <li>Without it every run gets a fresh seed derived from the monotonic clock and a counter,
 *       folded through FNV-1a so consecutive launches do not start with neighbouring seeds.</li>
 * </ul>
 *
 * <p>The wall clock is read here, in the presentation layer, and never inside {@code gameplay}:
 * the simulation only ever sees the {@code long} this class produced, so a run replayed from a
 * recorded seed is bit-identical (D5, D12).
 */
public final class SeedSequence {

    private static final long MIX = MathUtil.fnv1a64("flapforge-seed");

    private final boolean explicit;
    private final long base;
    private long index;

    private SeedSequence(boolean explicit, long base) {
        this.explicit = explicit;
        this.base = base;
    }

    /**
     * A sequence of clock-derived seeds.
     *
     * @return the sequence
     */
    public static SeedSequence random() {
        return new SeedSequence(false, 0);
    }

    /**
     * The sequence {@code seed, seed + 1, seed + 2, ...}.
     *
     * @param seed the first seed
     * @return the sequence
     */
    public static SeedSequence of(long seed) {
        return new SeedSequence(true, seed);
    }

    /**
     * The sequence implied by {@code --seed}: explicit when a seed was given, clock-derived
     * otherwise.
     *
     * @param seed the parsed {@code --seed} value, or {@code null} when absent
     * @return the sequence
     */
    public static SeedSequence from(Long seed) {
        return seed == null ? random() : of(seed);
    }

    /**
     * Whether the seeds come from {@code --seed} (and are therefore worth showing in the HUD).
     *
     * @return {@code true} for an explicit sequence
     */
    public boolean isExplicit() {
        return explicit;
    }

    /**
     * How many seeds were handed out.
     *
     * @return the count
     */
    public long issued() {
        return index;
    }

    /**
     * The next seed.
     *
     * @return {@code base + n} for an explicit sequence, a clock-derived value otherwise
     */
    public long next() {
        long n = index++;
        if (explicit) {
            return base + n;
        }
        return MathUtil.fold(MathUtil.fold(MIX, System.nanoTime()), n);
    }
}
