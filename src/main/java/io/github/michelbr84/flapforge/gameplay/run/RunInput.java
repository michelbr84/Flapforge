package io.github.michelbr84.flapforge.gameplay.run;

/**
 * Player intent for one run tick (D11).
 *
 * @param flap a flap edge this tick
 * @param ability an ability activation edge this tick
 * @param choice the index of the chosen offer card, {@link #NO_CHOICE} while a draft is still
 *     waiting for an answer, or {@link #SKIP} to close it without taking anything
 * @param autoFlapHeld hold-to-flap engaged (D2); never set by bots
 */
public record RunInput(boolean flap, boolean ability, int choice, boolean autoFlapHeld) {

    /** No card chosen (the draft overlay is still open, or no draft is open at all). */
    public static final int NO_CHOICE = -1;
    /** Close the open draft without taking a card (D11: skipping is always allowed). */
    public static final int SKIP = -2;
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

    /**
     * Input closing an open draft without taking a card.
     *
     * @return the input
     */
    public static RunInput skip() {
        return new RunInput(false, false, SKIP, false);
    }
}
