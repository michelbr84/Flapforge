package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.persistence.Settings;
import io.github.michelbr84.flapforge.persistence.SettingsStore;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.Adjustable;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.Label;
import io.github.michelbr84.flapforge.ui.component.ListView;
import io.github.michelbr84.flapforge.ui.component.Slider;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.Toggle;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The real settings screen (M2, D17, D25): language, volumes, display, accessibility and key
 * bindings, applied live and persisted through {@link SettingsStore}.
 *
 * <h2>How a change travels</h2>
 * Every control edits one field of a working {@link Settings} copy and then calls
 * {@link GameContext#applySettings(Settings)}, which is the single place that knows which engine
 * object owns which behaviour. The file write is deliberately <em>not</em> immediate: the screen
 * marks itself dirty and flushes after {@value #SAVE_IDLE_TICKS} idle ticks (or when it is left),
 * so dragging a volume slider produces one write instead of thirty, and no write ever happens on
 * a frame that is also running a simulation (D15).
 *
 * <h2>Changes from outside</h2>
 * The three global hotkeys ({@code M}, {@code F11}, {@code F3}) change a setting while this screen
 * is open. The screen therefore subscribes to {@code SettingsChanged} and re-reads its working
 * copy from any change it did not raise itself; without that, the pending debounced write would
 * flush a copy taken before the hotkey and silently undo it.
 *
 * <h2>Key rebinding (E29)</h2>
 * A rebind row opens a capture: the next key that appears in {@link InputFrame#rawKeyDowns()} is
 * taken, {@code Esc} cancels, and a key already held by another rebindable action is refused with
 * a toast rather than silently stealing it. The new bindings reach {@code InputQueue} on the loop
 * thread — this {@code tick} — so held keys keep their edges across the change.
 *
 * <h2>Layout</h2>
 * The rows do not fit on a 420x640 playfield, so they live in a content space that is scrolled
 * under a clip: rendering translates by the scroll offset and input is mapped into the same
 * space before it reaches the {@link FocusRing}, which keeps hit tests and focus honest. Moving
 * focus scrolls the focused row into view; the wheel scrolls freely.
 */
public final class SettingsScreen implements Screen {

    /** Idle ticks after the last edit before the settings file is written. */
    public static final int SAVE_IDLE_TICKS = 45;
    /** Top of the scrolling area. */
    public static final int VIEW_TOP = 78;
    /** Bottom of the scrolling area: the fixed footer bar starts below it. */
    public static final int VIEW_BOTTOM = Playfield.HEIGHT - 62;
    /** Top of the fixed footer bar that holds "restore defaults" and "back". */
    public static final int FOOTER_TOP = Playfield.HEIGHT - 58;
    /** Height of the footer buttons. */
    public static final int FOOTER_BUTTON_H = 42;
    /** Height of one settings row. */
    public static final int ROW_H = 30;
    /** Gap between two rows. */
    public static final int ROW_GAP = 6;
    /** Height of a section header. */
    public static final int HEADER_H = 28;
    /** Left edge of the content. */
    public static final int CONTENT_X = 26;
    /** Width of the content. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * CONTENT_X;
    /** Ticks the wheel scrolls per notch. */
    public static final int WHEEL_STEP = 28;
    /** Left edge of the panel drawn behind the rows. */
    public static final int PANEL_X = 12;
    /** Padding between the panel edge and the scrolling band. */
    public static final int PANEL_PAD = 8;

    /**
     * The actions a player may rebind; the rest (arrows, back) are fixed. It is exactly the set
     * {@code settings.json} carries, so a rebound key is always a key that was written.
     */
    public static final List<InputAction> REBINDABLE = KeyBindings.PERSISTED;

    /** The frame-rate caps the list offers, in order. */
    public static final List<Integer> FPS_OPTIONS = List.of(60, 120, 144,
            Settings.MAX_FPS_UNCAPPED, Settings.MAX_FPS_MATCH_REFRESH);

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 52;
    /** Band kept clear of toasts so they never cover the (much wider, translated) title. */
    private static final int TOAST_TOP_INSET = 64;
    /** Where the pointer is reported to be while it is outside the scrolling band. */
    private static final double OFF_SCREEN = -10_000;
    private static final Color SCROLLBAR = new Color(0xF4, 0xF8, 0xF8, 0x50);
    private static final Color CAPTURE_DIM = new Color(0, 0, 0, 0xA0);
    private static final int CAPTURE_PANEL_H = 110;

    private final ScreenManager screens;
    private final GameContext context;
    private final SettingsStore store;
    private final ToastLayer toasts;
    private final ParticleSystem particles;
    private final FocusRing ring = new FocusRing();
    private final FocusRing footerRing = new FocusRing();
    private final List<UiNode> rows = new ArrayList<>();
    private final List<Label> headers = new ArrayList<>();
    private final Map<StringKey, Label> headerByKey = new LinkedHashMap<>();
    private final Map<InputAction, Button> rebinds = new EnumMap<>(InputAction.class);
    private final Map<String, Slider> sliders = new LinkedHashMap<>();
    private final Map<String, Toggle> toggles = new LinkedHashMap<>();

    private Strings strings;
    private Settings working;
    private ListView languageList;
    private ListView fpsList;
    private ListView colorBlindList;
    private Button restore;
    private Button back;
    private String shownLanguage;
    private InputAction capturing;
    private double scroll;
    private double contentHeight;
    private UiNode lastRow;
    private boolean footerActive;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private double contentMouseX = OFF_SCREEN;
    private double contentMouseY = OFF_SCREEN;
    private boolean pointerActive;
    private boolean dirty;
    private boolean applying;
    private EventBus.Subscription settingsSubscription;
    private int idleTicks;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services
     */
    public SettingsScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context").screens(), context);
    }

    /**
     * Creates a stand-alone screen (tests, tools): the edits apply to the process but are written
     * to a store that discards them, so no test can touch the player's real settings file.
     *
     * @param screens the screen stack
     */
    public SettingsScreen(ScreenManager screens) {
        this(screens, null);
    }

    private SettingsScreen(ScreenManager screens, GameContext context) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.context = context;
        this.store = context != null && context.settingsStore() != null
                ? context.settingsStore() : new SettingsStore(task -> { });
        this.toasts = context != null && context.toasts() != null
                ? context.toasts() : new ToastLayer();
        this.strings = context != null && context.strings() != null
                ? context.strings() : Strings.active();
        this.particles = new ParticleSystem();
        this.working = store.settings().copy().normalize();
        this.shownLanguage = strings.language();
        build();
    }

    // ------------------------------------------------------------------ building

    private void build() {
        double y = 0;
        y = addRow(languageRow(), y);
        y = addHeader(StringKey.SETTINGS_SECTION_AUDIO, y);
        y = addRow(volumeSlider("master", StringKey.SETTINGS_MASTER_VOLUME, working.masterVolume,
                v -> working.masterVolume = v), y);
        y = addRow(volumeSlider("sfx", StringKey.SETTINGS_SFX_VOLUME, working.sfxVolume,
                v -> working.sfxVolume = v), y);
        y = addRow(volumeSlider("music", StringKey.SETTINGS_MUSIC_VOLUME, working.musicVolume,
                v -> working.musicVolume = v), y);
        y = addRow(toggle("muted", StringKey.SETTINGS_MUTED, working.muted,
                v -> working.muted = v), y);

        y = addHeader(StringKey.SETTINGS_SECTION_DISPLAY, y);
        y = addRow(toggle("fullscreen", StringKey.SETTINGS_FULLSCREEN, working.fullscreen,
                v -> working.fullscreen = v), y);
        y = addRow(toggle("integerScaling", StringKey.SETTINGS_INTEGER_SCALING,
                working.integerScaling, v -> working.integerScaling = v), y);
        y = addRow(fpsRow(), y);
        y = addRow(toggle("smoothing", StringKey.SETTINGS_SMOOTHING, working.smoothing,
                v -> working.smoothing = v), y);
        y = addRow(toggle("showFps", StringKey.SETTINGS_SHOW_FPS, working.showFps,
                v -> working.showFps = v), y);

        y = addHeader(StringKey.SETTINGS_SECTION_GAME, y);
        y = addRow(toggle("highContrast", StringKey.SETTINGS_HIGH_CONTRAST, working.highContrast,
                v -> working.highContrast = v), y);
        y = addRow(colourBlindRow(), y);
        y = addRow(toggle("reduceFlashing", StringKey.SETTINGS_REDUCE_FLASHING,
                working.reduceFlashing, v -> working.reduceFlashing = v), y);
        y = addRow(textScaleRow(), y);
        y = addRow(toggle("holdToFlap", StringKey.SETTINGS_HOLD_TO_FLAP, working.holdToFlap,
                v -> working.holdToFlap = v), y);

        y = addHeader(StringKey.SETTINGS_KEY_BINDINGS, y);
        for (InputAction action : REBINDABLE) {
            Button button = new Button("", null);
            button.setFontSize(14);
            button.setOnAction(() -> startCapture(action));
            rebinds.put(action, button);
            y = addRow(button, y);
        }

        contentHeight = y;
        lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);

        // The two actions a player must always be able to reach live in a fixed footer bar
        // rather than at the end of a long scroll, so Back is one click away from anywhere.
        int backWidth = 150;
        int restoreWidth = CONTENT_W - backWidth - 8;
        restore = new Button("", this::restoreDefaults);
        restore.setFontSize(16);
        restore.setBounds(CONTENT_X, FOOTER_TOP, restoreWidth, FOOTER_BUTTON_H);
        footerRing.add(restore);
        back = new Button("", screens::pop);
        back.setFontSize(16);
        back.setBounds(CONTENT_X + restoreWidth + 8, FOOTER_TOP, backWidth, FOOTER_BUTTON_H);
        footerRing.add(back);
        refreshTexts();
    }

    private ListView languageRow() {
        languageList = new ListView(strings.get(StringKey.SETTINGS_LANGUAGE), languageOptions(),
                Math.max(0, Settings.LANGUAGES.indexOf(working.language)));
        languageList.setOnChange(index -> {
            working.language = Settings.LANGUAGES.get(index);
            apply();
            toasts.push(strings.format(StringKey.TOAST_LANGUAGE_CHANGED,
                    languageName(strings.language())));
        });
        return languageList;
    }

    private ListView fpsRow() {
        fpsList = new ListView(strings.get(StringKey.SETTINGS_MAX_FPS), fpsOptions(),
                Math.max(0, FPS_OPTIONS.indexOf(working.maxFps)));
        fpsList.setOnChange(index -> {
            working.maxFps = FPS_OPTIONS.get(index);
            apply();
        });
        return fpsList;
    }

    /**
     * The colour-blind palette row (M8): one entry per name {@link Settings} accepts, labelled
     * through the string table so both languages carry it.
     *
     * @return the list
     */
    private ListView colourBlindRow() {
        colorBlindList = new ListView(strings.get(StringKey.SETTINGS_COLOR_BLIND_PALETTE),
                colourBlindOptions(),
                Math.max(0, Settings.COLOR_BLIND_PALETTES.indexOf(working.colorBlindPalette)));
        colorBlindList.setOnChange(index -> {
            working.colorBlindPalette = Settings.COLOR_BLIND_PALETTES.get(index);
            apply();
        });
        return colorBlindList;
    }

    private List<String> colourBlindOptions() {
        List<String> out = new ArrayList<>(Settings.COLOR_BLIND_PALETTES.size());
        for (String palette : Settings.COLOR_BLIND_PALETTES) {
            out.add(colourBlindName(palette));
        }
        return out;
    }

    private String colourBlindName(String palette) {
        switch (palette) {
            case "protanopia":
                return strings.get(StringKey.SETTINGS_COLOR_BLIND_PROTANOPIA);
            case "deuteranopia":
                return strings.get(StringKey.SETTINGS_COLOR_BLIND_DEUTERANOPIA);
            case "tritanopia":
                return strings.get(StringKey.SETTINGS_COLOR_BLIND_TRITANOPIA);
            default:
                return strings.get(StringKey.SETTINGS_COLOR_BLIND_NONE);
        }
    }

    private Slider textScaleRow() {
        Slider slider = new Slider(strings.get(StringKey.SETTINGS_TEXT_SCALE),
                Settings.MIN_TEXT_SCALE, Settings.MAX_TEXT_SCALE, 0.05, working.textScale);
        slider.setValueText(this::percent);
        slider.setOnChange(v -> {
            working.textScale = v;
            apply();
        });
        sliders.put("textScale", slider);
        return slider;
    }

    private Slider volumeSlider(String id, StringKey label, double value,
            java.util.function.DoubleConsumer setter) {
        Slider slider = new Slider(strings.get(label), value);
        slider.setValueText(this::percent);
        slider.setOnChange(v -> {
            setter.accept(v);
            apply();
        });
        sliders.put(id, slider);
        return slider;
    }

    private Toggle toggle(String id, StringKey label, boolean value,
            java.util.function.Consumer<Boolean> setter) {
        Toggle toggle = new Toggle(strings.get(label), value);
        toggle.setOnChange(v -> {
            setter.accept(v);
            apply();
            particles.emitUiSparkle(toggle.x() + toggle.width() - Toggle.PILL_WIDTH / 2.0,
                    toggle.centerY() + VIEW_TOP - scroll, PALETTE.accent());
        });
        toggles.put(id, toggle);
        return toggle;
    }

    private double addHeader(StringKey key, double y) {
        Label label = new Label("");
        label.setFont(Fonts.bold(15));
        label.setColor(ProceduralArt.accentColor(PALETTE));
        label.setBounds(CONTENT_X, y + ROW_GAP, CONTENT_W, HEADER_H);
        headers.add(label);
        headerByKey.put(key, label);
        return y + HEADER_H + ROW_GAP;
    }

    private double addRow(UiNode node, double y) {
        return addRow(node, y, ROW_H);
    }

    private double addRow(UiNode node, double y, double height) {
        node.setBounds(CONTENT_X, y, CONTENT_W, height);
        rows.add(node);
        ring.add(node);
        return y + height + ROW_GAP;
    }

    private List<String> languageOptions() {
        List<String> out = new ArrayList<>(Settings.LANGUAGES.size());
        for (String language : Settings.LANGUAGES) {
            out.add(Settings.LANGUAGE_AUTO.equals(language)
                    ? strings.format(StringKey.SETTINGS_LANGUAGE_AUTO,
                            languageName(working.resolvedLanguage(GameContext.systemLanguage())))
                    : languageName(language));
        }
        return out;
    }

    private List<String> fpsOptions() {
        List<String> out = new ArrayList<>(FPS_OPTIONS.size());
        for (int fps : FPS_OPTIONS) {
            if (fps == Settings.MAX_FPS_UNCAPPED) {
                out.add(strings.get(StringKey.SETTINGS_MAX_FPS_UNCAPPED));
            } else if (fps == Settings.MAX_FPS_MATCH_REFRESH) {
                out.add(strings.get(StringKey.SETTINGS_MAX_FPS_MATCH_REFRESH));
            } else {
                out.add(strings.format(StringKey.SETTINGS_MAX_FPS_VALUE, fps));
            }
        }
        return out;
    }

    private String languageName(String language) {
        return "pt_BR".equals(language) ? strings.get(StringKey.LANGUAGE_PT_BR)
                : strings.get(StringKey.LANGUAGE_EN);
    }

    private String percent(double value) {
        return strings.format(StringKey.COMMON_PERCENT, Math.round(value * 100));
    }

    // ------------------------------------------------------------------ accessors

    /**
     * The Back button.
     *
     * @return the button
     */
    public Button backButton() {
        return back;
    }

    /**
     * The "restore defaults" button.
     *
     * @return the button
     */
    public Button restoreButton() {
        return restore;
    }

    /**
     * The language row.
     *
     * @return the list
     */
    public ListView languageList() {
        return languageList;
    }

    /**
     * The frame-rate cap row.
     *
     * @return the list
     */
    public ListView maxFpsList() {
        return fpsList;
    }

    /**
     * The colour-blind palette row (M8).
     *
     * @return the list
     */
    public ListView colorBlindList() {
        return colorBlindList;
    }

    /**
     * A slider by id ({@code master}, {@code sfx}, {@code music}, {@code textScale}).
     *
     * @param id the id
     * @return the slider, or {@code null} when the id is unknown
     */
    public Slider slider(String id) {
        return sliders.get(id);
    }

    /**
     * A toggle by the settings field it edits ({@code muted}, {@code fullscreen},
     * {@code integerScaling}, {@code smoothing}, {@code showFps}, {@code reduceFlashing},
     * {@code holdToFlap}).
     *
     * @param id the id
     * @return the toggle, or {@code null} when the id is unknown
     */
    public Toggle toggle(String id) {
        return toggles.get(id);
    }

    /**
     * The rebind button of an action.
     *
     * @param action the action
     * @return the button, or {@code null} when the action is not rebindable
     */
    public Button rebindButton(InputAction action) {
        return rebinds.get(action);
    }

    /**
     * The state being edited (the screen's own copy, not the store's).
     *
     * @return the working settings
     */
    public Settings settings() {
        return working;
    }

    /**
     * Whether an edit is waiting to be written.
     *
     * @return {@code true} when the settings file is behind the working state
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * The action whose key is being captured.
     *
     * @return the action, or {@code null} when no capture is open
     */
    public InputAction capturingAction() {
        return capturing;
    }

    /**
     * The focus ring (tests inspecting focus).
     *
     * @return the ring
     */
    public FocusRing focusRing() {
        return ring;
    }

    /**
     * The rows in display order.
     *
     * @return an unmodifiable view
     */
    public List<UiNode> rows() {
        return List.copyOf(rows);
    }

    /**
     * Current scroll offset of the content.
     *
     * @return logical pixels from the top of the content
     */
    public double scroll() {
        return scroll;
    }

    /**
     * Where a scrolled row currently sits on screen — what a click has to aim at, which is not
     * the row's own {@code y} because the content is scrolled under a clip.
     *
     * @param node a row returned by {@link #rows()}
     * @return the row's centre in playfield coordinates
     */
    public double screenY(UiNode node) {
        Objects.requireNonNull(node, "node");
        return node.centerY() + VIEW_TOP - scroll;
    }

    /**
     * Whether a row is inside the visible band right now.
     *
     * @param node a row returned by {@link #rows()}
     * @return {@code true} when the row can be clicked
     */
    public boolean isRowVisible(UiNode node) {
        double y = screenY(node);
        return y - node.height() / 2 >= VIEW_TOP && y + node.height() / 2 <= VIEW_BOTTOM;
    }

    /**
     * Scrolls a row into view and gives it focus (used by the tests that click it).
     *
     * @param node a row returned by {@link #rows()}
     */
    public void focusRow(UiNode node) {
        ring.focus(node);
        footerActive = false;
        scrollFocusIntoView();
    }

    // ------------------------------------------------------------------ behaviour

    /**
     * Opens a key capture for an action; the next key press is taken as its binding (E29).
     *
     * @param action the action to rebind
     */
    public void startCapture(InputAction action) {
        if (REBINDABLE.contains(action)) {
            capturing = action;
        }
    }

    /** Closes an open capture without changing anything. */
    public void cancelCapture() {
        capturing = null;
    }

    /** Applies the working state to the engine and marks the settings file as behind. */
    private void apply() {
        applying = true;
        try {
            if (context != null) {
                context.applySettings(working);
            } else {
                applyStandalone();
            }
        } finally {
            applying = false;
        }
        particles.setReduceFlashing(working.reduceFlashing);
        markDirty();
    }

    /**
     * Adopts a change this screen did not make — a global hotkey, or anything else that reaches
     * {@code GameContext.applySettings} while the screen is open.
     *
     * @param event the change
     */
    private void onSettingsChanged(GameEvent.SettingsChanged event) {
        if (applying) {
            return;
        }
        working = event.settings().copy().normalize();
        syncControls();
        refreshTexts();
        particles.setReduceFlashing(working.reduceFlashing);
    }

    /**
     * What a screen without an application context can still apply on its own: the render-side
     * globals and the string table. Everything that needs the engine (viewport, limiter,
     * presenter, input queue) is simply not there in that mode.
     */
    private void applyStandalone() {
        Fonts.setTextScale(working.textScale);
        ProceduralArt.setSmoothing(working.smoothing);
        ParticleSystem.setDefaultReduceFlashing(working.reduceFlashing);
        Accessibility.setHighContrast(working.highContrast);
        Accessibility.setPalette(working.colorBlindPalette);
        String resolved = working.resolvedLanguage(GameContext.systemLanguage());
        if (!resolved.equals(strings.language())) {
            strings.reload(resolved);
            Strings.use(strings);
        }
    }

    private void markDirty() {
        dirty = true;
        idleTicks = 0;
    }

    /** Writes the working state when something is pending (idle debounce, or leaving). */
    public void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        idleTicks = 0;
        if (context != null) {
            context.saveSettings(working);
        } else {
            store.save(working);
        }
    }

    /** Puts every setting back to its default, applies it and writes it. */
    public void restoreDefaults() {
        working = Settings.defaults().normalize();
        syncControls();
        apply();
        refreshTexts();
        flush();
        toasts.push(strings.get(StringKey.TOAST_SETTINGS_RESTORED));
    }

    /** Copies the working state into the controls (after a reset). */
    private void syncControls() {
        sliders.get("master").setValueQuietly(working.masterVolume);
        sliders.get("sfx").setValueQuietly(working.sfxVolume);
        sliders.get("music").setValueQuietly(working.musicVolume);
        sliders.get("textScale").setValueQuietly(working.textScale);
        toggles.get("muted").setValueQuietly(working.muted);
        toggles.get("fullscreen").setValueQuietly(working.fullscreen);
        toggles.get("integerScaling").setValueQuietly(working.integerScaling);
        toggles.get("smoothing").setValueQuietly(working.smoothing);
        toggles.get("showFps").setValueQuietly(working.showFps);
        toggles.get("reduceFlashing").setValueQuietly(working.reduceFlashing);
        toggles.get("holdToFlap").setValueQuietly(working.holdToFlap);
        toggles.get("highContrast").setValueQuietly(working.highContrast);
        languageList.selectQuietly(Math.max(0, Settings.LANGUAGES.indexOf(working.language)));
        fpsList.selectQuietly(Math.max(0, FPS_OPTIONS.indexOf(working.maxFps)));
        colorBlindList.selectQuietly(
                Math.max(0, Settings.COLOR_BLIND_PALETTES.indexOf(working.colorBlindPalette)));
    }

    /** Re-reads every visible label from the string table (a language switch). */
    public void refreshTexts() {
        languageList.setLabel(strings.get(StringKey.SETTINGS_LANGUAGE));
        languageList.setOptions(languageOptions());
        fpsList.setLabel(strings.get(StringKey.SETTINGS_MAX_FPS));
        fpsList.setOptions(fpsOptions());
        sliders.get("master").setLabel(strings.get(StringKey.SETTINGS_MASTER_VOLUME));
        sliders.get("sfx").setLabel(strings.get(StringKey.SETTINGS_SFX_VOLUME));
        sliders.get("music").setLabel(strings.get(StringKey.SETTINGS_MUSIC_VOLUME));
        sliders.get("textScale").setLabel(strings.get(StringKey.SETTINGS_TEXT_SCALE));
        label(toggles.get("muted"), StringKey.SETTINGS_MUTED);
        label(toggles.get("fullscreen"), StringKey.SETTINGS_FULLSCREEN);
        label(toggles.get("integerScaling"), StringKey.SETTINGS_INTEGER_SCALING);
        label(toggles.get("smoothing"), StringKey.SETTINGS_SMOOTHING);
        label(toggles.get("showFps"), StringKey.SETTINGS_SHOW_FPS);
        label(toggles.get("reduceFlashing"), StringKey.SETTINGS_REDUCE_FLASHING);
        label(toggles.get("holdToFlap"), StringKey.SETTINGS_HOLD_TO_FLAP);
        label(toggles.get("highContrast"), StringKey.SETTINGS_HIGH_CONTRAST);
        colorBlindList.setLabel(strings.get(StringKey.SETTINGS_COLOR_BLIND_PALETTE));
        colorBlindList.setOptions(colourBlindOptions());
        for (Map.Entry<StringKey, Label> entry : headerByKey.entrySet()) {
            entry.getValue().setText(strings.get(entry.getKey()));
        }
        restore.setText(strings.get(StringKey.SETTINGS_RESTORE_DEFAULTS));
        back.setText(strings.get(StringKey.COMMON_BACK));
        refreshBindingLabels();
        shownLanguage = strings.language();
    }

    private void label(Toggle toggle, StringKey key) {
        toggle.setLabel(strings.get(key));
        toggle.setStateText(strings.get(StringKey.COMMON_ON), strings.get(StringKey.COMMON_OFF));
    }

    private void refreshBindingLabels() {
        KeyBindings bindings = working.bindings();
        for (Map.Entry<InputAction, Button> entry : rebinds.entrySet()) {
            InputAction action = entry.getKey();
            List<Integer> codes = bindings.keysFor(action);
            StringBuilder keys = new StringBuilder();
            for (int i = 0; i < codes.size(); i++) {
                keys.append(i == 0 ? "" : ", ").append(Keys.name(codes.get(i)));
            }
            if (codes.isEmpty()) {
                keys.append(strings.get(StringKey.INPUT_UNBOUND));
            }
            entry.getValue().setText(strings.format(StringKey.TOAST_BINDING_SET,
                    actionName(action), keys.toString()));
        }
    }

    private String actionName(InputAction action) {
        switch (action) {
            case FLAP:
                return strings.get(StringKey.INPUT_FLAP);
            case ABILITY:
                return strings.get(StringKey.INPUT_ABILITY);
            case PAUSE:
                return strings.get(StringKey.INPUT_PAUSE);
            case CONFIRM:
                return strings.get(StringKey.INPUT_CONFIRM);
            case MUTE:
                return strings.get(StringKey.INPUT_MUTE);
            case DEBUG:
                return strings.get(StringKey.INPUT_DEBUG);
            case FULLSCREEN:
                return strings.get(StringKey.INPUT_FULLSCREEN);
            default:
                return action.name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        footerRing.resetTransition();
        ring.focus(languageList);
        footerRing.focus(null);
        footerActive = false;
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
        contentMouseX = OFF_SCREEN;
        contentMouseY = OFF_SCREEN;
        scroll = 0;
        screens.setLetterboxRgb(PALETTE.letterbox());
        working = store.settings().copy().normalize();
        syncControls();
        refreshTexts();
        particles.setReduceFlashing(working.reduceFlashing);
        subscribeToSettings();
    }

    @Override
    public void onExit() {
        cancelCapture();
        flush();
        if (settingsSubscription != null) {
            settingsSubscription.cancel();
            settingsSubscription = null;
        }
    }

    /** Listens for settings changed elsewhere while this screen is on top. */
    private void subscribeToSettings() {
        EventBus bus = context != null ? context.events() : null;
        if (bus == null || settingsSubscription != null) {
            return;
        }
        settingsSubscription = bus.subscribe(GameEvent.SettingsChanged.class,
                this::onSettingsChanged);
    }

    @Override
    public void tick(InputFrame input) {
        toasts.tick();
        particles.update(1.0 / Playfield.TICK_RATE);
        if (capturing != null) {
            handleCapture(input);
            return;
        }
        updateActiveRegion(input);
        InputFrame local = toContent(input);
        if (footerActive) {
            clearHover(ring);
            UiNode activated = footerRing.handle(input);
            if (activated == restore) {
                particles.emitUiSparkle(activated.centerX(), activated.centerY(),
                        PALETTE.accent());
            }
        } else {
            clearHover(footerRing);
            UiNode activated = ring.handle(local);
            if (activated instanceof Button) {
                particles.emitUiSparkle(activated.centerX(),
                        activated.centerY() + VIEW_TOP - scroll, PALETTE.accent());
            }
            for (int i = 0; i < rows.size(); i++) {
                UiNode row = rows.get(i);
                if (row instanceof Slider slider) {
                    slider.tick(local);
                } else if (row instanceof Toggle toggle) {
                    toggle.tick(local);
                } else if (row instanceof ListView list) {
                    list.tick(local);
                }
            }
        }
        if (input.wheel() != 0) {
            scrollBy(-input.wheel() * (double) WHEEL_STEP);
        }
        scrollFocusIntoView();
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
        if (dirty && ++idleTicks >= SAVE_IDLE_TICKS) {
            flush();
        }
        if (input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
        }
    }

    private void handleCapture(InputFrame input) {
        List<Integer> downs = input.rawKeyDowns();
        if (downs.isEmpty()) {
            return;
        }
        int code = downs.get(0);
        InputAction action = capturing;
        capturing = null;
        if (code == Keys.ESCAPE) {
            return;
        }
        KeyBindings current = working.bindings();
        EnumSet<InputAction> holders = current.actionsFor(code);
        holders.remove(action);
        holders.retainAll(REBINDABLE);
        if (!holders.isEmpty()) {
            InputAction other = holders.iterator().next();
            toasts.push(strings.format(StringKey.TOAST_BINDING_CONFLICT, Keys.name(code),
                    actionName(other)), Toast.Kind.WARNING);
            return;
        }
        working.withBindings(current.withBinding(action, List.of(code)));
        apply();
        refreshBindingLabels();
        toasts.push(strings.format(StringKey.TOAST_BINDING_SET, actionName(action),
                Keys.name(code)));
    }

    /**
     * Decides whether the scrolled rows or the fixed footer bar owns this tick's input.
     *
     * <p>The pointer decides when it moves or clicks; the keyboard crosses the boundary with
     * {@code Down} from the last row and {@code Up} from the footer, which is what a single
     * column of controls makes a player expect.
     */
    private void updateActiveRegion(InputFrame input) {
        double mx = input.mouseX();
        double my = input.mouseY();
        boolean moved = !Double.isNaN(lastMouseX) && (mx != lastMouseX || my != lastMouseY);
        lastMouseX = mx;
        lastMouseY = my;
        // The pointer's position in content space also changes when the content scrolls under a
        // pointer that never moved. Recomputing it every tick would make the focus ring believe
        // the pointer moved and hand focus to whatever row slid under it, stealing it from the
        // row the keyboard just selected -- so the content position is only refreshed while the
        // pointer is actually doing something.
        pointerActive = moved || input.isMouseJustPressed(Keys.BUTTON_LEFT)
                || input.isMouseHeld(Keys.BUTTON_LEFT)
                || input.isMouseJustReleased(Keys.BUTTON_LEFT);
        if (pointerActive) {
            boolean inside = my >= VIEW_TOP && my <= VIEW_BOTTOM;
            contentMouseX = inside ? mx : OFF_SCREEN;
            contentMouseY = inside ? my - VIEW_TOP + scroll : OFF_SCREEN;
        }
        if (moved || input.isMouseJustPressed(Keys.BUTTON_LEFT)) {
            setFooterActive(my >= FOOTER_TOP);
        }
        if (!footerActive && input.isJustPressed(InputAction.DOWN) && ring.focused() == lastRow) {
            setFooterActive(true);
        } else if (footerActive && input.isJustPressed(InputAction.UP)) {
            setFooterActive(false);
        }
    }

    private void setFooterActive(boolean active) {
        if (active == footerActive) {
            return;
        }
        footerActive = active;
        if (active) {
            footerRing.focus(restore);
        } else {
            footerRing.focus(null);
            if (lastRow != null) {
                ring.focus(lastRow);
                scrollFocusIntoView();
            }
        }
    }

    private static void clearHover(FocusRing target) {
        for (UiNode node : target.nodes()) {
            node.setHovered(false);
        }
    }

    /**
     * Maps the pointer into the scrolled content space, using the position remembered by
     * {@link #updateActiveRegion(InputFrame)} so that scrolling alone never reads as a move.
     */
    private InputFrame toContent(InputFrame input) {
        return input.withMouse(contentMouseX, contentMouseY);
    }

    private void scrollBy(double delta) {
        scroll = MathUtil.clamp(scroll + delta, 0, maxScroll());
    }

    private double maxScroll() {
        return Math.max(0, contentHeight - (VIEW_BOTTOM - VIEW_TOP));
    }

    private void scrollFocusIntoView() {
        UiNode focused = footerActive ? null : ring.focused();
        if (focused == null) {
            return;
        }
        double viewHeight = VIEW_BOTTOM - VIEW_TOP;
        if (focused.y() < scroll) {
            scroll = focused.y() - ROW_GAP;
        } else if (focused.y() + focused.height() > scroll + viewHeight) {
            scroll = focused.y() + focused.height() - viewHeight + ROW_GAP;
        }
        scroll = MathUtil.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        g.setFont(Fonts.bold(34));
        TextPainter.drawOutlined(g, strings.get(StringKey.SETTINGS_TITLE),
                Playfield.WIDTH / 2.0, TITLE_BASELINE, Align.CENTER, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(PALETTE), 2);

        // A panel behind the rows: the world backdrop is a bright sky with clouds drifting
        // through it, and light grey labels on top of that are unreadable.
        ProceduralArt.panel(g, PANEL_X, VIEW_TOP - PANEL_PAD, Playfield.WIDTH - 2 * PANEL_X,
                VIEW_BOTTOM - VIEW_TOP + 2 * PANEL_PAD);

        Shape oldClip = g.getClip();
        g.clipRect(0, VIEW_TOP, Playfield.WIDTH, VIEW_BOTTOM - VIEW_TOP);
        double dy = VIEW_TOP - scroll;
        g.translate(0.0, dy);
        for (int i = 0; i < headers.size(); i++) {
            headers.get(i).render(g);
        }
        ring.render(g);
        g.translate(0.0, -dy);
        g.setClip(oldClip);

        renderScrollbar(g);
        footerRing.render(g);
        particles.render(g);
        toasts.render(g, TOAST_TOP_INSET);
        if (capturing != null) {
            renderCapture(g);
        }
    }

    private void renderScrollbar(Graphics2D g) {
        double max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackH = VIEW_BOTTOM - VIEW_TOP;
        int thumbH = (int) Math.max(24, trackH * (trackH / contentHeight));
        int thumbY = VIEW_TOP + (int) Math.round((trackH - thumbH) * (scroll / max));
        g.setColor(SCROLLBAR);
        g.fillRoundRect(Playfield.WIDTH - 10, thumbY, 4, thumbH, 4, 4);
    }

    private void renderCapture(Graphics2D g) {
        g.setColor(CAPTURE_DIM);
        g.fillRect(0, 0, Playfield.WIDTH, Playfield.HEIGHT);
        int px = 40;
        int py = (Playfield.HEIGHT - CAPTURE_PANEL_H) / 2;
        int pw = Playfield.WIDTH - 2 * px;
        ProceduralArt.panel(g, px, py, pw, CAPTURE_PANEL_H);
        g.setFont(Fonts.bold(20));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, actionName(capturing), Playfield.WIDTH / 2.0, py + 44.0);
        g.setFont(Fonts.regular(14));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.drawCentered(g, strings.get(StringKey.SETTINGS_PRESS_KEY),
                Playfield.WIDTH / 2.0, py + 76.0);
    }
}
