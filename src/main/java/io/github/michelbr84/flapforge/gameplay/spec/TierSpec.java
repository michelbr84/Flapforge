package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.List;
import java.util.Objects;

/**
 * A difficulty tier as the simulation sees it (D20): effects for the {@code TIER} layer, flags
 * and the reward multiplier the economy applies.
 *
 * @param id the tier id
 * @param effects effects pushed into the {@code TIER} layer
 * @param flags rules the tier activates
 * @param rewardMult multiplier on run rewards
 */
public record TierSpec(String id, List<StatModifier> effects, RuleSet flags, double rewardMult) {

    /** The default tier: no effects, no flags, rewards ×1. */
    public static final TierSpec NORMAL = new TierSpec("normal", List.of(), RuleSet.EMPTY, 1.0);

    /**
     * Copies the effects.
     *
     * @param id the tier id
     * @param effects tier effects
     * @param flags tier flags
     * @param rewardMult reward multiplier
     */
    public TierSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(flags, "flags");
        effects = List.copyOf(effects);
    }
}
