package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RewardContext;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ProgressionManager}: D14's fixed order, applied exactly once per run, with the statistics
 * and the records it is responsible for.
 */
class ProgressionManagerTest {

    private static final String COINS = PlayerProfile.CURRENCY_COINS;

    private FixedTimeSource time;
    private PlayerProfile profile;
    private List<RewardContext> seenContexts;
    private long coinsPerRun;
    private long xpPerRun;
    private long collectedPerRun;
    private ProgressionRules rules;

    @BeforeEach
    void setUp() {
        time = new FixedTimeSource(1_700_000_000_000L);
        profile = PlayerProfile.fresh(time.epochMillis()).normalize();
        seenContexts = new ArrayList<>();
        coinsPerRun = 75;
        xpPerRun = 0;
        collectedPerRun = 0;
        Map<Integer, Map<String, Long>> levelRewards = new LinkedHashMap<>();
        levelRewards.put(2, Map.of(COINS, 50L));
        levelRewards.put(3, Map.of(COINS, 80L));
        PlayerLevel levels = new PlayerLevel(100, 1.10, 50, levelRewards);
        rules = ProgressionRules.of(levels, (result, ctx) -> {
            seenContexts.add(ctx);
            return summary(coinsPerRun, xpPerRun, collectedPerRun);
        });
    }

    /** The three numbers the write path reads, with no breakdown behind them. */
    private static RewardSummary summary(long coins, long xp, long collected) {
        return new RewardSummary(coins, xp, collected, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1);
    }

    private ProgressionManager manager() {
        return new ProgressionManager(time);
    }

    /** A finished standard run over Green Fields at the normal tier. */
    private static RunResult run(int gates) {
        return runBuilder(gates).build();
    }

    private static Builder runBuilder(int gates) {
        return new Builder(gates);
    }

    /** Minimal {@link RunResult} factory for the pipeline tests. */
    private static final class Builder {
        private final RunStats stats = new RunStats();
        private RunConfig.Builder config = RunConfig.builder(42);

        Builder(int gates) {
            stats.setGatesPassed(gates);
            stats.setPoints(gates);
            stats.setDeathCause(CollisionCause.OBSTACLE);
        }

        Builder points(double points) {
            stats.setPoints(points);
            return this;
        }

        Builder world(String worldId) {
            config = config.worldId(worldId);
            return this;
        }

        Builder tier(String tierId) {
            config = config.tierId(tierId);
            return this;
        }

        Builder challenge(String challengeId, boolean objectiveMet) {
            config = config.challengeId(challengeId).mode(RunMode.CHALLENGE);
            stats.setObjectiveMet(objectiveMet);
            return this;
        }

        Builder daily() {
            config = config.mode(RunMode.DAILY);
            return this;
        }

        Builder seed(long seed) {
            config = config.seed(seed);
            return this;
        }

        Builder streakBest(int streak) {
            stats.setStreak(streak);
            stats.setStreak(0);
            return this;
        }

        Builder ticksAlive(int ticks) {
            for (int i = 0; i < ticks; i++) {
                stats.tickAlive();
            }
            return this;
        }

        Builder death(CollisionCause cause) {
            stats.setDeathCause(cause);
            return this;
        }

        Builder ability(String id, int uses) {
            for (int i = 0; i < uses; i++) {
                stats.countAbilityUse(id);
            }
            return this;
        }

        Builder boss(String worldId) {
            stats.addBossCleared(worldId);
            return this;
        }

        Builder modifier(String id) {
            stats.addModifierTaken(id);
            return this;
        }

        Builder coinsCollected(int coins) {
            stats.addCoinsCollected(coins);
            return this;
        }

        RunResult build() {
            return new RunResult(config.build(), stats.copy(), Map.of());
        }
    }

    @Test
    void stepsRunInTheFixedOrder() {
        ProgressionManager manager = manager();
        manager.apply(profile, run(10), rules);
        assertEquals(Arrays.asList(ProgressionManager.Step.values()), manager.lastSteps(),
                "D14's order is rewards, wallet, xp/level, statistics, challenge, daily, "
                        + "achievements, unlocks, dirty");
    }

