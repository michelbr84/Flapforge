package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * Reserved shape for endless generated tiers (D20; explicitly deferred). {@code difficulty.json}
 * ships {@code "tierGenerator": null}; the record exists so the key is bound strictly the day it
 * carries a value instead of being silently ignored.
 *
 * @param perTierEffects effects added per generated tier
 * @param rewardGrowth reward multiplier growth per generated tier
 */
public record TierGeneratorDef(List<StatModifierDef> perTierEffects, double rewardGrowth) {

    /**
     * Copies the effects.
     *
     * @param perTierEffects effects per generated tier
     * @param rewardGrowth reward growth
     */
    public TierGeneratorDef {
        perTierEffects = List.copyOf(perTierEffects);
    }
}
