package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Objects;

/**
 * A horizontal bar filled to a fraction in {@code [0, 1]}, with a label on the left and an
 * optional value text on the right (D17). It is what the run summary shows the level progress
 * with, and what M8's milestone rows will reuse.
 *
 * <p>Bars are not focusable: a progress bar is a readout, never a control. The label and the value
 * text are handed in already localised, exactly like every other component, so a language switch
 * is a {@link #setLabel(String)} plus a {@link #setValueText(String)}.
 *
 * <p>The fill is clamped rather than validated: a caller computing {@code xpIntoLevel / span}
 * across a level-up boundary can legitimately produce a value slightly outside the range, and a
 * bar that throws there would take the screen down with it.
 */
public class ProgressBar extends UiNode {

    /** Height of the bar itself; the label sits above it. */
    public static final int BAR_HEIGHT = 10;
    /** Corner radius of the track and the fill. */
    public static final int RADIUS = 5;
    /** Point size of the label and the value text. */
    public static final int FONT_SIZE = 13;

    private static final Color TRACK = new Color(0x10, 0x24, 0x26, 0xCC);
    private static final Color TRACK_BORDER = new Color(0xFF, 0xFF, 0xFF, 0x40);

    private String label;
    private String valueText = "";
    private double value;
    private Color fill = ProceduralArt.COIN_GOLD;

    /**
     * Creates an empty bar.
     *
     * @param label the text drawn above the bar, already localised
     */
    public ProgressBar(String label) {
        this(label, 0);
    }

    /**
     * Creates a bar.
     *
     * @param label the text drawn above the bar, already localised
     * @param value the filled fraction, clamped into {@code [0, 1]}
     */
    public ProgressBar(String label, double value) {
        this.label = Objects.requireNonNull(label, "label");
        setValue(value);
        setFocusable(false);
    }

    /**
     * The label.
     *
     * @return the text
     */
    public String label() {
        return label;
    }

    /**
     * Changes the label (a language switch).
     *
     * @param label the new text
     */
    public void setLabel(String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /**
     * The text drawn at the right end of the label line.
     *
     * @return the text, empty when none
     */
    public String valueText() {
        return valueText;
    }

    /**
     * Changes the value text.
     *
     * @param valueText the new text, or {@code null} for none
     */
    public void setValueText(String valueText) {
        this.valueText = valueText == null ? "" : valueText;
    }

    /**
     * The filled fraction.
     *
     * @return a value in {@code [0, 1]}
     */
    public double value() {
        return value;
    }

    /**
     * Sets the filled fraction.
     *
     * @param value the fraction; values outside {@code [0, 1]} are clamped and a non-finite value
     *     reads as 0
     */
    public final void setValue(double value) {
        this.value = Double.isFinite(value) ? MathUtil.clamp(value, 0, 1) : 0;
    }

    /**
     * Changes the fill colour.
     *
     * @param fill the colour
     */
    public void setFillColor(Color fill) {
        this.fill = Objects.requireNonNull(fill, "fill");
    }

    /**
     * The fill colour.
     *
     * @return the colour
     */
    public Color fillColor() {
        return fill;
    }

    @Override
    public void render(Graphics2D g) {
        int x = (int) Math.round(x());
        int w = (int) Math.round(width());
        int barY = (int) Math.round(y() + height() - BAR_HEIGHT);
        g.setFont(Fonts.regular(FONT_SIZE));
        g.setColor(ProceduralArt.TEXT_MUTED);
        double labelBaseline = y() + height() - BAR_HEIGHT - 4;
        TextPainter.draw(g, label, x, labelBaseline);
        if (!valueText.isEmpty()) {
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, valueText, x + (double) w, labelBaseline, Align.RIGHT);
        }
        g.setColor(TRACK);
        g.fillRoundRect(x, barY, w, BAR_HEIGHT, RADIUS, RADIUS);
        int filled = (int) Math.round(w * value);
        if (filled > 0) {
            g.setColor(fill);
            g.fillRoundRect(x, barY, Math.max(RADIUS, filled), BAR_HEIGHT, RADIUS, RADIUS);
        }
        g.setColor(TRACK_BORDER);
        g.drawRoundRect(x, barY, w, BAR_HEIGHT, RADIUS, RADIUS);
    }
}
