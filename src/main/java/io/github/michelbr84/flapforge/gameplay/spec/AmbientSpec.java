package io.github.michelbr84.flapforge.gameplay.spec;

/**
 * The atmosphere of a world as the simulation sees it (§4 {@code ambient}, E8, M7).
 *
 * <p>The wind is permanent and everywhere: {@code windX} is a change of the relative scroll speed
 * and {@code windY} a vertical acceleration on the bird, exactly the two pushes a
 * {@code WindZone} applies while the bird is inside it — the ambient wind is the zone that never
 * ends. {@code darkness} is read by the renderer and by nothing in the simulation.
 * {@code lightningEveryGates} is cosmetic (E8): every that many gates the simulation announces a
 * sky flash and nothing else — no hitbox, no stream, no rule.
 *
 * @param darkness how much of the playfield the renderer hides, {@code [0, 1]}
 * @param windX change of the relative scroll speed in px/s; negative slows the scroll (the air
 *     pushes the bird back), positive speeds it up
 * @param windY vertical acceleration on the bird in px/s², positive pushes it down
 * @param lightningEveryGates gates between cosmetic sky flashes; {@code 0} disables them
 */
public record AmbientSpec(double darkness, double windX, double windY, int lightningEveryGates) {

    /** Still air, full daylight, no flashes: Green Fields and every seam without a world. */
    public static final AmbientSpec NONE = new AmbientSpec(0, 0, 0, 0);

    /**
     * Validates the ranges.
     *
     * @param darkness the darkness
     * @param windX the scroll change
     * @param windY the vertical acceleration
     * @param lightningEveryGates the flash period
     */
    public AmbientSpec {
        if (darkness < 0 || darkness > 1) {
            throw new IllegalArgumentException("darkness must be in [0, 1]: " + darkness);
        }
        if (lightningEveryGates < 0) {
            throw new IllegalArgumentException(
                    "lightningEveryGates must not be negative: " + lightningEveryGates);
        }
        if (Double.isNaN(windX) || Double.isNaN(windY)) {
            throw new IllegalArgumentException("wind must be a number");
        }
    }

    /**
     * Whether the air pushes the bird at all.
     *
     * @return {@code true} when either wind component is non-zero
     */
    public boolean hasWind() {
        return windX != 0 || windY != 0;
    }

    /**
     * Whether the sky flashes.
     *
     * @return {@code true} when {@code lightningEveryGates > 0}
     */
    public boolean hasFlashes() {
        return lightningEveryGates > 0;
    }

    /**
     * Whether this ambience changes anything at all — the wind, the flashes or the darkness.
     *
     * @return {@code false} for {@link #NONE} and anything equal to it
     */
    public boolean isActive() {
        return hasWind() || hasFlashes() || darkness > 0;
    }
}
