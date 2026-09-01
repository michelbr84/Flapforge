package io.github.michelbr84.flapforge.gameplay.run;

/**
 * Player intent for one run tick (D11).
 *
 * @param flap a flap edge this tick
 * @param ability an ability activation edge this tick
 * @param choice the index of the chosen offer card, or {@link #NO_CHOICE}
 * @param autoFlapHeld hold-to-flap engaged (D2); never set by bots
 */
public record RunInput(boolean flap, boolean ability, int choice, boolean autoFlapHeld) {

    /** No card chosen. */
    public static final int NO_CHOICE = -1;
    /** No input. */
    public static final RunInput NONE = new RunInput(false, false, NO_CHOICE, false);
    /** A single flap edge. */
    public static final RunInput FLAP = new RunInput(true, false, NO_CHOICE, false);
    /** Hold-to-flap engaged without an edge. */
    public static final RunInput AUTO_FLAP = new RunInput(false, false, NO_CHOICE, true);

    /**
     * Input choosing an offer card.
     *
     * @param index the card index
     * @return the input
     */
    public static RunInput choose(int index) {
        return new RunInput(false, false, index, false);
    }
}
