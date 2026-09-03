package io.github.michelbr84.flapforge.support;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleParams;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.BossSpec;
import io.github.michelbr84.flapforge.gameplay.spec.PatternSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Boss encounters on a corridor with no surprises (E17, M8): the boss phases are flat patterns
 * whose gates sit exactly where {@link FixedSpawnTable} puts its own — gap centred on the bird's
 * start height, 160 px apart — so a pilot that holds the centre survives the warning, the fight
 * and the air after it, and the only thing a test measures is the encounter.
 *
 * <p>{@link #fly} is that pilot: one flap to start, then a flap whenever the bird sinks ten
 * pixels under its start height, and the first card of any draft.
 */
public final class BossRuns {

    /** The gap centre, as a fraction of the playable height, that rounds to the fixed table's top. */
    public static final double FLAT_CENTRE = 0.5351170568561873;
    /** The world id the flat world boss belongs to. */
    public static final String WORLD = "green_fields";
    /** The challenge id the flat challenge boss belongs to. */
    public static final String CHALLENGE = "boss_test";

    private BossRuns() {
    }

    /**
     * A flat phase: standard gates 160 px apart, centred on the bird's start height.
     *
     * @param id the pattern id
     * @param steps how many gates
     * @return the pattern, weight 0 and scoring
     */
    public static PatternSpec flatPhase(String id, int steps) {
        List<PatternSpec.Step> out = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            out.add(new PatternSpec.Step(Playfield.GATE_INTERVAL, ObstacleKind.PIPE_GATE,
                    ObstacleParams.resolve(ObstacleKind.PIPE_GATE, Map.of("layout", "STANDARD",
                            "gapCenter", FLAT_CENTRE, "gapSize", (double) Playfield.GAP)),
                    true));
        }
        return new PatternSpec(id, 0, 0, true, out);
    }

    /**
     * A world boss on the flat corridor (E26: clears {@link #WORLD}).
     *
     * @param atGate the warning gate
     * @param warningTicks the warning length
     * @param surviveTicks the fight length
     * @param phases the phases
     * @return the boss
     */
    public static BossSpec worldBoss(int atGate, int warningTicks, int surviveTicks,
            PatternSpec... phases) {
        return new BossSpec(WORLD, WORLD, atGate, warningTicks, List.of(phases), surviveTicks);
    }

    /**
     * A challenge boss on the flat corridor (E26: clears no world).
     *
     * @param atGate the warning gate
     * @param warningTicks the warning length
     * @param surviveTicks the fight length
     * @param phases the phases
     * @return the boss
     */
    public static BossSpec challengeBoss(int atGate, int warningTicks, int surviveTicks,
            PatternSpec... phases) {
        return new BossSpec(CHALLENGE, null, atGate, warningTicks, List.of(phases),
                surviveTicks);
    }

    /**
     * Two flat phases of four gates each, the shape of every shipped world boss.
     *
     * @return the phases
     */
    public static PatternSpec[] twoPhases() {
        return new PatternSpec[] {flatPhase("flat_p1", 4), flatPhase("flat_p2", 4)};
    }

    /**
     * A run on the fixed corridor.
     *
     * @param config the configuration
     * @param setup the setup, typically {@code RunSetup.CLASSIC.withBoss(...)}
     * @return the run, in {@code READY}
     */
    public static Run run(RunConfig config, RunSetup setup) {
        return new Run(config, setup, new FixedSpawnTable());
    }

    /**
     * The pilot that holds the centre of the corridor.
     *
     * @param run the run
     * @return the input for the next tick
     */
    public static RunInput fly(Run run) {
        if (run.phase() == RunPhase.READY) {
            return RunInput.FLAP;
        }
        if (run.phase() == RunPhase.CHOOSING_MODIFIER) {
            return RunInput.choose(0);
        }
        return run.simulation().bird().y() > Playfield.BIRD_START_Y + 10
                ? RunInput.FLAP : RunInput.NONE;
    }
}
