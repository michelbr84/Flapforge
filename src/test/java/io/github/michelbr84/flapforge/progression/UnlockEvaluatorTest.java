package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link UnlockEvaluator}: every condition type of D13 and E20 against the shipped content, the
 * "since prestige" reading of the cumulative types (E23), and the two rules that are not in the
 * JSON — {@code purchase} is never earned, and a palette needs its bird.
 */
class UnlockEvaluatorTest {

    private static GameContent content;

    private PlayerProfile profile;
    private UnlockEvaluator evaluator;

    @BeforeAll
    static void loadContent() {
        content = GameContent.load();
    }

    @BeforeEach
    void setUp() {
        profile = PlayerProfile.fresh(0).normalize();
        evaluator = UnlockEvaluator.of(content);
    }

    @Test
    void aFreshProfileAlreadyOwnsEveryDefaultAndNothingElse() {
        // E18's default set is exactly what the data says is default, once the palette rule has
        // been applied: the other birds' default palettes wait for their bird.
        //
        // The one part of E18's set that is not in PlayerProfile.DEFAULT_UNLOCKED is the
        // modifiers that ship with "unlock": "default" (M6): they are content, not code, so the
        // evaluator hands them over on the first evaluation and nothing else is pending.
        assertEquals(defaultModifiers(), evaluator.evaluate(profile),
                "a fresh profile has only the default modifiers left to be granted");
        assertTrue(profile.isUnlocked("bird:classic"));
        assertTrue(profile.isUnlocked("cosmetic:classic:default"));
        assertFalse(profile.isUnlocked("cosmetic:swift:default"));
    }

    @Test
    void defaultIsAlwaysSatisfied() {
        assertTrue(evaluator.isSatisfied(UnlockConditionDef.DEFAULT, profile));
    }

    @Test
    void bestGatesReadsTheLifetimeBest() {
        assertFalse(evaluator.evaluate(profile).contains("bird:swift"));
        profile.statistics.bestGates = 14;
        assertFalse(evaluator.evaluate(profile).contains("bird:swift"));
        profile.statistics.bestGates = 15;
        assertTrue(evaluator.evaluate(profile).contains("bird:swift"),
                "swift is any_of[best_gates 15, purchase 300]");
    }

    @Test
    void bestPointsReadsTheLifetimeBest() {
        UnlockConditionDef condition = threshold(UnlockType.BEST_POINTS, 100);
        assertFalse(evaluator.isSatisfied(condition, profile));
        profile.statistics.bestPoints = 100;
        assertTrue(evaluator.isSatisfied(condition, profile));
    }

    @Test
    void totalGatesIsCumulativeAndCountsSincePrestige() {
        UnlockConditionDef condition = threshold(UnlockType.TOTAL_GATES, 500);
        profile.statistics.totalGates = 500;
        assertTrue(evaluator.isSatisfied(condition, profile));
        profile.prestigeBaseline.totalGates = 1;
        assertFalse(evaluator.isSatisfied(condition, profile),
                "E23: a cumulative total is counted since the last prestige");
    }

    @Test
    void runsCountSincePrestige() {
        profile.statistics.totalRuns = 3;
        assertTrue(evaluator.evaluate(profile).contains("bird:guardian"),
                "Ironbeak is any_of[runs 3, purchase 150]");
        profile.prestigeBaseline.totalRuns = 3;
        assertFalse(evaluator.evaluate(profile).contains("bird:guardian"),
                "E23: the three runs were played before the prestige");
        profile.statistics.totalRuns = 6;
        assertTrue(evaluator.evaluate(profile).contains("bird:guardian"));
    }

    @Test
    void levelReadsTheCurrentLevelNotALifetimeOne() {
        profile.level = 2;
        assertFalse(evaluator.evaluate(profile).contains("tree:economy"));
        profile.level = 3;
        assertTrue(evaluator.evaluate(profile).contains("tree:economy"),
                "the economy tree is any_of[level 3, purchase 120]");
        // A prestige resets the level itself (E23), so no baseline is subtracted here.
        profile.prestigeCount = 1;
        profile.prestigeBaseline.totalRuns = 40;
        assertTrue(evaluator.evaluate(profile).contains("tree:economy"));
    }

    @Test
    void coinsEarnedTotalCountsSincePrestige() {
        profile.statistics.coinsEarned = 500;
        assertTrue(evaluator.evaluate(profile).contains("ability:coin_magnet"));
        profile.prestigeBaseline.coinsEarned = 200;
        assertFalse(evaluator.evaluate(profile).contains("ability:coin_magnet"));
    }

