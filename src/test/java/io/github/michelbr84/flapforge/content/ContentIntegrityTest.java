package io.github.michelbr84.flapforge.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.BirdArchetype;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.CurveEntry;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.support.TestContent;
import java.util.List;
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
        assertEquals(List.of("classic"), SHIPPED.birds().ids());
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
        assertEquals(RunSetup.CLASSIC, setup,
                "birds.json + difficulty.json must reproduce RunSetup.CLASSIC exactly");
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

    @Test
    void greenFieldsUsesTheClassicCurveAndOtherWorldsTheStandardOne() {
        RunFactory factory = new RunFactory(SHIPPED);
        assertEquals("classic", factory.curveIdFor("green_fields"));
        assertEquals("standard", factory.curveIdFor("storm_sky"));
    }
}
