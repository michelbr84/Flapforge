package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.modifier.ModifierTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One set bonus of {@code modifiers.json.synergies} (D27, E16).
 *
 * <p>{@link #requiresTags()} is a <em>multiset</em>: {@code ["ECONOMY", "ECONOMY"]} asks for two
 * economy contributions, and each modifier the run has taken contributes its own tags once
 * however many times it was stacked. A synergy activates only when the multiset is covered using
 * at least two distinct taken modifiers, which is what stops a single card from completing a set
 * bonus by itself.
 *
 * <p>A synergy is not an unlockable and has no unlock block: it activates itself from the build.
 *
 * @param id the synergy id, unique across the file
 * @param requiresTags the tag multiset that has to be covered
 * @param effects the stat modifiers pushed into the {@code MOD_SYNERGY} layer while active
 * @param flags rule flags the synergy turns on while active
 */
public record SynergyDef(String id, List<ModifierTag> requiresTags, List<StatModifierDef> effects,
        List<RuleFlag> flags) {

    /** Prefix of the label a synergy's effects carry in a stat breakdown. */
    public static final String SOURCE_PREFIX = "synergy:";

    /**
     * Copies the lists and checks the required fields.
     *
     * @throws NullPointerException when the id is missing
     */
    public SynergyDef {
        Objects.requireNonNull(id, "id");
        requiresTags = List.copyOf(requiresTags);
        effects = List.copyOf(effects);
        flags = List.copyOf(flags);
    }

    /**
     * The simulation seam records, labelled {@code synergy:<id>}.
     *
     * @return the modifiers, in authoring order
     */
    public List<StatModifier> toModifiers() {
        List<StatModifier> out = new ArrayList<>(effects.size());
        String source = SOURCE_PREFIX + id;
        for (StatModifierDef effect : effects) {
            out.add(effect.toModifier(source));
        }
        return out;
    }
}
