package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * D12 for the M7 families: a world that mixes all five kinds replays tick for tick, and its
 * spawn decisions depend on the seed alone (E32.d).
 */
class MixedKindDeterminismTest {

    private static final int TICKS = 3000;
    private static final Map<ObstacleKind, Integer> VOID_WEIGHTS = Map.of(
            ObstacleKind.PIPE_GATE, 40, ObstacleKind.GEAR, 20, ObstacleKind.PISTON, 20,
            ObstacleKind.WIND_ZONE, 10, ObstacleKind.LIGHTNING, 10);

    /** What one run produced: the per-tick hashes, the spawn decisions and the kinds spawned. */
    private record Trace(List<Long> hashes, List<Long> decisions, EnumMap<ObstacleKind, Integer> kinds,
            int ticks, int gates) {
    }

    private static Trace play(long seed, BotPilot.Preset preset, long pilotSeed) {
        Run run = new Run(RunConfig.classic(seed), RunSetup.CLASSIC, new SpawnTable(VOID_WEIGHTS));
        BotPilot bot = new BotPilot(preset, pilotSeed);
        List<Long> hashes = new ArrayList<>(TICKS);
        EnumMap<ObstacleKind, Integer> kinds = new EnumMap<>(ObstacleKind.class);
        int ticks = 0;
        while (!run.isFinished() && ticks < TICKS) {
            TickReport report = run.tick(bot.decide(run));
            for (TickFact f : report.facts()) {
                if (f instanceof TickFact.ObstacleSpawned spawned) {
                    kinds.merge(spawned.kind(), 1, Integer::sum);
                }
            }
            hashes.add(run.simulation().stateHash());
            ticks++;
        }
        return new Trace(hashes, run.simulation().spawner().decisionHashes(), kinds, ticks,
                run.stats().gatesPassed());
    }

    @Test
    void aFiveKindWorldReplaysTickForTick() {
        Trace a = play(42, BotPilot.Preset.PERFECT, 42);
        Trace b = play(42, BotPilot.Preset.PERFECT, 42);
        assertEquals(a.hashes(), b.hashes());
        assertEquals(a.decisions(), b.decisions());
        assertEquals(a.kinds(), b.kinds());
        assertEquals(TICKS, a.ticks(), "the perfect bot survives the mixed world: died after "
                + a.gates() + " gates");
        for (ObstacleKind kind : ObstacleKind.values()) {
            assertTrue(a.kinds().getOrDefault(kind, 0) > 0, kind + " never spawned: " + a.kinds());
        }
        assertTrue(a.gates() >= 20, "gates " + a.gates());
        Trace other = play(43, BotPilot.Preset.PERFECT, 43);
        assertNotEquals(a.hashes(), other.hashes());
    }

    @Test
    void theDecisionHashIsInvariantUnderThePilotsInputs() {
        Trace perfect = play(42, BotPilot.Preset.PERFECT, 1);
        Trace novice = play(42, BotPilot.Preset.NOVICE, 2);
        Trace idle = idle(42);
        int common = Math.min(perfect.decisions().size(), novice.decisions().size());
        assertTrue(common > 5, "the two runs must share more than five spawns, was " + common);
        assertEquals(perfect.decisions().subList(0, common), novice.decisions().subList(0, common),
                "different pilots, same seed → same decisions (E32.d)");
        int commonIdle = Math.min(perfect.decisions().size(), idle.decisions().size());
        assertTrue(commonIdle >= 1);
        assertEquals(perfect.decisions().subList(0, commonIdle),
                idle.decisions().subList(0, commonIdle), "a bird that never flaps draws the same");
        assertNotEquals(perfect.hashes(), novice.hashes(), "the runs themselves differ");
    }

    private static Trace idle(long seed) {
        Run run = new Run(RunConfig.classic(seed), RunSetup.CLASSIC, new SpawnTable(VOID_WEIGHTS));
        HeadlessRunner.run(run, r -> r.tick() == 0 ? RunInput.FLAP : RunInput.NONE, TICKS);
        return new Trace(List.of(), run.simulation().spawner().decisionHashes(),
                new EnumMap<>(ObstacleKind.class), 0, run.stats().gatesPassed());
    }
}
