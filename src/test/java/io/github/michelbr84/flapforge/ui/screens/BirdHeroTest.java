package io.github.michelbr84.flapforge.ui.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.ui.component.AttributeBadge;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The protagonist of the bird selection (M11): the bob and glow waves, the slide a bird change
 * starts, the three badges, and a non-blank render of every status in both contrast modes.
 */
class BirdHeroTest {

    /** The highest row the hero may paint: the header's own glyphs live above it. */
    private static final int CEILING = 36;

    private final GameContent content = GameContent.load();
    private final BirdHero hero = new BirdHero();

    @AfterEach
    void tearDown() {
        Accessibility.clear();
        Fonts.setTextScale(1.0);
    }

    private void show(String birdId, BirdHero.Status status, int direction) {
        BirdDef bird = content.birds().get(birdId);
        hero.bind(bird, bird.palettes().isEmpty() ? null : bird.palettes().get(0), birdId,
                status.name(), status, BirdAttributes.of(bird, content), direction);
        hero.setLabels("Mobility", "Defence", "Control");
    }

    @Test
    void theBobIsATriangleWaveAroundTheRestingLine() {
        assertEquals(-BirdHero.BOB_AMPLITUDE / 2, BirdHero.bobAt(0), 1e-9);
        assertEquals(BirdHero.BOB_AMPLITUDE / 2, BirdHero.bobAt(BirdHero.BOB_PERIOD_TICKS / 2),
                1e-9, "the top of the wave at half the period");
        assertEquals(BirdHero.bobAt(0), BirdHero.bobAt(BirdHero.BOB_PERIOD_TICKS), 1e-9);
        assertEquals(BirdHero.bobAt(7), BirdHero.bobAt(7 + BirdHero.BOB_PERIOD_TICKS), 1e-9);
        for (long tick = 0; tick < BirdHero.BOB_PERIOD_TICKS; tick++) {
            assertTrue(Math.abs(BirdHero.bobAt(tick)) <= BirdHero.BOB_AMPLITUDE / 2 + 1e-9,
                    "the bob never leaves its amplitude");
        }
    }

    @Test
    void theGlowPulsesUnlessTheFlashingWasCappedOrTheContrastRaised() {
        assertEquals(BirdHero.GLOW_BASE, hero.glowAlpha(0), 1e-9);
        assertEquals(BirdHero.GLOW_BASE + BirdHero.GLOW_PULSE,
                hero.glowAlpha(BirdHero.GLOW_PERIOD_TICKS / 2), 1e-9, "peak at half the period");
        assertEquals(BirdHero.GLOW_BASE, hero.glowAlpha(BirdHero.GLOW_PERIOD_TICKS), 1e-9);
        hero.setReduceFlashing(true);
        assertEquals(BirdHero.GLOW_BASE, hero.glowAlpha(BirdHero.GLOW_PERIOD_TICKS / 2), 1e-9,
                "the pulse is what reduce flashing takes away");
        hero.setReduceFlashing(false);
        Accessibility.setHighContrast(true);
        assertEquals(BirdHero.GLOW_BASE / 2, hero.glowAlpha(0), 1e-9,
                "half as bright where the plates are already loud");
    }

    @Test
    void aBirdChangeSlidesAndARefreshDoesNot() {
        assertNull(hero.birdId());
        show("classic", BirdHero.Status.SELECTED, 0);
        assertEquals("classic", hero.birdId());
        assertFalse(hero.isSliding(), "the first bird arrives, it does not slide in");
        show("classic", BirdHero.Status.OWNED, 1);
        assertFalse(hero.isSliding(), "the same bird again is a refresh");
        show("guardian", BirdHero.Status.OWNED, 0);
        assertFalse(hero.isSliding(), "direction zero is a refresh that changed the bird");
        show("swift", BirdHero.Status.LOCKED, 1);
        assertTrue(hero.isSliding());
        for (int i = 0; i < BirdHero.SLIDE_TICKS; i++) {
            assertTrue(hero.isSliding(), "still sliding after " + i + " ticks");
            hero.tick(i);
        }
        assertFalse(hero.isSliding(), "the slide lasts exactly SLIDE_TICKS ticks");
        show("heavy", BirdHero.Status.OWNED, -1);
        assertTrue(hero.isSliding());
        hero.snap();
        assertFalse(hero.isSliding(), "entering the screen ends any slide at once");
    }

