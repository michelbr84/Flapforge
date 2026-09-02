package io.github.michelbr84.flapforge.content;

import io.github.michelbr84.flapforge.ability.BehaviorRegistry;
import io.github.michelbr84.flapforge.ability.ParamSpec;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.AbilityLevelDef;
import io.github.michelbr84.flapforge.content.defs.AchievementConditionDef;
import io.github.michelbr84.flapforge.content.defs.AchievementDef;
import io.github.michelbr84.flapforge.content.defs.AmbientDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.BossDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.CurveDef;
import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.content.defs.FeatureDef;
import io.github.michelbr84.flapforge.content.defs.GrantDef;
import io.github.michelbr84.flapforge.content.defs.GrantType;
import io.github.michelbr84.flapforge.content.defs.LevelRewardDef;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.ModifiersDef;
import io.github.michelbr84.flapforge.content.defs.ObjectiveType;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.PatternDef;
import io.github.michelbr84.flapforge.content.defs.PatternStepDef;
import io.github.michelbr84.flapforge.content.defs.PrestigeDef;
import io.github.michelbr84.flapforge.content.defs.RuleCycleOptionDef;
import io.github.michelbr84.flapforge.content.defs.RuleCyclesDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.SynergyDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.TreeDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.difficulty.DifficultyCurve;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleParams;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.modifier.Rarity;
import io.github.michelbr84.flapforge.modifier.SynergyResolver;
import io.github.michelbr84.flapforge.progression.StatisticKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The content rules (D10, E19). From M4 the validator is FULL: id syntax and uniqueness, every
 * cross-reference that the supplied file set can resolve, cost and level consistency, the upgrade
 * prerequisite DAG, the E3 caps, the E20 cosmetic-only condition types, the contradiction rules,
 * the classic table and the {@link UnlockGraph}.
 *
 * <p>A cross-reference into a file that was not supplied is <em>not</em> an error
 * ({@link GameContent#has(String)}): milestones M1–M3 ship birds, difficulty and economy alone
 * and their data has to keep passing its own validator (E19), while the shipped M4 set carries
 * every file and is therefore checked in full. The same rule covers {@code patterns.json}: the
 * frozen golden fixture ships neither worlds nor patterns, so every pattern rule below is
 * conditional on the file being there (E19), and switched on for the shipped set since M7.
 *
 * <p>The string-key rules of D25 live in {@link #checkStrings(GameContent)}: they compare the
 * content against {@code data/strings/*.json}, not against another content file, so they run on
 * the <em>shipped</em> content ({@link GameContent#load()}) and are not part of
 * {@link #errorsOf(GameContent)}, which fixtures with deliberately renamed ids go through.
 *
 * <p>Every error carries a {@code file#/json/pointer} location and all of them are raised
 * together as one {@link ContentException}.
 */
public final class ContentValidator {

    /** Ids are lower snake case: {@code ^[a-z][a-z0-9_]*$}. */
    public static final Pattern ID = Pattern.compile("^[a-z][a-z0-9_]*$");

    /** The bird the classic table is proved against. */
    public static final String CLASSIC_BIRD = "classic";
    /** The curve the classic table is proved against. */
    public static final String CLASSIC_CURVE = "classic";
    /** The tier the classic table is proved against. */
    public static final String CLASSIC_TIER = "normal";
    /** Gate count at which {@code MOVING_CHANCE} must have reached its cap (E-table §5). */
    public static final int MOVING_CHANCE_CAP_GATE = 19;

    /**
     * The ability level cap a fresh profile starts with (E3). It mirrors
     * {@code PlayerProfile.DEFAULT_ABILITY_LEVEL_CAP}; the content package states it itself so
     * that validation never depends on the progression package.
     */
    public static final int BASE_ABILITY_LEVEL_CAP = 2;

    /** The most passive slots a bird may ever have, innate slots plus grants (E3). */
    public static final int MAX_PASSIVE_SLOTS = 4;

    /**
     * Smallest distance between two pattern columns, in px (§4 feasibility): a bird needs the
     * room to change lanes between a gate and the hazard after it.
     */
    public static final int MIN_STEP_DX = 100;
    /** Smallest distance a boss phase has to span, in px (§4 feasibility). */
    public static final int MIN_BOSS_PATTERN_DX = 480;
    /**
     * Slack taken off the scroll between a column and the bolt after it before the bolt's
     * travel is checked, in px: the half-width of the bolt column and the bird box's own
     * position inside the previous column.
     */
    public static final double BOLT_SCROLL_SLACK_PX = 5;
    /**
     * Smallest gap a pattern gate may leave on the tightest tier, in px (§4 feasibility):
     * {@code 31 × 1.5 + 8 = 54.5}, the scaled hitbox plus a landing margin.
     */
    public static final double MIN_FEASIBLE_GAP = 54.5;
    /** Safety factor on the tightest-tier gap (§4 feasibility). */
    public static final double GAP_FEASIBILITY_FACTOR = 0.9;

    private static final double EPSILON = 1e-9;
    private static final String PARAM_CHARGES =
            io.github.michelbr84.flapforge.ability.AbilityInstance.PARAM_CHARGES;
    private static final String BIRDS_FILE = "birds.json";
    private static final String DIFFICULTY_FILE = "difficulty.json";
    private static final String ECONOMY_FILE = "economy.json";
    private static final String UPGRADES_FILE = "upgrades.json";
    private static final String ABILITIES_FILE = "abilities.json";
    private static final String MODIFIERS_FILE = "modifiers.json";
    private static final String WORLDS_FILE = "worlds.json";
    private static final String PATTERNS_FILE = "patterns.json";
    private static final String CHALLENGES_FILE = "challenges.json";
    private static final String ACHIEVEMENTS_FILE = "achievements.json";
    private static final String ALIASES_FILE = "aliases.json";

    /** String-key prefix of a bird; {@link ContentKind} is the full list (E31.h). */
    public static final String KIND_BIRD = ContentKind.BIRD.key();
    /** Cosmetic keys are {@code cosmetic.<bird>.<palette>.name/.desc} (E31.h). */
    public static final String KIND_COSMETIC = ContentKind.COSMETIC.key();
    /** Difficulty tiers are shown by name in the run setup. */
    public static final String KIND_TIER = ContentKind.TIER.key();

    /**
     * What the string files say about the content (D25).
     *
     * @param errors keys the code or the content needs and {@code en.json} does not have
     * @param warnings keys a translation is missing, or carries without {@code en.json} having it
     */
    public record StringReport(List<String> errors, List<String> warnings) {

        /**
         * Copies both lists so the report is immutable.
         *
         * @param errors the errors
         * @param warnings the warnings
         */
        public StringReport {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }

        /**
         * Whether every required key resolves.
         *
         * @return {@code true} when there are no errors
         */
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private ContentValidator() {
    }

    /**
     * Validates content and raises every problem at once.
     *
     * @param content the content to check
     * @throws ContentException when at least one rule is broken
     */
    public static void validate(GameContent content) {
        List<String> errors = errorsOf(content);
        if (!errors.isEmpty()) {
            throw new ContentException("Content validation failed", errors);
        }
    }

    /**
     * Collects every problem without throwing (tests and the {@code contentCheck} tool).
     *
     * @param content the content to check
     * @return the errors in discovery order
     */
    public static List<String> errorsOf(GameContent content) {
        List<String> errors = new ArrayList<>();
        validateBirds(content, errors);
        validateCurves(content, errors);
        validateTiers(content, errors);
        validateEconomy(content, errors);
        validateTrees(content, errors);
        validateUpgrades(content, errors);
        validateAbilities(content, errors);
        validateModifiers(content, errors);
        validateWorlds(content, errors);
        validatePatterns(content, errors);
        validateChallenges(content, errors);
        validateAchievements(content, errors);
        validateAliases(content, errors);
        validateCaps(content, errors);
        validateClassicTable(content, errors);
        errors.addAll(UnlockGraph.of(content).errors());
        return errors;
    }

    /**
     * Problems that do not stop the game from running but that a balance pass has to see.
     *
     * @param content the content to check
     * @return the warnings, in discovery order
     */
    public static List<String> warningsOf(GameContent content) {
        List<String> warnings = new ArrayList<>();
        warnPointsSink(content, warnings);
        warnNoOpMultipliers(content, warnings);
        warnUnreachableSynergies(content, warnings);
        warnUnreferencedPatterns(content, warnings);
        return warnings;
    }

    /**
     * E1: {@code points} pay only through {@code rewards.coinsPerPoint}, a {@code best_points}
     * unlock condition or a {@code REACH_POINTS} objective. With none of them, {@code SCORE_MULT}
     * — and every bird spread, upgrade and modifier that touches it — is silently worthless.
     *
     * @param content the content to check
     * @param warnings where to append
     */
    private static void warnPointsSink(GameContent content, List<String> warnings) {
        EconomyDef economy = content.economy();
        if (economy == null || economy.rewards() == null || economy.rewards().coinsPerPoint() > 0) {
            return;
        }
        for (ChallengeDef challenge : content.challenges()) {
            if (challenge.objective().type() == ObjectiveType.REACH_POINTS) {
                return;
            }
        }
        for (UnlockConditionDef condition : everyCondition(content)) {
            if (condition.type() == UnlockType.BEST_POINTS) {
                return;
            }
        }
        warnings.add(ECONOMY_FILE + "#/rewards/coinsPerPoint: 0 leaves SCORE_MULT with no sink —"
                + " points would pay nothing, and no best_points unlock or REACH_POINTS objective"
                + " reads them (E1)");
    }

    private static void warnNoOpMultipliers(GameContent content, List<String> warnings) {
        for (BirdDef bird : content.birds()) {
            warnNoOp(warnings, BIRDS_FILE + "#/" + bird.id() + "/effects", bird.effects());
        }
        for (UpgradeDef node : content.upgrades()) {
            warnNoOp(warnings, UPGRADES_FILE + "#/nodes/" + node.id() + "/effectsPerLevel",
                    node.effectsPerLevel());
        }
        for (WorldDef world : content.worlds()) {
            warnNoOp(warnings, WORLDS_FILE + "#/worlds/" + world.id() + "/effects",
                    world.effects());
        }
        for (ChallengeDef challenge : content.challenges()) {
            warnNoOp(warnings, CHALLENGES_FILE + "#/challenges/" + challenge.id() + "/effects",
                    challenge.effects());
        }
        for (TierDef tier : content.tiers()) {
            warnNoOp(warnings, DIFFICULTY_FILE + "#/tiers/" + tier.id() + "/effects",
                    tier.effects());
        }
    }

    private static void warnNoOp(List<String> warnings, String at, List<StatModifierDef> effects) {
        for (int i = 0; i < effects.size(); i++) {
            StatModifierDef effect = effects.get(i);
            boolean noop = effect.op() == StatOp.MULTIPLY && Math.abs(effect.value() - 1) < EPSILON
                    || effect.op() != StatOp.MULTIPLY && Math.abs(effect.value()) < EPSILON;
            if (noop) {
                warnings.add(at + "/" + i + ": " + effect.stat() + " " + effect.op() + " "
                        + effect.value() + " does nothing");
            }
        }
    }

    // ------------------------------------------------------------------ birds

    private static void validateBirds(GameContent content, List<String> errors) {
        Set<String> seen = new HashSet<>();
        List<BirdDef> defs = content.birds().all();
        for (int i = 0; i < defs.size(); i++) {
            BirdDef def = defs.get(i);
            String at = BIRDS_FILE + "#/" + i;
            checkId(errors, at + "/id", "bird", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate bird id '" + def.id() + "'");
            }
            if (!BirdDef.SHAPES.contains(def.shape())) {
                // ProceduralArt.drawBirdPortrait falls through to the balanced silhouette for a
                // key it does not know, so a typo would ship the wrong bird in silence (D10).
                errors.add(at + "/shape: unknown silhouette '" + def.shape() + "'; the keys are "
                        + new TreeSet<>(BirdDef.SHAPES));
            }
            if (def.passiveSlots() > MAX_PASSIVE_SLOTS) {
                errors.add(at + "/passiveSlots: " + def.passiveSlots() + " is above the maximum "
                        + MAX_PASSIVE_SLOTS + " (E3)");
            }
            for (int a = 0; a < def.passiveAbilities().size(); a++) {
                String abilityId = def.passiveAbilities().get(a);
                if (content.has(GameContent.ABILITIES)
                        && !content.abilities().contains(abilityId)) {
                    errors.add(at + "/passiveAbilities/" + a + ": unknown ability '" + abilityId
                            + "'");
                }
            }
            checkCondition(content, errors, at + "/unlock", def.unlock(), false);
            Set<String> palettes = new HashSet<>();
            for (int p = 0; p < def.palettes().size(); p++) {
                PaletteDef palette = def.palettes().get(p);
                String pat = at + "/palettes/" + p;
                checkId(errors, pat + "/id", "palette", palette.id());
                if (!palettes.add(palette.id())) {
                    errors.add(pat + "/id: duplicate palette id '" + palette.id() + "' on bird '"
                            + def.id() + "'");
                }
                checkCondition(content, errors, pat + "/unlock", palette.unlock(), true);
            }
            if (def.palettes().isEmpty()) {
                errors.add(at + "/palettes: bird '" + def.id() + "' has no palette");
            }
        }
    }

    private static void validateCurves(GameContent content, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (CurveDef def : content.curves()) {
            String at = DIFFICULTY_FILE + "#/curves/" + def.id();
            checkId(errors, at, "curve", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + ": duplicate curve id '" + def.id() + "'");
            }
        }
    }

    private static void validateTiers(GameContent content, List<String> errors) {
        Set<String> seen = new HashSet<>();
        int defaults = 0;
        List<TierDef> defs = content.tiers().all();
        for (int i = 0; i < defs.size(); i++) {
            TierDef def = defs.get(i);
            String at = DIFFICULTY_FILE + "#/tiers/" + i;
            checkId(errors, at + "/id", "tier", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate tier id '" + def.id() + "'");
            }
            if (def.defaultTier()) {
                defaults++;
            }
            checkCondition(content, errors, at + "/unlock", def.unlock(), false);
            checkContradictions(errors, at, "tier '" + def.id() + "'", def.flags(), def.effects(),
                    null);
        }
        if (defs.isEmpty()) {
            errors.add(DIFFICULTY_FILE + "#/tiers: no tier is defined");
        } else if (defaults != 1) {
            errors.add(DIFFICULTY_FILE + "#/tiers: exactly one tier must be flagged \"default\", "
                    + "found " + defaults);
        }
    }

    // ---------------------------------------------------------------- economy

    private static void validateEconomy(GameContent content, List<String> errors) {
        EconomyDef economy = content.economy();
        if (economy == null) {
            errors.add(ECONOMY_FILE + "#: missing economy");
            return;
        }
        validateCurrencies(economy, errors);
        validateLevelRewards(content, economy, errors);
        validateFeatures(content, economy, errors);
        validatePrestige(economy, errors);
        List<String> pool = economy.daily().tierPool();
        for (int i = 0; i < pool.size(); i++) {
            if (!content.tiers().contains(pool.get(i))) {
                errors.add(ECONOMY_FILE + "#/daily/tierPool/" + i + ": unknown tier '"
                        + pool.get(i) + "'");
            }
        }
    }

    private static void validateCurrencies(EconomyDef economy, List<String> errors) {
        List<String> currencies = economy.currencies();
        if (currencies.isEmpty()) {
            errors.add(ECONOMY_FILE + "#/currencies: no currency is defined");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < currencies.size(); i++) {
            String id = currencies.get(i);
            String at = ECONOMY_FILE + "#/currencies/" + i;
            checkId(errors, at, "currency", id);
            if (!seen.add(id)) {
                errors.add(at + ": duplicate currency id '" + id + "'");
            }
        }
        if (!currencies.contains(EconomyDef.COINS)) {
            errors.add(ECONOMY_FILE + "#/currencies: every reward is paid in '" + EconomyDef.COINS
                    + "', which is not a declared currency");
        }
    }

    private static void validateLevelRewards(GameContent content, EconomyDef economy,
            List<String> errors) {
        int maxLevel = economy.xp().curve().maxLevel();
        for (Map.Entry<String, LevelRewardDef> entry : economy.xp().levelRewards().entrySet()) {
            String key = entry.getKey();
            String at = ECONOMY_FILE + "#/xp/levelRewards/" + key;
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                errors.add(at + ": level reward key '" + key + "' is not an integer");
                continue;
            }
            if (level < 2) {
                errors.add(at + ": level reward key '" + key + "' must be at least 2 (a profile "
                        + "starts at level 1)");
            } else if (level > maxLevel) {
                errors.add(at + ": level reward key '" + key + "' is above xp.curve.maxLevel "
                        + maxLevel);
            }
            checkUnlocks(content, errors, at + "/unlocks", entry.getValue().unlocks());
        }
    }

    private static void validateFeatures(GameContent content, EconomyDef economy,
            List<String> errors) {
        Set<String> seen = new HashSet<>();
        List<FeatureDef> features = economy.features();
        for (int i = 0; i < features.size(); i++) {
            FeatureDef def = features.get(i);
            String at = ECONOMY_FILE + "#/features/" + i;
            checkId(errors, at + "/id", "feature", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate feature id '" + def.id() + "'");
            }
            checkCondition(content, errors, at + "/unlock", def.unlock(), false);
        }
    }

    private static void validatePrestige(EconomyDef economy, List<String> errors) {
        PrestigeDef prestige = economy.prestige();
        List<String> keeps = prestige.keeps();
        for (int i = 0; i < keeps.size(); i++) {
            if (!PrestigeDef.KEEPS.contains(keeps.get(i))) {
                errors.add(ECONOMY_FILE + "#/prestige/keeps/" + i + ": unknown keep '"
                        + keeps.get(i) + "' (expected one of " + PrestigeDef.KEEPS + ")");
            }
        }
    }

    // --------------------------------------------------------------- upgrades

    private static void validateTrees(GameContent content, List<String> errors) {
        Set<String> seen = new HashSet<>();
        List<TreeDef> defs = content.trees().all();
        for (int i = 0; i < defs.size(); i++) {
            TreeDef def = defs.get(i);
            String at = UPGRADES_FILE + "#/trees/" + i;
            checkId(errors, at + "/id", "tree", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate tree id '" + def.id() + "'");
            }
            checkCondition(content, errors, at + "/unlock", def.unlock(), false);
        }
    }

    private static void validateUpgrades(GameContent content, List<String> errors) {
        Set<String> seen = new HashSet<>();
        List<UpgradeDef> defs = content.upgrades().all();
        for (int i = 0; i < defs.size(); i++) {
            UpgradeDef def = defs.get(i);
            String at = UPGRADES_FILE + "#/nodes/" + i;
            checkId(errors, at + "/id", "upgrade", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate upgrade id '" + def.id() + "'");
            }
            if (!content.trees().contains(def.tree())) {
                errors.add(at + "/tree: unknown tree '" + def.tree() + "'");
            }
            if (def.costs().size() != def.maxLevel()) {
                errors.add(at + "/costs: " + def.costs().size() + " costs for maxLevel "
                        + def.maxLevel() + " (costs.length must equal maxLevel)");
            }
            if (def.effectsPerLevel().isEmpty() && def.grants().isEmpty()) {
                errors.add(at + ": node '" + def.id() + "' has neither effects nor grants");
            }
            for (Map.Entry<String, List<StatModifierDef>> override
                    : def.levelOverrides().entrySet()) {
                String key = override.getKey();
                int level;
                try {
                    level = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    errors.add(at + "/levelOverrides/" + key + ": key is not an integer");
                    continue;
                }
                if (level < 1 || level > def.maxLevel()) {
                    errors.add(at + "/levelOverrides/" + key + ": level is outside 1.."
                            + def.maxLevel());
                }
            }
            for (int p = 0; p < def.prereqs().size(); p++) {
                String prereqId = def.prereqs().get(p);
                String pat = at + "/prereqs/" + p;
                if (!content.upgrades().contains(prereqId)) {
                    errors.add(pat + ": unknown upgrade node '" + prereqId + "'");
                    continue;
                }
                UpgradeDef prereq = content.upgrades().get(prereqId);
                if (!prereq.tree().equals(def.tree())) {
                    errors.add(pat + ": '" + prereqId + "' is in tree '" + prereq.tree()
                            + "', not in '" + def.tree() + "'");
                } else if (prereq.tier() >= def.tier()) {
                    errors.add(pat + ": '" + prereqId + "' is in tier " + prereq.tier()
                            + ", which is not below tier " + def.tier() + " of '" + def.id() + "'");
                }
            }
            for (int g = 0; g < def.grants().size(); g++) {
                GrantDef grant = def.grants().get(g);
                if (grant.type() == GrantType.UNLOCK) {
                    checkUnlockable(content, errors, at + "/grants/" + g + "/id", grant.id());
                }
            }
        }
        checkPrereqDag(content, errors);
    }

    /**
     * The prerequisite graph must be acyclic; the tier rule above already forbids most loops, but
     * a node whose prerequisite sits in the same tier chain would otherwise be unbuyable forever.
     *
     * @param content the content to check
     * @param errors where to append problems
     */
    private static void checkPrereqDag(GameContent content, List<String> errors) {
        Set<String> done = new HashSet<>();
        Set<String> reported = new HashSet<>();
        for (UpgradeDef node : content.upgrades()) {
            walkPrereqs(content, node.id(), new LinkedHashSet<>(), done, reported, errors);
        }
    }

    private static void walkPrereqs(GameContent content, String id, LinkedHashSet<String> stack,
            Set<String> done, Set<String> reported, List<String> errors) {
        if (done.contains(id)) {
            return;
        }
        if (!stack.add(id)) {
            List<String> loop = new ArrayList<>(stack);
            loop.add(id);
            String signature = new java.util.TreeSet<>(loop).toString();
            if (reported.add(signature)) {
                errors.add(UPGRADES_FILE + "#/nodes/" + id + "/prereqs: prerequisite cycle "
                        + String.join(" -> ", loop));
            }
            return;
        }
        if (content.upgrades().contains(id)) {
            for (String prereq : content.upgrades().get(id).prereqs()) {
                walkPrereqs(content, prereq, stack, done, reported, errors);
            }
        }
        stack.remove(id);
        done.add(id);
    }

    // -------------------------------------------------------------- abilities

    private static void validateAbilities(GameContent content, List<String> errors) {
        Set<String> seen = new HashSet<>();
        List<AbilityDef> defs = content.abilities().all();
        for (int i = 0; i < defs.size(); i++) {
            AbilityDef def = defs.get(i);
            String at = ABILITIES_FILE + "#/abilities/" + i;
            checkId(errors, at + "/id", "ability", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate ability id '" + def.id() + "'");
            }
            if (def.levels().isEmpty()) {
                errors.add(at + "/levels: ability '" + def.id() + "' has no level");
            } else if (def.levels().get(0).cost() != 0) {
                errors.add(at + "/levels/0/cost: level 1 comes with the unlock and must cost 0, "
                        + "not " + def.levels().get(0).cost());
            }
            for (int l = 1; l < def.levels().size(); l++) {
                AbilityLevelDef level = def.levels().get(l);
                if (level.cost() <= 0) {
                    errors.add(at + "/levels/" + l + "/cost: level " + (l + 1)
                            + " is bought in the shop and must cost more than 0");
                }
                if (level.cost() <= def.levels().get(l - 1).cost()) {
                    errors.add(at + "/levels/" + l + "/cost: level " + (l + 1)
                            + " must cost more than the level below it");
                }
            }
            checkBehavior(errors, at, def);
            checkAbilityTimings(errors, at, def);
            checkAbilityParams(errors, at, def);
            checkCondition(content, errors, at + "/unlock", def.unlock(), false);
        }
    }

    /**
     * M5: the behaviour id must be implemented. Until {@code BehaviorRegistry} existed an unknown
     * id was a run that silently did nothing; now it is a content error, which is the whole point
     * of pairing the data with a registry (D9, E19).
     *
     * @param errors where to append
     * @param at the pointer of the ability
     * @param def the ability
     */
    private static void checkBehavior(List<String> errors, String at, AbilityDef def) {
        if (!BehaviorRegistry.DEFAULT.contains(def.behavior())) {
            errors.add(at + "/behavior: unknown ability behavior '" + def.behavior()
                    + "'; known: " + BehaviorRegistry.DEFAULT.ids());
        }
    }

    /**
     * Cooldowns and durations: a passive has neither, an active has at least one way of being
     * gated, an active that authors {@code effects} has a window for them to apply in, and the
     * columns move the way a level-up is supposed to move them — cooldowns never up, durations
     * never down.
     *
     * @param errors where to append
     * @param at the pointer of the ability
     * @param def the ability
     */
    private static void checkAbilityTimings(List<String> errors, String at, AbilityDef def) {
        for (int l = 0; l < def.levels().size(); l++) {
            AbilityLevelDef level = def.levels().get(l);
            String levelAt = at + "/levels/" + l;
            if (def.kind() == AbilityKind.PASSIVE
                    && (level.cooldownTicks() != 0 || level.durationTicks() != 0)) {
                errors.add(levelAt + ": a PASSIVE ability is always on and must declare "
                        + "cooldownTicks 0 and durationTicks 0");
            }
            if (def.kind() == AbilityKind.ACTIVE && level.cooldownTicks() == 0
                    && level.durationTicks() == 0
                    && level.params().getOrDefault(PARAM_CHARGES, 0.0) <= 0) {
                errors.add(levelAt + ": an ACTIVE ability needs a cooldown, a duration or "
                        + "charges, otherwise it can be activated every tick for free");
            }
            if (def.kind() == AbilityKind.ACTIVE && !def.effects().isEmpty()
                    && level.durationTicks() == 0) {
                // An active contributes its effects only while its duration runs
                // (AbilityManager.publishLayer), so a zero here is an ability whose whole stat
                // half silently does nothing — exactly the class of typo D10/E19 wants caught.
                errors.add(levelAt + "/durationTicks: an ACTIVE ability with effects needs a "
                        + "duration, or its effects never reach the ABILITY layer");
            }
            if (l == 0) {
                continue;
            }
            AbilityLevelDef previous = def.levels().get(l - 1);
            if (level.cooldownTicks() > previous.cooldownTicks()) {
                errors.add(levelAt + "/cooldownTicks: a level up must not lengthen the cooldown ("
                        + previous.cooldownTicks() + " -> " + level.cooldownTicks() + ")");
            }
            if (level.durationTicks() < previous.durationTicks()) {
                errors.add(levelAt + "/durationTicks: a level up must not shorten the duration ("
                        + previous.durationTicks() + " -> " + level.durationTicks() + ")");
            }
        }
    }

    /**
     * The {@code params} contract (D9): every key a level declares is read by the behaviour,
     * every required key is declared by every level, values are inside the behaviour's range and
     * each column follows its {@link ParamSpec.Trend}.
     *
     * @param errors where to append
     * @param at the pointer of the ability
     * @param def the ability
     */
    private static void checkAbilityParams(List<String> errors, String at, AbilityDef def) {
        List<ParamSpec> specs = BehaviorRegistry.DEFAULT.params(def.behavior());
        if (!BehaviorRegistry.DEFAULT.contains(def.behavior())) {
            return;
        }
        Set<String> known = new HashSet<>();
        for (ParamSpec spec : specs) {
            known.add(spec.key());
        }
        for (int l = 0; l < def.levels().size(); l++) {
            String levelAt = at + "/levels/" + l + "/params";
            for (Map.Entry<String, Double> entry : def.levels().get(l).params().entrySet()) {
                if (!known.contains(entry.getKey())) {
                    errors.add(levelAt + "/" + entry.getKey() + ": behavior '" + def.behavior()
                            + "' reads no such parameter; it reads " + keysOf(specs));
                }
            }
        }
        for (ParamSpec spec : specs) {
            Double previous = null;
            for (int l = 0; l < def.levels().size(); l++) {
                String levelAt = at + "/levels/" + l + "/params/" + spec.key();
                Double value = def.levels().get(l).params().get(spec.key());
                if (value == null) {
                    if (spec.required()) {
                        errors.add(levelAt + ": behavior '" + def.behavior()
                                + "' requires this parameter at every level");
                    }
                    continue;
                }
                if (!spec.accepts(value)) {
                    errors.add(levelAt + ": " + value + " is outside [" + spec.min() + ", "
                            + spec.max() + "]");
                }
                if (previous != null && !spec.accepts(previous, value)) {
                    errors.add(levelAt + ": " + previous + " -> " + value
                            + " goes the wrong way for a " + spec.trend() + " parameter");
                }
                previous = value;
            }
        }
    }

    private static List<String> keysOf(List<ParamSpec> specs) {
        List<String> keys = new ArrayList<>(specs.size());
        for (ParamSpec spec : specs) {
            keys.add(spec.key());
        }
        return keys;
    }

    // ----------------------------------------------------------------- worlds

    private static void validateWorlds(GameContent content, List<String> errors) {
        Set<String> seen = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        List<WorldDef> defs = content.worlds().all();
        for (int i = 0; i < defs.size(); i++) {
            WorldDef def = defs.get(i);
            String at = WORLDS_FILE + "#/worlds/" + i;
            checkId(errors, at + "/id", "world", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate world id '" + def.id() + "'");
            }
            if (!orders.add(def.order())) {
                errors.add(at + "/order: duplicate order " + def.order());
            }
            if (!content.curves().contains(def.curve())) {
                errors.add(at + "/curve: unknown curve '" + def.curve() + "'");
            }
            checkCondition(content, errors, at + "/unlock", def.unlock(), false);
            checkContradictions(errors, at, "world '" + def.id() + "'", def.flags(), def.effects(),
                    null);
            checkSpawnWeights(errors, at + "/spawnWeights", def);
            checkAmbient(errors, at + "/ambient", def.ambient());
            checkRuleCycles(errors, at + "/ruleCycles", def.ruleCycles());
            for (int p = 0; p < def.patterns().size(); p++) {
                checkWorldPattern(content, errors, at + "/patterns/" + p, def,
                        def.patterns().get(p));
            }
            checkBoss(content, errors, at + "/boss", def.boss(), true);
        }
    }

    /**
     * A world's spawn table needs something to draw: the binder already guarantees the keys are
     * kinds, so what is left is at least one positive weight (a {@code SpawnTable} with none
     * throws at run start).
     *
     * @param errors where to append problems
     * @param at the pointer of the weights
     * @param def the world
     */
    private static void checkSpawnWeights(List<String> errors, String at, WorldDef def) {
        int total = 0;
        for (Integer weight : def.spawnWeights().values()) {
            total += weight;
        }
        if (total <= 0) {
            errors.add(at + ": world '" + def.id() + "' has no positive spawn weight");
        }
    }

    /**
     * The ambience ranges (§4, M7): the wind is the {@code WindZone} mechanism made permanent, so
     * it takes a zone's ranges; darkness and the flash period are checked by the record itself.
     *
     * @param errors where to append problems
     * @param at the pointer of the ambient block
     * @param ambient the block, or {@code null} for still air
     */
    private static void checkAmbient(List<String> errors, String at, AmbientDef ambient) {
        if (ambient == null) {
            return;
        }
        if (ambient.windX() < WindZone.MIN_SCROLL_DELTA || ambient.windX() > WindZone.MAX_SCROLL_DELTA) {
            errors.add(at + "/windX: " + ambient.windX() + " is outside ["
                    + WindZone.MIN_SCROLL_DELTA + ", " + WindZone.MAX_SCROLL_DELTA + "] px/s");
        }
        if (ambient.windY() < WindZone.MIN_ACCEL_Y || ambient.windY() > WindZone.MAX_ACCEL_Y) {
            errors.add(at + "/windY: " + ambient.windY() + " is outside [" + WindZone.MIN_ACCEL_Y
                    + ", " + WindZone.MAX_ACCEL_Y + "] px/s²");
        }
    }

    /**
     * The rule cycles of a world (§4, E31.g, M7): at least two options, because a shift never
     * lands the option already in force and one option could never shift; every option is a set
     * of flags and effects the run can actually apply — no {@code MOVING_CHANCE}, which the spawn
     * decision reads (E32.d), and none of the contradictions a challenge is refused.
     *
     * @param errors where to append problems
     * @param at the pointer of the block
     * @param cycles the block, or {@code null}
     */
    private static void checkRuleCycles(List<String> errors, String at, RuleCyclesDef cycles) {
        if (cycles == null) {
            return;
        }
        List<RuleCycleOptionDef> options = cycles.options();
        if (options.isEmpty()) {
            errors.add(at + "/options: a rule cycle needs at least one option");
            return;
        }
        if (options.size() < 2) {
            errors.add(at + "/options: a rule cycle needs at least two options, because a shift"
                    + " never lands the option already in force");
        }
        for (int i = 0; i < options.size(); i++) {
            RuleCycleOptionDef option = options.get(i);
            String optionAt = at + "/options/" + i;
            if (option.flags().isEmpty() && option.effects().isEmpty()) {
                errors.add(optionAt + ": a rule cycle option has to turn on a flag or apply an"
                        + " effect");
            }
            checkSpawnCriticalStats(errors, optionAt + "/effects", "a rule cycle option",
                    option.effects());
            checkContradictions(errors, optionAt, "rule cycle option " + i, option.flags(),
                    option.effects(), null);
        }
    }

    /**
     * A pattern a world lists (M7): it has to exist, belong to that world and carry a positive
     * weight, or the world could never draw it.
     *
     * @param content the content to check
     * @param errors where to append problems
     * @param at the pointer of the reference
     * @param world the world
     * @param patternId the id
     */
    private static void checkWorldPattern(GameContent content, List<String> errors, String at,
            WorldDef world, String patternId) {
        if (patternId == null || patternId.isBlank()) {
            errors.add(at + ": empty pattern id");
            return;
        }
        if (!content.has(GameContent.PATTERNS)) {
            return;
        }
        if (!content.patterns().contains(patternId)) {
            errors.add(at + ": unknown pattern '" + patternId + "'");
            return;
        }
        PatternDef pattern = content.patterns().get(patternId);
        if (!pattern.world().equals(world.id())) {
            errors.add(at + ": pattern '" + patternId + "' belongs to world '" + pattern.world()
                    + "', not to '" + world.id() + "'");
        }
        if (pattern.weight() <= 0) {
            errors.add(at + ": pattern '" + patternId + "' has weight 0, so world '" + world.id()
                    + "' could never draw it");
        }
    }

    private static void checkBoss(GameContent content, List<String> errors, String at,
            BossDef boss, boolean rewardRequired) {
        if (boss == null) {
            return;
        }
        if (rewardRequired && boss.reward() == null) {
            errors.add(at + "/reward: a world boss must pay a reward (E26)");
        }
        if (!rewardRequired && boss.reward() != null) {
            errors.add(at + "/reward: a challenge boss never pays; the challenge does (E26)");
        }
        if (boss.reward() != null) {
            checkUnlocks(content, errors, at + "/reward/unlocks", boss.reward().unlocks());
        }
        for (int p = 0; p < boss.patterns().size(); p++) {
            checkPattern(content, errors, at + "/patterns/" + p, boss.patterns().get(p), true);
        }
        if (boss.patterns().isEmpty()) {
            errors.add(at + "/patterns: a boss needs at least one pattern");
        }
    }

    /**
     * A pattern streamed on demand — a boss phase or a challenge's forced pattern (M7, E19: the
     * check is live now that {@code patterns.json} ships). It has to exist and carry weight 0,
     * because a pattern the world also draws by weight would double as a random set piece; a
     * boss phase has to span {@value #MIN_BOSS_PATTERN_DX} px, so a looped phase is a fight and
     * not a single column (§4 feasibility).
     *
     * @param content the content to check
     * @param errors where to append problems
     * @param at the pointer of the reference
     * @param patternId the id
     * @param boss {@code true} for a boss phase, {@code false} for a forced pattern
     */
    private static void checkPattern(GameContent content, List<String> errors, String at,
            String patternId, boolean boss) {
        if (patternId == null || patternId.isBlank()) {
            errors.add(at + ": empty pattern id");
            return;
        }
        if (!content.has(GameContent.PATTERNS)) {
            return;
        }
        if (!content.patterns().contains(patternId)) {
            errors.add(at + ": unknown pattern '" + patternId + "'");
            return;
        }
        PatternDef pattern = content.patterns().get(patternId);
        String role = boss ? "a boss phase" : "a forced pattern";
        if (pattern.weight() != 0) {
            errors.add(at + ": '" + patternId + "' is " + role + " and must have weight 0 (it has "
                    + pattern.weight() + "), or the world would also draw it at random");
        }
        if (boss && pattern.totalDx() < MIN_BOSS_PATTERN_DX) {
            errors.add(at + ": boss phase '" + patternId + "' spans " + pattern.totalDx()
                    + " px, less than the " + MIN_BOSS_PATTERN_DX + " a phase needs (§4)");
        }
    }

    // --------------------------------------------------------------- patterns

    /**
     * The obstacle patterns (§4, D10, M7): ids, the world each belongs to, every step's
     * parameters against its kind's {@code ParamSpec} (unknown or out-of-range values are
     * rejected with the step's pointer), and the feasibility rules — {@code dx ≥ 100} between
     * columns, a gate's {@code gapSize × (tightest tier gap multiplier) × 0.9 ≥ 54.5}, a
     * piston's {@code telegraphTicks ≥ 15} and a bolt's {@code lengthFrac ≤ 0.7} (both part of
     * the {@code ParamSpec} ranges), and a gate right after a bolt authored on the bolt's unlit
     * side — plus the listing rule: a pattern with a positive weight has to be listed by its
     * world, or it is content nothing can reach.
     *
     * @param content the content to check
     * @param errors where to append problems
     */
    private static void validatePatterns(GameContent content, List<String> errors) {
        if (!content.has(GameContent.PATTERNS)) {
            return;
        }
        double tightestGap = tightestTierGapMultiplier(content);
        Set<String> seen = new HashSet<>();
        List<PatternDef> defs = content.patterns().all();
        for (int i = 0; i < defs.size(); i++) {
            PatternDef def = defs.get(i);
            String at = PATTERNS_FILE + "#/patterns/" + i;
            checkId(errors, at + "/id", "pattern", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate pattern id '" + def.id() + "'");
            }
            WorldDef world = null;
            if (content.has(GameContent.WORLDS)) {
                if (!content.worlds().contains(def.world())) {
                    errors.add(at + "/world: unknown world '" + def.world() + "'");
                } else {
                    world = content.worlds().get(def.world());
                }
            }
            if (world != null && def.weight() > 0 && !world.patterns().contains(def.id())) {
                errors.add(at + "/weight: pattern '" + def.id() + "' has weight " + def.weight()
                        + " but world '" + def.world() + "' does not list it, so it is never drawn");
            }
            if (def.steps().isEmpty()) {
                errors.add(at + "/steps: a pattern needs at least one step");
            }
            double lastLethalBand = Double.NaN;
            double lastLethalWidth = 0;
            double sinceLethal = 0;
            for (int j = 0; j < def.steps().size(); j++) {
                PatternStepDef step = def.steps().get(j);
                checkStep(errors, at + "/steps/" + j, def, step, tightestGap);
                if (j > 0) {
                    checkGateAfterBolt(errors, at + "/steps/" + j, def.steps().get(j - 1), step);
                }
                sinceLethal += step.dx();
                if (step.kind() == ObstacleKind.LIGHTNING && !Double.isNaN(lastLethalBand)) {
                    checkBoltReachable(errors, at + "/steps/" + j, step, lastLethalBand,
                            sinceLethal - lastLethalWidth - BOLT_SCROLL_SLACK_PX);
                }
                if (step.kind() != ObstacleKind.WIND_ZONE) {
                    lastLethalBand = referenceBandOf(step);
                    lastLethalWidth = columnWidthOf(step);
                    sinceLethal = 0;
                }
            }
        }
    }

    /**
     * A lightning column is reachable from the lethal column before it (M7 fairness, the
     * authored twin of the spawn table's rule): the vertical travel from that column's band to
     * the bolt's safe band ({@link SpawnTable#lightningTravel}) may not exceed the scroll
     * between the column clearing the bird and the strike — the distance between them less the
     * column's width and a few px — because the bird climbs about one px per px of scroll at
     * the fastest tier scroll. A previous gate with a {@code "random"} centre cannot be checked
     * and is skipped.
     *
     * @param errors where to append problems
     * @param at the pointer of the bolt step
     * @param step the bolt step
     * @param previousBandY the band of the lethal column before it
     * @param scrollPx the scroll between that column clearing the bird and the strike
     */
    private static void checkBoltReachable(List<String> errors, String at, PatternStepDef step,
            double previousBandY, double scrollPx) {
        Object side = step.params().get("side");
        Object frac = step.params().get("lengthFrac");
        if (!(frac instanceof Number number) || side == null) {
            return;
        }
        Side boltSide = "BOTTOM".equals(String.valueOf(side)) ? Side.BOTTOM : Side.TOP;
        double travel = SpawnTable.lightningTravel(boltSide, number.doubleValue(), previousBandY);
        if (travel > scrollPx) {
            errors.add(String.format(Locale.ROOT, "%s/params/lengthFrac: the bolt's safe band is"
                    + " %.0f px from the band of the column before it, more than the %.0f px of"
                    + " scroll between them at the strike; lower the fraction, move the previous"
                    + " column's band or widen dx (§4 feasibility)", at, travel, scrollPx));
        }
    }

    /**
     * The band a bird crosses a lethal pattern step in, from its authored params (the same
     * geometry {@code SpawnDecision.referenceBandY} derives for a spawned column).
     *
     * @param step the step
     * @return the band centre, or {@code NaN} when it cannot be known (a random gate centre)
     */
    private static double referenceBandOf(PatternStepDef step) {
        Map<String, Object> p = step.params();
        switch (step.kind()) {
            case PIPE_GATE:
                return p.get("gapCenter") instanceof Number c
                        ? c.doubleValue() * Playfield.GROUND_Y : Double.NaN;
            case GEAR: {
                double cy = number(p.get("cy"), 0.5) * Playfield.GROUND_Y;
                double radius = number(p.get("radius"), Gear.MIN_RADIUS);
                double amplitude = p.get("rail") instanceof Map<?, ?> rail
                        ? number(rail.get("amplitude"), 0) : 0;
                double half = amplitude / 2 + radius;
                double above = cy - half;
                double below = Playfield.GROUND_Y - (cy + half);
                return above >= below ? (cy - half) / 2 : (cy + half + Playfield.GROUND_Y) / 2;
            }
            case PISTON: {
                double length = number(p.get("length"), Piston.MIN_LENGTH);
                return "BOTTOM".equals(String.valueOf(p.get("side")))
                        ? (Playfield.GROUND_Y - length) / 2 : (length + Playfield.GROUND_Y) / 2;
            }
            case LIGHTNING: {
                double lit = number(p.get("lengthFrac"), 0.5) * Playfield.GROUND_Y;
                return "BOTTOM".equals(String.valueOf(p.get("side")))
                        ? (Playfield.GROUND_Y - lit) / 2 : (lit + Playfield.GROUND_Y) / 2;
            }
            case WIND_ZONE:
            default:
                return Double.NaN;
        }
    }

    /** The lethal column width of a step: a gear's diameter, a bolt's 24 px, a pipe body. */
    private static double columnWidthOf(PatternStepDef step) {
        switch (step.kind()) {
            case GEAR:
                return 2 * number(step.params().get("radius"), Gear.MIN_RADIUS);
            case LIGHTNING:
                return LightningStrike.WIDTH;
            default:
                return Playfield.PIPE_BODY_W;
        }
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    /**
     * A gate placed right after a lightning column has its gap on the bolt's unlit side (M7
     * fairness): the bird is pinned to that side until the bolt has passed, and a {@code dx} of
     * 100–200 px leaves no room to dive or climb across a gap that opened on the other one. So
     * the centre is authored — never {@code "random"} — at or below {@code 0.5} after a
     * {@code BOTTOM} bolt (which lights the lower part) and at or above it after a {@code TOP}
     * bolt.
     *
     * @param errors where to append problems
     * @param at the pointer of the gate step
     * @param previous the step before it
     * @param step the gate step
     */
    private static void checkGateAfterBolt(List<String> errors, String at, PatternStepDef previous,
            PatternStepDef step) {
        if (previous.kind() != ObstacleKind.LIGHTNING || step.kind() != ObstacleKind.PIPE_GATE) {
            return;
        }
        Object side = previous.params().get("side");
        Object centre = step.params().get("gapCenter");
        boolean bottom = "BOTTOM".equals(String.valueOf(side));
        String unlit = bottom ? "at or below 0.5 (a BOTTOM bolt lights the lower part)"
                : "at or above 0.5 (a TOP bolt lights the upper part)";
        if (!(centre instanceof Number number)) {
            errors.add(at + "/params/gapCenter: a gate right after a bolt needs an authored"
                    + " centre " + unlit + ", not '" + centre + "' (§4 feasibility)");
            return;
        }
        double c = number.doubleValue();
        if (bottom ? c > 0.5 : c < 0.5) {
            errors.add(at + "/params/gapCenter: " + number + " is on the lit side of the bolt"
                    + " before it; a gate right after a bolt sits " + unlit + " (§4 feasibility)");
        }
    }

    /**
     * One pattern step: the distance rule, the kind's parameter contract and the gate gap rule.
     *
     * @param errors where to append problems
     * @param at the pointer of the step
     * @param pattern the pattern
     * @param step the step
     * @param tightestGap the smallest {@code GAP_SIZE} multiplier any tier applies
     */
    private static void checkStep(List<String> errors, String at, PatternDef pattern,
            PatternStepDef step, double tightestGap) {
        if (step.dx() < MIN_STEP_DX) {
            errors.add(at + "/dx: " + step.dx() + " px between columns is less than the "
                    + MIN_STEP_DX + " a bird needs to change lanes (§4 feasibility)");
        }
        for (String problem : ObstacleParams.validate(step.kind(), step.params())) {
            int colon = problem.indexOf(':');
            String key = colon < 0 ? "" : "/" + problem.substring(0, colon).replace('.', '/');
            String text = colon < 0 ? problem : problem.substring(colon + 1).trim();
            errors.add(at + "/params" + key + ": " + text);
        }
        if (step.kind() == ObstacleKind.PIPE_GATE) {
            Object gapSize = step.params().get("gapSize");
            if (gapSize instanceof Number number) {
                double tightest = number.doubleValue() * tightestGap * GAP_FEASIBILITY_FACTOR;
                if (tightest < MIN_FEASIBLE_GAP) {
                    errors.add(String.format(java.util.Locale.ROOT,
                            "%s/params/gapSize: %s px leaves %.1f px on the tightest tier"
                                    + " (× %.2f × %.1f), less than the %.1f a bird fits through"
                                    + " (§4 feasibility)",
                            at, number, tightest, tightestGap, GAP_FEASIBILITY_FACTOR,
                            MIN_FEASIBLE_GAP));
                }
            }
        }
        if (step.kind() == ObstacleKind.PISTON
                && step.params().get("telegraphTicks") instanceof Number telegraph
                && telegraph.doubleValue() < Piston.MIN_TELEGRAPH_TICKS) {
            // Also refused by the ParamSpec range; named here so the feasibility rule reads
            // as one list (§4).
            errors.add(at + "/params/telegraphTicks: a piston needs at least "
                    + Piston.MIN_TELEGRAPH_TICKS + " ticks of warning (§4 feasibility)");
        }
        if (step.kind() == ObstacleKind.LIGHTNING
                && step.params().get("lengthFrac") instanceof Number frac
                && frac.doubleValue() > LightningStrike.MAX_LENGTH_FRAC) {
            errors.add(at + "/params/lengthFrac: a bolt may light at most "
                    + LightningStrike.MAX_LENGTH_FRAC + " of the height, so a safe band always"
                    + " exists (§4 feasibility)");
        }
    }

    /**
     * The smallest {@code GAP_SIZE} multiplier any tier applies (the nightmare tier's 0.8 in the
     * shipped set): the factor the gate feasibility rule scales an authored gap by.
     *
     * @param content the content to check
     * @return the multiplier, 1 when no tier shrinks the gap
     */
    private static double tightestTierGapMultiplier(GameContent content) {
        double tightest = 1.0;
        for (TierDef tier : content.tiers()) {
            double product = 1.0;
            double percent = 0;
            double flat = 0;
            for (StatModifierDef effect : tier.effects()) {
                if (effect.stat() != StatId.GAP_SIZE) {
                    continue;
                }
                switch (effect.op()) {
                    case MULTIPLY:
                        product *= effect.value();
                        break;
                    case PERCENT_ADD:
                        percent += effect.value();
                        break;
                    case FLAT_ADD:
                    default:
                        flat += effect.value();
                        break;
                }
            }
            double factor = (1 + flat / StatId.GAP_SIZE.defaultValue()) * (1 + percent) * product;
            tightest = Math.min(tightest, factor);
        }
        return tightest;
    }

    /**
     * A pattern with weight 0 that no boss and no challenge names is content nothing streams —
     * a balance note rather than a broken reference, because a boss authored later may pick it
     * up.
     *
     * @param content the content to check
     * @param warnings where to append
     */
    private static void warnUnreferencedPatterns(GameContent content, List<String> warnings) {
        if (!content.has(GameContent.PATTERNS)) {
            return;
        }
        Set<String> referenced = new HashSet<>();
        for (WorldDef world : content.worlds()) {
            referenced.addAll(world.patterns());
            if (world.boss() != null) {
                referenced.addAll(world.boss().patterns());
            }
        }
        for (ChallengeDef challenge : content.challenges()) {
            if (challenge.forcedPattern() != null) {
                referenced.add(challenge.forcedPattern());
            }
            if (challenge.boss() != null) {
                referenced.addAll(challenge.boss().patterns());
            }
        }
        List<PatternDef> defs = content.patterns().all();
        for (int i = 0; i < defs.size(); i++) {
            PatternDef def = defs.get(i);
            if (def.weight() == 0 && !referenced.contains(def.id())) {
                warnings.add(PATTERNS_FILE + "#/patterns/" + i + ": pattern '" + def.id()
                        + "' has weight 0 and no boss or challenge names it, so nothing ever"
                        + " streams it");
            }
        }
    }

    // -------------------------------------------------------------- modifiers

    /**
     * The run modifiers and their set bonuses (D27, §4, M6).
     *
     * <p>Four rules are worth naming, because each of them describes content that would look fine
     * and do nothing:
     * <ul>
     *   <li>a rarity with no draw weight makes every card of that rarity unreachable;</li>
     *   <li>a {@code streakBonus} is coins, so a card that carries one must also refuse
     *       {@code NO_COINS} — otherwise a challenge that turns coins off is offering a card that
     *       pays nothing;</li>
     *   <li>{@code SPEED_RAMP} and {@code ALL_OBSTACLES_MOVE} are read by the difficulty layer
     *       when the run starts, so a card that turns one on mid-run would be a flag nothing
     *       looks at again;</li>
     *   <li>a synergy needs at least two required tags, because E16 asks for two distinct
     *       contributors and one tag can never be split across two entries.</li>
     * </ul>
     *
     * @param content the content to check
     * @param errors where to append problems
     */
    private static void validateModifiers(GameContent content, List<String> errors) {
        if (!content.has(GameContent.MODIFIERS)) {
            return;
        }
        ModifiersDef block = content.modifierBlock();
        if (block.offerSchedule().isEmpty()) {
            errors.add(MODIFIERS_FILE + "#/offerSchedule: no gate opens a draft");
        }
        int previous = 0;
        for (int i = 0; i < block.offerSchedule().size(); i++) {
            int gate = block.offerSchedule().get(i);
            if (gate <= previous) {
                errors.add(MODIFIERS_FILE + "#/offerSchedule/" + i + ": the schedule must be "
                        + "strictly ascending and positive (" + previous + " -> " + gate + ")");
            }
            previous = gate;
        }
        if (block.choicesPerOffer() < 1) {
            errors.add(MODIFIERS_FILE + "#/choicesPerOffer: a draft shows at least one card, not "
                    + block.choicesPerOffer());
        }
        for (Map.Entry<Rarity, Integer> weight : block.rarityWeights().entrySet()) {
            if (weight.getValue() < 0) {
                errors.add(MODIFIERS_FILE + "#/rarityWeights/" + weight.getKey()
                        + ": a draw weight cannot be negative");
            }
        }
        validateModifierList(content, block, errors);
        validateSynergyList(content, block, errors);
    }

    private static void validateModifierList(GameContent content, ModifiersDef block,
            List<String> errors) {
        Set<String> seen = new HashSet<>();
        List<ModifierDef> defs = block.modifiers();
        for (int i = 0; i < defs.size(); i++) {
            ModifierDef def = defs.get(i);
            String at = MODIFIERS_FILE + "#/modifiers/" + i;
            checkId(errors, at + "/id", "modifier", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate modifier id '" + def.id() + "'");
            }
            Integer weight = block.rarityWeights().get(def.rarity());
            if (weight == null || weight <= 0) {
                errors.add(at + "/rarity: rarity " + def.rarity() + " has no draw weight in "
                        + "rarityWeights, so '" + def.id() + "' can never be offered");
            }
            if (def.maxStacks() < 1) {
                errors.add(at + "/maxStacks: a card must be takeable at least once, not "
                        + def.maxStacks() + " times");
            }
            if (def.tags().isEmpty()) {
                errors.add(at + "/tags: a modifier with no tag can never feed a synergy");
            }
            for (int e = 0; e < def.excludes().size(); e++) {
                String other = def.excludes().get(e);
                if (def.id().equals(other)) {
                    errors.add(at + "/excludes/" + e + ": '" + def.id() + "' excludes itself; use "
                            + "maxStacks to say a card cannot be taken twice");
                } else if (!content.modifiers().contains(other)) {
                    errors.add(at + "/excludes/" + e + ": unknown modifier '" + other + "'");
                }
            }
            if (def.effects().isEmpty() && def.flags().isEmpty() && def.streakBonusCoins() <= 0) {
                errors.add(at + ": '" + def.id() + "' has no effect, no flag and no streak bonus");
            }
            if (def.streakBonus() != null) {
                if (def.streakBonusCoins() <= 0) {
                    errors.add(at + "/streakBonus/coins: a streak bonus pays a positive number of "
                            + "coins, not " + def.streakBonusCoins());
                }
                if (!def.forbids(RuleFlag.NO_COINS)) {
                    errors.add(at + "/requiresFlagsAbsent: '" + def.id() + "' pays coins per "
                            + "streak step, so it must list NO_COINS (E12)");
                }
            }
            checkMidRunFlags(errors, at + "/flags", "modifier '" + def.id() + "'", def.flags());
            checkSpawnCriticalStats(errors, at + "/effects", "modifier '" + def.id() + "'",
                    def.effects());
            checkCondition(content, errors, at + "/unlock", def.unlock(), false);
        }
    }

    private static void validateSynergyList(GameContent content, ModifiersDef block,
            List<String> errors) {
        Set<String> seen = new HashSet<>();
        List<SynergyDef> defs = block.synergies();
        for (int i = 0; i < defs.size(); i++) {
            SynergyDef def = defs.get(i);
            String at = MODIFIERS_FILE + "#/synergies/" + i;
            checkId(errors, at + "/id", "synergy", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate synergy id '" + def.id() + "'");
            }
            if (content.modifiers().contains(def.id())) {
                errors.add(at + "/id: '" + def.id() + "' is also a modifier id; the two share the "
                        + "string tables and would collide");
            }
            if (def.requiresTags().size() < SynergyResolver.MIN_DISTINCT_ENTRIES) {
                errors.add(at + "/requiresTags: a set bonus needs at least "
                        + SynergyResolver.MIN_DISTINCT_ENTRIES + " tags, because it activates only "
                        + "when two distinct modifiers contribute to it (E16)");
            }
            if (def.effects().isEmpty() && def.flags().isEmpty()) {
                errors.add(at + ": synergy '" + def.id() + "' has no effect and no flag");
            }
            checkMidRunFlags(errors, at + "/flags", "synergy '" + def.id() + "'", def.flags());
            checkSpawnCriticalStats(errors, at + "/effects", "synergy '" + def.id() + "'",
                    def.effects());
        }
    }

    /**
     * Stats a card or a synergy may not touch, because the spawner reads them when it decides what
     * to spawn (E32.d).
     *
     * <p>{@code SpawnTable.roll} reads {@code MOVING_CHANCE} to decide both the moving flag and the
     * layout, and how many draws come out of the {@code obstacle} stream follows from that. A
     * drafted change to it would therefore make the obstacle sequence a function of what the
     * player picked, which is exactly the invariant E32.d asks for: the sequence of spawn decisions
     * is the same however the drafts are answered. {@code checkMidRunFlags} above keeps
     * {@code ALL_OBSTACLES_MOVE} out for the same reason, one level up; this is the stat-level
     * half, and it is a list rather than a single case so that a stat a later spawn table starts
     * reading is one line to close.
     *
     * @param errors where to append problems
     * @param at the pointer of the effect list
     * @param what the thing being validated, for the message
     * @param effects the effects it pushes
     */
    private static void checkSpawnCriticalStats(List<String> errors, String at, String what,
            List<StatModifierDef> effects) {
        for (int i = 0; i < effects.size(); i++) {
            StatId stat = effects.get(i).stat();
            if (stat == StatId.MOVING_CHANCE) {
                errors.add(at + "/" + i + ": " + what + " may not change " + stat
                        + "; the spawn decision reads it, so the obstacle sequence would depend on"
                        + " what the player drafted (E32.d)");
            }
        }
    }

    /**
     * Rule flags a card or a synergy may not turn on mid-run: the difficulty layer reads them when
     * the run starts and never again, so granting one later is a flag nothing acts on.
     *
     * @param errors where to append problems
     * @param at the pointer of the flag list
     * @param what the thing being validated, for the message
     * @param flags the flags it grants
     */
    private static void checkMidRunFlags(List<String> errors, String at, String what,
            List<RuleFlag> flags) {
        for (int i = 0; i < flags.size(); i++) {
            RuleFlag flag = flags.get(i);
            if (flag == RuleFlag.SPEED_RAMP || flag == RuleFlag.ALL_OBSTACLES_MOVE) {
                errors.add(at + "/" + i + ": " + what + " may not grant " + flag + " mid-run; the "
                        + "difficulty layer resolves it at run start (D8)");
            }
        }
    }

    /**
     * A synergy no build can ever complete (D27, E16): warned rather than rejected, because it is
     * a balance problem and not a broken reference, and because a modifier added later can make
     * it reachable again.
     *
     * @param content the content to check
     * @param warnings where to append
     */
    private static void warnUnreachableSynergies(GameContent content, List<String> warnings) {
        if (!content.has(GameContent.MODIFIERS)) {
            return;
        }
        List<ModifierDef> cards = content.modifiers().all();
        List<SynergyDef> defs = content.synergies().all();
        for (int i = 0; i < defs.size(); i++) {
            SynergyDef def = defs.get(i);
            if (!satisfiable(def, cards)) {
                warnings.add(MODIFIERS_FILE + "#/synergies/" + i + "/requiresTags: no two distinct "
                        + "shipped modifiers can ever satisfy " + def.requiresTags()
                        + ", so '" + def.id() + "' can never activate");
            }
        }
    }

    /**
     * Whether some set of mutually compatible modifiers satisfies a synergy. The search is over
     * subsets of at most {@code requiresTags.size()} cards — more contributors than required tags
     * can never help — and it honours {@code excludes}, so a pair that could never be held
     * together does not count as a way of reaching the bonus.
     *
     * @param def the synergy
     * @param cards every shipped modifier
     * @return {@code true} when a legal build activates it
     */
    private static boolean satisfiable(SynergyDef def, List<ModifierDef> cards) {
        return search(def, cards, 0, new ArrayList<>(), def.requiresTags().size());
    }

    private static boolean search(SynergyDef def, List<ModifierDef> cards, int from,
            List<ModifierDef> picked, int limit) {
        if (picked.size() >= SynergyResolver.MIN_DISTINCT_ENTRIES
                && SynergyResolver.matches(def, picked)) {
            return true;
        }
        if (picked.size() >= limit) {
            return false;
        }
        for (int i = from; i < cards.size(); i++) {
            ModifierDef candidate = cards.get(i);
            if (conflicts(candidate, picked)) {
                continue;
            }
            picked.add(candidate);
            boolean found = search(def, cards, i + 1, picked, limit);
            picked.remove(picked.size() - 1);
            if (found) {
                return true;
            }
        }
        return false;
    }

    private static boolean conflicts(ModifierDef candidate, List<ModifierDef> picked) {
        for (ModifierDef held : picked) {
            if (held.excludes().contains(candidate.id())
                    || candidate.excludes().contains(held.id())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- challenges

    private static void validateChallenges(GameContent content, List<String> errors) {
        Set<String> seen = new HashSet<>();
        List<ChallengeDef> defs = content.challenges().all();
        for (int i = 0; i < defs.size(); i++) {
            ChallengeDef def = defs.get(i);
            String at = CHALLENGES_FILE + "#/challenges/" + i;
            checkId(errors, at + "/id", "challenge", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate challenge id '" + def.id() + "'");
            }
            if (content.has(GameContent.WORLDS) && !content.worlds().contains(def.world())) {
                errors.add(at + "/world: unknown world '" + def.world() + "'");
            }
            if (!content.tiers().contains(def.tier())) {
                errors.add(at + "/tier: unknown tier '" + def.tier() + "'");
            }
            if (!content.curves().contains(def.curve())) {
                errors.add(at + "/curve: unknown curve '" + def.curve() + "'");
            }
            checkCondition(content, errors, at + "/unlock", def.unlock(), false);
            checkUnlocks(content, errors, at + "/rewards/unlocks",
                    def.rewardsOrNone().unlocks());
            checkContradictions(errors, at, "challenge '" + def.id() + "'", def.flags(),
                    def.effects(), def.objective().type());
            // TODO(M8): a BOSS_CLEARED objective must carry a boss block once boss encounters
            // exist; boss_corridor_1 is authored with its final rewards and gets its boss then.
            checkBoss(content, errors, at + "/boss", def.boss(), false);
            if (def.forcedPattern() != null) {
                checkPattern(content, errors, at + "/forcedPattern", def.forcedPattern(), false);
                checkForcedPatternScores(content, errors, at, def);
            }
            checkForcedModifiers(content, errors, at, def);
        }
    }

    /**
     * E14: a challenge that forces a pattern and carries a boss reaches {@code boss.atGate} only
     * if the forced pattern scores — {@code scoringSteps} true and at least one step scoring —
     * because a looped pattern that never advances {@code gatesPassed} makes the boss
     * unreachable.
     *
     * @param content the content to check
     * @param errors where to append problems
     * @param at the pointer of the challenge
     * @param def the challenge
     */
    private static void checkForcedPatternScores(GameContent content, List<String> errors,
            String at, ChallengeDef def) {
        if (def.boss() == null || !content.has(GameContent.PATTERNS)
                || !content.patterns().contains(def.forcedPattern())) {
            return;
        }
        PatternDef pattern = content.patterns().get(def.forcedPattern());
        boolean anyStepScores = false;
        for (PatternStepDef step : pattern.steps()) {
            anyStepScores |= step.scores();
        }
        if (!pattern.stepsScore() || !anyStepScores) {
            errors.add(at + "/forcedPattern: '" + def.forcedPattern() + "' never scores, so"
                    + " boss.atGate " + def.boss().atGate() + " is unreachable (E14: a forced"
                    + " pattern must have scoringSteps true and a scoring step)");
        }
    }

    /**
     * A challenge's {@code forcedModifiers} (E19: the check switches on now that
     * {@code modifiers.json} ships).
     *
     * <p>{@code ModifierDirector.start} takes them one at a time under the authored rules, so a
     * list that breaks one of them silently loses a card at run start — and the challenge would
     * then be a different challenge from the one that was authored. Four ways to break it: an id
     * that resolves to nothing, more copies of a card than its {@code maxStacks}, two cards that
     * exclude each other, and a card whose {@code requiresFlagsAbsent} names a flag the challenge
     * itself turns on.
     *
     * @param content the content to check
     * @param errors where to append problems
     * @param at the pointer of the challenge
     * @param def the challenge
     */
    private static void checkForcedModifiers(GameContent content, List<String> errors, String at,
            ChallengeDef def) {
        if (!content.has(GameContent.MODIFIERS)) {
            return;
        }
        List<String> forced = def.forcedModifiers();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < forced.size(); i++) {
            String id = forced.get(i);
            String where = at + "/forcedModifiers/" + i;
            if (!content.modifiers().contains(id)) {
                errors.add(where + ": unknown modifier '" + id + "'");
                continue;
            }
            ModifierDef card = content.modifiers().get(id);
            int count = counts.merge(id, 1, Integer::sum);
            if (count > Math.max(1, card.maxStacks())) {
                errors.add(where + ": '" + id + "' is forced " + count + " times but its maxStacks"
                        + " is " + card.maxStacks() + ", so the extra copies are dropped");
            }
            for (RuleFlag flag : card.requiresFlagsAbsent()) {
                if (def.flags().contains(flag)) {
                    errors.add(where + ": '" + id + "' requires " + flag + " to be absent, and the"
                            + " challenge turns it on");
                }
            }
            for (int j = 0; j < i; j++) {
                String other = forced.get(j);
                if (!content.modifiers().contains(other) || other.equals(id)) {
                    continue;
                }
                ModifierDef held = content.modifiers().get(other);
                if (held.excludes().contains(id) || card.excludes().contains(other)) {
                    errors.add(where + ": '" + id + "' and '" + other + "' exclude each other");
                }
            }
        }
    }

    // ----------------------------------------------------------- achievements

    private static void validateAchievements(GameContent content, List<String> errors) {
        Set<String> seen = new HashSet<>();
        List<AchievementDef> defs = content.achievements().all();
        for (int i = 0; i < defs.size(); i++) {
            AchievementDef def = defs.get(i);
            String at = ACHIEVEMENTS_FILE + "#/achievements/" + i;
            checkId(errors, at + "/id", "achievement", def.id());
            if (!seen.add(def.id())) {
                errors.add(at + "/id: duplicate achievement id '" + def.id() + "'");
            }
            checkCounter(errors, at + "/condition/counter", def.condition());
            checkUnlocks(content, errors, at + "/reward/unlocks", def.rewardOrNone().unlocks());
        }
    }

    /**
     * An achievement counter must resolve (D10, E5): a {@code StatisticKey} field or map entry, a
     * profile-root scalar, one of the documented run values, or a collection percentage.
     *
     * @param errors where to append problems
     * @param at the pointer of the counter
     * @param condition the condition carrying it
     */
    private static void checkCounter(List<String> errors, String at,
            AchievementConditionDef condition) {
        String counter = condition.counter();
        switch (condition.scope()) {
            case LIFETIME:
                if (StatisticKey.of(counter) == null) {
                    errors.add(at + ": unknown LIFETIME counter '" + counter
                            + "' (expected a StatisticKey field, <mapField>.<key>, or a"
                            + " profile-root scalar)");
                }
                break;
            case RUN:
                if (!AchievementConditionDef.RUN_COUNTERS.contains(counter)) {
                    errors.add(at + ": unknown RUN counter '" + counter + "' (expected one of "
                            + AchievementConditionDef.RUN_COUNTERS + ")");
                }
                break;
            case COLLECTION:
            default: {
                String category = condition.collectionCategory();
                if (category == null) {
                    errors.add(at + ": a COLLECTION counter must read '"
                            + AchievementConditionDef.COLLECTION_PREFIX + "<category>"
                            + AchievementConditionDef.COLLECTION_SUFFIX + "', not '" + counter
                            + "'");
                } else if (!AchievementConditionDef.COLLECTION_CATEGORIES.contains(category)) {
                    errors.add(at + ": unknown collection category '" + category
                            + "' (expected one of "
                            + AchievementConditionDef.COLLECTION_CATEGORIES + ")");
                }
                break;
            }
        }
    }

    // ---------------------------------------------------------------- aliases

    private static void validateAliases(GameContent content, List<String> errors) {
        if (!content.has(GameContent.ALIASES)) {
            return;
        }
        for (Map.Entry<String, String> entry : content.aliases().unlocked().entrySet()) {
            checkUnlockable(content, errors,
                    ALIASES_FILE + "#/unlocked/" + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : content.aliases().upgrades().entrySet()) {
            if (content.has(GameContent.UPGRADES)
                    && !content.upgrades().contains(entry.getValue())) {
                errors.add(ALIASES_FILE + "#/upgrades/" + entry.getKey()
                        + ": renames to unknown upgrade node '" + entry.getValue() + "'");
            }
        }
        for (Map.Entry<String, String> entry : content.aliases().abilityLevels().entrySet()) {
            if (content.has(GameContent.ABILITIES)
                    && !content.abilities().contains(entry.getValue())) {
                errors.add(ALIASES_FILE + "#/abilityLevels/" + entry.getKey()
                        + ": renames to unknown ability '" + entry.getValue() + "'");
            }
        }
        for (String removed : content.aliases().removedUpgrades()) {
            if (content.has(GameContent.UPGRADES) && content.upgrades().contains(removed)) {
                errors.add(ALIASES_FILE + "#/removedUpgrades: '" + removed
                        + "' is still a node in " + UPGRADES_FILE);
            }
        }
        for (String node : content.aliases().refunds().keySet()) {
            if (!content.aliases().removedUpgrades().contains(node)) {
                errors.add(ALIASES_FILE + "#/refunds/" + node
                        + ": a refund is only paid for a removed node");
            }
        }
    }

    // ------------------------------------------------------------------- caps

    /**
     * The E3 caps: the ability level cap a player can reach must not exceed the number of levels
     * the abilities ship, and the passive slots a bird can reach must not exceed
     * {@link #MAX_PASSIVE_SLOTS}.
     *
     * @param content the content to check
     * @param errors where to append problems
     */
    private static void validateCaps(GameContent content, List<String> errors) {
        long abilityCapGrants = 0;
        long passiveSlotGrants = 0;
        for (UpgradeDef node : content.upgrades()) {
            for (GrantDef grant : node.grants()) {
                if (grant.type() == GrantType.ABILITY_CAP) {
                    abilityCapGrants += grant.amount();
                } else if (grant.type() == GrantType.PASSIVE_SLOT) {
                    passiveSlotGrants += grant.amount();
                }
            }
        }
        long cap = BASE_ABILITY_LEVEL_CAP + abilityCapGrants;
        if (content.has(GameContent.ABILITIES) && !content.abilities().all().isEmpty()) {
            int minLevels = Integer.MAX_VALUE;
            String thinnest = null;
            for (AbilityDef def : content.abilities()) {
                if (def.levels().size() < minLevels) {
                    minLevels = def.levels().size();
                    thinnest = def.id();
                }
            }
            if (cap > minLevels) {
                errors.add(UPGRADES_FILE + "#/nodes: the ability level cap reaches "
                        + cap + " (base " + BASE_ABILITY_LEVEL_CAP + " + " + abilityCapGrants
                        + " from ability_cap grants) but ability '" + thinnest + "' has only "
                        + minLevels + " levels (E3)");
            }
        }
        int maxSlots = 0;
        String widest = null;
        for (BirdDef bird : content.birds()) {
            if (bird.passiveSlots() > maxSlots) {
                maxSlots = bird.passiveSlots();
                widest = bird.id();
            }
        }
        if (maxSlots + passiveSlotGrants > MAX_PASSIVE_SLOTS) {
            errors.add(UPGRADES_FILE + "#/nodes: passive slots reach " + (maxSlots
                    + passiveSlotGrants) + " on bird '" + widest + "' (" + maxSlots + " innate + "
                    + passiveSlotGrants + " from passive_slot grants), above the maximum "
                    + MAX_PASSIVE_SLOTS + " (E3)");
        }
    }

    // ------------------------------------------------------- shared reference

    /**
     * Checks one unlock condition and everything under it (D13, E20).
     *
     * @param content the content to check against
     * @param errors where to append problems
     * @param at the pointer of the condition
     * @param condition the condition
     * @param cosmetic whether the owner is a cosmetic, which is the only place
     *     {@code prestige} and {@code counter} are allowed (E20)
     */
    private static void checkCondition(GameContent content, List<String> errors, String at,
            UnlockConditionDef condition, boolean cosmetic) {
        checkCondition(content, errors, at, condition, cosmetic, true);
    }

    /**
     * Checks one unlock condition and everything under it (D13, E20).
     *
     * @param content the content to check against
     * @param errors where to append problems
     * @param at the pointer of the condition
     * @param condition the condition
     * @param cosmetic whether the owner is a cosmetic, which is the only place
     *     {@code prestige} and {@code counter} are allowed (E20)
     * @param sellable whether a {@code purchase} at this position is a shop price: true at the
     *     root and under an {@code any_of}, false anywhere under an {@code all_of}
     */
    private static void checkCondition(GameContent content, List<String> errors, String at,
            UnlockConditionDef condition, boolean cosmetic, boolean sellable) {
        if (condition == null) {
            errors.add(at + ": missing unlock condition");
            return;
        }
        switch (condition.type()) {
            case DEFAULT:
                break;
            case BEST_GATES:
            case BEST_POINTS:
            case TOTAL_GATES:
            case RUNS:
            case LEVEL:
            case COINS_EARNED_TOTAL:
                if (condition.value() <= 0) {
                    errors.add(at + "/value: " + condition.type() + " needs a positive value");
                }
                break;
            case PURCHASE:
                if (condition.amount() <= 0) {
                    errors.add(at + "/amount: purchase needs a positive amount");
                }
                if (!sellable) {
                    // The shop reads a purchase branch as "this is what it costs" and sells the
                    // unlockable for it. Under an all_of the coins are one requirement among
                    // several, so selling it would hand over something its siblings still gate.
                    errors.add(at + "/type: 'purchase' may only be the whole condition or a"
                            + " branch of an 'any_of', never a member of an 'all_of' (D13)");
                }
                break;
            case CHALLENGE:
                checkReference(content, errors, at + "/id", condition.id(), "challenge",
                        GameContent.CHALLENGES, content.challenges()::contains);
                break;
            case ACHIEVEMENT:
                checkReference(content, errors, at + "/id", condition.id(), "achievement",
                        GameContent.ACHIEVEMENTS, content.achievements()::contains);
                break;
            case WORLD_CLEARED:
                checkReference(content, errors, at + "/id", condition.id(), "world",
                        GameContent.WORLDS, content.worlds()::contains);
                break;
            case PRESTIGE:
                if (!cosmetic) {
                    errors.add(at + "/type: 'prestige' is allowed only on a cosmetic (E20)");
                }
                if (condition.value() < 1) {
                    errors.add(at + "/value: prestige needs a value of at least 1");
                }
                break;
            case COUNTER:
                if (!cosmetic) {
                    errors.add(at + "/type: 'counter' is allowed only on a cosmetic (E20)");
                }
                checkCounter(errors, at + "/counter", new AchievementConditionDef(
                        condition.counter() == null ? "" : condition.counter(),
                        scopeOf(condition.counter()),
                        io.github.michelbr84.flapforge.content.defs.CompareOp.GTE,
                        condition.value()));
                break;
            case ALL_OF:
            case ANY_OF:
            default:
                if (condition.type() == UnlockType.ALL_OF && condition.conditions().isEmpty()) {
                    // An empty all_of is vacuously true, which silently means "default"; an empty
                    // any_of is never true, and the unlock graph says so far more usefully than a
                    // shape rule would ("cannot be reached from the default set").
                    errors.add(at + "/conditions: all_of has no condition");
                }
                boolean childrenSellable = sellable && condition.type() == UnlockType.ANY_OF;
                for (int i = 0; i < condition.conditions().size(); i++) {
                    checkCondition(content, errors, at + "/conditions/" + i,
                            condition.conditions().get(i), cosmetic, childrenSellable);
                }
                break;
        }
    }

    private static io.github.michelbr84.flapforge.content.defs.CounterScope scopeOf(
            String counter) {
        if (counter != null && counter.startsWith(AchievementConditionDef.RUN_PREFIX)) {
            return io.github.michelbr84.flapforge.content.defs.CounterScope.RUN;
        }
        if (counter != null && counter.startsWith(AchievementConditionDef.COLLECTION_PREFIX)) {
            return io.github.michelbr84.flapforge.content.defs.CounterScope.COLLECTION;
        }
        return io.github.michelbr84.flapforge.content.defs.CounterScope.LIFETIME;
    }

    private static void checkReference(GameContent content, List<String> errors, String at,
            String id, String kind, String file, java.util.function.Predicate<String> known) {
        if (id == null || id.isBlank()) {
            errors.add(at + ": missing " + kind + " id");
            return;
        }
        if (content.has(file) && !known.test(id)) {
            errors.add(at + ": unknown " + kind + " '" + id + "'");
        }
    }

    private static void checkUnlocks(GameContent content, List<String> errors, String at,
            List<String> unlocks) {
        for (int i = 0; i < unlocks.size(); i++) {
            checkUnlockable(content, errors, at + "/" + i, unlocks.get(i));
        }
    }

    /**
     * Resolves one namespaced unlockable id (D13). An id whose kind lives in a file that was not
     * supplied is left alone (E19).
     *
     * @param content the content to check against
     * @param errors where to append problems
     * @param at the pointer of the id
     * @param id the namespaced id
     */
    private static void checkUnlockable(GameContent content, List<String> errors, String at,
            String id) {
        if (id == null || id.isBlank()) {
            errors.add(at + ": empty unlockable id");
            return;
        }
        ContentKind kind = ContentKind.ofUnlockable(id);
        if (kind == null) {
            errors.add(at + ": '" + id + "' is not a namespaced unlockable id (expected one of "
                    + namespaces() + ")");
            return;
        }
        String rest = id.substring(kind.namespace().length());
        switch (kind) {
            case BIRD:
                if (!content.birds().contains(rest)) {
                    errors.add(at + ": unknown bird '" + rest + "' in '" + id + "'");
                }
                break;
            case COSMETIC: {
                int colon = rest.indexOf(':');
                if (colon <= 0 || colon + 1 >= rest.length()) {
                    errors.add(at + ": '" + id + "' must read cosmetic:<bird>:<palette>");
                    break;
                }
                String birdId = rest.substring(0, colon);
                String paletteId = rest.substring(colon + 1);
                if (!content.birds().contains(birdId)) {
                    errors.add(at + ": unknown bird '" + birdId + "' in '" + id + "'");
                } else if (content.birds().get(birdId).palette(paletteId) == null) {
                    errors.add(at + ": bird '" + birdId + "' has no palette '" + paletteId + "'");
                }
                break;
            }
            case ABILITY:
                if (content.has(GameContent.ABILITIES) && !content.abilities().contains(rest)) {
                    errors.add(at + ": unknown ability '" + rest + "' in '" + id + "'");
                }
                break;
            case TREE:
                if (content.has(GameContent.UPGRADES) && !content.trees().contains(rest)) {
                    errors.add(at + ": unknown tree '" + rest + "' in '" + id + "'");
                }
                break;
            case TIER:
                if (!content.tiers().contains(rest)) {
                    errors.add(at + ": unknown tier '" + rest + "' in '" + id + "'");
                }
                break;
            case WORLD:
                if (content.has(GameContent.WORLDS) && !content.worlds().contains(rest)) {
                    errors.add(at + ": unknown world '" + rest + "' in '" + id + "'");
                }
                break;
            case CHALLENGE:
                if (content.has(GameContent.CHALLENGES) && !content.challenges().contains(rest)) {
                    errors.add(at + ": unknown challenge '" + rest + "' in '" + id + "'");
                }
                break;
            case ACHIEVEMENT:
                if (content.has(GameContent.ACHIEVEMENTS)
                        && !content.achievements().contains(rest)) {
                    errors.add(at + ": unknown achievement '" + rest + "' in '" + id + "'");
                }
                break;
            case FEATURE:
                if (!content.features().contains(rest)) {
                    errors.add(at + ": unknown feature '" + rest + "' in '" + id + "'");
                }
                break;
            case MODIFIER:
                if (content.has(GameContent.MODIFIERS) && !content.modifiers().contains(rest)) {
                    errors.add(at + ": unknown modifier '" + rest + "' in '" + id + "'");
                }
                break;
            default:
                break;
        }
    }

    private static List<String> namespaces() {
        List<String> out = new ArrayList<>();
        for (ContentKind kind : ContentKind.values()) {
            if (kind.isUnlockable()) {
                out.add(kind.namespace());
            }
        }
        return out;
    }

    /**
     * The contradiction rules (D10): a rule flag that zeroes a stat must not be combined with
     * content that only exists to raise it.
     *
     * @param errors where to append problems
     * @param at the pointer of the owner
     * @param owner how to name the owner in the message
     * @param flags the owner's rule flags
     * @param effects the owner's stat modifiers
     * @param objective the challenge objective, or {@code null}
     */
    private static void checkContradictions(List<String> errors, String at, String owner,
            List<RuleFlag> flags, List<StatModifierDef> effects, ObjectiveType objective) {
        if (flags.contains(RuleFlag.NO_COINS) && objective == ObjectiveType.COLLECT_COINS) {
            errors.add(at + ": " + owner + " has flag NO_COINS and objective COLLECT_COINS, which"
                    + " can never be met");
        }
        for (int i = 0; i < effects.size(); i++) {
            StatModifierDef effect = effects.get(i);
            if (flags.contains(RuleFlag.NO_DEFENSIVE_ABILITIES)
                    && effect.stat() == StatId.SHIELD_CHARGES) {
                errors.add(at + "/effects/" + i + ": " + owner + " has flag"
                        + " NO_DEFENSIVE_ABILITIES, which zeroes SHIELD_CHARGES");
            }
            if (flags.contains(RuleFlag.NO_REVIVE) && effect.stat() == StatId.REVIVES) {
                errors.add(at + "/effects/" + i + ": " + owner + " has flag NO_REVIVE, which"
                        + " zeroes REVIVES");
            }
            if (flags.contains(RuleFlag.NO_COINS) && (effect.stat() == StatId.COIN_MULT
                    || effect.stat() == StatId.COIN_SPAWN_RATE)) {
                errors.add(at + "/effects/" + i + ": " + owner + " has flag NO_COINS, which makes "
                        + effect.stat() + " a no-op");
            }
        }
    }

    /**
     * Every unlock condition in the content, flattened (used by the E1 warning).
     *
     * @param content the content
     * @return the conditions, in content order
     */
    private static List<UnlockConditionDef> everyCondition(GameContent content) {
        List<UnlockConditionDef> out = new ArrayList<>();
        for (BirdDef bird : content.birds()) {
            flatten(bird.unlock(), out);
            for (PaletteDef palette : bird.palettes()) {
                flatten(palette.unlock(), out);
            }
        }
        for (TierDef tier : content.tiers()) {
            flatten(tier.unlock(), out);
        }
        for (TreeDef tree : content.trees()) {
            flatten(tree.unlock(), out);
        }
        for (AbilityDef ability : content.abilities()) {
            flatten(ability.unlock(), out);
        }
        for (WorldDef world : content.worlds()) {
            flatten(world.unlock(), out);
        }
        for (ChallengeDef challenge : content.challenges()) {
            flatten(challenge.unlock(), out);
        }
        if (content.economy() != null) {
            for (FeatureDef feature : content.economy().features()) {
                flatten(feature.unlock(), out);
            }
        }
        return out;
    }

    private static void flatten(UnlockConditionDef condition, List<UnlockConditionDef> out) {
        if (condition == null) {
            return;
        }
        out.add(condition);
        for (UnlockConditionDef child : condition.conditions()) {
            flatten(child, out);
        }
    }

    // ---------------------------------------------------------- classic table

    /**
     * The classic table (D10, §5): bird {@code classic} + curve {@code classic} + tier
     * {@code normal} must resolve, through the real {@link StatSheet} and {@link DifficultyCurve},
     * to the upstream numbers.
     *
     * @param content the content to check
     * @param errors where to append problems
     */
    private static void validateClassicTable(GameContent content, List<String> errors) {
        String at = DIFFICULTY_FILE + "#/curves/" + CLASSIC_CURVE;
        if (!content.birds().contains(CLASSIC_BIRD)) {
            errors.add(BIRDS_FILE + "#: the classic table needs a bird '" + CLASSIC_BIRD + "'");
            return;
        }
        if (!content.curves().contains(CLASSIC_CURVE)) {
            errors.add(at + ": the classic table needs a curve '" + CLASSIC_CURVE + "'");
            return;
        }
        if (!content.tiers().contains(CLASSIC_TIER)) {
            errors.add(DIFFICULTY_FILE + "#/tiers: the classic table needs a tier '" + CLASSIC_TIER
                    + "'");
            return;
        }
        BirdProfile bird = content.birdProfile(CLASSIC_BIRD);
        CurveSpec curve = content.curveSpec(CLASSIC_CURVE);
        TierSpec tier = content.tierSpec(CLASSIC_TIER);
        DifficultyCurve difficulty = new DifficultyCurve(curve);

        EffectStack stack = new EffectStack();
        RuleSet rules = tier.flags();
        StatSheet sheet = new StatSheet(bird.baseStats(), stack, rules);
        stack.setLayer(Layer.BIRD, bird.effects());
        stack.setLayer(Layer.TIER, tier.effects());
        stack.setLayer(Layer.DIFFICULTY, difficulty.at(0));

        expectClassicPhysics(errors, sheet, 0);
        expect(errors, sheet, StatId.MOVING_CHANCE, 0.05, 0);

        stack.setLayer(Layer.DIFFICULTY, difficulty.at(MOVING_CHANCE_CAP_GATE));
        expect(errors, sheet, StatId.MOVING_CHANCE, 1.0, MOVING_CHANCE_CAP_GATE);
        stack.setLayer(Layer.DIFFICULTY, difficulty.at(25));
        expectClassicPhysics(errors, sheet, 25);
        expect(errors, sheet, StatId.MOVING_CHANCE, 1.0, 25);
    }

    /**
     * The six physics numbers the classic table pins (D10), checked at gate 0 <em>and</em> at
     * gate 25: {@code MOVING_CHANCE} is the only stat the classic curve is allowed to move, and a
     * stray {@code SCROLL_SPEED} or {@code GAP_SIZE} entry added to it — which gate 0 alone
     * cannot see — is exactly the regression this validator exists to catch.
     *
     * @param errors where to append problems
     * @param sheet the resolved sheet at the gate under test
     * @param gates the gate count the difficulty layer is set to
     */
    private static void expectClassicPhysics(List<String> errors, StatSheet sheet, int gates) {
        expect(errors, sheet, StatId.GRAVITY, 1800, gates);
        expect(errors, sheet, StatId.FLAP_VELOCITY, 405, gates);
        expect(errors, sheet, StatId.MAX_FALL_SPEED, 1500, gates);
        expect(errors, sheet, StatId.SCROLL_SPEED, 120, gates);
        expect(errors, sheet, StatId.GAP_SIZE, 128, gates);
        expect(errors, sheet, StatId.GATE_INTERVAL, 160, gates);
    }

    private static void expect(List<String> errors, StatSheet sheet, StatId stat, double expected,
            int gates) {
        double actual = sheet.resolve(stat);
        if (Math.abs(actual - expected) > EPSILON) {
            errors.add(DIFFICULTY_FILE + "#/curves/" + CLASSIC_CURVE + ": classic table broken — "
                    + stat + " at gate " + gates + " resolves to " + actual + ", expected "
                    + expected);
        }
    }

    // ---------------------------------------------------------------- strings

    /**
     * Checks the shipped string files against the code and the content (D25, E31.h): every
     * {@link StringKey} and every {@code <kind>.<id>.name/.desc} of a kind that already exists
     * must be in {@code en.json} (an error), and every key {@code en.json} has should also be in
     * each translation (a warning — the missing key falls back to English at runtime).
     *
     * @param content the content to check
     * @return the errors and the warnings
     * @throws ContentException when a string file is missing or malformed
     */
    public static StringReport checkStrings(GameContent content) {
        Map<String, Map<String, String>> translations = new LinkedHashMap<>();
        for (String language : Strings.LANGUAGES) {
            if (!Strings.SOURCE_LANGUAGE.equals(language)) {
                translations.put(language, Strings.tableOf(language));
            }
        }
        return checkStrings(content, Strings.tableOf(Strings.SOURCE_LANGUAGE), translations);
    }

    /**
     * The same checks against tables the caller supplies (tests and tools).
     *
     * @param content the content to check
     * @param source the {@code en.json} table
     * @param translations the other languages, keyed by language
     * @return the errors and the warnings
     */
    public static StringReport checkStrings(GameContent content, Map<String, String> source,
            Map<String, Map<String, String>> translations) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(source, "source");
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String sourceFile = Strings.fileOf(Strings.SOURCE_LANGUAGE);
        for (StringKey key : StringKey.values()) {
            if (!source.containsKey(key.key())) {
                errors.add(sourceFile + "#/" + key.key() + ": missing string for StringKey."
                        + key.name());
            }
        }
        for (String key : contentKeys(content)) {
            if (!source.containsKey(key)) {
                errors.add(sourceFile + "#/" + key + ": missing content string");
            }
        }
        if (translations != null) {
            for (Map.Entry<String, Map<String, String>> entry : translations.entrySet()) {
                String file = Strings.fileOf(entry.getKey());
                Map<String, String> table = entry.getValue();
                for (String key : source.keySet()) {
                    if (!table.containsKey(key)) {
                        warnings.add(file + "#/" + key
                                + ": missing translation, falls back to English");
                    }
                }
                for (String key : table.keySet()) {
                    if (!source.containsKey(key)) {
                        warnings.add(file + "#/" + key + ": key is not in " + sourceFile);
                    }
                }
            }
        }
        return new StringReport(errors, warnings);
    }

    /**
     * Runs {@link #checkStrings(GameContent)} and raises the errors; warnings are the caller's
     * business.
     *
     * @param content the content to check
     * @return the report, so the caller can still read the warnings
     * @throws ContentException when a required key is missing from {@code en.json}
     */
    public static StringReport validateStrings(GameContent content) {
        StringReport report = checkStrings(content);
        if (!report.ok()) {
            throw new ContentException("String validation failed", report.errors());
        }
        return report;
    }

    /**
     * Every {@code name}/{@code desc} key the content in hand needs (E31.h). A kind whose file
     * was not supplied contributes nothing, which is what lets an M1-shaped fixture pass.
     *
     * @param content the content
     * @return the keys, in content order
     */
    public static Set<String> contentKeys(GameContent content) {
        Set<String> keys = new LinkedHashSet<>();
        for (BirdDef bird : content.birds()) {
            addNameAndDesc(keys, ContentKind.BIRD, bird.id());
            for (PaletteDef palette : bird.palettes()) {
                addNameAndDesc(keys, ContentKind.COSMETIC, bird.id() + "." + palette.id());
            }
        }
        for (AbilityDef ability : content.abilities()) {
            addNameAndDesc(keys, ContentKind.ABILITY, ability.id());
        }
        for (ModifierDef modifier : content.modifiers()) {
            addNameAndDesc(keys, ContentKind.MODIFIER, modifier.id());
        }
        for (SynergyDef synergy : content.synergies()) {
            addNameAndDesc(keys, ContentKind.SYNERGY, synergy.id());
        }
        for (TreeDef tree : content.trees()) {
            addNameAndDesc(keys, ContentKind.TREE, tree.id());
        }
        for (UpgradeDef node : content.upgrades()) {
            addNameAndDesc(keys, ContentKind.UPGRADE, node.id());
        }
        for (TierDef tier : content.tiers()) {
            addNameAndDesc(keys, ContentKind.TIER, tier.id());
        }
        if (content.economy() != null) {
            for (FeatureDef feature : content.economy().features()) {
                addNameAndDesc(keys, ContentKind.FEATURE, feature.id());
            }
        }
        for (WorldDef world : content.worlds()) {
            addNameAndDesc(keys, ContentKind.WORLD, world.id());
        }
        for (ChallengeDef challenge : content.challenges()) {
            addNameAndDesc(keys, ContentKind.CHALLENGE, challenge.id());
        }
        for (AchievementDef achievement : content.achievements()) {
            addNameAndDesc(keys, ContentKind.ACHIEVEMENT, achievement.id());
        }
        return keys;
    }

    private static void addNameAndDesc(Set<String> keys, ContentKind kind, String id) {
        keys.add(Strings.nameKey(kind.key(), id));
        keys.add(Strings.descKey(kind.key(), id));
    }

    private static void checkId(List<String> errors, String at, String kind, String id) {
        if (id == null) {
            errors.add(at + ": missing " + kind + " id");
        } else if (!ID.matcher(id).matches()) {
            errors.add(at + ": " + kind + " id '" + id + "' does not match " + ID.pattern());
        }
    }
}
