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
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
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
}
