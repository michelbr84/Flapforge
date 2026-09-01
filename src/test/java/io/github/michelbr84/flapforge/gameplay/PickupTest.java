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
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.Oscillator;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.pickup.Coin;
import io.github.michelbr84.flapforge.gameplay.pickup.PickupLayer;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Coins in the world (D7, E2): the spawn distribution, the trail geometry and the magnet. */
class PickupTest {

    private static final int GATES = 2000;
    /** Seed of the end-to-end case; the golden run's, so the two move together. */
    private static final long RUN_SEED = 42;
    private static final double GAP = Playfield.GAP;
    private static final double TOP = 200;

    private static SimContext context(Map<StatId, Double> stats, RuleSet rules, Bird bird) {
        StatSheet sheet = new StatSheet(stats, new EffectStack(), rules);
        return new SimContext(1, 1.0, sheet, rules, new RandomProvider(7), bird);
    }

    private static SimContext contextWithRate(double rate) {
        return context(Map.of(StatId.COIN_SPAWN_RATE, rate), RuleSet.EMPTY, Bird.classic());
    }

    private static PipeGate gate() {
        return PipeGate.standard(300, TOP, GAP, null);
    }

    /** Spawns one trail per gate and returns how many coins each gate got. */
    private static int[] spawnPerGate(double rate, long seed, int gates) {
        PickupLayer layer = new PickupLayer(new RandomProvider(seed));
        SimContext ctx = contextWithRate(rate);
        Obstacle gate = gate();
        int[] counts = new int[gates];
        for (int i = 0; i < gates; i++) {
            counts[i] = layer.spawnFor(gate, ctx);
            layer.clear();
        }
        return counts;
    }

    @Test
    void rateZeroSpawnsNothing() {
        for (int count : spawnPerGate(0, 42, 100)) {
            assertEquals(0, count);
        }
    }

    /**
     * E2: {@code COIN_SPAWN_RATE} is the expected number of coins per scoring gate, so the long-run
     * average of the Bernoulli draw must land on the rate itself.
     */
    @Test
    void theDefaultRateAveragesHalfACoinPerGate() {
        int total = 0;
        for (int count : spawnPerGate(0.5, 42, GATES)) {
            assertTrue(count == 0 || count == 1, "0.5 can only ever pay 0 or 1: " + count);
            total += count;
        }
        double average = (double) total / GATES;
        assertEquals(0.5, average, 0.5 * 0.05, "average over " + GATES + " gates: " + average);
    }

    @Test
    void aFractionalRateAboveOneSpawnsFloorOrFloorPlusOne() {
        int threes = 0;
        for (int count : spawnPerGate(2.6, 1234, GATES)) {
            assertTrue(count == 2 || count == 3, "2.6 pays 2 or 3, not " + count);
            if (count == 3) {
                threes++;
            }
        }
        double frequency = (double) threes / GATES;
        assertEquals(0.6, frequency, 0.6 * 0.05, "frequency of the extra coin: " + frequency);
    }

    @Test
    void aWholeRateAlwaysSpawnsExactlyThatMany() {
        for (int count : spawnPerGate(3, 99, 200)) {
            assertEquals(3, count);
        }
    }

    @Test
    void theTrailIsCentredOnTheColumnAndSitsInsideTheGap() {
        PickupLayer layer = new PickupLayer(new RandomProvider(5));
        PipeGate gate = gate();
        assertEquals(3, layer.spawnFor(gate, contextWithRate(3)));
        List<Coin> coins = layer.coins();
        double columnCenter = gate.x() + gate.width() / 2;
        assertEquals(columnCenter - PickupLayer.TRAIL_SPACING_PX, coins.get(0).x());
        assertEquals(columnCenter, coins.get(1).x());
        assertEquals(columnCenter + PickupLayer.TRAIL_SPACING_PX, coins.get(2).x());
        for (Coin coin : coins) {
            assertEquals(gate.safeBandY(coin.x()), coin.y());
            assertEquals(gate.gapCenterY(), coin.y(), 0.0, "a gate's safe band is its gap centre");
            assertTrue(coin.y() - Coin.RADIUS > gate.gapTopY(), "coin above the gap: " + coin);
            assertTrue(coin.y() + Coin.RADIUS < gate.gapBottomY(), "coin below the gap: " + coin);
        }
    }

