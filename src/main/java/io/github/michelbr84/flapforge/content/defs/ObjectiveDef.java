package io.github.michelbr84.flapforge.content.defs;

import java.util.Objects;

/**
 * The goal of a challenge (§4): a type and the number it needs.
 *
 * @param type what to do
 * @param value how much of it; {@code 1} for {@link ObjectiveType#BOSS_CLEARED}
 */
public record ObjectiveDef(ObjectiveType type, long value) {

    /**
     * Checks the shape.
     *
     * @throws NullPointerException when the type is missing
     * @throws IllegalArgumentException when the value is not positive
     */
    public ObjectiveDef {
        Objects.requireNonNull(type, "type");
        if (value < 1) {
            throw new IllegalArgumentException("objective.value must be at least 1: " + value);
        }
    }
}
