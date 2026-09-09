package io.github.michelbr84.flapforge.ui.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The responsive secondary-screen regression: the world picker, the run summary, the statistics
 * and the settings carry no five-item navigation bar, so they anchor their footer buttons to
 * {@link LayoutMetrics#contentBottom()} instead of {@link LayoutMetrics#navTop()} — and the room
 * a tall phone frees goes to the scrolling region between the header and the footer, never to a
 * dead letterbox-coloured band above and below the content. At the classic 420x640 surface every
 * value must stay exactly the fixed constant it replaced.
 *
 * <p>The band geometry is asserted through {@code ScreenManager#metrics()} and the screens' own
 * node bounds and live-band getters, never through a measured pixel width (the runner's font is
 * not the game's). Each frame is also written to {@code build/responsive-qa/} as a PNG, so the
 * migrated screens can be looked at and not only measured; the assertion on them is only that
 * they are not blank.
 */
class ResponsiveSecondaryTest {

    private static final int PHONE_W = 1080;
    private static final int PHONE_H = 2400;
    private static final int CLASSIC_W = 420;
    private static final int CLASSIC_H = 640;
    private static final int PHONE_TOP = -147;
    private static final int PHONE_BOTTOM = 786;

    private int saves;

    @BeforeEach
    void setUp() {
        Strings.use(Strings.load("en"));
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
    }

    @Test
    void worldSelectPinsFooterToTheBottomOfATallSurface() {
        ScreenManager screens = new ScreenManager(new Viewport(PHONE_W, PHONE_H, false));
        WorldSelectScreen screen = new WorldSelectScreen(screens, Strings.active(),
                GameContent.load(), profile(), null, new ToastLayer());
        screens.push(screen);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        LayoutMetrics metrics = screens.metrics();
        assertEquals(PHONE_TOP, metrics.contentTop());
        assertEquals(PHONE_BOTTOM, metrics.contentBottom());

        // The Back button rides the bottom edge of the usable area, not row 584.
        assertEquals(metrics.contentBottom() - 56, (int) screen.backButton().y());
        // The title side pins to the top of the band...
        assertEquals(metrics.contentTop() + 56, (int) screen.worldGrid().y());
        // ...and the detail panel keeps its classic adjacency below the card list: the room the
        // tall surface frees goes into the card rows themselves, not into a dead gap between
        // the list and the panel that belongs to the same selection flow.
        int rows = screen.worldIds().size();
        int classicGridH = rows * WorldSelectScreen.CARD_H
                + (rows - 1) * WorldSelectScreen.CARD_GAP;
        int classicGap = 362 - (56 + classicGridH);
        int tallGap = (int) screen.tierList().y()
                - ((int) screen.worldGrid().y() + (int) screen.worldGrid().height());
        assertEquals(classicGap, tallGap);
        assertTrue(screen.worldGrid().cellHeight() > WorldSelectScreen.CARD_H,
                "the tall surface must grow the card rows");
        // The panel's own layout is unchanged: the description rides its classic 52 rows below
        // the difficulty row.
        assertEquals((int) screen.tierList().y() + 52, screen.descriptionBaseline());

        assertTrue(distinctColors(frame(screens, PHONE_W, PHONE_H, "worldselect-phone.png")) >= 2);
    }

    @Test
    void runSummaryPinsFooterToTheBottomOfATallSurface() {
        ScreenManager screens = new ScreenManager(new Viewport(PHONE_W, PHONE_H, false));
        RunSummaryScreen screen = new RunSummaryScreen(screens, run(12), () -> { });
        screens.push(screen);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        LayoutMetrics metrics = screens.metrics();
        assertEquals(PHONE_TOP, metrics.contentTop());
        assertEquals(PHONE_BOTTOM, metrics.contentBottom());

        // Retry and Menu ride the bottom edge of the usable area, not row 584.
        assertEquals(metrics.contentBottom() - 56, (int) screen.retryButton().y());
        assertEquals(metrics.contentBottom() - 56, (int) screen.menuButton().y());
        // The scrolling area pins to the top of the band and grows down to its classic 66-row
        // distance above the footer.
        assertEquals(metrics.contentTop() + 56, screen.viewTop());
        assertEquals(metrics.contentBottom() - 66, screen.viewBottom());
        assertTrue(screen.viewBottom() - screen.viewTop() > 574 - 56);
        // The freed height goes into the breakdown itself: the block spreads its rows and
        // section breaks over the panel instead of leaving the interior a void below the seed
        // line.
        assertTrue(screen.contentHeight() >= screen.viewBottom() - screen.viewTop() - 1,
                () -> "the stretched breakdown must fill the view: " + screen.contentHeight());

        assertTrue(distinctColors(frame(screens, PHONE_W, PHONE_H, "runsummary-phone.png")) >= 2);
    }

    @Test
    void statisticsPinsFooterToTheBottomOfATallSurface() {
        ScreenManager screens = new ScreenManager(new Viewport(PHONE_W, PHONE_H, false));
        StatisticsScreen screen = new StatisticsScreen(screens);
        screens.push(screen);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        LayoutMetrics metrics = screens.metrics();
        assertEquals(PHONE_TOP, metrics.contentTop());
        assertEquals(PHONE_BOTTOM, metrics.contentBottom());

        // Back rides the bottom edge of the usable area, not row 584...
        assertEquals(metrics.contentBottom() - 56, (int) screen.backButton().y());
        // ...and the history row and the prestige action keep their classic distances above it.
        int footerTop = metrics.contentBottom() - 56;
        assertEquals(footerTop - 72, (int) screen.historyList().y());
        assertEquals(footerTop - 38, (int) screen.prestigeButton().y());
        // The scrolling area pins to the top of the band and grows down to its classic 88-row
        // distance above the footer.
        assertEquals(metrics.contentTop() + 140, screen.viewTop());
        assertEquals(footerTop - 88, screen.viewBottom());
        assertTrue(screen.viewBottom() - screen.viewTop() > 496 - 140);

        assertTrue(distinctColors(frame(screens, PHONE_W, PHONE_H, "statistics-phone.png")) >= 2);
    }

    @Test
    void settingsPinsFooterToTheBottomOfATallSurface() {
        ScreenManager screens = new ScreenManager(new Viewport(PHONE_W, PHONE_H, false));
        SettingsScreen screen = new SettingsScreen(screens);
        screens.push(screen);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        LayoutMetrics metrics = screens.metrics();
        assertEquals(PHONE_TOP, metrics.contentTop());
        assertEquals(PHONE_BOTTOM, metrics.contentBottom());

        // Restore defaults and Back ride the bottom edge of the usable area, not row 582.
        assertEquals(metrics.contentBottom() - 58, (int) screen.restoreButton().y());
        assertEquals(metrics.contentBottom() - 58, (int) screen.backButton().y());
        // The scrolling area pins to the top of the band and grows down to its classic 62-row
        // distance above the footer.
        assertEquals(metrics.contentTop() + 78, screen.viewTop());
        assertEquals(metrics.contentBottom() - 62, screen.viewBottom());
        assertTrue(screen.viewBottom() - screen.viewTop() > 578 - 78);

        assertTrue(distinctColors(frame(screens, PHONE_W, PHONE_H, "settings-phone.png")) >= 2);
    }

    @Test
    void classicSurfaceKeepsTheFixedWorldSelectGeometry() {
        ScreenManager screens = new ScreenManager(new Viewport(CLASSIC_W, CLASSIC_H, false));
        WorldSelectScreen screen = new WorldSelectScreen(screens, Strings.active(),
                GameContent.load(), profile(), null, new ToastLayer());
        screens.push(screen);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        assertEquals(LayoutMetrics.classic(), screens.metrics());
        assertEquals(584, (int) screen.backButton().y());
        assertEquals(56, (int) screen.worldGrid().y());
        assertEquals(362, (int) screen.tierList().y());
        assertEquals(414, screen.descriptionBaseline());

        assertTrue(distinctColors(frame(screens, CLASSIC_W, CLASSIC_H, "worldselect-classic.png")) >= 2);
    }

    @Test
    void classicSurfaceKeepsTheFixedRunSummaryGeometry() {
        ScreenManager screens = new ScreenManager(new Viewport(CLASSIC_W, CLASSIC_H, false));
        RunSummaryScreen screen = new RunSummaryScreen(screens, run(12), () -> { });
        screens.push(screen);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        assertEquals(LayoutMetrics.classic(), screens.metrics());
        assertEquals(584, (int) screen.retryButton().y());
        assertEquals(584, (int) screen.menuButton().y());
        assertEquals(56, screen.viewTop());
        assertEquals(574, screen.viewBottom());

        assertTrue(distinctColors(frame(screens, CLASSIC_W, CLASSIC_H, "runsummary-classic.png")) >= 2);
    }

    @Test
    void classicSurfaceKeepsTheFixedStatisticsGeometry() {
        ScreenManager screens = new ScreenManager(new Viewport(CLASSIC_W, CLASSIC_H, false));
        StatisticsScreen screen = new StatisticsScreen(screens);
        screens.push(screen);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        assertEquals(LayoutMetrics.classic(), screens.metrics());
        assertEquals(584, (int) screen.backButton().y());
        assertEquals(512, (int) screen.historyList().y());
        assertEquals(546, (int) screen.prestigeButton().y());
        assertEquals(140, screen.viewTop());
        assertEquals(496, screen.viewBottom());

        assertTrue(distinctColors(frame(screens, CLASSIC_W, CLASSIC_H, "statistics-classic.png")) >= 2);
    }

    @Test
    void classicSurfaceKeepsTheFixedSettingsGeometry() {
        ScreenManager screens = new ScreenManager(new Viewport(CLASSIC_W, CLASSIC_H, false));
        SettingsScreen screen = new SettingsScreen(screens);
        screens.push(screen);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        assertEquals(LayoutMetrics.classic(), screens.metrics());
        assertEquals(582, (int) screen.restoreButton().y());
        assertEquals(582, (int) screen.backButton().y());
        assertEquals(78, screen.viewTop());
        assertEquals(578, screen.viewBottom());

        assertTrue(distinctColors(frame(screens, CLASSIC_W, CLASSIC_H, "settings-classic.png")) >= 2);
    }

    private PlayerProfile profile() {
        FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
        return PlayerProfile.fresh(time.epochMillis()).normalize();
    }

    /** A finished run with gates, points, a streak and coins picked up in the world. */
    private static RunResult run(int gates) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(gates);
        stats.setPoints(gates);
        stats.addCoinsCollected(7);
        stats.setStreak(gates);
        stats.setStreakSteps(gates / 5);
        for (int i = 0; i < gates * 60; i++) {
            stats.tickAlive();
        }
        stats.setDeathCause(CollisionCause.OBSTACLE);
        Map<String, Long> counters = new LinkedHashMap<>();
        return new RunResult(RunConfig.builder(42L).mode(RunMode.SEEDED).build(), stats, counters);
    }

    /**
     * Renders the top screen into a picture and writes it under {@code build/responsive-qa/}, so
     * the migrated screens can be looked at and not only measured.
     *
     * @param screens the stack
     * @param w the image width
     * @param h the image height
     * @param name the file name
     * @return the image
     */
    private BufferedImage frame(ScreenManager screens, int w, int h, String name) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        // The presenters publish the visible band before applying the viewport; do the same so
        // the background paints into the elastic band instead of stopping at row 0.
        screens.viewport().publishOverscan();
        screens.viewport().apply(g);
        screens.render(g, 0.5);
        g.dispose();
        try {
            Path out = Path.of("build", "responsive-qa", name);
            Files.createDirectories(out.getParent());
            ImageIO.write(image, "png", out.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return image;
    }

    private int distinctColors(BufferedImage image) {
        Set<Integer> seen = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 5) {
            for (int x = 0; x < image.getWidth(); x += 5) {
                seen.add(image.getRGB(x, y));
            }
        }
        return seen.size();
    }
}
