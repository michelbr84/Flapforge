package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One step of a {@code patterns.json} pattern (§4, M7): an obstacle placed {@link #dx} px after
 * the previous column, with the parameters its kind reads.
 *
 * <p>{@link #params} is bound generically — numbers as {@link Long} or {@link Double}
 * ({@code LONG_OR_DOUBLE}), strings, booleans and nested objects as {@link Map} — because the
 * keys depend on the kind; {@code ObstacleParams} is the contract the validator checks them
 * against and the resolver that turns them into typed geometry.
 *
 * @param dx the distance from the previous column's left edge to this one, in px
 * @param kind the obstacle family
 * @param params the kind's parameters, as authored
 * @param scoring whether clearing the column awards a gate; {@code null} (absent) means
 *     {@code true}, the default of the schema
 */
public record PatternStepDef(int dx, ObstacleKind kind, Map<String, Object> params,
        Boolean scoring) {

    /**
     * Copies the parameter map and checks the required fields.
     *
     * @throws NullPointerException when the kind is missing
     */
    public PatternStepDef {
        Objects.requireNonNull(kind, "kind");
        params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /**
     * Whether the step scores — {@code scoring} with its default applied.
     *
     * @return {@code true} unless the step says {@code "scoring": false}
     */
    public boolean scores() {
        return scoring == null || scoring;
    }
}
