package io.github.michelbr84.flapforge.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.AbilityLevelDef;
import io.github.michelbr84.flapforge.content.defs.AbilityTag;
import io.github.michelbr84.flapforge.content.defs.AchievementDef;
import io.github.michelbr84.flapforge.content.defs.AliasDef;
import io.github.michelbr84.flapforge.content.defs.BirdArchetype;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.CounterScope;
import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.content.defs.FeatureDef;
import io.github.michelbr84.flapforge.content.defs.GrantDef;
import io.github.michelbr84.flapforge.content.defs.GrantType;
import io.github.michelbr84.flapforge.content.defs.ObjectiveType;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.PrestigeDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.gameplay.spec.CurveEntry;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.support.TestContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The content that actually ships must load, bind strictly, validate and resolve to the classic
 * feel (M1 verification list).
 */
class ContentIntegrityTest {

    private static final GameContent SHIPPED = GameContent.load();

    @Test
    void shippedContentLoadsBindsAndValidates() {
        assertEquals(List.of(), ContentValidator.errorsOf(SHIPPED));
        assertEquals(List.of("classic", "swift", "heavy", "guardian", "gambler", "mystic",
                "forge"), SHIPPED.birds().ids());
        assertEquals(List.of("classic", "standard"), SHIPPED.curves().ids());
        assertEquals(List.of("normal", "hard", "nightmare"), SHIPPED.tiers().ids());
        assertEquals("normal", SHIPPED.defaultTierId());
        assertEquals(0.0005, SHIPPED.speedRampPerTick());
        assertNull(SHIPPED.tierGenerator(), "endless tiers are deferred; the key stays null");
    }

    @Test
    void theClassicBirdIsTheUpstreamBird() {
        BirdDef def = SHIPPED.birds().get("classic");
        assertEquals(BirdArchetype.BALANCED, def.archetype());
        assertEquals(2, def.passiveSlots());
        assertEquals("balanced", def.shape());
        assertNull(def.sprite(), "M1 draws every bird procedurally");
        assertEquals(UnlockType.DEFAULT, def.unlock().type());
        assertEquals(List.of(), def.effects());
        assertEquals(List.of(), def.rampEffects());
        assertEquals(List.of(), def.synergyEffects());
        assertEquals(33, def.hitbox().w());
        assertEquals(31, def.hitbox().h());
        assertEquals(-17, def.hitbox().ox());
        assertEquals(-12, def.hitbox().oy());
        assertEquals(1800.0, def.baseStats().get(StatId.GRAVITY));
        assertEquals(405.0, def.baseStats().get(StatId.FLAP_VELOCITY));
        assertEquals(1500.0, def.baseStats().get(StatId.MAX_FALL_SPEED));
    }

    @Test
    void theClassicPalettesCarryTheirUnlockBlocks() {
        BirdDef def = SHIPPED.birds().get("classic");
        assertEquals(List.of("default", "ember", "voidglass", "prestige"),
                def.palettes().stream().map(PaletteDef::id).toList());
        PaletteDef base = def.palette("default");
        assertNotNull(base);
        assertEquals(0xF5C542, base.bodyRgb());
        assertEquals(0xE09A2B, base.wingRgb());
        assertEquals(0x222222, base.eyeRgb());
        assertEquals(0xFFF3C4, base.accentRgb());
        assertEquals(UnlockType.DEFAULT, base.unlock().type());

        PaletteDef ember = def.palette("ember");
        assertNotNull(ember);
        assertEquals(0xE0533A, ember.bodyRgb());
        assertEquals(UnlockType.CHALLENGE, ember.unlock().type());
        assertEquals("no_shield_1", ember.unlock().id());

        PaletteDef voidglass = def.palette("voidglass");
        assertNotNull(voidglass);
        assertEquals(UnlockType.WORLD_CLEARED, voidglass.unlock().type());
        assertEquals("void", voidglass.unlock().id());

        PaletteDef prestige = def.palette("prestige");
        assertNotNull(prestige);
        assertEquals(UnlockType.PRESTIGE, prestige.unlock().type());
        assertEquals(1.0, prestige.unlock().value());
    }

