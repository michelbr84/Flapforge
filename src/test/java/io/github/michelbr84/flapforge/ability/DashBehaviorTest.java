package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code dash}: 20 ticks of held line at 2.5x the scroll (D9, E24 — the line is a bird-level
 * override, the speed is a stat).
 */
class DashBehaviorTest {

    @Test
    void theBurstHoldsTheLineForExactlyTwentyTicks() {
        Run run = AbilityRuns.started(AbilityRuns.active("dash"));
        AbilityRuns.idle(run, 10);
        run.tick(AbilityRuns.useAbility());
        double held = run.simulation().bird().y();

        for (int t = 2; t <= 20; t++) {
            run.tick(RunInput.NONE);
            assertEquals(held, run.simulation().bird().y(), 0.0, "tick " + t + " of the burst");
            assertEquals(0.0, run.simulation().bird().vy(), 0.0);
        }
        assertEquals(1, run.simulation().abilities().active().durationRemaining(),
                "twenty ticks held; the counter runs out at the start of the next one");

        run.tick(RunInput.NONE);
        assertFalse(run.simulation().abilities().active().isActive());
        assertNotEquals(held, run.simulation().bird().y(), "the 21st tick falls again");
        assertEquals(1800.0 / 60, run.simulation().bird().vy(), 1e-9,
                "and it falls from a standstill, not from the speed it had before the dash");
    }

    @Test
    void theScrollGoesThroughTheStatPipelineAndClampsAtItsCeiling() {
        Run normal = AbilityRuns.started(AbilityRuns.active("dash"));
        normal.tick(AbilityRuns.useAbility());
        assertEquals(300.0, normal.simulation().stats().resolve(StatId.SCROLL_SPEED), 0.0,
                "120 x 2.5");

        Run nightmare = AbilityRuns.started(AbilityRuns.factory().newRun(
                AbilityRuns.config(9, "dash", List.of()).tierId("nightmare").build()));
        assertEquals(156.0, nightmare.simulation().stats().resolve(StatId.SCROLL_SPEED), 1e-9,
                "120 x 1.3 from the tier");
        nightmare.tick(AbilityRuns.useAbility());
        assertEquals(360.0, nightmare.simulation().stats().resolve(StatId.SCROLL_SPEED), 0.0,
                "156 x 2.5 = 390 clamps to the SCROLL_SPEED ceiling of 360 (D8)");
    }

    @Test
    void theBurstGrantsInvulnerabilityForItsWholeLengthAndThenGhostsOutOfWhatItHit() {
        Run run = AbilityRuns.started(AbilityRuns.active("dash"));
        PipeGate wall = AbilityRuns.wall(run);
        assertTrue(run.tick(AbilityRuns.useAbility()).has(TickFact.AbilityActivated.class));
        assertEquals(20, run.simulation().invulnerableTicks());
        for (int t = 2; t <= 20; t++) {
            wall.setX(AbilityRuns.GATE_X);
            assertFalse(run.tick(RunInput.NONE).has(TickFact.Crashed.class),
                    "the dash flies through the wall, tick " + t);
        }
        // The i-frames are spent exactly with the burst, and at level 1 there are no extra ticks:
        // what keeps the bird alive from here is "ghost until clear", the rule D9 gives a shield
        // absorb. Without it the level-1 dash killed the bird on its release frame (measured:
        // 13.9 mean gates against the 79.6 of a bird with no ability at all).
        for (int t = 21; t <= 40; t++) {
            wall.setX(AbilityRuns.GATE_X);
            assertFalse(run.tick(RunInput.NONE).has(TickFact.Crashed.class),
                    "the burst must not end inside the pipe it flew into, tick " + t);
        }
        assertEquals(0, run.simulation().invulnerableTicks(), "and not thanks to i-frames");
        assertTrue(run.simulation().isGhosting());

        run.simulation().obstacles().clear();
        run.simulation().bird().setY(320);
        run.tick(RunInput.NONE);
        assertFalse(run.simulation().isGhosting(), "clear of it, the bird is solid again");
        AbilityRuns.wall(run);
        assertTrue(run.tick(RunInput.NONE).has(TickFact.Crashed.class),
                "and the next hazard is a normal death");
    }

    @Test
    void aHazardThatArrivesAfterTheBurstIsNotFreeToo() {
        Run run = AbilityRuns.started(AbilityRuns.active("dash"));
        PipeGate flownInto = AbilityRuns.wall(run);
        run.tick(AbilityRuns.useAbility());
        for (int t = 2; t <= 25; t++) {
            flownInto.setX(AbilityRuns.GATE_X);
            run.tick(RunInput.NONE);
        }
        assertTrue(run.simulation().isGhosting(), "still ghosting out of the first wall");

        // A second, unrelated column: the ghost was granted against the one the burst ended in.
        run.simulation().obstacles().clear();
        AbilityRuns.wall(run);
        assertTrue(run.tick(RunInput.NONE).has(TickFact.Crashed.class),
                "one ghost covers one hazard, not the world");
    }

    @Test
    void aFlapDuringTheBurstIsRefusedInsteadOfBeingEaten() {
        Run run = AbilityRuns.started(AbilityRuns.active("dash"));
        AbilityRuns.idle(run, 10);
        run.tick(AbilityRuns.useAbility());
        double held = run.simulation().bird().y();
        int flaps = run.simulation().flaps();

        for (int t = 2; t <= 20; t++) {
            assertFalse(run.tick(RunInput.FLAP).has(TickFact.Flapped.class),
                    "a flap the hold is about to undo must not answer, tick " + t);
            assertEquals(held, run.simulation().bird().y(), 0.0);
        }
        assertEquals(flaps, run.simulation().flaps(), "and none of them counted");
        assertTrue(run.tick(RunInput.FLAP).has(TickFact.Flapped.class),
                "the tick the burst ends, the control answers again");
    }

    @Test
    void levelThreeDashesLongerAndKeepsTheIFramesAfterTheBurst() {
        Run run = AbilityRuns.started(AbilityRuns.active("dash", 3));
        run.tick(AbilityRuns.useAbility());
        assertEquals(32, run.simulation().abilities().active().durationRemaining());
        assertEquals(44, run.simulation().invulnerableTicks(), "32 + 12 extra ticks");
    }

    @Test
    void noDefensiveAbilitiesSuppressesTheIFramesButNotTheBurst() {
        Run run = AbilityRuns.started(AbilityRuns.factory().newRun(
                AbilityRuns.config(9, "dash", List.of())
                        .rules(RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES)).build()));
        assertFalse(run.simulation().abilities().isEmpty(), "the dash is MOVEMENT, not DEFENSIVE");
        AbilityRuns.idle(run, 5);
        double before = run.simulation().bird().y();
        run.tick(AbilityRuns.useAbility());
        assertEquals(0, run.simulation().invulnerableTicks(), "no i-frames under the flag");
        assertEquals(300.0, run.simulation().stats().resolve(StatId.SCROLL_SPEED), 0.0);
        run.tick(RunInput.NONE);
        assertEquals(before, run.simulation().bird().y(), 1e-9, "the held line stays");

        AbilityRuns.wall(run);
        assertTrue(run.tick(RunInput.NONE).has(TickFact.Crashed.class),
                "and the wall kills the dashing bird");
    }
}
