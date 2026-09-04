package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.DailyDef;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.modifier.ModifierPool;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * The daily challenge (D28, E27): one world, one tier and two forced modifiers, picked from a
 * seed that is nothing but today's UTC date.
 *
 * <p><b>The date is the whole seed.</b> {@code seed = fnv1a("daily:" + yyyy-MM-dd)}, and the pick
 * is streamed from {@code RandomProvider(seed).stream("daily")} — the named stream of D12, so the
 * daily draws nothing from the streams the simulation uses and two players on opposite sides of
 * the planet get the same challenge on the same UTC day with no server involved (the rejected
 * alternative: server-side dailies, which 1.0 has no infrastructure for).
 *
 * <p><b>The pick is persisted, and that is the point</b> (E27). The first time a player
 * <em>views</em> the daily — not only the first time they play it — {@code profile.daily} is
 * written with the date, the seed, the world, the tier and the modifier ids, and every later
 * question about that date is answered from the record. Unlocking a new world at lunchtime
 * therefore cannot change the challenge the player has been practising all morning, and the
 * attempt counter and the best gate count stay attached to a configuration that has not moved
 * under them. A stored pick is rebuilt exactly once and only when the content can no longer play
 * it — an id removed by an older save or a content edit ({@link Pick#reused()} says which
 * happened, and {@link Pick#note()} says why).
 *
 * <p><b>Only unlocked content is picked.</b> The world comes from the worlds the profile owns,
 * the tier from {@code economy.daily.tierPool} intersected with the tiers it owns, and the
 * modifiers from the cards it owns — an empty pool degrades to the selected/default world and to
 * the lowest unlocked tier rather than throwing. The forced modifiers do <em>not</em> require
 * {@code feature:modifiers}: only <em>drafting</em> mid-run does (D28). What the daily does
 * require is {@code feature:seeded_runs}, which is the mode's own unlock and is checked by the
 * screen offering it ({@link #isAvailable}).
 *
 * <p>Compatibility of the forced pair is not re-implemented here: {@link ModifierPool} already
 * answers "may this card be taken now" for {@code maxStacks}, {@code excludes} (in both
 * directions), {@code requiresFlagsAbsent} and E12's derived inertness, and the pool is re-asked
 * after every pick against the rules of the world and the tier that were just drawn. The draw
 * itself is uniform over the eligible cards rather than rarity-weighted: a daily is a fixed
 * configuration, not an offer, so a legendary is exactly as likely as a common — which is also
 * what makes the two picks stable when a rarity weight is retuned.
 */
public final class DailyChallenge {

    /** The feature that opens Seeded and Daily mode ({@code economy.json.features}). */
    public static final String SEEDED_RUNS_FEATURE = "seeded_runs";
    /** Prefix of the hashed seed text, {@code "daily:" + yyyy-MM-dd}. */
    public static final String SEED_PREFIX = "daily:";

    private static final long MILLIS_PER_DAY = 86_400_000L;

    private final TimeSource clock;

    /**
     * Creates the daily of one session (D23: the clock is injected, never read from the wall).
     *
     * @param clock the time source the UTC date comes from
     */
    public DailyChallenge(TimeSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Today's UTC date.
     *
     * @return the date as {@code yyyy-MM-dd}
     */
    public String today() {
        return dateOf(clock.epochMillis());
    }

    /**
     * Today's pick, stored on the profile the first time it is asked for (E27).
     *
     * @param profile the profile, read for what is unlocked and written with the pick
     * @param content the loaded content
     * @return the pick
     */
    public Pick today(PlayerProfile profile, GameContent content) {
        return forDate(clock.epochMillis(), profile, content);
    }

    /**
     * The pick of the UTC day a timestamp falls in, stored on the profile (E27).
     *
     * @param epochMillis the timestamp, from the injected {@link TimeSource}
     * @param profile the profile, read for what is unlocked and written with the pick
     * @param content the loaded content
     * @return the pick
     */
    public static Pick forDate(long epochMillis, PlayerProfile profile, GameContent content) {
        return forDay(Math.floorDiv(epochMillis, MILLIS_PER_DAY), profile, content);
    }

    /**
     * The pick of one UTC day, stored on the profile (D28, E27).
     *
     * @param epochDay days since 1970-01-01, UTC
     * @param profile the profile, read for what is unlocked and written with the pick
     * @param content the loaded content
     * @return the pick
     */
    public static Pick forDay(long epochDay, PlayerProfile profile, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        String date = LocalDate.ofEpochDay(epochDay).toString();
        PlayerProfile.DailyRecord stored = profile.daily;
        if (stored != null && stored.isFor(date)) {
            String broken = unplayable(stored, content);
            if (broken == null) {
                return new Pick(date, stored.seed, stored.worldId, stored.tierId,
                        stored.modifierIds, true, "");
            }
            Pick rebuilt = draw(date, profile, content, "stored pick rebuilt: " + broken);
            store(profile, rebuilt, false);
            return rebuilt;
        }
        Pick pick = draw(date, profile, content, "");
        store(profile, pick, true);
        return pick;
    }

    /**
     * The UTC date a timestamp falls in.
     *
     * @param epochMillis the timestamp, from the injected {@link TimeSource}
     * @return the date as {@code yyyy-MM-dd}
     */
    public static String dateOf(long epochMillis) {
        return LocalDate.ofEpochDay(Math.floorDiv(epochMillis, MILLIS_PER_DAY)).toString();
    }

    /**
     * The seed of one date: {@code fnv1a("daily:" + yyyy-MM-dd)} (D28).
     *
     * @param date the date as {@code yyyy-MM-dd}
     * @return the seed every attempt of that day is played with
     */
    public static long seedFor(String date) {
        return MathUtil.fnv1a64(SEED_PREFIX + date);
    }

    /**
     * Whether the profile may play Daily and Seeded mode at all (D28).
     *
     * @param profile the profile
     * @return {@code true} once {@code feature:seeded_runs} is unlocked
     */
    public static boolean isAvailable(PlayerProfile profile) {
        return profile != null
                && profile.isUnlocked(ContentKind.FEATURE.unlockableId(SEEDED_RUNS_FEATURE));
    }

    // ------------------------------------------------------------------ picking

    /**
     * Draws a pick for a date from the {@code daily} stream.
     *
     * @param date the date as {@code yyyy-MM-dd}
     * @param profile the profile, read for what is unlocked
     * @param content the loaded content
     * @param note why the pick had to be drawn, empty for the ordinary first view of a day
     * @return the pick
     */
    private static Pick draw(String date, PlayerProfile profile, GameContent content,
            String note) {
        long seed = seedFor(date);
        Random rng = new RandomProvider(seed).stream(RandomProvider.DAILY);
        String worldId = pickWorld(rng, profile, content);
        String tierId = pickTier(rng, profile, content);
        List<String> modifiers = pickModifiers(rng, profile, content, worldId, tierId);
        return new Pick(date, seed, worldId, tierId, modifiers, false, note);
    }

    /**
     * The world of the day: one of the worlds the profile owns, in content order.
     *
     * @param rng the {@code daily} stream
     * @param profile the profile
     * @param content the loaded content
     * @return the world id, the selected one when nothing is owned or the build ships no worlds
     */
    private static String pickWorld(Random rng, PlayerProfile profile, GameContent content) {
        String fallback = content.has(GameContent.WORLDS)
                && content.worlds().contains(profile.selected.worldId)
                        ? profile.selected.worldId : PlayerProfile.DEFAULT_WORLD;
        if (!content.has(GameContent.WORLDS)) {
            return fallback;
        }
        List<String> owned = new ArrayList<>();
        for (WorldDef def : content.worlds()) {
            if (profile.isUnlocked(def.unlockableId())) {
                owned.add(def.id());
            }
        }
        if (owned.isEmpty()) {
            return content.worlds().contains(fallback) ? fallback : content.worlds().ids().get(0);
        }
        return owned.get(rng.nextInt(owned.size()));
    }

    /**
     * The tier of the day: one of {@code economy.daily.tierPool} the profile owns, in pool order.
     *
     * @param rng the {@code daily} stream
     * @param profile the profile
     * @param content the loaded content
     * @return the tier id, the lowest unlocked tier when the pool is empty for this profile
     */
    private static String pickTier(Random rng, PlayerProfile profile, GameContent content) {
        DailyDef daily = content.economy().daily();
        List<String> pool = new ArrayList<>();
        for (String id : daily.tierPool()) {
            if (content.tiers().contains(id)
                    && profile.isUnlocked(ContentKind.TIER.unlockableId(id))) {
                pool.add(id);
            }
        }
        if (!pool.isEmpty()) {
            return pool.get(rng.nextInt(pool.size()));
        }
        // Degrading to the lowest tier the player owns keeps the daily playable for a profile
        // that has not unlocked a single tier of the pool (and for a pool an edit emptied).
        for (TierDef def : content.tiers()) {
            if (profile.isUnlocked(def.unlockableId())) {
                return def.id();
            }
        }
        return content.defaultTierId();
    }

    /**
     * The forced modifiers of the day: {@code economy.daily.forcedModifierCount} cards the
     * profile owns that can be held together under the drawn world and tier.
     *
     * @param rng the {@code daily} stream
     * @param profile the profile
     * @param content the loaded content
     * @param worldId the world drawn for the day
     * @param tierId the tier drawn for the day
     * @return the ids, fewer than asked for when the pool runs out, never {@code null}
     */
    private static List<String> pickModifiers(Random rng, PlayerProfile profile,
            GameContent content, String worldId, String tierId) {
        int want = content.economy().daily().forcedModifierCount();
        if (want <= 0 || !content.has(GameContent.MODIFIERS)) {
            return List.of();
        }
        ModifierCatalog catalog =
                content.modifierCatalog(RunLoadout.availableModifiers(profile, content));
        ModifierPool pool = new ModifierPool(catalog, rulesOf(content, worldId, tierId), rng);
        Map<String, Integer> taken = new LinkedHashMap<>();
        List<String> out = new ArrayList<>(want);
        while (out.size() < want) {
            List<ModifierDef> candidates = pool.candidates(taken);
            if (candidates.isEmpty()) {
                break;
            }
            ModifierDef picked = candidates.get(rng.nextInt(candidates.size()));
            out.add(picked.id());
            taken.merge(picked.id(), 1, Integer::sum);
        }
        return List.copyOf(out);
    }

    /**
     * The rules a daily run carries before anything is taken: the world's flags and the tier's.
     *
     * @param content the loaded content
     * @param worldId the world of the day
     * @param tierId the tier of the day
     * @return the rule set the forced pair has to be compatible with
     */
    private static RuleSet rulesOf(GameContent content, String worldId, String tierId) {
        RuleSet rules = RuleSet.EMPTY;
        if (content.has(GameContent.WORLDS) && content.worlds().contains(worldId)) {
            rules = rules.union(RuleSet.of(content.worlds().get(worldId).flags()));
        }
        if (content.tiers().contains(tierId)) {
            rules = rules.union(RuleSet.of(content.tiers().get(tierId).flags()));
        }
        return rules;
    }

    // ------------------------------------------------------------------ persistence

    /**
     * Why the content can no longer play a stored pick.
     *
     * @param stored the stored record
     * @param content the loaded content
     * @return the reason, or {@code null} when the record is playable as it stands
     */
    private static String unplayable(PlayerProfile.DailyRecord stored, GameContent content) {
        if (content.has(GameContent.WORLDS) && !content.worlds().contains(stored.worldId)) {
            return "unknown world " + quoted(stored.worldId);
        }
        if (!content.tiers().contains(stored.tierId)) {
            return "unknown tier " + quoted(stored.tierId);
        }
        for (String id : stored.modifierIds) {
            if (!content.has(GameContent.MODIFIERS) || !content.modifiers().contains(id)) {
                return "unknown modifier " + quoted(id);
            }
        }
        return null;
    }

    private static String quoted(String id) {
        return "'" + (id == null ? "" : id) + "'";
    }

    /**
     * Writes the pick onto the profile (E27). The attempt counter and the best gate count belong
     * to the date, so they are cleared when the day turns over and kept when a same-day record
     * merely had to be rebuilt against changed content.
     *
     * @param profile the profile to write
     * @param pick the pick of the day
     * @param newDay whether this is a different date from the one stored
     */
    private static void store(PlayerProfile profile, Pick pick, boolean newDay) {
        PlayerProfile.DailyRecord record = profile.daily;
        if (record == null) {
            record = new PlayerProfile.DailyRecord();
            profile.daily = record;
        }
        if (newDay) {
            record.attempts = 0;
            record.bestGates = 0;
        }
        record.date = pick.date();
        record.seed = pick.seed();
        record.worldId = pick.worldId();
        record.tierId = pick.tierId();
        record.modifierIds = new ArrayList<>(pick.modifierIds());
    }

    /**
     * One day's challenge: the date, its seed and the configuration every attempt of that date is
     * played with.
     *
     * @param date the UTC date as {@code yyyy-MM-dd}
     * @param seed the seed of every attempt
     * @param worldId the world of the day
     * @param tierId the tier of the day
     * @param modifierIds the modifiers forced on every attempt, in the order they were drawn
     * @param reused whether the pick came from {@code profile.daily} rather than being drawn now
     * @param note why a stored pick had to be redrawn, empty when nothing had to be repaired
     */
    public record Pick(String date, long seed, String worldId, String tierId,
            List<String> modifierIds, boolean reused, String note) {

        /**
         * Copies the modifier list and rejects nulls.
         *
         * @param date the date
         * @param seed the seed
         * @param worldId the world
         * @param tierId the tier
         * @param modifierIds the forced modifiers
         * @param reused whether the pick was reused
         * @param note the repair note
         */
        public Pick {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(tierId, "tierId");
            modifierIds = List.copyOf(modifierIds);
            note = note == null ? "" : note;
        }
    }
}
