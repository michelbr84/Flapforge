package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@code score_multiplier}: every gate inside the window pays double (D9, E1). */
class ScoreMultiplierBehaviorTest {

    /** Scores one gate and returns the points it awarded. */
    private static double scoreOneGate(Run run) {
        // A gate just to the right of the bird, so it is scored within a few ticks.
        run.simulation().obstacles().add(PipeGate.standard(140, 200, 200, null));
        for (int t = 0; t < 60; t++) {
            run.simulation().bird().setY(320);
            run.simulation().bird().setVy(0);
            TickReport report = run.tick(RunInput.NONE);
            Optional<TickFact.Scored> scored = report.first(TickFact.Scored.class);
            if (scored.isPresent()) {
                return scored.get().points();
            }
        }
        throw new AssertionError("the gate was never scored");
    }

    @Test
    void theWindowDoublesThePointsAGatePays() {
        Run plain = AbilityRuns.started(AbilityRuns.active("score_multiplier"));
        assertEquals(1.0, scoreOneGate(plain), 1e-9, "one point per gate outside the window");

        Run boosted = AbilityRuns.started(AbilityRuns.active("score_multiplier"));
        boosted.tick(AbilityRuns.useAbility());
        assertEquals(2.0, boosted.simulation().stats().resolve(StatId.SCORE_MULT), 0.0);
        assertEquals(2.0, scoreOneGate(boosted), 1e-9, "and two inside it");
        assertEquals(1, boosted.stats().abilitiesUsed().get("score_multiplier"));
    }

    @Test
    void theWindowIsThreeHundredTicksLong() {
        Run run = AbilityRuns.started(AbilityRuns.active("score_multiplier"));
        run.tick(AbilityRuns.useAbility());
        for (int t = 2; t <= 300; t++) {
            run.simulation().bird().setY(320);
            run.simulation().bird().setVy(0);
            run.tick(RunInput.NONE);
        }
        assertTrue(run.simulation().abilities().active().isActive(), "the 300th tick still counts");
        assertEquals(2.0, run.simulation().stats().resolve(StatId.SCORE_MULT), 0.0);

        run.simulation().bird().setY(320);
        run.simulation().bird().setVy(0);
        run.tick(RunInput.NONE);
        assertEquals(1.0, run.simulation().stats().resolve(StatId.SCORE_MULT), 0.0,
                "and the 301st does not");
    }
}
