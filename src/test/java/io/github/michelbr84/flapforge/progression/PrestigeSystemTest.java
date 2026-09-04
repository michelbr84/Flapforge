package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.PrestigeDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatBreakdown;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PrestigeSystem} against the letter of E23: the level gate and the cap, the baseline
 * snapshot, exactly the listed resets (and the selection fallback E15 forces), exactly the keeps,
 * the badge cosmetic, the {@code PRESTIGE} layer resolving {@code bonusPerPrestige × count}, the
 * "since prestige" reading of every cumulative condition — and, the sentence the erratum ends on,
 * nothing condition-derived re-granted on the next {@code ProgressionManager.apply}.
 */
class PrestigeSystemTest {

    private static GameContent content() {
        return GameContent.load();
    }

    /** A profile standing exactly where a prestige asks for: level 25, nothing else special. */
    private static PlayerProfile eligible() {
        PlayerProfile profile = PlayerProfile.fresh(1000L).normalize();
        profile.level = 25;
        return profile;
    }

    @Test
    void prestigeRequiresLevelTwentyFive() {
        PlayerProfile profile = eligible();
        profile.level = 24;
        Wallet.of(profile).set(PlayerProfile.CURRENCY_COINS, 700);

        assertEquals(PrestigeSystem.Status.LEVEL_TOO_LOW, PrestigeSystem.check(profile, null));
        PrestigeSystem.Result refused = PrestigeSystem.prestige(profile, null);
        assertFalse(refused.ok(), "level 24 cannot prestige");
        assertNull(refused.cosmeticGranted(), "a refused call grants nothing");
        assertEquals(24, profile.level, "a refused call changes nothing");
        assertEquals(700, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS));
        assertEquals(0, profile.prestigeCount);

