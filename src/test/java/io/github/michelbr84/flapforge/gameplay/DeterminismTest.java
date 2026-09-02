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
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
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
        // Seed 40: one where the average bot spends both the dash and a shield charge. It was
        // seed 31 until M7 fixed the bot's corridor selection during a dash, after which that
        // run never needs its shield and the passive would go unexercised.
        RunConfig config = RunConfig.builder(40)
                .activeAbilityId("dash")
                .passiveAbilityIds(List.of("shield", "coin_magnet"))
                .abilityLevels(Map.of("dash", 2, "shield", 3, "coin_magnet", 2))
                .build();
        RunFactory factory = new RunFactory(GameContent.load());
        HeadlessRunner.Outcome a = HeadlessRunner.run(factory.newRun(config),
                new BotPilot(BotPilot.Preset.AVERAGE, 40), TICKS, true);
        HeadlessRunner.Outcome b = HeadlessRunner.run(factory.newRun(config),
                new BotPilot(BotPilot.Preset.AVERAGE, 40), TICKS, true);
        assertEquals(a.hashes(), b.hashes());
        assertEquals(a.result().counters(), b.result().counters());
        assertEquals(a.result().stats().abilitiesUsed(), b.result().stats().abilitiesUsed());
        assertEquals(a.result().stats().shieldAbsorbs(), b.result().stats().shieldAbsorbs());
        assertTrue(a.result().stats().abilitiesUsed().getOrDefault("dash", 0) > 0,
                "the active must actually be spent, or its timers are never exercised");
        assertTrue(a.result().stats().shieldAbsorbs() > 0, "and the passive must be spent too");

        HeadlessRunner.Outcome bare = HeadlessRunner.run(
                factory.newRun(RunConfig.classic(40)),
                new BotPilot(BotPilot.Preset.AVERAGE, 40), TICKS, true);
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

    /**
     * E32.d, the milestone's own case: two runs on the same seed where the player answers the
     * drafts differently draw the same spawn decisions. The two runs diverge in everything else —
     * a card that changes the scroll speed moves every obstacle and ends the run at a different
     * tick — so the assertion is on the common prefix of the per-spawn hashes, and the test first
     * proves the two runs really did take different cards, or it would be measuring nothing.
     *
     * <p>The mechanism is the named streams (D12): the draft draws from {@code offers} and the
     * spawner from {@code spawn}/{@code obstacle}, so however much the draft draws, the obstacle
     * sequence is a function of the seed alone.
     */
    @Test
    void theSpawnDecisionSequenceSurvivesADifferentChoice() {
        Draft first = draftRun(9, 0);
        Draft second = draftRun(9, 2);
        Draft skipping = draftRun(9, RunInput.SKIP);

        assertFalse(first.taken.isEmpty(), "the first run must reach a draft and take a card");
        assertNotEquals(first.taken, second.taken, "and the two runs must differ in what they took");
        assertEquals(List.of(), skipping.taken, "the third run skipped every draft");

        assertPrefixEquals(first.decisions, second.decisions);
        assertPrefixEquals(first.decisions, skipping.decisions);
    }

    private static void assertPrefixEquals(List<Long> a, List<Long> b) {
        int common = Math.min(a.size(), b.size());
        assertTrue(common > 12, "the runs must share more than twelve spawns, was " + common);
        assertEquals(a.subList(0, common), b.subList(0, common),
                "the same seed draws the same obstacles whatever the player picks (E32.d)");
    }

    /** What one drafted run produced: the cards it took and the spawns it drew. */
    private record Draft(List<String> taken, List<Long> decisions) {
    }

    /**
     * Plays a run with drafts on, answering every offer the same way.
     *
     * @param seed the run seed
     * @param choice the card index to take, or {@link RunInput#SKIP}
     * @return what the run took and drew
     */
    private static Draft draftRun(long seed, int choice) {
        RunConfig config = RunConfig.builder(seed).allowOffers(true).build();
        Run run = new RunFactory(GameContent.load()).newRun(config);
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, seed);
        for (int t = 0; t < TICKS && !run.isFinished(); t++) {
            RunInput input = run.phase() == RunPhase.CHOOSING_MODIFIER
                    ? new RunInput(false, false, choice, false) : bot.decide(run);
            run.tick(input);
        }
        return new Draft(List.copyOf(run.stats().modifiersTaken()),
                run.simulation().spawner().decisionHashes());
    }

    private static List<Long> spawnDecisions(long seed, BotPilot.Preset preset, long pilotSeed) {
        Run run = Run.classic(RunConfig.classic(seed));
        HeadlessRunner.run(run, new BotPilot(preset, pilotSeed), 2000);
        return run.simulation().spawner().decisionHashes();
    }

    /**
     * M7: the worlds with the new families, patterns, ambience and rule cycles replay tick for
     * tick — same seed twice → identical per-tick hashes, on every shipped world.
     */
    @Test
    void everyShippedWorldReplaysTickForTick() {
        RunFactory factory = new RunFactory(GameContent.load());
        for (String world : factory.content().worlds().ids()) {
            RunConfig config = RunConfig.builder(77).worldId(world).build();
            HeadlessRunner.Outcome a = HeadlessRunner.run(factory.newRun(config),
                    new BotPilot(BotPilot.Preset.EXPERT, 77), TICKS, true);
            HeadlessRunner.Outcome b = HeadlessRunner.run(factory.newRun(config),
                    new BotPilot(BotPilot.Preset.EXPERT, 77), TICKS, true);
            assertEquals(a.hashes(), b.hashes(), world);
            assertEquals(a.result().counters(), b.result().counters(), world);
            assertTrue(a.result().gatesPassed() >= 5, world + ": gates " + a.result().gatesPassed());
        }
    }

    /**
     * E32.d on a world that streams patterns and samples wind: the spawn decisions — table draws
     * and pattern steps alike — depend on the seed and not on the pilot. Storm Sky mixes gates,
     * bolts, wind zones and two patterns; the wind zones make the scroll itself depend on where
     * the bird flies, and the decision sequence must still be the same.
     */
    @Test
    void patternStepsAndTableDrawsDependOnlyOnTheSeed() {
        RunFactory factory = new RunFactory(GameContent.load());
        RunConfig config = RunConfig.builder(19).worldId("storm_sky").build();
        List<Long> expert = decisionsOf(factory, config, BotPilot.Preset.EXPERT, 1);
        List<Long> perfect = decisionsOf(factory, config, BotPilot.Preset.PERFECT, 2);
        List<Long> novice = decisionsOf(factory, config, BotPilot.Preset.NOVICE, 3);
        assertPrefixEquals(expert, perfect);
        int common = Math.min(expert.size(), novice.size());
        assertTrue(common > 3, "the novice run must spawn a few columns, was " + common);
        assertEquals(expert.subList(0, common), novice.subList(0, common),
                "a pilot that dies early still draws the same decisions while it lives");
        Run run = factory.newRun(config);
        HeadlessRunner.run(run, new BotPilot(BotPilot.Preset.PERFECT, 2), TICKS);
        assertTrue(run.simulation().spawner().streamer().patternsStarted() > 0,
                "the run must have streamed at least one pattern for the claim to cover steps");
    }

    /**
     * E32.d in the shipped Void: the rule cycles land {@code ALL_OBSTACLES_MOVE} on a tick that
     * depends on how the run was played (a draft holds the landing, a scroll card moves it), so
     * the spawn the flag first applies from differs between runs of the same seed — and the
     * decisions must still be the same, because the rule is applied at materialisation and
     * never folded into a decision. Twelve seeds, four draft answers, two presets each.
     */
    @Test
    void theVoidsDecisionsDependOnTheSeedAloneWhateverIsDraftedOrLanded() {
        RunFactory factory = new RunFactory(GameContent.load());
        int landedRuns = 0;
        boolean landingSpawnDiffered = false;
        for (long seed = 1; seed <= 12; seed++) {
            List<Long> reference = null;
            java.util.Set<List<Integer>> landingSpawns = new java.util.HashSet<>();
            for (int choice : new int[] {0, 1, 2, RunInput.SKIP}) {
                for (BotPilot.Preset preset : List.of(BotPilot.Preset.PERFECT,
                        BotPilot.Preset.EXPERT)) {
                    RunConfig config = RunConfig.builder(seed).worldId("void").allowOffers(true)
                            .build();
                    Run run = factory.newRun(config);
                    BotPilot bot = new BotPilot(preset, seed);
                    int shifts = 0;
                    List<Integer> landings = new java.util.ArrayList<>();
                    for (int t = 0; t < 6000 && !run.isFinished(); t++) {
                        RunInput input = run.phase() == RunPhase.CHOOSING_MODIFIER
                                ? new RunInput(false, false, choice, false) : bot.decide(run);
                        run.tick(input);
                        WorldEffects effects = run.simulation().worldEffects();
                        if (effects.shifts() > shifts) {
                            shifts = effects.shifts();
                            if (effects.activeIndex() == 0) {
                                // The spawn ALL_OBSTACLES_MOVE applies from, in this run.
                                landings.add(run.simulation().spawner().spawnCount());
                            }
                        }
                    }
                    List<Long> decisions = run.simulation().spawner().decisionHashes();
                    if (reference == null) {
                        reference = decisions;
                    } else {
                        int common = Math.min(reference.size(), decisions.size());
                        assertTrue(common > 5, "seed " + seed + ": common spawns " + common);
                        assertEquals(reference.subList(0, common), decisions.subList(0, common),
                                "seed " + seed + " choice " + choice + " " + preset.name()
                                        + ": the Void's decisions depend on the seed alone");
                    }
                    if (!landings.isEmpty()) {
                        landedRuns++;
                        landingSpawns.add(landings);
                    }
                }
            }
            landingSpawnDiffered |= landingSpawns.size() > 1;
        }
        assertTrue(landedRuns > 0, "ALL_OBSTACLES_MOVE never landed: the check would be vacuous");
        assertTrue(landingSpawnDiffered,
                "the flag landed on the same spawn in every run of every seed: nothing was tested");
    }

    private static List<Long> decisionsOf(RunFactory factory, RunConfig config,
            BotPilot.Preset preset, long pilotSeed) {
        Run run = factory.newRun(config);
        HeadlessRunner.run(run, new BotPilot(preset, pilotSeed), TICKS);
        return run.simulation().spawner().decisionHashes();
    }
}
