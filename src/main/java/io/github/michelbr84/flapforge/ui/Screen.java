package io.github.michelbr84.flapforge.ui;

import io.github.michelbr84.flapforge.input.InputFrame;
import java.awt.Graphics2D;

/**
 * One screen or overlay in the {@link ScreenManager} stack (D17).
 *
 * <p>Screens are ticked at the simulation rate with the per-tick {@link InputFrame} (mouse
 * coordinates already in logical playfield space) and rendered with an interpolation alpha.
 * Overlays render on top of the screen below them and swallow input while on the stack.
 */
public interface Screen {

    /** Called when the screen becomes part of the stack. */
    default void onEnter() {
    }

    /** Called when the screen is removed from the stack. */
    default void onExit() {
    }

    /**
     * Advances the screen by one tick.
     *
     * @param input the input of this tick
     */
    void tick(InputFrame input);

    /**
     * Draws the screen into a context in logical coordinates.
     *
     * @param g the graphics context
     * @param alpha interpolation factor in {@code [0, 1)}
     */
    void render(Graphics2D g, double alpha);

    /**
     * Tells whether a run is in progress on this screen, which the 60-second autosave must not
     * interrupt (D15: never while the phase is {@code FLYING}, {@code BOSS} or
     * {@code CHOOSING_MODIFIER}).
     *
     * @return {@code true} while the screen is running a live run
     */
    default boolean blocksAutosave() {
        return false;
    }

    /**
     * Tells whether the screen is an overlay drawn over the screen below it.
     *
     * @return {@code true} for overlays; {@code false} for full screens
     */
    default boolean isOverlay() {
        return false;
    }
}
