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
    /** HUD: the equipped active ability is off cooldown. */
    HUD_ABILITY_READY("hud.ability.ready"),
    /** HUD: the cooldown left, {@code {0}} is the number of ticks. */
    HUD_ABILITY_COOLDOWN("hud.ability.cooldown"),
    /** HUD: the charges left of a charge-gated ability, {@code {0}} is the count. */
    HUD_ABILITY_CHARGES("hud.ability.charges"),
    /** HUD: the shield charges left, {@code {0}} is the count. */
    HUD_SHIELD_CHARGES("hud.shield.charges"),

    /** Pause overlay title. */
    PAUSE_TITLE("pause.title"),
    /** Pause overlay: the resume button. */
    PAUSE_RESUME("pause.resume"),

    /** Game-over overlay title. */
    GAMEOVER_TITLE("gameover.title"),
    /** Game-over overlay: the button that opens the run summary. */
    GAMEOVER_SUMMARY("gameover.summary"),
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
    /** Daily challenge record, as the value of a "Daily" row: {@code {0}} best gates today,
     * {@code {1}} attempts (M9). */
    DAILY_RESULT("daily.result"),
    /** Daily challenge record: nothing flown today yet (M9). */
    DAILY_UNPLAYED("daily.unplayed"),

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
    TOAST_LEVEL_UP("toast.level_up"),

    /** Main menu: open the bird selection. */
    MENU_BIRDS("menu.birds"),
    /** Main menu: open the upgrade trees. */
    MENU_UPGRADES("menu.upgrades"),
    /** Main menu: open the shop. */
    MENU_SHOP("menu.shop"),
    /** Main menu: the world the next run is played in, {@code {0}} its name (M7). */
    MENU_WORLD("menu.world"),

    /** Make the focused entry the one the next run uses. */
    COMMON_SELECT("common.select"),
    /** Buy the focused entry. */
    COMMON_BUY("common.buy"),
    /** State of something not owned yet. */
    COMMON_LOCKED("common.locked"),
    /** State of something already owned. */
    COMMON_OWNED("common.owned"),
    /** State of the entry the next run uses. */
    COMMON_SELECTED("common.selected"),
    /** Nothing at all. */
    COMMON_NONE("common.none"),
    /** Content that ships in a later milestone, {@code {0}} is the milestone. */
    COMMON_SOON("common.soon"),

    /** Bird selection: title. */
    BIRDS_TITLE("birds.title"),
    /** Bird selection: the palette row. */
    BIRDS_PALETTES("birds.palettes"),
    /** Bird selection: the ability slots. */
    BIRDS_ABILITIES("birds.abilities"),
    /** Bird selection: how many passives fit, {@code {0}} is the count. */
    BIRDS_PASSIVE_SLOTS("birds.passive_slots"),
    /** Bird selection: the difficulty tier of the next run. */
    BIRDS_TIER("birds.tier"),
    /** Bird selection: the world picker label (M7). */
    BIRDS_WORLD("birds.world"),
    /** Bird selection: the hazards a world spawns, {@code {0}} the list (M7). */
    BIRDS_WORLD_HAZARDS("birds.world.hazards"),
    /** Bird selection: how a locked world opens, {@code {0}} the condition (M7). */
    BIRDS_WORLD_LOCKED("birds.world.locked"),
    /** Bird selection: the run mode picker label (M9). */
    BIRDS_MODE("birds.mode"),
    /** Bird selection: how a locked mode opens, {@code {0}} the condition (M9). */
    BIRDS_MODE_LOCKED("birds.mode.locked"),
    /** Bird selection: what Standard mode does (M9). */
    BIRDS_MODE_STANDARD_HINT("birds.mode.standard_hint"),
    /** Bird selection: what Seeded mode replays, {@code {0}} the seed (M9). */
    BIRDS_MODE_SEEDED_HINT("birds.mode.seeded_hint"),
    /** Bird selection: today's daily, {@code {0}} world, {@code {1}} tier, {@code {2}} cards. */
    BIRDS_MODE_DAILY_HINT("birds.mode.daily_hint"),
    /** Bird selection: the stat breakdown panel. */
    BIRDS_BREAKDOWN("birds.breakdown"),
    /** Bird selection: the bird's base value of a stat. */
    BIRDS_BREAKDOWN_BASE("birds.breakdown.base"),
    /** Bird selection: no modifier touches any stat yet. */
    BIRDS_BREAKDOWN_EMPTY("birds.breakdown.empty"),
    /** Bird selection: the loadout row. */
    BIRDS_LOADOUT("birds.loadout"),
    /** Bird selection: the active ability slot. */
    BIRDS_SLOT_ACTIVE("birds.slot.active"),
    /** Bird selection: one passive slot, {@code {0}} is its number. */
    BIRDS_SLOT_PASSIVE("birds.slot.passive"),
    /** Bird selection: a passive the bird grants and nothing can unequip. */
    BIRDS_SLOT_INNATE("birds.slot.innate"),
    /** Bird selection: an empty slot. */
    BIRDS_SLOT_EMPTY("birds.slot.empty"),
    /** Bird selection: how a slot is filled. */
    BIRDS_SLOT_HINT("birds.slot.hint"),
    /** Bird selection: the title of the panel holding the abilities and the stat breakdown. */
    BIRDS_PANEL("birds.panel"),
    /** Bird selection: the list of unlocked abilities. */
    BIRDS_ABILITY_LIST("birds.ability_list"),
    /** Bird selection: the player owns no ability of that kind yet. */
    BIRDS_ABILITY_NONE_OWNED("birds.ability.none_owned"),
    /** Bird selection: an ability this run's rules would strip, {@code {0}} is the rule. */
    BIRDS_ABILITY_BLOCKED("birds.ability.blocked"),
    /** Bird selection: an ability is equipped. */
    BIRDS_ABILITY_EQUIPPED("birds.ability.equipped"),

    /** An active ability, triggered by the player. */
    ABILITY_KIND_ACTIVE("ability.kind.active"),
    /** A passive ability, always on while equipped. */
    ABILITY_KIND_PASSIVE("ability.kind.passive"),
    /** Ability tag: prevents or absorbs a lethal hit. */
    ABILITY_TAG_DEFENSIVE("ability.tag.defensive"),
    /** Ability tag: brings the bird back. */
    ABILITY_TAG_REVIVE("ability.tag.revive"),
    /** Ability tag: changes how the bird moves. */
    ABILITY_TAG_MOVEMENT("ability.tag.movement"),
    /** Ability tag: changes the pace of the world. */
    ABILITY_TAG_TEMPO("ability.tag.tempo"),
    /** Ability tag: pays in coins or points. */
    ABILITY_TAG_ECONOMY("ability.tag.economy"),
    /** Ability level, {@code {0}} of {@code {1}}. */
    ABILITY_LEVEL("ability.level"),
    /** Ability effect: the cooldown, {@code {0}} is the number of ticks. */
    ABILITY_EFFECT_COOLDOWN("ability.effect.cooldown"),
    /** Ability effect: the duration, {@code {0}} is the number of ticks. */
    ABILITY_EFFECT_DURATION("ability.effect.duration"),
    /** Ability level parameter {@code charges}, {@code {0}} is the count. */
    ABILITY_PARAM_CHARGES("ability.param.charges"),
    /** Ability level parameter {@code rechargeEveryGates}, {@code {0}} is the cadence. */
    ABILITY_PARAM_RECHARGE_EVERY_GATES("ability.param.recharge_every_gates"),
    /** Ability level parameter {@code invulnTicks}, {@code {0}} is the count. */
    ABILITY_PARAM_INVULN_TICKS("ability.param.invuln_ticks"),
    /** Ability level parameter {@code invulnExtraTicks}, {@code {0}} is the count. */
    ABILITY_PARAM_INVULN_EXTRA_TICKS("ability.param.invuln_extra_ticks"),
    /** Ability level parameter {@code regenEveryGates}, {@code {0}} is the cadence. */
    ABILITY_PARAM_REGEN_EVERY_GATES("ability.param.regen_every_gates"),
    /** Ability level parameter {@code flapMultiplier}, {@code {0}} is the factor. */
    ABILITY_PARAM_FLAP_MULTIPLIER("ability.param.flap_multiplier"),
    /** Ability level parameter {@code extraRadius}, {@code {0}} is the distance in px. */
    ABILITY_PARAM_EXTRA_RADIUS("ability.param.extra_radius"),
    /** Ability level parameter {@code kickMultiplier}, {@code {0}} is the factor. */
    ABILITY_PARAM_KICK_MULTIPLIER("ability.param.kick_multiplier"),

    /** Rule flag: defensive abilities are stripped. */
    RULE_NO_DEFENSIVE_ABILITIES("rule.no_defensive_abilities"),
    /** Rule flag: revives are stripped. */
    RULE_NO_REVIVE("rule.no_revive"),
    /** Rule flag: every obstacle uses its moving variant. */
    RULE_ALL_OBSTACLES_MOVE("rule.all_obstacles_move"),
    /** Rule flag: the top edge kills. */
    RULE_LETHAL_CEILING("rule.lethal_ceiling"),
    /** Rule flag: no coins spawn. */
    RULE_NO_COINS("rule.no_coins"),
    /** Rule flag: the scroll speed grows with time. */
    RULE_SPEED_RAMP("rule.speed_ramp"),
    /** Rule-shift banner: the heading (M7). */
    RULE_SHIFT_TITLE("rule_shift.title"),
    /** Rule-shift banner: the countdown, {@code {0}} the rule and {@code {1}} the seconds. */
    RULE_SHIFT_COUNTDOWN("rule_shift.countdown"),
    /** Rule-shift banner: the countdown ran out and the shift waits for clear air, {@code {0}}. */
    RULE_SHIFT_NOW("rule_shift.now"),
    /** Rule-shift banner: the rule landed, {@code {0}} the rule (M7). */
    RULE_SHIFT_IN_EFFECT("rule_shift.in_effect"),
    /** Obstacle family: pipe gates (M7). */
    OBSTACLE_PIPE_GATE("obstacle.pipe_gate.name"),
    /** Obstacle family: gears. */
    OBSTACLE_GEAR("obstacle.gear.name"),
    /** Obstacle family: pistons. */
    OBSTACLE_PISTON("obstacle.piston.name"),
    /** Obstacle family: wind zones. */
    OBSTACLE_WIND_ZONE("obstacle.wind_zone.name"),
    /** Obstacle family: lightning columns. */
    OBSTACLE_LIGHTNING("obstacle.lightning.name"),

    /** Upgrade trees: title. */
    UPGRADES_TITLE("upgrades.title"),
    /** Upgrade node: owned level, {@code {0}} of {@code {1}}. */
    UPGRADES_LEVEL("upgrades.level"),
    /** Upgrade node: every level owned. */
    UPGRADES_MAXED("upgrades.maxed"),
    /** Upgrade node: what one level does, {@code {0}} is the effect. */
    UPGRADES_PER_LEVEL("upgrades.per_level"),
    /** Upgrade tree: a tier of nodes, {@code {0}} is the number. */
    UPGRADES_TIER("upgrades.tier"),
    /** Upgrade node: unmet prerequisites, {@code {0}} lists them. */
    UPGRADES_NEEDS("upgrades.needs"),
    /** Upgrade tree: how the tree is unlocked, {@code {0}} is the condition. */
    UPGRADES_TREE_LOCKED("upgrades.tree_locked"),
    /** Upgrade node: what it unlocks, {@code {0}} is the name. */
    UPGRADES_GRANT_UNLOCK("upgrades.grant.unlock"),
    /** Upgrade node: raises the ability level cap. */
    UPGRADES_GRANT_ABILITY_CAP("upgrades.grant.ability_cap"),
    /** Upgrade node: adds a passive ability slot. */
    UPGRADES_GRANT_PASSIVE_SLOT("upgrades.grant.passive_slot"),
    /** Upgrade node: it only grants something the profile already has. */
    UPGRADES_ALREADY_OWNED("upgrades.already_owned"),

    /** Shop: title. */
    SHOP_TITLE("shop.title"),
    /** A price in coins, {@code {0}} is the amount. */
    SHOP_PRICE("shop.price"),
    /** Shop tab: birds and their colours. */
    SHOP_TAB_BIRDS("shop.tab.birds"),
    /** Shop tab: abilities. */
    SHOP_TAB_ABILITIES("shop.tab.abilities"),
    /** Shop tab: worlds, tiers and challenges. */
    SHOP_TAB_WORLDS("shop.tab.worlds"),
    /** Shop tab: upgrade trees and features. */
    SHOP_TAB_FEATURES("shop.tab.features"),
    /** Shop tab: everything in it is owned. */
    SHOP_EMPTY("shop.empty"),
    /** Shop: the wallet does not hold the price. */
    SHOP_CANNOT_AFFORD("shop.cannot_afford"),
    /** Shop: the next level of an ability, {@code {0}} is the level. */
    SHOP_ABILITY_NEXT_LEVEL("shop.ability.next_level"),
    /** Shop: the E3 ability level cap, {@code {0}} is the cap. */
    SHOP_ABILITY_CAP("shop.ability.cap"),
    /** Shop: the ability is at the level cap the profile has earned (E3). */
    SHOP_ABILITY_CAPPED("shop.ability.capped"),
    /** Shop: the ability owns every level the content ships. */
    SHOP_ABILITY_MAXED("shop.ability.maxed"),

    /** Toast: something was bought, {@code {0}} is its name. */
    TOAST_PURCHASED("toast.purchased"),
    /** Toast: a node was raised, {@code {0}} is its name and {@code {1}} the level. */
    TOAST_UPGRADED("toast.upgraded"),
    /** Toast: a purchase was refused, {@code {0}} says why. */
    TOAST_PURCHASE_FAILED("toast.purchase_failed"),
    /** Toast: an ability level was bought, {@code {0}} is its name and {@code {1}} the level. */
    TOAST_ABILITY_LEVEL("toast.ability_level"),
    /** Toast: the ability key was pressed with nothing equipped. */
    TOAST_ABILITY_NONE("toast.ability.none"),
    /** Toast: the ability key was pressed while the ability recharges. */
    TOAST_ABILITY_COOLDOWN("toast.ability.cooldown"),
    /** Toast: the ability key was pressed with no charge left. */
    TOAST_ABILITY_NO_CHARGE("toast.ability.no_charge"),
    /** Toast: this run's rules stripped the ability, {@code {0}} is the rule. */
    TOAST_ABILITY_BLOCKED("toast.ability.blocked"),
    /** Toast: a build completed a set bonus, {@code {0}} is the synergy name (D27). */
    TOAST_SYNERGY("toast.synergy"),

    /** Stat source: a bird effect that grows with every gate, {@code {0}} is the bird. */
    SOURCE_RAMP("source.ramp"),
    /** Stat source: a bird effect scaling with owned upgrades, {@code {0}} is the bird. */
    SOURCE_SYNERGY("source.synergy"),
    /** Stat source: the difficulty curve of the world. */
    SOURCE_CURVE("source.curve"),
    /** Stat source: the speed ramp rule. */
    SOURCE_SPEED_RAMP("source.speed_ramp"),

    /** An added stat value, {@code {0}} is the amount and {@code {1}} the stat. */
    STAT_EFFECT_FLAT("stat.effect.flat"),
    /** A percentage stat change, {@code {0}} is the amount and {@code {1}} the stat. */
    STAT_EFFECT_PERCENT("stat.effect.percent"),
    /** A stat multiplier, {@code {0}} is the factor and {@code {1}} the stat. */
    STAT_EFFECT_MULTIPLY("stat.effect.multiply"),

    /** Stat name: downward acceleration. */
    STAT_GRAVITY("stat.gravity"),
    /** Stat name: the speed a flap sets. */
    STAT_FLAP_VELOCITY("stat.flap_velocity"),
    /** Stat name: terminal velocity. */
    STAT_MAX_FALL_SPEED("stat.max_fall_speed"),
    /** Stat name: how fast the world scrolls. */
    STAT_SCROLL_SPEED("stat.scroll_speed"),
    /** Stat name: the height of a gate gap. */
    STAT_GAP_SIZE("stat.gap_size"),
    /** Stat name: the distance between gates. */
    STAT_GATE_INTERVAL("stat.gate_interval"),
    /** Stat name: the size of the bird hitbox. */
    STAT_HITBOX_SCALE("stat.hitbox_scale"),
    /** Stat name: points per gate. */
    STAT_SCORE_MULT("stat.score_mult"),
    /** Stat name: coin reward multiplier. */
    STAT_COIN_MULT("stat.coin_mult"),
    /** Stat name: XP reward multiplier. */
    STAT_XP_MULT("stat.xp_mult"),
    /** Stat name: coins spawned per scoring gate. */
    STAT_COIN_SPAWN_RATE("stat.coin_spawn_rate"),
    /** Stat name: the radius coins are attracted from. */
    STAT_MAGNET_RADIUS("stat.magnet_radius"),
    /** Stat name: ability cooldown multiplier. */
    STAT_ABILITY_COOLDOWN_MULT("stat.ability_cooldown_mult"),
    /** Stat name: ability duration multiplier. */
    STAT_ABILITY_DURATION_MULT("stat.ability_duration_mult"),
    /** Stat name: shield charges at run start. */
    STAT_SHIELD_CHARGES("stat.shield_charges"),
    /** Stat name: revives at run start. */
    STAT_REVIVES("stat.revives"),
    /** Stat name: the chance an obstacle moves. */
    STAT_MOVING_CHANCE("stat.moving_chance"),
    /** Stat name: how fast a moving obstacle travels. */
    STAT_OSCILLATION_SPEED("stat.oscillation_speed"),
    /** Stat name: the scale applied to the world clock. */
    STAT_TIME_SCALE("stat.time_scale"),

    /** Unlock condition: owned from the first run. */
    UNLOCK_DEFAULT("unlock.default"),
    /** Unlock condition: play {@code {0}} runs. */
    UNLOCK_RUNS("unlock.runs"),
    /** Unlock condition: pass {@code {0}} gates in one run. */
    UNLOCK_BEST_GATES("unlock.best_gates"),
    /** Unlock condition: score {@code {0}} points in one run. */
    UNLOCK_BEST_POINTS("unlock.best_points"),
    /** Unlock condition: pass {@code {0}} gates in total. */
    UNLOCK_TOTAL_GATES("unlock.total_gates"),
    /** Unlock condition: reach level {@code {0}}. */
    UNLOCK_LEVEL("unlock.level"),
    /** Unlock condition: earn {@code {0}} coins in total. */
    UNLOCK_COINS_EARNED("unlock.coins_earned_total"),
    /** Unlock condition: complete the challenge {@code {0}}. */
    UNLOCK_CHALLENGE("unlock.challenge"),
    /** Unlock condition: earn the achievement {@code {0}}. */
    UNLOCK_ACHIEVEMENT("unlock.achievement"),
    /** Unlock condition: clear the world {@code {0}}. */
    UNLOCK_WORLD_CLEARED("unlock.world_cleared"),
    /** Unlock condition: prestige {@code {0}} times. */
    UNLOCK_PRESTIGE("unlock.prestige"),
    /** Unlock condition: own {@code {0}} per cent of the {@code {1}}. */
    UNLOCK_COLLECTION("unlock.collection"),
    /** Unlock condition: a counter {@code {0}} reaches {@code {1}}. */
    UNLOCK_COUNTER("unlock.counter"),
    /** Unlock condition: both halves, {@code {0}} and {@code {1}}. */
    UNLOCK_ALL_OF("unlock.all_of"),

    /** Bird archetype: the upstream feel. */
    ARCHETYPE_BALANCED("archetype.balanced"),
    /** Bird archetype: light and fast. */
    ARCHETYPE_SWIFT("archetype.swift"),
    /** Bird archetype: heavy, low terminal velocity. */
    ARCHETYPE_HEAVY("archetype.heavy"),
    /** Bird archetype: defensive. */
    ARCHETYPE_GUARDIAN("archetype.guardian"),
    /** Bird archetype: high risk, high reward. */
    ARCHETYPE_GAMBLER("archetype.gambler"),
    /** Bird archetype: ability focused. */
    ARCHETYPE_MYSTIC("archetype.mystic"),
    /** Bird archetype: scales with owned upgrades. */
    ARCHETYPE_FORGE("archetype.forge"),

    /** Draft overlay: the title over the cards. */
    DRAFT_TITLE("draft.title"),
    /** Draft overlay: which draft this is, {@code {0}} of {@code {1}} at gate {@code {2}}. */
    DRAFT_SUBTITLE("draft.subtitle"),
    /** Draft overlay: the key hint under the cards. */
    DRAFT_HINT("draft.hint"),
    /** Draft overlay: take nothing. */
    DRAFT_SKIP("draft.skip"),
    /** Draft card: the stack it would be, {@code {0}} of {@code {1}}. */
    DRAFT_STACKS("draft.stacks"),
    /** Draft card: the set bonus taking it completes, {@code {0}}. */
    DRAFT_SYNERGY("draft.synergy"),
    /** Draft overlay: the line under the resume countdown when nothing was taken. */
    DRAFT_RESUME("draft.resume"),

    /** Rarity of a run modifier: common. */
    RARITY_COMMON("rarity.common"),
    /** Rarity of a run modifier: rare. */
    RARITY_RARE("rarity.rare"),
    /** Rarity of a run modifier: epic. */
    RARITY_EPIC("rarity.epic"),
    /** Rarity of a run modifier: legendary. */
    RARITY_LEGENDARY("rarity.legendary"),

    /** Modifier tag: coins and everything that pays. */
    MODIFIER_TAG_ECONOMY("modifier.tag.economy"),
    /** Modifier tag: scroll speed and the score that comes with it. */
    MODIFIER_TAG_SPEED("modifier.tag.speed"),
    /** Modifier tag: shields and revives. */
    MODIFIER_TAG_DEFENSE("modifier.tag.defense"),
    /** Modifier tag: hitbox, gaps and clean gates. */
    MODIFIER_TAG_PRECISION("modifier.tag.precision"),
    /** Modifier tag: ability timings and slower obstacles. */
    MODIFIER_TAG_TEMPO("modifier.tag.tempo"),
    /** Modifier tag: power bought with danger. */
    MODIFIER_TAG_RISK("modifier.tag.risk"),
    /** Modifier tag: power bought with greed. */
    MODIFIER_TAG_GREED("modifier.tag.greed"),

    /** HUD: one taken modifier, {@code {0}} its name and {@code {1}} its stacks. */
    HUD_MODIFIER_STACK("hud.modifier.stack"),
    /** HUD: the coins one clean-gate streak step pays, {@code {0}}. */
    HUD_STREAK_BONUS("hud.streak_bonus"),

    /** Run summary: the section listing the build the run ended with. */
    SUMMARY_SECTION_BUILD("summary.section.build"),
    /** Run summary: how many stacks of a modifier were taken, {@code {0}}. */
    SUMMARY_STACKS("summary.stacks"),
    /** Run summary: the badge of an activated set bonus. */
    SUMMARY_SYNERGY("summary.synergy"),
    /** Run summary: drafts did not open because {@code {0}} is not unlocked. */
    SUMMARY_MODIFIERS_LOCKED("summary.modifiers_locked"),
    /** Coin breakdown: the extra streak coins the taken modifiers paid. */
    REWARD_STREAK_BONUS("reward.streak_bonus"),

    /** Statistics: the lifetime group about drafted builds. */
    STATS_GROUP_BUILDS("stats.group.builds"),
    /** Statistics: modifiers taken across every run. */
    STATS_MODIFIERS_TAKEN("stats.modifiers_taken"),
    /** Statistics: synergies activated across every run. */
    STATS_SYNERGIES_ACTIVATED("stats.synergies_activated"),

    /** Main menu: open the challenges (M8). */
    MENU_CHALLENGES("menu.challenges"),
    /** Main menu: open the achievements (M8). */
    MENU_ACHIEVEMENTS("menu.achievements"),
    /** Challenges screen: title. */
    CHALLENGES_TITLE("challenges.title"),
    /** Challenges screen: the world a challenge is played in, {@code {0}} its name (E6). */
    CHALLENGES_WORLD("challenges.world"),
    /** Challenges screen: the tier a challenge is played at, {@code {0}} its name. */
    CHALLENGES_TIER("challenges.tier"),
    /** Challenges screen: the rule line, {@code {0}} the rules in words. */
    CHALLENGES_RULES("challenges.rules"),
    /** Challenges screen: a challenge with no flag, effect or forced card. */
    CHALLENGES_RULES_NONE("challenges.rules.none"),
    /** Challenges screen: a forced modifier, {@code {0}} its name. */
    CHALLENGES_RULE_MODIFIER("challenges.rule.modifier"),
    /** Challenges screen: the run streams one authored pattern. */
    CHALLENGES_RULE_PATTERN("challenges.rule.pattern"),
    /** Challenges screen: the challenge's own boss, {@code {0}} its gate. */
    CHALLENGES_RULE_BOSS("challenges.rule.boss"),
    /** Challenges screen: the objective line, {@code {0}} the objective in words. */
    CHALLENGES_OBJECTIVE("challenges.objective"),
    /** Challenges screen: the record, {@code {0}} best gates and {@code {1}} attempts. */
    CHALLENGES_RECORD("challenges.record"),
    /** Challenges screen: no attempt recorded. */
    CHALLENGES_RECORD_NONE("challenges.record.none"),
    /** Challenges screen: the objective has been met at least once. */
    CHALLENGES_COMPLETED("challenges.completed"),
    /** Challenges screen: what the first completion pays, {@code {0}} the list (E11). */
    CHALLENGES_REWARDS("challenges.rewards"),
    /** Challenges screen: a coin reward, {@code {0}} the amount. */
    CHALLENGES_REWARD_COINS("challenges.reward.coins"),
    /** Challenges screen: how a locked challenge opens, {@code {0}} the condition. */
    CHALLENGES_LOCKED("challenges.locked"),
    /** Challenges screen: a locked entry of the list, {@code {0}} its name. */
    CHALLENGES_LOCKED_ENTRY("challenges.locked_entry"),
    /** Challenges screen: the Play button of a locked challenge. */
    CHALLENGES_LOCKED_TITLE("challenges.locked_title"),
    /** Challenges screen: a record that has met its objective, {@code {0}} the record. */
    CHALLENGES_COMPLETED_ENTRY("challenges.completed_entry"),
    /** Challenges screen: start the focused challenge. */
    CHALLENGES_PLAY("challenges.play"),
    /** Objective in words: pass {@code {0}} gates in one run. */
    OBJECTIVE_SURVIVE_GATES("objective.survive_gates"),
    /** Objective in words: stay alive for {@code {0}} ticks in one run. */
    OBJECTIVE_SURVIVE_TICKS("objective.survive_ticks"),
    /** Objective in words: pick up {@code {0}} coins in one run. */
    OBJECTIVE_COLLECT_COINS("objective.collect_coins"),
    /** Objective in words: score {@code {0}} points in one run. */
    OBJECTIVE_REACH_POINTS("objective.reach_points"),
    /** Objective in words: survive the challenge's boss. */
    OBJECTIVE_BOSS_CLEARED("objective.boss_cleared"),
    /** Achievements screen: title. */
    ACHIEVEMENTS_TITLE("achievements.title"),
    /** Achievements screen: the achievements tab. */
    ACHIEVEMENTS_TAB_ACHIEVEMENTS("achievements.tab.achievements"),
    /** Achievements screen: the milestones tab (D13). */
    ACHIEVEMENTS_TAB_MILESTONES("achievements.tab.milestones"),
    /** Achievements screen: the collections tab (D13). */
    ACHIEVEMENTS_TAB_COLLECTIONS("achievements.tab.collections"),
    /** Achievements screen: how many are held, {@code {0}} of {@code {1}}. */
    ACHIEVEMENTS_COUNT("achievements.count"),
    /** Achievements screen: the name of a hidden achievement not held yet. */
    ACHIEVEMENTS_HIDDEN_NAME("achievements.hidden.name"),
    /** Achievements screen: the description of a hidden achievement not held yet. */
    ACHIEVEMENTS_HIDDEN_DESC("achievements.hidden.desc"),
    /** Achievements screen: when it fired, {@code {0}} the date. */
    ACHIEVEMENTS_UNLOCKED_AT("achievements.unlocked_at"),
    /** Achievements screen: what it pays, {@code {0}} the coins. */
    ACHIEVEMENTS_REWARD("achievements.reward"),
    /** Milestones tab: the heading over the next thresholds. */
    MILESTONES_NEXT("milestones.next"),
    /** Milestones tab: a level reward, {@code {0}} the level and {@code {1}} the coins. */
    MILESTONES_LEVEL_REWARD("milestones.level_reward"),
    /** Milestones tab: a bar's value, {@code {0}} of {@code {1}}. */
    MILESTONES_PROGRESS("milestones.progress"),
    /** Milestones tab: nothing left to reach. */
    MILESTONES_NONE("milestones.none"),
    /** Collections tab: the birds row. */
    COLLECTIONS_BIRDS("collections.birds"),
    /** Collections tab: the abilities row. */
    COLLECTIONS_ABILITIES("collections.abilities"),
    /** Collections tab: the worlds row. */
    COLLECTIONS_WORLDS("collections.worlds"),
    /** Collections tab: the challenges row. */
    COLLECTIONS_CHALLENGES("collections.challenges"),
    /** Collections tab: the palettes row. */
    COLLECTIONS_COSMETICS("collections.cosmetics"),
    /** Collections tab: the achievements row. */
    COLLECTIONS_ACHIEVEMENTS("collections.achievements"),
    /** Collections tab: the upgrade levels row. */
    COLLECTIONS_UPGRADES("collections.upgrades"),
    /** Collections tab: every category at once. */
    COLLECTIONS_ALL("collections.all"),
    /** Collections tab: a row's value, {@code {0}} owned of {@code {1}}, {@code {2}} the percentage. */
    COLLECTIONS_VALUE("collections.value"),
    /** Boss banner: the heading (M8). */
    BOSS_TITLE("boss.title"),
    /** Boss banner: the warning countdown, {@code {0}} the boss and {@code {1}} the seconds. */
    BOSS_WARNING("boss.warning"),
    /** Boss banner: the fight, {@code {0}} the boss and {@code {1}} the seconds left. */
    BOSS_FIGHT("boss.fight"),
    /** Boss banner: the boss was survived, {@code {0}} the boss. */
    BOSS_CLEARED("boss.cleared"),
    /** HUD: the boss warning countdown, {@code {0}} the seconds. */
    HUD_BOSS_WARNING("hud.boss.warning"),
    /** HUD: the boss survival countdown, {@code {0}} the seconds. */
    HUD_BOSS_FIGHT("hud.boss.fight"),
    /** HUD: a gate objective, {@code {0}} of {@code {1}} gates. */
    HUD_OBJECTIVE_GATES("hud.objective.gates"),
    /** HUD: a tick objective, {@code {0}} of {@code {1}} ticks. */
    HUD_OBJECTIVE_TICKS("hud.objective.ticks"),
    /** HUD: a coin objective, {@code {0}} of {@code {1}} coins. */
    HUD_OBJECTIVE_COINS("hud.objective.coins"),
    /** HUD: a point objective, {@code {0}} of {@code {1}} points. */
    HUD_OBJECTIVE_POINTS("hud.objective.points"),
    /** HUD: a boss objective. */
    HUD_OBJECTIVE_BOSS("hud.objective.boss"),
    /** HUD: the objective was met. */
    HUD_OBJECTIVE_COMPLETE("hud.objective.complete"),
    /** Result row: the challenge objective. */
    STAT_OBJECTIVE("stat.objective"),
    /** Result value: the objective was met. */
    STAT_OBJECTIVE_MET("stat.objective.met"),
    /** Result value: the objective was not met. */
    STAT_OBJECTIVE_MISSED("stat.objective.missed"),
    /** Result row: the boss encounter. */
    STAT_BOSS("stat.boss"),
    /** Result value: the boss was survived. */
    STAT_BOSS_CLEARED("stat.boss.cleared"),
    /** Result value: the furthest boss phase reached, {@code {0}}. */
    STAT_BOSS_PHASE("stat.boss.phase"),
    /** Game-over line after a first challenge completion, {@code {0}} the coins (E11). */
    GAMEOVER_CHALLENGE_COMPLETED("gameover.challenge_completed"),
    /** Run summary row: the challenge played, {@code {0}} its name. */
    SUMMARY_CHALLENGE("summary.challenge"),
    /** Run summary row: what the first completion paid, {@code {0}} the list. */
    SUMMARY_FIRST_COMPLETION("summary.first_completion"),
    /** Run summary section: the achievements this run unlocked. */
    SUMMARY_SECTION_ACHIEVEMENTS("summary.section.achievements"),
    /** Toast: an achievement fired, {@code {0}} its name. */
    TOAST_ACHIEVEMENT("toast.achievement"),
    /** Toast: an achievement fired and paid, {@code {0}} its name and {@code {1}} the coins. */
    TOAST_ACHIEVEMENT_COINS("toast.achievement_coins"),
    /** Toast: something was granted, {@code {0}} its name. */
    TOAST_UNLOCK_GRANTED("toast.unlock_granted"),
    /** Toast: a challenge was completed for the first time, {@code {0}} its name. */
    TOAST_CHALLENGE_COMPLETED("toast.challenge_completed"),
    /** Toast: the objective was met mid-run. */
    TOAST_OBJECTIVE_MET("toast.objective_met"),

    /** Prestige panel: the statistics group about prestige (M9). */
    PRESTIGE_GROUP("prestige.group"),
    /** Prestige panel: how many times the profile has prestiged (M9). */
    PRESTIGE_COUNT("prestige.count"),
    /** Prestige panel: the permanent coin bonus the prestiges banked (M9). */
    PRESTIGE_BONUS("prestige.bonus"),
    /** Prestige value: the permanent bonus, {@code {0}} is the percentage (M9). */
    PRESTIGE_BONUS_VALUE("prestige.bonus.value"),
    /** Prestige panel: the requirement row (M9). */
    PRESTIGE_REQUIREMENT("prestige.requirement"),
    /** Prestige panel: what a prestige keeps (M9). */
    PRESTIGE_KEEPS("prestige.keeps"),
    /** Prestige panel: what a prestige resets (M9). */
    PRESTIGE_RESETS("prestige.resets"),
    /** Prestige panel: the reset list in words (M9). */
    PRESTIGE_RESETS_LIST("prestige.resets_list"),
    /** Prestige keep: every owned bird (M9). */
    PRESTIGE_KEEP_BIRDS("prestige.keep.birds"),
    /** Prestige keep: every unlocked achievement (M9). */
    PRESTIGE_KEEP_ACHIEVEMENTS("prestige.keep.achievements"),
    /** Prestige keep: every owned cosmetic (M9). */
    PRESTIGE_KEEP_COSMETICS("prestige.keep.cosmetics"),
    /** Prestige keep: the lifetime statistics (M9). */
    PRESTIGE_KEEP_STATISTICS("prestige.keep.statistics"),
    /** Prestige action: the first step of the confirm (M9). */
    PRESTIGE_ACTION("prestige.action"),
    /** Prestige action: the second step of the confirm (M9). */
    PRESTIGE_CONFIRM("prestige.confirm"),
    /** Prestige refusal: the level is short, {@code {0}} is the level it opens at (M9). */
    PRESTIGE_NEEDS_LEVEL("prestige.needs_level"),
    /** Prestige refusal: the cap is reached (M9). */
    PRESTIGE_MAXED("prestige.maxed"),
    /** Main menu badge: how many times the player has prestiged, {@code {0}} the count (M9). */
    MENU_PRESTIGE_BADGE("menu.prestige_badge"),
    /** Toast: a prestige was performed, {@code {0}} the count (M9). */
    TOAST_PRESTIGE("toast.prestige");

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