    /**
     * A trail through a <em>moving</em> gate has to move with it. The gate swings ±51 px
     * ({@link Oscillator#DEFAULT_AMPLITUDE}) over a 3.4 s period; a coin left at the y the band had
     * at spawn time is outside the gap for part of every swing at anything below the shipped
     * {@code normal} gap — at {@code nightmare}'s 0.8 × 128 it spends most of the period inside the
     * pipe body, which is a pickup you could only take by dying.
     */
    @Test
    void aTrailThroughAMovingGateStaysInsideTheGapForAWholePeriod() {
        double gap = GAP * 0.8;
        PipeGate gate = PipeGate.standard(300, TOP, gap, Oscillator.classic());
        PickupLayer layer = new PickupLayer(new RandomProvider(5));
        SimContext ctx = contextWithRate(3);
        assertEquals(3, layer.spawnFor(gate, ctx));

        // 3.4 s at 60 Hz: a full triangle, both extremes of the swing.
        for (int tick = 0; tick < 205; tick++) {
            gate.update(ctx);
            layer.update(ctx);
            for (Coin coin : layer.coins()) {
                assertTrue(coin.y() - Coin.RADIUS > gate.gapTopY(),
                        "tick " + tick + ": coin above the gap, " + coin + " gapTop="
                                + gate.gapTopY());
                assertTrue(coin.y() + Coin.RADIUS < gate.gapBottomY(),
                        "tick " + tick + ": coin below the gap, " + coin + " gapBottom="
                                + gate.gapBottomY());
                assertEquals(gate.gapCenterY(), coin.y(), 1e-9,
                        "the coin rides the band, it does not just stay inside it");
            }
            if (layer.isEmpty()) {
                break;
            }
        }
        assertTrue(gate.offsetY() != 0, "the gate really moved");
    }

    /** A coin the magnet has taken hold of is on its own: it must not snap back to the band. */
    @Test
    void theMagnetDetachesACoinFromItsGate() {
        Bird bird = Bird.classic();
        PipeGate gate = PipeGate.standard(bird.x(), TOP, GAP, Oscillator.classic());
        PickupLayer layer = new PickupLayer(new RandomProvider(5));
        SimContext spawnCtx = contextWithRate(1);
        layer.spawnFor(gate, spawnCtx);
        Coin coin = layer.coins().get(0);
        assertNotNull(coin.owner(), "a trail coin starts bound to its gate");

        SimContext magnet = context(Map.of(StatId.MAGNET_RADIUS, 200.0), RuleSet.EMPTY, bird);
        layer.update(magnet);

        assertNull(coin.owner(), "the pull detaches it");
        assertNotEquals(gate.gapCenterY(), coin.y(), "and it keeps the position the pull gave it");
    }

    /**
     * The wiring {@code Simulation} owns: a scoring obstacle spawns a trail, and nothing else
     * does. Without this case the whole class stays green with
     * {@code pickups.spawnFor(spawned, ctx)} deleted from {@code Simulation.tick}.
     */
    @Test
    void everyTrailOfARealRunComesFromAScoringObstacle() {
        Run run = Run.classic(RunConfig.classic(RUN_SEED));
        BotPilot pilot = new BotPilot(BotPilot.Preset.PERFECT, RUN_SEED);
        int scoringSpawns = 0;
        int before = 0;
        for (int i = 0; i < 3000 && !run.isFinished(); i++) {
            TickReport report = run.tick(pilot.decide(run));
            int after = run.simulation().pickups().spawnedCount();
            boolean spawnedObstacle = report.has(TickFact.ObstacleSpawned.class);
            if (after > before) {
                assertTrue(spawnedObstacle, "a trail appeared on a tick with no obstacle: "
                        + report.facts());
                assertTrue(after - before <= 5, "a trail is at most COIN_SPAWN_RATE's cap");
            }
            if (spawnedObstacle) {
                scoringSpawns++;
            }
            before = after;
        }

        int spawned = run.simulation().pickups().spawnedCount();
        assertTrue(scoringSpawns >= 20, "the run must be long enough to matter: " + scoringSpawns);
        // COIN_SPAWN_RATE 0.5: mean = n/2, sd = sqrt(n)/2, so 4 sd is a band that will not flake.
        double mean = scoringSpawns * 0.5;
        double band = 2 * Math.sqrt(scoringSpawns);
        assertTrue(Math.abs(spawned - mean) <= band, spawned + " coins over " + scoringSpawns
                + " gates is outside [" + (mean - band) + ", " + (mean + band) + "]");
    }

    @Test
    void anEvenTrailStraddlesTheColumnCentre() {
        PickupLayer layer = new PickupLayer(new RandomProvider(5));
        PipeGate gate = gate();
        layer.spawnFor(gate, contextWithRate(2));
        double columnCenter = gate.x() + gate.width() / 2;
        assertEquals(2, layer.size());
        assertEquals(columnCenter - PickupLayer.TRAIL_SPACING_PX / 2, layer.coins().get(0).x());
        assertEquals(columnCenter + PickupLayer.TRAIL_SPACING_PX / 2, layer.coins().get(1).x());
    }

    @Test
    void noCoinsRuleSuppressesEveryTrail() {
        PickupLayer layer = new PickupLayer(new RandomProvider(5));
        RuleSet rules = RuleSet.of(RuleFlag.NO_COINS);
        SimContext ctx = context(Map.of(StatId.COIN_SPAWN_RATE, 5.0), rules, Bird.classic());
        for (int i = 0; i < 50; i++) {
            assertEquals(0, layer.spawnFor(gate(), ctx));
        }
        assertTrue(layer.isEmpty());
        assertEquals(0, layer.spawnedCount());
    }

    @Test
    void theSameSeedLaysTheSameTrails() {
        assertEquals(Arrays.toString(spawnPerGate(0.5, 2024, 200)),
                Arrays.toString(spawnPerGate(0.5, 2024, 200)));
        assertNotEquals(Arrays.toString(spawnPerGate(0.5, 2024, 200)),
                Arrays.toString(spawnPerGate(0.5, 2025, 200)));
    }

