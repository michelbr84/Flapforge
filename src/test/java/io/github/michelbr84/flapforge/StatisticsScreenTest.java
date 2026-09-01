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
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.Statistics;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.StatisticsScreen;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The statistics screen (D13), driven headlessly through the input queue and the loop the way
 * {@code SettingsScreenTest} drives the settings screen: the groups carry the profile's own
 * counters, the run history is paged newest first, the screen is reachable from the menu and comes
 * back from it, and a brand-new profile renders without an exception rather than showing a wall of
 * blanks.
 */
class StatisticsScreenTest {

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

    private static RunResult run(int gates, CollisionCause cause) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(gates);
        stats.setPoints(gates);
        stats.addCoinsCollected(3);
        stats.setStreak(gates);
        for (int i = 0; i < gates * 60; i++) {
            stats.tickAlive();
        }
        stats.setDeathCause(cause);
        Map<String, Long> counters = new LinkedHashMap<>();
        return new RunResult(RunConfig.classic(gates), stats, counters);
    }

    /** Writes three finished runs into the profile through the real progression pipeline. */
    private void playThreeRuns() {
        progression.apply(profile, run(4, CollisionCause.OBSTACLE), rules);
        progression.apply(profile, run(11, CollisionCause.GROUND), rules);
        progression.apply(profile, run(7, CollisionCause.OBSTACLE), rules);
    }

    private StatisticsScreen open(PlayerProfile shown) {
        StatisticsScreen screen = new StatisticsScreen(screens, strings, shown);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        return screen;
    }

    @Test
    void theGroupsCarryTheProfileCounters() {
        playThreeRuns();
        Statistics stats = profile.statistics;
        StatisticsScreen screen = open(profile);

        assertEquals(Long.toString(stats.totalRuns), value(screen, "totalRuns"));
        assertEquals(Long.toString(stats.bestGates), value(screen, "bestGates"));
        assertEquals(Long.toString(stats.totalGates), value(screen, "totalGates"));
        assertEquals(Long.toString(stats.bestPoints), value(screen, "bestPoints"));
        assertEquals(Long.toString(stats.totalPoints), value(screen, "totalPoints"));
        assertEquals(Long.toString(stats.coinsEarned), value(screen, "coinsEarned"));
        assertEquals(Long.toString(stats.coinsCollected), value(screen, "coinsCollected"));
        assertEquals(Long.toString(stats.xpEarned), value(screen, "xpEarned"));
        assertEquals(Long.toString(stats.streakBest), value(screen, "streakBest"));
        assertEquals(Integer.toString(profile.level), value(screen, "level"));
        assertEquals("11", value(screen, "bestGates"), "the best run of the three");

        // Deaths are grouped by cause and only the causes that happened are listed.
        assertEquals("2", value(screen, "death.OBSTACLE"));
        assertEquals("1", value(screen, "death.GROUND"));
        assertNull(screen.row("death.CEILING"), "no ceiling death happened");
        assertNull(screen.row("death.none"));

        // Every group header is on the screen, in the documented order.
        List<String> headers = new ArrayList<>();
        for (StatisticsScreen.Row row : screen.rows()) {
            if (row.header()) {
                headers.add(row.id());
            }
        }
        assertEquals(List.of(StringKey.STATS_GROUP_FLIGHTS.key(),
                StringKey.STATS_GROUP_DISTANCE.key(), StringKey.STATS_GROUP_ECONOMY.key(),
                StringKey.STATS_GROUP_STREAKS.key(), StringKey.STATS_GROUP_DEATHS.key()), headers);
    }

    @Test
    void theWalletShowsTheProfileBalance() {
        playThreeRuns();
        StatisticsScreen screen = open(profile);
        assertEquals(profile.wallet.get(PlayerProfile.CURRENCY_COINS).longValue(),
                screen.walletDisplay().displayedAmount());
        assertFalse(screen.walletDisplay().isRolling(), "the balance is shown, not animated in");
    }

    @Test
    void theHistoryListPagesThroughTheLastRunsNewestFirst() {
        playThreeRuns();
        StatisticsScreen screen = open(profile);
        assertEquals(3, screen.historyList().options().size());
        assertSame(screen.historyList(), screen.focusRing().focused(), "the list has the focus");

        List<Statistics.RunHistoryEntry> history = profile.statistics.runHistory;
        Statistics.RunHistoryEntry newest = history.get(history.size() - 1);
        assertEquals(strings.format(StringKey.STATS_HISTORY_ENTRY, 1, newest.gates, newest.coins),
                screen.historyList().selectedOption(), "the newest run is shown first");

        tap(Keys.RIGHT);
        Statistics.RunHistoryEntry previous = history.get(history.size() - 2);
        assertEquals(1, screen.historyList().selectedIndex());
        assertEquals(strings.format(StringKey.STATS_HISTORY_ENTRY, 2, previous.gates,
                previous.coins), screen.historyList().selectedOption());
    }

    @Test
    void anEmptyProfileRendersWithoutExceptions() {
        StatisticsScreen screen = open(null);
        assertEquals("0", value(screen, "totalRuns"));
        assertEquals("0", value(screen, "coinsEarned"));
        assertEquals(1, screen.historyList().options().size());
        assertEquals(strings.get(StringKey.STATS_HISTORY_EMPTY),
                screen.historyList().selectedOption());
        assertNotNull(screen.row("death.none"), "an empty deaths group says so");
        assertEquals(0, screen.walletDisplay().displayedAmount());

        presenter.present(0.5);
        BufferedImage frame = presenter.image();
        assertNotNull(frame);
        assertTrue(distinctColours(frame) >= 2, "the statistics screen is uniform");
    }

    @Test
    void theMenuOpensItAndBackReturns() {
        MainMenuScreen menu = new MainMenuScreen(screens, new ClassicRunFactory(),
                SeedSequence.of(1));
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);

        tap(Keys.DOWN);
        assertSame(menu.statisticsButton(), menu.focusRing().focused());
        tap(Keys.ENTER);
        assertTrue(screens.top() instanceof StatisticsScreen, "Enter opens the statistics");
        ticks(GRACE);

        tap(Keys.ESCAPE);
        ticks(2);
        assertSame(menu, screens.top(), "Esc pops back to the menu");
        assertEquals(1, screens.depth());
    }

    @Test
    void backButtonReturnsToTheMenu() {
        MainMenuScreen menu = new MainMenuScreen(screens, new ClassicRunFactory(),
                SeedSequence.of(1));
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        tap(Keys.DOWN);
        tap(Keys.ENTER);
        StatisticsScreen screen = (StatisticsScreen) screens.top();
        ticks(GRACE);

        tap(Keys.DOWN);
        assertSame(screen.backButton(), screen.focusRing().focused(), "Down moves to Back");
        tap(Keys.ENTER);
        ticks(2);
        assertSame(menu, screens.top());
    }

    @Test
    void aLanguageSwitchRelabelsEveryRow() {
        playThreeRuns();
        StatisticsScreen screen = open(profile);
        assertEquals(strings.get(StringKey.STATS_RUNS), label(screen, "totalRuns"));

        Strings.active().reload("pt_BR");
        Strings.use(Strings.active());
        ticks(1);

        Strings pt = Strings.load("pt_BR");
        assertEquals(pt.get(StringKey.STATS_RUNS), label(screen, "totalRuns"));
        assertEquals(pt.get(StringKey.COMMON_BACK), screen.backButton().text());
        assertEquals(pt.get(StringKey.STATS_HISTORY), screen.historyList().label());
        assertEquals("3", value(screen, "totalRuns"), "the numbers survived the rebuild");
    }

    private static String value(StatisticsScreen screen, String id) {
        StatisticsScreen.Row row = screen.row(id);
        assertNotNull(row, "missing row " + id + ": " + screen.rowTexts());
        assertFalse(row.header());
        return row.value();
    }

    private static String label(StatisticsScreen screen, String id) {
        StatisticsScreen.Row row = screen.row(id);
        assertNotNull(row, "missing row " + id);
        return row.label();
    }

    private static int distinctColours(BufferedImage img) {
        List<Integer> seen = new ArrayList<>();
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
