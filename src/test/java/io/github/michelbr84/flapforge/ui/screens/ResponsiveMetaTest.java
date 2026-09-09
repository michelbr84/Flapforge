package io.github.michelbr84.flapforge.ui.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.SelectionManager;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UnlockManager;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.render.Overscan;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.NavBar;
import io.github.michelbr84.flapforge.ui.component.NavButton;
import io.github.michelbr84.flapforge.ui.component.SectionNav;
import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The responsive surface, pinned where the screens meet it. The Forge and the bird selection lay
 * their bands out against {@link LayoutMetrics}: at the classic 420x640 viewport the metrics are
 * the classic ones and every band lands where it always did; on a 1080x2400 (20:9) phone the
 * logical surface grows to 420x933, the header pins above the playfield's first row, the
 * navigation pins to the surface's bottom edge instead of floating 377 physical pixels above
 * it, and the content region takes the room between. Both screens render at both sizes into
 * {@code build/responsive-qa/} so a human can look at what the geometry asserts.
 */
class ResponsiveMetaTest {

    /** A 1080x2400 phone publishes a 420x933 logical band, letterboxing centred. */
    private static final int PHONE_W = 1080;
    private static final int PHONE_H = 2400;
    private static final int PHONE_TOP = -147;
    private static final int PHONE_H_LOGICAL = 933;
    private static final int PHONE_NAV_TOP = 728;

    /** Every navigation item, whatever its role on the row. */
    private static final String[] NAV_IDS = {SectionNav.SHOP, SectionNav.BIRDS, SectionNav.PLAY,
        SectionNav.FORGE, SectionNav.GOALS};

    private Strings strings;
    private GameContent content;

    @BeforeEach
    void setUp() {
        strings = Strings.load("en");
        Strings.use(strings);
        content = GameContent.load();
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
        Overscan.reset();
    }

    /**
     * The Forge at the classic viewport keeps the bands it always had, and on the phone's band
     * it pins the header above row 0 and the navigation to the surface's bottom edge.
     */
    @Test
    void forgePinsItsBandsToTheSurface() throws IOException {
        Rig classic = rig(Playfield.WIDTH, Playfield.HEIGHT);
        UpgradeTreeScreen classicForge = forge(classic);
        tick(classic);
        LayoutMetrics classicMetrics = classic.screens().metrics();
        assertEquals(LayoutMetrics.classic(), classicMetrics);
        assertBand(classicForge.nav(), classicMetrics);
        assertEquals(100, round(classicForge.tabBar().y()), "tabs under the header at row 100");
        assertNotBlank(draw(classic.screens(), classic.viewport(), Playfield.WIDTH,
                Playfield.HEIGHT, "forge-classic.png"));

        Rig phone = rig(PHONE_W, PHONE_H);
        UpgradeTreeScreen phoneForge = forge(phone);
        tick(phone);
        LayoutMetrics phoneMetrics = phone.screens().metrics();
        assertEquals(PHONE_TOP, phoneMetrics.contentTop(), "the band reaches above row 0");
        assertEquals(PHONE_H_LOGICAL, phoneMetrics.height(), "the surface grew, not stretched");
        assertEquals(PHONE_NAV_TOP, phoneMetrics.navTop(), "navigation on the physical bottom");
        assertBand(phoneForge.nav(), phoneMetrics);
        assertTrue(phoneForge.tabBar().y() < 0, "header pinned above the playfield's first row");
        assertNotBlank(draw(phone.screens(), phone.viewport(), PHONE_W, PHONE_H,
                "forge-phone.png"));
    }

    /**
     * The bird selection at the classic viewport keeps the bands it always had, and on the
     * phone's band it pins the header above row 0 and the navigation to the surface's bottom
     * edge.
     */
    @Test
    void birdSelectionPinsItsBandsToTheSurface() throws IOException {
        Rig classic = rig(Playfield.WIDTH, Playfield.HEIGHT);
        BirdSelectionScreen classicBirds = birds(classic);
        tick(classic);
        LayoutMetrics classicMetrics = classic.screens().metrics();
        assertEquals(LayoutMetrics.classic(), classicMetrics);
        assertBand(classicBirds.nav(), classicMetrics);
        assertEquals(0, round(classicBirds.header().y()), "header at the top of the band");
        assertNotBlank(draw(classic.screens(), classic.viewport(), Playfield.WIDTH,
                Playfield.HEIGHT, "birds-classic.png"));

        Rig phone = rig(PHONE_W, PHONE_H);
        BirdSelectionScreen phoneBirds = birds(phone);
        tick(phone);
        LayoutMetrics phoneMetrics = phone.screens().metrics();
        assertEquals(PHONE_TOP, phoneMetrics.contentTop(), "the band reaches above row 0");
        assertEquals(PHONE_H_LOGICAL, phoneMetrics.height(), "the surface grew, not stretched");
        assertEquals(PHONE_NAV_TOP, phoneMetrics.navTop(), "navigation on the physical bottom");
        assertBand(phoneBirds.nav(), phoneMetrics);
        assertTrue(phoneBirds.header().y() < 0, "header pinned above the playfield's first row");
        assertNotBlank(draw(phone.screens(), phone.viewport(), PHONE_W, PHONE_H,
                "birds-phone.png"));
    }

