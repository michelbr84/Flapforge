package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import java.util.List;

/**
 * One option a world rule cycle can shift into (E31.g): a set of flags and a set of stat
 * modifiers that go into the {@code WORLD_CYCLE} layer. Authored for the Void in M7.
 *
 * @param flags the rule flags the option turns on
 * @param effects the stat modifiers the option applies
 */
public record RuleCycleOptionDef(List<RuleFlag> flags, List<StatModifierDef> effects) {

    /**
     * Copies both lists.
     */
    public RuleCycleOptionDef {
        flags = List.copyOf(flags);
        effects = List.copyOf(effects);
    }
}
