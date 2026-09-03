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
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.ChallengesScreen;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The challenges screen (D17, E6, M8), driven headlessly through the input queue and the loop the
 * way {@code StatisticsScreenTest} drives the statistics screen: the seven challenges in file
 * order, the detail block carrying world (a label, never an unlock requirement), tier, rules,
 * objective, record and rewards, locked challenges offering their condition instead of Play, and
 * Play starting a {@code CHALLENGE} run with the challenge's own configuration.
 */
class ChallengesScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

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
    private ProgressionManager progression;
    private io.github.michelbr84.flapforge.progression.PlayerProfile profile;
    private GameContext context;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        SavePaths.clearOverride();
        realHomeExisted = Files.exists(SavePaths.profileDir());
        SavePaths.override(home);
        assertEquals(home.toAbsolutePath().normalize(), SavePaths.profileDir(),
                "the profile directory must be the temporary one");

        content = GameContent.load();
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        presenter = new NullPresenter(screens, viewport, Playfield.WIDTH, Playfield.HEIGHT);
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);

        FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
        SaveManager save = new SaveManager(new DirectExecutor(), time);
        save.load();
        progression = new ProgressionManager(time,
                io.github.michelbr84.flapforge.progression.AchievementEvaluator.of(content),
                io.github.michelbr84.flapforge.progression.UnlockEvaluator.of(content));
        EventBus events = new EventBus();
        Strings stringsLoaded = Strings.load("en");
        Strings.use(stringsLoaded);
        strings = stringsLoaded;
        profile = save.profile();
        context = new GameContext(LaunchOptions.DEFAULTS, (io.github.michelbr84.flapforge.app.Clock) clock, time,
                new Threads(), input, viewport, screens, presenter, null, loop,
                FrameLimiter.uncapped(clock), null, events, new AudioManager(new NullAudio()),
                strings, new ToastLayer(), content, save, progression,
                ProgressionRules.fromEconomy(content.economy()));
        screens.setTickTask(context::drainSaveResults);
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

    private ChallengesScreen open() {
        ChallengesScreen screen = new ChallengesScreen(context);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        return screen;
    }

    @Test
    void theListCarriesEveryChallengeInFileOrder() {
        ChallengesScreen screen = open();
        assertEquals(content.challenges().ids().size(), screen.list().options().size());
        for (int i = 0; i < content.challenges().ids().size(); i++) {
            assertSame(content.challenges().all().get(i), screen.selected());
            tap(Keys.RIGHT);
        }
        assertTrue(screen.list().options().get(0).contains("No Shield"),
                "the first challenge by file order");
    }

    @Test
    void theDetailBlockNamesWorldTierRulesObjectiveAndRewards() {
        ChallengesScreen screen = open();
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
        ChallengesScreen screen = open();
        assertTrue(profile.isUnlocked("challenge:no_shield_1") == false);
        String lockedLine = screen.detailTexts().get(screen.detailTexts().size() - 1);
        assertTrue(lockedLine.startsWith("Locked: "), lockedLine);
        assertTrue(lockedLine.contains("20"), "the best-gates condition in words");
        assertEquals(strings.get(StringKey.CHALLENGES_LOCKED_TITLE), screen.playButton().text());
        assertNull(screen.playSource(), "a locked challenge offers no run");

        // Activating the Play button on a locked challenge is a no-op.
        tap(Keys.DOWN);
        assertSame(screen.playButton(), screen.focusRing().focused());
        tap(Keys.ENTER);
        ticks(GRACE);
        assertSame(screen, screens.top(), "nothing was pushed");
    }

    @Test
    void anUnlockedChallengeOffersPlayWithTheRightConfiguration() {
        profile.unlock("challenge:no_shield_1");
        ChallengesScreen screen = open();
        assertEquals("No Shield I", screen.list().options().get(0),
                "an unlocked entry shows its bare name");
        assertEquals(strings.get(StringKey.CHALLENGES_PLAY), screen.playButton().text());
        assertNotNull(screen.playSource());

        tap(Keys.DOWN);
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
    void theRecordLineFollowsTheProfile() {
        io.github.michelbr84.flapforge.progression.PlayerProfile.ChallengeRecord record =
                profile.challenge("no_shield_1");
        record.attempts = 2;
        record.bestGates = 17;
        ChallengesScreen screen = open();
        assertTrue(screen.detailTexts().get(6).contains("17"), String.join(" | ", screen
                .detailTexts()));
        assertTrue(screen.detailTexts().get(6).contains("2"));

        record.completed = true;
        assertTrue(screen.detailTexts().get(6).contains("completed"),
                "the completed mark rides on the record line");
    }

    @Test
    void aLanguageSwitchRelabelsTheScreen() {
        profile.unlock("challenge:no_shield_1");
        ChallengesScreen screen = open();
        Strings.active().reload("pt_BR");
        Strings.use(Strings.active());
        ticks(1);

        Strings pt = Strings.load("pt_BR");
        assertEquals(pt.get(StringKey.CHALLENGES_TITLE), screen.list().label());
        assertEquals(pt.get(StringKey.CHALLENGES_PLAY), screen.playButton().text());
        assertTrue(screen.detailTexts().get(2).startsWith("Mundo: "),
                () -> String.join(" | ", screen.detailTexts()));
    }

    @Test
    void theScreenRendersWithoutExceptions() {
        ChallengesScreen screen = open();
        presenter.present(0.5);
        assertNotNull(presenter.image(), "a frame was drawn");
        assertEquals(Playfield.WIDTH, presenter.image().getWidth());
        assertSame(screen, screens.top());
        assertEquals(3, screen.focusRing().nodes().size(), "list, play and back are focusable");
    }

    @Test
    void unlockingAChallengeRebuildsTheListEntry() {
        ChallengesScreen screen = open();
        assertTrue(screen.list().options().get(0).endsWith("(locked)"));
        profile.unlock("challenge:no_shield_1");
        screen.refreshTexts();
        assertEquals("No Shield I", screen.list().options().get(0));
        assertEquals(strings.get(StringKey.CHALLENGES_PLAY), screen.playButton().text());
    }
}
