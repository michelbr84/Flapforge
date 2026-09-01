package io.github.michelbr84.flapforge.gameplay.bird;

import io.github.michelbr84.flapforge.core.geom.Aabb;

/**
 * Bird hitbox geometry relative to the bird origin (D7): a box of {@code w × h} whose top-left
 * corner sits at {@code (x + ox, y + oy)}. The classic values reproduce upstream's rectangle
 * {@code (x − 17, y − 12, 33, 31)} inside the 39×33 sprite (height derived from the width; the
 * quirk is preserved as data).
 *
 * @param w the width in px
 * @param h the height in px
 * @param ox the horizontal offset of the left edge from the bird origin
 * @param oy the vertical offset of the top edge from the bird origin
 */
public record HitboxSpec(double w, double h, double ox, double oy) {

    /** Upstream's hitbox: 33×31 at (−17, −12). */
    public static final HitboxSpec CLASSIC = new HitboxSpec(33, 31, -17, -12);

    /**
     * Validates the size.
     *
     * @param w the width
     * @param h the height
     * @param ox the horizontal offset
     * @param oy the vertical offset
     */
    public HitboxSpec {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Hitbox size must be positive: " + w + "x" + h);
        }
    }

    /**
     * Places the box for a bird at {@code (x, y)} and scales it about its centre.
     *
     * @param x the bird origin x
     * @param y the bird origin y
     * @param scale the {@code HITBOX_SCALE} factor (1 = unscaled)
     * @return the box
     */
    public Aabb at(double x, double y, double scale) {
        Aabb box = new Aabb(x + ox, y + oy, w, h);
        return scale == 1.0 ? box : box.scaledAboutCenter(scale);
    }

    /**
     * Vertical offset of the box centre from the bird origin.
     *
     * @return {@code oy + h / 2}
     */
    public double centerOffsetY() {
        return oy + h / 2;
    }
}
