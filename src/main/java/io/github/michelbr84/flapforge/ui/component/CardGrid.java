package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A grid of cards (D17, M4): the roster in the bird selection and the offers of one shop tab.
 *
 * <p>A card is a {@link UiNode}, so navigation is not reimplemented here — the screen registers
 * the cards with its {@link FocusRing} through {@link #registerFocusables(FocusRing)} and the ring
 * moves focus spatially with the arrows, cycles with Tab, follows the pointer and activates on
 * Enter, Space or a click, exactly as it does for buttons. What this class adds is the layout
 * (rows of {@link #columns()} cells inside its own bounds) and the card look: a portrait area a
 * screen paints into, a title, a subtitle, a badge on the right and the two states meta-progression
 * needs — {@code selected} (an accent outline), {@code locked} (a dimmed body with a padlock,
 * still focusable, because the reason it is locked is the thing the player wants to read) and
 * {@code dimmed} (the veil without the padlock: open, but not affordable right now).
 *
 * <p>Everything is laid out at once and nothing scrolls: a screen sizes the grid so its cards fit,
 * which keeps hit testing and keyboard focus reading from the same rectangles. A title or
 * subtitle wider than the space the badge leaves is measured and ends in an ellipsis, so a long
 * translation says "there is more" instead of stopping mid-word.
 */
public class CardGrid extends UiNode {

    /** Default gap between cards. */
    public static final int DEFAULT_GAP = 8;
    /** Default height of a card. */
    public static final int DEFAULT_CELL_HEIGHT = 52;
    /** Point size of a card title. */
    public static final int TITLE_SIZE = 14;
    /** Point size of a card subtitle. */
    public static final int SUBTITLE_SIZE = 11;
    /** Point size of a card badge. */
    public static final int BADGE_SIZE = 12;

    /** What a card's text ends in when it does not fit. */
    public static final String ELLIPSIS = "\u2026";

    private static final Color LOCK_VEIL = new Color(0x10, 0x1C, 0x1E, 0x9C);
    private static final Color LOCK_BODY = new Color(0xD8, 0xE2, 0xE4);
    private static final Stroke SELECTED_STROKE = new BasicStroke(2f);

    private final List<Card> cards = new ArrayList<>();
    private final List<Card> readOnlyCards = Collections.unmodifiableList(cards);
    private int columns = 2;
    private double gapX = DEFAULT_GAP;
    private double gapY = DEFAULT_GAP;
    private double cellHeight = DEFAULT_CELL_HEIGHT;

    /** Creates an empty grid. The grid itself never takes focus; its cards do. */
    public CardGrid() {
        setFocusable(false);
    }

    /**
     * Appends a card.
     *
     * @param card the card
     * @return the card, for chaining
     */
    public Card add(Card card) {
        cards.add(Objects.requireNonNull(card, "card"));
        return card;
    }

    /** Removes every card. */
    public void clear() {
        cards.clear();
    }

    /**
     * The cards in insertion order.
     *
     * @return an unmodifiable view
     */
    public List<Card> cards() {
        return readOnlyCards;
    }

    /**
     * A card by id.
     *
     * @param id the card id
     * @return the card, or {@code null} when the grid has no such card
     */
    public Card card(String id) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).id().equals(id)) {
                return cards.get(i);
            }
        }
        return null;
    }

    /**
     * The number of cards.
     *
     * @return the count
     */
    public int size() {
        return cards.size();
    }

    /**
     * The number of columns.
     *
     * @return the count
     */
    public int columns() {
        return columns;
    }

    /**
     * Sets the number of columns.
     *
     * @param newColumns the count (at least 1)
     */
    public void setColumns(int newColumns) {
        this.columns = Math.max(1, newColumns);
    }

    /**
     * Sets the gaps between cards.
     *
     * @param newGapX the horizontal gap
     * @param newGapY the vertical gap
     */
    public void setGap(double newGapX, double newGapY) {
        this.gapX = Math.max(0, newGapX);
        this.gapY = Math.max(0, newGapY);
    }

    /**
     * The height of one card.
     *
     * @return logical pixels
     */
    public double cellHeight() {
        return cellHeight;
    }

    /**
     * Sets the height of one card.
     *
     * @param newCellHeight logical pixels
     */
    public void setCellHeight(double newCellHeight) {
        this.cellHeight = Math.max(1, newCellHeight);
    }

    /**
     * Places every card in row-major order inside the grid bounds.
     */
    public void layout() {
        double cellWidth = (width() - gapX * (columns - 1)) / columns;
        for (int i = 0; i < cards.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            cards.get(i).setBounds(x() + col * (cellWidth + gapX),
                    y() + row * (cellHeight + gapY), Math.max(0, cellWidth), cellHeight);
        }
    }

    /**
     * The height a grid of {@code count} cards needs.
     *
     * @param count the number of cards
     * @param columns the number of columns
     * @param cellHeight the card height
     * @param gapY the vertical gap
     * @return the total height
     */
    public static double heightFor(int count, int columns, double cellHeight, double gapY) {
        int cols = Math.max(1, columns);
        int rows = Math.max(0, (count + cols - 1) / cols);
        return rows == 0 ? 0 : rows * cellHeight + (rows - 1) * gapY;
    }

    /**
     * Adds every card to a focus ring, in insertion order.
     *
     * @param ring the ring
     */
    public void registerFocusables(FocusRing ring) {
        Objects.requireNonNull(ring, "ring");
        for (Card card : cards) {
            if (card.isFocusable()) {
                ring.add(card);
            }
        }
    }

    /**
     * The card marked as selected.
     *
     * @return the card, or {@code null} when none is
     */
    public Card selected() {
        for (Card card : cards) {
            if (card.isSelected()) {
                return card;
            }
        }
        return null;
    }

    /**
     * Marks one card as selected and clears the flag on every other one.
     *
     * @param id the card id, or {@code null} to clear the selection
     */
    public void select(String id) {
        for (Card card : cards) {
            card.setSelected(card.id().equals(id));
        }
    }

    @Override
    public void render(Graphics2D g) {
        for (Card card : cards) {
            if (card.isVisible()) {
                card.render(g);
            }
        }
    }

    /** Paints the portrait area of a card. */
    @FunctionalInterface
    public interface ArtPainter {

        /**
         * Draws into the square art area of a card.
         *
         * @param g the context in logical coordinates
         * @param card the card being drawn
         * @param cx the centre x of the art area
         * @param cy the centre y of the art area
         * @param size the side of the art area
         */
        void paint(Graphics2D g, Card card, double cx, double cy, double size);
    }

    /**
     * One card: an id, up to three lines of text, an optional portrait and the two meta-progression
     * flags.
     */
    public static class Card extends UiNode {

        private final String id;
        private String title;
        private String subtitle = "";
        private String badge = "";
        private String tooltip = "";
        private boolean locked;
        private boolean dimmed;
        private boolean selected;
        private boolean coinBadge;
        private ArtPainter art;
        private final Ellipse2D.Double coin = new Ellipse2D.Double();

        /**
         * Creates a card.
         *
         * @param id the stable id a screen and a test address the card by
         * @param title the headline
         * @param onAction what activating the card does, or {@code null}
         */
        public Card(String id, String title, Runnable onAction) {
            this.id = Objects.requireNonNull(id, "id");
            this.title = Objects.requireNonNull(title, "title");
            setOnAction(onAction);
        }

        /**
         * The card id.
         *
         * @return the id
         */
        public String id() {
            return id;
        }

        /**
         * The headline.
         *
         * @return the title
         */
        public String title() {
            return title;
        }

        /**
         * Changes the headline.
         *
         * @param newTitle the title
         */
        public void setTitle(String newTitle) {
            this.title = Objects.requireNonNull(newTitle, "title");
        }

        /**
         * The second line.
         *
         * @return the subtitle, possibly empty
         */
        public String subtitle() {
            return subtitle;
        }

        /**
         * Changes the second line.
         *
         * @param newSubtitle the subtitle, or {@code null} for none
         */
        public void setSubtitle(String newSubtitle) {
            this.subtitle = newSubtitle == null ? "" : newSubtitle;
        }

        /**
         * The right-hand badge (a price, a level, a state).
         *
         * @return the badge, possibly empty
         */
        public String badge() {
            return badge;
        }

        /**
         * Changes the badge.
         *
         * @param newBadge the badge, or {@code null} for none
         * @param withCoin whether to draw the coin icon in front of it
         */
        public void setBadge(String newBadge, boolean withCoin) {
            this.badge = newBadge == null ? "" : newBadge;
            this.coinBadge = withCoin && !this.badge.isEmpty();
        }

        /**
         * The text a {@link Tooltip} shows for this card.
         *
         * @return the text, possibly empty
         */
        public String tooltip() {
            return tooltip;
        }

        /**
         * Sets the tooltip text.
         *
         * @param newTooltip the text, or {@code null} for none
         */
        public void setTooltip(String newTooltip) {
            this.tooltip = newTooltip == null ? "" : newTooltip;
        }

        /**
         * Whether the card is drawn as locked.
         *
         * @return {@code true} when locked
         */
        public boolean isLocked() {
            return locked;
        }

        /**
         * Marks the card locked or unlocked.
         *
         * @param newLocked the flag
         */
        public void setLocked(boolean newLocked) {
            this.locked = newLocked;
        }

        /**
         * Whether the card is drawn dimmed without a padlock: the thing exists and is open, but
         * cannot be acted on right now (a price the wallet does not cover).
         *
         * @return {@code true} when dimmed
         */
        public boolean isDimmed() {
            return dimmed;
        }

        /**
         * Dims the card without marking it locked.
         *
         * @param newDimmed the flag
         */
        public void setDimmed(boolean newDimmed) {
            this.dimmed = newDimmed;
        }

        /**
         * Whether the card carries the selection outline.
         *
         * @return {@code true} when selected
         */
        public boolean isSelected() {
            return selected;
        }

        /**
         * Sets the selection outline.
         *
         * @param newSelected the flag
         */
        public void setSelected(boolean newSelected) {
            this.selected = newSelected;
        }

        /**
         * Installs the portrait painter.
         *
         * @param painter the painter, or {@code null} for a card without a portrait
         */
        public void setArt(ArtPainter painter) {
            this.art = painter;
        }

        /**
         * The portrait painter.
         *
         * @return the painter, or {@code null}
         */
        public ArtPainter art() {
            return art;
        }

        @Override
        public void render(Graphics2D g) {
            int bx = (int) Math.round(x());
            int by = (int) Math.round(y());
            int bw = (int) Math.round(width());
            int bh = (int) Math.round(height());
            ButtonState state = ButtonState.of(isEnabled(), isFocused(), isHovered());
            ProceduralArt.button(g, bx, by, bw, bh, state);

            double artSize = Math.min(bh - 8.0, bh * 0.8);
            double textLeft = bx + 8.0;
            if (art != null) {
                double cx = bx + 6 + artSize / 2;
                double cy = by + bh / 2.0;
                Shape clip = g.getClip();
                g.clipRect(bx + 4, by + 4, (int) Math.round(artSize) + 4, bh - 8);
                art.paint(g, this, cx, cy, artSize);
                g.setClip(clip);
                textLeft = bx + 10 + artSize;
            }

            // The badge owns the right end of the card; the title and the subtitle are clipped
            // to what is left, so a long translation cannot run over the price or off the card.
            double badgeWidth = 0;
            if (!badge.isEmpty()) {
                g.setFont(Fonts.bold(BADGE_SIZE));
                double right = bx + bw - 8.0;
                badgeWidth = TextPainter.width(g, badge) + (coinBadge ? 20 : 8);
                g.setColor(locked || dimmed ? ProceduralArt.TEXT_MUTED : ProceduralArt.TEXT_LIGHT);
                TextPainter.draw(g, badge, right, by + bh / 2.0 + 4, Align.RIGHT);
                if (coinBadge) {
                    double iconX = right - TextPainter.width(g, badge) - 10;
                    ProceduralArt.drawCoin(g, coin, iconX, by + bh / 2.0, 6, 1);
                }
            }
            Shape textClip = g.getClip();
            int textWidth = (int) Math.round(bx + bw - 8 - badgeWidth - textLeft);
            g.clipRect((int) Math.round(textLeft), by, Math.max(0, textWidth), bh);
            g.setFont(Fonts.bold(TITLE_SIZE));
            g.setColor(locked || dimmed ? ProceduralArt.TEXT_MUTED
                    : ProceduralArt.buttonTextColor(state));
            double titleBaseline = subtitle.isEmpty() ? by + bh / 2.0 + 5 : by + bh / 2.0 - 2;
            TextPainter.draw(g, ellipsised(g, title, textWidth), textLeft, titleBaseline);
            if (!subtitle.isEmpty()) {
                g.setFont(Fonts.regular(SUBTITLE_SIZE));
                g.setColor(ProceduralArt.TEXT_MUTED);
                TextPainter.draw(g, ellipsised(g, subtitle, textWidth), textLeft,
                        by + bh / 2.0 + 12);
            }
            g.setClip(textClip);
            if (locked || dimmed) {
                g.setColor(LOCK_VEIL);
                g.fillRoundRect(bx, by, bw, bh, ProceduralArt.BUTTON_RADIUS,
                        ProceduralArt.BUTTON_RADIUS);
                if (locked) {
                    // A padlock means "not open at all"; the veil alone means "not right now".
                    drawPadlock(g, bx + bw - 13.0, by + 13.0, 9);
                }
            }
            if (selected) {
                Stroke old = g.getStroke();
                g.setStroke(SELECTED_STROKE);
                g.setColor(ProceduralArt.COIN_GOLD);
                g.drawRoundRect(bx + 1, by + 1, bw - 2, bh - 2, ProceduralArt.BUTTON_RADIUS,
                        ProceduralArt.BUTTON_RADIUS);
                g.setStroke(old);
            }
        }

        /**
         * Shortens text to fit a width, ending it in an ellipsis.
         *
         * <p>The clip alone would cut the last word in half ({@code -10% Max fall speec}), which
         * reads as a rendering bug rather than as "there is more". Measuring is cheap here: a
         * card's text changes only when the profile or the language does.
         *
         * @param g the context, with the font already set
         * @param text the text
         * @param width the width available, in logical pixels
         * @return the text, or a prefix of it followed by {@value #ELLIPSIS}
         */
        private static String ellipsised(Graphics2D g, String text, int width) {
            if (width <= 0 || TextPainter.width(g, text) <= width) {
                return text;
            }
            int ellipsis = TextPainter.width(g, ELLIPSIS);
            int end = text.length();
            while (end > 0 && TextPainter.width(g, text.substring(0, end)) + ellipsis > width) {
                end--;
            }
            // Trailing spaces and a dangling separator look like a typo next to the ellipsis.
            while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '-')) {
                end--;
            }
            return text.substring(0, end) + ELLIPSIS;
        }

        /**
         * Draws the padlock of a locked card.
         *
         * @param g the context
         * @param cx the centre x
         * @param cy the centre y
         * @param size the width of the lock body
         */
        private static void drawPadlock(Graphics2D g, double cx, double cy, double size) {
            int w = (int) Math.round(size);
            int h = (int) Math.round(size * 0.8);
            int bodyX = (int) Math.round(cx - size / 2);
            int bodyY = (int) Math.round(cy - size * 0.1);
            g.setColor(LOCK_BODY);
            g.fillRoundRect(bodyX, bodyY, w, h, 2, 2);
            Stroke old = g.getStroke();
            g.setStroke(new BasicStroke(1.6f));
            g.drawArc((int) Math.round(cx - size * 0.3), (int) Math.round(cy - size * 0.65),
                    (int) Math.round(size * 0.6), (int) Math.round(size * 0.7), 0, 180);
            g.setStroke(old);
        }
    }
}
