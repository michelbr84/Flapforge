package io.github.michelbr84.flapforge.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.SfxSet;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.persistence.Settings;
import io.github.michelbr84.flapforge.support.CaptureAudioBackend;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The manager is the only place that decides which cue a moment deserves, so this test pins the
 * event-to-sound table, the volume arithmetic and the mute behaviour. It runs against the capture
 * backend, which mixes the real generated samples, so "the right cue fired" and "something
 * audible came out" are both checked.
 */
class AudioManagerTest {

    private CaptureAudioBackend backend;
    private AudioManager manager;

    @BeforeEach
    void setUp() {
        backend = new CaptureAudioBackend();
        manager = new AudioManager(backend);
        manager.setVolumes(1.0, 1.0, 1.0);
    }

    @Test
    void mapsGameplayEventsToTheirCues() {
        assertEquals(ToneSynth.FLAP, AudioManager.sfxIdFor(new GameEvent.Flapped(false)));
        assertEquals(ToneSynth.SCORE, AudioManager.sfxIdFor(new GameEvent.GatePassed(3, true)));
        assertEquals(ToneSynth.COIN, AudioManager.sfxIdFor(new GameEvent.CoinCollected(1, 4)));
        assertEquals(ToneSynth.CRASH, AudioManager.sfxIdFor(new GameEvent.Crashed("pipe", 7)));
        assertEquals(ToneSynth.ABILITY,
                AudioManager.sfxIdFor(new GameEvent.AbilityActivated("dash", 2)));
        assertEquals(ToneSynth.ABILITY, AudioManager.sfxIdFor(new GameEvent.AbilityReady("dash")));
        assertEquals(ToneSynth.SHIELD, AudioManager.sfxIdFor(new GameEvent.ShieldAbsorbed(1)));
        assertEquals(ToneSynth.REVIVE, AudioManager.sfxIdFor(new GameEvent.Revived(0)));
        assertEquals(ToneSynth.SYNERGY,
                AudioManager.sfxIdFor(new GameEvent.SynergyActivated("daredevil")));
        assertEquals(ToneSynth.RULE_SHIFT,
                AudioManager.sfxIdFor(new GameEvent.RuleShift(List.of("NO_COINS"))));
        assertEquals(ToneSynth.LIGHTNING_WARNING,
                AudioManager.sfxIdFor(new GameEvent.LightningWarning()));
        assertEquals(ToneSynth.THUNDER, AudioManager.sfxIdFor(new GameEvent.AmbientFlash()));
        assertEquals(ToneSynth.PISTON_TELEGRAPH,
                AudioManager.sfxIdFor(new GameEvent.PistonTelegraph()));
        assertEquals(ToneSynth.WIND, AudioManager.sfxIdFor(new GameEvent.WindGust()));
    }

    @Test
    void picksTheSoundSetOfTheRunsWorldAndLeavesTheMenuCuesAlone() {
        assertEquals(SfxSet.FIELDS, manager.sfxSet(), "the canonical set before any run");
        manager.setSfxSetResolver(world -> "storm_sky".equals(world) ? SfxSet.STORM : null);
        manager.handle(new GameEvent.RunStarted("classic", "storm_sky", "normal", 1L));
        assertEquals(SfxSet.STORM, manager.sfxSet());
        manager.handle(new GameEvent.Flapped(false));
        assertEquals(SoundBank.key(ToneSynth.FLAP, SfxSet.STORM), backend.lastId(),
                "an in-run cue is asked for in the world's set");
        manager.uiSelect();
        assertEquals(ToneSynth.UI_SELECT, backend.lastId(), "a menu cue never is");
        manager.handle(new GameEvent.RunStarted("classic", "green_fields", "normal", 2L));
        assertEquals(SfxSet.FIELDS, manager.sfxSet(), "an unknown answer is the fields");
        manager.handle(new GameEvent.GatePassed(1, true));
        assertEquals(ToneSynth.SCORE, backend.lastId(), "the fields key is the bare id");
    }

