package io.github.michelbr84.flapforge.audio;

import io.github.michelbr84.flapforge.content.defs.SfxSet;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.persistence.Settings;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Turns game events into sounds (D16, D19). It is the only thing in the game that decides which
 * cue a moment deserves; screens and systems publish facts and never name a sound id.
 *
 * <p>It subscribes once, to {@link GameEvent} itself, so a new event type cannot silently slip
 * past a missing registration — {@link #sfxIdFor(GameEvent)} is a total function over the sealed
 * hierarchy and answers {@code null} for the events that are deliberately silent (near misses,
 * XP, currency book-keeping, the run-ended summary that a crash has already scored).
 *
 * <p>It also owns the three volumes and the mute flag from {@link Settings}. The master fader
 * goes to the backend, where it applies to future music voices too; the effect volume is folded
 * into each play. Muting does both: the fader drops to zero <em>and</em> plays are skipped, so a
 * muted game queues nothing at all.
 *
 * <p>Nothing here blocks. Every path ends in {@link AudioBackend#play(String, float, float)},
 * which is a bounded, drop-on-full queue offer, so the loop thread pays a handful of nanoseconds
 * per event however busy the mixer is.
 *
 * <p>From M7 the in-run cues come in the sound set of the run's world (E31.g): {@code RunStarted}
 * names the world, {@link #setSfxSetResolver(Function)} maps it to its {@link SfxSet} (the
 * application installs {@code worlds.json.sfxSet}), and every gameplay cue is then asked for
 * under {@link SoundBank#key(String, SfxSet)}. The menu cues stay in the canonical set, so a
 * button sounds the same on every screen.
 *
 * <p>The backend can be replaced once, by {@link #setBackend(AudioBackend)}: opening a real line
 * takes hundreds of milliseconds, so the application starts with {@link NullAudio} and the boot
 * screen's warm-up step installs the mixer it opened on the boot thread (D19). The field is
 * {@code volatile} because that hand-over crosses threads, and a manager that has already been
 * closed closes the incoming backend instead of adopting it, so a boot step finishing after a
 * quit can never leave a device open.
 */
public final class AudioManager {

    /** Gain for an ability coming off cooldown: present, but quieter than using it. */
    private static final float ABILITY_READY_GAIN = 0.45f;
    /** Gain for the menu-movement blip, which fires often. */
    private static final float UI_MOVE_GAIN = 0.7f;
    /** Gain for the wind whoosh, a texture rather than a cue. */
    private static final float WIND_GAIN = 0.55f;
    /** Gain for the piston telegraph, which several pistons can raise at once. */
    private static final float PISTON_GAIN = 0.6f;

    private volatile AudioBackend backend;
    private boolean closed;
    private EventBus bus;
    private EventBus.Subscription subscription;
    private double masterVolume = 0.8;
    private double sfxVolume = 1.0;
    private double musicVolume = 0.6;
    private boolean muted;
    private long played;
    private long suppressed;
    private SfxSet sfxSet = SfxSet.FIELDS;
    private Function<String, SfxSet> sfxSetResolver;
    private volatile String musicId;
    private volatile float musicBaseGain;
    private final Map<String, float[]> preparedMusic = new ConcurrentHashMap<>();

    /**
     * Creates a manager over a backend. Volumes start at the {@link Settings} defaults; call
     * {@link #applySettings(Settings)} once the settings file has been read.
     *
     * @param backend the output
     */
    public AudioManager(AudioBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        pushMasterGain();
    }

    /**
     * The backend this manager drives.
     *
     * @return the backend
     */
    public AudioBackend backend() {
        return backend;
    }

    /**
     * Replaces the output, pushing the current fader into it.
     *
     * @param next the new backend; closed immediately when this manager is already closed
     */
    public void setBackend(AudioBackend next) {
        Objects.requireNonNull(next, "next");
        if (closed) {
            next.close();
            return;
        }
        AudioBackend previous = backend;
        backend = next;
        pushMasterGain();
        if (previous != null && previous != next) {
            previous.close();
        }
    }

    /**
     * Starts listening on a bus. Subscribing twice replaces the first registration, so calling
     * this again after a bus swap is safe.
     *
     * @param bus the presentation event bus, driven by the loop thread
     */
    public void attach(EventBus bus) {
        Objects.requireNonNull(bus, "bus");
        detach();
        this.bus = bus;
        this.subscription = bus.subscribe(GameEvent.class, this::handle);
    }

    /** Stops listening. Safe to call when not attached. */
    public void detach() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
        bus = null;
    }

    /**
     * Whether this manager is currently subscribed.
     *
     * @return {@code true} while attached
     */
    public boolean isAttached() {
        return subscription != null;
    }

    /**
     * Reads the volumes and the mute flag out of the settings.
     *
     * @param settings the current settings
     */
    public void applySettings(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        setVolumes(settings.masterVolume, settings.sfxVolume, settings.musicVolume);
        setMuted(settings.muted);
    }

    /**
     * Sets all three volumes at once. Values outside {@code [0, 1]} are clamped. A changed music
     * volume retargets the current loop immediately, so the slider is audible without waiting for
     * the next screen change — and a slide to zero stops the loop, the same silence
     * {@link #startMusic} and {@link #duckMusic} treat volume zero as; raising the volume again
     * re-issues the loop, the way unmuting does.
     *
     * @param master the global fader
     * @param sfx the sound-effect volume
     * @param music the music volume
     */
    public void setVolumes(double master, double sfx, double music) {
        masterVolume = clampVolume(master);
        sfxVolume = clampVolume(sfx);
        double previousMusic = musicVolume;
        musicVolume = clampVolume(music);
        pushMasterGain();
        String id = musicId;
        if (muted || id == null || musicVolume == previousMusic) {
            return;
        }
        if (musicVolume <= 0.0) {
            // Zero is not a retarget: a loop has nowhere to fade to, so it is stopped instead of
            // left sounding at the old gain until the next screen change.
            backend.stopLooping(id);
        } else {
            backend.playLooping(id, (float) (musicBaseGain * musicVolume), 0.0f);
        }
    }

    /**
     * Mutes or unmutes every output. Muting stops every voice, the music included; unmuting
     * re-issues the loop the screens last asked for, so the music resumes where the mix expects
     * it instead of staying silent until the next screen change.
     *
     * @param muted {@code true} to silence the game
     */
    public void setMuted(boolean muted) {
        this.muted = muted;
        pushMasterGain();
        if (muted) {
            backend.stopAll();
        } else {
            String id = musicId;
            if (id != null && musicVolume > 0.0) {
                backend.playLooping(id, (float) (musicBaseGain * musicVolume), 0.0f);
            }
        }
    }

    /**
     * Whether the game is muted.
     *
     * @return {@code true} when muted
     */
    public boolean isMuted() {
        return muted;
    }

    /**
     * The global fader, in {@code [0, 1]}.
     *
     * @return the master volume
     */
    public double masterVolume() {
        return masterVolume;
    }

    /**
     * The sound-effect volume, in {@code [0, 1]}.
     *
     * @return the effect volume
     */
    public double sfxVolume() {
        return sfxVolume;
    }

    /**
     * The music volume, in {@code [0, 1]}. Every music loop plays at its asked-for base gain
     * times this (M8, D19).
     *
     * @return the music volume
     */
    public double musicVolume() {
        return musicVolume;
    }

    /**
     * Sounds actually handed to the backend.
     *
     * @return the count
     */
    public long played() {
        return played;
    }

    /**
     * Sounds skipped because the game is muted or the effect volume is zero.
     *
     * @return the count
     */
    public long suppressed() {
        return suppressed;
    }

    /**
     * Installs the world-to-set mapping (M7): called with the world id of every
     * {@code RunStarted}; a {@code null} answer or a missing resolver keeps the canonical set.
     *
     * @param resolver maps a world id to its sound set, or {@code null} to always use the fields
     */
    public void setSfxSetResolver(Function<String, SfxSet> resolver) {
        this.sfxSetResolver = resolver;
    }

    /**
     * Switches the in-run cues to a world's sound set.
     *
     * @param set the set, or {@code null} for {@link SfxSet#FIELDS}
     */
    public void setSfxSet(SfxSet set) {
        this.sfxSet = set == null ? SfxSet.FIELDS : set;
    }

    /**
     * The sound set the in-run cues play in.
     *
     * @return the set
     */
    public SfxSet sfxSet() {
        return sfxSet;
    }

    /**
     * The bank key a cue would be played under right now (tests): menu cues are never
     * flavoured, everything else carries the world's set.
     *
     * @param id the sound id
     * @return the key
     */
    public String keyFor(String id) {
        if (id == null) {
            return null;
        }
        if (ToneSynth.UI_MOVE.equals(id) || ToneSynth.UI_SELECT.equals(id)
                || ToneSynth.UI_BACK.equals(id)) {
            return id;
        }
        return SoundBank.key(id, sfxSet);
    }

    /**
     * Plays an effect at full gain, centred.
     *
     * @param id the sound id, normally a {@link ToneSynth} constant
     */
    public void playSfx(String id) {
        playSfx(id, 1.0f, 0.0f);
    }

    /**
     * Plays an effect.
     *
     * @param id the sound id
     * @param gain per-cue gain in {@code [0, 1]}, before the effect volume
     * @param pan {@code -1} hard left to {@code +1} hard right
     */
    public void playSfx(String id, float gain, float pan) {
        if (id == null) {
            return;
        }
        if (muted || sfxVolume <= 0.0) {
            suppressed++;
            return;
        }
        played++;
        backend.play(keyFor(id), (float) (gain * sfxVolume), pan);
    }

    /** Menu focus moved. */
    public void uiMove() {
        playSfx(ToneSynth.UI_MOVE, UI_MOVE_GAIN, 0.0f);
    }

    /** Menu item confirmed. */
    public void uiSelect() {
        playSfx(ToneSynth.UI_SELECT, 1.0f, 0.0f);
    }

    /** Menu dismissed or cancelled. */
    public void uiBack() {
        playSfx(ToneSynth.UI_BACK, 1.0f, 0.0f);
    }

    /** Asks the backend to decode or generate every sound ahead of time (boot screen, D19). */
    public void warmUp() {
        backend.warmUp();
    }

    /**
     * Decodes every sound on the calling thread and returns when the bank is warm — what the boot
     * step runs, so the splash's progress bar reflects work that actually happened and the decode
     * never lands between two device writes.
     */
    public void warmUpBlocking() {
        backend.warmUpBlocking();
    }

    /** Stops every voice immediately, for a screen change or a pause. */
    public void stopAll() {
        backend.stopAll();
    }

    // ------------------------------------------------------------------ music (M8, D19)

    /**
     * Hands a rendered loop over to the backend and remembers it, so the screens can ask
     * {@link #hasMusic(String)} before paying for a render. Rendering itself happens in
     * {@code MusicSequencer}, on the calling thread — at boot for the menu loop, at run start for
     * the world loop — never here.
     *
     * @param id the loop id, e.g. {@code music/green_fields}
     * @param samples interleaved stereo samples from {@code MusicSequencer.render}
     */
    public void prepareMusic(String id, float[] samples) {
        if (id == null || samples == null) {
            return;
        }
        preparedMusic.put(id, samples);
        backend.registerLoop(id, samples);
    }

    /**
     * Whether a loop is already prepared.
     *
     * @param id the loop id
     * @return {@code true} when it need not be rendered again
     */
    public boolean hasMusic(String id) {
        return id != null && preparedMusic.containsKey(id);
    }

    /**
     * Starts — or moves to — the looping music under an id. A first call fades the loop in; a
     * call for a <em>different</em> id fades the previous loop out at the same time, which is the
     * whole crossfade (the mixer owns the ramps, the manager only records which loop is wanted).
     * Calling it again for the id already playing just retargets its gain, so a volume change or
     * a re-entered screen never restarts or stutters the music.
     *
     * <p>The loop's gain is the asked-for base gain times {@link #musicVolume()}; the backend's
     * master fader (master volume, mute) applies after that. A muted manager records the request
     * and stays silent; unmuting re-issues the current loop.
     *
     * @param id the loop id, a {@code MusicSequencer} id registered in the bank
     * @param baseGain the requested gain before the music volume
     */
    public void startMusic(String id, float baseGain) {
        if (id == null) {
            return;
        }
        String previous = musicId;
        musicId = id;
        musicBaseGain = Math.max(0.0f, baseGain);
        if (muted || musicVolume <= 0.0) {
            suppressed++;
            return;
        }
        if (previous != null && !previous.equals(id)) {
            backend.stopLooping(previous);
        }
        backend.playLooping(id, (float) (musicBaseGain * musicVolume), 0.0f);
    }

    /**
     * Retargets the current loop's gain — the pause duck and its undo. A no-op when nothing is
     * playing.
     *
     * @param factor the factor on the loop's usual gain, in {@code [0, 1]}
     */
    public void duckMusic(float factor) {
        String id = musicId;
        if (id == null || muted || musicVolume <= 0.0) {
            return;
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, factor));
        backend.playLooping(id, (float) (musicBaseGain * musicVolume * clamped), 0.0f);
    }

    /**
     * Fades the current loop out and forgets it. A no-op when nothing is playing.
     */
    public void stopMusic() {
        String id = musicId;
        musicId = null;
        musicBaseGain = 0.0f;
        if (id != null) {
            backend.stopLooping(id);
        }
    }

    /**
     * The id of the loop the manager has been asked for.
     *
     * @return the id, or {@code null} when no music is wanted
     */
    public String currentMusicId() {
        return musicId;
    }

    /** Detaches from the bus and closes the backend. */
    public void close() {
        detach();
        closed = true;
        backend.close();
    }

    /**
     * Reacts to one event. Public so a test — or a screen with an event it builds by hand — can
     * drive the manager without a bus.
     *
     * @param event the event
     */
    public void handle(GameEvent event) {
        if (event == null) {
            return;
        }
        if (event instanceof GameEvent.SettingsChanged changed) {
            applySettings(changed.settings());
            return;
        }
        if (event instanceof GameEvent.RunStarted started) {
            SfxSet set = sfxSetResolver == null ? null : sfxSetResolver.apply(started.worldId());
            setSfxSet(set);
        }
        String id = sfxIdFor(event);
        if (id != null) {
            playSfx(id, gainFor(event), 0.0f);
        }
    }

    /**
     * The sound an event maps to. A total function over {@link GameEvent}: {@code null} means the
     * event is deliberately silent, not that a case was forgotten.
     *
     * @param event the event
     * @return the sound id, or {@code null} for a silent event
     */
    public static String sfxIdFor(GameEvent event) {
        if (event instanceof GameEvent.Flapped) {
            return ToneSynth.FLAP;
        }
        if (event instanceof GameEvent.GatePassed) {
            return ToneSynth.SCORE;
        }
        if (event instanceof GameEvent.CoinCollected) {
            return ToneSynth.COIN;
        }
        if (event instanceof GameEvent.Crashed) {
            return ToneSynth.CRASH;
        }
        if (event instanceof GameEvent.AbilityActivated
                || event instanceof GameEvent.AbilityReady) {
            return ToneSynth.ABILITY;
        }
        if (event instanceof GameEvent.ShieldAbsorbed) {
            return ToneSynth.SHIELD;
        }
        if (event instanceof GameEvent.Revived) {
            return ToneSynth.REVIVE;
        }
        if (event instanceof GameEvent.StreakChanged streak) {
            // A broken streak is a loss, not an achievement: only the step up is scored.
            return streak.streak() > 0 && streak.step() > 0 ? ToneSynth.STREAK : null;
        }
        if (event instanceof GameEvent.SynergyActivated) {
            return ToneSynth.SYNERGY;
        }
        if (event instanceof GameEvent.RuleShift) {
            return ToneSynth.RULE_SHIFT;
        }
        if (event instanceof GameEvent.LightningWarning) {
            return ToneSynth.LIGHTNING_WARNING;
        }
        if (event instanceof GameEvent.AmbientFlash) {
            return ToneSynth.THUNDER;
        }
        if (event instanceof GameEvent.PistonTelegraph) {
            return ToneSynth.PISTON_TELEGRAPH;
        }
        if (event instanceof GameEvent.WindGust) {
            return ToneSynth.WIND;
        }
        if (event instanceof GameEvent.BossWarning || event instanceof GameEvent.BossStarted) {
            return ToneSynth.BOSS_WARNING;
        }
        if (event instanceof GameEvent.BossCleared) {
            return ToneSynth.BOSS_CLEARED;
        }
        if (event instanceof GameEvent.ObjectiveMet) {
            return ToneSynth.OBJECTIVE_MET;
        }
        if (event instanceof GameEvent.UnlockGranted
                || event instanceof GameEvent.AchievementUnlocked) {
            return ToneSynth.UNLOCK;
        }
        if (event instanceof GameEvent.LevelUp
                || event instanceof GameEvent.ChallengeCompleted) {
            return ToneSynth.LEVEL_UP;
        }
        if (event instanceof GameEvent.RunStarted
                || event instanceof GameEvent.ModifierChosen
                || event instanceof GameEvent.LanguageChanged) {
            return ToneSynth.UI_SELECT;
        }
        // A screen transition and a modifier offer get the soft movement blip, not the confirm
        // one: the button that caused them already played `ui_select` through uiSelect().
        if (event instanceof GameEvent.ScreenChanged
                || event instanceof GameEvent.ModifierOffered) {
            return ToneSynth.UI_MOVE;
        }
        if (event instanceof GameEvent.SaveFailed) {
            return ToneSynth.UI_BACK;
        }
        // Silent on purpose: NearMiss (the gate blip is enough), RunEnded (the crash already
        // played), CurrencyChanged and XpGained (book-keeping that fires in bursts),
        // DailyRecorded, and SettingsChanged (handled before this method is reached).
        return null;
    }

    /**
     * The per-cue gain for an event, before the effect volume.
     *
     * @param event the event
     * @return a gain in {@code [0, 1]}
     */
    public static float gainFor(GameEvent event) {
        if (event instanceof GameEvent.AbilityReady) {
            return ABILITY_READY_GAIN;
        }
        if (event instanceof GameEvent.ModifierOffered
                || event instanceof GameEvent.ScreenChanged) {
            return UI_MOVE_GAIN;
        }
        if (event instanceof GameEvent.WindGust) {
            return WIND_GAIN;
        }
        if (event instanceof GameEvent.PistonTelegraph) {
            return PISTON_GAIN;
        }
        return 1.0f;
    }

    private void pushMasterGain() {
        backend.setMasterGain(muted ? 0.0f : (float) masterVolume);
    }

    private static double clampVolume(double value) {
        return Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
    }
}