    @Test
    void applyIsIdempotentPerRun() {
        ProgressionManager manager = manager();
        RunResult result = run(10);
        ProgressionOutcome first = manager.apply(profile, result, rules);
        ProgressionOutcome second = manager.apply(profile, result, rules);
        assertSame(first, second, "a second call must return the first outcome");
        assertEquals(1, profile.statistics.totalRuns);
        assertEquals(75, profile.wallet.get(COINS), "a run must never be paid twice");
        assertEquals(1, seenContexts.size(), "the reward formula must run once");
        assertTrue(manager.isApplied(result));
    }

    /**
     * The guard has to hold for two <em>snapshots</em> of one finished run, not just for the same
     * object handed in twice: {@code Run.result()} used to build a fresh {@code RunResult} on every
     * call, and {@code RunStats} has no value equality, so the second snapshot slipped past the
     * guard and paid the run again.
     */
    @Test
    void twoSnapshotsOfOneFinishedRunArePaidOnce() {
        Run run = Run.classic(RunConfig.classic(2024));
        run.tick(RunInput.FLAP);
        for (int i = 0; i < 600 && !run.isFinished(); i++) {
            run.tick(RunInput.NONE);
        }
        assertTrue(run.isFinished(), "the dive must reach FINISHED");

        RunResult first = run.result();
        RunResult second = run.result();
        assertSame(first, second, "a finished run has one result, not one per call");

        ProgressionManager manager = manager();
        manager.apply(profile, first, rules);
        manager.apply(profile, second, rules);

        assertEquals(1, profile.statistics.totalRuns);
        assertEquals(1, profile.statistics.runHistory.size());
        assertEquals(75, profile.wallet.get(COINS), "a run must never be paid twice");
        assertTrue(manager.isApplied(second));
    }

    @Test
    void aZeroGateRunStillCountsAsARun() {
        ProgressionManager manager = manager();
        coinsPerRun = 0;
        manager.apply(profile, run(0), rules);
        assertEquals(1, profile.statistics.totalRuns);
        assertEquals(0, profile.statistics.totalGates);
        assertEquals(0, profile.statistics.bestGates);
        assertEquals(1, profile.statistics.runHistory.size(), "the history keeps every run");
        assertEquals(0, profile.wallet.get(COINS));
    }

    @Test
    void statisticsAccumulateAcrossRuns() {
        ProgressionManager manager = manager();
        manager.apply(profile, runBuilder(12).points(24).build(), rules);
        manager.apply(profile, runBuilder(7).points(14).tier("hard").build(), rules);
        Statistics stats = profile.statistics;
        assertEquals(2, stats.totalRuns);
        assertEquals(19, stats.totalGates);
        assertEquals(12, stats.bestGates);
        assertEquals(38, stats.totalPoints);
        assertEquals(24, stats.bestPoints);
        assertEquals(12L, stats.bestGatesByWorld.get(RunConfig.DEFAULT_WORLD));
        assertEquals(12L, stats.bestGatesByTier.get(RunConfig.DEFAULT_TIER));
        assertEquals(7L, stats.bestGatesByTier.get("hard"), "each tier keeps its own best");
        assertEquals(150, stats.coinsEarned, "every credited coin is counted");
        assertEquals(150, profile.wallet.get(COINS));
    }

    @Test
    void theFirstRunFlagIsReadBeforeTheRunIsCounted() {
        ProgressionManager manager = manager();
        manager.apply(profile, run(3), rules);
        manager.apply(profile, run(4), rules);
        assertTrue(seenContexts.get(0).firstRun(), "run 1 must see totalRuns == 0");
        assertFalse(seenContexts.get(1).firstRun(), "run 2 must not");
    }

    @Test
    void experienceRaisesTheLevelAndPaysEveryLevelCrossed() {
        ProgressionManager manager = manager();
        coinsPerRun = 0;
        xpPerRun = 210;
        ProgressionOutcome outcome = manager.apply(profile, run(5), rules);
        assertEquals(3, profile.level);
        assertEquals(210, profile.xp);
        assertEquals(List.of(2, 3), outcome.levelUps());
        assertEquals(Map.of(COINS, 130L), outcome.levelRewardsGranted());
        assertEquals(130, profile.wallet.get(COINS), "level rewards go through the wallet");
        assertEquals(130, profile.statistics.coinsEarned,
                "and are counted in coinsEarned like every other grant (E32.a)");
        assertEquals(210, profile.statistics.xpEarned);
    }

