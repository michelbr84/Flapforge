package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterminismTest {

    private static final int TICKS = 3000;

    private static HeadlessRunner.Outcome perfectRun(long seed) {
        Run run = Run.classic(RunConfig.classic(seed));
        return HeadlessRunner.run(run, new BotPilot(BotPilot.Preset.PERFECT, seed), TICKS, true);
    }

    @Test
    void sameSeedProducesIdenticalPerTickHashes() {
        HeadlessRunner.Outcome a = perfectRun(42);
        HeadlessRunner.Outcome b = perfectRun(42);
        assertEquals(TICKS, a.ticks(), "the perfect bot must survive the whole budget");
        assertEquals(TICKS, b.ticks());
        assertTrue(a.result().gatesPassed() >= 20, "gates " + a.result().gatesPassed());
        assertEquals(a.hashes(), b.hashes());
        assertEquals(a.result().gatesPassed(), b.result().gatesPassed());
        assertEquals(a.result().counters(), b.result().counters());
    }

    @Test
    void differentSeedsDiverge() {
        HeadlessRunner.Outcome a = perfectRun(42);
        HeadlessRunner.Outcome b = perfectRun(43);
        assertNotEquals(a.hashes(), b.hashes());
        int firstDifference = -1;
        for (int i = 0; i < Math.min(a.hashes().size(), b.hashes().size()); i++) {
            if (!a.hashes().get(i).equals(b.hashes().get(i))) {
                firstDifference = i;
                break;
            }
        }
        assertTrue(firstDifference >= 0 && firstDifference < 5,
                "the first gate geometry differs already at spawn: " + firstDifference);
    }

    /**
     * E32.d: the sequence of spawn decisions depends on the seed and on nothing else — not on how
     * well the run is played. The two pilots die at different ticks, so the assertion is on the
     * common prefix of the per-spawn hashes; it must never be conditional, or a pilot that dies
     * one gate earlier turns the whole test into a no-op.
     */
    @Test
    void spawnDecisionsDependOnlyOnTheSeed() {
        List<Long> hashesA = spawnDecisions(11, BotPilot.Preset.PERFECT, 1);
        List<Long> hashesB = spawnDecisions(11, BotPilot.Preset.EXPERT, 2);
        List<Long> hashesC = spawnDecisions(11, BotPilot.Preset.NOVICE, 3);
        int ab = Math.min(hashesA.size(), hashesB.size());
        int ac = Math.min(hashesA.size(), hashesC.size());
        assertTrue(ab > 5, "the two runs must share more than five spawns, was " + ab);
        assertTrue(ac > 0, "the novice run must spawn at least one gate");
        assertEquals(hashesA.subList(0, ab), hashesB.subList(0, ab),
                "different pilots, same seed → same decisions");
        assertEquals(hashesA.subList(0, ac), hashesC.subList(0, ac),
                "a pilot that dies early still draws the same decisions while it lives");
    }

    private static List<Long> spawnDecisions(long seed, BotPilot.Preset preset, long pilotSeed) {
        Run run = Run.classic(RunConfig.classic(seed));
        HeadlessRunner.run(run, new BotPilot(preset, pilotSeed), 2000);
        return run.simulation().spawner().decisionHashes();
    }
}