    @Test
    void mapsProgressionAndUiEventsToTheirCues() {
        assertEquals(ToneSynth.BOSS_WARNING,
                AudioManager.sfxIdFor(new GameEvent.BossWarning("gale", 3)));
        assertEquals(ToneSynth.BOSS_WARNING,
                AudioManager.sfxIdFor(new GameEvent.BossStarted("gale")));
        assertEquals(ToneSynth.UNLOCK,
                AudioManager.sfxIdFor(new GameEvent.UnlockGranted("bird:zephyr")));
        assertEquals(ToneSynth.UNLOCK,
                AudioManager.sfxIdFor(new GameEvent.AchievementUnlocked("first_flight")));
        assertEquals(ToneSynth.UNLOCK,
                AudioManager.sfxIdFor(new GameEvent.BossCleared("gale", "wind_valley")));
        assertEquals(ToneSynth.LEVEL_UP, AudioManager.sfxIdFor(new GameEvent.LevelUp(4)));
        assertEquals(ToneSynth.LEVEL_UP,
                AudioManager.sfxIdFor(new GameEvent.ChallengeCompleted("no_shield_1", true)));
        assertEquals(ToneSynth.UI_SELECT,
                AudioManager.sfxIdFor(new GameEvent.LanguageChanged("pt_BR")));
        assertEquals(ToneSynth.UI_MOVE,
                AudioManager.sfxIdFor(new GameEvent.ScreenChanged("settings")));
        assertEquals(ToneSynth.UI_BACK,
                AudioManager.sfxIdFor(new GameEvent.SaveFailed("settings.json", "disk full")));
    }

    @Test
    void leavesTheDeliberatelySilentEventsSilent() {
        assertNull(AudioManager.sfxIdFor(new GameEvent.NearMiss(9)));
        assertNull(AudioManager.sfxIdFor(new GameEvent.RunEnded(9, 120, 900, false)));
        assertNull(AudioManager.sfxIdFor(new GameEvent.XpGained(30, 300)));
        assertNull(AudioManager.sfxIdFor(new GameEvent.CurrencyChanged("coins", 12, 400)));
        assertNull(AudioManager.sfxIdFor(new GameEvent.DailyRecorded("2026-09-01", 12)));
        // A streak that steps up is a cue; a streak that breaks is not.
        assertEquals(ToneSynth.STREAK, AudioManager.sfxIdFor(new GameEvent.StreakChanged(3, 1)));
        assertNull(AudioManager.sfxIdFor(new GameEvent.StreakChanged(0, 0)));
    }

    @Test
    void playsThroughTheBusAndProducesAudibleOutput() {
        EventBus bus = new EventBus();
        manager.attach(bus);
        assertTrue(manager.isAttached());
        assertEquals(1, bus.subscriberCount(GameEvent.class));

        bus.publish(new GameEvent.Flapped(false));
        bus.publish(new GameEvent.GatePassed(1, true));
        bus.publish(new GameEvent.Crashed("ground", 1));

        assertEquals(List.of(ToneSynth.FLAP, ToneSynth.SCORE, ToneSynth.CRASH), backend.ids());
        assertTrue(backend.peak() > 0.05f, "the mixed output must not be silent: " + backend.peak());
        assertTrue(backend.rms() > 0.0);

        manager.detach();
        assertFalse(manager.isAttached());
        bus.publish(new GameEvent.Flapped(false));
        assertEquals(3, backend.playCount(), "a detached manager must stop playing");
    }

    @Test
    void silentEventsReachTheBackendAsNothingAtAll() {
        manager.handle(new GameEvent.NearMiss(2));
        manager.handle(new GameEvent.XpGained(10, 10));
        assertEquals(0, backend.playCount());
        assertEquals(0.0f, backend.peak(), 0.0f);
    }

    @Test
    void muteSilencesEverything() {
        manager.setMuted(true);
        assertTrue(manager.isMuted());
        assertEquals(0.0f, backend.masterGain(), 0.0f, "mute drops the master fader to zero");
        assertEquals(1, backend.stops(), "mute stops the voices already in flight");

        manager.handle(new GameEvent.Flapped(false));
        manager.uiSelect();
        assertEquals(0, backend.playCount(), "a muted game must not queue anything");
        assertEquals(0.0f, backend.peak(), 0.0f);
        assertEquals(2, manager.suppressed());

        manager.setMuted(false);
        manager.handle(new GameEvent.Flapped(false));
        assertEquals(1, backend.playCount());
        assertEquals(1.0f, backend.masterGain(), 1e-6f);
    }

    @Test
    void volumesScaleTheOutput() {
        manager.setVolumes(0.5, 0.4, 0.25);
        assertEquals(0.5, manager.masterVolume(), 1e-9);
        assertEquals(0.4, manager.sfxVolume(), 1e-9);
        assertEquals(0.25, manager.musicVolume(), 1e-9);
        assertEquals(0.5f, backend.masterGain(), 1e-6f, "the master fader goes to the backend");

        manager.playSfx(ToneSynth.SCORE);
        assertEquals(0.4f, backend.lastGain(), 1e-6f, "the effect volume folds into the play gain");

        // The master fader multiplies on top of the play gain inside the backend.
        float quiet = backend.peak();
        backend.reset();
        manager.setVolumes(1.0, 1.0, 1.0);
        manager.playSfx(ToneSynth.SCORE);
        assertTrue(backend.peak() > quiet * 4.0f,
                "a five-fold volume increase must be audible: " + quiet + " -> " + backend.peak());
    }

