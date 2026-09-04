package io.github.michelbr84.flapforge.input;

/**
 * Immutable input event produced by the windowing bridge and consumed by {@link InputQueue}.
 *
 * <p>Coordinates are window (canvas) pixels; the screen manager maps them to logical playfield
 * coordinates through the viewport (E30.a). Key codes follow {@link Keys}.
 */
public sealed interface RawInput
        permits RawInput.KeyDown, RawInput.KeyUp, RawInput.MouseDown, RawInput.MouseUp,
                RawInput.MouseMove, RawInput.Wheel, RawInput.SystemEvent {

    /**
     * A key went down.
     *
     * @param code the key code
     * @param whenMs the toolkit timestamp in milliseconds
     */
    record KeyDown(int code, long whenMs) implements RawInput {
    }

    /**
     * A key went up.
     *
     * @param code the key code
     * @param whenMs the toolkit timestamp in milliseconds
     */
    record KeyUp(int code, long whenMs) implements RawInput {
    }

    /**
     * A mouse button went down.
     *
     * @param button the button id ({@link Keys#BUTTON_LEFT} ...)
     * @param x the window x coordinate
     * @param y the window y coordinate
     */
    record MouseDown(int button, int x, int y) implements RawInput {
    }

    /**
     * A mouse button went up.
     *
     * @param button the button id
     * @param x the window x coordinate
     * @param y the window y coordinate
     */
    record MouseUp(int button, int x, int y) implements RawInput {
    }

    /**
     * The pointer moved (with or without buttons held).
     *
     * @param x the window x coordinate
     * @param y the window y coordinate
     */
    record MouseMove(int x, int y) implements RawInput {
    }

    /**
     * The mouse wheel rotated.
     *
     * @param rotation the number of notches (negative = away from the user)
     */
    record Wheel(int rotation) implements RawInput {
    }

    /**
     * Events about the window rather than the pointer or keyboard. They are forwarded to the
     * screen manager in the order received.
     */
    sealed interface SystemEvent extends RawInput
            permits FocusLost, Iconified, Resized, CloseRequested, FullscreenToggled {
    }

    /**
     * The window lost keyboard focus; the queue synthesises releases for every held key and
     * remembers them as "ghosts" so a key still physically held when focus returns (its
     * auto-repeat resumes without a new physical press) is re-armed silently instead of
     * producing a second press edge.
     *
     * @param whenMs the toolkit timestamp in milliseconds, comparable with
     *     {@link KeyDown#whenMs()}, or {@link #UNKNOWN_WHEN} when the producer has none (no
     *     ghosts are kept in that case)
     */
    record FocusLost(long whenMs) implements SystemEvent {

        /** Timestamp meaning "unknown". */
        public static final long UNKNOWN_WHEN = -1L;

        /** Creates a focus-loss event without a timestamp. */
        public FocusLost() {
            this(UNKNOWN_WHEN);
        }
    }

    /**
     * The window was iconified or restored.
     *
     * @param iconified {@code true} when minimised
     */
    record Iconified(boolean iconified) implements SystemEvent {
    }

    /**
     * The canvas was resized.
     *
     * @param width the new width in window pixels
     * @param height the new height in window pixels
     */
    record Resized(int width, int height) implements SystemEvent {
    }

    /** The user asked to close the window. */
    record CloseRequested() implements SystemEvent {
    }

    /** A fullscreen toggle was requested programmatically (tests, external triggers). */
    record FullscreenToggled() implements SystemEvent {
    }
}
