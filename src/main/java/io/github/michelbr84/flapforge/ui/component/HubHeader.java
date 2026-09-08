package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * The header band every section screen wears (M11): the screen's title outlined on the left and
 * the coin readout on the right, in the sizes and at the coordinates the home hub uses.
 *
 * <p>The chip is focusable only when it was given somewhere to go: a screen without an
 * application context has no shop to open, so the readout is a number rather than a control the
 * focus ring stops on.
 */
public class HubHeader extends Panel {

    /** Height of the band. */
    public static final int HEIGHT = 48;
    /** Left edge of the title. */
    public static final int TITLE_X = 12;
    /** Baseline of the title. */
    public static final int TITLE_BASELINE = 34;
    /** Point size of the title. */
    public static final int TITLE_SIZE = 26;
    /** Left edge of the coin chip. */
    public static final int CHIP_X = 276;
    /** Top edge of the coin chip. */
    public static final int CHIP_Y = 12;
    /** Width of the coin chip. */
    public static final int CHIP_W = 130;
    /** Height of the coin chip. */
    public static final int CHIP_H = 30;

    private final CurrencyChip chip;
    private String title = "";
    private Color outline = ProceduralArt.TEXT_DARK;

    /**
     * Creates a header.
     *
     * @param onCoins what activating the coin chip does, or {@code null} for a plain readout
     */
    public HubHeader(Runnable onCoins) {
        setBackground(false);
        setPadding(0);
        setBounds(0, 0, Playfield.WIDTH, HEIGHT);
        chip = add(new CurrencyChip(onCoins));
        chip.setBounds(CHIP_X, CHIP_Y, CHIP_W, CHIP_H);
        chip.setFocusable(onCoins != null);
        chip.setEnabled(onCoins != null);
    }

    /**
     * The title, as drawn.
     *
     * @return the text
     */
    public String title() {
        return title;
    }

    /**
     * Changes the title (a language switch).
     *
     * @param newTitle the text
     */
    public void setTitle(String newTitle) {
        this.title = newTitle == null ? "" : newTitle;
    }

    /**
     * Changes the colour the title is outlined in (the world's letterbox tone).
     *
     * @param newOutline the colour
     */
    public void setOutline(Color newOutline) {
        this.outline = newOutline == null ? ProceduralArt.TEXT_DARK : newOutline;
    }

    /**
     * The coin chip.
     *
     * @return the chip
     */
    public CurrencyChip chip() {
        return chip;
    }

    /**
     * The coin readout inside the chip.
     *
     * @return the display
     */
    public CurrencyDisplay display() {
        return chip.display();
    }

    /** Advances the readout's roll-up by one tick. */
    public void tick() {
        chip.tick();
    }

    @Override
    public void render(Graphics2D g) {
        g.setFont(Fonts.bold(TITLE_SIZE));
        TextPainter.drawOutlined(g, title, TITLE_X, TITLE_BASELINE, Align.LEFT,
                ProceduralArt.TEXT_LIGHT, outline, 2);
        super.render(g);
    }
}
