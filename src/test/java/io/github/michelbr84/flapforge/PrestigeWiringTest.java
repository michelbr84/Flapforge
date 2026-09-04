package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.persistence.SaveManager;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.StatisticsScreen;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The prestige wiring end to end (M9, E4, E23): a session with a real save. The menu shows the
 * prestige badge for a profile that has started over and nothing before the first prestige, and
 * the two-step confirm in the statistics screen writes the reset straight through the save layer —
 * the profile a fresh {@code SaveManager} loads back is a level-1 profile with a baseline.
 */
class PrestigeWiringTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    @TempDir
    private Path home;

    private boolean realHomeExisted;
    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private GameLoop loop;
    private GameContent content;
    private Strings strings;
    private SaveManager save;
    private FixedTimeSource time;
    private ToastLayer toasts;
    private GameContext context;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        SavePaths.clearOverride();
        realHomeExisted = SavePaths.profileDir().toFile().exists();
        SavePaths.override(home);

        content = GameContent.load();
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);

        time = new FixedTimeSource(1_700_000_000_000L);
        save = new SaveManager(new DirectExecutor(), time);
        save.load();
        ProgressionManager progression = new ProgressionManager(time,
                io.github.michelbr84.flapforge.progression.AchievementEvaluator.of(content),
                io.github.michelbr84.flapforge.progression.UnlockEvaluator.of(content));
        strings = Strings.load("en");
        Strings.use(strings);
        toasts = new ToastLayer();
        context = new GameContext(LaunchOptions.DEFAULTS, clock, time, new Threads(), input,
                viewport, screens, presenter, null, loop, FrameLimiter.uncapped(clock), null,
                new EventBus(), new AudioManager(new NullAudio()), strings, toasts, content,
                save, progression, ProgressionRules.fromEconomy(content.economy()));
        screens.setTickTask(context::drainSaveResults);
    }

    @AfterEach
    void tearDown() {
        SavePaths.clearOverride();
        Strings.use(Strings.load("en"));
        assertEquals(realHomeExisted, SavePaths.profileDir().toFile().exists(),
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

    @Test
    void theMenuBadgesAProfileThatHasPrestiged() {
        MainMenuScreen menu = new MainMenuScreen(context,
                new io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory(),
                SeedSequence.of(1));
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);

        assertEquals("", menu.prestigeBadge(), "no prestige, no badge");
        context.profile().prestigeCount = 3;
        ticks(1);
        assertEquals(strings.format(StringKey.MENU_PRESTIGE_BADGE, 3), menu.prestigeBadge(),
                "the badge notices the count on its own");
    }

    @Test
    void aPrestigeThroughThePanelIsWrittenStraightBack() {
        PlayerProfile profile = context.profile();
        profile.level = 25;
        profile.wallet.put(PlayerProfile.CURRENCY_COINS, 700L);
        profile.unlock("world:wind_valley");

        MainMenuScreen menu = new MainMenuScreen(context, new ClassicRunFactory(),
                SeedSequence.of(1));
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        assertEquals("", menu.prestigeBadge(), "the badge waits for the first prestige");

        // The wired menu lists the meta screens before Statistics; walk down to it.
        for (int i = 0; i < 10 && menu.focusRing().focused() != menu.statisticsButton(); i++) {
            tap(Keys.DOWN);
        }
        assertSame(menu.statisticsButton(), menu.focusRing().focused());
        tap(Keys.ENTER);
        ticks(GRACE);
        StatisticsScreen stats = (StatisticsScreen) screens.top();

        tap(Keys.DOWN);
        tap(Keys.ENTER);
        tap(Keys.ENTER);
        ticks(2);

        assertEquals(1, profile.prestigeCount, "the confirm reset the profile");
        assertEquals(1, profile.level);
        assertFalse(profile.isUnlocked("world:wind_valley"));
        assertEquals(1, toasts.pushedCount(), "the player is told what happened");

        // The write went through the save layer: a fresh reader loads the prestige, not the
        // profile the screen started with.
        SaveManager reader = new SaveManager(new DirectExecutor(), time);
        PlayerProfile reloaded = reader.load().profile();
        assertEquals(1, reloaded.prestigeCount);
        assertEquals(1, reloaded.level);
        assertTrue(reloaded.isUnlocked("cosmetic:classic:prestige"));
        assertFalse(reloaded.isUnlocked("world:wind_valley"), "the reset persisted (E22)");

        // And the menu badge is live on the way back out.
        tap(Keys.ESCAPE);
        ticks(2);
        assertEquals(strings.format(StringKey.MENU_PRESTIGE_BADGE, 1), menu.prestigeBadge());
    }
}
