package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.run.StreakTracker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The clean-gate streak (D26, E26): what breaks it, what pays a step, and the wiring. */
class StreakTrackerTest {

    private static final int STEP = 5;

    @Test
    void cleanGatesIncrementTheStreakAndTrackTheBest() {
        StreakTracker tracker = new StreakTracker(STEP);
        for (int i = 1; i <= 7; i++) {
            assertTrue(tracker.onGatePassed(true), "a clean gate always changes the streak");
            assertEquals(i, tracker.streak());
            assertEquals(i, tracker.best());
        }
        tracker.onGatePassed(false);
        assertEquals(0, tracker.streak());
        assertEquals(7, tracker.best(), "the best survives the reset");
        tracker.onGatePassed(true);
        assertEquals(1, tracker.streak());
        assertEquals(7, tracker.best());
    }

    @Test
    void aDirtyColumnResetsTheStreak() {
        StreakTracker tracker = new StreakTracker(STEP);
        tracker.onGatePassed(true);
        tracker.onGatePassed(true);
        assertEquals(2, tracker.streak());
        assertTrue(tracker.onGatePassed(false), "the reset is a change");
        assertEquals(0, tracker.streak());
        assertFalse(tracker.onGatePassed(false), "0 → 0 is not a change");
    }

    @Test
    void aShieldAbsorbDirtiesTheGateBeingFlown() {
        StreakTracker tracker = new StreakTracker(STEP);
        tracker.onGatePassed(true);
        tracker.markShieldAbsorb();
        assertTrue(tracker.isWindowDirty());
        tracker.onGatePassed(true);
        assertEquals(0, tracker.streak(), "a clean column is not enough after an absorb");
        assertFalse(tracker.isWindowDirty(), "the window is cleared by the gate");
        tracker.onGatePassed(true);
        assertEquals(1, tracker.streak(), "only the gate it happened on is punished");
    }

    @Test
    void aReviveDirtiesTheGateBeingFlown() {
        StreakTracker tracker = new StreakTracker(STEP);
        tracker.onGatePassed(true);
        tracker.markRevive();
        tracker.onGatePassed(true);
        assertEquals(0, tracker.streak());
    }

    @Test
    void everyMultipleOfTheStepPaysOne() {
        StreakTracker tracker = new StreakTracker(STEP);
        for (int i = 1; i <= 17; i++) {
            tracker.onGatePassed(true);
            assertEquals(i / STEP, tracker.steps(), "after " + i + " clean gates");
        }
        assertEquals(3, tracker.steps());
    }

    @Test
    void aResetCostsTheProgressTowardsTheNextStepButNotTheStepsPaid() {
        StreakTracker tracker = new StreakTracker(STEP);
        for (int i = 0; i < 9; i++) {
            tracker.onGatePassed(true);
        }
        assertEquals(1, tracker.steps());
        tracker.onGatePassed(false);
        for (int i = 0; i < 4; i++) {
            tracker.onGatePassed(true);
        }
        assertEquals(1, tracker.steps(), "the four clean gates after the reset pay nothing yet");
        tracker.onGatePassed(true);
        assertEquals(2, tracker.steps());
        assertEquals(9, tracker.best());
    }

    @Test
    void aStepOfZeroDisablesTheStepsButKeepsTheStreak() {
        StreakTracker tracker = new StreakTracker(0);
        for (int i = 0; i < 20; i++) {
            tracker.onGatePassed(true);
        }
        assertEquals(20, tracker.streak());
        assertEquals(0, tracker.steps());
    }

    @Test
    void theShippedEconomyDrivesTheRunTracker() {
        assertEquals(StreakTracker.DEFAULT_STEP, RunSetup.CLASSIC.streakStep());
        Run run = Run.classic(RunConfig.classic(1));
        assertEquals(StreakTracker.DEFAULT_STEP, run.simulation().streaks().step());
    }

