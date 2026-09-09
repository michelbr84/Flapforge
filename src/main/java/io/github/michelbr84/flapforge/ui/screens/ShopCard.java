package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;

/**
 * One thing the shop sells, as a card in a two-column grid (M12): the art on top, the name under
 * it, one line saying what it is, and one line saying where it stands.
 *
 * <p>It is a {@link CardGrid.Card} with a portrait layout instead of the row layout the settings-
 * like screens use, the same way {@link io.github.michelbr84.flapforge.ui.component.Carousel.Tile}
 * is. Everything a screen or a test addresses — the title, the subtitle, the badge, the tooltip
 * and the locked, dimmed and selected flags — is the inherited API; what changes is where the
 * pieces are drawn and how the badge is dressed.
 *
 * <p>The badge is the state, and there are exactly three dressings, chosen by what the screen put
 * on the card rather than by an enum the screen has to keep in step:
 *
 * <ul>
 *   <li>a <b>price</b> ({@link #setBadge(String, boolean) a badge with the coin}) is drawn on a
 *       little wooden tag — the one piece of market furniture the shop borrows, and the reason
 *       its cards do not read as the bird selection's;</li>
 *   <li><b>owned</b> ({@link #setOwned(boolean)}) is a check in the toolkit's confirm green,
 *       with the word beside it and no price at all, because an owned thing has no price;</li>
 *   <li>anything else — "Cap reached", "Fully upgraded" — is the word alone, muted.</li>
 * </ul>
 *
 * <p>An unaffordable card is {@linkplain #setDimmed(boolean) dimmed} and never padlocked: it is
 * for sale, the wallet is simply short, and a padlock would say something that is not true. In
 * this game everything for sale is also earnable, so the subtitle carries the way to earn it
 * instead — that is the shop's answer to "why is this locked".
 *
 * <p>The two measured strings are cached on their source, the room they were measured for and the
 * text scale, so a card that has not changed costs no measuring in the render path.
 */
public final class ShopCard extends CardGrid.Card {

    /** Centre of the art, from the top of the card. */
    public static final int ART_CY = 32;
    /** Size of the art. */
    public static final int ART_SIZE = 40;
    /** Baseline of the name, from the top of the card. */
    public static final int TITLE_BASELINE = 66;
    /** Baseline of the line under the name. */
    public static final int SUBTITLE_BASELINE = 79;
    /** Top of the state tag, from the top of the card. */
    public static final int TAG_TOP = 84;
    /** Height of the state tag. */
    public static final int TAG_H = 16;
    /** Point size of the name. */
    public static final int TITLE_SIZE = 12;
    /** Point size of the line under the name. */
    public static final int SUBTITLE_SIZE = 9;
    /** Point size of the state tag. */
    public static final int TAG_SIZE = 11;
    /** Room kept clear at each side of the text. */
    public static final int PADDING = 6;
    /** Radius of the coin on a price tag. */
    public static final int COIN_R = 5;
    /** Size of the owned check. */
    public static final int CHECK_SIZE = 11;

    private static final Color LOCK_VEIL = new Color(0x10, 0x1C, 0x1E, 0x9C);
    private static final Color OWNED = new Color(0x6F, 0xD1, 0xA8);
    private static final Color TAG_EDGE = new Color(0x3E, 0x2A, 0x18);
    private static final Color TAG_GRAIN = new Color(0xFF, 0xFF, 0xFF, 0x28);
    private static final Stroke SELECTED_STROKE = new BasicStroke(2f);

    private final Ellipse2D.Double coin = new Ellipse2D.Double();
    private boolean owned;
    private double viewportTop = Double.NEGATIVE_INFINITY;
    private double viewportBottom = Double.POSITIVE_INFINITY;
    private String shownTitle = "";
    private String shownTitleSource;
    private String shownSubtitle = "";
    private String shownSubtitleSource;
    private int shownRoom = -1;
    private double shownScale;

    /**
     * Creates a card.
     *
     * @param id the namespaced unlockable id the card stands for
     * @param title the translated name
     * @param onAction what activating it does
     */
    public ShopCard(String id, String title, Runnable onAction) {
        super(id, title, onAction);
    }

    /**
     * Whether the profile already owns what this card sells.
     *
     * @return {@code true} when owned
     */
    public boolean isOwned() {
        return owned;
    }

    /**
     * Marks the card as something the profile already owns.
     *
     * @param newOwned the flag
     */
    public void setOwned(boolean newOwned) {
        this.owned = newOwned;
    }

    /**
     * Limits where the card answers to the pointer, to the band the grid is clipped to.
     *
     * <p>The grid scrolls by moving its cards rather than the canvas, so a card scrolled past the
     * top or the bottom still has bounds — it is simply not drawn. Without this clamp the focus
     * ring would hover, click and tooltip a card nobody can see, which is the same trap
     * {@link io.github.michelbr84.flapforge.ui.component.Carousel.Tile} avoids at the ends of its
     * row.
     *
     * @param top the first visible row of pixels
     * @param bottom the last visible row of pixels
     */
    public void setViewport(double top, double bottom) {
        this.viewportTop = top;
        this.viewportBottom = bottom;
    }

    @Override
    public boolean contains(double px, double py) {
        return py >= viewportTop && py <= viewportBottom && super.contains(px, py);
    }

