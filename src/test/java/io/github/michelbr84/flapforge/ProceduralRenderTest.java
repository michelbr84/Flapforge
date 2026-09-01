package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionOutcome;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.render.AssetManager;
import io.github.michelbr84.flapforge.render.AssetResolver;
import io.github.michelbr84.flapforge.render.DebugOverlay;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.ui.screens.BootScreen;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import io.github.michelbr84.flapforge.ui.screens.RunSummaryScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
import io.github.michelbr84.flapforge.ui.screens.StatisticsScreen;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Headless rendering of everything the game draws (D18): the icon at every size and every screen
 * through {@link NullPresenter} into a {@link BufferedImage}, asserting non-blank output and no
 * exception, with an empty asset manifest so every pixel comes from {@link ProceduralArt}.
 *
 * <p>From M2 the sweep runs in <b>both shipped languages</b> and covers the boot splash, the
 * menu, the settings screen with and without an open key capture, and the four phases of a run.
 * A language with a longer word, a missing string or a screen that only builds its labels in the
 * constructor shows up here as an exception or a blank frame.
 */
class ProceduralRenderTest {

    private static final String ACCENTED = "ção Ω";
    /** A manifest declaring the test sheet under the id the bird resolves (D18). */
    private static final String TEST_SHEET_MANIFEST = "{\"version\": 1, \"assets\": ["
            + "{\"id\": \"bird\", \"path\": \"sprites/test_sheet.png\", \"kind\": \"SHEET\","
            + " \"frameWidth\": 16, \"frameHeight\": 16,"
            + " \"license\": \"CC0-1.0\", \"source\": \"drawn for this test\"}]}";
    /** Per-frame allocation budget of the game screen, in bytes. */
    private static final long ALLOCATION_BUDGET_BYTES = 24 * 1024;

    @Test
    void iconIsNonBlankAtEverySize() {
        for (int size : ProceduralArt.ICON_SIZES) {
            BufferedImage icon = ProceduralArt.icon(size);
            assertEquals(size, icon.getWidth());
            assertEquals(size, icon.getHeight());
            assertTrue(distinctColours(icon, 1) >= 2, "icon " + size + " is uniform");
        }
        List<BufferedImage> all = ProceduralArt.icons();
        assertEquals(ProceduralArt.ICON_SIZES.size(), all.size());
    }

    @Test
    void mainMenuRendersNonBlank() {
        BufferedImage frame = renderScreen(MainMenuScreen::new, 30);
        assertTrue(distinctColours(frame, 2) >= 2, "main menu is uniform");
    }

    @Test
    void mainMenuRendersAtDoubleScaleWithLetterbox() {
        BufferedImage frame = renderScreen(MainMenuScreen::new, 5, 1000, 1280);
        assertTrue(distinctColours(frame, 4) >= 2, "scaled main menu is uniform");
        int letterbox = frame.getRGB(10, 640) & 0xFFFFFF;
        assertEquals(WorldPalette.GREEN_FIELDS.letterbox(), letterbox,
                "letterbox bar uses the palette letterbox tone");
    }

    @Test
    void settingsScreenRendersNonBlank() {
        BufferedImage frame = renderScreen(SettingsScreen::new, 5);
        assertTrue(distinctColours(frame, 2) >= 2, "settings screen is uniform");
    }

    @Test
    void runSummaryRendersNonBlank() {
        Fixture fixture = Fixture.played();
        BufferedImage frame = renderScreen(sm -> fixture.summary(sm), 5);
        assertTrue(distinctColours(frame, 2) >= 2, "run summary is uniform");
    }

    @Test
    void statisticsScreenRendersNonBlank() {
        Fixture fixture = Fixture.played();
        BufferedImage frame = renderScreen(sm -> fixture.statistics(sm), 5);
        assertTrue(distinctColours(frame, 2) >= 2, "statistics screen is uniform");
    }

