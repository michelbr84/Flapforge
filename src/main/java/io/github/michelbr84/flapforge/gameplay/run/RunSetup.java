package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.gameplay.difficulty.DifficultyState;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
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
 */
public record RunSetup(BirdProfile bird, WorldSpec world, TierSpec tier, double speedRampPerTick,
        int streakStep) {

    /** Forgewing in Green Fields on the normal tier. */
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
     */
    public RunSetup {
        Objects.requireNonNull(bird, "bird");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(tier, "tier");
        if (streakStep < 1) {
            throw new IllegalArgumentException("streakStep must be at least 1: " + streakStep);
        }
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
        return new RunSetup(newBird, world, tier, speedRampPerTick, streakStep);
    }

    /**
     * Copy with another world.
     *
     * @param newWorld the world
     * @return the copy
     */
    public RunSetup withWorld(WorldSpec newWorld) {
        return new RunSetup(bird, newWorld, tier, speedRampPerTick, streakStep);
    }

    /**
     * Copy with another tier.
     *
     * @param newTier the tier
     * @return the copy
     */
    public RunSetup withTier(TierSpec newTier) {
        return new RunSetup(bird, world, newTier, speedRampPerTick, streakStep);
    }
}
