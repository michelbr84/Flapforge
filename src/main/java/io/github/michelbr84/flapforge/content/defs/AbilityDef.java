package io.github.michelbr84.flapforge.content.defs;

import java.util.List;
import java.util.Objects;

/**
 * One ability of {@code abilities.json} (D9, §4).
 *
 * <p>M4 ships this as a <em>stub with its final unlock and cost blocks</em> (E19): {@link #id},
 * {@link #kind}, {@link #behavior}, {@link #tags}, {@link #unlock} and one
 * {@link AbilityLevelDef} per level carrying its {@code cost} are authored now, so the strict
 * validator can resolve every {@code ability:} reference, price the shop and apply the E3 cap
 * rule. M5 fills {@link #effects} and the level timings/parameters in place and switches on the
 * {@code BehaviorRegistry} check for {@link #behavior}.
 *
 * @param id the ability id
 * @param kind active or passive (D9)
 * @param behavior the {@code BehaviorRegistry} key implementing it (checked from M5)
 * @param tags what rule flags strip (D9)
 * @param levels the three levels; level 1 comes with the unlock
 * @param effects the stat modifiers a passive contributes (populated in M5)
 * @param unlock the condition that unlocks {@code ability:<id>}
 */
public record AbilityDef(String id, AbilityKind kind, String behavior, List<AbilityTag> tags,
        List<AbilityLevelDef> levels, List<StatModifierDef> effects, UnlockConditionDef unlock) {

    /** Namespace of the unlockable id of an ability. */
    public static final String NAMESPACE = "ability:";

    /**
     * Copies the lists and checks the required fields.
     *
     * @throws NullPointerException when the id, the kind or the unlock block is missing
     */
    public AbilityDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(unlock, "unlock");
        tags = List.copyOf(tags);
        levels = List.copyOf(levels);
        effects = List.copyOf(effects);
    }

    /**
     * The namespaced unlockable id, {@code ability:<id>}.
     *
     * @return the id
     */
    public String unlockableId() {
        return NAMESPACE + id;
    }

    /**
     * Whether the ability carries a tag.
     *
     * @param tag the tag to look for
     * @return {@code true} when it is present
     */
    public boolean has(AbilityTag tag) {
        return tags.contains(tag);
    }
}
