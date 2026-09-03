package io.github.michelbr84.flapforge.content;

import io.github.michelbr84.flapforge.content.defs.AmbientDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.BossDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.CurveDef;
import io.github.michelbr84.flapforge.content.defs.PatternDef;
import io.github.michelbr84.flapforge.content.defs.PatternStepDef;
import io.github.michelbr84.flapforge.content.defs.RampEffectDef;
import io.github.michelbr84.flapforge.content.defs.RuleCycleOptionDef;
import io.github.michelbr84.flapforge.content.defs.RuleCyclesDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.SynergyEffectDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleParams;
import io.github.michelbr84.flapforge.gameplay.spec.AmbientSpec;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.BossSpec;
import io.github.michelbr84.flapforge.gameplay.spec.ChallengeSpec;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.PatternSpec;
import io.github.michelbr84.flapforge.gameplay.spec.RampEffect;
import io.github.michelbr84.flapforge.gameplay.spec.RuleCycleSpec;
import io.github.michelbr84.flapforge.gameplay.spec.SynergyEffect;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProfileSchema;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps content definitions onto the simulation seam records (D10). This is the only place where
 * a {@code *Def} becomes something {@code gameplay} understands, so the simulation never learns
 * about JSON and the definitions never learn about the stat pipeline.
 */
public final class ContentAdapters {

    private ContentAdapters() {
    }

    /**
     * Converts a bird definition.
     *
     * @param def the definition
     * @return the profile (effect source {@code bird:&lt;id&gt;}, ramp source
     *     {@code bird:&lt;id&gt;:ramp}, synergy source {@code bird:&lt;id&gt;:synergy})
     */
    public static BirdProfile toProfile(BirdDef def) {
        String source = "bird:" + def.id();
        List<StatModifier> effects = new ArrayList<>(def.effects().size());
        for (StatModifierDef e : def.effects()) {
            effects.add(e.toModifier(source));
        }
        List<RampEffect> ramps = new ArrayList<>(def.rampEffects().size());
        for (RampEffectDef r : def.rampEffects()) {
            ramps.add(r.toRampEffect());
        }
        List<SynergyEffect> synergies = new ArrayList<>(def.synergyEffects().size());
        for (SynergyEffectDef s : def.synergyEffects()) {
            synergies.add(s.toSynergyEffect());
        }
        return new BirdProfile(def.id(), def.baseStats(), def.hitbox().toSpec(), effects, ramps,
                synergies, def.passiveSlots());
    }

    /**
     * Converts a curve definition.
     *
     * @param def the definition
     * @return the curve spec
     */
    public static CurveSpec toSpec(CurveDef def) {
        return def.toSpec();
    }

    /**
     * Converts a tier definition.
     *
     * @param def the definition
     * @return the tier spec
     */
    public static TierSpec toSpec(TierDef def) {
        return def.toSpec();
    }

    /**
     * Converts a world definition (M7): the curve is looked up, the effects become
     * {@code WORLD}-layer modifiers with source {@code world:<id>}, the rule cycle options'
     * effects get source {@code cycle:<index>}, and the pattern ids are resolved through
     * {@link GameContent#patternSpec}.
     *
     * @param def the definition
     * @param content the content the curve and the patterns are resolved against
     * @return the world spec
     * @throws UnknownIdException when the curve or a pattern id is not in the registries
     */
    public static WorldSpec toSpec(WorldDef def, GameContent content) {
        String source = "world:" + def.id();
        List<StatModifier> effects = new ArrayList<>(def.effects().size());
        for (StatModifierDef e : def.effects()) {
            effects.add(e.toModifier(source));
        }
        List<PatternSpec> patterns = new ArrayList<>(def.patterns().size());
        for (String id : def.patterns()) {
            patterns.add(content.patternSpec(id));
        }
        AmbientDef ambient = def.ambient() == null ? AmbientDef.NONE : def.ambient();
        return new WorldSpec(def.id(), content.curveSpec(def.curve()), effects,
                RuleSet.of(def.flags()), def.spawnWeights(), patterns,
                new AmbientSpec(ambient.darkness(), ambient.windX(), ambient.windY(),
                        ambient.lightningEveryGates()),
                toSpec(def.ruleCycles()));
    }