    @Test
    void aZeroEffectVolumeSuppressesPlaysWithoutMuting() {
        manager.setVolumes(1.0, 0.0, 1.0);
        manager.handle(new GameEvent.GatePassed(1, true));
        assertEquals(0, backend.playCount());
        assertEquals(1, manager.suppressed());
        assertFalse(manager.isMuted());
        assertEquals(1.0f, backend.masterGain(), 1e-6f);
    }

    @Test
    void settingsChangedAppliesVolumesAndMute() {
        Settings settings = Settings.defaults();
        settings.masterVolume = 0.3;
        settings.sfxVolume = 0.6;
        settings.musicVolume = 0.1;
        settings.muted = true;

        manager.handle(new GameEvent.SettingsChanged(settings));
        assertEquals(0.3, manager.masterVolume(), 1e-9);
        assertEquals(0.6, manager.sfxVolume(), 1e-9);
        assertEquals(0.1, manager.musicVolume(), 1e-9);
        assertTrue(manager.isMuted());
        assertEquals(0.0f, backend.masterGain(), 0.0f);
        assertEquals(0, backend.playCount(), "a settings change is not itself a cue");
    }

    @Test
    void clampsNonsenseVolumes() {
        manager.setVolumes(4.0, -1.0, Double.NaN);
        assertEquals(1.0, manager.masterVolume(), 1e-9);
        assertEquals(0.0, manager.sfxVolume(), 1e-9);
        assertEquals(0.0, manager.musicVolume(), 1e-9);
    }

    @Test
    void uiHelpersPlayTheUiCues() {
        manager.uiMove();
        manager.uiSelect();
        manager.uiBack();
        assertEquals(List.of(ToneSynth.UI_MOVE, ToneSynth.UI_SELECT, ToneSynth.UI_BACK),
                backend.ids());
        assertTrue(backend.peak() > 0.05f);
    }

    @Test
    void warmUpAndCloseReachTheBackend() {
        EventBus bus = new EventBus();
        manager.attach(bus);
        manager.warmUp();
        assertEquals(1, backend.warmUps());

        manager.close();
        assertTrue(backend.isClosed());
        assertFalse(manager.isAttached());
        assertEquals(0, bus.subscriberCount(GameEvent.class));
    }

    @Test
    void theBootHandOverReplacesTheBackendAndCarriesTheFaderAcross() {
        // The application starts with NullAudio and the boot step installs the mixer it opened on
        // the boot thread (D19), so the fader the settings already set has to travel with it.
        NullAudio silent = new NullAudio();
        AudioManager handing = new AudioManager(silent);
        handing.setVolumes(0.25, 1.0, 0.6);

        CaptureAudioBackend opened = new CaptureAudioBackend();
        handing.setBackend(opened);

        assertSame(opened, handing.backend());
        assertEquals(0.25f, opened.masterGain(), 1e-6f, "the fader followed the hand-over");
        assertTrue(silent.isClosed(), "the backend it replaced is released");

        handing.playSfx(ToneSynth.FLAP);
        assertEquals(1, opened.playCount(), "plays go to the new backend");
    }

    @Test
    void aBootStepThatFinishesAfterTheQuitDoesNotLeaveADeviceOpen() {
        AudioManager quitting = new AudioManager(new CaptureAudioBackend());
        quitting.close();

        CaptureAudioBackend late = new CaptureAudioBackend();
        quitting.setBackend(late);

        assertTrue(late.isClosed(), "a backend handed to a closed manager is closed at once");
        assertNotSame(late, quitting.backend(), "and never adopted");
    }

    @Test
    void defaultsMatchTheSettingsDefaults() {
        AudioManager fresh = new AudioManager(new CaptureAudioBackend());
        Settings defaults = Settings.defaults();
        assertEquals(defaults.masterVolume, fresh.masterVolume(), 1e-9);
        assertEquals(defaults.sfxVolume, fresh.sfxVolume(), 1e-9);
        assertEquals(defaults.musicVolume, fresh.musicVolume(), 1e-9);
        assertEquals(defaults.muted, fresh.isMuted());
    }
}
