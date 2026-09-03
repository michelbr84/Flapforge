package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.AchievementConditionDef;
import io.github.michelbr84.flapforge.content.defs.AchievementDef;
import io.github.michelbr84.flapforge.content.defs.CompareOp;
import io.github.michelbr84.flapforge.content.defs.CounterScope;
import io.github.michelbr84.flapforge.content.defs.RewardDef;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link AchievementEvaluator} (D13, E1, E5, M8): every scope resolves against the profile or
 * the finished run, every operator compares, a hidden achievement evaluates like any other, an
 * achievement is listed once, and {@code progressOf} feeds the Milestones tab without ever
 * overshooting its target. {@code RUN}-scoped conditions are never satisfied without a run —
 * that is what keeps them out of the purchase pass.
 */
class AchievementEvaluatorTest {

    private GameContent content;
    private AchievementEvaluator evaluator;
    private PlayerProfile profile;

    @BeforeEach
    void setUp() {
        content = GameContent.load();
        evaluator = AchievementEvaluator.of(content);
        profile = PlayerProfile.fresh(1_700_000_000_000L).normalize();
    }

    // ------------------------------------------------------------------ helpers

    private static AchievementDef def(String id, boolean hidden, AchievementConditionDef cond) {
        return new AchievementDef(id, hidden, cond, new RewardDef(10, List.of()));
    }

    private static AchievementDef def(String id, AchievementConditionDef cond) {
        return def(id, false, cond);
    }

