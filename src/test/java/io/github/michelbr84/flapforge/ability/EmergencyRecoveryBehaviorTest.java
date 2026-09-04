package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code emergency_recovery}: one revive per charge, with the kick and the longer window. */
class EmergencyRecoveryBehaviorTest {

    @Test
    void itFiresOncePerChargeAndTheSecondHitIsFatal() {
        Run run = AbilityRuns.started(AbilityRuns.passive("emergency_recovery", 1));
        assertEquals(1.0, run.simulation().stats().resolve(StatId.REVIVES), 0.0);

        PipeGate wall = AbilityRuns.wall(run);
        assertTrue(run.tick(RunInput.NONE).has(TickFact.Revived.class));
        assertEquals(1, run.stats().revives());

        // Fly the 90 invulnerability ticks out inside the same wall: the ghost and the timer keep
        // the bird alive, and neither spends a second charge that is not there.
        for (int t = 0; t < 200; t++) {
            wall.setX(AbilityRuns.GATE_X);
            run.simulation().bird().setY(320);
            run.simulation().bird().setVy(0);
            assertFalse(run.tick(RunInput.NONE).has(TickFact.Crashed.class), "tick " + t);
        }
        assertEquals(1, run.stats().revives(), "one charge, one revive");

        run.simulation().obstacles().clear();
        run.tick(RunInput.NONE);
        AbilityRuns.wall(run);
        assertTrue(run.tick(RunInput.NONE).has(TickFact.Crashed.class));
        assertEquals(1, run.stats().revives());
    }

    @Test
    void twoChargesRevivesTwice() {
        Run run = AbilityRuns.started(AbilityRuns.factory().newRun(
                AbilityRuns.config(21, null, List.of("emergency_recovery"))
                        .permanentEffects(List.of(StatModifier.flat(StatId.REVIVES, 1,
                                "upgrade:second_chance_1")))
                        .build()));
        assertEquals(2, run.simulation().revive().maxCharges(), "REVIVES clamps at 2 (D8)");
        AbilityRuns.wall(run);
        assertTrue(run.tick(RunInput.NONE).has(TickFact.Revived.class));

        run.simulation().obstacles().clear();
        for (int t = 0; t < 120; t++) {
            run.simulation().bird().setY(320);
            run.simulation().bird().setVy(0);
            run.tick(RunInput.NONE);
        }
        AbilityRuns.wall(run);
        assertTrue(run.tick(RunInput.NONE).has(TickFact.Revived.class), "the second charge");
        assertEquals(2, run.stats().revives());
        assertEquals(0, run.simulation().revive().charges());
    }
}