    @Test
    void challengeNeedsACompletedRecord() {
        profile.challenge("no_shield_1").attempts = 4;
        assertFalse(evaluator.evaluate(profile).contains("cosmetic:classic:ember"),
                "attempts are not completions");
        profile.challenge("no_shield_1").completed = true;
        assertTrue(evaluator.evaluate(profile).contains("cosmetic:classic:ember"));
    }

    @Test
    void achievementNeedsTheRecord() {
        assertFalse(evaluator.evaluate(profile).contains("bird:mystic"));
        profile.achievements.put("ability_adept", new PlayerProfile.AchievementRecord(1));
        assertTrue(evaluator.evaluate(profile).contains("bird:mystic"),
                "Oracle is any_of[achievement ability_adept, purchase 600]");
    }

    @Test
    void worldClearedReadsTheBossListSincePrestige() {
        profile.statistics.recordBossClear("green_fields");
        List<String> granted = evaluator.evaluate(profile);
        assertTrue(granted.contains("world:wind_valley"));
        assertTrue(granted.contains("bird:forge"));
        profile.prestigeBaseline.bossesCleared.add("green_fields");
        assertFalse(evaluator.evaluate(profile).contains("world:wind_valley"),
                "E23: a boss cleared before the prestige does not count again");
    }

    @Test
    void purchaseIsNeverEarned() {
        Wallet.of(profile).add(PlayerProfile.CURRENCY_COINS, 1_000_000);
        assertEquals(defaultModifiers(), evaluator.evaluate(profile),
                "a purchase branch means 'buyable', never 'earned'");
        assertFalse(evaluator.isSatisfied(evaluator.conditionOf("bird:heavy"), profile));
        assertEquals(200, evaluator.priceOf("bird:heavy"));
        assertEquals(-1, evaluator.priceOf("bird:classic"), "the default bird is not for sale");
        assertEquals(-1, evaluator.priceOf("nothing:at:all"));
    }

    @Test
    void prestigeUnlocksTheCosmeticOfTheBirdsYouOwn() {
        assertFalse(evaluator.evaluate(profile).contains("cosmetic:classic:prestige"));
        profile.prestigeCount = 1;
        List<String> granted = evaluator.evaluate(profile);
        assertTrue(granted.contains("cosmetic:classic:prestige"), "E20: every bird has one");
        assertFalse(granted.contains("cosmetic:swift:prestige"),
                "a palette of a bird nobody owns is not granted");
        profile.unlock("bird:swift");
        assertTrue(evaluator.evaluate(profile).contains("cosmetic:swift:prestige"));
    }

    @Test
    void counterReadsACollectionPercentage() {
        profile.unlock("bird:forge");
        assertFalse(evaluator.evaluate(profile).contains("cosmetic:forge:molten"));
        int total = 0;
        for (UpgradeDef node : content.upgrades()) {
            total += node.maxLevel();
        }
        // Half of every upgrade level in the game, node by node, until the percentage tips over.
        int owned = 0;
        for (UpgradeDef node : content.upgrades()) {
            if (100L * owned / total >= 50) {
                break;
            }
            profile.upgrades.put(node.id(), node.maxLevel());
            owned += node.maxLevel();
        }
        assertEquals(owned, profile.upgradeLevelsTotal());
        assertTrue(100L * owned / total >= 50, "the loop stops once the counter is past 50 %");
        assertEquals(100L * owned / total,
                evaluator.counter("collection.upgrades.percent", profile));
        assertTrue(evaluator.evaluate(profile).contains("cosmetic:forge:molten"),
                "E20: molten is counter collection.upgrades.percent >= 50");
    }

    @Test
    void collectionCountersSeeTheGrantsOfTheSamePass() {
        // The fixed point matters: owning every bird raises collection.birds.percent, which a
        // cosmetic could be gated on, so one pass must not stop at the first round of grants.
        profile.statistics.totalRuns = 40;
        profile.statistics.bestGates = 40;
        profile.statistics.coinsEarned = 5000;
        profile.level = 10;
        List<String> granted = evaluator.evaluate(profile);
        assertTrue(granted.contains("bird:heavy"));
        assertTrue(granted.contains("cosmetic:heavy:default"),
                "a bird granted in one round brings its default palette in the next");
        assertTrue(granted.indexOf("bird:heavy") < granted.indexOf("cosmetic:heavy:default"),
                "grants are listed in the order they were earned");
    }

