package io.github.michelbr84.flapforge.content;

import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds runs from content (D10, D11): it resolves the ids in a {@link RunConfig} against the
 * registries and hands {@link Run} the {@link RunSetup} it needs. Everything that plays the game
 * — the game screen, the headless hash, the bot harness, the balancing tool — goes through here,
 * so they all read the same data.
 */
public final class RunFactory {

    /** Curve of Green Fields: pure upstream (D20). */
    public static final String GREEN_FIELDS_CURVE = "classic";
    /** Curve of every other world until {@code worlds.json} ships in M7 (D20). */
    public static final String DEFAULT_CURVE = "standard";
    /** Gates are the only obstacle family Green Fields spawns (D6). */
    public static final int PIPE_GATE_WEIGHT = 100;

    private final GameContent content;

    /**
     * Creates a factory over a content set.
     *
     * @param content the content
     */
    public RunFactory(GameContent content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    /**
     * The content this factory reads.
     *
     * @return the content
     */
    public GameContent content() {
        return content;
    }

    /**
     * Resolves the ids of a configuration into simulation records.
     *
     * @param config the configuration
     * @return the setup
     * @throws UnknownIdException when the bird, tier or curve id is not in the registries
     */
    public RunSetup setup(RunConfig config) {
        Objects.requireNonNull(config, "config");
        WorldSpec world = new WorldSpec(config.worldId(),
                content.curveSpec(curveIdFor(config.worldId())), List.of(),
                RuleSet.EMPTY, Map.of(ObstacleKind.PIPE_GATE, PIPE_GATE_WEIGHT));
        return new RunSetup(content.birdProfile(config.birdId()), world,
                content.tierSpec(config.tierId()), content.speedRampPerTick(),
                content.economy().rewards().streak().step());
    }

    /**
     * Builds a run.
     *
     * @param config the configuration
     * @return a run in phase {@code READY}
     * @throws UnknownIdException when an id is not in the registries
     */
    public Run newRun(RunConfig config) {
        return new Run(config, setup(config));
    }

    /**
     * Builds a run with the default configuration for a seed.
     *
     * @param seed the run seed
     * @return a run in phase {@code READY}
     */
    public Run newRun(long seed) {
        return newRun(RunConfig.classic(seed));
    }

    /**
     * The curve a world uses.
     *
     * <p>M1 has no {@code worlds.json}: Green Fields is the classic curve and anything else falls
     * back to {@code standard}. From M7 the mapping comes from {@code WorldDef.curve}.
     *
     * @param worldId the world id
     * @return the curve id
     */
    public String curveIdFor(String worldId) {
        // TODO(M7): read the curve from worlds.json instead of this two-case fallback.
        return RunConfig.DEFAULT_WORLD.equals(worldId) ? GREEN_FIELDS_CURVE : DEFAULT_CURVE;
    }
}
