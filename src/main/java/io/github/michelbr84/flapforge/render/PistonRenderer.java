package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws a {@link Piston} (D18, M7): a base plate on the anchored edge, a rod and a head that
 * travels the extension, with a telegraph glow over the whole reach while the piston warns and a
 * motion smear behind the head while it extends.
 *
 * <p>The glow is the fairness cue: during {@code TELEGRAPH} the column the head is about to
 * fill is tinted in the world accent and pulses on a triangle wave, brighter as the telegraph
 * runs out, so the player sees <em>where</em> and <em>when</em> before anything is lethal. The
 * head position and the smear are interpolated with the frame alpha through
 * {@link Piston#extensionAt(double)}. Nothing is allocated per frame: the shapes are scratch
 * objects and the glow colours come from a small ramp built once.
 */
public final class PistonRenderer {

    /** Height of the head block. */
    public static final double HEAD_H = 18;
    /** Height of the base plate on the anchored edge. */
    public static final double BASE_H = 10;
    /** Width of the rod behind the head. */
    public static final double ROD_W = 12;
    /** Period of the telegraph pulse, in ticks. */
    public static final int PULSE_TICKS = 16;
    /** Peak alpha of the telegraph glow. */
    public static final double GLOW_PEAK = 0.55;

    private static final int GLOW_STEPS = 16;
    private static final Stroke OUTLINE = new BasicStroke(1f);
    private static final Color SMEAR = new Color(0xFF, 0xFF, 0xFF, 0x30);

    private final RoundRectangle2D.Double round = new RoundRectangle2D.Double();
    private final Rectangle2D.Double rect = new Rectangle2D.Double();
    private final Color[] glowRamp = new Color[GLOW_STEPS + 1];
    private int glowRgb = -1;

    /** Creates a renderer. */
    public PistonRenderer() {
    }

    /**
     * Draws one piston.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param piston the piston
     * @param palette the world palette
     * @param animTicks the renderer's animation clock (the pulse)
     */
    public void render(Graphics2D g, double alpha, Piston piston, WorldPalette palette,
            long animTicks) {
        double x = MathUtil.lerp(piston.prevX(), piston.x(), alpha);
        double ext = piston.extensionAt(alpha);
        boolean top = piston.side() == Side.TOP;
        Color body = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE);
        Color edge = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE_EDGE);
        Color light = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE_LIGHT);
        double w = Piston.WIDTH;

        if (piston.phase() == Piston.Phase.TELEGRAPH) {
            // The glow covers the full reach — what will be lethal — and pulses harder as the
            // telegraph runs out.
            double pulse = triangle(animTicks, PULSE_TICKS);
            double urgency = 0.45 + 0.55 * piston.phaseProgress();
            double a = GLOW_PEAK * urgency * (0.55 + 0.45 * pulse);
            g.setColor(glow(Accessibility.tone(palette.accent(), Accessibility.Role.DANGER), a));
            double reach = piston.length();
            rect.setFrame(x - 2, top ? 0 : Playfield.GROUND_Y - reach, w + 4, reach);
            g.fill(rect);
            // A tip mark where the head will stop: the far end of the reach.
            double tipY = top ? reach : Playfield.GROUND_Y - reach;
            g.setColor(glow(Accessibility.tone(palette.accent(), Accessibility.Role.DANGER),
                    Math.min(1, a + 0.3)));
            rect.setFrame(x - 6, tipY - 1.5, w + 12, 3);
            g.fill(rect);
        }

        if (piston.phase() == Piston.Phase.EXTEND && ext > 0) {
            // Motion smear: the head's travel over this tick, drawn as a translucent trail.
            double from = piston.extensionAt(Math.max(0, alpha - 0.5));
            double trail = Math.max(0, ext - from);
            if (trail > 1) {
                g.setColor(SMEAR);
                rect.setFrame(x, top ? ext - HEAD_H - trail : Playfield.GROUND_Y - ext + HEAD_H,
                        w, trail);
                g.fill(rect);
            }
        }

        Stroke old = g.getStroke();
        g.setStroke(OUTLINE);
        if (ext > 0) {
            // Rod from the base to the head.
            double rodTop = top ? 0 : Playfield.GROUND_Y - ext + HEAD_H;
            double rodH = Math.max(0, ext - HEAD_H);
            g.setColor(edge);
            rect.setFrame(x + (w - ROD_W) / 2, rodTop, ROD_W, rodH);
            g.fill(rect);
            g.setColor(light);
            rect.setFrame(x + (w - ROD_W) / 2 + 2, rodTop, 3, rodH);
            g.fill(rect);
            // Head.
            double headH = Math.min(HEAD_H, ext);
            double headY = top ? ext - headH : Playfield.GROUND_Y - ext;
            g.setColor(body);
            round.setRoundRect(x, headY, w, headH, 5, 5);
            g.fill(round);
            g.setColor(light);
            rect.setFrame(x + 5, headY + 2, 5, Math.max(0, headH - 4));
            g.fill(rect);
            g.setColor(edge);
            g.draw(round);
        }
        // Base plate on the anchored edge, drawn last so it covers the rod's end.
        double baseY = top ? 0 : Playfield.GROUND_Y - BASE_H;
        g.setColor(body);
        round.setRoundRect(x - 4, baseY, w + 8, BASE_H, 4, 4);
        g.fill(round);
        g.setColor(edge);
        g.draw(round);
        g.setStroke(old);
    }

    private Color glow(int rgb, double a) {
        if (glowRgb != rgb) {
            glowRgb = rgb;
            for (int i = 0; i <= GLOW_STEPS; i++) {
                glowRamp[i] = new Color((rgb & 0xFFFFFF) | (Math.min(255, i * 16) << 24), true);
            }
        }
        int step = MathUtil.clamp((int) Math.round(a * GLOW_STEPS), 0, GLOW_STEPS);
        return glowRamp[step];
    }

    private static double triangle(long t, int period) {
        double p = Math.floorMod(t, (long) period) / (double) period;
        return p < 0.5 ? p * 2 : 2 - p * 2;
    }
}
