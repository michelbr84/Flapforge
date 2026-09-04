package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.AchievementConditionDef;
import io.github.michelbr84.flapforge.content.defs.AchievementDef;
import io.github.michelbr84.flapforge.content.defs.CompareOp;
import io.github.michelbr84.flapforge.content.defs.CounterScope;
import io.github.michelbr84.flapforge.content.defs.RewardDef;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Decides which achievements a profile has just earned (D13, D14, E1, E5, M8), and how far it
 * is from the ones it has not (the Milestones tab, {@link #progressOf}).
 *
 * <p>Every {@link AchievementDef} of {@code achievements.json} is one condition
 * {@code counter op value} in one of three scopes (§4):
 * <ul>
 *   <li><b>{@code LIFETIME}</b> reads the profile through {@link Statistics#resolve}: a
 *       {@code StatisticKey} scalar ({@code totalRuns}), one entry of a map counter
 *       ({@code bossClears.void}, {@code bestGatesByTier.hard}), the size of a list
 *       ({@code bossesCleared}) or a profile-root scalar ({@code level}, E5).</li>
 *   <li><b>{@code RUN}</b> reads the run that just finished: {@code run.gatesPassed},
 *       {@code run.points}, {@code run.streakBest}, {@code run.coinsCollected}. A run counter is
 *       only ever judged inside {@code ProgressionManager.apply}, where there <em>is</em> a
 *       finished run; the purchase pass ({@link #evaluate(PlayerProfile)}) has none and never
 *       grants one, whatever the profile holds.</li>
 *   <li><b>{@code COLLECTION}</b> reads {@code collection.<category>.percent} through
 *       {@link CollectionProgress} — the same arithmetic the Collections tab shows and the
 *       cosmetic {@code counter} unlock condition (E20) reads.</li>
 * </ul>
 *
 * <p>The evaluator lists ids only; granting is {@link ProgressionManager}'s job, which records
 * the {@code AchievementRecord} with the injected timestamp, pays {@link #rewardOf} through the
 * wallet (counted in {@code coinsEarned}, E32.a) and hands the reward's unlocks to the unlock
 * step. An achievement already held is skipped, so it fires once in a profile's life. The
 * {@code hidden} flag changes nothing here: a secret achievement is evaluated like any other and
 * only its <em>display</em> is withheld until it fires.
 *
 * <p>{@link #progressOf} is the Milestones tab's number: {@code current} against {@code target}
 * with {@code current} clamped into {@code [0, target]}. A {@code RUN}-scoped achievement has no
 * lifetime counter of its own, so it reports the best matching lifetime statistic where one
 * exists ({@code run.streakBest → streakBest}, {@code run.gatesPassed → bestGates},
 * {@code run.points → bestPoints}) and {@code 0 / target} otherwise; an achievement already
 * held reports {@code target / target}.
 *
 * <p>Pure: nothing here reads a clock, a random stream or anything but its arguments, and the
 * list it returns is in content order, so the same profile and run always grant the same ids
 * in the same order.
 */
public final class AchievementEvaluator implements ProgressionManager.AchievementHook {

    /** Prefix of a run counter. */
    private static final String RUN_PREFIX = AchievementConditionDef.RUN_PREFIX;

    private final GameContent content;
    private final CollectionProgress collections;
    private final List<AchievementDef> definitions;

    /**
     * Creates an evaluator over a content set.
     *
     * @param content the loaded content
     */
    public AchievementEvaluator(GameContent content) {
        this(content, CollectionProgress.of(content));
    }

    /**
     * Creates an evaluator sharing a collection reader (the one the unlock evaluator holds).
     *
     * @param content the loaded content
     * @param collections the collection arithmetic
     */
    public AchievementEvaluator(GameContent content, CollectionProgress collections) {
        this.content = Objects.requireNonNull(content, "content");
        this.collections = Objects.requireNonNull(collections, "collections");
        this.definitions = content.has(GameContent.ACHIEVEMENTS)
                ? List.copyOf(content.achievements().all()) : List.of();
    }

    /**
     * Creates an evaluator over a content set.
     *
     * @param content the loaded content
     * @return the evaluator
     */
    public static AchievementEvaluator of(GameContent content) {
        return new AchievementEvaluator(content);
    }

    /**
     * Every achievement the content ships, in file order.
     *
     * @return an unmodifiable list
     */
    public List<AchievementDef> definitions() {
        return definitions;
    }

    /**
     * One achievement by id.
     *
     * @param id the achievement id
     * @return the definition, or {@code null} when the content does not ship it
     */
    public AchievementDef definition(String id) {
        if (id == null) {
            return null;
        }
        for (int i = 0; i < definitions.size(); i++) {
            if (definitions.get(i).id().equals(id)) {
                return definitions.get(i);
            }
        }
        return null;
    }

    /**
     * The collection reader this evaluator counts with.
     *
     * @return the reader
     */
    public CollectionProgress collections() {
        return collections;
    }

    /**
     * The content the evaluator reads.
     *
     * @return the content
     */
    public GameContent content() {
        return content;
    }

    /**
     * The achievements a profile now satisfies and does not hold, with no run to judge: the
     * purchase pass of D14. {@code RUN}-scoped achievements are never listed here.
     *
     * @param profile the profile, already updated by the earlier steps
     * @return the ids, in content order
     */
    @Override
    public List<String> evaluate(PlayerProfile profile) {
        return evaluate(profile, null);
    }

    /**
     * The achievements a profile now satisfies and does not hold, judging the {@code RUN} scope
     * against a finished run when one is given.
     *
     * @param profile the profile, already updated by the earlier steps
     * @param result the finished run, or {@code null} after a purchase
     * @return the ids, in content order
     */
    @Override
    public List<String> evaluate(PlayerProfile profile, RunResult result) {
        Objects.requireNonNull(profile, "profile");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            AchievementDef def = definitions.get(i);
            if (profile.achievements.containsKey(def.id())) {
                continue;
            }
            if (isSatisfied(def, profile, result)) {
                out.add(def.id());
            }
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public RewardDef rewardOf(String achievementId) {
        AchievementDef def = definition(achievementId);
        return def == null ? RewardDef.NONE : def.rewardOrNone();
    }

    /**
     * Whether a profile holds an achievement.
     *
     * @param def the achievement
     * @param profile the profile
     * @return {@code true} when the record exists
     */
    public boolean isUnlocked(AchievementDef def, PlayerProfile profile) {
        return def != null && profile != null && profile.achievements.containsKey(def.id());
    }

    /**
     * Whether an achievement's condition holds right now, whether or not it is already held.
     *
     * @param def the achievement
     * @param profile the profile to read
     * @param result the finished run, or {@code null} when there is none (a {@code RUN}
     *     condition is then never satisfied)
     * @return {@code true} when the condition holds
     */
    public boolean isSatisfied(AchievementDef def, PlayerProfile profile, RunResult result) {
        Objects.requireNonNull(def, "def");
        Objects.requireNonNull(profile, "profile");
        AchievementConditionDef condition = def.condition();
        if (condition.scope() == CounterScope.RUN && result == null) {
            return false;
        }
        return compare(condition.op(), counter(condition, profile, result), condition.value());
    }

    /**
     * The value a condition's counter reads right now.
     *
     * @param condition the condition
     * @param profile the profile to read
     * @param result the finished run, or {@code null}; a {@code RUN} counter then reads 0
     * @return the value
     */
    public long counter(AchievementConditionDef condition, PlayerProfile profile,
            RunResult result) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(profile, "profile");
        switch (condition.scope()) {
            case RUN:
                return result == null ? 0 : runCounter(condition.counter(), result.stats());
            case COLLECTION:
                return collections.percent(condition.collectionCategory(), profile);
            case LIFETIME:
            default:
                return Statistics.resolve(profile, condition.counter());
        }
    }

    /**
     * One of the documented run values ({@link AchievementConditionDef#RUN_COUNTERS}).
     *
     * @param counter the counter name, {@code run.<name>}
     * @param stats the finished run's stats
     * @return the value, 0 for a name the run does not expose
     */
    public static long runCounter(String counter, RunStats stats) {
        if (counter == null || stats == null || !counter.startsWith(RUN_PREFIX)) {
            return 0;
        }
        switch (counter.substring(RUN_PREFIX.length())) {
            case "gatesPassed":
                return stats.gatesPassed();
            case "points":
                return (long) Math.floor(stats.points());
            case "streakBest":
                return stats.streakBest();
            case "coinsCollected":
                return stats.coinsCollected();
            default:
                return 0;
        }
    }

    /**
     * The lifetime counter a {@code RUN} counter is best mirrored by, for a progress bar: the
     * personal best the profile already keeps.
     *
     * @param counter the run counter name
     * @return the lifetime counter name, or {@code null} when the run value has no lifetime best
     */
    public static String lifetimeMirrorOf(String counter) {
        if (counter == null || !counter.startsWith(RUN_PREFIX)) {
            return null;
        }
        switch (counter.substring(RUN_PREFIX.length())) {
            case "gatesPassed":
                return StatisticKey.BEST_GATES.field();
            case "points":
                return StatisticKey.BEST_POINTS.field();
            case "streakBest":
                return StatisticKey.STREAK_BEST.field();
            default:
                return null;
        }
    }

    /**
     * Compares a counter with a threshold.
     *
     * @param op the operator
     * @param value the counter
     * @param threshold the threshold
     * @return the comparison
     */
    public static boolean compare(CompareOp op, long value, double threshold) {
        Objects.requireNonNull(op, "op");
        switch (op) {
            case GT:
                return value > threshold;
            case LTE:
                return value <= threshold;
            case LT:
                return value < threshold;
            case EQ:
                return value == threshold;
            case GTE:
            default:
                return value >= threshold;
        }
    }

    /**
     * How far a profile is towards an achievement (D13: the Milestones tab).
     *
     * @param def the achievement
     * @param profile the profile to read
     * @return {@code current / target}, {@code current} clamped into {@code [0, target]};
     *     {@code target / target} once the achievement is held
     */
    public Progress progressOf(AchievementDef def, PlayerProfile profile) {
        Objects.requireNonNull(def, "def");
        Objects.requireNonNull(profile, "profile");
        AchievementConditionDef condition = def.condition();
        long target = Math.max(0, Math.round(condition.value()));
        if (isUnlocked(def, profile)) {
            return new Progress(target, target);
        }
        long current;
        switch (condition.scope()) {
            case RUN: {
                String mirror = lifetimeMirrorOf(condition.counter());
                current = mirror == null ? 0 : Statistics.resolve(profile, mirror);
                break;
            }
            case COLLECTION:
                current = collections.percent(condition.collectionCategory(), profile);
                break;
            case LIFETIME:
            default:
                current = Statistics.resolve(profile, condition.counter());
                break;
        }
        return new Progress(current, target);
    }

    @Override
    public String toString() {
        return "AchievementEvaluator{achievements=" + definitions.size() + '}';
    }

    /**
     * Progress towards a threshold.
     *
     * @param current the counter, clamped into {@code [0, target]}
     * @param target the threshold
     */
    public record Progress(long current, long target) {

        /**
         * Clamps the counter.
         *
         * @param current the counter
         * @param target the threshold
         */
        public Progress {
            target = Math.max(0, target);
            current = Math.max(0, Math.min(current, target));
        }

        /**
         * The fraction reached, for a progress bar.
         *
         * @return a value in {@code [0, 1]}; 1 when the target is 0
         */
        public double fraction() {
            return target <= 0 ? 1 : (double) current / target;
        }

        /**
         * Whether the threshold is reached.
         *
         * @return {@code true} when current equals target
         */
        public boolean isComplete() {
            return current >= target;
        }
    }
}
