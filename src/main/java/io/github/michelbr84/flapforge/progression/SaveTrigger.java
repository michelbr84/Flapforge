package io.github.michelbr84.flapforge.progression;

/**
 * The "save now" step of a write trigger (D15): a purchase and a selection change are written to
 * the disk immediately, not at the end of the next run.
 *
 * <p>It is a seam rather than a direct call because {@code progression} must stay free of
 * {@code persistence} plumbing and of the save thread: the application wires this to
 * {@code GameContext.saveProfile()}, a test wires it to a counter, and a tool wires it to nothing.
 * A trigger never reports failure — a failed write leaves the profile dirty, and the autosave and
 * the exit save try again (D15).
 */
@FunctionalInterface
public interface SaveTrigger {

    /** Writes nothing: the default for tools and for tests that do not care about the disk. */
    SaveTrigger NONE = () -> {
    };

    /** Asks the owner of the save file to write the profile now. */
    void saveNow();
}