    @Test
    void collectedCoinsAreCountedSeparately() {
        ProgressionManager manager = manager();
        coinsPerRun = 90;
        collectedPerRun = 15;
        manager.apply(profile, runBuilder(6).coinsCollected(15).build(), rules);
        assertEquals(90, profile.wallet.get(COINS), "collected coins are already inside the total");
        assertEquals(90, profile.statistics.coinsEarned);
        assertEquals(15, profile.statistics.coinsCollected);
    }

    @Test
    void runHistoryIsCappedAtOneHundred() {
        ProgressionManager manager = manager();
        for (int i = 0; i < Statistics.RUN_HISTORY_LIMIT + 20; i++) {
            manager.apply(profile, runBuilder(i).seed(1000 + i).build(), rules);
        }
        List<Statistics.RunHistoryEntry> history = profile.statistics.runHistory;
        assertEquals(Statistics.RUN_HISTORY_LIMIT, history.size());
        assertEquals(1020, history.get(0).seed, "the oldest entries fall out first");
        assertEquals(1119, history.get(history.size() - 1).seed);
        assertEquals(120, profile.statistics.totalRuns, "the cap never touches the totals");
    }

    @Test
    void theHistoryEntryDescribesTheRun() {
        ProgressionManager manager = manager();
        coinsPerRun = 60;
        xpPerRun = 30;
        manager.apply(profile, runBuilder(9).points(18).seed(7).ticksAlive(120)
                .death(CollisionCause.GROUND).build(), rules);
        Statistics.RunHistoryEntry entry = profile.statistics.runHistory.get(0);
        assertEquals(time.epochMillis(), entry.finishedAtEpochMs, "timestamps come from TimeSource");
        assertEquals(7, entry.seed);
        assertEquals(RunMode.STANDARD.name(), entry.mode);
        assertEquals(9, entry.gates);
        assertEquals(18, entry.points);
        assertEquals(60, entry.coins);
        assertEquals(30, entry.xp);
        assertEquals(120, entry.ticksAlive);
        assertEquals(CollisionCause.GROUND.name(), entry.deathCause);
        assertEquals(7, profile.lastSeed);
        assertEquals(2, profile.statistics.playtimeSeconds, "120 ticks are two seconds");
    }

    @Test
    void deathsAbilitiesModifiersAndStreaksAreTallied() {
        ProgressionManager manager = manager();
        manager.apply(profile, runBuilder(4).death(CollisionCause.GROUND).streakBest(6)
                .ability("dash", 3).ability("shield", 1).modifier("tailwind").build(), rules);
        manager.apply(profile, runBuilder(2).death(CollisionCause.GROUND).streakBest(2).build(),
                rules);
        Statistics stats = profile.statistics;
        assertEquals(2L, stats.deathsByCause.get(CollisionCause.GROUND.name()));
        assertEquals(3L, stats.abilitiesUsed.get("dash"));
        assertEquals(4, stats.abilitiesUsedTotal);
        assertEquals(1L, stats.modifiersTaken.get("tailwind"));
        assertEquals(6, stats.streakBest, "the best streak is a lifetime maximum, not the last run");
    }

    @Test
    void aClearedWorldBossIsRecordedOnceAndReportedAsFirstToTheFormula() {
        ProgressionManager manager = manager();
        manager.apply(profile, runBuilder(30).boss("wind_valley").build(), rules);
        manager.apply(profile, runBuilder(31).boss("wind_valley").build(), rules);
        assertEquals(List.of("wind_valley"), profile.statistics.bossesCleared);
        assertEquals(2L, profile.statistics.bossClears.get("wind_valley"));
        assertEquals(List.of("wind_valley"), List.copyOf(seenContexts.get(0).firstBossClears()));
        assertTrue(seenContexts.get(1).firstBossClears().isEmpty(), "only the first clear is first");
    }

