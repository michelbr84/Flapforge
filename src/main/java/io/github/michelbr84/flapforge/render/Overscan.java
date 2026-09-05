package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.Playfield;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * The vertical logical range renderers may paint, published each frame by the presenters (D3
 * revision).
 *
 * <p>The playfield stays the fixed 420x640 logical space and everything interactive stays
 * inside it. On windows taller than the playfield aspect — portrait phones foremost — the
 * viewport's clip extends vertically and the presenters publish the visible range here, so the
 * sky, the ground, the pipes and the full-frame fills can paint the rows that used to be
 * letterbox bars. Cosmetic only: the simulation never reads this class, and gameplay geometry
 * stays keyed to {@link Playfield}.
 *
 * <p>Static, loop-thread-only state, in the mould of {@link Fonts#setTextScale}: the
 * {@code Screen} render signature is fixed and threading the range through every renderer would
 * touch dozens of call sites for a value that changes at most once per frame. Headless runs and
 * code paths that never present leave it at the default {@code [0, 640]}, which renders
 * pixel-identical to the letterboxed frame. Tests that call {@link #set} must {@link #reset()}
 * afterwards.
 */
public final class Overscan {

    private static double top;
    private static double bottom = Playfield.HEIGHT;

    private Overscan() {
    }

    /**
     * Publishes the visible logical range. The values are clamped so the range always covers
     * the playfield: {@code top <= 0} and {@code bottom >= 640}.
     *
     * @param newTop the visible top edge in logical pixels (usually negative)
     * @param newBottom the visible bottom edge in logical pixels (usually beyond 640)
     */
    public static void set(double newTop, double newBottom) {
        top = Math.min(0, newTop);
        bottom = Math.max(Playfield.HEIGHT, newBottom);
    }

    /** Restores the default playfield-only range. */
    public static void reset() {
        top = 0;
        bottom = Playfield.HEIGHT;
    }

    /**
     * The visible top edge.
     *
     * @return logical pixels, at most 0
     */
    public static double top() {
        return top;
    }

    /**
     * The visible bottom edge.
     *
     * @return logical pixels, at least {@link Playfield#HEIGHT}
     */
    public static double bottom() {
        return bottom;
    }

    /**
     * The visible top edge, floored to whole pixels.
     *
     * @return logical pixels, at most 0
     */
    public static int topInt() {
        return (int) Math.floor(top);
    }

    /**
     * The visible bottom edge, ceiled to whole pixels.
     *
     * @return logical pixels, at least {@link Playfield#HEIGHT}
     */
    public static int bottomInt() {
        return (int) Math.ceil(bottom);
    }

    /**
     * Fills the whole visible range with one colour — the full-frame scrims and backdrops use
     * this instead of a {@code 0..640} rect so they cover the extended rows too.
     *
     * @param g the graphics context in logical coordinates
     * @param color the fill colour
     */
    public static void fillVisible(Graphics2D g, Color color) {
        g.setColor(color);
        g.fillRect(0, topInt(), Playfield.WIDTH, bottomInt() - topInt());
    }
}
