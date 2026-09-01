package io.github.michelbr84.flapforge.content.defs;

/**
 * The atmosphere of a world (§4): how dark it is, how hard the air pushes and how often the sky
 * flashes. The flash is cosmetic and honours {@code settings.reduceFlashing} (E8); lethal bolts
 * come from the {@code lightning} spawn kind, never from here. Consumed from M7.
 *
 * @param darkness how much of the playfield is hidden, {@code [0,1]}
 * @param windX horizontal acceleration in px/s², negative pushes the bird back
 * @param windY vertical acceleration in px/s², positive pushes the bird down
 * @param lightningEveryGates gates between cosmetic sky flashes; {@code 0} disables them
 */
public record AmbientDef(double darkness, double windX, double windY, int lightningEveryGates) {

    /** Still air, full daylight, no flashes. */
    public static final AmbientDef NONE = new AmbientDef(0, 0, 0, 0);

    /**
     * Checks the ranges.
     *
     * @throws IllegalArgumentException when darkness is outside {@code [0,1]} or the flash period
     *     is negative
     */
    public AmbientDef {
        if (darkness < 0 || darkness > 1) {
            throw new IllegalArgumentException("ambient.darkness must be in [0,1]: " + darkness);
        }
        if (lightningEveryGates < 0) {
            throw new IllegalArgumentException(
                    "ambient.lightningEveryGates must not be negative: " + lightningEveryGates);
        }
    }
}