    @Test
    void tiersCarryTheirEffectsFlagsAndUnlocks() {
        TierDef normal = SHIPPED.tiers().get("normal");
        assertTrue(normal.defaultTier());
        assertEquals(1.0, normal.rewardMult());
        assertEquals(List.of(), normal.flags());

        TierSpec hard = SHIPPED.tierSpec("hard");
        assertEquals(1.5, hard.rewardMult());
        assertEquals(2, hard.effects().size());
        assertEquals("tier:hard", hard.effects().get(0).source());

        TierDef nightmare = SHIPPED.tiers().get("nightmare");
        assertEquals(List.of(RuleFlag.ALL_OBSTACLES_MOVE, RuleFlag.LETHAL_CEILING),
                nightmare.flags());
        assertEquals(2.5, nightmare.rewardMult());
        assertEquals(UnlockType.ANY_OF, nightmare.unlock().type());
        assertEquals(2, nightmare.unlock().conditions().size());
    }

    /** {@code economy.json} as §4 patched by E1 (points pay) and E4 (no prestige shards). */
    @Test
    void theEconomyShipsTheDocumentedNumbers() {
        EconomyDef economy = SHIPPED.economy();
        assertEquals(List.of("coins"), economy.currencies());
        assertEquals("coins", economy.primaryCurrency());

        assertEquals(20, economy.rewards().participation());
        assertEquals(25, economy.rewards().firstRunBonus());
        assertEquals(2, economy.rewards().coinsPerGate(), "E1: gates pay 2");
        assertEquals(1, economy.rewards().coinsPerPoint(), "E1: points feed progression");
        assertEquals(150, economy.rewards().bossBonus());
        assertEquals(100, economy.rewards().challengeBonus());
        assertEquals(5, economy.rewards().streak().step());
        assertEquals(5, economy.rewards().streak().coins());

        assertEquals(15, economy.xp().participation());
        assertEquals(10, economy.xp().perGate());
        assertEquals(200, economy.xp().bossBonus());
        assertEquals(100, economy.xp().curve().base());
        assertEquals(1.10, economy.xp().curve().growth());
        assertEquals(50, economy.xp().curve().maxLevel());
        assertEquals(List.of("2", "5", "10", "15", "20", "25"),
                List.copyOf(economy.xp().levelRewards().keySet()));
        assertEquals(2000, economy.xp().levelRewards().get("25").coins());

        assertEquals(List.of("modifiers", "seeded_runs"),
                economy.features().stream().map(FeatureDef::id).toList());
        assertEquals("feature:modifiers", economy.feature("modifiers").unlockableId());
        assertEquals(UnlockType.ANY_OF, economy.feature("seeded_runs").unlock().type());
        assertNull(economy.feature("nothing"));

        assertEquals(List.of("normal", "hard"), economy.daily().tierPool());
        assertEquals(2, economy.daily().forcedModifierCount());
        assertEquals(1.25, economy.daily().rewardMult());

        PrestigeDef prestige = economy.prestige();
        assertEquals(25, prestige.requiredLevel());
        assertEquals(5, prestige.maxPrestige(), "E4: five prestiges, no shards");
        assertEquals(PrestigeDef.KEEPS, prestige.keeps());
        assertEquals(1, prestige.bonusPerPrestige().size());
        assertEquals(StatId.COIN_MULT, prestige.bonusPerPrestige().get(0).stat());
        assertEquals(StatOp.PERCENT_ADD, prestige.bonusPerPrestige().get(0).op());
        assertEquals(0.05, prestige.bonusPerPrestige().get(0).value());
    }

    /** D26: the run reads its streak step from the economy, not from a constant. */
    @Test
    void theStreakStepReachesTheRun() {
        assertEquals(SHIPPED.economy().rewards().streak().step(),
                new RunFactory(SHIPPED).setup(RunConfig.classic(1)).streakStep());
    }

