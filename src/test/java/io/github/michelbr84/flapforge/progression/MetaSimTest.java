package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.MetaSim;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The E25 thresholds of the meta-progression, played out by {@link MetaSim} through the real
 * progression stack — {@link ProgressionManager}, {@link UnlockManager}, {@link UpgradeManager}
 * — on the shipped {@code economy.json}. Every number a threshold needs is the mean over the
 * seed lines of the run index at which the profile first owned the id (or first bought the
 * node), which is what {@code BalancingSim --meta} prints and {@code docs/BALANCING.md} §13
 * records.
 *
 * <p>The four gates, from the plan (E25, §6 M9):
 * <ul>
 *   <li><b>spender-average</b> owns every non-cosmetic unlockable within 200 runs and maxes
 *       every node and ability level within 600 while playing {@code tier:hard} once unlocked —
 *       E25's 600 supersedes the M9 table's 400;</li>
 *   <li><b>saver-average</b> reaches world 2 (wind_valley) within 10 runs;</li>
 *   <li><b>saver-novice</b> reaches it within 15;</li>
 *   <li><b>spender-novice</b> replays the README's opening hour (E17): the first upgrade node by
 *       run 3, Ironbeak by run 3, the shield by run 5, {@code feature:modifiers} by run 7;</li>
 *   <li>and the M6 criterion: of the runs that reach the third modifier offer, at least 20 %
 *       activate a synergy.</li>
 * </ul>
 *
 * <p>The policies are fixed (E25) and the bot is never weakened to pass: the skill presets are
 * the shipped {@link BotPilot.Preset}s. When a threshold fails, the lever is a price or a reward
 * in {@code data/*.json}, verified against the determinism hash afterwards.
 */
@Tag("sim")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetaSimTest {

    /** Seed lines per simulation — E25 asks for at least 20. */
    private static final int SEEDS = 20;
    /** The run budget that only stops a runaway: the spender finishes in about 25. */
    private static final int SPENDER_MAX_RUNS = 250;
    /** The saver never maxes anything, so its budget bounds the line directly. */
    private static final int SAVER_MAX_RUNS = 60;
    /** The opening-hour journey (E17) is decided inside the first seven runs. */
    private static final int JOURNEY_RUNS = 8;

    private final GameContent content = GameContent.load();

    @Test
    void spenderAverageOwnsEveryUnlockableWithin200RunsAndMaxesTheTreesWithin600() {
        MetaSim.Outcome outcome = run(MetaSim.Policy.SPENDER, BotPilot.Preset.AVERAGE,
                SPENDER_MAX_RUNS);
        List<String> report = List.of(
                "spender-average over " + SEEDS + " seeds: everything owned at mean run "
                        + outcome.meanCompletedRun() + ", maxed at mean run "
                        + outcome.meanMaxedRun() + ", synergies " + synergyRate(outcome));
        for (String id : outcome.unlockIds()) {
            assertEquals(SEEDS, outcome.seedsOwning(id),
                    () -> id + " was never owned by some seed line\n" + report);
            assertTrue(outcome.meanFirstOwned(id) <= 200,
                    () -> id + " was first owned only at mean run " + outcome.meanFirstOwned(id)
                            + " (worst " + outcome.maxFirstOwned(id) + ")\n" + report);
        }
        assertEquals(SEEDS, outcome.seedsMaxed(),
                () -> "some seed line never maxed every node and ability level\n" + report);
        assertTrue(outcome.meanMaxedRun() <= 600,
                () -> "the trees were maxed only at mean run " + outcome.meanMaxedRun()
                        + "; E25 allows 600 playing tier:hard\n" + report);
    }

    @Test
    void spenderAverageActivatesASynergyInAtLeastOneFifthOfTheRunsReachingOfferThree() {
        MetaSim.Outcome outcome = run(MetaSim.Policy.SPENDER, BotPilot.Preset.AVERAGE,
                SPENDER_MAX_RUNS);
        assertTrue(outcome.runsReachingOffer3() > 0,
                () -> "the average spender never reached the third modifier offer; the M6"
                        + " criterion is unmeasurable\n" + synergyRate(outcome));
        assertTrue(synergyRate(outcome) >= 20.0,
                () -> "only " + synergyRate(outcome) + "% of the runs that reached offer 3"
                        + " activated a synergy (" + outcome.runsReachingOffer3WithSynergy()
                        + " of " + outcome.runsReachingOffer3() + "); the M6 criterion is 20%");
    }

    @Test
    void saverAverageReachesWorldTwoWithinTenRuns() {
        MetaSim.Outcome outcome = run(MetaSim.Policy.SAVER, BotPilot.Preset.AVERAGE,
                SAVER_MAX_RUNS);
        assertReachedWithin(outcome, "world:wind_valley", 10);
    }

    @Test
    void saverNoviceReachesWorldTwoWithinFifteenRuns() {
        MetaSim.Outcome outcome = run(MetaSim.Policy.SAVER, BotPilot.Preset.NOVICE,
                SAVER_MAX_RUNS);
        assertReachedWithin(outcome, "world:wind_valley", 15);
    }

    @Test
    void spenderNoviceReplaysTheReadmeOpeningHour() {
        MetaSim.Outcome outcome = run(MetaSim.Policy.SPENDER, BotPilot.Preset.NOVICE,
                JOURNEY_RUNS);
        assertBoughtWithin(outcome, "feather_1", 3);
        assertReachedWithin(outcome, "bird:guardian", 3);
        assertReachedWithin(outcome, "ability:shield", 5);
        assertReachedWithin(outcome, "feature:modifiers", 7);
    }

    private void assertReachedWithin(MetaSim.Outcome outcome, String id, int runs) {
        assertEquals(SEEDS, outcome.seedsOwning(id),
                () -> id + " was never owned by some seed line");
        assertTrue(outcome.meanFirstOwned(id) <= runs,
                () -> id + " was first owned only at mean run " + outcome.meanFirstOwned(id)
                        + " (worst " + outcome.maxFirstOwned(id) + "); the threshold is "
                        + runs);
    }

    private void assertBoughtWithin(MetaSim.Outcome outcome, String nodeId, int runs) {
        assertEquals(SEEDS, outcome.seedsBuying(nodeId),
                () -> nodeId + " was never bought by some seed line");
        assertTrue(outcome.meanFirstBuy(nodeId) <= runs,
                () -> nodeId + " was first bought only at mean run " + outcome.meanFirstBuy(nodeId)
                        + " (worst " + outcome.maxFirstBuy(nodeId) + "); the threshold is "
                        + runs);
    }

    private MetaSim.Outcome run(MetaSim.Policy policy, BotPilot.Preset preset, int maxRuns) {
        return MetaSim.simulate(content, new MetaSim.Settings(policy, preset, SEEDS, 1,
                maxRuns, MetaSim.MAX_TICKS));
    }

    private static double synergyRate(MetaSim.Outcome outcome) {
        return outcome.runsReachingOffer3() == 0 ? 0
                : 100.0 * outcome.runsReachingOffer3WithSynergy() / outcome.runsReachingOffer3();
    }
}
