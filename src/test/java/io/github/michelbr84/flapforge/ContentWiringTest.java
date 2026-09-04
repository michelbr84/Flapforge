package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.ContentAdapters;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProfileSchema;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.ContentRunFactory;
import io.github.michelbr84.flapforge.ui.screens.SeededRunSource;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The seam {@code GameApplication} wires: the screens play runs built from the shipped content
 * (D10, D11), not from the hard-coded seam records. For M1 the two must be indistinguishable —
 * the moment they are not, either the data files or the seam records moved without the other.
 */
class ContentWiringTest {

    private static final GameContent SHIPPED = GameContent.load();
    private static final long SEED = 42;
    private static final int TICKS = 3000;

    @Test
    void theContentFactoryStampsTheModeTheLaunchChose() {
        assertEquals(RunMode.STANDARD, new ContentRunFactory(SHIPPED).newRun(SEED).config().mode());
        assertEquals(RunMode.SEEDED,
                new ContentRunFactory(SHIPPED, RunMode.SEEDED).newRun(SEED).config().mode());
        assertSame(SHIPPED, new ContentRunFactory(SHIPPED).content());
    }

    @Test
    void theContentFactoryBuildsTheDefaultConfigurationForASeed() {
        Run run = new ContentRunFactory(SHIPPED).newRun(SEED);
        RunConfig config = run.config();
        assertEquals(SEED, config.seed());
        assertEquals(RunConfig.DEFAULT_BIRD, config.birdId());
        assertEquals(RunConfig.DEFAULT_WORLD, config.worldId());
        assertEquals(RunConfig.DEFAULT_TIER, config.tierId());
        assertEquals(RunSetup.CLASSIC, run.setup().withModifiers(ModifierCatalog.EMPTY),
                "M1 content resolves to the classic seam, plus the M6 draft catalogue");
        assertFalse(run.isFinished());
    }

    @Test
    void theContentFactoryBuildsEveryRunFromTheLiveProfile() {
        // M4: what the player bought reaches the bird. The supplier is read per run, so a
        // purchase made between two runs lands on the second one.
        PlayerProfile profile = PlayerProfile.fresh(0).normalize();
        ContentRunFactory factory =
                new ContentRunFactory(SHIPPED, RunMode.STANDARD, () -> profile);
        assertEquals(1800, factory.newRun(SEED).simulation().stats().resolve(StatId.GRAVITY), 1e-9);

        profile.upgrades.put("feather_1", 1);
        profile.unlock("bird:heavy");
        profile.unlock("cosmetic:heavy:default");
        profile.selected.birdId = "heavy";
        profile.selected.paletteId = "default";
        Run run = factory.newRun(SEED);

        assertEquals("heavy", run.config().birdId());
        assertEquals(1, run.config().upgradeLevelsTotal());
        assertEquals(List.of("upgrade:feather_1"),
                run.config().permanentEffects().stream().map(m -> m.source()).toList());
        assertEquals(2200 * 0.97, run.simulation().stats().resolve(StatId.GRAVITY), 1e-9,
                "Anvil's base gravity, minus the three percent the node pays for");
    }

    @Test
    void aFactoryWithoutAProfileKeepsTheDefaultConfiguration() {
        ContentRunFactory factory = new ContentRunFactory(SHIPPED, RunMode.STANDARD, () -> null);
        assertEquals(RunConfig.DEFAULT_BIRD, factory.newRun(SEED).config().birdId());
        assertEquals(List.of(), factory.newRun(SEED).config().permanentEffects());
    }

    @Test
    void theProfileSchemaTheApplicationInstallsKeepsEveryShippedId() {
        // The save manager normalises through this schema from M4 on (E15, E21): every id the
        // content ships has to survive it, and only an id the content dropped is repaired away.
        ProfileSchema schema = ContentAdapters.toProfileSchema(SHIPPED);
        PlayerProfile profile = PlayerProfile.fresh(0);
        profile.selected.birdId = "forge";
        profile.selected.paletteId = "molten";
        profile.selected.worldId = "void";
        profile.selected.tierId = "nightmare";
        profile.selected.activeAbilityId = "dash";
        profile.upgrades.put("second_chance_1", 1);
        profile.normalize(schema);

        assertEquals("forge", profile.selected.birdId);
        assertEquals("molten", profile.selected.paletteId);
        assertEquals("void", profile.selected.worldId);
        assertEquals("nightmare", profile.selected.tierId);
        assertEquals("dash", profile.selected.activeAbilityId);
        assertTrue(profile.isUnlocked("tree:forge"),
                "owning a node implies its tree, which the schema is what resolves");

        profile.selected.birdId = "phoenix";
        profile.selected.worldId = "atlantis";
        profile.upgrades.put("no_such_node", 3);
        profile.abilityLevelCap = 99;
        profile.normalize(schema);
        assertEquals(PlayerProfile.DEFAULT_BIRD, profile.selected.birdId);
        assertEquals(PlayerProfile.DEFAULT_WORLD, profile.selected.worldId);
        assertEquals(0, profile.upgradeLevel("no_such_node"),
                "a node the content dropped is repaired away too, so Cinder's synergy input"
                        + " cannot be inflated by a stale key");
        assertEquals(1, profile.upgradeLevelsTotal());
        assertEquals(3, schema.maxAbilityLevelCap(),
                "E3: base cap 2 plus the single ability_cap grant the trees ship");
        assertEquals(3, profile.abilityLevelCap, "and the cap is clamped to it from above");
    }

