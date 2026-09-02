package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.List;
import java.util.Objects;

/**
 * Turns a profile into the {@link RunConfig} the player has actually earned (D8, D11, D14).
 *
 * <p>It is the bridge between what is <em>owned</em> and what is <em>played</em>: the selected
 * bird, palette, world and tier, the equipped abilities, and — the point of the whole milestone —
 * the {@code UPGRADES} layer built from {@code profile.upgrades} plus the total of owned levels
 * that the bird synergies scale with ({@code BIRD_SYNERGY}, resolved once at run start by the
 * simulation). Buying {@code feather_1} therefore changes the gravity of the next run, and the
 * selection screen can show the same numbers through {@code StatSheet.breakdown}.
 *
 * <p>Nothing here writes: a run configuration is a snapshot, and a snapshot of a mutable profile
 * has to be taken at a point in time — the moment the run starts.
 *
 * <p>The abilities travel as ids plus {@code profile.passiveSlotBonus} (E3), not as a resolved
 * loadout: how many of the selected passives fit depends on the bird, which the content layer
 * resolves ({@code RunFactory.loadout}), and what the rules allow depends on the world, the tier
 * and the challenge, which the simulation strips defensively at run start (D9).
 */
public final class RunLoadout {

    private RunLoadout() {
    }

    /**
     * Applies everything the profile owns to a run-configuration builder.
     *
     * @param builder the builder to fill, already carrying the seed and the mode
     * @param profile the profile to read
     * @param content the loaded content
     * @return the same builder
     */
    public static RunConfig.Builder configure(RunConfig.Builder builder, PlayerProfile profile,
            GameContent content) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        PlayerProfile.Selection selected = profile.selected;
        return builder
                .birdId(selected.birdId)
                .paletteId(selected.paletteId)
                .worldId(selected.worldId)
                .tierId(selected.tierId)
                .activeAbilityId(selected.activeAbilityId)
                .passiveAbilityIds(selected.passiveAbilityIds)
                .passiveSlotBonus(profile.passiveSlotBonus)
                .abilityLevels(profile.abilityLevels)
                .permanentEffects(upgradeEffects(profile, content))
                .upgradeLevelsTotal(profile.upgradeLevelsTotal());
    }

    /**
     * The configuration of a standard run on a profile.
     *
     * @param profile the profile to read
     * @param content the loaded content
     * @param seed the run seed
     * @param mode the run mode
     * @return the configuration
     */
    public static RunConfig configFor(PlayerProfile profile, GameContent content, long seed,
            RunMode mode) {
        return configure(RunConfig.builder(seed).mode(mode), profile, content).build();
    }

    /**
     * The stat sheet the next run would resolve, for the selection screen (D8, D17: "stat
     * breakdown by source").
     *
     * <p>It is not a second implementation of the pipeline: it builds the very run the player is
     * about to start and reads its sheet, so what the screen shows and what the bird flies with
     * cannot drift apart. {@code StatSheet.breakdown(stat)} then names every contributing
     * modifier — {@code bird:forge}, {@code upgrade:feather_1}, {@code synergy:forge} — with the
     * layer it sits in.
     *
     * @param profile the profile to read
     * @param content the loaded content
     * @return the resolved sheet of a run started right now
     */
    public static StatSheet previewStats(PlayerProfile profile, GameContent content) {
        return previewRun(profile, content).simulation().stats();
    }

    /**
     * The run the profile would start right now, for a screen that wants to show what the next
     * run will actually be (D8, D9, D17).
     *
     * <p>It is a real {@code Run}, not a summary of one: the selection screen reads its stat sheet
     * for the breakdown and its {@code RuleSet} for the abilities the run's world, tier and
     * challenge would strip, and both are then the numbers and the rules the bird will fly with.
     *
     * @param profile the profile to read
     * @param content the loaded content
     * @return the run, never started
     */
    public static Run previewRun(PlayerProfile profile, GameContent content) {
        RunConfig config = configFor(profile, content, profile.lastSeed, RunMode.STANDARD);
        return new RunFactory(content).newRun(config);
    }

    /**
     * The {@code UPGRADES} layer of a run (D8): every owned node at its owned level.
     *
     * @param profile the profile to read
     * @param content the loaded content
     * @return the modifiers, sourced as {@code upgrade:&lt;node&gt;}
     */
    public static List<StatModifier> upgradeEffects(PlayerProfile profile, GameContent content) {
        return UpgradeManager.effectsOf(profile, content);
    }
}