    @Test
    void anEmptyProfileRendersBothM3Screens() {
        // A brand-new installation: no run has been played, so the summary has no reward section
        // and every statistic is zero. Neither may throw, and neither may come out blank.
        Fixture fresh = Fixture.fresh();
        assertTrue(distinctColours(renderScreen(sm -> fresh.summary(sm), 3), 2) >= 2,
                "the summary of an empty profile is uniform");
        assertTrue(distinctColours(renderScreen(sm -> new StatisticsScreen(sm), 3), 2) >= 2,
                "the statistics of an empty profile are uniform");
    }

    @Test
    void gameScreenRendersEveryPhaseNonBlank() {
        for (Phase phase : Phase.values()) {
            Rig rig = new Rig();
            rig.driveTo(phase);
            BufferedImage frame = rig.frame(0.5);
            assertTrue(distinctColours(frame, 2) >= 2, phase + " frame is uniform");
            assertTrue(rig.screens.depth() >= 1);
        }
    }

    @Test
    void gameScreenRendersEveryPhaseWithTheDebugOverlayOn() {
        for (Phase phase : Phase.values()) {
            Rig rig = new Rig();
            rig.screens.setDebugOverlayVisible(true);
            rig.driveTo(phase);
            BufferedImage frame = rig.frame(0.25);
            assertTrue(distinctColours(frame, 2) >= 2, phase + " frame with F3 is uniform");
        }
    }

    @Test
    void flyingFrameShowsAGateAndTheGroundHasScrolled() {
        Rig rig = new Rig();
        rig.driveTo(Phase.FLYING);
        assertFalse(rig.game.run().simulation().obstacles().isEmpty(), "a gate is on screen");
        assertTrue(rig.game.renderer().background().distance() > 0, "the ground scrolled");
        BufferedImage frame = rig.frame(0.0);
        assertTrue(distinctColours(frame, 2) >= 2);
    }

    @Test
    @Tag("perf")
    void aGameFrameStaysWithinItsAllocationBudget() {
        // The renderers cache every palette colour and reuse their shapes, and the HUD rebuilds
        // its score string only when the score changes (D18), so a steady frame must allocate far
        // less than a naive one would. Measured per thread, so other JVM activity cannot inflate
        // it; skipped when the JVM does not expose per-thread allocation counters. Section 7 keeps
        // budgets in `perfTest` because they drift with the JDK and the machine -- and because an
        // assumeTrue skip must not hide inside the milestone's own `test` gate.
        com.sun.management.ThreadMXBean threads = allocationCounter();
        assumeTrue(threads != null, "no per-thread allocation counter on this JVM");
        Rig rig = new Rig();
        rig.driveTo(Phase.FLYING);
        for (int i = 0; i < 50; i++) {
            rig.frame(0.5); // warm up the font, glyph and paint caches
        }
        long id = Thread.currentThread().getId();
        long before = threads.getThreadAllocatedBytes(id);
        int frames = 300;
        for (int i = 0; i < frames; i++) {
            rig.frame(0.5);
        }
        long perFrame = (threads.getThreadAllocatedBytes(id) - before) / frames;
        System.out.println("[render] game frame allocates " + perFrame + " bytes");
        assertTrue(perFrame < ALLOCATION_BUDGET_BYTES, "a game frame allocated " + perFrame
                + " bytes, budget " + ALLOCATION_BUDGET_BYTES);
    }

    @Test
    void aManifestEntryReplacesTheProceduralBird() {
        // D18's drop-in path: with the shipped (empty) manifest the bird is drawn by
        // ProceduralArt; declaring a sheet for the id must actually change the pixels, or the
        // whole AssetManager/AssetResolver layer is dead code.
        Rig procedural = new Rig();
        procedural.driveTo(Phase.FLYING);
        assertNull(procedural.game.renderer().bird().sheet(), "the shipped game is procedural");
        BufferedImage before = copy(procedural.frame(0.0));

        AssetResolver.use(new AssetResolver(AssetManager.fromJson(TEST_SHEET_MANIFEST)));
        try {
            Rig withSheet = new Rig();
            withSheet.driveTo(Phase.FLYING);
            assertNotNull(withSheet.game.renderer().bird().sheet(),
                    "the manifest entry must reach BirdRenderer");
            BufferedImage after = withSheet.frame(0.0);
            assertFalse(identical(before, after), "the sheet must change what is drawn");
        } finally {
            AssetResolver.use(AssetResolver.empty());
        }

        Rig backToProcedural = new Rig();
        backToProcedural.driveTo(Phase.FLYING);
        assertNull(backToProcedural.game.renderer().bird().sheet());
    }

