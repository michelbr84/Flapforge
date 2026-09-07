package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Graphics2D;

/**
 * The coin readout of the home hub (D17, D13): a {@link CurrencyDisplay} on a
 * {@link ProceduralArt#chip} plate that can be activated to open the shop. The readout keeps
 * its roll-up, driven by {@link #tick()}; the number ends at the right edge so the left of the
 * plate stays clear (E19 reserves it for a second currency; none exists today).
 */
public class CurrencyChip extends UiNode {

    /** Inner horizontal padding. */
    public static final int PADDING = 10;

    private final CurrencyDisplay display = new CurrencyDisplay();

    /**
     * Creates a chip.
     *
     * @param onAction the action run on activation, or {@code null}
     */
    public CurrencyChip(Runnable onAction) {
        display.setAlign(Align.RIGHT);
        display.setFontSize(15);
        setOnAction(onAction);
    }

    /**
     * The readout inside the chip.
     *
     * @return the display
     */
    public CurrencyDisplay display() {
        return display;
    }

    /** Advances the readout's roll-up by one tick. */
    public void tick() {
        display.tick();
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
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        ProceduralArt.chip(g, bx, by, bw, bh, state());
        display.setBounds(x() + PADDING, y(), Math.max(0, width() - 2 * PADDING), height());
        display.render(g);
    }
}
