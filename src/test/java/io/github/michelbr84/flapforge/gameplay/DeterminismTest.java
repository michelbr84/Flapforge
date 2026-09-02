package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import java.util.List;
import java.util.Map;
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
        assertTrue(a.result().stats().coinsCollected() > 0, "the coin trails are part of the run");
        assertEquals(a.result().stats().coinsCollected(), b.result().stats().coinsCollected());
        assertTrue(a.result().stats().streakBest() > 0, "and so is the clean-gate streak");
        assertEquals(a.result().stats().streakBest(), b.result().stats().streakBest());
        assertEquals(a.result().stats().streakSteps(), b.result().stats().streakSteps());
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

    /**
     * M5: a run with abilities equipped replays tick for tick like any other. The loadout is
     * pushed through the content factory, so the ability layer, the timers and the shield charges
     * are all part of what {@code stateHash} folds.
     */
    @Test
    void anAbilityEquippedRunIsReproducible() {
        RunConfig config = RunConfig.builder(31)
                .activeAbilityId("dash")
                .passiveAbilityIds(List.of("shield", "coin_magnet"))
                .abilityLevels(Map.of("dash", 2, "shield", 3, "coin_magnet", 2))
                .build();
        RunFactory factory = new RunFactory(GameContent.load());
        HeadlessRunner.Outcome a = HeadlessRunner.run(factory.newRun(config),
                new BotPilot(BotPilot.Preset.AVERAGE, 31), TICKS, true);
        HeadlessRunner.Outcome b = HeadlessRunner.run(factory.newRun(config),
                new BotPilot(BotPilot.Preset.AVERAGE, 31), TICKS, true);
        assertEquals(a.hashes(), b.hashes());
        assertEquals(a.result().counters(), b.result().counters());
        assertEquals(a.result().stats().abilitiesUsed(), b.result().stats().abilitiesUsed());
        assertEquals(a.result().stats().shieldAbsorbs(), b.result().stats().shieldAbsorbs());
        assertTrue(a.result().stats().abilitiesUsed().getOrDefault("dash", 0) > 0,
                "the active must actually be spent, or its timers are never exercised");
        assertTrue(a.result().stats().shieldAbsorbs() > 0, "and the passive must be spent too");

        HeadlessRunner.Outcome bare = HeadlessRunner.run(
                factory.newRun(RunConfig.classic(31)),
                new BotPilot(BotPilot.Preset.AVERAGE, 31), TICKS, true);
        assertNotEquals(bare.hashes(), a.hashes(), "and it is not the same run as without them");
    }

    /**
     * D12: the per-tick ability counters are part of the state hash. Two runs that differ only in
     * whether the ability was pressed have identical birds, identical worlds and identical scores
     * — the score multiplier moves nothing — so the only thing that can tell them apart is the
     * cooldown, duration and activation count the fold has to include.
     */
    @Test
    void theAbilityTimersAreFoldedIntoTheStateHash() {
        RunConfig config = RunConfig.builder(21).activeAbilityId("score_multiplier").build();
        RunFactory factory = new RunFactory(GameContent.load());
        Run idle = started(factory.newRun(config));
        Run used = started(factory.newRun(config));
        assertEquals(idle.simulation().stateHash(), used.simulation().stateHash(),
                "same seed, same loadout, same tick");

        idle.tick(RunInput.NONE);
        used.tick(new RunInput(false, true, RunInput.NO_CHOICE, false));
        assertEquals(1, used.stats().abilitiesUsed().getOrDefault("score_multiplier", 0));
        assertEquals(idle.simulation().bird().y(), used.simulation().bird().y(), 0.0,
                "the score multiplier does not move the bird");
        assertNotEquals(idle.simulation().stateHash(), used.simulation().stateHash(),
                "a running ability is part of the state");
    }

    private static Run started(Run run) {
        run.tick(RunInput.FLAP);
        run.simulation().spawner().setSuppressed(true);
        run.simulation().obstacles().clear();
        return run;
    }

    /**
     * D12: the published {@code --headless-run} hash is the ability-free configuration, and the
     * ability systems must leave it exactly where M4 left it. This is the unit-level half of that
     * guarantee: an empty loadout with no shield charge folds nothing extra.
     */
    @Test
    void anAbilityFreeRunFoldsNoAbilityState() {
        Run run = Run.classic(RunConfig.classic(42));
        assertFalse(run.simulation().hasRunSystems());
        HeadlessRunner.run(run, new BotPilot(BotPilot.Preset.PERFECT, 42), 600, false);
        assertFalse(run.simulation().hasRunSystems(),
                "nothing in a classic run can grow an ability, a shield or a revive");
    }

    private static List<Long> spawnDecisions(long seed, BotPilot.Preset preset, long pilotSeed) {
        Run run = Run.classic(RunConfig.classic(seed));
        HeadlessRunner.run(run, new BotPilot(preset, pilotSeed), 2000);
        return run.simulation().spawner().decisionHashes();
    }
}
