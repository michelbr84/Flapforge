package io.github.michelbr84.flapforge.content.defs;

/**
 * What an upgrade node hands over when it is bought (E31.f).
 *
 * <p>Only {@link #UNLOCK} is an edge of the unlock graph; the other two raise a counter on the
 * profile and are bounded by the E3 cap rules in {@code ContentValidator}.
 */
public enum GrantType {

    /** Adds a namespaced unlockable id to {@code profile.unlocked}. */
    UNLOCK,
    /** Raises {@code profile.abilityLevelCap} by {@code amount} (E3: exactly one ships). */
    ABILITY_CAP,
    /** Raises {@code profile.passiveSlotBonus} by {@code amount} (E3: at most +1). */
    PASSIVE_SLOT
}
