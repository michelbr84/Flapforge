package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.gameplay.difficulty.DifficultyState;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.BossSpec;
import io.github.michelbr84.flapforge.gameplay.spec.ChallengeSpec;
import io.github.michelbr84.flapforge.gameplay.spec.PatternSpec;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import java.util.List;
import java.util.Objects;

/**
 * The content a run is built from, already resolved from the ids in {@link RunConfig}. The
 * content layer maps JSON definitions to these seam records; tests and the M1 harness use
 * {@link #CLASSIC}.
 *
 * @param bird the bird profile
 * @param world the world
 * @param tier the difficulty tier
 * @param speedRampPerTick {@code difficulty.json.speedRampPerTick}
 * @param streakStep {@code economy.json.rewards.streak.step}, the clean-gate streak length that
 *     pays one reward step (D26)
 * @param abilities the loadout of the run, active first (D9): the selected active ability, the
 *     selected passives that fit the bird's slots and the bird's innate passives, already
 *     resolved from their ids. The rules strip what they forbid when the run starts, so what is
 *     listed here is what the player chose, not necessarily what will be equipped
 * @param modifiers the roguelite content of the run (M6): the offer schedule, the rarity weights,
 *     the cards the profile may be shown and the set bonuses. {@link ModifierCatalog#EMPTY} turns
 *     the whole draft layer off, which is what every seam that plays without content uses
 * @param forcedPattern the one pattern the run streams, looped forever, instead of the world's
 *     spawn table (D7, M7) — a challenge's {@code forcedPattern}, or {@code --pattern} in the
 *     balancing tool; {@code null} for an ordinary run
 * @param boss the boss encounter of the run (D11, M8): the world's for a standard, seeded or
 *     daily run, the challenge's own for a challenge that carries a {@code boss} block, and
 *     {@code null} for a challenge without one, for the world-less content fallback and for a
 *     configuration whose {@link RunConfig#bossEnabled()} is off (the pinned classic run)
 * @param challenge the challenge layer of the run (M8): its {@code CHALLENGE} effects and its
 *     objective; {@code null} outside challenge mode
 */
