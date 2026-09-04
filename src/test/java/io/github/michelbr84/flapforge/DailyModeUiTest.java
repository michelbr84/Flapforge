package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.DailyChallenge;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.SelectionManager;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UnlockManager;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.BirdSelectionScreen;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import io.github.michelbr84.flapforge.ui.screens.RunSummaryScreen;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The run-mode picker of the bird selection (M9, D17, D28), driven headlessly through the loop.
 *
 * <p>What is asserted is what the row promises: Standard is always there, Seeded and Daily say in
 * words what would open them until {@code feature:seeded_runs} is unlocked and pick nothing while
 * they are locked, and <em>looking</em> at the Daily row settles the day — the pick is
 * written onto the profile and flushed (E27), so the challenge cannot move under a player who
 * unlocks a world an hour later. Both shipped languages are exercised, because every label here
 * goes through {@link Strings} (D25).
 */
class DailyModeUiTest {

    /** 2026-03-03T00:00:00Z. */
    private static final long DAY = 1_772_496_000_000L;
    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    private ManualClock clock;
    private InputQueue input;
    private Viewport viewport;
    private ScreenManager screens;
    private GameLoop loop;
    private Strings strings;
    private GameContent content;
    private PlayerProfile profile;
    private ToastLayer toasts;
    private BirdSelectionScreen screen;
    private int saves;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);
        content = GameContent.load();
        profile = PlayerProfile.fresh(DAY).normalize();
        toasts = new ToastLayer();
        use("en");
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
    }

    private void use(String language) {
        strings = Strings.load(language);
        Strings.use(strings);
    }

    /** Opens the screen with a clock, which is what puts Daily in the picker. */
    private void open() {
        open(true);
    }

    private void open(boolean withClock) {
        ProgressionManager progression = new ProgressionManager(new FixedTimeSource(DAY),
                ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
        screen = new BirdSelectionScreen(screens, strings, content, profile,
                new SelectionManager(progression, () -> saves++),
                new UnlockManager(progression, () -> saves++), toasts,
                withClock ? new FixedTimeSource(DAY) : null, () -> saves++);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            clock.advance(Playfield.TICK_NS);
            loop.frame();
        }
    }

    /** Steps the mode row one to the right, the way the keyboard does. */
    private void stepMode() {
        screen.focusRing().focus(screen.modeList());
        input.offer(new RawInput.KeyDown(Keys.RIGHT, stamp++));
        input.offer(new RawInput.KeyUp(Keys.RIGHT, stamp++));
        ticks(1);
    }

    private void unlockSeededRuns() {
        profile.unlock(ContentKind.FEATURE.unlockableId(DailyChallenge.SEEDED_RUNS_FEATURE));
        screen.refreshState();
    }

    private String seededUnlockText() {
        return ProgressionText.unlockText(strings, content,
                content.features().get(DailyChallenge.SEEDED_RUNS_FEATURE).unlock(), profile);
    }

    @Test
    void theRowOffersThreeModesAndNamesWhatOpensTheLockedTwo() {
        open();
        assertEquals(List.of(RunMode.STANDARD, RunMode.SEEDED, RunMode.DAILY), screen.modes());
        List<String> options = screen.modeList().options();
        assertEquals(strings.get(StringKey.MODE_STANDARD), options.get(0));
        assertEquals(strings.get(StringKey.MODE_SEEDED) + " (" + seededUnlockText() + ")",
                options.get(1), "a locked mode says how it opens");
        assertEquals(strings.get(StringKey.MODE_DAILY) + " (" + seededUnlockText() + ")",
                options.get(2));
        assertEquals(strings.get(StringKey.BIRDS_MODE), screen.modeList().label());
        assertEquals(strings.get(StringKey.MENU_PLAY), screen.playButton().text());
        assertEquals(strings.get(StringKey.BIRDS_MODE_STANDARD_HINT), screen.modeDetail());
    }

    @Test
    void aLockedModeCanBeReadButNotFlown() {
        open();
        stepMode();
        assertEquals(RunMode.SEEDED, screen.selectedMode(), "a locked mode can be looked at");
        assertTrue(screen.modeList().isLocked(), "the row greys it");
        assertEquals(strings.format(StringKey.BIRDS_MODE_LOCKED, seededUnlockText()),
                screen.modeDetail(), "and says what opens it");
        assertFalse(toasts.isEmpty(), "which is also said out loud");

        stepMode();
        assertEquals(RunMode.DAILY, screen.selectedMode());
        assertEquals("", profile.daily.date, "a locked daily is never picked, let alone written");
        assertNull(screen.dailyPick());
    }

    @Test
    void viewingTheDailyRowSettlesAndPersistsThePick() {
        open();
        unlockSeededRuns();
        int savesBefore = saves;

        stepMode();
        assertEquals(RunMode.SEEDED, screen.selectedMode());
        assertEquals(strings.format(StringKey.BIRDS_MODE_SEEDED_HINT, profile.lastSeed),
                screen.modeDetail());

        stepMode();
        assertEquals(RunMode.DAILY, screen.selectedMode());
        assertNotNull(screen.dailyPick());
        assertEquals("2026-03-03", profile.daily.date, "E27: viewing writes the pick");
        assertEquals(DailyChallenge.seedFor("2026-03-03"), profile.daily.seed);
        assertEquals(screen.dailyPick().worldId(), profile.daily.worldId);
        assertTrue(saves > savesBefore, "and flushes it");
        assertTrue(screen.modeDetail().contains(ProgressionText.name(strings, ContentKind.WORLD,
                profile.daily.worldId)), () -> "the row names the world: " + screen.modeDetail());
        assertTrue(screen.modeDetail().contains(ProgressionText.name(strings, ContentKind.TIER,
                profile.daily.tierId)), () -> "and the tier: " + screen.modeDetail());
    }

    @Test
    void aNewUnlockDoesNotMoveTodaysPick() {
        open();
        unlockSeededRuns();
        stepMode();
        stepMode();
        String world = profile.daily.worldId;
        List<String> cards = List.copyOf(profile.daily.modifierIds);

        for (io.github.michelbr84.flapforge.content.defs.WorldDef def : content.worlds()) {
            profile.unlock(def.unlockableId());
        }
        screen.refreshState();
        ticks(2);

        assertEquals(world, profile.daily.worldId, "E27: the day's pick is fixed");
        assertEquals(cards, profile.daily.modifierIds);
        assertTrue(screen.dailyPick().reused());
    }

    @Test
    void theGameOverStripAndTheSummaryShowTheDaysRecord() {
        open();
        unlockSeededRuns();
        stepMode();
        stepMode();
        profile.daily.attempts = 3;
        profile.daily.bestGates = 17;

        RunStats stats = new RunStats();
        stats.setGatesPassed(12);
        stats.setPoints(12);
        RunResult result = new RunResult(
                RunConfig.builder(profile.daily.seed).mode(RunMode.DAILY).build(), stats.copy(),
                Map.of());
        ProgressionRules rules = ProgressionRules.fromContent(content);
        String expected = strings.format(StringKey.DAILY_RESULT, 17, 3);

        GameOverOverlay overlay = new GameOverOverlay(screens, result, null, () -> { },
                new GameRenderer(WorldPalette.GREEN_FIELDS, "ready"), strings)
                .withProfile(profile, rules);
        assertTrue(overlay.rowTexts().contains(strings.get(StringKey.MODE_DAILY) + " " + expected),
                () -> "the strip carries the day's record: " + overlay.rowTexts());

        RunSummaryScreen summary = new RunSummaryScreen(screens, result, null, profile, rules,
                () -> { }, strings);
        RunSummaryScreen.Row row = summary.row("daily");
        assertNotNull(row, () -> "the summary carries it too: " + summary.rowTexts());
        assertEquals(expected, row.value());
    }

    @Test
    void aScreenWithoutAClockDoesNotOfferTheDaily() {
        open(false);
        assertEquals(List.of(RunMode.STANDARD, RunMode.SEEDED), screen.modes(),
                "without a time source the screen cannot say what the daily would be");
    }

    @Test
    void everyLabelSpeaksTheSelectedLanguage() {
        use("pt_BR");
        open();
        assertEquals("Modo", screen.modeList().label());
        assertEquals(strings.get(StringKey.MENU_PLAY), screen.playButton().text());
        assertTrue(screen.modeList().options().get(2).startsWith(
                        strings.get(StringKey.MODE_DAILY)),
                () -> "the Daily entry is translated: " + screen.modeList().options());
        assertEquals(strings.get(StringKey.BIRDS_MODE_STANDARD_HINT), screen.modeDetail());

        unlockSeededRuns();
        stepMode();
        stepMode();
        assertEquals(RunMode.DAILY, screen.selectedMode());
        assertTrue(screen.modeDetail().contains(ProgressionText.name(strings, ContentKind.WORLD,
                profile.daily.worldId)), () -> screen.modeDetail());
        assertFalse(screen.modeDetail().equals(screen.modeList().tooltip()),
                "the tooltip adds the day's record to the setup line");
    }
}
