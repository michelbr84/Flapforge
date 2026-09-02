package io.github.michelbr84.flapforge.ui;

import io.github.michelbr84.flapforge.core.geom.Aabb;
import java.awt.Graphics2D;

/**
 * Base of every UI component (D17): a rectangle in logical playfield coordinates with focus,
 * enabled, visible and hover flags, an action callback and a hit test.
 *
 * <p>Keyboard confirm, Space and a click all end in {@link #activate()}, which runs the action
 * only while the node is enabled and visible. Flags are plain fields written by the
 * {@link FocusRing} on the loop thread.
 */
public abstract class UiNode {

    private double x;
    private double y;
    private double w;
    private double h;
    private boolean focusable = true;
    private boolean enabled = true;
    private boolean visible = true;
    private boolean hovered;
    private boolean focused;
    private Runnable onAction;

    /** Creates a node with empty bounds. */
    protected UiNode() {
    }

    /**
     * Creates a node with bounds.
     *
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     */
    protected UiNode(double x, double y, double w, double h) {
        setBounds(x, y, w, h);
    }

    /**
     * Moves and resizes the node.
     *
     * @param x the left edge
     * @param y the top edge
     * @param w the width (negative values are clamped to 0)
     * @param h the height (negative values are clamped to 0)
     */
    public final void setBounds(double x, double y, double w, double h) {
        this.x = x;
        this.y = y;
        this.w = Math.max(0, w);
        this.h = Math.max(0, h);
    }

    /**
     * Moves the node without resizing it.
     *
     * @param x the left edge
     * @param y the top edge
     */
    public final void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Left edge.
     *
     * @return the x
     */
    public final double x() {
        return x;
    }

    /**
     * Top edge.
     *
     * @return the y
     */
    public final double y() {
        return y;
    }

    /**
     * Width.
     *
     * @return the width
     */
    public final double width() {
        return w;
    }

    /**
     * Height.
     *
     * @return the height
     */
    public final double height() {
        return h;
    }

    /**
     * Horizontal centre.
     *
     * @return {@code x + w / 2}
     */
    public final double centerX() {
        return x + w / 2;
    }

    /**
     * Vertical centre.
     *
     * @return {@code y + h / 2}
     */
    public final double centerY() {
        return y + h / 2;
    }

    /**
     * Bounds as a box (a new record each call; use the accessors in per-frame code).
     *
     * @return the bounds
     */
    public final Aabb bounds() {
        return new Aabb(x, y, w, h);
    }

    /**
     * Hit test in logical coordinates. Hidden nodes contain nothing.
     *
     * @param px the point x
     * @param py the point y
     * @return {@code true} when the point lies inside {@code [x, x + w) x [y, y + h)}
     */
    public boolean contains(double px, double py) {
        return visible && px >= x && px < x + w && py >= y && py < y + h;
    }

    /**
     * Whether the node takes part in focus navigation at all.
     *
     * @return {@code true} for interactive nodes
     */
    public final boolean isFocusable() {
        return focusable;
    }

    /**
     * Sets whether the node takes part in focus navigation.
     *
     * @param focusable the flag
     */
    public final void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    /**
     * Whether the node reacts to input.
     *
     * @return {@code true} when enabled
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the node.
     *
     * @param enabled the flag
     */
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Whether the node is drawn and hit-testable.
     *
     * @return {@code true} when visible
     */
    public final boolean isVisible() {
        return visible;
    }

    /**
     * Shows or hides the node.
     *
     * @param visible the flag
     */
    public final void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Whether the pointer is over the node (maintained by the {@link FocusRing}).
     *
     * @return {@code true} when hovered
     */
    public final boolean isHovered() {
        return hovered;
    }

    /**
     * Sets the hover flag.
     *
     * @param hovered the flag
     */
    public final void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    /**
     * Whether the node has keyboard focus (maintained by the {@link FocusRing}).
     *
     * @return {@code true} when focused
     */
    public final boolean isFocused() {
        return focused;
    }

    /**
     * Sets the focus flag.
     *
     * @param focused the flag
     */
    public final void setFocused(boolean focused) {
        this.focused = focused;
    }

    /**
     * Whether the node can receive focus right now.
     *
     * @return {@code true} when focusable, enabled and visible
     */
    public final boolean canFocus() {
        return focusable && enabled && visible;
    }

    /**
     * Installs the action run by {@link #activate()}.
     *
     * @param onAction the callback, or {@code null} for none
     */
    public final void setOnAction(Runnable onAction) {
        this.onAction = onAction;
    }

    /**
     * The installed action.
     *
     * @return the callback, or {@code null}
     */
    public final Runnable onAction() {
        return onAction;
    }

    /**
     * Runs the action when the node is enabled and visible. Every input path (Enter, Space,
     * click) ends here.
     *
     * @return {@code true} when the action ran
     */
    public boolean activate() {
        if (!enabled || !visible || onAction == null) {
            return false;
        }
        onAction.run();
        return true;
    }

    /**
     * Whether the node steps on {@code Left}/{@code Right} itself while focused (a list row),
     * so the {@link FocusRing} must not move the focus on those keys. The default is
     * {@code false}: arrows navigate.
     *
     * @return {@code true} when the node consumes the horizontal keys
     */
    public boolean handlesHorizontalKeys() {
        return false;
    }

    /**
     * Draws the node in logical coordinates.
     *
     * @param g the context
     */
    public abstract void render(Graphics2D g);
}
