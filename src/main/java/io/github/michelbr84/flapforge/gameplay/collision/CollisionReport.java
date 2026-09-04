package io.github.michelbr84.flapforge.gameplay.collision;

import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;

/**
 * Result of one {@link CollisionSystem#test} call (D7).
 *
 * @param lethalHit {@code true} when the bird died this tick
 * @param nearMiss {@code true} when the inflated bird box overlapped a lethal hitbox while no
 *     lethal hit happened
 * @param cause what was hit, or {@code null} when nothing lethal was hit
 * @param obstacle the obstacle involved (lethal hit or near miss), or {@code null} for the ground,
 *     the ceiling or no contact
 */
public record CollisionReport(boolean lethalHit, boolean nearMiss, CollisionCause cause,
        Obstacle obstacle) {

    /** No contact at all. */
    public static final CollisionReport NONE = new CollisionReport(false, false, null, null);

    /**
     * Builds a lethal report.
     *
     * @param cause the cause
     * @param obstacle the obstacle, or {@code null}
     * @return the report
     */
    public static CollisionReport lethal(CollisionCause cause, Obstacle obstacle) {
        return new CollisionReport(true, false, cause, obstacle);
    }

    /**
     * Builds a near-miss report.
     *
     * @param obstacle the obstacle grazed
     * @return the report
     */
    public static CollisionReport nearMiss(Obstacle obstacle) {
        return new CollisionReport(false, true, null, obstacle);
    }
}
