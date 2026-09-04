package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.screens.BossBanner;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The boss banner (D17, M8): the telegraph counts down in whole seconds, the fight shows the
 * survival countdown, the clear flashes and hides itself, a world boss is named by its world and a
 * challenge boss by its challenge (E26), and a live language switch relabels the line.
 */
class BossBannerTest {

    private Strings strings;
    private BossBanner banner;

    @BeforeEach
    void setUp() {
        strings = Strings.load("en");
        Strings.use(strings);
        banner = new BossBanner(strings);
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
    }

    @Test
    void nothingShowsUntilAFactArrives() {
        assertFalse(banner.isVisible());
        assertEquals(BossBanner.Phase.HIDDEN, banner.phase());
        assertEquals("", banner.line());
        assertEquals("", banner.bossName());
        assertEquals(0, banner.announcements());
    }

    @Test
    void theWarningCountsDownInWholeSeconds() {
        banner.announce(new TickFact.BossWarning("green_fields", "green_fields", 120));
        assertEquals(BossBanner.Phase.WARNING, banner.phase());
        assertTrue(banner.isVisible());
        assertEquals(120, banner.remaining());
        assertEquals("Green Fields", banner.bossName());
        assertEquals(strings.format(StringKey.BOSS_WARNING, "Green Fields", 2), banner.line());

        // One tick does not move a whole-second readout; the next second boundary does.
        banner.tick();
        assertEquals(119, banner.remaining());
        assertEquals(strings.format(StringKey.BOSS_WARNING, "Green Fields", 2), banner.line());
        for (int i = 0; i < Playfield.TICK_RATE - 1; i++) {
            banner.tick();
        }
        assertEquals(60, banner.remaining());
        assertEquals(strings.format(StringKey.BOSS_WARNING, "Green Fields", 1), banner.line());
    }

    @Test
    void theFightShowsTheSurvivalCountdown() {
        banner.announce(new TickFact.BossWarning("green_fields", "green_fields", 60));
        banner.announce(new TickFact.BossStarted("green_fields", 900));
        assertEquals(BossBanner.Phase.ACTIVE, banner.phase());
        assertEquals(2, banner.announcements(), "warning and opener are two facts");
        assertEquals(900, banner.remaining());
        assertEquals(strings.format(StringKey.BOSS_FIGHT, "Green Fields", 15), banner.line(),
                "the fight keeps the name the warning announced (E26)");

        banner.tick();
        assertEquals(strings.format(StringKey.BOSS_FIGHT, "Green Fields", 15), banner.line(),
                "899 ticks still read 15 whole seconds");
        for (int i = 0; i < Playfield.TICK_RATE - 1; i++) {
            banner.tick();
        }
        assertEquals(strings.format(StringKey.BOSS_FIGHT, "Green Fields", 14), banner.line());
    }

    @Test
    void theClearFlashesAndHidesItself() {
        banner.announce(new TickFact.BossWarning("green_fields", "green_fields", 60));
        banner.announce(new TickFact.BossCleared("green_fields", "green_fields"));
        assertEquals(BossBanner.Phase.CLEARED, banner.phase());
        assertEquals(strings.format(StringKey.BOSS_CLEARED, "Green Fields"), banner.line());
        assertTrue(banner.isVisible());

        for (int i = 0; i < BossBanner.CLEARED_TICKS; i++) {
            banner.tick();
        }
        assertEquals(BossBanner.Phase.HIDDEN, banner.phase());
        assertFalse(banner.isVisible());
        assertEquals("", banner.line());
    }

    @Test
    void aChallengeBossIsNamedByItsChallenge() {
        banner.announce(new TickFact.BossWarning("boss_corridor_1", null, 60));
        assertEquals("Corridor Boss", banner.bossName(), "E26: the challenge owns the encounter");
        assertEquals(strings.format(StringKey.BOSS_WARNING, "Corridor Boss", 1), banner.line());
    }

    @Test
    void aLanguageSwitchRelabelsTheBanner() {
        banner.announce(new TickFact.BossWarning("green_fields", "green_fields", 120));
        strings.reload("pt_BR");
        banner.refreshTexts();
        assertEquals(strings.format(StringKey.BOSS_WARNING, "Green Fields", 2), banner.line());
        assertTrue(banner.line().endsWith("em 2s"), banner.line());
        banner.announce(new TickFact.BossCleared("green_fields", "green_fields"));
        assertTrue(banner.line().endsWith("vencido!"), banner.line());
    }

    @Test
    void resetHidesEverything() {
        banner.announce(new TickFact.BossWarning("green_fields", "green_fields", 60));
        banner.reset();
        assertFalse(banner.isVisible());
        assertEquals("", banner.line());
        assertEquals(0, banner.remaining());
        assertEquals(0, banner.announcements(), "a new run starts the count over");
    }

    @Test
    void theRenderPaintsOnlyWhileVisible() {
        BufferedImage image = new BufferedImage(Playfield.WIDTH, Playfield.HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            banner.render(g, WorldPalette.GREEN_FIELDS);
            int blank = image.getRGB(10, 10);
            banner.announce(new TickFact.BossWarning("green_fields", "green_fields", 120));
            banner.render(g, WorldPalette.GREEN_FIELDS);
            boolean changed = false;
            for (int x = BossBanner.PANEL_RIGHT - BossBanner.PANEL_W;
                    x < BossBanner.PANEL_RIGHT && !changed; x++) {
                for (int y = BossBanner.PANEL_Y; y < BossBanner.PANEL_Y + BossBanner.PANEL_H;
                        y++) {
                    if (image.getRGB(x, y) != blank) {
                        changed = true;
                        break;
                    }
                }
            }
            assertTrue(changed, "the banner panel is painted inside the ground strip");
            assertTrue(banner.lineFont() >= BossBanner.LINE_FONT_MIN
                    && banner.lineFont() <= BossBanner.LINE_FONT_MAX, "the line laid out");
        } finally {
            g.dispose();
        }
    }
}
