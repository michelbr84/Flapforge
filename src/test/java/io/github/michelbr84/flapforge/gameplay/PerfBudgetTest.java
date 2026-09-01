package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.obstacle.Oscillator;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Local-only budget (§7): 10 000 simulation ticks with at least 6 obstacles alive in 300 ms. */
@Tag("perf")
class PerfBudgetTest {

    private static final int MIN_OBSTACLES = 6;
    private static final int TICKS = 10_000;
    private static final long BUDGET_NANOS = 300_000_000L;

    @Test
    void tenThousandBusyTicksUnderThreeHundredMilliseconds() {
        // JIT warm-up on the same code path.
        runTicks(3000, 1);
        runTicks(3000, 2);
        long start = System.nanoTime();
        int obstaclesSeen = runTicks(TICKS, 3);
        long elapsed = System.nanoTime() - start;
        assertTrue(obstaclesSeen >= MIN_OBSTACLES, "obstacles alive per tick: " + obstaclesSeen);
        assertTrue(elapsed < BUDGET_NANOS, "10 000 ticks took " + elapsed / 1_000_000 + " ms");
    }

    /**
     * Ticks runs driven by the perfect bot, topping the layer up to six gates (all at the same
     * gap height so the bot always survives); restarts a fresh run if one ends.
     *
     * @return the minimum number of obstacles alive before any tick
     */
    private static int runTicks(int ticks, long seed) {
        int minAlive = Integer.MAX_VALUE;
        Run run = Run.classic(RunConfig.classic(seed));
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, seed);
        for (int t = 0; t < ticks; t++) {
            if (run.isFinished()) {
                run = Run.classic(RunConfig.classic(seed + t));
                bot = new BotPilot(BotPilot.Preset.PERFECT, seed + t);
            }
            ObstacleLayer layer = run.simulation().obstacles();
            if (t > 0) {
                while (layer.size() < MIN_OBSTACLES) {
                    Obstacle last = layer.last();
                    double x = last == null ? 420 : last.x() + 120;
                    layer.add(PipeGate.standard(x, 240, 128, Oscillator.classic()));
                }
                minAlive = Math.min(minAlive, layer.size());
            }
            run.tick(bot.decide(run));
        }
        return minAlive;
    }
}
