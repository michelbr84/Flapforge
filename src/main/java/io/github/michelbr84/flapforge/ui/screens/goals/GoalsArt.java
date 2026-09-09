package io.github.michelbr84.flapforge.ui.screens.goals;

import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;

/**
 * The Goals section's measured look: the palette the four reference screens sample to and the
 * few shapes they draw that no shared painter carries yet.
 *
 * <p>Values come from the visual spec's consolidated table ({@code goals-visual-spec.md}
 * section 5): where a measurement and a shipped constant agree to within a point or two, the
 * shipped constant wins ({@link ProceduralArt#TEXT_LIGHT}, the gold family); where the
 * references are specific — the panel gradient, the complete state's green, the Everything
 * card's olive-and-yellow — the measured value ships here. Colours allocate once; the render
 * path only reads them.
 */
public final class GoalsArt {

    /** Content panel, top of its vertical gradient. */
    public static final Color PANEL_TOP = new Color(0x18, 0x45, 0x49);
    /** Content panel, bottom of its vertical gradient. */
    public static final Color PANEL_BOTTOM = new Color(0x0D, 0x39, 0x3D);
    /** The panel's light-teal edge. */
    public static final Color PANEL_EDGE = new Color(0x34, 0x7C, 0x84);
    /** Card fill (the locked state's base). */
    public static final Color CARD_FILL = new Color(0x12, 0x3F, 0x44);
    /** Card stroke, shared by cards, pills and tabs. */
    public static final Color CARD_STROKE = new Color(0x3E, 0x7C, 0x88);
    /** The World/Tier pair's fill, one step lighter than a full row. */
    public static final Color PAIR_FILL = new Color(0x1F, 0x52, 0x57);
    /** The inset icon tile, one step lighter than the card. */
    public static final Color TILE_FILL = new Color(0x1E, 0x53, 0x5B);
    /** An achieved thing's fill: the green glow, not gold. */
    public static final Color COMPLETE_FILL = new Color(0x1B, 0x61, 0x54);
    /** An achieved thing's border. */
    public static final Color COMPLETE_BORDER = new Color(0x4D, 0xCC, 0xA2);
    /** The check circle of the achieved state. */
    public static final Color CHECK_CIRCLE = new Color(0x5D, 0xBB, 0x46);
    /** The disabled call to action's slate fill. */
    public static final Color CTA_DISABLED_FILL = new Color(0x4E, 0x6F, 0x7C);
    /** The disabled call to action's pale border. */
    public static final Color CTA_DISABLED_BORDER = new Color(0xAC, 0xC8, 0xD8);
    /** The Everything card's olive fill. */
    public static final Color EVERYTHING_FILL = new Color(0x33, 0x3C, 0x2A);
    /** The Everything card's bright yellow border. */
    public static final Color EVERYTHING_BORDER = new Color(0xFF, 0xF0, 0x60);
    /** The display title's light gold. */
    public static final Color TITLE_FILL = new Color(0xFC, 0xE6, 0x8A);
    /** The display title's near-black outline. */
    public static final Color TITLE_OUTLINE = new Color(0x00, 0x02, 0x07);
    /** The active tab pill's top stop. */
    public static final Color TAB_ACTIVE_TOP = new Color(0xFF, 0xD8, 0x62);
    /** The active tab pill's bottom stop. */
    public static final Color TAB_ACTIVE_BOTTOM = new Color(0xFC, 0xD0, 0x54);
    /** An inactive tab pill's fill. */
    public static final Color TAB_IDLE_FILL = new Color(0x1F, 0x52, 0x57);
    /** An inactive tab's ink (spec-estimated light teal). */
    public static final Color TAB_INK = new Color(0xDC, 0xED, 0xEF);
    /** The status pill's fill. */
    public static final Color PILL_FILL = new Color(0x23, 0x47, 0x4C);
    /** The progress-bar fill: the references' bar gold. */
    public static final Color BAR_FILL = new Color(0xFD, 0xD1, 0x4C);
    /** The 16:9 tabs' progress-bar track: lighter than its card, so an empty meter reads as one. */
    public static final Color BAR_TRACK = new Color(0x17, 0x4A, 0x50);
    /** The 16:9 tabs' track stroke. */
    public static final Color BAR_TRACK_EDGE = new Color(0x3A, 0x8B, 0x94);
    /** The Collections tab's track: its own mock measures this darker, unstroked slot. */
    public static final Color BAR_TRACK_DARK = new Color(0x0E, 0x27, 0x2B);
    /** The scrollbar thumb: the shop's translucent white, per the spec's decision. */
    public static final Color SCROLLBAR = new Color(0xF4, 0xF8, 0xF8, 0x50);
    /** Pale ink on disabled controls and padlocks; the padlock painter's own default. */
    public static final Color PALE_INK = new Color(0xD8, 0xE2, 0xE4);
    /** The gold accent, the shipped constant. */
    public static final Color GOLD = ProceduralArt.COIN_GOLD;

