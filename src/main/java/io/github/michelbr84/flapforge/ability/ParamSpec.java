package io.github.michelbr84.flapforge.ability;

import java.util.Objects;

/**
 * Declaration of one {@code AbilityLevelDef.params} entry a behaviour reads (D9, E19).
 *
 * <p>A behaviour that reads {@code params.get("invulnTicks")} must declare it here, because the
 * content validator checks the two halves against each other: every key a level declares must be
 * read by the behaviour, every required key must be present, every value must be inside
 * {@code [min, max]} and the column must follow {@link Trend} across the levels of the ability.
 * A typo in {@code abilities.json} is then a content error instead of a silently applied default.
 *
 * @param key the parameter name in {@code abilities.json}
 * @param min the lowest accepted value (inclusive)
 * @param max the highest accepted value (inclusive)
 * @param trend how the value may change from one level to the next
 * @param required whether every level must declare the key
 */
public record ParamSpec(String key, double min, double max, Trend trend, boolean required) {

    /** How a parameter column may move as the ability levels up. */
    public enum Trend {
        /** Never decreases (a bigger number is the better one). */
        UP,
        /** Never increases (a smaller number is the better one). */
        DOWN,
        /** Free: the number is a cadence or a switch, not a magnitude. */
        ANY
    }

    /**
     * Validates the components.
     *
     * @param key the parameter name
     * @param min the lower bound
     * @param max the upper bound
     * @param trend the level trend
     * @param required whether the key is mandatory
     */
    public ParamSpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(trend, "trend");
        if (min > max) {
            throw new IllegalArgumentException("min must not exceed max for '" + key + "'");
        }
    }

    /**
     * A required parameter that grows with the level.
     *
     * @param key the parameter name
     * @param min the lower bound
     * @param max the upper bound
     * @return the spec
     */
    public static ParamSpec up(String key, double min, double max) {
        return new ParamSpec(key, min, max, Trend.UP, true);
    }

    /**
     * A required parameter that shrinks with the level.
     *
     * @param key the parameter name
     * @param min the lower bound
     * @param max the upper bound
     * @return the spec
     */
    public static ParamSpec down(String key, double min, double max) {
        return new ParamSpec(key, min, max, Trend.DOWN, true);
    }

    /**
     * A required parameter with no monotonicity (cadences, where {@code 0} disables).
     *
     * @param key the parameter name
     * @param min the lower bound
     * @param max the upper bound
     * @return the spec
     */
    public static ParamSpec free(String key, double min, double max) {
        return new ParamSpec(key, min, max, Trend.ANY, true);
    }

    /**
     * Tells whether a value is inside the declared range.
     *
     * @param value the value to test
     * @return {@code true} when accepted
     */
    public boolean accepts(double value) {
        return value >= min && value <= max;
    }

    /**
     * Tells whether a step from one level to the next respects {@link #trend()}.
     *
     * @param previous the value at the lower level
     * @param next the value at the higher level
     * @return {@code true} when the step is allowed
     */
    public boolean accepts(double previous, double next) {
        switch (trend) {
            case UP:
                return next >= previous;
            case DOWN:
                return next <= previous;
            default:
                return true;
        }
    }
}
