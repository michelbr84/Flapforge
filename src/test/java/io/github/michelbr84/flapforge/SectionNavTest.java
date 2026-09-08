package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import io.github.michelbr84.flapforge.ui.component.NavBar;
import io.github.michelbr84.flapforge.ui.component.NavButton;
import io.github.michelbr84.flapforge.ui.component.SectionNav;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The five-item bottom navigation the hub and its section screens share (M11): the item order,
 * the primary plate, the routes, the geometry and the five glyphs.
 */
class SectionNavTest {

    private final List<String> activated = new ArrayList<>();

    @AfterEach
    void tearDown() {
        Accessibility.clear();
    }

    private NavBar build(String primaryId) {
        return SectionNav.build(new NavBar(), primaryId,
                new SectionNav.Routes(() -> activated.add("shop"), () -> activated.add("birds"),
                        () -> activated.add("play"), () -> activated.add("forge"),
                        () -> activated.add("goals")));
    }

    @Test
    void theBarCarriesTheFiveItemsInTheHubsOrder() {
        NavBar nav = build(SectionNav.BIRDS);
        assertEquals(5, nav.buttons().size());
        assertEquals(List.of(SectionNav.SHOP, SectionNav.BIRDS, SectionNav.PLAY,
                SectionNav.FORGE, SectionNav.GOALS),
                nav.buttons().stream().map(NavButton::id).toList());
        assertEquals(SectionNav.TOP, nav.y(), 1e-9);
        assertEquals(SectionNav.HEIGHT, nav.height(), 1e-9);
        assertEquals(Playfield.WIDTH, nav.width(), 1e-9);
        for (NavButton button : nav.buttons()) {
            assertNotNull(button.icon(), button.id() + " has a glyph");
            assertTrue(button.y() >= SectionNav.TOP, button.id() + " stays in the band");
            assertTrue(button.y() + button.height() <= SectionNav.TOP + SectionNav.HEIGHT,
                    button.id() + " stays in the band");
        }
    }

    @Test
    void theSectionItselfIsThePrimaryPlate() {
        NavBar birds = build(SectionNav.BIRDS);
        assertTrue(birds.button(SectionNav.BIRDS).isPrimary(), "you are here");
        assertFalse(birds.button(SectionNav.PLAY).isPrimary());
        NavButton primary = birds.button(SectionNav.BIRDS);
        NavButton play = birds.button(SectionNav.PLAY);
        assertTrue(primary.height() > play.height(), "the gold plate is the taller one");
        assertTrue(primary.width() > play.width());
        activated.clear();
        NavBar hub = build(SectionNav.PLAY);
        assertTrue(hub.button(SectionNav.PLAY).isPrimary(), "on the hub the run is primary");
        assertFalse(hub.button(SectionNav.BIRDS).isPrimary());
    }

    @Test
    void eachItemRunsItsOwnRoute() {
        NavBar nav = build(SectionNav.BIRDS);
        for (NavButton button : nav.buttons()) {
            assertTrue(button.activate(), button.id() + " is enabled");
        }
        assertEquals(List.of(SectionNav.SHOP, SectionNav.BIRDS, SectionNav.PLAY,
                SectionNav.FORGE, SectionNav.GOALS), activated);
    }

    @Test
    void anItemWithoutARouteIsDrawnAndWalkedOverButDoesNothing() {
        NavBar nav = SectionNav.build(new NavBar(), SectionNav.GOALS,
                new SectionNav.Routes(null, null, null, null, null));
        for (NavButton button : nav.buttons()) {
            assertTrue(button.isFocusable(), button.id() + " is still walked over");
            assertFalse(button.activate(), button.id() + " has nothing to do");
        }
    }

    @Test
    void theGlyphsAreOnePerItemAndAnUnknownIdIsRefused() {
        for (String id : List.of(SectionNav.SHOP, SectionNav.BIRDS, SectionNav.PLAY,
                SectionNav.FORGE, SectionNav.GOALS)) {
            assertNotNull(SectionNav.icon(id), id);
        }
        assertThrows(IllegalArgumentException.class, () -> SectionNav.icon("statistics"));
        assertThrows(NullPointerException.class, () -> SectionNav.icon(null));
        assertThrows(IllegalArgumentException.class,
                () -> SectionNav.build(new NavBar(), "statistics",
                        new SectionNav.Routes(null, null, null, null, null)));
    }

    @Test
    void everyGlyphRendersNonBlankInBothContrastModes() {
        for (boolean highContrast : new boolean[] {false, true}) {
            Accessibility.setHighContrast(highContrast);
            for (String id : List.of(SectionNav.SHOP, SectionNav.BIRDS, SectionNav.PLAY,
                    SectionNav.FORGE, SectionNav.GOALS)) {
                assertGlyphNonBlank(id, SectionNav.icon(id), highContrast);
            }
        }
    }

    @Test
    void theBarRendersNonBlankWithTheSectionHighlighted() {
        NavBar nav = build(SectionNav.BIRDS);
        BufferedImage img = new BufferedImage(Playfield.WIDTH, Playfield.HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            ProceduralArt.prepare(g);
            ProceduralArt.navBand(g, SectionNav.TOP, SectionNav.HEIGHT);
            nav.render(g);
        } finally {
            g.dispose();
        }
        Set<Integer> colours = new HashSet<>();
        NavButton primary = nav.button(SectionNav.BIRDS);
        for (int y = (int) primary.y(); y < primary.y() + primary.height(); y++) {
            for (int x = (int) primary.x(); x < primary.x() + primary.width(); x++) {
                colours.add(img.getRGB(x, y));
            }
        }
        assertTrue(colours.size() >= 3, "the section plate is uniform");
    }

    private static void assertGlyphNonBlank(String what, IconPainter icon, boolean highContrast) {
        BufferedImage img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            ProceduralArt.prepare(g);
            icon.paint(g, 24, 24, 32, ProceduralArt.TEXT_LIGHT);
        } finally {
            g.dispose();
        }
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                colours.add(img.getRGB(x, y));
            }
        }
        assertTrue(colours.size() >= 2,
                what + " is blank (high contrast " + highContrast + ")");
    }
}
