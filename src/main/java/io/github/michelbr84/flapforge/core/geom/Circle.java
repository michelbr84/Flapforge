package io.github.michelbr84.flapforge.core.geom;

/**
 * Circular hitbox given by its centre and radius.
 *
 * @param cx the centre x
 * @param cy the centre y
 * @param r the radius
 */
public record Circle(double cx, double cy, double r) implements Hitbox {

    @Override
    public boolean intersects(Aabb box) {
        if (r <= 0 || box.isEmpty()) {
            return false;
        }
        double nearestX = clamp(cx, box.x(), box.maxX());
        double nearestY = clamp(cy, box.y(), box.maxY());
        double dx = cx - nearestX;
        double dy = cy - nearestY;
        return dx * dx + dy * dy < r * r;
    }

    /**
     * Tests intersection with another circle.
     *
     * @param other the other circle
     * @return {@code true} when the interiors overlap
     */
    public boolean intersects(Circle other) {
        double dx = cx - other.cx;
        double dy = cy - other.cy;
        double rr = r + other.r;
        return rr > 0 && dx * dx + dy * dy < rr * rr;
    }

    /**
     * Tells whether a point lies strictly inside the circle.
     *
     * @param px the point x
     * @param py the point y
     * @return {@code true} when inside
     */
    public boolean contains(double px, double py) {
        double dx = cx - px;
        double dy = cy - py;
        return dx * dx + dy * dy < r * r;
    }

    @Override
    public Aabb bounds() {
        return new Aabb(cx - r, cy - r, 2 * r, 2 * r);
    }

    @Override
    public Circle translated(double dx, double dy) {
        return new Circle(cx + dx, cy + dy, r);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
