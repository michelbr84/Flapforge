package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import io.github.michelbr84.flapforge.content.ContentAdapters;
import io.github.michelbr84.flapforge.content.ContentLoader;
import io.github.michelbr84.flapforge.content.StrictBinder;
import io.github.michelbr84.flapforge.content.defs.PatternDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.KindParams;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleParams;
import io.github.michelbr84.flapforge.gameplay.obstacle.PatternStreamer;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.PatternSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.support.FixedSpawnTable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** D7, M7: set pieces streamed through the spawn cursor. */
class PatternStreamerTest {

    private static final double EPS = 1e-9;

    private static PatternSpec.Step step(int dx, ObstacleKind kind, Map<String, ?> params,
            boolean scoring) {
        return new PatternSpec.Step(dx, kind, ObstacleParams.resolve(kind, params), scoring);
    }

    private static PatternSpec.Step gate(int dx, Object gapCenter, double gapSize) {
        return step(dx, ObstacleKind.PIPE_GATE,
                Map.of("layout", "STANDARD", "gapCenter", gapCenter, "gapSize", gapSize), true);
    }

    private static PatternSpec pattern(String id, int weight, int minGate, boolean scoringSteps,
            PatternSpec.Step... steps) {
        return new PatternSpec(id, weight, minGate, scoringSteps, List.of(steps));
    }

    /** The five-kind set piece every placement test streams. */
    private static PatternSpec mixed() {
        return pattern("mixed", 0, 0, true,
                gate(160, 0.45, 128),
                step(130, ObstacleKind.GEAR, Map.of("cy", 0.3, "radius", 36,
                        "rail", Map.of("amplitude", 60, "speed", 40)), false),
                step(130, ObstacleKind.PISTON, Map.of("side", "BOTTOM", "length", 220,
                        "telegraphTicks", 40), true),
                step(180, ObstacleKind.WIND_ZONE, Map.of("width", 120, "cy", 0.5, "height", 300,
                        "accelY", -400, "scrollDelta", 20), true),
                step(200, ObstacleKind.LIGHTNING, Map.of("side", "TOP", "lengthFrac", 0.6,
                        "warningTicks", 50, "strikeTicks", 8), true));
    }

    private static Run forced(long seed, PatternSpec forced) {
        return new Run(RunConfig.classic(seed), RunSetup.CLASSIC.withForcedPattern(forced));
    }

    /** The x every column of a run spawned at, keyed by identity. */
    private static final Map<Obstacle, Double> SPAWN_X = new java.util.IdentityHashMap<>();
    /** The distance from the previous column's left edge on the tick a column spawned. */
    private static final Map<Obstacle, Double> SPACING = new java.util.IdentityHashMap<>();

    /**
     * Ticks a run until {@code count} obstacles spawned, with the bird parked and invulnerable:
     * the columns are what these tests read, not the flight.
     */
    private static List<Obstacle> spawnedBy(Run run, int count) {
        List<Obstacle> spawned = new ArrayList<>();
        run.simulation().grantIFrames(1_000_000);
        RunInput input = RunInput.FLAP;
        while (spawned.size() < count && run.tick() < 4000 && !run.isFinished()) {
            run.simulation().bird().setY(Playfield.BIRD_START_Y);
            run.simulation().bird().setVy(0);
            run.tick(input);
            input = RunInput.NONE;
            for (Obstacle o : run.simulation().obstacles().obstacles()) {
                if (!spawned.contains(o)) {
                    // A column does not scroll on the tick it spawns (the layer moves before the
                    // spawner runs), so its x after that tick is its spawn x, and the previous
                    // column is still alive at its scrolled x: the step's dx is the distance
                    // between the two now, not between their spawn positions.
                    Obstacle previous = spawned.isEmpty() ? null : spawned.get(spawned.size() - 1);
                    spawned.add(o);
                    SPAWN_X.put(o, o.x());
                    SPACING.put(o, previous == null ? Double.NaN : o.x() - previous.x());
                }
            }
        }
        return spawned;
    }

    /** The x a column spawned at. */
    private static double spawnX(Obstacle o) {
        return SPAWN_X.get(o);
    }

