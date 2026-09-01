package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * A world that changes its own rules while you fly through it (§4, the Void): every
 * {@link #everyGates} gates one of the {@link #options} is picked, announced for
 * {@link #telegraphTicks} and applied. Authored and consumed in M7.
 *
 * @param everyGates gates between rule shifts
 * @param telegraphTicks how long the banner warns before the shift lands
 * @param options the options the shift picks from
 */
public record RuleCyclesDef(int everyGates, int telegraphTicks, List<RuleCycleOptionDef> options) {

    /**
     * Copies the option list and checks the ranges.
     *
     * @throws IllegalArgumentException when a period is out of range
     */
    public RuleCyclesDef {
        if (everyGates < 1) {
            throw new IllegalArgumentException(
                    "ruleCycles.everyGates must be at least 1: " + everyGates);
        }
        if (telegraphTicks < 0) {
            throw new IllegalArgumentException(
                    "ruleCycles.telegraphTicks must not be negative: " + telegraphTicks);
        }
        options = List.copyOf(options);
    }
}
