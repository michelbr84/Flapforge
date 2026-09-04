package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.ObjectiveDef;
import io.github.michelbr84.flapforge.content.defs.ObjectiveType;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.PatternStreamer;
import io.github.michelbr84.flapforge.gameplay.run.BossEncounter;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.BossSpec;
import io.github.michelbr84.flapforge.gameplay.spec.ChallengeSpec;
import io.github.michelbr84.flapforge.gameplay.spec.PatternSpec;
import io.github.michelbr84.flapforge.support.BossRuns;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The boss encounter as a run sees it (D11, E7, E26, M8): a scripted pilot holds the centre of a
 * flat corridor and every timing the milestone promises is measured on it — the warning at
 * {@code atGate}, its length with spawning suppressed, the phases in order and looped, the clear
 * at {@code surviveTicks}, the air after it, the world clear recorded once, and the challenge
 * override that clears nothing.
 */
class BossEncounterTest {

    private static final int AT_GATE = 3;
    private static final int WARNING = 120;
    private static final int SURVIVE = 600;

    /** One tick's facts, with the phase and the gate count after it. */
    private record Step(int tick, TickReport report, RunPhase phase, int gates) {
    }

    /** Flies until the predicate holds or the budget runs out; returns every tick's step. */
    private static List<Step> flyUntil(Run run, int budget,
            java.util.function.Predicate<Step> until) {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < budget; i++) {
            TickReport report = run.tick(BossRuns.fly(run));
            Step step = new Step(run.tick(), report, run.phase(), run.stats().gatesPassed());
            steps.add(step);
            if (until.test(step) || run.isFinished()) {
                break;
            }
        }
        return steps;
    }

    private static Run worldBossRun(int atGate) {
        return BossRuns.run(RunConfig.builder(1).build(), RunSetup.CLASSIC
                .withBoss(BossRuns.worldBoss(atGate, WARNING, SURVIVE, BossRuns.twoPhases())));
    }

    private static <T extends TickFact> Step first(List<Step> steps, Class<T> fact) {
        for (Step s : steps) {
            if (s.report().has(fact)) {
                return s;
            }
        }
        return null;
    }

    @Test
    void theWarningStartsAtTheGateSuppressesSpawningAndLastsWarningTicks() {
        Run run = worldBossRun(AT_GATE);
        BossEncounter boss = run.simulation().boss();
        assertTrue(boss.hasBoss());
        assertEquals(BossEncounter.State.AHEAD, boss.state());
        assertFalse(run.simulation().bossPending(), "not pending at gate 0");

        List<Step> steps = flyUntil(run, 4000, s -> s.report().has(TickFact.BossWarning.class));
        Step warning = steps.get(steps.size() - 1);
        assertTrue(warning.report().has(TickFact.BossWarning.class), "the warning never came");
        TickFact.BossWarning fact = warning.report().first(TickFact.BossWarning.class).get();
        assertEquals(BossRuns.WORLD, fact.bossId());
        assertEquals(BossRuns.WORLD, fact.worldId());
        assertEquals(WARNING, fact.warningTicks());
        assertEquals(AT_GATE, warning.gates(), "the warning starts on the tick atGate is passed");
        assertTrue(warning.report().has(TickFact.GatePassed.class));
        assertEquals(RunPhase.BOSS_WARNING, run.phase());
        assertTrue(run.simulation().spawner().isSuppressed());
        assertTrue(run.simulation().bossActive());
        assertFalse(run.simulation().bossPending(), "active, not pending");
        assertEquals(WARNING, boss.ticksRemaining());

        // E7's pending window: the tick before the warning, gates >= atGate - 1 held.
        Step before = steps.get(steps.size() - 2);
        assertEquals(AT_GATE - 1, before.gates());

        List<Step> countdown = flyUntil(run, WARNING + 5,
                s -> s.report().has(TickFact.BossStarted.class));
        Step started = countdown.get(countdown.size() - 1);
        assertTrue(started.report().has(TickFact.BossStarted.class));
        assertEquals(WARNING, countdown.size(), "the fight starts exactly warningTicks later");
        for (int i = 0; i < countdown.size() - 1; i++) {
            assertFalse(countdown.get(i).report().has(TickFact.ObstacleSpawned.class),
                    "nothing spawns during the warning");
            assertEquals(RunPhase.BOSS_WARNING, countdown.get(i).phase());
        }
        assertEquals(SURVIVE, started.report().first(TickFact.BossStarted.class).get()
                .surviveTicks());
        assertEquals(RunPhase.BOSS, run.phase());
        assertFalse(run.simulation().spawner().isSuppressed());
        assertEquals(SURVIVE, boss.ticksRemaining());
    }

    @Test
    void thePhasesStreamInOrderLoopAndAreCountedAndTheFirstColumnIsFlooredAtTheRightEdge() {
        Run run = worldBossRun(AT_GATE);
        flyUntil(run, 4000, s -> s.report().has(TickFact.BossStarted.class));
        PatternStreamer streamer = run.simulation().spawner().streamer();
        assertNotNull(streamer);
        assertTrue(streamer.isBossActive());

        // The first boss column was placed on the tick the fight started.
        List<String> placed = new ArrayList<>();
        List<Double> spawnX = new ArrayList<>();
        PatternStreamer.Placement opening = run.simulation().spawner().lastPlacement();
        assertNotNull(opening, "the fight's first column is placed on the tick it starts");
        placed.add(opening.pattern().id());
        spawnX.add(run.simulation().obstacles().last().x());
        for (int t = 0; t < SURVIVE + 5 && !run.simulation().boss().isCleared(); t++) {
            TickReport report = run.tick(BossRuns.fly(run));
            if (report.has(TickFact.ObstacleSpawned.class)) {
                PatternStreamer.Placement placement = run.simulation().spawner().lastPlacement();
                placed.add(placement == null ? "table" : placement.pattern().id());
                spawnX.add(run.simulation().obstacles().last().x());
            }
        }
        assertTrue(run.simulation().boss().isCleared(), "the fight was survived");
        assertTrue(placed.size() >= 9, "at least two phases and a loop: " + placed);
        assertTrue(spawnX.get(0) >= Playfield.WIDTH,
                "the first boss column is floored at the right edge, was " + spawnX.get(0));
        for (int i = 0; i < placed.size(); i++) {
            // Four steps of flat_p1, four of flat_p2, then flat_p1 again (D11: looped).
            String expected = (i / 4) % 2 == 0 ? "flat_p1" : "flat_p2";
            assertEquals(expected, placed.get(i), "column " + i + " of " + placed);
        }
        assertEquals(2, run.stats().phasesReached(), "both phases were reached");
        assertEquals(2, run.simulation().boss().phasesReached());
    }

    @Test
    void theClearComesAtSurviveTicksResumesSpawningAfterOneAndAHalfIntervalsAndIsRecordedOnce() {
        Run run = worldBossRun(AT_GATE);
        List<Step> toStart = flyUntil(run, 4000, s -> s.report().has(TickFact.BossStarted.class));
        int startTick = toStart.get(toStart.size() - 1).tick();
        List<Step> fight = flyUntil(run, SURVIVE + 5,
                s -> s.report().has(TickFact.BossCleared.class));
        Step cleared = fight.get(fight.size() - 1);
        assertTrue(cleared.report().has(TickFact.BossCleared.class), "never cleared");
        assertEquals(startTick + SURVIVE, cleared.tick(), "cleared exactly surviveTicks later");
        TickFact.BossCleared fact = cleared.report().first(TickFact.BossCleared.class).get();
        assertEquals(BossRuns.WORLD, fact.worldId());
        assertEquals(List.of(BossRuns.WORLD), run.stats().bossesCleared());
        assertEquals(RunPhase.FLYING, run.phase(), "the run goes back to flying");
        assertTrue(run.simulation().boss().isCleared());
        assertFalse(run.simulation().bossActive());
        assertFalse(run.simulation().bossPending(), "a cleared boss is no longer ahead");
        assertFalse(run.simulation().spawner().streamer().isBossActive());
        assertEquals(BossEncounter.RESUME_INTERVALS,
                run.simulation().spawner().deferredIntervals(), 0.0,
                "the next ordinary spawn is pushed out 1.5 intervals (D11)");

        // The next column is a table draw, 1.5 intervals further out than the cursor's usual
        // interval from the last boss column.
        Obstacle lastBoss = run.simulation().obstacles().last();
        List<Step> after = flyUntil(run, 2000, s -> s.report().has(TickFact.ObstacleSpawned.class));
        assertTrue(after.get(after.size() - 1).report().has(TickFact.ObstacleSpawned.class));
        assertNull(run.simulation().spawner().lastPlacement(), "not a boss step any more");
        Obstacle next = run.simulation().obstacles().last();
        assertEquals(lastBoss.x() + Playfield.GATE_INTERVAL * (1 + BossEncounter.RESUME_INTERVALS),
                next.x(), 1e-9);

        // The clear is recorded once, and stays when the bird dies afterwards (D11).
        flyUntil(run, 2000, s -> s.gates() >= AT_GATE + 12);
        assertEquals(List.of(BossRuns.WORLD), run.stats().bossesCleared());
        while (!run.isFinished()) {
            run.tick(RunInput.NONE);
        }
        assertEquals(List.of(BossRuns.WORLD), run.result().stats().bossesCleared(),
                "granted at run end even though the bird crashed later");
        assertEquals(2, run.result().stats().phasesReached());
    }

    /** D11: {@code phasesReached} is the furthest phase, so a wrap back to phase 1 keeps it. */
    @Test
    void phasesReachedStaysAtTheFurthestPhaseWhenTheFightLoopsPastTheLastPhase() {
        PatternSpec[] phases = {BossRuns.flatPhase("flat_p1", 3), BossRuns.flatPhase("flat_p2", 3),
            BossRuns.flatPhase("flat_p3", 3)};
        Run run = BossRuns.run(RunConfig.builder(5).build(), RunSetup.CLASSIC
                .withBoss(BossRuns.worldBoss(AT_GATE, WARNING, 900, phases)));
        flyUntil(run, 4000, s -> s.report().has(TickFact.BossStarted.class));
        List<String> placed = new ArrayList<>();
        PatternStreamer.Placement opening = run.simulation().spawner().lastPlacement();
        assertNotNull(opening, "the fight's first column is placed on the tick it starts");
        placed.add(opening.pattern().id());
        while (!run.simulation().boss().isCleared() && placed.size() < 40) {
            TickReport report = run.tick(BossRuns.fly(run));
            if (report.has(TickFact.ObstacleSpawned.class)) {
                PatternStreamer.Placement placement = run.simulation().spawner().lastPlacement();
                placed.add(placement == null ? "table" : placement.pattern().id());
            }
        }
        assertTrue(run.simulation().boss().isCleared(), "the fight was survived");
        assertTrue(placed.size() > 9, "the fight looped past the third phase: " + placed);
        assertEquals("flat_p3", placed.get(8), "the third phase was streamed");
        assertEquals("flat_p1", placed.get(9), "phase 1 again after the wrap");
        assertEquals(3, run.simulation().boss().phasesReached(),
                "the wrap back to phase 1 must not lower phasesReached");
        assertEquals(3, run.stats().phasesReached());
        while (!run.isFinished()) {
            run.tick(RunInput.NONE);
        }
        assertEquals(3, run.result().stats().phasesReached(), "kept in the result");
    }

    @Test
    void dyingDuringTheFightClearsNothing() {
        Run run = worldBossRun(AT_GATE);
        flyUntil(run, 4000, s -> s.report().has(TickFact.BossStarted.class));
        for (int i = 0; i < 60; i++) {
            run.tick(BossRuns.fly(run));
        }
        assertEquals(RunPhase.BOSS, run.phase());
        while (!run.isFinished()) {
            run.tick(RunInput.NONE);
        }
        assertEquals(List.of(), run.result().stats().bossesCleared());
        assertFalse(run.simulation().boss().isCleared());
        assertEquals(BossEncounter.State.ACTIVE, run.simulation().boss().state());
    }

    /** E26: a challenge's own boss uses the challenge's block and never clears a world. */
    @Test
    void aChallengeBossSetsTheObjectiveAndNeverClearsAWorld() {
        PatternSpec[] phases = BossRuns.twoPhases();
        RunConfig config = RunConfig.builder(2).mode(RunMode.CHALLENGE)
                .challengeId(BossRuns.CHALLENGE).build();
        RunSetup setup = RunSetup.CLASSIC
                .withBoss(BossRuns.challengeBoss(AT_GATE, WARNING, SURVIVE, phases))
                .withChallenge(new ChallengeSpec(BossRuns.CHALLENGE, List.of(),
                        new ObjectiveDef(ObjectiveType.BOSS_CLEARED, 1)));
        Run run = BossRuns.run(config, setup);
        assertNotNull(run.simulation().objective());
        List<Step> steps = flyUntil(run, 6000, s -> s.report().has(TickFact.BossCleared.class));
        Step cleared = steps.get(steps.size() - 1);
        assertTrue(cleared.report().has(TickFact.BossCleared.class));
        TickFact.BossCleared fact = cleared.report().first(TickFact.BossCleared.class).get();
        assertEquals(BossRuns.CHALLENGE, fact.bossId());
        assertNull(fact.worldId(), "a challenge boss carries no world");
        assertTrue(cleared.report().has(TickFact.ObjectiveMet.class),
                "BOSS_CLEARED is met on the tick the boss clears");
        assertEquals(List.of(), run.stats().bossesCleared(), "E26: no world cleared");
        assertTrue(run.stats().objectiveMet());
        assertTrue(first(steps, TickFact.BossWarning.class).report()
                .first(TickFact.BossWarning.class).get().worldId() == null);
        assertEquals(RunPhase.FLYING, run.phase(), "the run continues after the objective");
        int gates = run.stats().gatesPassed();
        flyUntil(run, 600, s -> s.gates() > gates + 2);
        assertTrue(run.stats().gatesPassed() > gates, "and keeps scoring");
        while (!run.isFinished()) {
            run.tick(RunInput.NONE);
        }
        assertEquals(List.of(), run.result().stats().bossesCleared());
        assertTrue(run.result().stats().objectiveMet());
    }

    @Test
    void theEncounterSeamsAnswerTheDirectorsQuestions() {
        Run run = worldBossRun(AT_GATE);
        Simulation sim = run.simulation();
        flyUntil(run, 4000, s -> s.gates() >= AT_GATE - 1);
        assertTrue(sim.bossPending(), "pending from atGate - 1 (E7)");
        assertFalse(sim.bossActive());
        flyUntil(run, 4000, s -> s.report().has(TickFact.BossWarning.class));
        assertFalse(sim.bossPending());
        assertTrue(sim.bossActive());
        flyUntil(run, 4000, s -> s.report().has(TickFact.BossCleared.class));
        assertFalse(sim.bossPending());
        assertFalse(sim.bossActive());
    }

    @Test
    void aRunWithoutABossHasAnInertEncounterAndAStreamerOnlyWhenItStreams() {
        Run run = Run.classic(RunConfig.classic(3));
        BossEncounter boss = run.simulation().boss();
        assertFalse(boss.hasBoss());
        assertNull(run.simulation().spawner().streamer(), "nothing to stream, no streamer");
        assertNull(run.simulation().objective());
        run.tick(RunInput.FLAP);
        for (int i = 0; i < 600 && !run.isFinished(); i++) {
            run.tick(BossRuns.fly(run));
        }
        assertEquals(BossEncounter.State.AHEAD, boss.state());
        assertFalse(boss.isPending(1000));
        assertEquals(0, boss.ticksRemaining());
    }

    @Test
    void aBossNeedsAStreamerCarryingItsPhases() {
        BossSpec spec = BossRuns.worldBoss(AT_GATE, WARNING, SURVIVE, BossRuns.twoPhases());
        Run plain = Run.classic(RunConfig.classic(4));
        assertThrows(IllegalStateException.class,
                () -> new BossEncounter(spec, plain.simulation().spawner()));
        assertThrows(IllegalArgumentException.class,
                () -> new BossSpec("x", null, 1, 0, List.of(), 1), "no phases");
    }

    @Test
    void theBossSwitchOfTheConfigurationPinsTheEncounterOff() {
        assertFalse(RunConfig.classic(1).bossEnabled(), "the pinned classic run has no boss");
        assertTrue(RunConfig.builder(1).build().bossEnabled(), "everything else has");
        assertFalse(RunConfig.classic(1).withSeed(2).bossEnabled(), "instant retry keeps it");
        assertTrue(RunConfig.builder(1).build().withSeed(2).bossEnabled());
    }
}
