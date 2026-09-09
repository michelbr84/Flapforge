package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;

/**
 * One node of an upgrade tree as a row card (M13): the glyph on the left, the name over the
 * {@code Lv owned/max - effect} line, and the state on the right end — the next level's price
 * with a coin, or nothing but a padlock when the card is locked, because a locked card has no
 * price the player can act on.
 *
 * <p>It is a {@link CardGrid.Card} with the inherited API — title, subtitle, badge, tooltip, the
 * locked, dimmed and selected flags — redrawn in the Forge's row anatomy, the way
 * {@link ShopCard} is for the shop's portrait cards. Activating the card only
 * <em>selects</em> it: the purchase lives on the panel's call to action, so one tap can never
 * spend coins on something the player has not read.
 *
 * <p>The two measured strings are cached on their source, the room they were measured for and
 * the text scale, so a card that has not changed costs no measuring in the render path.
 */
final class ForgeNodeCard extends CardGrid.Card {

    /** Size of the node glyph. */
    public static final int ICON_SIZE = 25;
    /** Left inset of the glyph. */
    public static final int ICON_INSET = 8;
    /** Gap between the glyph and the text column. */
    public static final int TEXT_GAP = 8;
    /** Point size of the node name. */
    public static final int TITLE_SIZE = 12;
    /** Point size of the {@code Lv o/m - effect} line. */
    public static final int SUBTITLE_SIZE = 9;
    /** Point size of the price badge. */
    public static final int BADGE_SIZE = 12;
    /** Room kept clear at the right end of the card. */
    public static final int BADGE_INSET = 8;
    /** Radius of the coin in front of a price. */
    static final int COIN_R = 5;
    /** The room a coin adds in front of a price: its diameter and the gap around it. */
    static final int COIN_ROOM = 2 * COIN_R + 6;
    /** Room kept between an ellipsised title and the price badge left of it. */
    static final int TITLE_BADGE_PAD = 6;

    private static final Stroke SELECTED_STROKE = new BasicStroke(2f);

    private final Ellipse2D.Double coin = new Ellipse2D.Double();
    private IconPainter glyph;
    private double viewportTop = Double.NEGATIVE_INFINITY;
    private double viewportBottom = Double.POSITIVE_INFINITY;
    private String shownTitle = "";
    private String shownTitleSource;
    private String shownSubtitle = "";
    private String shownSubtitleSource;
    private int shownRoom = -1;
    private int shownTitleRoom = -1;
    private double shownScale;

    /**
     * Creates a card.
     *
     * @param id the node id the card stands for
     * @param title the translated name
     * @param onAction what activating the card does (selection, never a purchase)
     */
    ForgeNodeCard(String id, String title, Runnable onAction) {
        super(id, title, onAction);
    }

    /**
     * Gives the card its glyph.
     *
     * @param newGlyph the painter, or {@code null} for a text-only card
     */
    void setGlyph(IconPainter newGlyph) {
        this.glyph = newGlyph;
    }