    @Test
    void theClassicTableResolvesThroughTheStatPipeline() {
        // ContentValidator proves the numbers; this asserts the check is actually wired in.
        assertEquals(List.of(), ContentValidator.errorsOf(SHIPPED));
        CurveSpec classic = SHIPPED.curveSpec("classic");
        assertEquals(1, classic.entries().size());
        assertEquals(StatId.MOVING_CHANCE, classic.entries().get(0).stat());
        assertEquals(0.05, classic.entries().get(0).valueAt(0));
        assertEquals(1.0, classic.entries().get(0).valueAt(19));
        assertEquals(1.0, classic.entries().get(0).valueAt(25));
    }

    @Test
    void contentBuildsTheSameSetupAsTheHardCodedClassicSeam() {
        RunFactory factory = new RunFactory(SHIPPED);
        RunSetup setup = factory.setup(RunConfig.classic(1));
        assertEquals(RunSetup.CLASSIC, setup.withModifiers(ModifierCatalog.EMPTY),
                "birds.json + difficulty.json must reproduce RunSetup.CLASSIC exactly");
        // The one thing content adds on top of the classic seam is the M6 draft catalogue, and a
        // default configuration carries only the cards that ship unlocked.
        assertEquals(14, setup.modifiers().modifiers().size(),
                "the three legendaries are unlockables and are not in a default catalogue");
        assertEquals(List.of(10, 25, 45, 70, 100, 140), setup.modifiers().offerSchedule());
        BirdProfile profile = SHIPPED.birdProfile("classic");
        assertEquals(BirdProfile.CLASSIC, profile);
    }

    @Test
    void unknownIdsAreRejectedWithTheirKind() {
        UnknownIdException e = assertThrows(UnknownIdException.class,
                () -> SHIPPED.birds().get("sparrow"));
        assertEquals("bird", e.kind());
        assertEquals("sparrow", e.id());
        assertEquals("tier", assertThrows(UnknownIdException.class,
                () -> SHIPPED.tierSpec("impossible")).kind());
        assertEquals("curve", assertThrows(UnknownIdException.class,
                () -> SHIPPED.curveSpec("nope")).kind());
    }

    /**
     * The frozen fixture is a <em>separate</em> guarantee from the shipped files (D12): the golden
     * run is recorded against it so that it catches simulation drift, while {@code --headless-run}
     * hashes what actually ships. Asserting the two byte-identical would couple them again and
     * force the golden run to be re-recorded on every balance change — exactly what the fixture
     * exists to prevent. What is asserted instead is that the frozen copy still stands on its own
     * and still describes the classic table. {@code ContentWiringTest
     * .theShippedContentPlaysExactlyLikeTheHardCodedClassicSeam} guards the shipped side.
     */
    @Test
    void theFrozenFixtureValidatesAndStillResolvesToTheClassicTable() {
        GameContent frozen = TestContent.frozen();
        assertEquals(List.of(), ContentValidator.errorsOf(frozen),
                "the frozen fixture must keep validating on its own");
        assertEquals(RunSetup.CLASSIC, TestContent.frozenFactory().setup(RunConfig.classic(42)),
                "the golden run is recorded against the classic table");

        StatSheet stats = TestContent.frozenFactory().newRun(RunConfig.classic(42))
                .simulation().stats();
        assertEquals(1800.0, stats.resolve(StatId.GRAVITY));
        assertEquals(405.0, stats.resolve(StatId.FLAP_VELOCITY));
        assertEquals(1500.0, stats.resolve(StatId.MAX_FALL_SPEED));
        assertEquals(120.0, stats.resolve(StatId.SCROLL_SPEED));
        assertEquals(128.0, stats.resolve(StatId.GAP_SIZE));
        assertEquals(160.0, stats.resolve(StatId.GATE_INTERVAL));

        CurveEntry moving = frozen.curveSpec("classic").entries().get(0);
        assertEquals(StatId.MOVING_CHANCE, moving.stat());
        assertEquals(0.05, moving.valueAt(0));
        assertEquals(1.0, moving.valueAt(25));
    }

    // ------------------------------------------------------------------- M4

