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
                StatSheet.defaults().resolve(StatId.MOVING_CHANCE), false);
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
            SpawnDecision d = SpawnTable.GREEN_FIELDS.roll(spawn, obstacle, 0.5, false);
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
                if (SpawnTable.GREEN_FIELDS.roll(spawn, obstacle, chance, false).moving()) {
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
            assertTrue(SpawnTable.GREEN_FIELDS.roll(spawn, obstacle, 0.0, true).moving());
        }
        ObstacleLayer layer = new ObstacleLayer();
        ObstacleSpawner spawner = new ObstacleSpawner(layer, SpawnTable.GREEN_FIELDS,
                new RandomProvider(3));
        SimContext ctx = context(StatSheet.defaults(), RuleSet.of(RuleFlag.ALL_OBSTACLES_MOVE));
        PipeGate gate = (PipeGate) spawner.update(ctx);
        assertTrue(gate.isMoving());
    }

    @Test
    void forcedMovingStillConsumesTheSpawnStream() {
        List<SpawnDecision> forced = new ArrayList<>();
        List<SpawnDecision> free = new ArrayList<>();
        Random s1 = new Random(5);
        Random o1 = new Random(6);
        Random s2 = new Random(5);
        Random o2 = new Random(6);
        for (int i = 0; i < 100; i++) {
            forced.add(SpawnTable.GREEN_FIELDS.roll(s1, o1, 1.0, true));
            free.add(SpawnTable.GREEN_FIELDS.roll(s2, o2, 1.0, false));
        }
        assertEquals(free, forced, "at chance 1.0 forcing changes nothing, streams stay aligned");
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
