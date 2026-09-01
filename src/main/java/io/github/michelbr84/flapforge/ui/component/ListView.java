package io.github.michelbr84.flapforge.ui.component;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A list of options of which exactly one is selected — the language row, the frame-rate cap row
 * (D17).
 *
 * <p>It renders as one settings row rather than as an open list, because at 420x640 an open list
 * would push everything else off the screen: the current option sits between two arrows, the
 * left and right arrows (or a click on one of the arrow zones) move the selection, and Enter,
 * Space or a click on the value advances it. The labels are handed in already localised, so a
 * language switch is a {@link #setOptions(List)} call.
 */
public class ListView extends UiNode implements Adjustable {

    /** Width of an arrow hit zone. */
    public static final int ARROW_WIDTH = 22;

    private static final Color ARROW = new Color(0x6F, 0xD1, 0xA8);
    private static final Color ARROW_DIM = new Color(0x4A, 0x6A, 0x6C);
    private static final Stroke FOCUS_STROKE = new BasicStroke(2f);

    private final int[] arrowX = new int[3];
    private final int[] arrowY = new int[3];
    private String label;
    private final List<String> options = new ArrayList<>();
    private int selected;
    private IntConsumer onChange;
    private boolean wrapping = true;
    private int fontSize = 15;

    /**
     * Creates a list.
     *
     * @param label the row label
     * @param options the option labels, already localised (at least one)
     * @param selected the initially selected index
     */
    public ListView(String label, List<String> options, int selected) {
        this.label = Objects.requireNonNull(label, "label");
        setOptions(options);
        selectQuietly(selected);
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
     * Replaces the option labels, keeping the selected index when it still exists.
     *
     * @param newOptions the labels (at least one)
     */
    public final void setOptions(List<String> newOptions) {
        Objects.requireNonNull(newOptions, "options");
        if (newOptions.isEmpty()) {
            throw new IllegalArgumentException("a list needs at least one option");
        }
        options.clear();
        options.addAll(newOptions);
        if (selected >= options.size()) {
            selected = options.size() - 1;
        }
    }

    /**
     * The option labels.
     *
     * @return an unmodifiable snapshot
     */
    public List<String> options() {
        return Collections.unmodifiableList(new ArrayList<>(options));
    }

    /**
     * The selected index.
     *
     * @return the index
     */
    public int selectedIndex() {
        return selected;
    }

    /**
     * The selected label.
     *
     * @return the option text
     */
    public String selectedOption() {
        return options.get(selected);
    }

    /**
     * Selects an option, running the change callback when it moved.
     *
     * @param index the index (clamped into range)
     * @return {@code true} when the selection changed
     */
    public boolean select(int index) {
        int next = Math.max(0, Math.min(options.size() - 1, index));
        if (next == selected) {
            return false;
        }
        selected = next;
        if (onChange != null) {
            onChange.accept(selected);
        }
        return true;
    }

    /**
     * Selects an option without running the change callback (loading a stored state).
     *
     * @param index the index (clamped into range)
     */
    public final void selectQuietly(int index) {
        selected = Math.max(0, Math.min(options.size() - 1, index));
    }

    /**
     * Installs the callback run whenever the selection changes.
     *
     * @param onChange the callback, or {@code null} for none
     */
    public void setOnChange(IntConsumer onChange) {
        this.onChange = onChange;
    }

    /**
     * Whether moving past an end wraps to the other one.
     *
     * @param wrapping {@code true} to wrap (the default)
     */
    public void setWrapping(boolean wrapping) {
        this.wrapping = wrapping;
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
        if (steps == 0 || options.size() < 2) {
            return false;
        }
        int next = selected + steps;
        if (wrapping) {
            next = Math.floorMod(next, options.size());
        }
        return select(next);
    }

    @Override
    public boolean activate() {
        if (!isEnabled() || !isVisible()) {
            return false;
        }
        if (onAction() != null) {
            return super.activate();
        }
        adjust(1);
        return true;
    }

    /**
     * Applies one tick of input: the arrows while focused, and clicks on the two arrow zones.
     *
     * @param input the tick input with the pointer in the screen's coordinate space
     * @return {@code true} when the selection changed this tick
     */
    public boolean tick(InputFrame input) {
        if (!isEnabled() || !isVisible()) {
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
        }
        if (input.isMouseJustPressed(Keys.BUTTON_LEFT)) {
            double mx = input.mouseX();
            double my = input.mouseY();
            if (contains(mx, my)) {
                if (mx <= leftArrowX() + ARROW_WIDTH) {
                    changed |= adjust(-1);
                } else if (mx >= rightArrowX()) {
                    changed |= adjust(1);
                }
            }
        }
        if (changed) {
            // Every path here is one discrete step, so one blip per changed selection.
            UiCues.move();
        }
        return changed;
    }

    private double leftArrowX() {
        return x() + width() * 0.46;
    }

    private double rightArrowX() {
        return x() + width() - ARROW_WIDTH;
    }

    @Override
    public void render(Graphics2D g) {
        double cy = centerY();
        g.setFont(Fonts.regular(fontSize));
        g.setColor(isEnabled() ? (isFocused() || isHovered() ? ProceduralArt.TEXT_LIGHT
                : ProceduralArt.TEXT_MUTED) : ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, label, x(), TextPainter.centeredBaseline(g, cy));

        boolean active = isEnabled() && options.size() > 1;
        g.setColor(active ? ARROW : ARROW_DIM);
        triangle(g, leftArrowX() + ARROW_WIDTH, cy, -1);
        triangle(g, rightArrowX(), cy, 1);

        g.setColor(ProceduralArt.TEXT_LIGHT);
        double mid = (leftArrowX() + ARROW_WIDTH + rightArrowX()) / 2;
        TextPainter.draw(g, selectedOption(), mid, TextPainter.centeredBaseline(g, cy),
                Align.CENTER);
        if (isFocused()) {
            Stroke old = g.getStroke();
            g.setStroke(FOCUS_STROKE);
            g.setColor(ARROW);
            g.drawRoundRect((int) Math.round(leftArrowX()), (int) Math.round(y() + 2),
                    (int) Math.round(width() - (leftArrowX() - x())),
                    (int) Math.round(height() - 4), 10, 10);
            g.setStroke(old);
        }
    }

    /** Draws a small triangle pointing left ({@code dir < 0}) or right, allocation-free. */
    private void triangle(Graphics2D g, double tipX, double cy, int dir) {
        int h = 6;
        int w = 7;
        arrowX[0] = (int) Math.round(tipX);
        arrowY[0] = (int) Math.round(cy);
        arrowX[1] = (int) Math.round(tipX - dir * w);
        arrowY[1] = (int) Math.round(cy - h);
        arrowX[2] = (int) Math.round(tipX - dir * w);
        arrowY[2] = (int) Math.round(cy + h);
        g.fillPolygon(arrowX, arrowY, 3);
    }
}
