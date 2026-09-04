package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.gameplay.run.RewardContext;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunRewardCalculator;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.support.TestContent;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The run reward formula (E32.a), against the shipped economy numbers. */
class RunRewardCalculatorTest {

    private static final EconomyDef ECONOMY = TestContent.frozen().economy();

    private static RunResult result(RunMode mode, int gates, double points, int ticks,
            int streakSteps, int coinsCollected) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(gates);
        stats.setPoints(points);
        stats.setStreakSteps(streakSteps);
        stats.addCoinsCollected(coinsCollected);
        for (int i = 0; i < ticks; i++) {
            stats.tickAlive();
        }
        RunConfig config = RunConfig.builder(1).mode(mode).build();
        return new RunResult(config, stats, Map.of());
    }

    private static RunResult run(int gates, int ticks) {
        return result(RunMode.STANDARD, gates, gates, ticks, 0, 0);
    }

    @Test
    void theEconomyShipsTheNumbersTheFormulaIsWrittenFor() {
        assertEquals(20, ECONOMY.rewards().participation());
        assertEquals(25, ECONOMY.rewards().firstRunBonus());
        assertEquals(2, ECONOMY.rewards().coinsPerGate());
        assertEquals(1, ECONOMY.rewards().coinsPerPoint());
        assertEquals(5, ECONOMY.rewards().streak().step());
        assertEquals(5, ECONOMY.rewards().streak().coins());
        assertEquals(15, ECONOMY.xp().participation());
        assertEquals(10, ECONOMY.xp().perGate());
    }

    /** The plan's worked example: run 1 with 10 gates pays 20 + 25 + 20 + 10 = 75. */
    @Test
    void theFirstRunWithTenGatesPaysSeventyFive() {
        RewardSummary summary =
                RunRewardCalculator.compute(run(10, 900), ECONOMY, RewardContext.ofFirstRun());
        assertEquals(75, summary.coins());
        assertEquals(20, summary.participation());
        assertEquals(25, summary.firstRunBonus());
        assertEquals(20, summary.gateCoins());
        assertEquals(10, summary.pointCoins());
        assertEquals(0, summary.streakCoins());
        assertEquals(0, summary.bossCoins());
        assertEquals(0, summary.challengeCoins());
        assertEquals(0, summary.coinsCollected());
        assertEquals(75, summary.baseCoins());
        assertEquals(115, summary.xp(), "15 + 10 × 10");
    }

    /** E32.a: a 0-gate instant-retry dive earns nothing but the unconditional first-run bonus. */
    @Test
    void aOneSecondDivePaysOnlyTheFirstRunBonus() {
        RunResult dive = run(0, 60);
        RewardSummary first =
                RunRewardCalculator.compute(dive, ECONOMY, RewardContext.ofFirstRun());
        assertEquals(25, first.coins());
        assertEquals(0, first.participation());
        assertEquals(25, first.firstRunBonus());

        RewardSummary later = RunRewardCalculator.compute(dive, ECONOMY, RewardContext.plain());
        assertEquals(0, later.coins(), "a later 0-gate dive earns nothing at all");
        assertEquals(0, later.xp(), "the participation gate covers the XP term too");
        assertEquals(0, first.xp(), "even on the very first run: only the coin bonus is ungated");
    }

    /**
     * The XP gate is an amendment to E32.a's literal formula, so it gets its own case: an ungated
     * XP participation is worth more than the coin participation it guards, because XP buys levels
     * and levels pay coins.
     */
    @Test
    void theXpParticipationIsGatedLikeTheCoinOne() {
        assertEquals(0, RunRewardCalculator.compute(run(0, 179), ECONOMY, RewardContext.plain())
                .xp(), "179 ticks and no gate: nothing");
        assertEquals(15, RunRewardCalculator.compute(run(0, 180), ECONOMY, RewardContext.plain())
                .xp(), "the same 180-tick threshold opens it");
        assertEquals(25, RunRewardCalculator.compute(run(1, 10), ECONOMY, RewardContext.plain())
                .xp(), "one gate opens it too: 15 + 10");
    }

    @Test
    void theParticipationGateOpensOnTheFirstGateOrAtOneHundredAndEightyTicks() {
        assertEquals(0, RunRewardCalculator.compute(run(0, 179), ECONOMY, RewardContext.plain())
                .participation());
        assertEquals(20, RunRewardCalculator.compute(run(0, 180), ECONOMY, RewardContext.plain())
                .participation());
        assertEquals(20, RunRewardCalculator.compute(run(1, 10), ECONOMY, RewardContext.plain())
                .participation());
    }

    /** E1: points carry {@code SCORE_MULT}, so a score modifier feeds the wallet through them. */
    @Test
    void pointsCarryTheScoreMultiplier() {
        RunResult doubled = result(RunMode.STANDARD, 10, 20, 900, 0, 0);
        RewardSummary summary =
                RunRewardCalculator.compute(doubled, ECONOMY, RewardContext.plain());
        assertEquals(20, summary.gateCoins(), "gates are unchanged");
        assertEquals(20, summary.pointCoins(), "20 points at 1 coin each");
        assertEquals(60, summary.coins(), "20 + 20 + 20");
    }

    @Test
    void streakStepsPayTheirCoins() {
        RunResult streaky = result(RunMode.STANDARD, 20, 20, 1200, 4, 0);
        RewardSummary summary = RunRewardCalculator.compute(streaky, ECONOMY, RewardContext.plain());
        assertEquals(20, summary.streakCoins(), "4 steps × 5 coins");
        assertEquals(20 + 40 + 20 + 20, summary.coins());
    }

    @Test
    void collectedCoinsAreAddedAfterTheMultipliers() {
        RunResult rich = result(RunMode.STANDARD, 10, 10, 900, 0, 13);
        RewardContext ctx = RewardContext.plain().withMultipliers(2, 1, 1, 1);
        RewardSummary summary = RunRewardCalculator.compute(rich, ECONOMY, ctx);
        assertEquals(13, summary.coinsCollected());
        assertEquals(50, summary.baseCoins());
        assertEquals(100 + 13, summary.coins(), "round(50 × 2) + 13");
        assertEquals(100, summary.earnedCoins());
    }

    /** The multipliers compose in the documented order: {@code COIN_MULT × tier × daily}. */
    @Test
    void theMultipliersCompose() {
        RunResult daily = result(RunMode.DAILY, 10, 10, 900, 0, 0);
        RewardContext ctx = RewardContext.plain().withMultipliers(1.2, 1, 1.5, 1.25);
        RewardSummary summary = RunRewardCalculator.compute(daily, ECONOMY, ctx);
        assertEquals(1.2, summary.coinMult());
        assertEquals(1.5, summary.tierMult());
        assertEquals(1.25, summary.dailyMult());
        assertEquals(1.2 * 1.5 * 1.25, summary.totalMultiplier(), 1e-12);
        assertEquals(Math.round(50 * 1.2 * 1.5 * 1.25), summary.coins());
    }

    @Test
    void theDailyMultiplierOnlyAppliesToADailyRun() {
        RewardContext ctx = RewardContext.plain().withMultipliers(1, 1, 1, 1.25);
        RewardSummary standard =
                RunRewardCalculator.compute(run(10, 900), ECONOMY, ctx);
        assertEquals(1.0, standard.dailyMult());
        assertEquals(50, standard.coins());

        RewardSummary daily = RunRewardCalculator.compute(
                result(RunMode.DAILY, 10, 10, 900, 0, 0), ECONOMY, ctx);
        assertEquals(1.25, daily.dailyMult());
        assertEquals(63, daily.coins(), "round(50 × 1.25)");
    }

    @Test
    void bossAndChallengeTermsFeedTheirOwnLine() {
        RunStats stats = new RunStats();
        stats.setGatesPassed(30);
        stats.setPoints(30);
        stats.addBossCleared("green_fields");
        stats.setObjectiveMet(true);
        for (int i = 0; i < 1800; i++) {
            stats.tickAlive();
        }
        RunResult result = new RunResult(
                RunConfig.builder(1).mode(RunMode.CHALLENGE).challengeId("no_shield_1").build(),
                stats, Map.of());
        RewardContext ctx = new RewardContext(false, true, Set.of("green_fields"), 1, 1, 1, 1);

        RewardSummary summary = RunRewardCalculator.compute(result, ECONOMY, ctx);

        assertEquals(150, summary.bossCoins(), "one world boss at 150");
        assertEquals(100, summary.challengeCoins(), "the objective was met");
        assertEquals(20 + 60 + 30 + 150 + 100, summary.coins());
        assertEquals(15 + 300 + 200, summary.xp(), "the boss pays XP too");
    }

    @Test
    void theXpMultiplierOnlyTouchesXp() {
        RewardContext ctx = RewardContext.plain().withMultipliers(1, 2, 1, 1);
        RewardSummary summary = RunRewardCalculator.compute(run(10, 900), ECONOMY, ctx);
        assertEquals(50, summary.coins());
        assertEquals(230, summary.xp());
    }

    @Test
    void everyTermOfTheFormulaIsInTheBreakdown() {
        RunResult result = result(RunMode.DAILY, 12, 24, 1000, 2, 7);
        RewardContext ctx = new RewardContext(true, false, Set.of(), 1.1, 1.0, 1.5, 1.25);
        RewardSummary summary = RunRewardCalculator.compute(result, ECONOMY, ctx);

        assertEquals(20 + 25 + 24 + 24 + 10, summary.baseCoins());
        assertEquals(summary.participation() + summary.firstRunBonus() + summary.gateCoins()
                + summary.pointCoins() + summary.streakCoins() + summary.bossCoins()
                + summary.challengeCoins(), summary.baseCoins());
        assertEquals(Math.round(summary.baseCoins() * summary.totalMultiplier())
                + summary.coinsCollected(), summary.coins());
        assertTrue(summary.coins() > summary.baseCoins());
    }

    /**
     * M6, E32.a: the {@code Σ modifier.streakBonus.coins × streakSteps} term the milestone before
     * pinned to zero. {@code streak_bounty} pays 10 per step on top of the economy's 5, so four
     * steps go from 20 coins to 60 — and a run that took no such card still pays exactly 20.
     */
    @Test
    void modifierStreakBonusesAddToEveryStep() {
        RunResult plain = result(RunMode.STANDARD, 20, 20, 1200, 4, 0);
        assertEquals(20, RunRewardCalculator.compute(plain, ECONOMY, RewardContext.plain())
                .streakCoins());

        RunStats stats = plain.stats().copy();
        stats.addModifierTaken("streak_bounty");
        stats.setModifierStreakCoins(10);
        RunResult drafted = new RunResult(plain.config(), stats, Map.of());
        RewardSummary summary =
                RunRewardCalculator.compute(drafted, ECONOMY, RewardContext.plain());
        assertEquals(60, summary.streakCoins(), "4 steps × (5 + 10)");
        assertEquals(40, summary.coins() - RunRewardCalculator
                .compute(plain, ECONOMY, RewardContext.plain()).coins(),
                "and the extra 40 coins are the only difference the card makes");
    }

    /**
     * M8, E32.a: the first-clear boss and first-completion challenge terms the milestone before
     * pinned to zero, read from the context the progression layer resolves.
     */
    @Test
    void firstClearCoinsRideTheBossAndChallengeTerms() {
        RunStats stats = new RunStats();
        stats.setGatesPassed(30);
        stats.setPoints(30);
        stats.addBossCleared("green_fields");
        stats.setObjectiveMet(true);
        for (int i = 0; i < 1800; i++) {
            stats.tickAlive();
        }
        RunResult result = new RunResult(
                RunConfig.builder(1).mode(RunMode.CHALLENGE).challengeId("no_shield_1").build(),
                stats, Map.of());
        RewardContext first = new RewardContext(false, true, Set.of("green_fields"), 1, 1, 1, 1)
                .withFirstClearCoins(200, 300);
        RewardSummary summary = RunRewardCalculator.compute(result, ECONOMY, first);
        assertEquals(150 + 200, summary.bossCoins(), "bossBonus plus boss.reward.coins");
        assertEquals(100 + 300, summary.challengeCoins(),
                "challengeBonus plus challenge.rewards.coins");
        assertEquals(20 + 60 + 30 + 350 + 400, summary.coins());

        RewardContext repeat = new RewardContext(false, false, Set.of(), 1, 1, 1, 1)
                .withFirstClearCoins(200, 300);
        RewardSummary again = RunRewardCalculator.compute(result, ECONOMY, repeat);
        assertEquals(150, again.bossCoins(), "no first clear: the resolved coins are ignored");
        assertEquals(100, again.challengeCoins(), "no first completion: bonus only (E11)");
    }

    /** E26: a challenge boss is not a world boss — neither boss term nor the boss XP pays. */
    @Test
    void aChallengeBossPaysNoneOfTheWorldBossTerms() {
        RunStats stats = new RunStats();
        stats.setGatesPassed(25);
        stats.setPoints(25);
        stats.setObjectiveMet(true);
        stats.setPhasesReached(1);
        for (int i = 0; i < 2400; i++) {
            stats.tickAlive();
        }
        RunResult result = new RunResult(RunConfig.builder(1).mode(RunMode.CHALLENGE)
                .challengeId("boss_corridor_1").build(), stats, Map.of());
        RewardContext ctx = new RewardContext(false, true, Set.of(), 1, 1, 1, 1)
                .withFirstClearCoins(0, 500);
        RewardSummary summary = RunRewardCalculator.compute(result, ECONOMY, ctx);
        assertEquals(0, summary.bossCoins(), "bossesCleared is empty for a challenge boss");
        assertEquals(100 + 500, summary.challengeCoins());
        assertEquals(15 + 250, summary.xp(), "no xp.bossBonus either");
    }

    /** Two stacks of the same bonus pay twice, because the director sums them per stack. */
    @Test
    void stackedStreakBonusesPayPerStack() {
        RunStats stats = result(RunMode.STANDARD, 20, 20, 1200, 4, 0).stats().copy();
        stats.setModifierStreakCoins(20);
        RunResult drafted = new RunResult(RunConfig.builder(1).build(), stats, Map.of());
        assertEquals(100, RunRewardCalculator.compute(drafted, ECONOMY, RewardContext.plain())
                .streakCoins(), "4 steps × (5 + 20)");
    }

}
