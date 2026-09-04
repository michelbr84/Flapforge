package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Objects;

/**
 * A single line of text vertically centred in its bounds and aligned to its left edge, centre
 * or right edge (D17). Labels are not focusable.
 */
public class Label extends UiNode {

    private String text;
    private Align align;
    private Font font;
    private Color color = ProceduralArt.TEXT_LIGHT;

    /**
     * Creates a left-aligned label in the default UI font.
     *
     * @param text the text
     */
    public Label(String text) {
        this(text, Align.LEFT);
    }

    /**
     * Creates a label.
     *
     * @param text the text
     * @param align the alignment inside the bounds
     */
    public Label(String text, Align align) {
        this.text = Objects.requireNonNull(text, "text");
        this.align = Objects.requireNonNull(align, "align");
        setFocusable(false);
    }

    /**
     * The text.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * Changes the text.
     *
     * @param text the new text
     */
    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * Changes the alignment.
     *
     * @param align the alignment
     */
    public void setAlign(Align align) {
        this.align = Objects.requireNonNull(align, "align");
    }

    /**
     * Uses a specific font instead of the default 16 pt UI font.
     *
     * @param font the font, or {@code null} for the default
     */
    public void setFont(Font font) {
        this.font = font;
    }

    /**
     * Changes the text colour.
     *
     * @param color the colour
     */
    public void setColor(Color color) {
        this.color = Objects.requireNonNull(color, "color");
    }

    @Override
    public void render(Graphics2D g) {
        g.setFont(font != null ? font : Fonts.regular(16));
        g.setColor(color);
        double baseline = TextPainter.centeredBaseline(g, centerY());
        double anchor;
        switch (align) {
            case CENTER:
                anchor = centerX();
                break;
            case RIGHT:
                anchor = x() + width();
                break;
            default:
                anchor = x();
                break;
        }
        TextPainter.draw(g, text, anchor, baseline, align);
    }
}
