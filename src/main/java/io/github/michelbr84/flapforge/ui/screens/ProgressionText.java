package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.Wallet;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns meta-progression data into the words the three M4 screens show (D25).
 *
 * <p>Three kinds of translation live here, and only here, so the bird selection, the upgrade trees
 * and the shop cannot phrase the same thing differently:
 * <ul>
 *   <li><b>Numbers.</b> A stat, a modifier and an upgrade effect per level become
 *       {@code "-3% Gravity"} or {@code "x0.92 Ability cooldown"} — a signed value in the shape its
 *       {@link StatOp} implies, followed by the stat's translated name.</li>
 *   <li><b>Sources.</b> The {@code source} string a {@link StatModifier} carries
 *       ({@code bird:forge}, {@code upgrade:feather_1}, {@code synergy:forge}, {@code tier:hard},
 *       {@code curve:classic}) becomes the name the player knows that thing by, which is what makes
 *       the stat breakdown readable rather than a dump of ids.</li>
 *   <li><b>Unlock conditions.</b> A condition tree becomes one short phrase — "Play 3 runs",
 *       "150 coins". For an {@code any_of} it is the branch the profile is <em>closest</em> to
 *       finishing (D13's "cheapest path"), measured as the fraction of the threshold still
 *       missing, with the coin branch measured against the wallet; ties keep content order.</li>
 * </ul>
 *
 * <p>Everything is static and reads nothing but its arguments: the same profile and content always
 * produce the same words, which is what lets the screen tests assert on them.
 */
public final class ProgressionText {

    /** How a collection counter names its category, {@code collection.<category>.percent}. */
    private static final String COLLECTION_PREFIX = "collection.";
    /** Suffix of a collection percentage counter. */
    private static final String PERCENT_SUFFIX = ".percent";

    private static final Map<StatId, StringKey> STAT_LABELS = statLabels();

    private ProgressionText() {
    }

    /**
     * The {@link StringKey} naming each stat, resolved from the enum constant so a new stat cannot
     * be added without its key.
     *
     * @return the table
     */
    private static Map<StatId, StringKey> statLabels() {
        EnumMap<StatId, StringKey> table = new EnumMap<>(StatId.class);
        for (StatId stat : StatId.values()) {
            table.put(stat, StringKey.valueOf("STAT_" + stat.name()));
        }
        return table;
    }

    /**
     * The translated name of a stat.
     *
     * @param strings the string table
     * @param stat the stat
     * @return the name
     */
    public static String statLabel(Strings strings, StatId stat) {
        return strings.get(STAT_LABELS.get(stat));
    }

    /**
     * A stat value as the player reads it: whole numbers for the physics stats, up to two decimals
     * for the multipliers.
     *
     * @param value the value
     * @return the formatted number
     */
    public static String number(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) Math.rint(rounded));
        }
        if (Math.round(rounded * 10.0) / 10.0 == rounded) {
            return String.format(Locale.ROOT, "%.1f", rounded);
        }
        return String.format(Locale.ROOT, "%.2f", rounded);
    }

    /**
     * A signed number, for a value that is added to something.
     *
     * @param value the value
     * @return the formatted number with an explicit {@code +} when positive
     */
    public static String signed(double value) {
        String text = number(value);
        return value > 0 ? "+" + text : text;
    }

    /**
     * One modifier in words: {@code "-3% Gravity"}, {@code "+20 Magnet radius"},
     * {@code "x0.92 Ability cooldown"}.
     *
     * @param strings the string table
     * @param stat the stat the modifier touches
     * @param op how it applies
     * @param value the value
     * @return the phrase
     */
    public static String effect(Strings strings, StatId stat, StatOp op, double value) {
        String label = statLabel(strings, stat);
        switch (op) {
            case PERCENT_ADD:
                return strings.format(StringKey.STAT_EFFECT_PERCENT, signed(value * 100), label);
            case MULTIPLY:
                return strings.format(StringKey.STAT_EFFECT_MULTIPLY, number(value), label);
            case FLAT_ADD:
            default:
                return strings.format(StringKey.STAT_EFFECT_FLAT, signed(value), label);
        }
    }

    /**
     * One resolved modifier in words.
     *
     * @param strings the string table
     * @param modifier the modifier
     * @return the phrase
     */
    public static String effect(Strings strings, StatModifier modifier) {
        return effect(strings, modifier.stat(), modifier.op(), modifier.value());
    }

    /**
     * One authored effect in words, as it reads on an upgrade node ("per level").
     *
     * @param strings the string table
     * @param def the authored effect
     * @return the phrase
     */
    public static String effect(Strings strings, StatModifierDef def) {
        return effect(strings, def.stat(), def.op(), def.value());
    }

    /**
     * Every effect of an upgrade node, one phrase per line, joined by a comma.
     *
     * @param strings the string table
     * @param effects the authored effects
     * @return the joined phrase, empty when the node has no stat effects
     */
    public static String effects(Strings strings, List<StatModifierDef> effects) {
        StringBuilder out = new StringBuilder();
        for (StatModifierDef def : effects) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(effect(strings, def));
        }
        return out.toString();
    }

    /**
     * The name of the thing a modifier came from.
     *
     * @param strings the string table
     * @param content the loaded content
     * @param source the {@code source} of a {@link StatModifier}
     * @return the translated name, or the raw source when nothing claims it
     */
    public static String sourceLabel(Strings strings, GameContent content, String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        int split = source.indexOf(':');
        if (split < 0) {
            return "speed_ramp".equals(source) ? strings.get(StringKey.SOURCE_SPEED_RAMP) : source;
        }
        String prefix = source.substring(0, split);
        String id = source.substring(split + 1);
        switch (prefix) {
            case "ramp":
                return strings.format(StringKey.SOURCE_RAMP, name(strings, ContentKind.BIRD, id));
            case "synergy":
                return strings.format(StringKey.SOURCE_SYNERGY,
                        name(strings, ContentKind.BIRD, id));
            case "curve":
                return strings.get(StringKey.SOURCE_CURVE);
            case "bird":
                return name(strings, ContentKind.BIRD, id);
            case "upgrade":
                return name(strings, ContentKind.UPGRADE, id);
            case "tier":
                return name(strings, ContentKind.TIER, id);
            case "world":
                return name(strings, ContentKind.WORLD, id);
            default:
                return unlockableName(strings, content, source);
        }
    }

    /**
     * The name of a namespaced unlockable ({@code bird:guardian},
     * {@code cosmetic:classic:ember}, {@code feature:modifiers}).
     *
     * @param strings the string table
     * @param content the loaded content
     * @param unlockId the namespaced id
     * @return the translated name, or the raw id when no kind claims the prefix
     */
    public static String unlockableName(Strings strings, GameContent content, String unlockId) {
        ContentKind kind = ContentKind.ofUnlockable(unlockId);
        if (kind == null) {
            return unlockId;
        }
        String id = unlockId.substring(kind.namespace().length());
        if (kind == ContentKind.COSMETIC) {
            // cosmetic:<bird>:<palette> is one unlockable but two words in the string table.
            id = id.replace(':', '.');
        }
        return name(strings, kind, id);
    }

    /**
     * The display name of one entry of a kind.
     *
     * @param strings the string table
     * @param kind the kind
     * @param id the entry id (for a cosmetic, {@code <bird>.<palette>})
     * @return the translated name
     */
    public static String name(Strings strings, ContentKind kind, String id) {
        return strings.name(kind.key(), id);
    }

    /**
     * The description of one entry of a kind.
     *
     * @param strings the string table
     * @param kind the kind
     * @param id the entry id
     * @return the translated description
     */
    public static String description(Strings strings, ContentKind kind, String id) {
        return strings.desc(kind.key(), id);
    }

    /**
     * A price in coins.
     *
     * @param strings the string table
     * @param coins the price
     * @return the phrase
     */
    public static String price(Strings strings, long coins) {
        return strings.format(StringKey.SHOP_PRICE, coins);
    }

    /**
     * The way to unlock something, in words: the branch of the condition tree the profile is
     * closest to finishing.
     *
     * @param strings the string table
     * @param content the loaded content
     * @param condition the condition tree, may be {@code null}
     * @param profile the profile the distance is measured against, may be {@code null}
     * @return the phrase, empty when there is no condition at all
     */
    public static String unlockText(Strings strings, GameContent content,
            UnlockConditionDef condition, PlayerProfile profile) {
        UnlockConditionDef branch = cheapestBranch(condition, profile);
        if (branch == null) {
            return "";
        }
        if (branch.type() == UnlockType.ALL_OF) {
            String joined = "";
            for (UnlockConditionDef child : branch.conditions()) {
                String text = unlockText(strings, content, child, profile);
                if (text.isEmpty()) {
                    continue;
                }
                joined = joined.isEmpty() ? text
                        : strings.format(StringKey.UNLOCK_ALL_OF, joined, text);
            }
            return joined;
        }
        return phrase(strings, content, branch, profile);
    }

    /**
     * One condition in words, without descending into {@code any_of}.
     *
     * @param strings the string table
     * @param content the loaded content
     * @param condition the condition
     * @param profile the profile, may be {@code null}
     * @return the phrase
     */
    private static String phrase(Strings strings, GameContent content,
            UnlockConditionDef condition, PlayerProfile profile) {
        long value = Math.round(condition.value());
        switch (condition.type()) {
            case DEFAULT:
                return strings.get(StringKey.UNLOCK_DEFAULT);
            case RUNS:
                return strings.format(StringKey.UNLOCK_RUNS, value);
            case BEST_GATES:
                return strings.format(StringKey.UNLOCK_BEST_GATES, value);
            case BEST_POINTS:
                return strings.format(StringKey.UNLOCK_BEST_POINTS, value);
            case TOTAL_GATES:
                return strings.format(StringKey.UNLOCK_TOTAL_GATES, value);
            case LEVEL:
                return strings.format(StringKey.UNLOCK_LEVEL, value);
            case COINS_EARNED_TOTAL:
                return strings.format(StringKey.UNLOCK_COINS_EARNED, value);
            case PURCHASE:
                return price(strings, Math.round(condition.amount()));
            case CHALLENGE:
                return strings.format(StringKey.UNLOCK_CHALLENGE,
                        name(strings, ContentKind.CHALLENGE, condition.id()));
            case ACHIEVEMENT:
                return strings.format(StringKey.UNLOCK_ACHIEVEMENT,
                        name(strings, ContentKind.ACHIEVEMENT, condition.id()));
            case WORLD_CLEARED:
                return strings.format(StringKey.UNLOCK_WORLD_CLEARED,
                        name(strings, ContentKind.WORLD, condition.id()));
            case PRESTIGE:
                return strings.format(StringKey.UNLOCK_PRESTIGE, value);
            case COUNTER:
                return counterPhrase(strings, condition);
            case ALL_OF:
            case ANY_OF:
            default:
                return unlockText(strings, content, condition, profile);
        }
    }

    /**
     * A {@code counter} condition in words. A collection percentage — the only shape the shipped
     * content uses (E20) — reads as "Own 50% of the upgrades"; anything else falls back to naming
     * the counter.
     *
     * @param strings the string table
     * @param condition the condition
     * @return the phrase
     */
    private static String counterPhrase(Strings strings, UnlockConditionDef condition) {
        String counter = condition.counter() == null ? "" : condition.counter();
        long value = Math.round(condition.value());
        if (counter.startsWith(COLLECTION_PREFIX) && counter.endsWith(PERCENT_SUFFIX)) {
            String category = counter.substring(COLLECTION_PREFIX.length(),
                    counter.length() - PERCENT_SUFFIX.length());
            return strings.format(StringKey.UNLOCK_COLLECTION, value,
                    strings.text(COLLECTION_PREFIX + category));
        }
        return strings.format(StringKey.UNLOCK_COUNTER, counter, value);
    }

    /**
     * The branch of a condition tree a profile is closest to finishing.
     *
     * @param condition the condition tree, may be {@code null}
     * @param profile the profile, may be {@code null} (then every threshold counts as untouched)
     * @return the branch, or {@code null} when there is no condition
     */
    public static UnlockConditionDef cheapestBranch(UnlockConditionDef condition,
            PlayerProfile profile) {
        if (condition == null) {
            return null;
        }
        if (condition.type() != UnlockType.ANY_OF) {
            return condition;
        }
        UnlockConditionDef best = null;
        double bestRemaining = Double.MAX_VALUE;
        for (UnlockConditionDef child : condition.conditions()) {
            UnlockConditionDef branch = cheapestBranch(child, profile);
            if (branch == null) {
                continue;
            }
            double remaining = remaining(branch, profile);
            if (remaining < bestRemaining) {
                best = branch;
                bestRemaining = remaining;
            }
        }
        return best == null ? condition : best;
    }

    /**
     * How much of a condition is still missing, as a fraction of its threshold.
     *
     * <p>The number is a ranking key, not something the player sees: {@code 0} means done,
     * {@code 1} means untouched, and a condition with no measurable progress (a challenge, an
     * achievement, a cleared world) counts as untouched so a countable branch always wins a tie.
     *
     * @param condition the condition
     * @param profile the profile, may be {@code null}
     * @return the missing fraction in {@code [0, 1]}
     */
    private static double remaining(UnlockConditionDef condition, PlayerProfile profile) {
        if (condition.type() == UnlockType.DEFAULT) {
            return 0;
        }
        if (profile == null) {
            return 1;
        }
        double target;
        double current;
        switch (condition.type()) {
            case PURCHASE:
                target = condition.amount();
                current = Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
                break;
            case RUNS:
                target = condition.value();
                current = profile.statistics.totalRuns - profile.prestigeBaseline.totalRuns;
                break;
            case TOTAL_GATES:
                target = condition.value();
                current = profile.statistics.totalGates - profile.prestigeBaseline.totalGates;
                break;
            case COINS_EARNED_TOTAL:
                target = condition.value();
                current = profile.statistics.coinsEarned - profile.prestigeBaseline.coinsEarned;
                break;
            case BEST_GATES:
                target = condition.value();
                current = profile.statistics.bestGates;
                break;
            case BEST_POINTS:
                target = condition.value();
                current = profile.statistics.bestPoints;
                break;
            case LEVEL:
                target = condition.value();
                current = profile.level;
                break;
            case PRESTIGE:
                target = condition.value();
                current = profile.prestigeCount;
                break;
            default:
                return 1;
        }
        if (target <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, 1 - current / target));
    }

    /**
     * The palette a bird shows when nothing else is selected: the first one it declares.
     *
     * @param bird the bird
     * @return the palette id
     */
    public static String defaultPaletteId(BirdDef bird) {
        return bird.palettes().isEmpty() ? PlayerProfile.DEFAULT_PALETTE
                : bird.palettes().get(0).id();
    }
}
