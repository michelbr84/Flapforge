package io.github.michelbr84.flapforge.content.defs;

/**
 * How an ability is used (D9). A loadout holds one {@link #ACTIVE} ability plus
 * {@code BirdDef.passiveSlots} {@link #PASSIVE} ones.
 */
public enum AbilityKind {

    /** Triggered by the player (X / Shift / right-click), with a cooldown and a duration. */
    ACTIVE,
    /** Always on while equipped; contributes its effects for the whole run. */
    PASSIVE
}
