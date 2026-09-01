package io.github.michelbr84.flapforge.content;

import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.CurveDef;
import io.github.michelbr84.flapforge.content.defs.RampEffectDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.RampEffect;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
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
     *     {@code bird:&lt;id&gt;:ramp})
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
        return new BirdProfile(def.id(), def.baseStats(), def.hitbox().toSpec(), effects, ramps,
                def.passiveSlots());
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
}
