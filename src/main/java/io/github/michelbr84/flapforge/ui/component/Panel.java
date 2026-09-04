package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A container drawing an optional {@link ProceduralArt#panel} background and its children, with
 * column and grid layout helpers (D17).
 *
 * <p>A panel draws its children itself, so a screen renders the panel and registers the focusable
 * children with its {@link FocusRing} through {@link #registerFocusables(FocusRing)} rather than
 * drawing them twice. Panels are never focusable.
 */
public class Panel extends UiNode {

    /** Default inner padding. */
    public static final int DEFAULT_PADDING = 16;

    private final List<UiNode> children = new ArrayList<>();
    private final List<UiNode> readOnlyChildren = Collections.unmodifiableList(children);
    private boolean background = true;
    private double padding = DEFAULT_PADDING;

    /** Creates an empty panel with a background. */
    public Panel() {
        setFocusable(false);
    }

    /**
     * Appends a child.
     *
     * @param <T> the child type
     * @param child the child
     * @return the child, for chaining into field initialisers
     */
    public <T extends UiNode> T add(T child) {
        Objects.requireNonNull(child, "child");
        children.add(child);
        return child;
    }

    /**
     * The children in insertion order.
     *
     * @return an unmodifiable view
     */
    public List<UiNode> children() {
        return readOnlyChildren;
    }

    /**
     * Whether the panel body is drawn.
     *
     * @return {@code true} to draw the background
     */
    public boolean hasBackground() {
        return background;
    }

    /**
     * Enables or disables the panel body.
     *
     * @param background the flag
     */
    public void setBackground(boolean background) {
        this.background = background;
    }

    /**
     * Inner padding used by the layout helpers.
     *
     * @return logical pixels
     */
    public double padding() {
        return padding;
    }

    /**
     * Changes the inner padding.
     *
     * @param padding logical pixels (negative values are clamped to 0)
     */
    public void setPadding(double padding) {
        this.padding = Math.max(0, padding);
    }

    /**
     * Adds every focusable child (recursing into nested panels) to a focus ring, in order.
     *
     * @param ring the ring
     */
    public void registerFocusables(FocusRing ring) {
        for (UiNode child : children) {
            if (child instanceof Panel nested) {
                nested.registerFocusables(ring);
            } else if (child.isFocusable()) {
                ring.add(child);
            }
        }
    }

    /**
     * Stacks the children vertically inside the padded bounds, each as wide as the content area.
     *
     * @param itemHeight the height of each child
     * @param gap the space between children
     */
    public void layoutColumn(double itemHeight, double gap) {
        double cx = x() + padding;
        double cw = Math.max(0, width() - 2 * padding);
        double cy = y() + padding;
        for (UiNode child : children) {
            child.setBounds(cx, cy, cw, itemHeight);
            cy += itemHeight + gap;
        }
    }

    /**
     * Arranges the children in rows of {@code columns} cells inside the padded bounds.
     *
     * @param columns the number of columns (at least 1)
     * @param cellHeight the height of each cell
     * @param gapX the horizontal space between cells
     * @param gapY the vertical space between rows
     */
    public void layoutGrid(int columns, double cellHeight, double gapX, double gapY) {
        int cols = Math.max(1, columns);
        double cw = Math.max(0, width() - 2 * padding);
        double cellWidth = Math.max(0, (cw - gapX * (cols - 1)) / cols);
        double startX = x() + padding;
        double cy = y() + padding;
        for (int i = 0; i < children.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            children.get(i).setBounds(startX + col * (cellWidth + gapX),
                    cy + row * (cellHeight + gapY), cellWidth, cellHeight);
        }
    }

    /**
     * Height a column layout of {@code count} items needs, padding included.
     *
     * @param count the number of items
     * @param itemHeight the item height
     * @param gap the gap between items
     * @param padding the inner padding
     * @return the total height
     */
    public static double columnHeight(int count, double itemHeight, double gap, double padding) {
        if (count <= 0) {
            return 2 * padding;
        }
        return 2 * padding + count * itemHeight + (count - 1) * gap;
    }

    @Override
    public void render(Graphics2D g) {
        if (background) {
            ProceduralArt.panel(g, (int) Math.round(x()), (int) Math.round(y()),
                    (int) Math.round(width()), (int) Math.round(height()));
        }
        for (UiNode child : children) {
            if (child.isVisible()) {
                child.render(g);
            }
        }
    }
}
