package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Graphics2D;
import java.util.Objects;

/**
 * One headline attribute of a bird (M11): an icon, a label and the value out of ten.
 *
 * <p>The badge is never focusable — it states a number, it does not do anything — but it is a
 * {@link UiNode} so a screen can hit-test it for a tooltip and lay it out with the rest. It sits
 * on a chip plate, because the hero band behind it is sky and muted text on sky is unreadable. The
 * label is ellipsised to the room the value leaves and re-measured only when the text or the text
 * scale changes, and the value reads from a string built when it was bound, so the draw path
 * adds nothing of its own.
 */
public class AttributeBadge extends UiNode {

    /** The value the scale runs to. */
    public static final int MAX = 10;
    /** Size of the icon. */
    public static final int ICON_SIZE = 12;
    /** Point size of the label. */
    public static final int LABEL_SIZE = 10;
    /** Point size of the value. */
    public static final int VALUE_SIZE = 11;
    /** Horizontal padding. */
    public static final int PADDING = 4;
    /** Room the value takes at the right edge at the designed text scale. */
    public static final int VALUE_WIDTH = 38;

    private final IconPainter icon;
    private String label = "";
    private int value;
    private String reading = "0/" + MAX;
    private String shown = "";
    private String shownSource;
    private int shownWidth = -1;
    private double shownScale;

    /**
     * Creates a badge.
     *
     * @param icon the glyph in front of the label
     */
    public AttributeBadge(IconPainter icon) {
        this.icon = Objects.requireNonNull(icon, "icon");
        setFocusable(false);
    }

    /**
     * Points the badge at an attribute.
     *
     * @param newLabel the translated attribute name
     * @param newValue the value, clamped to {@code [0, MAX]}
     */
    public void bind(String newLabel, int newValue) {
        this.label = newLabel == null ? "" : newLabel;
        int clamped = MathUtil.clamp(newValue, 0, MAX);
        if (clamped != value) {
            // Built here, not per frame: the value changes with the browsed bird.
            reading = clamped + "/" + MAX;
        }
        this.value = clamped;
    }

    /**
     * The value as drawn.
     *
     * @return {@code "<value>/<MAX>"}
     */
    public String reading() {
        return reading;
    }

    /**
     * The attribute name, as drawn.
     *
     * @return the text
     */
    public String label() {
        return label;
    }

    /**
     * The value.
     *
     * @return the value in {@code [0, MAX]}
     */
    public int value() {
        return value;
    }

    /**
     * The glyph.
     *
     * @return the painter
     */
    public IconPainter icon() {
        return icon;
    }

    @Override
    public void render(Graphics2D g) {
        int bx = (int) Math.round(x());
        int bw = (int) Math.round(width());
        double cy = centerY();
        ProceduralArt.chip(g, bx, (int) Math.round(y()), bw, (int) Math.round(height()),
                isHovered() ? ButtonState.HOVER : ButtonState.NORMAL);
        icon.paint(g, bx + PADDING + ICON_SIZE / 2.0, cy, ICON_SIZE,
                Accessibility.tone(ProceduralArt.COIN_GOLD));
        double textLeft = bx + PADDING + ICON_SIZE + 5;
        double scale = Fonts.textScale();
        // The value is drawn at the scaled point size, so the room it needs grows with it.
        int valueRoom = (int) Math.round(VALUE_WIDTH * scale);
        int room = (int) Math.round(bx + bw - PADDING - valueRoom - textLeft);
        g.setFont(Fonts.regular(LABEL_SIZE));
        if (shownSource != label || shownWidth != room || shownScale != scale) {
            // Measured only when the label, the room or the text scale changed.
            shown = TextPainter.ellipsise(g, label, Math.max(0, room));
            shownSource = label;
            shownWidth = room;
            shownScale = scale;
        }
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, shown, textLeft, TextPainter.centeredBaseline(g, cy));
        g.setFont(Fonts.bold(VALUE_SIZE));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, reading, bx + bw - PADDING,
                TextPainter.centeredBaseline(g, cy), Align.RIGHT);
    }
}
