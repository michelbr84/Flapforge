package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Graphics2D;
import java.util.Objects;

/**
 * A push button with a centred label (D17). The body comes from
 * {@link ProceduralArt#button}, whose state follows the node's enabled, focused and hovered
 * flags.
 */
public class Button extends UiNode {

    /** Default label size. */
    public static final int DEFAULT_FONT_SIZE = 20;

    private String text;
    private int fontSize = DEFAULT_FONT_SIZE;

    /**
     * Creates a button.
     *
     * @param text the label
     * @param onAction the action run on activation, or {@code null}
     */
    public Button(String text, Runnable onAction) {
        this.text = Objects.requireNonNull(text, "text");
        setOnAction(onAction);
    }

    /**
     * The label.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * Changes the label.
     *
     * @param text the new text
     */
    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * Changes the label size.
     *
     * @param fontSize the point size
     */
    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    /**
     * Current visual state.
     *
     * @return the state derived from the flags
     */
    public ButtonState state() {
        return ButtonState.of(isEnabled(), isFocused(), isHovered());
    }

    @Override
    public void render(Graphics2D g) {
        ButtonState state = state();
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        ProceduralArt.button(g, bx, by, bw, bh, state);
        g.setFont(Fonts.bold(fontSize));
        g.setColor(ProceduralArt.buttonTextColor(state));
        TextPainter.drawInBox(g, text, bx, by, bw, bh);
    }
}
