package io.github.michelbr84.flapforge.content.defs;

/**
 * What an ability is about (D9). Tags are what rule flags strip:
 * {@code NO_DEFENSIVE_ABILITIES} removes every {@link #DEFENSIVE} ability, innate bird passives
 * included, and {@code NO_REVIVE} removes every {@link #REVIVE} one.
 */
public enum AbilityTag {

    /** Prevents or absorbs a lethal hit. */
    DEFENSIVE,
    /** Brings the bird back after a lethal hit. */
    REVIVE,
    /** Changes how the bird moves. */
    MOVEMENT,
    /** Changes the pace of the world. */
    TEMPO,
    /** Pays in coins or points. */
    ECONOMY
}
