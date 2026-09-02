package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Circle;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionSystem;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.KindParams;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleSignal;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnDecision;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.support.FixedSpawnTable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The four M7 families (D6): phases, hitboxes, signals, safe bands and interpolation. */
class ObstacleFamilyTest {

    private static final double EPS = 1e-9;

    private static SimContext ctx(double worldDt) {
        return new SimContext(1, worldDt, StatSheet.defaults(), RuleSet.EMPTY,
                new RandomProvider(1), Bird.classic());
    }

    private static void advance(Obstacle o, SimContext ctx, int ticks) {
        for (int i = 0; i < ticks; i++) {
            o.update(ctx);
        }
    }

    private static Aabb only(List<Hitbox> boxes) {
        assertEquals(1, boxes.size());
        return (Aabb) boxes.get(0);
    }

    // ------------------------------------------------------------------ piston

    @Test
    void pistonPhasesCycleFromSpawnAndAdvanceByWorldDt() {
        Piston piston = Piston.standard(400, Side.TOP, 300, 0);
        assertEquals(Piston.Phase.TELEGRAPH, piston.phase());
        assertEquals(0, piston.extension(), EPS);
        assertTrue(piston.hitboxes().isEmpty(), "a retracted head is a free column");

        SimContext ctx = ctx(1.0);
        advance(piston, ctx, 39);
        assertEquals(Piston.Phase.TELEGRAPH, piston.phase());
        advance(piston, ctx, 1);
        assertEquals(Piston.Phase.EXTEND, piston.phase());
        assertEquals(0, piston.extension(), EPS);
        advance(piston, ctx, 6);
        assertEquals(150, piston.extension(), EPS, "half way through the 12 extend ticks");
        advance(piston, ctx, 6);
        assertEquals(Piston.Phase.HOLD, piston.phase());
        assertEquals(300, piston.extension(), EPS);
        advance(piston, ctx, 30);
        assertEquals(Piston.Phase.RETRACT, piston.phase());
        advance(piston, ctx, 10);
        assertEquals(150, piston.extension(), EPS, "half way back");
        advance(piston, ctx, 10);
        assertEquals(Piston.Phase.TELEGRAPH, piston.phase(), "the cycle wraps after 102 ticks");
        assertEquals(0, piston.clock(), EPS);

        Piston slow = Piston.standard(400, Side.TOP, 300, 0);
        advance(slow, ctx(0.5), 40);
        assertEquals(Piston.Phase.TELEGRAPH, slow.phase(), "TIME_SCALE 0.5 halves the clock");
        assertEquals(20, slow.clock(), EPS);
        advance(slow, ctx(0.5), 40);
        assertEquals(Piston.Phase.EXTEND, slow.phase());
    }

    @Test
    void pistonHeadSpansTheAnchoringEdgeForBothSides() {
        Piston top = Piston.standard(200, Side.TOP, 240, 40 + 6);
        assertEquals(Piston.Phase.EXTEND, top.phase());
        assertEquals(120, top.extension(), EPS);
        assertEquals(new Aabb(200, 0, Piston.WIDTH, 120), only(top.hitboxes()));

        Piston bottom = Piston.standard(200, Side.BOTTOM, 240, 40 + 12 + 5);
        assertEquals(Piston.Phase.HOLD, bottom.phase());
        assertEquals(new Aabb(200, Playfield.GROUND_Y - 240, Piston.WIDTH, 240),
                only(bottom.hitboxes()));
        assertEquals(bottom.headBox(200, 240), only(bottom.hitboxes()));
    }

