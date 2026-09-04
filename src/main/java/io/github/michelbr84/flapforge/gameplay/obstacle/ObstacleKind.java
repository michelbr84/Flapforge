package io.github.michelbr84.flapforge.gameplay.obstacle;

/**
 * Obstacle families (D6). Only {@link #PIPE_GATE} exists in M1; the other constants are reserved
 * so content, spawn tables and facts can name them before their classes land in M7.
 */
public enum ObstacleKind {
    /** A pair of pipe segments with a gap (standard or floating layout). */
    PIPE_GATE,
    /** A rotating gear (circle hitbox), optionally on a vertical rail. */
    GEAR,
    /** A piston extending from the top or bottom edge after a telegraph. */
    PISTON,
    /** A non-lethal zone pushing the bird. */
    WIND_ZONE,
    /** A partial-height lightning bolt with a warning. */
    LIGHTNING
}