    private static RunResult run(int gates, int streakBest, int coins) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(gates);
        stats.setPoints(gates * 10.0);
        stats.setStreak(streakBest);
        for (int i = 0; i < coins; i++) {
            stats.addCoinsCollected(1);
        }
        stats.setDeathCause(CollisionCause.OBSTACLE);
        Map<String, Long> counters = new LinkedHashMap<>();
        return new RunResult(RunConfig.classic(gates), stats, counters);
    }

    // ------------------------------------------------------------------ LIFETIME

    /**
     * The shipped {@code first_flight} (LIFETIME {@code totalRuns >= 1}) is not satisfied by a
     * fresh profile and is satisfied after the profile has one recorded run.
     */
    @Test
    void aLifetimeScalarReadsTheProfile() {
        AchievementDef def = evaluator.definition("first_flight");
        assertFalse(evaluator.isSatisfied(def, profile, null));

        profile.statistics.totalRuns = 1;
        assertTrue(evaluator.isSatisfied(def, profile, null));
        assertTrue(evaluator.evaluate(profile).contains("first_flight"));
    }

    /**
     * E5: {@code level}, {@code xp} and {@code prestigeCount} live on the profile root, not in
     * the statistics tree, and a LIFETIME condition reads them there.
     */
    @Test
    void aProfileRootScalarResolvesLikeE5Says() {
        profile.level = 7;
        profile.xp = 4500;
        assertTrue(evaluator.isSatisfied(def("level_7", new AchievementConditionDef("level",
                CounterScope.LIFETIME, CompareOp.GTE, 7)), profile, null));
        assertFalse(evaluator.isSatisfied(def("level_8", new AchievementConditionDef("level",
                CounterScope.LIFETIME, CompareOp.GTE, 8)), profile, null));
        assertEquals(7, evaluator.counter(new AchievementConditionDef("level",
                CounterScope.LIFETIME, CompareOp.GTE, 1), profile, null));
        assertEquals(4500, evaluator.counter(new AchievementConditionDef("xp",
                CounterScope.LIFETIME, CompareOp.GTE, 1), profile, null));
        assertEquals(0, evaluator.counter(new AchievementConditionDef("prestigeCount",
                CounterScope.LIFETIME, CompareOp.GTE, 1), profile, null));
    }

    /**
     * A map counter resolves one entry of its map: {@code bossClears.void} reads only the Void
     * entry, and {@code bestGatesByTier.hard} only the hard tier's best.
     */
    @Test
    void aMapCounterReadsOneEntryOfItsMap() {
        profile.statistics.bossClears.put("void", 1L);
        profile.statistics.bestGatesByTier.put("hard", 21L);

        assertTrue(evaluator.isSatisfied(evaluator.definition("boss_void"), profile, null));
        assertFalse(evaluator.isSatisfied(evaluator.definition("boss_green_fields"), profile,
                null));
        assertTrue(evaluator.isSatisfied(evaluator.definition("hard_10"), profile, null));
        assertFalse(evaluator.isSatisfied(evaluator.definition("nightmare_10"), profile, null));
    }

    // ------------------------------------------------------------------ RUN

    /**
     * A {@code RUN} condition is judged on the finished run only: with no run it is never
     * satisfied — which is what keeps the purchase pass from granting one — and with one it
     * reads the run's own tallies, not the lifetime bests.
     */
    @Test
    void aRunConditionNeedsARunAndReadsIt() {
        AchievementDef def = evaluator.definition("clean_10");
        assertFalse(evaluator.isSatisfied(def, profile, null),
                "no run, no grant, whatever the profile holds");

        profile.statistics.streakBest = 30;
        assertFalse(evaluator.isSatisfied(def, profile, null),
                "the lifetime best is not the run's number");
        assertFalse(evaluator.evaluate(profile).contains("clean_10"),
                "the purchase pass has no run to judge");
        assertTrue(evaluator.isSatisfied(def, profile, run(12, 30, 4)));
        assertTrue(evaluator.evaluate(profile, run(12, 30, 4)).contains("clean_10"));
        assertFalse(evaluator.isSatisfied(def, profile, run(12, 9, 4)));
    }

    /** Every documented run counter resolves from the finished run. */
    @Test
    void everyRunCounterResolves() {
        RunResult result = run(12, 8, 4);
        assertEquals(12, AchievementEvaluator.runCounter("run.gatesPassed", result.stats()));
        assertEquals(120, AchievementEvaluator.runCounter("run.points", result.stats()));
        assertEquals(8, AchievementEvaluator.runCounter("run.streakBest", result.stats()));
        assertEquals(4, AchievementEvaluator.runCounter("run.coinsCollected", result.stats()));
        assertEquals(0, AchievementEvaluator.runCounter("run.nonsense", result.stats()));
        assertEquals(0, AchievementEvaluator.runCounter("totalRuns", result.stats()));
        assertEquals(0, AchievementEvaluator.runCounter(null, result.stats()));
        assertEquals(0, AchievementEvaluator.runCounter("run.gatesPassed", null));
    }

    // ------------------------------------------------------------------ COLLECTION

    /**
     * A {@code COLLECTION} condition reads the same percentage the Collections tab shows: a
     * profile with every bird but one holds 6 of 7 birds, which floors to 85 and does not fire
     * the 100 achievement.
     */
    @Test
    void aCollectionConditionReadsTheSharedArithmetic() {
        AchievementDef def = evaluator.definition("collect_all_birds");
        assertFalse(evaluator.isSatisfied(def, profile, null));

        int birds = 0;
        for (int i = 0; i < content.birds().size() - 1; i++) {
            profile.unlock("bird:" + content.birds().ids().get(i));
            birds++;
        }
        assertEquals(85, evaluator.collections().percent("birds", profile),
                "6 of 7 birds floors to 85");
        assertFalse(evaluator.isSatisfied(def, profile, null));
        profile.unlock("bird:" + content.birds().ids().get(content.birds().size() - 1));
        assertTrue(evaluator.isSatisfied(def, profile, null));
        assertTrue(evaluator.evaluate(profile).contains("collect_all_birds"));
    }

    // ------------------------------------------------------------------ operators

    /** Every operator compares the way its name says, at and around the threshold. */
    @Test
    void everyOperatorCompares() {
        assertTrue(AchievementEvaluator.compare(CompareOp.GTE, 10, 10));
        assertFalse(AchievementEvaluator.compare(CompareOp.GT, 10, 10));
        assertTrue(AchievementEvaluator.compare(CompareOp.GT, 11, 10));
        assertTrue(AchievementEvaluator.compare(CompareOp.LTE, 10, 10));
        assertFalse(AchievementEvaluator.compare(CompareOp.LT, 10, 10));
        assertTrue(AchievementEvaluator.compare(CompareOp.LT, 9, 10));
        assertTrue(AchievementEvaluator.compare(CompareOp.EQ, 10, 10));
        assertFalse(AchievementEvaluator.compare(CompareOp.EQ, 11, 10));
        assertFalse(AchievementEvaluator.compare(CompareOp.EQ, 9, 10));

        AchievementConditionDef gte = new AchievementConditionDef("totalRuns",
                CounterScope.LIFETIME, CompareOp.GTE, 5);
        AchievementConditionDef lt = new AchievementConditionDef("totalRuns",
                CounterScope.LIFETIME, CompareOp.LT, 5);
        profile.statistics.totalRuns = 5;
        assertTrue(evaluator.isSatisfied(def("gte", gte), profile, null));
        assertFalse(evaluator.isSatisfied(def("lt", lt), profile, null));
    }

    // ------------------------------------------------------------------ hidden and once

    /**
     * A hidden achievement evaluates like any other — the flag withholds its display, not its
     * grant — and an achievement already held is never listed again.
     */
    @Test
    void hiddenEvaluatesAndHeldIsNeverListedTwice() {
        AchievementDef secret = def("secret", true, new AchievementConditionDef("totalRuns",
                CounterScope.LIFETIME, CompareOp.GTE, 1));
        profile.statistics.totalRuns = 1;

        assertTrue(evaluator.isSatisfied(secret, profile, null));
        assertTrue(evaluator.progressOf(secret, profile).isComplete());

        // Once: the evaluator skips ids the profile's record map already holds.
        profile.achievements.put("secret", new PlayerProfile.AchievementRecord(1L));
        assertTrue(evaluator.evaluate(profile, run(1, 0, 0)).isEmpty()
                || !evaluator.evaluate(profile, run(1, 0, 0)).contains("secret"));
        assertEquals(new AchievementEvaluator.Progress(1, 1).current(),
                evaluator.progressOf(secret, profile).current(),
                "a held achievement reports a full bar");
    }

    /**
     * The shipped content evaluates as a whole without failing, and its definitions come in file
     * order.
     */
    @Test
    void theShippedContentEvaluatesInFileOrder() {
        assertEquals(content.achievements().ids(), evaluator.definitions().stream()
                .map(AchievementDef::id).toList());
        assertTrue(evaluator.evaluate(profile).isEmpty(),
                "a fresh profile holds none of the 41");
        assertNull(evaluator.definition("no_such_achievement"));
        assertEquals(RewardDef.NONE, evaluator.rewardOf("no_such_achievement"));
        assertEquals(25, evaluator.rewardOf("first_flight").coins());
    }

    // ------------------------------------------------------------------ progressOf

    /**
     * {@code progressOf} clamps into {@code [0, target]}: a counter above the target reports the
     * target, a held achievement reports a full bar, and an untouched one reports 0.
     */
    @Test
    void progressOfClampsAndMirrors() {
        AchievementDef gates = evaluator.definition("gates_25");
        assertEquals(new AchievementEvaluator.Progress(0, 25), evaluator.progressOf(gates,
                profile));

        profile.statistics.bestGates = 40;
        assertEquals(new AchievementEvaluator.Progress(25, 25), evaluator.progressOf(gates,
                profile), "a counter over the target clamps to it");

        profile.achievements.put("gates_25", new PlayerProfile.AchievementRecord(1L));
        assertEquals(25, evaluator.progressOf(gates, profile).current(),
                "held reports the target");

        profile.statistics.bestGates = 12;
        profile.achievements.remove("gates_25");
        AchievementEvaluator.Progress partial = evaluator.progressOf(gates, profile);
        assertEquals(12, partial.current());
        assertEquals(25, partial.target());
        assertEquals(12 / 25.0, partial.fraction(), 1e-9);
        assertFalse(partial.isComplete());
    }

    /**
     * E1: a {@code RUN} achievement's progress bar reads the best matching lifetime statistic —
     * {@code run.streakBest} mirrors {@code streakBest} — and 0 over the target when the run
     * value has no lifetime best.
     */
    @Test
    void runProgressMirrorsTheLifetimeBest() {
        AchievementDef clean = evaluator.definition("clean_10");
        profile.statistics.streakBest = 7;
        assertEquals(new AchievementEvaluator.Progress(7, 10), evaluator.progressOf(clean,
                profile));

        assertEquals(StatisticKey.BEST_GATES.field(),
                AchievementEvaluator.lifetimeMirrorOf("run.gatesPassed"));
        assertEquals(StatisticKey.BEST_POINTS.field(),
                AchievementEvaluator.lifetimeMirrorOf("run.points"));
        assertEquals(StatisticKey.STREAK_BEST.field(),
                AchievementEvaluator.lifetimeMirrorOf("run.streakBest"));
        assertNull(AchievementEvaluator.lifetimeMirrorOf("run.coinsCollected"),
                "a coin total is not a per-run record");
        assertNull(AchievementEvaluator.lifetimeMirrorOf("totalRuns"));

        // The mirror is the profile's number, so a run in flight never moves the bar: the value
        // comes from the statistics, not from any RunResult.
        assertEquals(7, evaluator.progressOf(clean, profile).current());
    }

    /** A fresh profile's milestones are the low thresholds, in target order. */
    @Test
    void aFreshProfileStartsAtZero() {
        AchievementEvaluator.Progress first = evaluator.progressOf(
                evaluator.definition("first_flight"), profile);
        assertEquals(0, first.current());
        assertEquals(1, first.target());
        assertTrue(first.fraction() >= 0 && first.fraction() <= 1);
    }
}
