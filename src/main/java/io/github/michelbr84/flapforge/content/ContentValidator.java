package io.github.michelbr84.flapforge.content;

import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.CurveDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.gameplay.difficulty.DifficultyCurve;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The M1 content rules (D10, E19). Milestones M1–M3 ship the <em>minimal</em> validator: id
 * syntax and uniqueness, enum validity (already enforced by {@link StrictBinder}), the
 * cost/level consistency of whatever carries costs, and the classic table — the numeric proof
 * that the shipped data still reproduces the upstream feel through the real stat pipeline.
 *
 * <p>Cross-reference, unlock-graph and string-key checks are FULL from M4, when the files they
 * point at exist; see {@link #validateCrossReferencesM4(GameContent, List)}.
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

    private static final double EPSILON = 1e-9;
    private static final String BIRDS_FILE = "birds.json";
    private static final String DIFFICULTY_FILE = "difficulty.json";

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
        validateCostsAndLevels(content, errors);
        validateClassicTable(content, errors);
        validateCrossReferencesM4(content, errors);
        return errors;
    }

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
            Set<String> palettes = new HashSet<>();
            for (int p = 0; p < def.palettes().size(); p++) {
                PaletteDef palette = def.palettes().get(p);
                String pat = at + "/palettes/" + p;
                checkId(errors, pat + "/id", "palette", palette.id());
                if (!palettes.add(palette.id())) {
                    errors.add(pat + "/id: duplicate palette id '" + palette.id() + "' on bird '"
                            + def.id() + "'");
                }
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
        }
        if (defs.isEmpty()) {
            errors.add(DIFFICULTY_FILE + "#/tiers: no tier is defined");
        } else if (defaults != 1) {
            errors.add(DIFFICULTY_FILE + "#/tiers: exactly one tier must be flagged \"default\", "
                    + "found " + defaults);
        }
    }

    /**
     * Cost and level consistency ({@code costs.length == maxLevel}, ability level caps, E3).
     *
     * <p>Nothing in the M1 file set carries costs or levels: {@code upgrades.json} lands in M4 and
     * {@code abilities.json} in M5. The hook exists so the rule has one home when it applies.
     *
     * @param content the content to check
     * @param errors where to append problems
     */
    private static void validateCostsAndLevels(GameContent content, List<String> errors) {
        // No cost- or level-bearing definition exists before M4; nothing to check yet.
    }

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

    /**
     * Cross-reference checks deferred to M4 (E19): bird passive abilities, palette and tier
     * unlock ids ({@code challenge}, {@code achievement}, {@code world_cleared}), upgrade
     * prerequisites and grants, world and challenge references, achievement counters, string
     * keys and the unlock graph. They stay off until the files they point at ship, so every
     * milestone's data passes its own validator.
     *
     * @param content the content to check
     * @param errors where to append problems
     */
    private static void validateCrossReferencesM4(GameContent content, List<String> errors) {
        // TODO(M4): enable cross-reference, unlock-graph and string-key validation (E19).
    }

    private static void checkId(List<String> errors, String at, String kind, String id) {
        if (id == null) {
            errors.add(at + ": missing " + kind + " id");
        } else if (!ID.matcher(id).matches()) {
            errors.add(at + ": " + kind + " id '" + id + "' does not match " + ID.pattern());
        }
    }
}
