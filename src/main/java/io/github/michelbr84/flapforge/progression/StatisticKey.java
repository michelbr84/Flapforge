package io.github.michelbr84.flapforge.progression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every counter a saved profile exposes, by name (D13). Achievements, unlock conditions and the
 * statistics screen all name a counter as text — {@code "totalGates"}, {@code "bestGatesByTier
 * .hard"}, {@code "bossClears.wind_valley"}, {@code "level"} — and this enum is the one table that
 * says which names exist and where each one reads from.
 *
 * <p>Three shapes exist. A {@link Kind#SCALAR} is a single number. A {@link Kind#MAP} is addressed
 * with a key after a dot ({@code bestGatesByWorld.green_fields}), and reading it without a key
 * yields the sum over the map. A {@link Kind#LIST} yields how many entries the list holds
 * ({@code bossesCleared} is "how many worlds have been cleared").
 *
 * <p>Most keys live under {@code statistics}; three are profile-root scalars, because E5 makes
 * {@code level}, {@code xp} and {@code prestigeCount} resolvable as lifetime counters (the
 * {@code level_10} and {@code level_25} achievements read {@code level}). Both spellings resolve —
 * {@code totalGates} and {@code statistics.totalGates} name the same counter — so content authors
 * may write either.
 */
public enum StatisticKey {

    /** Runs finished, of every mode. */
    TOTAL_RUNS("totalRuns", Kind.SCALAR),
    /** Gates passed across every run. */
    TOTAL_GATES("totalGates", Kind.SCALAR),
    /** Most gates passed in one run. */
    BEST_GATES("bestGates", Kind.SCALAR),
    /** Most gates passed in one run, per world id. */
    BEST_GATES_BY_WORLD("bestGatesByWorld", Kind.MAP),
    /** Most gates passed in one run, per tier id. */
    BEST_GATES_BY_TIER("bestGatesByTier", Kind.MAP),
    /** Points scored across every run (E1). */
    TOTAL_POINTS("totalPoints", Kind.SCALAR),
    /** Most points scored in one run (E1). */
    BEST_POINTS("bestPoints", Kind.SCALAR),
    /** Coins credited to the wallet, from every source (E32.a). */
    COINS_EARNED("coinsEarned", Kind.SCALAR),
    /** Coins spent in the shop and the trees. */
    COINS_SPENT("coinsSpent", Kind.SCALAR),
    /** Coins picked up in runs (the pickups themselves, E2). */
    COINS_COLLECTED("coinsCollected", Kind.SCALAR),
    /** Experience earned across every run. */
    XP_EARNED("xpEarned", Kind.SCALAR),
    /** Deaths per collision cause. */
    DEATHS_BY_CAUSE("deathsByCause", Kind.MAP),
    /** Activations per ability id. */
    ABILITIES_USED("abilitiesUsed", Kind.MAP),
    /** Ability activations of every kind. */
    ABILITIES_USED_TOTAL("abilitiesUsedTotal", Kind.SCALAR),
    /** Hits absorbed by a shield. */
    SHIELD_ABSORBS("shieldAbsorbs", Kind.SCALAR),
    /** Revives consumed. */
    REVIVES("revives", Kind.SCALAR),
    /** Longest clean-gate streak ever reached (D26). */
    STREAK_BEST("streakBest", Kind.SCALAR),
    /** World ids whose boss has been cleared (E26). */
    BOSSES_CLEARED("bossesCleared", Kind.LIST),
    /** How many times each world boss has been cleared. */
    BOSS_CLEARS("bossClears", Kind.MAP),
    /** Challenge completions, repeats included (E11). */
    CHALLENGES_COMPLETED("challengesCompleted", Kind.SCALAR),
    /** Daily runs played. */
    DAILIES_PLAYED("dailiesPlayed", Kind.SCALAR),
    /** How many times each modifier has been taken. */
    MODIFIERS_TAKEN("modifiersTaken", Kind.MAP),
    /** How many times each synergy has been activated. */
    SYNERGIES_ACTIVATED("synergiesActivated", Kind.MAP),
    /** Seconds spent flying (the only place playtime is kept, §4). */
    PLAYTIME_SECONDS("playtimeSeconds", Kind.SCALAR),
    /** Entries in the capped run history. */
    RUN_HISTORY("runHistory", Kind.LIST),
    /** Profile-root scalar: the current level (E5). */
    LEVEL("level", Kind.SCALAR, true),
    /** Profile-root scalar: experience since the last prestige (E5). */
    XP("xp", Kind.SCALAR, true),
    /** Profile-root scalar: how many times the player has prestiged (E5). */
    PRESTIGE_COUNT("prestigeCount", Kind.SCALAR, true);

    /** Prefix under which the statistics counters also resolve. */
    public static final String STATISTICS_PREFIX = "statistics.";

    /** The shape of a counter. */
    public enum Kind {
        /** One number. */
        SCALAR,
        /** A string-keyed map of numbers; {@code <field>.<key>} addresses one entry. */
        MAP,
        /** A list; the counter is its size. */
        LIST
    }

    private static final Map<String, StatisticKey> BY_FIELD = index();

    private final String field;
    private final Kind kind;
    private final boolean profileRoot;

    StatisticKey(String field, Kind kind) {
        this(field, kind, false);
    }

    StatisticKey(String field, Kind kind, boolean profileRoot) {
        this.field = field;
        this.kind = kind;
        this.profileRoot = profileRoot;
    }

    private static Map<String, StatisticKey> index() {
        Map<String, StatisticKey> map = new LinkedHashMap<>();
        for (StatisticKey key : values()) {
            map.put(key.field, key);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * The field name inside {@code statistics} (or the profile root for {@link #isProfileRoot()}).
     *
     * @return the field name
     */
    public String field() {
        return field;
    }

    /**
     * The shape of the counter.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Whether the counter is a map addressed with {@code <field>.<key>}.
     *
     * @return {@code true} for {@link Kind#MAP}
     */
    public boolean isMap() {
        return kind == Kind.MAP;
    }

    /**
     * Whether the counter lives on the profile root rather than under {@code statistics} (E5).
     *
     * @return {@code true} for {@link #LEVEL}, {@link #XP} and {@link #PRESTIGE_COUNT}
     */
    public boolean isProfileRoot() {
        return profileRoot;
    }

    /**
     * The canonical, fully qualified path of the counter.
     *
     * @return {@code level} for a root scalar, {@code statistics.totalGates} otherwise
     */
    public String path() {
        return profileRoot ? field : STATISTICS_PREFIX + field;
    }

    /**
     * The canonical path of one entry of a map counter.
     *
     * @param mapKey the entry key, for example a tier id
     * @return {@code statistics.bestGatesByTier.hard}
     * @throws IllegalStateException when the counter is not a map
     */
    public String path(String mapKey) {
        if (!isMap()) {
            throw new IllegalStateException(field + " is not a map counter");
        }
        return path() + '.' + mapKey;
    }

    /**
     * Resolves a counter name, with or without the {@code statistics.} prefix and with or without
     * a map key.
     *
     * @param counter the name, for example {@code bestGatesByTier.hard}
     * @return the key, or {@code null} when no counter carries that name
     */
    public static StatisticKey of(String counter) {
        if (counter == null || counter.isBlank()) {
            return null;
        }
        String name = strip(counter);
        StatisticKey direct = BY_FIELD.get(name);
        if (direct != null) {
            return direct;
        }
        int dot = name.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        StatisticKey mapped = BY_FIELD.get(name.substring(0, dot));
        return mapped != null && mapped.isMap() && dot + 1 < name.length() ? mapped : null;
    }

    /**
     * The map key a counter name addresses.
     *
     * @param counter the name, for example {@code statistics.bossClears.wind_valley}
     * @return the key ({@code wind_valley}), or {@code null} when the name addresses no entry
     */
    public static String mapKeyOf(String counter) {
        if (counter == null) {
            return null;
        }
        String name = strip(counter);
        int dot = name.indexOf('.');
        if (dot <= 0 || dot + 1 >= name.length()) {
            return null;
        }
        StatisticKey mapped = BY_FIELD.get(name.substring(0, dot));
        return mapped != null && mapped.isMap() ? name.substring(dot + 1) : null;
    }

    private static String strip(String counter) {
        String trimmed = counter.trim();
        return trimmed.startsWith(STATISTICS_PREFIX)
                ? trimmed.substring(STATISTICS_PREFIX.length()) : trimmed;
    }
}
