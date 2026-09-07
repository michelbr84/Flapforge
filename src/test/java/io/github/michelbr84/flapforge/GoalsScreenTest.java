package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.persistence.SaveManager;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.progression.AchievementEvaluator;
import io.github.michelbr84.flapforge.progression.CollectionProgress;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ProgressBar;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.GoalsScreen;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Goals screen (D13, D17, M8, M10), driven headlessly through the input queue and the loop
 * the way {@code StatisticsScreenTest} drives the statistics screen: the four tabs stepped with
 * the arrows, the challenges list and detail block with Play starting a {@code CHALLENGE} run
 * through a wired context, the content order of the achievements, the secret until it fires,
 * the milestones fed by {@link AchievementEvaluator#progressOf} and the collections fed by the
 * shared {@link CollectionProgress} arithmetic.
 */
class GoalsScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;
    private static final long NOW = 1_700_000_000_000L;

    @TempDir
    private Path home;

    private boolean realHomeExisted;
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
        SavePaths.clearOverride();
        realHomeExisted = Files.exists(SavePaths.profileDir());
        SavePaths.override(home);
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
        SavePaths.clearOverride();
        Strings.use(Strings.load("en"));
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

    private GoalsScreen show(GoalsScreen screen) {
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        return screen;
    }

    /** A stand-alone screen on the Challenges tab. */
    private GoalsScreen open() {
        return show(new GoalsScreen(screens, strings, content, profile, rules));
    }

    /** A stand-alone screen on a tab. */
    private GoalsScreen open(String tabId) {
        GoalsScreen screen = new GoalsScreen(screens, strings, content, profile, rules);
        screen.tabBar().select(tabId);
        return show(screen);
    }

    /**
     * A screen behind a wired {@link GameContext}, whose profile is the save manager's: the one
     * path that can start a challenge run.
     */
    private GoalsScreen openWired() {
        FixedTimeSource time = new FixedTimeSource(NOW);
        SaveManager save = new SaveManager(new DirectExecutor(), time);
        save.load();
        ProgressionManager progression = new ProgressionManager(time,
                evaluator, UnlockEvaluator.of(content));
        profile = save.profile();
        GameContext context = new GameContext(LaunchOptions.DEFAULTS,
                (io.github.michelbr84.flapforge.app.Clock) clock, time, new Threads(), input,
                new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false), screens, presenter, null,
                loop, FrameLimiter.uncapped(clock), null, new EventBus(),
                new AudioManager(new NullAudio()), strings, new ToastLayer(), content, save,
                progression, rules);
        screens.setTickTask(context::drainSaveResults);
        profile.unlock("challenge:no_shield_1");
        return show(new GoalsScreen(context));
    }

    // ------------------------------------------------------------------ tabs

    @Test
    void theScreenOpensOnChallengesAndTheArrowsStepThroughTheTabs() {
        GoalsScreen screen = open();
        assertEquals(GoalsScreen.TAB_CHALLENGES, screen.tabBar().selectedId());
        assertEquals(4, screen.tabBar().tabs().size());
        assertEquals(strings.get(StringKey.CHALLENGES_TITLE),
                screen.tabBar().tabs().get(0).label());
        assertEquals(4, screen.focusRing().nodes().size(),
                "tabs, the list, Play and Back are focusable on the Challenges tab");
        assertSame(screen.tabBar(), screen.focusRing().focused());
        tap(Keys.RIGHT);
        assertEquals(GoalsScreen.TAB_ACHIEVEMENTS, screen.tabBar().selectedId());
        assertEquals(2, screen.focusRing().nodes().size(), "tabs and Back elsewhere");
        assertNotNull(screen.line("count"));
        tap(Keys.RIGHT);
        assertEquals(GoalsScreen.TAB_MILESTONES, screen.tabBar().selectedId());
        assertTrue(screen.lineTexts().contains(strings.get(StringKey.MILESTONES_NEXT)));
        tap(Keys.RIGHT);
        assertEquals(GoalsScreen.TAB_COLLECTIONS, screen.tabBar().selectedId());
        assertEquals(8, screen.bars().size());
        tap(Keys.RIGHT);
        assertEquals(GoalsScreen.TAB_CHALLENGES, screen.tabBar().selectedId(), "the bar wraps");
        assertEquals(4, screen.focusRing().nodes().size());
        tap(Keys.LEFT);
        assertEquals(GoalsScreen.TAB_COLLECTIONS, screen.tabBar().selectedId());
    }

    @Test
    void aTabChangeArrowDoesNotAlsoMoveFocusOrStepTheList() {
        GoalsScreen screen = open(GoalsScreen.TAB_ACHIEVEMENTS);
        tap(Keys.LEFT);
        assertEquals(GoalsScreen.TAB_CHALLENGES, screen.tabBar().selectedId());
        assertSame(screen.tabBar(), screen.focusRing().focused(),
                "the tabs keep the focus after a tab change");
        assertEquals(0, screen.challengeList().selectedIndex(),
                "the arrow that changed the tab did not step the list");
        tap(Keys.DOWN);
        assertSame(screen.challengeList(), screen.focusRing().focused());
        tap(Keys.RIGHT);
        assertEquals(1, screen.challengeList().selectedIndex(), "the list steps on its own");
        assertEquals(GoalsScreen.TAB_CHALLENGES, screen.tabBar().selectedId(),
                "a focused list never changes the tab");
    }

    // ------------------------------------------------------------------ challenges tab

    @Test
    void theListCarriesEveryChallengeInFileOrder() {
        GoalsScreen screen = open();
        screen.focusRing().focus(screen.challengeList());
        ticks(1);
        assertEquals(content.challenges().ids().size(), screen.challengeList().options().size());
        for (int i = 0; i < content.challenges().ids().size(); i++) {
            assertSame(content.challenges().all().get(i), screen.selectedChallenge());
            tap(Keys.RIGHT);
        }
        assertTrue(screen.challengeList().options().get(0).contains("No Shield"),
                "the first challenge by file order");
    }

    @Test
    void theDetailBlockNamesWorldTierRulesObjectiveAndRewards() {
        GoalsScreen screen = open();
        List<String> detail = screen.detailTexts();
        assertTrue(detail.get(0).contains("No Shield"), String.join(" | ", detail));
        assertTrue(detail.get(2).startsWith("World: "), String.join(" | ", detail));
        assertTrue(detail.get(2).contains("Green Fields"));
        assertTrue(detail.get(3).startsWith("Tier: "));
        assertTrue(detail.get(4).startsWith("Rules: "), String.join(" | ", detail));
        assertTrue(detail.get(4).toLowerCase(java.util.Locale.ROOT)
                .contains("defensive"), "the rule flag in words: " + detail.get(4));
        assertTrue(detail.get(5).startsWith("Objective: "));
        assertTrue(detail.get(5).contains("30"), "the number substituted");
        assertTrue(detail.get(6).contains("Not played yet"));
        assertTrue(detail.get(7).startsWith("Rewards: "));
        assertTrue(detail.get(7).contains("200"), "the first-completion coins (E11)");
        assertTrue(detail.get(7).contains(ProgressionText.unlockableName(strings, content,
                "cosmetic:classic:ember")), "the unlock rides on the rewards line");
    }

    @Test
    void aLockedChallengeShowsItsConditionAndOffersNoPlay() {
        GoalsScreen screen = open();
        assertFalse(profile.isUnlocked("challenge:no_shield_1"));
        String lockedLine = screen.detailTexts().get(screen.detailTexts().size() - 1);
        assertTrue(lockedLine.startsWith("Locked: "), lockedLine);
        assertTrue(lockedLine.contains("20"), "the best-gates condition in words");
        assertEquals(strings.get(StringKey.CHALLENGES_LOCKED_TITLE), screen.playButton().text());
        assertNull(screen.playSource(), "a locked challenge offers no run");

        // Activating the Play button on a locked challenge is a no-op.
        tap(Keys.DOWN);
        tap(Keys.DOWN);
        assertSame(screen.playButton(), screen.focusRing().focused());
        tap(Keys.ENTER);
        ticks(GRACE);
        assertSame(screen, screens.top(), "nothing was pushed");
    }

    @Test
    void anUnlockedChallengeOffersPlayWithTheRightConfiguration() {
        GoalsScreen screen = openWired();
        assertEquals("No Shield I", screen.challengeList().options().get(0),
                "an unlocked entry shows its bare name");
        assertEquals(strings.get(StringKey.CHALLENGES_PLAY), screen.playButton().text());
        assertNotNull(screen.playSource());

        tap(Keys.DOWN);
        tap(Keys.DOWN);
        assertSame(screen.playButton(), screen.focusRing().focused());
        tap(Keys.ENTER);
        ticks(GRACE);
        GameScreen game = (GameScreen) screens.top();
        assertEquals(RunMode.CHALLENGE, game.run().config().mode());
        assertEquals("no_shield_1", game.run().config().challengeId());
        assertEquals("green_fields", game.run().config().worldId());
        assertFalse(game.run().config().allowOffers(),
                "the challenge's own draft switch");
    }

    @Test
    void aStandAloneScreenCannotStartARun() {
        profile.unlock("challenge:no_shield_1");
        GoalsScreen screen = open();
        assertEquals(strings.get(StringKey.CHALLENGES_PLAY), screen.playButton().text());
        assertNull(screen.playSource(), "no context, no run");
        tap(Keys.DOWN);
        tap(Keys.DOWN);
        tap(Keys.ENTER);
        ticks(GRACE);
        assertSame(screen, screens.top());
    }

    @Test
    void theRecordLineFollowsTheProfile() {
        PlayerProfile.ChallengeRecord record = profile.challenge("no_shield_1");
        record.attempts = 2;
        record.bestGates = 17;
        GoalsScreen screen = open();
        assertTrue(screen.detailTexts().get(6).contains("17"),
                String.join(" | ", screen.detailTexts()));
        assertTrue(screen.detailTexts().get(6).contains("2"));

        record.completed = true;
        assertTrue(screen.detailTexts().get(6).contains("completed"),
                "the completed mark rides on the record line");
    }

    @Test
    void aChallengeUnlockedUnderAnotherScreenShowsAfterThePop() {
        GoalsScreen screen = open();
        assertTrue(screen.challengeList().options().get(0).endsWith("(locked)"));
        Screen cover = new Screen() {
            @Override
            public void tick(io.github.michelbr84.flapforge.input.InputFrame input) {
            }

            @Override
            public void render(java.awt.Graphics2D g, double alpha) {
            }
        };
        screens.push(cover);
        screens.applyPending();
        ticks(2);
        profile.unlock("challenge:no_shield_1");
        screens.pop();
        screens.applyPending();
        ticks(GRACE);
        assertSame(screen, screens.top());
        assertEquals("No Shield I", screen.challengeList().options().get(0),
                "the pop never re-enters the screen, so the tick has to notice the unlock");
        assertEquals(strings.get(StringKey.CHALLENGES_PLAY), screen.playButton().text());
    }

    @Test
    void unlockingAChallengeRebuildsTheListEntry() {
        GoalsScreen screen = open();
        assertTrue(screen.challengeList().options().get(0).endsWith("(locked)"));
        profile.unlock("challenge:no_shield_1");
        screen.refreshTexts();
        assertEquals("No Shield I", screen.challengeList().options().get(0));
        assertEquals(strings.get(StringKey.CHALLENGES_PLAY), screen.playButton().text());
    }

    // ------------------------------------------------------------------ achievements tab

    @Test
    void theGridListsEveryDefinitionInContentOrder() {
        GoalsScreen screen = open(GoalsScreen.TAB_ACHIEVEMENTS);
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
        GoalsScreen screen = open(GoalsScreen.TAB_ACHIEVEMENTS);

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
        GoalsScreen screen = open(GoalsScreen.TAB_ACHIEVEMENTS);
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
        GoalsScreen screen = open(GoalsScreen.TAB_MILESTONES);

        assertEquals(1 + GoalsScreen.MILESTONE_COUNT, screen.bars().size());
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
        GoalsScreen screen = open(GoalsScreen.TAB_MILESTONES);
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
        GoalsScreen screen = open(GoalsScreen.TAB_COLLECTIONS);

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

    // ------------------------------------------------------------------ language and render

    @Test
    void aLanguageSwitchRelabelsEveryTab() {
        profile.unlock("challenge:no_shield_1");
        GoalsScreen screen = open();
        Strings.active().reload("pt_BR");
        Strings.use(Strings.active());
        ticks(1);

        Strings pt = Strings.load("pt_BR");
        assertEquals(pt.get(StringKey.CHALLENGES_TITLE), screen.tabBar().tabs().get(0).label());
        assertEquals(pt.get(StringKey.ACHIEVEMENTS_TAB_MILESTONES),
                screen.tabBar().tabs().get(2).label());
        assertEquals(pt.get(StringKey.CHALLENGES_TITLE), screen.challengeList().label());
        assertEquals(pt.get(StringKey.CHALLENGES_PLAY), screen.playButton().text());
        assertTrue(screen.detailTexts().get(2).startsWith("Mundo: "),
                () -> String.join(" | ", screen.detailTexts()));
        screen.tabBar().select(GoalsScreen.TAB_ACHIEVEMENTS);
        ticks(1);
        assertTrue(screen.line("count").startsWith("0 de 41"), screen.line("count"));
        screen.tabBar().select(GoalsScreen.TAB_COLLECTIONS);
        ticks(1);
        assertEquals(pt.get(StringKey.byKey("collections.birds")), screen.bars().get(0).label());
    }

    @Test
    void theScreenRendersAndKeepsTheScrollInsideTheContent() {
        GoalsScreen screen = open();
        presenter.present(0.5);
        assertNotNull(presenter.image(), "a frame was drawn");
        assertEquals(Playfield.WIDTH, presenter.image().getWidth());
        assertEquals(0.0, screen.scroll(), 1e-9, "the tab opens at the top");
        assertTrue(screen.bars().isEmpty(), "the challenges tab draws a list, not bars");

        screen.tabBar().select(GoalsScreen.TAB_ACHIEVEMENTS);
        ticks(1);
        presenter.present(0.5);
        assertTrue(screen.bars().isEmpty(), "the achievements tab draws lines, not bars");
        screen.tabBar().select(GoalsScreen.TAB_COLLECTIONS);
        ticks(1);
        screen.refreshTexts();
        presenter.present(0.5);
        assertEquals(0.0, screen.scroll(), 1e-9);
        assertEquals(2, screen.focusRing().nodes().size(), "tabs and back are focusable");
    }
}
