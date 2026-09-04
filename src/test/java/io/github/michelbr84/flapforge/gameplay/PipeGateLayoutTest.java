package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.Oscillator;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnDecision;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PipeGateLayoutTest {

    private static SimContext context(StatSheet stats) {
        return new SimContext(1, 1.0, stats, RuleSet.EMPTY, new RandomProvider(1), Bird.classic());
    }

    @Test
    void standardLayoutMatchesUpstreamRectangles() {
        PipeGate gate = PipeGate.standard(200, 150, 128, null);
        assertEquals(new Aabb(200, -100, 40, 250), gate.upperSegment());
        assertEquals(new Aabb(200, 278, 40, 362), gate.lowerSegment());
        assertEquals(150, gate.upperSegment().maxY(), 0.0, "upper segment ends at top");
        assertEquals(640, gate.lowerSegment().maxY(), 0.0, "lower segment reaches the bottom");
        assertEquals(214, gate.gapCenterY(), 0.0);
        assertEquals(240, gate.scoreLineX(), 0.0);
        assertEquals(PipeGate.Layout.STANDARD, gate.layout());
        ClassicReference.Rect top = ClassicReference.normalTop(200, 150);
        ClassicReference.Rect bottom = ClassicReference.normalBottom(200, 150);
        assertEquals(new Aabb(top.x(), top.y(), top.w(), top.h()), gate.upperSegment());
        assertEquals(new Aabb(bottom.x(), bottom.y(), bottom.w(), bottom.h()),
                gate.lowerSegment());
    }

    @Test
    void floatingLayoutMatchesUpstreamHoverPipes() {
        PipeGate gate = PipeGate.floating(200, 60, 120, 128, null);
        assertEquals(new Aabb(200, 60, 40, 120), gate.upperSegment());
        assertEquals(new Aabb(200, 308, 40, 272), gate.lowerSegment());
        assertEquals(640 - 60, gate.lowerSegment().maxY(), 0.0, "lower segment ends at 640 − y");
        assertEquals(60 + 120 + 64, gate.gapCenterY(), 0.0);
        ClassicReference.Rect top = ClassicReference.hoverTop(200, 60, 120);
        ClassicReference.Rect bottom = ClassicReference.hoverBottom(200, 60, 120);
        assertEquals(new Aabb(top.x(), top.y(), top.w(), top.h()), gate.upperSegment());
        assertEquals(new Aabb(bottom.x(), bottom.y(), bottom.w(), bottom.h()),
                gate.lowerSegment());
    }

    @Test
    void floatingGateIsPassableAboveAndBelow() {
        PipeGate gate = PipeGate.floating(88, 60, 120, 128, null);
        Aabb above = new Aabb(88, 20, 33, 31); // bottom at 51 < 60
        Aabb below = new Aabb(88, 585, 33, 31); // top at 585 > 580
        Aabb inGap = new Aabb(88, 200, 33, 31);
        Aabb inPipe = new Aabb(88, 100, 33, 31);
        for (Hitbox h : gate.hitboxes()) {
            assertFalse(h.intersects(above));
            assertFalse(h.intersects(below));
            assertFalse(h.intersects(inGap));
        }
        assertTrue(gate.hitboxes().get(0).intersects(inPipe));
    }

    @Test
    void oscillatorIsATriangleWaveStartingDownwards() {
        Oscillator osc = Oscillator.classic();
        assertEquals(51, osc.amplitude(), 0.0);
        assertEquals(0, osc.offset(), 0.0);
        assertEquals(1, osc.direction());
        for (int i = 0; i < 102; i++) {
            osc.advance(0.5);
        }
        assertEquals(51, osc.offset(), 0.0, "peak after 102 half-pixel steps");
        osc.advance(0.5);
        assertEquals(50.5, osc.offset(), 0.0);
        assertEquals(-1, osc.direction());
        for (int i = 0; i < 101; i++) {
            osc.advance(0.5);
        }
        assertEquals(0, osc.offset(), 0.0, "back at rest after one period (204 ticks = 3.4 s)");
        assertEquals(0.5, osc.prevOffset(), 0.0);
        assertEquals(0.25, osc.offsetAt(0.5), 0.0, "interpolated between the tick states");
    }

    @Test
    void movingPairShiftsRigidly() {
        StatSheet stats = StatSheet.defaults();
        PipeGate gate = PipeGate.standard(300, 150, 128, Oscillator.classic());
        SimContext ctx = context(stats);
        for (int i = 0; i < 10; i++) {
            gate.update(ctx);
        }
        assertEquals(300 - 20, gate.x(), 0.0, "scrolls 2 px per tick");
        assertEquals(5, gate.offsetY(), 0.0, "0.5 px per tick");
        assertEquals(new Aabb(280, -95, 40, 250), gate.upperSegment());
        assertEquals(new Aabb(280, 283, 40, 362), gate.lowerSegment());
        assertEquals(219, gate.gapCenterY(), 0.0);
        assertTrue(gate.isMoving());
        assertEquals(2.0, gate.maxDisplacement(), 0.0);
        List<Hitbox> mid = gate.hitboxesAt(0.5);
        assertEquals(new Aabb(281, -95.25, 40, 250), mid.get(0));
    }

    @Test
    void staticGateHasNoOffsetAndScrollsWithTheWorld() {
        PipeGate gate = PipeGate.floating(420, 60, 120, 128, null);
        gate.update(context(StatSheet.defaults()));
        assertEquals(418, gate.x(), 0.0);
        assertEquals(420, gate.prevX(), 0.0);
        assertEquals(0, gate.offsetY(), 0.0);
        assertTrue(gate.oscillator().isEmpty());
        assertFalse(gate.offscreen());
        gate.setX(-Playfield.PIPE_CAP_W);
        assertFalse(gate.offscreen());
        gate.setX(-Playfield.PIPE_CAP_W - 0.5);
        assertTrue(gate.offscreen());
    }

    @Test
    void spawnTableGeometryStaysInUpstreamRanges() {
        Random spawn = new Random(11);
        Random obstacle = new Random(12);
        int minTop = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        for (int i = 0; i < 20_000; i++) {
            SpawnDecision d = SpawnTable.GREEN_FIELDS.roll(spawn, obstacle, 0.5);
            if (d.layout() == PipeGate.Layout.STANDARD) {
                minTop = Math.min(minTop, (int) d.top());
                maxTop = Math.max(maxTop, (int) d.top());
                assertTrue(d.top() >= ClassicReference.MIN_HEIGHT && d.top() <= ClassicReference.MAX_HEIGHT);
            } else {
                minY = Math.min(minY, (int) d.floatY());
                maxY = Math.max(maxY, (int) d.floatY());
                minH = Math.min(minH, (int) d.floatH());
                maxH = Math.max(maxH, (int) d.floatH());
                assertTrue(d.floatY() >= ClassicReference.HOVER_Y_MIN && d.floatY() < ClassicReference.HOVER_Y_MAX);
                assertTrue(d.floatH() >= ClassicReference.HOVER_H_MIN && d.floatH() < ClassicReference.HOVER_H_MAX);
            }
        }
        assertEquals(80, minTop);
        assertEquals(400, maxTop, "top range is inclusive");
        assertEquals(53, minY);
        assertEquals(105, maxY, "y range is exclusive at 106");
        assertEquals(106, minH);
        assertEquals(159, maxH, "h range is exclusive at 160");
    }

    @Test
    void materializedGatesUseTheDecision() {
        SpawnDecision standard = new SpawnDecision(ObstacleKind.PIPE_GATE,
                PipeGate.Layout.STANDARD, true, 250, 0, 0);
        PipeGate gate = (PipeGate) SpawnTable.GREEN_FIELDS.materialize(standard, 420, 128);
        assertEquals(420, gate.x(), 0.0);
        assertEquals(250, gate.baseGapTopY(), 0.0);
        assertTrue(gate.isMoving());
        SpawnDecision floating = new SpawnDecision(ObstacleKind.PIPE_GATE,
                PipeGate.Layout.FLOATING, false, 0, 70, 130);
        PipeGate hover = (PipeGate) SpawnTable.GREEN_FIELDS.materialize(floating, 420, 100);
        assertEquals(200, hover.baseGapTopY(), 0.0);
        assertEquals(300, hover.gapBottomY(), 0.0);
        assertFalse(hover.isMoving());
    }
}