    /** The seven-bird roster of §4, checked number by number. */
    @Test
    void theSevenBirdsCarryTheRosterNumbers() {
        assertEquals(7, SHIPPED.birds().size());
        for (BirdDef bird : SHIPPED.birds()) {
            assertEquals(33, bird.hitbox().w(), bird.id() + " shares the classic hitbox");
            assertEquals(31, bird.hitbox().h(), bird.id() + " shares the classic hitbox");
            assertNotNull(bird.palette("default"), bird.id() + " has a default palette");
            assertNotNull(bird.palette("prestige"), bird.id() + " has a prestige palette (E20)");
        }

        BirdDef swift = SHIPPED.birds().get("swift");
        assertEquals(BirdArchetype.SWIFT, swift.archetype());
        assertEquals(2100.0, swift.baseStats().get(StatId.GRAVITY));
        assertEquals(470.0, swift.baseStats().get(StatId.FLAP_VELOCITY));

        BirdDef heavy = SHIPPED.birds().get("heavy");
        assertEquals(2200.0, heavy.baseStats().get(StatId.GRAVITY));
        assertEquals(460.0, heavy.baseStats().get(StatId.FLAP_VELOCITY));
        assertEquals(450.0, heavy.baseStats().get(StatId.MAX_FALL_SPEED), "Anvil floats down");

        BirdDef guardian = SHIPPED.birds().get("guardian");
        assertEquals(List.of("shield"), guardian.passiveAbilities());
        assertEquals(1, guardian.effects().size());
        assertEquals(StatId.COIN_MULT, guardian.effects().get(0).stat());
        assertEquals(-0.20, guardian.effects().get(0).value());
        assertEquals(UnlockType.ANY_OF, guardian.unlock().type());
        assertEquals(UnlockType.RUNS, guardian.unlock().conditions().get(0).type());
        assertEquals(3.0, guardian.unlock().conditions().get(0).value(), "README: run 3");
        assertEquals(150.0, guardian.unlock().conditions().get(1).amount());

        BirdDef gambler = SHIPPED.birds().get("gambler");
        assertEquals(4, gambler.effects().size());
        assertEquals(StatId.HITBOX_SCALE, gambler.effects().get(3).stat());
        assertEquals(0.10, gambler.effects().get(3).value());

        BirdDef mystic = SHIPPED.birds().get("mystic");
        assertEquals(3, mystic.passiveSlots(), "Oracle is the only bird with three slots");
        assertEquals(StatOp.MULTIPLY, mystic.effects().get(0).op());

        BirdDef forge = SHIPPED.birds().get("forge");
        assertEquals(385.0, forge.baseStats().get(StatId.FLAP_VELOCITY));
        assertEquals(1, forge.rampEffects().size());
        assertEquals(StatId.SCORE_MULT, forge.rampEffects().get(0).stat());
        assertEquals(0.02, forge.rampEffects().get(0).perGate());
        assertEquals(0.50, forge.rampEffects().get(0).max());
        assertEquals(2, forge.synergyEffects().size());
        assertEquals(StatId.FLAP_VELOCITY, forge.synergyEffects().get(0).stat());
        assertEquals(0.005, forge.synergyEffects().get(0).perUpgradeLevel());
        assertEquals(0.08, forge.synergyEffects().get(0).max());
        assertEquals(StatId.COIN_MULT, forge.synergyEffects().get(1).stat());
        assertEquals(0.25, forge.synergyEffects().get(1).max());
    }

    /** The palette unlocks, including the two E20 types. */
    @Test
    void thePaletteUnlocksUseTheDocumentedConditions() {
        assertEquals(UnlockType.TOTAL_GATES,
                SHIPPED.birds().get("heavy").palette("basalt").unlock().type());
        assertEquals(500.0, SHIPPED.birds().get("heavy").palette("basalt").unlock().value());
        assertEquals(UnlockType.ACHIEVEMENT,
                SHIPPED.birds().get("mystic").palette("aurora").unlock().type());
        assertEquals("ability_master",
                SHIPPED.birds().get("mystic").palette("aurora").unlock().id());

        UnlockConditionDef molten = SHIPPED.birds().get("forge").palette("molten").unlock();
        assertEquals(UnlockType.COUNTER, molten.type(), "E20 counter condition");
        assertEquals("collection.upgrades.percent", molten.counter());
        assertEquals(50.0, molten.value());

        for (BirdDef bird : SHIPPED.birds()) {
            assertEquals(UnlockType.PRESTIGE, bird.palette("prestige").unlock().type(),
                    "E4/E20: every bird has a prestige palette");
            assertEquals(1.0, bird.palette("prestige").unlock().value());
        }
    }