    /**
     * The wiring, end to end: a gate the bird grazed is passed dirty, so it resets the streak and
     * the {@code StreakChanged} fact reports the new value; the next, untouched gate starts a new
     * streak. The bird is pinned at a fixed height every tick so the graze is exact: with
     * {@code top = y − 15} the bird box ({@code y − 12 … y + 19}) clears the upper segment by 3 px
     * and the box inflated by {@link Playfield#NEAR_MISS_INFLATE_PX} does not.
     *
     * <p>The streak lands after the score, not with it (D26): the column is only judged once it
     * has left the inflated box, so {@code StreakChanged} follows {@code GatePassed} by three
     * ticks at the classic scroll.
     */
    @Test
    void aNearMissMakesTheGateDirtyAndResetsTheStreakOfARealRun() {
        double y = 320;
        Simulation sim = new Simulation(RunConfig.classic(4), RunSetup.CLASSIC);
        sim.spawner().setSuppressed(true);
        PipeGate grazed = PipeGate.standard(200, y + 0.5 - 15, Playfield.GAP, null);
        PipeGate clean = PipeGate.standard(600, y + 0.5 - Playfield.GAP / 2.0,
                Playfield.GAP, null);
        sim.obstacles().add(grazed);
        sim.obstacles().add(clean);

        List<Integer> streakFacts = new ArrayList<>();
        List<Boolean> gates = new ArrayList<>();
        List<Integer> scoreTicks = new ArrayList<>();
        List<Integer> streakTicks = new ArrayList<>();
        Bird bird = sim.bird();
        for (int i = 0; i < 400; i++) {
            bird.setY(y);
            bird.setVy(0);
            TickReport report = sim.tick(SimInput.NONE);
            for (TickFact fact : report.facts()) {
                if (fact instanceof TickFact.GatePassed passed) {
                    gates.add(passed.clean());
                    scoreTicks.add(report.tick());
                } else if (fact instanceof TickFact.StreakChanged changed) {
                    streakFacts.add(changed.streak());
                    streakTicks.add(report.tick());
                }
            }
        }

        assertTrue(sim.nearMisses() >= 1, "the first gate must be grazed");
        assertTrue(grazed.isDirty());
        assertFalse(clean.isDirty());
        assertEquals(List.of(false, true), gates, "gate 1 dirty, gate 2 clean");
        // The dirty gate resolves 0 → 0, which is not a change and emits no fact; the clean one
        // that follows is the only StreakChanged of the run.
        assertEquals(List.of(1), streakFacts);
        assertEquals(1, streakTicks.size());
        assertTrue(streakTicks.get(0) > scoreTicks.get(1),
                "the streak is resolved after the score line, not on it");
        assertEquals(1, sim.streaks().streak());
        assertEquals(1, sim.streaks().best());
        assertEquals(0, sim.streaks().steps());
    }

    /**
     * The case the fixed-height wiring test cannot reach: the graze happens strictly <em>after</em>
     * the gate scored, inside the 6 px the inflated hitbox keeps open past the score line. That
     * graze used to be free — the streak had already counted the gate as clean — which is how a
     * bot that scraped every pipe kept a perfect streak.
     */
    @Test
    void aGrazeAfterTheScoreLineStillCostsTheGate() {
        double y = 320;
        Simulation sim = new Simulation(RunConfig.classic(4), RunSetup.CLASSIC);
        sim.spawner().setSuppressed(true);
        // Wide enough that the bird flies through the middle untouched...
        PipeGate gate = PipeGate.standard(300, y + 0.5 - Playfield.GAP / 2.0, Playfield.GAP, null);
        sim.obstacles().add(gate);

        Bird bird = sim.bird();
        int scoredAt = 0;
        int dirtiedAt = 0;
        Integer streakAfter = null;
        for (int i = 0; i < 300 && streakAfter == null; i++) {
            // ... and only drops onto the pipe lip once the column is behind the score line.
            bird.setY(gate.isScored() ? y + 0.5 + Playfield.GAP / 2.0 - 20 : y);
            bird.setVy(0);
            TickReport report = sim.tick(SimInput.NONE);
            for (TickFact fact : report.facts()) {
                if (fact instanceof TickFact.GatePassed passed) {
                    assertTrue(passed.clean(), "the gate was clean when it crossed the score line");
                    scoredAt = report.tick();
                } else if (fact instanceof TickFact.StreakChanged changed) {
                    streakAfter = changed.streak();
                }
            }
            if (gate.isDirty() && dirtiedAt == 0) {
                dirtiedAt = report.tick();
            }
        }

        assertTrue(scoredAt > 0, "the gate must be scored");
        assertTrue(dirtiedAt > scoredAt, "the graze must land after the score: " + dirtiedAt
                + " vs " + scoredAt);
        assertEquals(0, sim.streaks().streak(), "a graze in the trailing window costs the gate");
        assertEquals(0, sim.streaks().best());
        assertNull(streakAfter, "0 → 0 is not a change, so no fact is emitted");
    }

    @Test
    void aCleanRunFeedsTheRunStatsThroughTheFacts() {
        double y = 320;
        Simulation sim = new Simulation(RunConfig.classic(4), RunSetup.CLASSIC);
        sim.spawner().setSuppressed(true);
        for (int i = 0; i < 6; i++) {
            sim.obstacles().add(PipeGate.standard(200 + i * 160.0,
                    y + 0.5 - Playfield.GAP / 2.0, Playfield.GAP, null));
        }
        Bird bird = sim.bird();
        int changes = 0;
        for (int i = 0; i < 800; i++) {
            bird.setY(y);
            bird.setVy(0);
            changes += sim.tick(SimInput.NONE).count(TickFact.StreakChanged.class);
        }
        assertEquals(6, sim.gatesPassed());
        assertEquals(6, changes, "one StreakChanged per gate");
        assertEquals(6, sim.streaks().streak());
        assertEquals(1, sim.streaks().steps(), "one step at five clean gates");
    }
}
