package io.github.michelbr84.flapforge.gameplay;

/**
 * Player intent for one simulation tick (D2).
 *
 * @param flap {@code true} when a flap edge (or a synthetic flap) is requested this tick
 * @param autoFlapHeld {@code true} while hold-to-flap is engaged; the simulation issues a
 *     synthetic flap every {@link io.github.michelbr84.flapforge.core.Playfield#AUTO_FLAP_PERIOD_TICKS}
 *     ticks since the last flap while it is held
 */
public record SimInput(boolean flap, boolean autoFlapHeld) {

    /** No input. */
    public static final SimInput NONE = new SimInput(false, false);
    /** A single flap edge. */
    public static final SimInput FLAP = new SimInput(true, false);
}