    /** The three trees and the eighteen nodes of §4. */
    @Test
    void theUpgradeTreesShipEighteenNodes() {
        assertEquals(List.of("flight", "economy", "forge"), SHIPPED.trees().ids());
        assertEquals(UnlockType.DEFAULT, SHIPPED.trees().get("flight").unlock().type());
        assertEquals(UnlockType.ANY_OF, SHIPPED.trees().get("economy").unlock().type());
        assertEquals(3.0, SHIPPED.trees().get("economy").unlock().conditions().get(0).value());
        assertEquals(120.0, SHIPPED.trees().get("economy").unlock().conditions().get(1).amount());
        assertEquals("wind_valley",
                SHIPPED.trees().get("forge").unlock().conditions().get(0).id());
        assertEquals(900.0, SHIPPED.trees().get("forge").unlock().conditions().get(1).amount());

        assertEquals(18, SHIPPED.upgrades().size());
        assertEquals(List.of("feather_1", "glide_1", "slim_frame_1", "quick_recharge_1",
                "updraft_1", "featherfall_2", "coin_purse_1", "scholar_1", "lodestone_1",
                "coin_rain_1", "hard_tier_1", "ability_scholar_1", "tempered_shield_1",
                "ability_forge_1", "cooldown_forge_1", "hitbox_forge_1", "master_forge_1",
                "second_chance_1"), SHIPPED.upgrades().ids());
        for (UpgradeDef node : SHIPPED.upgrades()) {
            assertEquals(node.maxLevel(), node.costs().size(),
                    node.id() + " prices every level");
        }

        // The whole price table of §4, pinned row by row. Prices are the one thing the journey
        // simulation is meant to guard and the one thing a stray edit changes silently: with only
        // feather_1 pinned, doubling any of the other seventeen was invisible to the suite.
        Map<String, List<Long>> costs = new LinkedHashMap<>();
        costs.put("feather_1", List.of(50L, 120L, 250L));
        costs.put("glide_1", List.of(90L, 200L));
        costs.put("slim_frame_1", List.of(150L, 300L, 600L));
        costs.put("quick_recharge_1", List.of(150L, 300L, 600L));
        costs.put("updraft_1", List.of(400L, 800L));
        costs.put("featherfall_2", List.of(500L, 1000L));
        costs.put("coin_purse_1", List.of(80L, 160L, 320L, 640L));
        costs.put("scholar_1", List.of(80L, 160L, 320L, 640L));
        costs.put("lodestone_1", List.of(150L, 300L, 600L));
        costs.put("coin_rain_1", List.of(120L, 240L, 480L));
        costs.put("hard_tier_1", List.of(400L));
        costs.put("ability_scholar_1", List.of(900L));
        costs.put("tempered_shield_1", List.of(500L, 1200L));
        costs.put("ability_forge_1", List.of(300L, 600L, 1000L));
        costs.put("cooldown_forge_1", List.of(300L, 600L, 1000L));
        costs.put("hitbox_forge_1", List.of(700L, 1400L));
        costs.put("master_forge_1", List.of(1200L));
        costs.put("second_chance_1", List.of(1500L));
        assertEquals(SHIPPED.upgrades().ids(), new ArrayList<>(costs.keySet()),
                "every shipped node has a pinned price row");
        long total = 0;
        for (Map.Entry<String, List<Long>> entry : costs.entrySet()) {
            UpgradeDef node = SHIPPED.upgrades().get(entry.getKey());
            assertEquals(entry.getValue(), node.costs(), entry.getKey() + " prices");
            for (long price : entry.getValue()) {
                total += price;
            }
        }
        assertEquals(21_400, total, "the whole tree costs what docs/BALANCING.md says");

        UpgradeDef feather = SHIPPED.upgrades().get("feather_1");
        assertEquals("flight", feather.tree());
        assertEquals(1, feather.tier());
        assertEquals(List.of(50L, 120L, 250L), feather.costs());
        assertEquals(StatId.GRAVITY, feather.effectsPerLevel().get(0).stat());
        assertEquals(StatOp.PERCENT_ADD, feather.effectsPerLevel().get(0).op());
        assertEquals(-0.03, feather.effectsPerLevel().get(0).value());
        // D8: PERCENT_ADD scales linearly with the level.
        assertEquals(-0.06, feather.effectsAt(2).get(0).value(), 1e-9);
        assertEquals("upgrade:feather_1", feather.effectsAt(1).get(0).source());
        // D8: MULTIPLY compounds.
        assertEquals(0.92 * 0.92,
                SHIPPED.upgrades().get("quick_recharge_1").effectsAt(2).get(0).value(), 1e-9);
        assertEquals(List.of("feather_1", "glide_1"),
                SHIPPED.upgrades().get("updraft_1").prereqs());
    }

