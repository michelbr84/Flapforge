package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * The root of {@code abilities.json}: the ability list in file order, plus room for the file-level
 * {@code _comment} that records which fields land in which milestone (E19).
 *
 * @param abilities the abilitys
 */
public record AbilitiesDef(List<AbilityDef> abilities) {

    /**
     * Copies the list.
     */
    public AbilitiesDef {
        abilities = List.copyOf(abilities);
    }
}
