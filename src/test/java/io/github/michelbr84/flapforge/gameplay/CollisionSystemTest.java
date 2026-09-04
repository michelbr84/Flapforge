package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionReport;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionSystem;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollisionSystemTest {

    /** A thin lethal bar with an explicit previous position (tunnelling scenarios). */
    static final class Bar extends Obstacle {
        private final double y;
        private final double h;
        private final double fromX;

        Bar(double fromX, double toX, double y, double w, double h) {
            super(ObstacleKind.PIPE_GATE, toX, w, false);
            this.fromX = fromX;
            this.y = y;
            this.h = h;
        }

        @Override
        public List<Hitbox> hitboxesAt(double t) {
            return List.of(new Aabb(fromX + (x() - fromX) * t, y, width(), h));
        }

        @Override
        public double maxDisplacement() {
            return Math.abs(x() - fromX);
        }

        @Override
        public boolean lethal() {
            return true;
        }

        @Override
        public double safeBandY(double atX) {
            return y - 100;
        }

        @Override
        protected long hashGeometry(long hash) {
            return MathUtil.fold(hash, Double.doubleToLongBits(y));
        }
    }

    private final CollisionSystem system = new CollisionSystem();

    private static ObstacleLayer layerOf(Obstacle... obstacles) {
        ObstacleLayer layer = new ObstacleLayer();
        for (Obstacle o : obstacles) {
            layer.add(o);
        }
        return layer;
    }

    private static Bird birdAt(double y) {
        Bird bird = new Bird(HitboxSpec.CLASSIC, y);
        bird.beginTick();
        return bird;
    }

    @Test
    void horizontalEdgesAreStrict() {
        // Bird box spans x [88, 121]; a gate at x = 121 touches without overlapping.
        Bird bird = birdAt(320);
        PipeGate touching = PipeGate.standard(121, 100, 128, null);
        assertFalse(system.test(bird, layerOf(touching), 0).lethalHit());
        PipeGate overlapping = PipeGate.standard(120.75, 100, 128, null);
        CollisionReport hit = system.test(bird, layerOf(overlapping), 0);
        assertTrue(hit.lethalHit());
        assertEquals(CollisionCause.OBSTACLE, hit.cause());
        assertSame(overlapping, hit.obstacle());
    }

    @Test
    void verticalEdgesAreStrict() {
        // Gap [200, 328]; bird box spans y [y − 12, y + 19].
        PipeGate gate = PipeGate.standard(100, 200, 128, null);
        assertFalse(system.test(birdAt(212), layerOf(gate), 0).lethalHit(), "top touching");
        assertTrue(system.test(birdAt(211.75), layerOf(gate), 0).lethalHit(), "top overlapping");
        assertFalse(system.test(birdAt(309), layerOf(gate), 0).lethalHit(), "bottom touching");
        assertTrue(system.test(birdAt(309.25), layerOf(gate), 0).lethalHit(), "bottom overlap");
    }

    @Test
    void groundRule() {
        CollisionReport onGround = system.test(birdAt(Playfield.GROUND_DEATH_Y), layerOf(), 0);
        assertTrue(onGround.lethalHit());
        assertEquals(CollisionCause.GROUND, onGround.cause());
        assertNull(onGround.obstacle());
        assertFalse(system.test(birdAt(Playfield.GROUND_DEATH_Y - 0.25), layerOf(), 0).lethalHit());
    }

    @Test
    void ceilingIsLethalOnlyUnderTheFlag() {
        Bird bird = birdAt(11.75); // box top at −0.25
        assertFalse(system.test(bird, layerOf(), 0, 1.0, RuleSet.EMPTY).lethalHit());
        CollisionReport report = system.test(bird, layerOf(), 0, 1.0,
                RuleSet.of(RuleFlag.LETHAL_CEILING));
        assertTrue(report.lethalHit());
        assertEquals(CollisionCause.CEILING, report.cause());
        assertFalse(system.test(birdAt(12), layerOf(), 0, 1.0, RuleSet.of(RuleFlag.LETHAL_CEILING))
                .lethalHit(), "box top exactly at 0 is not above the edge");
    }

    @Test
    void nearMissUsesTheInflatedBox() {
        PipeGate gate = PipeGate.standard(100, 200, 128, null);
        Bird bird = birdAt(215); // box top 203: 3 px below the upper pipe
        CollisionReport report = system.test(bird, layerOf(gate), Playfield.NEAR_MISS_INFLATE_PX);
        assertFalse(report.lethalHit());
        assertTrue(report.nearMiss());
        assertSame(gate, report.obstacle());
        Bird far = birdAt(222); // 10 px below: outside the 6 px inflation
        assertEquals(CollisionReport.NONE, system.test(far, layerOf(gate), 6));
        assertFalse(system.test(bird, layerOf(gate), 0).nearMiss(), "inflation 0 disables it");
    }

    @Test
    void hitboxScaleShrinksTheBirdAboutItsCentre() {
        PipeGate gate = PipeGate.standard(100, 200, 128, null);
        Bird bird = birdAt(211.75);
        assertTrue(system.test(bird, layerOf(gate), 0, 1.0, RuleSet.EMPTY).lethalHit());
        assertFalse(system.test(bird, layerOf(gate), 0, 0.5, RuleSet.EMPTY).lethalHit());
    }

    @Test
    void substepCountFollowsTheTwelvePixelRule() {
        assertEquals(1, CollisionSystem.substeps(12, 0));
        assertEquals(2, CollisionSystem.substeps(12.5, 0));
        assertEquals(3, CollisionSystem.substeps(0, 25));
        assertEquals(5, CollisionSystem.substeps(60, 25));
    }

    @Test
    void twentyFivePixelsPerTickAgainstA31PixelObstacleHits() {
        // Bird drops 25 px in the tick onto a 31 px-tall bar under its column.
        Bird bird = new Bird(HitboxSpec.CLASSIC, 480);
        bird.beginTick();
        bird.setY(505);
        Bar bar = new Bar(100, 100, 500, 40, 31);
        assertTrue(system.test(bird, layerOf(bar), 0).lethalHit());
    }

    @Test
    void fastBirdCannotTunnelThroughAThinBar() {
        // A half-scale bird (15.5 px tall) moving 25 px would skip a 4 px bar between its
        // previous and current boxes; sub-stepping catches the crossing.
        Bird bird = new Bird(HitboxSpec.CLASSIC, 500);
        bird.beginTick();
        bird.setY(525);
        Bar bar = new Bar(100, 100, 514, 40, 4);
        Aabb before = bird.hitboxAt(500, 0.5);
        Aabb after = bird.hitbox(0.5);
        assertFalse(bar.hitboxes().get(0).intersects(before), "sanity: no overlap before");
        assertFalse(bar.hitboxes().get(0).intersects(after), "sanity: no overlap after");
        assertTrue(system.test(bird, layerOf(bar), 0, 0.5, RuleSet.EMPTY).lethalHit());
    }

    @Test
    void fastObstacleCannotTunnelThroughTheBird() {
        // A 4 px wide bar jumping 60 px in one tick across the bird column.
        Bird bird = birdAt(320);
        Bar bar = new Bar(125, 65, 300, 4, 40);
        assertFalse(bar.hitboxes().get(0).intersects(bird.hitbox()), "sanity: ends past the bird");
        assertTrue(system.test(bird, layerOf(bar), 0).lethalHit());
    }

    @Test
    void movingGateIsTestedAlongItsPath() {
        // Oscillating pair whose previous offset kept the gap clear; use a bar to emulate.
        Bird bird = birdAt(320);
        Bar still = new Bar(100, 100, 400, 40, 10);
        assertFalse(system.test(bird, layerOf(still), 0).lethalHit());
        assertEquals(CollisionReport.NONE, system.test(bird, layerOf(), 6));
    }

    private static SimContext simContext() {
        return new SimContext(1, 1.0, StatSheet.defaults(), RuleSet.EMPTY, new RandomProvider(1),
                Bird.classic());
    }

    @Test
    void railGearCannotTunnelThroughTheBird() {
        // A gear whose rail jumps 100 px in one tick: the circle sits above the bird box before
        // the tick and below it after, and only the sub-stepped path crosses the box.
        Bird bird = birdAt(320);
        Gear gear = new Gear(80, 370, 20, 200, 6000);
        gear.update(simContext());
        assertFalse(gear.hitboxesAt(0).get(0).intersects(bird.hitbox()), "clear before");
        assertFalse(gear.hitboxes().get(0).intersects(bird.hitbox()), "clear after");
        assertEquals(100, gear.maxDisplacement(), 1e-9);
        assertEquals(9, CollisionSystem.substeps(0, gear.maxDisplacement()));
        CollisionReport report = system.test(bird, layerOf(gear), 0);
        assertTrue(report.lethalHit());
        assertSame(gear, report.obstacle());
    }

    @Test
    void extendingPistonIsSubSteppedAndHitsOnTheTickTheHeadReachesTheBird() {
        // The longest spawn-table head moves 300 px in 12 ticks = 25 px per tick, above the
        // 12 px sub-step rule; a half-scale bird sitting where the head arrives is hit on the
        // first tick the head reaches its box, not one tick late.
        Piston piston = Piston.standard(100, Side.TOP, 300, 40);
        Bird bird = new Bird(HitboxSpec.CLASSIC, 300);
        bird.beginTick();
        SimContext ctx = simContext();
        piston.update(ctx);
        assertEquals(25, piston.maxDisplacement(), 1e-9);
        assertEquals(3, CollisionSystem.substeps(0, piston.maxDisplacement()));
        int hitTick = -1;
        for (int t = 1; t <= 12 && hitTick < 0; t++) {
            if (system.test(bird, layerOf(piston), 0, 0.5, RuleSet.EMPTY).lethalHit()) {
                hitTick = t;
            } else {
                piston.update(ctx);
            }
        }
        // Half-scale box top = 300 + 3.5 − 7.75 = 295.75; the head passes it on tick 12 (300).
        assertEquals(12, hitTick, "hit the moment the head crosses the box top");
        assertTrue(piston.extension() > 295.75 && piston.extensionAt(0) < 295.75,
                "the previous state was still clear: " + piston.extensionAt(0));
    }

    @Test
    void windZonesAreIgnoredByTheLethalAndTheNearMissTests() {
        Bird bird = birdAt(320);
        io.github.michelbr84.flapforge.gameplay.obstacle.WindZone zone =
                new io.github.michelbr84.flapforge.gameplay.obstacle.WindZone(60, 120, 320, 200,
                        -500, 0);
        assertTrue(zone.hitboxes().get(0).intersects(bird.hitbox()), "sanity: overlapping");
        assertEquals(CollisionReport.NONE, system.test(bird, layerOf(zone), 6));
    }
}
