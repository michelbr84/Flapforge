package io.github.michelbr84.flapforge.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every string key the code refers to by name (D25). {@link Strings#get(StringKey)} is the only
 * way code reads a literal string, so a key that is not listed here cannot be used, and
 * {@link ContentValidator} proves that every key listed here exists in {@code en.json}.
 *
 * <p>Text derived from content — a bird's name, an ability's description — is not listed: it is
 * resolved by id through {@link Strings#name(String, String)} and
 * {@link Strings#desc(String, String)}.
 */
public enum StringKey {

    /** Game title, drawn on the boot and menu screens. */
    APP_TITLE("app.title"),
    /** Line under the title. */
    APP_TAGLINE("app.tagline"),
    /** Console message when no display is available. */
    APP_NO_DISPLAY("app.no_display"),

    /** Boot screen: content is being read. */
    BOOT_CONTENT("boot.content"),
    /** Boot screen: settings are being read. */
    BOOT_SETTINGS("boot.settings"),
    /** Boot screen: fonts are warming up. */
    BOOT_FONTS("boot.fonts"),
    /** Boot screen: audio is warming up. */
    BOOT_AUDIO("boot.audio"),
    /** Boot screen: everything is ready. */
    BOOT_READY("boot.ready"),
    /** Boot screen: prompt to continue. */
    BOOT_PRESS_ANY("boot.press_any"),

    /** Main menu: start a run. */
    MENU_PLAY("menu.play"),
    /** Main menu: open the settings screen. */
    MENU_SETTINGS("menu.settings"),
    /** Main menu: leave the game. */
    MENU_QUIT("menu.quit"),

    /** Return to the previous screen. */
    COMMON_BACK("common.back"),
    /** Value of a toggle that is on. */
    COMMON_ON("common.on"),
    /** Value of a toggle that is off. */
    COMMON_OFF("common.off"),
    /** Label of a "restore the default" action. */
    COMMON_DEFAULT("common.default"),
    /** Percentage value, {@code {0}} is the number. */
    COMMON_PERCENT("common.percent"),

    /** Settings screen title. */
    SETTINGS_TITLE("settings.title"),
    /** Settings row: language. */
    SETTINGS_LANGUAGE("settings.language"),
    /** Settings value: follow the system locale, {@code {0}} is the resolved language. */
    SETTINGS_LANGUAGE_AUTO("settings.language.auto"),
    /** Settings row: master volume. */
    SETTINGS_MASTER_VOLUME("settings.master_volume"),
    /** Settings row: sound-effect volume. */
    SETTINGS_SFX_VOLUME("settings.sfx_volume"),
    /** Settings row: music volume. */
    SETTINGS_MUSIC_VOLUME("settings.music_volume"),
    /** Settings row: mute everything. */
    SETTINGS_MUTED("settings.muted"),
    /** Settings section: key bindings. */
    SETTINGS_KEY_BINDINGS("settings.key_bindings"),
    /** Settings section: volumes and muting. */
    SETTINGS_SECTION_AUDIO("settings.section.audio"),
    /** Settings section: window, scaling and frame rate. */
    SETTINGS_SECTION_DISPLAY("settings.section.display"),
    /** Settings section: accessibility and gameplay options. */
    SETTINGS_SECTION_GAME("settings.section.game"),
    /** Settings row: integer scaling. */
    SETTINGS_INTEGER_SCALING("settings.integer_scaling"),
    /** Settings row: fullscreen. */
    SETTINGS_FULLSCREEN("settings.fullscreen"),
    /** Settings row: frame-rate cap. */
    SETTINGS_MAX_FPS("settings.max_fps"),
    /** Settings value: a frame-rate cap, {@code {0}} is the number. */
    SETTINGS_MAX_FPS_VALUE("settings.max_fps.value"),
    /** Settings value: no frame-rate cap at all. */
    SETTINGS_MAX_FPS_UNCAPPED("settings.max_fps.uncapped"),
    /** Settings value: follow the display's refresh rate. */
    SETTINGS_MAX_FPS_MATCH_REFRESH("settings.max_fps.match_refresh"),
    /** Settings row: bilinear smoothing. */
    SETTINGS_SMOOTHING("settings.smoothing"),
    /** Settings row: show the frame-time overlay. */
    SETTINGS_SHOW_FPS("settings.show_fps"),
    /** Settings row: reduce flashing. */
    SETTINGS_REDUCE_FLASHING("settings.reduce_flashing"),
    /** Settings row: high contrast. */
    SETTINGS_HIGH_CONTRAST("settings.high_contrast"),
    /** Settings row: colour-blind palette. */
    SETTINGS_COLOR_BLIND_PALETTE("settings.color_blind_palette"),
    /** Colour-blind palette value: none. */
    SETTINGS_COLOR_BLIND_NONE("settings.color_blind.none"),
    /** Colour-blind palette value: protanopia. */
    SETTINGS_COLOR_BLIND_PROTANOPIA("settings.color_blind.protanopia"),
    /** Colour-blind palette value: deuteranopia. */
    SETTINGS_COLOR_BLIND_DEUTERANOPIA("settings.color_blind.deuteranopia"),
    /** Colour-blind palette value: tritanopia. */
    SETTINGS_COLOR_BLIND_TRITANOPIA("settings.color_blind.tritanopia"),
    /** Settings row: text scale. */
    SETTINGS_TEXT_SCALE("settings.text_scale"),
    /** Settings row: hold to flap. */
    SETTINGS_HOLD_TO_FLAP("settings.hold_to_flap"),
    /** Settings action: restore every default. */
    SETTINGS_RESTORE_DEFAULTS("settings.restore_defaults"),
    /** Prompt shown while a key is being rebound. */
    SETTINGS_PRESS_KEY("settings.press_key"),

    /** Language name: English. */
    LANGUAGE_EN("language.en"),
    /** Language name: Brazilian Portuguese. */
    LANGUAGE_PT_BR("language.pt_br"),

    /** Input action name: flap. */
    INPUT_FLAP("input.flap"),
    /** Input action name: ability. */
    INPUT_ABILITY("input.ability"),
    /** Input action name: pause. */
    INPUT_PAUSE("input.pause"),
    /** Input action name: confirm. */
    INPUT_CONFIRM("input.confirm"),
    /** Input action name: back. */
    INPUT_BACK("input.back"),
    /** Input action name: move focus up. */
    INPUT_UP("input.up"),
    /** Input action name: move focus down. */
    INPUT_DOWN("input.down"),
    /** Input action name: move focus left. */
    INPUT_LEFT("input.left"),
    /** Input action name: move focus right. */
    INPUT_RIGHT("input.right"),
    /** Input action name: mute. */
    INPUT_MUTE("input.mute"),
    /** Input action name: debug overlay. */
    INPUT_DEBUG("input.debug"),
    /** Input action name: fullscreen. */
    INPUT_FULLSCREEN("input.fullscreen"),
    /** Shown instead of a key name when an action has no binding. */
    INPUT_UNBOUND("input.unbound"),

    /** Blinking hint shown while a run waits for its first flap. */
    GAME_READY_HINT("game.ready_hint"),
    /** HUD: the seed of a seeded run, {@code {0}} is the seed. */
    HUD_SEED("hud.seed"),
    /** HUD: the clean-gate streak, {@code {0}} is the length. */
    HUD_STREAK("hud.streak"),

    /** Pause overlay title. */
    PAUSE_TITLE("pause.title"),
    /** Pause overlay: how to resume. */
    PAUSE_RESUME_HINT("pause.resume_hint"),
    /** Pause overlay: how to quit. */
    PAUSE_QUIT_HINT("pause.quit_hint"),

    /** Game-over overlay title. */
    GAMEOVER_TITLE("gameover.title"),
    /** Game-over overlay prompt. */
    GAMEOVER_RETRY_HINT("gameover.retry_hint"),
    /** Game-over line shown after a personal best, {@code {0}} is the point total. */
    GAMEOVER_BEST_HINT("gameover.best_hint"),
    /** Result row: gates cleared. */
    STAT_GATES("stat.gates"),
    /** Result row: points scored. */
    STAT_POINTS("stat.points"),
    /** Result row: time survived. */
    STAT_TIME_ALIVE("stat.time_alive"),
    /** Result value: seconds and ticks survived, {@code {0}} seconds and {@code {1}} ticks. */
    STAT_TIME_ALIVE_VALUE("stat.time_alive.value"),

    /** Footer: the global keys. */
    FOOTER_KEYS("footer.keys"),
    /** Footer: the version, {@code {0}} is the version string. */
    FOOTER_VERSION("footer.version"),
    /** Footer: the build the game runs on, {@code {0}} is the Java runtime version. */
    FOOTER_BUILD("footer.build"),

    /** Toast: the settings file was reset, {@code {0}} is the archived file name. */
    TOAST_SETTINGS_RESET("toast.settings_reset"),
    /** Toast: the language changed, {@code {0}} is the language name. */
    TOAST_LANGUAGE_CHANGED("toast.language_changed"),
    /** Toast: a key was bound, {@code {0}} is the action and {@code {1}} the key. */
    TOAST_BINDING_SET("toast.binding_set"),
    /** Toast: a key is taken, {@code {0}} is the key and {@code {1}} the action holding it. */
    TOAST_BINDING_CONFLICT("toast.binding_conflict"),
    /** Toast: every setting went back to its default. */
    TOAST_SETTINGS_RESTORED("toast.settings_restored"),
    /** Toast: audio was muted. */
    TOAST_MUTED("toast.muted"),
    /** Toast: audio was unmuted. */
    TOAST_UNMUTED("toast.unmuted"),
    /** Toast: a file could not be written, {@code {0}} is the reason. */
    TOAST_SAVE_FAILED("toast.save_failed");

    private static final Map<String, StringKey> BY_KEY;

    static {
        Map<String, StringKey> byKey = new LinkedHashMap<>();
        for (StringKey value : values()) {
            byKey.put(value.key, value);
        }
        BY_KEY = Collections.unmodifiableMap(byKey);
    }

    private final String key;

    StringKey(String key) {
        this.key = key;
    }

    /**
     * The key as it appears in the string files.
     *
     * @return the key
     */
    public String key() {
        return key;
    }

    /**
     * Looks a key up.
     *
     * @param key the key text
     * @return the constant, or {@code null} when no constant carries it
     */
    public static StringKey byKey(String key) {
        return BY_KEY.get(key);
    }

    /**
     * Every key, in declaration order.
     *
     * @return an unmodifiable set
     */
    public static Set<String> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(BY_KEY.keySet()));
    }

    @Override
    public String toString() {
        return key;
    }
}
