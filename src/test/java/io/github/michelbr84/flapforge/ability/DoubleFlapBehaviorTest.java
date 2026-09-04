package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import org.junit.jupiter.api.Test;

/** {@code double_flap}: the fall is zeroed and replaced by 1.5x a flap, whatever it was (D9). */
class DoubleFlapBehaviorTest {

    private static final double FLAP = 405;

    @Test
    void theActivationZeroesTheFallAndSetsOneAndAHalfFlaps() {
        Run run = AbilityRuns.started(AbilityRuns.active("double_flap"));
        AbilityRuns.idle(run, 20);
        assertTrue(run.simulation().bird().vy() > 0, "the bird is falling");

        run.tick(AbilityRuns.useAbility());
        // The activation runs before the tick's integration, so the velocity the tick ends with
        // is the set one plus this tick's gravity step.
        assertEquals(-FLAP * 1.5 + 1800.0 / 60, run.simulation().bird().vy(), 1e-9);
    }

    @Test
    void aDivingBirdAndAHoveringBirdLeaveTheActivationAtTheSameSpeed() {
        Run diving = AbilityRuns.started(AbilityRuns.active("double_flap"));
        AbilityRuns.idle(diving, 25);
        double fast = diving.simulation().bird().vy();
        diving.tick(AbilityRuns.useAbility());

        Run hovering = AbilityRuns.started(AbilityRuns.active("double_flap"));
        hovering.simulation().bird().setVy(0);
        hovering.tick(AbilityRuns.useAbility());

        assertTrue(fast > 300, "the diving bird really was falling: " + fast);
        assertEquals(hovering.simulation().bird().vy(), diving.simulation().bird().vy(), 1e-9);
    }

    @Test
    void theLevelThreeFlapIsStronger() {
        Run run = AbilityRuns.started(AbilityRuns.active("double_flap", 3));
        run.simulation().bird().setVy(0);
        run.tick(AbilityRuns.useAbility());
        assertEquals(-FLAP * 1.6 + 1800.0 / 60, run.simulation().bird().vy(), 1e-9);
        assertEquals(FLAP, run.simulation().stats().resolve(StatId.FLAP_VELOCITY), 0.0,
                "the impulse is one-off: FLAP_VELOCITY itself never changes");
    }

    /**
     * The ceiling gate the ordinary flap obeys (M1 parity, upstream's {@code rect.y > 20}) applies
     * to the ability that amplifies it. Without it a press at the ceiling set {@code vy = -607.5}
     * and carried the bird about 95 px above the playfield, where nothing clamps it.
     */
    @Test
    void itIsRefusedAtTheCeilingAndTheChargeIsNotSpent() {
        Run run = AbilityRuns.started(AbilityRuns.active("double_flap"));
        AbilityInstance ability = run.simulation().abilities().active();
        run.simulation().bird().setY(Playfield.CEILING_FLAP_Y - 1);
        run.simulation().bird().setVy(0);

        TickReport report = run.tick(AbilityRuns.useAbility());
        assertFalse(report.has(TickFact.AbilityActivated.class), "the physics refuses it");
        assertEquals(2, ability.charges(), "and a refused press costs nothing");
        assertEquals(0, ability.cooldownRemaining());
        assertEquals(1800.0 / 60, run.simulation().bird().vy(), 1e-9, "the bird simply falls");

        run.simulation().bird().setY(Playfield.CEILING_FLAP_Y + 1);
        run.simulation().bird().setVy(0);
        assertTrue(run.tick(AbilityRuns.useAbility()).has(TickFact.AbilityActivated.class),
                "one pixel below the gate it works");
        assertEquals(1, ability.charges());
    }

    @Test
    void pressingBothOnOneTickLandsTheAbilityNotTheFlap() {
        Run run = AbilityRuns.started(AbilityRuns.active("double_flap"));
        run.simulation().bird().setVy(200);
        run.tick(new RunInput(true, true, RunInput.NO_CHOICE, false));
        assertEquals(-FLAP * 1.5 + 1800.0 / 60, run.simulation().bird().vy(), 1e-9,
                "the activation runs after the flap, so the charge is never wasted on a tick the "
                        + "player also flapped");
    }
}
