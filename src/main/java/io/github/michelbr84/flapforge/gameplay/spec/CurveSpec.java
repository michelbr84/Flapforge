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

    /**
     * The same curve started {@code gates} gates in: every entry's base moves to its value at
     * that gate and its slope and clamps stay, so gate {@code k} of the copy is gate
     * {@code gates + k} of the original. The balancing tool and the boss feasibility test use it
     * to play an encounter under the difficulty of {@code boss.atGate} without flying there
     * first ({@code RunSetup.startingAtBoss}, M8).
     *
     * @param gates the gates to skip; 0 returns an equal curve
     * @return the shifted curve, with {@code "<id>+<gates>"} as its id
     */
    public CurveSpec shiftedBy(int gates) {
        if (gates <= 0) {
            return this;
        }
        List<CurveEntry> shifted = new java.util.ArrayList<>(entries.size());
        for (CurveEntry e : entries) {
            shifted.add(new CurveEntry(e.stat(), e.op(), e.base() + e.perGate() * gates,
                    e.perGate(), e.min(), e.max()));
        }
        return new CurveSpec(id + "+" + gates, shifted);
    }
}
