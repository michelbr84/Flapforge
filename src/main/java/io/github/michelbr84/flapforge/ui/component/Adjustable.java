package io.github.michelbr84.flapforge.ui.component;

/**
 * A component whose value the left and right arrows change (D17).
 *
 * <p>The focus ring uses the arrows to move between nodes, which in a single-column settings
 * list means left and right have nothing to move to; a screen therefore offers them to the
 * focused node first through this interface, and only the nodes that implement it react.
 */
public interface Adjustable {

    /**
     * Moves the value by whole steps.
     *
     * @param steps how many steps, negative for left
     * @return {@code true} when the value actually changed
     */
    boolean adjust(int steps);
}
