package io.github.michelbr84.flapforge.gameplay;

/**
 * Player intent for one simulation tick (D2, D9).
 *
 * @param flap {@code true} when a flap edge (or a synthetic flap) is requested this tick
 * @param ability {@code true} on the activation edge of the equipped active ability (X / Shift /
 *     right-click, D17); a held button never re-activates
 * @param autoFlapHeld {@code true} while hold-to-flap is engaged; the simulation issues a
 *     synthetic flap every {@link io.github.michelbr84.flapforge.core.Playfield#AUTO_FLAP_PERIOD_TICKS}
 *     ticks since the last flap while it is held
 */
public record SimInput(boolean flap, boolean ability, boolean autoFlapHeld) {

    /** No input. */
    public static final SimInput NONE = new SimInput(false, false, false);
    /** A single flap edge. */
    public static final SimInput FLAP = new SimInput(true, false, false);
    /** A single ability activation edge. */
    public static final SimInput ABILITY = new SimInput(false, true, false);

    /**
     * Intent without an ability edge.
     *
     * @param flap a flap edge this tick
     * @param autoFlapHeld hold-to-flap engaged
     */
    public SimInput(boolean flap, boolean autoFlapHeld) {
        this(flap, false, autoFlapHeld);
    }
}
