package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.UnlockGraph;
import io.github.michelbr84.flapforge.content.defs.AchievementConditionDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Decides whether a player has earned an unlockable (D13, E20, E23).
 *
 * <p>It answers two questions. {@link #isSatisfied(UnlockConditionDef, PlayerProfile)} evaluates
 * one condition tree against one profile; {@link #evaluate(PlayerProfile)} walks every unlockable
 * the content ships and lists the ids the profile now satisfies but does not own yet. The second
 * is the {@link ProgressionManager.UnlockHook} of D14 — the class implements the interface — so
 * the unlock step of a finished run and of a purchase is this evaluator and nothing else.
 *
 * <p>The table of "id → condition" is not built here: it is {@link UnlockGraph}'s node list, which
 * is the same table the validator proves is a reachable DAG. Building it once per evaluator keeps
 * one definition of what an unlockable is.
 *
 * <p>Three rules are worth stating because they are not visible in the JSON:
 * <ul>
 *   <li><b>{@code purchase} is never satisfied.</b> It means "buyable for N coins", not "earned":
 *       {@link UnlockManager} is the only path that grants it (D13). An {@code any_of} of a skill
 *       condition and a price is therefore earned by the skill branch alone.</li>
 *   <li><b>Cumulative conditions read "since prestige" (E23).</b> {@code runs},
 *       {@code total_gates} and {@code coins_earned_total} subtract
 *       {@link PlayerProfile#prestigeBaseline}, {@code world_cleared} ignores a boss cleared
 *       before the prestige, and {@code level} is the current (reset) level. The skill conditions
 *       ({@code best_gates}, {@code best_points}) and {@code achievement} read lifetime values,
 *       because achievements and statistics survive a prestige.</li>
 *   <li><b>A palette needs its bird.</b> Every bird's default palette carries
 *       {@code {"type": "default"}}, which on its own would hand a fresh profile the default
 *       palette of all seven birds; a {@code cosmetic:&lt;bird&gt;:&lt;palette&gt;} is therefore
 *       granted only once {@code bird:&lt;bird&gt;} is owned, which is exactly E18's default
 *       set.</li>
 * </ul>
 *
 * <p>{@link #evaluate(PlayerProfile)} iterates to a fixed point: a collection counter (E20's
 * {@code counter} type) reads how much of a category is owned, so one grant can satisfy the next.
 * The profile is never modified — the caller decides what to do with the list, which is what makes
 * the evaluator safe to call from a screen that only wants to preview.
 */
public final class UnlockEvaluator implements ProgressionManager.UnlockHook {

    /** How many times {@link #evaluate(PlayerProfile)} re-runs while grants keep appearing. */
    public static final int MAX_PASSES = 8;

    /** Suffix of the percentage a collection counter reads. */
    private static final String PERCENT = AchievementConditionDef.COLLECTION_SUFFIX;
    /** Prefix of a collection counter. */
    private static final String COLLECTION = AchievementConditionDef.COLLECTION_PREFIX;
    private final Map<String, UnlockConditionDef> conditions;
    private final Map<String, ContentKind> kinds;
    private final CollectionProgress collections;

    /**
     * Creates an evaluator over a content set.
     *
     * @param content the loaded content
     */
    public UnlockEvaluator(GameContent content) {
        Objects.requireNonNull(content, "content");
        Map<String, UnlockConditionDef> table = new LinkedHashMap<>();
        Map<String, ContentKind> kindTable = new LinkedHashMap<>();
        for (UnlockGraph.Node node : UnlockGraph.of(content).nodes().values()) {
            if (!node.unlockable() || node.condition() == null) {
                continue;
            }
            table.put(node.id(), node.condition());
            if (node.kind() != null) {
                kindTable.put(node.id(), node.kind());
            }
        }
        this.conditions = Collections.unmodifiableMap(table);
        this.kinds = Collections.unmodifiableMap(kindTable);
        this.collections = new CollectionProgress(content, kindTable);
    }

    /**
     * Creates an evaluator over a content set.
     *
     * @param content the loaded content
     * @return the evaluator
     */
    public static UnlockEvaluator of(GameContent content) {
        return new UnlockEvaluator(content);
    }

    /**
     * Every unlockable id a profile now satisfies and does not own yet (D14's unlock step).
     *
     * @param profile the profile to read
     * @param content the loaded content
     * @return the ids, in content order
     */
    public static List<String> evaluateAll(PlayerProfile profile, GameContent content) {
        return new UnlockEvaluator(content).evaluate(profile);
    }

    /**
     * The condition of every unlockable the content ships, in content order.
     *
     * @return an unmodifiable map of namespaced id to condition
     */
    public Map<String, UnlockConditionDef> conditions() {
        return conditions;
    }

    /**
     * The condition that opens one unlockable.
     *
     * @param unlockId the namespaced id
     * @return the condition, or {@code null} when no unlockable carries that id
     */
    public UnlockConditionDef conditionOf(String unlockId) {
        return conditions.get(unlockId);
    }

    /**
     * The kind an unlockable belongs to.
     *
     * @param unlockId the namespaced id
     * @return the kind, or {@code null} when the id is unknown
     */
    public ContentKind kindOf(String unlockId) {
        return kinds.get(unlockId);
    }

    /**
     * The shop price of an unlockable: the cheapest {@code purchase} branch of its condition tree
     * (D13 "Shop = purchase-type unlocks").
     *
     * @param unlockId the namespaced id
     * @return the price in coins, or {@code -1} when the id is unknown or is not for sale
     */
    public long priceOf(String unlockId) {
        return priceOf(conditions.get(unlockId));
    }

    /**
     * The cheapest {@code purchase} branch of a condition tree.
     *
     * <p>Only a {@code purchase} at the root or under an {@code any_of} is a price. Under an
     * {@code all_of} it is one requirement among several, and selling the unlockable for it would
     * hand over something its siblings still gate; the validator refuses that shape, and this
     * refuses to price it either.
     *
     * @param condition the condition, may be {@code null}
     * @return the price in coins, or {@code -1} when the tree has no {@code purchase} branch
     */
    public static long priceOf(UnlockConditionDef condition) {
        if (condition == null) {
            return -1;
        }
        if (condition.type() == UnlockType.PURCHASE) {
            return (long) condition.amount();
        }
        if (condition.type() != UnlockType.ANY_OF) {
            return -1;
        }
        long best = -1;
        for (UnlockConditionDef child : condition.conditions()) {
            long price = priceOf(child);
            if (price >= 0 && (best < 0 || price < best)) {
                best = price;
            }
        }
        return best;
    }

    @Override
    public List<String> evaluate(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        Set<String> owned = new LinkedHashSet<>(profile.unlocked);
        List<String> granted = new ArrayList<>();
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            List<String> round = new ArrayList<>();
            for (Map.Entry<String, UnlockConditionDef> entry : conditions.entrySet()) {
                String id = entry.getKey();
                if (owned.contains(id) || !requirementsOwned(id, owned)) {
                    continue;
                }
                if (isSatisfied(entry.getValue(), profile, owned)) {
                    round.add(id);
                }
            }
            if (round.isEmpty()) {
                break;
            }
            owned.addAll(round);
            granted.addAll(round);
        }
        return Collections.unmodifiableList(granted);
    }

    /**
     * Whether everything an unlockable implies is owned already.
     *
     * <p>The one rule is the cosmetic rule of the class comment: a palette belongs to a bird, and
     * a bird nobody owns has no palettes to grant.
     *
     * @param unlockId the namespaced id
     * @param owned the unlock ids the profile holds
     * @return {@code true} when the id may be granted
     */
    private boolean requirementsOwned(String unlockId, Set<String> owned) {
        if (kinds.get(unlockId) != ContentKind.COSMETIC) {
            return true;
        }
        int split = unlockId.indexOf(':', BirdDef.COSMETIC_NAMESPACE.length());
        if (split < 0) {
            return true;
        }
        String birdId = unlockId.substring(BirdDef.COSMETIC_NAMESPACE.length(), split);
        return owned.contains(BirdDef.NAMESPACE + birdId);
    }

    /**
     * Whether a profile satisfies a condition tree right now.
     *
     * @param condition the condition; {@code null} is never satisfied
     * @param profile the profile to read
     * @return {@code true} when the condition holds
     */
    public boolean isSatisfied(UnlockConditionDef condition, PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return isSatisfied(condition, profile, new LinkedHashSet<>(profile.unlocked));
    }

    private boolean isSatisfied(UnlockConditionDef condition, PlayerProfile profile,
            Set<String> owned) {
        if (condition == null) {
            return false;
        }
        switch (condition.type()) {
            case DEFAULT:
                return true;
            case BEST_GATES:
                return profile.statistics.bestGates >= condition.value();
            case BEST_POINTS:
                return profile.statistics.bestPoints >= condition.value();
            case TOTAL_GATES:
                return sincePrestige(profile.statistics.totalGates,
                        profile.prestigeBaseline.totalGates) >= condition.value();
            case RUNS:
                return sincePrestige(profile.statistics.totalRuns,
                        profile.prestigeBaseline.totalRuns) >= condition.value();
            case LEVEL:
                return profile.level >= condition.value();
            case COINS_EARNED_TOTAL:
                return sincePrestige(profile.statistics.coinsEarned,
                        profile.prestigeBaseline.coinsEarned) >= condition.value();
            case CHALLENGE:
                return isChallengeCompleted(profile, condition.id());
            case ACHIEVEMENT:
                return condition.id() != null && profile.achievements.containsKey(condition.id());
            case WORLD_CLEARED:
                return isWorldCleared(profile, condition.id());
            case PURCHASE:
                // "Buyable for N coins", never "earned": UnlockManager.purchase is the only path.
                return false;
            case PRESTIGE:
                return profile.prestigeCount >= condition.value();
            case COUNTER:
                return counter(condition.counter(), profile, owned) >= condition.value();
            case ALL_OF: {
                // An empty all_of is vacuously true; the validator rejects one so it cannot ship.
                for (UnlockConditionDef child : condition.conditions()) {
                    if (!isSatisfied(child, profile, owned)) {
                        return false;
                    }
                }
                return true;
            }
            case ANY_OF:
            default: {
                for (UnlockConditionDef child : condition.conditions()) {
                    if (isSatisfied(child, profile, owned)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    /**
     * A cumulative total counted since the last prestige (E23).
     *
     * @param lifetime the lifetime total
     * @param baseline the total frozen by the last prestige
     * @return the difference, never negative
     */
    private static long sincePrestige(long lifetime, long baseline) {
        return Math.max(0, lifetime - baseline);
    }

    private static boolean isChallengeCompleted(PlayerProfile profile, String challengeId) {
        if (challengeId == null) {
            return false;
        }
        PlayerProfile.ChallengeRecord record = profile.challenges.get(challengeId);
        return record != null && record.completed;
    }

    /**
     * Whether a world boss has been cleared since the last prestige (E23).
     *
     * @param profile the profile
     * @param worldId the world id
     * @return {@code true} when the world is in {@code statistics.bossesCleared} and was not
     *     already there when the player prestiged
     */
    private static boolean isWorldCleared(PlayerProfile profile, String worldId) {
        return worldId != null && profile.statistics.bossesCleared.contains(worldId)
                && !profile.prestigeBaseline.bossesCleared.contains(worldId);
    }

    /**
     * Resolves an E20 counter: a collection percentage, or any counter the achievement evaluator
     * knows (a {@code StatisticKey} field, a map entry of one, or a profile-root scalar, E5).
     *
     * @param name the counter name
     * @param profile the profile to read
     * @return the value, 0 when the name resolves to nothing
     */
    public long counter(String name, PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return counter(name, profile, new LinkedHashSet<>(profile.unlocked));
    }

    private long counter(String name, PlayerProfile profile, Set<String> owned) {
        if (name == null || name.isBlank()) {
            return 0;
        }
        if (name.startsWith(COLLECTION)) {
            return collectionPercent(category(name), profile, owned);
        }
        return Statistics.resolve(profile, name);
    }

    private static String category(String counter) {
        if (!counter.endsWith(PERCENT)) {
            return null;
        }
        String middle = counter.substring(COLLECTION.length(), counter.length() - PERCENT.length());
        return middle.isEmpty() ? null : middle;
    }

    /**
     * How much of a collection a profile owns, as a percentage floored to a whole number (D13).
     *
     * <p>The arithmetic lives in {@link CollectionProgress} — the same instance the Collections
     * tab and the achievement evaluator read — so {@code forge/molten}'s
     * {@code collection.upgrades.percent} condition, the {@code collect_*} achievements and the
     * number on the tab can never disagree (M8).
     *
     * @param category one of {@link CollectionProgress#CATEGORIES}
     * @param profile the profile to read
     * @param owned the unlock ids the profile holds
     * @return the percentage in {@code [0, 100]}, 0 for an unknown category
     */
    private long collectionPercent(String category, PlayerProfile profile, Set<String> owned) {
        if (category == null) {
            return 0;
        }
        return collections.percent(category, profile, owned);
    }

    /**
     * The collection reader this evaluator counts with, sharing its id table.
     *
     * @return the reader
     */
    public CollectionProgress collections() {
        return collections;
    }

    @Override
    public String toString() {
        return "UnlockEvaluator{unlockables=" + conditions.size() + '}';
    }
}
