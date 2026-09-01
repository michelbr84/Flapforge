package io.github.michelbr84.flapforge.core.geom;

/**
 * A collision shape in logical playfield coordinates (E31.a).
 *
 * <p>The hierarchy is sealed so collision code can enumerate every shape with
 * {@code instanceof} patterns. Intersection tests use strict inequalities: touching edges do not
 * intersect, matching the semantics of the AWT rectangle intersection used by the original game.
 */
public sealed interface Hitbox permits Aabb, Circle {

    /**
     * Tests intersection with an axis-aligned box.
     *
     * @param box the box
     * @return {@code true} when the interiors overlap
     */
    boolean intersects(Aabb box);

    /**
     * Returns the tightest axis-aligned box enclosing this shape.
     *
     * @return the bounds
     */
    Aabb bounds();

    /**
     * Returns a copy of this shape moved by the given offset.
     *
     * @param dx the horizontal offset
     * @param dy the vertical offset
     * @return the translated shape
     */
    Hitbox translated(double dx, double dy);
}
