package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.StreakBonusDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.modifier.ModifierTag;
import io.github.michelbr84.flapforge.modifier.Rarity;
import io.github.michelbr84.flapforge.support.FixedSpawnTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunLifecycleTest {

    /** A card that pays a streak bonus and nothing else, so the M6 term is easy to read. */
    private static final ModifierDef BOUNTY = new ModifierDef("bounty", Rarity.RARE,
            List.of(ModifierTag.ECONOMY), 1, List.of(), List.of(), List.of(), List.of(),
            new StreakBonusDef(10), UnlockConditionDef.DEFAULT);

    @Test
    void readyWaitsForTheFirstFlap() {
        Run run = Run.classic(RunConfig.classic(1));
        assertEquals(RunPhase.READY, run.phase());
        for (int i = 0; i < 100; i++) {
            TickReport report = run.tick(RunInput.NONE);
            assertTrue(report.isEmpty());
        }
        assertEquals(RunPhase.READY, run.phase());
        assertEquals(Playfield.BIRD_START_Y, run.simulation().bird().y(), 0.0, "bird floats");
        assertTrue(run.simulation().obstacles().isEmpty(), "nothing spawns in READY");
        assertEquals(0, run.simulation().tick());
        assertEquals(0, run.stats().ticksAlive());
        assertEquals(100, run.tick());
        run.tick(RunInput.AUTO_FLAP);
        assertEquals(RunPhase.READY, run.phase(), "holding without an edge does not start");
    }

    @Test
    void firstFlapStartsFlyingAndSpawnsTheFirstGate() {
        Run run = Run.classic(RunConfig.classic(1));
        TickReport report = run.tick(RunInput.FLAP);
        assertEquals(RunPhase.FLYING, run.phase());
        Optional<TickFact.PhaseChanged> change = report.first(TickFact.PhaseChanged.class);
        assertTrue(change.isPresent());
        assertEquals(RunPhase.READY, change.get().from());
        assertEquals(RunPhase.FLYING, change.get().to());
        assertTrue(report.has(TickFact.Flapped.class));
        assertTrue(report.has(TickFact.ObstacleSpawned.class));
        assertEquals(1, run.simulation().obstacles().size());
        assertEquals(Playfield.BIRD_START_Y - 6.25, run.simulation().bird().y(), 0.0,
                "the flap is applied before the first integration");
        assertEquals(1, run.stats().ticksAlive());
    }

    @Test
    void obstaclesScrollOnlyWhileFlying() {
        Run run = Run.classic(RunConfig.classic(2));
        run.tick(RunInput.FLAP);
        Obstacle gate = run.simulation().obstacles().last();
        double x0 = gate.x();
        run.tick(RunInput.NONE);
        assertEquals(x0 - 2, gate.x(), 0.0, "2 px per tick at 120 px/s");
    }

    @Test
    void groundContactFinishesTheRunOnTheSameTick() {
        Run run = Run.classic(RunConfig.classic(1));
        run.simulation().spawner().setSuppressed(true);
        run.tick(RunInput.FLAP);
        TickReport last = null;
        int ticks = 1;
        while (run.phase() == RunPhase.FLYING) {
            last = run.tick(RunInput.NONE);
            ticks++;
        }
        assertEquals(RunPhase.FINISHED, run.phase());
        assertEquals(2, last.count(TickFact.PhaseChanged.class), "FLYING→DYING→FINISHED");
        assertTrue(last.has(TickFact.Crashed.class));
        assertEquals(CollisionCause.GROUND, last.first(TickFact.Crashed.class).get().cause());
        assertEquals(CollisionCause.GROUND, run.stats().deathCause());
        assertEquals(Playfield.GROUND_DEATH_Y, run.simulation().bird().y(), 0.0);
        assertEquals(Bird.State.DEAD, run.simulation().bird().state());
        assertEquals(ticks, run.stats().ticksAlive());
        assertTrue(run.isFinished());
    }

    @Test
    void obstacleHitFreezesTheWorldWhileTheBirdFalls() {
        // Flapping every tick pins the bird against the ceiling gate, where the first gate's
        // upper pipe (top ≥ 80) catches it.
        Run run = Run.classic(RunConfig.classic(3));
        run.tick(RunInput.FLAP);
        int guard = 0;
        while (run.phase() == RunPhase.FLYING && guard++ < 1000) {
            run.tick(RunInput.FLAP);
        }
        assertEquals(RunPhase.DYING, run.phase());
        assertEquals(CollisionCause.OBSTACLE, run.stats().deathCause());
        assertEquals(15.0, run.simulation().bird().vy(), 0.0, "E28: death fall seeded at +15 px/s");
        assertEquals(Bird.State.DYING, run.simulation().bird().state());
        List<Obstacle> obstacles = run.simulation().obstacles().obstacles();
        assertFalse(obstacles.isEmpty());
        List<Double> xs = new ArrayList<>();
        List<Double> offsets = new ArrayList<>();
        for (Obstacle o : obstacles) {
            xs.add(o.x());
            offsets.add(((PipeGate) o).offsetY());
            assertEquals(o.x(), o.prevX(), 0.0, "settled for interpolation");
        }
        double yBefore = run.simulation().bird().y();
        int dyingTicks = 0;
        while (run.phase() == RunPhase.DYING) {
            TickReport report = run.tick(RunInput.FLAP);
            assertFalse(report.has(TickFact.Flapped.class), "no flaps while dying");
            dyingTicks++;
            for (int i = 0; i < obstacles.size(); i++) {
                assertEquals(xs.get(i), obstacles.get(i).x(), 0.0, "obstacles frozen");
                assertEquals(offsets.get(i), ((PipeGate) obstacles.get(i)).offsetY(), 0.0,
                        "oscillators frozen");
            }
        }
        assertTrue(dyingTicks > 1);
        assertTrue(run.simulation().bird().y() > yBefore, "the bird fell");
        assertEquals(RunPhase.FINISHED, run.phase());
        assertEquals(Playfield.GROUND_DEATH_Y, run.simulation().bird().y(), 0.0);
        assertEquals(Bird.State.DEAD, run.simulation().bird().state());
        assertEquals(obstacles.size(), run.simulation().obstacles().size(), "no spawns after death");
        int ticksAlive = run.stats().ticksAlive();
        run.tick(RunInput.FLAP);
        assertEquals(RunPhase.FINISHED, run.phase());
        assertEquals(ticksAlive, run.stats().ticksAlive(), "FINISHED ticks do nothing");
    }

    @Test
    void scoringAwardsEachGateOnceWhenFullyCleared() {
        Run run = Run.classic(RunConfig.classic(42));
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 42);
        List<Obstacle> scoredEver = new ArrayList<>();
        int gates = 0;
        for (int t = 0; t < 1200 && !run.isFinished(); t++) {
            TickReport report = run.tick(bot.decide(run));
            gates += report.count(TickFact.GatePassed.class);
            assertEquals(gates, run.stats().gatesPassed());
            assertEquals(report.count(TickFact.GatePassed.class),
                    report.count(TickFact.Scored.class));
            double left = run.simulation().bird().hitbox().x();
            for (Obstacle o : run.simulation().obstacles().obstacles()) {
                if (o.isScored()) {
                    assertTrue(o.scoreLineX() <= left, "a scored gate lies fully behind the hitbox");
                    if (!scoredEver.contains(o)) {
                        scoredEver.add(o);
                    }
                } else {
                    assertTrue(o.scoreLineX() > left, "an unscored gate is still ahead");
                }
            }
        }
        assertTrue(gates >= 5, "gates " + gates);
        assertEquals(gates, scoredEver.size(), "every gate scored exactly once");
        assertEquals(gates, run.stats().points(), 0.0, "SCORE_MULT 1 → points = gates");
        assertEquals(gates, run.result().counter("gates"));
    }

    @Test
    void scoreMultiplierScalesPoints() {
        RunConfig config = RunConfig.builder(42)
                .addPermanentEffect(StatModifier.percent(StatId.SCORE_MULT, 0.5, "test")).build();
        Run run = Run.classic(config);
        assertEquals(List.of(StatModifier.percent(StatId.SCORE_MULT, 0.5, "test")),
                run.simulation().effects().layer(Layer.UPGRADES));
        HeadlessRunner.Outcome outcome = HeadlessRunner.run(run,
                new BotPilot(BotPilot.Preset.PERFECT, 42), 1200);
        assertTrue(outcome.result().gatesPassed() >= 3);
        assertEquals(outcome.result().gatesPassed() * 1.5, outcome.result().stats().points(), 1e-9);
    }

    @Test
    void resultSnapshotsTheStats() {
        Run run = Run.classic(RunConfig.classic(7));
        run.simulation().spawner().setSuppressed(true);
        run.tick(RunInput.FLAP);
        RunResult early = run.result();
        assertEquals(1, early.counter("ticks"));
        assertEquals(1, early.counter("flaps"));
        assertNull(early.stats().deathCause());
        while (!run.isFinished()) {
            run.tick(RunInput.NONE);
        }
        RunResult late = run.result();
        assertEquals(1, early.stats().ticksAlive(), "the early snapshot did not change");
        assertTrue(late.stats().ticksAlive() > 1);
        assertEquals(CollisionCause.GROUND, late.stats().deathCause());
        assertEquals(RunConfig.classic(7), late.config());
        assertEquals(0, late.counter("obstaclesSpawned"));
        assertEquals(0, late.counter("missing"));
    }

    /** M3: the coins of a run and its clean-gate streak are tallied into the result (D11). */
    @Test
    void coinsAndStreaksAreTalliedIntoTheResult() {
        Run run = Run.classic(RunConfig.classic(42));
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 42);
        int coinFacts = 0;
        int streakFacts = 0;
        for (int t = 0; t < 1200 && !run.isFinished(); t++) {
            TickReport report = run.tick(bot.decide(run));
            coinFacts += report.count(TickFact.CoinCollected.class);
            streakFacts += report.count(TickFact.StreakChanged.class);
            assertEquals(coinFacts, run.stats().coinsCollected(), "every coin fact is tallied");
            assertEquals(run.simulation().streaks().streak(), run.stats().streak());
        }
        assertTrue(coinFacts > 0, "the perfect bot flies through the coin trails");
        assertEquals(run.stats().gatesPassed(), streakFacts, "one streak change per gate");
        RunResult result = run.result();
        assertEquals(coinFacts, result.counter("coins"));
        assertEquals(run.stats().streakBest(), result.counter("streakBest"));
        assertEquals(run.simulation().streaks().steps(), result.stats().streakSteps());
        assertTrue(result.stats().streakBest() >= result.stats().streak());
    }

    /**
     * M5 (D9, D11): the ability edge is part of the run input, so it does nothing before the run
     * starts, activates inside FLYING, and is tallied into {@code RunStats} through the facts.
     */
    @Test
    void theAbilityEdgeIsPartOfTheRunLifecycle() {
        RunFactory factory = new RunFactory(GameContent.load());
        Run run = factory.newRun(RunConfig.builder(5).activeAbilityId("dash").build());

        TickReport ready = run.tick(new RunInput(false, true, RunInput.NO_CHOICE, false));
        assertEquals(RunPhase.READY, run.phase(), "an ability edge does not start a run");
        assertTrue(ready.isEmpty());
        assertEquals(0, run.simulation().abilities().active().activations());

        run.tick(RunInput.FLAP);
        run.simulation().spawner().setSuppressed(true);
        run.simulation().obstacles().clear();
        TickReport used = run.tick(new RunInput(false, true, RunInput.NO_CHOICE, false));
        assertTrue(used.has(TickFact.AbilityActivated.class));
        assertEquals(1, run.stats().abilitiesUsed().get("dash"));

        while (!run.isFinished()) {
            run.tick(new RunInput(false, true, RunInput.NO_CHOICE, false));
        }
        RunResult result = run.result();
        assertEquals(Map.of("dash", 1), result.stats().abilitiesUsed(),
                "DYING and FINISHED ignore the edge");
        assertEquals("dash", result.config().activeAbilityId());
    }

    /**
     * M6 (D11): the draft phases are part of the lifecycle, and the run comes back out of them
     * exactly where it went in. The corridor is fixed so the only thing that can move the bird is
     * the draft, and it moves it not at all.
     */
    @Test
    void theDraftPhasesRunAndGiveTheRunBack() {
        ModifierCatalog catalog = new ModifierCatalog(List.of(2), 1,
                Map.of(Rarity.RARE, 1), List.of(BOUNTY), List.of());
        Run run = new Run(RunConfig.builder(4).allowOffers(true).build(),
                RunSetup.CLASSIC.withModifiers(catalog), new FixedSpawnTable());
        List<RunPhase> phases = new ArrayList<>();
        phases.add(run.phase());
        for (int t = 0; t < 3000 && run.stats().modifiersTaken().isEmpty(); t++) {
            TickReport report = run.tick(pilot(run));
            for (TickFact fact : report.facts()) {
                if (fact instanceof TickFact.PhaseChanged change) {
                    phases.add(change.to());
                }
            }
        }
        assertEquals(List.of(RunPhase.READY, RunPhase.FLYING, RunPhase.BREATHER,
                RunPhase.CHOOSING_MODIFIER, RunPhase.RESUME_HOLD), phases,
                "READY → FLYING → BREATHER → CHOOSING_MODIFIER → RESUME_HOLD (D11)");
        for (int t = 0; t < ModifierDirector.RESUME_HOLD_TICKS; t++) {
            run.tick(RunInput.NONE);
        }
        assertEquals(RunPhase.FLYING, run.phase());
        assertEquals(List.of("bounty"), run.stats().modifiersTaken());
        assertEquals(10, run.stats().modifierStreakCoins(),
                "E32.a: the streak term M3 left at zero is fed by the card the run took");
        assertEquals(10, run.result().stats().modifierStreakCoins(), "and it reaches the result");
    }

    /** A forced modifier is taken before the first tick and is in the stats from tick zero. */
    @Test
    void aForcedModifierIsAlreadyTakenBeforeTheRunStarts() {
        ModifierCatalog catalog = new ModifierCatalog(List.of(), 1, Map.of(Rarity.RARE, 1),
                List.of(BOUNTY), List.of());
        Run run = new Run(RunConfig.builder(4).forcedModifiers(List.of("bounty")).build(),
                RunSetup.CLASSIC.withModifiers(catalog));
        assertEquals(RunPhase.READY, run.phase());
        assertEquals(List.of("bounty"), run.stats().modifiersTaken());
        assertEquals(10, run.stats().modifierStreakCoins());
    }

    private static RunInput pilot(Run run) {
        if (run.phase() == RunPhase.READY) {
            return RunInput.FLAP;
        }
        if (run.phase() == RunPhase.CHOOSING_MODIFIER) {
            return RunInput.choose(0);
        }
        return run.simulation().bird().y() > Playfield.BIRD_START_Y + 10
                ? RunInput.FLAP : RunInput.NONE;
    }

    @Test
    void stateHashChangesEveryTick() {
        Run run = Run.classic(RunConfig.classic(9));
        long h0 = run.simulation().stateHash();
        run.tick(RunInput.FLAP);
        long h1 = run.simulation().stateHash();
        run.tick(RunInput.NONE);
        long h2 = run.simulation().stateHash();
        assertTrue(h0 != h1 && h1 != h2);
    }
}
