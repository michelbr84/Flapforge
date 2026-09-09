package io.github.michelbr84.flapforge.ui.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/**
 * The Forge node card's title column (M13): the name is ellipsised against the room left of the
 * price badge, not against the card's own edge. The defect this locks had the pt_BR title
 * "Corrente Ascendente" of flight's tier 3 hard-clip into the coin+400 badge drawn over its last
 * letters.
 */
class ForgeNodeCardTest {

    /** The pt_BR title that clipped into its price badge before the fix. */
    private static final String TITLE = "Corrente Ascendente";
    /** The price the defect's badge carried. */
    private static final String PRICE = "400";
    /** A short name that fits any room the card can offer. */
    private static final String SHORT_TITLE = "Peso Pena";

    @Test
    void aTitleWiderThanTheBadgeRoomEndsInAnEllipsisLeftOfTheBadge() {
        ForgeNodeCard card = card(TITLE, PRICE, true);
        String shown = render(card);
        assertTrue(shown.endsWith(TextPainter.ELLIPSIS),
                () -> "the title was drawn whole over the badge: " + shown);
        assertTrue(TITLE.startsWith(shown.substring(0,
                        shown.length() - TextPainter.ELLIPSIS.length())),
                () -> "the ellipsis replaced the title's tail: " + shown);
        assertClearOfTheBadge(card, shown);
    }

    @Test
    void aTitleThatFitsTheBadgeRoomIsDrawnWhole() {
        assertEquals(SHORT_TITLE, render(card(SHORT_TITLE, PRICE, true)),
                "a name with room needs no ellipsis");
    }

    /**
     * The room the badge takes is the title's loss and nothing else: without a badge the same
     * title has the card's whole width again.
     */
    @Test
    void withoutABadgeTheTitleKeepsTheWholeCardRoom() {
        // No two machines hand out the same font metrics, so how wide this title is cannot be
        // a premise — on the CI runner it is a sixth wider than it is here. What no font can
        // move is the geometry: the badge's width and its pad come out of the title's room
        // when there is a badge, and nothing does when there is not.
        ForgeNodeCard withBadge = card(TITLE, PRICE, true);
        int badgeRoom = cardRight() - ForgeNodeCard.BADGE_INSET - badgeWidth(withBadge)
                - ForgeNodeCard.TITLE_BADGE_PAD - textLeft();
        int wholeRoom = cardRight() - ForgeNodeCard.BADGE_INSET - textLeft();
        assertTrue(wholeRoom > badgeRoom,
                () -> "the card grew no room when the badge went away: " + wholeRoom
                        + " <= " + badgeRoom);

        // And the drawing agrees with the geometry: dropping the badge never cuts the name
        // shorter, and the name it draws stops at the card's own inset, not at the badge's.
        String drawnWithBadge = render(withBadge);
        String drawnWhole = render(card(TITLE, "", false));
        assertTrue(prefixOf(drawnWhole).length() >= prefixOf(drawnWithBadge).length(),
                () -> "no badge, yet the name kept the badge's room: " + drawnWithBadge
                        + " / " + drawnWhole);
        int insetRight = cardRight() - ForgeNodeCard.BADGE_INSET;
        assertTrue(textLeft() + widthOfTitle(drawnWhole) <= insetRight,
                () -> "the name ran past the card's inset: " + widthOfTitle(drawnWhole)
                        + " > " + wholeRoom);
    }

    /**
     * The cut has to hold at any size the player picks (D25) — a larger text scale is the same
     * problem a wider font is, and the CI runner's font is a sixth wider than this machine's,
     * which is how the first version of this test passed here and failed there.
     */
    @Test
    void aBiggerTextScaleStillEndsInAnEllipsisLeftOfTheBadge() {
        double scale = Fonts.textScale();
        try {
            Fonts.setTextScale(Fonts.MAX_TEXT_SCALE);
            ForgeNodeCard card = card(TITLE, PRICE, true);
            String shown = render(card);
            assertTrue(shown.endsWith(TextPainter.ELLIPSIS),
                    () -> "the title was drawn whole over the badge: " + shown);
            assertClearOfTheBadge(card, shown);
        } finally {
            Fonts.setTextScale(scale);
        }
    }

    private void assertClearOfTheBadge(ForgeNodeCard card, String shown) {
        int titleRight = textLeft() + widthOfTitle(shown);
        int badgeLeft = cardRight() - ForgeNodeCard.BADGE_INSET - badgeWidth(card);
        assertTrue(titleRight + ForgeNodeCard.TITLE_BADGE_PAD <= badgeLeft,
                () -> "the title ends at " + titleRight + " but the badge starts at "
                        + badgeLeft);
    }

    /** The title as drawn, with the ellipsis of a cut name taken off. */
    private static String prefixOf(String shown) {
        return shown.endsWith(TextPainter.ELLIPSIS)
                ? shown.substring(0, shown.length() - TextPainter.ELLIPSIS.length())
                : shown;
    }

    /** The width the price badge takes on a card: the price, and the coin in front of it. */
    private static int badgeWidth(ForgeNodeCard card) {
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        try {
            ProceduralArt.prepare(g);
            g.setFont(Fonts.bold(ForgeNodeCard.BADGE_SIZE));
            return (int) Math.ceil(TextPainter.width(g, PRICE))
                    + (card.hasCoinBadge() ? ForgeNodeCard.COIN_ROOM : 0);
        } finally {
            g.dispose();
        }
    }

    /** The width the title's font gives a string. */
    private static int widthOfTitle(String text) {
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        try {
            ProceduralArt.prepare(g);
            g.setFont(Fonts.bold(ForgeNodeCard.TITLE_SIZE));
            return (int) Math.ceil(TextPainter.width(g, text));
        } finally {
            g.dispose();
        }
    }

    /** The right edge of the card's own room, before the badge's inset. */
    private static int cardRight() {
        return UpgradeTreeScreen.MARGIN + cellWidth();
    }

    /** A card in the flight tree's own cell geometry. */
    private ForgeNodeCard card(String title, String badge, boolean coin) {
        ForgeNodeCard card = new ForgeNodeCard("node", title, () -> {
        });
        card.setBadge(badge, coin);
        card.setBounds(UpgradeTreeScreen.MARGIN, 0, cellWidth(), UpgradeTreeScreen.NODE_H);
        return card;
    }

    /** The width one node card is laid out with, the way the screen's rebuild computes it. */
    private static int cellWidth() {
        return (UpgradeTreeScreen.CONTENT_W
                - CardGrid.DEFAULT_GAP * (UpgradeTreeScreen.COLUMNS - 1))
                / UpgradeTreeScreen.COLUMNS;
    }

    /** The left edge of the card's text column. */
    private static int textLeft() {
        return UpgradeTreeScreen.MARGIN + ForgeNodeCard.ICON_INSET + ForgeNodeCard.ICON_SIZE
                + ForgeNodeCard.TEXT_GAP;
    }

    /** Renders the card headlessly and returns the title as it was actually drawn. */
    private static String render(ForgeNodeCard card) {
        BufferedImage frame = new BufferedImage(Playfield.WIDTH, Playfield.HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        try {
            ProceduralArt.prepare(g);
            card.render(g);
        } finally {
            g.dispose();
        }
        return card.shownTitle();
    }
}
