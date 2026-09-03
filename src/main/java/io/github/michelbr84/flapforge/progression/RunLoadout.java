package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.ArrayList;
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

    /** The feature that gates mid-run drafts (D11, {@code economy.json.features}). */
    public static final String MODIFIERS_FEATURE = "modifiers";

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
                .upgradeLevelsTotal(profile.upgradeLevelsTotal())
                .availableModifiers(availableModifiers(profile, content))
                .allowOffers(allowOffers(profile, content));
    }

    /**
     * The modifier ids the profile owns (M6), so the draft never offers a card the player has not
     * unlocked. The cards that ship {@code unlock: default} are in the list too once the evaluator
     * has granted them, and {@link io.github.michelbr84.flapforge.modifier.ModifierCatalog} keeps
     * them available even before that — what this list adds is the three earned legendaries.
     *
     * @param profile the profile to read
     * @param content the loaded content
     * @return the bare ids, in content order
     */
    public static List<String> availableModifiers(PlayerProfile profile, GameContent content) {
        if (!content.has(GameContent.MODIFIERS)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (ModifierDef def : content.modifiers()) {
            if (profile.isUnlocked(def.unlockableId())) {
                out.add(def.id());
            }
        }
        return out;
    }

    /**
     * Whether mid-run drafts open for this profile (D11).
     *
     * <p>Two conditions, and the second one is a milestone gate rather than a rule of the game:
     * the profile has to own {@code feature:modifiers}, and the feature has to be
     * {@linkplain GameContent#playable(ContentKind, String) playable}, which it becomes when
     * {@code GameContent.FEATURE_MILESTONES} stops naming a milestone for it. It stopped in M6,
     * when {@code ModifierChoiceOverlay} shipped and a frozen draft finally had something that
     * could answer it; the second condition is therefore satisfied today and the gate is the
     * unlock alone (runs 7, or 150 coins in the shop).
     *
     * @param profile the profile to read
     * @param content the loaded content
     * @return {@code true} when a run may open a draft
     */
    public static boolean allowOffers(PlayerProfile profile, GameContent content) {
        return profile.isUnlocked(ContentKind.FEATURE.unlockableId(MODIFIERS_FEATURE))
                && content.playable(ContentKind.FEATURE, MODIFIERS_FEATURE);
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
     * The configuration of a challenge run on a profile (D11, M8): everything the profile owns
     * — the selected bird and palette, the equipped abilities, the upgrade layer, the owned
     * modifiers — under the challenge's own world, tier, flags, forced modifiers and offer
     * switch ({@link RunFactory#challengeConfig}). Nothing here asks whether the world is
     * unlocked: a challenge run is self-contained (E6); the screen that offers it checks the
     * challenge's own unlock.
     *
     * @param profile the profile to read
     * @param content the loaded content
     * @param seed the run seed
     * @param challengeId the challenge
     * @return the configuration, in {@link RunMode#CHALLENGE}
     * @throws io.github.michelbr84.flapforge.content.UnknownIdException when no challenge
     *     carries the id
     */
    public static RunConfig challengeConfigFor(PlayerProfile profile, GameContent content,
            long seed, String challengeId) {
        return new RunFactory(content).challengeConfig(
                configFor(profile, content, seed, RunMode.CHALLENGE), challengeId);
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
