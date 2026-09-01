package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Circle;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import org.junit.jupiter.api.Test;

class HitboxTest {

    @Test
    void classicSpecReproducesUpstreamRectangle() {
        Aabb box = HitboxSpec.CLASSIC.at(Playfield.BIRD_X, 320, 1.0);
        assertEquals(new Aabb(88, 308, 33, 31), box);
        ClassicReference.Rect rect = new ClassicReference().rect();
        assertEquals(rect.x(), box.x(), 0.0);
        assertEquals(rect.y(), box.y(), 0.0);
        assertEquals(rect.w(), box.w(), 0.0);
        assertEquals(rect.h(), box.h(), 0.0);
    }

    @Test
    void scalingIsAboutTheCentre() {
        Aabb full = HitboxSpec.CLASSIC.at(105, 320, 1.0);
        Aabb half = HitboxSpec.CLASSIC.at(105, 320, 0.5);
        assertEquals(full.centerX(), half.centerX(), 1e-12);
        assertEquals(full.centerY(), half.centerY(), 1e-12);
        assertEquals(16.5, half.w(), 1e-12);
        assertEquals(15.5, half.h(), 1e-12);
        Aabb bigger = HitboxSpec.CLASSIC.at(105, 320, 1.5);
        assertEquals(49.5, bigger.w(), 1e-12);
        assertEquals(full.centerY(), bigger.centerY(), 1e-12);
    }

    @Test
    void birdHitboxFollowsItsPosition() {
        Bird bird = Bird.classic();
        bird.setY(100);
        assertEquals(new Aabb(88, 88, 33, 31), bird.hitbox());
        assertEquals(new Aabb(88, 188, 33, 31), bird.hitboxAt(200, 1.0));
    }

    @Test
    void touchingBoxesDoNotIntersect() {
        Aabb a = new Aabb(0, 0, 10, 10);
        assertFalse(a.intersects(new Aabb(10, 0, 10, 10)), "shared right edge");
        assertFalse(a.intersects(new Aabb(0, 10, 10, 10)), "shared bottom edge");
        assertFalse(a.intersects(new Aabb(-10, -10, 10, 10)), "shared corner");
        assertTrue(a.intersects(new Aabb(9.999, 0, 10, 10)));
        assertTrue(a.intersects(new Aabb(0, 9.75, 10, 10)));
        assertTrue(a.intersects(new Aabb(-5, -5, 20, 20)), "containing box");
        assertTrue(a.intersects(new Aabb(2, 2, 3, 3)), "contained box");
    }

    @Test
    void emptyBoxesNeverIntersect() {
        Aabb a = new Aabb(0, 0, 10, 10);
        assertFalse(a.intersects(new Aabb(5, 5, 0, 10)));
        assertFalse(new Aabb(5, 5, 10, 0).intersects(a));
    }

    @Test
    void circleAgainstBoxIsStrict() {
        Aabb box = new Aabb(0, 0, 10, 10);
        assertFalse(new Circle(15, 5, 5).intersects(box), "tangent circle");
        assertTrue(new Circle(14.9, 5, 5).intersects(box));
        assertTrue(new Circle(5, 5, 1).intersects(box), "inside");
        Circle corner = new Circle(13, 13, 4.2);
        assertFalse(corner.intersects(box), "corner distance sqrt(18) = 4.24 > 4.2");
        assertTrue(new Circle(13, 13, 4.3).intersects(box));
        Hitbox h = corner;
        assertEquals(corner.bounds(), h.bounds());
    }

    @Test
    void inflationAndTranslation() {
        Aabb a = new Aabb(10, 10, 10, 10);
        assertEquals(new Aabb(4, 4, 22, 22), a.inflated(6));
        assertEquals(new Aabb(12, 12, 6, 6), a.inflated(-2));
        assertEquals(new Aabb(15, 7, 10, 10), a.translated(5, -3));
        Hitbox c = new Circle(0, 0, 3).translated(1, 1);
        assertEquals(new Circle(1, 1, 3), c);
    }
}