    /**
     * M7: {@code RunFactory.setup} reads the whole world from {@code worlds.json} — curve,
     * effects, flags, weights, patterns, ambience, rule cycles — and Green Fields resolves, field
     * by field, to exactly what the M6 factory built from its two-case fallback.
     */
    @Test
    void everyWorldResolvesFromWorldsJson() {
        RunFactory factory = new RunFactory(SHIPPED);
        for (WorldDef def : SHIPPED.worlds()) {
            RunSetup setup = factory.setup(RunConfig.builder(1).worldId(def.id()).build());
            WorldSpec world = setup.world();
            assertEquals(def.id(), world.id());
            assertEquals(def.curve(), world.curve().id());
            assertEquals(def.effects().size(), world.effects().size(), def.id());
            for (int i = 0; i < def.effects().size(); i++) {
                assertEquals(def.effects().get(i).stat(), world.effects().get(i).stat());
                assertEquals(def.effects().get(i).value(), world.effects().get(i).value(), 0.0);
                assertEquals("world:" + def.id(), world.effects().get(i).source());
            }
            assertEquals(RuleSet.of(def.flags()), world.flags());
            assertEquals(def.spawnWeights(), world.spawnWeights());
            assertEquals(def.patterns().size(), world.patterns().size());
            for (int i = 0; i < def.patterns().size(); i++) {
                assertEquals(def.patterns().get(i), world.patterns().get(i).id());
                assertTrue(world.patterns().get(i).weight() > 0);
            }
            assertEquals(def.ambient().darkness(), world.ambient().darkness(), 0.0);
            assertEquals(def.ambient().windX(), world.ambient().windX(), 0.0);
            assertEquals(def.ambient().windY(), world.ambient().windY(), 0.0);
            assertEquals(def.ambient().lightningEveryGates(), world.ambient().lightningEveryGates());
            assertEquals(def.ruleCycles() == null, world.ruleCycles() == null, def.id());
            assertSame(world, factory.worldSpec(def.id()), "resolved once and cached");
            assertEquals(def.id(), factory.newRun(RunConfig.builder(1).worldId(def.id()).build())
                    .setup().world().id());
        }
        WorldSpec voidWorld = factory.worldSpec("void");
        assertEquals(5, voidWorld.ruleCycles().everyGates());
        assertEquals(90, voidWorld.ruleCycles().telegraphTicks());
        assertEquals(4, voidWorld.ruleCycles().options().size());
        assertEquals(RuleSet.of(RuleFlag.ALL_OBSTACLES_MOVE),
                voidWorld.ruleCycles().options().get(0).flags());
        assertEquals(StatId.GAP_SIZE, voidWorld.ruleCycles().options().get(1).effects().get(0).stat());
        assertEquals(RuleSet.of(RuleFlag.LETHAL_CEILING),
                voidWorld.ruleCycles().options().get(3).flags());
        assertEquals(-20, factory.worldSpec("wind_valley").ambient().windX(), 0.0);
        assertEquals(3, factory.worldSpec("storm_sky").ambient().lightningEveryGates());
        assertEquals(0.5, factory.worldSpec("storm_sky").ambient().darkness(), 0.0);
        assertTrue(factory.worldSpec("iron_forge").hasWorldEffects(), "patterns and darkness");
    }

    @Test
    void greenFieldsResolvesToTheM6SetupFieldByField() {
        RunSetup setup = new RunFactory(SHIPPED).setup(RunConfig.classic(SEED));
        RunSetup classic = RunSetup.CLASSIC;
        assertEquals(classic.bird(), setup.bird());
        assertEquals(classic.world().id(), setup.world().id());
        assertEquals(classic.world().curve(), setup.world().curve());
        assertEquals(classic.world().effects(), setup.world().effects());
        assertEquals(classic.world().flags(), setup.world().flags());
        assertEquals(classic.world().spawnWeights(), setup.world().spawnWeights());
        assertEquals(classic.world().patterns(), setup.world().patterns());
        assertEquals(classic.world().ambient(), setup.world().ambient());
        assertEquals(classic.world().ruleCycles(), setup.world().ruleCycles());
        assertEquals(classic.world(), setup.world(), "Green Fields is the M1 world, whole");
        assertEquals(classic.tier(), setup.tier());
        assertEquals(classic.speedRampPerTick(), setup.speedRampPerTick(), 0.0);
        assertEquals(classic.streakStep(), setup.streakStep());
        assertEquals(classic.abilities(), setup.abilities());
        assertEquals(classic.forcedPattern(), setup.forcedPattern());
        assertEquals(classic, setup.withModifiers(ModifierCatalog.EMPTY));
        assertFalse(setup.world().hasWorldEffects(),
                "nothing M7 added has work to do in Green Fields, so its hash stands");
    }

    @Test
    void theShippedContentPlaysExactlyLikeTheHardCodedClassicSeam() {
        assertEquals(hashes(new ClassicRunFactory()), hashes(new ContentRunFactory(SHIPPED)),
                "swapping the content-backed factory in must not change a single tick");
    }

    private static List<Long> hashes(SeededRunSource factory) {
        return HeadlessRunner.run(factory.newRun(SEED),
                new BotPilot(BotPilot.Preset.PERFECT, SEED), TICKS, true).hashes();
    }
}
