package io.github.michelbr84.flapforge.input;

/**
 * Logical input actions the game reacts to. Physical keys are mapped to actions by
 * {@link KeyBindings}; mouse buttons are fixed (left = {@link #FLAP}, right = {@link #ABILITY}).
 */
public enum InputAction {
    /** Flap (jump). */
    FLAP,
    /** Activate the equipped active ability. */
    ABILITY,
    /** Pause or resume a run. */
    PAUSE,
    /** Confirm the focused UI element. */
    CONFIRM,
    /** Go back / cancel. */
    BACK,
    /** Move focus up. */
    UP,
    /** Move focus down. */
    DOWN,
    /** Move focus left. */
    LEFT,
    /** Move focus right. */
    RIGHT,
    /** Toggle audio mute. */
    MUTE,
    /** Toggle the debug overlay. */
    DEBUG,
    /** Toggle borderless fullscreen. */
    FULLSCREEN
}
