package io.github.michelbr84.flapforge.ability;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import java.util.List;
import java.util.Map;

/**
 * Builds runs with a loadout from the shipped {@code abilities.json}: the behaviour tests must
 * exercise the numbers the game ships, not numbers invented in a fixture.
 */
final class AbilityRuns {

    /** The bird column, where a test gate is placed to guarantee a hit. */
    static final double GATE_X = 95;

    private static GameContent content;

    private AbilityRuns() {
    }

    static synchronized GameContent content() {
        if (content == null) {
            content = GameContent.load();
        }
        return content;
    }

    static RunFactory factory() {
        return new RunFactory(content());
    }

    static AbilityDef def(String id) {
        return content().abilities().get(id);
    }

    /** A configuration builder with the given loadout. */
    static RunConfig.Builder config(long seed, String active, List<String> passives) {
        return RunConfig.builder(seed).activeAbilityId(active).passiveAbilityIds(passives);
    }

    /** A run with one active ability at level 1. */
    static Run active(String id) {
        return factory().newRun(config(7, id, List.of()).build());
    }

    /** A run with one active ability at a level. */
    static Run active(String id, int level) {
        return factory().newRun(config(7, id, List.of())
                .abilityLevels(Map.of(id, level)).build());
    }

    /** A run with one passive ability at a level. */
    static Run passive(String id, int level) {
        return factory().newRun(config(7, null, List.of(id))
                .abilityLevels(Map.of(id, level)).build());
    }

    /** A run with one passive ability at a level and extra rules. */
    static Run passive(String id, int level, RuleSet rules) {
        return factory().newRun(config(7, null, List.of(id))
                .abilityLevels(Map.of(id, level)).rules(rules).build());
    }

    /** Starts the run, stops the spawner and drops whatever it spawned on the first tick. */
    static Run started(Run run) {
        run.tick(RunInput.FLAP);
        run.simulation().spawner().setSuppressed(true);
        run.simulation().obstacles().clear();
        return run;
    }

    /**
     * Puts a wall in the bird's column: a gate whose gap is at the very top, so a bird flying at
     * its start height is inside the lower pipe.
     *
     * @param run the run
     * @return the gate
     */
    static PipeGate wall(Run run) {
        PipeGate gate = PipeGate.standard(GATE_X, 0, 72, null);
        run.simulation().obstacles().add(gate);
        return gate;
    }

    /** Ticks a run n times with no input. */
    static void idle(Run run, int ticks) {
        for (int i = 0; i < ticks; i++) {
            run.tick(RunInput.NONE);
        }
    }

    /** The ability activation input. */
    static RunInput useAbility() {
        return new RunInput(false, true, RunInput.NO_CHOICE, false);
    }
}
