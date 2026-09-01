package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RewardContext;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The one place a finished run or a purchase is written into a {@link PlayerProfile} (D14).
 *
 * <p>{@link #apply} runs the fixed order and nothing else may reorder it:
 * rewards → wallet → experience and level (with the level rewards) → statistics → challenge
 * record → daily record → achievements → unlocks → mark dirty. The order is not cosmetic. Rewards
 * are computed first because the first-run bonus reads {@code statistics.totalRuns == 0} before
 * the run is counted (E32.a); the level rewards are credited before the statistics step so that
 * {@code coinsEarned} can count every coin the pass paid, from every source; achievements run
 * after the statistics they test; unlocks run after the achievements they may depend on
 * ({@code type: achievement}). {@link #lastSteps()} reports the order that actually ran, which is
 * what {@code ProgressionManagerTest} asserts.
 *
 * <p>{@link #apply} is invoked exactly once per run, when the run reaches {@code FINISHED}
 * (D29). Calling it twice with the same result is a no-op that returns the first outcome again —
 * a double call must never pay a run twice. The guard is the identity of the result, which is why
 * {@code Run.result()} returns the <em>same</em> instance for a finished run: a fresh snapshot per
 * call would defeat it, because {@code RunStats} is a mutable holder with no value equality (and
 * giving it one would be worse — two distinct runs may legitimately produce equal stats, and the
 * second of them must still be paid).
 *
 * <p>Achievement evaluation (M8) and unlock evaluation (M4) are hooks. They are declared here, in
 * their place in the order, and default to "nothing happened"; the milestone that owns the
 * evaluator injects it without touching this class or its test.
 *
 * <p>The only impurity is the injected {@link TimeSource} (D23): timestamps for the achievement
 * records and the run history. Everything else is a function of the arguments.
 */
public final class ProgressionManager {

    /** A step of the fixed order, in the order it runs. */
    public enum Step {
        /** The run's coins and experience are computed (pure). */
        REWARDS,
        /** The coins are credited to the wallet. */
        WALLET,
        /** The experience is added, the level recomputed and the level rewards credited. */
        XP_LEVEL,
        /** The lifetime statistics and the run history are updated. */
        STATISTICS,
        /** The challenge record is updated. */
        CHALLENGE,
        /** The daily record is updated. */
        DAILY,
        /** Achievements are evaluated. */
        ACHIEVEMENTS,
        /** Unlocks are evaluated. */
        UNLOCKS,
        /** The profile is marked as needing a save. */
        DIRTY
    }

    /**
     * Evaluates which achievements a profile has just unlocked (M8, {@code AchievementEvaluator}).
     */
    @FunctionalInterface
    public interface AchievementHook {

        /** The hook every milestone before M8 uses: nothing is ever unlocked. */
        AchievementHook NONE = profile -> List.of();

        /**
         * Lists the achievements the profile now satisfies but does not hold yet.
         *
         * @param profile the profile, already updated by the earlier steps
         * @return the achievement ids, in a deterministic order
         */
        List<String> evaluate(PlayerProfile profile);
    }

    /**
     * Evaluates which unlock ids a profile has just earned (M4, {@code UnlockEvaluator}).
     */
    @FunctionalInterface
    public interface UnlockHook {

        /** The hook every milestone before M4 uses: nothing is ever granted. */
        UnlockHook NONE = profile -> List.of();

        /**
         * Lists the namespaced unlock ids the profile now satisfies but does not hold yet.
         *
         * @param profile the profile, already updated by the earlier steps
         * @return the unlock ids, in a deterministic order
         */
        List<String> evaluate(PlayerProfile profile);
    }

    private final TimeSource time;
    private final AchievementHook achievementHook;
    private final UnlockHook unlockHook;
    private final List<Step> steps = new ArrayList<>();

    private RunResult lastApplied;
    private ProgressionOutcome lastOutcome = ProgressionOutcome.EMPTY;
    private long changedVersion;
    private long queuedVersion;
    private long savedVersion;

    /**
     * Creates a manager with no achievement or unlock evaluator yet.
     *
     * @param time the injected clock (D23)
     */
    public ProgressionManager(TimeSource time) {
        this(time, AchievementHook.NONE, UnlockHook.NONE);
    }

    /**
     * Creates a manager.
     *
     * @param time the injected clock (D23)
     * @param achievementHook the achievement evaluator, or {@link AchievementHook#NONE}
     * @param unlockHook the unlock evaluator, or {@link UnlockHook#NONE}
     */
    public ProgressionManager(TimeSource time, AchievementHook achievementHook,
            UnlockHook unlockHook) {
        this.time = Objects.requireNonNull(time, "time");
        this.achievementHook = achievementHook == null ? AchievementHook.NONE : achievementHook;
        this.unlockHook = unlockHook == null ? UnlockHook.NONE : unlockHook;
    }

    /**
     * Writes a finished run into the profile, with every multiplier at 1.
     *
     * @param profile the profile to update in place
     * @param result the finished run
     * @param rules the economy numbers
     * @return what changed
     */
    public ProgressionOutcome apply(PlayerProfile profile, RunResult result,
            ProgressionRules rules) {
        return apply(profile, result, rules, ProgressionRules.RewardMultipliers.NEUTRAL);
    }

    /**
     * Writes a finished run into the profile, in D14's fixed order.
     *
     * @param profile the profile to update in place
     * @param result the finished run
     * @param rules the economy numbers
     * @param multipliers the multipliers the run was played under
     * @return what changed; the previous outcome, unchanged, when this run was already applied
     */
    public ProgressionOutcome apply(PlayerProfile profile, RunResult result,
            ProgressionRules rules, ProgressionRules.RewardMultipliers multipliers) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(rules, "rules");
        ProgressionRules.RewardMultipliers mult = multipliers == null
                ? ProgressionRules.RewardMultipliers.NEUTRAL : multipliers;
        if (result.equals(lastApplied)) {
            return lastOutcome;
        }
        steps.clear();

        RewardSummary rewards = computeRewards(profile, result, rules, mult);
        Wallet wallet = credit(profile, rules, rewards);
        LevelChange levels = advanceLevel(profile, rules, wallet, rewards);
        recordStatistics(profile, result, rewards, levels.creditedCoins());
        boolean firstCompletion = recordChallenge(profile, result);
        boolean dailyRecorded = recordDaily(profile, result);
        List<String> achievements = evaluateAchievements(profile);
        List<String> unlocks = evaluateUnlocks(profile);
        markDirty();

        lastApplied = result;
        lastOutcome = new ProgressionOutcome(rewards, levels.levelUps(), levels.grants(),
                achievements, unlocks, firstCompletion, dailyRecorded);
        return lastOutcome;
    }

    /**
     * Runs the trailing steps of the pipeline after an atomic purchase (D14): achievements →
     * unlocks → dirty. Buying the last bird must fire {@code collect_all_birds} immediately, not
     * at the end of the next run.
     *
     * @param profile the profile, already debited and granted by the caller
     * @param rules the economy numbers (unused today; kept so the signature does not change when
     *     a purchase starts paying rewards)
     * @return what the two evaluators granted
     */
    public ProgressionOutcome applyPurchase(PlayerProfile profile, ProgressionRules rules) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(rules, "rules");
        steps.clear();
        List<String> achievements = evaluateAchievements(profile);
        List<String> unlocks = evaluateUnlocks(profile);
        markDirty();
        return new ProgressionOutcome(RewardSummary.NONE, List.of(), Map.of(), achievements, unlocks,
                false, false);
    }

    private RewardSummary computeRewards(PlayerProfile profile, RunResult result,
            ProgressionRules rules, ProgressionRules.RewardMultipliers mult) {
        RewardContext ctx = rewardContext(profile, result, mult);
        RewardSummary rewards = rules.rewards().compute(result, ctx);
        steps.add(Step.REWARDS);
        return rewards == null ? RewardSummary.NONE : rewards;
    }

    /**
     * The parts of the reward formula that only the profile knows (E32.a). Every one of them is
     * read <em>before</em> any step has written to the profile, which is why the reward step comes
     * first.
     *
     * @param profile the profile, not yet updated
     * @param result the finished run
     * @param mult the multipliers the run was played under
     * @return the context the reward formula reads
     */
    public RewardContext rewardContext(PlayerProfile profile, RunResult result,
            ProgressionRules.RewardMultipliers mult) {
        Statistics stats = profile.statistics;
        boolean firstRun = stats == null || stats.totalRuns == 0;
        String challengeId = result.config().challengeId();
        boolean firstChallenge = false;
        if (challengeId != null && !challengeId.isBlank() && result.stats().objectiveMet()) {
            PlayerProfile.ChallengeRecord record = profile.challenges.get(challengeId);
            firstChallenge = record == null || !record.completed;
        }
        Set<String> firstBosses = new LinkedHashSet<>();
        List<String> cleared = stats == null ? List.of() : stats.bossesCleared;
        for (String worldId : result.stats().bossesCleared()) {
            if (!cleared.contains(worldId)) {
                firstBosses.add(worldId);
            }
        }
        return new RewardContext(firstRun, firstChallenge, firstBosses, mult.coinMult(),
                mult.xpMult(), mult.tierRewardMult(), mult.dailyRewardMult());
    }

    private Wallet credit(PlayerProfile profile, ProgressionRules rules, RewardSummary rewards) {
        Wallet wallet = Wallet.of(profile);
        wallet.declare(rules.currencies());
        wallet.add(rules.primaryCurrency(), rewards.coins());
        steps.add(Step.WALLET);
        return wallet;
    }

    private LevelChange advanceLevel(PlayerProfile profile, ProgressionRules rules, Wallet wallet,
            RewardSummary rewards) {
        PlayerLevel curve = rules.levels();
        int before = curve.levelFor(profile.xp);
        profile.xp = Math.max(0, profile.xp + rewards.xp());
        int after = curve.levelFor(profile.xp);
        profile.level = after;
        List<Integer> ups = curve.levelsCrossed(before, after);
        Map<String, Long> grants = curve.rewardsBetween(before, after);
        long creditedCoins = rewards.coins();
        for (Map.Entry<String, Long> grant : grants.entrySet()) {
            wallet.add(grant.getKey(), grant.getValue());
            creditedCoins += grant.getValue();
        }
        steps.add(Step.XP_LEVEL);
        return new LevelChange(ups, grants, creditedCoins);
    }

    private void recordStatistics(PlayerProfile profile, RunResult result, RewardSummary rewards,
            long creditedCoins) {
        RunConfig config = result.config();
        RunStats run = result.stats();
        Statistics stats = profile.statistics;
        long points = Math.round(run.points());
        stats.countRun();
        stats.recordGates(run.gatesPassed(), config.worldId(), config.tierId());
        stats.recordPoints(points);
        stats.addCoinsEarned(creditedCoins);
        stats.addCoinsCollected(rewards.coinsCollected());
        stats.addXpEarned(rewards.xp());
        CollisionCause cause = run.deathCause();
        stats.countDeath(cause == null ? null : cause.name());
        for (Map.Entry<String, Integer> used : run.abilitiesUsed().entrySet()) {
            stats.countAbilityUses(used.getKey(), used.getValue());
        }
        stats.addShieldAbsorbs(run.shieldAbsorbs());
        stats.addRevives(run.revives());
        stats.recordStreak(run.streakBest());
        for (String worldId : run.bossesCleared()) {
            stats.recordBossClear(worldId);
        }
        for (String modifierId : run.modifiersTaken()) {
            stats.countModifierTaken(modifierId);
        }
        for (String synergyId : run.synergiesActivated()) {
            stats.countSynergyActivated(synergyId);
        }
        if (config.mode() == RunMode.CHALLENGE && run.objectiveMet()) {
            stats.challengesCompleted++;
        }
        if (config.mode() == RunMode.DAILY) {
            stats.dailiesPlayed++;
        }
        stats.addPlaytimeTicks(run.ticksAlive());
        stats.addHistory(historyEntry(result, rewards, points));
        profile.lastSeed = config.seed();
        steps.add(Step.STATISTICS);
    }

    private Statistics.RunHistoryEntry historyEntry(RunResult result, RewardSummary rewards,
            long points) {
        RunConfig config = result.config();
        RunStats run = result.stats();
        Statistics.RunHistoryEntry entry = new Statistics.RunHistoryEntry();
        entry.finishedAtEpochMs = time.epochMillis();
        entry.seed = config.seed();
        entry.mode = config.mode().name();
        entry.birdId = config.birdId();
        entry.worldId = config.worldId();
        entry.tierId = config.tierId();
        entry.challengeId = config.challengeId() == null ? "" : config.challengeId();
        entry.gates = run.gatesPassed();
        entry.points = points;
        entry.coins = rewards.coins();
        entry.xp = rewards.xp();
        entry.streakBest = run.streakBest();
        entry.coinsCollected = run.coinsCollected();
        entry.ticksAlive = run.ticksAlive();
        CollisionCause cause = run.deathCause();
        entry.deathCause = cause == null ? Statistics.CAUSE_UNKNOWN : cause.name();
        entry.objectiveMet = run.objectiveMet();
        return entry;
    }

    private boolean recordChallenge(PlayerProfile profile, RunResult result) {
        String challengeId = result.config().challengeId();
        boolean first = false;
        if (challengeId != null && !challengeId.isBlank()) {
            PlayerProfile.ChallengeRecord record = profile.challenge(challengeId);
            record.attempts++;
            long gates = result.stats().gatesPassed();
            if (gates > record.bestGates) {
                record.bestGates = gates;
            }
            if (result.stats().objectiveMet() && !record.completed) {
                record.completed = true;
                first = true;
            }
            // Having a record means having played it, which E15 says implies owning it. This is
            // the same repair normalisation makes, done here so the evaluators that run after this
            // step already see a consistent profile; it is not an unlock the player "earned", so
            // it is not reported in the outcome.
            profile.unlock("challenge:" + challengeId);
        }
        steps.add(Step.CHALLENGE);
        return first;
    }

    private boolean recordDaily(PlayerProfile profile, RunResult result) {
        boolean recorded = false;
        if (result.config().mode() == RunMode.DAILY) {
            PlayerProfile.DailyRecord daily = profile.daily;
            daily.attempts++;
            long gates = result.stats().gatesPassed();
            if (gates > daily.bestGates) {
                daily.bestGates = gates;
            }
            recorded = true;
        }
        steps.add(Step.DAILY);
        return recorded;
    }

    private List<String> evaluateAchievements(PlayerProfile profile) {
        List<String> unlocked = new ArrayList<>();
        for (String id : achievementHook.evaluate(profile)) {
            if (id != null && !id.isBlank() && !profile.achievements.containsKey(id)) {
                profile.achievements.put(id,
                        new PlayerProfile.AchievementRecord(time.epochMillis()));
                unlocked.add(id);
            }
        }
        steps.add(Step.ACHIEVEMENTS);
        return unlocked;
    }

    private List<String> evaluateUnlocks(PlayerProfile profile) {
        List<String> granted = new ArrayList<>();
        for (String id : unlockHook.evaluate(profile)) {
            if (profile.unlock(id)) {
                granted.add(id);
            }
        }
        steps.add(Step.UNLOCKS);
        return granted;
    }

    private void markDirty() {
        changedVersion++;
        steps.add(Step.DIRTY);
    }

    /**
     * The steps the last {@link #apply} or {@link #applyPurchase} ran, in order.
     *
     * @return an unmodifiable view of the trace
     */
    public List<Step> lastSteps() {
        return Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /**
     * The outcome of the last pass.
     *
     * @return the outcome, {@link ProgressionOutcome#EMPTY} before the first pass
     */
    public ProgressionOutcome lastOutcome() {
        return lastOutcome;
    }

    /**
     * Whether the profile has changes that are not on the disk yet.
     *
     * <p>The flag is a version comparison rather than a boolean because a write is asynchronous:
     * {@link #markSaveQueued()} records which state went to the writer and {@link #confirmSave()}
     * only marks that state as saved, so a change made <em>after</em> the write was queued keeps
     * the profile dirty, and a write that fails never clears anything (D15).
     *
     * @return {@code true} when a pass ran whose state has not landed on the disk
     */
    public boolean isDirty() {
        return changedVersion != savedVersion;
    }

    /**
     * Records that the current state has been handed to the writer.
     *
     * @return the version that was queued
     */
    public long markSaveQueued() {
        queuedVersion = changedVersion;
        return queuedVersion;
    }

    /**
     * Marks the last queued state as the one on the disk. Called only for a write that reported
     * OK, and only once nothing is still in flight.
     */
    public void confirmSave() {
        savedVersion = queuedVersion;
    }

    /** Declares everything written (tools and tests; the game goes through the two calls above). */
    public void clearDirty() {
        queuedVersion = changedVersion;
        savedVersion = changedVersion;
    }

    /**
     * Whether a run has already been written into the profile.
     *
     * @param result the run
     * @return {@code true} when {@link #apply} would return the previous outcome
     */
    public boolean isApplied(RunResult result) {
        return result != null && result.equals(lastApplied);
    }

    /**
     * Forgets which run was applied last, so the next {@link #apply} runs the pipeline again. The
     * game never needs this; a tool or a test that replays runs into one manager does.
     */
    public void forgetLastRun() {
        lastApplied = null;
        lastOutcome = ProgressionOutcome.EMPTY;
    }

    /** The result of the experience step, carried to the outcome. */
    private record LevelChange(List<Integer> levelUps, Map<String, Long> grants,
            long creditedCoins) {

        LevelChange {
            levelUps = List.copyOf(levelUps);
            grants = Collections.unmodifiableMap(new LinkedHashMap<>(grants));
        }
    }
}
