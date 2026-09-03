package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
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

    /**
     * M8 (E17): the hook is the shipped {@link AchievementEvaluator}, so one finished run over
     * Green Fields grants {@code first_flight} (one run, lifetime scope), stamps the injected
     * time, pays its 25 coins through the wallet and lets the unlock step grant the M6 default
     * modifiers. A second run re-grants neither.
     */
    @Test
    void hooksGrantAchievementsAndUnlocksAndStampTheTime() {
        GameContent content = GameContent.load();
        ProgressionManager manager = new ProgressionManager(time,
                AchievementEvaluator.of(content), UnlockEvaluator.of(content));
        long before = profile.wallet.getOrDefault(COINS, 0L);

        ProgressionOutcome outcome = manager.apply(profile, run(6), rules);
        assertEquals(List.of("first_flight"), outcome.achievementsUnlocked(),
                "the real evaluator fires exactly the one-run achievements");
        assertEquals(coinsPerRun + 25, profile.wallet.get(COINS) - before,
                "the run's reward plus the achievement's coin reward reach the wallet");
        assertEquals(time.epochMillis(),
                profile.achievements.get("first_flight").unlockedAtEpochMs);
        assertFalse(outcome.unlocksGranted().isEmpty(), "the unlock step still runs");
        for (String granted : outcome.unlocksGranted()) {
            assertTrue(granted.startsWith("modifier:"), granted);
        }
        assertTrue(profile.isUnlocked(outcome.unlocksGranted().get(0)));

        time.advance(60_000);
        ProgressionOutcome again = manager.apply(profile, run(8), rules);
        assertEquals(List.of(), again.achievementsUnlocked(),
                "an achievement is granted once in a profile's life");
        assertEquals(1_700_000_000_000L,
                profile.achievements.get("first_flight").unlockedAtEpochMs,
                "an achievement already held keeps its timestamp");
    }

    /**
     * M8 (E17): the hook is the shipped {@link AchievementEvaluator}, so a purchase is propagated
     * at once and is able to fire a {@code COLLECTION} achievement: owning every bird but one,
     * buying the last one completes {@code collection.birds} and {@code collect_all_birds} fires
     * in the very call that charged the bird, stamped with the injected time and paid.
     */
    @Test
    void purchaseTriggersAchievementsAndUnlocks() {
        GameContent content = GameContent.load();
        ProgressionManager manager = new ProgressionManager(time,
                AchievementEvaluator.of(content), UnlockEvaluator.of(content));
        UnlockManager shop = new UnlockManager(manager, SaveTrigger.NONE);
        for (BirdDef bird : content.birds()) {
            if (!"heavy".equals(bird.id())) {
                profile.unlock("bird:" + bird.id());
            }
        }
        Wallet.of(profile).add(COINS, 1000);
        long price = shop.priceOf("bird:heavy", content);
        assertTrue(price > 0, "the bird is for sale");

        PurchaseResult bought = shop.purchase(profile, "bird:heavy", content);

        assertTrue(bought.ok(), () -> "refused with " + bought.status());
        assertEquals(List.of("collect_all_birds"), bought.outcome().achievementsUnlocked(),
                "buying the last bird completes the collection and fires the achievement");
        assertEquals(time.epochMillis(),
                profile.achievements.get("collect_all_birds").unlockedAtEpochMs);
        assertEquals(500, profile.wallet.get(COINS)
                - (1000 - price), "the achievement's coin reward is paid in the same call");
        assertTrue(bought.outcome().unlocksGranted().contains("cosmetic:heavy:default"),
                "buying the bird makes its default palette earned, in the same call");
        // The other birds were granted without their palettes, so the unlock step of this same
        // call also earns those defaults, and with them whatever content conditions a fuller
        // collection satisfies (the M6 default-modifier set, ability grants, ...). What matters
        // is that every grant is real and now owned.
        for (String granted : bought.outcome().unlocksGranted()) {
            assertTrue(profile.isUnlocked(granted), granted);
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

    /**
     * M8 (E26, E32.a): against the shipped content, a first Green Fields boss clear pays
     * {@code bossBonus + boss.reward.coins} and grants {@code world:wind_valley}; a repeat pays
     * {@code bossBonus} alone and grants nothing. The summary's terms add up to the wallet
     * delta, level rewards aside.
     */
    @Test
    void aFirstBossClearPaysItsRewardAndGrantsItsUnlockOnce() {
        GameContent content = GameContent.load();
        ProgressionRules shipped = ProgressionRules.fromContent(content);
        ProgressionManager manager = new ProgressionManager(time,
                ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
        long before = profile.wallet.getOrDefault(COINS, 0L);

        ProgressionOutcome first = manager.apply(profile,
                runBuilder(31).points(31).ticksAlive(3000).boss("green_fields").build(), shipped);
        RewardSummary paid = first.rewardSummary();
        assertEquals(150 + 200, paid.bossCoins(), "bossBonus 150 + boss.reward.coins 200");
        assertEquals(20 + 25 + 62 + 31 + 350, paid.baseCoins());
        assertEquals(paid.participation() + paid.firstRunBonus() + paid.gateCoins()
                + paid.pointCoins() + paid.streakCoins() + paid.bossCoins()
                + paid.challengeCoins(), paid.baseCoins(), "the terms sum to the base");
        long levelGrants = 0;
        for (long grant : first.levelRewardsGranted().values()) {
            levelGrants += grant;
        }
        assertEquals(before + paid.coins() + levelGrants, profile.wallet.get(COINS),
                "the wallet moved by the summary plus the level rewards, nothing double-counted");
        assertEquals(paid.coins() + levelGrants, profile.statistics.coinsEarned);
        assertTrue(profile.isUnlocked("world:wind_valley"), "boss.reward.unlocks granted");
        assertTrue(first.unlocksGranted().contains("world:wind_valley"));
        assertEquals(List.of(ProgressionManager.Step.values().length),
                List.of(manager.lastSteps().size()), "D14's order is untouched");

        ProgressionOutcome again = manager.apply(profile,
                runBuilder(32).points(32).ticksAlive(3000).boss("green_fields").build(), shipped);
        assertEquals(150, again.rewardSummary().bossCoins(), "a repeat pays bossBonus only");
        assertFalse(again.unlocksGranted().contains("world:wind_valley"), "granted once");
        assertEquals(2L, profile.statistics.bossClears.get("green_fields"));
    }

    /**
     * M8 (E11): a first challenge completion pays {@code challengeBonus + rewards.coins} and
     * grants the cosmetic; a repeat pays {@code challengeBonus} alone.
     */
    @Test
    void aFirstChallengeCompletionPaysItsRewardAndGrantsItsUnlockOnce() {
        GameContent content = GameContent.load();
        ProgressionRules shipped = ProgressionRules.fromContent(content);
        ProgressionManager manager = new ProgressionManager(time,
                ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));

        ProgressionOutcome first = manager.apply(profile, runBuilder(30).points(30)
                .ticksAlive(2400).challenge("no_shield_1", true).build(), shipped);
        RewardSummary paid = first.rewardSummary();
        assertTrue(first.challengeFirstCompleted());
        assertEquals(100 + 200, paid.challengeCoins(),
                "challengeBonus 100 + rewards.coins 200 (E11)");
        assertEquals(0, paid.bossCoins());
        assertTrue(profile.isUnlocked("cosmetic:classic:ember"), "rewards.unlocks granted");
        assertTrue(first.unlocksGranted().contains("cosmetic:classic:ember"));
        long grants = 0;
        for (long grant : first.levelRewardsGranted().values()) {
            grants += grant;
        }
        assertEquals(paid.coins() + grants, profile.wallet.get(COINS));

        ProgressionOutcome again = manager.apply(profile, runBuilder(33).points(33)
                .ticksAlive(2500).challenge("no_shield_1", true).build(), shipped);
        assertFalse(again.challengeFirstCompleted());
        assertEquals(100, again.rewardSummary().challengeCoins(), "a repeat pays the bonus only");
        assertFalse(again.unlocksGranted().contains("cosmetic:classic:ember"));
        assertEquals(2, profile.statistics.challengesCompleted);

        // A challenge boss never pays the world terms (E26): a boss_corridor_1 completion has
        // an empty bossesCleared and pays challenge coins only.
        ProgressionOutcome corridor = manager.apply(profile, runBuilder(25).points(25)
                .ticksAlive(3000).challenge("boss_corridor_1", true).build(), shipped);
        assertEquals(0, corridor.rewardSummary().bossCoins());
        assertEquals(100 + 500, corridor.rewardSummary().challengeCoins());
        assertTrue(profile.isUnlocked("tier:nightmare"));
        assertTrue(profile.statistics.bossesCleared.isEmpty(), "no world was cleared");
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