    @Test
    void stepsLandAtLastXPlusDxInOrderAndLoop() {
        PatternSpec mixed = mixed();
        Run run = forced(3, mixed);
        List<Obstacle> spawned = spawnedBy(run, 8);
        assertEquals(8, spawned.size(), "eight columns spawn within the budget: " + spawned);
        assertEquals(Playfield.WIDTH, spawnX(spawned.get(0)), EPS, "the opening column at 420");
        assertEquals(ObstacleKind.PIPE_GATE, spawned.get(0).kind());
        List<PatternSpec.Step> steps = mixed.steps();
        for (int i = 1; i < spawned.size(); i++) {
            PatternSpec.Step step = steps.get(i % steps.size());
            assertEquals(step.kind(), spawned.get(i).kind(), "column " + i);
            assertEquals(step.dx(), SPACING.get(spawned.get(i)), 1e-6,
                    "column " + i + " lands dx after the previous column");
        }
        PatternStreamer streamer = run.simulation().spawner().streamer();
        assertNotNull(streamer);
        assertTrue(streamer.isForced());
        assertEquals(8, streamer.stepsStreamed());
        assertEquals(2, streamer.patternsStarted(), "the forced pattern loops");
    }

    @Test
    void everyKindsParamsAreHonoured() {
        Run run = forced(5, mixed());
        List<Obstacle> spawned = spawnedBy(run, 5);
        PipeGate gate = (PipeGate) spawned.get(0);
        assertEquals(PipeGate.Layout.STANDARD, gate.layout());
        assertEquals(128, gate.gap(), EPS);
        assertEquals(Math.round(0.45 * Playfield.GROUND_Y - 64), gate.baseGapTopY(), EPS);
        Gear gear = (Gear) spawned.get(1);
        assertEquals(0.3 * Playfield.GROUND_Y, gear.cy(), 1e-9);
        assertEquals(36, gear.radius(), EPS);
        assertEquals(60, gear.railAmplitude(), EPS);
        assertEquals(40, gear.railSpeed(), EPS);
        assertFalse(gear.isScoring(), "the step said scoring: false");
        Piston piston = (Piston) spawned.get(2);
        assertEquals(Side.BOTTOM, piston.side());
        assertEquals(220, piston.length(), EPS);
        assertEquals(40, piston.telegraphTicks());
        assertEquals(Piston.DEFAULT_HOLD_TICKS, piston.holdTicks(), "defaults fill the rest");
        WindZone zone = (WindZone) spawned.get(3);
        assertEquals(120, zone.width(), EPS);
        assertEquals(300, zone.height(), EPS);
        assertEquals(-400, zone.accelY(), EPS);
        assertEquals(20, zone.scrollDelta(), EPS);
        assertFalse(zone.isScoring(), "a wind zone never scores, whatever the step says");
        LightningStrike bolt = (LightningStrike) spawned.get(4);
        assertEquals(Side.TOP, bolt.side());
        assertEquals(0.6, bolt.lengthFrac(), EPS);
        assertEquals(50, bolt.warningTicks());
        assertEquals(8, bolt.strikeTicks());
        assertTrue(bolt.isScoring());
    }

    @Test
    void aRandomGapCentreIsRolledFromTheObstacleStream() {
        PatternSpec random = pattern("random", 0, 0, true, gate(160, "random", 128));
        Run a = forced(77, random);
        Run b = forced(77, random);
        PipeGate first = (PipeGate) spawnedBy(a, 1).get(0);
        PipeGate again = (PipeGate) spawnedBy(b, 1).get(0);
        assertEquals(first.baseGapTopY(), again.baseGapTopY(), EPS, "same seed, same roll");
        Random obstacle = new RandomProvider(77).stream(RandomProvider.OBSTACLE);
        int expected = SpawnTable.STANDARD_TOP_MIN
                + obstacle.nextInt(SpawnTable.STANDARD_TOP_MAX - SpawnTable.STANDARD_TOP_MIN + 1);
        assertEquals(expected, first.baseGapTopY(), EPS,
                "the first draw of the obstacle stream, exactly as a table gate would roll it");
        assertTrue(spawnedBy(forced(78, random), 1).get(0) instanceof PipeGate other
                && other.baseGapTopY() != first.baseGapTopY(), "another seed rolls another top");
    }

