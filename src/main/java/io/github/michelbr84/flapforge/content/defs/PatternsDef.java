package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * The root of {@code patterns.json} (M7): the pattern list in file order, plus room for the
 * file-level {@code _comment}.
 *
 * @param patterns the patterns
 */
public record PatternsDef(List<PatternDef> patterns) {

    /**
     * Copies the list.
     */
    public PatternsDef {
        patterns = List.copyOf(patterns);
    }
}
