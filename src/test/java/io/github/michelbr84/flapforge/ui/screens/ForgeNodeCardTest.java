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
        // The title under test must sit in the band this test measures: wider than the badge
        // room, narrower than the card's own.
        ForgeNodeCard card = card(TITLE, "", false);
        int textRoom = cellWidth() - ForgeNodeCard.BADGE_INSET - textLeft();
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        g.setFont(Fonts.bold(ForgeNodeCard.TITLE_SIZE));
        int fullWidth = TextPainter.width(g, TITLE);
        g.dispose();
        assertTrue(fullWidth <= textRoom, () -> "the title left the card room this test"
                + " assumes: " + fullWidth + " > " + textRoom);
        assertEquals(TITLE, render(card), "no badge, no narrower room");
    }

    private void assertClearOfTheBadge(ForgeNodeCard card, String shown) {
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        ProceduralArt.prepare(g);
        g.setFont(Fonts.bold(ForgeNodeCard.TITLE_SIZE));
        int titleRight = textLeft() + TextPainter.width(g, shown);
        g.setFont(Fonts.bold(ForgeNodeCard.BADGE_SIZE));
        int badgeWidth = TextPainter.width(g, PRICE)
                + (card.hasCoinBadge() ? ForgeNodeCard.COIN_ROOM : 0);
        int badgeLeft = UpgradeTreeScreen.MARGIN + cellWidth() - ForgeNodeCard.BADGE_INSET
                - badgeWidth;
        g.dispose();
        assertTrue(titleRight + ForgeNodeCard.TITLE_BADGE_PAD <= badgeLeft,
                () -> "the title ends at " + titleRight + " but the badge starts at "
                        + badgeLeft);
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
