package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.obstacle.Oscillator;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Local-only budget (§7): 10 000 simulation ticks with at least 6 obstacles alive in 300 ms. */
@Tag("perf")
class PerfBudgetTest {

    private static final int MIN_OBSTACLES = 6;
    private static final int TICKS = 10_000;
    private static final long BUDGET_NANOS = 300_000_000L;
    /** The Void mix (§4): every family, so the M7 kinds are on the measured path. */
    private static final Map<ObstacleKind, Integer> FIVE_KINDS = Map.of(
            ObstacleKind.PIPE_GATE, 40, ObstacleKind.GEAR, 20, ObstacleKind.PISTON, 20,
            ObstacleKind.WIND_ZONE, 10, ObstacleKind.LIGHTNING, 10);

    @Test
    void tenThousandBusyTicksUnderThreeHundredMilliseconds() {
        // JIT warm-up on the same code path.
        runTicks(3000, 1, null);
        runTicks(3000, 2, null);
        long start = System.nanoTime();
        int obstaclesSeen = runTicks(TICKS, 3, null);
        long elapsed = System.nanoTime() - start;
        System.out.println("perf: 10 000 Green Fields ticks in " + elapsed / 1_000_000 + " ms");
        assertTrue(obstaclesSeen >= MIN_OBSTACLES, "obstacles alive per tick: " + obstaclesSeen);
        assertTrue(elapsed < BUDGET_NANOS, "10 000 ticks took " + elapsed / 1_000_000 + " ms");
    }

    /** M7: the same budget with a spawn table that draws all five families. */
    @Test
    void tenThousandFiveKindTicksUnderThreeHundredMilliseconds() {
        SpawnTable table = new SpawnTable(FIVE_KINDS);
        runTicks(3000, 1, table);
        runTicks(3000, 2, table);
        long start = System.nanoTime();
        int obstaclesSeen = runTicks(TICKS, 3, table);
        long elapsed = System.nanoTime() - start;
        System.out.println("perf: 10 000 five-kind ticks in " + elapsed / 1_000_000 + " ms");
        assertTrue(obstaclesSeen >= MIN_OBSTACLES, "obstacles alive per tick: " + obstaclesSeen);
        assertTrue(elapsed < BUDGET_NANOS, "10 000 five-kind ticks took " + elapsed / 1_000_000
                + " ms");
    }

    /**
     * Ticks runs driven by the perfect bot, topping the layer up to six gates (all at the same
     * gap height so the bot always survives); restarts a fresh run if one ends.
     *
     * @return the minimum number of obstacles alive before any tick
     */
    private static int runTicks(int ticks, long seed, SpawnTable table) {
        int minAlive = Integer.MAX_VALUE;
        Run run = new Run(RunConfig.classic(seed), RunSetup.CLASSIC, table);
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, seed);
        for (int t = 0; t < ticks; t++) {
            if (run.isFinished()) {
                run = new Run(RunConfig.classic(seed + t), RunSetup.CLASSIC, table);
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
