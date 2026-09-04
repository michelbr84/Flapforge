package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import java.util.List;

/**
 * A non-lethal wind zone (D6): an {@link Aabb} that pushes the bird while its hitbox overlaps it.
 *
 * <p>{@link #affectBird} is sampled by the simulation at the <em>start</em> of a tick, from the
 * bird's tick-start hitbox and the zone's tick-start box, and adds to the bird's wind
 * accumulators ({@link Bird#applyWind}): {@code accelY} joins gravity in that tick's integration
 * and {@code scrollDelta} joins {@code SCROLL_SPEED} for that tick's scroll of the whole world —
 * the bird's x is fixed, so a horizontal wind can only show up as a change of the relative scroll
 * speed (D6). The unscaled hitbox is used: wind acts on the bird's body, not on the
 * {@code HITBOX_SCALE} the collision test applies.
 *
 * <p>The zone never scores and never kills; {@link #hitboxesAt} still describes its box so the
 * renderer and the harness can see it, and {@code CollisionSystem} skips it because
 * {@link #lethal()} is {@code false}. The safe band (E32.c) is a pass-through: the zone centre.
 */
public final class WindZone extends Obstacle {

    /** Smallest width (§4 {@code ParamSpec}). */
    public static final double MIN_WIDTH = 60;
    /** Largest width (§4 {@code ParamSpec}). */
    public static final double MAX_WIDTH = 240;
    /** Strongest updraft, in px/s² (§4 {@code ParamSpec}). */
    public static final double MIN_ACCEL_Y = -900;
    /** Strongest downdraft, in px/s² (§4 {@code ParamSpec}). */
    public static final double MAX_ACCEL_Y = 900;
    /** Strongest tailwind (slower relative scroll), in px/s (§4 {@code ParamSpec}). */
    public static final double MIN_SCROLL_DELTA = -60;
    /** Strongest headwind (faster relative scroll), in px/s (§4 {@code ParamSpec}). */
    public static final double MAX_SCROLL_DELTA = 60;

    private final double cy;
    private final double height;
    private final double accelY;
    private final double scrollDelta;
    private boolean affecting;

    /**
     * Creates a zone.
     *
     * @param x the left edge
     * @param width the width in px
     * @param cy the centre y
     * @param height the height in px
     * @param accelY the vertical acceleration applied to the bird in px/s² (positive = down)
     * @param scrollDelta the change of the relative scroll speed in px/s while the bird is inside
     */
    public WindZone(double x, double width, double cy, double height, double accelY,
            double scrollDelta) {
        super(ObstacleKind.WIND_ZONE, x, width, false);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Wind zone size must be positive: " + width + "x"
                    + height);
        }
        this.cy = cy;
        this.height = height;
        this.accelY = accelY;
        this.scrollDelta = scrollDelta;
    }

    @Override
    public List<Hitbox> hitboxesAt(double t) {
        double atX = prevX() + (x() - prevX()) * t;
        return List.of(boxAt(atX));
    }

    @Override
    public double maxDisplacement() {
        return Math.abs(x() - prevX());
    }

    @Override
    public boolean lethal() {
        return false;
    }

    @Override
    public boolean offscreen() {
        return x() + width() < 0;
    }

    @Override
    public void affectBird(Bird bird, SimContext ctx) {
        affecting = box().intersects(bird.hitbox());
        if (affecting) {
            bird.applyWind(accelY, scrollDelta);
        }
    }

    @Override
    public double safeBandY(double atX) {
        return cy;
    }

    @Override
    protected long hashGeometry(long hash) {
        long h = MathUtil.fold(hash, Double.doubleToLongBits(cy));
        h = MathUtil.fold(h, Double.doubleToLongBits(height));
        h = MathUtil.fold(h, Double.doubleToLongBits(accelY));
        h = MathUtil.fold(h, Double.doubleToLongBits(scrollDelta));
        return MathUtil.fold(h, affecting ? 1 : 0);
    }

    /**
     * Current box.
     *
     * @return the zone
     */
    public Aabb box() {
        return boxAt(x());
    }

    /**
     * The box at a column x.
     *
     * @param atX the left edge
     * @return the zone
     */
    public Aabb boxAt(double atX) {
        return new Aabb(atX, cy - height / 2, width(), height);
    }

    /**
     * Centre y.
     *
     * @return the y
     */
    public double cy() {
        return cy;
    }

    /**
     * Height.
     *
     * @return the height in px
     */
    public double height() {
        return height;
    }

    /**
     * Vertical acceleration applied to the bird inside the zone.
     *
     * @return px/s², positive = down
     */
    public double accelY() {
        return accelY;
    }

    /**
     * Change of the relative scroll speed while the bird is inside.
     *
     * @return px/s, positive = faster
     */
    public double scrollDelta() {
        return scrollDelta;
    }

    /**
     * Tells whether the last sample found the bird inside the zone (renderer feedback).
     *
     * @return {@code true} while the wind acts on the bird
     */
    public boolean isAffecting() {
        return affecting;
    }

    @Override
    public String toString() {
        return "WindZone{x=" + x() + ", w=" + width() + ", cy=" + cy + ", h=" + height
                + ", accelY=" + accelY + ", scrollDelta=" + scrollDelta + '}';
    }
}
