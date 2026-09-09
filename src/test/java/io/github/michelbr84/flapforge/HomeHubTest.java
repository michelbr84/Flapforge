package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.app.Threads;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.audio.NullAudio;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.persistence.SaveManager;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.progression.AchievementEvaluator;
import io.github.michelbr84.flapforge.progression.DailyChallenge;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.SelectionManager;
import io.github.michelbr84.flapforge.progression.Statistics;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.NavButton;
import io.github.michelbr84.flapforge.ui.component.SectionNav;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.BirdSelectionScreen;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.GoalsScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
import io.github.michelbr84.flapforge.ui.screens.ShopScreen;
import io.github.michelbr84.flapforge.ui.screens.StatisticsScreen;
import io.github.michelbr84.flapforge.ui.screens.UpgradeTreeScreen;
import io.github.michelbr84.flapforge.ui.screens.WorldSelectScreen;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The home hub behind a wired {@link GameContext} (D17, M10), driven headlessly through the
 * input queue and the loop: the focus graph across the HUD, the plaque, the next-unlock card,
 * START RUN and the navigation; every control pushing its screen; START RUN playing the
 * profile's selection through its own factory; the hub following the profile without being
 * re-entered; the next unlock, the last-run line, the forge stage and the player card; the
 * quit arming; and a non-blank render in both languages and at a larger text scale.
 */
class HomeHubTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;
    private static final long NOW = 1_700_000_000_000L;

    @TempDir
    private Path home;

    private boolean realHomeExisted;
    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private Viewport viewport;
    private NullPresenter presenter;
    private GameLoop loop;
    private GameContent content;
    private Strings strings;
    private ProgressionManager progression;
    private ProgressionRules rules;
    private SaveManager save;
    private ToastLayer toasts;
    private GameContext context;
    private boolean closed;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        SavePaths.clearOverride();
        realHomeExisted = Files.exists(SavePaths.profileDir());
        SavePaths.override(home);
        content = GameContent.load();
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        presenter = new NullPresenter(screens, viewport, Playfield.WIDTH, Playfield.HEIGHT);
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(() -> {
            closed = true;
            loop.stop();
        });
        FixedTimeSource time = new FixedTimeSource(NOW);
        save = new SaveManager(new DirectExecutor(), time);
        save.load();
        progression = new ProgressionManager(time, AchievementEvaluator.of(content),
                UnlockEvaluator.of(content));
        rules = ProgressionRules.fromEconomy(content.economy());
        strings = Strings.load("en");
        Strings.use(strings);
        toasts = new ToastLayer();
        context = new GameContext(LaunchOptions.DEFAULTS,
                (io.github.michelbr84.flapforge.app.Clock) clock, time, new Threads(), input,
                viewport, screens, presenter, null, loop, FrameLimiter.uncapped(clock), null,
                new EventBus(), new AudioManager(new NullAudio()), strings, toasts, content, save,
                progression, rules);
        screens.setTickTask(context::drainSaveResults);
    }

    @AfterEach
    void tearDown() {
        SavePaths.clearOverride();
        Strings.use(Strings.load("en"));
        Fonts.setTextScale(1.0);
        assertEquals(realHomeExisted, Files.exists(SavePaths.profileDir()),
                "the real profile directory must be untouched");
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            clock.advance(Playfield.TICK_NS);
            loop.frame();
        }
    }

    private void tap(int keyCode) {
        input.offer(new RawInput.KeyDown(keyCode, stamp++));
        input.offer(new RawInput.KeyUp(keyCode, stamp++));
        ticks(1);
    }

    /** Clicks a node the way the player does: through the window, mapped back to logical space. */
    private void click(UiNode node) {
        Vec2 w = viewport.toWindow(node.centerX(), node.centerY());
        int wx = (int) Math.round(w.x());
        int wy = (int) Math.round(w.y());
        input.offer(new RawInput.MouseMove(wx, wy));
        input.offer(new RawInput.MouseDown(Keys.BUTTON_LEFT, wx, wy));
        input.offer(new RawInput.MouseUp(Keys.BUTTON_LEFT, wx, wy));
        ticks(1);
    }

    private MainMenuScreen open(SeedSequence seeds) {
        MainMenuScreen menu = new MainMenuScreen(context, new ClassicRunFactory(), seeds);
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        return menu;
    }

    private MainMenuScreen open() {
        return open(SeedSequence.random());
    }

    private PlayerProfile profile() {
        return save.profile();
    }

    private static RunResult run(int gates) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(gates);
        stats.setPoints(gates);
        stats.addCoinsCollected(3);
        stats.setStreak(gates);
        for (int i = 0; i < gates * 60; i++) {
            stats.tickAlive();
        }
        stats.setDeathCause(CollisionCause.OBSTACLE);
        Map<String, Long> counters = new LinkedHashMap<>();
        return new RunResult(RunConfig.classic(gates), stats, counters);
    }

    private void focus(MainMenuScreen menu, UiNode node) {
        menu.focusRing().focus(node);
        ticks(1);
        assertSame(node, menu.focusRing().focused());
    }

    /** Activates a node with Enter, checks the screen it pushed, and pops back with Esc. */
    private void opens(MainMenuScreen menu, UiNode node, Class<?> screen) {
        focus(menu, node);
        tap(Keys.ENTER);
        assertTrue(screen.isInstance(screens.top()),
                node + " pushed " + screens.top().getClass().getSimpleName()
                        + ", expected " + screen.getSimpleName());
        ticks(GRACE);
        tap(Keys.ESCAPE);
        ticks(2);
        assertSame(menu, screens.top(), "Esc popped back to the hub");
        assertFalse(menu.quitArmed(), "a Back spent on a sub-screen never arms the quit");
        // The manager strips key edges for a few ticks after a stack change; let them pass so
        // the next press in the test is a real one.
        ticks(GRACE);
    }

    // ------------------------------------------------------------------ focus and screens

    @Test
    void theFocusWalksTheHubAndTheNavigationWraps() {
        MainMenuScreen menu = open();
        assertSame(menu.startRunButton(), menu.focusRing().focused(), "START RUN on entry");
        tap(Keys.DOWN);
        assertSame(menu.navButton(MainMenuScreen.NAV_PLAY), menu.focusRing().focused());
        tap(Keys.RIGHT);
        assertSame(menu.navButton(MainMenuScreen.NAV_FORGE), menu.focusRing().focused());
        tap(Keys.RIGHT);
        assertSame(menu.navButton(MainMenuScreen.NAV_GOALS), menu.focusRing().focused());
        tap(Keys.RIGHT);
        assertSame(menu.navButton(MainMenuScreen.NAV_SHOP), menu.focusRing().focused(),
                "Right from Goals wraps to Shop, past the gear above");
        tap(Keys.LEFT);
        assertSame(menu.navButton(MainMenuScreen.NAV_GOALS), menu.focusRing().focused(),
                "Left from Shop wraps to Goals");
        tap(Keys.UP);
        assertSame(menu.startRunButton(), menu.focusRing().focused(),
                "Up from any item reaches START RUN");
        tap(Keys.UP);
        assertSame(menu.nextUnlockCard(), menu.focusRing().focused());
        tap(Keys.UP);
        assertSame(menu.worldPlaque(), menu.focusRing().focused());
        tap(Keys.UP);
        assertSame(menu.coinsChip(), menu.focusRing().focused());
        tap(Keys.LEFT);
        assertSame(menu.playerCard(), menu.focusRing().focused());
        tap(Keys.RIGHT);
        assertSame(menu.coinsChip(), menu.focusRing().focused());
        tap(Keys.RIGHT);
        assertSame(menu.settingsButton(), menu.focusRing().focused());
        tap(Keys.DOWN);
        assertSame(menu.worldPlaque(), menu.focusRing().focused(), "Down from the HUD");
        focus(menu, menu.navButton(MainMenuScreen.NAV_SHOP));
        tap(Keys.UP);
        assertSame(menu.startRunButton(), menu.focusRing().focused());
        assertEquals(11, menu.focusRing().nodes().size(), "every hub control is on the ring");
        assertTrue(menu.navButton(MainMenuScreen.NAV_SHOP).isEnabled());
        assertTrue(menu.navButton(MainMenuScreen.NAV_GOALS).isEnabled());
        assertTrue(menu.coinsChip().isVisible());
        assertTrue(menu.worldPlaque().isVisible());
        assertTrue(menu.nextUnlockCard().isVisible());
    }

    @Test
    void everyControlPushesItsScreen() {
        MainMenuScreen menu = open();
        opens(menu, menu.navButton(MainMenuScreen.NAV_SHOP), ShopScreen.class);
        opens(menu, menu.navButton(MainMenuScreen.NAV_BIRDS), BirdSelectionScreen.class);
        opens(menu, menu.navButton(MainMenuScreen.NAV_FORGE), UpgradeTreeScreen.class);
        opens(menu, menu.navButton(MainMenuScreen.NAV_GOALS), GoalsScreen.class);
        opens(menu, menu.playerCard(), StatisticsScreen.class);
        opens(menu, menu.coinsChip(), ShopScreen.class);
        opens(menu, menu.settingsButton(), SettingsScreen.class);
        opens(menu, menu.worldPlaque(), WorldSelectScreen.class);
        opens(menu, menu.nextUnlockCard(), UpgradeTreeScreen.class);
        focus(menu, menu.navButton(MainMenuScreen.NAV_PLAY));
        tap(Keys.ENTER);
        assertTrue(screens.top() instanceof GameScreen, "the Play item starts a run");
    }

    @Test
    void everySectionsPlayItemGoesBackToTheHub() {
        MainMenuScreen menu = open();
        goesHome(menu, MainMenuScreen.NAV_SHOP, ShopScreen.class);
        goesHome(menu, MainMenuScreen.NAV_BIRDS, BirdSelectionScreen.class);
        goesHome(menu, MainMenuScreen.NAV_FORGE, UpgradeTreeScreen.class);
        goesHome(menu, MainMenuScreen.NAV_GOALS, GoalsScreen.class);
    }

    /**
     * Opens a section from the hub and clicks that section's Play item. Play is the hub's own
     * section, so it unwinds the stack to the hub rather than flying: the run is the hub's
     * START RUN, which is the button the player goes looking for.
     */
    private void goesHome(MainMenuScreen menu, String navId, Class<?> section) {
        focus(menu, menu.navButton(navId));
        tap(Keys.ENTER);
        ticks(GRACE);
        assertTrue(section.isInstance(screens.top()),
                () -> navId + " pushed " + screens.top().getClass().getSimpleName());
        click(playItem(screens.top()));
        ticks(GRACE);
        assertSame(menu, screens.top(),
                () -> "Play on " + navId + " went back to the hub");
    }

    /** The Play item of a section's own navigation. */
    private NavButton playItem(io.github.michelbr84.flapforge.ui.Screen section) {
        if (section instanceof ShopScreen shop) {
            return shop.nav().button(SectionNav.PLAY);
        }
        if (section instanceof BirdSelectionScreen birds) {
            return birds.nav().button(SectionNav.PLAY);
        }
        if (section instanceof UpgradeTreeScreen forge) {
            return forge.nav().button(SectionNav.PLAY);
        }
        return ((GoalsScreen) section).nav().button(SectionNav.PLAY);
    }

    @Test
    void theModeRowOfTheBirdsReachesTheHubsStartRun() {
        MainMenuScreen menu = open();
        profile().unlock(ContentKind.FEATURE.unlockableId(DailyChallenge.SEEDED_RUNS_FEATURE));
        focus(menu, menu.navButton(MainMenuScreen.NAV_BIRDS));
        tap(Keys.ENTER);
        ticks(GRACE);
        BirdSelectionScreen birds = (BirdSelectionScreen) screens.top();
        birds.openRunSetup();
        birds.focusRing().focus(birds.modeList());
        tap(Keys.RIGHT);
        tap(Keys.RIGHT);
        assertEquals(RunMode.DAILY, birds.selectedMode(), "the row is on the daily");
        birds.closeRunSetup();
        ticks(1);
        click(birds.nav().button(SectionNav.PLAY));
        ticks(GRACE);
        assertSame(menu, screens.top(), "Play took the daily back to the hub");

        click(menu.startRunButton());
        ticks(GRACE);
        GameScreen game = (GameScreen) screens.top();
        assertEquals(RunMode.DAILY, game.run().config().mode(),
                "START RUN flies the run the mode row picked, not a standard one");
        assertSame(menu, screens.screens().get(screens.depth() - 2),
                "the run sits over the hub that started it");
    }

    @Test
    void startRunPlaysTheSelectionThroughItsOwnFactory() {
        PlayerProfile profile = profile();
        profile.unlock("world:iron_forge");
        assertTrue(new SelectionManager(progression, () -> { })
                .selectWorld(profile, "iron_forge", content));
        MainMenuScreen menu = open(SeedSequence.of(42));
        tap(Keys.ENTER);
        GameScreen game = (GameScreen) screens.top();
        assertEquals("iron_forge", game.run().config().worldId(),
                "the plaque's world is the run's, not the injected factory's");
        assertEquals(RunMode.SEEDED, game.run().config().mode(), "an explicit seed stays seeded");
        assertEquals(42L, game.run().config().seed());
        ticks(GRACE);
        tap(Keys.ESCAPE);
        ticks(2);
        assertSame(menu, screens.top());

        MainMenuScreen random = open(SeedSequence.random());
        tap(Keys.ENTER);
        GameScreen standard = (GameScreen) screens.top();
        assertEquals(RunMode.STANDARD, standard.run().config().mode());
        assertEquals("iron_forge", standard.run().config().worldId());
        assertSame(random, screens.screens().get(screens.depth() - 2),
                "the run sits over the hub that started it");
    }

    // ------------------------------------------------------------------ live state

    @Test
    void thePlaqueTheBackdropAndTheSubtitleFollowTheSelectionWithoutReEntry() {
        MainMenuScreen menu = open();
        String fields = ProgressionText.name(strings, ContentKind.WORLD, "green_fields");
        assertEquals(strings.format(StringKey.MENU_WORLD_PLAQUE, 1, fields), menu.worldLine());
        assertEquals(menu.worldLine(), menu.worldPlaque().text());
        assertEquals(strings.format(StringKey.MENU_RUN_SUBTITLE, fields,
                ProgressionText.name(strings, ContentKind.TIER, "normal")),
                menu.startRunButton().subtitle());
        assertEquals(WorldPalette.GREEN_FIELDS, menu.hubPalette());

        PlayerProfile profile = profile();
        profile.unlock("world:storm_sky");
        profile.unlock("tier:hard");
        SelectionManager selection = new SelectionManager(progression, () -> { });
        assertTrue(selection.selectWorld(profile, "storm_sky", content));
        assertTrue(selection.selectTier(profile, "hard", content));
        ticks(1);
        String storm = ProgressionText.name(strings, ContentKind.WORLD, "storm_sky");
        assertEquals(strings.format(StringKey.MENU_WORLD_PLAQUE,
                content.worlds().get("storm_sky").order(), storm), menu.worldLine());
        assertEquals(strings.format(StringKey.MENU_RUN_SUBTITLE, storm,
                ProgressionText.name(strings, ContentKind.TIER, "hard")),
                menu.startRunButton().subtitle());
        WorldPalette palette = WorldPalette.from(content.worlds().get("storm_sky").palette());
        assertEquals(palette, menu.hubPalette());
        assertEquals(palette.letterbox(), screens.letterboxRgb());
    }

    @Test
    void theNextUnlockCardNamesTheNearestUnlockAndOpensItsScreen() {
        MainMenuScreen menu = open();
        assertNotNull(menu.nextUnlock());
        assertEquals("tree:economy", menu.nextUnlock().id(), "a fresh profile is nearest a tree");
        assertEquals(ContentKind.TREE, menu.nextUnlockCard().kind());
        assertEquals(strings.format(StringKey.MENU_NEXT_UNLOCK,
                ProgressionText.unlockableName(strings, content, "tree:economy")),
                menu.nextUnlockCard().label());
        assertEquals(strings.format(StringKey.MILESTONES_PROGRESS, 1, 3),
                menu.nextUnlockCard().counterText());
        assertEquals(1 / 3.0, menu.nextUnlockCard().fraction(), 1e-9);
        assertTrue(menu.nextUnlockCard().isFocusable());

        progression.apply(profile(), run(4), rules);
        progression.apply(profile(), run(6), rules);
        ticks(1);
        assertEquals("bird:guardian", menu.nextUnlock().id(),
                "two runs later the guardian's third run is nearest");
        assertEquals(ContentKind.BIRD, menu.nextUnlockCard().kind());
        opens(menu, menu.nextUnlockCard(), BirdSelectionScreen.class);
    }

    @Test
    void theLastRunLineAndTheBestFollowTheHistory() {
        MainMenuScreen menu = open();
        assertEquals(strings.get(StringKey.MENU_LAST_RUN_NONE), menu.lastRunLine());
        progression.apply(profile(), run(9), rules);
        ticks(1);
        PlayerProfile profile = profile();
        int size = profile.statistics.runHistory.size();
        assertEquals(strings.format(StringKey.MENU_LAST_RUN,
                profile.statistics.runHistory.get(size - 1).gates,
                profile.statistics.runHistory.get(size - 1).coins, profile.statistics.bestGates),
                menu.lastRunLine());
        assertTrue(menu.lastRunLine().contains("9"), menu.lastRunLine());
        progression.apply(profile(), run(4), rules);
        ticks(1);
        assertTrue(menu.lastRunLine().contains("4"), menu.lastRunLine());
        assertTrue(menu.lastRunLine().contains("9"), "the best stays: " + menu.lastRunLine());
    }

    @Test
    void theForgeStageAndThePlayerCardFollowTheProfile() {
        MainMenuScreen menu = open();
        assertEquals(0, menu.forgeStage());
        assertEquals(strings.get(StringKey.MENU_PLAYER_NAME), menu.playerCard().name());
        assertEquals(strings.format(StringKey.MENU_PLAYER_LEVEL, 1),
                menu.playerCard().levelText());
        assertEquals("", menu.playerCard().prestigeText());
        assertEquals("", menu.prestigeBadge());

        PlayerProfile profile = profile();
        profile.upgrades.put("feather_1", 3);
        profile.upgrades.put("glide_1", 3);
        ticks(1);
        assertEquals(2, menu.forgeStage(), "six levels light the hearth and add a barrel");
        profile.upgrades.put("coin_purse_1", 30);
        ticks(1);
        assertEquals(5, menu.forgeStage());

        profile.xp = 50;
        profile.prestigeCount = 2;
        ticks(1);
        assertEquals(strings.format(StringKey.MENU_PRESTIGE_BADGE, 2),
                menu.playerCard().prestigeText());
        assertEquals(menu.prestigeBadge(), menu.playerCard().prestigeText());
        assertEquals(rules.levels().progressWithin(50).fraction(),
                menu.playerCard().xpFraction(), 1e-9);
        assertTrue(menu.playerCard().xpFraction() > 0);
    }

    @Test
    void theHubNoticesARunOnceTheHistoryIsFullAndRestoresItsLetterboxAfterAPop() {
        PlayerProfile profile = profile();
        for (int i = 0; i < Statistics.RUN_HISTORY_LIMIT; i++) {
            Statistics.RunHistoryEntry entry = new Statistics.RunHistoryEntry();
            entry.gates = 5;
            entry.coins = 2;
            profile.statistics.runHistory.add(entry);
        }
        profile.statistics.totalRuns = Statistics.RUN_HISTORY_LIMIT;
        profile.statistics.bestGates = 5;
        profile.unlock("world:storm_sky");
        assertTrue(new SelectionManager(progression, () -> { })
                .selectWorld(profile, "storm_sky", content));
        MainMenuScreen menu = open();
        assertEquals(strings.format(StringKey.MENU_LAST_RUN, 5L, 2L, 5L), menu.lastRunLine());

        // A quick death that pays nothing and falls off the capped history: only the lifetime
        // count moves, and the hub still has to notice.
        RunStats stats = new RunStats();
        stats.setDeathCause(CollisionCause.GROUND);
        for (int i = 0; i < 10; i++) {
            stats.tickAlive();
        }
        long xpBefore = profile.xp;
        Long coinsBefore = profile.wallet.get(PlayerProfile.CURRENCY_COINS);
        // Without the achievement hook: the hundred runs above would otherwise fire a lifetime
        // achievement whose coins are exactly the kind of change this test keeps out.
        new ProgressionManager(new FixedTimeSource(NOW), ProgressionManager.AchievementHook.NONE,
                UnlockEvaluator.of(content)).apply(profile,
                        new RunResult(RunConfig.classic(1), stats, new LinkedHashMap<>()), rules);
        assertEquals(Statistics.RUN_HISTORY_LIMIT, profile.statistics.runHistory.size(),
                "the history stays capped");
        assertEquals(xpBefore, profile.xp, "a three-second zero-gate death pays no XP");
        assertEquals(coinsBefore, profile.wallet.get(PlayerProfile.CURRENCY_COINS),
                "and no coins, so only the lifetime count moved");
        ticks(1);
        assertEquals(strings.format(StringKey.MENU_LAST_RUN, 0L,
                profile.statistics.runHistory.get(Statistics.RUN_HISTORY_LIMIT - 1).coins, 5L),
                menu.lastRunLine(), "the zero-gate run is the last run now");

        // A sub-screen paints its own letterbox; the pop never re-enters the hub, so the hub
        // puts its world's back on its first tick.
        WorldPalette storm = WorldPalette.from(content.worlds().get("storm_sky").palette());
        assertEquals(storm.letterbox(), screens.letterboxRgb());
        opens(menu, menu.settingsButton(), SettingsScreen.class);
        assertEquals(storm.letterbox(), screens.letterboxRgb(),
                "the hub's letterbox is back after the settings screen popped");
    }

    // ------------------------------------------------------------------ quit and render

    @Test
    void escArmsTheQuitAndAPushDisarmsIt() {
        MainMenuScreen menu = open();
        tap(Keys.ESCAPE);
        assertTrue(menu.quitArmed());
        assertEquals(1, toasts.pushedCount());
        assertFalse(closed);
        opens(menu, menu.settingsButton(), SettingsScreen.class);
        assertFalse(menu.quitArmed(), "a screen came and went");
        tap(Keys.ESCAPE);
        assertFalse(closed, "the first Back after a screen only arms again");
        tap(Keys.ESCAPE);
        assertTrue(closed, "the second Back quits");
    }

    @Test
    void theHubRendersNonBlankInBothLanguagesAndAtALargerTextScale() {
        MainMenuScreen menu = open();
        progression.apply(profile(), run(7), rules);
        profile().prestigeCount = 1;
        ticks(1);
        presenter.present(0.5);
        assertTrue(distinctColours(presenter.image()) >= 4, "the hub is uniform");

        Strings.active().reload("pt_BR");
        Strings.use(Strings.active());
        ticks(1);
        Strings pt = Strings.load("pt_BR");
        assertEquals(pt.get(StringKey.MENU_START_RUN), menu.startRunButton().text());
        assertEquals(pt.format(StringKey.MENU_WORLD_PLAQUE, 1, pt.name("world", "green_fields")),
                menu.worldLine());
        assertTrue(menu.lastRunLine().startsWith(pt.get(StringKey.MENU_LAST_RUN).substring(0, 6)),
                menu.lastRunLine());
        presenter.present(0.5);
        assertTrue(distinctColours(presenter.image()) >= 4, "the hub is uniform in pt_BR");

        Fonts.setTextScale(1.5);
        ticks(2);
        presenter.present(0.5);
        assertTrue(distinctColours(presenter.image()) >= 4, "the hub is uniform at 1.5x");
    }

    private static int distinctColours(BufferedImage img) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += 4) {
            for (int x = 0; x < img.getWidth(); x += 4) {
                colours.add(img.getRGB(x, y) & 0xFFFFFF);
                if (colours.size() > 8) {
                    return colours.size();
                }
            }
        }
        return colours.size();
    }
}