    @Test
    void pistonSignalsTheTelegraphOncePerCycleAndTheSimulationTurnsItIntoAFact() {
        Piston piston = Piston.standard(400, Side.TOP, 200, 0);
        assertEquals(ObstacleSignal.PISTON_TELEGRAPH, piston.takeSignal(), "spawned telegraphing");
        assertNull(piston.takeSignal(), "taken once");
        SimContext ctx = ctx(1.0);
        for (int t = 1; t < Piston.DEFAULT_CYCLE_TICKS; t++) {
            piston.update(ctx);
            assertNull(piston.takeSignal(), "tick " + t + " is inside the first cycle");
        }
        piston.update(ctx);
        assertEquals(ObstacleSignal.PISTON_TELEGRAPH, piston.takeSignal(), "the second cycle");

        Piston midCycle = Piston.standard(400, Side.TOP, 200, 60);
        assertEquals(Piston.Phase.HOLD, midCycle.phase());
        assertNull(midCycle.takeSignal(), "a piston spawned holding did not start a telegraph");

        Run run = new Run(RunConfig.classic(3), RunSetup.CLASSIC, new FixedSpawnTable());
        run.tick(RunInput.FLAP);
        run.simulation().obstacles().add(Piston.standard(400, Side.TOP, 200, 0));
        TickReport report = run.tick(RunInput.NONE);
        assertEquals(1, report.facts().stream()
                .filter(f -> f instanceof TickFact.PistonTelegraph).count());
        TickReport next = run.tick(RunInput.NONE);
        assertEquals(0, next.facts().stream()
                .filter(f -> f instanceof TickFact.PistonTelegraph).count());
    }

    @Test
    void pistonExtensionAtClockIsThePhaseFunction() {
        Piston piston = new Piston(300, Side.BOTTOM, 200, 20, 10, 5, 10, 0);
        assertEquals(0, piston.extensionAtClock(19), EPS);
        assertEquals(0, piston.extensionAtClock(20), EPS);
        assertEquals(100, piston.extensionAtClock(25), EPS);
        assertEquals(200, piston.extensionAtClock(30), EPS);
        assertEquals(200, piston.extensionAtClock(34), EPS);
        assertEquals(100, piston.extensionAtClock(40), EPS);
        assertEquals(0, piston.extensionAtClock(45), EPS, "wraps into the next telegraph");
        assertEquals(piston.extensionAtClock(3), piston.extensionAtClock(48), EPS);
        assertEquals(piston.extensionAtClock(30), piston.extensionAtClock(-15), EPS);
    }

    // --------------------------------------------------------------- lightning

    @Test
    void lightningGoesIdleWarningStrikeSpentAgainstTheBirdColumn() {
        LightningStrike bolt = LightningStrike.standard(300, Side.TOP, 0.5);
        SimContext ctx = ctx(1.0);
        // Centre at 312, scroll 2 px/tick, warning distance 45 × 2 = 90 px.
        assertEquals(LightningStrike.State.IDLE, bolt.state());
        advance(bolt, ctx, 58);
        assertEquals(LightningStrike.State.IDLE, bolt.state(), "centre 196: 91 px away");
        assertNull(bolt.takeSignal());
        advance(bolt, ctx, 1);
        assertEquals(LightningStrike.State.WARNING, bolt.state(), "centre 194: 89 px away");
        assertEquals(ObstacleSignal.LIGHTNING_WARNING, bolt.takeSignal());
        assertNull(bolt.takeSignal(), "warned once");
        assertTrue(bolt.hitboxes().isEmpty(), "no hitbox during the warning");
        assertEquals(new Aabb(bolt.x(), 0, LightningStrike.WIDTH, 299), bolt.boltSpan(),
                "the warning shows the side and the extent");

        advance(bolt, ctx, 44);
        assertEquals(LightningStrike.State.WARNING, bolt.state(), "centre 106");
        assertTrue(bolt.hitboxes().isEmpty());
        advance(bolt, ctx, 1);
        assertEquals(LightningStrike.State.STRIKE, bolt.state(), "centre 104 ≤ BIRD_X");
        assertEquals(new Aabb(bolt.x(), 0, LightningStrike.WIDTH, 299), only(bolt.hitboxes()));
        assertNull(bolt.takeSignal(), "the strike raises nothing");
        advance(bolt, ctx, 9);
        assertEquals(LightningStrike.State.STRIKE, bolt.state(), "lethal for 10 ticks");
        advance(bolt, ctx, 1);
        assertEquals(LightningStrike.State.SPENT, bolt.state());
        assertTrue(bolt.hitboxes().isEmpty());
        assertTrue(bolt.isScoring(), "a bolt column scores like a gate");
    }

