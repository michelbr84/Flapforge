package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerLevel;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionOutcome;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.RunSummaryScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The run summary (D29), driven headlessly through the input queue and the loop the way
 * {@code SettingsScreenTest} drives the settings screen: every term of {@link RewardSummary} has
 * its own row and carries the number the formula produced, {@code Enter} on the game-over strip
 * opens the screen, Retry starts a new run with a new seed and Menu goes all the way back.
 *
 * <p>An empty profile — a session with no save layer at all — must still render: the coin and XP
 * sections are simply not built.
 */
class RunSummaryScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private NullPresenter presenter;
    private GameLoop loop;
    private Strings strings;
    private ProgressionRules rules;
    private ProgressionManager progression;
    private PlayerProfile profile;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        presenter = new NullPresenter(screens, viewport, Playfield.WIDTH, Playfield.HEIGHT);
        screens.setPresenter(presenter);
        FrameLimiter limiter = FrameLimiter.uncapped(clock);
        loop = new GameLoop(clock, input, screens, presenter, limiter);
        screens.setCloseHandler(loop::stop);
        strings = Strings.load("en");
        Strings.use(strings);
        rules = ProgressionRules.fromEconomy(GameContent.load().economy());
        FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
        progression = new ProgressionManager(time);
        profile = PlayerProfile.fresh(time.epochMillis()).normalize();
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
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

    private RunSummaryScreen openSummary(RunResult result, ProgressionOutcome outcome,
            PlayerProfile shown) {
        RunSummaryScreen screen = new RunSummaryScreen(screens, result, outcome, shown,
                shown == null ? null : rules, () -> { }, strings);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        return screen;
    }

    @Test
    void everyTermOfTheRewardSummaryHasItsOwnRow() {
        RunResult result = run(12);
        ProgressionOutcome outcome = progression.apply(profile, result, rules);
        RewardSummary rewards = outcome.rewardSummary();
        RunSummaryScreen screen = openSummary(result, outcome, profile);

        assertEquals(signed(rewards.participation()), value(screen, "participation"));
        assertEquals(signed(rewards.firstRunBonus()), value(screen, "firstRunBonus"));
        assertEquals(signed(rewards.gateCoins()), value(screen, "gateCoins"));
        assertEquals(signed(rewards.pointCoins()), value(screen, "pointCoins"));
        assertEquals(signed(rewards.streakCoins()), value(screen, "streakCoins"));
        assertEquals(signed(rewards.bossCoins()), value(screen, "bossCoins"));
        assertEquals(signed(rewards.challengeCoins()), value(screen, "challengeCoins"));
        assertEquals(Long.toString(rewards.baseCoins()), value(screen, "base"));
        assertEquals("x1.00", value(screen, "coinMult"));
        assertEquals("x1.00", value(screen, "tierMult"));
        assertEquals("x1.00", value(screen, "dailyMult"));
        assertEquals(signed(rewards.coinsCollected()), value(screen, "coinsCollected"));
        assertEquals(signed(rewards.coins()), value(screen, "coins"));
        assertEquals(signed(rewards.xp()), value(screen, "xp"));

        // The terms shown really add up to the total the wallet was credited with (E32.a).
        long base = rewards.participation() + rewards.firstRunBonus() + rewards.gateCoins()
                + rewards.pointCoins() + rewards.streakCoins() + rewards.bossCoins()
                + rewards.challengeCoins();
        assertEquals(base, rewards.baseCoins());
        assertEquals(Math.round(base * rewards.totalMultiplier()) + rewards.coinsCollected(),
                rewards.coins());
        assertEquals(rewards.coins(), profile.wallet.get(PlayerProfile.CURRENCY_COINS)
                - creditedByLevels(outcome));
    }

    @Test
    void theRunRowsCarryTheSeedTheModeAndThePersonalBests() {
        RunResult result = run(12);
        ProgressionOutcome outcome = progression.apply(profile, result, rules);
        RunSummaryScreen screen = openSummary(result, outcome, profile);

        assertEquals("12", value(screen, "gates"));
        assertEquals("12", value(screen, "points"));
        assertTrue(screen.row("gates").best(), "the first run is the best run");
        assertTrue(screen.row("points").best());
        assertEquals(strings.format(StringKey.SUMMARY_SEED, 42L,
                strings.get(StringKey.MODE_SEEDED)), screen.row("seed").label());
        assertTrue(screen.rowTexts().stream().anyMatch(line -> line.contains("720 ticks")),
                () -> "the duration row is missing: " + screen.rowTexts());
    }

    @Test
    void theLevelBarShowsWhereTheProfileNowStands() {
        RunResult result = run(12);
        ProgressionOutcome outcome = progression.apply(profile, result, rules);
        RunSummaryScreen screen = openSummary(result, outcome, profile);

        PlayerLevel.Progress progress = rules.levels().progressWithin(profile.xp);
        assertNotNull(screen.levelBar());
        assertEquals(progress.fraction(), screen.levelBar().value(), 1e-9);
        assertEquals(strings.format(StringKey.SUMMARY_LEVEL, progress.level()),
                screen.levelBar().label());
        assertTrue(outcome.leveledUp(), "135 XP crosses level 2 on the shipped curve");
        assertNotNull(screen.row("levelUp"), "a level-up line is shown");
    }

    @Test
    void anEmptyProfileRendersWithoutExceptions() {
        RunSummaryScreen screen = openSummary(run(0), null, null);
        assertNull(screen.row("coins"), "no reward rows without a profile");
        assertNull(screen.levelBar());
        assertNotNull(screen.row("gates"));
        presenter.present(0.5);
        BufferedImage frame = presenter.image();
        assertNotNull(frame);
        assertTrue(distinctColours(frame) >= 2, "the summary is uniform");
    }

    @Test
    void enterOnTheGameOverStripOpensTheSummaryAndRetryStartsANewRun() {
        MainMenuScreen menu = new MainMenuScreen(screens, new ClassicRunFactory(),
                SeedSequence.of(42));
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);

        tap(Keys.ENTER);
        assertTrue(screens.top() instanceof GameScreen, "Enter on Play starts a run");
        GameScreen game = (GameScreen) screens.top();
        assertEquals(42L, game.seed());
        ticks(GRACE);
        driveToGameOver();
        assertTrue(screens.top() instanceof GameOverOverlay, "the dive ended the run");
        ticks(GRACE);

        tap(Keys.ENTER);
        assertTrue(screens.top() instanceof RunSummaryScreen, "Enter opens the summary");
        RunSummaryScreen summary = (RunSummaryScreen) screens.top();
        assertSame(summary.retryButton(), summary.focusRing().focused(), "Retry is focused");
        ticks(GRACE);

        tap(Keys.ENTER);
        ticks(2);
        assertSame(game, screens.top(), "Retry left the summary and the strip behind");
        assertEquals(43L, game.seed(), "the retry used the next seed");
        assertEquals(2, game.runsStarted());
        assertEquals(2, screens.depth(), "menu + game screen");
    }

    @Test
    void menuLeavesTheSummaryTheStripAndTheRunBehind() {
        MainMenuScreen menu = new MainMenuScreen(screens, new ClassicRunFactory(),
                SeedSequence.of(7));
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        tap(Keys.ENTER);
        GameScreen game = (GameScreen) screens.top();
        assertNotNull(game);
        ticks(GRACE);
        driveToGameOver();
        ticks(GRACE);
        tap(Keys.ENTER);
        RunSummaryScreen summary = (RunSummaryScreen) screens.top();
        ticks(GRACE);

        tap(Keys.RIGHT);
        assertSame(summary.menuButton(), summary.focusRing().focused(), "Right moves to Menu");
        tap(Keys.ENTER);
        ticks(2);
        assertSame(menu, screens.top(), "Menu popped the summary, the strip and the run");
        assertEquals(1, screens.depth());
    }

    @Test
    void escapeGoesBackToTheGameOverStrip() {
        MainMenuScreen menu = new MainMenuScreen(screens, new ClassicRunFactory(),
                SeedSequence.of(7));
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        tap(Keys.ENTER);
        ticks(GRACE);
        driveToGameOver();
        ticks(GRACE);
        tap(Keys.ENTER);
        assertTrue(screens.top() instanceof RunSummaryScreen);
        ticks(GRACE);
        tap(Keys.ESCAPE);
        ticks(2);
        assertTrue(screens.top() instanceof GameOverOverlay, "Esc returns to the strip");
    }

    @Test
    void theArrowsScrollTheBreakdownBecauseTheButtonsSitSideBySide() {
        RunResult result = run(12);
        ProgressionOutcome outcome = progression.apply(profile, result, rules);
        RunSummaryScreen screen = openSummary(result, outcome, profile);
        assertEquals(0, screen.scroll(), 1e-9);
        tap(Keys.DOWN);
        assertSame(screen.retryButton(), screen.focusRing().focused(),
                "Down must not move focus off Retry");
        // The breakdown fits at the shipped text scale, so the scroll is clamped to zero; what
        // this asserts is that Down reaches the screen rather than the focus ring.
        assertEquals(0, screen.scroll(), 1e-9);
    }

    /**
     * Starts the run with one flap and then stops flapping: the bird falls to the ground line and
     * the game-over strip is pushed.
     */
    private void driveToGameOver() {
        tap(Keys.SPACE);
        for (int i = 0; i < 900 && !(screens.top() instanceof GameOverOverlay); i++) {
            ticks(1);
        }
    }

    private static String value(RunSummaryScreen screen, String id) {
        RunSummaryScreen.Row row = screen.row(id);
        assertNotNull(row, "missing row " + id + ": " + screen.rowTexts());
        assertFalse(row.header());
        return row.value();
    }

    private static String signed(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
    }

    private static long creditedByLevels(ProgressionOutcome outcome) {
        long granted = 0;
        for (Long amount : outcome.levelRewardsGranted().values()) {
            granted += amount == null ? 0 : amount;
        }
        return granted;
    }

    private static int distinctColours(BufferedImage img) {
        List<Integer> seen = new java.util.ArrayList<>();
        for (int y = 0; y < img.getHeight(); y += 4) {
            for (int x = 0; x < img.getWidth(); x += 4) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                if (!seen.contains(rgb)) {
                    seen.add(rgb);
                    if (seen.size() > 4) {
                        return seen.size();
                    }
                }
            }
        }
        return seen.size();
    }
}