    /**
     * The navigation row keeps a margin above the band's bottom edge at every size, so the gold
     * plate never runs into the physical screen edge — the place a phone's gesture bar lives and
     * the one row a thumb cannot reach without leaving the game.
     */
    @Test
    void theNavigationRowKeepsAMarginAboveTheSurfaceEdge() {
        for (Rig rig : new Rig[] {rig(Playfield.WIDTH, Playfield.HEIGHT), rig(PHONE_W, PHONE_H)}) {
            UpgradeTreeScreen screen = forge(rig);
            tick(rig);
            NavBar nav = screen.nav();
            double bandBottom = nav.y() + nav.height();
            for (String id : NAV_IDS) {
                NavButton item = nav.button(id);
                assertTrue(item.y() + item.height() < bandBottom - 4,
                        () -> id + " clears the band's bottom edge");
            }
            assertTrue(nav.button(SectionNav.PLAY).y() > nav.y(),
                    "the row starts inside the band, not above it");
        }
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * One screen stack over one viewport, with the progression services a hub screen reads.
     *
     * @param screens the screen stack
     * @param viewport the viewport the stack lays out against
     * @param profile a fresh profile
     * @param upgrades the Forge's purchase path
     * @param selection the bird selection's writer
     * @param unlocks the bird selection's purchase path
     */
    private record Rig(ScreenManager screens, Viewport viewport, PlayerProfile profile,
            UpgradeManager upgrades, SelectionManager selection, UnlockManager unlocks) {
    }

    /**
     * Builds a stack over a viewport of the given window size, the way the app host does.
     *
     * @param width the window width in pixels
     * @param height the window height in pixels
     * @return the rig
     */
    private Rig rig(int width, int height) {
        Viewport viewport = new Viewport(width, height, false);
        ScreenManager screens = new ScreenManager(viewport);
        screens.setPresenter(new NullPresenter(screens, viewport, width, height));
        FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
        PlayerProfile profile = PlayerProfile.fresh(time.epochMillis()).normalize();
        ProgressionManager progression = new ProgressionManager(time,
                ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
        return new Rig(screens, viewport, profile,
                new UpgradeManager(progression, () -> {
                }),
                new SelectionManager(progression, () -> {
                }),
                new UnlockManager(progression, () -> {
                }));
    }

    /**
     * Builds the Forge over a rig and puts it on the stack.
     *
     * @param rig the rig
     * @return the screen
     */
    private UpgradeTreeScreen forge(Rig rig) {
        UpgradeTreeScreen screen = new UpgradeTreeScreen(rig.screens(), strings, content,
                rig.profile(), rig.upgrades(), null);
        rig.screens().push(screen);
        return screen;
    }

    /**
     * Builds the bird selection over a rig and puts it on the stack.
     *
     * @param rig the rig
     * @return the screen
     */
    private BirdSelectionScreen birds(Rig rig) {
        BirdSelectionScreen screen = new BirdSelectionScreen(rig.screens(), strings, content,
                rig.profile(), rig.selection(), rig.unlocks(), null);
        rig.screens().push(screen);
        return screen;
    }

    /**
     * Applies the pending push and advances the stack a few ticks, so the screens settle into
     * their laid-out state before they are measured or drawn.
     *
     * @param rig the rig
     */
    private void tick(Rig rig) {
        rig.screens().applyPending();
        for (int i = 0; i < 5; i++) {
            rig.screens().tick(InputFrame.EMPTY);
        }
    }

    /**
     * Asserts the navigation band's geometry against the metrics: the band starts at the
     * metrics' nav top, spans the design width and stands the navigation band's height.
     *
     * @param nav the bar
     * @param metrics the metrics of the surface
     */
    private static void assertBand(NavBar nav, LayoutMetrics metrics) {
        assertEquals(metrics.navTop(), round(nav.y()), "band top at the metrics' nav top");
        assertEquals(LayoutMetrics.DESIGN_W, round(nav.width()), "band spans the design width");
        assertEquals(LayoutMetrics.NAV_BAND_H, round(nav.height()), "band is one nav tall");
    }

    /**
     * Renders the stack's top screen in the viewport's logical space and writes it as a PNG
     * under {@code build/responsive-qa/}, with the vertical extension on so the whole visible
     * band — not just the classic 640 rows — reaches the picture.
     *
     * @param screens the stack
     * @param viewport the viewport to apply
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param name the file name under {@code build/responsive-qa/}
     * @return the drawn image
     * @throws IOException when the PNG cannot be written
     */
    private static BufferedImage draw(ScreenManager screens, Viewport viewport, int width,
            int height, String name) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            viewport.setExtendVertical(true);
            viewport.publishOverscan();
            viewport.apply(g);
            screens.render(g, 0.5);
        } finally {
            g.dispose();
            Overscan.reset();
        }
        Path out = Path.of("build", "responsive-qa", name);
        Files.createDirectories(out.getParent());
        ImageIO.write(image, "png", out.toFile());
        return image;
    }

    /**
     * Asserts the picture is not blank: at least two distinct colours in a coarse scan.
     *
     * @param image the drawn image
     */
    private static void assertNotBlank(BufferedImage image) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < image.getHeight() && colours.size() < 2; y += 4) {
            for (int x = 0; x < image.getWidth() && colours.size() < 2; x += 4) {
                colours.add(image.getRGB(x, y));
            }
        }
        assertTrue(colours.size() >= 2, "the frame drew nothing but the background");
    }

    /**
     * Rounds a logical coordinate to the int the layout bands use.
     *
     * @param value the coordinate
     * @return the rounded value
     */
    private static int round(double value) {
        return (int) Math.round(value);
    }
}
