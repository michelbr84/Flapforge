package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.persistence.AtomicFiles;
import io.github.michelbr84.flapforge.persistence.Settings;
import io.github.michelbr84.flapforge.persistence.SettingsStore;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import java.util.Locale;
import java.util.Objects;

/**
 * Shared services handed to screens and subsystems (the M0 set plus the M2 presentation services;
 * later milestones add the player profile).
 *
 * <p>It is also the one place that knows <em>how</em> a setting reaches the running game:
 * {@link #applySettings(Settings)} pushes each field to the object that owns that behaviour —
 * the viewport, the frame limiter, the presenter, the input queue, the fonts, the procedural art
 * and the particle pools — and publishes {@code SettingsChanged} for the subsystems that subscribe
 * instead. The audio manager is one of those: it reads the three volumes and the mute flag off
 * that event, so nothing here has to know what a volume does. Screens therefore never reach into
 * the engine themselves; they edit a {@link Settings} and hand it here.
 *
 * <p>That also makes this the only owner of the three global hotkeys that change a setting:
 * {@code M} (mute), {@code F11} (fullscreen) and {@code F3} (the debug overlay).
 * {@link ScreenManager} owns the <em>keys</em>, because they have to work on every screen, but it
 * runs the handlers installed from here, so the engine state and the stored setting can never
 * disagree: §4 persists {@code fullscreen} and {@code showFps}, and a hotkey that only flipped the
 * presenter would be silently undone by the next {@link #applySettings(Settings)}.
 *
 * @param options the parsed launch options
 * @param clock the monotonic clock
 * @param timeSource the wall clock for the pure packages
 * @param threads the thread owner
 * @param input the input queue
 * @param viewport the loop-owned viewport
 * @param screens the screen stack
 * @param presenter the frame presenter
 * @param window the window, or {@code null} when headless
 * @param loop the game loop
 * @param limiter the frame pacer, or {@code null} when the launch does not pace
 * @param settingsStore the settings file, or {@code null} in a context without persistence
 * @param events the presentation event bus
 * @param audio the audio manager, or {@code null} in a context without sound
 * @param strings the active string table
 * @param toasts the shared toast queue
 * @param content the loaded game content, or {@code null} when a context has none
 */