    @Test
    void lightningBoltSpansForBothSidesAndTheStrikeLastsInWorldTime() {
        LightningStrike bottom = new LightningStrike(92, Side.BOTTOM, 0.4, 30, 8);
        assertEquals(Playfield.GROUND_Y - 0.4 * Playfield.GROUND_Y, bottom.boltTopY(), EPS);
        assertEquals(Playfield.GROUND_Y, bottom.boltBottomY(), EPS);
        assertEquals((Playfield.GROUND_Y - 0.4 * Playfield.GROUND_Y) / 2, bottom.safeBandY(0), EPS);
        LightningStrike top = new LightningStrike(92, Side.TOP, 0.4, 30, 8);
        assertEquals(0, top.boltTopY(), EPS);
        assertEquals(0.4 * Playfield.GROUND_Y, top.boltBottomY(), EPS);
        assertEquals((0.4 * Playfield.GROUND_Y + Playfield.GROUND_Y) / 2, top.safeBandY(0), EPS);

        // Column centre 104 → 103 ≤ BIRD_X after one tick of 1 px (TIME_SCALE 0.5): strikes at once.
        SimContext slow = ctx(0.5);
        advance(bottom, slow, 1);
        assertEquals(LightningStrike.State.STRIKE, bottom.state());
        assertEquals(ObstacleSignal.LIGHTNING_WARNING, bottom.takeSignal(),
                "warned and struck on the same tick when it spawns inside the warning distance");
        advance(bottom, slow, 15);
        assertEquals(LightningStrike.State.STRIKE, bottom.state(), "8 ticks of world time = 16");
        advance(bottom, slow, 1);
        assertEquals(LightningStrike.State.SPENT, bottom.state());
    }

    // -------------------------------------------------------------------- gear

    @Test
    void gearHasACircleHitboxAndRidesItsRail() {
        Gear gear = Gear.onRail(300, 300, 40);
        assertEquals(80, gear.width(), EPS);
        assertEquals(new Circle(340, 270, 40), gear.hitboxes().get(0), "starts at the sweep top");
        SimContext ctx = ctx(1.0);
        advance(gear, ctx, 45);
        assertEquals(300, gear.centerY(), 1e-6, "40 px/s: 30 px in 45 ticks");
        assertEquals(new Circle(300 - 90 + 40, 300, 40), gear.hitboxes().get(0));
        advance(gear, ctx, 45);
        assertEquals(330, gear.centerY(), 1e-6, "the sweep bottom");
        advance(gear, ctx, 90);
        assertEquals(270, gear.centerY(), 1e-6, "and back to the top");
        assertEquals(0.5 * 180 / 60 % 1, gear.angle(), 1e-6, "half a turn per second");

        Gear slow = Gear.onRail(300, 300, 40);
        advance(slow, ctx(0.5), 90);
        assertEquals(300, slow.centerY(), 1e-6, "TIME_SCALE 0.5 halves the rail speed");
        assertEquals(0.5 * 45 / 60, slow.angle(), 1e-6, "and the rotation");

        Gear fixed = Gear.fixed(300, 200, 30);
        advance(fixed, ctx, 100);
        assertEquals(200, fixed.centerY(), EPS);
        assertFalse(fixed.isMoving());
        assertEquals(0, fixed.maxDisplacement() - 2, EPS, "only the scroll moves it");
    }

    @Test
    void gearPredictsItsRailPosition() {
        Gear gear = Gear.onRail(300, 300, 40);
        SimContext ctx = ctx(1.0);
        double perTick = ctx.perTick(Gear.DEFAULT_RAIL_SPEED);
        double[] predicted = new double[200];
        for (int k = 0; k < predicted.length; k++) {
            predicted[k] = gear.predictedCenterY(k, perTick);
        }
        for (int k = 0; k < predicted.length; k++) {
            assertEquals(predicted[k], gear.centerY(), 1e-6, "tick " + k);
            gear.update(ctx);
        }
    }

    // -------------------------------------------------------------------- wind

