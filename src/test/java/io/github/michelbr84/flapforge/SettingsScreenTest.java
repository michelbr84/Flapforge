package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.Clock;
import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.app.Threads;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.audio.NullAudio;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.persistence.Settings;
import io.github.michelbr84.flapforge.persistence.SettingsStore;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ListView;
import io.github.michelbr84.flapforge.ui.component.Slider;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Toggle;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The settings screen driven headlessly through the queue and the loop, the way
 * {@code MenuNavigationTest} drives the menu: a language switch really re-labels the screen, a
 * rebind travels through {@code rawKeyDowns} into {@code KeyBindings} and is refused when the key
 * is taken, a slider edits the settings and leaves the file behind until the debounce fires, and
 * "restore defaults" puts everything back, and a global hotkey pressed while the screen is open
 * neither loses the player's pending edit nor is lost itself.
 *
 * <p>Every write goes to a {@link TempDir}, so no test can touch the player's real
 * {@code ~/.flapforge/settings.json}, and the render-side globals the screen changes (text scale,
 * smoothing, the particle default) are restored afterwards.
 */
class SettingsScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    @TempDir
    Path home;

    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private GameLoop loop;
    private SettingsStore store;
    private EventBus events;
    private Strings strings;
    private ToastLayer toasts;
    private AudioManager audio;
    private GameContext context;
    private SettingsScreen screen;
    private final List<GameEvent> published = new ArrayList<>();
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        FrameLimiter limiter = FrameLimiter.uncapped(clock);
        loop = new GameLoop(clock, input, screens, presenter, limiter);
        screens.setCloseHandler(loop::stop);
        store = new SettingsStore(new DirectExecutor(), home.resolve("settings.json"));
        store.load();
        events = new EventBus();
        strings = Strings.load("en");
        Strings.use(strings);
        toasts = new ToastLayer();
        audio = new AudioManager(new NullAudio());
        context = new GameContext(LaunchOptions.DEFAULTS, (Clock) clock, () -> 0L, new Threads(),
                input, viewport, screens, presenter, null, loop, limiter, store, events, audio,
                strings, toasts, null, null, null, null);
        audio.attach(events);
        // The same three handlers GameApplication installs, so the hotkeys travel the real path.
        screens.setMuteHandler(context::toggleMute);
        screens.setFullscreenHandler(context::toggleFullscreen);
        screens.setDebugOverlayHandler(context::toggleDebugOverlay);
        screens.setTickTask(context::drainSaveResults);
        events.subscribe(GameEvent.SettingsChanged.class, published::add);
        events.subscribe(GameEvent.LanguageChanged.class, published::add);
        screen = new SettingsScreen(context);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
        Fonts.setTextScale(1.0);
        Accessibility.clear();
        ProceduralArt.setSmoothing(true);
        ParticleSystem.setDefaultReduceFlashing(true);
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
    void switchingTheLanguageRelabelsEveryVisibleControl() {
        ListView language = screen.languageList();
        assertEquals("Back", screen.backButton().text());
        assertEquals("Language", language.label());

        screen.focusRing().focus(language);
        int ptIndex = Settings.LANGUAGES.indexOf("pt_BR");
        for (int i = language.selectedIndex(); i < ptIndex; i++) {
            tap(Keys.RIGHT);
        }

        assertEquals("pt_BR", screen.settings().language);
        assertEquals("pt_BR", strings.language(), "the table itself was reloaded");
        assertEquals("Voltar", screen.backButton().text(), "the label follows the language");
        assertEquals("Idioma", language.label());
        assertEquals("Restaurar padrões", screen.restoreButton().text());
        assertTrue(published.stream().anyMatch(e -> e instanceof GameEvent.LanguageChanged),
                "a LanguageChanged event reached the bus");
        assertTrue(toasts.visibleCount() > 0, "the switch is announced with a toast");
    }

    @Test
    void rebindingFlapThroughRawKeyDownsUpdatesTheBindings() {
        screen.startCapture(InputAction.FLAP);
        assertEquals(InputAction.FLAP, screen.capturingAction());

        tap(Keys.Z);

        assertNull(screen.capturingAction(), "the capture closed");
        assertEquals(List.of(Keys.Z), screen.settings().bindings().keysFor(InputAction.FLAP));
        assertEquals(List.of(Keys.Z), input.bindings().keysFor(InputAction.FLAP),
                "the queue was rebound on the loop thread");
        assertTrue(screen.isDirty(), "the settings file is behind until the debounce fires");

        // The new key really flaps, the old one does not.
        assertFalse(input.bindings().isBound(Keys.SPACE, InputAction.FLAP));
        assertTrue(input.bindings().isBound(Keys.Z, InputAction.FLAP));
    }

    @Test
    void aConflictingRebindIsRefusedWithAToast() {
        List<Integer> before = screen.settings().bindings().keysFor(InputAction.FLAP);
        screen.startCapture(InputAction.FLAP);

        tap(Keys.X); // X is the default ABILITY key

        assertNull(screen.capturingAction());
        assertEquals(before, screen.settings().bindings().keysFor(InputAction.FLAP),
                "the binding is untouched");
        assertFalse(screen.isDirty(), "a refused rebind is not a change");
        assertEquals(1, toasts.visibleCount());
        assertEquals(Toast.Kind.WARNING, toasts.visibleToasts().get(0).kind());
    }

    @Test
    void escapeCancelsAnOpenCapture() {
        List<Integer> before = screen.settings().bindings().keysFor(InputAction.PAUSE);
        screen.startCapture(InputAction.PAUSE);

        tap(Keys.ESCAPE);

        assertNull(screen.capturingAction());
        assertEquals(before, screen.settings().bindings().keysFor(InputAction.PAUSE));
        assertFalse(screen.isDirty());
        assertSame(screen, screens.top(), "cancelling a capture does not leave the screen");
    }

    @Test
    void aSliderChangesTheSettingsAndMarksTheStoreDirtyUntilTheDebounceFires() {
        Slider master = screen.slider("master");
        double before = master.value();
        screen.focusRing().focus(master);

        tap(Keys.RIGHT);

        assertEquals(before + master.step(), master.value(), 1e-9);
        assertEquals(master.value(), screen.settings().masterVolume, 1e-9);
        assertTrue(screen.isDirty());
        assertFalse(Files.exists(store.file()), "nothing is written while the player is dragging");
        assertTrue(published.stream().anyMatch(e -> e instanceof GameEvent.SettingsChanged),
                "the mixer hears about it through the bus");

        ticks(SettingsScreen.SAVE_IDLE_TICKS + 2);

        assertFalse(screen.isDirty(), "the idle debounce flushed the file");
        assertTrue(Files.exists(store.file()));
        assertEquals(master.value(), store.settings().masterVolume, 1e-9);
    }

    @Test
    void togglesReachTheEngineTheyBelongTo() {
        Toggle smoothing = screen.toggle("smoothing");
        Toggle integerScaling = screen.toggle("integerScaling");
        Toggle fillScreen = screen.toggle("fillScreen");
        Toggle showFps = screen.toggle("showFps");
        Toggle reduceFlashing = screen.toggle("reduceFlashing");

        smoothing.setValue(false);
        integerScaling.setValue(true);
        fillScreen.setValue(false);
        showFps.setValue(true);
        reduceFlashing.setValue(false);
        ticks(1);

        assertFalse(ProceduralArt.isSmoothing(), "rendering honours smoothing");
        assertTrue(context.viewport().isIntegerScaling(), "the viewport honours integer scaling");
        assertFalse(context.viewport().isExtendVertical(), "the viewport honours fill screen");
        assertTrue(screens.isDebugOverlayVisible(), "the debug overlay honours showFps");
        assertFalse(ParticleSystem.defaultReduceFlashing(), "particles honour reduce flashing");
        assertTrue(screen.isDirty());
    }

    @Test
    void theAccessibilityRowsReachTheRenderers() {
        Toggle highContrast = screen.toggle("highContrast");
        ListView palette = screen.colorBlindList();
        Toggle holdToFlap = screen.toggle("holdToFlap");

        highContrast.setValue(true);
        palette.select(Settings.COLOR_BLIND_PALETTES.indexOf("deuteranopia"));
        holdToFlap.setValue(true);
        ticks(1);

        assertTrue(Accessibility.isHighContrast(), "the renderer honours high contrast");
        assertEquals("deuteranopia", Accessibility.paletteName(),
                "the renderer honours the colour-blind palette");
        assertTrue(screen.settings().highContrast, "the row marks the stored state");
        assertEquals("deuteranopia", screen.settings().colorBlindPalette);
        assertTrue(screen.settings().holdToFlap);
        assertTrue(screen.isDirty());

        highContrast.setValue(false);
        palette.select(Settings.COLOR_BLIND_PALETTES.indexOf("none"));
        ticks(1);
        assertFalse(Accessibility.isHighContrast());
        assertEquals("none", Accessibility.paletteName());
        assertFalse(screen.settings().highContrast);
    }

    @Test
    void theAccessibilityRowsSurviveASettingsReload() {
        screen.toggle("highContrast").setValue(true);
        screen.colorBlindList().select(Settings.COLOR_BLIND_PALETTES.indexOf("tritanopia"));
        screen.toggle("holdToFlap").setValue(true);
        ticks(1);
        assertTrue(screen.isDirty());

        screens.pop();
        ticks(2);

        SettingsStore reopened = new SettingsStore(new DirectExecutor(),
                home.resolve("settings.json"));
        Settings reloaded = reopened.load().settings();
        assertTrue(reloaded.highContrast, "high contrast survives a restart");
        assertEquals("tritanopia", reloaded.colorBlindPalette);
        assertTrue(reloaded.holdToFlap);
    }

    @Test
    void theFrameRateCapReachesTheLimiterIncludingItsTwoSymbolicValues() {
        ListView fps = screen.maxFpsList();
        fps.select(SettingsScreen.FPS_OPTIONS.indexOf(144));
        ticks(1);
        assertEquals(144, context.limiter().targetFps());

        fps.select(SettingsScreen.FPS_OPTIONS.indexOf(Settings.MAX_FPS_UNCAPPED));
        ticks(1);
        assertEquals(FrameLimiter.UNCAPPED, context.limiter().targetFps());

        fps.select(SettingsScreen.FPS_OPTIONS.indexOf(Settings.MAX_FPS_MATCH_REFRESH));
        ticks(1);
        assertTrue(context.limiter().targetFps() >= FrameLimiter.MIN_FPS,
                "match-refresh resolves to a real rate");
        assertEquals(Settings.MAX_FPS_MATCH_REFRESH, screen.settings().maxFps,
                "the stored value stays symbolic");
    }

    @Test
    void textScaleReachesTheFonts() {
        Slider textScale = screen.slider("textScale");
        textScale.setValue(Settings.MAX_TEXT_SCALE);
        ticks(1);

        assertEquals(Settings.MAX_TEXT_SCALE, Fonts.textScale(), 1e-9);
        assertEquals(30, Fonts.regular(20).getSize());
    }

    @Test
    void restoreDefaultsPutsEverythingBackAndWritesIt() {
        screen.slider("master").setValue(0.1);
        screen.toggle("holdToFlap").setValue(true);
        screen.startCapture(InputAction.MUTE);
        tap(Keys.Z);
        ticks(1);
        assertNotEquals(Settings.defaults().masterVolume, screen.settings().masterVolume);

        screen.restoreButton().activate();
        ticks(1);

        Settings defaults = Settings.defaults().normalize();
        assertEquals(defaults.masterVolume, screen.settings().masterVolume, 1e-9);
        assertFalse(screen.settings().holdToFlap);
        assertEquals(defaults.keyBindings, screen.settings().keyBindings);
        assertEquals(defaults.bindings().keysFor(InputAction.MUTE),
                input.bindings().keysFor(InputAction.MUTE), "the queue was rebound too");
        assertFalse(screen.isDirty(), "a reset writes immediately");
        assertTrue(Files.exists(store.file()));
        assertEquals(defaults.masterVolume, store.settings().masterVolume, 1e-9);
    }

    @Test
    void leavingTheScreenFlushesAndTheChangeSurvivesAReload() {
        screen.slider("music").setValue(0.25);
        ticks(1);
        assertTrue(screen.isDirty());

        screens.pop();
        ticks(2);

        assertFalse(screen.isDirty(), "onExit flushed the pending write");
        SettingsStore reopened = new SettingsStore(new DirectExecutor(),
                home.resolve("settings.json"));
        assertEquals(0.25, reopened.load().settings().musicVolume, 1e-9,
                "the setting survives a restart");
    }

    @Test
    void theFullscreenAndOverlayHotkeysArePersistedLikeAnyOtherSetting() {
        assertFalse(store.settings().fullscreen);
        assertFalse(store.settings().showFps);

        tap(Keys.F11);
        ticks(1);
        assertTrue(screens.isFullscreen(), "F11 reached the presenter");
        assertTrue(store.settings().fullscreen, "and the setting followed it");
        assertTrue(screen.toggle("fullscreen").value(), "the row shows the new state");

        tap(Keys.F3);
        ticks(1);
        assertTrue(screens.isDebugOverlayVisible(), "F3 reached the overlay");
        assertTrue(store.settings().showFps, "and the setting followed it");
        assertTrue(screen.toggle("showFps").value(), "the row shows the new state");

        // The decisive part: applying the settings again must not undo either of them.
        context.applySettings(store.settings());
        assertTrue(screens.isFullscreen(), "applySettings agrees with the presenter");
        assertTrue(screens.isDebugOverlayVisible());

        tap(Keys.F11);
        ticks(1);
        assertFalse(screens.isFullscreen(), "a second press leaves fullscreen");
        assertFalse(store.settings().fullscreen);
    }

    @Test
    void aHotkeyPressedWhileEditingKeepsBothChanges() {
        Slider master = screen.slider("master");
        screen.focusRing().focus(master);
        tap(Keys.LEFT);
        double edited = master.value();
        assertNotEquals(Settings.defaults().masterVolume, edited, "the slider really moved");
        assertTrue(screen.isDirty(), "the write is still pending");

        tap(Keys.M);
        ticks(1);

        assertTrue(audio.isMuted(), "the hotkey took effect");
        assertTrue(screen.settings().muted, "and the screen adopted it");
        assertEquals(edited, screen.settings().masterVolume, 1e-9,
                "the pending slider edit must survive the hotkey");

        ticks(SettingsScreen.SAVE_IDLE_TICKS + 2);

        assertFalse(screen.isDirty());
        assertTrue(store.settings().muted, "the flush must not resurrect the pre-hotkey copy");
        assertEquals(edited, store.settings().masterVolume, 1e-9);
        assertTrue(audio.isMuted(), "and the mixer is still muted");
    }

    @Test
    void arrowsMoveBetweenTheScrolledRowsAndTheFixedFooter() {
        assertSame(screen.languageList(), screen.focusRing().focused());
        assertEquals(0.0, screen.scroll(), 1e-9);

        for (int i = 0; i < 30; i++) {
            tap(Keys.DOWN);
        }

        assertTrue(screen.scroll() > 0, "the focused row scrolled into view");
        assertTrue(screen.backButton().isFocused() || screen.restoreButton().isFocused(),
                "the footer is reachable with the keyboard");

        tap(Keys.UP);
        assertFalse(screen.backButton().isFocused());
        assertFalse(screen.restoreButton().isFocused());
    }

    @Test
    void everyRowIsFocusableAndLabelled() {
        assertEquals(SettingsScreen.REBINDABLE.size(), 7);
        for (InputAction action : SettingsScreen.REBINDABLE) {
            assertFalse(screen.rebindButton(action).text().isBlank(),
                    action + " has no label");
        }
        assertEquals(strings.get(StringKey.SETTINGS_RESTORE_DEFAULTS),
                screen.restoreButton().text());
        assertTrue(screen.rows().size() >= 18, "found " + screen.rows().size() + " rows");
    }
}
