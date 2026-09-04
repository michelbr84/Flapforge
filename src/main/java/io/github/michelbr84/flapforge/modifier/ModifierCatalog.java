package io.github.michelbr84.flapforge.modifier;

import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.ModifiersDef;
import io.github.michelbr84.flapforge.content.defs.SynergyDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The roguelite content <em>one run</em> plays with: the offer schedule, the offer width, the
 * rarity weights, the cards this profile may actually be shown and the set bonuses (D27).
 *
 * <p>It is deliberately a run-scoped snapshot rather than a view of the registries. Three of the
 * seventeen shipped modifiers are unlockables ({@code gold_rush}, {@code phoenix},
 * {@code stormrider}), so what may appear in a draft depends on the profile; resolving that once,
 * where the profile is known, keeps {@link ModifierPool} and {@code ModifierDirector} free of any
 * notion of ownership. {@link #EMPTY} is what a run without {@code modifiers.json} — the classic
 * seam, the golden run, the published {@code --headless-run} hash — carries, and a director built
 * on it does nothing at all.
 */
public final class ModifierCatalog {

    /** No schedule, no cards, no synergies: the roguelite layer is off. */
    public static final ModifierCatalog EMPTY =
            new ModifierCatalog(List.of(), 0, Map.of(), List.of(), List.of());

    private final List<Integer> offerSchedule;
    private final int choicesPerOffer;
    private final Map<Rarity, Integer> rarityWeights;
    private final List<ModifierDef> modifiers;
    private final List<SynergyDef> synergies;
    private final Map<String, ModifierDef> byId;

    /**
     * Creates a catalogue.
     *
     * @param offerSchedule the gate counts that open a draft, ascending
     * @param choicesPerOffer how many cards one draft shows
     * @param rarityWeights the draw weight of each rarity
     * @param modifiers the cards this run may be offered, in content order
     * @param synergies the set bonuses, in content order
     */
    public ModifierCatalog(List<Integer> offerSchedule, int choicesPerOffer,
            Map<Rarity, Integer> rarityWeights, List<ModifierDef> modifiers,
            List<SynergyDef> synergies) {
        this.offerSchedule = List.copyOf(offerSchedule);
        this.choicesPerOffer = Math.max(0, choicesPerOffer);
        Map<Rarity, Integer> weights = new EnumMap<>(Rarity.class);
        weights.putAll(rarityWeights);
        this.rarityWeights = Collections.unmodifiableMap(weights);
        this.modifiers = List.copyOf(modifiers);
        this.synergies = List.copyOf(synergies);
        Map<String, ModifierDef> map = new LinkedHashMap<>();
        for (ModifierDef def : this.modifiers) {
            map.putIfAbsent(def.id(), def);
        }
        this.byId = Collections.unmodifiableMap(map);
    }

    /**
     * Builds a catalogue from a bound {@code modifiers.json}, keeping only the cards a profile may
     * be shown.
     *
     * @param defs the bound file, or {@code null}
     * @param available the modifier ids the profile owns (bare ids, not namespaced); every card
     *     whose {@code unlock} is {@code default} is available whether it is listed or not
     * @return the catalogue, {@link #EMPTY} when there is nothing to draft
     */
    public static ModifierCatalog of(ModifiersDef defs, Collection<String> available) {
        if (defs == null || defs.modifiers().isEmpty()) {
            return EMPTY;
        }
        Set<String> owned = available == null ? Set.of() : new LinkedHashSet<>(available);
        List<ModifierDef> kept = new ArrayList<>(defs.modifiers().size());
        for (ModifierDef def : defs.modifiers()) {
            if (def.unlock().type() == UnlockType.DEFAULT || owned.contains(def.id())
                    || owned.contains(def.unlockableId())) {
                kept.add(def);
            }
        }
        return new ModifierCatalog(defs.offerSchedule(), defs.choicesPerOffer(),
                defs.rarityWeights(), kept, defs.synergies());
    }

    /**
     * Whether this catalogue can ever open a draft.
     *
     * @return {@code true} when there is no schedule, no card or no width
     */
    public boolean isEmpty() {
        return offerSchedule.isEmpty() || modifiers.isEmpty() || choicesPerOffer <= 0;
    }

    /**
     * The gate counts that open a draft.
     *
     * @return an unmodifiable list, ascending
     */
    public List<Integer> offerSchedule() {
        return offerSchedule;
    }

    /**
     * How many cards one draft shows.
     *
     * @return the width
     */
    public int choicesPerOffer() {
        return choicesPerOffer;
    }

    /**
     * The draw weight of a rarity.
     *
     * @param rarity the rarity
     * @return the weight, or 0 when the file gives it none (never drawn)
     */
    public int weightOf(Rarity rarity) {
        Integer weight = rarityWeights.get(rarity);
        return weight == null ? 0 : Math.max(0, weight);
    }

    /**
     * The weights, for the UI and the balancing report.
     *
     * @return an unmodifiable map in {@link Rarity} order
     */
    public Map<Rarity, Integer> rarityWeights() {
        return rarityWeights;
    }

    /**
     * The cards this run may be offered, in content order.
     *
     * @return an unmodifiable list
     */
    public List<ModifierDef> modifiers() {
        return modifiers;
    }

    /**
     * The set bonuses, in content order.
     *
     * @return an unmodifiable list
     */
    public List<SynergyDef> synergies() {
        return synergies;
    }

    /**
     * Looks a card up.
     *
     * @param id the modifier id
     * @return the definition, or {@code null} when this run has no such card
     */
    public ModifierDef get(String id) {
        return id == null ? null : byId.get(id);
    }

    /**
     * Whether a card is part of this catalogue.
     *
     * @param id the modifier id
     * @return {@code true} when it is
     */
    public boolean contains(String id) {
        return id != null && byId.containsKey(id);
    }

    @Override
    public String toString() {
        return "ModifierCatalog{cards=" + modifiers.size() + ", synergies=" + synergies.size()
                + ", schedule=" + offerSchedule + ", choices=" + choicesPerOffer + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModifierCatalog other)) {
            return false;
        }
        return choicesPerOffer == other.choicesPerOffer
                && offerSchedule.equals(other.offerSchedule)
                && rarityWeights.equals(other.rarityWeights)
                && modifiers.equals(other.modifiers)
                && synergies.equals(other.synergies);
    }

    /**
     * Specified hash: {@link Enum#hashCode()} is an identity hash, and this catalogue travels
     * inside the {@code RunSetup} record, so the generated one would differ between JVMs. Only ids
     * and ordinals go in, which is enough to tell two catalogues apart and is the same value
     * everywhere.
     *
     * @return the hash
     */
    @Override
    public int hashCode() {
        int h = offerSchedule.hashCode();
        h = 31 * h + choicesPerOffer;
        for (Rarity rarity : Rarity.values()) {
            h = 31 * h + weightOf(rarity);
        }
        h = 31 * h + byId.keySet().hashCode();
        for (SynergyDef synergy : synergies) {
            h = 31 * h + synergy.id().hashCode();
        }
        return h;
    }
}
