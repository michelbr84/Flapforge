package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Draws a {@link Gear} (D18, M7): a toothed polygon precomputed once in unit space, scaled to
 * the gear's radius and rotated by the obstacle's angle, with a hub and spokes so the rotation
 * reads; a gear on a rail also draws its track as a faint line spanning the sweep.
 *
 * <p>The angle the simulation keeps is in turns (E30.c: no trigonometry in the pure packages);
 * this side multiplies by {@code 2π} for the context transform, which is where the rotation
 * belongs. Everything is interpolated with the frame alpha — the x, the rail offset and the
 * angle — and nothing is allocated per frame: the polygon, the hub and the strokes are built
 * once.
 */
public final class GearRenderer {

    /** Teeth around the wheel. */
    public static final int TEETH = 12;
    /** Inner radius of the teeth as a fraction of the outer radius. */
    public static final double ROOT_RADIUS = 0.82;
    /** Radius of the hub as a fraction of the outer radius. */
    public static final double HUB_RADIUS = 0.3;

    private static final Stroke OUTLINE = new BasicStroke(0.05f);
    private static final Stroke TRACK = new BasicStroke(2f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private static final Color TRACK_TINT = new Color(0, 0, 0, 0x48);
    private static final Shape WHEEL = toothedWheel();
    private static final Shape HUB = new Ellipse2D.Double(-HUB_RADIUS, -HUB_RADIUS,
            2 * HUB_RADIUS, 2 * HUB_RADIUS);
    private static final Shape AXLE = new Ellipse2D.Double(-0.1, -0.1, 0.2, 0.2);
    private static final Shape SPOKES = spokes();

    private final Rectangle2D.Double rect = new Rectangle2D.Double();

    /** Creates a renderer. */
    public GearRenderer() {
    }

    /**
     * Draws one gear.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param gear the gear
     * @param palette the world palette
     */
    public void render(Graphics2D g, double alpha, Gear gear, WorldPalette palette) {
        double x = MathUtil.lerp(gear.prevX(), gear.x(), alpha);
        double cx = x + gear.radius();
        double cy = gear.centerYAt(alpha);
        double r = gear.radius();
        Color body = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE);
        Color edge = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE_EDGE);
        Color light = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE_LIGHT);

        if (gear.isMoving()) {
            // The rail: a track from one end of the sweep to the other, with a stop at each end,
            // so the player reads where the wheel can and cannot go.
            Stroke old = g.getStroke();
            g.setStroke(TRACK);
            g.setColor(TRACK_TINT);
            double top = gear.sweepTopY() + r;
            double bottom = gear.sweepBottomY() - r;
            g.drawLine((int) Math.round(cx), (int) Math.round(top), (int) Math.round(cx),
                    (int) Math.round(bottom));
            g.setStroke(old);
            g.setColor(edge);
            rect.setFrame(cx - 6, top - 3, 12, 3);
            g.fill(rect);
            rect.setFrame(cx - 6, bottom, 12, 3);
            g.fill(rect);
        }

        double angle = gear.angleAt(alpha) * 2 * Math.PI;
        g.translate(cx, cy);
        g.rotate(angle);
        g.scale(r, r);
        Stroke old = g.getStroke();
        g.setStroke(OUTLINE);
        g.setColor(body);
        g.fill(WHEEL);
        g.setColor(edge);
        g.draw(WHEEL);
        g.setColor(edge);
        g.fill(SPOKES);
        g.setColor(light);
        g.fill(HUB);
        g.setColor(edge);
        g.fill(AXLE);
        g.setStroke(old);
        g.scale(1 / r, 1 / r);
        g.rotate(-angle);
        g.translate(-cx, -cy);
    }

    /** The unit wheel: {@value #TEETH} trapezoid teeth around a circle of radius 1. */
    private static Shape toothedWheel() {
        Path2D.Double path = new Path2D.Double();
        int points = TEETH * 4;
        for (int i = 0; i < points; i++) {
            // Within each tooth: root, flank up, tip, flank down. The corners sit at the quarter
            // turns of the tooth pitch; the tips are narrower than the roots.
            int phase = i % 4;
            double pitch = 2 * Math.PI / TEETH;
            double base = (i / 4) * pitch;
            double a;
            double radius;
            switch (phase) {
                case 0:
                    a = base;
                    radius = ROOT_RADIUS;
                    break;
                case 1:
                    a = base + pitch * 0.18;
                    radius = 1.0;
                    break;
                case 2:
                    a = base + pitch * 0.42;
                    radius = 1.0;
                    break;
                default:
                    a = base + pitch * 0.6;
                    radius = ROOT_RADIUS;
                    break;
            }
            double px = Math.cos(a) * radius;
            double py = Math.sin(a) * radius;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.closePath();
        return path;
    }

    /** Four thin spokes from the hub to the root circle, as filled quads. */
    private static Shape spokes() {
        Path2D.Double path = new Path2D.Double();
        double half = 0.055;
        double reach = ROOT_RADIUS - 0.08;
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 4;
            double dx = Math.cos(a);
            double dy = Math.sin(a);
            double nx = -dy * half;
            double ny = dx * half;
            path.moveTo(-dx * reach + nx, -dy * reach + ny);
            path.lineTo(dx * reach + nx, dy * reach + ny);
            path.lineTo(dx * reach - nx, dy * reach - ny);
            path.lineTo(-dx * reach - nx, -dy * reach - ny);
            path.closePath();
        }
        return path;
    }
}
