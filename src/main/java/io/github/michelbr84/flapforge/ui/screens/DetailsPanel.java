package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.Panel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.List;

/**
 * Everything the next run resolves to (M11): every unlocked ability with its level, kind, tags
 * and effect, and the stat breakdown of the run that would start right now, on a panel the bird
 * selection's "See details" opens.
 *
 * <p>The rows are built by the screen — the ids a caller addresses them by are unchanged — and
 * handed over here; the panel scrolls and draws them in a viewport tall enough to read, which is
 * the point of moving them off the screen itself. Like the run-setup panel it owns its own
 * {@link FocusRing}, so while it is up nothing under the veil can be reached.
 */
final class DetailsPanel extends Panel {

    /** Left edge. */
    static final int X = 8;
    /** Top edge. */
    static final int Y = 60;
    /** Width. */
    static final int W = 404;
    /** Height. */
    static final int H = 512;
    /** Baseline of the title. */
    static final int TITLE_BASELINE = Y + 28;
    /** Point size of the title. */
    static final int TITLE_SIZE = 18;
    /** Top of the scrolling viewport. */
    static final int VIEW_TOP = 100;
    /** Bottom of the scrolling viewport. */
    static final int VIEW_BOTTOM = 520;
    /** Left edge of a row's label. */
    static final int TEXT_LEFT = 20;
    /** Right edge a row's value ends at. */
    static final int TEXT_RIGHT = 396;
    /** Height of one detail row. */
    static final int ROW_H = 15;
    /** Height of one header row. */
    static final int HEADER_H = 17;
    /** Logical pixels one wheel notch scrolls. */
    static final int WHEEL_STEP = 30;
    /** Logical pixels one arrow press scrolls. */
    static final int KEY_STEP = 45;

    private static final Color SCROLLBAR = new Color(0xF4, 0xF8, 0xF8, 0x50);
    private static final Color DIMMED = new Color(0xE8, 0x5A, 0x4A);

    private final Button done;
    private final FocusRing ring = new FocusRing();
    private List<BirdSelectionScreen.Row> rows = List.of();
    private double contentHeight;
    private double scroll;
    private String title = "";

    /**
     * Creates the panel.
     *
     * @param onDone what the Done button does
     */
    DetailsPanel(Runnable onDone) {
        setBounds(X, Y, W, H);
        setPadding(16);
        done = add(new Button("", onDone));
        done.setFontSize(16);
        done.setBounds(X + 16, Y + 468, W - 32, 40);
        registerFocusables(ring);
        setOpen(false);
    }

    /**
     * The ring the panel takes its input on.
     *
     * @return the ring
     */
    FocusRing ring() {
        return ring;
    }

    /**
     * The Done button.
     *
     * @return the button
     */
    Button doneButton() {
        return done;
    }

    /**
     * Changes the title (a language switch).
     *
     * @param newTitle the text
     */
    void setTitle(String newTitle) {
        this.title = newTitle == null ? "" : newTitle;
    }

    /**
     * Changes the Done label (a language switch).
     *
     * @param text the text
     */
    void setDoneText(String text) {
        done.setText(text);
    }

    /**
     * Points the panel at the rows the screen built.
     *
     * @param newRows the rows in display order
     * @param newContentHeight how tall they are together
     */
    void setRows(List<BirdSelectionScreen.Row> newRows, double newContentHeight) {
        this.rows = newRows == null ? List.of() : newRows;
        this.contentHeight = Math.max(0, newContentHeight);
        this.scroll = MathUtil.clamp(scroll, 0, maxScroll());
    }

    /**
     * Whether the panel is up.
     *
     * @return {@code true} while it is shown
     */
    boolean isOpen() {
        return isVisible();
    }

    /**
     * Shows or hides the panel.
     *
     * @param open whether the panel is up
     */
    void setOpen(boolean open) {
        setVisible(open);
        for (UiNode child : children()) {
            child.setVisible(open);
        }
        if (open) {
            scroll = 0;
            ring.resetTransition();
            ring.focus(done);
        }
    }

    /**
     * Scrolls the rows by wheel notches.
     *
     * @param notches the notches, positive scrolls towards the top
     */
    void scrollBy(int notches) {
        if (notches == 0) {
            return;
        }
        scroll = MathUtil.clamp(scroll - notches * (double) WHEEL_STEP, 0, maxScroll());
    }

    /**
     * Scrolls the rows with the arrows: the panel holds one button, so up and down are free.
     *
     * @param input the tick's input
     */
    void scrollKeys(InputFrame input) {
        if (input.isJustPressed(InputAction.DOWN)) {
            scroll = MathUtil.clamp(scroll + KEY_STEP, 0, maxScroll());
        }
        if (input.isJustPressed(InputAction.UP)) {
            scroll = MathUtil.clamp(scroll - KEY_STEP, 0, maxScroll());
        }
    }

    /**
     * How far the rows are scrolled.
     *
     * @return the offset in logical pixels
     */
    double scroll() {
        return scroll;
    }

    /**
     * The largest offset the rows can take.
     *
     * @return the offset in logical pixels
     */
    double maxScroll() {
        return Math.max(0, contentHeight - (VIEW_BOTTOM - VIEW_TOP));
    }

    @Override
    public void render(Graphics2D g) {
        super.render(g);
        g.setFont(Fonts.bold(TITLE_SIZE));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, title, X + W / 2.0, TITLE_BASELINE);
        Shape unclipped = g.getClip();
        g.clipRect(X + 4, VIEW_TOP, W - 8, VIEW_BOTTOM - VIEW_TOP);
        double dy = VIEW_TOP - scroll;
        g.translate(0.0, dy);
        for (BirdSelectionScreen.Row row : rows) {
            renderRow(g, row);
        }
        g.translate(0.0, -dy);
        g.setClip(unclipped);
        renderScrollbar(g);
    }

    private void renderRow(Graphics2D g, BirdSelectionScreen.Row row) {
        double baseline = row.y() + (row.header() ? HEADER_H - 5 : ROW_H - 4);
        if (row.header()) {
            g.setFont(Fonts.bold(12));
            g.setColor(row.dimmed() ? DIMMED : ProceduralArt.TEXT_LIGHT);
        } else {
            g.setFont(Fonts.regular(11));
            g.setColor(row.dimmed() ? DIMMED : ProceduralArt.TEXT_MUTED);
        }
        TextPainter.draw(g, row.label(), TEXT_LEFT + (row.header() ? 0.0 : 10.0), baseline);
        if (!row.value().isEmpty()) {
            g.setColor(row.header() ? ProceduralArt.COIN_GOLD : ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, row.value(), TEXT_RIGHT, baseline, Align.RIGHT);
        }
    }

    private void renderScrollbar(Graphics2D g) {
        double max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackH = VIEW_BOTTOM - VIEW_TOP;
        int thumbH = (int) Math.max(20, trackH * (trackH / contentHeight));
        int thumbY = VIEW_TOP + (int) Math.round((trackH - thumbH) * (scroll / max));
        g.setColor(SCROLLBAR);
        g.fillRoundRect(X + W - 8, thumbY, 4, thumbH, 4, 4);
    }
}
