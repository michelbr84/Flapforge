package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The seam between the two halves of M3: the shipped {@code economy.json} becomes
 * {@link ProgressionRules}, the rules run the real {@code RunRewardCalculator}, and the write path
 * credits exactly what the formula produced (E32.a, D13, D14).
 *
 * <p>The numbers asserted here are the ones E32.a spells out for the shipped balance: a first run
 * with 10 gates pays {@code participation 20 + firstRunBonus 25 + coinsPerGate 2 × 10 +
 * coinsPerPoint 1 × 10 = 75} coins and {@code participation 15 + perGate 10 × 10 = 115} XP, which
 * crosses level 2 (100 XP) and pays its 50-coin level reward on top.
 */
class ProgressionEconomyTest {

    private EconomyDef economy;
    private ProgressionRules rules;
    private PlayerProfile profile;
    private ProgressionManager manager;

    @BeforeEach
    void setUp() {
        economy = GameContent.load().economy();
        rules = ProgressionRules.fromEconomy(economy);
        FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
        profile = PlayerProfile.fresh(time.epochMillis()).normalize();
        manager = new ProgressionManager(time);
    }

    private static RunResult run(int gates) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(gates);
        stats.setPoints(gates);
        for (int i = 0; i < gates * 160; i++) {
            stats.tickAlive();
        }
        stats.setDeathCause(CollisionCause.OBSTACLE);
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("gates", (long) gates);
        return new RunResult(RunConfig.classic(42L), stats, counters);
    }

    /**
     * The instant-retry dive, end to end and against the shipped economy: 50 runs of half a second
     * that pass no gate must leave the profile exactly where it started. The coin participation
     * was always gated; the XP one was not, and 400 such dives measured level 21 and 2725 coins —
     * more coins per minute than actually playing the game (docs/BALANCING.md).
     */
    @Test
    void fiftyInstantRetryDivesEarnNothingAtAll() {
        // Run 1 pays the unconditional first-run bonus (E32.a), so the loop starts after it.
        manager.apply(profile, dive(), rules);
        manager.forgetLastRun();
        long afterFirstRun = profile.wallet.get(PlayerProfile.CURRENCY_COINS);
        assertEquals(25, afterFirstRun, "only the first-run bonus");
        long xpAfterFirstRun = profile.xp;

        for (int i = 0; i < 50; i++) {
            manager.apply(profile, dive(), rules);
            manager.forgetLastRun();
        }

        assertEquals(afterFirstRun, profile.wallet.get(PlayerProfile.CURRENCY_COINS),
                "50 dives moved the wallet not at all");
        assertEquals(xpAfterFirstRun, profile.xp, "and paid no experience");
        assertEquals(0, profile.xp);
        assertEquals(1, profile.level, "so no level reward can leak in either");
        assertEquals(51, profile.statistics.totalRuns, "they were still counted as runs");
    }

    /** A 30-tick run with no gate: the shape of an instant-retry dive. */
    private static RunResult dive() {
        RunStats stats = new RunStats();
        for (int i = 0; i < 30; i++) {
            stats.tickAlive();
        }
        stats.setDeathCause(CollisionCause.GROUND);
        return new RunResult(RunConfig.classic(7L), stats, Map.of());
    }

    @Test
    void theRulesCarryTheShippedCurrenciesAndCurve() {
        assertEquals(economy.currencies(), rules.currencies());
        assertEquals(PlayerProfile.CURRENCY_COINS, rules.primaryCurrency());
        assertEquals(economy.xp().curve().maxLevel(), rules.levels().maxLevel());
        assertEquals(economy.xp().curve().base(), rules.levels().base());
        assertEquals(List.of(2, 5, 10, 15, 20, 25), rules.levels().rewardedLevels(),
                "economy.json.xp.levelRewards reaches the curve");
        assertEquals(Map.of(PlayerProfile.CURRENCY_COINS, 50L), rules.levels().rewardsAt(2));
    }

    @Test
    void aFirstRunOfTenGatesPaysTheFormulaOfE32a() {
        ProgressionOutcome outcome = manager.apply(profile, run(10), rules);
        RewardSummary rewards = outcome.rewardSummary();
        assertEquals(20, rewards.participation());
        assertEquals(25, rewards.firstRunBonus());
        assertEquals(20, rewards.gateCoins());
        assertEquals(10, rewards.pointCoins());
        assertEquals(75, rewards.coins(), "20 + 25 + 20 + 10");
        assertEquals(115, rewards.xp(), "15 + 10 x 10");
    }

    @Test
    void theLevelRewardIsCreditedOnTopAndCountsAsEarnedCoins() {
        ProgressionOutcome outcome = manager.apply(profile, run(10), rules);
        assertEquals(List.of(2), outcome.levelUps());
        assertEquals(Map.of(PlayerProfile.CURRENCY_COINS, 50L), outcome.levelRewardsGranted());
        assertEquals(125L, profile.wallet.get(PlayerProfile.CURRENCY_COINS), "75 + 50");
        assertEquals(125, profile.statistics.coinsEarned, "E32.a: every grant counts as earned");
        assertEquals(115, profile.xp);
        assertEquals(2, profile.level);
    }

    @Test
    void theSecondRunNoLongerPaysTheFirstRunBonus() {
        manager.apply(profile, run(10), rules);
        manager.apply(profile, run(10), rules);
        assertEquals(0, manager.lastOutcome().rewardSummary().firstRunBonus());
        assertEquals(50, manager.lastOutcome().rewardSummary().coins(), "20 + 20 + 10");
    }

    @Test
    void aZeroGateDiveEarnsNothingButTheFirstRunBonus() {
        RunStats stats = new RunStats();
        for (int i = 0; i < 30; i++) {
            stats.tickAlive();
        }
        stats.setDeathCause(CollisionCause.GROUND);
        RunResult dive = new RunResult(RunConfig.classic(1L), stats, Map.of());
        ProgressionOutcome outcome = manager.apply(profile, dive, rules);
        assertEquals(0, outcome.rewardSummary().participation(),
                "E32.a gates participation so an instant-retry dive earns nothing");
        assertEquals(25, outcome.rewardSummary().coins(), "the first-run bonus is not gated");
    }

    @Test
    void theTierAndDailyMultipliersReachTheFormula() {
        ProgressionOutcome outcome = manager.apply(profile, run(10), rules,
                new ProgressionRules.RewardMultipliers(1, 1, 1.5, 1.25));
        RewardSummary rewards = outcome.rewardSummary();
        assertEquals(1.5, rewards.tierMult());
        assertEquals(1.0, rewards.dailyMult(), "the daily multiplier applies to a daily run only");
        assertEquals(113, rewards.coins(), "round(75 x 1.5)");
        assertTrue(profile.statistics.coinsEarned >= 113);
    }
}
