package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.ContentAdapters;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
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
