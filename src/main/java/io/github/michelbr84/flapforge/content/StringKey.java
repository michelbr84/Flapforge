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
    /** Main menu: open the statistics screen. */
    MENU_STATISTICS("menu.statistics"),
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
    /** HUD and wallet: a coin amount, {@code {0}} is the number. */
    HUD_COINS("hud.coins"),

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
    /** Result row: coins earned by the run. */
    STAT_COINS("stat.coins"),
    /** Result row: experience earned by the run. */
    STAT_XP("stat.xp"),
    /** Result row: the longest clean-gate streak of the run. */
    STAT_STREAK_BEST("stat.streak_best"),
    /** Game-over line after a level-up, {@code {0}} is the new level. */
    GAMEOVER_LEVEL_UP("gameover.level_up"),

    /** Run summary title. */
    SUMMARY_TITLE("summary.title"),
    /** Run summary section: what the run itself did. */
    SUMMARY_SECTION_RUN("summary.section.run"),
    /** Run summary section: the coin breakdown. */
    SUMMARY_SECTION_COINS("summary.section.coins"),
    /** Run summary section: experience and level. */
    SUMMARY_SECTION_XP("summary.section.xp"),
    /** Run summary section: how the run was set up. */
    SUMMARY_SECTION_INFO("summary.section.info"),
    /** Run summary button: play again with a new seed. */
    SUMMARY_RETRY("summary.retry"),
    /** Run summary button: back to the main menu. */
    SUMMARY_MENU("summary.menu"),
    /** Marker appended to a row the run set a personal best in. */
    SUMMARY_BEST("summary.best"),
    /** Run summary row: the seed and the mode, {@code {0}} seed and {@code {1}} mode. */
    SUMMARY_SEED("summary.seed"),
    /** Run summary row: the level reached, {@code {0}} is the level. */
    SUMMARY_LEVEL("summary.level"),
    /** Run summary value: progress inside a level, {@code {0}} of {@code {1}} XP. */
    SUMMARY_LEVEL_PROGRESS("summary.level_progress"),
    /** Run summary value: the level cap has been reached. */
    SUMMARY_LEVEL_MAX("summary.level_max"),

    /** Run mode: a normal run. */
    MODE_STANDARD("mode.standard"),
    /** Run mode: a run with a chosen seed. */
    MODE_SEEDED("mode.seeded"),
    /** Run mode: the daily challenge. */
    MODE_DAILY("mode.daily"),
    /** Run mode: a challenge. */
    MODE_CHALLENGE("mode.challenge"),

    /** Reward row: the coins any run that got going pays. */
    REWARD_PARTICIPATION("reward.participation"),
    /** Reward row: the bonus a profile's first run pays. */
    REWARD_FIRST_RUN("reward.first_run"),
    /** Reward row: the coins the gates paid. */
    REWARD_GATES("reward.gates"),
    /** Reward row: the coins the points paid. */
    REWARD_POINTS("reward.points"),
    /** Reward row: the coins the streak steps paid. */
    REWARD_STREAK("reward.streak"),
    /** Reward row: the coins the bosses paid. */
    REWARD_BOSS("reward.boss"),
    /** Reward row: the coins the challenge paid. */
    REWARD_CHALLENGE("reward.challenge"),
    /** Reward row: the sum of the terms before any multiplier. */
    REWARD_BASE("reward.base"),
    /** Reward row: the {@code COIN_MULT} stat the run was played with. */
    REWARD_COIN_MULT("reward.coin_mult"),
    /** Reward row: the tier's reward multiplier. */
    REWARD_TIER_MULT("reward.tier_mult"),
    /** Reward row: the daily multiplier. */
    REWARD_DAILY_MULT("reward.daily_mult"),
    /** Reward row: the coins picked up in the world. */
    REWARD_COLLECTED("reward.collected"),
    /** Reward row: what the run paid in total. */
    REWARD_TOTAL("reward.total"),
    /** Reward value: a multiplier, {@code {0}} is the factor. */
    REWARD_MULTIPLIER_VALUE("reward.multiplier_value"),

    /** Statistics screen title. */
    STATS_TITLE("stats.title"),
    /** Statistics group: how much has been flown. */
    STATS_GROUP_FLIGHTS("stats.group.flights"),
    /** Statistics group: how far the flights got. */
    STATS_GROUP_DISTANCE("stats.group.distance"),
    /** Statistics group: coins and experience. */
    STATS_GROUP_ECONOMY("stats.group.economy"),
    /** Statistics group: clean-gate streaks and what breaks them. */
    STATS_GROUP_STREAKS("stats.group.streaks"),
    /** Statistics group: deaths per collision cause. */
    STATS_GROUP_DEATHS("stats.group.deaths"),
    /** Statistics row: runs finished. */
    STATS_RUNS("stats.runs"),
    /** Statistics row: time spent flying. */
    STATS_PLAYTIME("stats.playtime"),
    /** Statistics value: a duration, {@code {0}} hours and {@code {1}} minutes. */
    STATS_PLAYTIME_VALUE("stats.playtime.value"),
    /** Statistics row: daily runs played. */
    STATS_DAILIES("stats.dailies"),
    /** Statistics row: challenges completed. */
    STATS_CHALLENGES("stats.challenges"),
    /** Statistics row: most gates in one run. */
    STATS_BEST_GATES("stats.best_gates"),
    /** Statistics row: gates passed across every run. */
    STATS_TOTAL_GATES("stats.total_gates"),
    /** Statistics row: most points in one run. */
    STATS_BEST_POINTS("stats.best_points"),
    /** Statistics row: points scored across every run. */
    STATS_TOTAL_POINTS("stats.total_points"),
    /** Statistics row: coins credited from every source. */
    STATS_COINS_EARNED("stats.coins_earned"),
    /** Statistics row: coins spent. */
    STATS_COINS_SPENT("stats.coins_spent"),
    /** Statistics row: coins picked up in runs. */
    STATS_COINS_COLLECTED("stats.coins_collected"),
    /** Statistics row: experience earned. */
    STATS_XP_EARNED("stats.xp_earned"),
    /** Statistics row: the current level. */
    STATS_LEVEL("stats.level"),
    /** Statistics row: hits absorbed by a shield. */
    STATS_SHIELD_ABSORBS("stats.shield_absorbs"),
    /** Statistics row: revives consumed. */
    STATS_REVIVES("stats.revives"),
    /** Statistics: the run history list. */
    STATS_HISTORY("stats.history"),
    /** Statistics: one history entry, {@code {0}} index, {@code {1}} gates, {@code {2}} coins. */
    STATS_HISTORY_ENTRY("stats.history.entry"),
    /** Statistics: shown instead of the history when no run has been finished yet. */
    STATS_HISTORY_EMPTY("stats.history.empty"),
    /** Statistics: shown instead of a group whose counters are all zero. */
    STATS_NONE("stats.none"),

    /** Death cause: flew into an obstacle. */
    DEATH_OBSTACLE("death.obstacle"),
    /** Death cause: hit the ground. */
    DEATH_GROUND("death.ground"),
    /** Death cause: hit the ceiling. */
    DEATH_CEILING("death.ceiling"),
    /** Death cause: the run ended without one. */
    DEATH_UNKNOWN("death.unknown"),

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
    TOAST_SAVE_FAILED("toast.save_failed"),
    /** Toast: the save was unusable and the backup was loaded, {@code {0}} is the file name. */
    TOAST_SAVE_RESTORED("toast.save_restored"),
    /** Toast: save and backup were both unusable, {@code {0}} is the quarantined file name. */
    TOAST_SAVE_RESET("toast.save_reset"),
    /** Toast: the save is newer than this build, so nothing is written this session. */
    TOAST_SAVE_READ_ONLY("toast.save_read_only"),
    /** Toast: the save file could not be opened, {@code {0}} is the file name. */
    TOAST_SAVE_UNREADABLE("toast.save_unreadable"),
    /** Toast: the player reached a new level, {@code {0}} is the level. */
    TOAST_LEVEL_UP("toast.level_up");

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
