package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.UnlockGraph;
import io.github.michelbr84.flapforge.content.defs.AchievementConditionDef;
import io.github.michelbr84.flapforge.content.defs.AchievementDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * How much of each collection a profile owns (D13, E20, M8): one {@link Entry} per category —
 * {@code birds}, {@code abilities}, {@code worlds}, {@code challenges}, {@code cosmetics},
 * {@code achievements}, {@code upgrades} and {@code all} — with what is owned, what the content
 * ships and the percentage floored to a whole number.
 *
 * <p>This is the <em>single</em> arithmetic behind three readers: the {@code collect_*}
 * achievements ({@link AchievementEvaluator}, scope {@code COLLECTION}), the {@code counter}
 * unlock condition of a cosmetic ({@link UnlockEvaluator}, E20: {@code forge/molten} opens at
 * {@code collection.upgrades.percent >= 50}) and the Collections tab of the achievements screen.
 * Keeping it in one class is what makes the number on the tab the number the evaluators act on.
 *
 * <p>What counts as owned: for the id-based categories the namespaced unlock ids the profile
 * holds ({@code bird:}, {@code ability:}, {@code world:}, {@code cosmetic:}); for challenges the
 * records marked {@code completed}; for achievements the records held; for upgrades the levels
 * owned over the levels that exist (a node at level 2 of 3 is two thirds of that node), which
 * is why the total of that category is a level count rather than a node count. {@code all} is
 * the sum of the seven others, owned over total, not the mean of their percentages.
 *
 * <p>The category totals come from the same table the unlock graph proves reachable
 * ({@link UnlockGraph#nodes()}), so an unlockable the validator does not know is not part of a
 * collection either. Nothing here reads a clock or a random stream; the same profile and content
 * always give the same entries.
 */
public final class CollectionProgress {

    /** The categories, in the order the Collections tab lists them ({@code all} last). */
    public static final List<String> CATEGORIES = AchievementConditionDef.COLLECTION_CATEGORIES;
    /** The category naming every other category at once. */
    public static final String ALL = "all";

    private final GameContent content;
    private final Map<String, ContentKind> kinds;

    /**
     * Creates the progress reader of a content set.
     *
     * @param content the loaded content
     */
    public CollectionProgress(GameContent content) {
        this(content, kindsOf(content));
    }

    /**
     * Creates the reader over an already built id-to-kind table (the one {@link UnlockEvaluator}
     * holds), so the two share one graph walk.
     *
     * @param content the loaded content
     * @param kinds the kind of every unlockable id, in content order
     */
    CollectionProgress(GameContent content, Map<String, ContentKind> kinds) {
        this.content = Objects.requireNonNull(content, "content");
        this.kinds = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(kinds, "kinds")));
    }

    /**
     * Creates the progress reader of a content set.
     *
     * @param content the loaded content
     * @return the reader
     */
    public static CollectionProgress of(GameContent content) {
        return new CollectionProgress(content);
    }

    /**
     * The kind of every unlockable the content ships, from the unlock graph's node list.
     *
     * @param content the loaded content
     * @return the table, in content order
     */
    static Map<String, ContentKind> kindsOf(GameContent content) {
        Map<String, ContentKind> table = new LinkedHashMap<>();
        for (UnlockGraph.Node node : UnlockGraph.of(content).nodes().values()) {
            if (node.unlockable() && node.condition() != null && node.kind() != null) {
                table.put(node.id(), node.kind());
            }
        }
        return table;
    }

    /**
     * The percentage of a collection as a whole number in {@code [0, 100]}: floored, never
     * rounded up, so 6 of 7 birds is 85 and only 7 of 7 is 100 (D13).
     *
     * @param owned what is owned
     * @param total what exists
     * @return the percentage, 0 when nothing exists
     */
    public static long percentOf(long owned, long total) {
        if (total <= 0) {
            return 0;
        }
        return 100L * Math.max(0, Math.min(owned, total)) / total;
    }

    /**
     * Whether a name is a category this class knows.
     *
     * @param category the name
     * @return {@code true} for one of {@link #CATEGORIES}
     */
    public static boolean knows(String category) {
        return category != null && CATEGORIES.contains(category);
    }

    /**
     * The progress of one category.
     *
     * @param category one of {@link #CATEGORIES}
     * @param profile the profile to read
     * @return the entry; {@code 0 / 0} for an unknown category
     */
    public Entry of(String category, PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return of(category, profile, new LinkedHashSet<>(profile.unlocked));
    }

    /**
     * The progress of one category with the owned unlock ids handed in — what the unlock
     * evaluator needs while it iterates to its fixed point, where a grant of the same pass is
     * owned before it reaches the profile.
     *
     * @param category one of {@link #CATEGORIES}
     * @param profile the profile to read (for challenges, achievements and upgrades)
     * @param owned the unlock ids to count as owned
     * @return the entry; {@code 0 / 0} for an unknown category
     */
    public Entry of(String category, PlayerProfile profile, Set<String> owned) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(owned, "owned");
        if (category == null) {
            return new Entry("", 0, 0);
        }
        long[] totals = ALL.equals(category) ? allCategories(profile, owned)
                : oneCategory(category, profile, owned);
        if (totals == null) {
            return new Entry(category, 0, 0);
        }
        return new Entry(category, totals[0], totals[1]);
    }

    /**
     * The percentage of one category — the value of {@code collection.<category>.percent}.
     *
     * @param category one of {@link #CATEGORIES}
     * @param profile the profile to read
     * @return the percentage in {@code [0, 100]}, 0 for an unknown category
     */
    public long percent(String category, PlayerProfile profile) {
        return of(category, profile).percent();
    }

    /**
     * The percentage of one category with the owned ids handed in.
     *
     * @param category one of {@link #CATEGORIES}
     * @param profile the profile to read
     * @param owned the unlock ids to count as owned
     * @return the percentage in {@code [0, 100]}, 0 for an unknown category
     */
    public long percent(String category, PlayerProfile profile, Set<String> owned) {
        return of(category, profile, owned).percent();
    }

    /**
     * Every category, in {@link #CATEGORIES} order, {@code all} last.
     *
     * @param profile the profile to read
     * @return one entry per category
     */
    public List<Entry> all(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        Set<String> owned = new LinkedHashSet<>(profile.unlocked);
        List<Entry> out = new ArrayList<>(CATEGORIES.size());
        for (String category : CATEGORIES) {
            out.add(of(category, profile, owned));
        }
        return Collections.unmodifiableList(out);
    }

    private long[] allCategories(PlayerProfile profile, Set<String> owned) {
        long[] sum = new long[2];
        for (String category : CATEGORIES) {
            if (ALL.equals(category)) {
                continue;
            }
            long[] one = oneCategory(category, profile, owned);
            if (one != null) {
                sum[0] += one[0];
                sum[1] += one[1];
            }
        }
        return sum;
    }

    /**
     * The {@code {owned, total}} pair of one collection category.
     *
     * @param category the category
     * @param profile the profile
     * @param owned the unlock ids the profile holds
     * @return the pair, or {@code null} when the category is unknown
     */
    private long[] oneCategory(String category, PlayerProfile profile, Set<String> owned) {
        switch (category) {
            case "birds":
                return counted(ContentKind.BIRD, owned);
            case "abilities":
                return counted(ContentKind.ABILITY, owned);
            case "worlds":
                return counted(ContentKind.WORLD, owned);
            case "cosmetics":
                return cosmetics(owned);
            case "challenges":
                return challenges(profile);
            case "achievements":
                return achievements(profile);
            case "upgrades":
                return upgrades(profile);
            default:
                return null;
        }
    }

    private long[] counted(ContentKind kind, Set<String> owned) {
        long total = 0;
        long have = 0;
        for (Map.Entry<String, ContentKind> entry : kinds.entrySet()) {
            if (entry.getValue() != kind) {
                continue;
            }
            total++;
            if (owned.contains(entry.getKey())) {
                have++;
            }
        }
        return new long[] {have, total};
    }

    private long[] cosmetics(Set<String> owned) {
        long total = 0;
        long have = 0;
        if (!content.has(GameContent.BIRDS)) {
            return new long[] {0, 0};
        }
        for (BirdDef bird : content.birds()) {
            for (PaletteDef palette : bird.palettes()) {
                total++;
                if (owned.contains(bird.cosmeticId(palette.id()))) {
                    have++;
                }
            }
        }
        return new long[] {have, total};
    }

    private long[] challenges(PlayerProfile profile) {
        long total = 0;
        long have = 0;
        if (!content.has(GameContent.CHALLENGES)) {
            return new long[] {0, 0};
        }
        for (ChallengeDef challenge : content.challenges()) {
            total++;
            PlayerProfile.ChallengeRecord record = profile.challenges.get(challenge.id());
            if (record != null && record.completed) {
                have++;
            }
        }
        return new long[] {have, total};
    }

    private long[] achievements(PlayerProfile profile) {
        long total = 0;
        long have = 0;
        if (!content.has(GameContent.ACHIEVEMENTS)) {
            return new long[] {0, 0};
        }
        for (AchievementDef achievement : content.achievements()) {
            total++;
            if (profile.achievements.containsKey(achievement.id())) {
                have++;
            }
        }
        return new long[] {have, total};
    }

    private long[] upgrades(PlayerProfile profile) {
        long total = 0;
        long have = 0;
        if (!content.has(GameContent.UPGRADES)) {
            return new long[] {0, 0};
        }
        for (UpgradeDef node : content.upgrades()) {
            total += node.maxLevel();
            have += Math.min(profile.upgradeLevel(node.id()), node.maxLevel());
        }
        return new long[] {have, total};
    }

    @Override
    public String toString() {
        return "CollectionProgress{unlockables=" + kinds.size() + '}';
    }

    /**
     * The progress of one collection category.
     *
     * @param category the category name
     * @param owned what the profile holds
     * @param total what the content ships
     */
    public record Entry(String category, long owned, long total) {

        /**
         * Copies the name and clamps the counts.
         *
         * @param category the category name
         * @param owned what the profile holds
         * @param total what the content ships
         */
        public Entry {
            category = category == null ? "" : category;
            total = Math.max(0, total);
            owned = Math.max(0, Math.min(owned, total));
        }

        /**
         * The percentage, floored to a whole number.
         *
         * @return a value in {@code [0, 100]}
         */
        public long percent() {
            return percentOf(owned, total);
        }

        /**
         * The fraction owned, for a progress bar.
         *
         * @return a value in {@code [0, 1]}, 0 when nothing exists
         */
        public double fraction() {
            return total <= 0 ? 0 : (double) owned / total;
        }

        /**
         * Whether everything the category holds is owned.
         *
         * @return {@code true} when owned equals a positive total
         */
        public boolean isComplete() {
            return total > 0 && owned >= total;
        }
    }
}
