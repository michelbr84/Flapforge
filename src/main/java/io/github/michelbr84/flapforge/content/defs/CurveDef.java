package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.spec.CurveEntry;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A named difficulty curve (D20). In {@code difficulty.json} the curves are a
 * {@code Map&lt;String, List&lt;CurveEntryDef&gt;&gt;}; this record pairs a map entry with its
 * key so the curves can live in an ordered {@link io.github.michelbr84.flapforge.content.Registry}
 * like every other kind of content.
 *
 * @param id the curve id (the map key)
 * @param entries the curve lines in file order
 */
public record CurveDef(String id, List<CurveEntryDef> entries) {

    /**
     * Copies the entries.
     *
     * @param id the curve id
     * @param entries the curve lines
     */
    public CurveDef {
        Objects.requireNonNull(id, "id");
        entries = List.copyOf(entries);
    }

    /**
     * The simulation seam record.
     *
     * @return the curve spec
     */
    public CurveSpec toSpec() {
        List<CurveEntry> out = new ArrayList<>(entries.size());
        for (CurveEntryDef e : entries) {
            out.add(e.toEntry());
        }
        return new CurveSpec(id, out);
    }
}
