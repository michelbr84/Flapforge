package io.github.michelbr84.flapforge.content;

import com.google.gson.JsonElement;
import io.github.michelbr84.flapforge.content.defs.AbilitiesDef;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AchievementDef;
import io.github.michelbr84.flapforge.content.defs.AchievementsDef;
import io.github.michelbr84.flapforge.content.defs.AliasDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.ChallengesDef;
import io.github.michelbr84.flapforge.content.defs.CurveDef;
import io.github.michelbr84.flapforge.content.defs.DifficultyDef;
import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.content.defs.FeatureDef;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.ModifiersDef;
import io.github.michelbr84.flapforge.content.defs.PatternDef;
import io.github.michelbr84.flapforge.content.defs.PatternsDef;
import io.github.michelbr84.flapforge.content.defs.SynergyDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.TierGeneratorDef;
import io.github.michelbr84.flapforge.content.defs.TreeDef;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.content.defs.UpgradesDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.content.defs.WorldsDef;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.PatternSpec;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Every registry the game reads its rules from (D10). M1 holds birds, difficulty curves and
 * tiers, M3 adds the economy, and M4 adds the upgrade trees and nodes, the id aliases and the
 * ability / world / challenge / achievement files — the last four as stubs carrying their final
 * unlock and reward blocks (E19), so the strict validator and the {@link UnlockGraph} can be
 * complete a milestone before the behaviour behind them exists.
 *
 * <p>Build it with {@link #load()} (the files shipped on the classpath) or
 * {@link #fromJson(Map)} (a fixture, a test or a tool). Both paths bind strictly and validate,
 * so a {@code GameContent} instance is content that has already passed every rule that its file
 * set can prove: a cross-reference into a file that was not supplied is not checked, which is how
 * an M1-shaped fixture (birds + difficulty + economy) still validates while the shipped set is
 * checked in full ({@link #has(String)}).
 *
 * <p>{@link #playable(ContentKind)} is the other half of E19: content that is authored and
 * validated but whose systems land later says so, and the UI shows it as locked by milestone
 * rather than pretending it works.
 */
public final class GameContent {

    /** Base name of the bird file. */
    public static final String BIRDS = "birds";
    /** Base name of the difficulty file. */
    public static final String DIFFICULTY = "difficulty";
    /** Base name of the economy file. */
    public static final String ECONOMY = "economy";
    /** Base name of the upgrade file (trees and nodes). */
    public static final String UPGRADES = "upgrades";
    /** Base name of the id alias table (E21). */
    public static final String ALIASES = "aliases";
    /** Base name of the ability file. */
    public static final String ABILITIES = "abilities";
    /** Base name of the run-modifier file (M6). */
    public static final String MODIFIERS = "modifiers";
    /** Base name of the world file. */
    public static final String WORLDS = "worlds";
    /** Base name of the obstacle pattern file (M7). */
    public static final String PATTERNS = "patterns";
    /** Base name of the challenge file. */
    public static final String CHALLENGES = "challenges";
    /** Base name of the achievement file. */
    public static final String ACHIEVEMENTS = "achievements";

    /** The files that must be present for content to bind at all. */
    public static final List<String> REQUIRED_FILES = List.of(BIRDS, DIFFICULTY, ECONOMY);

    /**
     * The milestone each declared feature's system arrives in (E19). A feature that is not listed
     * here works today; one that is listed is authored, validated and buyable, but the UI has to
     * say when it will start doing something rather than presenting a switch that does nothing.
     */
    private static final Map<String, String> FEATURE_MILESTONES;

    static {
        Map<String, String> milestones = new LinkedHashMap<>();
        milestones.put("seeded_runs", "M9");
        FEATURE_MILESTONES = Collections.unmodifiableMap(milestones);
    }

    /**
     * The kinds whose systems already exist; every other kind is authored but not yet playable.
     * Challenges joined in M8 with {@code ObjectiveEvaluator} and {@code BossEncounter}, and
     * achievements with {@code AchievementEvaluator} in the same milestone (E19): from M8 every
     * kind plays, and only {@link #featureMilestone} still stages anything.
     */
    private static final Set<ContentKind> PLAYABLE_KINDS = Collections.unmodifiableSet(EnumSet.of(
            ContentKind.BIRD, ContentKind.COSMETIC, ContentKind.UPGRADE, ContentKind.TREE,
            ContentKind.TIER, ContentKind.FEATURE, ContentKind.WORLD, ContentKind.ABILITY,
            ContentKind.MODIFIER, ContentKind.SYNERGY, ContentKind.CHALLENGE,
            ContentKind.ACHIEVEMENT));

    private final Set<String> files;
    private final Registry<BirdDef> birds;
    private final Registry<CurveDef> curves;
    private final Registry<TierDef> tiers;
    private final EconomyDef economy;
    private final Registry<FeatureDef> features;
    private final Registry<TreeDef> trees;
    private final Registry<UpgradeDef> upgrades;
    private final Registry<AbilityDef> abilities;
    private final Registry<ModifierDef> modifiers;
    private final Registry<SynergyDef> synergies;
    private final ModifiersDef modifierBlock;
    private final Registry<WorldDef> worlds;
    private final Registry<PatternDef> patterns;
    private final Registry<ChallengeDef> challenges;
    private final Registry<AchievementDef> achievements;
    private final AliasDef aliases;
    private final double speedRampPerTick;
    private final TierGeneratorDef tierGenerator;
    private final Map<String, BirdProfile> birdProfiles = new LinkedHashMap<>();
    private final Map<String, CurveSpec> curveSpecs = new LinkedHashMap<>();
    private final Map<String, TierSpec> tierSpecs = new LinkedHashMap<>();
    private final Map<String, PatternSpec> patternSpecs = new LinkedHashMap<>();
    private final Map<String, WorldSpec> worldSpecs = new LinkedHashMap<>();

    private GameContent(Bound bound) {
        this.files = Collections.unmodifiableSet(new LinkedHashSet<>(bound.files));
        this.birds = new Registry<>("bird", bound.birds, BirdDef::id);
        this.curves = new Registry<>("curve", bound.difficulty.curveDefs(), CurveDef::id);
        this.tiers = new Registry<>("tier", bound.difficulty.tiers(), TierDef::id);
        this.economy = bound.economy;
        this.features = new Registry<>("feature",
                bound.economy == null ? List.of() : bound.economy.features(), FeatureDef::id);
        this.trees = new Registry<>("tree", bound.trees, TreeDef::id);
        this.upgrades = new Registry<>("upgrade", bound.upgrades, UpgradeDef::id);
        this.abilities = new Registry<>("ability", bound.abilities, AbilityDef::id);
        this.modifierBlock = bound.modifiers == null ? ModifiersDef.EMPTY : bound.modifiers;
        this.modifiers = new Registry<>("modifier", modifierBlock.modifiers(), ModifierDef::id);
        this.synergies = new Registry<>("synergy", modifierBlock.synergies(), SynergyDef::id);
        this.worlds = new Registry<>("world", bound.worlds, WorldDef::id);
        this.patterns = new Registry<>("pattern", bound.patterns, PatternDef::id);
        this.challenges = new Registry<>("challenge", bound.challenges, ChallengeDef::id);
        this.achievements = new Registry<>("achievement", bound.achievements, AchievementDef::id);
        this.aliases = bound.aliases == null ? AliasDef.EMPTY : bound.aliases;
        this.speedRampPerTick = bound.difficulty.speedRampPerTick();
        this.tierGenerator = bound.difficulty.tierGenerator();
        for (BirdDef def : birds) {
            birdProfiles.putIfAbsent(def.id(), ContentAdapters.toProfile(def));
        }
        for (CurveDef def : curves) {
            curveSpecs.putIfAbsent(def.id(), ContentAdapters.toSpec(def));
        }
        for (TierDef def : tiers) {
            tierSpecs.putIfAbsent(def.id(), ContentAdapters.toSpec(def));
        }
    }

    /** Everything the binder produced, before the registries are built. */
    private static final class Bound {
        final Set<String> files = new LinkedHashSet<>();
        List<BirdDef> birds = List.of();
        DifficultyDef difficulty = new DifficultyDef(Map.of(), 0, List.of(), null);
        EconomyDef economy;
        List<TreeDef> trees = List.of();
        List<UpgradeDef> upgrades = List.of();
        List<AbilityDef> abilities = List.of();
        ModifiersDef modifiers;
        List<WorldDef> worlds = List.of();
        List<PatternDef> patterns = List.of();
        List<ChallengeDef> challenges = List.of();
        List<AchievementDef> achievements = List.of();
        AliasDef aliases;
    }

    /**
     * Loads, binds and validates the content shipped on the classpath, including the string keys
     * it needs from {@code data/strings/en.json} (D25).
     *
     * @return the content
     * @throws ContentException when a file is missing, malformed, does not bind, breaks a rule or
     *     is missing a display string
     */
    public static GameContent load() {
        GameContent content = fromJson(ContentLoader.loadAll(ContentLoader.FILES));
        ContentValidator.validateStrings(content);
        return content;
    }

    /**
     * Binds and validates already-parsed content (fixtures, tests and tools).
     *
     * <p>{@link #REQUIRED_FILES} must be present; every other file is optional and its absence
     * turns off the checks that would need it (E19).
     *
     * @param files the parsed trees keyed by base name
     * @return the content
     * @throws ContentException when a required file is missing, something does not bind or a rule
     *     is broken
     */
    public static GameContent fromJson(Map<String, JsonElement> files) {
        Objects.requireNonNull(files, "files");
        List<String> errors = new ArrayList<>();
        Bound bound = new Bound();
        bound.birds = bindList(files, BIRDS, BirdDef.class, bound, errors);
        DifficultyDef difficulty = bind(files, DIFFICULTY, DifficultyDef.class, bound, errors);
        if (difficulty != null) {
            bound.difficulty = difficulty;
        }
        bound.economy = bind(files, ECONOMY, EconomyDef.class, bound, errors);
        UpgradesDef upgrades = bind(files, UPGRADES, UpgradesDef.class, bound, errors);
        if (upgrades != null) {
            bound.trees = upgrades.trees();
            bound.upgrades = upgrades.nodes();
        }
        AbilitiesDef abilities = bind(files, ABILITIES, AbilitiesDef.class, bound, errors);
        if (abilities != null) {
            bound.abilities = abilities.abilities();
        }
        bound.modifiers = bind(files, MODIFIERS, ModifiersDef.class, bound, errors);
        WorldsDef worlds = bind(files, WORLDS, WorldsDef.class, bound, errors);
        if (worlds != null) {
            bound.worlds = worlds.worlds();
        }
        PatternsDef patterns = bind(files, PATTERNS, PatternsDef.class, bound, errors);
        if (patterns != null) {
            bound.patterns = patterns.patterns();
        }
        ChallengesDef challenges = bind(files, CHALLENGES, ChallengesDef.class, bound, errors);
        if (challenges != null) {
            bound.challenges = challenges.challenges();
        }
        AchievementsDef achievements =
                bind(files, ACHIEVEMENTS, AchievementsDef.class, bound, errors);
        if (achievements != null) {
            bound.achievements = achievements.achievements();
        }
        bound.aliases = bind(files, ALIASES, AliasDef.class, bound, errors);
        if (!errors.isEmpty()) {
            throw new ContentException("Content failed to bind", errors);
        }
        GameContent content = new GameContent(bound);
        ContentValidator.validate(content);
        return content;
    }

    private static <T> T bind(Map<String, JsonElement> files, String name, Class<T> type,
            Bound bound, List<String> errors) {
        JsonElement root = files.get(name);
        String file = ContentLoader.fileOf(name);
        if (root == null) {
            if (REQUIRED_FILES.contains(name)) {
                errors.add(file + "#: missing content file");
            }
            return null;
        }
        bound.files.add(name);
        StrictBinder binder = new StrictBinder(file);
        T def = binder.bind(type, root);
        errors.addAll(binder.errors());
        return def;
    }

    private static <T> List<T> bindList(Map<String, JsonElement> files, String name,
            Class<T> elementType, Bound bound, List<String> errors) {
        JsonElement root = files.get(name);
        String file = ContentLoader.fileOf(name);
        if (root == null) {
            if (REQUIRED_FILES.contains(name)) {
                errors.add(file + "#: missing content file");
            }
            return List.of();
        }
        bound.files.add(name);
        StrictBinder binder = new StrictBinder(file);
        List<T> defs = binder.bindList(elementType, root);
        errors.addAll(binder.errors());
        return defs;
    }

    /**
     * Whether a content file was part of the set this content was built from.
     *
     * <p>Cross-reference rules that point into a file consult this first: an M1-shaped fixture
     * cannot be blamed for a challenge id it has no challenge file to resolve (E19), while the
     * shipped set carries every file and is therefore checked in full.
     *
     * @param name the base name, for example {@code worlds}
     * @return {@code true} when the file was supplied
     */
    public boolean has(String name) {
        return files.contains(name);
    }

    /**
     * The base names of the files this content was built from, in load order.
     *
     * @return an unmodifiable set
     */
    public Set<String> files() {
        return files;
    }

    /**
     * Whether the systems behind a kind of content already exist (E19).
     *
     * <p>M4 authors and validates abilities, worlds, challenges and achievements, but only M5,
     * M7 and M8 make them do anything. The UI asks this to show them as locked by milestone
     * instead of offering something that cannot happen yet.
     *
     * @param kind the kind
     * @return {@code true} when the kind can be used in a run today
     */
    public boolean playable(ContentKind kind) {
        return PLAYABLE_KINDS.contains(kind);
    }

    /**
     * Whether one entry of a kind is playable (E19). One kind differs per id: a feature plays
     * only once the system behind it exists ({@link #featureMilestone}). Worlds answered per id
     * from M4 to M6 — Green Fields alone — and every world plays from M7, so they answer per kind
     * again; the per-id method stays because the selection and the shop ask it and a later build
     * may stage content the same way.
     *
     * @param kind the kind
     * @param id the entry id
     * @return {@code true} when that entry can be used in a run today
     */
    public boolean playable(ContentKind kind, String id) {
        if (kind == ContentKind.FEATURE) {
            return featureMilestone(id) == null && playable(kind);
        }
        return playable(kind);
    }

    /**
     * The milestone the system behind a feature arrives in (E19).
     *
     * <p>{@code feature:modifiers} was listed here from M4 until M6: the draft it gates — pool,
     * director, synergies, the two stat layers — was complete, but without
     * {@code ModifierChoiceOverlay} a run would have frozen on a draft nobody could answer. The
     * overlay ships in M6, so the entry is gone and {@code RunLoadout.allowOffers} now says yes to
     * a profile that owns the feature. {@code feature:seeded_runs} is read by the Seeded mode
     * entry in M9. Neither is a lie the shop is allowed to tell silently, which is what this table
     * is for.
     *
     * @param id the bare feature id
     * @return the milestone name, or {@code null} when the feature works today
     */
    public static String featureMilestone(String id) {
        return FEATURE_MILESTONES.get(id);
    }

    /**
     * The bird registry, in file order.
     *
     * @return the registry
     */
    public Registry<BirdDef> birds() {
        return birds;
    }

    /**
     * The difficulty-curve registry, in file order.
     *
     * @return the registry
     */
    public Registry<CurveDef> curves() {
        return curves;
    }

    /**
     * The tier registry, in file order.
     *
     * @return the registry
     */
    public Registry<TierDef> tiers() {
        return tiers;
    }

    /**
     * The upgrade-tree registry, in file order (D13).
     *
     * @return the registry, empty when {@code upgrades.json} was not supplied
     */
    public Registry<TreeDef> trees() {
        return trees;
    }

    /**
     * The upgrade-node registry, in file order (D13).
     *
     * @return the registry, empty when {@code upgrades.json} was not supplied
     */
    public Registry<UpgradeDef> upgrades() {
        return upgrades;
    }

    /**
     * The ability registry, in file order (M4 stub, completed in M5).
     *
     * @return the registry, empty when {@code abilities.json} was not supplied
     */
    public Registry<AbilityDef> abilities() {
        return abilities;
    }

    /**
     * The run-modifier registry, in file order (M6).
     *
     * @return the registry, empty when {@code modifiers.json} was not supplied
     */
    public Registry<ModifierDef> modifiers() {
        return modifiers;
    }

    /**
     * The synergy registry, in file order (M6).
     *
     * @return the registry, empty when {@code modifiers.json} was not supplied
     */
    public Registry<SynergyDef> synergies() {
        return synergies;
    }

    /**
     * The whole of {@code modifiers.json} (§4): the schedule, the offer width, the rarity weights
     * and both lists.
     *
     * @return the block, {@link ModifiersDef#EMPTY} when the file was not supplied
     */
    public ModifiersDef modifierBlock() {
        return modifierBlock;
    }

    /**
     * The roguelite content of one run: everything of {@code modifiers.json} filtered down to the
     * cards a profile may be offered (M6, D27).
     *
     * @param available the modifier ids the profile owns, bare or namespaced; cards whose
     *     {@code unlock} is {@code default} are available whether they are listed or not
     * @return the catalogue, {@link ModifierCatalog#EMPTY} when the file was not supplied
     */
    public ModifierCatalog modifierCatalog(Collection<String> available) {
        return ModifierCatalog.of(modifierBlock, available);
    }

    /**
     * The world registry, in file order (M4 stub, completed in M7).
     *
     * @return the registry, empty when {@code worlds.json} was not supplied
     */
    public Registry<WorldDef> worlds() {
        return worlds;
    }

    /**
     * The obstacle pattern registry, in file order (M7).
     *
     * @return the registry, empty when {@code patterns.json} was not supplied
     */
    public Registry<PatternDef> patterns() {
        return patterns;
    }

    /**
     * The simulation spec of a world (M7): curve, effects, flags, weights, patterns, ambience and
     * rule cycles resolved from {@code worlds.json}.
     *
     * <p>Resolved on first use and cached, not in the constructor: turning a pattern step's
     * parameters into typed geometry rejects invalid values, and the validator has to report those
     * with their pointers before any spec is built.
     *
     * @param id the world id
     * @return the spec
     * @throws UnknownIdException when no world carries the id
     */
    public synchronized WorldSpec worldSpec(String id) {
        WorldSpec spec = worldSpecs.get(id);
        if (spec == null) {
            if (!worlds.contains(id)) {
                throw new UnknownIdException("world", id);
            }
            spec = ContentAdapters.toSpec(worlds.get(id), this);
            worldSpecs.put(id, spec);
        }
        return spec;
    }

    /**
     * The simulation spec of a pattern (M7), with every step's parameters typed.
     *
     * @param id the pattern id
     * @return the spec
     * @throws UnknownIdException when no pattern carries the id
     */
    public synchronized PatternSpec patternSpec(String id) {
        PatternSpec spec = patternSpecs.get(id);
        if (spec == null) {
            if (!patterns.contains(id)) {
                throw new UnknownIdException("pattern", id);
            }
            spec = ContentAdapters.toSpec(patterns.get(id));
            patternSpecs.put(id, spec);
        }
        return spec;
    }

    /**
     * The challenge registry, in file order (M4 stub, completed in M8).
     *
     * @return the registry, empty when {@code challenges.json} was not supplied
     */
    public Registry<ChallengeDef> challenges() {
        return challenges;
    }

    /**
     * The achievement registry, in file order (M4 stub, completed in M8).
     *
     * @return the registry, empty when {@code achievements.json} was not supplied
     */
    public Registry<AchievementDef> achievements() {
        return achievements;
    }

    /**
     * The feature registry, in {@code economy.json} order (D13).
     *
     * @return the registry
     */
    public Registry<FeatureDef> features() {
        return features;
    }

    /**
     * The id alias table (E21).
     *
     * @return the table, or {@link AliasDef#EMPTY} when {@code aliases.json} was not supplied
     */
    public AliasDef aliases() {
        return aliases;
    }

    /**
     * The whole of {@code economy.json} (§4): rewards, XP, features, daily and prestige.
     *
     * @return the economy
     */
    public EconomyDef economy() {
        return economy;
    }

    /**
     * {@code difficulty.json.speedRampPerTick} (E32.b).
     *
     * @return the rate
     */
    public double speedRampPerTick() {
        return speedRampPerTick;
    }

    /**
     * The reserved endless-tier generator block.
     *
     * @return the block, or {@code null} (always {@code null} in 1.0)
     */
    public TierGeneratorDef tierGenerator() {
        return tierGenerator;
    }

    /**
     * The simulation profile of a bird.
     *
     * @param id the bird id
     * @return the profile
     * @throws UnknownIdException when no bird carries the id
     */
    public BirdProfile birdProfile(String id) {
        BirdProfile profile = birdProfiles.get(id);
        if (profile == null) {
            throw new UnknownIdException("bird", id);
        }
        return profile;
    }

    /**
     * The simulation spec of a difficulty curve.
     *
     * @param id the curve id
     * @return the spec
     * @throws UnknownIdException when no curve carries the id
     */
    public CurveSpec curveSpec(String id) {
        CurveSpec spec = curveSpecs.get(id);
        if (spec == null) {
            throw new UnknownIdException("curve", id);
        }
        return spec;
    }

    /**
     * The simulation spec of a tier.
     *
     * @param id the tier id
     * @return the spec
     * @throws UnknownIdException when no tier carries the id
     */
    public TierSpec tierSpec(String id) {
        TierSpec spec = tierSpecs.get(id);
        if (spec == null) {
            throw new UnknownIdException("tier", id);
        }
        return spec;
    }

    /**
     * The id of the tier flagged {@code default} in {@code difficulty.json}.
     *
     * @return the tier id, or {@code null} when no tier is flagged (the validator rejects that)
     */
    public String defaultTierId() {
        for (TierDef def : tiers) {
            if (def.defaultTier()) {
                return def.id();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "GameContent{files=" + files + ", birds=" + birds.size() + ", upgrades="
                + upgrades.size() + ", abilities=" + abilities.size() + ", modifiers="
                + modifiers.size() + ", worlds=" + worlds.size()
                + ", challenges=" + challenges.size() + ", achievements=" + achievements.size()
                + '}';
    }
}
