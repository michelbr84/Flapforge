package io.github.michelbr84.flapforge.gameplay.stats;

/**
 * Every tunable gameplay stat with its default value and hard clamp range (D8, E2).
 *
 * <p>Physics stats are in logical pixels and seconds (60 Hz ticks integrate them); multipliers
 * are dimensionless. The defaults are what the classic bird resolves to when no modifier is
 * active; the clamps bound the resolved value after every layer has been applied.
 */
public enum StatId {
    /** Downward acceleration in px/s². */
    GRAVITY(1800, 600, 4000),
    /** Upward speed set by a flap in px/s (a flap sets, never adds). */
    FLAP_VELOCITY(405, 200, 800),
    /** Maximum downward speed in px/s. */
    MAX_FALL_SPEED(1500, 300, 2000),
    /** Horizontal scroll speed of the world in px/s. */
    SCROLL_SPEED(120, 60, 360),
    /** Vertical size of a gate gap in px. */
    GAP_SIZE(128, 72, 220),
    /** Horizontal distance between consecutive gates in px. */
    GATE_INTERVAL(160, 120, 320),
    /** Scale of the bird hitbox about its centre. */
    HITBOX_SCALE(1, 0.5, 1.5),
    /** Points awarded per gate. */
    SCORE_MULT(1, 0.1, 5),
    /** Multiplier on coin rewards. */
    COIN_MULT(1, 0, 5),
    /** Multiplier on XP rewards. */
    XP_MULT(1, 0, 5),
    /** Expected coins per scoring gate (E2). */
    COIN_SPAWN_RATE(0.5, 0, 5),
    /** Radius in px within which coins are attracted to the bird. */
    MAGNET_RADIUS(0, 0, 200),
    /** Multiplier on ability cooldowns. */
    ABILITY_COOLDOWN_MULT(1, 0.25, 3),
    /** Multiplier on ability durations. */
    ABILITY_DURATION_MULT(1, 0.25, 3),
    /** Shield charges available at run start. */
    SHIELD_CHARGES(0, 0, 5),
    /** Revives available at run start. */
    REVIVES(0, 0, 2),
    /** Probability that a spawned obstacle moves. */
    MOVING_CHANCE(0, 0, 1),
    /** Speed of moving obstacles in px/s. */
    OSCILLATION_SPEED(30, 0, 120),
    /** Scale applied to the world clock (scroll, obstacle phases), never to the bird. */
    TIME_SCALE(1, 0.25, 2);

    /** Number of stats (size of the resolved value array). */
    public static final int COUNT = values().length;

    private final double defaultValue;
    private final double min;
    private final double max;

    StatId(double defaultValue, double min, double max) {
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
    }

    /**
     * Value used when no base stat is supplied.
     *
     * @return the default
     */
    public double defaultValue() {
        return defaultValue;
    }

    /**
     * Lower clamp of the resolved value.
     *
     * @return the minimum
     */
    public double min() {
        return min;
    }

    /**
     * Upper clamp of the resolved value.
     *
     * @return the maximum
     */
    public double max() {
        return max;
    }

    /**
     * Clamps a resolved value into this stat's range.
     *
     * @param value the raw value
     * @return the clamped value
     */
    public double clamp(double value) {
        return value < min ? min : (value > max ? max : value);
    }
}