    @Test
    void windPushesTheBirdAndNeverKills() {
        WindZone updraft = new WindZone(60, 120, 320, 200, -500, 0);
        assertFalse(updraft.lethal());
        assertFalse(updraft.isScoring());
        assertEquals(320, updraft.safeBandY(0), EPS);
        Bird bird = Bird.classic();
        bird.beginTick();
        ObstacleLayer layer = new ObstacleLayer();
        layer.add(updraft);
        assertFalse(new CollisionSystem().test(bird, layer, 6).lethalHit());
        assertFalse(new CollisionSystem().test(bird, layer, 6).nearMiss());

        Run windy = new Run(RunConfig.classic(3), RunSetup.CLASSIC, new FixedSpawnTable());
        Run calm = new Run(RunConfig.classic(3), RunSetup.CLASSIC, new FixedSpawnTable());
        windy.tick(RunInput.FLAP);
        calm.tick(RunInput.FLAP);
        windy.simulation().obstacles().add(new WindZone(60, 120, 320, 200, -500, 0));
        windy.tick(RunInput.NONE);
        calm.tick(RunInput.NONE);
        Bird windyBird = windy.simulation().bird();
        Bird calmBird = calm.simulation().bird();
        assertEquals(calmBird.vy() - 500.0 / Playfield.TICK_RATE, windyBird.vy(), 1e-9,
                "the updraft joins gravity for the tick");
        assertTrue(windyBird.y() < calmBird.y());
        assertTrue(((WindZone) windy.simulation().obstacles().obstacles().get(1)).isAffecting());
    }

    @Test
    void horizontalWindChangesTheRelativeScrollOfTheWholeLayer() {
        Run run = new Run(RunConfig.classic(3), RunSetup.CLASSIC, new FixedSpawnTable());
        run.tick(RunInput.FLAP);
        Obstacle gate = run.simulation().obstacles().last();
        double before = gate.x();
        run.simulation().obstacles().add(new WindZone(60, 120, 320, 200, 0, 40));
        run.tick(RunInput.NONE);
        assertEquals(before - 160.0 / Playfield.TICK_RATE, gate.x(), 1e-9,
                "120 + 40 px/s for the tick");
        run.tick(RunInput.NONE);
        assertEquals(before - 2 * 160.0 / Playfield.TICK_RATE, gate.x(), 1e-9);

        Run still = new Run(RunConfig.classic(3), RunSetup.CLASSIC, new FixedSpawnTable());
        still.tick(RunInput.FLAP);
        Obstacle stillGate = still.simulation().obstacles().last();
        double stillBefore = stillGate.x();
        still.simulation().obstacles().add(new WindZone(300, 60, 320, 200, 0, 40));
        still.tick(RunInput.NONE);
        assertEquals(stillBefore - 2, stillGate.x(), 1e-9, "a zone ahead of the bird does nothing");
    }

    @Test
    void windIsNotSampledWhileDying() {
        Run run = new Run(RunConfig.classic(3), RunSetup.CLASSIC, new FixedSpawnTable());
        run.tick(RunInput.FLAP);
        run.simulation().obstacles().add(new WindZone(60, 120, 320, 200, -900, 0));
        run.simulation().beginDying();
        double vy = run.simulation().bird().vy();
        run.simulation().tickDying();
        assertEquals(vy + 1800.0 / Playfield.TICK_RATE, run.simulation().bird().vy(), 1e-9,
                "gravity alone in DYING");
    }

    // ------------------------------------------------------------- safe bands

    @Test
    void safeBandOfEveryKindClearsEveryLethalHitboxAtTheCrossing() {
        double x = Playfield.BIRD_X - 20;
        Bird bird = Bird.classic();

        PipeGate gate = PipeGate.standard(x, 200, 128, null);
        assertClear(bird, gate.safeBandY(Playfield.BIRD_X), gate.hitboxes());

        Gear gear = Gear.onRail(x, 300, 56);
        double band = gear.safeBandY(Playfield.BIRD_X);
        for (double cy : new double[] {gear.sweepTopY() + 56, gear.cy(), gear.sweepBottomY() - 56}) {
            assertClear(bird, band, List.of(new Circle(gear.centerX(), cy, 56)));
        }

        Piston top = Piston.standard(x, Side.TOP, 360, 0);
        assertClear(bird, top.safeBandY(Playfield.BIRD_X), List.of(top.headBox(x, 360)));
        Piston bottom = Piston.standard(x, Side.BOTTOM, 360, 0);
        assertClear(bird, bottom.safeBandY(Playfield.BIRD_X), List.of(bottom.headBox(x, 360)));

        LightningStrike topBolt = LightningStrike.standard(x, Side.TOP, 0.7);
        assertClear(bird, topBolt.safeBandY(Playfield.BIRD_X), List.of(topBolt.boltSpan()));
        LightningStrike bottomBolt = LightningStrike.standard(x, Side.BOTTOM, 0.7);
        assertClear(bird, bottomBolt.safeBandY(Playfield.BIRD_X), List.of(bottomBolt.boltSpan()));
    }