    /** E3: exactly one {@code ability_cap} grant ships, and the scholar grants a slot instead. */
    @Test
    void theGrantsFollowTheErrataE3() {
        List<String> capGrants = new ArrayList<>();
        List<String> slotGrants = new ArrayList<>();
        List<String> unlockGrants = new ArrayList<>();
        for (UpgradeDef node : SHIPPED.upgrades()) {
            for (GrantDef grant : node.grants()) {
                if (grant.type() == GrantType.ABILITY_CAP) {
                    capGrants.add(node.id() + "+" + grant.amount());
                } else if (grant.type() == GrantType.PASSIVE_SLOT) {
                    slotGrants.add(node.id() + "+" + grant.amount());
                } else {
                    unlockGrants.add(node.id() + "->" + grant.id());
                }
            }
        }
        assertEquals(List.of("master_forge_1+1"), capGrants);
        assertEquals(List.of("ability_scholar_1+1"), slotGrants);
        assertEquals(List.of("hard_tier_1->tier:hard"), unlockGrants);
        assertEquals(ContentValidator.BASE_ABILITY_LEVEL_CAP + 1, SHIPPED.abilities().all()
                .stream().mapToInt(a -> a.levels().size()).min().orElse(0),
                "base cap 2 + the one grant must not exceed the shipped ability levels");
    }

    /** The M4 stub files carry their final unlock and reward blocks (E19). */
    @Test
    void theStubFilesCarryTheirFinalUnlockAndRewardBlocks() {
        assertEquals(List.of("double_flap", "shield", "dash", "coin_magnet", "slow_time",
                "emergency_recovery", "score_multiplier", "invulnerability"),
                SHIPPED.abilities().ids());
        AbilityDef shield = SHIPPED.abilities().get("shield");
        assertEquals(AbilityKind.PASSIVE, shield.kind());
        assertEquals(List.of(AbilityTag.DEFENSIVE), shield.tags());
        assertEquals(List.of(0L, 400L, 800L),
                shield.levels().stream().map(AbilityLevelDef::cost).toList());
        assertEquals(UnlockType.DEFAULT, SHIPPED.abilities().get("double_flap").unlock().type());

        assertEquals(List.of("green_fields", "wind_valley", "iron_forge", "storm_sky", "void"),
                SHIPPED.worlds().ids());
        WorldDef greenFields = SHIPPED.worlds().get("green_fields");
        assertEquals(1, greenFields.order());
        assertEquals("classic", greenFields.curve());
        assertEquals(30, greenFields.boss().atGate());
        assertEquals(120, greenFields.boss().warningTicks());
        assertEquals(1200, greenFields.boss().surviveTicks());
        assertEquals(List.of("gf_boss_p1", "gf_boss_p2"), greenFields.boss().patterns());
        assertEquals(200, greenFields.boss().reward().coins());
        assertEquals(List.of("world:wind_valley"), greenFields.boss().reward().unlocks());
        assertEquals(List.of("world:iron_forge", "tree:forge"),
                SHIPPED.worlds().get("wind_valley").boss().reward().unlocks(),
                "README: the Wind Valley boss opens a new upgrade tree");
        assertEquals(List.of("cosmetic:classic:voidglass"),
                SHIPPED.worlds().get("void").boss().reward().unlocks());

        assertEquals(7, SHIPPED.challenges().size());
        ChallengeDef coinRush = SHIPPED.challenges().get("coin_rush_1");
        assertEquals(ObjectiveType.COLLECT_COINS, coinRush.objective().type());
        assertEquals(60, coinRush.objective().value());
        assertEquals(List.of("coin_drops"), coinRush.forcedModifiers(), "E2");
        assertEquals(300, coinRush.rewards().coins());
        assertEquals(List.of("cosmetic:gambler:gilded"), coinRush.rewards().unlocks());

        assertEquals(41, SHIPPED.achievements().size());
        AchievementDef adept = SHIPPED.achievements().get("ability_adept");
        assertEquals("abilitiesUsedTotal", adept.condition().counter());
        assertEquals(CounterScope.LIFETIME, adept.condition().scope());
        assertEquals(50.0, adept.condition().value());
        assertEquals(100, adept.reward().coins());
        assertTrue(SHIPPED.achievements().contains("points_100"), "E1 points achievements");
        assertTrue(SHIPPED.achievements().contains("points_500"));
        assertTrue(SHIPPED.achievements().contains("points_1000"));
    }

