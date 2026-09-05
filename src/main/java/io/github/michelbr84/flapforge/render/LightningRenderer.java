package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.Random;

/**
 * Draws a {@link LightningStrike} (D18, D6, M7).
 *
 * <p>While {@code IDLE} the column already shows where it is (M7 fairness): a faint outline of
 * the span the bolt will light and a small anchor plate on the edge it hangs from, so a bolt is
 * visible from the moment it scrolls in like every other hazard, and the side to be on can be
 * read before the bird commits to the gap in front of it. The warning then brightens it.
 *
 * <p>During {@code WARNING} the column is a marker over exactly the span the bolt will light —
 * the anchored side and the extent — that brightens as the strike approaches: a tinted band
 * with a hard bar across its far end (where the bolt stops, so the safe band on the other side
 * is unmistakable) and chevrons along the anchored edge pointing the way the bolt will come.
 * The brightness is the warning progress, {@code 1 − distance / (warningTicks × scroll)},
 * which is why the renderer takes the current scroll per tick.
 *
 * <p>During {@code STRIKE} the bolt is a jittered polyline down the span with a local glow
 * around it. The jitter comes from this renderer's own seeded {@link Random}, re-seeded per
 * frame from the strike clock so the bolt crackles without ever touching the run's streams
 * (D12). With {@code reduceFlashing} on the glow is dimmer and the core does not saturate.
 * Everything is interpolated with the frame alpha; the polyline lives in reused int arrays.
 */
public final class LightningRenderer {

    /** Segments of the bolt polyline. */
    public static final int SEGMENTS = 9;
    /** Peak alpha of the warning band just before the strike. */
    public static final double WARNING_PEAK = 0.62;
    /** Lowest alpha the warning band starts at, so the marker is visible from its first tick. */
    public static final double WARNING_FLOOR = 0.16;
    /** Alpha of the idle outline, drawn from spawn. */
    public static final double IDLE_ALPHA = 0.10;
    /** Alpha of the idle anchor plate and outline stroke. */
    public static final double IDLE_EDGE_ALPHA = 0.28;
    /** Height of the anchor plate on the edge the bolt hangs from. */
    public static final double ANCHOR_H = 4;