    private static void assertClear(Bird bird, double y, List<? extends Hitbox> hitboxes) {
        Aabb box = bird.hitboxAt(y, 1.0);
        for (Hitbox h : hitboxes) {
            assertFalse(h.intersects(box), h + " overlaps the bird at the safe band " + y);
        }
        assertTrue(y > 0 && y < Playfield.GROUND_DEATH_Y, "inside the playfield: " + y);
    }

    @Test
    void gearSafeBandIsTheLargerFreeSpace() {
        Gear low = Gear.fixed(100, 450, 40);
        assertTrue(low.safeBandAbove());
        assertEquals((450 - 40) / 2.0, low.safeBandY(0), EPS);
        Gear high = Gear.onRail(100, 150, 40);
        assertFalse(high.safeBandAbove());
        assertEquals((150 + 30 + 40 + Playfield.GROUND_Y) / 2.0, high.safeBandY(0), EPS);
    }

    // --------------------------------------------------- ALL_OBSTACLES_MOVE

    /**
     * D7's per-kind rule, applied where the decision becomes an obstacle (E32.d): the decision
     * itself records the roll and never the rule.
     */
    @Test
    void allObstaclesMoveAppliesPerKind() {
        Random spawn = new Random(1);
        Random obstacle = new Random(2);
        SpawnTable gears = new SpawnTable(Map.of(ObstacleKind.GEAR, 1));
        SpawnDecision gear = gears.roll(spawn, obstacle, 0);
        assertFalse(gear.moving(), "rolled static at chance 0");
        Gear railed = (Gear) gears.materialize(gear, 420, 128, true);
        assertTrue(railed.isMoving(), "gear → rail");
        assertEquals(Gear.DEFAULT_RAIL_AMPLITUDE, railed.railAmplitude(), EPS);
        assertFalse(((Gear) gears.materialize(gear, 420, 128, false)).isMoving());

        SpawnTable pistons = new SpawnTable(Map.of(ObstacleKind.PISTON, 1));
        SpawnDecision piston = pistons.roll(spawn, obstacle, 0);
        assertEquals(Piston.DEFAULT_TELEGRAPH_TICKS,
                ((KindParams.PistonSpec) piston.params()).telegraphTicks(), "the decision: 40");
        assertEquals(Piston.FORCED_TELEGRAPH_TICKS,
                ((Piston) pistons.materialize(piston, 420, 128, true)).telegraphTicks(),
                "piston → telegraph 25");

        SpawnTable bolts = new SpawnTable(Map.of(ObstacleKind.LIGHTNING, 1));
        SpawnDecision bolt = bolts.roll(spawn, new Random(7), 0);
        LightningStrike free = (LightningStrike) bolts.materialize(bolt, 420, 128, false);
        LightningStrike ruled = (LightningStrike) bolts.materialize(bolt, 420, 128, true);
        assertEquals(free.side(), ruled.side(), "lightning → unchanged");
        assertEquals(free.lengthFrac(), ruled.lengthFrac(), EPS);
        assertEquals(free.warningTicks(), ruled.warningTicks());

        SpawnDecision gate = SpawnTable.GREEN_FIELDS.roll(spawn, obstacle, 0);
        assertFalse(gate.moving(), "rolled static");
        assertTrue(((PipeGate) SpawnTable.GREEN_FIELDS.materialize(gate, 420, 128, true))
                .isMoving(), "gate → oscillate");

        KindParams.PistonSpec authored = new KindParams.PistonSpec(Side.TOP, 200, 40, 12, 30, 20,
                0);
        assertEquals(40, ((KindParams.PistonSpec) SpawnTable.decisionFor(authored, spawn).params())
                .telegraphTicks(), "the decision keeps the authored telegraph");
        assertEquals(25, ((Piston) pistons.materialize(SpawnTable.decisionFor(authored, spawn),
                420, 128, true)).telegraphTicks());
        KindParams.PistonSpec quick = new KindParams.PistonSpec(Side.TOP, 200, 18, 12, 30, 20, 0);
        assertEquals(18, ((Piston) pistons.materialize(SpawnTable.decisionFor(quick, spawn), 420,
                128, true)).telegraphTicks(), "an authored shorter telegraph is kept");

        Run run = new Run(RunConfig.builder(5).rules(RuleSet.of(RuleFlag.ALL_OBSTACLES_MOVE))
                .build(), RunSetup.CLASSIC, new SpawnTable(Map.of(ObstacleKind.GEAR, 1)));
        run.tick(RunInput.FLAP);
        // Past the bird's column, so the opening gate cannot kill it before the gear spawns.
        run.simulation().obstacles().last().setX(200);
        run.tick(RunInput.NONE);
        assertTrue(((Gear) run.simulation().obstacles().last()).isMoving());
    }

