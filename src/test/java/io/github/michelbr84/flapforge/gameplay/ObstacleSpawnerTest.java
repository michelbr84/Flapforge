package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleSpawner;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnDecision;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ObstacleSpawnerTest {

    private static final double TOLERANCE = 0.03;

    private static SimContext context(StatSheet stats, RuleSet rules) {
        return new SimContext(1, 1.0, stats, rules, new RandomProvider(1), Bird.classic());
    }

    @Test
    void firstGateSpawnsAt420OnTheFirstFlyingTick() {
        Run run = Run.classic(RunConfig.classic(5));
        run.tick(RunInput.FLAP);
        assertEquals(1, run.simulation().obstacles().size());
        assertEquals(Playfield.WIDTH, run.simulation().obstacles().last().x(), 0.0);
        assertEquals(ClassicReference.FIRST_X, run.simulation().obstacles().last().x(), 0.0);
    }

    @Test
    void theFirstGateOfARunIsAlwaysStaticAndStandardLikeUpstream() {
        for (long seed = 1; seed <= 300; seed++) {
            Run run = Run.classic(RunConfig.classic(seed));
            run.tick(RunInput.FLAP);
            PipeGate gate = (PipeGate) run.simulation().obstacles().last();
            assertEquals(PipeGate.Layout.STANDARD, gate.layout(),
                    "seed " + seed + ": upstream's empty-container branch is always normal");
            assertFalse(gate.isMoving(),
                    "seed " + seed + ": upstream rolls no moving probability for the first pair");
            double top = gate.baseGapTopY();
            assertTrue(top >= SpawnTable.STANDARD_TOP_MIN && top <= SpawnTable.STANDARD_TOP_MAX,
                    "seed " + seed + ": top " + top);
        }
    }

    @Test
    void theFirstGateDrawsFromTheObstacleStreamOnlySoTheSpawnStreamStaysAligned() {
        ObstacleLayer layer = new ObstacleLayer();
        ObstacleSpawner spawner = new ObstacleSpawner(layer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(2024));
        SimContext ctx = context(StatSheet.defaults(), RuleSet.EMPTY);
        PipeGate first = (PipeGate) spawner.update(ctx);

        Random obstacle = new RandomProvider(2024).stream(RandomProvider.OBSTACLE);
        Random spawn = new RandomProvider(2024).stream(RandomProvider.SPAWN);
        int expectedTop = SpawnTable.STANDARD_TOP_MIN
                + obstacle.nextInt(SpawnTable.STANDARD_TOP_MAX - SpawnTable.STANDARD_TOP_MIN + 1);
        assertEquals(expectedTop, first.baseGapTopY(), 0.0,
                "the first top comes from the obstacle stream, exactly as upstream");

        // The spawn stream was not touched, so the second gate is its very first roll.
        layer.last().setX(200);
        SpawnDecision second = SpawnTable.GREEN_FIELDS.roll(spawn, obstacle,
                StatSheet.defaults().resolve(StatId.MOVING_CHANCE));
        PipeGate gate = (PipeGate) spawner.update(ctx);
        assertEquals(second.layout(), gate.layout());
        assertEquals(second.moving(), gate.isMoving());
        assertEquals(second.layout() == PipeGate.Layout.STANDARD ? second.top()
                : second.floatY() + second.floatH(), gate.baseGapTopY(), 0.0);
    }

    @Test
    void allObstaclesMoveAlsoMovesTheOpeningGate() {
        ObstacleLayer layer = new ObstacleLayer();
        ObstacleSpawner spawner = new ObstacleSpawner(layer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(8));
        SimContext ctx = context(StatSheet.defaults(), RuleSet.of(RuleFlag.ALL_OBSTACLES_MOVE));
        PipeGate first = (PipeGate) spawner.update(ctx);
        assertTrue(first.isMoving(), "the rule flag applies to the first gate too");
        assertEquals(PipeGate.Layout.STANDARD, first.layout(), "still upstream's opening layout");
    }

    @Test
    void cursorRuleSpacesGatesBy160() {
        ObstacleLayer layer = new ObstacleLayer();
        ObstacleSpawner spawner = new ObstacleSpawner(layer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(5));
        SimContext ctx = context(StatSheet.defaults(), RuleSet.EMPTY);
        List<Double> spawnXs = new ArrayList<>();
        List<Double> lastXAtSpawn = new ArrayList<>();
        for (int t = 0; t < 400; t++) {
            layer.update(ctx);
            Obstacle last = layer.last();
            double lastX = last == null ? Double.NaN : last.x();
            Obstacle spawned = spawner.update(ctx);
            if (spawned != null) {
                spawnXs.add(spawned.x());
                lastXAtSpawn.add(lastX);
            } else if (last != null) {
                assertFalse(ClassicReference.shouldSpawnNext((int) Math.round(lastX)),
                        "tick " + t + ": upstream would have spawned");
            }
        }
        assertTrue(spawnXs.size() >= 4, "spawned " + spawnXs.size());
        assertEquals(420.0, spawnXs.get(0), 0.0);
        for (int i = 1; i < spawnXs.size(); i++) {
            int lastX = (int) Math.round(lastXAtSpawn.get(i));
            assertTrue(ClassicReference.shouldSpawnNext(lastX));
            assertEquals(ClassicReference.nextX(lastX), spawnXs.get(i), 0.0, "spawn " + i);
            assertEquals(378.0, lastXAtSpawn.get(i), 0.0,
                    "the previous gate has just become fully visible (378 + 40 < 420)");
            assertEquals(160.0, spawnXs.get(i) - lastXAtSpawn.get(i), 0.0);
        }
    }

    @Test
    void noSpawnWhileTheLastGateIsNotFullyVisible() {
        ObstacleLayer layer = new ObstacleLayer();
        ObstacleSpawner spawner = new ObstacleSpawner(layer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(9));
        SimContext ctx = context(StatSheet.defaults(), RuleSet.EMPTY);
        assertNotNull(spawner.update(ctx));
        layer.last().setX(380);
        assertNull(spawner.update(ctx), "380 + 40 < 420 is false");
        layer.last().setX(379.5);
        Obstacle next = spawner.update(ctx);
        assertNotNull(next);
        assertEquals(379.5 + 160, next.x(), 0.0);
    }

    @Test
    void frequenciesMatchUpstreamAtHalfMovingChance() {
        Random spawn = new RandomProvider(2024).stream(RandomProvider.SPAWN);
        Random obstacle = new RandomProvider(2024).stream(RandomProvider.OBSTACLE);
        int n = 5000;
        int moving = 0;
        int movingFloating = 0;
        int staticStandard = 0;
        for (int i = 0; i < n; i++) {
            SpawnDecision d = SpawnTable.GREEN_FIELDS.roll(spawn, obstacle, 0.5);
            if (d.moving()) {
                moving++;
                if (d.layout() == PipeGate.Layout.FLOATING) {
                    movingFloating++;
                }
            } else if (d.layout() == PipeGate.Layout.STANDARD) {
                staticStandard++;
            }
        }
        int stationary = n - moving;
        assertEquals(0.5, moving / (double) n, TOLERANCE, "P(moving)");
        assertEquals(ClassicReference.MOVING_HOVER_SHARE, movingFloating / (double) moving,
                TOLERANCE, "moving → ¼ floating");
        assertEquals(ClassicReference.STATIC_NORMAL_SHARE, staticStandard / (double) stationary,
                TOLERANCE, "static → ½ standard");
    }

    @Test
    void movingShareFollowsTheStat() {
        for (double chance : new double[] {0.05, 0.3, 0.8, 1.0}) {
            Random spawn = new RandomProvider(77).stream(RandomProvider.SPAWN);
            Random obstacle = new RandomProvider(77).stream(RandomProvider.OBSTACLE);
            int moving = 0;
            int n = 5000;
            for (int i = 0; i < n; i++) {
                if (SpawnTable.GREEN_FIELDS.roll(spawn, obstacle, chance).moving()) {
                    moving++;
                }
            }
            assertEquals(chance, moving / (double) n, TOLERANCE, "chance " + chance);
        }
    }

    @Test
    void classicCurveReproducesUpstreamMovingProbability() {
        for (int gates = 0; gates <= 30; gates++) {
            double expected = ClassicReference.movingProbability(gates);
            assertEquals(expected, CurveSpec.CLASSIC.entries().get(0).valueAt(gates), 1e-12,
                    "gates " + gates);
        }
    }

    @Test
    void allObstaclesMoveForcesOscillation() {
        Random spawn = new Random(1);
        Random obstacle = new Random(2);
        for (int i = 0; i < 200; i++) {
            // The decision records the roll (static at chance 0); the rule applies when the
            // decision becomes an obstacle (E32.d).
            SpawnDecision d = SpawnTable.GREEN_FIELDS.roll(spawn, obstacle, 0.0);
            assertFalse(d.moving(), "a decision never carries the rule");
            assertTrue(((PipeGate) SpawnTable.GREEN_FIELDS.materialize(d, 420, 128, true))
                    .isMoving());
            assertFalse(((PipeGate) SpawnTable.GREEN_FIELDS.materialize(d, 420, 128, false))
                    .isMoving());
        }
        ObstacleLayer layer = new ObstacleLayer();
        ObstacleSpawner spawner = new ObstacleSpawner(layer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(3));
        SimContext ctx = context(StatSheet.defaults(), RuleSet.of(RuleFlag.ALL_OBSTACLES_MOVE));
        PipeGate gate = (PipeGate) spawner.update(ctx);
        assertTrue(gate.isMoving());
    }

    /**
     * E32.d: the same seed draws the same decisions whether or not {@code ALL_OBSTACLES_MOVE}
     * is on — the rule is applied at materialisation, so a spawner under the rule and one
     * without it agree on every decision hash and leave both streams at the same position.
     */
    @Test
    void theRuleNeverTouchesTheDecisionOrTheStreams() {
        ObstacleLayer forcedLayer = new ObstacleLayer();
        ObstacleLayer freeLayer = new ObstacleLayer();
        ObstacleSpawner forced = new ObstacleSpawner(forcedLayer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(5));
        ObstacleSpawner free = new ObstacleSpawner(freeLayer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(5));
        SimContext ruled = context(StatSheet.defaults(), RuleSet.of(RuleFlag.ALL_OBSTACLES_MOVE));
        SimContext plain = context(StatSheet.defaults(), RuleSet.EMPTY);
        int movingUnderRule = 0;
        int movingFree = 0;
        for (int i = 0; i < 100; i++) {
            forcedLayer.clear();
            freeLayer.clear();
            PipeGate a = (PipeGate) forced.update(ruled);
            PipeGate b = (PipeGate) free.update(plain);
            assertTrue(a.isMoving(), "spawn " + i + " moves under the rule");
            movingUnderRule += a.isMoving() ? 1 : 0;
            movingFree += b.isMoving() ? 1 : 0;
            assertEquals(b.layout(), a.layout(), "spawn " + i);
            assertEquals(b.baseGapTopY(), a.baseGapTopY(), 0.0, "spawn " + i);
        }
        assertEquals(free.decisionHashes(), forced.decisionHashes(),
                "the rule is not part of any decision (E32.d)");
        assertEquals(100, movingUnderRule);
        assertTrue(movingFree < 100, "without the rule the roll decides: " + movingFree);
    }

    /**
     * M7 fairness: the cursor measures the interval from where a pipe body's right edge would
     * be, so a wide column pushes the next one out by its extra width and a 24 px bolt is never
     * pulled closer than a gate would be. For a 40 px gate that is upstream's rule to the pixel.
     */
    @Test
    void wideKindsPushTheNextColumnOutByTheirExtraWidth() {
        for (ObstacleKind kind : ObstacleKind.values()) {
            ObstacleLayer layer = new ObstacleLayer();
            ObstacleSpawner spawner = new ObstacleSpawner(layer, new SpawnTable(Map.of(kind, 100)),
                    new RandomProvider(31));
            SimContext ctx = context(StatSheet.defaults(), RuleSet.EMPTY);
            spawner.update(ctx);
            for (int i = 0; i < 40; i++) {
                Obstacle last = layer.last();
                last.setX(300);
                Obstacle next = spawner.update(ctx);
                double extra = Math.max(0, last.width() - Playfield.PIPE_BODY_W);
                assertEquals(300 + extra + Playfield.GATE_INTERVAL, next.x(), 0.0,
                        kind + " after a " + last.width() + " px column");
                assertTrue(next.x() - (last.x() + last.width()) >= Playfield.GATE_INTERVAL
                        - Playfield.PIPE_BODY_W, kind + ": at least 120 px of clear air");
            }
        }
    }

    /**
     * M7: a breather's deferral is also an absolute clearance behind the last column, so a wide
     * column or a pattern step never leaves the draft without its window.
     */
    @Test
    void theDeferralGuaranteesClearAirBehindAWideColumn() {
        ObstacleLayer layer = new ObstacleLayer();
        ObstacleSpawner spawner = new ObstacleSpawner(layer,
                new SpawnTable(Map.of(ObstacleKind.GEAR, 100)), new RandomProvider(4));
        SimContext ctx = context(StatSheet.defaults(), RuleSet.EMPTY);
        spawner.update(ctx);
        layer.last().setX(300);
        spawner.update(ctx);
        Obstacle gear = layer.last();
        gear.setX(300);
        spawner.deferNextSpawn(1.5, 352);
        assertEquals(1.5, spawner.deferredIntervals(), 0.0);
        assertEquals(352, spawner.deferredClearancePx(), 0.0);
        Obstacle next = spawner.update(ctx);
        double natural = 300 + (gear.width() - Playfield.PIPE_BODY_W) + 160 * 2.5;
        assertEquals(Math.max(natural, 300 + gear.width() + 352), next.x(), 0.0);
        assertTrue(next.x() - (gear.x() + gear.width()) >= 352, "the window exists");
        assertEquals(0, spawner.deferredClearancePx(), 0.0, "consumed by the spawn");
        // A plain gate world is untouched by the floor: the D11 push already clears it.
        ObstacleLayer gates = new ObstacleLayer();
        ObstacleSpawner classic = new ObstacleSpawner(gates, SpawnTable.GREEN_FIELDS,
                new RandomProvider(4));
        classic.update(ctx);
        gates.last().setX(300);
        classic.deferNextSpawn(1.5, 352);
        assertEquals(300 + 160 * 2.5, classic.update(ctx).x(), 0.0);
    }

    @Test
    void suppressionAndDeferralHooks() {
        ObstacleLayer layer = new ObstacleLayer();
        ObstacleSpawner spawner = new ObstacleSpawner(layer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(4));
        SimContext ctx = context(StatSheet.defaults(), RuleSet.EMPTY);
        spawner.setSuppressed(true);
        assertNull(spawner.update(ctx));
        assertTrue(layer.isEmpty());
        spawner.setSuppressed(false);
        assertNotNull(spawner.update(ctx));
        layer.last().setX(300);
        spawner.deferNextSpawn(1.5);
        assertEquals(1.5, spawner.deferredIntervals(), 0.0);
        Obstacle next = spawner.update(ctx);
        assertEquals(300 + 160 * 2.5, next.x(), 0.0);
        assertEquals(0, spawner.deferredIntervals(), 0.0, "consumed by the spawn");
        assertEquals(2, spawner.spawnCount());
    }

    @Test
    void decisionHashIsSeedDeterministic() {
        long a = decisionHash(123, 50);
        long b = decisionHash(123, 50);
        long c = decisionHash(124, 50);
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    private static long decisionHash(long seed, int spawns) {
        ObstacleLayer layer = new ObstacleLayer();
        ObstacleSpawner spawner = new ObstacleSpawner(layer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(seed));
        SimContext ctx = context(StatSheet.defaults(), RuleSet.EMPTY);
        for (int i = 0; i < spawns; i++) {
            layer.clear();
            spawner.update(ctx);
        }
        return spawner.decisionHash();
    }
}
