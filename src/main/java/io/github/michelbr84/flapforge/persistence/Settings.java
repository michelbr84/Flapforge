package io.github.michelbr84.flapforge.persistence;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.input.KeyBindings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Everything {@code settings.json} holds (§4). The fields are the JSON keys, in file order, and
 * their initialisers are the defaults a fresh installation starts from, so a missing key simply
 * keeps its default when Gson binds a partial file.
 *
 * <p>{@link #normalize()} is the single validation point: it clamps the volumes to
 * {@code [0, 1]}, the text scale to {@code [0.75, 1.5]} and the frame-rate cap to
 * {@code [30, 240]}, and it replaces an unknown language or colour-blind palette with the
 * default. It runs on load and on save, so a hand-edited file can never feed a nonsense value
 * into the renderer or the mixer.
 */
public final class Settings {

    /** Schema version written to {@code settings.json}. */
    public static final int VERSION = 1;
    /** Language value meaning "follow the system locale". */
    public static final String LANGUAGE_AUTO = "auto";
    /** The languages the game ships, plus {@link #LANGUAGE_AUTO}. */
    public static final List<String> LANGUAGES = List.of(LANGUAGE_AUTO, "en", "pt_BR");
    /** Colour-blind palette meaning "no remapping". */
    public static final String PALETTE_NONE = "none";
    /** The colour-blind palettes the settings screen offers. */
    public static final List<String> COLOR_BLIND_PALETTES =
            List.of(PALETTE_NONE, "protanopia", "deuteranopia", "tritanopia");
    /** Lowest accepted volume. */
    public static final double MIN_VOLUME = 0.0;
    /** Highest accepted volume. */
    public static final double MAX_VOLUME = 1.0;
    /** Lowest accepted text scale. */
    public static final double MIN_TEXT_SCALE = 0.75;
    /** Highest accepted text scale. */
    public static final double MAX_TEXT_SCALE = 1.5;
    /** Lowest accepted frame-rate cap. */
    public static final int MIN_FPS = 30;
    /** Highest accepted frame-rate cap. */
    public static final int MAX_FPS = 240;
    /**
     * {@link #maxFps} value meaning "do not pace at all" (the limiter's
     * {@code FrameLimiter.UNCAPPED}). It survives {@link #normalize()} unchanged, unlike any
     * other out-of-range number.
     */
    public static final int MAX_FPS_UNCAPPED = 0;
    /**
     * {@link #maxFps} value meaning "follow the display's refresh rate", resolved at apply time
     * because the rate is only known to the windowing layer (D1, E30.f).
     */
    public static final int MAX_FPS_MATCH_REFRESH = -1;

    /** Schema version of the file this instance came from. */
    public int version = VERSION;
    /** {@code auto}, {@code en} or {@code pt_BR} (D25). */
    public String language = LANGUAGE_AUTO;
    /** Master gain applied to every voice, in {@code [0, 1]}. */
    public double masterVolume = 0.8;
    /** Sound-effect gain, in {@code [0, 1]}. */
    public double sfxVolume = 1.0;
    /** Music gain, in {@code [0, 1]}. */
    public double musicVolume = 0.6;
    /** Whether all audio is muted. */
    public boolean muted = false;
    /** Action name to key codes, as {@link KeyBindings#toMap()} writes it. */
    public Map<String, List<Integer>> keyBindings = KeyBindings.defaults().toMap();
    /** Whether the viewport snaps to whole-pixel scales (D3). */
    public boolean integerScaling = false;
    /**
     * Whether the renderers extend sky and earth over the vertical letterbox bars (D3). Off by
     * default until the extension is validated on a phone: the letterboxed frame is the one
     * every shipped build has drawn, so a launch never depends on the newer render path.
     */
    public boolean fillScreen = false;
    /** Whether the window starts in borderless fullscreen. */
    public boolean fullscreen = false;
    /** Frame-rate cap in frames per second, in {@code [30, 240]}. */
    public int maxFps = 60;
    /** Whether bilinear smoothing is allowed for non-integer scales. */
    public boolean smoothing = true;
    /** Whether the frame-time overlay starts visible. */
    public boolean showFps = false;
    /** Accessibility: suppress full-screen flashes (E8). */
    public boolean reduceFlashing = true;
    /** Accessibility: draw with the high-contrast palette. */
    public boolean highContrast = false;
    /** Accessibility: one of {@link #COLOR_BLIND_PALETTES}. */
    public String colorBlindPalette = PALETTE_NONE;
    /** Accessibility: UI text scale, in {@code [0.75, 1.5]}. */
    public double textScale = 1.0;
    /** Accessibility: holding flap keeps flapping at a fixed period (D2). */
    public boolean holdToFlap = false;

    /**
     * Creates the defaults. Gson uses this constructor too, so a file with missing keys binds
     * onto default values.
     */
    public Settings() {
    }

    /**
     * A fresh instance carrying every default.
     *
     * @return the defaults
     */
    public static Settings defaults() {
        return new Settings();
    }

    /**
     * Clamps and repairs every field, in place.
     *
     * @return {@code this}
     */
    public Settings normalize() {
        version = version <= 0 ? VERSION : version;
        language = oneOf(language, LANGUAGES, LANGUAGE_AUTO);
        colorBlindPalette = oneOf(colorBlindPalette, COLOR_BLIND_PALETTES, PALETTE_NONE);
        masterVolume = MathUtil.clamp(finite(masterVolume, 0.8), MIN_VOLUME, MAX_VOLUME);
        sfxVolume = MathUtil.clamp(finite(sfxVolume, 1.0), MIN_VOLUME, MAX_VOLUME);
        musicVolume = MathUtil.clamp(finite(musicVolume, 0.6), MIN_VOLUME, MAX_VOLUME);
        textScale = MathUtil.clamp(finite(textScale, 1.0), MIN_TEXT_SCALE, MAX_TEXT_SCALE);
        maxFps = isFpsSentinel(maxFps) ? maxFps : MathUtil.clamp(maxFps, MIN_FPS, MAX_FPS);
        keyBindings = KeyBindings.fromMap(keyBindings).toMap();
        return this;
    }

    /**
     * A deep copy: the caller can hand one instance to the UI and keep another as the last saved
     * state without the two sharing the bindings map.
     *
     * @return the copy
     */
    public Settings copy() {
        Settings out = new Settings();
        out.version = version;
        out.language = language;
        out.masterVolume = masterVolume;
        out.sfxVolume = sfxVolume;
        out.musicVolume = musicVolume;
        out.muted = muted;
        out.keyBindings = copyBindings(keyBindings);
        out.integerScaling = integerScaling;
        out.fillScreen = fillScreen;
        out.fullscreen = fullscreen;
        out.maxFps = maxFps;
        out.smoothing = smoothing;
        out.showFps = showFps;
        out.reduceFlashing = reduceFlashing;
        out.highContrast = highContrast;
        out.colorBlindPalette = colorBlindPalette;
        out.textScale = textScale;
        out.holdToFlap = holdToFlap;
        return out;
    }

    /**
     * The bindings as the input layer wants them.
     *
     * @return the parsed bindings (defaults for anything missing)
     */
    public KeyBindings bindings() {
        return KeyBindings.fromMap(keyBindings);
    }

    /**
     * Replaces the bindings from the input layer.
     *
     * @param bindings the bindings
     * @return {@code this}
     */
    public Settings withBindings(KeyBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        keyBindings = bindings.toMap();
        return this;
    }

    /**
     * Resolves {@link #LANGUAGE_AUTO} against a system locale tag (D25): anything Portuguese
     * becomes {@code pt_BR}, everything else {@code en}.
     *
     * @param systemLanguage the two-letter language of the default locale, may be {@code null}
     * @return the language to load strings for, never {@code auto}
     */
    public String resolvedLanguage(String systemLanguage) {
        if (!LANGUAGE_AUTO.equals(language)) {
            return language;
        }
        String tag = systemLanguage == null ? "" : systemLanguage.toLowerCase(Locale.ROOT);
        return tag.startsWith("pt") ? "pt_BR" : "en";
    }

    /**
     * Whether a frame-rate cap is one of the two symbolic values rather than a rate.
     *
     * @param fps the value
     * @return {@code true} for {@link #MAX_FPS_UNCAPPED} and {@link #MAX_FPS_MATCH_REFRESH}
     */
    public static boolean isFpsSentinel(int fps) {
        return fps == MAX_FPS_UNCAPPED || fps == MAX_FPS_MATCH_REFRESH;
    }

    private static String oneOf(String value, List<String> allowed, String fallback) {
        return value != null && allowed.contains(value) ? value : fallback;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static Map<String, List<Integer>> copyBindings(Map<String, List<Integer>> source) {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, List<Integer>> e : source.entrySet()) {
                out.put(e.getKey(), e.getValue() == null ? List.of()
                        : List.copyOf(new ArrayList<>(e.getValue())));
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Settings other)) {
            return false;
        }
        return version == other.version
                && Double.compare(masterVolume, other.masterVolume) == 0
                && Double.compare(sfxVolume, other.sfxVolume) == 0
                && Double.compare(musicVolume, other.musicVolume) == 0
                && muted == other.muted
                && integerScaling == other.integerScaling
                && fillScreen == other.fillScreen
                && fullscreen == other.fullscreen
                && maxFps == other.maxFps
                && smoothing == other.smoothing
                && showFps == other.showFps
                && reduceFlashing == other.reduceFlashing
                && highContrast == other.highContrast
                && Double.compare(textScale, other.textScale) == 0
                && holdToFlap == other.holdToFlap
                && Objects.equals(language, other.language)
                && Objects.equals(colorBlindPalette, other.colorBlindPalette)
                && Objects.equals(keyBindings, other.keyBindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, language, masterVolume, sfxVolume, musicVolume, muted,
                keyBindings, integerScaling, fillScreen, fullscreen, maxFps, smoothing, showFps,
                reduceFlashing, highContrast, colorBlindPalette, textScale, holdToFlap);
    }

    @Override
    public String toString() {
        return "Settings{version=" + version + ", language=" + language + ", maxFps=" + maxFps
                + ", muted=" + muted + ", textScale=" + textScale + '}';
    }
}
