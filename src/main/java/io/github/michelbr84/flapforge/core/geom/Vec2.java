package io.github.michelbr84.flapforge.core.geom;

/**
 * Immutable 2D vector in logical coordinates.
 *
 * @param x the horizontal component
 * @param y the vertical component
 */
public record Vec2(double x, double y) {

    /** The zero vector. */
    public static final Vec2 ZERO = new Vec2(0, 0);

    /**
     * Component-wise sum.
     *
     * @param other the vector to add
     * @return {@code this + other}
     */
    public Vec2 plus(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    /**
     * Component-wise difference.
     *
     * @param other the vector to subtract
     * @return {@code this - other}
     */
    public Vec2 minus(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    /**
     * Scalar multiplication.
     *
     * @param factor the scale factor
     * @return {@code this * factor}
     */
    public Vec2 scaled(double factor) {
        return new Vec2(x * factor, y * factor);
    }

    /**
     * Euclidean length.
     *
     * @return {@code sqrt(x^2 + y^2)}
     */
    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    /**
     * Squared Euclidean length (no square root).
     *
     * @return {@code x^2 + y^2}
     */
    public double lengthSquared() {
        return x * x + y * y;
    }

    /**
     * Distance to another point.
     *
     * @param other the other point
     * @return the Euclidean distance
     */
    public double distanceTo(Vec2 other) {
        return minus(other).length();
    }

    /**
     * Linear interpolation towards another vector.
     *
     * @param target the vector at {@code t = 1}
     * @param t the blend factor
     * @return the interpolated vector
     */
    public Vec2 lerp(Vec2 target, double t) {
        return new Vec2(x + (target.x - x) * t, y + (target.y - y) * t);
    }
}
