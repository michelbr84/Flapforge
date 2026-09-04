package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.content.JsonName;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One difficulty tier of {@code difficulty.json} (D20, E19): effects for the {@code TIER} layer,
 * rule flags, the reward multiplier the economy applies and how the tier is unlocked.
 *
 * <p>The JSON key of {@code defaultTier} is {@code "default"}, which is not a Java identifier;
 * {@link JsonName} carries the mapping.
 *
 * @param id the tier id
 * @param defaultTier whether this tier is selected when nothing else is
 * @param effects effects pushed into the {@code TIER} layer
 * @param flags rules the tier activates
 * @param rewardMult multiplier on run rewards
 * @param unlock how the tier is earned
 */
public record TierDef(String id, @JsonName("default") boolean defaultTier,
        List<StatModifierDef> effects, List<RuleFlag> flags, double rewardMult,
        UnlockConditionDef unlock) {

    /** Namespace of the unlockable id of a tier. */
    public static final String NAMESPACE = "tier:";

    /**
     * Copies the collections.
     *
     * @param id the tier id
     * @param defaultTier whether this is the default tier
     * @param effects tier effects
     * @param flags tier flags
     * @param rewardMult reward multiplier
     * @param unlock the unlock condition
     */
    public TierDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(unlock, "unlock");
        effects = List.copyOf(effects);
        flags = List.copyOf(flags);
        if (rewardMult <= 0) {
            throw new IllegalArgumentException("rewardMult must be positive: " + rewardMult);
        }
    }

    /**
     * The simulation seam record.
     *
     * @return the tier spec (effect source {@code tier:&lt;id&gt;})
     */
    public TierSpec toSpec() {
        List<StatModifier> out = new ArrayList<>(effects.size());
        String source = "tier:" + id;
        for (StatModifierDef e : effects) {
            out.add(e.toModifier(source));
        }
        return new TierSpec(id, out, RuleSet.of(flags), rewardMult);
    }

    /**
     * The namespaced unlockable id, {@code tier:<id>}.
     *
     * @return the id
     */
    public String unlockableId() {
        return NAMESPACE + id;
    }
}
