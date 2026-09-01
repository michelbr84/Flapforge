package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The explanation that appears next to the thing the player is pointing at or has focused (D17,
 * M4): the unlock condition of a locked bird, the wording of an upgrade effect, why a purchase is
 * refused.
 *
 * <p>Three rules make it usable rather than noisy:
 * <ul>
 *   <li><b>It waits.</b> A target has to stay the target for {@value #DELAY_TICKS} ticks before
 *       anything is drawn, so moving the pointer across a grid of cards does not flash seven
 *       tooltips. The delay is counted in simulation ticks, never in frame time, so a screenshot
 *       taken at tick {@code n} always looks the same.</li>
 *   <li><b>It wraps.</b> The text is broken into lines no wider than {@link #maxWidth()} using the
 *       font the tooltip is actually drawn with, which is measured on the first render and again
 *       whenever the text, the width or the text scale changes.</li>
 *   <li><b>It stays inside the playfield.</b> The box is placed under its target, flipped above it
 *       when there is no room below, and finally clamped into
 *       {@code [0, Playfield.WIDTH] x [0, Playfield.HEIGHT]} — a tooltip that hangs off the screen
 *       is worse than no tooltip at all.</li>
 * </ul>
 *
 * <p>The screen drives it with one call per tick: {@link #update(UiNode, String)} with the node
 * the pointer is over (or the focused node) and the text it explains, or {@code null} to hide it.
 * Tooltips are readouts, never focusable, and they are drawn last so they sit above the panel they
 * explain.
 */
public class Tooltip extends UiNode {

    /** Ticks a target must be held before the tooltip appears. */
    public static final int DELAY_TICKS = 20;
    /** Point size of the tooltip text. */
    public static final int FONT_SIZE = 12;
    /** Inner padding of the box. */
    public static final int PADDING = 6;
    /** Height of one wrapped line. */
    public static final int LINE_HEIGHT = 14;
    /** Default maximum width of the box. */
    public static final int DEFAULT_MAX_WIDTH = 220;
    /** Gap between the target and the box. */
    public static final int GAP = 4;

    private static final Color FILL = new Color(0x10, 0x24, 0x27, 0xF0);
    private static final Color BORDER = new Color(0xF5, 0xC5, 0x42, 0xB0);

    private final List<String> lines = new ArrayList<>();
    private final List<String> readOnlyLines = Collections.unmodifiableList(lines);
    private UiNode target;
    private String text = "";
    private int heldTicks;
    private int maxWidth = DEFAULT_MAX_WIDTH;
    private boolean layoutDirty = true;
    private double laidOutScale;

    /** Creates a hidden tooltip. */
    public Tooltip() {
        setFocusable(false);
        setVisible(false);
    }

    /**
     * Points the tooltip at a target and counts the hold.
     *
     * <p>Calling it with the same target and text as last tick advances the delay; a different
     * target (or {@code null}, or blank text) restarts it and hides the box.
     *
     * @param newTarget the node being hovered or focused, or {@code null} for none
     * @param newText the text explaining it, or {@code null} for none
     */
    public void update(UiNode newTarget, String newText) {
        String wanted = newText == null ? "" : newText;
        if (newTarget == null || wanted.isBlank()) {
            hide();
            return;
        }
        if (newTarget != target || !wanted.equals(text)) {
            target = newTarget;
            text = wanted;
            heldTicks = 0;
            layoutDirty = true;
            setVisible(false);
            return;
        }
        if (heldTicks < DELAY_TICKS) {
            heldTicks++;
            if (heldTicks >= DELAY_TICKS) {
                setVisible(true);
            }
        }
    }

    /** Hides the tooltip and forgets its target. */
    public void hide() {
        target = null;
        text = "";
        heldTicks = 0;
        setVisible(false);
    }

    /**
     * The node the tooltip explains.
     *
     * @return the target, or {@code null} when there is none
     */
    public UiNode target() {
        return target;
    }

    /**
     * The text the tooltip shows.
     *
     * @return the text, empty when there is none
     */
    public String text() {
        return text;
    }

    /**
     * Ticks the target has been held for.
     *
     * @return the count, capped at {@value #DELAY_TICKS}
     */
    public int heldTicks() {
        return heldTicks;
    }

    /**
     * Whether the box is drawn.
     *
     * @return {@code true} once the delay elapsed on a target with text
     */
    public boolean isShowing() {
        return isVisible() && target != null && !text.isBlank();
    }

    /**
     * The widest the box may become.
     *
     * @return logical pixels
     */
    public int maxWidth() {
        return maxWidth;
    }

    /**
     * Changes the maximum width.
     *
     * @param newMaxWidth logical pixels (clamped to at least four times the padding)
     */
    public void setMaxWidth(int newMaxWidth) {
        int wanted = Math.max(4 * PADDING, newMaxWidth);
        if (wanted != maxWidth) {
            maxWidth = wanted;
            layoutDirty = true;
        }
    }

    /**
     * The wrapped lines, available after the first render.
     *
     * @return an unmodifiable view
     */
    public List<String> lines() {
        return readOnlyLines;
    }

    /**
     * Wraps the text and places the box next to its target, both in logical coordinates.
     *
     * <p>Called by {@link #render(Graphics2D)}; a test that wants the bounds without drawing can
     * call it with the graphics it would have drawn into.
     *
     * @param g the context whose font metrics measure the text
     */
    public void layout(Graphics2D g) {
        if (target == null) {
            return;
        }
        if (layoutDirty || laidOutScale != Fonts.textScale()) {
            g.setFont(Fonts.regular(FONT_SIZE));
            wrap(g);
            layoutDirty = false;
            laidOutScale = Fonts.textScale();
        }
        g.setFont(Fonts.regular(FONT_SIZE));
        double width = 0;
        for (String line : lines) {
            width = Math.max(width, TextPainter.width(g, line));
        }
        double w = Math.min(maxWidth, width + 2 * PADDING);
        double h = lines.size() * LINE_HEIGHT + 2 * PADDING;
        double x = target.centerX() - w / 2;
        double y = target.y() + target.height() + GAP;
        if (y + h > Playfield.HEIGHT) {
            // No room below: flip above the target, and only clamp when that does not fit either.
            y = target.y() - h - GAP;
        }
        setBounds(clamp(x, w, Playfield.WIDTH), clamp(y, h, Playfield.HEIGHT), w, h);
    }

    /**
     * Keeps one axis of the box inside the playfield.
     *
     * @param value the wanted top or left edge
     * @param size the box size on that axis
     * @param limit the playfield size on that axis
     * @return the clamped edge
     */
    private static double clamp(double value, double size, double limit) {
        if (size >= limit) {
            return 0;
        }
        return Math.max(0, Math.min(value, limit - size));
    }

    /**
     * Breaks the text into lines that fit the maximum width, splitting on spaces and, for a word
     * that is longer than the whole box, between characters.
     *
     * @param g the context whose font metrics measure the words
     */
    private void wrap(Graphics2D g) {
        lines.clear();
        double limit = maxWidth - 2.0 * PADDING;
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (line.length() == 0) {
                line.append(word);
            } else if (TextPainter.width(g, line + " " + word) <= limit) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
            while (TextPainter.width(g, line.toString()) > limit && line.length() > 1) {
                int cut = line.length() - 1;
                while (cut > 1 && TextPainter.width(g, line.substring(0, cut)) > limit) {
                    cut--;
                }
                lines.add(line.substring(0, cut));
                line.delete(0, cut);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (!isShowing()) {
            return;
        }
        layout(g);
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        g.setColor(FILL);
        g.fillRoundRect(bx, by, bw, bh, 8, 8);
        g.setColor(BORDER);
        g.drawRoundRect(bx, by, bw, bh, 8, 8);
        g.setFont(Fonts.regular(FONT_SIZE));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        double baseline = by + PADDING + LINE_HEIGHT - 4.0;
        for (String line : lines) {
            TextPainter.draw(g, line, bx + (double) PADDING, baseline);
            baseline += LINE_HEIGHT;
        }
    }
}