    @Test
    void allOfNeedsEveryBranchAndAnyOfNeedsOne() {
        UnlockConditionDef runs = threshold(UnlockType.RUNS, 3);
        UnlockConditionDef level = threshold(UnlockType.LEVEL, 5);
        UnlockConditionDef allOf = new UnlockConditionDef(UnlockType.ALL_OF, 0, null, 0, null,
                List.of(runs, level));
        UnlockConditionDef anyOf = new UnlockConditionDef(UnlockType.ANY_OF, 0, null, 0, null,
                List.of(runs, level));
        profile.statistics.totalRuns = 3;
        assertFalse(evaluator.isSatisfied(allOf, profile));
        assertTrue(evaluator.isSatisfied(anyOf, profile));
        profile.level = 5;
        assertTrue(evaluator.isSatisfied(allOf, profile));
        UnlockConditionDef emptyAny = new UnlockConditionDef(UnlockType.ANY_OF, 0, null, 0, null,
                List.of());
        assertFalse(evaluator.isSatisfied(emptyAny, profile), "an empty any_of is never true");
    }

    @Test
    void anUnknownConditionIsNeverSatisfied() {
        assertFalse(evaluator.isSatisfied(null, profile));
        assertFalse(evaluator.isSatisfied(
                new UnlockConditionDef(UnlockType.COUNTER, 1, null, 0, "collection.nope.percent",
                        List.of()), profile));
        assertFalse(evaluator.isSatisfied(
                new UnlockConditionDef(UnlockType.COUNTER, 1, null, 0, "notACounter", List.of()),
                profile));
    }

    @Test
    void theStaticFormIsTheSameAnswer() {
        profile.statistics.totalRuns = 3;
        assertEquals(evaluator.evaluate(profile),
                UnlockEvaluator.evaluateAll(profile, content));
    }

    @Test
    void alreadyOwnedIdsAreNeverListedTwice() {
        profile.statistics.totalRuns = 3;
        List<String> first = evaluator.evaluate(profile);
        assertTrue(first.contains("bird:guardian"));
        for (String id : first) {
            profile.unlock(id);
        }
        assertEquals(List.of(), evaluator.evaluate(profile));
    }

    /**
     * D13: a {@code purchase} branch is a shop price only where paying for it is enough. Nested
     * in an {@code all_of} it is one requirement among several, so pricing it would sell an
     * unlockable the siblings still gate. The validator refuses that shape; this is the second
     * lock on the same door.
     */
    @Test
    void aPurchaseNestedInAnAllOfIsNotAPrice() {
        UnlockConditionDef purchase = new UnlockConditionDef(UnlockType.PURCHASE, 0, null, 250,
                null, List.of());
        UnlockConditionDef cleared = new UnlockConditionDef(UnlockType.WORLD_CLEARED, 0, "void", 0,
                null, List.of());
        assertEquals(250, UnlockEvaluator.priceOf(purchase), "at the root it is the price");
        assertEquals(250, UnlockEvaluator.priceOf(new UnlockConditionDef(UnlockType.ANY_OF, 0,
                null, 0, null, List.of(cleared, purchase))), "and under an any_of");
        assertEquals(-1, UnlockEvaluator.priceOf(new UnlockConditionDef(UnlockType.ALL_OF, 0, null,
                0, null, List.of(cleared, purchase))), "but never under an all_of");
        assertEquals(-1, UnlockEvaluator.priceOf(new UnlockConditionDef(UnlockType.ANY_OF, 0, null,
                0, null, List.of(new UnlockConditionDef(UnlockType.ALL_OF, 0, null, 0, null,
                        List.of(cleared, purchase))))),
                "nor under an all_of that an any_of holds");
    }

    private static UnlockConditionDef threshold(UnlockType type, double value) {
        return new UnlockConditionDef(type, value, null, 0, null, List.of());
    }

    /**
     * The modifiers {@code modifiers.json} ships as {@code unlock: default} (M6). They are
     * content, so {@code PlayerProfile.DEFAULT_UNLOCKED} does not list them and the evaluator
     * grants them the first time it runs.
     *
     * @return the namespaced ids, in content order
     */
    private static List<String> defaultModifiers() {
        List<String> ids = new ArrayList<>();
        for (ModifierDef def : content.modifiers()) {
            if (def.unlock().type() == UnlockType.DEFAULT) {
                ids.add(def.unlockableId());
            }
        }
        return ids;
    }
}