    @Override
    public void render(Graphics2D g) {
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        ButtonState state = ButtonState.of(isEnabled(), isFocused(), isHovered());
        ProceduralArt.button(g, bx, by, bw, bh, state);

        if (art() != null) {
            art().paint(g, this, bx + bw / 2.0, by + ART_CY, ART_SIZE);
        }

        int room = bw - 2 * PADDING;
        measure(g, room);

        Shape unclipped = g.getClip();
        g.clipRect(bx + PADDING / 2, by, Math.max(0, bw - PADDING), bh);
        g.setFont(Fonts.bold(TITLE_SIZE));
        g.setColor(isDimmed() ? ProceduralArt.TEXT_MUTED : ProceduralArt.buttonTextColor(state));
        TextPainter.drawCentered(g, shownTitle, bx + bw / 2.0, by + TITLE_BASELINE);
        if (!shownSubtitle.isEmpty()) {
            g.setFont(Fonts.regular(SUBTITLE_SIZE));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.drawCentered(g, shownSubtitle, bx + bw / 2.0, by + SUBTITLE_BASELINE);
        }
        g.setClip(unclipped);

        if (isDimmed() || isLocked()) {
            g.setColor(LOCK_VEIL);
            g.fillRoundRect(bx, by, bw, bh, ProceduralArt.BUTTON_RADIUS,
                    ProceduralArt.BUTTON_RADIUS);
            if (isLocked()) {
                ProceduralArt.drawPadlock(g, bx + bw - 13.0, by + 13.0, 10, null);
            }
        }
        // The tag goes on top of the veil: a price the wallet cannot cover yet is exactly the
        // number the player wants to read, so it stays crisp while the rest of the card dims.
        renderBadge(g, bx, by, bw);
        if (isSelected()) {
            Stroke old = g.getStroke();
            g.setStroke(SELECTED_STROKE);
            g.setColor(Accessibility.tone(ProceduralArt.COIN_GOLD));
            g.drawRoundRect(bx + 1, by + 1, bw - 2, bh - 2, ProceduralArt.BUTTON_RADIUS,
                    ProceduralArt.BUTTON_RADIUS);
            g.setStroke(old);
        }
    }

    /**
     * Draws the state: a price on a wooden tag, an owned check, or the bare word.
     *
     * @param g the context
     * @param bx the card's left edge
     * @param by the card's top edge
     * @param bw the card's width
     */
    private void renderBadge(Graphics2D g, int bx, int by, int bw) {
        if (badge().isEmpty()) {
            return;
        }
        g.setFont(Fonts.bold(TAG_SIZE));
        double textWidth = TextPainter.width(g, badge());
        double centre = bx + bw / 2.0;
        double baseline = TextPainter.centeredBaseline(g, by + TAG_TOP + TAG_H / 2.0);
        if (hasCoinBadge()) {
            int tagWidth = (int) Math.round(textWidth + 2 * COIN_R + 3 * PADDING);
            int tagX = (int) Math.round(centre - tagWidth / 2.0);
            g.setColor(ProceduralArt.WOOD);
            g.fillRoundRect(tagX, by + TAG_TOP, tagWidth, TAG_H, 5, 5);
            g.setColor(TAG_GRAIN);
            g.drawLine(tagX + 3, by + TAG_TOP + 3, tagX + tagWidth - 4, by + TAG_TOP + 3);
            g.setColor(TAG_EDGE);
            g.drawRoundRect(tagX, by + TAG_TOP, tagWidth - 1, TAG_H - 1, 5, 5);
            ProceduralArt.drawCoin(g, coin, tagX + PADDING + COIN_R - 1.0,
                    by + TAG_TOP + TAG_H / 2.0, COIN_R, 1);
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, badge(), tagX + tagWidth - PADDING, baseline,
                    TextPainter.Align.RIGHT);
            return;
        }
        if (owned) {
            double left = centre - (textWidth + CHECK_SIZE + 4) / 2;
            ProceduralArt.drawCheck(g, left + CHECK_SIZE / 2.0, by + TAG_TOP + TAG_H / 2.0,
                    CHECK_SIZE, Accessibility.tone(OWNED));
            g.setColor(Accessibility.tone(OWNED));
            TextPainter.draw(g, badge(), left + CHECK_SIZE + 4, baseline);
            return;
        }
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.drawCentered(g, badge(), centre, baseline);
    }

    /**
     * Re-measures the two clipped strings, but only when the text, the room or the text scale
     * moved — a card's text changes only when the profile or the language does.
     *
     * @param g the context, with the title font about to be set
     * @param room the width the strings have to fit
     */
    private void measure(Graphics2D g, int room) {
        double scale = Fonts.textScale();
        if (shownTitleSource == title() && shownSubtitleSource == subtitle()
                && shownRoom == room && shownScale == scale) {
            return;
        }
        g.setFont(Fonts.bold(TITLE_SIZE));
        shownTitle = TextPainter.ellipsise(g, title(), Math.max(0, room));
        g.setFont(Fonts.regular(SUBTITLE_SIZE));
        shownSubtitle = TextPainter.ellipsise(g, subtitle(), Math.max(0, room));
        shownTitleSource = title();
        shownSubtitleSource = subtitle();
        shownRoom = room;
        shownScale = scale;
    }
}
