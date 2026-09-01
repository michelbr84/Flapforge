package io.github.michelbr84.flapforge.content;

import com.google.gson.JsonElement;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.CurveDef;
import io.github.michelbr84.flapforge.content.defs.DifficultyDef;
import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.TierGeneratorDef;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every registry the game reads its rules from (D10). M1 holds birds, difficulty curves and
 * tiers, M3 adds the economy; later milestones add abilities, upgrades, modifiers, worlds,
 * patterns, challenges and achievements to the same object.
 *
 * <p>Build it with {@link #load()} (the files shipped on the classpath) or
 * {@link #fromJson(Map)} (a fixture, a test or a tool). Both paths bind strictly and validate,
 * so a {@code GameContent} instance is content that has already passed every M1 rule.
 */
public final class GameContent {

    /** Base name of the bird file. */
    public static final String BIRDS = "birds";
    /** Base name of the difficulty file. */
    public static final String DIFFICULTY = "difficulty";
    /** Base name of the economy file. */
    public static final String ECONOMY = "economy";

    private final Registry<BirdDef> birds;
    private final Registry<CurveDef> curves;
    private final Registry<TierDef> tiers;
    private final EconomyDef economy;
    private final double speedRampPerTick;
    private final TierGeneratorDef tierGenerator;
    private final Map<String, BirdProfile> birdProfiles = new LinkedHashMap<>();
    private final Map<String, CurveSpec> curveSpecs = new LinkedHashMap<>();
    private final Map<String, TierSpec> tierSpecs = new LinkedHashMap<>();

    private GameContent(List<BirdDef> birdDefs, DifficultyDef difficulty, EconomyDef economy) {
        this.birds = new Registry<>("bird", birdDefs, BirdDef::id);
        this.curves = new Registry<>("curve", difficulty.curveDefs(), CurveDef::id);
        this.tiers = new Registry<>("tier", difficulty.tiers(), TierDef::id);
        this.economy = economy;
        this.speedRampPerTick = difficulty.speedRampPerTick();
        this.tierGenerator = difficulty.tierGenerator();
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
     * @param files the parsed trees keyed by base name ({@code birds}, {@code difficulty},
     *     {@code economy})
     * @return the content
     * @throws ContentException when a file is missing, does not bind or breaks a rule
     */
    public static GameContent fromJson(Map<String, JsonElement> files) {
        Objects.requireNonNull(files, "files");
        List<String> errors = new ArrayList<>();
        List<BirdDef> birdDefs = bindBirds(files, errors);
        DifficultyDef difficulty = bindDifficulty(files, errors);
        EconomyDef economy = bindEconomy(files, errors);
        if (!errors.isEmpty()) {
            throw new ContentException("Content failed to bind", errors);
        }
        GameContent content = new GameContent(birdDefs, difficulty, economy);
        ContentValidator.validate(content);
        return content;
    }

    private static List<BirdDef> bindBirds(Map<String, JsonElement> files, List<String> errors) {
        JsonElement root = files.get(BIRDS);
        String file = ContentLoader.fileOf(BIRDS);
        if (root == null) {
            errors.add(file + "#: missing content file");
            return List.of();
        }
        StrictBinder binder = new StrictBinder(file);
        List<BirdDef> defs = binder.bindList(BirdDef.class, root);
        errors.addAll(binder.errors());
        return defs;
    }

    private static DifficultyDef bindDifficulty(Map<String, JsonElement> files,
            List<String> errors) {
        JsonElement root = files.get(DIFFICULTY);
        String file = ContentLoader.fileOf(DIFFICULTY);
        if (root == null) {
            errors.add(file + "#: missing content file");
            return new DifficultyDef(Map.of(), 0, List.of(), null);
        }
        StrictBinder binder = new StrictBinder(file);
        DifficultyDef def = binder.bind(DifficultyDef.class, root);
        errors.addAll(binder.errors());
        return def == null ? new DifficultyDef(Map.of(), 0, List.of(), null) : def;
    }

    private static EconomyDef bindEconomy(Map<String, JsonElement> files, List<String> errors) {
        JsonElement root = files.get(ECONOMY);
        String file = ContentLoader.fileOf(ECONOMY);
        if (root == null) {
            errors.add(file + "#: missing content file");
            return null;
        }
        StrictBinder binder = new StrictBinder(file);
        EconomyDef def = binder.bind(EconomyDef.class, root);
        errors.addAll(binder.errors());
        return def;
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
        return "GameContent{birds=" + birds.ids() + ", curves=" + curves.ids() + ", tiers="
                + tiers.ids() + '}';
    }
}
