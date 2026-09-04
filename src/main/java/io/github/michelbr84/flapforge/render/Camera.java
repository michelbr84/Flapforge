package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import java.awt.Graphics2D;

/**
 * Screen shake and parallax factors for the in-run picture (D18).
 *
 * <p>Flapforge's world scrolls past a bird that never moves horizontally, so the camera does
 * <em>not</em> translate the playfield to follow anything: it only adds a short decaying offset
 * when something hits hard ({@link #shake(double)}) and hands the background layers the factor
 * their depth deserves ({@link #parallax(double, double)}). Keeping it that way means the
 * simulation's coordinates are the screen's coordinates, which is what every renderer, hitbox
 * overlay and test assumes.
 *
 * <p>The offset is advanced once per simulation tick and interpolated with the frame alpha like
 * everything else (E30.g). The oscillation comes from a tiny deterministic generator, so two
 * runs with the same facts shake identically and nothing here allocates.
 */
public final class Camera {

    /** Largest offset a shake can reach, in logical pixels. */
    public static final double MAX_SHAKE = 8.0;
    /** Fraction of the magnitude that survives one tick (about 0.2 s to fade out). */
    public static final double DECAY_PER_TICK = 0.86;
    /** Magnitude below which the shake is considered over. */
    public static final double EPSILON = 0.05;

    private double magnitude;
    private double x;
    private double y;
    private double prevX;
    private double prevY;
    private long noise = 0x9E3779B97F4A7C15L;
    private double appliedX;
    private double appliedY;

    /** Creates a camera at rest. */
    public Camera() {
    }

    /**
     * Adds a shake impulse, capped at {@link #MAX_SHAKE}.
     *
     * @param strength the magnitude in logical pixels (negative values are ignored)
     */
    public void shake(double strength) {
        if (strength <= 0) {
            return;
        }
        magnitude = Math.min(MAX_SHAKE, magnitude + strength);
    }

    /** Advances the shake by one simulation tick. */
    public void tick() {
        prevX = x;
        prevY = y;
        if (magnitude < EPSILON) {
            magnitude = 0;
            x = 0;
            y = 0;
            return;
        }
        x = magnitude * nextSigned();
        y = magnitude * nextSigned() * 0.6;
        magnitude *= DECAY_PER_TICK;
    }

    /** Stops the shake immediately (a new run). */
    public void reset() {
        magnitude = 0;
        x = 0;
        y = 0;
        prevX = 0;
        prevY = 0;
    }

    /**
     * Current shake magnitude.
     *
     * @return logical pixels
     */
    public double magnitude() {
        return magnitude;
    }

    /**
     * Whether the camera is offset at all.
     *
     * @return {@code true} while a shake is running
     */
    public boolean isShaking() {
        return magnitude >= EPSILON;
    }

    /**
     * Interpolated horizontal offset.
     *
     * @param alpha the frame alpha in {@code [0, 1)}
     * @return logical pixels
     */
    public double offsetX(double alpha) {
        return MathUtil.lerp(prevX, x, alpha);
    }

    /**
     * Interpolated vertical offset.
     *
     * @param alpha the frame alpha in {@code [0, 1)}
     * @return logical pixels
     */
    public double offsetY(double alpha) {
        return MathUtil.lerp(prevY, y, alpha);
    }

    /**
     * Translates a context by the current offset. Always paired with {@link #unapply(Graphics2D)}.
     *
     * @param g the context
     * @param alpha the frame alpha in {@code [0, 1)}
     */
    public void apply(Graphics2D g, double alpha) {
        appliedX = offsetX(alpha);
        appliedY = offsetY(alpha);
        if (appliedX != 0 || appliedY != 0) {
            g.translate(appliedX, appliedY);
        }
    }

    /**
     * Undoes the last {@link #apply(Graphics2D, double)} on the same context.
     *
     * @param g the context
     */
    public void unapply(Graphics2D g) {
        if (appliedX != 0 || appliedY != 0) {
            g.translate(-appliedX, -appliedY);
            appliedX = 0;
            appliedY = 0;
        }
    }

    /**
     * The offset a parallax layer draws at.
     *
     * @param worldOffset how far the world has scrolled
     * @param factor the layer's depth factor ({@code 1} = with the world, {@code 0.5} = half as
     *     fast, {@code 1 / Playfield.CLOUD_SPEED_FACTOR} = upstream's cloud layer)
     * @return the layer offset
     */
    public static double parallax(double worldOffset, double factor) {
        return worldOffset * factor;
    }

    /** A xorshift step in {@code [-1, 1]}; deterministic and allocation-free. */
    private double nextSigned() {
        noise ^= noise << 13;
        noise ^= noise >>> 7;
        noise ^= noise << 17;
        return ((noise >>> 11) / (double) (1L << 53)) * 2 - 1;
    }

    @Override
    public String toString() {
        return "Camera[shake=" + String.format(java.util.Locale.ROOT, "%.2f", magnitude) + "]";
    }
}
