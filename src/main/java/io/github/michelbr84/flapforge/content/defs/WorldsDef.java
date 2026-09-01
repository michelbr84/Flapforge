package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * The root of {@code worlds.json}: the world list in file order, plus room for the file-level
 * {@code _comment} that records which fields land in which milestone (E19).
 *
 * @param worlds the worlds
 */
public record WorldsDef(List<WorldDef> worlds) {

    /**
     * Copies the list.
     */
    public WorldsDef {
        worlds = List.copyOf(worlds);
    }
}
