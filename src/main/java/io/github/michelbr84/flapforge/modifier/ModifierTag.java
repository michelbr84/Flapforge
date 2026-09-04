package io.github.michelbr84.flapforge.modifier;

/**
 * What a run modifier is <em>about</em> (D27, E31.d). Tags are the only input of the synergy
 * rules: {@code modifiers.json.synergies[].requiresTags} is a multiset over this enum, and
 * {@link SynergyResolver} matches it against the tags of the modifiers a run has taken.
 *
 * <p>They are therefore part of the balance surface, not decoration: adding a tag to a shipped
 * modifier changes which set bonuses it can complete.
 */
public enum ModifierTag {
    /** Coins, coin spawn rate, magnets — anything that pays. */
    ECONOMY,
    /** Faster scrolling, and the score that comes with it. */
    SPEED,
    /** Shields, revives, anything that survives a mistake. */
    DEFENSE,
    /** Smaller hitbox, wider gaps, clean-gate rewards. */
    PRECISION,
    /** Ability cooldowns and durations, slower obstacles. */
    TEMPO,
    /** Buys power by making the run more dangerous. */
    RISK,
    /** Buys power by making the run pay more. */
    GREED
}
