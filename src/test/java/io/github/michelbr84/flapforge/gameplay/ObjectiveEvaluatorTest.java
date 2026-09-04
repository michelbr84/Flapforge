package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.ObjectiveDef;
import io.github.michelbr84.flapforge.content.defs.ObjectiveType;
import io.github.michelbr84.flapforge.gameplay.run.ObjectiveEvaluator;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.gameplay.spec.ChallengeSpec;
import io.github.michelbr84.flapforge.support.BossRuns;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D11, M8: the five objective types, met once, and the run that goes on afterwards. */
class ObjectiveEvaluatorTest {

    private static ObjectiveDef objective(ObjectiveType type, long value) {
        return new ObjectiveDef(type, value);
    }

    private static RunStats stats(int gates, double points, int coins) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(gates);
        stats.setPoints(points);
        stats.addCoinsCollected(coins);
        return stats;
    }

    @Test
    void eachTypeReadsItsOwnTally() {
        assertTrue(ObjectiveEvaluator.isMet(objective(ObjectiveType.SURVIVE_GATES, 30), 30, 0, 0,
                0, false));
        assertFalse(ObjectiveEvaluator.isMet(objective(ObjectiveType.SURVIVE_GATES, 30), 29, 99,
                99, 99, true));
        assertTrue(ObjectiveEvaluator.isMet(objective(ObjectiveType.SURVIVE_TICKS, 600), 0, 0, 0,
                600, false));
        assertFalse(ObjectiveEvaluator.isMet(objective(ObjectiveType.SURVIVE_TICKS, 600), 99, 99,
                99, 599, true));
        assertTrue(ObjectiveEvaluator.isMet(objective(ObjectiveType.COLLECT_COINS, 60), 0, 0, 60,
                0, false));
        assertFalse(ObjectiveEvaluator.isMet(objective(ObjectiveType.COLLECT_COINS, 60), 99, 99,
                59, 99, true));
        assertTrue(ObjectiveEvaluator.isMet(objective(ObjectiveType.REACH_POINTS, 40), 20, 40.0,
                0, 0, false));
        assertTrue(ObjectiveEvaluator.isMet(objective(ObjectiveType.REACH_POINTS, 40), 20, 40.5,
                0, 0, false), "points carry SCORE_MULT and may be fractional (E1)");
        assertFalse(ObjectiveEvaluator.isMet(objective(ObjectiveType.REACH_POINTS, 40), 99, 39.9,
                99, 99, true));
        assertTrue(ObjectiveEvaluator.isMet(objective(ObjectiveType.BOSS_CLEARED, 1), 0, 0, 0,
                0, true));
        assertFalse(ObjectiveEvaluator.isMet(objective(ObjectiveType.BOSS_CLEARED, 1), 99, 99,
                99, 99, false));
    }

    @Test
    void progressIsTheTallyCappedAtTheTarget() {
        ObjectiveEvaluator gates = new ObjectiveEvaluator("c", objective(
                ObjectiveType.SURVIVE_GATES, 30));
        assertEquals(12, gates.progress(12, 12, 0, 0, false));
        assertEquals(30, gates.progress(45, 45, 0, 0, false));
        assertEquals(30, gates.target());
        ObjectiveEvaluator ticks = new ObjectiveEvaluator("c", objective(
                ObjectiveType.SURVIVE_TICKS, 600));
        assertEquals(120, ticks.progress(0, 0, 0, 120, false));
        assertEquals(600, ticks.progress(0, 0, 0, 900, false), "capped at the target");
        assertEquals(600, ticks.target());
        ObjectiveEvaluator points = new ObjectiveEvaluator("c", objective(
                ObjectiveType.REACH_POINTS, 40));
        assertEquals(21, points.progress(14, 21.5, 0, 0, false), "floored");
        ObjectiveEvaluator coins = new ObjectiveEvaluator("c", objective(
                ObjectiveType.COLLECT_COINS, 60));
        assertEquals(7, coins.progress(0, 0, 7, 0, false));
        ObjectiveEvaluator boss = new ObjectiveEvaluator("c", objective(
                ObjectiveType.BOSS_CLEARED, 1));
        assertEquals(0, boss.progress(50, 50, 50, 50, false));
        assertEquals(1, boss.progress(0, 0, 0, 0, true));
    }

    @Test
    void theObjectiveIsMetOnceAndTheFactGoesOutOnce() {
        ObjectiveEvaluator evaluator = new ObjectiveEvaluator("no_shield_1",
                objective(ObjectiveType.SURVIVE_GATES, 3));
        List<TickFact> facts = new ArrayList<>();
        assertFalse(evaluator.tick(stats(2, 2, 0), false, facts));
        assertTrue(facts.isEmpty());
        assertFalse(evaluator.isMet());
        assertTrue(evaluator.tick(stats(3, 3, 0), false, facts), "met on the tick it holds");
        assertEquals(1, facts.size());
        assertEquals("no_shield_1", ((TickFact.ObjectiveMet) facts.get(0)).challengeId());
        assertTrue(evaluator.isMet());
        assertFalse(evaluator.tick(stats(4, 4, 0), false, facts), "never again");
        assertFalse(evaluator.tick(stats(0, 0, 0), false, facts),
                "and the latch holds even if the tally went down");
        assertEquals(1, facts.size());
        long met = evaluator.hashState(0);
        assertTrue(met != new ObjectiveEvaluator("no_shield_1",
                objective(ObjectiveType.SURVIVE_GATES, 3)).hashState(0),
                "the latch is part of the state hash");
    }

    /** The evaluator inside a run: the fact reaches the stats, and the run keeps flying. */
    @Test
    void aRunContinuesAfterItsObjectiveIsMet() {
        RunConfig config = RunConfig.builder(5).mode(RunMode.CHALLENGE).challengeId("flat_3")
                .build();
        RunSetup setup = RunSetup.CLASSIC.withChallenge(new ChallengeSpec("flat_3", List.of(),
                objective(ObjectiveType.SURVIVE_GATES, 3)));
        Run run = BossRuns.run(config, setup);
        int metAt = -1;
        int metFacts = 0;
        for (int t = 0; t < 2000 && run.stats().gatesPassed() < 8; t++) {
            TickReport report = run.tick(BossRuns.fly(run));
            if (report.has(TickFact.ObjectiveMet.class)) {
                metFacts++;
                metAt = run.stats().gatesPassed();
                assertTrue(run.stats().objectiveMet(), "the stats see it on the same tick");
            }
        }
        assertEquals(1, metFacts, "one ObjectiveMet fact");
        assertEquals(3, metAt);
        assertTrue(run.stats().gatesPassed() >= 8, "the run went on scoring");
        assertEquals(RunPhase.FLYING, run.phase());
        assertTrue(run.stats().objectiveMet());
        while (!run.isFinished()) {
            run.tick(RunInput.NONE);
        }
        assertTrue(run.result().stats().objectiveMet(), "kept in the result");
    }

    /** The tick objective is judged on the same counter the stats count (D11). */
    @Test
    void aTickObjectiveLatchesAtTheTargetTickAndTheRunGoesOn() {
        RunConfig config = RunConfig.builder(7).mode(RunMode.CHALLENGE).challengeId("ticks_1")
                .build();
        RunSetup setup = RunSetup.CLASSIC.withChallenge(new ChallengeSpec("ticks_1", List.of(),
                objective(ObjectiveType.SURVIVE_TICKS, 300)));
        Run run = BossRuns.run(config, setup);
        int metFacts = 0;
        int metAtTick = -1;
        while (!run.isFinished() && run.stats().ticksAlive() < 500) {
            TickReport report = run.tick(BossRuns.fly(run));
            if (report.has(TickFact.ObjectiveMet.class)) {
                metFacts++;
                metAtTick = run.stats().ticksAlive();
                assertEquals(300, metAtTick, "met on the tick the target is reached");
            }
        }
        assertEquals(1, metFacts, "one ObjectiveMet fact");
        assertTrue(run.stats().ticksAlive() > 300, "the run went on flying");
        assertTrue(run.stats().objectiveMet());
        assertEquals(RunPhase.FLYING, run.phase());
        while (!run.isFinished()) {
            run.tick(RunInput.NONE);
        }
        assertTrue(run.result().stats().objectiveMet(), "kept in the result");
    }

    @Test
    void aCoinObjectiveReadsTheCoinsTheRunPickedUp() {
        RunConfig config = RunConfig.builder(6).mode(RunMode.CHALLENGE).challengeId("coins_1")
                .build();
        RunSetup setup = RunSetup.CLASSIC.withChallenge(new ChallengeSpec("coins_1", List.of(),
                objective(ObjectiveType.COLLECT_COINS, 1)));
        Run run = BossRuns.run(config, setup);
        boolean met = false;
        for (int t = 0; t < 3000 && !met && !run.isFinished(); t++) {
            TickReport report = run.tick(BossRuns.fly(run));
            if (report.has(TickFact.ObjectiveMet.class)) {
                met = true;
                assertTrue(run.stats().coinsCollected() >= 1);
                assertTrue(report.has(TickFact.CoinCollected.class)
                        || run.stats().coinsCollected() >= 1);
            }
        }
        assertTrue(met, "the flat corridor's coin trail pays at least one coin: "
                + run.stats().coinsCollected());
    }
}
