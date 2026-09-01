package io.github.michelbr84.flapforge.content.defs;

import java.util.List;
import java.util.Objects;

/**
 * When an achievement fires (§4): {@code {counter, scope, op, value}}.
 *
 * <p>The counter name is resolved against the scope by {@code AchievementEvaluator} (M8) and is
 * checked by the validator now: {@link CounterScope#LIFETIME} names a {@code StatisticKey} field
 * (or {@code <mapField>.<key>}, or a profile-root scalar per E5), {@link CounterScope#RUN} names
 * one of {@link #RUN_COUNTERS} and {@link CounterScope#COLLECTION} names
 * {@code collection.<category>.percent} for a category in {@link #COLLECTION_CATEGORIES}.
 *
 * @param counter the counter name
 * @param scope where the counter lives
 * @param op how it is compared
 * @param value the threshold
 */
public record AchievementConditionDef(String counter, CounterScope scope, CompareOp op,
        double value) {

    /** Prefix of a {@link CounterScope#RUN} counter. */
    public static final String RUN_PREFIX = "run.";
    /** Prefix of a {@link CounterScope#COLLECTION} counter. */
    public static final String COLLECTION_PREFIX = "collection.";
    /** Suffix of a {@link CounterScope#COLLECTION} counter. */
    public static final String COLLECTION_SUFFIX = ".percent";

    /** The run values an achievement may read (§4). */
    public static final List<String> RUN_COUNTERS = List.of("run.gatesPassed", "run.points",
            "run.streakBest", "run.coinsCollected");

    /**
     * The collection categories (D13). {@code all} is every category at once, which is what a
     * completionist achievement asks for.
     */
    public static final List<String> COLLECTION_CATEGORIES = List.of("birds", "abilities",
            "worlds", "challenges", "cosmetics", "achievements", "upgrades", "all");

    /**
     * Checks the required fields.
     *
     * @throws NullPointerException when the counter, the scope or the operator is missing
     */
    public AchievementConditionDef {
        Objects.requireNonNull(counter, "counter");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(op, "op");
    }

    /**
     * The collection category a {@link CounterScope#COLLECTION} counter addresses.
     *
     * @return the category, or {@code null} when the counter is not shaped like one
     */
    public String collectionCategory() {
        if (!counter.startsWith(COLLECTION_PREFIX) || !counter.endsWith(COLLECTION_SUFFIX)) {
            return null;
        }
        String middle = counter.substring(COLLECTION_PREFIX.length(),
                counter.length() - COLLECTION_SUFFIX.length());
        return middle.isEmpty() ? null : middle;
    }
}