    /**
     * Limits where the card answers to the pointer, to the band the tree viewport is clipped to.
     *
     * <p>The tree scrolls by moving its cards rather than the canvas, so a card scrolled past
     * the top or the bottom still has bounds — it is simply not drawn. Without this clamp the
     * focus ring would hover, click and tooltip a card nobody can see, which is the same trap
     * {@link ShopCard#setViewport} avoids for the shop grid.
     *
     * @param top the first visible row of pixels
     * @param bottom the last visible row of pixels
     */
    void setViewport(double top, double bottom) {
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

        boolean greyed = isLocked() || isDimmed();
        if (glyph != null) {
            glyph.paint(g, bx + ICON_INSET + ICON_SIZE / 2.0, by + bh / 2.0, ICON_SIZE,
                    greyed ? ProceduralArt.TEXT_MUTED : ProceduralArt.TEXT_LIGHT);
        }

        double textLeft = bx + ICON_INSET + ICON_SIZE + TEXT_GAP;
        double right = bx + bw - BADGE_INSET;
        int textRoom = (int) Math.max(0, Math.round(right - textLeft));

        // The badge owns the right end of the card. The name is ellipsised against the room
        // left of it — the badge's left edge minus a small padding, not the card's edge — so a
        // long translation ends in an ellipsis instead of clipping into the price; the state
        // line, which no badge shares a baseline with, keeps the room to the card's inset. The
        // clip below stays as the backstop for both.
        double badgeTextWidth = 0;
        double badgeWidth = 0;
        if (!badge().isEmpty()) {
            g.setFont(Fonts.bold(BADGE_SIZE));
            badgeTextWidth = TextPainter.width(g, badge());
            badgeWidth = badgeTextWidth + (hasCoinBadge() ? COIN_ROOM : 0);
        }
        int titleRoom = badgeWidth > 0
                ? (int) Math.max(0, Math.round(right - badgeWidth - TITLE_BADGE_PAD - textLeft))
                : textRoom;
        measure(g, textRoom, titleRoom);

        if (badgeWidth > 0) {
            g.setFont(Fonts.bold(BADGE_SIZE));
            g.setColor(greyed ? ProceduralArt.TEXT_MUTED : ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, badge(), right, by + bh / 2.0 + 4, TextPainter.Align.RIGHT);
            if (hasCoinBadge()) {
                ProceduralArt.drawCoin(g, coin, right - badgeTextWidth - COIN_R - 3,
                        by + bh / 2.0, COIN_R, 1);
            }
        }
        Shape unclipped = g.getClip();
        g.clipRect((int) Math.round(textLeft), by,
                (int) Math.max(0, bx + bw - BADGE_INSET - badgeWidth - textLeft), bh);
        g.setFont(Fonts.bold(TITLE_SIZE));
        g.setColor(greyed ? ProceduralArt.TEXT_MUTED : ProceduralArt.buttonTextColor(state));
        TextPainter.draw(g, shownTitle, textLeft, by + bh / 2.0 - 2);
        g.setFont(Fonts.regular(SUBTITLE_SIZE));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, shownSubtitle, textLeft, by + bh / 2.0 + 12);
        g.setClip(unclipped);

        if (greyed) {
            g.setColor(ForgeArt.CARD_VEIL);
            g.fillRoundRect(bx, by, bw, bh, ProceduralArt.BUTTON_RADIUS,
                    ProceduralArt.BUTTON_RADIUS);
            if (isLocked()) {
                // A padlock means "not open at all"; the veil alone means "not right now".
                ProceduralArt.drawPadlock(g, bx + bw - 13.0, by + 13.0, 9, ForgeArt.PADLOCK);
            }
        }
        if (isSelected()) {
            Stroke old = g.getStroke();
            g.setStroke(SELECTED_STROKE);
            g.setColor(ForgeArt.SELECTION);
            g.drawRoundRect(bx + 1, by + 1, bw - 2, bh - 2, ProceduralArt.BUTTON_RADIUS,
                    ProceduralArt.BUTTON_RADIUS);
            g.setStroke(old);
        }
    }

    /**
     * The title as it will actually be drawn, ellipsis included — exposed for the card's
     * regression test, which asserts the ellipsis against the badge's room.
     *
     * @return the ellipsised title
     */
    String shownTitle() {
        return shownTitle;
    }

    /**
     * Re-measures the two clipped strings, but only when the text, their rooms or the text scale
     * moved — a card's text changes only when the profile or the language does.
     *
     * @param g the context, with the title font about to be set
     * @param roomWidth the width the state line has to fit
     * @param titleRoom the width the name has to fit, left of the price badge
     */
    private void measure(Graphics2D g, int roomWidth, int titleRoom) {
        double scale = Fonts.textScale();
        if (shownTitleSource == title() && shownSubtitleSource == subtitle()
                && shownRoom == roomWidth && shownTitleRoom == titleRoom && shownScale == scale) {
            return;
        }
        g.setFont(Fonts.bold(TITLE_SIZE));
        shownTitle = TextPainter.ellipsise(g, title(), titleRoom);
        g.setFont(Fonts.regular(SUBTITLE_SIZE));
        shownSubtitle = TextPainter.ellipsise(g, subtitle(), roomWidth);
        shownTitleSource = title();
        shownSubtitleSource = subtitle();
        shownRoom = roomWidth;
        shownTitleRoom = titleRoom;
        shownScale = scale;
    }
}
