package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.List;
import java.util.Objects;

/**
 * A world that changes its own rules while you fly through it (§4 {@code ruleCycles}, the Void,
 * M7): every {@link #everyGates} gates one {@link Option} is picked from the {@code cycles}
 * stream — never the one already active — announced for {@link #telegraphTicks} and then
 * applied, its flags joining the run's rules and its effects replacing the previous option's in
 * the {@code WORLD_CYCLE} layer.
 *
 * @param everyGates gates between shifts
 * @param telegraphTicks ticks the banner warns before the shift lands
 * @param options what a shift can pick, in authored order
 */
public record RuleCycleSpec(int everyGates, int telegraphTicks, List<Option> options) {

    /**
     * One thing a shift can turn into (E31.g): flags and effects, either possibly empty.
     *
     * @param flags the rule flags the option turns on
     * @param effects the stat modifiers the option pushes into {@code WORLD_CYCLE}
     */
    public record Option(RuleSet flags, List<StatModifier> effects) {

        /**
         * Copies the effects.
         *
         * @param flags the flags
         * @param effects the effects
         */
        public Option {
            Objects.requireNonNull(flags, "flags");
            effects = List.copyOf(effects);
        }
    }

    /**
     * Validates the components.
     *
     * @param everyGates the period in gates
     * @param telegraphTicks the warning length
     * @param options the options
     */
    public RuleCycleSpec {
        if (everyGates < 1) {
            throw new IllegalArgumentException("everyGates must be at least 1: " + everyGates);
        }
        if (telegraphTicks < 0) {
            throw new IllegalArgumentException(
                    "telegraphTicks must not be negative: " + telegraphTicks);
        }
        options = List.copyOf(options);
        if (options.isEmpty()) {
            throw new IllegalArgumentException("a rule cycle needs at least one option");
        }
    }
}
