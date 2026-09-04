package io.github.michelbr84.flapforge.gameplay.stats;

/**
 * Boolean rules that change how a run behaves (D8, D9). Flags are unioned from every source
 * (run config, world, tier, challenge, modifiers, rule cycles); two of them zero a stat.
 */
public enum RuleFlag {
    /** Strips defensive abilities and zeroes {@link StatId#SHIELD_CHARGES}. */
    NO_DEFENSIVE_ABILITIES,
    /** Strips revive abilities and zeroes {@link StatId#REVIVES}. */
    NO_REVIVE,
    /** Every obstacle uses its moving variant. */
    ALL_OBSTACLES_MOVE,
    /** Touching the top edge of the playfield is lethal. */
    LETHAL_CEILING,
    /** No coins spawn. */
    NO_COINS,
    /** Scroll speed grows with ticks alive (E32.b). */
    SPEED_RAMP
}
