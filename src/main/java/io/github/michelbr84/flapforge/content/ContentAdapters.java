package io.github.michelbr84.flapforge.content;

import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.CurveDef;
import io.github.michelbr84.flapforge.content.defs.RampEffectDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.SynergyEffectDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.RampEffect;
import io.github.michelbr84.flapforge.gameplay.spec.SynergyEffect;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
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
