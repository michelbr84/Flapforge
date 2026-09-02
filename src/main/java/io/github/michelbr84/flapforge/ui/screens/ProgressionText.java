package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.ability.BehaviorRegistry;
import io.github.michelbr84.flapforge.ability.ParamSpec;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.AbilityLevelDef;
import io.github.michelbr84.flapforge.content.defs.AbilityTag;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
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

    /** Prefix of the string key naming an ability level parameter. */
    private static final String PARAM_PREFIX = "ability.param.";

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

    // ------------------------------------------------------------------ abilities (M5)

    /**
     * The description of one ability <em>at one level</em> (D9, M5).
     *
     * <p>{@code ability.<id>.desc} is a pattern rather than a sentence, because the numbers that
     * make an ability worth buying a second level of are exactly the ones the level carries. The
     * arguments are positional and the same for every ability, so a translator can rely on them:
     * {@code {0}} the level, {@code {1}} the duration in ticks, {@code {2}} the cooldown in ticks,
     * and from {@code {3}} the behaviour's own parameters in the order its
     * {@link ParamSpec} list declares them. A description that needs none of them simply uses
     * none — {@link Strings#substitute} leaves an index without an argument untouched, and the
     * shipped coin magnet does exactly that.
     *
     * @param strings the string table
     * @param def the ability
     * @param level the owned level, 1-based (clamped into the levels the ability ships)
     * @return the translated description with the level's numbers in it
     */
    public static String abilityDescription(Strings strings, AbilityDef def, int level) {
        return Strings.substitute(description(strings, ContentKind.ABILITY, def.id()),
                (Object[]) abilityArgs(def, level));
    }

    /**
     * The substitution arguments of {@link #abilityDescription}.
     *
     * @param def the ability
     * @param level the owned level
     * @return the arguments, already formatted as the player reads them
     */
    private static String[] abilityArgs(AbilityDef def, int level) {
        AbilityLevelDef levelDef = levelOf(def, level);
        List<ParamSpec> specs = BehaviorRegistry.DEFAULT.params(def.behavior());
        String[] args = new String[3 + specs.size()];
        args[0] = Integer.toString(clampLevel(def, level));
        args[1] = Integer.toString(levelDef.durationTicks());
        args[2] = Integer.toString(levelDef.cooldownTicks());
        for (int i = 0; i < specs.size(); i++) {
            Double value = levelDef.params().get(specs.get(i).key());
            args[3 + i] = number(value == null ? 0 : value);
        }
        return args;
    }

    /**
     * The level of an ability, clamped into the levels it ships.
     *
     * @param def the ability
     * @param level the owned level, 1-based
     * @return the level in {@code [1, levels]}
     */
    public static int clampLevel(AbilityDef def, int level) {
        return Math.max(1, Math.min(def.levels().size(), level));
    }

    private static AbilityLevelDef levelOf(AbilityDef def, int level) {
        return def.levels().get(clampLevel(def, level) - 1);
    }

    /**
     * The owned level of an ability in words, {@code "Level 2/3"}.
     *
     * @param strings the string table
     * @param def the ability
     * @param level the owned level
     * @return the phrase
     */
    public static String abilityLevel(Strings strings, AbilityDef def, int level) {
        return strings.format(StringKey.ABILITY_LEVEL, clampLevel(def, level),
                def.levels().size());
    }

    /**
     * Active or passive, translated.
     *
     * @param strings the string table
     * @param kind the kind
     * @return the word
     */
    public static String abilityKind(Strings strings, AbilityKind kind) {
        return strings.get(kind == AbilityKind.ACTIVE
                ? StringKey.ABILITY_KIND_ACTIVE : StringKey.ABILITY_KIND_PASSIVE);
    }

    /**
     * An ability's tags, translated and joined.
     *
     * @param strings the string table
     * @param def the ability
     * @return the phrase, empty when the ability carries no tag
     */
    public static String abilityTags(Strings strings, AbilityDef def) {
        StringBuilder out = new StringBuilder();
        for (AbilityTag tag : def.tags()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(strings.get(StringKey.valueOf("ABILITY_TAG_" + tag.name())));
        }
        return out.toString();
    }

    /**
     * What one level of an ability actually does, as one line: its timings, its charges and every
     * stat it contributes.
     *
     * <p>Only the numbers that are set are named — a passive has no cooldown and no duration, and
     * the shield at level 1 has no regeneration — so the line never advertises a zero.
     *
     * @param strings the string table
     * @param def the ability
     * @param level the level to describe
     * @return the phrase
     */
    public static String abilityEffects(Strings strings, AbilityDef def, int level) {
        AbilityLevelDef levelDef = levelOf(def, level);
        StringBuilder out = new StringBuilder();
        append(out, effects(strings, def.effects()));
        if (levelDef.durationTicks() > 0) {
            append(out, strings.format(StringKey.ABILITY_EFFECT_DURATION,
                    levelDef.durationTicks()));
        }
        if (levelDef.cooldownTicks() > 0) {
            append(out, strings.format(StringKey.ABILITY_EFFECT_COOLDOWN,
                    levelDef.cooldownTicks()));
        }
        for (ParamSpec spec : BehaviorRegistry.DEFAULT.params(def.behavior())) {
            double value = param(levelDef, spec.key());
            if (value == 0) {
                // A zero is the "off" value of every shipped parameter (no regeneration, no extra
                // invulnerability, no extra radius), and "0 extra ticks of grace" is noise.
                continue;
            }
            append(out, paramText(strings, spec.key(), value));
        }
        return out.toString();
    }

    /**
     * One level parameter in words: {@code "45 ticks of grace"}, {@code "2 charges"}.
     *
     * <p>The label comes from {@code ability.param.<snake_case key>}, so a behaviour that declares
     * a new parameter needs one string and no code. A key with no string falls back to
     * {@code "<key> <value>"} rather than disappearing, which is what makes the gap visible.
     *
     * @param strings the string table
     * @param key the parameter name as {@code abilities.json} spells it
     * @param value the value
     * @return the phrase
     */
    public static String paramText(Strings strings, String key, double value) {
        StringKey label = StringKey.byKey(PARAM_PREFIX + snakeCase(key));
        return label == null ? key + " " + number(value)
                : strings.format(label, number(value));
    }

    /**
     * {@code invulnExtraTicks} to {@code invuln_extra_ticks}.
     *
     * @param key the camel-case parameter name
     * @return the snake-case string key suffix
     */
    private static String snakeCase(String key) {
        StringBuilder out = new StringBuilder(key.length() + 4);
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                out.append('_').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static double param(AbilityLevelDef level, String key) {
        Double value = level.params().get(key);
        return value == null ? 0 : value;
    }

    private static void append(StringBuilder out, String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append(", ");
        }
        out.append(part);
    }

    /**
     * The name of a rule flag, as the screens and the HUD say it.
     *
     * @param strings the string table
     * @param flag the flag
     * @return the phrase
     */
    public static String ruleName(Strings strings, RuleFlag flag) {
        StringKey key = StringKey.byKey("rule." + flag.name().toLowerCase(Locale.ROOT));
        return key == null ? flag.name() : strings.get(key);
    }

    /**
     * The rule that would strip an ability from a run, if any (D9).
     *
     * @param def the ability
     * @param rules the rules the run would carry
     * @return the flag responsible, or {@code null} when the ability survives
     */
    public static RuleFlag strippedBy(AbilityDef def, RuleSet rules) {
        if (rules.contains(RuleFlag.NO_DEFENSIVE_ABILITIES) && def.has(AbilityTag.DEFENSIVE)) {
            return RuleFlag.NO_DEFENSIVE_ABILITIES;
        }
        if (rules.contains(RuleFlag.NO_REVIVE) && def.has(AbilityTag.REVIVE)) {
            return RuleFlag.NO_REVIVE;
        }
        return null;
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
