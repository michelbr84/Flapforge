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
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.AchievementEvaluator;
import io.github.michelbr84.flapforge.progression.CollectionProgress;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ProgressBar;
import io.github.michelbr84.flapforge.ui.screens.AchievementsScreen;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The achievements screen (D13, D17, M8), driven headlessly through the input queue and the loop
 * the way {@code StatisticsScreenTest} drives the statistics screen: the three tabs, the content
 * order of the grid, the secret until it fires, the milestones fed by
 * {@link AchievementEvaluator#progressOf} and the collections fed by the shared
 * {@link CollectionProgress} arithmetic.
 */
class AchievementsScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;
    private static final long NOW = 1_700_000_000_000L;

    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private NullPresenter presenter;
    private GameLoop loop;
    private GameContent content;
    private Strings strings;
    private ProgressionRules rules;
    private AchievementEvaluator evaluator;
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
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);
        strings = Strings.load("en");
        Strings.use(strings);
        content = GameContent.load();
        rules = ProgressionRules.fromEconomy(content.economy());
        evaluator = AchievementEvaluator.of(content);
        profile = PlayerProfile.fresh(NOW).normalize();
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

    private AchievementsScreen open() {
        AchievementsScreen screen = new AchievementsScreen(screens, strings, content, profile,
                rules);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        return screen;
    }

    // ------------------------------------------------------------------ achievements tab

    @Test
    void theGridListsEveryDefinitionInContentOrder() {
        AchievementsScreen screen = open();
        List<String> ids = content.achievements().ids();
        assertEquals(41, ids.size());
        assertEquals(strings.format(StringKey.ACHIEVEMENTS_COUNT, 0, ids.size()),
                screen.line("count"));
        for (int i = 0; i < ids.size(); i++) {
            assertNotNull(screen.line(ids.get(i) + ".name"), ids.get(i));
            assertNotNull(screen.line(ids.get(i) + ".desc"), ids.get(i));
        }
        assertNull(screen.line("no_such_achievement.name"));
    }

    @Test
    void aHeldAchievementShowsItsDateAndALockedOneItsReward() {
        profile.statistics.totalRuns = 1;
        profile.achievements.put("first_flight", new PlayerProfile.AchievementRecord(NOW));
        AchievementsScreen screen = open();

        assertTrue(screen.line("first_flight.desc").contains(
                strings.format(StringKey.ACHIEVEMENTS_UNLOCKED_AT, "2023-11-14")),
                screen.line("first_flight.desc"));
        assertEquals(strings.get(StringKey.ACHIEVEMENTS_COUNT).replace("{0}", "1")
                .replace("{1}", "41"), screen.line("count"));

        assertTrue(screen.line("gates_25.desc").contains(
                strings.format(StringKey.ACHIEVEMENTS_REWARD, 100)),
                "a locked achievement offers its coins");
        assertFalse(screen.line("gates_25.desc").contains("Unlocked"),
                "a locked achievement shows no date");
    }

    @Test
    void aHiddenAchievementStaysSecretUntilItFires() {
        AchievementsScreen screen = open();
        assertEquals(strings.get(StringKey.ACHIEVEMENTS_HIDDEN_NAME),
                screen.line("boss_void.name"));
        assertTrue(screen.line("boss_void.desc").startsWith(
                        strings.get(StringKey.ACHIEVEMENTS_HIDDEN_DESC)),
                screen.line("boss_void.desc"));

        profile.statistics.bossClears.put("void", 1L);
        profile.achievements.put("boss_void", new PlayerProfile.AchievementRecord(NOW));
        screen.refreshTexts();
        assertEquals(ProgressionText.name(strings, ContentKind.ACHIEVEMENT, "boss_void"),
                screen.line("boss_void.name"));
        assertTrue(screen.line("boss_void.desc").contains(
                strings.format(StringKey.ACHIEVEMENTS_UNLOCKED_AT, "2023-11-14")),
                "a fired secret is told like any other");
    }

    // ------------------------------------------------------------------ milestones tab

    @Test
    void theMilestonesTabShowsTheLevelAndTheNextFiveThresholds() {
        AchievementsScreen screen = open();
        screen.tabBar().select(AchievementsScreen.TAB_MILESTONES);
        ticks(1);

        assertEquals(1 + AchievementsScreen.MILESTONE_COUNT, screen.bars().size());
        assertEquals(strings.format(StringKey.SUMMARY_LEVEL, 1), screen.bars().get(0).label());
        assertEquals(strings.format(StringKey.SUMMARY_LEVEL_PROGRESS, 0L, 100L),
                screen.bars().get(0).valueText());

        // A fresh profile is one run and one level from two milestones; the level reward was
        // built first, so it wins the tie and leads.
        assertTrue(screen.bars().get(1).label().contains(
                strings.format(StringKey.MILESTONES_LEVEL_REWARD, 2, 50)),
                screen.bars().get(1).label());
        for (int i = 2; i < screen.bars().size(); i++) {
            ProgressBar bar = screen.bars().get(i);
            assertTrue(bar.valueText().contains(" / "), bar.label() + ": " + bar.valueText());
            assertFalse(bar.label().equals(ProgressionText.name(strings,
                    ContentKind.ACHIEVEMENT, "boss_void")), "a secret is never a milestone bar");
            assertFalse(bar.label().equals(ProgressionText.name(strings,
                    ContentKind.ACHIEVEMENT, "clean_10")), "a run bar is not on the tab");
        }
    }

    @Test
    void aHeldOrAchievedMilestoneLeavesTheTab() {
        AchievementsScreen screen = open();
        screen.tabBar().select(AchievementsScreen.TAB_MILESTONES);
        ticks(1);
        String firstFlight = ProgressionText.name(strings, ContentKind.ACHIEVEMENT,
                "first_flight");
        assertTrue(screen.bars().stream().anyMatch(bar -> bar.label().contains(firstFlight)));

        profile.achievements.put("first_flight", new PlayerProfile.AchievementRecord(NOW));
        screen.refreshTexts();
        assertFalse(screen.bars().stream().anyMatch(bar -> bar.label().contains(firstFlight)),
                "a held achievement is not a next milestone");
    }

    // ------------------------------------------------------------------ collections tab

    @Test
    void theCollectionsTabShowsEveryCategoryWithTheSharedNumbers() {
        AchievementsScreen screen = open();
        screen.tabBar().select(AchievementsScreen.TAB_COLLECTIONS);
        ticks(1);

        List<CollectionProgress.Entry> entries = evaluator.collections().all(profile);
        assertEquals(CollectionProgress.CATEGORIES.size(), screen.bars().size());
        for (int i = 0; i < entries.size(); i++) {
            ProgressBar bar = screen.bars().get(i);
            assertEquals(strings.get(StringKey.byKey("collections."
                    + entries.get(i).category())), bar.label());
            assertEquals(entries.get(i).fraction(), bar.value(), 1e-9);
            assertEquals(strings.format(StringKey.COLLECTIONS_VALUE, entries.get(i).owned(),
                    entries.get(i).total(), entries.get(i).percent()), bar.valueText());
        }
        assertEquals("Everything", screen.bars().get(screen.bars().size() - 1).label(),
                "all is last");
        assertEquals("Birds", screen.bars().get(0).label());
        assertEquals(strings.format(StringKey.COLLECTIONS_VALUE, 1, 7, 14),
                screen.bars().get(0).valueText(), "one starter bird of seven floors to 14%");
    }

    // ------------------------------------------------------------------ tabs and language

    @Test
    void theArrowsStepThroughTheTabs() {
        AchievementsScreen screen = open();
        assertEquals(AchievementsScreen.TAB_ACHIEVEMENTS, screen.tabBar().selectedId());
        tap(Keys.RIGHT);
        assertEquals(AchievementsScreen.TAB_MILESTONES, screen.tabBar().selectedId());
        assertTrue(screen.lineTexts().contains(strings.get(StringKey.MILESTONES_NEXT)));
        tap(Keys.RIGHT);
        assertEquals(AchievementsScreen.TAB_COLLECTIONS, screen.tabBar().selectedId());
        assertEquals(8, screen.bars().size());
        tap(Keys.RIGHT);
        assertEquals(AchievementsScreen.TAB_ACHIEVEMENTS, screen.tabBar().selectedId(),
                "the bar wraps");
    }

    @Test
    void aLanguageSwitchRelabelsEveryTab() {
        AchievementsScreen screen = open();
        Strings.active().reload("pt_BR");
        Strings.use(Strings.active());
        ticks(1);

        Strings pt = Strings.load("pt_BR");
        assertEquals(pt.get(StringKey.ACHIEVEMENTS_TAB_MILESTONES),
                screen.tabBar().tabs().get(1).label());
        assertTrue(screen.line("count").startsWith("0 de 41"), screen.line("count"));
        screen.tabBar().select(AchievementsScreen.TAB_COLLECTIONS);
        ticks(1);
        assertEquals(pt.get(StringKey.byKey("collections.birds")), screen.bars().get(0).label());
    }

    @Test
    void theScreenRendersAndKeepsTheScrollInsideTheContent() {
        AchievementsScreen screen = open();
        presenter.present(0.5);
        assertNotNull(presenter.image(), "a frame was drawn");
        assertEquals(0.0, screen.scroll(), 1e-9, "the tab opens at the top");
        assertTrue(screen.bars().isEmpty(), "the achievements tab draws lines, not bars");

        screen.tabBar().select(AchievementsScreen.TAB_COLLECTIONS);
        ticks(1);
        screen.refreshTexts();
        assertEquals(0.0, screen.scroll(), 1e-9);
        assertTrue(screen.focusRing().nodes().size() >= 2, "tabs and back are focusable");
    }
}
