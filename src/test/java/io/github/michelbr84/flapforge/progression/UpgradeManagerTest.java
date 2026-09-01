package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.AliasDef;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.StatBreakdown;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link UpgradeManager}: what a bought node costs, what it needs, what it grants (E3, E31.f) and
 * — the point of the milestone — what it does to the numbers the bird flies with (D8).
 */
class UpgradeManagerTest {

    private static final String COINS = PlayerProfile.CURRENCY_COINS;
    private static final double EPS = 1e-9;

    private static GameContent content;

    private PlayerProfile profile;
    private ProgressionManager progression;
    private UpgradeManager manager;
    private int saves;

    @BeforeAll
    static void loadContent() {
        content = GameContent.load();
    }

    @BeforeEach
    void setUp() {
        profile = PlayerProfile.fresh(0).normalize();
        progression = new ProgressionManager(new FixedTimeSource(1_700_000_000_000L),
                ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
        saves = 0;
        manager = new UpgradeManager(progression, () -> saves++);
    }

    private void give(long coins) {
        Wallet.of(profile).add(COINS, coins);
    }

    private long balance() {
        return Wallet.of(profile).balance(COINS);
    }

    private PurchaseResult buy(String nodeId) {
        return manager.buy(profile, nodeId, content);
    }

    @Test
    void featherOneMakesGravityOneThousandSevenHundredAndFortySix() {
        give(50);
        PurchaseResult result = buy("feather_1");

        assertTrue(result.ok(), () -> "refused with " + result.status());
        assertEquals(1, result.level());
        assertEquals(50, result.cost());
        assertEquals(0, balance());
        assertEquals(1, profile.upgradeLevel("feather_1"));
        assertEquals(50, profile.statistics.coinsSpent);
        assertEquals(1, saves);

        StatSheet stats = RunLoadout.previewStats(profile, content);
        assertEquals(1746, stats.resolve(StatId.GRAVITY), EPS,
                "1800 with a PERCENT_ADD of -0.03 is 1746");

        StatBreakdown breakdown = stats.breakdown(StatId.GRAVITY);
        assertEquals(1800, breakdown.base(), EPS);
        assertEquals(-0.03, breakdown.percentSum(), EPS);
        EffectStack.Entry entry = only(breakdown, "upgrade:feather_1");
        assertEquals(Layer.UPGRADES, entry.layer(), "an owned node lives in the UPGRADES layer");
        assertEquals(-0.03, entry.modifier().value(), EPS);
    }

    @Test
    void flatAndPercentScaleLinearlyWithTheLevel() {
        give(50 + 120 + 250);
        assertEquals(50, UpgradeManager.nextCost(profile, "feather_1", content));
        assertTrue(buy("feather_1").ok());
        assertEquals(120, UpgradeManager.nextCost(profile, "feather_1", content));
        assertTrue(buy("feather_1").ok());
        assertEquals(250, UpgradeManager.nextCost(profile, "feather_1", content));
        PurchaseResult third = buy("feather_1");

        assertEquals(3, third.level());
        assertEquals(0, balance(), "50 + 120 + 250 is the whole cost table");
        assertEquals(420, profile.statistics.coinsSpent);
        assertEquals(1800 * (1 - 0.09), RunLoadout.previewStats(profile, content)
                .resolve(StatId.GRAVITY), EPS, "three levels of -0.03 are -0.09, not -0.03 cubed");
        assertEquals(-1, UpgradeManager.nextCost(profile, "feather_1", content));
        assertEquals(PurchaseStatus.MAX_LEVEL, buy("feather_1").status());
    }

    @Test
    void multiplyCompoundsWithTheLevel() {
        give(150 + 300);
        assertTrue(buy("quick_recharge_1").ok());
        assertEquals(0.92, cooldownMult(), EPS);
        assertTrue(buy("quick_recharge_1").ok());
        assertEquals(0.92 * 0.92, cooldownMult(), EPS, "MULTIPLY compounds as value^level");

        StatBreakdown breakdown = RunLoadout.previewStats(profile, content)
                .breakdown(StatId.ABILITY_COOLDOWN_MULT);
        assertEquals(0.8464, breakdown.multiplyProduct(), EPS);
        assertEquals(0.8464, only(breakdown, "upgrade:quick_recharge_1").modifier().value(), EPS);
    }

    private double cooldownMult() {
        return RunLoadout.previewStats(profile, content).resolve(StatId.ABILITY_COOLDOWN_MULT);
    }

    @Test
    void aPrerequisiteMustBeOwnedAtLevelOne() {
        give(1000);
        assertFalse(UpgradeManager.isAvailable(profile, "slim_frame_1", content));
        assertEquals(PurchaseStatus.MISSING_PREREQ, buy("slim_frame_1").status());
        assertEquals(1000, balance());

        assertTrue(buy("feather_1").ok());
        assertTrue(UpgradeManager.isAvailable(profile, "slim_frame_1", content));
        assertTrue(buy("slim_frame_1").ok());
        assertEquals(1000 - 50 - 150, balance());
    }

    @Test
    void aNodeOfALockedTreeCannotBeBought() {
        give(1000);
        assertFalse(profile.isUnlocked("tree:economy"));
        PurchaseResult result = buy("coin_purse_1");

        assertEquals(PurchaseStatus.TREE_LOCKED, result.status());
        assertEquals(80, result.cost(), "the price is still reported, so the shop can show it");
        assertEquals(1000, balance());
        assertEquals(0, profile.upgradeLevel("coin_purse_1"));
        assertFalse(progression.isDirty());
    }

    @Test
    void anUnknownNodeIsRefused() {
        give(1000);
        assertEquals(PurchaseStatus.UNKNOWN_ID, buy("feather_9").status());
        assertEquals(PurchaseStatus.UNKNOWN_ID, buy(null).status());
        assertEquals(-1, UpgradeManager.nextCost(profile, "feather_9", content));
        assertEquals(1000, balance());
        assertEquals(0, saves);
    }

    @Test
    void anUnaffordableLevelLeavesEverythingUntouched() {
        give(49);
        PurchaseResult result = buy("feather_1");

        assertEquals(PurchaseStatus.INSUFFICIENT_FUNDS, result.status());
        assertEquals(50, result.cost());
        assertEquals(49, balance());
        assertEquals(0, profile.upgradeLevel("feather_1"));
        assertTrue(profile.upgrades.isEmpty(), "no half-bought node is left behind");
        assertEquals(0, profile.statistics.coinsSpent);
        assertFalse(progression.isDirty());
        assertEquals(0, saves);
        assertEquals(1800, RunLoadout.previewStats(profile, content).resolve(StatId.GRAVITY), EPS);
    }

    @Test
    void anUnlockGrantIsAppliedWhenTheNodeReachesLevelOne() {
        give(1000);
        profile.unlock("tree:economy");
        assertTrue(buy("coin_purse_1").ok());
        PurchaseResult result = buy("hard_tier_1");

        assertTrue(result.ok());
        assertEquals(List.of("tier:hard"), result.granted());
        assertTrue(profile.isUnlocked("tier:hard"), "E31.f: an UNLOCK grant is an unlock");
    }

    @Test
    void theAbilityCapGrantIsClampedToTheLevelsAbilitiesShip() {
        give(10_000);
        profile.unlock("tree:forge");
        assertTrue(buy("ability_forge_1").ok());
        assertTrue(buy("cooldown_forge_1").ok());
        assertEquals(PlayerProfile.DEFAULT_ABILITY_LEVEL_CAP, profile.abilityLevelCap);

        assertTrue(buy("master_forge_1").ok());
        assertEquals(3, profile.abilityLevelCap, "E3: base cap 2 plus the single ability_cap:1");
        assertEquals(3, UpgradeManager.maxAbilityLevelCap(content),
                "the ceiling is the thinnest ability's level count");
        assertEquals(3, UpgradeManager.abilityLevelCeiling(content),
                "E3: base cap 2 plus every ability_cap grant, never above the ability levels");

        // With the cap already at the ceiling the node has nothing left to grant, so the purchase
        // is refused before the debit rather than taking 1200 coins for nothing.
        profile.upgrades.remove("master_forge_1");
        long before = balance();
        PurchaseResult again = buy("master_forge_1");
        assertEquals(PurchaseStatus.ALREADY_OWNED, again.status());
        assertEquals(before, balance(), "a refused purchase debits nothing");
        assertEquals(3, profile.abilityLevelCap, "the grant never pushes the cap past the ceiling");
        assertFalse(UpgradeManager.isAvailable(profile, "master_forge_1", content));
    }

    @Test
    void thePassiveSlotGrantIsClampedToOne() {
        give(10_000);
        profile.unlock("tree:economy");
        assertTrue(buy("scholar_1").ok());
        assertTrue(buy("lodestone_1").ok());
        assertTrue(buy("ability_scholar_1").ok());
        assertEquals(1, profile.passiveSlotBonus, "E3: passive_slot:1");

        profile.upgrades.remove("ability_scholar_1");
        long before = balance();
        PurchaseResult again = buy("ability_scholar_1");
        assertEquals(PurchaseStatus.ALREADY_OWNED, again.status(),
                "the slot bonus is already at its maximum, so the node would grant nothing");
        assertEquals(before, balance(), "a refused purchase debits nothing");
        assertEquals(PlayerProfile.MAX_PASSIVE_SLOT_BONUS, profile.passiveSlotBonus);
    }

    /**
     * {@code hard_tier_1} has no effects and its only value is granting {@code tier:hard}, which
     * {@code difficulty.json} also gives away for 400 lifetime gates. A player who walks that path
     * first must not be able to spend 400 coins on nothing.
     */
    @Test
    void aNodeWhoseOnlyGrantIsAlreadyOwnedIsRefusedBeforeTheDebit() {
        give(10_000);
        profile.unlock("tree:economy");
        assertTrue(buy("coin_purse_1").ok());
        assertTrue(UpgradeManager.isAvailable(profile, "hard_tier_1", content),
                "it is a normal buy while tier:hard is not owned");

        profile.unlock("tier:hard");
        long before = balance();
        PurchaseResult result = buy("hard_tier_1");

        assertEquals(PurchaseStatus.ALREADY_OWNED, result.status());
        assertEquals(before, balance(), "the wallet is untouched");
        assertEquals(0, profile.upgradeLevel("hard_tier_1"));
        assertFalse(UpgradeManager.isAvailable(profile, "hard_tier_1", content));
        assertTrue(UpgradeManager.isRedundant(profile, "hard_tier_1", content));
        assertFalse(UpgradeManager.isRedundant(profile, "coin_rain_1", content),
                "a node with effects is never redundant");
    }

    @Test
    void aGrantIsAppliedOnceNotOnEveryLevel() {
        give(10_000);
        profile.unlock("tree:forge");
        assertTrue(buy("tempered_shield_1").ok());
        assertEquals(1, RunLoadout.previewStats(profile, content).resolve(StatId.SHIELD_CHARGES),
                EPS);
        PurchaseResult second = buy("tempered_shield_1");
        assertEquals(2, second.level());
        assertEquals(List.of(), second.granted(), "grants belong to level 1");
        assertEquals(2, RunLoadout.previewStats(profile, content).resolve(StatId.SHIELD_CHARGES),
                EPS);
    }

    @Test
    void cinderSynergyResolvesOnceFromTheOwnedLevels() {
        give(10_000);
        profile.unlock("bird:forge");
        profile.unlock("cosmetic:forge:default");
        profile.selected.birdId = "forge";
        StatSheet before = RunLoadout.previewStats(profile, content);
        assertEquals(385, before.resolve(StatId.FLAP_VELOCITY), EPS, "Cinder's base flap");
        assertEquals(1, before.resolve(StatId.COIN_MULT), EPS);

        assertTrue(buy("feather_1").ok());
        assertTrue(buy("glide_1").ok());
        assertEquals(2, profile.upgradeLevelsTotal());

        StatSheet after = RunLoadout.previewStats(profile, content);
        assertEquals(385 * (1 + 2 * 0.005), after.resolve(StatId.FLAP_VELOCITY), EPS,
                "D8: the synergy scales with the total of owned upgrade levels");
        assertEquals(1 + 2 * 0.01, after.resolve(StatId.COIN_MULT), EPS);
        assertEquals(Layer.BIRD_SYNERGY,
                only(after.breakdown(StatId.COIN_MULT), "synergy:forge").layer());

        // The cap of the roster table: 0.08 on the flap, whatever the level total.
        profile.upgrades.put("coin_purse_1", 4);
        profile.upgrades.put("scholar_1", 4);
        profile.upgrades.put("lodestone_1", 3);
        profile.upgrades.put("coin_rain_1", 3);
        assertEquals(385 * 1.08, RunLoadout.previewStats(profile, content)
                .resolve(StatId.FLAP_VELOCITY), EPS);
    }

    @Test
    void ownedLevelsBecomeTheUpgradeLayerInContentOrder() {
        profile.upgrades.put("glide_1", 2);
        profile.upgrades.put("feather_1", 1);
        List<StatModifier> effects = UpgradeManager.effectsOf(profile, content);

        assertEquals(2, effects.size());
        assertEquals("upgrade:feather_1", effects.get(0).source(),
                "content order, not the order the player bought them in");
        assertEquals("upgrade:glide_1", effects.get(1).source());
        assertEquals(-0.20, effects.get(1).value(), EPS, "two levels of -0.10");
    }

    @Test
    void reconcileRenamesDropsAndRefundsOnce() {
        profile.upgrades.put("feather", 2);
        profile.upgrades.put("old_node", 1);
        profile.unlocked.add("bird:starter");
        profile.selected.birdId = "starter";
        AliasDef aliases = new AliasDef(1, Map.of("bird:starter", "bird:classic"),
                Map.of("feather", "feather_1"), Map.of(),
                Map.of("birdId", Map.of("starter", "classic")), List.of("old_node"),
                Map.of("old_node", 120L));

        List<String> report = UpgradeManager.reconcile(profile, aliases, COINS);

        assertFalse(report.isEmpty(), () -> "nothing was reconciled: " + report);
        assertEquals(2, profile.upgradeLevel("feather_1"));
        assertEquals(0, profile.upgradeLevel("feather"));
        assertFalse(profile.upgrades.containsKey("old_node"));
        assertFalse(profile.unlocked.contains("bird:starter"));
        assertEquals("classic", profile.selected.birdId);
        assertEquals(120, balance(), "E21: a removed node is refunded");
        assertEquals(120, profile.statistics.coinsEarned);

        assertEquals(List.of(), UpgradeManager.reconcile(profile, aliases, COINS),
                "a second pass finds nothing left to rename");
        assertEquals(120, balance(), "reconciled ids are recorded, so a refund is paid once");
    }

    @Test
    void aRefundIsOnlyPaidToAProfileThatOwnedTheRemovedNode() {
        AliasDef aliases = new AliasDef(1, Map.of(), Map.of(), Map.of(), Map.of(),
                List.of("master_forge_1"), Map.of("master_forge_1", 1200L));

        List<String> report = UpgradeManager.reconcile(profile, aliases, COINS);

        assertEquals(List.of(), report, "nothing was owned, so nothing was reconciled");
        assertEquals(0, balance(), "E21: the refund is what was spent, not a gift to everyone");
        assertEquals(0, profile.statistics.coinsEarned,
                "and it never inflates coins_earned_total, which is a live unlock condition");
        assertEquals(List.of(), profile.reconciled,
                "no token is recorded, so the owner of the node is still refunded later");
    }

    @Test
    void anEmptyAliasTableChangesNothing() {
        profile.upgrades.put("feather_1", 1);
        assertEquals(List.of(), UpgradeManager.reconcile(profile, AliasDef.EMPTY, COINS));
        assertEquals(List.of(), UpgradeManager.reconcile(profile, content.aliases(), COINS),
                "the shipped table is empty until a content id is renamed");
        assertEquals(1, profile.upgradeLevel("feather_1"));
    }

    /**
     * The single contribution of one source to a stat.
     *
     * @param breakdown the breakdown to search
     * @param source the modifier source
     * @return the entry
     */
    private static EffectStack.Entry only(StatBreakdown breakdown, String source) {
        EffectStack.Entry found = null;
        for (EffectStack.Entry entry : breakdown.contributions()) {
            if (source.equals(entry.modifier().source())) {
                assertNull(found, "more than one contribution from " + source);
                found = entry;
            }
        }
        assertNotNull(found, () -> "no contribution from " + source + " in " + breakdown);
        return found;
    }
}