    @Test
    void aCoinScrollsWithTheWorldAndIsDroppedOffscreen() {
        PickupLayer layer = new PickupLayer(new RandomProvider(1));
        Coin coin = new Coin(100, 300);
        layer.add(coin);
        SimContext ctx = contextWithRate(0);
        layer.update(ctx);
        assertEquals(98, coin.x(), 0.0, "120 px/s is 2 px per tick");
        assertEquals(300, coin.y(), 0.0, "no magnet, no vertical drift");
        assertEquals(100, coin.prevX(), 0.0);

        layer.add(new Coin(-Coin.RADIUS, 300));
        layer.update(ctx);
        assertEquals(1, layer.size(), "the offscreen coin is dropped");
    }

    @Test
    void theMagnetOnlyPullsInsideItsRadius() {
        Bird bird = Bird.classic();
        SimContext ctx = context(Map.of(StatId.MAGNET_RADIUS, 60.0), RuleSet.EMPTY, bird);
        PickupLayer layer = new PickupLayer(new RandomProvider(1));
        Coin far = new Coin(bird.x() + 200, bird.y() - 100);
        Coin near = new Coin(bird.x() + 30, bird.y() - 30);
        layer.add(far);
        layer.add(near);
        double farDistance = Math.hypot(far.x() - bird.x(), far.y() - bird.y());

        layer.update(ctx);

        assertEquals(bird.y() - 100, far.y(), 0.0, "outside the radius: no vertical pull");
        assertEquals(bird.x() + 198, far.x(), 0.0, "it only scrolls");
        assertTrue(Math.hypot(far.x() - bird.x(), far.y() - bird.y()) < farDistance);

        // The step is taken after the scroll, from (bird.x + 28, bird.y - 30).
        double scrolled = Math.hypot(28, 30);
        double step = Coin.MAGNET_SPEED / Playfield.TICK_RATE;
        assertEquals(scrolled - step, Math.hypot(near.x() - bird.x(), near.y() - bird.y()), 1e-9,
                "a fixed step towards the bird, not a spring");
        assertTrue(near.y() > bird.y() - 30, "inside the radius: pulled down towards the bird");
    }

    @Test
    void theMagnetNeverOvershootsTheBird() {
        Bird bird = Bird.classic();
        SimContext ctx = context(Map.of(StatId.MAGNET_RADIUS, 200.0), RuleSet.EMPTY, bird);
        PickupLayer layer = new PickupLayer(new RandomProvider(1));
        Coin coin = new Coin(bird.x() + 1, bird.y() + 1);
        layer.add(coin);
        layer.update(ctx);
        assertEquals(bird.x(), coin.x(), 1e-9);
        assertEquals(bird.y(), coin.y(), 1e-9);
    }

    @Test
    void collectingACoinCountsInTheRunStatsAndReportsAFact() {
        Run run = Run.classic(RunConfig.classic(3));
        run.simulation().spawner().setSuppressed(true);
        run.tick(RunInput.FLAP);
        Bird bird = run.simulation().bird();
        run.simulation().pickups().add(new Coin(bird.x(), bird.y()));

        TickReport report = run.tick(RunInput.NONE);

        assertTrue(report.has(TickFact.CoinCollected.class), "facts: " + report.facts());
        assertEquals(Coin.DEFAULT_VALUE,
                report.first(TickFact.CoinCollected.class).orElseThrow().value());
        assertEquals(1, run.stats().coinsCollected());
        assertEquals(1, run.simulation().coinsCollected());
        assertEquals(1, run.simulation().pickups().collectedCount());
        assertEquals(1L, run.result().counter("coins"));

        run.tick(RunInput.NONE);
        assertTrue(run.simulation().pickups().isEmpty(), "a collected coin leaves the layer");
        assertEquals(1, run.stats().coinsCollected(), "and is never collected twice");
    }

    @Test
    void aCoinOutOfReachIsNotCollected() {
        Run run = Run.classic(RunConfig.classic(3));
        run.simulation().spawner().setSuppressed(true);
        run.tick(RunInput.FLAP);
        Bird bird = run.simulation().bird();
        run.simulation().pickups().add(new Coin(bird.x(), bird.y() - 80));
        TickReport report = run.tick(RunInput.NONE);
        assertFalse(report.has(TickFact.CoinCollected.class));
        assertEquals(0, run.stats().coinsCollected());
    }

    @Test
    void coinsFreezeWithTheWorldWhileTheBirdIsDying() {
        Run run = Run.classic(RunConfig.classic(3));
        run.simulation().spawner().setSuppressed(true);
        run.tick(RunInput.FLAP);
        Coin coin = new Coin(300, 100);
        run.simulation().pickups().add(coin);
        run.simulation().beginDying();
        double x = coin.x();
        for (int i = 0; i < 20; i++) {
            run.simulation().tickDying();
        }
        assertEquals(x, coin.x(), 0.0, "the world is frozen in DYING");
        assertEquals(x, coin.prevX(), 0.0, "and interpolation shows no motion");
    }
}
