package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * The scrolling world backdrop of a run (D18, plan section 5 cosmetic rows): sky gradient, two
 * parallax hill bands and the 42 px ground strip.
 *
 * <p>Upstream drew one 253x84 image tiled across the window and moved it by {@code GAME_SPEED}
 * (4 px per 30 Hz frame), wrapping at the strip width. Flapforge keeps the same motion —
 * {@code SCROLL_SPEED} (120 px/s = 2 px/tick, the obstacle speed) and a {@value #STRIP_WIDTH} px
 * wrap — but generates the strip: the ground band carries a repeating tuft/dirt pattern of that
 * period, so the wrap is visible exactly where upstream's was. The hill bands scroll at
 * {@value #HILL_FAR_PARALLAX} and {@value #HILL_NEAR_PARALLAX} of the ground speed.
 *
 * <p>The renderer is advanced one simulation tick at a time by {@link #tick(double, boolean)} and
 * keeps the previous offset so {@link #render} can interpolate with the frame alpha (E30.g).
 * A frozen tick (the run is {@code DYING} or {@code FINISHED}) leaves both offsets untouched, so
 * the ground stops the moment the bird dies, as upstream's {@code GameBackground.draw} did by
 * returning before {@code movement()}. Nothing is allocated per frame.
 */
public final class BackgroundRenderer {

    /** Wrap period of the ground pattern: the width of upstream's background strip. */
    public static final int STRIP_WIDTH = 253;
    /** Wrap period of the far hill band. */
    public static final int HILL_FAR_PERIOD = 210;
    /** Wrap period of the near hill band. */
    public static final int HILL_NEAR_PERIOD = 280;
    /** Fraction of the ground speed at which the far hills scroll. */
    public static final double HILL_FAR_PARALLAX = 0.25;
    /** Fraction of the ground speed at which the near hills scroll. */
    public static final double HILL_NEAR_PARALLAX = 0.5;
    /** Height of the darker line along the top of the ground strip. */
    public static final int GROUND_EDGE_H = 4;

    private static final double FAR_HILL_Y = 452;
    private static final double FAR_HILL_W = 268;
    private static final double FAR_HILL_H = 210;
    private static final double NEAR_HILL_Y = 508;
    private static final double NEAR_HILL_W = 330;
    private static final double NEAR_HILL_H = 180;

    /* Ground decoration inside one 253 px tile: {x, width} of the dirt dashes and tuft bases. */
    private static final double[] DIRT = {12, 46, 96, 30, 148, 62, 226, 20};
    private static final double[] TUFTS = {8, 34, 61, 97, 132, 158, 191, 219, 240};

    private final Rectangle2D.Double rect = new Rectangle2D.Double();
    private final Ellipse2D.Double oval = new Ellipse2D.Double();
    private double distance;
    private double prevDistance;

    /** Creates a backdrop scrolled to its start position. */
    public BackgroundRenderer() {
    }

    /**
     * Advances the backdrop by one simulation tick.
     *
     * @param scrollPerTick the world scroll of this tick in px (normally
     *     {@code SCROLL_SPEED / TICK_RATE})
     * @param frozen {@code true} while the run is dying or finished; the backdrop stops and the
     *     interpolation state is settled so the render shows no motion
     */
    public void tick(double scrollPerTick, boolean frozen) {
        prevDistance = distance;
        if (!frozen) {
            distance += scrollPerTick;
        }
    }

    /** Puts the backdrop back at its start position (a new run). */
    public void reset() {
        distance = 0;
        prevDistance = 0;
    }

    /**
     * Distance scrolled since the run started, in px.
     *
     * @return the distance
     */
    public double distance() {
        return distance;
    }

    /**
     * Draws sky, hills and ground.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param palette the world palette
     */
    public void render(Graphics2D g, double alpha, WorldPalette palette) {
        double d = MathUtil.lerp(prevDistance, distance, alpha);

        Paint oldPaint = g.getPaint();
        g.setPaint(ProceduralArt.skyPaint(palette));
        rect.setFrame(0, 0, Playfield.WIDTH, Playfield.GROUND_Y);
        g.fill(rect);
        g.setPaint(oldPaint);

        hills(g, ProceduralArt.color(palette, ProceduralArt.Tone.HILL_FAR),
                wrap(d * HILL_FAR_PARALLAX, HILL_FAR_PERIOD), HILL_FAR_PERIOD, FAR_HILL_Y,
                FAR_HILL_W, FAR_HILL_H);
        hills(g, ProceduralArt.color(palette, ProceduralArt.Tone.HILL_NEAR),
                wrap(d * HILL_NEAR_PARALLAX, HILL_NEAR_PERIOD), HILL_NEAR_PERIOD, NEAR_HILL_Y,
                NEAR_HILL_W, NEAR_HILL_H);

        ground(g, palette, wrap(d, STRIP_WIDTH));
    }

    private void hills(Graphics2D g, Color color, double offset, double period, double y,
            double w, double h) {
        // Subpixel positions, not fillOval(int, ...): the far band moves half a pixel per tick, so
        // snapping to the logical pixel grid would make it step visibly at any viewport scale.
        g.setColor(color);
        for (double x = -offset - period; x < Playfield.WIDTH + period; x += period) {
            oval.setFrame(x, y, w, h);
            g.fill(oval);
        }
    }

    private void ground(Graphics2D g, WorldPalette palette, double offset) {
        Color base = ProceduralArt.color(palette, ProceduralArt.Tone.GROUND);
        Color edge = ProceduralArt.color(palette, ProceduralArt.Tone.GROUND_EDGE);
        Color grass = ProceduralArt.color(palette, ProceduralArt.Tone.HILL_NEAR);

        g.setColor(base);
        rect.setFrame(0, Playfield.GROUND_Y, Playfield.WIDTH, Playfield.GROUND_HEIGHT);
        g.fill(rect);
        g.setColor(edge);
        rect.setFrame(0, Playfield.GROUND_Y, Playfield.WIDTH, GROUND_EDGE_H);
        g.fill(rect);

        // Grass tufts straddling the ground line, one set per strip: they poke above the edge
        // band into the sky, which is what makes the wrap readable while the ground scrolls.
        g.setColor(grass);
        for (double tile = -offset - STRIP_WIDTH; tile < Playfield.WIDTH;
                tile += STRIP_WIDTH) {
            for (double t : TUFTS) {
                rect.setFrame(tile + t, Playfield.GROUND_Y - 5, 7, 9);
                g.fill(rect);
            }
        }

        // Dirt dashes lower in the band, same period, so the wrap reads like upstream's strip.
        g.setColor(edge);
        for (double tile = -offset - STRIP_WIDTH; tile < Playfield.WIDTH;
                tile += STRIP_WIDTH) {
            for (int i = 0; i < DIRT.length; i += 2) {
                rect.setFrame(tile + DIRT[i], Playfield.GROUND_Y + 18, DIRT[i + 1], 5);
                g.fill(rect);
            }
        }
    }

    private static double wrap(double value, double period) {
        double m = value % period;
        return m < 0 ? m + period : m;
    }
}