    // ---------------------------------------------------------- interpolation

    @Test
    void hitboxesAtZeroAndOneAreThePreviousAndCurrentState() {
        SimContext ctx = ctx(1.0);
        Gear gear = Gear.onRail(300, 300, 40);
        List<Hitbox> gearBefore = gear.hitboxes();
        gear.update(ctx);
        assertEquals(gearBefore, gear.hitboxesAt(0));
        assertEquals(gear.hitboxes(), gear.hitboxesAt(1));
        assertNotEquals(gear.hitboxesAt(0), gear.hitboxesAt(1));

        Piston piston = Piston.standard(300, Side.TOP, 300, 40 + 3);
        List<Hitbox> pistonBefore = piston.hitboxes();
        piston.update(ctx);
        assertEquals(pistonBefore, piston.hitboxesAt(0));
        assertEquals(piston.hitboxes(), piston.hitboxesAt(1));
        assertEquals(25, piston.maxDisplacement(), EPS, "300 px in 12 ticks");
        assertEquals(87.5, ((Aabb) piston.hitboxesAt(0.5).get(0)).h(), EPS);

        WindZone wind = new WindZone(300, 100, 320, 200, 0, 0);
        List<Hitbox> windBefore = wind.hitboxes();
        wind.update(ctx);
        assertEquals(windBefore, wind.hitboxesAt(0));
        assertEquals(wind.hitboxes(), wind.hitboxesAt(1));

        LightningStrike bolt = LightningStrike.standard(92, Side.TOP, 0.5);
        bolt.update(ctx);
        assertTrue(bolt.isStriking());
        assertEquals(bolt.boltSpanAt(bolt.prevX()), bolt.hitboxesAt(0).get(0));
        assertEquals(bolt.boltSpan(), bolt.hitboxesAt(1).get(0));
    }

    @Test
    void settleFreezesTheInterpolationOfEveryKind() {
        SimContext ctx = ctx(1.0);
        Gear gear = Gear.onRail(300, 300, 40);
        Piston piston = Piston.standard(300, Side.TOP, 300, 40 + 3);
        gear.update(ctx);
        piston.update(ctx);
        gear.settle();
        piston.settle();
        assertEquals(gear.hitboxes(), gear.hitboxesAt(0));
        assertEquals(piston.hitboxes(), piston.hitboxesAt(0));
        assertSame(ObstacleKind.GEAR, gear.kind());
    }

    @Test
    void wideKindsLeaveTheLayerOnlyOnceFullyOffscreen() {
        Gear gear = Gear.fixed(-100, 300, 56);
        assertFalse(gear.offscreen(), "the right edge is still at 12");
        gear.setX(-113);
        assertTrue(gear.offscreen());
        WindZone wind = new WindZone(-150, 200, 320, 200, 0, 0);
        assertFalse(wind.offscreen());
        wind.setX(-201);
        assertTrue(wind.offscreen());
    }
}
