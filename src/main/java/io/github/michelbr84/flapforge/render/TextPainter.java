package io.github.michelbr84.flapforge.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Text drawing helpers on a {@link Graphics2D} in logical coordinates (D17).
 *
 * <p>All positions are baseline positions; alignment moves the text horizontally around the given
 * x. {@link #drawOutlined} draws the outline by repeating the string around the fill position, so
 * it allocates nothing per call (no glyph vectors) and stays crisp at every viewport scale.
 */
public final class TextPainter {

    /** Horizontal alignment of a text run relative to its anchor x. */
    public enum Align {
        /** Anchor is the left edge. */
        LEFT,
        /** Anchor is the horizontal centre. */
        CENTER,
        /** Anchor is the right edge. */
        RIGHT
    }

    private TextPainter() {
    }

    /**
     * Enables text and shape antialiasing on a context.
     *
     * @param g the context
     */
    public static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /**
     * Width of a text in the context's current font.
     *
     * @param g the context
     * @param text the text
     * @return the advance width in logical pixels
     */
    public static int width(Graphics2D g, String text) {
        return g.getFontMetrics().stringWidth(text);
    }

    /**
     * Width of a text in a given font.
     *
     * @param g the context (its transform does not affect the result)
     * @param font the font
     * @param text the text
     * @return the advance width in logical pixels
     */
    public static int width(Graphics2D g, Font font, String text) {
        return g.getFontMetrics(font).stringWidth(text);
    }

    /**
     * Baseline that vertically centres the current font's cap height around {@code centerY}.
     *
     * @param g the context
     * @param centerY the vertical centre
     * @return the baseline y
     */
    public static double centeredBaseline(Graphics2D g, double centerY) {
        FontMetrics fm = g.getFontMetrics();
        return centerY + (fm.getAscent() - fm.getDescent()) / 2.0;
    }

    /**
     * Draws left-aligned text at a baseline.
     *
     * @param g the context (current font and colour)
     * @param text the text
     * @param x the left edge
     * @param baseline the baseline y
     */
    public static void draw(Graphics2D g, String text, double x, double baseline) {
        g.drawString(text, (float) x, (float) baseline);
    }

    /**
     * Draws aligned text at a baseline.
     *
     * @param g the context (current font and colour)
     * @param text the text
     * @param x the anchor x
     * @param baseline the baseline y
     * @param align how the text relates to the anchor
     */
    public static void draw(Graphics2D g, String text, double x, double baseline, Align align) {
        g.drawString(text, (float) alignedX(g, text, x, align), (float) baseline);
    }

    /**
     * Draws text centred on an x coordinate.
     *
     * @param g the context (current font and colour)
     * @param text the text
     * @param centerX the horizontal centre
     * @param baseline the baseline y
     */
    public static void drawCentered(Graphics2D g, String text, double centerX, double baseline) {
        draw(g, text, centerX, baseline, Align.CENTER);
    }

    /**
     * Draws text ending at an x coordinate.
     *
     * @param g the context (current font and colour)
     * @param text the text
     * @param rightX the right edge
     * @param baseline the baseline y
     */
    public static void drawRight(Graphics2D g, String text, double rightX, double baseline) {
        draw(g, text, rightX, baseline, Align.RIGHT);
    }

    /**
     * Draws text centred both ways inside a box.
     *
     * @param g the context (current font and colour)
     * @param text the text
     * @param x the box left edge
     * @param y the box top edge
     * @param w the box width
     * @param h the box height
     */
    public static void drawInBox(Graphics2D g, String text, double x, double y, double w,
            double h) {
        drawCentered(g, text, x + w / 2, centeredBaseline(g, y + h / 2));
    }

    /**
     * Draws text with a solid outline.
     *
     * @param g the context (current font)
     * @param text the text
     * @param x the anchor x
     * @param baseline the baseline y
     * @param align how the text relates to the anchor
     * @param fill the fill colour
     * @param outline the outline colour
     * @param thickness the outline thickness in logical pixels (0 draws no outline)
     */
    public static void drawOutlined(Graphics2D g, String text, double x, double baseline,
            Align align, Color fill, Color outline, int thickness) {
        float ax = (float) alignedX(g, text, x, align);
        float by = (float) baseline;
        if (thickness > 0) {
            // High contrast (D17): one extra pixel of outline, in whichever of black or white is
            // further from the fill on the luminance scale — the outline can then never merge
            // with the text it carries.
            Color chosen = Accessibility.isHighContrast()
                    ? (Accessibility.luminance(fill.getRGB() & 0xFFFFFF) >= 128
                        ? OUTLINE_BLACK : OUTLINE_WHITE)
                    : outline;
            int t = Accessibility.outlineThickness(thickness);
            g.setColor(chosen);
            for (int dy = -t; dy <= t; dy++) {
                for (int dx = -t; dx <= t; dx++) {
                    if (dx != 0 || dy != 0) {
                        g.drawString(text, ax + dx, by + dy);
                    }
                }
            }
        }
        g.setColor(fill);
        g.drawString(text, ax, by);
    }

    private static final Color OUTLINE_BLACK = new Color(0x000000);
    private static final Color OUTLINE_WHITE = new Color(0xFFFFFF);

    private static double alignedX(Graphics2D g, String text, double x, Align align) {
        switch (align) {
            case CENTER:
                return x - width(g, text) / 2.0;
            case RIGHT:
                return x - width(g, text);
            default:
                return x;
        }
    }
}