    @Test
    void aChallengeRunUpdatesItsRecordAndReportsTheFirstCompletionOnce() {
        ProgressionManager manager = manager();
        ProgressionOutcome failed =
                manager.apply(profile, runBuilder(5).challenge("no_shield_1", false).build(), rules);
        assertFalse(failed.challengeFirstCompleted());
        ProgressionOutcome first =
                manager.apply(profile, runBuilder(9).challenge("no_shield_1", true).build(), rules);
        assertTrue(first.challengeFirstCompleted());
        ProgressionOutcome again =
                manager.apply(profile, runBuilder(11).challenge("no_shield_1", true).build(), rules);
        assertFalse(again.challengeFirstCompleted(), "only the first completion is the first");

        PlayerProfile.ChallengeRecord record = profile.challenges.get("no_shield_1");
        assertNotNull(record);
        assertEquals(3, record.attempts);
        assertEquals(11, record.bestGates);
        assertTrue(record.completed);
        assertEquals(2, profile.statistics.challengesCompleted, "repeats count as completions");
        assertTrue(profile.isUnlocked("challenge:no_shield_1"), "playing it implies owning it");
        assertTrue(seenContexts.get(1).firstChallengeCompletion());
        assertFalse(seenContexts.get(2).firstChallengeCompletion());
    }

    @Test
    void aDailyRunUpdatesTheDailyRecord() {
        ProgressionManager manager = manager();
        profile.daily.date = "2026-09-01";
        profile.daily.seed = 99;
        ProgressionOutcome outcome = manager.apply(profile, runBuilder(14).daily().build(), rules);
        assertTrue(outcome.dailyRecorded());
        assertEquals(1, profile.daily.attempts);
        assertEquals(14, profile.daily.bestGates);
        assertEquals("2026-09-01", profile.daily.date, "the pick itself is not touched (E27)");
        assertEquals(1, profile.statistics.dailiesPlayed);
    }

    @Test
    void hooksGrantAchievementsAndUnlocksAndStampTheTime() {
        ProgressionManager manager = new ProgressionManager(time,
                p -> List.of("first_flight", "first_flight"), p -> List.of("bird:swift"));
        ProgressionOutcome outcome = manager.apply(profile, run(6), rules);
        assertEquals(List.of("first_flight"), outcome.achievementsUnlocked(),
                "an achievement is granted once");
        assertEquals(List.of("bird:swift"), outcome.unlocksGranted());
        assertEquals(time.epochMillis(),
                profile.achievements.get("first_flight").unlockedAtEpochMs);
        assertTrue(profile.isUnlocked("bird:swift"));

        time.advance(60_000);
        manager.apply(profile, run(8), rules);
        assertEquals(1_700_000_000_000L,
                profile.achievements.get("first_flight").unlockedAtEpochMs,
                "an achievement already held keeps its timestamp");
    }

    @Test
    void purchaseTriggersAchievementsAndUnlocks() {
        // E17: M4 asserts the unlock half of D14's promise — a purchase is propagated at once,
        // not at the end of the next run. The achievement half is asserted here with a stub hook
        // and lands for real in M8, when AchievementEvaluator ships.
        GameContent content = GameContent.load();
        List<PlayerProfile> achievementCalls = new ArrayList<>();
        ProgressionManager manager = new ProgressionManager(time, p -> {
            achievementCalls.add(p);
            return List.of();
        }, UnlockEvaluator.of(content));
        UnlockManager shop = new UnlockManager(manager, SaveTrigger.NONE);
        Wallet.of(profile).add(COINS, 1000);

        PurchaseResult bought = shop.purchase(profile, "bird:heavy", content);

        assertTrue(bought.ok(), () -> "refused with " + bought.status());
        assertEquals(1, achievementCalls.size(), "the achievement hook runs on every purchase");
        assertSame(profile, achievementCalls.get(0));
        assertEquals("cosmetic:heavy:default", bought.outcome().unlocksGranted().get(0),
                "buying the bird makes its default palette earned, in the same call");
        // Everything after it is the M6 default-modifier set, which is content rather than part
        // of PlayerProfile.DEFAULT_UNLOCKED and is therefore granted by the first evaluation the
        // profile ever goes through — here, the one this purchase triggers.
        for (String granted : bought.outcome().unlocksGranted().subList(1,
                bought.outcome().unlocksGranted().size())) {
            assertTrue(granted.startsWith("modifier:"), granted);
        }
        assertTrue(profile.isUnlocked("cosmetic:heavy:default"));
        assertEquals(List.of(ProgressionManager.Step.ACHIEVEMENTS,
                ProgressionManager.Step.UNLOCKS, ProgressionManager.Step.DIRTY),
                manager.lastSteps());
        assertTrue(manager.isDirty());
        assertEquals(0, profile.statistics.totalRuns, "a purchase is not a run");
    }