    @Test
    void theThreeBadgesCarryTheBirdsOwnScores() {
        show("guardian", BirdHero.Status.SELECTED, 0);
        assertEquals(3, hero.badges().size());
        BirdAttributes scores = BirdAttributes.of(content.birds().get("guardian"), content);
        assertEquals(scores, hero.attributes());
        assertEquals("Mobility", hero.badges().get(0).label());
        assertEquals(scores.mobility(), hero.badges().get(0).value());
        assertEquals("Defence", hero.badges().get(1).label());
        assertEquals(scores.defence(), hero.badges().get(1).value());
        assertEquals("Control", hero.badges().get(2).label());
        assertEquals(scores.control(), hero.badges().get(2).value());
        for (AttributeBadge badge : hero.badges()) {
            assertFalse(badge.isFocusable(), badge.label() + " is a readout");
            assertEquals(BirdHero.BADGE_TOP, badge.y(), 1e-9);
            assertEquals(BirdHero.BADGE_W, badge.width(), 1e-9);
            assertTrue(badge.y() + badge.height() <= BirdHero.BAND_BOTTOM,
                    badge.label() + " stays inside the hero band");
        }
        AttributeBadge middle = hero.badges().get(1);
        assertSame(middle, hero.badgeAt(middle.centerX(), middle.centerY()));
        assertNull(hero.badgeAt(middle.centerX(), BirdHero.BAND_TOP + 2),
                "the sky above the row is not a badge");
    }

    @Test
    void everyStatusRendersNonBlankInsideTheBand() {
        for (boolean highContrast : new boolean[] {false, true}) {
            Accessibility.setHighContrast(highContrast);
            for (BirdHero.Status status : BirdHero.Status.values()) {
                show(status == BirdHero.Status.LOCKED ? "mystic" : "guardian", status, 0);
                assertBandNonBlank(status + " contrast=" + highContrast, 0);
            }
        }
        Accessibility.clear();
        show("swift", BirdHero.Status.OWNED, 1);
        assertTrue(hero.isSliding());
        assertBandNonBlank("mid slide", 0);
        Fonts.setTextScale(1.5);
        hero.setLabels("Mobilidade", "Defesa", "Controle");
        assertBandNonBlank("scaled labels", BirdHero.BOB_PERIOD_TICKS / 2);
    }

    private void assertBandNonBlank(String what, long ticks) {
        BufferedImage img = new BufferedImage(Playfield.WIDTH, Playfield.HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            ProceduralArt.prepare(g);
            hero.render(g, BirdHero.bobAt(ticks), ticks);
        } finally {
            g.dispose();
        }
        Set<Integer> colours = new HashSet<>();
        for (int y = BirdHero.BAND_TOP; y < BirdHero.BAND_BOTTOM; y++) {
            for (int x = 0; x < Playfield.WIDTH; x++) {
                colours.add(img.getRGB(x, y));
            }
        }
        assertTrue(colours.size() >= 8, what + " drew almost nothing");
        // The name's ascent reaches a few pixels into the header band at a large text scale, and
        // the screen draws the header last so the title and the coin chip win that strip. What
        // must never happen is the hero climbing to the top edge.
        int top = Playfield.HEIGHT;
        for (int y = 0; y < BirdHero.BAND_TOP; y++) {
            for (int x = 0; x < Playfield.WIDTH; x++) {
                if (img.getRGB(x, y) != 0) {
                    top = Math.min(top, y);
                }
            }
        }
        assertTrue(top >= CEILING, what + " painted up to y " + top);
    }
}
