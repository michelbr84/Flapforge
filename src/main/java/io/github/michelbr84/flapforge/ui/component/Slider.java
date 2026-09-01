package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
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
import java.util.Locale;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * A horizontal value slider with a label and a value read-out (D17): volumes, text scale, and
 * anything else that is a number in a range.
 *
 * <p>Three input paths reach the same {@link #setValue(double)}: the left and right arrows move
 * by one {@link #step()} while the slider has focus ({@link Adjustable}), dragging the knob with
 * the left mouse button sets the value the pointer is over, and a click anywhere on the track
 * jumps there. Every change that actually moves the value runs the change callback exactly once,
 * so a screen can persist on change without debouncing clicks itself.
 */
public class Slider extends UiNode implements Adjustable {

    /** Height of the track. */
    public static final int TRACK_HEIGHT = 6;
    /** Radius of the knob. */
    public static final int KNOB_RADIUS = 8;
    /** Width reserved for the value read-out on the right. */
    public static final int VALUE_WIDTH = 62;

    private static final Color TRACK = new Color(0x24, 0x3A, 0x3E, 0xC8);
    private static final Color FILL = new Color(0x6F, 0xD1, 0xA8);
    private static final Color KNOB = new Color(0xF4F8F8);
    private static final Color KNOB_DISABLED = new Color(0x8A, 0x9A, 0x9C);
    private static final Stroke FOCUS_STROKE = new BasicStroke(2f);

    private String label;
    private final double min;
    private final double max;
    private final double step;
    private double value;
    private DoubleConsumer onChange;
    private DoubleFunction<String> valueText = v -> String.format(Locale.ROOT, "%.2f", v);
    private boolean dragging;
    private int fontSize = 15;

    /**
     * Creates a slider over {@code [0, 1]} in steps of {@code 0.05}.
     *
     * @param label the row label
     * @param value the initial value
     */
    public Slider(String label, double value) {
        this(label, 0, 1, 0.05, value);
    }

    /**
     * Creates a slider.
     *
     * @param label the row label
     * @param min the lowest value
     * @param max the highest value (must be above {@code min})
     * @param step the increment one arrow press applies
     * @param value the initial value
     */
    public Slider(String label, double min, double max, double step, double value) {
        this.label = Objects.requireNonNull(label, "label");
        if (!(max > min)) {
            throw new IllegalArgumentException("max must be above min: " + min + ".." + max);
        }
        this.min = min;
        this.max = max;
        this.step = step > 0 ? step : (max - min) / 20;
        this.value = clampToStep(value);
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
     * The lowest value.
     *
     * @return the minimum
     */
    public double min() {
        return min;
    }

    /**
     * The highest value.
     *
     * @return the maximum
     */
    public double max() {
        return max;
    }

    /**
     * The increment one arrow press applies.
     *
     * @return the step
     */
    public double step() {
        return step;
    }

    /**
     * The current value.
     *
     * @return the value
     */
    public double value() {
        return value;
    }

    /**
     * Position of the value in its range.
     *
     * @return a value in {@code [0, 1]}
     */
    public double fraction() {
        return (value - min) / (max - min);
    }

    /**
     * Sets the value, snapping it to the step and clamping it to the range. The change callback
     * runs only when the value really moved.
     *
     * @param newValue the requested value
     * @return {@code true} when the value changed
     */
    public boolean setValue(double newValue) {
        double next = clampToStep(newValue);
        if (next == value) {
            return false;
        }
        value = next;
        if (onChange != null) {
            onChange.accept(value);
        }
        return true;
    }

    /**
     * Sets the value without running the change callback (loading a stored state).
     *
     * @param newValue the requested value
     */
    public void setValueQuietly(double newValue) {
        value = clampToStep(newValue);
    }

    /**
     * Installs the callback run whenever the value changes.
     *
     * @param onChange the callback, or {@code null} for none
     */
    public void setOnChange(DoubleConsumer onChange) {
        this.onChange = onChange;
    }

    /**
     * Installs the formatter for the value read-out.
     *
     * @param valueText the formatter (never {@code null})
     */
    public void setValueText(DoubleFunction<String> valueText) {
        this.valueText = Objects.requireNonNull(valueText, "valueText");
    }

    /**
     * The value as the read-out shows it.
     *
     * @return the formatted value
     */
    public String valueText() {
        return valueText.apply(value);
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
        return steps != 0 && setValue(value + steps * step);
    }

    /**
     * Applies one tick of input: arrows while focused, drag and click on the track.
     *
     * @param input the tick input with the pointer in the screen's coordinate space
     * @return {@code true} when the value changed this tick
     */
    public boolean tick(InputFrame input) {
        if (!isEnabled() || !isVisible()) {
            dragging = false;
            return false;
        }
        boolean changed = false;
        if (isFocused()) {
            if (input.isJustPressed(InputAction.LEFT)) {
                changed |= adjust(-1);
            }
            if (input.isJustPressed(InputAction.RIGHT)) {
                changed |= adjust(1);
            }
            if (changed) {
                // Only the discrete arrow steps are audible: a drag changes the value on most
                // ticks and would machine-gun the blip.
                UiCues.move();
            }
        }
        if (input.isMouseJustPressed(Keys.BUTTON_LEFT)
                && contains(input.mouseX(), input.mouseY())) {
            dragging = true;
        }
        if (dragging && !input.isMouseHeld(Keys.BUTTON_LEFT)) {
            dragging = false;
        }
        if (dragging) {
            changed |= setValue(valueAt(input.mouseX()));
        }
        return changed;
    }

    /**
     * Whether the knob is being dragged.
     *
     * @return {@code true} while the left button is down on the slider
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * The value a pointer position on the track means.
     *
     * @param pointerX the x in the screen's coordinate space
     * @return the value
     */
    public double valueAt(double pointerX) {
        double left = trackLeft();
        double width = trackWidth();
        if (width <= 0) {
            return min;
        }
        double t = MathUtil.clamp((pointerX - left) / width, 0, 1);
        return min + t * (max - min);
    }

    private double trackLeft() {
        return x() + width() * 0.44;
    }

    private double trackWidth() {
        return Math.max(0, width() * 0.56 - VALUE_WIDTH);
    }

    private double clampToStep(double raw) {
        double clamped = MathUtil.clamp(Double.isFinite(raw) ? raw : min, min, max);
        double snapped = min + Math.round((clamped - min) / step) * step;
        return MathUtil.clamp(snapped, min, max);
    }

    @Override
    public void render(Graphics2D g) {
        double cy = centerY();
        g.setFont(Fonts.regular(fontSize));
        g.setColor(isEnabled() ? (isFocused() || isHovered() ? ProceduralArt.TEXT_LIGHT
                : ProceduralArt.TEXT_MUTED) : ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, label, x(), TextPainter.centeredBaseline(g, cy));

        double left = trackLeft();
        double w = trackWidth();
        int ty = (int) Math.round(cy - TRACK_HEIGHT / 2.0);
        g.setColor(TRACK);
        g.fillRoundRect((int) Math.round(left), ty, (int) Math.round(w), TRACK_HEIGHT,
                TRACK_HEIGHT, TRACK_HEIGHT);
        double f = fraction();
        g.setColor(isEnabled() ? FILL : KNOB_DISABLED);
        g.fillRoundRect((int) Math.round(left), ty, (int) Math.round(w * f), TRACK_HEIGHT,
                TRACK_HEIGHT, TRACK_HEIGHT);

        int kx = (int) Math.round(left + w * f);
        int ky = (int) Math.round(cy);
        g.setColor(isEnabled() ? KNOB : KNOB_DISABLED);
        g.fillOval(kx - KNOB_RADIUS, ky - KNOB_RADIUS, KNOB_RADIUS * 2, KNOB_RADIUS * 2);
        if (isFocused() || dragging) {
            Stroke old = g.getStroke();
            g.setStroke(FOCUS_STROKE);
            g.setColor(FILL);
            g.drawOval(kx - KNOB_RADIUS - 2, ky - KNOB_RADIUS - 2, KNOB_RADIUS * 2 + 4,
                    KNOB_RADIUS * 2 + 4);
            g.setStroke(old);
        }

        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, valueText(), x() + width(), TextPainter.centeredBaseline(g, cy),
                Align.RIGHT);
    }
}