    @Test
    void applyPurchaseRunsOnlyTheTrailingSteps() {
        ProgressionManager manager = new ProgressionManager(time,
                p -> List.of("collect_all_birds"), p -> List.of("feature:modifiers"));
        ProgressionOutcome outcome = manager.applyPurchase(profile, rules);
        assertEquals(List.of(ProgressionManager.Step.ACHIEVEMENTS,
                ProgressionManager.Step.UNLOCKS, ProgressionManager.Step.DIRTY),
                manager.lastSteps());
        assertEquals(List.of("collect_all_birds"), outcome.achievementsUnlocked());
        assertEquals(List.of("feature:modifiers"), outcome.unlocksGranted());
        assertEquals(0, profile.statistics.totalRuns, "a purchase is not a run");
        assertTrue(manager.isDirty());
    }

    @Test
    void theDirtyFlagIsRaisedByAPassAndClearedBySaving() {
        ProgressionManager manager = manager();
        assertFalse(manager.isDirty());
        manager.apply(profile, run(1), rules);
        assertTrue(manager.isDirty());
        manager.clearDirty();
        assertFalse(manager.isDirty());
    }

    @Test
    void forgettingTheLastRunLetsTheSameResultBeAppliedAgain() {
        ProgressionManager manager = manager();
        RunResult result = run(3);
        manager.apply(profile, result, rules);
        manager.forgetLastRun();
        manager.apply(profile, result, rules);
        assertEquals(2, profile.statistics.totalRuns);
    }

    @Test
    void multipliersReachTheRewardFormulaUnchanged() {
        ProgressionManager manager = manager();
        manager.apply(profile, run(5), rules,
                new ProgressionRules.RewardMultipliers(1.3, 1.1, 1.5, 1.25));
        RewardContext inputs = seenContexts.get(0);
        assertEquals(1.3, inputs.coinMult());
        assertEquals(1.1, inputs.xpMult());
        assertEquals(1.5, inputs.tierRewardMult());
        assertEquals(1.25, inputs.dailyRewardMult());
    }

    @Test
    void abrokenMultiplierFallsBackToOne() {
        ProgressionRules.RewardMultipliers mult =
                new ProgressionRules.RewardMultipliers(Double.NaN, -2, Double.POSITIVE_INFINITY, 2);
        assertEquals(1, mult.coinMult());
        assertEquals(1, mult.xpMult());
        assertEquals(1, mult.tierRewardMult());
        assertEquals(2, mult.dailyRewardMult());
    }

    @Test
    void countersResolveThroughTheStatisticKeys() {
        ProgressionManager manager = manager();
        xpPerRun = 120;
        manager.apply(profile, runBuilder(12).tier("hard").boss("void").build(), rules);
        assertEquals(1, Statistics.resolve(profile, "totalRuns"));
        assertEquals(12, Statistics.resolve(profile, "statistics.totalGates"));
        assertEquals(12, Statistics.resolve(profile, "bestGatesByTier.hard"));
        assertEquals(0, Statistics.resolve(profile, "bestGatesByTier.nightmare"));
        assertEquals(1, Statistics.resolve(profile, "bossClears.void"));
        assertEquals(2, Statistics.resolve(profile, "level"), "E5: the level is a counter too");
        assertEquals(120, Statistics.resolve(profile, "xp"));
        assertEquals(0, Statistics.resolve(profile, "nonsense"));
    }
}