public record GameContext(LaunchOptions options, Clock clock, TimeSource timeSource,
        Threads threads, InputQueue input, Viewport viewport, ScreenManager screens,
        FramePresenter presenter, GameWindow window, GameLoop loop, FrameLimiter limiter,
        SettingsStore settingsStore, EventBus events, AudioManager audio, Strings strings,
        ToastLayer toasts, GameContent content) {

    /**
     * Whether the application runs without a window.
     *
     * @return {@code true} when headless
     */
    public boolean isHeadless() {
        return window == null;
    }

    /** Asks the application to quit cleanly. */
    public void requestQuit() {
        screens.requestClose();
    }

    /**
     * The settings in force.
     *
     * @return the live settings, or fresh defaults when the context has no store
     */
    public Settings settings() {
        return settingsStore == null ? Settings.defaults() : settingsStore.settings();
    }

    /**
     * Pushes every setting into the running engine and announces the change.
     *
     * <p>Ordering matters in one place only: the language is applied first, so a subscriber
     * reacting to {@code SettingsChanged} already sees the new strings.
     *
     * @param settings the state to apply (normalised by the caller or by the store)
     */
    public void applySettings(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        // The store is the single answer to "what is in force right now": adopting the state here
        // (without writing it) keeps a live edit that is still waiting for its debounced write
        // from being reverted by a hotkey that builds on settings().
        if (settingsStore != null) {
            settingsStore.hold(settings);
        }
        applyLanguage(settings);
        if (viewport != null) {
            viewport.setIntegerScaling(settings.integerScaling);
        }
        if (limiter != null) {
            limiter.setTargetFps(resolveFps(settings.maxFps));
        }
        if (input != null) {
            // The loop thread owns the queue's bindings; every caller of applySettings is on it.
            input.setBindings(settings.bindings());
        }
        Fonts.setTextScale(settings.textScale);
        ProceduralArt.setSmoothing(settings.smoothing);
        ParticleSystem.setDefaultReduceFlashing(settings.reduceFlashing);
        if (screens != null) {
            screens.setDebugOverlayVisible(settings.showFps);
            // The presenter, not the window, is the source of truth here: a null presenter has no
            // state to change and a NullPresenter simply records the request.
            if (screens.isFullscreen() != settings.fullscreen) {
                screens.setFullscreen(settings.fullscreen);
            }
        }
        publish(new GameEvent.SettingsChanged(settings));
    }

    /**
     * Reloads the string table when {@code settings.language} resolves to another language, and
     * announces it so every screen can refresh its labels.
     *
     * @param settings the state being applied
     * @return {@code true} when the language actually changed
     */
    public boolean applyLanguage(Settings settings) {
        if (strings == null) {
            return false;
        }
        String resolved = settings.resolvedLanguage(systemLanguage());
        if (resolved.equals(strings.language())) {
            return false;
        }
        strings.reload(resolved);
        Strings.use(strings);
        publish(new GameEvent.LanguageChanged(resolved));
        return true;
    }

    /**
     * Applies a state and writes it to {@code settings.json} through the store (D15: the JSON is
     * built here, the file write happens on the store's executor).
     *
     * @param settings the state to apply and persist
     */
    public void applyAndSave(Settings settings) {
        applySettings(settings);
        saveSettings(settings);
    }

    /**
     * Writes a state to {@code settings.json} without applying it, and turns a failed write into
     * a toast and a {@code SaveFailed} event.
     *
     * @param settings the state to persist
     */
    public void saveSettings(Settings settings) {
        if (settingsStore == null) {
            return;
        }
        settingsStore.save(settings);
        // With the real save executor the write has not run yet, so nothing is reported here;
        // with an inline executor (tests, tools) the result is already waiting. Either way the
        // loop drains the queue every tick, so each failure is reported once, for its own write.
        drainSaveResults();
    }

    /**
     * Turns every write that finished since the last call into a {@code SaveFailed} event and a
     * warning toast (D15). Runs on the loop thread — {@code ScreenManager} calls it once per tick
     * — because that is where the bus and the toast layer live.
     */
    public void drainSaveResults() {
        if (settingsStore == null) {
            return;
        }
        AtomicFiles.WriteResult write;
        while ((write = settingsStore.pollCompletedWrite()) != null) {
            if (write.ok()) {
                continue;
            }
            publish(new GameEvent.SaveFailed(String.valueOf(write.target()), write.detail()));
            if (toasts != null && strings != null) {
                toasts.push(strings.format(StringKey.TOAST_SAVE_FAILED, write.detail()),
                        Toast.Kind.WARNING);
            }
        }
    }

    /**
     * Flips the mute flag, applies it and persists it — what the {@code M} key does on every
     * screen. The audio manager hears the resulting {@code SettingsChanged} like any other
     * change; the toast tells the player what just happened, because muting is otherwise
     * invisible.
     *
     * @return the new mute state
     */
    public boolean toggleMute() {
        Settings next = settings().copy();
        next.muted = !next.muted;
        applyAndSave(next);
        if (toasts != null && strings != null) {
            toasts.push(strings.get(next.muted ? StringKey.TOAST_MUTED
                    : StringKey.TOAST_UNMUTED));
        }
        return next.muted;
    }

    /**
     * Flips {@code settings.fullscreen}, applies it and persists it — what {@code F11} does on
     * every screen. The new state is read from the presenter rather than from the stored value,
     * because the presenter owns the window and a launch may have started fullscreen.
     *
     * @return the new fullscreen state
     */
    public boolean toggleFullscreen() {
        Settings next = settings().copy();
        next.fullscreen = screens != null ? !screens.isFullscreen() : !next.fullscreen;
        applyAndSave(next);
        return next.fullscreen;
    }

    /**
     * Flips {@code settings.showFps}, applies it and persists it — what {@code F3} does on every
     * screen.
     *
     * @return the new overlay state
     */
    public boolean toggleDebugOverlay() {
        Settings next = settings().copy();
        next.showFps = screens != null ? !screens.isDebugOverlayVisible() : !next.showFps;
        applyAndSave(next);
        return next.showFps;
    }

    /**
     * Publishes an event when the context has a bus.
     *
     * @param event the event
     */
    public void publish(GameEvent event) {
        if (events != null) {
            events.publish(event);
        }
    }

    /**
     * Resolves a stored frame-rate cap into a target the {@link FrameLimiter} understands.
     *
     * @param maxFps the stored value ({@link Settings#MAX_FPS_UNCAPPED},
     *     {@link Settings#MAX_FPS_MATCH_REFRESH} or a rate)
     * @return the limiter target
     */
    public static int resolveFps(int maxFps) {
        if (maxFps == Settings.MAX_FPS_UNCAPPED) {
            return FrameLimiter.UNCAPPED;
        }
        if (maxFps == Settings.MAX_FPS_MATCH_REFRESH) {
            return GameApplication.detectRefreshRate();
        }
        return FrameLimiter.clampFps(maxFps);
    }

    /**
     * The two-letter language of the default locale, which is what {@code language: auto}
     * resolves against (D25).
     *
     * @return the language tag
     */
    public static String systemLanguage() {
        return Locale.getDefault().getLanguage();
    }
}
