package io.github.michelbr84.flapforge.ui;

import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Focus management for one screen (D17): keeps the ordered list of nodes, knows which one has
 * focus and turns the per-tick {@link InputFrame} into focus moves and activations.
 *
 * <ul>
 *   <li>Arrows move focus to the nearest focusable node in that direction (weighted so nodes
 *       roughly in line win over nearer but sideways ones); with nothing in that direction the
 *       focus wraps to the farthest node on the opposite side. Tab moves to the next node in
 *       insertion order.</li>
 *   <li>Enter ({@code CONFIRM}) and Space activate the focused node. Presses during the first
 *       {@link ScreenManager#TRANSITION_GRACE_TICKS} ticks after {@link #resetTransition()} are
 *       ignored, matching the manager's grace for held keys.</li>
 *   <li>Moving the pointer onto a node focuses it; a left click on a node focuses and activates
 *       it. Hover flags are refreshed every tick.</li>
 * </ul>
 * Every activation goes through {@link UiNode#activate()}.
 */
public final class FocusRing {

    private final List<UiNode> nodes = new ArrayList<>();
    private final List<UiNode> readOnlyNodes = Collections.unmodifiableList(nodes);
    private UiNode focused;
    private double lastMouseX;
    private double lastMouseY;
    private boolean mouseSeen;
    private int graceTicks;

    /**
     * Appends a node.
     *
     * @param <T> the node type
     * @param node the node
     * @return the node, for chaining into field initialisers
     */
    public <T extends UiNode> T add(T node) {
        Objects.requireNonNull(node, "node");
        nodes.add(node);
        return node;
    }

    /**
     * Removes a node, dropping focus if it had it.
     *
     * @param node the node
     */
    public void remove(UiNode node) {
        nodes.remove(node);
        if (focused == node) {
            focus(null);
        }
    }

    /** Removes every node. */
    public void clear() {
        focus(null);
        nodes.clear();
    }

    /**
     * The nodes in insertion order.
     *
     * @return an unmodifiable view
     */
    public List<UiNode> nodes() {
        return readOnlyNodes;
    }

    /**
     * The focused node.
     *
     * @return the node, or {@code null}
     */
    public UiNode focused() {
        return focused;
    }

    /**
     * Moves focus to a node (or clears it).
     *
     * @param node the node, or {@code null}
     */
    public void focus(UiNode node) {
        if (node == focused) {
            return;
        }
        if (focused != null) {
            focused.setFocused(false);
        }
        focused = node;
        if (node != null) {
            node.setFocused(true);
        }
    }

    /**
     * Focuses the first node that can take focus.
     *
     * @return {@code true} when one was found
     */
    public boolean focusFirst() {
        for (UiNode n : nodes) {
            if (n.canFocus()) {
                focus(n);
                return true;
            }
        }
        return false;
    }

    /**
     * Starts the post-transition grace (call from {@code Screen.onEnter}) and forgets the last
     * pointer position so a pointer resting over a node does not steal focus on entry.
     */
    public void resetTransition() {
        graceTicks = ScreenManager.TRANSITION_GRACE_TICKS;
        mouseSeen = false;
    }

    /**
     * Applies one tick of input.
     *
     * @param input the tick input with the pointer in logical coordinates
     * @return the node activated this tick, or {@code null}
     */
    public UiNode handle(InputFrame input) {
        boolean inGrace = graceTicks > 0;
        if (inGrace) {
            graceTicks--;
        }
        if (focused != null && !focused.canFocus()) {
            focus(null);
        }
        if (focused == null) {
            focusFirst();
        }

        double mx = input.mouseX();
        double my = input.mouseY();
        boolean moved = mouseSeen && (mx != lastMouseX || my != lastMouseY);
        mouseSeen = true;
        lastMouseX = mx;
        lastMouseY = my;
        UiNode under = nodeAt(mx, my);
        for (UiNode n : nodes) {
            n.setHovered(n == under && n.isEnabled());
        }
        if (moved && under != null && under.canFocus()) {
            focus(under);
        }

        UiNode activated = null;
        if (input.isMouseJustPressed(Keys.BUTTON_LEFT) && under != null && under.canFocus()) {
            focus(under);
            activated = under;
        }

        if (input.isJustPressed(InputAction.UP)) {
            move(0, -1);
        }
        if (input.isJustPressed(InputAction.DOWN)) {
            move(0, 1);
        }
        if (input.isJustPressed(InputAction.LEFT)) {
            move(-1, 0);
        }
        if (input.isJustPressed(InputAction.RIGHT)) {
            move(1, 0);
        }
        if (hasRawKey(input, Keys.TAB)) {
            next();
        }
        if (activated == null && !inGrace && focused != null
                && (input.isJustPressed(InputAction.CONFIRM) || hasRawKey(input, Keys.SPACE))) {
            activated = focused;
        }
        if (activated != null) {
            activated.activate();
        }
        return activated;
    }

    /**
     * Draws every visible node in insertion order.
     *
     * @param g the context in logical coordinates
     */
    public void render(Graphics2D g) {
        for (UiNode n : nodes) {
            if (n.isVisible()) {
                n.render(g);
            }
        }
    }

    /**
     * Topmost (last added) node under a point.
     *
     * @param px the x
     * @param py the y
     * @return the node, or {@code null}
     */
    public UiNode nodeAt(double px, double py) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            UiNode n = nodes.get(i);
            if (n.contains(px, py)) {
                return n;
            }
        }
        return null;
    }

    private static boolean hasRawKey(InputFrame input, int code) {
        List<Integer> downs = input.rawKeyDowns();
        for (int i = 0; i < downs.size(); i++) {
            if (downs.get(i) == code) {
                return true;
            }
        }
        return false;
    }

    private void move(int dx, int dy) {
        if (focused == null) {
            focusFirst();
            return;
        }
        double fx = focused.centerX();
        double fy = focused.centerY();
        UiNode best = null;
        double bestScore = Double.MAX_VALUE;
        UiNode wrap = null;
        double wrapPrimary = -1;
        double wrapSecondary = Double.MAX_VALUE;
        for (UiNode n : nodes) {
            if (n == focused || !n.canFocus()) {
                continue;
            }
            double ddx = n.centerX() - fx;
            double ddy = n.centerY() - fy;
            double primary = dx * ddx + dy * ddy;
            double secondary = Math.abs(dy * ddx - dx * ddy);
            if (primary > 0.5) {
                double score = primary * primary + 4 * secondary * secondary;
                if (score < bestScore) {
                    best = n;
                    bestScore = score;
                }
            } else if (primary < -0.5) {
                double behind = -primary;
                if (behind > wrapPrimary + 0.5
                        || (Math.abs(behind - wrapPrimary) <= 0.5 && secondary < wrapSecondary)) {
                    wrap = n;
                    wrapPrimary = behind;
                    wrapSecondary = secondary;
                }
            }
        }
        if (best != null) {
            focus(best);
        } else if (wrap != null) {
            focus(wrap);
        }
    }

    private void next() {
        if (nodes.isEmpty()) {
            return;
        }
        int start = focused == null ? -1 : nodes.indexOf(focused);
        for (int k = 1; k <= nodes.size(); k++) {
            UiNode n = nodes.get((start + k + nodes.size()) % nodes.size());
            if (n.canFocus()) {
                focus(n);
                return;
            }
        }
    }
}