    private static final Stroke CORE = new BasicStroke(3f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private static final Stroke HALO = new BasicStroke(9f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private static final Stroke EDGE = new BasicStroke(1.5f);
    private static final Color CORE_WHITE = new Color(0xFF, 0xFF, 0xFF, 0xF0);
    private static final Color CORE_SOFT = new Color(0xFF, 0xFF, 0xFF, 0xB0);

    private final Rectangle2D.Double rect = new Rectangle2D.Double();
    private final Random jitter = new Random(0x0B01L);
    private final int[] boltX = new int[SEGMENTS + 1];
    private final int[] boltY = new int[SEGMENTS + 1];
    private final int[] chevronX = new int[3];
    private final int[] chevronY = new int[3];
    private final Color[] warnRamp = new Color[17];
    private final Color[] glowRamp = new Color[17];
    private int rampRgb = -1;

    /** Creates a renderer. */
    public LightningRenderer() {
    }

    /**
     * Draws one bolt column.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param bolt the column
     * @param palette the world palette
     * @param scrollPerTick the world scroll of this tick (the warning progress is measured in
     *     ticks of it)
     * @param reduceFlashing {@code settings.reduceFlashing}
     */
    public void render(Graphics2D g, double alpha, LightningStrike bolt, WorldPalette palette,
            double scrollPerTick, boolean reduceFlashing) {
        LightningStrike.State state = bolt.state();
        if (state == LightningStrike.State.SPENT) {
            return;
        }
        ramps(Accessibility.tone(palette.accent(), Accessibility.Role.DANGER));
        double x = MathUtil.lerp(bolt.prevX(), bolt.x(), alpha);
        double w = LightningStrike.WIDTH;
        double top = bolt.boltTopY();
        double h = bolt.boltHeight();
        boolean fromTop = bolt.side() == Side.TOP;

        if (state == LightningStrike.State.IDLE) {
            // The span, faintly, and the plate it hangs from: readable, never alarming.
            g.setColor(warn(IDLE_ALPHA));
            rect.setFrame(x, top, w, h);
            g.fill(rect);
            Stroke old = g.getStroke();
            g.setStroke(EDGE);
            g.setColor(warn(IDLE_EDGE_ALPHA));
            g.draw(rect);
            g.setStroke(old);
            double plateY = fromTop ? top : top + h - ANCHOR_H;
            // A TOP plate hangs from row 0, which on tall windows sits below the visible top:
            // a mount column up to the visible edge keeps it attached instead of floating in
            // extended sky. The warning/strike band itself is never extended — it communicates
            // the exact lethal extent.
            if (fromTop && Overscan.top() < 0) {
                rect.setFrame(x - 4, Overscan.top(), w + 8, -Overscan.top() + 2);
                g.fill(rect);
            }
            rect.setFrame(x - 4, plateY, w + 8, ANCHOR_H);
            g.fill(rect);
            return;
        }

        if (state == LightningStrike.State.WARNING) {
            double centre = x + w / 2;
            double window = Math.max(1e-6, bolt.warningTicks() * scrollPerTick);
            double progress = MathUtil.clamp(1 - (centre - Playfield.BIRD_X) / window, 0, 1);
            double a = WARNING_FLOOR + (WARNING_PEAK - WARNING_FLOOR) * progress;
            // The band over the exact extent.
            g.setColor(warn(a));
            rect.setFrame(x, top, w, h);
            g.fill(rect);
            Stroke old = g.getStroke();
            g.setStroke(EDGE);
            g.setColor(warn(Math.min(1, a + 0.35)));
            g.draw(rect);
            // The bar at the far end: below this line (or above it, for a bottom bolt) is safe.
            double tipY = fromTop ? top + h : top;
            rect.setFrame(x - 8, tipY - 2, w + 16, 4);
            g.fill(rect);
            // Chevrons along the anchored edge, pointing the way the bolt will travel.
            int count = Math.max(1, (int) (h / 60));
            for (int i = 0; i < count; i++) {
                double cy = fromTop ? top + 14 + i * 60 : top + h - 14 - i * 60;
                chevron(g, centre, cy, fromTop ? 1 : -1);
            }
            g.setStroke(old);
            return;
        }

        // STRIKE: the glow first, then the jittered bolt with a soft edge and a white core.
        double clock = bolt.strikeClock();
        double life = 1 - MathUtil.clamp(clock / Math.max(1, bolt.strikeTicks()), 0, 1);
        double glowA = (reduceFlashing ? 0.25 : 0.5) * (0.5 + 0.5 * life);
        g.setColor(glow(glowA));
        rect.setFrame(x - 14, top, w + 28, h);
        g.fill(rect);
        jitter.setSeed(0x0B01L + (long) (clock * 7) + (long) (alpha * 4));
        double cx = x + w / 2;
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            double y = fromTop ? top + h * t : top + h * (1 - t);
            double dx = (i == 0 || i == SEGMENTS) ? 0 : (jitter.nextDouble() * 2 - 1) * (w * 0.45);
            boltX[i] = (int) Math.round(cx + dx);
            boltY[i] = (int) Math.round(y);
        }
        Stroke old = g.getStroke();
        g.setStroke(HALO);
        g.setColor(glow(reduceFlashing ? 0.45 : 0.7));
        g.drawPolyline(boltX, boltY, SEGMENTS + 1);
        g.setStroke(CORE);
        g.setColor(reduceFlashing ? CORE_SOFT : CORE_WHITE);
        g.drawPolyline(boltX, boltY, SEGMENTS + 1);
        g.setStroke(old);
    }

    private void chevron(Graphics2D g, double cx, double cy, int dir) {
        chevronX[0] = (int) Math.round(cx - 7);
        chevronY[0] = (int) Math.round(cy - 5 * dir);
        chevronX[1] = (int) Math.round(cx);
        chevronY[1] = (int) Math.round(cy + 4 * dir);
        chevronX[2] = (int) Math.round(cx + 7);
        chevronY[2] = (int) Math.round(cy - 5 * dir);
        g.drawPolyline(chevronX, chevronY, 3);
    }

    private void ramps(int rgb) {
        if (rampRgb == rgb) {
            return;
        }
        rampRgb = rgb;
        for (int i = 0; i <= 16; i++) {
            warnRamp[i] = new Color((rgb & 0xFFFFFF) | (Math.min(255, i * 16) << 24), true);
            int lighter = WorldPalette.lighten(rgb, 0.4);
            glowRamp[i] = new Color((lighter & 0xFFFFFF) | (Math.min(255, i * 16) << 24), true);
        }
    }

    private Color warn(double a) {
        return warnRamp[MathUtil.clamp((int) Math.round(a * 16), 0, 16)];
    }

    private Color glow(double a) {
        return glowRamp[MathUtil.clamp((int) Math.round(a * 16), 0, 16)];
    }
}
