package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The bottom navigation of the home hub (D17): a band across the playfield with a row of
 * {@link NavButton}s, the primary one taller and centred. The bar is a {@link Panel}, so a
 * screen renders it once and registers its items with the focus ring through
 * {@link #registerFocusables}.
 *
 * <p>Every item shares one vertical centre on purpose: Up and Down never move inside the row,
 * Up from any item leaves it, and the taller primary item does not sit in the way of the side
 * items' Up.
 */
public class NavBar extends Panel {

    private final List<NavButton> items = new ArrayList<>();
    private final List<NavButton> readOnlyItems = Collections.unmodifiableList(items);

    /** Creates an empty bar without the panel body; the band is drawn instead. */
    public NavBar() {
        setBackground(false);
        setPadding(0);
    }

    @Override
    public <T extends UiNode> T add(T child) {
        super.add(child);
        if (child instanceof NavButton item) {
            items.add(item);
        }
        return child;
    }

    /**
     * The items in insertion order.
     *
     * @return an unmodifiable view
     */
    public List<NavButton> buttons() {
        return readOnlyItems;
    }

    /**
     * Looks an item up by id.
     *
     * @param id the id handed to the item's constructor
     * @return the item
     * @throws IllegalArgumentException when no item has that id
     */
    public NavButton button(String id) {
        Objects.requireNonNull(id, "id");
        for (NavButton item : items) {
            if (item.id().equals(id)) {
                return item;
            }
        }
        throw new IllegalArgumentException("no navigation item " + id);
    }

    /**
     * Lays the items out left to right inside the bar, the primary one on its own height. All
     * offsets are relative to the bar's top edge.
     *
     * @param sideTop the top of a side item below the bar's top
     * @param sideHeight the height of a side item
     * @param primaryTop the top of the primary item below the bar's top
     * @param primaryHeight the height of the primary item
     * @param sideWidth the width of a side item
     * @param primaryWidth the width of the primary item
     * @param gap the space between items
     * @param margin the space before the first item
     */
    public void layoutRow(double sideTop, double sideHeight, double primaryTop,
            double primaryHeight, double sideWidth, double primaryWidth, double gap,
            double margin) {
        double cx = x() + margin;
        for (NavButton item : items) {
            if (item.isPrimary()) {
                item.setBounds(cx, y() + primaryTop, primaryWidth, primaryHeight);
                cx += primaryWidth + gap;
            } else {
                item.setBounds(cx, y() + sideTop, sideWidth, sideHeight);
                cx += sideWidth + gap;
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        ProceduralArt.navBand(g, (int) Math.round(y()), (int) Math.round(height()));
        super.render(g);
    }
}
