package io.github.michelbr84.flapforge.modifier;

import static io.github.michelbr84.flapforge.modifier.ModifierTestData.WEIGHTS;
import static io.github.michelbr84.flapforge.modifier.ModifierTestData.card;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.support.BossRuns;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * E7 with a real {@code BossEncounter} behind the director's two seams: no breather while a
 * boss is pending or active, and a schedule gate that falls inside the warning or the fight
 * defers its offer to the first spawn interval after {@code BossCleared} — the clear air after
 * the last boss column, before the first ordinary one. The corridor is flat ({@link BossRuns}),
 * so the only thing that can move an offer is the boss.
 */
class BossOfferInterplayTest {

    private static final int WARNING = 90;
    private static final int SURVIVE = 400;

    private static ModifierCatalog catalog(int gate) {
        return new ModifierCatalog(List.of(gate), 3, WEIGHTS,
                List.of(card("alpha", Rarity.COMMON, 2, ModifierTag.SPEED),
                        card("beta", Rarity.COMMON, 2, ModifierTag.RISK),
                        card("gamma", Rarity.RARE, 2, ModifierTag.ECONOMY)),
                List.of());
    }

    private static Run run(int offerGate, int bossAtGate) {
        RunSetup setup = RunSetup.CLASSIC.withModifiers(catalog(offerGate))
                .withBoss(BossRuns.worldBoss(bossAtGate, WARNING, SURVIVE, BossRuns.twoPhases()));
        return BossRuns.run(RunConfig.builder(7).allowOffers(true).build(), setup);
    }

    /**
     * The order in which the run's milestones came, with the gate count of each, and how many
     * boss columns were still ahead of the bird on the tick of the clear.
     */
    private record Timeline(List<String> events, List<Integer> gates, List<RunPhase> phases,
            int columnsAheadAtClear) {
        int indexOf(String event) {
            return events.indexOf(event);
        }

        int gateAt(String event) {
            return gates.get(indexOf(event));
        }
    }

    private static Timeline play(Run run, int budget, String stopAfter) {
        List<String> events = new ArrayList<>();
        List<Integer> gates = new ArrayList<>();
        List<RunPhase> phases = new ArrayList<>();
        int ahead = -1;
        for (int t = 0; t < budget && !run.isFinished(); t++) {
            TickReport report = run.tick(BossRuns.fly(run));
            for (TickFact fact : report.facts()) {
                String name = null;
                if (fact instanceof TickFact.BossWarning) {
                    name = "warning";
                } else if (fact instanceof TickFact.BossStarted) {
                    name = "started";
                } else if (fact instanceof TickFact.BossCleared) {
                    name = "cleared";
                    ahead = 0;
                    double left = run.simulation().bird().hitbox().x();
                    for (Obstacle o : run.simulation().obstacles().obstacles()) {
                        if (o.scoreLineX() > left) {
                            ahead++;
                        }
                    }
                } else if (fact instanceof TickFact.ModifierOffered) {
                    name = "offered";
                } else if (fact instanceof TickFact.ModifierChosen) {
                    name = "chosen";
                }
                if (name != null) {
                    events.add(name);
                    gates.add(run.stats().gatesPassed());
                    phases.add(run.phase());
                }
            }
            if (events.contains(stopAfter)) {
                break;
            }
        }
        return new Timeline(events, gates, phases, ahead);
    }

    /** The offer opens once the boss columns are behind the bird and before any ordinary one. */
    private static void assertOfferInTheAirAfterTheBoss(Timeline timeline) {
        assertEquals(List.of("warning", "started", "cleared", "offered", "chosen"),
                timeline.events(), timeline.toString());
        assertTrue(timeline.columnsAheadAtClear() >= 0 && timeline.columnsAheadAtClear() <= 4,
                "a few boss columns are still ahead on the clear: " + timeline);
        assertTrue(timeline.gateAt("offered") <= timeline.gateAt("cleared")
                + timeline.columnsAheadAtClear(),
                "E7: the offer opens in the air after the last boss column: " + timeline);
        assertEquals(RunPhase.CHOOSING_MODIFIER,
                timeline.phases().get(timeline.indexOf("offered")));
    }

    /** Offer at gate 3, boss at gate 4: pending from gate 3, so the breather waits. */
    @Test
    void anOfferOnAPendingBossWaitsForTheClear() {
        Run run = run(3, 4);
        Timeline timeline = play(run, 6000, "chosen");
        assertOfferInTheAirAfterTheBoss(timeline);
        assertEquals(List.of("alpha"), run.stats().modifiersTaken());
        assertEquals(List.of(BossRuns.WORLD), run.stats().bossesCleared());
    }

    /** Offer at gate 6, boss at gate 3: the schedule gate is passed inside the fight. */
    @Test
    void aScheduleGateInsideTheFightDefersToAfterTheClear() {
        Run run = run(6, 3);
        Timeline timeline = play(run, 6000, "chosen");
        assertOfferInTheAirAfterTheBoss(timeline);
        assertTrue(timeline.gateAt("cleared") >= 6,
                "gate 6 was passed during the fight (the phases score): " + timeline);
        assertEquals(ModifierDirector.State.HOLD, run.simulation().modifiers().state(),
                "the card was just taken: the 3-2-1 is running");
    }

    /** Offer at gate 2, boss at gate 4: the breather is already waiting when the warning starts. */
    @Test
    void aBreatherAlreadyWaitingWhenTheWarningStartsIsHeldUntilTheClear() {
        Run run = run(2, 4);
        Timeline timeline = play(run, 6000, "chosen");
        assertOfferInTheAirAfterTheBoss(timeline);
    }

    /** Offer at gate 1, boss far away at gate 30: nothing holds the draft back. */
    @Test
    void aDraftWellBeforeTheBossOpensAsUsual() {
        Run run = run(1, 30);
        Timeline timeline = play(run, 3000, "chosen");
        assertEquals(List.of("offered", "chosen"), timeline.events(), timeline.toString());
        assertFalse(run.simulation().bossActive());
        assertFalse(run.simulation().bossPending());
        assertTrue(timeline.gateAt("offered") < 29, "long before E7's pending window");
    }
}
