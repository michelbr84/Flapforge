package io.github.michelbr84.flapforge.gameplay.collision;

/** What killed the bird (D7); also the {@code deathCause} of a run. */
public enum CollisionCause {
    /** A lethal obstacle hitbox. */
    OBSTACLE,
    /** The ground line ({@code y ≥ GROUND_DEATH_Y}). */
    GROUND,
    /** The top edge, lethal only under {@code LETHAL_CEILING}. */
    CEILING
}
