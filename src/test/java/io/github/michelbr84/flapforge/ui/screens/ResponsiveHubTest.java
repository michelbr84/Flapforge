package io.github.michelbr84.flapforge.ui.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UnlockManager;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.SectionNav;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The responsive hub regression: the hub and the shop pin their navigation band to the bottom
 * of the elastic surface ({@link LayoutMetrics#navTop()}), pin their headers to its top, and
 * give the room a tall phone frees to the content between them — never to a dead band above
 * the bar. At the classic 420x640 surface the layout must stay identical to the fixed
 * geometry it replaced.
 *
 * <p>The band geometry is asserted through {@code ScreenManager#metrics()} and the screens'
 * own node bounds, never through a measured pixel width (the runner's font is not the game's);
 * the rendered frames are only checked to be non-blank.
 */
class ResponsiveHubTest {

    private static final int PHONE_W = 1080;
    private static final int PHONE_H = 2400;
    private static final int CLASSIC_W = 420;
    private static final int CLASSIC_H = 640;
    private static final int PHONE_TOP = -147;
    private static final int PHONE_NAV_TOP = 728;

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
    void hubPinsNavigationToTheBottomOfATallSurface() {
        ScreenManager screens = new ScreenManager(new Viewport(PHONE_W, PHONE_H, false));
        MainMenuScreen hub = new MainMenuScreen(screens);
        screens.push(hub);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        LayoutMetrics metrics = screens.metrics();
        // The elastic band reaches above the playfield and ends well below 640.
        assertEquals(PHONE_TOP, metrics.contentTop());
        assertEquals(PHONE_NAV_TOP, metrics.navTop());
        assertEquals(metrics.contentBottom(), metrics.navTop() + metrics.navHeight());

        // The navigation rides the bottom edge of the usable area, not row 582.
        assertEquals(metrics.navTop(), (int) hub.navBar().y());
        assertEquals(metrics.width(), (int) hub.navBar().width());
        assertEquals(metrics.navHeight(), (int) hub.navBar().height());
        assertEquals(metrics.contentBottom(),
                (int) (hub.navBar().y() + hub.navBar().height()));

        // The CTA cluster sits just above the band: no dead gap above the navigation.
        assertEquals(metrics.navTop() - 108, (int) hub.startRunButton().y());
        assertTrue(hub.startRunButton().y() + hub.startRunButton().height() <= metrics.navTop());

        assertTrue(distinctColors(frame(screens, PHONE_W, PHONE_H)) >= 2);
    }

    @Test
    void shopPinsNavigationToTheBottomOfATallSurface() {
        ScreenManager screens = new ScreenManager(new Viewport(PHONE_W, PHONE_H, false));
        ShopScreen shop = new ShopScreen(screens, Strings.active(), GameContent.load(),
                profile(), unlocks(), upgrades(), new ToastLayer());
        screens.push(shop);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        LayoutMetrics metrics = screens.metrics();
        assertEquals(PHONE_TOP, metrics.contentTop());
        assertEquals(PHONE_NAV_TOP, metrics.navTop());

        // The header and the grid pin to the top of the band...
        assertEquals(metrics.contentTop() + ShopScreen.TABS_TOP, (int) shop.tabBar().y());
        assertEquals(metrics.contentTop() + ShopScreen.GRID_TOP - (int) shop.scroll(),
                (int) shop.offerGrid().y());
        // ...and the grid's visible band grows down to the navigation instead of 446.
        assertEquals(metrics.navTop() - (SectionNav.TOP - ShopScreen.GRID_BOTTOM),
                shop.gridBottom());

        // The navigation rides the bottom edge of the usable area.
        assertEquals(metrics.navTop(), (int) shop.nav().y());
        assertEquals(metrics.contentBottom(), (int) (shop.nav().y() + shop.nav().height()));

        assertTrue(distinctColors(frame(screens, PHONE_W, PHONE_H)) >= 2);
    }

    @Test
    void classicSurfaceKeepsTheFixedHubGeometry() {
        ScreenManager screens = new ScreenManager(new Viewport(CLASSIC_W, CLASSIC_H, false));
        MainMenuScreen hub = new MainMenuScreen(screens);
        screens.push(hub);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        LayoutMetrics metrics = screens.metrics();
        assertEquals(LayoutMetrics.classic(), metrics);
        assertEquals(0, metrics.contentTop());
        assertEquals(582, metrics.navTop());

        assertEquals(582, (int) hub.navBar().y());
        assertEquals(Playfield.WIDTH, (int) hub.navBar().width());
        assertEquals(58, (int) hub.navBar().height());
        assertEquals(40, (int) hub.startRunButton().x());
        assertEquals(474, (int) hub.startRunButton().y());
        assertEquals(340, (int) hub.startRunButton().width());
        assertEquals(62, (int) hub.startRunButton().height());

        assertTrue(distinctColors(frame(screens, CLASSIC_W, CLASSIC_H)) >= 2);
    }

    @Test
    void classicSurfaceKeepsTheFixedShopGeometry() {
        ScreenManager screens = new ScreenManager(new Viewport(CLASSIC_W, CLASSIC_H, false));
        ShopScreen shop = new ShopScreen(screens, Strings.active(), GameContent.load(),
                profile(), unlocks(), upgrades(), new ToastLayer());
        screens.push(shop);
        screens.applyPending();
        for (int i = 0; i < 5; i++) {
            screens.tick(InputFrame.EMPTY);
        }

        LayoutMetrics metrics = screens.metrics();
        assertEquals(LayoutMetrics.classic(), metrics);
        assertEquals(0, metrics.contentTop());
        assertEquals(582, metrics.navTop());

        assertEquals(52, (int) shop.tabBar().y());
        assertEquals(ShopScreen.GRID_TOP - (int) shop.scroll(), (int) shop.offerGrid().y());
        assertEquals(ShopScreen.GRID_BOTTOM, shop.gridBottom());
        assertEquals(582, (int) shop.nav().y());
        assertEquals(640, (int) (shop.nav().y() + shop.nav().height()));

        assertTrue(distinctColors(frame(screens, CLASSIC_W, CLASSIC_H)) >= 2);
    }

    private PlayerProfile profile() {
        FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
        return PlayerProfile.fresh(time.epochMillis()).normalize();
    }

    private UnlockManager unlocks() {
        return new UnlockManager(progression(), () -> saves++);
    }

    private UpgradeManager upgrades() {
        return new UpgradeManager(progression(), () -> saves++);
    }

    private ProgressionManager progression() {
        return new ProgressionManager(new FixedTimeSource(1_700_000_000_000L),
                ProgressionManager.AchievementHook.NONE,
                UnlockEvaluator.of(GameContent.load()));
    }

    private BufferedImage frame(ScreenManager screens, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        // The presenters publish the visible band before applying the viewport; do the same so
        // the background paints into the elastic band instead of stopping at row 0.
        screens.viewport().publishOverscan();
        screens.viewport().apply(g);
        screens.render(g, 0.5);
        g.dispose();
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
