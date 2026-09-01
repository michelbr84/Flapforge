package io.github.michelbr84.flapforge.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The lifetime numbers of a profile (§4, D13). It is a persisted POJO like
 * {@link PlayerProfile} — public fields, a no-argument constructor, initialisers as defaults, only
 * numbers, strings, {@code List<String>} and string-keyed maps — plus the typed mutators the
 * progression pipeline writes through and the counter resolver achievements and unlock conditions
 * read through.
 *
 * <p>Maps are {@link LinkedHashMap}s and stay in insertion order: the file must not reshuffle
 * itself between saves, or every write would produce a different diff (D5).
 *
 * <p>{@link #runHistory} is capped at {@link #RUN_HISTORY_LIMIT} entries, oldest first out. The cap
 * is why the list is replaced wholesale on write (E22): merging would resurrect the runs the cap
 * dropped.
 */
public final class Statistics {

    /** How many finished runs the history keeps (D13). */
    public static final int RUN_HISTORY_LIMIT = 100;
    /** Ticks per second of the simulation, used to turn ticks into {@link #playtimeSeconds}. */
    public static final int TICKS_PER_SECOND = 60;
    /** Death cause recorded when a run ends without one (an aborted or budget-limited run). */
    public static final String CAUSE_UNKNOWN = "unknown";

    /** Runs finished, of every mode. */
    public long totalRuns;
    /** Gates passed across every run. */
    public long totalGates;
    /** Most gates passed in one run. */
    public long bestGates;
    /** Most gates passed in one run, per world id. */
    public Map<String, Long> bestGatesByWorld = new LinkedHashMap<>();
    /** Most gates passed in one run, per tier id. */
    public Map<String, Long> bestGatesByTier = new LinkedHashMap<>();
    /** Points scored across every run (E1). */
    public long totalPoints;
    /** Most points scored in one run (E1). */
    public long bestPoints;
    /** Coins credited to the wallet from every source (E32.a). */
    public long coinsEarned;
    /** Coins spent in the shop and the trees. */
    public long coinsSpent;
    /** Coins picked up in runs (E2). */
    public long coinsCollected;
    /** Experience earned across every run. */
    public long xpEarned;
    /** Deaths per collision cause. */
    public Map<String, Long> deathsByCause = new LinkedHashMap<>();
    /** Activations per ability id. */
    public Map<String, Long> abilitiesUsed = new LinkedHashMap<>();
    /** Ability activations of every kind. */
    public long abilitiesUsedTotal;
    /** Hits absorbed by a shield. */
    public long shieldAbsorbs;
    /** Revives consumed. */
    public long revives;
    /** Longest clean-gate streak ever reached (D26). */
    public long streakBest;
    /** World ids whose boss has been cleared (E26). */
    public List<String> bossesCleared = new ArrayList<>();
    /** How many times each world boss has been cleared. */
    public Map<String, Long> bossClears = new LinkedHashMap<>();
    /** Challenge completions, repeats included (E11). */
    public long challengesCompleted;
    /** Daily runs played. */
    public long dailiesPlayed;
    /** How many times each modifier has been taken. */
    public Map<String, Long> modifiersTaken = new LinkedHashMap<>();
    /** How many times each synergy has been activated. */
    public Map<String, Long> synergiesActivated = new LinkedHashMap<>();
    /** Seconds spent flying; the only place playtime is kept (§4). */
    public long playtimeSeconds;
    /** The last {@link #RUN_HISTORY_LIMIT} finished runs, oldest first. */
    public List<RunHistoryEntry> runHistory = new ArrayList<>();

    /** Creates empty statistics. Gson binds onto this constructor. */
    public Statistics() {
    }

    /** Replaces nulls, drops negative numbers and enforces the history cap. */
    public void normalize() {
        totalRuns = Math.max(0, totalRuns);
        totalGates = Math.max(0, totalGates);
        bestGates = Math.max(0, bestGates);
        totalPoints = Math.max(0, totalPoints);
        bestPoints = Math.max(0, bestPoints);
        coinsEarned = Math.max(0, coinsEarned);
        coinsSpent = Math.max(0, coinsSpent);
        coinsCollected = Math.max(0, coinsCollected);
        xpEarned = Math.max(0, xpEarned);
        abilitiesUsedTotal = Math.max(0, abilitiesUsedTotal);
        shieldAbsorbs = Math.max(0, shieldAbsorbs);
        revives = Math.max(0, revives);
        streakBest = Math.max(0, streakBest);
        challengesCompleted = Math.max(0, challengesCompleted);
        dailiesPlayed = Math.max(0, dailiesPlayed);
        playtimeSeconds = Math.max(0, playtimeSeconds);
        bestGatesByWorld = clean(bestGatesByWorld);
        bestGatesByTier = clean(bestGatesByTier);
        deathsByCause = clean(deathsByCause);
        abilitiesUsed = clean(abilitiesUsed);
        bossClears = clean(bossClears);
        modifiersTaken = clean(modifiersTaken);
        synergiesActivated = clean(synergiesActivated);
        bossesCleared = bossesCleared == null ? new ArrayList<>() : dedupe(bossesCleared);
        if (runHistory == null) {
            runHistory = new ArrayList<>();
        }
        runHistory.removeIf(entry -> entry == null);
        for (RunHistoryEntry entry : runHistory) {
            entry.normalize();
        }
        trimHistory();
    }

    private static Map<String, Long> clean(Map<String, Long> map) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (map == null) {
            return out;
        }
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            String key = entry.getKey();
            Long value = entry.getValue();
            if (key != null && !key.isBlank() && value != null && value > 0) {
                out.put(key, value);
            }
        }
        return out;
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

    /** Counts one finished run. */
    public void countRun() {
        totalRuns++;
    }

    /**
     * Adds the gates of one run and updates the bests, overall and per world and tier.
     *
     * @param gates gates passed in the run
     * @param worldId the world the run was played in
     * @param tierId the tier the run was played at
     * @return {@code true} when the run beat the overall best
     */
    public boolean recordGates(long gates, String worldId, String tierId) {
        long passed = Math.max(0, gates);
        totalGates += passed;
        boolean best = passed > bestGates;
        if (best) {
            bestGates = passed;
        }
        raise(bestGatesByWorld, worldId, passed);
        raise(bestGatesByTier, tierId, passed);
        return best;
    }

    /**
     * Adds the points of one run and updates the best.
     *
     * @param points points scored in the run
     * @return {@code true} when the run beat the best
     */
    public boolean recordPoints(long points) {
        long scored = Math.max(0, points);
        totalPoints += scored;
        if (scored > bestPoints) {
            bestPoints = scored;
            return true;
        }
        return false;
    }

    /**
     * Adds coins credited to the wallet (E32.a: every grant counts here).
     *
     * @param coins the amount
     */
    public void addCoinsEarned(long coins) {
        coinsEarned += Math.max(0, coins);
    }

    /**
     * Adds coins taken out of the wallet by a purchase.
     *
     * @param coins the amount
     */
    public void addCoinsSpent(long coins) {
        coinsSpent += Math.max(0, coins);
    }

    /**
     * Adds coins picked up in a run.
     *
     * @param coins the amount
     */
    public void addCoinsCollected(long coins) {
        coinsCollected += Math.max(0, coins);
    }

    /**
     * Adds earned experience.
     *
     * @param xp the amount
     */
    public void addXpEarned(long xp) {
        xpEarned += Math.max(0, xp);
    }

    /**
     * Counts one death.
     *
     * @param cause the collision cause name, or {@code null} for {@link #CAUSE_UNKNOWN}
     */
    public void countDeath(String cause) {
        bump(deathsByCause, cause == null || cause.isBlank() ? CAUSE_UNKNOWN : cause, 1);
    }

    /**
     * Counts ability activations.
     *
     * @param abilityId the ability
     * @param uses how many times it was used
     */
    public void countAbilityUses(String abilityId, long uses) {
        if (uses > 0) {
            bump(abilitiesUsed, abilityId, uses);
            abilitiesUsedTotal += uses;
        }
    }

    /**
     * Adds absorbed hits.
     *
     * @param count the number of absorbs
     */
    public void addShieldAbsorbs(long count) {
        shieldAbsorbs += Math.max(0, count);
    }

    /**
     * Adds consumed revives.
     *
     * @param count the number of revives
     */
    public void addRevives(long count) {
        revives += Math.max(0, count);
    }

    /**
     * Raises the best streak.
     *
     * @param streak the streak reached in a run
     * @return {@code true} when it is a new best
     */
    public boolean recordStreak(long streak) {
        if (streak > streakBest) {
            streakBest = streak;
            return true;
        }
        return false;
    }

    /**
     * Records a cleared world boss (E26: only world bosses reach this).
     *
     * @param worldId the world
     * @return {@code true} when this is the first clear of that world
     */
    public boolean recordBossClear(String worldId) {
        if (worldId == null || worldId.isBlank()) {
            return false;
        }
        bump(bossClears, worldId, 1);
        if (bossesCleared.contains(worldId)) {
            return false;
        }
        bossesCleared.add(worldId);
        return true;
    }

    /**
     * Counts a taken modifier.
     *
     * @param modifierId the modifier
     */
    public void countModifierTaken(String modifierId) {
        bump(modifiersTaken, modifierId, 1);
    }

    /**
     * Counts an activated synergy.
     *
     * @param synergyId the synergy
     */
    public void countSynergyActivated(String synergyId) {
        bump(synergiesActivated, synergyId, 1);
    }

    /**
     * Adds the ticks of a run to the playtime, rounded to the nearest second.
     *
     * @param ticks ticks the bird was alive
     */
    public void addPlaytimeTicks(long ticks) {
        if (ticks > 0) {
            playtimeSeconds += (ticks + TICKS_PER_SECOND / 2) / TICKS_PER_SECOND;
        }
    }

    /**
     * Appends a run to the history and enforces the cap.
     *
     * @param entry the entry
     */
    public void addHistory(RunHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        runHistory.add(entry);
        trimHistory();
    }

    private void trimHistory() {
        while (runHistory.size() > RUN_HISTORY_LIMIT) {
            runHistory.remove(0);
        }
    }

    private static void raise(Map<String, Long> map, String key, long value) {
        if (key == null || key.isBlank()) {
            return;
        }
        Long current = map.get(key);
        if (current == null || value > current) {
            map.put(key, value);
        }
    }

    private static void bump(Map<String, Long> map, String key, long delta) {
        if (key == null || key.isBlank() || delta == 0) {
            return;
        }
        map.merge(key, delta, Long::sum);
    }

    /**
     * Reads a counter by name, with or without the {@code statistics.} prefix. A map counter with
     * no key sums the map; a list counter yields its size.
     *
     * @param counter the counter name, for example {@code bestGatesByTier.hard}
     * @return the value, or 0 when the name is unknown or the map has no such entry
     */
    public long counter(String counter) {
        StatisticKey key = StatisticKey.of(counter);
        if (key == null || key.isProfileRoot()) {
            return 0;
        }
        return value(key, StatisticKey.mapKeyOf(counter));
    }

    /**
     * Reads a scalar or a whole map counter.
     *
     * @param key the counter
     * @return the value; a map is summed and a list is counted
     */
    public long value(StatisticKey key) {
        return value(key, null);
    }

    /**
     * Reads one entry of a map counter.
     *
     * @param key the counter
     * @param mapKey the entry key, or {@code null} for the whole counter
     * @return the value, 0 when absent
     */
    public long value(StatisticKey key, String mapKey) {
        if (key == null) {
            return 0;
        }
        switch (key) {
            case TOTAL_RUNS:
                return totalRuns;
            case TOTAL_GATES:
                return totalGates;
            case BEST_GATES:
                return bestGates;
            case BEST_GATES_BY_WORLD:
                return fromMap(bestGatesByWorld, mapKey);
            case BEST_GATES_BY_TIER:
                return fromMap(bestGatesByTier, mapKey);
            case TOTAL_POINTS:
                return totalPoints;
            case BEST_POINTS:
                return bestPoints;
            case COINS_EARNED:
                return coinsEarned;
            case COINS_SPENT:
                return coinsSpent;
            case COINS_COLLECTED:
                return coinsCollected;
            case XP_EARNED:
                return xpEarned;
            case DEATHS_BY_CAUSE:
                return fromMap(deathsByCause, mapKey);
            case ABILITIES_USED:
                return fromMap(abilitiesUsed, mapKey);
            case ABILITIES_USED_TOTAL:
                return abilitiesUsedTotal;
            case SHIELD_ABSORBS:
                return shieldAbsorbs;
            case REVIVES:
                return revives;
            case STREAK_BEST:
                return streakBest;
            case BOSSES_CLEARED:
                return bossesCleared.size();
            case BOSS_CLEARS:
                return fromMap(bossClears, mapKey);
            case CHALLENGES_COMPLETED:
                return challengesCompleted;
            case DAILIES_PLAYED:
                return dailiesPlayed;
            case MODIFIERS_TAKEN:
                return fromMap(modifiersTaken, mapKey);
            case SYNERGIES_ACTIVATED:
                return fromMap(synergiesActivated, mapKey);
            case PLAYTIME_SECONDS:
                return playtimeSeconds;
            case RUN_HISTORY:
                return runHistory.size();
            default:
                return 0;
        }
    }

    private static long fromMap(Map<String, Long> map, String mapKey) {
        if (mapKey == null) {
            long sum = 0;
            for (Long value : map.values()) {
                sum += value == null ? 0 : value;
            }
            return sum;
        }
        Long value = map.get(mapKey);
        return value == null ? 0 : value;
    }

    /**
     * Reads any counter of a profile, including the profile-root scalars {@code level},
     * {@code xp} and {@code prestigeCount} (E5). This is the resolver the achievement evaluator
     * (M8) and the {@code counter} unlock condition (E20) call.
     *
     * @param profile the profile
     * @param counter the counter name
     * @return the value, or 0 when the name is unknown
     */
    public static long resolve(PlayerProfile profile, String counter) {
        if (profile == null) {
            return 0;
        }
        StatisticKey key = StatisticKey.of(counter);
        if (key == null) {
            return 0;
        }
        if (key.isProfileRoot()) {
            Long root = profile.rootCounter(key.field());
            return root == null ? 0 : root;
        }
        Statistics stats = profile.statistics;
        return stats == null ? 0 : stats.value(key, StatisticKey.mapKeyOf(counter));
    }

    /**
     * Whether a counter name resolves at all.
     *
     * @param counter the counter name
     * @return {@code true} when {@link StatisticKey#of(String)} knows it
     */
    public static boolean knows(String counter) {
        return StatisticKey.of(counter) != null;
    }

    @Override
    public String toString() {
        return "Statistics{runs=" + totalRuns + ", gates=" + totalGates + ", best=" + bestGates
                + ", coinsEarned=" + coinsEarned + ", history=" + runHistory.size() + '}';
    }

    /**
     * One finished run, as the statistics screen and the run summary list it.
     *
     * <p>D15 keeps the persisted tree to numbers, strings and string-keyed collections; an entry
     * holds exactly those, so the history binds with plain Gson and stays readable in the file. A
     * history of bare strings would be unreadable to the screens that consume it, which is why
     * this is a small POJO rather than a {@code List<String>}.
     */
    public static final class RunHistoryEntry {
        /** When the run finished, from the injected time source. */
        public long finishedAtEpochMs;
        /** The seed the run was played with. */
        public long seed;
        /** {@code RunMode} name ({@code STANDARD}, {@code SEEDED}, {@code DAILY}, {@code CHALLENGE}). */
        public String mode = "";
        /** The bird flown. */
        public String birdId = "";
        /** The world played. */
        public String worldId = "";
        /** The tier played. */
        public String tierId = "";
        /** The challenge played, or an empty string. */
        public String challengeId = "";
        /** Gates passed. */
        public long gates;
        /** Points scored. */
        public long points;
        /** Coins the run paid, after every multiplier. */
        public long coins;
        /** Experience the run paid. */
        public long xp;
        /** Best clean-gate streak of the run. */
        public long streakBest;
        /** Coins picked up in the run. */
        public long coinsCollected;
        /** Ticks the bird was alive. */
        public long ticksAlive;
        /** Collision cause name, or {@code "unknown"}. */
        public String deathCause = CAUSE_UNKNOWN;
        /** Whether the run met its objective (challenges and dailies). */
        public boolean objectiveMet;

        /** Creates an empty entry. Gson binds onto this constructor. */
        public RunHistoryEntry() {
        }

        /** Replaces nulls and negative numbers. */
        public void normalize() {
            mode = mode == null ? "" : mode;
            birdId = birdId == null ? "" : birdId;
            worldId = worldId == null ? "" : worldId;
            tierId = tierId == null ? "" : tierId;
            challengeId = challengeId == null ? "" : challengeId;
            deathCause = deathCause == null || deathCause.isBlank() ? CAUSE_UNKNOWN : deathCause;
            finishedAtEpochMs = Math.max(0, finishedAtEpochMs);
            gates = Math.max(0, gates);
            points = Math.max(0, points);
            coins = Math.max(0, coins);
            xp = Math.max(0, xp);
            streakBest = Math.max(0, streakBest);
            coinsCollected = Math.max(0, coinsCollected);
            ticksAlive = Math.max(0, ticksAlive);
        }
    }
}
