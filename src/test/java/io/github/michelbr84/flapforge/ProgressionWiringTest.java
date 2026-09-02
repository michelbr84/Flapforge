package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.Clock;
import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.app.Threads;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.audio.ToneSynth;
import io.github.michelbr84.flapforge.audio.NullAudio;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.persistence.SaveManager;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionOutcome;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.ContentRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The M3 wiring, end to end on the loop thread (D14, D15, D29): a run that reaches
 * {@code FINISHED} is written into the profile exactly once, the coins and the experience are
 * announced on the bus, the profile is queued for the disk before the overlay appears, and the
 * instant retry keeps every reward.
 *
 * <p>Every save in here goes to a {@code @TempDir}: {@link #setUp()} proves the override took, and
 * {@link #tearDown()} proves the real profile directory was neither created nor touched.
 */
class ProgressionWiringTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;
    /** Enough ticks for an unflapped bird to fall from 320 to the ground line and land. */
    private static final int FALL_TICKS = 240;

    @TempDir
    private Path home;

    private boolean realHomeExisted;
    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private GameLoop loop;
    private SaveManager save;
    private ProgressionManager progression;
    private GameContext context;
    private GameContent content;
    private GameScreen game;
    private final List<GameEvent> published = new ArrayList<>();
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
        NullPresenter presenter = new NullPresenter();
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);

        FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
        // A direct executor makes the write observable the moment it is queued (D15).
        save = new SaveManager(new DirectExecutor(), time);
        save.load();
        progression = new ProgressionManager(time);
        EventBus events = new EventBus();
        events.subscribe(GameEvent.class, published::add);
        Strings strings = Strings.load("en");
        Strings.use(strings);
        context = new GameContext(LaunchOptions.DEFAULTS, (Clock) clock, time, new Threads(),
                input, viewport, screens, presenter, null, loop, FrameLimiter.uncapped(clock),
                null, events, new AudioManager(new NullAudio()), strings, new ToastLayer(),
                content, save, progression, ProgressionRules.fromEconomy(content.economy()));
        screens.setTickTask(context::drainSaveResults);
        game = new GameScreen(context, new ContentRunFactory(content, RunMode.SEEDED),
                SeedSequence.from(42L));
        screens.push(game);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
    }

    @AfterEach
    void tearDown() {
        SavePaths.clearOverride();
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

    /**
     * Starts the run with one flap and lets the bird fall until the overlay is up. The leading
     * grace ticks matter after a retry: the manager swallows input for a few ticks after every
     * screen change, so the flap that starts the run must come after that window.
     *
     * @return the overlay the finished run pushed
     */
    private GameOverOverlay diveToGameOver() {
        ticks(GRACE);
        tap(Keys.SPACE);
        for (int i = 0; i < FALL_TICKS && !(screens.top() instanceof GameOverOverlay); i++) {
            ticks(1);
        }
        assertTrue(screens.top() instanceof GameOverOverlay, "the dive ended the run");
        return (GameOverOverlay) screens.top();
    }

    private PlayerProfile profile() {
        return save.profile();
    }

    private long coins() {
        Long balance = profile().wallet.get(PlayerProfile.CURRENCY_COINS);
        return balance == null ? 0 : balance;
    }

    private <T extends GameEvent> List<T> eventsOf(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (GameEvent event : published) {
            if (type.isInstance(event)) {
                out.add(type.cast(event));
            }
        }
        return out;
    }

    @Test
    void theFinishedRunIsWrittenIntoTheProfileExactlyOnce() {
        GameOverOverlay overlay = diveToGameOver();
        assertEquals(1, profile().statistics.totalRuns);
        ProgressionOutcome outcome = overlay.outcome();
        assertNotNull(outcome, "the overlay carries what the run paid");
        assertEquals(coins(), outcome.rewardSummary().coins()
                + outcome.levelRewardsGranted().getOrDefault(PlayerProfile.CURRENCY_COINS, 0L));
        // The overlay only blinks; nothing here may write the run a second time (D14).
        ticks(180);
        assertEquals(1, profile().statistics.totalRuns);
        assertEquals(1, eventsOf(GameEvent.RunEnded.class).size());
    }

    /**
     * D16/D17: the four ability facts reach the bus, so the ability sounds have a source. The
     * mapping is the one seam between a simulation fact and a sound, and the ability facts were
     * the ones M5 added — an activation the player hears nothing for reads as a dropped input.
     */
    @Test
    void anActivatedAbilityIsAnnouncedOnTheBus() {
        // The default screen of this fixture flies without a loadout; the ability facts need the
        // profile-aware factory, which is what the application wires (E18: a fresh profile flies
        // with the double flap).
        GameScreen equipped = new GameScreen(context,
                new ContentRunFactory(content, RunMode.SEEDED, save::profile),
                SeedSequence.from(42L));
        screens.push(equipped);
        screens.applyPending();
        ticks(GRACE);
        tap(Keys.SPACE);
        assertEquals("double_flap", equipped.run().simulation().abilities().active().id());
        tap(Keys.X);

        List<GameEvent.AbilityActivated> used = eventsOf(GameEvent.AbilityActivated.class);
        assertEquals(1, used.size(), "the press was announced once");
        assertEquals("double_flap", used.get(0).abilityId());
        assertEquals(1, used.get(0).level());
        assertEquals(ToneSynth.ABILITY, AudioManager.sfxIdFor(used.get(0)),
                "and the audio manager knows what to play for it");
    }

    @Test
    void theFirstRunBonusIsPaidAndAnnouncedOnTheBus() {
        diveToGameOver();
        assertTrue(coins() > 0, "E32.a: the first run always pays");
        List<GameEvent.CurrencyChanged> currency = eventsOf(GameEvent.CurrencyChanged.class);
        assertEquals(1, currency.size());
        assertEquals(PlayerProfile.CURRENCY_COINS, currency.get(0).currency());
        assertEquals(coins(), currency.get(0).total());
        // The participation gate covers the XP term too, so a 0-gate dive pays no experience and
        // there is nothing to announce. Only the unconditional first-run bonus moved the wallet.
        assertTrue(eventsOf(GameEvent.XpGained.class).isEmpty(),
                "a 0-gate dive earns no experience");
        assertEquals(0, profile().xp);
    }

    @Test
    void theProfileReachesTheDiskBeforeTheOverlayIsUp() {
        diveToGameOver();
        Path file = SavePaths.saveFile();
        assertTrue(Files.isRegularFile(file), "the run end queued a save (D15)");
        assertFalse(progression.isDirty(), "the completed write cleared the dirty flag");
        SaveManager reader = new SaveManager(new DirectExecutor(), new FixedTimeSource(0));
        assertEquals(1, reader.load().profile().statistics.totalRuns,
                "the written file holds the run");
    }

    @Test
    void theInstantRetryKeepsTheRewards() {
        diveToGameOver();
        long afterFirst = coins();
        long xpAfterFirst = profile().xp;
        assertTrue(afterFirst > 0);

        // The overlay was pushed a tick ago; the manager swallows input during its grace window.
        ticks(GRACE);
        tap(Keys.SPACE);
        assertTrue(screens.top() instanceof GameScreen, "the retry went straight back into a run");
        assertEquals(2, game.runsStarted());
        assertEquals(afterFirst, coins(), "D29: rewards are never lost by a retry");
        assertEquals(xpAfterFirst, profile().xp);

        diveToGameOver();
        assertEquals(2, profile().statistics.totalRuns);
        assertTrue(coins() >= afterFirst, "the second run added to the same wallet");
    }
}