public record RunSetup(BirdProfile bird, WorldSpec world, TierSpec tier, double speedRampPerTick,
        int streakStep, List<AbilityDef> abilities, ModifierCatalog modifiers,
        PatternSpec forcedPattern, BossSpec boss, ChallengeSpec challenge) {

    /** Forgewing in Green Fields on the normal tier, with no ability equipped. */
    public static final RunSetup CLASSIC = new RunSetup(BirdProfile.CLASSIC, WorldSpec.GREEN_FIELDS,
            TierSpec.NORMAL, DifficultyState.DEFAULT_SPEED_RAMP_PER_TICK);

    /**
     * Validates the components.
     *
     * @param bird the bird profile
     * @param world the world
     * @param tier the tier
     * @param speedRampPerTick the speed ramp per tick
     * @param streakStep the streak reward step
     * @param abilities the loadout, active first
     * @param modifiers the roguelite content of the run
     * @param forcedPattern the forced pattern or {@code null}
     * @param boss the boss encounter or {@code null}
     * @param challenge the challenge layer or {@code null}
     */
    public RunSetup {
        Objects.requireNonNull(bird, "bird");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(tier, "tier");
        if (streakStep < 1) {
            throw new IllegalArgumentException("streakStep must be at least 1: " + streakStep);
        }
        abilities = List.copyOf(abilities);
        modifiers = modifiers == null ? ModifierCatalog.EMPTY : modifiers;
    }

    /**
     * Builds a setup with a loadout, roguelite content and a forced pattern, but no boss and no
     * challenge layer (the M7 shape).
     *
     * @param bird the bird profile
     * @param world the world
     * @param tier the tier
     * @param speedRampPerTick the speed ramp per tick
     * @param streakStep the streak reward step
     * @param abilities the loadout, active first
     * @param modifiers the roguelite content of the run
     * @param forcedPattern the forced pattern or {@code null}
     */
    public RunSetup(BirdProfile bird, WorldSpec world, TierSpec tier, double speedRampPerTick,
            int streakStep, List<AbilityDef> abilities, ModifierCatalog modifiers,
            PatternSpec forcedPattern) {
        this(bird, world, tier, speedRampPerTick, streakStep, abilities, modifiers, forcedPattern,
                null, null);
    }

    /**
     * Builds a setup with a loadout and roguelite content and no forced pattern.
     *
     * @param bird the bird profile
     * @param world the world
     * @param tier the tier
     * @param speedRampPerTick the speed ramp per tick
     * @param streakStep the streak reward step
     * @param abilities the loadout, active first
     * @param modifiers the roguelite content of the run
     */
    public RunSetup(BirdProfile bird, WorldSpec world, TierSpec tier, double speedRampPerTick,
            int streakStep, List<AbilityDef> abilities, ModifierCatalog modifiers) {
        this(bird, world, tier, speedRampPerTick, streakStep, abilities, modifiers, null, null,
                null);
    }

    /**
     * Builds a setup without a loadout (the hard-coded seams and every test that plays without
     * abilities).
     *
     * @param bird the bird profile
     * @param world the world
     * @param tier the tier
     * @param speedRampPerTick the speed ramp per tick
     * @param streakStep the streak reward step
     */
    public RunSetup(BirdProfile bird, WorldSpec world, TierSpec tier, double speedRampPerTick,
            int streakStep) {
        this(bird, world, tier, speedRampPerTick, streakStep, List.of(), ModifierCatalog.EMPTY,
                null, null, null);
    }

    /**
     * Builds a setup with a loadout and no roguelite content.
     *
     * @param bird the bird profile
     * @param world the world
     * @param tier the tier
     * @param speedRampPerTick the speed ramp per tick
     * @param streakStep the streak reward step
     * @param abilities the loadout, active first
     */
    public RunSetup(BirdProfile bird, WorldSpec world, TierSpec tier, double speedRampPerTick,
            int streakStep, List<AbilityDef> abilities) {
        this(bird, world, tier, speedRampPerTick, streakStep, abilities, ModifierCatalog.EMPTY,
                null, null, null);
    }

    /**
     * Copy with another loadout.
     *
     * @param newAbilities the abilities, active first
     * @return the copy
     */
    public RunSetup withAbilities(List<AbilityDef> newAbilities) {
        return new RunSetup(bird, world, tier, speedRampPerTick, streakStep, newAbilities,
                modifiers, forcedPattern, boss, challenge);
    }

    /**
     * Copy with other roguelite content.
     *
     * @param newModifiers the catalogue
     * @return the copy
     */
    public RunSetup withModifiers(ModifierCatalog newModifiers) {
        return new RunSetup(bird, world, tier, speedRampPerTick, streakStep, abilities,
                newModifiers, forcedPattern, boss, challenge);
    }

    /**
     * Copy with a forced pattern (M7): the run streams it, looped, instead of the world's table.
     *
     * @param newForcedPattern the pattern, or {@code null} for the world's own spawns
     * @return the copy
     */
    public RunSetup withForcedPattern(PatternSpec newForcedPattern) {
        return new RunSetup(bird, world, tier, speedRampPerTick, streakStep, abilities,
                modifiers, newForcedPattern, boss, challenge);
    }

    /**
     * Copy with another boss encounter (M8).
     *
     * @param newBoss the boss, or {@code null} for a run without one
     * @return the copy
     */
    public RunSetup withBoss(BossSpec newBoss) {
        return new RunSetup(bird, world, tier, speedRampPerTick, streakStep, abilities,
                modifiers, forcedPattern, newBoss, challenge);
    }

    /**
     * Copy with another challenge layer (M8).
     *
     * @param newChallenge the challenge, or {@code null} outside challenge mode
     * @return the copy
     */
    public RunSetup withChallenge(ChallengeSpec newChallenge) {
        return new RunSetup(bird, world, tier, speedRampPerTick, streakStep, abilities,
                modifiers, forcedPattern, boss, newChallenge);
    }

    /**
     * The same run started at its boss (M8): the warning fires at the first gate and the world's
     * curve is shifted so that gate plays under the difficulty of the authored {@code atGate}
     * ({@link io.github.michelbr84.flapforge.gameplay.spec.CurveSpec#shiftedBy}). The balancing
     * tool's {@code --boss} and {@code ContentFeasibilityTest} measure the encounter itself with
     * it — whether the expert survives the fight, not whether it gets there, which the M7 table
     * already answers.
     *
     * @return the copy
     * @throws IllegalStateException when the setup has no boss
     */
    public RunSetup startingAtBoss() {
        if (boss == null) {
            throw new IllegalStateException("no boss to start at in " + world.id());
        }
        return new RunSetup(bird, world.withCurve(world.curve().shiftedBy(boss.atGate() - 1)),
                tier, speedRampPerTick, streakStep, abilities, modifiers, forcedPattern,
                boss.withAtGate(1), challenge);
    }

    /**
     * Builds a setup with the default streak step ({@link StreakTracker#DEFAULT_STEP}), for the
     * hard-coded seams and the tests that do not load an economy.
     *
     * @param bird the bird profile
     * @param world the world
     * @param tier the tier
     * @param speedRampPerTick the speed ramp per tick
     */
    public RunSetup(BirdProfile bird, WorldSpec world, TierSpec tier, double speedRampPerTick) {
        this(bird, world, tier, speedRampPerTick, StreakTracker.DEFAULT_STEP);
    }

    /**
     * Copy with another bird.
     *
     * @param newBird the bird
     * @return the copy
     */
    public RunSetup withBird(BirdProfile newBird) {
        return new RunSetup(newBird, world, tier, speedRampPerTick, streakStep, abilities,
                modifiers, forcedPattern, boss, challenge);
    }

    /**
     * Copy with another world.
     *
     * @param newWorld the world
     * @return the copy
     */
    public RunSetup withWorld(WorldSpec newWorld) {
        return new RunSetup(bird, newWorld, tier, speedRampPerTick, streakStep, abilities,
                modifiers, forcedPattern, boss, challenge);
    }

    /**
     * Copy with another tier.
     *
     * @param newTier the tier
     * @return the copy
     */
    public RunSetup withTier(TierSpec newTier) {
        return new RunSetup(bird, world, newTier, speedRampPerTick, streakStep, abilities,
                modifiers, forcedPattern, boss, challenge);
    }
}
