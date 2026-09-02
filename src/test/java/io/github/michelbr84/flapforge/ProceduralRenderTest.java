package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionOutcome;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.SelectionManager;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UnlockManager;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.render.AssetManager;
import io.github.michelbr84.flapforge.render.AssetResolver;
import io.github.michelbr84.flapforge.render.DarknessOverlay;
import io.github.michelbr84.flapforge.render.DebugOverlay;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.render.WorldStyle;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.DraftRuns;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.ui.screens.BirdSelectionScreen;
import io.github.michelbr84.flapforge.ui.screens.BootScreen;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.ModifierChoiceOverlay;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import io.github.michelbr84.flapforge.ui.screens.RunSummaryScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.SeededRunSource;
import io.github.michelbr84.flapforge.ui.screens.RuleShiftBanner;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
import io.github.michelbr84.flapforge.ui.screens.ShopScreen;
import io.github.michelbr84.flapforge.ui.screens.StatisticsScreen;
import io.github.michelbr84.flapforge.ui.screens.UpgradeTreeScreen;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.imageio.ImageIO;
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
    void theDraftOverlayRendersItsCardsAndItsCountdown() {
        // The cards are the shipped ones on a flat corridor (E17), so what is drawn is what a
        // player sees: three panels, their rarity colours and the dimmed frozen game behind them.
        Rig rig = new Rig(DraftRuns.source(DraftRuns.catalog(GameContent.load(), 2, 3)));
        ModifierChoiceOverlay overlay = rig.flyToDraft();
        assertEquals(3, overlay.cards().size());
        BufferedImage cards = copy(rig.frame(0.5));
        assertTrue(distinctColours(cards, 2) >= 2, "the draft frame is uniform");

        // The countdown is a different frame: the cards are gone and a 3-2-1 stands in their
        // place. The card is activated the way the focus ring activates it.
        overlay.cards().get(0).activate();
        rig.tick(2);
        BufferedImage hold = rig.frame(0.5);
        assertTrue(distinctColours(hold, 2) >= 2, "the countdown frame is uniform");
        assertFalse(identical(cards, hold), "the countdown must not look like the draft");
    }

    @Test
    void theDraftOverlayShowsTheSynergyACardWouldComplete() {
        Rig rig = new Rig(DraftRuns.source(
                DraftRuns.catalog(GameContent.load(), 2, 2, "coin_drops", "magnet_burst"),
                List.of("coin_drops")));
        ModifierChoiceOverlay overlay = rig.flyToDraft();
        boolean promised = false;
        for (ModifierChoiceOverlay.Card card : overlay.cards()) {
            promised |= !card.synergy().isEmpty();
        }
        assertTrue(promised, () -> "no card promised coin_engine: " + overlay.cards().stream()
                .map(ModifierChoiceOverlay.Card::id).toList());
        assertTrue(distinctColours(rig.frame(0.5), 2) >= 2, "the synergy frame is uniform");
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

                Meta meta = Meta.spent();
                BufferedImage birds = renderScreen(meta::birds, 5);
                assertTrue(distinctColours(birds, 2) >= 2,
                        "bird selection is uniform in " + language);
                byLanguage.put(language + "-birds", copy(birds));

                BufferedImage trees = renderScreen(meta::upgrades, 5);
                assertTrue(distinctColours(trees, 2) >= 2,
                        "upgrade trees are uniform in " + language);
                byLanguage.put(language + "-upgrades", copy(trees));

                BufferedImage shop = renderScreen(meta::shop, 5);
                assertTrue(distinctColours(shop, 2) >= 2, "shop is uniform in " + language);
                byLanguage.put(language + "-shop", copy(shop));

                for (Phase phase : Phase.values()) {
                    Rig rig = new Rig();
                    rig.driveTo(phase);
                    assertTrue(distinctColours(rig.frame(0.5), 2) >= 2,
                            phase + " frame is uniform in " + language);
                }

                // M6: the draft is the one screen whose text comes from the content tables rather
                // than from StringKey alone, so a missing modifier translation shows up here.
                Rig draft = new Rig(DraftRuns.source(DraftRuns.catalog(GameContent.load(), 2, 2,
                        "coin_drops", "magnet_burst"), List.of("coin_drops")));
                ModifierChoiceOverlay overlay = draft.flyToDraft();
                assertEquals(Strings.active().name("modifier", overlay.cards().get(0).id()),
                        overlay.cards().get(0).name(), "the card is named in " + language);
                BufferedImage cards = draft.frame(0.5);
                assertTrue(distinctColours(cards, 2) >= 2, "the draft is uniform in " + language);
                byLanguage.put(language + "-draft", copy(cards));
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
        assertFalse(identical(byLanguage.get("en-birds"), byLanguage.get("pt_BR-birds")),
                "the bird selection must look different in the two languages");
        assertFalse(identical(byLanguage.get("en-upgrades"), byLanguage.get("pt_BR-upgrades")),
                "the upgrade trees must look different in the two languages");
        assertFalse(identical(byLanguage.get("en-shop"), byLanguage.get("pt_BR-shop")),
                "the shop must look different in the two languages");
        assertFalse(identical(byLanguage.get("en-draft"), byLanguage.get("pt_BR-draft")),
                "the modifier draft must look different in the two languages");
    }

    @Test
    void theThreeMetaScreensRenderNonBlank() {
        Meta meta = Meta.spent();
        assertTrue(distinctColours(renderScreen(meta::birds, 5), 2) >= 2,
                "bird selection is uniform");
        assertTrue(distinctColours(renderScreen(meta::upgrades, 5), 2) >= 2,
                "upgrade trees are uniform");
        assertTrue(distinctColours(renderScreen(meta::shop, 5), 2) >= 2, "shop is uniform");
    }

    @Test
    void theMetaScreensRenderTheirTooltipsAndAFreshProfile() {
        // A brand-new profile: everything but the starter bird is locked, no node is owned and
        // the shop is full. Neither the locked states nor the empty breakdown may throw.
        Meta fresh = Meta.fresh();
        assertTrue(distinctColours(renderScreen(fresh::birds, 3), 2) >= 2);
        assertTrue(distinctColours(renderScreen(fresh::upgrades, 3), 2) >= 2);
        assertTrue(distinctColours(renderScreen(fresh::shop, 3), 2) >= 2);

        // The tooltip appears after its delay and is drawn over the panel it explains.
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        BirdSelectionScreen birds = (BirdSelectionScreen) fresh.birds(screens);
        screens.push(birds);
        screens.applyPending();
        for (int i = 0; i < 40; i++) {
            screens.tick(InputFrame.EMPTY);
        }
        assertTrue(birds.tooltip().isShowing(), "a focused card explains itself");
        presenter.present(0.0);
        assertTrue(distinctColours(presenter.image(), 2) >= 2);
    }

    /**
     * A profile for the three M4 screens: {@link #fresh()} owns nothing but the starter bird,
     * {@link #spent()} has coins, a bought upgrade node and a second tree, so the cards, the
     * prerequisite links and the stat breakdown all have something to draw.
     */
    private static final class Meta {
        final GameContent content = GameContent.load();
        final PlayerProfile profile;
        final SelectionManager selection;
        final UnlockManager unlocks;
        final UpgradeManager upgrades;

        private Meta(boolean spent) {
            FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
            profile = PlayerProfile.fresh(time.epochMillis()).normalize();
            ProgressionManager progression = new ProgressionManager(time,
                    ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
            selection = new SelectionManager(progression, null);
            unlocks = new UnlockManager(progression, null);
            upgrades = new UpgradeManager(progression, null);
            if (!spent) {
                return;
            }
            Wallet.of(profile).add(PlayerProfile.CURRENCY_COINS, 5_000);
            unlocks.purchase(profile, "bird:guardian", content);
            unlocks.purchase(profile, "tree:economy", content);
            upgrades.buy(profile, "feather_1", content);
            upgrades.buy(profile, "glide_1", content);
            selection.selectBird(profile, "guardian", content);
        }

        static Meta fresh() {
            return new Meta(false);
        }

        static Meta spent() {
            return new Meta(true);
        }

        Screen birds(ScreenManager sm) {
            return new BirdSelectionScreen(sm, Strings.active(), content, profile, selection,
                    unlocks, null);
        }

        Screen upgrades(ScreenManager sm) {
            return new UpgradeTreeScreen(sm, Strings.active(), content, profile, upgrades, null);
        }

        Screen shop(ScreenManager sm) {
            return new ShopScreen(sm, Strings.active(), content, profile, unlocks, null);
        }
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
            this(new ClassicRunFactory());
        }

        Rig(SeededRunSource source) {
            presenter = new NullPresenter(screens, viewport, Playfield.WIDTH, Playfield.HEIGHT);
            screens.setPresenter(presenter);
            game = new GameScreen(screens, source, SeedSequence.of(42));
            screens.push(game);
            screens.applyPending();
            screens.tick(InputFrame.EMPTY);
        }

        /**
         * Flies the flat corridor until the draft opens, flapping whenever the bird has sunk
         * below the gap centre (M6).
         *
         * @return the overlay on top of the stack
         */
        ModifierChoiceOverlay flyToDraft() {
            flap();
            for (int i = 0; i < 4000 && !(screens.top() instanceof ModifierChoiceOverlay); i++) {
                if (game.run().simulation().bird().y() > Playfield.BIRD_START_Y + 10) {
                    flap();
                } else {
                    tick(1);
                }
            }
            assertTrue(screens.top() instanceof ModifierChoiceOverlay,
                    () -> "the draft never opened: " + game.run().phase());
            tick(ScreenManager.TRANSITION_GRACE_TICKS + 2);
            return (ModifierChoiceOverlay) screens.top();
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

        /** Ticks with a flap every 25 ticks, which keeps the bird in the air (M7 tests). */
        void fly(int n) {
            for (int i = 0; i < n; i++) {
                if (i % 25 == 24) {
                    flap();
                } else {
                    tick(1);
                }
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

    // ------------------------------------------------------------------ worlds (M7)

    /** Where the world frames are written for a reviewer to look at. */
    private static final Path RENDER_DIR = Path.of("build", "render");
    /** Minimum L1 distance between the colour histograms of two worlds' frames. */
    private static final double WORLD_DISTANCE = 0.25;

    @Test
    void everyWorldRendersEveryKindInBothPosesAndLooksDifferent() throws IOException {
        GameContent content = GameContent.load();
        Map<String, double[]> histograms = new LinkedHashMap<>();
        for (WorldDef world : content.worlds()) {
            WorldRig rig = new WorldRig(content, world.id());
            rig.placeEveryKind();
            BufferedImage ready = copy(rig.frame(0.5));
            assertEquals(ProceduralArt.BirdPose.NORMAL, rig.pose(), world.id() + " at READY");
            assertTrue(distinctColours(ready, 2) >= 2, world.id() + " READY frame is uniform");
            save(ready, world.id() + "-normal");

            rig.flap();
            assertEquals(ProceduralArt.BirdPose.UP, rig.pose(), world.id() + " after a flap");
            BufferedImage up = copy(rig.frame(0.5));
            assertTrue(distinctColours(up, 2) >= 2, world.id() + " UP frame is uniform");
            assertFalse(identical(ready, up), world.id() + ": the pose must change the frame");
            save(up, world.id() + "-up");
            for (ObstacleKind kind : ObstacleKind.values()) {
                assertTrue(rig.has(kind), world.id() + " lost its " + kind);
            }
            histograms.put(world.id(), histogram(up));
        }
        assertEquals(5, histograms.size(), "the five worlds of worlds.json");
        List<String> ids = new ArrayList<>(histograms.keySet());
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                double distance = distance(histograms.get(ids.get(i)), histograms.get(ids.get(j)));
                assertTrue(distance >= WORLD_DISTANCE, ids.get(i) + " and " + ids.get(j)
                        + " look alike: histogram distance " + distance);
            }
        }
    }

    @Test
    void theDarknessVeilKeepsTheBirdAndTheNextHazardVisible() throws IOException {
        GameContent content = GameContent.load();
        WorldRig rig = new WorldRig(content, "storm_sky");
        assertEquals(0.5, rig.run.simulation().darkness(), 0.0, "storm_sky is half dark");
        // The next hazard: a gate 200 px ahead of the bird, its body spanning x 305..345.
        PipeGate gate = PipeGate.standard(305, 200, 128, null);
        rig.add(gate);
        BufferedImage frame = copy(rig.frame(0.0));
        save(frame, "storm_sky-darkness");

        DarknessOverlay veil = rig.renderer.darkness();
        assertTrue(veil.isActive(), "the veil is drawn in a dark world");
        double birdY = rig.run.simulation().bird().y();
        assertEquals(0.0, veil.alphaAt(Playfield.BIRD_X, birdY, birdY), 1e-9,
                "the bird sits in the clear");
        assertTrue(veil.alphaAt(325, 150, birdY) > 0.2, "the far gate is under the veil");
        assertTrue(veil.alphaAt(325, 150, birdY) <= 0.5 + 1e-9, "never darker than the world");

        // The bird: its body is the world accent; inside the sprite box some pixel must still
        // be that colour (the hole is fully clear), not a veiled version of it.
        int accent = WorldPalette.from(content.worlds().get("storm_sky").palette()).accent();
        int best = Integer.MAX_VALUE;
        for (int y = (int) birdY - 16; y <= (int) birdY + 16; y++) {
            for (int x = Playfield.BIRD_X - 19; x <= Playfield.BIRD_X + 19; x++) {
                best = Math.min(best, colourDistance(frame.getRGB(x, y) & 0xFFFFFF, accent));
            }
        }
        int nearest = best;
        assertTrue(nearest < 24, () -> "the bird body should be the accent "
                + Integer.toHexString(accent) + " somewhere in the sprite box; the nearest pixel"
                + " is " + nearest + " away");

        // The hazard: the upper segment of the gate at y 150 against the sky right beside it.
        int pipe = frame.getRGB(325, 150) & 0xFFFFFF;
        int sky = frame.getRGB(290, 150) & 0xFFFFFF;
        assertTrue(Math.abs(luminance(pipe) - luminance(sky)) > 25,
                () -> "the gate must stand out from the sky under the veil: pipe "
                        + Integer.toHexString(pipe) + " sky " + Integer.toHexString(sky));
    }

    @Test
    void theLightningWarningMarkerShowsTheBoltsSideAndExtent() throws IOException {
        GameContent content = GameContent.load();
        for (Side side : Side.values()) {
            WorldRig rig = new WorldRig(content, "storm_sky");
            // The same frame without the bolt is the reference: the veil, the rain and the
            // clouds are identical in both (the decorations run on the renderer's clock, which
            // does not move between the two draws), so any difference is the marker.
            BufferedImage reference = copy(rig.frame(0.0));
            LightningStrike bolt = new LightningStrike(180, side, 0.5, 45, 10);
            rig.add(bolt);
            bolt.update(rig.run.simulation().context());
            assertEquals(LightningStrike.State.WARNING, bolt.state(), side + " bolt is warning");
            BufferedImage frame = copy(rig.frame(0.0));
            save(frame, "storm_sky-warning-" + side.name().toLowerCase(java.util.Locale.ROOT));

            int cx = (int) Math.round(bolt.centerX());
            int inside = side == Side.TOP ? 150 : 450;
            int outside = side == Side.TOP ? 450 : 150;
            int insidePixel = patch(frame, cx, inside);
            int insideBefore = patch(reference, cx, inside);
            int outsidePixel = patch(frame, cx, outside);
            int outsideBefore = patch(reference, cx, outside);
            assertTrue(colourDistance(insidePixel, insideBefore) > 30,
                    () -> side + ": the marker must be visible over the lit span, got "
                            + Integer.toHexString(insidePixel) + " over "
                            + Integer.toHexString(insideBefore));
            assertEquals(outsideBefore, outsidePixel,
                    () -> side + ": nothing may be drawn over the safe band, got "
                            + Integer.toHexString(outsidePixel) + " over "
                            + Integer.toHexString(outsideBefore));
            assertTrue(colourDistance(patch(frame, cx + 60, inside),
                    patch(reference, cx + 60, inside)) == 0,
                    side + ": the marker stays inside the column");
        }
    }

    @Test
    void theRuleShiftBannerRendersInBothLanguagesAndTheWorldPickerToo() throws IOException {
        String original = Strings.active().language();
        Map<String, BufferedImage> banners = new LinkedHashMap<>();
        Map<String, BufferedImage> pickers = new LinkedHashMap<>();
        try {
            for (String language : Strings.LANGUAGES) {
                Strings.use(Strings.load(language));
                Rig rig = new Rig();
                rig.flap();
                rig.fly(40);
                BufferedImage plain = copy(rig.frame(0.5));
                rig.game.banner().announce(List.of(RuleFlag.ALL_OBSTACLES_MOVE),
                        List.of(new StatModifier(StatId.GAP_SIZE, StatOp.MULTIPLY, 0.85, "cycle")),
                        90);
                assertTrue(rig.game.banner().isVisible(), "the banner is up in " + language);
                assertTrue(rig.game.banner().line().contains(
                        Strings.active().get(StringKey.RULE_ALL_OBSTACLES_MOVE)),
                        () -> "the flag is named in " + language + ": "
                                + rig.game.banner().line());
                // The same simulation state, drawn again with the banner: everything outside
                // the panel is untouched, so a translation that runs long is fitted (shrunk or
                // wrapped) inside it rather than drawn over the playfield.
                BufferedImage withBanner = copy(rig.frame(0.5));
                assertFalse(identical(plain, withBanner), "the banner changes the frame");
                int panelBottom = RuleShiftBanner.PANEL_Y + rig.game.banner().panelHeight();
                assertTrue(RuleShiftBanner.PANEL_Y >= Playfield.GROUND_Y,
                        "the banner sits in the ground strip, never over the playfield (M7)");
                assertTrue(panelBottom <= Playfield.HEIGHT);
                for (int y = 0; y < Playfield.HEIGHT; y++) {
                    for (int x = 0; x < Playfield.WIDTH; x++) {
                        boolean inside = x >= RuleShiftBanner.PANEL_X - 1
                                && x < RuleShiftBanner.PANEL_X + RuleShiftBanner.PANEL_W + 1
                                && y >= RuleShiftBanner.PANEL_Y - 1 && y < panelBottom + 1;
                        if (!inside && plain.getRGB(x, y) != withBanner.getRGB(x, y)) {
                            fail(language + ": the banner drew outside its panel at " + x + ","
                                    + y + " (line '" + rig.game.banner().line() + "' at "
                                    + rig.game.banner().lineFont() + " pt on "
                                    + rig.game.banner().rows() + " row(s))");
                        }
                    }
                }
                assertTrue(rig.game.banner().rows() >= 1);
                save(withBanner, "rule-shift-" + language);
                banners.put(language, withBanner);

                Meta meta = Meta.spent();
                BirdSelectionScreen birds = null;
                Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
                ScreenManager screens = new ScreenManager(viewport);
                NullPresenter presenter = new NullPresenter(screens, viewport, Playfield.WIDTH,
                        Playfield.HEIGHT);
                screens.setPresenter(presenter);
                birds = (BirdSelectionScreen) meta.birds(screens);
                screens.push(birds);
                screens.applyPending();
                screens.tick(InputFrame.EMPTY);
                assertEquals(Strings.active().get(StringKey.BIRDS_WORLD),
                        birds.worldList().label(), "the picker is labelled in " + language);
                assertEquals(5, birds.worldIds().size(), "five worlds in the picker");
                assertFalse(birds.worldDetail().isEmpty(), "the hazard line is filled in");
                presenter.present(0.5);
                BufferedImage picker = copy(presenter.image());
                assertTrue(distinctColours(picker, 2) >= 2);
                save(picker, "world-picker-" + language);
                pickers.put(language, picker);
            }
        } finally {
            Strings.use(Strings.load(original));
        }
        assertFalse(identical(banners.get("en"), banners.get("pt_BR")),
                "the banner must read differently in the two languages");
        assertFalse(identical(pickers.get("en"), pickers.get("pt_BR")),
                "the world picker must read differently in the two languages");
    }

    /**
     * A run in one world of {@code worlds.json} drawn by a {@link GameRenderer} set to that
     * world, with obstacles placed by hand so every family is on screen at once.
     */
    private static final class WorldRig {
        final GameContent content;
        final Run run;
        final GameRenderer renderer;
        final BufferedImage image = new BufferedImage(Playfield.WIDTH, Playfield.HEIGHT,
                BufferedImage.TYPE_INT_RGB);

        WorldRig(GameContent content, String worldId) {
            this.content = content;
            RunConfig config = RunConfig.builder(7).worldId(worldId).build();
            run = new RunFactory(content).newRun(config);
            WorldDef def = content.worlds().get(worldId);
            renderer = new GameRenderer(WorldPalette.from(def.palette()), "READY");
            renderer.setWorld(WorldPalette.from(def.palette()), WorldStyle.fromId(def.style()));
            renderer.setWorldName(worldId);
            renderer.reset();
        }

        void add(Obstacle o) {
            run.simulation().obstacles().add(o);
        }

        /**
         * One of every family, each in the state that draws the most, added left to right so
         * the spawner's cursor rule ({@code last.x + 160}) puts its own next gate off screen.
         */
        void placeEveryKind() {
            SimContext ctx = run.simulation().context();
            LightningStrike strike = new LightningStrike(90, Side.TOP, 0.4, 45, 10);
            add(strike);
            strike.update(ctx);
            assertEquals(LightningStrike.State.STRIKE, strike.state());
            add(new WindZone(130, 160, 300, 400, -450, 0));
            LightningStrike warning = new LightningStrike(170, Side.BOTTOM, 0.5, 45, 10);
            add(warning);
            warning.update(ctx);
            assertEquals(LightningStrike.State.WARNING, warning.state());
            add(Piston.standard(240, Side.TOP, 220, 0));
            add(Gear.onRail(300, 300, 36));
            add(PipeGate.standard(380, 220, 128, null));
        }

        boolean has(ObstacleKind kind) {
            for (Obstacle o : run.simulation().obstacles().obstacles()) {
                if (o.kind() == kind) {
                    return true;
                }
            }
            return false;
        }

        void flap() {
            run.tick(RunInput.FLAP);
            renderer.tick(run, true);
        }

        ProceduralArt.BirdPose pose() {
            return io.github.michelbr84.flapforge.render.BirdRenderer.poseOf(
                    run.simulation().bird());
        }

        BufferedImage frame(double alpha) {
            Graphics2D g = image.createGraphics();
            try {
                renderer.render(g, alpha, run, null, false);
            } finally {
                g.dispose();
            }
            return image;
        }
    }

    private static void save(BufferedImage image, String name) throws IOException {
        Files.createDirectories(RENDER_DIR);
        ImageIO.write(image, "png", RENDER_DIR.resolve(name + ".png").toFile());
    }

    /** A 512-bin colour histogram (3 bits per channel) normalised to sum 1. */
    private static double[] histogram(BufferedImage image) {
        double[] bins = new double[512];
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 21) & 7;
                int g = (rgb >> 13) & 7;
                int b = (rgb >> 5) & 7;
                bins[(r << 6) | (g << 3) | b]++;
                count++;
            }
        }
        for (int i = 0; i < bins.length; i++) {
            bins[i] /= count;
        }
        return bins;
    }

    private static double distance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(a[i] - b[i]);
        }
        return sum;
    }

    /** The average colour of the 5x5 patch centred on a pixel. */
    private static int patch(BufferedImage image, int cx, int cy) {
        int r = 0;
        int g = 0;
        int b = 0;
        for (int y = cy - 2; y <= cy + 2; y++) {
            for (int x = cx - 2; x <= cx + 2; x++) {
                int rgb = image.getRGB(x, y);
                r += (rgb >> 16) & 0xFF;
                g += (rgb >> 8) & 0xFF;
                b += rgb & 0xFF;
            }
        }
        return ((r / 25) << 16) | ((g / 25) << 8) | (b / 25);
    }

    private static int colourDistance(int a, int b) {
        Color ca = new Color(a);
        Color cb = new Color(b);
        return Math.abs(ca.getRed() - cb.getRed()) + Math.abs(ca.getGreen() - cb.getGreen())
                + Math.abs(ca.getBlue() - cb.getBlue());
    }

    private static double luminance(int rgb) {
        Color c = new Color(rgb);
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }
}
