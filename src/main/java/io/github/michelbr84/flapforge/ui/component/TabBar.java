package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A row of tabs (D17, M4): the three upgrade trees, the four shop groups.
 *
 * <p>It is one focusable node rather than one node per tab, for the same reason {@link ListView}
 * is: the arrows have to change the <em>value</em>, not move the focus. Left and right step through
 * the tabs while the bar has focus, a click picks the tab under the pointer, and a disabled tab —
 * a tree the player has not unlocked — is skipped by the keyboard but still drawn and still
 * clickable, because selecting it is how the player reads why it is locked.
 *
 * <p>The bar consumes the arrow presses it acts on: a screen calls {@link #tick(InputFrame)} before
 * handing the frame to its {@link io.github.michelbr84.flapforge.ui.FocusRing} and, when this
 * returns {@code true}, strips {@code LEFT}/{@code RIGHT} from the frame first, so one press never
 * both changes the tab and jumps the focus into the content below.
 */
public class TabBar extends UiNode implements Adjustable {

    /** Point size of a tab label. */
    public static final int FONT_SIZE = 13;
    /** Height the bar is usually given. */
    public static final int DEFAULT_HEIGHT = 28;

    private static final Color TAB_IDLE = new Color(0x1C, 0x3A, 0x3E, 0xC8);
    private static final Color TAB_HOVER = new Color(0x2E, 0x6B, 0x72, 0xDC);
    private static final Color TAB_SELECTED = new Color(0x3C, 0x8A, 0x92, 0xF0);
    private static final Color BORDER = new Color(0x8F, 0xDD, 0xE3, 0x99);
    private static final Stroke FOCUS_STROKE = new BasicStroke(2f);

    private final List<Tab> tabs = new ArrayList<>();
    private final List<Tab> readOnlyTabs = Collections.unmodifiableList(tabs);
    private int selected;
    private IntConsumer onChange;

    /** Creates an empty bar. */
    public TabBar() {
    }

    /**
     * Appends a tab.
     *
     * @param id the stable id a screen and a test address the tab by
     * @param label the translated label
     * @return the tab
     */
    public Tab add(String id, String label) {
        Tab tab = new Tab(id, label);
        tabs.add(tab);
        return tab;
    }

    /** Removes every tab. */
    public void clear() {
        tabs.clear();
        selected = 0;
    }

    /**
     * The tabs in insertion order.
     *
     * @return an unmodifiable view
     */
    public List<Tab> tabs() {
        return readOnlyTabs;
    }

    /**
     * The number of tabs.
     *
     * @return the count
     */
    public int size() {
        return tabs.size();
    }

    /**
     * The selected index.
     *
     * @return the index, {@code 0} when the bar is empty
     */
    public int selectedIndex() {
        return selected;
    }

    /**
     * The selected tab's id.
     *
     * @return the id, or {@code null} when the bar is empty
     */
    public String selectedId() {
        return tabs.isEmpty() ? null : tabs.get(selected).id();
    }

    /**
     * Selects a tab by index and notifies the listener when it changed.
     *
     * @param index the index (out-of-range values are ignored)
     * @return {@code true} when the selection changed
     */
    public boolean select(int index) {
        if (index < 0 || index >= tabs.size() || index == selected) {
            return false;
        }
        selected = index;
        if (onChange != null) {
            onChange.accept(selected);
        }
        return true;
    }

    /**
     * Selects a tab by id.
     *
     * @param id the tab id
     * @return {@code true} when the selection changed
     */
    public boolean select(String id) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id().equals(id)) {
                return select(i);
            }
        }
        return false;
    }

    /**
     * Selects a tab without notifying the listener (rebuilding after a language switch).
     *
     * @param index the index
     */
    public void selectQuietly(int index) {
        if (index >= 0 && index < tabs.size()) {
            selected = index;
        }
    }

    /**
     * Installs the listener run whenever the selection changes.
     *
     * @param listener the listener, or {@code null} for none
     */
    public void setOnChange(IntConsumer listener) {
        this.onChange = listener;
    }

    @Override
    public boolean adjust(int steps) {
        if (steps == 0 || tabs.size() < 2) {
            return false;
        }
        int direction = steps > 0 ? 1 : -1;
        int index = selected;
        for (int taken = 0; taken < Math.abs(steps); taken++) {
            int next = index;
            // Skip disabled tabs rather than stopping on them: the keyboard should never park on
            // something it cannot open, while a click still can (and shows why it is locked).
            for (int k = 0; k < tabs.size(); k++) {
                next = Math.floorMod(next + direction, tabs.size());
                if (tabs.get(next).isEnabled()) {
                    break;
                }
            }
            index = next;
        }
        return select(index);
    }

    /**
     * Applies one tick of input: the arrows while focused, and clicks on a tab.
     *
     * @param input the tick input with the pointer in the screen's coordinate space
     * @return {@code true} when the selection changed this tick
     */
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
            int index = indexAt(input.mouseX(), input.mouseY());
            if (index >= 0) {
                changed |= select(index);
            }
        }
        if (changed) {
            UiCues.move();
        }
        return changed;
    }

    /**
     * The tab under a point.
     *
     * @param px the x in logical coordinates
     * @param py the y in logical coordinates
     * @return the index, or {@code -1} when the point is outside the bar
     */
    public int indexAt(double px, double py) {
        if (tabs.isEmpty() || !contains(px, py)) {
            return -1;
        }
        double tabWidth = width() / tabs.size();
        int index = (int) ((px - x()) / tabWidth);
        return Math.max(0, Math.min(tabs.size() - 1, index));
    }

    @Override
    public void render(Graphics2D g) {
        if (tabs.isEmpty()) {
            return;
        }
        double tabWidth = width() / tabs.size();
        g.setFont(Fonts.bold(FONT_SIZE));
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tx = (int) Math.round(x() + i * tabWidth);
            int tw = (int) Math.round(x() + (i + 1) * tabWidth) - tx;
            int ty = (int) Math.round(y());
            int th = (int) Math.round(height());
            boolean isSelected = i == selected;
            g.setColor(isSelected ? TAB_SELECTED : (isHovered() ? TAB_HOVER : TAB_IDLE));
            g.fillRoundRect(tx, ty, tw, th, 8, 8);
            g.setColor(BORDER);
            g.drawRoundRect(tx, ty, tw, th, 8, 8);
            g.setColor(tab.isEnabled()
                    ? (isSelected ? ProceduralArt.TEXT_LIGHT : ProceduralArt.TEXT_MUTED)
                    : ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, tab.label(), tx + tw / 2.0,
                    TextPainter.centeredBaseline(g, ty + th / 2.0), Align.CENTER);
            if (!tab.isEnabled()) {
                g.drawLine(tx + 6, ty + th - 4, tx + tw - 6, ty + th - 4);
            }
        }
        if (isFocused()) {
            Stroke old = g.getStroke();
            g.setStroke(FOCUS_STROKE);
            g.setColor(ProceduralArt.COIN_GOLD);
            int sx = (int) Math.round(x() + selected * tabWidth);
            g.drawRoundRect(sx + 1, (int) Math.round(y()) + 1, (int) Math.round(tabWidth) - 2,
                    (int) Math.round(height()) - 2, 8, 8);
            g.setStroke(old);
        }
    }

    /** One tab: an id, a label and whether the keyboard may stop on it. */
    public static final class Tab {

        private final String id;
        private String label;
        private boolean enabled = true;

        /**
         * Creates a tab.
         *
         * @param id the tab id
         * @param label the translated label
         */
        Tab(String id, String label) {
            this.id = Objects.requireNonNull(id, "id");
            this.label = Objects.requireNonNull(label, "label");
        }

        /**
         * The tab id.
         *
         * @return the id
         */
        public String id() {
            return id;
        }

        /**
         * The label.
         *
         * @return the translated label
         */
        public String label() {
            return label;
        }

        /**
         * Changes the label (a language switch).
         *
         * @param newLabel the label
         */
        public void setLabel(String newLabel) {
            this.label = Objects.requireNonNull(newLabel, "label");
        }

        /**
         * Whether the arrows stop on this tab.
         *
         * @return {@code true} when enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the tab.
         *
         * @param newEnabled the flag
         */
        public void setEnabled(boolean newEnabled) {
            this.enabled = newEnabled;
        }
    }
}
