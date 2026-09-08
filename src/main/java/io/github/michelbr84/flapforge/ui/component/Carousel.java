package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.UiCues;
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
 * A horizontal, clipped row of tall cards that scrolls (M11): the bird roster of the selection
 * screen, and anything else that is a short list of things one of which is chosen.
 *
 * <p>The carousel itself is never focusable — its {@link Tile}s are the ring nodes and the two
 * arrows at its ends are click zones, in the same way {@link ListView} handles its arrows, so the
 * focus never lands on a control that can stop existing. Left and Right on a focused tile walk
 * the row and clamp at both ends ({@link Tile#handlesHorizontalKeys()} keeps the keys away from
 * the ring's spatial move), and any focus change onto a tile — pointer, Tab or a vertical
 * move — scrolls that tile into view.
 *
 * <p>Motion is tick-driven and deterministic: the offset eases over {@value #SCROLL_TICKS} ticks
 * with the ease-out-quad the coin readout uses, the selected tile carries the gold pulse of the
 * hub's call to action (capped under reduce flashing, which caps luminance pulses only and leaves
 * motion alone), and a locked tile nudges its padlock for {@value #NUDGE_TICKS} ticks when it is
 * tapped. The draw path allocates nothing: the glow ramp and the strokes are constants, every
 * measured string is cached on its text, its room and the text scale, and the viewport clip is
 * the one bracket per frame.
 */
public class Carousel extends UiNode {

    /** Width of an arrow zone at either end. */
    public static final int ARROW_WIDTH = 40;
    /** Height of an arrow zone. */
    public static final int ARROW_HEIGHT = 44;
    /** Default width of one tile. */
    public static final int DEFAULT_TILE_WIDTH = 68;
    /** Default height of one tile. */
    public static final int DEFAULT_TILE_HEIGHT = 80;
    /** Default space between two tiles. */
    public static final int DEFAULT_GAP = 6;
    /** Top inset of the tile row inside the bar. */
    public static final int TILE_TOP = 2;
    /** Ticks one scroll tween takes. */
    public static final int SCROLL_TICKS = 12;
    /** Ticks a tapped padlock nudges for. */
    public static final int NUDGE_TICKS = 12;
    /** Ticks of one glow pulse of the selected tile. */
    public static final int GLOW_PERIOD = 90;
    /** Peak alpha of that glow. */
    public static final double GLOW_PEAK = 0.35;
    /** Peak alpha of that glow under reduce flashing. */
    public static final double GLOW_PEAK_REDUCED = CtaButton.GLOW_PEAK_REDUCED;
    /** Size of the portrait area of a tile. */
    public static final int ART_SIZE = 34;
    /** Point size of a tile's name. */
    public static final int TITLE_SIZE = 11;
    /** Point size of a tile's second line. */
    public static final int SUBTITLE_SIZE = 9;

    private static final Color[] GLOW = ProceduralArt.alphaRamp(ProceduralArt.COIN_GOLD, 16);
    private static final Color LOCK_VEIL = new Color(0x10, 0x1C, 0x1E, 0x9C);
    private static final Color ARROW_ON = new Color(0xF4, 0xF8, 0xF8);
    private static final Color ARROW_OFF = new Color(0x6E, 0x7A, 0x7C);
    private static final Stroke SELECTED_STROKE = new BasicStroke(2f);

    private final List<Tile> tiles = new ArrayList<>();
    private final List<Tile> readOnlyTiles = Collections.unmodifiableList(tiles);
    private double tileWidth = DEFAULT_TILE_WIDTH;
    private double tileHeight = DEFAULT_TILE_HEIGHT;
    private double gap = DEFAULT_GAP;
    private double offset;
    private double offsetFrom;
    private double offsetTo;
    private int scrollTicks;
    private long ticks;
    private boolean reduceFlashing;
    private boolean hoverPrev;
    private boolean hoverNext;
    private UiNode lastFocused;

    /** Creates an empty carousel. */
    public Carousel() {
        setFocusable(false);
    }

    /**
     * Appends a tile.
     *
     * @param tile the tile
     * @return the tile, for chaining
     */
    public Tile add(Tile tile) {
        Objects.requireNonNull(tile, "tile");
        tile.owner = this;
        tiles.add(tile);
        return tile;
    }

    /** Drops every tile. */
    public void clear() {
        for (Tile tile : tiles) {
            tile.owner = null;
        }
        tiles.clear();
        offset = 0;
        scrollTicks = 0;
    }

    /**
     * The tiles in insertion order.
     *
     * @return an unmodifiable view
     */
    public List<Tile> cards() {
        return readOnlyTiles;
    }

    /**
     * Looks a tile up by id.
     *
     * @param id the tile id
     * @return the tile, or {@code null} when the carousel holds none with that id
     */
    public Tile card(String id) {
        for (Tile tile : tiles) {
            if (tile.id().equals(id)) {
                return tile;
            }
        }
        return null;
    }

    /**
     * How many tiles the carousel holds.
     *
     * @return the count
     */
    public int size() {
        return tiles.size();
    }

    /**
     * The position of a tile in the row.
     *
     * @param id the tile id
     * @return the index, or {@code -1} when the carousel holds none with that id
     */
    public int indexOf(String id) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Marks one tile as the selected one and clears the flag on the others.
     *
     * @param id the tile id, or {@code null} to clear every flag
     */
    public void select(String id) {
        for (Tile tile : tiles) {
            tile.setSelected(tile.id().equals(id));
        }
    }

    /**
     * The selected tile.
     *
     * @return the tile, or {@code null} when none is selected
     */
    public Tile selected() {
        for (Tile tile : tiles) {
            if (tile.isSelected()) {
                return tile;
            }
        }
        return null;
    }

    /**
     * Sizes the tiles.
     *
     * @param width the tile width
     * @param height the tile height
     */
    public void setTileSize(double width, double height) {
        this.tileWidth = Math.max(1, width);
        this.tileHeight = Math.max(1, height);
    }

    /**
     * Changes the space between two tiles.
     *
     * @param newGap the gap
     */
    public void setGap(double newGap) {
        this.gap = Math.max(0, newGap);
    }

    /**
     * Places every tile at its scrolled position.
     */
    public void layout() {
        double pitch = tileWidth + gap;
        double top = y() + TILE_TOP;
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).setBounds(viewportLeft() + i * pitch - offset, top, tileWidth,
                    tileHeight);
        }
    }

    /**
     * Adds every tile to a focus ring, in order.
     *
     * @param ring the ring
     */
    public void registerFocusables(FocusRing ring) {
        Objects.requireNonNull(ring, "ring");
        for (Tile tile : tiles) {
            ring.add(tile);
        }
    }

    /**
     * Left edge of the scrolling viewport (the arrow zone ends there).
     *
     * @return the x coordinate
     */
    public double viewportLeft() {
        return x() + ARROW_WIDTH + 4;
    }

    /**
     * Right edge of the scrolling viewport.
     *
     * @return the x coordinate
     */
    public double viewportRight() {
        return x() + width() - ARROW_WIDTH - 4;
    }

    /**
     * Whether a point is inside the viewport, arrow zones excluded.
     *
     * @param px the x coordinate
     * @param py the y coordinate
     * @return {@code true} when the point is inside
     */
    public boolean viewportContains(double px, double py) {
        return px >= viewportLeft() && px < viewportRight() && py >= y()
                && py < y() + height();
    }

    /**
     * How far the row is scrolled.
     *
     * @return the offset in logical pixels
     */
    public double offset() {
        return offset;
    }

    /**
     * The largest offset the row can take.
     *
     * @return the offset in logical pixels, {@code 0} when everything fits
     */
    public double maxOffset() {
        double content = tiles.size() * tileWidth + Math.max(0, tiles.size() - 1) * gap;
        return Math.max(0, content - (viewportRight() - viewportLeft()));
    }

    /**
     * Whether a scroll tween is running.
     *
     * @return {@code true} while the row is moving
     */
    public boolean isScrolling() {
        return scrollTicks > 0;
    }

    /**
     * Brings a tile fully inside the viewport.
     *
     * @param tile the tile
     * @param animate whether to ease there instead of jumping
     */
    public void scrollIntoView(Tile tile, boolean animate) {
        if (tile == null || tile.owner != this) {
            return;
        }
        int index = tiles.indexOf(tile);
        double pitch = tileWidth + gap;
        double left = index * pitch;
        double viewWidth = viewportRight() - viewportLeft();
        double target = offset;
        if (left < offset) {
            target = left;
        } else if (left + tileWidth > offset + viewWidth) {
            target = left + tileWidth - viewWidth;
        }
        target = MathUtil.clamp(target, 0, maxOffset());
        if (animate) {
            scrollTo(target);
        } else {
            offset = target;
            scrollTicks = 0;
            layout();
        }
    }

    /**
     * Jumps the row so a tile is inside the viewport.
     *
     * @param tile the tile
     */
    public void snapTo(Tile tile) {
        scrollIntoView(tile, false);
    }

    /**
     * Scrolls the row by one tile.
     *
     * @param direction {@code -1} left, {@code +1} right
     */
    public void page(int direction) {
        scrollTo(offset + direction * (tileWidth + gap));
    }

    private void scrollTo(double target) {
        double clamped = MathUtil.clamp(target, 0, maxOffset());
        if (clamped == offset) {
            scrollTicks = 0;
            return;
        }
        offsetFrom = offset;
        offsetTo = clamped;
        scrollTicks = SCROLL_TICKS;
    }

    /**
     * Advances the glow and the nudges to a tick of the screen's clock.
     *
     * @param newTicks the tick count
     */
    public void setTicks(long newTicks) {
        this.ticks = newTicks;
    }

    /**
     * Caps the selection glow for players who asked for less flashing. The scroll tween, the
     * padlock nudge and the portrait's wing beat are motion rather than luminance and stay on,
     * exactly as the hub's bob does.
     *
     * @param value the flag
     */
    public void setReduceFlashing(boolean value) {
        this.reduceFlashing = value;
    }

    /**
     * Whether the glow is capped.
     *
     * @return the flag
     */
    public boolean isReduceFlashing() {
        return reduceFlashing;
    }

    /**
     * The alpha of the selected tile's glow: a triangle wave over {@value #GLOW_PERIOD} ticks.
     *
     * @return the alpha in {@code [0, 1]}
     */
    public double glowAlpha() {
        double phase = (ticks % GLOW_PERIOD) / (double) GLOW_PERIOD;
        double wave = phase < 0.5 ? phase * 2 : 2 - phase * 2;
        return (reduceFlashing ? GLOW_PEAK_REDUCED : GLOW_PEAK) * wave;
    }

    /**
     * Handles the row's own input and advances its motion.
     *
     * @param input the tick's input
     * @param ring the ring the tiles are registered with
     * @return {@code true} when the row moved the focus itself
     */
    public boolean tick(InputFrame input, FocusRing ring) {
        Objects.requireNonNull(input, "input");
        if (scrollTicks > 0) {
            scrollTicks--;
            double t = 1.0 - scrollTicks / (double) SCROLL_TICKS;
            double eased = 1 - (1 - t) * (1 - t);
            offset = offsetFrom + (offsetTo - offsetFrom) * eased;
            if (scrollTicks == 0) {
                offset = offsetTo;
            }
        }
        for (Tile tile : tiles) {
            if (tile.nudgeTicks > 0) {
                tile.nudgeTicks--;
            }
        }
        boolean moved = false;
        UiNode focused = ring == null ? null : ring.focused();
        if (focused instanceof Tile tile && tile.owner == this) {
            if (input.isJustPressed(InputAction.LEFT)) {
                moved = focusNeighbour(ring, tile, -1);
            }
            if (input.isJustPressed(InputAction.RIGHT)) {
                moved |= focusNeighbour(ring, tile, 1);
            }
        }
        UiNode now = ring == null ? null : ring.focused();
        if (now != lastFocused && now instanceof Tile tile && tile.owner == this) {
            scrollIntoView(tile, true);
        }
        lastFocused = now;
        double mx = input.mouseX();
        double my = input.mouseY();
        hoverPrev = inPrevZone(mx, my);
        hoverNext = inNextZone(mx, my);
        if (input.isMouseJustPressed(Keys.BUTTON_LEFT)) {
            if (hoverPrev) {
                page(-1);
            } else if (hoverNext) {
                page(1);
            }
        }
        if (input.wheel() != 0 && contains(mx, my)) {
            page(input.wheel() > 0 ? -1 : 1);
        }
        layout();
        if (moved) {
            UiCues.move();
        }
        return moved;
    }

    private boolean focusNeighbour(FocusRing ring, Tile from, int direction) {
        int index = tiles.indexOf(from) + direction;
        if (index < 0 || index >= tiles.size()) {
            return false;
        }
        Tile next = tiles.get(index);
        if (!next.canFocus()) {
            return false;
        }
        ring.focus(next);
        scrollIntoView(next, true);
        return true;
    }

    private boolean inPrevZone(double px, double py) {
        double top = y() + (height() - ARROW_HEIGHT) / 2;
        return px >= x() && px < x() + ARROW_WIDTH && py >= top && py < top + ARROW_HEIGHT;
    }

    private boolean inNextZone(double px, double py) {
        double top = y() + (height() - ARROW_HEIGHT) / 2;
        return px >= x() + width() - ARROW_WIDTH && px < x() + width() && py >= top
                && py < top + ARROW_HEIGHT;
    }

    @Override
    public void render(Graphics2D g) {
        int top = (int) Math.round(y() + (height() - ARROW_HEIGHT) / 2);
        boolean canLeft = offset > 0.5;
        boolean canRight = offset < maxOffset() - 0.5;
        ProceduralArt.chip(g, (int) Math.round(x()), top, ARROW_WIDTH, ARROW_HEIGHT,
                hoverPrev && canLeft ? ButtonState.HOVER : ButtonState.NORMAL);
        ProceduralArt.drawChevron(g, x() + ARROW_WIDTH / 2.0, top + ARROW_HEIGHT / 2.0, 12,
                canLeft ? ARROW_ON : ARROW_OFF, true);
        ProceduralArt.chip(g, (int) Math.round(x() + width() - ARROW_WIDTH), top, ARROW_WIDTH,
                ARROW_HEIGHT, hoverNext && canRight ? ButtonState.HOVER : ButtonState.NORMAL);
        ProceduralArt.drawChevron(g, x() + width() - ARROW_WIDTH / 2.0,
                top + ARROW_HEIGHT / 2.0, 12, canRight ? ARROW_ON : ARROW_OFF, false);

        Shape unclipped = g.getClip();
        g.clipRect((int) Math.round(viewportLeft()), (int) Math.round(y()),
                (int) Math.round(viewportRight() - viewportLeft()), (int) Math.round(height()));
        // The glow of the selected tile first, so its outer ring never sits over a neighbour.
        Tile chosen = selected();
        if (chosen != null && chosen.isVisible()) {
            ProceduralArt.ctaGlow(g, (int) Math.round(chosen.x()), (int) Math.round(chosen.y()),
                    (int) Math.round(chosen.width()), (int) Math.round(chosen.height()), GLOW,
                    glowAlpha());
        }
        for (Tile tile : tiles) {
            if (tile.isVisible()) {
                tile.render(g);
            }
        }
        g.setClip(unclipped);
    }

    /**
     * One tile of a carousel: a {@link CardGrid.Card} drawn tall — portrait on top, name under
     * it, one more line under that — and hit-tested only where the viewport shows it.
     */
    public static class Tile extends CardGrid.Card {

        private final Ellipse2D.Double coin = new Ellipse2D.Double();
        Carousel owner;
        int nudgeTicks;
        private String shownTitle = "";
        private String shownTitleSource;
        private String shownLine = "";
        private String shownLineSource;
        private int shownWidth = -1;
        private double shownScale;

        /**
         * Creates a tile.
         *
         * @param id the stable id a screen and a test address the tile by
         * @param title the name
         * @param onAction what activating the tile does, or {@code null}
         */
        public Tile(String id, String title, Runnable onAction) {
            super(id, title, onAction);
        }

        /** Starts the padlock nudge: the answer to a tap on something not open yet. */
        public void nudge() {
            nudgeTicks = NUDGE_TICKS;
        }

        /**
         * Whether the padlock is nudging.
         *
         * @return {@code true} while it moves
         */
        public boolean isNudging() {
            return nudgeTicks > 0;
        }

        /**
         * Ticks left of the nudge.
         *
         * @return the count
         */
        public int nudgeTicks() {
            return nudgeTicks;
        }

        @Override
        public boolean handlesHorizontalKeys() {
            // Left and Right walk the row; the ring's spatial move would leave it.
            return true;
        }

        @Override
        public boolean contains(double px, double py) {
            return super.contains(px, py)
                    && (owner == null || owner.viewportContains(px, py));
        }

        @Override
        public void render(Graphics2D g) {
            int bx = (int) Math.round(x());
            int by = (int) Math.round(y());
            int bw = (int) Math.round(width());
            int bh = (int) Math.round(height());
            ButtonState state = ButtonState.of(isEnabled(), isFocused(), isHovered());
            ProceduralArt.chip(g, bx, by, bw, bh, state);
            if (art() != null) {
                art().paint(g, this, centerX(), by + 26.0, ART_SIZE);
            }
            int room = bw - 8;
            g.setFont(Fonts.bold(TITLE_SIZE));
            double scale = Fonts.textScale();
            String line = secondLine();
            if (shownTitleSource != title() || shownLineSource != line || shownWidth != room
                    || shownScale != scale) {
                // Measured only when the text, the room or the text scale changed.
                shownTitle = TextPainter.ellipsise(g, title(), Math.max(0, room));
                shownTitleSource = title();
                shownScale = scale;
                shownWidth = room;
                g.setFont(Fonts.regular(SUBTITLE_SIZE));
                shownLine = TextPainter.ellipsise(g, line,
                        Math.max(0, room - (hasCoinBadge() ? 14 : 0)));
                shownLineSource = line;
                g.setFont(Fonts.bold(TITLE_SIZE));
            }
            g.setColor(isLocked() ? ProceduralArt.TEXT_MUTED : ProceduralArt.TEXT_LIGHT);
            TextPainter.drawCentered(g, shownTitle, centerX(), by + 58.0);
            if (!shownLine.isEmpty()) {
                g.setFont(Fonts.regular(SUBTITLE_SIZE));
                g.setColor(isSelected() ? Accessibility.tone(ProceduralArt.COIN_GOLD)
                        : ProceduralArt.TEXT_MUTED);
                double lineWidth = TextPainter.width(g, shownLine);
                double left = centerX() - lineWidth / 2 + (hasCoinBadge() ? 7 : 0);
                TextPainter.draw(g, shownLine, left, by + 72.0);
                if (hasCoinBadge()) {
                    ProceduralArt.drawCoin(g, coin, left - 9, by + 68.0, 5, 1);
                }
            }
            if (isLocked()) {
                g.setColor(LOCK_VEIL);
                g.fillRoundRect(bx, by, bw, bh, ProceduralArt.CHIP_RADIUS,
                        ProceduralArt.CHIP_RADIUS);
                double dx = nudgeTicks == 0 ? 0
                        : (((nudgeTicks / 2) & 1) == 0 ? 1 : -1) * (nudgeTicks > 6 ? 2 : 1);
                ProceduralArt.drawPadlock(g, bx + bw - 12.0 + dx, by + 12.0, 10, null);
            }
            if (isSelected()) {
                Stroke old = g.getStroke();
                g.setStroke(SELECTED_STROKE);
                g.setColor(ProceduralArt.COIN_GOLD);
                g.drawRoundRect(bx + 1, by + 1, bw - 2, bh - 2, ProceduralArt.CHIP_RADIUS,
                        ProceduralArt.CHIP_RADIUS);
                g.setStroke(old);
            }
        }

        /**
         * The line under the name: the badge when the tile carries a price, the subtitle
         * otherwise.
         *
         * @return the text, possibly empty
         */
        private String secondLine() {
            return hasCoinBadge() ? badge() : subtitle();
        }
    }
}