    @Test
    void everyScreenRendersInBothLanguages() {
        String original = Strings.active().language();
        Map<String, BufferedImage> byLanguage = new LinkedHashMap<>();
        try {
            for (String language : Strings.LANGUAGES) {
                Strings.use(Strings.load(language));

                BufferedImage boot = renderScreen(sm -> new BootScreen(sm, new DirectExecutor(),
                        () -> new MainMenuScreen(sm)), 5);
                assertTrue(distinctColours(boot, 2) >= 2, "boot screen is uniform in " + language);

                BufferedImage menu = renderScreen(MainMenuScreen::new, 30);
                assertTrue(distinctColours(menu, 2) >= 2, "menu is uniform in " + language);
                byLanguage.put(language + "-menu", copy(menu));

                BufferedImage settings = renderScreen(SettingsScreen::new, 5);
                assertTrue(distinctColours(settings, 2) >= 2, "settings is uniform in " + language);
                byLanguage.put(language + "-settings", copy(settings));

                BufferedImage capture = renderScreen(sm -> {
                    SettingsScreen screen = new SettingsScreen(sm);
                    screen.startCapture(InputAction.FLAP);
                    return screen;
                }, 3);
                assertTrue(distinctColours(capture, 2) >= 2,
                        "the key-capture prompt is uniform in " + language);

                Fixture fixture = Fixture.played();
                BufferedImage summary = renderScreen(sm -> fixture.summary(sm), 5);
                assertTrue(distinctColours(summary, 2) >= 2,
                        "run summary is uniform in " + language);
                byLanguage.put(language + "-summary", copy(summary));

                BufferedImage statistics = renderScreen(sm -> fixture.statistics(sm), 5);
                assertTrue(distinctColours(statistics, 2) >= 2,
                        "statistics is uniform in " + language);
                byLanguage.put(language + "-statistics", copy(statistics));

                for (Phase phase : Phase.values()) {
                    Rig rig = new Rig();
                    rig.driveTo(phase);
                    assertTrue(distinctColours(rig.frame(0.5), 2) >= 2,
                            phase + " frame is uniform in " + language);
                }
            }
        } finally {
            Strings.use(Strings.load(original));
        }
        // Two frames that are pixel-identical would mean the sweep proves nothing about the
        // translation actually reaching the screen.
        assertFalse(identical(byLanguage.get("en-menu"), byLanguage.get("pt_BR-menu")),
                "the menu must look different in the two languages");
        assertFalse(identical(byLanguage.get("en-settings"), byLanguage.get("pt_BR-settings")),
                "the settings screen must look different in the two languages");
        assertFalse(identical(byLanguage.get("en-summary"), byLanguage.get("pt_BR-summary")),
                "the run summary must look different in the two languages");
        assertFalse(identical(byLanguage.get("en-statistics"),
                        byLanguage.get("pt_BR-statistics")),
                "the statistics screen must look different in the two languages");
    }

    /**
     * A profile and a finished run to draw the M3 screens from: {@link #played()} has three runs
     * written into it through the real progression pipeline, {@link #fresh()} has none.
     */
    private static final class Fixture {
        final ProgressionRules rules =
                ProgressionRules.fromEconomy(GameContent.load().economy());
        final PlayerProfile profile;
        final RunResult result = run(12);
        final ProgressionOutcome outcome;

        private Fixture(boolean played) {
            FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
            profile = PlayerProfile.fresh(time.epochMillis()).normalize();
            if (!played) {
                outcome = null;
                return;
            }
            ProgressionManager progression = new ProgressionManager(time);
            outcome = progression.apply(profile, result, rules);
            progression.apply(profile, run(5), rules);
            progression.apply(profile, run(21), rules);
        }

        static Fixture played() {
            return new Fixture(true);
        }

        static Fixture fresh() {
            return new Fixture(false);
        }

