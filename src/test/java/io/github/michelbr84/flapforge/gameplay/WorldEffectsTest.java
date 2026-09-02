package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.AmbientSpec;
import io.github.michelbr84.flapforge.gameplay.spec.RuleCycleSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.support.DraftRuns;
import io.github.michelbr84.flapforge.support.FixedSpawnTable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M7: ambient wind, cosmetic flashes (E8) and the Void's rule cycles. */
class WorldEffectsTest {

    private static final double EPS = 1e-9;
    private static final StatModifier GAP = StatModifier.multiply(StatId.GAP_SIZE, 0.85, "cycle");
    private static final StatModifier GRAVITY =
            StatModifier.multiply(StatId.GRAVITY, 1.3, "cycle");

    private static Run run(long seed, WorldSpec world) {
        return new Run(RunConfig.classic(seed), RunSetup.CLASSIC.withWorld(world),
                new FixedSpawnTable());
    }

    private static void idle(Run run, int ticks) {
        for (int i = 0; i < ticks; i++) {
            run.tick(RunInput.NONE);
        }
    }

    // ------------------------------------------------------------ ambient wind

    @Test
    void aVerticalAmbientWindJoinsGravityEveryTick() {
        WorldSpec down = WorldSpec.GREEN_FIELDS.withAmbient(new AmbientSpec(0, 0, 300, 0));
        WorldSpec up = WorldSpec.GREEN_FIELDS.withAmbient(new AmbientSpec(0, 0, -300, 0));
        Run still = run(1, WorldSpec.GREEN_FIELDS);
        Run downdraft = run(1, down);
        Run updraft = run(1, up);
        still.tick(RunInput.FLAP);
        downdraft.tick(RunInput.FLAP);
        updraft.tick(RunInput.FLAP);
        idle(still, 10);
        idle(downdraft, 10);
        idle(updraft, 10);
        double stillY = still.simulation().bird().y();
        assertTrue(downdraft.simulation().bird().y() > stillY, "a downdraft pulls the bird down");
        assertTrue(updraft.simulation().bird().y() < stillY, "an updraft holds it up");
        // The wind is a plain acceleration: over 11 velocity-first ticks it adds
        // 300 / 60 px/s per tick to vy, i.e. 300 / 3600 × (1 + 2 + … + 11) px to y.
        double expected = 300.0 / 3600 * (11 * 12 / 2.0);
        assertEquals(expected, downdraft.simulation().bird().y() - stillY, 1e-6);
        assertTrue(downdraft.setup().world().hasWorldEffects());
        assertTrue(downdraft.simulation().worldEffects().isActive());
        assertNotEquals(still.simulation().stateHash(), downdraft.simulation().stateHash());
    }

    @Test
    void aHorizontalAmbientWindChangesTheScroll() {
        WorldSpec headwind = WorldSpec.GREEN_FIELDS.withAmbient(new AmbientSpec(0, -20, 0, 0));
        Run still = run(2, WorldSpec.GREEN_FIELDS);
        Run windy = run(2, headwind);
        still.tick(RunInput.FLAP);
        windy.tick(RunInput.FLAP);
        idle(still, 30);
        idle(windy, 30);
        Obstacle a = still.simulation().obstacles().obstacles().get(0);
        Obstacle b = windy.simulation().obstacles().obstacles().get(0);
        assertEquals(Playfield.WIDTH - 30 * 2.0, a.x(), EPS, "120 px/s is 2 px a tick");
        assertEquals(Playfield.WIDTH - 30 * (120 - 20) / 60.0, b.x(), EPS,
                "a −20 px/s headwind slows the world to 100 px/s");
        assertEquals(-20, windy.simulation().context().windScroll(), EPS);
        assertEquals(0, still.simulation().context().windScroll(), EPS);
    }

    @Test
    void greenFieldsSamplesNoWindAndFoldsNothing() {
        Run run = run(3, WorldSpec.GREEN_FIELDS);
        assertFalse(run.simulation().worldEffects().isActive());
        assertEquals(0, run.simulation().darkness(), EPS);
        run.tick(RunInput.FLAP);
        assertEquals(0, run.simulation().bird().windAccelY(), EPS);
        assertEquals(0, run.simulation().bird().windScroll(), EPS);
    }

