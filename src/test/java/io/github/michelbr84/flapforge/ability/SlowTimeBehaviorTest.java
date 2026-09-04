package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import org.junit.jupiter.api.Test;

/**
 * {@code slow_time}: the world halves its pace, the bird does not (D8, D9). The apex of a flap is
 * the muscle memory the whole game is built on, so it is asserted here rather than assumed.
 */
class SlowTimeBehaviorTest {

    @Test
    void theApexStaysFortyTwoPixelsWhileTimeIsSlowed() {
        Run run = AbilityRuns.started(AbilityRuns.active("slow_time"));
        run.tick(AbilityRuns.useAbility());
        assertEquals(0.5, run.simulation().stats().resolve(StatId.TIME_SCALE), 0.0);

        run.simulation().bird().setY(320);
        run.simulation().bird().setVy(0);
        run.tick(RunInput.FLAP);
        double start = 320.0;
        double best = run.simulation().bird().y();
        int bestTick = 1;
        for (int t = 2; t <= 30; t++) {
            run.tick(RunInput.NONE);
            double y = run.simulation().bird().y();
            if (t == 13) {
                assertEquals(42.25, start - y, 1e-9, "the classic apex, under slow time");
            }
            if (t == 14) {
                assertEquals(42.0, start - y, 1e-9);
            }
            if (y < best) {
                best = y;
                bestTick = t;
            }
        }
        assertEquals(13, bestTick, "and it is reached on the same tick as always");
        assertEquals(0.5, run.simulation().stats().resolve(StatId.TIME_SCALE), 0.0,
                "the 90-tick window is still open");
    }

    @Test
    void theWorldScrollsAtHalfSpeedForNinetyTicks() {
        Run run = AbilityRuns.started(AbilityRuns.active("slow_time"));
        PipeGate gate = PipeGate.standard(400, 200, 128, null);
        run.simulation().obstacles().add(gate);
        run.tick(RunInput.NONE);
        double normalStep = 400 - gate.x();
        assertEquals(2.0, normalStep, 1e-9, "120 px/s at 60 Hz");

        double before = gate.x();
        run.tick(AbilityRuns.useAbility());
        assertEquals(1.0, before - gate.x(), 1e-9, "half a step while the window is open");

        for (int t = 2; t <= 90; t++) {
            run.simulation().bird().setY(320);
            run.simulation().bird().setVy(0);
            run.tick(RunInput.NONE);
        }
        double last = gate.x();
        run.simulation().bird().setY(320);
        run.simulation().bird().setVy(0);
        run.tick(RunInput.NONE);
        assertEquals(2.0, last - gate.x(), 1e-9, "the 91st tick is back to full speed");
        assertTrue(run.simulation().obstacles().obstacles().contains((Obstacle) gate));
    }
}
