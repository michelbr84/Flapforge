package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import org.junit.jupiter.api.Test;

/** {@code shield}: absorb, then ghost until the bird is clear of what it survived (D9). */
class ShieldBehaviorTest {

    @Test
    void theAbsorbGhostsTheBirdUntilNothingOverlapsItAnyMore() {
        Run run = AbilityRuns.started(AbilityRuns.passive("shield", 1));
        PipeGate wall = AbilityRuns.wall(run);
        assertTrue(run.tick(RunInput.NONE).has(TickFact.ShieldAbsorbed.class));
        assertTrue(run.simulation().isGhosting());

        // Hold the bird inside the wall far longer than the 45 invulnerability ticks: the ghost,
        // not the timer, is what keeps it alive while the hitboxes still overlap.
        for (int i = 0; i < 200; i++) {
            wall.setX(AbilityRuns.GATE_X);
            run.simulation().bird().setY(320);
            run.simulation().bird().setVy(0);
            assertFalse(run.tick(RunInput.NONE).has(TickFact.Crashed.class), "tick " + i);
        }
        assertEquals(0, run.simulation().invulnerableTicks(), "the timer ran out long ago");
        assertTrue(run.simulation().isGhosting());
        assertEquals(1, run.stats().shieldAbsorbs(), "and it cost exactly one charge");

        // Clear of it: the bird is solid again on the very next tick.
        run.simulation().obstacles().clear();
        run.tick(RunInput.NONE);
        assertFalse(run.simulation().isGhosting());

        AbilityRuns.wall(run);
        assertTrue(run.tick(RunInput.NONE).has(TickFact.Crashed.class),
                "no charge, no ghost, no second chance");
        assertEquals(RunPhase.DYING, run.phase());
    }

    @Test
    void theRegeneratingShieldGivesTheChargeBackAfterTheCadence() {
        Run run = AbilityRuns.started(AbilityRuns.passive("shield", 3));
        assertEquals(10, run.simulation().shield().regenEveryGates());
        assertTrue(run.simulation().shield().absorb());
        assertEquals(0, run.simulation().shield().charges());
        assertFalse(run.simulation().shield().onGatePassed(9));
        assertTrue(run.simulation().shield().onGatePassed(10));
        assertEquals(1, run.simulation().shield().charges());
    }
}