    private static final Stroke EDGE = new BasicStroke(1.5f);
    private static final Stroke EDGE_THICK = new BasicStroke(2f);
    private static final int RADIUS_CARD = 7;
    private static final int RADIUS_PANEL = 12;

    /** A five-point star in unit coordinates, one point up, spanning [-0.5, 0.5]. */
    private static final Shape STAR = buildStar();

    private static Shape buildStar() {
        // Path2D.Double, not Path2D: the Android shim keeps moveTo/lineTo/closePath on the
        // concrete class only, and a Shape built through the abstract type does not compile there.
        Path2D.Double star = new Path2D.Double();
        int points = 5;
        double outer = 0.5;
        double inner = 0.21;
        for (int i = 0; i < points * 2; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / points;
            double radius = i % 2 == 0 ? outer : inner;
            double x = Math.cos(angle) * radius;
            double y = Math.sin(angle) * radius;
            if (i == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.closePath();
        return star;
    }

    private GoalsArt() {
    }

    /**
     * The content panel: the measured vertical gradient over a light-teal edge.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     */
    public static void panel(Graphics2D g, int x, int y, int w, int h) {
        Paint old = g.getPaint();
        g.setPaint(new GradientPaint(x, y, PANEL_TOP, x, y + Math.max(1, h), PANEL_BOTTOM));
        g.fillRoundRect(x, y, w, h, RADIUS_PANEL, RADIUS_PANEL);
        g.setPaint(old);
        g.setColor(PANEL_EDGE);
        g.setStroke(EDGE);
        g.drawRoundRect(x, y, w, h, RADIUS_PANEL, RADIUS_PANEL);
    }

    /**
     * A base card: the locked state's fill and stroke, no veil.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     */
    public static void card(Graphics2D g, int x, int y, int w, int h) {
        fillCard(g, x, y, w, h, CARD_FILL, CARD_STROKE, EDGE);
    }

    /**
     * A complete card: the achieved state's green fill and bright border.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     */
    public static void cardComplete(Graphics2D g, int x, int y, int w, int h) {
        fillCard(g, x, y, w, h, COMPLETE_FILL, COMPLETE_BORDER, EDGE_THICK);
    }

    /**
     * The Everything card: olive fill, bright yellow border, the strongest card on any tab.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     */
    public static void cardEverything(Graphics2D g, int x, int y, int w, int h) {
        fillCard(g, x, y, w, h, EVERYTHING_FILL, EVERYTHING_BORDER, EDGE_THICK);
    }

    /**
     * One rounded fill-and-edge rectangle, the body every Goals card shares.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     * @param fill the fill colour
     * @param edge the stroke colour
     * @param stroke the stroke
     */
    private static void fillCard(Graphics2D g, int x, int y, int w, int h, Color fill,
            Color edge, Stroke stroke) {
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, RADIUS_CARD, RADIUS_CARD);
        g.setColor(edge);
        g.setStroke(stroke);
        g.drawRoundRect(x, y, w, h, RADIUS_CARD, RADIUS_CARD);
    }

    /**
     * The inset icon tile: a rounded square one step lighter than the card around it.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param size the side
     */
    public static void tile(Graphics2D g, int x, int y, int size) {
        g.setColor(TILE_FILL);
        g.fillRoundRect(x, y, size, size, 6, 6);
    }

    /**
     * The status pill under a challenge's title.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     */
    public static void pill(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(PILL_FILL);
        g.fillRoundRect(x, y, w, h, h / 2, h / 2);
        g.setColor(CARD_STROKE);
        g.setStroke(EDGE);
        g.drawRoundRect(x, y, w, h, h / 2, h / 2);
    }

    /**
     * One category pill of the tab row: gold when active (the spec's canonical choice),
     * dark teal when not.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     * @param active whether this is the selected tab
     * @param hovered whether the pointer rests on an inactive tab
     */
    public static void tab(Graphics2D g, int x, int y, int w, int h, boolean active,
            boolean hovered) {
        if (active) {
            Paint old = g.getPaint();
            g.setPaint(new GradientPaint(x, y, TAB_ACTIVE_TOP, x, y + Math.max(1, h),
                    TAB_ACTIVE_BOTTOM));
            g.fillRoundRect(x, y, w, h, h / 2, h / 2);
            g.setPaint(old);
            return;
        }
        g.setColor(hovered ? TILE_FILL : TAB_IDLE_FILL);
        g.fillRoundRect(x, y, w, h, h / 2, h / 2);
        g.setColor(CARD_STROKE);
        g.setStroke(EDGE);
        g.drawRoundRect(x, y, w, h, h / 2, h / 2);
    }

    /**
     * A progress bar on the 16:9-style tabs (achievements summary, milestones): the track sits
     * one step <em>lighter</em> than its card with a light stroke, per the mock — an empty
     * meter reads as an unfilled meter, not a hole. The gold fill is drawn inside the stroke.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     * @param fraction the filled fraction, clamped inside
     */
    public static void bar(Graphics2D g, int x, int y, int w, int h, double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        g.setColor(BAR_TRACK);
        g.fillRoundRect(x, y, w, h, h / 2, h / 2);
        g.setColor(BAR_TRACK_EDGE);
        g.setStroke(EDGE);
        g.drawRoundRect(x, y, w, h, h / 2, h / 2);
        int fill = (int) Math.round(w * f);
        if (fill > 0) {
            g.setColor(BAR_FILL);
            int inset = h >= 10 ? 2 : 1;
            g.fillRoundRect(x + inset, y + inset, Math.max(h - 2 * inset, fill - 2 * inset),
                    h - 2 * inset, (h - 2 * inset) / 2, (h - 2 * inset) / 2);
        }
    }

    /**
     * A progress bar on the Collections tab: its own mock measures a darker, unstroked slot,
     * so the row keeps the near-black pill the references show there.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     * @param fraction the filled fraction, clamped inside
     */
    public static void barDark(Graphics2D g, int x, int y, int w, int h, double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        g.setColor(BAR_TRACK_DARK);
        g.fillRoundRect(x, y, w, h, h / 2, h / 2);
        int fill = (int) Math.round(w * f);
        if (fill > 0) {
            g.setColor(BAR_FILL);
            g.fillRoundRect(x, y, Math.max(h, fill), h, h / 2, h / 2);
        }
    }

    /**
     * The red flag glyph (the Collections Challenges row): a pole with a triangular pennant,
     * the mock's recognisable identity for "a challenge to run".
     *
     * @param g the context
     * @param cx the centre x
     * @param cy the centre y
     * @param size the glyph's height
     * @param pole the pole colour
     * @param pennant the flag colour
     */
    public static void flag(Graphics2D g, double cx, double cy, double size, Color pole,
            Color pennant) {
        int top = (int) Math.round(cy - size / 2.0);
        int bottom = (int) Math.round(cy + size / 2.0);
        int px = (int) Math.round(cx - size / 2.0);
        g.setColor(pole);
        g.fillRect(px, top, 2, bottom - top);
        int wave = (int) Math.round(size * 0.62);
        int drop = (int) Math.round(size * 0.38);
        g.setColor(pennant);
        g.fillPolygon(new int[] {px + 2, px + 2 + wave, px + 2},
                new int[] {top, top + drop / 2, top + drop}, 3);
    }

    /**
     * The gold star glyph (the Collections Achievements row), the mock's identity for "an
     * achievement to win"; drawn from a prebuilt unit path, so the render allocates nothing.
     *
     * @param g the context
     * @param cx the centre x
     * @param cy the centre y
     * @param size the star's width
     * @param color the fill
     */
    public static void star(Graphics2D g, double cx, double cy, double size, Color color) {
        if (size <= 0) {
            return;
        }
        g.translate(cx, cy);
        g.scale(size, size);
        g.setColor(color);
        g.fill(STAR);
        g.scale(1 / size, 1 / size);
        g.translate(-cx, -cy);
    }

    /**
     * A carousel arrow: a rounded square with a pale chevron.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param size the side
     * @param pointsLeft {@code true} for the left arrow
     */
    public static void chevronButton(Graphics2D g, int x, int y, int size, boolean pointsLeft) {
        g.setColor(CARD_FILL);
        g.fillRoundRect(x, y, size, size, 8, 8);
        g.setColor(CARD_STROKE);
        g.setStroke(EDGE);
        g.drawRoundRect(x, y, size, size, 8, 8);
        ProceduralArt.drawChevron(g, x + size / 2.0, y + size / 2.0, size * 0.55,
                ProceduralArt.TEXT_LIGHT, pointsLeft);
    }

    /**
     * The small bar-chart glyph (the Tier tile, the Progress row).
     *
     * @param g the context
     * @param cx the centre x
     * @param cy the centre y
     * @param size the glyph size
     * @param color the bar colour
     */
    public static void barChart(Graphics2D g, double cx, double cy, double size, Color color) {
        g.setColor(color);
        double base = cy + size * 0.42;
        double w = size * 0.2;
        double gap = size * 0.14;
        double left = cx - (3 * w + 2 * gap) / 2.0;
        for (int i = 0; i < 3; i++) {
            double h = size * (0.4 + 0.28 * i);
            g.fillRoundRect((int) Math.round(left + i * (w + gap)),
                    (int) Math.round(base - h), (int) Math.round(w), (int) Math.round(h), 2, 2);
        }
    }

    /**
     * The artist's-palette glyph (the Colours row): a tan disc with paint dabs and the thumb
     * hole, the one multi-colour glyph the collection rows carry.
     *
     * @param g the context
     * @param cx the centre x
     * @param cy the centre y
     * @param size the glyph size
     */
    public static void paletteDisc(Graphics2D g, double cx, double cy, double size) {
        int d = (int) Math.round(size);
        int x = (int) Math.round(cx - size / 2.0);
        int y = (int) Math.round(cy - size / 2.0);
        g.setColor(new Color(0xE0, 0xA8, 0x68));
        g.fillOval(x, y, d, (int) Math.round(d * 0.82));
        g.setColor(new Color(0xC8, 0x8A, 0x4A));
        g.drawOval(x, y, d, (int) Math.round(d * 0.82));
        Color[] dabs = {new Color(0xD9, 0x4A, 0x4A), ProceduralArt.COIN_GOLD,
            new Color(0x62, 0xB7, 0x4B), new Color(0x54, 0x9E, 0xD8)};
        for (int i = 0; i < dabs.length; i++) {
            g.setColor(dabs[i]);
            int dx = x + d / 6 + (i % 2) * d / 3;
            int dy = y + d / 6 + (i / 2) * d / 4;
            g.fillOval(dx, dy, d / 7, d / 7);
        }
        g.setColor(CARD_FILL);
        g.fillOval(x + d - d / 4, y + (int) Math.round(d * 0.5), d / 5, d / 5);
    }

    /**
     * The achieved badge: the green circle with the white check.
     *
     * @param g the context
     * @param cx the centre x
     * @param cy the centre y
     * @param size the circle size
     */
    public static void checkBadge(Graphics2D g, double cx, double cy, double size) {
        int d = (int) Math.round(size);
        int x = (int) Math.round(cx - size / 2.0);
        int y = (int) Math.round(cy - size / 2.0);
        g.setColor(CHECK_CIRCLE);
        g.fillOval(x, y, d, d);
        g.setColor(new Color(0x3D, 0x8A, 0x30));
        g.drawOval(x, y, d, d);
        ProceduralArt.drawCheck(g, cx, cy, size * 0.62, ProceduralArt.TEXT_LIGHT);
    }

    /**
     * The screen's display title: the light gold on the near-black outline, with the soft
     * shadow the outline's second pass reads as.
     *
     * @param g the context, already carrying the title font
     * @param text the title
     * @param x the left edge
     * @param baseline the baseline
     */
    public static void displayTitle(Graphics2D g, String text, double x, double baseline) {
        TextPainter.drawOutlined(g, text, x, baseline, Align.LEFT, TITLE_FILL, TITLE_OUTLINE, 3);
    }

    /** The scrollbar thumb's width: the mock's ~20px at 941 scales to ~9 here (ff-vision 1.2). */
    public static final int THUMB_W = 9;

    /**
     * The scrollbar thumb: only drawn when the content actually scrolls, per the shop's rule.
     *
     * @param g the context
     * @param x the left edge of the thumb
     * @param trackTop the first row of the track
     * @param trackH the height of the track
     * @param contentH the full height of the scrolled content
     * @param scroll the current offset
     */
    public static void scrollbar(Graphics2D g, int x, int trackTop, int trackH, double contentH,
            double scroll) {
        double max = Math.max(1.0, contentH - trackH);
        int thumbH = Math.max(20, (int) Math.round(trackH * (trackH / Math.max(1.0, contentH))));
        int thumbY = (int) Math.round(trackTop + (trackH - thumbH) * (scroll / max));
        g.setColor(SCROLLBAR);
        g.fillRoundRect(x, thumbY, THUMB_W, thumbH, THUMB_W, THUMB_W);
    }
}