    @Test
    void aWorldPatternStartsFromThePatternsStreamOnceItsMinGateIsReached() {
        PatternSpec eager = pattern("eager", 100_000, 5, true, gate(160, 0.5, 128),
                gate(160, 0.5, 128));
        WorldSpec world = WorldSpec.GREEN_FIELDS.withPatterns(List.of(eager));
        Run run = new Run(RunConfig.classic(21), RunSetup.CLASSIC.withWorld(world),
                new FixedSpawnTable());
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 21);
        int firstPatternGate = -1;
        while (!run.isFinished() && run.tick() < 6000) {
            run.tick(bot.decide(run));
            PatternStreamer.Placement placement = run.simulation().spawner().lastPlacement();
            if (placement != null && firstPatternGate < 0) {
                firstPatternGate = run.stats().gatesPassed();
            }
            if (run.stats().gatesPassed() < 5) {
                assertNull(placement, "no pattern before minGate; gates "
                        + run.stats().gatesPassed());
            }
        }
        assertTrue(firstPatternGate >= 5, "a pattern started once minGate was reached: "
                + firstPatternGate);
        PatternStreamer streamer = run.simulation().spawner().streamer();
        assertNotNull(streamer);
        assertTrue(streamer.patternsStarted() >= 2, "with a weight that dwarfs the plain share"
                + " the pattern keeps starting: " + streamer.patternsStarted());
    }

    @Test
    void theSelectionIsWeightedAgainstThePlainShare() {
        // P(start) = w / (w + 100): with w = 100 a pattern starts at about half of the free
        // spawns; the pool has a two-step pattern, so about a third of all spawns are steps.
        PatternSpec half = pattern("half", 100, 0, true, gate(160, 0.5, 128), gate(160, 0.5, 128));
        PatternStreamer streamer = new PatternStreamer(List.of(half), null,
                new RandomProvider(5).stream(RandomProvider.PATTERNS));
        assertNull(streamer.next(true, 0), "the opening spawn is always plain");
        int steps = 0;
        int spawns = 4000;
        for (int i = 0; i < spawns; i++) {
            if (streamer.next(false, 20) != null) {
                steps++;
            }
        }
        // A start costs the two steps plus one plain cooldown spawn: 3 spawns per start, and
        // a start happens with probability ½ at each free spawn, so steps ≈ 2 / (2 + 2) = ½.
        double share = steps / (double) spawns;
        assertTrue(share > 0.42 && share < 0.58, "steps share " + share);
        assertEquals(steps / 2, streamer.patternsStarted());
    }

    @Test
    void greenFieldsHasNoStreamerAndNothingChanges() {
        Run run = Run.classic(RunConfig.classic(42));
        assertNull(run.simulation().spawner().streamer(),
                "a world with no patterns never touches the patterns stream");
        assertFalse(run.setup().world().hasWorldEffects());
    }

    @Test
    void scoringFlagsAreHonouredAndNonScoringColumnsGetNoCoins() {
        PatternSpec quiet = pattern("quiet", 0, 0, false, gate(160, 0.5, 128), gate(160, 0.5, 128));
        Run run = forced(9, quiet);
        for (Obstacle o : spawnedBy(run, 4)) {
            assertFalse(o.isScoring(), "scoringSteps false vetoes every step");
        }
        assertEquals(0, run.simulation().pickups().spawnedCount(),
                "no coin trail is laid through a column that does not score (E2)");

        PatternSpec mixedScoring = pattern("mixed", 0, 0, true,
                gate(160, 0.5, 128),
                step(160, ObstacleKind.PIPE_GATE, Map.of("gapCenter", 0.5, "gapSize", 128),
                        false));
        List<Obstacle> spawned = spawnedBy(forced(9, mixedScoring), 4);
        assertTrue(spawned.get(0).isScoring());
        assertFalse(spawned.get(1).isScoring());
        assertTrue(spawned.get(2).isScoring());
        assertFalse(spawned.get(3).isScoring());
    }

    @Test
    void aNonScoringColumnNeverAdvancesTheGateCount() {
        PatternSpec quiet = pattern("quiet", 0, 0, false, gate(160, 0.5, 128));
        Run run = forced(11, quiet);
        HeadlessRunner.run(run, new BotPilot(BotPilot.Preset.PERFECT, 11), 1500);
        assertEquals(0, run.stats().gatesPassed());
        assertTrue(run.simulation().spawner().spawnCount() > 5, "columns were streamed");
    }

    @Test
    void theDecisionHashFoldsEveryStepParameter() {
        PatternSpec a = pattern("a", 0, 0, true, gate(160, 0.5, 128));
        PatternSpec b = pattern("b", 0, 0, true, gate(160, 0.5, 132));
        Run runA = forced(4, a);
        Run runB = forced(4, b);
        spawnedBy(runA, 1);
        spawnedBy(runB, 1);
        assertNotEquals(runA.simulation().spawner().decisionHash(),
                runB.simulation().spawner().decisionHash(),
                "a 4 px gap difference is a different decision (E32.d)");
        Run runA2 = forced(4, a);
        spawnedBy(runA2, 1);
        assertEquals(runA.simulation().spawner().decisionHash(),
                runA2.simulation().spawner().decisionHash());
    }

    /**
     * Two runs with identical obstacles that differ in nothing but the streamer's state hash
     * differently: the only thing that changed is the step index and the count, so the fold of
     * {@code PatternStreamer.hashState} is what tells them apart (D12).
     */
    @Test
    void theStreamerStateIsPartOfTheStateHash() {
        PatternSpec a = pattern("a", 0, 0, true, gate(160, 0.5, 128), gate(160, 0.5, 128));
        Run one = forced(4, a);
        Run other = forced(4, a);
        one.tick(RunInput.FLAP);
        other.tick(RunInput.FLAP);
        assertEquals(one.simulation().stateHash(), other.simulation().stateHash());
        // Advance the streamer of one run without spawning: the obstacles stay identical.
        other.simulation().spawner().streamer().next(false, 0);
        assertEquals(one.simulation().obstacles().hashState(1), other.simulation().obstacles()
                .hashState(1), "the layers are still the same");
        assertNotEquals(one.simulation().stateHash(), other.simulation().stateHash(),
                "the streamer's own state is folded");
    }

    /**
     * An authored {@code gapSize} is a base value the run scales by its gap multiplier (M7):
     * the nightmare tier's ×0.8 makes a 128 px pattern gate 102.4 px wide, centred where it was
     * authored, which is what the validator's {@code gapSize × 0.8 × 0.9 ≥ 54.5} describes.
     */
    @Test
    void theAuthoredGapScalesWithTheTierAndStaysCentred() {
        io.github.michelbr84.flapforge.content.RunFactory factory =
                new io.github.michelbr84.flapforge.content.RunFactory(
                        io.github.michelbr84.flapforge.content.GameContent.load());
        PatternSpec spec = pattern("centred", 0, 0, true, gate(160, 0.5, 128));
        for (String tier : List.of("normal", "hard", "nightmare")) {
            RunConfig config = RunConfig.builder(4).tierId(tier).build();
            Run run = new Run(config, factory.setup(config).withForcedPattern(spec));
            run.tick(RunInput.FLAP);
            PipeGate gate = (PipeGate) run.simulation().obstacles().last();
            double multiplier = run.simulation().stats().resolve(
                    io.github.michelbr84.flapforge.gameplay.stats.StatId.GAP_SIZE) / 128;
            double expectedMultiplier = "nightmare".equals(tier) ? 0.85
                    : "hard".equals(tier) ? 0.92 : 1.0;
            assertEquals(expectedMultiplier, multiplier, EPS, tier);
            assertEquals(128 * expectedMultiplier, gate.gap(), EPS, tier + ": gapSize × tier");
            assertEquals(0.5 * Playfield.GROUND_Y, gate.gapCenterY(), 1.0,
                    tier + ": the gap stays centred where it was authored");
        }
    }

    /**
     * E17: {@code test_flat_corridor.json} is the pattern-shaped twin of {@code FixedSpawnTable}
     * — standard gates at one height, 160 px apart. Streamed as a forced pattern it puts every
     * gate exactly where the fixed table puts it, gap for gap, tick for tick.
     */
    @Test
    void theFlatCorridorFixtureMatchesTheFixedSpawnTable() {
        PatternSpec corridor = ContentAdapters.toSpec(flatCorridor());
        assertEquals(4, corridor.steps().size());
        assertTrue(corridor.scoringSteps());
        Run streamed = forced(13, corridor);
        Run fixed = new Run(RunConfig.classic(13), RunSetup.CLASSIC, new FixedSpawnTable());
        BotPilot botA = new BotPilot(BotPilot.Preset.PERFECT, 13);
        BotPilot botB = new BotPilot(BotPilot.Preset.PERFECT, 13);
        streamed.tick(RunInput.FLAP);
        fixed.tick(RunInput.FLAP);
        for (int t = 0; t < 900; t++) {
            List<Obstacle> a = streamed.simulation().obstacles().obstacles();
            List<Obstacle> b = fixed.simulation().obstacles().obstacles();
            assertEquals(b.size(), a.size(), "tick " + t);
            for (int i = 0; i < a.size(); i++) {
                PipeGate ga = (PipeGate) a.get(i);
                PipeGate gb = (PipeGate) b.get(i);
                assertEquals(gb.x(), ga.x(), EPS, "tick " + t + " gate " + i);
                assertEquals(gb.baseGapTopY(), ga.baseGapTopY(), EPS, "tick " + t + " gate " + i);
                assertEquals(gb.gap(), ga.gap(), EPS);
                assertEquals(gb.layout(), ga.layout());
                assertFalse(ga.isMoving());
            }
            assertEquals(fixed.simulation().bird().y(), streamed.simulation().bird().y(), EPS);
            RunInput input = botA.decide(streamed);
            assertEquals(input, botB.decide(fixed), "tick " + t + ": same world, same decision");
            streamed.tick(input);
            fixed.tick(input);
            assertEquals(fixed.phase(), streamed.phase());
        }
        assertEquals(fixed.stats().gatesPassed(), streamed.stats().gatesPassed());
        assertTrue(streamed.stats().gatesPassed() >= 4, "the corridor was flown: "
                + streamed.stats().gatesPassed());
    }

    static PatternDef flatCorridor() {
        String resource = "/fixtures/test_flat_corridor.json";
        try (InputStream in = PatternStreamerTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + resource);
            }
            JsonElement root = ContentLoader.parse("test_flat_corridor",
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
            StrictBinder binder = new StrictBinder("test_flat_corridor.json");
            PatternDef def = binder.bind(PatternDef.class, root);
            assertEquals(List.of(), binder.errors());
            return def;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
    }

    /**
     * M8: the boss phases stream in order, loop, take precedence over a forced pattern and the
     * world's own patterns, and hand the run back to them when the fight ends.
     */
    @Test
    void bossPhasesStreamInOrderLoopAndYieldToTheForcedPatternAfterwards() {
        PatternSpec p1 = pattern("p1", 0, 0, true, gate(160, 0.4, 128), gate(160, 0.6, 128));
        PatternSpec p2 = pattern("p2", 0, 0, true, gate(150, 0.5, 120), gate(150, 0.5, 120),
                gate(150, 0.5, 120));
        PatternSpec forced = mixed();
        PatternStreamer streamer = new PatternStreamer(List.of(), forced, List.of(p1, p2),
                new Random(1));
        assertTrue(streamer.hasWork());
        assertFalse(streamer.isBossActive());
        assertEquals(forced, streamer.next(true, 0).pattern(), "the forced pattern first");
        assertEquals(forced, streamer.next(false, 0).pattern());

        streamer.startBoss();
        assertTrue(streamer.isBossActive());
        assertEquals(p1, streamer.active());
        List<String> order = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            PatternStreamer.Placement placement = streamer.next(false, 5);
            order.add(placement.pattern().id());
            indices.add(placement.index());
        }
        assertEquals(List.of("p1", "p1", "p2", "p2", "p2", "p1", "p1", "p2", "p2", "p2", "p1",
                "p1"), order, "in order and looped");
        assertEquals(List.of(0, 1, 0, 1, 2, 0, 1, 0, 1, 2, 0, 1), indices);
        assertEquals(5, streamer.bossPhasesStarted(), "p1, p2, p1, p2, p1");
        assertEquals(1, streamer.bossPhase(), "the next step comes from p2");

        streamer.endBoss();
        assertFalse(streamer.isBossActive());
        PatternStreamer.Placement back = streamer.next(false, 5);
        assertEquals(forced, back.pattern(), "the forced pattern resumes");
        assertEquals(0, back.index(), "from its first step");

        // A streamer with only boss phases has work, and a streamer without them refuses to start.
        assertTrue(new PatternStreamer(List.of(), null, List.of(p1), new Random(2)).hasWork());
        assertThrows(IllegalStateException.class,
                () -> new PatternStreamer(List.of(), null, new Random(3)).startBoss());
    }

    @Test
    void bossPhasesTakePrecedenceOverTheWorldsPatternsAndFoldIntoTheHash() {
        PatternSpec world = pattern("world", 40, 0, true, gate(160, 0.5, 128));
        PatternSpec p1 = pattern("p1", 0, 0, true, gate(160, 0.4, 128));
        PatternStreamer streamer = new PatternStreamer(List.of(world), null, List.of(p1),
                new Random(4));
        long idle = streamer.hashState(0);
        streamer.startBoss();
        assertNotEquals(idle, streamer.hashState(0), "the boss cursor is part of the hash");
        for (int i = 0; i < 5; i++) {
            assertEquals(p1, streamer.next(false, 20).pattern(), "never the world's pattern");
        }
        streamer.endBoss();
        assertNull(streamer.next(false, 20), "one plain spawn before the next chunk (cooldown)");
        PatternStreamer plain = new PatternStreamer(List.of(world), null, new Random(4));
        assertEquals(plain.hashState(0), new PatternStreamer(List.of(world), null, List.of(),
                new Random(4)).hashState(0), "no phases, no fold: the M7 hash stands");
    }

    @Test
    void aStepIsTypedThroughTheParamContract() {
        PatternSpec.Step step = mixed().steps().get(1);
        assertTrue(step.params() instanceof KindParams.GearSpec);
        assertEquals(130, step.dx(), EPS);
        assertFalse(step.scoring());
    }
}
