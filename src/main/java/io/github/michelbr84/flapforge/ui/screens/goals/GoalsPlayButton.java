package io.github.michelbr84.flapforge.ui.screens.goals;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.component.Button;
import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;

/**
 * The Challenges tab's call to action, in the reference's two states.
 *
 * <p>Locked: the slate fill with the pale border, the pale padlock beside the pale label — the
 * one locked button in the game that keeps its own colours instead of the shared button paint,
 * because the reference measures this exact pair. Available: the gold accent, dark ink, the
 * treatment the active tab pill and the primary navigation item share. Focused: a ring, so
 * keyboard play sees what a pointer hover sees.
 *
 * <p>The locked state is <em>visual</em>: the button stays on the focus ring and activating it
 * is simply inert (the screen builds no run source for a locked challenge). The tab's own test
 * walks onto the locked button with two DOWN presses, so the lock may not take the button off
 * the ring.
 */
public final class GoalsPlayButton extends Button {

    /** Radius of the reference's rounded action. */
    public static final int RADIUS = 10;
    /** Side of the padlock drawn beside a locked label. */
    public static final int LOCK_SIZE = 13;
    /** Room between the padlock and the label. */
    public static final int LOCK_GAP = 6;
    /** The label's size, the spec's 4.2%-of-width call-to-action figure at 420. */
    public static final int FONT_SIZE = 18;

    private static final Stroke FOCUS_STROKE = new BasicStroke(2f);

    private boolean locked;

    /**
     * Creates the button.
     *
     * @param label the label
     * @param onAction what playing does
     */
    public GoalsPlayButton(String label, Runnable onAction) {
        super(label, onAction);
    }

    /**
     * Puts the button in its locked (slate, padlock, pale label) state.
     *
     * @param newLocked whether the selected challenge is locked
     */
    public void setLocked(boolean newLocked) {
        this.locked = newLocked;
    }

    @Override
    public void render(Graphics2D g) {
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        if (!locked) {
            Paint old = g.getPaint();
            g.setPaint(new GradientPaint(bx, by, GoalsArt.TAB_ACTIVE_TOP, bx, by + bh,
                    GoalsArt.TAB_ACTIVE_BOTTOM));
            g.fillRoundRect(bx, by, bw, bh, RADIUS, RADIUS);
            g.setPaint(old);
            g.setColor(ProceduralArt.TEXT_DARK);
        } else {
            g.setColor(GoalsArt.CTA_DISABLED_FILL);
            g.fillRoundRect(bx, by, bw, bh, RADIUS, RADIUS);
            g.setColor(GoalsArt.CTA_DISABLED_BORDER);
            g.drawRoundRect(bx, by, bw, bh, RADIUS, RADIUS);
            g.setColor(GoalsArt.PALE_INK);
        }
        g.setFont(Fonts.bold(FONT_SIZE));
        double textWidth = TextPainter.width(g, text());
        double centreX = bx + bw / 2.0;
        double baseline = TextPainter.centeredBaseline(g, by + bh / 2.0);
        if (!locked) {
            TextPainter.draw(g, text(), centreX, baseline, TextPainter.Align.CENTER);
        } else {
            double group = LOCK_SIZE + LOCK_GAP + textWidth;
            double left = centreX - group / 2.0;
            ProceduralArt.drawPadlock(g, left + LOCK_SIZE / 2.0, by + bh / 2.0, LOCK_SIZE,
                    GoalsArt.PALE_INK);
            TextPainter.draw(g, text(), left + LOCK_SIZE + LOCK_GAP, baseline);
        }
        if (isFocused()) {
            Stroke old = g.getStroke();
            g.setStroke(FOCUS_STROKE);
            g.setColor(locked ? GoalsArt.PALE_INK : ProceduralArt.TEXT_DARK);
            g.drawRoundRect(bx + 1, by + 1, bw - 2, bh - 2, RADIUS, RADIUS);
            g.setStroke(old);
        }
    }
}