    /**
     * M5 completed the M4 ability stub in place (E19): the behaviours, the effects and the
     * timings are authored now, and they are the numbers D9 specifies.
     */
    @Test
    void theAbilitiesCarryTheirBehavioursEffectsAndTimings() {
        for (AbilityDef def : SHIPPED.abilities()) {
            assertEquals(def.id(), def.behavior(),
                    "the shipped abilities map one to one onto their behaviours");
            assertEquals(3, def.levels().size(), "E3: three levels each");
        }

        AbilityDef shield = SHIPPED.abilities().get("shield");
        assertEquals(List.of(new StatModifierDef(StatId.SHIELD_CHARGES, StatOp.FLAT_ADD, 1)),
                shield.effects(), "D9: the charge is data, so an upgrade alone can grant one");
        assertEquals(45.0, shield.levels().get(0).params().get("invulnTicks"));
        assertEquals(0.0, shield.levels().get(0).params().get("regenEveryGates"));
        assertEquals(10.0, shield.levels().get(2).params().get("regenEveryGates"));

        AbilityDef dash = SHIPPED.abilities().get("dash");
        assertEquals(List.of(new StatModifierDef(StatId.SCROLL_SPEED, StatOp.MULTIPLY, 2.5)),
                dash.effects(), "E24: only the speed goes through the stat pipeline");
        assertEquals(20, dash.levels().get(0).durationTicks());
        assertEquals(600, dash.levels().get(0).cooldownTicks());

        AbilityDef doubleFlap = SHIPPED.abilities().get("double_flap");
        assertEquals(2.0, doubleFlap.levels().get(0).params().get("charges"));
        assertEquals(5.0, doubleFlap.levels().get(0).params().get("rechargeEveryGates"));
        assertEquals(1.5, doubleFlap.levels().get(0).params().get("flapMultiplier"));

        assertEquals(90, SHIPPED.abilities().get("slow_time").levels().get(0).durationTicks());
        assertEquals(List.of(new StatModifierDef(StatId.TIME_SCALE, StatOp.MULTIPLY, 0.5)),
                SHIPPED.abilities().get("slow_time").effects());
        assertEquals(300,
                SHIPPED.abilities().get("score_multiplier").levels().get(0).durationTicks());
        assertEquals(List.of(new StatModifierDef(StatId.SCORE_MULT, StatOp.MULTIPLY, 2)),
                SHIPPED.abilities().get("score_multiplier").effects());
        assertEquals(120,
                SHIPPED.abilities().get("invulnerability").levels().get(0).durationTicks());
        assertEquals(List.of(new StatModifierDef(StatId.MAGNET_RADIUS, StatOp.FLAT_ADD, 90)),
                SHIPPED.abilities().get("coin_magnet").effects());
        assertEquals(List.of(new StatModifierDef(StatId.REVIVES, StatOp.FLAT_ADD, 1)),
                SHIPPED.abilities().get("emergency_recovery").effects());
        assertEquals(90.0, SHIPPED.abilities().get("emergency_recovery").levels().get(0)
                .params().get("invulnTicks"));
    }

