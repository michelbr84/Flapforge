package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.List;
import java.util.Objects;

/**
 * A named difficulty curve (D20): the seam between {@code difficulty.json} and the simulation.
 *
 * @param id the curve id
 * @param entries the curve lines
 */
public record CurveSpec(String id, List<CurveEntry> entries) {

    /** Pure upstream: {@code MOVING_CHANCE = 0.05 + 0.05 × gates}, capped at 1. */
    public static final CurveSpec CLASSIC = new CurveSpec("classic", List.of(
            new CurveEntry(StatId.MOVING_CHANCE, StatOp.FLAT_ADD, 0.05, 0.05, 0, 1.0)));

    /** No difficulty change at all. */
    public static final CurveSpec FLAT = new CurveSpec("flat", List.of());

    /**
     * Copies the entries.
     *
     * @param id the curve id
     * @param entries the curve lines
     */
    public CurveSpec {
        Objects.requireNonNull(id, "id");
        entries = List.copyOf(entries);
    }
}
