package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An on/off switch with a label and a pill (D17).
 *
 * <p>Enter, Space and a click all flip it through {@link UiNode#activate()}; the left and right
 * arrows set it to off and on explicitly ({@link Adjustable}), which is what a player expects
 * from a settings row. The on and off words are supplied by the screen so they come from
 * {@code Strings} and follow a language switch.
 */
public class Toggle extends UiNode implements Adjustable {

    /** Width of the pill. */
    public static final int PILL_WIDTH = 46;
    /** Height of the pill. */
    public static final int PILL_HEIGHT = 22;

    private static final Color OFF_TRACK = new Color(0x24, 0x3A, 0x3E, 0xC8);
    private static final Color ON_TRACK = new Color(0x6F, 0xD1, 0xA8);
    private static final Color KNOB = new Color(0xF4F8F8);
    private static final Stroke FOCUS_STROKE = new BasicStroke(2f);

    private String label;
    private String onText = "On";
    private String offText = "Off";
    private boolean value;
    private Consumer<Boolean> onChange;
    private int fontSize = 15;

    /**
     * Creates a toggle.
     *
     * @param label the row label
     * @param value the initial state
     */
    public Toggle(String label, boolean value) {
        this.label = Objects.requireNonNull(label, "label");
        this.value = value;
        setOnAction(this::toggle);
    }

    /**
     * The row label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Changes the row label (a language switch).
     *
     * @param label the new label
     */
    public void setLabel(String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /**
     * Sets the words shown next to the pill.
     *
     * @param onText the word for "on"
     * @param offText the word for "off"
     */
    public void setStateText(String onText, String offText) {
        this.onText = Objects.requireNonNull(onText, "onText");
        this.offText = Objects.requireNonNull(offText, "offText");
    }

    /**
     * The current state.
     *
     * @return {@code true} when on
     */
    public boolean value() {
        return value;
    }

    /**
     * Sets the state, running the change callback when it moved.
     *
     * @param newValue the new state
     * @return {@code true} when the state changed
     */
    public boolean setValue(boolean newValue) {
        if (newValue == value) {
            return false;
        }
        value = newValue;
        if (onChange != null) {
            onChange.accept(value);
        }
        return true;
    }

    /**
     * Sets the state without running the change callback (loading a stored state).
     *
     * @param newValue the new state
     */
    public void setValueQuietly(boolean newValue) {
        this.value = newValue;
    }

    /** Flips the state. */
    public void toggle() {
        setValue(!value);
    }

    /**
     * Installs the callback run whenever the state changes.
     *
     * @param onChange the callback, or {@code null} for none
     */
    public void setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
    }

    /**
     * Changes the label size.
     *
     * @param fontSize the point size
     */
    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    @Override
    public boolean adjust(int steps) {
        if (steps == 0) {
            return false;
        }
        return setValue(steps > 0);
    }

    /**
     * Applies one tick of input: the arrows while focused. Clicks and Enter arrive through the
     * focus ring as an activation.
     *
     * @param input the tick input
     * @return {@code true} when the state changed this tick
     */
    public boolean tick(InputFrame input) {
        if (!isEnabled() || !isVisible() || !isFocused()) {
            return false;
        }
        boolean changed = false;
        if (input.isJustPressed(InputAction.LEFT)) {
            changed |= adjust(-1);
        }
        if (input.isJustPressed(InputAction.RIGHT)) {
            changed |= adjust(1);
        }
        if (changed) {
            // A flip through activate() is the focus ring's confirm cue; the arrows are a move.
            UiCues.move();
        }
        return changed;
    }

    @Override
    public void render(Graphics2D g) {
        double cy = centerY();
        g.setFont(Fonts.regular(fontSize));
        g.setColor(isEnabled() ? (isFocused() || isHovered() ? ProceduralArt.TEXT_LIGHT
                : ProceduralArt.TEXT_MUTED) : ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, label, x(), TextPainter.centeredBaseline(g, cy));

        int px = (int) Math.round(x() + width() - PILL_WIDTH);
        int py = (int) Math.round(cy - PILL_HEIGHT / 2.0);
        g.setColor(value ? ON_TRACK : OFF_TRACK);
        g.fillRoundRect(px, py, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT, PILL_HEIGHT);
        int knob = PILL_HEIGHT - 6;
        int kx = value ? px + PILL_WIDTH - knob - 3 : px + 3;
        g.setColor(KNOB);
        g.fillOval(kx, py + 3, knob, knob);
        if (isFocused()) {
            Stroke old = g.getStroke();
            g.setStroke(FOCUS_STROKE);
            g.setColor(ON_TRACK);
            g.drawRoundRect(px - 3, py - 3, PILL_WIDTH + 6, PILL_HEIGHT + 6, PILL_HEIGHT,
                    PILL_HEIGHT);
            g.setStroke(old);
        }

        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, value ? onText : offText, px - 10,
                TextPainter.centeredBaseline(g, cy), Align.RIGHT);
    }
}
