package io.github.michelbr84.flapforge.input;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the game needs to know about input for one simulation tick (D2, E29).
 *
 * <p>Instances are immutable. Edges ({@code justPressed}/{@code justReleased}) are computed by
 * {@link InputQueue} from per-key state so a tap shorter than a tick is never lost and keyboard
 * auto-repeat never produces a second edge. {@link #pressCount(InputAction)} counts multiple
 * press edges that landed in the same tick. Mouse coordinates are window pixels when produced by
 * the queue; {@link #withMouse(double, double)} rebases them to logical coordinates.
 */
public final class InputFrame {

    /** A frame with no input at all. */
    public static final InputFrame EMPTY = new InputFrame(new int[InputAction.values().length],
            EnumSet.noneOf(InputAction.class), EnumSet.noneOf(InputAction.class), 0, 0,
            0, 0, 0, 0, List.of(), List.of());

    private final int[] pressCounts;
    private final Set<InputAction> justPressed;
    private final Set<InputAction> held;
    private final Set<InputAction> justReleased;
    private final double mouseX;
    private final double mouseY;
    private final int mouseJustPressedMask;
    private final int mouseHeldMask;
    private final int mouseJustReleasedMask;
    private final int wheel;
    private final List<Integer> rawKeyDowns;
    private final List<RawInput.SystemEvent> systemEvents;

    /**
     * Creates a frame. The queue is the only intended caller; tests may build frames directly.
     *
     * @param pressCounts press edges per action ordinal
     * @param held actions held at the end of the tick
     * @param justReleased actions released during the tick
     * @param mouseX pointer x
     * @param mouseY pointer y
     * @param mouseJustPressedMask bit {@code 1 << button} for buttons pressed this tick
     * @param mouseHeldMask bit {@code 1 << button} for buttons held at the end of the tick
     * @param mouseJustReleasedMask bit {@code 1 << button} for buttons released this tick
     * @param wheel total wheel rotation this tick
     * @param rawKeyDowns key codes with a down edge this tick (for key capture)
     * @param systemEvents window events in arrival order
     */
    public InputFrame(int[] pressCounts, EnumSet<InputAction> held, EnumSet<InputAction> justReleased,
            double mouseX, double mouseY, int mouseJustPressedMask, int mouseHeldMask,
            int mouseJustReleasedMask, int wheel, List<Integer> rawKeyDowns,
            List<RawInput.SystemEvent> systemEvents) {
        this.pressCounts = pressCounts.clone();
        EnumSet<InputAction> pressed = EnumSet.noneOf(InputAction.class);
        for (InputAction a : InputAction.values()) {
            if (this.pressCounts[a.ordinal()] > 0) {
                pressed.add(a);
            }
        }
        this.justPressed = Collections.unmodifiableSet(pressed);
        this.held = Collections.unmodifiableSet(EnumSet.copyOf(held));
        this.justReleased = Collections.unmodifiableSet(EnumSet.copyOf(justReleased));
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.mouseJustPressedMask = mouseJustPressedMask;
        this.mouseHeldMask = mouseHeldMask;
        this.mouseJustReleasedMask = mouseJustReleasedMask;
        this.wheel = wheel;
        this.rawKeyDowns = List.copyOf(rawKeyDowns);
        this.systemEvents = List.copyOf(systemEvents);
    }

    private InputFrame(InputFrame base, double mouseX, double mouseY, int[] pressCounts,
            Set<InputAction> justReleasedView) {
        this.pressCounts = pressCounts;
        EnumSet<InputAction> pressed = EnumSet.noneOf(InputAction.class);
        for (InputAction a : InputAction.values()) {
            if (pressCounts[a.ordinal()] > 0) {
                pressed.add(a);
            }
        }
        this.justPressed = Collections.unmodifiableSet(pressed);
        this.held = base.held;
        this.justReleased = justReleasedView;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.mouseJustPressedMask = base.mouseJustPressedMask;
        this.mouseHeldMask = base.mouseHeldMask;
        this.mouseJustReleasedMask = base.mouseJustReleasedMask;
        this.wheel = base.wheel;
        this.rawKeyDowns = base.rawKeyDowns;
        this.systemEvents = base.systemEvents;
    }

    /**
     * Actions with at least one press edge this tick.
     *
     * @return an unmodifiable set
     */
    public Set<InputAction> justPressed() {
        return justPressed;
    }

    /**
     * Actions held at the end of this tick.
     *
     * @return an unmodifiable set
     */
    public Set<InputAction> held() {
        return held;
    }

    /**
     * Actions released this tick.
     *
     * @return an unmodifiable set
     */
    public Set<InputAction> justReleased() {
        return justReleased;
    }

    /**
     * Tells whether an action has a press edge this tick.
     *
     * @param action the action
     * @return {@code true} when pressed
     */
    public boolean isJustPressed(InputAction action) {
        return pressCounts[action.ordinal()] > 0;
    }

    /**
     * Tells whether an action is held.
     *
     * @param action the action
     * @return {@code true} when held
     */
    public boolean isHeld(InputAction action) {
        return held.contains(action);
    }

    /**
     * Tells whether an action was released this tick.
     *
     * @param action the action
     * @return {@code true} when released
     */
    public boolean isJustReleased(InputAction action) {
        return justReleased.contains(action);
    }

    /**
     * Number of press edges of an action in this tick (a quick tap-tap inside one tick counts 2).
     *
     * @param action the action
     * @return the count
     */
    public int pressCount(InputAction action) {
        return pressCounts[action.ordinal()];
    }

    /**
     * Pointer x (window pixels from the queue, logical after {@link #withMouse}).
     *
     * @return the x coordinate
     */
    public double mouseX() {
        return mouseX;
    }

    /**
     * Pointer y (window pixels from the queue, logical after {@link #withMouse}).
     *
     * @return the y coordinate
     */
    public double mouseY() {
        return mouseY;
    }

    /**
     * Tells whether a mouse button went down this tick.
     *
     * @param button the button id ({@link Keys#BUTTON_LEFT} ...)
     * @return {@code true} when pressed
     */
    public boolean isMouseJustPressed(int button) {
        return (mouseJustPressedMask & (1 << button)) != 0;
    }

    /**
     * Tells whether a mouse button is held.
     *
     * @param button the button id
     * @return {@code true} when held
     */
    public boolean isMouseHeld(int button) {
        return (mouseHeldMask & (1 << button)) != 0;
    }

    /**
     * Tells whether a mouse button went up this tick.
     *
     * @param button the button id
     * @return {@code true} when released
     */
    public boolean isMouseJustReleased(int button) {
        return (mouseJustReleasedMask & (1 << button)) != 0;
    }

    /**
     * Total wheel rotation this tick (negative = away from the user).
     *
     * @return the rotation in notches
     */
    public int wheel() {
        return wheel;
    }

    /**
     * Key codes that went down this tick, for key-capture UI (E29).
     *
     * @return an unmodifiable list in arrival order
     */
    public List<Integer> rawKeyDowns() {
        return rawKeyDowns;
    }

    /**
     * Window events received this tick, in arrival order.
     *
     * @return an unmodifiable list
     */
    public List<RawInput.SystemEvent> systemEvents() {
        return systemEvents;
    }

    /**
     * Tells whether a system event of the given type arrived this tick.
     *
     * @param type the event class
     * @return {@code true} when present
     */
    public boolean hasSystemEvent(Class<? extends RawInput.SystemEvent> type) {
        for (RawInput.SystemEvent e : systemEvents) {
            if (type.isInstance(e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether any keyboard or mouse edge happened this tick.
     *
     * @return {@code true} when there is at least one press or release
     */
    public boolean hasEdges() {
        return !justPressed.isEmpty() || !justReleased.isEmpty() || mouseJustPressedMask != 0
                || mouseJustReleasedMask != 0;
    }

    /**
     * Returns a copy with the pointer position replaced (used to map window to logical).
     *
     * @param x the new x
     * @param y the new y
     * @return the copy
     */
    public InputFrame withMouse(double x, double y) {
        return new InputFrame(this, x, y, pressCounts, justReleased);
    }

    /**
     * Returns a copy without the press edges of the given actions (held state is kept).
     *
     * @param actions the actions whose presses are suppressed
     * @return the copy
     */
    public InputFrame withoutPresses(Set<InputAction> actions) {
        int[] counts = pressCounts.clone();
        for (InputAction a : actions) {
            counts[a.ordinal()] = 0;
        }
        return new InputFrame(this, mouseX, mouseY, counts, justReleased);
    }

    /**
     * Returns a copy without any keyboard press or release edge (held state and mouse kept).
     *
     * @return the copy
     */
    public InputFrame withoutKeyEdges() {
        return new InputFrame(this, mouseX, mouseY, new int[pressCounts.length],
                Collections.emptySet());
    }

    @Override
    public String toString() {
        return "InputFrame{pressed=" + justPressed + ", held=" + held + ", released=" + justReleased
                + ", mouse=(" + mouseX + "," + mouseY + "), system=" + systemEvents + '}';
    }
}
