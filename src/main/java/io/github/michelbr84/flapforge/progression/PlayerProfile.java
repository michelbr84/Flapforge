package io.github.michelbr84.flapforge.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Everything the game remembers about a player (§4 "SaveData v1", D13, D15). This is the single
 * persisted POJO: {@code save.json} carries an envelope
 * ({@code io.github.michelbr84.flapforge.persistence.SaveFile}) and one of these under
 * {@code profile}.
 *
 * <p>The shape follows the persistence rules of D15 to the letter, because a save must bind with
 * plain Gson on every JDK the game ships on and must stay readable by a human: fields are public,
 * the class has a no-argument constructor, every field initialiser <em>is</em> the default a fresh
 * installation starts from, and the value types are limited to numbers, booleans, strings,
 * {@code List<String>}, string-keyed maps and small nested POJOs. There are no enum keys and no
 * {@code java.time} types anywhere in the tree.
 *
 * <p>{@link #normalize(ProfileSchema)} is the single repair point. It runs after every load and it
 * does two things: it replaces nulls and impossible numbers left by a hand edit or an older build,
 * and it enforces the E15 consistency rules — {@code unlocked} gains the {@code ability:} /
 * {@code challenge:} / {@code tree:} / {@code bird:} / {@code cosmetic:} / {@code world:} /
 * {@code tier:} ids implied by {@code abilityLevels}, {@code challenges}, {@code upgrades} and
 * {@code selected}, and any {@code selected.*} id the content no longer knows falls back to its
 * default. Upgrade nodes are <em>not</em> unlockables (E21): {@code unlocked} never contains an
 * {@code upgrade:} id, node ownership lives only in {@link #upgrades}, and what an owned node
 * implies is the unlock of its tree.
 */
public final class PlayerProfile {

    /** The currency every build ships with ({@code economy.json.currencies}). */
    public static final String CURRENCY_COINS = "coins";
    /** Default selected bird. */
    public static final String DEFAULT_BIRD = "classic";
    /** Default selected palette. */
    public static final String DEFAULT_PALETTE = "default";
    /** Default selected world. */
    public static final String DEFAULT_WORLD = "green_fields";
    /** Default selected difficulty tier. */
    public static final String DEFAULT_TIER = "normal";
    /** Default equipped active ability. */
    public static final String DEFAULT_ACTIVE_ABILITY = "double_flap";
    /** Ability level a player may buy up to before an {@code ability_cap} grant (E3). */
    public static final int DEFAULT_ABILITY_LEVEL_CAP = 2;
    /** Highest {@link #passiveSlotBonus} any number of grants may reach (E3). */
    public static final int MAX_PASSIVE_SLOT_BONUS = 1;
    /** Highest {@link #prestigeCount} the economy allows (E4). */
    public static final int MAX_PRESTIGE_COUNT = 5;

    /**
     * The ids a fresh profile owns (E18), minus the modifiers that carry
     * {@code "unlock": "default"} — those are content and are granted by the unlock evaluator as
     * soon as {@code modifiers.json} exists (M6).
     */
    public static final List<String> DEFAULT_UNLOCKED = List.of(
            "bird:" + DEFAULT_BIRD,
            "ability:" + DEFAULT_ACTIVE_ABILITY,
            "world:" + DEFAULT_WORLD,
            "tree:flight",
            "tier:" + DEFAULT_TIER,
            "cosmetic:" + DEFAULT_BIRD + ":" + DEFAULT_PALETTE);

    /** When the profile was created, from the injected {@code TimeSource}. */
    public long createdAtEpochMs;
    /** How many times the player has prestiged (E4, E23). */
    public int prestigeCount;
    /** Lifetime totals frozen at the last prestige (E23). */
    public PrestigeBaseline prestigeBaseline = new PrestigeBaseline();
    /**
     * Extra passive ability slots granted by upgrades (E3), at most
     * {@link #MAX_PASSIVE_SLOT_BONUS}.
     */
    public int passiveSlotBonus;
    /** Balance per currency; never negative. */
    public Map<String, Long> wallet = defaultWallet();
    /** Lifetime experience since the last prestige. */
    public long xp;
    /** Level derived from {@link #xp} the last time it changed; never below 1. */
    public int level = 1;
    /** Highest ability level the player may buy (E3). */
    public int abilityLevelCap = DEFAULT_ABILITY_LEVEL_CAP;
    /** Namespaced unlock ids the player owns, in the order they were granted. */
    public List<String> unlocked = new ArrayList<>(DEFAULT_UNLOCKED);
    /** Owned level per upgrade node id (bare ids, E21). */
    public Map<String, Integer> upgrades = new LinkedHashMap<>();
    /** Owned level per ability id. */
    public Map<String, Integer> abilityLevels = new LinkedHashMap<>();
    /** Alias and refund ids already reconciled, so a refund is paid exactly once (E21). */
    public List<String> reconciled = new ArrayList<>();
    /** Unlocked achievements by id. */
    public Map<String, AchievementRecord> achievements = new LinkedHashMap<>();
    /** Per-challenge progress by id. */
    public Map<String, ChallengeRecord> challenges = new LinkedHashMap<>();
    /** The daily pick in force, persisted so it survives new unlocks (E27). */
    public DailyRecord daily = new DailyRecord();
    /** Lifetime statistics. */
    public Statistics statistics = new Statistics();
    /** What the player has equipped. */
    public Selection selected = new Selection();
    /** Seed of the last run, so a seeded retry can be offered. */
    public long lastSeed;

    /** Creates a profile carrying every default. Gson binds onto this constructor. */
    public PlayerProfile() {
    }

    /**
     * A fresh profile.
     *
     * @param createdAtEpochMs creation timestamp from the injected time source
     * @return the profile
     */
    public static PlayerProfile fresh(long createdAtEpochMs) {
        PlayerProfile profile = new PlayerProfile();
        profile.createdAtEpochMs = createdAtEpochMs;
        return profile;
    }

    private static Map<String, Long> defaultWallet() {
        Map<String, Long> wallet = new LinkedHashMap<>();
        wallet.put(CURRENCY_COINS, 0L);
        return wallet;
    }

    /**
     * Repairs the profile with no knowledge of the content registries: nulls, impossible numbers
     * and the E15 rules that need no id table.
     *
     * @return {@code this}
     */
    public PlayerProfile normalize() {
        return normalize(ProfileSchema.permissive());
    }

    /**
     * Repairs the profile against the content the build knows (E15, E21).
     *
     * @param schema the id tables and defaults; {@link ProfileSchema#permissive()} accepts every id
     * @return {@code this}
     */
    public PlayerProfile normalize(ProfileSchema schema) {
        normalizeAndReport(schema);
        return this;
    }

    /**
     * Repairs the profile and reports what had to be repaired, one English line per repair, so a
     * loader or {@code SaveInspector} can show the player what changed in their save.
     *
     * @param schema the id tables and defaults, or {@code null} for
     *     {@link ProfileSchema#permissive()}
     * @return the repairs, in the order they were made; empty when the profile was already sound
     */
    public List<String> normalizeAndReport(ProfileSchema schema) {
        ProfileSchema known = schema == null ? ProfileSchema.permissive() : schema;
        List<String> repairs = new ArrayList<>();
        repairNulls(repairs);
        repairNumbers(known, repairs);
        repairUpgrades(known, repairs);
        repairSelection(known, repairs);
        repairImpliedUnlocks(known, repairs);
        return repairs;
    }

    private void repairNulls(List<String> repairs) {
        if (prestigeBaseline == null) {
            prestigeBaseline = new PrestigeBaseline();
            repairs.add("prestigeBaseline was missing");
        }
        prestigeBaseline.normalize();
        if (wallet == null) {
            wallet = defaultWallet();
            repairs.add("wallet was missing");
        }
        if (!wallet.containsKey(CURRENCY_COINS)) {
            wallet.put(CURRENCY_COINS, 0L);
            repairs.add("wallet had no " + CURRENCY_COINS + " balance");
        }
        if (unlocked == null) {
            unlocked = new ArrayList<>(DEFAULT_UNLOCKED);
            repairs.add("unlocked was missing");
        }
        if (upgrades == null) {
            upgrades = new LinkedHashMap<>();
            repairs.add("upgrades was missing");
        }
        if (abilityLevels == null) {
            abilityLevels = new LinkedHashMap<>();
            repairs.add("abilityLevels was missing");
        }
        if (reconciled == null) {
            reconciled = new ArrayList<>();
            repairs.add("reconciled was missing");
        }
        if (achievements == null) {
            achievements = new LinkedHashMap<>();
            repairs.add("achievements was missing");
        }
        achievements.values().removeIf(record -> record == null);
        if (challenges == null) {
            challenges = new LinkedHashMap<>();
            repairs.add("challenges was missing");
        }
        challenges.values().removeIf(record -> record == null);
        for (ChallengeRecord record : challenges.values()) {
            record.normalize();
        }
        if (daily == null) {
            daily = new DailyRecord();
            repairs.add("daily was missing");
        }
        daily.normalize();
        if (statistics == null) {
            statistics = new Statistics();
            repairs.add("statistics was missing");
        }
        statistics.normalize();
        if (selected == null) {
            selected = new Selection();
            repairs.add("selected was missing");
        }
        selected.normalize();
    }

    private void repairNumbers(ProfileSchema schema, List<String> repairs) {
        if (createdAtEpochMs < 0) {
            repairs.add("createdAtEpochMs was negative");
            createdAtEpochMs = 0;
        }
        if (prestigeCount < 0 || prestigeCount > MAX_PRESTIGE_COUNT) {
            repairs.add("prestigeCount " + prestigeCount + " is out of range");
            prestigeCount = prestigeCount < 0 ? 0 : MAX_PRESTIGE_COUNT;
        }
        if (passiveSlotBonus < 0 || passiveSlotBonus > MAX_PASSIVE_SLOT_BONUS) {
            repairs.add("passiveSlotBonus " + passiveSlotBonus + " is out of range");
            passiveSlotBonus = passiveSlotBonus < 0 ? 0 : MAX_PASSIVE_SLOT_BONUS;
        }
        if (xp < 0) {
            repairs.add("xp was negative");
            xp = 0;
        }
        if (level < 1) {
            repairs.add("level was below 1");
            level = 1;
        }
        if (abilityLevelCap < DEFAULT_ABILITY_LEVEL_CAP) {
            repairs.add("abilityLevelCap was below " + DEFAULT_ABILITY_LEVEL_CAP);
            abilityLevelCap = DEFAULT_ABILITY_LEVEL_CAP;
        }
        // E3: the cap is the base plus the ability_cap grants the trees ship, so a hand-edited or
        // corrupted value above that would offer ability levels the abilities do not have. A
        // schema without content reports 0 and nothing is clamped from above.
        int ceiling = schema.maxAbilityLevelCap();
        if (ceiling >= DEFAULT_ABILITY_LEVEL_CAP && abilityLevelCap > ceiling) {
            repairs.add("abilityLevelCap " + abilityLevelCap + " is above the content ceiling "
                    + ceiling);
            abilityLevelCap = ceiling;
        }
        for (Map.Entry<String, Long> entry : wallet.entrySet()) {
            Long value = entry.getValue();
            if (value == null || value < 0) {
                repairs.add("wallet." + entry.getKey() + " was negative or missing");
                entry.setValue(0L);
            }
        }
        dropNonPositive(upgrades, "upgrades", repairs);
        dropNonPositive(abilityLevels, "abilityLevels", repairs);
        List<String> ids = dedupe(unlocked);
        if (ids.size() != unlocked.size()) {
            repairs.add("unlocked held duplicate or blank ids");
        }
        unlocked = ids;
        List<String> reconciledIds = dedupe(reconciled);
        if (reconciledIds.size() != reconciled.size()) {
            repairs.add("reconciled held duplicate or blank ids");
        }
        reconciled = reconciledIds;
    }

    private static void dropNonPositive(Map<String, Integer> levels, String name,
            List<String> repairs) {
        levels.entrySet().removeIf(entry -> {
            boolean drop = entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue() <= 0;
            if (drop) {
                repairs.add(name + " held a level of zero or less for " + entry.getKey());
            }
            return drop;
        });
    }

    private static List<String> dedupe(List<String> ids) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                unique.add(id);
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * Drops owned levels of upgrade nodes the build no longer ships (E21).
     *
     * <p>An unknown node key is not harmless: {@link #upgradeLevelsTotal()} feeds Cinder's
     * {@code BIRD_SYNERGY} layer, so a stale or hand-edited entry inflates the synergy of every
     * run. The supported way to carry a renamed node forward is {@code aliases.json}, which runs
     * before this — a key that survives it is a node this build does not have.
     */
    private void repairUpgrades(ProfileSchema schema, List<String> repairs) {
        upgrades.keySet().removeIf(nodeId -> {
            boolean drop = !schema.knowsUpgrade(nodeId);
            if (drop) {
                repairs.add("upgrades dropped the unknown node " + nodeId);
            }
            return drop;
        });
    }

    private void repairSelection(ProfileSchema schema, List<String> repairs) {
        if (!schema.knowsBird(selected.birdId)) {
            repairs.add("selected.birdId " + selected.birdId + " is unknown");
            selected.birdId = schema.defaultBirdId();
            selected.paletteId = schema.defaultPaletteId();
        }
        if (!schema.knowsPalette(selected.birdId, selected.paletteId)) {
            repairs.add("selected.paletteId " + selected.paletteId + " is unknown for "
                    + selected.birdId);
            selected.paletteId = schema.defaultPaletteId();
        }
        if (!schema.knowsWorld(selected.worldId)) {
            repairs.add("selected.worldId " + selected.worldId + " is unknown");
            selected.worldId = schema.defaultWorldId();
        }
        if (!schema.knowsTier(selected.tierId)) {
            repairs.add("selected.tierId " + selected.tierId + " is unknown");
            selected.tierId = schema.defaultTierId();
        }
        if (selected.activeAbilityId != null && !schema.knowsAbility(selected.activeAbilityId)) {
            repairs.add("selected.activeAbilityId " + selected.activeAbilityId + " is unknown");
            selected.activeAbilityId = schema.defaultActiveAbilityId();
        }
        List<String> passives = new ArrayList<>();
        for (String id : selected.passiveAbilityIds) {
            if (schema.knowsAbility(id)) {
                passives.add(id);
            } else {
                repairs.add("selected.passiveAbilityIds dropped the unknown id " + id);
            }
        }
        selected.passiveAbilityIds = passives;
    }

    /**
     * E15: an id the profile <em>uses</em> must be an id the profile <em>owns</em>. Ownership of an
     * upgrade node implies the unlock of its tree, never {@code upgrade:<node>} (E21).
     */
    private void repairImpliedUnlocks(ProfileSchema schema, List<String> repairs) {
        LinkedHashSet<String> owned = new LinkedHashSet<>(unlocked);
        for (String abilityId : abilityLevels.keySet()) {
            grant(owned, "ability:" + abilityId, "abilityLevels." + abilityId, repairs);
        }
        for (String challengeId : challenges.keySet()) {
            grant(owned, "challenge:" + challengeId, "challenges." + challengeId, repairs);
        }
        for (String nodeId : upgrades.keySet()) {
            String tree = schema.treeOf(nodeId);
            if (tree != null) {
                grant(owned, "tree:" + tree, "upgrades." + nodeId, repairs);
            }
        }
        grant(owned, "bird:" + selected.birdId, "selected.birdId", repairs);
        grant(owned, "cosmetic:" + selected.birdId + ":" + selected.paletteId,
                "selected.paletteId", repairs);
        grant(owned, "world:" + selected.worldId, "selected.worldId", repairs);
        grant(owned, "tier:" + selected.tierId, "selected.tierId", repairs);
        if (selected.activeAbilityId != null && !selected.activeAbilityId.isBlank()) {
            grant(owned, "ability:" + selected.activeAbilityId, "selected.activeAbilityId",
                    repairs);
        }
        for (String id : selected.passiveAbilityIds) {
            grant(owned, "ability:" + id, "selected.passiveAbilityIds", repairs);
        }
        if (owned.removeIf(id -> id.startsWith("upgrade:"))) {
            repairs.add("unlocked held upgrade: ids, which are not unlockables (E21)");
        }
        unlocked = new ArrayList<>(owned);
    }

    private static void grant(LinkedHashSet<String> owned, String unlockId, String because,
            List<String> repairs) {
        if (owned.add(unlockId)) {
            repairs.add("unlocked gained " + unlockId + " implied by " + because);
        }
    }

    /**
     * Whether the player owns an unlock id.
     *
     * @param unlockId the namespaced id
     * @return {@code true} when {@link #unlocked} contains it
     */
    public boolean isUnlocked(String unlockId) {
        return unlocked.contains(unlockId);
    }

    /**
     * Grants an unlock id if it is not owned yet.
     *
     * @param unlockId the namespaced id
     * @return {@code true} when the id was added by this call
     */
    public boolean unlock(String unlockId) {
        if (unlockId == null || unlockId.isBlank() || unlocked.contains(unlockId)) {
            return false;
        }
        return unlocked.add(unlockId);
    }

    /**
     * Owned level of an upgrade node.
     *
     * @param nodeId the bare node id
     * @return the level, 0 when the node is not owned
     */
    public int upgradeLevel(String nodeId) {
        Integer owned = upgrades.get(nodeId);
        return owned == null ? 0 : owned;
    }

    /**
     * Owned level of an ability.
     *
     * @param abilityId the ability id
     * @return the level, 0 when the ability is not owned
     */
    public int abilityLevel(String abilityId) {
        Integer owned = abilityLevels.get(abilityId);
        return owned == null ? 0 : owned;
    }

    /**
     * Sum of every owned upgrade level (Cinder's synergy input, D11 {@code upgradeLevelsTotal}).
     *
     * @return the total
     */
    public int upgradeLevelsTotal() {
        int total = 0;
        for (Integer owned : upgrades.values()) {
            total += owned == null ? 0 : owned;
        }
        return total;
    }

    /**
     * The record of a challenge, created on first use.
     *
     * @param challengeId the challenge id
     * @return the record held by {@link #challenges}
     */
    public ChallengeRecord challenge(String challengeId) {
        return challenges.computeIfAbsent(challengeId, id -> new ChallengeRecord());
    }

    /**
     * A profile-root scalar an achievement counter may name (E5).
     *
     * @param name {@code level}, {@code xp} or {@code prestigeCount}
     * @return the value, or {@code null} when the name is not a root scalar
     */
    public Long rootCounter(String name) {
        if (name == null) {
            return null;
        }
        switch (name) {
            case "level":
                return (long) level;
            case "xp":
                return xp;
            case "prestigeCount":
                return (long) prestigeCount;
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        return "PlayerProfile{level=" + level + ", xp=" + xp + ", wallet=" + wallet
                + ", unlocked=" + unlocked.size() + ", runs=" + statistics.totalRuns + '}';
    }

    /**
     * Lifetime totals frozen by the last prestige, so cumulative unlocks read "since prestige"
     * (E23).
     */
    public static final class PrestigeBaseline {
        /** {@code statistics.totalRuns} at the moment of the prestige. */
        public long totalRuns;
        /** {@code statistics.totalGates} at the moment of the prestige. */
        public long totalGates;
        /** {@code statistics.coinsEarned} at the moment of the prestige. */
        public long coinsEarned;
        /** {@code statistics.bossesCleared} at the moment of the prestige. */
        public List<String> bossesCleared = new ArrayList<>();

        /** Creates an empty baseline. */
        public PrestigeBaseline() {
        }

        /** Replaces nulls and negative totals. */
        public void normalize() {
            totalRuns = Math.max(0, totalRuns);
            totalGates = Math.max(0, totalGates);
            coinsEarned = Math.max(0, coinsEarned);
            bossesCleared = bossesCleared == null ? new ArrayList<>() : dedupe(bossesCleared);
        }
    }

    /** When an achievement was unlocked. */
    public static final class AchievementRecord {
        /** Timestamp from the injected time source. */
        public long unlockedAtEpochMs;

        /** Creates an empty record. */
        public AchievementRecord() {
        }

        /**
         * Creates a record.
         *
         * @param unlockedAtEpochMs the timestamp
         */
        public AchievementRecord(long unlockedAtEpochMs) {
            this.unlockedAtEpochMs = unlockedAtEpochMs;
        }
    }

    /** Per-challenge progress. */
    public static final class ChallengeRecord {
        /** Whether the objective has ever been met. */
        public boolean completed;
        /** Best gate count reached in this challenge. */
        public long bestGates;
        /** How many times the challenge has been played to the end. */
        public long attempts;

        /** Creates an empty record. */
        public ChallengeRecord() {
        }

        /** Replaces negative counters. */
        public void normalize() {
            bestGates = Math.max(0, bestGates);
            attempts = Math.max(0, attempts);
        }
    }

    /** The daily pick in force; written once per date and reused for that date (E27). */
    public static final class DailyRecord {
        /**
         * The date the pick belongs to, as {@code yyyy-MM-dd} text (never a {@code java.time}
         * type).
         */
        public String date = "";
        /** The seed every attempt of the day uses. */
        public long seed;
        /** The world of the day. */
        public String worldId = "";
        /** The tier of the day. */
        public String tierId = "";
        /** The modifiers forced on every attempt. */
        public List<String> modifierIds = new ArrayList<>();
        /** How many attempts have been played today. */
        public long attempts;
        /** Best gate count reached today. */
        public long bestGates;

        /** Creates an empty record. */
        public DailyRecord() {
        }

        /** Replaces nulls and negative counters. */
        public void normalize() {
            date = date == null ? "" : date;
            worldId = worldId == null ? "" : worldId;
            tierId = tierId == null ? "" : tierId;
            modifierIds = modifierIds == null ? new ArrayList<>() : dedupe(modifierIds);
            attempts = Math.max(0, attempts);
            bestGates = Math.max(0, bestGates);
        }

        /**
         * Whether a pick has been made for a date.
         *
         * @param today the date to test, as {@code yyyy-MM-dd}
         * @return {@code true} when this record is the pick of that date
         */
        public boolean isFor(String today) {
            return !date.isEmpty() && date.equals(today);
        }
    }

    /** What the player has equipped. */
    public static final class Selection {
        /** Selected bird id. */
        public String birdId = DEFAULT_BIRD;
        /** Selected palette id, within {@link #birdId}. */
        public String paletteId = DEFAULT_PALETTE;
        /** Selected world id. */
        public String worldId = DEFAULT_WORLD;
        /** Selected difficulty tier id. */
        public String tierId = DEFAULT_TIER;
        /** Equipped active ability id, or {@code null} for none. */
        public String activeAbilityId = DEFAULT_ACTIVE_ABILITY;
        /** Equipped passive ability ids. */
        public List<String> passiveAbilityIds = new ArrayList<>();

        /** Creates a selection carrying the defaults. */
        public Selection() {
        }

        /** Replaces nulls; unknown ids are {@link ProfileSchema}'s business. */
        public void normalize() {
            birdId = blankToDefault(birdId, DEFAULT_BIRD);
            paletteId = blankToDefault(paletteId, DEFAULT_PALETTE);
            worldId = blankToDefault(worldId, DEFAULT_WORLD);
            tierId = blankToDefault(tierId, DEFAULT_TIER);
            if (activeAbilityId != null && activeAbilityId.isBlank()) {
                activeAbilityId = null;
            }
            passiveAbilityIds = passiveAbilityIds == null
                    ? new ArrayList<>() : dedupe(passiveAbilityIds);
        }

        private static String blankToDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