    @Test
    void darknessIsAValueTheRendererReads() {
        WorldSpec dark = WorldSpec.GREEN_FIELDS.withAmbient(new AmbientSpec(0.5, 0, 0, 0));
        Run run = run(4, dark);
        assertEquals(0.5, run.simulation().darkness(), EPS);
        Run bright = run(4, WorldSpec.GREEN_FIELDS);
        run.tick(RunInput.FLAP);
        bright.tick(RunInput.FLAP);
        idle(run, 60);
        idle(bright, 60);
        assertEquals(bright.simulation().bird().y(), run.simulation().bird().y(), EPS,
                "darkness changes nothing in the simulation");
        assertEquals(bright.simulation().obstacles().obstacles().get(0).x(),
                run.simulation().obstacles().obstacles().get(0).x(), EPS);
    }

    // ---------------------------------------------------------- sky flashes

    @Test
    void cosmeticLightningFlashesEveryNGatesWithNoHitbox() {
        WorldSpec stormy = WorldSpec.GREEN_FIELDS.withAmbient(new AmbientSpec(0, 0, 0, 3));
        Run run = run(5, stormy);
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 5);
        List<Integer> flashGates = new ArrayList<>();
        while (!run.isFinished() && run.stats().gatesPassed() < 12) {
            TickReport report = run.tick(bot.decide(run));
            for (TickFact f : report.facts()) {
                if (f instanceof TickFact.AmbientFlash) {
                    flashGates.add(run.stats().gatesPassed());
                }
            }
        }
        assertEquals(List.of(3, 6, 9, 12), flashGates, "one flash every three gates (E8)");
        assertEquals(4, run.simulation().worldEffects().flashes());
        for (Obstacle o : run.simulation().obstacles().obstacles()) {
            assertNotEquals(ObstacleKind.LIGHTNING, o.kind(), "a flash is not a bolt");
        }
        assertEquals(ObstacleKind.PIPE_GATE, run.simulation().obstacles().last().kind());
    }

    // ---------------------------------------------------------- rule cycles

    private static RuleCycleSpec cycle(int everyGates, int telegraph) {
        return new RuleCycleSpec(everyGates, telegraph, List.of(
                new RuleCycleSpec.Option(RuleSet.of(RuleFlag.ALL_OBSTACLES_MOVE), List.of()),
                new RuleCycleSpec.Option(RuleSet.EMPTY, List.of(GAP)),
                new RuleCycleSpec.Option(RuleSet.EMPTY, List.of(GRAVITY)),
                new RuleCycleSpec.Option(RuleSet.of(RuleFlag.LETHAL_CEILING), List.of())));
    }

    /** What a run's rule cycles did, tick by tick. */
    private record Trace(List<Integer> shiftGates, List<Integer> telegraphs,
            List<Integer> landedTicks, List<Integer> landedOptions, List<RunPhase> landedPhases,
            List<Integer> factTicks) {
    }

    private static Trace trace(Run run, BotPilot bot, int maxGates) {
        List<Integer> shiftGates = new ArrayList<>();
        List<Integer> telegraphs = new ArrayList<>();
        List<Integer> landedTicks = new ArrayList<>();
        List<Integer> landedOptions = new ArrayList<>();
        List<RunPhase> landedPhases = new ArrayList<>();
        List<Integer> factTicks = new ArrayList<>();
        int shifts = 0;
        int tail = 0;
        while (!run.isFinished() && run.tick() < 30000) {
            if (run.stats().gatesPassed() >= maxGates && ++tail > 120) {
                // A shift announced on the last gate still has its telegraph to run.
                break;
            }
            TickReport report = run.tick(bot.decide(run));
            for (TickFact f : report.facts()) {
                if (f instanceof TickFact.RuleShift shift) {
                    shiftGates.add(run.stats().gatesPassed());
                    telegraphs.add(shift.telegraphTicks());
                    factTicks.add(run.tick());
                }
            }
            WorldEffects effects = run.simulation().worldEffects();
            if (effects.shifts() > shifts) {
                shifts = effects.shifts();
                landedTicks.add(run.tick());
                landedOptions.add(effects.activeIndex());
                landedPhases.add(run.phase());
            }
        }
        return new Trace(shiftGates, telegraphs, landedTicks, landedOptions, landedPhases,
                factTicks);
    }

    @Test
    void aShiftIsDrawnEveryNGatesTelegraphedAndLandsAfterTheTelegraph() {
        WorldSpec voidLike = WorldSpec.GREEN_FIELDS.withRuleCycles(cycle(2, 30));
        Run run = run(6, voidLike);
        Trace trace = trace(run, new BotPilot(BotPilot.Preset.PERFECT, 6), 12);
        assertEquals(List.of(2, 4, 6, 8, 10, 12), trace.shiftGates(), "one draw every two gates");
        for (int t : trace.telegraphs()) {
            assertEquals(30, t, "the fact carries the countdown start");
        }
        assertEquals(6, trace.landedTicks().size(), "every announced shift landed");
        for (int i = 0; i < 6; i++) {
            assertEquals(trace.factTicks().get(i) + 30, trace.landedTicks().get(i),
                    "shift " + i + " landed thirty flying ticks after its telegraph");
            assertEquals(RunPhase.FLYING, trace.landedPhases().get(i));
        }
        assertEquals(6, run.simulation().worldEffects().shifts());
    }

    @Test
    void aShiftNeverLandsTheSameOptionTwiceInARow() {
        WorldSpec voidLike = WorldSpec.GREEN_FIELDS.withRuleCycles(cycle(1, 0));
        Run run = run(7, voidLike);
        Trace trace = trace(run, new BotPilot(BotPilot.Preset.PERFECT, 7), 40);
        assertTrue(trace.landedOptions().size() >= 30, "shifts " + trace.landedOptions());
        for (int i = 1; i < trace.landedOptions().size(); i++) {
            assertNotEquals(trace.landedOptions().get(i - 1), trace.landedOptions().get(i),
                    "shift " + i + " repeated its option: " + trace.landedOptions());
        }
        boolean[] seen = new boolean[4];
        for (int option : trace.landedOptions()) {
            seen[option] = true;
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(seen[i], "option " + i + " never landed: " + trace.landedOptions());
        }
    }

    @Test
    void anOptionsEffectsLandInTheWorldCycleLayerAndReplaceThePreviousOnes() {
        WorldSpec voidLike = WorldSpec.GREEN_FIELDS.withRuleCycles(cycle(1, 0));
        Run run = run(8, voidLike);
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 8);
        int checked = 0;
        int previous = -1;
        while (!run.isFinished() && run.stats().gatesPassed() < 30) {
            run.tick(bot.decide(run));
            WorldEffects effects = run.simulation().worldEffects();
            int active = effects.activeIndex();
            if (active < 0 || active == previous) {
                continue;
            }
            previous = active;
            checked++;
            List<StatModifier> layer = run.simulation().effects().layer(Layer.WORLD_CYCLE);
            RuleSet rules = run.simulation().rules();
            switch (active) {
                case 0:
                    assertEquals(List.of(), layer);
                    assertTrue(rules.contains(RuleFlag.ALL_OBSTACLES_MOVE));
                    assertFalse(rules.contains(RuleFlag.LETHAL_CEILING));
                    break;
                case 1:
                    assertEquals(List.of(GAP), layer, "the previous option's effects are gone");
                    assertEquals(128 * 0.85, run.simulation().stats().resolve(StatId.GAP_SIZE),
                            EPS);
                    assertFalse(rules.contains(RuleFlag.ALL_OBSTACLES_MOVE));
                    break;
                case 2:
                    assertEquals(List.of(GRAVITY), layer);
                    assertEquals(1800 * 1.3, run.simulation().stats().resolve(StatId.GRAVITY),
                            EPS);
                    assertEquals(128, run.simulation().stats().resolve(StatId.GAP_SIZE), EPS);
                    break;
                default:
                    assertEquals(List.of(), layer);
                    assertTrue(rules.contains(RuleFlag.LETHAL_CEILING));
                    assertFalse(rules.contains(RuleFlag.ALL_OBSTACLES_MOVE),
                            "flags are replaced, not accumulated");
                    break;
            }
        }
        assertTrue(checked >= 10, "options checked: " + checked);
    }

    @Test
    void aFlagThatZeroesTheShieldTakesItAwayAndGivesItBack() {
        RuleCycleSpec defensive = new RuleCycleSpec(1, 0, List.of(
                new RuleCycleSpec.Option(RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES), List.of()),
                new RuleCycleSpec.Option(RuleSet.EMPTY, List.of(GAP))));
        WorldSpec world = WorldSpec.GREEN_FIELDS.withRuleCycles(defensive);
        RunConfig config = RunConfig.builder(9)
                .addPermanentEffect(StatModifier.flat(StatId.SHIELD_CHARGES, 2, "upgrade:test"))
                .build();
        Run run = new Run(config, RunSetup.CLASSIC.withWorld(world), new FixedSpawnTable());
        assertEquals(2, run.simulation().shield().maxCharges());
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 9);
        int zeroed = 0;
        int restored = 0;
        while (!run.isFinished() && run.stats().gatesPassed() < 12) {
            run.tick(bot.decide(run));
            int active = run.simulation().worldEffects().activeIndex();
            if (active == 0) {
                assertEquals(0, run.simulation().stats().resolve(StatId.SHIELD_CHARGES), EPS);
                assertEquals(0, run.simulation().shield().maxCharges(), "zeroed by the flag (D8)");
                assertFalse(run.simulation().shield().hasCharge());
                zeroed++;
            } else if (active == 1) {
                assertEquals(2, run.simulation().shield().maxCharges(), "the charges come back");
                assertTrue(run.simulation().shield().hasCharge());
                restored++;
            }
        }
        assertTrue(zeroed > 0 && restored > 0, "zeroed " + zeroed + " restored " + restored);
    }

    @Test
    void aShiftNeverLandsDuringADraftAndWaitsForTheNextFlyingTick() {
        GameContent content = GameContent.load();
        WorldSpec voidLike = WorldSpec.GREEN_FIELDS.withRuleCycles(cycle(2, 30));
        RunConfig config = RunConfig.builder(10).allowOffers(true).build();
        Run run = new Run(config, RunSetup.CLASSIC.withWorld(voidLike)
                .withModifiers(DraftRuns.catalog(content, 2, 3, "score_plus", "tailwind",
                        "wide_gaps")), new FixedSpawnTable());
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 10);
        boolean sawChoosing = false;
        boolean sawBreather = false;
        int landedAfterDraft = -1;
        int shifts = 0;
        int telegraphTick = -1;
        int tail = 0;
        while (!run.isFinished() && run.tick() < 6000) {
            if (run.stats().gatesPassed() >= 5
                    && (!run.simulation().worldEffects().isTelegraphing() || ++tail > 400)) {
                break;
            }
            TickReport report = run.tick(bot.decide(run));
            for (TickFact f : report.facts()) {
                if (f instanceof TickFact.RuleShift && telegraphTick < 0) {
                    telegraphTick = run.tick();
                }
            }
            sawBreather |= run.phase() == RunPhase.BREATHER;
            sawChoosing |= run.phase() == RunPhase.CHOOSING_MODIFIER;
            WorldEffects effects = run.simulation().worldEffects();
            if (effects.shifts() > shifts) {
                shifts = effects.shifts();
                assertEquals(RunPhase.FLYING, run.phase(),
                        "a shift lands on a flying tick only, at tick " + run.tick());
                if (sawChoosing && landedAfterDraft < 0) {
                    landedAfterDraft = run.tick();
                }
            }
        }
        assertTrue(sawBreather && sawChoosing, "the draft at gate 2 opened");
        assertTrue(telegraphTick > 0, "the cycle at gate 2 announced itself");
        assertTrue(landedAfterDraft > telegraphTick + 30,
                "the shift announced with the breather landed only after the draft closed: "
                        + landedAfterDraft + " vs telegraph at " + telegraphTick);
        assertNull(run.simulation().worldEffects().pendingOption(),
                "nothing is left pending once the run flies again");
    }

    /**
     * One announcement is one landing (M7 fix): a cadence gate reached while the previous
     * option is still pending draws nothing and announces nothing, so the banner never promises
     * a rule that is then replaced. A 90-tick telegraph on every gate outlives the next gate at
     * the classic scroll (80 ticks), and a draft holding a landing keeps it pending longer.
     */
    @Test
    void aCadenceGateReachedWhileAnOptionIsPendingAnnouncesNothing() {
        WorldSpec everyGate = WorldSpec.GREEN_FIELDS.withRuleCycles(cycle(1, 90));
        Run run = run(12, everyGate);
        Trace trace = trace(run, new BotPilot(BotPilot.Preset.PERFECT, 12), 24);
        WorldEffects effects = run.simulation().worldEffects();
        assertTrue(trace.shiftGates().size() >= 8, "announcements " + trace.shiftGates());
        assertTrue(trace.shiftGates().size() < 24, "some cadence gates were skipped: "
                + trace.shiftGates());
        assertEquals(effects.announcements(), trace.shiftGates().size(), "one fact per draw");
        assertEquals(effects.shifts() + (effects.isTelegraphing() ? 1 : 0),
                effects.announcements(), "every announcement lands, once");
        assertEquals(trace.landedTicks().size(), effects.shifts());
        for (int i = 1; i < trace.shiftGates().size(); i++) {
            assertTrue(trace.factTicks().get(i) > trace.landedTicks().get(i - 1),
                    "announcement " + i + " came only after the previous one landed");
        }

        GameContent content = GameContent.load();
        RunConfig config = RunConfig.builder(13).allowOffers(true).build();
        Run drafted = new Run(config, RunSetup.CLASSIC.withWorld(everyGate)
                .withModifiers(DraftRuns.catalog(content, 2, 3, "score_plus", "tailwind",
                        "wide_gaps")), new FixedSpawnTable());
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 13);
        int factsBeforeFirstLanding = 0;
        boolean sawChoosing = false;
        while (!drafted.isFinished() && drafted.simulation().worldEffects().shifts() == 0
                && drafted.tick() < 6000) {
            TickReport report = drafted.tick(bot.decide(drafted));
            for (TickFact f : report.facts()) {
                if (f instanceof TickFact.RuleShift) {
                    factsBeforeFirstLanding++;
                }
            }
            sawChoosing |= drafted.phase() == RunPhase.CHOOSING_MODIFIER;
        }
        assertTrue(sawChoosing, "the draft at gate 2 held the first landing");
        assertEquals(1, drafted.simulation().worldEffects().shifts());
        assertEquals(1, factsBeforeFirstLanding,
                "the gates passed while the draft held the option announced nothing new");
    }

    @Test
    void theCycleStateIsFoldedIntoTheStateHash() {
        WorldSpec voidLike = WorldSpec.GREEN_FIELDS.withRuleCycles(cycle(1, 0));
        Run a = run(11, voidLike);
        Run b = run(11, voidLike);
        BotPilot botA = new BotPilot(BotPilot.Preset.PERFECT, 11);
        BotPilot botB = new BotPilot(BotPilot.Preset.PERFECT, 11);
        for (int i = 0; i < 1500 && !a.isFinished(); i++) {
            a.tick(botA.decide(a));
            b.tick(botB.decide(b));
            assertEquals(a.simulation().stateHash(), b.simulation().stateHash(), "tick " + i);
        }
        assertTrue(a.simulation().worldEffects().shifts() >= 3);
        Run still = run(11, WorldSpec.GREEN_FIELDS);
        still.tick(RunInput.FLAP);
        Run cycling = run(11, voidLike);
        cycling.tick(RunInput.FLAP);
        assertNotEquals(still.simulation().stateHash(), cycling.simulation().stateHash(),
                "a world with cycles folds their state even before the first shift");
    }
}