    /** E19: authored and validated is not the same as playable. */
    @Test
    void thePlayableGateReportsWhatEachMilestoneEnabled() {
        assertTrue(SHIPPED.playable(ContentKind.BIRD));
        assertTrue(SHIPPED.playable(ContentKind.COSMETIC));
        assertTrue(SHIPPED.playable(ContentKind.UPGRADE));
        assertTrue(SHIPPED.playable(ContentKind.TREE));
        assertTrue(SHIPPED.playable(ContentKind.TIER));
        assertTrue(SHIPPED.playable(ContentKind.FEATURE));
        assertTrue(SHIPPED.playable(ContentKind.ABILITY), "abilities landed in M5");
        assertTrue(SHIPPED.playable(ContentKind.MODIFIER), "modifiers landed in M6");
        assertTrue(SHIPPED.playable(ContentKind.SYNERGY), "and so did their set bonuses");
        assertTrue(SHIPPED.playable(ContentKind.WORLD, "green_fields"));
        assertTrue(SHIPPED.playable(ContentKind.WORLD, "storm_sky"), "worlds landed in M7");
        assertTrue(SHIPPED.playable(ContentKind.WORLD, "void"));
        assertTrue(SHIPPED.playable(ContentKind.CHALLENGE), "challenges landed in M8");
        assertTrue(SHIPPED.playable(ContentKind.ACHIEVEMENT),
                "achievements landed with the M8 evaluator");
    }

    /** E21: {@code aliases.json} ships empty, with the per-field shape ready for the first rename. */
    @Test
    void theAliasTableShipsEmpty() {
        AliasDef aliases = SHIPPED.aliases();
        assertEquals(1, aliases.version());
        assertTrue(aliases.isEmpty());
        assertEquals(Map.of(), aliases.unlocked());
        assertEquals(Map.of(), aliases.selected());
        assertEquals(List.of(), aliases.removedUpgrades());
    }

    /** Every id of every shipped kind has a name and a description in en.json (E31.h). */
    @Test
    void everyContentIdHasItsDisplayStrings() {
        ContentValidator.StringReport report = ContentValidator.checkStrings(SHIPPED);
        assertEquals(List.of(), report.errors());
        assertEquals(List.of(), report.warnings(),
                "pt_BR gaps are warnings; the shipped content ships translated");
        assertTrue(ContentValidator.contentKeys(SHIPPED).contains("upgrade.master_forge_1.name"));
        assertTrue(ContentValidator.contentKeys(SHIPPED).contains("challenge.coin_rush_1.desc"));
        assertTrue(ContentValidator.contentKeys(SHIPPED).contains("modifier.stormrider.name"));
        assertTrue(ContentValidator.contentKeys(SHIPPED).contains("synergy.daredevil.desc"));
    }

    /** The M7 file set is what the game loads. */
    @Test
    void theShippedFileSetIsTheM7One() {
        assertEquals(List.of("birds", "difficulty", "economy", "upgrades", "aliases", "abilities",
                "modifiers", "worlds", "patterns", "challenges", "achievements"),
                ContentLoader.FILES);
        for (String file : ContentLoader.FILES) {
            assertTrue(SHIPPED.has(file), file + " was loaded");
        }
    }

    @Test
    void greenFieldsUsesTheClassicCurveAndOtherWorldsTheStandardOne() {
        RunFactory factory = new RunFactory(SHIPPED);
        assertEquals("classic", factory.curveIdFor("green_fields"));
        assertEquals("standard", factory.curveIdFor("storm_sky"));
        // M7: the answer now comes from worlds.json rather than from a two-case fallback, so
        // every shipped world's curve is what the file says.
        for (WorldDef world : SHIPPED.worlds()) {
            assertEquals(world.curve(), factory.curveIdFor(world.id()), world.id());
        }
        // ... and the frozen fixture, which ships no worlds.json, keeps the fallback (E19).
        RunFactory frozen = TestContent.frozenFactory();
        assertEquals("classic", frozen.curveIdFor("green_fields"));
        assertEquals("standard", frozen.curveIdFor("storm_sky"));
    }
}
