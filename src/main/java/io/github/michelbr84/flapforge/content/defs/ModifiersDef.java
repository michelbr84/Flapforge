package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.modifier.Rarity;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The root of {@code modifiers.json} (§4): when drafts happen, how wide they are, how the rarities
 * are weighted, and the two lists the roguelite layer is made of.
 *
 * @param offerSchedule the gate counts that open a draft, ascending
 * @param choicesPerOffer how many cards one draft shows
 * @param rarityWeights the draw weight of each rarity; a rarity with no entry is never drawn
 * @param modifiers the draftable cards, in file order
 * @param synergies the set bonuses, in file order
 */
public record ModifiersDef(List<Integer> offerSchedule, int choicesPerOffer,
        Map<Rarity, Integer> rarityWeights, List<ModifierDef> modifiers,
        List<SynergyDef> synergies) {

    /** An empty block, for content sets that ship no {@code modifiers.json}. */
    public static final ModifiersDef EMPTY =
            new ModifiersDef(List.of(), 0, Map.of(), List.of(), List.of());

    /**
     * Copies the collections into deterministic, unmodifiable ones.
     *
     * @param offerSchedule the gate counts that open a draft
     * @param choicesPerOffer how many cards one draft shows
     * @param rarityWeights the draw weight of each rarity
     * @param modifiers the draftable cards
     * @param synergies the set bonuses
     */
    public ModifiersDef {
        offerSchedule = List.copyOf(offerSchedule);
        Map<Rarity, Integer> weights = new EnumMap<>(Rarity.class);
        weights.putAll(rarityWeights);
        rarityWeights = Collections.unmodifiableMap(weights);
        modifiers = List.copyOf(modifiers);
        synergies = List.copyOf(synergies);
    }
}
