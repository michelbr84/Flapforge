package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.support.ScriptedPilot;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunInputTest {

    private static Run suppressedRun(long seed) {
        Run run = Run.classic(RunConfig.classic(seed));
        run.simulation().spawner().setSuppressed(true);
        return run;
    }

    @Test
    void autoFlapFiresEvery24TicksWhileHeld() {
        Run run = suppressedRun(1);
        List<Integer> flapTicks = new ArrayList<>();
        for (int t = 0; t < 200 && run.phase() != RunPhase.FINISHED; t++) {
            RunInput input = t == 0 ? RunInput.FLAP : RunInput.AUTO_FLAP;
            TickReport report = run.tick(input);
            if (report.has(TickFact.Flapped.class) || report.has(TickFact.FlapRefused.class)) {
                flapTicks.add(t);
            }
        }
        assertEquals(24, Playfield.AUTO_FLAP_PERIOD_TICKS);
        assertEquals(List.of(0, 24, 48, 72, 96, 120, 144, 168, 192), flapTicks);
    }

    @Test
    void manualFlapResetsTheAutoFlapTimer() {
        Run run = suppressedRun(1);
        List<Integer> flapTicks = new ArrayList<>();
        for (int t = 0; t < 80; t++) {
            RunInput input;
            if (t == 0 || t == 10) {
                input = new RunInput(true, false, RunInput.NO_CHOICE, true);
            } else {
                input = RunInput.AUTO_FLAP;
            }
            TickReport report = run.tick(input);
            if (report.has(TickFact.Flapped.class)) {
                flapTicks.add(t);
            }
        }
        assertEquals(List.of(0, 10, 34, 58), flapTicks);
    }

    @Test
    void noAutoFlapWithoutTheHold() {
        Run run = suppressedRun(1);
        int flaps = 0;
        for (int t = 0; t < 60; t++) {
            TickReport report = run.tick(t == 0 ? RunInput.FLAP : RunInput.NONE);
            if (report.has(TickFact.Flapped.class)) {
                flaps++;
            }
        }
        assertEquals(1, flaps);
    }

    @Test
    void botNeverHoldsTheFlapKey() {
        Run run = Run.classic(RunConfig.classic(5));
        BotPilot bot = new BotPilot(BotPilot.Preset.AVERAGE, 5);
        int decisions = 0;
        int flaps = 0;
        while (!run.isFinished() && decisions < 600) {
            RunInput input = bot.decide(run);
            assertFalse(input.autoFlapHeld(), "bots flap by edges only");
            assertFalse(input.ability());
            assertEquals(RunInput.NO_CHOICE, input.choice());
            if (input.flap()) {
                flaps++;
            }
            run.tick(input);
            decisions++;
        }
        assertTrue(flaps > 5);
    }

    @Test
    void scriptedPilotReplaysTickIndices() {
        Run run = suppressedRun(2);
        ScriptedPilot pilot = ScriptedPilot.flapsAt(3, 5, 5, 40);
        List<Integer> flapTicks = new ArrayList<>();
        for (int t = 0; t < 60; t++) {
            RunInput input = pilot.decide(run);
            assertEquals(t, run.tick());
            TickReport report = run.tick(input);
            if (report.has(TickFact.Flapped.class)) {
                flapTicks.add(t);
            }
        }
        assertEquals(List.of(3, 5, 40), flapTicks);
        assertEquals(RunPhase.FLYING, run.phase());
        ScriptedPilot holding = new ScriptedPilot(List.of(0), true);
        assertTrue(holding.decide(Run.classic(RunConfig.classic(1))).autoFlapHeld());
    }

    @Test
    void ticksSinceLastFlapIsExposed() {
        Run run = suppressedRun(1);
        run.tick(RunInput.FLAP);
        assertEquals(0, run.simulation().ticksSinceLastFlap());
        run.tick(RunInput.NONE);
        run.tick(RunInput.NONE);
        assertEquals(2, run.simulation().ticksSinceLastFlap());
    }
}
