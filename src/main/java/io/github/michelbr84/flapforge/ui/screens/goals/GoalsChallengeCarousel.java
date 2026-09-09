package io.github.michelbr84.flapforge.ui.screens.goals;

import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.component.ListView;
import java.awt.Graphics2D;
import java.util.List;

/**
 * The Challenges tab's one-at-a-time carousel: the reference's two rounded chevron buttons
 * flanking the title band, one challenge visible, arrows and clicks stepping through the seven.
 *
 * <p>It is a {@link ListView} because the selection model the screen and the tests drive — the
 * options, the selected index, the change callback, the wrapping, the way a focused row keeps
 * Left and Right for itself — is exactly the list's, and rewriting it would be a second
 * implementation of a settled contract. What differs is the body: the list renders a settings
 * row, the carousel renders the two chevron buttons (the challenge's own title is painted by
 * the screen, centred between them), so both {@link #tick(InputFrame)} and {@link #render}
 * are overridden. Activating the carousel itself does nothing on purpose: the call to action
 * below the detail block is the tab's one action, and a press that both stepped the carousel
 * and offered the play prompt would be two outcomes for one key.
 */
public final class GoalsChallengeCarousel extends ListView {

    private int arrowInset = 8;
    private int arrowSize = 36;

    /**
     * Creates a carousel.
     *
     * @param label the row label (the tab's name, kept for the language-refresh path)
     * @param options the challenge names, already localised
     * @param selected the initially selected index
     */
    public GoalsChallengeCarousel(String label, List<String> options, int selected) {
        super(label, options, selected);
    }

    /**
     * Places the two arrows inside the band the screen lays out.
     *
     * @param newInset the room between the band's edge and an arrow
     * @param newSize the side of one arrow button
     */
    public void setArrowLayout(int newInset, int newSize) {
        this.arrowInset = newInset;
        this.arrowSize = newSize;
    }

    private double leftArrowX() {
        return x() + arrowInset;
    }

    private double rightArrowX() {
        return x() + width() - arrowInset - arrowSize;
    }

    private double arrowY() {
        return y() + (height() - arrowSize) / 2.0;
    }

    private boolean inArrow(double px, double py, double ax) {
        return px >= ax && px <= ax + arrowSize
                && py >= arrowY() && py <= arrowY() + arrowSize;
    }

    @Override
    public boolean activate() {
        // The press is consumed so the ring does not look further, but the carousel itself
        // has no action: stepping is the arrows' job, playing is the call to action's.
        return isEnabled() && isVisible();
    }

    @Override
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
                if (inArrow(mx, my, leftArrowX())) {
                    changed |= adjust(-1);
                } else if (inArrow(mx, my, rightArrowX())) {
                    changed |= adjust(1);
                }
            }
        }
        if (changed) {
            // One discrete step per press, so one blip per changed selection.
            UiCues.move();
        }
        return changed;
    }

    @Override
    public void render(Graphics2D g) {
        GoalsArt.chevronButton(g, (int) Math.round(leftArrowX()), (int) Math.round(arrowY()),
                arrowSize, true);
        GoalsArt.chevronButton(g, (int) Math.round(rightArrowX()), (int) Math.round(arrowY()),
                arrowSize, false);
    }
}
