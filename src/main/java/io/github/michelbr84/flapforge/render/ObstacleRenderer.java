package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Draws the pipe gates of a run (D18). M1 had only {@link PipeGate}; from M7 the
 * {@link ObstacleRendererRegistry} dispatches every family to its own renderer and this class
 * keeps the gate art, exposed as {@link #drawGate(Graphics2D, double, PipeGate, WorldPalette)}.
 *
 * <p>A gate is a {@link Playfield#PIPE_BODY_W} px body — the lethal width — with a
 * {@link Playfield#PIPE_CAP_W}x{@link Playfield#PIPE_CAP_H} decorative cap at each end that faces
 * the gap (both ends for a floating gate, whose segments hang free), a highlight stripe and a
 * darker outline. {@link PipeGate.Layout#STANDARD} anchors its segments to the playfield edges
 * ({@link Playfield#TOP_PIPE_EXTRA} px of the upper one sits above the screen);
 * {@link PipeGate.Layout#FLOATING} leaves space above and below, exactly like upstream's hover
 * pipes.
 *
 * <p>The x and the oscillator offset are interpolated with the frame alpha (E30.g), so a moving
 * gate is as smooth as a static one. Nothing is allocated per frame: the two shapes are reused.
 */
public final class ObstacleRenderer {

    private static final Color HITBOX = new Color(0x3B, 0xFF, 0x6E, 0xC0);
    private static final Stroke HITBOX_STROKE = new BasicStroke(1f);
    private static final Stroke OUTLINE = new BasicStroke(1f);
    private static final int BODY_ARC = 6;
    private static final int CAP_ARC = 6;

    private final RoundRectangle2D.Double round = new RoundRectangle2D.Double();
    private final Rectangle2D.Double rect = new Rectangle2D.Double();

    /** Creates a renderer. */
    public ObstacleRenderer() {
    }

    /**
     * Draws every obstacle of a layer, back to front (spawn order).
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param layer the live obstacles
     * @param palette the world palette
     * @param debugHitboxes {@code true} to outline the lethal hitboxes ({@code F3})
     */
    public void render(Graphics2D g, double alpha, ObstacleLayer layer, WorldPalette palette,
            boolean debugHitboxes) {
        List<Obstacle> obstacles = layer.obstacles();
        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle o = obstacles.get(i);
            if (o instanceof PipeGate gate) {
                drawGate(g, alpha, gate, palette);
            }
        }
        if (debugHitboxes) {
            Stroke old = g.getStroke();
            g.setStroke(HITBOX_STROKE);
            g.setColor(HITBOX);
            for (int i = 0; i < obstacles.size(); i++) {
                for (Hitbox h : obstacles.get(i).hitboxesAt(alpha)) {
                    Aabb b = h.bounds();
                    rect.setFrame(b.x(), b.y(), b.w(), b.h());
                    g.draw(rect);
                }
            }
            g.setStroke(old);
        }
    }

    /**
     * Draws one gate: both segments with their caps, interpolated with the frame alpha.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param gate the gate
     * @param palette the world palette
     */
    public void drawGate(Graphics2D g, double alpha, PipeGate gate, WorldPalette palette) {
        double x = MathUtil.lerp(gate.prevX(), gate.x(), alpha);
        double dy = gate.offsetYAt(alpha);
        double gapTop = gate.baseGapTopY() + dy;
        double gap = gate.gap();

        if (gate.layout() == PipeGate.Layout.STANDARD) {
            double upperTop = -Playfield.TOP_PIPE_EXTRA + dy;
            segment(g, palette, x, upperTop, gapTop - upperTop, false, true);
            segment(g, palette, x, gapTop + gap, Playfield.HEIGHT - gate.baseGapTopY() - gap,
                    true, false);
        } else {
            double upperTop = gate.floatY() + dy;
            segment(g, palette, x, upperTop, gate.floatH(), true, true);
            double lowerTop = gapTop + gap;
            double lowerH = Playfield.HEIGHT - 2 * gate.floatY() - gate.floatH() - gap;
            segment(g, palette, x, lowerTop, lowerH, true, true);
        }
    }

    /**
     * Draws one pipe segment.
     *
     * @param topCap whether the segment gets a cap at its top edge
     * @param bottomCap whether the segment gets a cap at its bottom edge
     */
    private void segment(Graphics2D g, WorldPalette palette, double x, double y, double h,
            boolean topCap, boolean bottomCap) {
        if (h <= 0) {
            return;
        }
        Color body = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE);
        Color edge = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE_EDGE);
        Color light = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE_LIGHT);
        Stroke old = g.getStroke();
        g.setStroke(OUTLINE);

        g.setColor(body);
        round.setRoundRect(x, y, Playfield.PIPE_BODY_W, h, BODY_ARC, BODY_ARC);
        g.fill(round);
        g.setColor(light);
        rect.setFrame(x + 6, y + 2, 6, h - 4);
        g.fill(rect);
        g.setColor(edge);
        g.draw(round);

        double capX = x - (Playfield.PIPE_CAP_W - Playfield.PIPE_BODY_W) / 2.0;
        if (topCap) {
            cap(g, body, light, edge, capX, y);
        }
        if (bottomCap) {
            cap(g, body, light, edge, capX, y + h - Playfield.PIPE_CAP_H);
        }
        g.setStroke(old);
    }

    private void cap(Graphics2D g, Color body, Color light, Color edge, double x, double y) {
        g.setColor(body);
        round.setRoundRect(x, y, Playfield.PIPE_CAP_W, Playfield.PIPE_CAP_H, CAP_ARC, CAP_ARC);
        g.fill(round);
        g.setColor(light);
        rect.setFrame(x + 6, y + 3, 6, Playfield.PIPE_CAP_H - 6.0);
        g.fill(rect);
        g.setColor(edge);
        g.draw(round);
    }
}
