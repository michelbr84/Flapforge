package io.github.michelbr84.flapforge.gameplay.obstacle;

import java.util.List;
import java.util.Objects;

/**
 * Declaration of one {@code patterns.json} step parameter an obstacle kind reads (§4, D10).
 *
 * <p>This is deliberately not {@code ability.ParamSpec}: obstacle parameters have enum values
 * ({@code layout}, {@code side}), a nested object ({@code gear.rail}), the {@code "random"}
 * sentinel on {@code gapCenter}, optional keys with defaults and no level trend, none of which the
 * ability record models (it is numeric-only with a monotonic trend across levels).
 *
 * @param key the parameter name
 * @param type the value shape
 * @param min the lowest accepted number (numeric types)
 * @param max the highest accepted number (numeric types)
 * @param required whether the key must be present
 * @param allowed the accepted names of an {@link Type#ENUM}
 * @param allowsRandom whether the string {@code "random"} is accepted instead of a number
 * @param children the specs of an {@link Type#OBJECT}'s fields
 */
public record ObstacleParamSpec(String key, Type type, double min, double max, boolean required,
        List<String> allowed, boolean allowsRandom, List<ObstacleParamSpec> children) {

    /** The shape of a parameter value. */
    public enum Type {
        /** Any number inside {@code [min, max]}. */
        NUMBER,
        /** A whole number inside {@code [min, max]} (tick counts). */
        INTEGER,
        /** One of {@link ObstacleParamSpec#allowed()}. */
        ENUM,
        /** {@code true} or {@code false}. */
        BOOLEAN,
        /** A nested object described by {@link ObstacleParamSpec#children()}. */
        OBJECT
    }

    /**
     * Validates the components.
     *
     * @param key the parameter name
     * @param type the value shape
     * @param min the lower bound
     * @param max the upper bound
     * @param required whether the key is mandatory
     * @param allowed the enum names
     * @param allowsRandom whether {@code "random"} is accepted
     * @param children the nested specs
     */
    public ObstacleParamSpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        if (min > max) {
            throw new IllegalArgumentException("min must not exceed max for '" + key + "'");
        }
        allowed = List.copyOf(allowed);
        children = List.copyOf(children);
    }

    /**
     * A numeric parameter.
     *
     * @param key the name
     * @param min the lower bound
     * @param max the upper bound
     * @param required whether it is mandatory
     * @return the spec
     */
    public static ObstacleParamSpec number(String key, double min, double max, boolean required) {
        return new ObstacleParamSpec(key, Type.NUMBER, min, max, required, List.of(), false,
                List.of());
    }

    /**
     * A whole-number parameter (ticks, offsets).
     *
     * @param key the name
     * @param min the lower bound
     * @param max the upper bound
     * @param required whether it is mandatory
     * @return the spec
     */
    public static ObstacleParamSpec integer(String key, double min, double max, boolean required) {
        return new ObstacleParamSpec(key, Type.INTEGER, min, max, required, List.of(), false,
                List.of());
    }

    /**
     * A numeric parameter that also accepts {@code "random"}.
     *
     * @param key the name
     * @param min the lower bound
     * @param max the upper bound
     * @param required whether it is mandatory
     * @return the spec
     */
    public static ObstacleParamSpec numberOrRandom(String key, double min, double max,
            boolean required) {
        return new ObstacleParamSpec(key, Type.NUMBER, min, max, required, List.of(), true,
                List.of());
    }

    /**
     * An enum-valued parameter.
     *
     * @param key the name
     * @param allowed the accepted names
     * @param required whether it is mandatory
     * @return the spec
     */
    public static ObstacleParamSpec enumOf(String key, List<String> allowed, boolean required) {
        return new ObstacleParamSpec(key, Type.ENUM, 0, 0, required, allowed, false, List.of());
    }

    /**
     * An optional boolean parameter.
     *
     * @param key the name
     * @return the spec
     */
    public static ObstacleParamSpec bool(String key) {
        return new ObstacleParamSpec(key, Type.BOOLEAN, 0, 0, false, List.of(), false, List.of());
    }

    /**
     * A nested object parameter.
     *
     * @param key the name
     * @param children the field specs
     * @param required whether it is mandatory
     * @return the spec
     */
    public static ObstacleParamSpec object(String key, List<ObstacleParamSpec> children,
            boolean required) {
        return new ObstacleParamSpec(key, Type.OBJECT, 0, 0, required, List.of(), false,
                children);
    }

    /**
     * Tells whether a number is inside the declared range.
     *
     * @param value the value
     * @return {@code true} when accepted
     */
    public boolean accepts(double value) {
        return value >= min && value <= max;
    }
}
