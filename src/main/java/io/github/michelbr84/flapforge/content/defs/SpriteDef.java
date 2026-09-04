package io.github.michelbr84.flapforge.content.defs;

import java.util.Objects;

/**
 * Optional sprite override for a bird (D19): the asset id prefix of its animation frames and how
 * many ticks each frame lasts. Absent in M1 — every bird is drawn procedurally.
 *
 * @param frames the asset id prefix, for example {@code birds/guardian}
 * @param ticksPerFrame ticks each frame is shown
 */
public record SpriteDef(String frames, int ticksPerFrame) {

    /**
     * Validates the components.
     *
     * @param frames the asset id prefix
     * @param ticksPerFrame ticks per frame
     */
    public SpriteDef {
        Objects.requireNonNull(frames, "frames");
        if (ticksPerFrame <= 0) {
            throw new IllegalArgumentException("ticksPerFrame must be positive: " + ticksPerFrame);
        }
    }
}