    /**
     * Converts a boss block (M8) with its phases resolved.
     *
     * @param def the definition
     * @param ownerId the id the encounter belongs to: the world of a world boss, the challenge of
     *     a challenge boss
     * @param worldId the world the encounter clears, or {@code null} for a challenge boss (E26)
     * @param content the content the phase ids are resolved against
     * @return the spec
     * @throws UnknownIdException when a phase id is not in the pattern registry
     */
    public static BossSpec toSpec(BossDef def, String ownerId, String worldId,
            GameContent content) {
        List<PatternSpec> phases = new ArrayList<>(def.patterns().size());
        for (String id : def.patterns()) {
            phases.add(content.patternSpec(id));
        }
        return new BossSpec(ownerId, worldId, def.atGate(), def.warningTicks(), phases,
                def.surviveTicks());
    }

    /**
     * Converts the simulation half of a challenge (M8): the {@code CHALLENGE} layer effects and
     * the objective. The world, tier, flags, forced modifiers and offer switch travel in the
     * {@code RunConfig} ({@link RunFactory#challengeConfig}); the curve override, the forced
     * pattern and the boss are resolved into the {@code RunSetup} by {@link RunFactory#setup}.
     *
     * @param def the definition
     * @return the spec
     */
    public static ChallengeSpec toSpec(ChallengeDef def) {
        String source = "challenge:" + def.id();
        List<StatModifier> effects = new ArrayList<>(def.effects().size());
        for (StatModifierDef e : def.effects()) {
            effects.add(e.toModifier(source));
        }
        return new ChallengeSpec(def.id(), effects, def.objective());
    }

    /**
     * Converts a rule cycle block (M7).
     *
     * @param def the definition, or {@code null}
     * @return the spec, or {@code null} for a world without cycles
     */
    public static RuleCycleSpec toSpec(RuleCyclesDef def) {
        if (def == null) {
            return null;
        }
        List<RuleCycleSpec.Option> options = new ArrayList<>(def.options().size());
        for (int i = 0; i < def.options().size(); i++) {
            RuleCycleOptionDef option = def.options().get(i);
            String source = "cycle:" + i;
            List<StatModifier> effects = new ArrayList<>(option.effects().size());
            for (StatModifierDef e : option.effects()) {
                effects.add(e.toModifier(source));
            }
            options.add(new RuleCycleSpec.Option(RuleSet.of(option.flags()), effects));
        }
        return new RuleCycleSpec(def.everyGates(), def.telegraphTicks(), options);
    }

    /**
     * Converts a pattern definition (M7): every step's parameters are typed through
     * {@link ObstacleParams#resolve}, which is where a {@code 0..1} fraction becomes pixels.
     *
     * @param def the definition
     * @return the pattern spec
     * @throws IllegalArgumentException when a step's parameters break the kind's contract (the
     *     validator reports the same problems with their pointers first)
     */
    public static PatternSpec toSpec(PatternDef def) {
        List<PatternSpec.Step> steps = new ArrayList<>(def.steps().size());
        for (PatternStepDef step : def.steps()) {
            steps.add(new PatternSpec.Step(step.dx(), step.kind(),
                    ObstacleParams.resolve(step.kind(), step.params()), step.scores()));
        }
        return new PatternSpec(def.id(), def.weight(), def.minGate(), def.stepsScore(), steps);
    }

    /**
     * The id tables {@code PlayerProfile.normalize} needs (E15, E21): which birds, palettes,
     * worlds, tiers and abilities this build ships, and which tree each upgrade node belongs to.
     *
     * <p>{@link ProfileSchema} is deliberately content-free, so this is where the registries are
     * projected onto it. A registry a milestone has not shipped yet stays empty, which
     * {@link ProfileSchema} reads as "accept every id" — a build that does not know about worlds
     * must not repair a world selection away.
     *
     * @param content the loaded content
     * @return the schema
     */
    public static ProfileSchema toProfileSchema(GameContent content) {
        ProfileSchema.Builder schema = ProfileSchema.builder();
        for (BirdDef bird : content.birds()) {
            List<String> palettes = new ArrayList<>(bird.palettes().size());
            for (PaletteDef palette : bird.palettes()) {
                palettes.add(palette.id());
            }
            schema.bird(bird.id(), palettes);
        }
        schema.worlds(content.worlds().ids());
        schema.tiers(content.tiers().ids());
        schema.abilities(content.abilities().ids());
        for (UpgradeDef node : content.upgrades()) {
            schema.upgradeNode(node.id(), node.tree());
        }
        schema.abilityLevelCap(UpgradeManager.abilityLevelCeiling(content));
        return schema.defaults(PlayerProfile.DEFAULT_BIRD, PlayerProfile.DEFAULT_PALETTE,
                PlayerProfile.DEFAULT_WORLD, PlayerProfile.DEFAULT_TIER,
                PlayerProfile.DEFAULT_ACTIVE_ABILITY).build();
    }
}
