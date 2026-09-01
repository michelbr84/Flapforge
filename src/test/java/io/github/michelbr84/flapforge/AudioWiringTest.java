package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.persistence.AtomicFiles;
import io.github.michelbr84.flapforge.persistence.Settings;
import io.github.michelbr84.flapforge.persistence.SettingsStore;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.CaptureAudioBackend;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import java.awt.Graphics2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The seams the application wires between gameplay, the event bus and the audio stack (M2, D16,
 * D19): a screen transition is announced once, an event published on the bus reaches the backend
 * as a sound, a settings change carries the volumes and the mute flag through, and the menu cues
 * the UI components raise land on the manager the application installed.
 *
 * <p>Nothing here opens a device: the backend is the capture one, so the assertions are about
 * which sound was asked for, not about what came out of a speaker.
 */
class AudioWiringTest {

    /** A screen that does nothing, identifiable by its class name in {@code ScreenChanged}. */
    static final class FirstScreen implements Screen {

        @Override
        public void tick(InputFrame input) {
            // Nothing to do.
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            // Nothing to draw.
        }
    }

    /** A second screen, so a transition changes the name the manager announces. */
    static final class SecondScreen implements Screen {

        @Override
        public void tick(InputFrame input) {
            // Nothing to do.
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            // Nothing to draw.
        }
    }

    @TempDir
    Path home;

    private EventBus events;
    private CaptureAudioBackend backend;
    private AudioManager audio;
    private ScreenManager screens;
    private SettingsStore store;
    private ToastLayer toasts;
    private GameContext context;
    private final List<GameEvent> published = new ArrayList<>();

    @BeforeEach
    void setUp() {
        events = new EventBus();
        events.adopt();
        backend = new CaptureAudioBackend();
        audio = new AudioManager(backend);
        audio.attach(events);
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        screens.setEvents(events);
        ManualClock clock = new ManualClock(1_000_000_000L);
        InputQueue input = new InputQueue(KeyBindings.defaults());
        FrameLimiter limiter = FrameLimiter.uncapped(clock);
        GameLoop loop = new GameLoop(clock, input, screens, presenter, limiter);
        store = new SettingsStore(new DirectExecutor(), home.resolve("settings.json"));
        store.load();
        Strings strings = Strings.load("en");
        Strings.use(strings);
        toasts = new ToastLayer();
        context = new GameContext(LaunchOptions.DEFAULTS, (Clock) clock, () -> 0L, new Threads(),
                input, viewport, screens, presenter, null, loop, limiter, store, events, audio,
                strings, toasts, null);
        events.subscribe(GameEvent.class, published::add);
    }

    @AfterEach
    void tearDown() {
        UiCues.silence();
        Fonts.setTextScale(1.0);
        ProceduralArt.setSmoothing(true);
        ParticleSystem.setDefaultReduceFlashing(true);
    }

    private List<String> screenChanges() {
        List<String> names = new ArrayList<>();
        for (GameEvent event : published) {
            if (event instanceof GameEvent.ScreenChanged changed) {
                names.add(changed.screen());
            }
        }
        return names;
    }

    @Test
    void everyTransitionIsAnnouncedOnceAndAReplacementIsOneTransition() {
        screens.push(new FirstScreen());
        screens.applyPending();
        screens.push(new SecondScreen());
        screens.applyPending();
        screens.replace(new FirstScreen());
        screens.applyPending();
        screens.pop();
        screens.applyPending();

        assertEquals(List.of("FirstScreen", "SecondScreen", "FirstScreen"), screenChanges(),
                "a replace pops and pushes but is one transition; the final pop empties the stack");
    }

    @Test
    void aPushThatDoesNotChangeTheTopIsNotAnnouncedTwice() {
        screens.push(new FirstScreen());
        screens.applyPending();
        int before = screenChanges().size();
        screens.applyPending();
        assertEquals(before, screenChanges().size(), "an empty batch announces nothing");
    }

    @Test
    void runFactsPublishedOnTheBusReachTheBackendAsSounds() {
        events.publish(new GameEvent.Flapped(false));
        events.publish(new GameEvent.GatePassed(1, true));
        events.publish(new GameEvent.NearMiss(1));
        events.publish(new GameEvent.Crashed("PIPE", 1));

        assertEquals(List.of(ToneSynth.FLAP, ToneSynth.SCORE, ToneSynth.CRASH), backend.ids(),
                "a near miss is deliberately silent");
    }

    @Test
    void aScreenChangeIsAudibleAsTheSoftMovementBlip() {
        screens.push(new FirstScreen());
        screens.applyPending();
        assertEquals(List.of(ToneSynth.UI_MOVE), backend.ids());
    }

    @Test
    void applyingSettingsCarriesTheVolumesAndTheMuteFlagToTheManager() {
        Settings settings = Settings.defaults();
        settings.masterVolume = 0.25;
        settings.sfxVolume = 0.5;
        settings.musicVolume = 0.75;
        context.applySettings(settings);

        assertEquals(0.25, audio.masterVolume(), 1e-9);
        assertEquals(0.5, audio.sfxVolume(), 1e-9);
        assertEquals(0.75, audio.musicVolume(), 1e-9);
        assertEquals(0.25f, backend.masterGain(), 1e-6f, "the master fader reaches the backend");

        backend.reset();
        audio.playSfx(ToneSynth.FLAP);
        assertEquals(0.5f, backend.lastGain(), 1e-6f, "the effect volume is folded into the play");
    }

    @Test
    void theMuteHotkeyFlipsTheSettingSilencesTheManagerAndPersists() {
        assertFalse(store.settings().muted, "the default is unmuted");

        assertTrue(context.toggleMute(), "the first press mutes");
        assertTrue(audio.isMuted(), "the manager heard the settings change");
        assertTrue(store.settings().muted, "and the store kept it");
        assertEquals(1, toasts.visibleCount(), "muting is invisible without a toast");
        backend.reset();
        audio.playSfx(ToneSynth.FLAP);
        assertEquals(0, backend.playCount(), "a muted game queues nothing at all");

        assertFalse(context.toggleMute(), "the second press unmutes");
        assertFalse(audio.isMuted());
        audio.playSfx(ToneSynth.FLAP);
        assertEquals(1, backend.playCount());
    }

    @Test
    void theMenuCuesReachTheInstalledSinkAndAreSilentWithoutOne() {
        UiCues.move();
        UiCues.select();
        UiCues.back();
        assertEquals(0, backend.playCount(), "no sink is installed by default");

        UiCues.use(UiCues.of(audio::uiMove, audio::uiSelect, audio::uiBack));
        UiCues.move();
        UiCues.select();
        UiCues.back();
        assertEquals(List.of(ToneSynth.UI_MOVE, ToneSynth.UI_SELECT, ToneSynth.UI_BACK),
                backend.ids());

        UiCues.silence();
        backend.reset();
        UiCues.move();
        assertEquals(0, backend.playCount(), "silence() puts the silent sink back");
    }

    @Test
    void aFailedSettingsWriteBecomesAnEventAndAToast() {
        store.failurePoint(AtomicFiles.FailurePoint.BEFORE_MOVE);
        context.saveSettings(Settings.defaults());

        boolean sawFailure = false;
        for (GameEvent event : published) {
            if (event instanceof GameEvent.SaveFailed) {
                sawFailure = true;
            }
        }
        assertTrue(sawFailure, "a failed write is announced");
        assertEquals(1, toasts.visibleCount(), "and shown to the player");
        assertEquals(ToneSynth.UI_BACK, backend.lastId(), "with the negative cue");
    }
}
