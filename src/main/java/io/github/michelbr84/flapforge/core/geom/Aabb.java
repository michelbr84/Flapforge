package io.github.michelbr84.flapforge.core.geom;

/**
 * Axis-aligned bounding box given by its top-left corner and size.
 *
 * <p>Intersection uses strict inequalities and treats empty boxes as never intersecting, exactly
 * like the AWT rectangle test the original game relied on.
 *
 * @param x the left edge
 * @param y the top edge
 * @param w the width
 * @param h the height
 */
public record Aabb(double x, double y, double w, double h) implements Hitbox {

    /**
     * Builds a box from its centre and size.
     *
     * @param cx the centre x
     * @param cy the centre y
     * @param w the width
     * @param h the height
     * @return the box
     */
    public static Aabb centered(double cx, double cy, double w, double h) {
        return new Aabb(cx - w / 2, cy - h / 2, w, h);
    }

    /**
     * Right edge.
     *
     * @return {@code x + w}
     */
    public double maxX() {
        return x + w;
    }

    /**
     * Bottom edge.
     *
     * @return {@code y + h}
     */
    public double maxY() {
        return y + h;
    }

    /**
     * Horizontal centre.
     *
     * @return {@code x + w / 2}
     */
    public double centerX() {
        return x + w / 2;
    }

    /**
     * Vertical centre.
     *
     * @return {@code y + h / 2}
     */
    public double centerY() {
        return y + h / 2;
    }

    /**
     * Tells whether the box has no area.
     *
     * @return {@code true} when width or height is not positive
     */
    public boolean isEmpty() {
        return w <= 0 || h <= 0;
    }

    @Override
    public boolean intersects(Aabb box) {
        if (isEmpty() || box.isEmpty()) {
            return false;
        }
        return box.x < x + w && box.x + box.w > x && box.y < y + h && box.y + box.h > y;
    }

    /**
     * Tests intersection with any hitbox.
     *
     * @param other the other shape
     * @return {@code true} when the interiors overlap
     */
    public boolean intersects(Hitbox other) {
        return other.intersects(this);
    }

    /**
     * Tells whether a point lies strictly inside the box.
     *
     * @param px the point x
     * @param py the point y
     * @return {@code true} when inside
     */
    public boolean contains(double px, double py) {
        return px > x && px < x + w && py > y && py < y + h;
    }

    /**
     * Grows the box by {@code px} on every side (negative values shrink it).
     *
     * @param px the inflation in logical pixels
     * @return the inflated box
     */
    public Aabb inflated(double px) {
        return new Aabb(x - px, y - px, w + 2 * px, h + 2 * px);
    }

    /**
     * Scales the box about its centre.
     *
     * @param factor the scale factor
     * @return the scaled box
     */
    public Aabb scaledAboutCenter(double factor) {
        return centered(centerX(), centerY(), w * factor, h * factor);
    }

    @Override
    public Aabb bounds() {
        return this;
    }

    @Override
    public Aabb translated(double dx, double dy) {
        return new Aabb(x + dx, y + dy, w, h);
    }
}