        Screen summary(ScreenManager sm) {
            return new RunSummaryScreen(sm, result, outcome, outcome == null ? null : profile,
                    outcome == null ? null : rules, () -> { }, Strings.active());
        }

        Screen statistics(ScreenManager sm) {
            return new StatisticsScreen(sm, Strings.active(), profile);
        }

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
            return new RunResult(RunConfig.builder(42L).mode(RunMode.SEEDED).build(), stats,
                    Map.of());
        }
    }

    @Test
    void theBootScreenWarmsUpOnAnotherThreadAndHandsOverToTheMenu() {
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        BootScreen boot = new BootScreen(screens, new DirectExecutor(),
                () -> new MainMenuScreen(screens));
        screens.push(boot);
        screens.applyPending();

        screens.tick(InputFrame.EMPTY);
        assertTrue(boot.isReady(), "the warm-up ran on the executor");
        assertFalse(boot.hasHandedOver(), "the splash is held for a moment");
        presenter.present(0.0);
        assertTrue(distinctColours(presenter.image(), 2) >= 2, "boot frame is uniform");

        for (int i = 0; i < BootScreen.MIN_TICKS + 4; i++) {
            screens.tick(InputFrame.EMPTY);
        }
        assertTrue(boot.hasHandedOver());
        assertTrue(screens.top() instanceof MainMenuScreen, "the splash handed over to the menu");
        assertEquals(1, screens.depth(), "the splash left the stack");
        assertTrue(boot.sequence().errors().isEmpty(),
                () -> String.join("\n", boot.sequence().errors()));
    }

    /** The phases of a run a frame can be captured in. */
    private enum Phase {
        /** Waiting for the first flap. */
        READY,
        /** Flying with a gate on screen. */
        FLYING,
        /** Paused through a focus loss. */
        PAUSED,
        /** The game-over overlay is up. */
        GAME_OVER
    }

    /** A headless game screen driven straight through the screen manager. */
    private static final class Rig {
        final Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        final ScreenManager screens = new ScreenManager(viewport);
        final NullPresenter presenter;
        final GameScreen game;

        Rig() {
            presenter = new NullPresenter(screens, viewport, Playfield.WIDTH, Playfield.HEIGHT);
            screens.setPresenter(presenter);
            game = new GameScreen(screens, new ClassicRunFactory(), SeedSequence.of(42));
            screens.push(game);
            screens.applyPending();
            screens.tick(InputFrame.EMPTY);
        }

        void driveTo(Phase phase) {
            if (phase == Phase.READY) {
                tick(70); // past the first blink so the hint is drawn
                return;
            }
            flap();
            if (phase == Phase.FLYING) {
                tick(90);
                return;
            }
            if (phase == Phase.PAUSED) {
                tick(20);
                screens.tick(frameWith(new RawInput.FocusLost()));
                tick(5);
                assertTrue(screens.top() instanceof PauseOverlay, "focus loss paused the run");
                return;
            }
            for (int i = 0; i < 400 && !(screens.top() instanceof GameOverOverlay); i++) {
                tick(1);
            }
            tick(70); // past the first blink so the prompt is drawn
        }

        void flap() {
            int[] counts = new int[InputAction.values().length];
            counts[InputAction.FLAP.ordinal()] = 1;
            screens.tick(new InputFrame(counts, EnumSet.of(InputAction.FLAP),
                    EnumSet.noneOf(InputAction.class), 0, 0, 0, 0, 0, 0, List.of(), List.of()));
        }

        InputFrame frameWith(RawInput.SystemEvent event) {
            return new InputFrame(new int[InputAction.values().length],
                    EnumSet.noneOf(InputAction.class), EnumSet.noneOf(InputAction.class), 0, 0,
                    0, 0, 0, 0, List.of(), List.of(event));
        }

        void tick(int n) {
            for (int i = 0; i < n; i++) {
                screens.tick(InputFrame.EMPTY);
            }
        }

        BufferedImage frame(double alpha) {
            presenter.present(alpha);
            BufferedImage image = presenter.image();
            assertNotNull(image);
            return image;
        }
    }

    /** A detached copy, so a later {@code present} into the presenter's image cannot change it. */
    private static BufferedImage copy(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static boolean identical(BufferedImage a, BufferedImage b) {
        assertNotNull(a);
        assertNotNull(b);
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if ((a.getRGB(x, y) & 0xFFFFFF) != (b.getRGB(x, y) & 0xFFFFFF)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static com.sun.management.ThreadMXBean allocationCounter() {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory
                .getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean sun)
                || !sun.isThreadAllocatedMemorySupported()) {
            return null;
        }
        sun.setThreadAllocatedMemoryEnabled(true);
        return sun;
    }

    @Test
    void debugOverlayDrawsOnTopAndMeasuresRates() {
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        long[] nanos = {0};
        DebugOverlay overlay = new DebugOverlay(screens, () -> nanos[0]);
        overlay.setSource(new DebugOverlay.Source() {
            @Override
            public boolean isVisible() {
                return true;
            }

            @Override
            public long tickCount() {
                return screens.tickCount();
            }

            @Override
            public long accumulatorNs() {
                return 1_234_567L;
            }

            @Override
            public int lastTicks() {
                return 1;
            }

            @Override
            public List<String> screenNames() {
                return List.of("MainMenuScreen");
            }

            @Override
            public double mouseX() {
                return 12.5;
            }

            @Override
            public double mouseY() {
                return 34.5;
            }
        });
        NullPresenter presenter = new NullPresenter(overlay, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        screens.push(new MainMenuScreen(screens));
        screens.applyPending();

        for (int i = 0; i < 130; i++) {
            nanos[0] += Playfield.TICK_NS;
            screens.tick(InputFrame.EMPTY);
            presenter.present(0.25);
        }
        assertEquals(DebugOverlay.HISTORY, overlay.sampleCount());
        assertEquals(60.0, overlay.fps(), 0.5);
        assertEquals(60.0, overlay.tps(), 0.5);
        assertEquals(WorldPalette.GREEN_FIELDS.letterbox(), overlay.letterboxRgb());
        BufferedImage frame = presenter.image();
        assertNotNull(frame);
        assertTrue(distinctColours(frame, 2) >= 2, "overlay frame is uniform");
        BufferedImage panelArea = frame.getSubimage(8, 8, 240, 100);
        assertTrue(distinctColours(panelArea, 1) >= 2, "overlay panel area is uniform");
    }

    @Test
    void activeFontDisplaysPortugueseAccents() {
        assertTrue(Fonts.canDisplay(ACCENTED), "base font cannot display " + ACCENTED);
        Font bold = Fonts.bold(20);
        assertEquals(-1, bold.canDisplayUpTo(ACCENTED));
        assertEquals(-1, Fonts.regular(14).canDisplayUpTo("Configurações — Início"));
        assertEquals(20, bold.getSize());
        assertTrue(bold.isBold());
        assertEquals(Fonts.bold(20), bold, "font instances are cached");
        assertTrue(Fonts.mono(11).canDisplayUpTo("tps 60.0 fps 60.0") == -1);
    }

    private static BufferedImage renderScreen(Function<ScreenManager, Screen> factory, int ticks) {
        return renderScreen(factory, ticks, Playfield.WIDTH, Playfield.HEIGHT);
    }

    private static BufferedImage renderScreen(Function<ScreenManager, Screen> factory, int ticks,
            int width, int height) {
        Viewport viewport = new Viewport(width, height, false);
        ScreenManager screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, width, height);
        screens.setPresenter(presenter);
        screens.push(factory.apply(screens));
        screens.applyPending();
        for (int i = 0; i < ticks; i++) {
            screens.tick(InputFrame.EMPTY);
        }
        presenter.present(0.5);
        BufferedImage image = presenter.image();
        assertNotNull(image);
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
        return image;
    }

    private static int distinctColours(BufferedImage img, int stride) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += stride) {
            for (int x = 0; x < img.getWidth(); x += stride) {
                colours.add(img.getRGB(x, y) & 0xFFFFFF);
                if (colours.size() > 8) {
                    return colours.size();
                }
            }
        }
        return colours.size();
    }
}
