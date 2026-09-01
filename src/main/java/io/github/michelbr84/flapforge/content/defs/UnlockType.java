package io.github.michelbr84.flapforge.content.defs;

/**
 * Kinds of unlock condition (D13, E20). JSON writes them lower case ({@code "any_of"},
 * {@code "world_cleared"}); {@link io.github.michelbr84.flapforge.content.StrictBinder} maps the
 * string case-insensitively.
 *
 * <p>M1 only binds and validates the enum; evaluation lives in {@code progression} from M4.
 */
public enum UnlockType {

    /** Owned from the first launch. */
    DEFAULT,
    /** Best gates in a single run (skill). */
    BEST_GATES,
    /** Best points in a single run (skill, E1). */
    BEST_POINTS,
    /** Gates passed across all runs (cumulative). */
    TOTAL_GATES,
    /** Runs finished (cumulative). */
    RUNS,
    /** Player level. */
    LEVEL,
    /** Coins earned across all runs (cumulative). */
    COINS_EARNED_TOTAL,
    /** A named challenge was completed. */
    CHALLENGE,
    /** A named achievement was unlocked. */
    ACHIEVEMENT,
    /** A world boss was cleared. */
    WORLD_CLEARED,
    /** Bought in the shop for {@code amount} coins. */
    PURCHASE,
    /** Every nested condition holds. */
    ALL_OF,
    /** At least one nested condition holds. */
    ANY_OF,
    /** Prestige count at least {@code value} (cosmetics only, E20). */
    PRESTIGE,
    /** A named counter reached {@code value} (cosmetics only, E20). */
    COUNTER
}
