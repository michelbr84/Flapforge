package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;

/**
 * The bird hitbox as authored in {@code birds.json}: a box of {@code w × h} whose top-left corner
 * sits at {@code (x + ox, y + oy)} relative to the bird origin (D7).
 *
 * @param w the width in px
 * @param h the height in px
 * @param ox the horizontal offset of the left edge
 * @param oy the vertical offset of the top edge
 */
public record HitboxDef(double w, double h, double ox, double oy) {

    /**
     * Validates the size the same way the simulation seam does.
     *
     * @param w the width
     * @param h the height
     * @param ox the horizontal offset
     * @param oy the vertical offset
     */
    public HitboxDef {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("hitbox size must be positive: " + w + "x" + h);
        }
    }

    /**
     * The simulation seam record.
     *
     * @return the spec
     */
    public HitboxSpec toSpec() {
        return new HitboxSpec(w, h, ox, oy);
    }
}
