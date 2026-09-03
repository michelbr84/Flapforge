package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * §6 M8: the two unlock chains a boss and a challenge open, played end to end through the shipped
 * content — the perfect pilot clears the Green Fields boss and the profile owns
 * {@code world:wind_valley}; it completes {@code no_shield_1} and owns
 * {@code cosmetic:classic:ember}. Every coin of the first clear reaches the wallet and the
 * summary's terms add up to what was paid (E11, E26, E32.a).
 */
class UnlockChainTest {

    private static final int TICKS = 20_000;
    private static final int SEEDS_TRIED = 12;

    private final GameContent content = GameContent.load();
    private final RunFactory runs = new RunFactory(content);
    private final FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
    private final ProgressionManager progression = new ProgressionManager(time,
            ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
    private final ProgressionRules rules = ProgressionRules.fromContent(content);

    /** The first seed from {@code 1} whose perfect run satisfies the predicate. */
    private Run firstRunThat(java.util.function.Function<Long, RunConfig> configs,
            java.util.function.Predicate<Run> done) {
        for (long seed = 1; seed <= SEEDS_TRIED; seed++) {
            Run run = runs.newRun(configs.apply(seed));
            HeadlessRunner.run(run, new BotPilot(BotPilot.Preset.PERFECT, seed), TICKS);
            if (done.test(run)) {
                return run;
            }
        }
        throw new AssertionError("no perfect run satisfied the chain within " + SEEDS_TRIED
                + " seeds");
    }

    private static RunResult finished(Run run) {
        while (!run.isFinished()) {
            run.tick(io.github.michelbr84.flapforge.gameplay.run.RunInput.NONE);
        }
        return run.result();
    }

    @Test
    void clearingTheGreenFieldsBossOpensWindValleyOnceAndPaysItsRewardOnce() {
        PlayerProfile profile = PlayerProfile.fresh(time.epochMillis()).normalize();
        assertFalse(profile.isUnlocked("world:wind_valley"));
        Run run = firstRunThat(
                seed -> RunLoadout.configFor(profile, content, seed, RunMode.STANDARD),
                r -> r.stats().bossesCleared().contains("green_fields"));
        assertTrue(run.config().bossEnabled(), "a profile run has its boss on");
        RunResult result = finished(run);
        long before = profile.wallet.getOrDefault(PlayerProfile.CURRENCY_COINS, 0L);

        ProgressionOutcome outcome = progression.apply(profile, result, rules);

        RewardSummary paid = outcome.rewardSummary();
        assertEquals(150 + 200, paid.bossCoins(), "bossBonus plus the first-clear reward");
        assertTrue(profile.isUnlocked("world:wind_valley"), "the chain: boss → world");
        assertTrue(outcome.unlocksGranted().contains("world:wind_valley"));
        assertEquals(List.of("green_fields"), profile.statistics.bossesCleared);
        assertEquals(1L, profile.statistics.bossClears.get("green_fields"));
        long levelGrants = 0;
        for (long grant : outcome.levelRewardsGranted().values()) {
            levelGrants += grant;
        }
        assertEquals(before + paid.coins() + levelGrants,
                profile.wallet.get(PlayerProfile.CURRENCY_COINS),
                "the wallet moved by exactly the summary plus the level rewards");
        assertEquals(paid.participation() + paid.firstRunBonus() + paid.gateCoins()
                + paid.pointCoins() + paid.streakCoins() + paid.bossCoins()
                + paid.challengeCoins(), paid.baseCoins(), "the terms add up");
        assertEquals(Math.round(paid.baseCoins() * paid.totalMultiplier())
                + paid.coinsCollected(), paid.coins());

        // The second clear: bossBonus only, nothing granted.
        progression.forgetLastRun();
        Run again = firstRunThat(
                seed -> RunLoadout.configFor(profile, content, 100 + seed, RunMode.STANDARD),
                r -> r.stats().bossesCleared().contains("green_fields"));
        ProgressionOutcome repeat = progression.apply(profile, finished(again), rules);
        assertEquals(150, repeat.rewardSummary().bossCoins(), "a repeat pays bossBonus alone");
        assertFalse(repeat.unlocksGranted().contains("world:wind_valley"));
        assertEquals(2L, profile.statistics.bossClears.get("green_fields"));
        assertEquals(List.of("green_fields"), profile.statistics.bossesCleared, "listed once");
    }

    @Test
    void completingNoShieldOpensTheEmberPaletteOnceAndPaysItsRewardOnce() {
        PlayerProfile profile = PlayerProfile.fresh(time.epochMillis()).normalize();
        assertFalse(profile.isUnlocked("cosmetic:classic:ember"));
        Run run = firstRunThat(
                seed -> RunLoadout.challengeConfigFor(profile, content, seed, "no_shield_1"),
                r -> r.stats().objectiveMet());
        assertEquals(RunMode.CHALLENGE, run.config().mode());
        assertEquals("green_fields", run.config().worldId());
        assertEquals("standard", run.setup().world().curve().id(), "the challenge's curve");
        assertTrue(run.setup().boss() == null, "no_shield_1 has no boss block, so no boss (E26)");
        RunResult result = finished(run);
        assertTrue(result.stats().gatesPassed() >= 30);

        ProgressionOutcome first = progression.apply(profile, result, rules);
        assertTrue(first.challengeFirstCompleted());
        assertEquals(100 + 200, first.rewardSummary().challengeCoins(),
                "challengeBonus plus the first-completion reward (E11)");
        assertEquals(0, first.rewardSummary().bossCoins(), "no boss in this challenge");
        assertTrue(profile.isUnlocked("cosmetic:classic:ember"), "the chain: challenge → palette");
        assertTrue(first.unlocksGranted().contains("cosmetic:classic:ember"));
        assertTrue(profile.challenges.get("no_shield_1").completed);

        progression.forgetLastRun();
        Run again = firstRunThat(
                seed -> RunLoadout.challengeConfigFor(profile, content, 100 + seed, "no_shield_1"),
                r -> r.stats().objectiveMet());
        ProgressionOutcome repeat = progression.apply(profile, finished(again), rules);
        assertFalse(repeat.challengeFirstCompleted());
        assertEquals(100, repeat.rewardSummary().challengeCoins(),
                "a repeat pays challengeBonus alone");
        assertFalse(repeat.unlocksGranted().contains("cosmetic:classic:ember"));
        assertEquals(2, profile.statistics.challengesCompleted);
    }
}
