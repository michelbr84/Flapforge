package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/**
 * Draws a {@link WindZone} (D18, M7): a faintly outlined box filled with translucent stripes
 * that drift in the direction the wind pushes — up for an updraft, down for a downdraft, left
 * for a headwind (the world scrolls faster) and right for a tailwind — brighter while the bird
 * is inside it.
 *
 * <p>The stripes are render-side animation: their spacing comes from a table this renderer
 * draws once from its own seeded {@link Random} (D12: never the run's streams) and their drift
 * is the renderer's animation clock, so the simulation carries no state for them. The zone box
 * is interpolated with the frame alpha like every other column; nothing is allocated per frame
 * — the stripes are cut at the zone's edge by geometry rather than by a clip, which would be.
 */
public final class WindZoneRenderer {

    /** Stripes per zone. */
    public static final int STRIPES = 9;
    /** Drift of the stripes per tick, in logical px. */
    public static final double DRIFT_PER_TICK = 1.4;

    private static final Stroke DASHED = new BasicStroke(1.2f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 4f, new float[] {6f, 5f}, 0f);
    private final Color[] stripeAlpha = new Color[17];
    private int stripeRgb = -1;
    private final RoundRectangle2D.Double round = new RoundRectangle2D.Double();
    private final Rectangle2D.Double rect = new Rectangle2D.Double();
    private final double[] spacing = new double[STRIPES];
    private final double[] thickness = new double[STRIPES];

    /** Creates a renderer with its own seeded stripe table. */
    public WindZoneRenderer() {
        Random rng = new Random(0x71D3L);
        for (int i = 0; i < STRIPES; i++) {
            spacing[i] = 0.55 + rng.nextDouble() * 0.9;
            thickness[i] = 2 + rng.nextDouble() * 3;
        }
    }

    /**
     * Draws one zone.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param zone the zone
     * @param palette the world palette
     * @param animTicks the renderer's animation clock (the drift)
     */
    public void render(Graphics2D g, double alpha, WindZone zone, WorldPalette palette,
            long animTicks) {
        double x = MathUtil.lerp(zone.prevX(), zone.x(), alpha);
        double w = zone.width();
        double h = zone.height();
        double top = zone.cy() - h / 2;
        boolean inside = zone.isAffecting();
        // The stripes have to read on the world's own sky: a lightened fog on a dark sky, a
        // darkened one on a bright sky (Wind Valley), never white on white.
        int sky = palette.skyBottom();
        double luminance = 0.299 * ((sky >> 16) & 0xFF) + 0.587 * ((sky >> 8) & 0xFF)
                + 0.114 * (sky & 0xFF);
        ramp(luminance > 140 ? WorldPalette.mix(palette.fog(), palette.letterbox(), 0.55)
                : WorldPalette.lighten(palette.fog(), 0.5));

        double drift = animTicks * DRIFT_PER_TICK;
        boolean vertical = zone.accelY() != 0;
        // Sign: an updraft (accelY < 0) drifts up, a downdraft down; a headwind (scrollDelta > 0,
        // faster scroll) drifts left, a tailwind right.
        double direction = vertical ? (zone.accelY() < 0 ? -1 : 1)
                : (zone.scrollDelta() > 0 ? -1 : 1);
        double span = vertical ? h : w;
        double pitch = span / STRIPES;
        int level = inside ? 9 : 5;
        for (int i = 0; i < STRIPES; i++) {
            double offset = wrap(i * pitch * spacing[i] + direction * drift, span);
            // A stripe wrapping past the far edge is cut there by geometry: no clip is set,
            // because a clip is a per-frame allocation and the stripes are plain rectangles.
            double length = Math.min(thickness[i], span - offset);
            g.setColor(stripeAlpha[level]);
            if (vertical) {
                // A horizontal band that scrolls vertically, with a lighter core line.
                rect.setFrame(x + 4, top + offset, w - 8, length);
            } else {
                rect.setFrame(x + offset, top + 4, length, h - 8);
            }
            g.fill(rect);
        }
        // A soft wash so the zone reads as a body of air, not just lines.
        g.setColor(stripeAlpha[inside ? 3 : 2]);
        rect.setFrame(x, top, w, h);
        g.fill(rect);

        Stroke old = g.getStroke();
        g.setStroke(DASHED);
        g.setColor(stripeAlpha[inside ? 12 : 7]);
        round.setRoundRect(x, top, w, h, 10, 10);
        g.draw(round);
        g.setStroke(old);
    }

    /** Rebuilds the 17-step alpha ramp of the stripe colour when the palette changes. */
    private void ramp(int rgb) {
        if (stripeRgb == rgb) {
            return;
        }
        stripeRgb = rgb;
        for (int i = 0; i <= 16; i++) {
            stripeAlpha[i] = new Color((rgb & 0xFFFFFF) | (Math.min(255, i * 16) << 24), true);
        }
    }

    private static double wrap(double value, double period) {
        double m = value % period;
        return m < 0 ? m + period : m;
    }
}