        profile.level = 25;
        assertEquals(PrestigeSystem.Status.ELIGIBLE, PrestigeSystem.check(profile, null));
        assertTrue(PrestigeSystem.prestige(profile, null).ok());
        assertEquals(1, profile.prestigeCount);
    }

    @Test
    void theCapIsFivePrestiges() {
        PlayerProfile profile = eligible();
        profile.prestigeCount = PlayerProfile.MAX_PRESTIGE_COUNT;
        Wallet.of(profile).set(PlayerProfile.CURRENCY_COINS, 700);

        assertEquals(PrestigeSystem.Status.MAX_REACHED, PrestigeSystem.check(profile, content()));
        PrestigeSystem.Result refused = PrestigeSystem.prestige(profile, content());
        assertFalse(refused.ok());
        assertEquals(PlayerProfile.MAX_PRESTIGE_COUNT, refused.prestigeCount(),
                "the count did not move past the cap");
        assertEquals(PlayerProfile.MAX_PRESTIGE_COUNT, profile.prestigeCount);
        assertEquals(700, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS),
                "a refused call does not reset the wallet");
    }

    @Test
    void theBaselineSnapshotsTheLifetimeTotals() {
        PlayerProfile profile = eligible();
        Statistics stats = profile.statistics;
        stats.totalRuns = 40;
        stats.totalGates = 812;
        stats.coinsEarned = 3000;
        stats.bossesCleared.add("green_fields");
        stats.bossesCleared.add("wind_valley");

        assertTrue(PrestigeSystem.prestige(profile, content()).ok());

        PlayerProfile.PrestigeBaseline baseline = profile.prestigeBaseline;
        assertEquals(40, baseline.totalRuns);
        assertEquals(812, baseline.totalGates);
        assertEquals(3000, baseline.coinsEarned);
        assertEquals(List.of("green_fields", "wind_valley"), baseline.bossesCleared,
                "the frozen " + "boss list is the one the statistics held at the prestige");

        // The statistics are kept, so they keep growing past the baseline; only the difference
        // counts (the "since prestige" tests below read exactly that difference).
        assertEquals(40, stats.totalRuns);
        assertEquals(2, stats.bossesCleared.size());
    }

    @Test
    void prestigeResetsExactlyTheListedValues() {
        PlayerProfile profile = eligible();
        Wallet.of(profile).set(PlayerProfile.CURRENCY_COINS, 500);
        profile.xp = 9200;
        profile.upgrades.put("glide_1", 2);
        profile.abilityLevels.put("shield", 2);
        profile.abilityLevelCap = 4;
        profile.passiveSlotBonus = 1;
        profile.challenges.put("no_shield_1", new PlayerProfile.ChallengeRecord());
        profile.daily.date = "2026-09-03";
        profile.daily.seed = 7;
        profile.daily.worldId = "wind_valley";
        profile.selected.worldId = "void";
        profile.selected.tierId = "nightmare";
        profile.selected.activeAbilityId = "shield";

        assertTrue(PrestigeSystem.prestige(profile, content()).ok());

        assertEquals(0, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS),
                "the wallet is emptied, every currency");
        assertEquals(0, profile.xp);
        assertEquals(1, profile.level);
        assertTrue(profile.upgrades.isEmpty(), "the upgrade nodes are dropped");
        assertTrue(profile.abilityLevels.isEmpty(), "the ability levels are dropped");
        assertEquals(PlayerProfile.DEFAULT_ABILITY_LEVEL_CAP, profile.abilityLevelCap);
        assertEquals(0, profile.passiveSlotBonus);
        assertTrue(profile.challenges.isEmpty(), "the challenge records are dropped");
        assertTrue(profile.daily.date.isEmpty(), "the daily pick is dropped");

        // Not in E23's list, but forced by E15: the implied-unlock repair of PlayerProfile
        // re-grants whatever selected points at, so a kept selection would hand back the world,
        // the tier or the ability the reset just took away — on every load, forever.
        assertEquals(PlayerProfile.DEFAULT_WORLD, profile.selected.worldId);
        assertEquals(PlayerProfile.DEFAULT_TIER, profile.selected.tierId);
        assertEquals(PlayerProfile.DEFAULT_ACTIVE_ABILITY, profile.selected.activeAbilityId);
    }

    @Test
    void prestigeKeepsExactlyTheListedValues() {
        PlayerProfile profile = eligible();
        profile.unlock("bird:swift");
        profile.unlock("cosmetic:swift:default");
        profile.unlock("cosmetic:classic:ember");
        profile.unlock("world:wind_valley");
        profile.unlock("ability:shield");
        profile.unlock("tier:hard");
        profile.unlock("tree:forge");
        profile.unlock("feature:modifiers");
        profile.achievements.put("first_flight", new PlayerProfile.AchievementRecord(1234L));
        profile.statistics.totalRuns = 40;
        profile.statistics.bestGates = 31;
        profile.statistics.runHistory.add(new Statistics.RunHistoryEntry());
        profile.reconciled.add("old_node");

        assertTrue(PrestigeSystem.prestige(profile, content()).ok());

        // Kept: the achievements, with their timestamps.
        assertEquals(1, profile.achievements.size());
        assertEquals(1234L, profile.achievements.get("first_flight").unlockedAtEpochMs);
        // Kept: the lifetime statistics, totals and history alike.
        assertEquals(40, profile.statistics.totalRuns);
        assertEquals(31, profile.statistics.bestGates);
        assertEquals(1, profile.statistics.runHistory.size());
        // Kept: the alias bookkeeping — it exists so a refund is paid exactly once (E21), and a
        // prestige must not arrange a second one.
        assertEquals(List.of("old_node"), profile.reconciled);

        // unlocked := defaults ∪ kept (bird:*, cosmetic:*), defaults first.
        List<String> unlocked = profile.unlocked;
        assertEquals(PlayerProfile.DEFAULT_UNLOCKED, unlocked.subList(0,
                PlayerProfile.DEFAULT_UNLOCKED.size()), "the defaults come first");
        for (String id : PlayerProfile.DEFAULT_UNLOCKED) {
            assertTrue(unlocked.contains(id), "the defaults survive: " + id);
        }
        assertTrue(unlocked.contains("bird:swift"), "birds are kept");
        assertTrue(unlocked.contains("cosmetic:swift:default"), "cosmetics are kept");
        assertTrue(unlocked.contains("cosmetic:classic:ember"), "cosmetics are kept");
        assertFalse(unlocked.contains("world:wind_valley"), "worlds are earned again");
        assertFalse(unlocked.contains("ability:shield"), "abilities are earned again");
        assertFalse(unlocked.contains("tier:hard"), "tiers are earned again");
        assertFalse(unlocked.contains("tree:forge"), "trees are earned again");
        assertFalse(unlocked.contains("feature:modifiers"), "features are earned again");
        assertEquals(new java.util.LinkedHashSet<>(unlocked).size(), unlocked.size(),
                "no duplicates between the defaults and the kept ids");
    }

    @Test
    void theCountClimbsAndTheBadgeCosmeticNamesTheBirdYouStandOn() {
        PlayerProfile profile = eligible();
        profile.unlock("bird:swift");
        profile.selected.birdId = "swift";

        PrestigeSystem.Result first = PrestigeSystem.prestige(profile, content());
        assertTrue(first.ok());
        assertEquals(1, first.prestigeCount());
        assertEquals("cosmetic:swift:prestige", first.cosmeticGranted());
        assertTrue(profile.isUnlocked("cosmetic:swift:prestige"), "E20: every bird has one");
        assertFalse(profile.isUnlocked("cosmetic:classic:prestige"),
                "the other birds' palettes are not granted");

        // The selection fell back to the defaults, so the second prestige badges classic. The
        // climb starts over too: the second prestige has to earn its level again.
        profile.level = 25;
        PrestigeSystem.Result second = PrestigeSystem.prestige(profile, content());
        assertTrue(second.ok());
        assertEquals(2, second.prestigeCount());
        assertEquals("cosmetic:classic:prestige", second.cosmeticGranted());
    }

    @Test
    void thePrestigeLayerResolvesBonusPerPrestigeTimesCount() {
        PlayerProfile profile = eligible();
        assertTrue(PrestigeSystem.effectsOf(profile, null).isEmpty(),
                "a profile that never prestiged carries no layer at all");

        profile.prestigeCount = 3;
        List<StatModifier> effects =
                PrestigeSystem.effectsOf(profile, content());
        assertEquals(3, effects.size(), "one bonus per prestige performed");
        for (StatModifier modifier : effects) {
            assertEquals(StatId.COIN_MULT, modifier.stat());
            assertEquals(StatOp.PERCENT_ADD,
                    modifier.op());
            assertEquals(0.05, modifier.value(), 1e-9);
            assertEquals(PrestigeSystem.SOURCE, modifier.source());
        }

        EffectStack stack = new EffectStack();
        stack.setLayer(Layer.PRESTIGE, effects);
        StatSheet sheet = new StatSheet(Map.of(), stack, RuleSet.EMPTY);
        assertEquals(1.15, sheet.resolve(StatId.COIN_MULT), 1e-9, "1 + 3 × 0.05");
        StatBreakdown breakdown = sheet.breakdown(StatId.COIN_MULT);
        long prestigeEntries = breakdown.contributions().stream()
                .filter(entry -> entry.layer() == Layer.PRESTIGE)
                .count();
        assertEquals(3, prestigeEntries, "the breakdown names the PRESTIGE layer three times");
    }

    @Test
    void cumulativeConditionsReadSincePrestige() {
        PlayerProfile profile = eligible();
        profile.statistics.totalRuns = 10;
        profile.statistics.totalGates = 500;
        profile.statistics.coinsEarned = 3000;
        profile.statistics.bestGates = 30;
        profile.statistics.bossesCleared.add("green_fields");
        profile.achievements.put("first_flight", new PlayerProfile.AchievementRecord(1234L));
        assertTrue(PrestigeSystem.prestige(profile, content()).ok());

        UnlockEvaluator evaluator = UnlockEvaluator.of(content());

        // The four cumulative readings subtract the baseline; every lifetime total is still far
        // past the thresholds, so only the subtraction can explain a refusal.
        assertFalse(evaluator.isSatisfied(runs(5), profile), "runs since prestige = 0");
        assertFalse(evaluator.isSatisfied(totalGates(400), profile),
                "total gates since prestige = 0");
        assertFalse(evaluator.isSatisfied(coinsEarned(2000), profile),
                "coins earned since prestige = 0");
        assertFalse(evaluator.isSatisfied(worldCleared("green_fields"), profile),
                "a boss cleared before the prestige does not count again");
        assertFalse(evaluator.isSatisfied(level(2), profile), "level is the reset level");

        // The statistics keep counting past the baseline, and the difference is what satisfies.
        profile.statistics.totalRuns = 13;
        profile.statistics.bossesCleared.add("wind_valley");
        assertTrue(evaluator.isSatisfied(runs(3), profile), "13 − 10 = 3 runs since prestige");
        assertFalse(evaluator.isSatisfied(runs(4), profile));
        assertTrue(evaluator.isSatisfied(worldCleared("wind_valley"), profile),
                "a boss cleared after the prestige counts");

        // The non-cumulative readings are untouched (E20, E23): achievements and statistics
        // survive, so their conditions keep working.
        assertTrue(evaluator.isSatisfied(bestGates(30), profile), "best_gates stays lifetime");
        assertTrue(evaluator.isSatisfied(achievement("first_flight"), profile),
                "achievement conditions keep working across a prestige");
    }

    @Test
    void nothingConditionDerivedIsRegrantedOnTheNextApply() {
        GameContent content = content();
        UnlockEvaluator evaluator = UnlockEvaluator.of(content);
        ProgressionManager progression = new ProgressionManager(
                new io.github.michelbr84.flapforge.support.FixedTimeSource(1000L),
                ProgressionManager.AchievementHook.NONE, evaluator);

        // A mid-game profile: three unlockables the cumulative conditions have already paid for.
        PlayerProfile profile = eligible();
        profile.statistics.totalRuns = 40;
        profile.statistics.totalGates = 900;
        profile.statistics.coinsEarned = 5000;
        profile.statistics.bossesCleared.add("green_fields");
        profile.statistics.bestGates = 45;
        for (String id : evaluator.evaluate(profile)) {
            profile.unlock(id);
        }
        assertTrue(profile.isUnlocked("world:wind_valley"), "granted before the prestige");
        assertTrue(profile.isUnlocked("ability:shield"), "granted before the prestige");
        assertTrue(profile.isUnlocked("tier:hard"), "granted before the prestige");
        List<String> before = List.copyOf(profile.unlocked);

        assertTrue(PrestigeSystem.prestige(profile, content).ok());
        assertFalse(profile.isUnlocked("world:wind_valley"), "worlds are earned again");
        assertFalse(profile.isUnlocked("ability:shield"), "abilities are earned again");
        List<String> afterPrestige = List.copyOf(profile.unlocked);

        // The next run is written through the real pipeline. Its statistics advance, the unlock
        // pass genuinely re-runs — and the wallet is empty and the baseline is frozen, so
        // wind_valley's world_cleared branch and shield's runs branch both stay shut.
        RunResult result = run(5);
        ProgressionOutcome outcome = progression.apply(profile, result,
                ProgressionRules.fromEconomy(content.economy()));
        assertTrue(profile.statistics.totalRuns > 40, "the run was really written");
        assertFalse(profile.isUnlocked("world:wind_valley"),
                "E23: nothing condition-derived is re-granted");
        assertFalse(profile.isUnlocked("ability:shield"),
                "E23: nothing condition-derived is re-granted");
        assertFalse(outcome.unlocksGranted().contains("world:wind_valley"));
        assertFalse(outcome.unlocksGranted().contains("ability:shield"));

        // Everything the prestige left behind is still there; the only growth is content the
        // profile qualifies for without its pre-prestige history (the default modifiers).
        for (String id : afterPrestige) {
            assertTrue(profile.isUnlocked(id), "the prestige kept " + id);
        }
        assertTrue(profile.unlocked.size() >= afterPrestige.size());
        assertNotEquals(before, profile.unlocked, "the prestige did reset the unlock list");
    }

    @Test
    void theDefaultsFallBackToTheShippedEconomy() {
        PrestigeDef def = PrestigeSystem.defOf(null);
        assertEquals(25, def.requiredLevel(), "E23: level ≥ 25");
        assertEquals(5, def.maxPrestige(), "E4: max five prestiges");
        assertEquals(PrestigeDef.KEEPS, def.keeps());
        assertEquals(1, def.bonusPerPrestige().size());
        assertEquals(StatId.COIN_MULT, def.bonusPerPrestige().get(0).stat());
        assertEquals(0.05, def.bonusPerPrestige().get(0).value(), 1e-9);

        // The shipped economy agrees with the defaults, so a data change is a conscious one.
        assertEquals(25, PrestigeSystem.defOf(content()).requiredLevel());
        assertEquals(5, PrestigeSystem.defOf(content()).maxPrestige());
    }

    private static UnlockConditionDef runs(double value) {
        return new UnlockConditionDef(UnlockType.RUNS, value, null, 0, null, List.of());
    }

    private static UnlockConditionDef totalGates(double value) {
        return new UnlockConditionDef(UnlockType.TOTAL_GATES, value, null, 0, null, List.of());
    }

    private static UnlockConditionDef coinsEarned(double value) {
        return new UnlockConditionDef(UnlockType.COINS_EARNED_TOTAL, value, null, 0, null,
                List.of());
    }

    private static UnlockConditionDef bestGates(double value) {
        return new UnlockConditionDef(UnlockType.BEST_GATES, value, null, 0, null, List.of());
    }

    private static UnlockConditionDef level(double value) {
        return new UnlockConditionDef(UnlockType.LEVEL, value, null, 0, null, List.of());
    }

    private static UnlockConditionDef worldCleared(String worldId) {
        return new UnlockConditionDef(UnlockType.WORLD_CLEARED, 0, worldId, 0, null, List.of());
    }

    private static UnlockConditionDef achievement(String achievementId) {
        return new UnlockConditionDef(UnlockType.ACHIEVEMENT, 0, achievementId, 0, null,
                List.of());
    }

    private static RunResult run(int gates) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(gates);
        stats.setPoints(gates);
        stats.addCoinsCollected(3);
        stats.setStreak(gates);
        for (int i = 0; i < gates * 60; i++) {
            stats.tickAlive();
        }
        stats.setDeathCause(CollisionCause.OBSTACLE);
        Map<String, Long> counters = new LinkedHashMap<>();
        return new RunResult(RunConfig.classic(gates), stats, counters);
    }
}
