package io.github.michelbr84.flapforge.input;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded queue of {@link RawInput} events crossing from the toolkit thread to the game loop
 * (D2, E29).
 *
 * <p>Producers (the event-dispatch thread) call {@link #offer(RawInput)} from any thread; the
 * single consumer (the loop thread) calls {@link #nextTick()} once per simulation tick to drain
 * the queue into an immutable {@link InputFrame}. The queue owns the per-key held state, so:
 * <ul>
 *   <li>a {@code KeyDown} of a key already held is ignored (keyboard auto-repeat);</li>
 *   <li>a press and release inside the same tick still yield a press edge;</li>
 *   <li>a {@code KeyUp} immediately followed by a {@code KeyDown} of the same key with the same
 *       timestamp (auto-repeat as delivered by some X servers, E30.e) is dropped as a pair; when
 *       the pair straddles a tick boundary the {@code KeyDown} re-arms the key silently instead
 *       of producing a second press edge;</li>
 *   <li>{@code FocusLost} synthesises releases for every held key and button and keeps the
 *       released key codes as <em>ghosts</em> for {@value #FOCUS_GHOST_WINDOW_MS} ms: a
 *       {@code KeyDown} of a ghost inside that window (the auto-repeat of a key that was never
 *       physically released, for example {@code F11} held across the fullscreen handshake, which
 *       disposes and recreates the window) re-arms the key without a press edge; a real
 *       {@code KeyUp} clears the ghost;</li>
 *   <li>consecutive {@code MouseMove} events and consecutive {@code Resized} events are
 *       coalesced on {@link #offer(RawInput)} (only the last position or size matters at drain
 *       time), so a high-rate pointer cannot push key events out of a full queue;</li>
 *   <li>bindings can be swapped with {@link #setBindings(KeyBindings)} without losing held keys,
 *       because state is keyed by key code, not by action.</li>
 * </ul>
 * The capacity is 512 events; when full the oldest event is dropped.
 */
public final class InputQueue {

    /** Default capacity in events. */
    public static final int DEFAULT_CAPACITY = 512;

    /**
     * How long after a {@code FocusLost} a {@code KeyDown} of a key released by that focus loss
     * is treated as resumed auto-repeat rather than a new press.
     */
    public static final long FOCUS_GHOST_WINDOW_MS = 1_000L;

    /**
     * A key code whose next {@code KeyDown} may be a continuation of a hold that the queue
     * already released: the {@code KeyUp} half of an auto-repeat pair ({@code windowMs == 0},
     * same timestamp only) or a release synthesised by {@code FocusLost}.
     */
    private record Ghost(long whenMs, long windowMs) {
        boolean covers(long keyDownWhenMs) {
            return Math.abs(keyDownWhenMs - whenMs) <= windowMs;
        }
    }

    private final Object lock = new Object();
    private final ArrayDeque<RawInput> pending;
    private final int capacity;
    private long dropped;
    private long coalesced;

    private KeyBindings bindings;
    private final Set<Integer> heldCodes = new LinkedHashSet<>();
    private final Map<Integer, Ghost> ghosts = new HashMap<>();
    private int mouseHeldMask;
    private int mouseX;
    private int mouseY;

    /**
     * Creates a queue with the default capacity.
     *
     * @param bindings the initial key bindings
     */
    public InputQueue(KeyBindings bindings) {
        this(bindings, DEFAULT_CAPACITY);
    }

    /**
     * Creates a queue.
     *
     * @param bindings the initial key bindings
     * @param capacity the maximum number of queued events
     */
    public InputQueue(KeyBindings bindings, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.capacity = capacity;
        this.pending = new ArrayDeque<>(capacity);
    }

    /**
     * Enqueues an event. Thread-safe. A {@code MouseMove} or {@code Resized} replaces a queued
     * event of the same kind at the tail; when the queue is full the oldest event is discarded.
     *
     * @param event the event
     */
    public void offer(RawInput event) {
        Objects.requireNonNull(event, "event");
        synchronized (lock) {
            RawInput tail = pending.peekLast();
            if (tail != null && coalesces(tail, event)) {
                pending.pollLast();
                coalesced++;
            } else if (pending.size() >= capacity) {
                pending.pollFirst();
                dropped++;
            }
            pending.addLast(event);
        }
    }

    private static boolean coalesces(RawInput tail, RawInput next) {
        return tail instanceof RawInput.MouseMove && next instanceof RawInput.MouseMove
                || tail instanceof RawInput.Resized && next instanceof RawInput.Resized;
    }

    /**
     * Number of events discarded because the queue was full (diagnostics).
     *
     * @return the count
     */
    public long droppedCount() {
        synchronized (lock) {
            return dropped;
        }
    }

    /**
     * Number of events replaced by a newer event of the same kind (diagnostics).
     *
     * @return the count
     */
    public long coalescedCount() {
        synchronized (lock) {
            return coalesced;
        }
    }

    /**
     * Number of events waiting to be drained.
     *
     * @return the count
     */
    public int pendingCount() {
        synchronized (lock) {
            return pending.size();
        }
    }

    /**
     * Replaces the key bindings. Must be called on the consumer (loop) thread; held keys keep
     * their state and later edges are interpreted with the new bindings.
     *
     * @param bindings the new bindings
     */
    public void setBindings(KeyBindings bindings) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    /**
     * Current key bindings.
     *
     * @return the bindings
     */
    public KeyBindings bindings() {
        return bindings;
    }

    /**
     * Key codes currently held (consumer thread only).
     *
     * @return a copy of the held set
     */
    public Set<Integer> heldCodes() {
        return new LinkedHashSet<>(heldCodes);
    }

    /**
     * Drains every queued event and computes the frame for the next tick. Consumer thread only.
     *
     * @return the frame
     */
    public InputFrame nextTick() {
        List<RawInput> drained;
        synchronized (lock) {
            drained = new ArrayList<>(pending);
            pending.clear();
        }

        int[] pressCounts = new int[InputAction.values().length];
        EnumSet<InputAction> released = EnumSet.noneOf(InputAction.class);
        List<Integer> rawKeyDowns = new ArrayList<>();
        List<RawInput.SystemEvent> systemEvents = new ArrayList<>();
        int justPressedMask = 0;
        int justReleasedMask = 0;
        int wheel = 0;

        for (int i = 0; i < drained.size(); i++) {
            RawInput event = drained.get(i);
            if (isAutoRepeatPair(event, i + 1 < drained.size() ? drained.get(i + 1) : null)) {
                i++;
                continue;
            }
            if (event instanceof RawInput.KeyDown down) {
                keyDown(down, pressCounts, rawKeyDowns);
            } else if (event instanceof RawInput.KeyUp up) {
                releaseKey(up.code(), released);
                ghosts.put(up.code(), new Ghost(up.whenMs(), 0));
            } else if (event instanceof RawInput.MouseDown md) {
                mouseX = md.x();
                mouseY = md.y();
                int bit = 1 << md.button();
                if ((mouseHeldMask & bit) == 0) {
                    mouseHeldMask |= bit;
                    justPressedMask |= bit;
                    InputAction action = mouseAction(md.button());
                    if (action != null) {
                        pressCounts[action.ordinal()]++;
                    }
                }
            } else if (event instanceof RawInput.MouseUp mu) {
                mouseX = mu.x();
                mouseY = mu.y();
                if (releaseButton(mu.button(), released)) {
                    justReleasedMask |= 1 << mu.button();
                }
            } else if (event instanceof RawInput.MouseMove mm) {
                mouseX = mm.x();
                mouseY = mm.y();
            } else if (event instanceof RawInput.Wheel w) {
                wheel += w.rotation();
            } else if (event instanceof RawInput.FocusLost fl) {
                focusLost(fl, released);
                for (int button = 0; button < 31; button++) {
                    if (releaseButton(button, released)) {
                        justReleasedMask |= 1 << button;
                    }
                }
                systemEvents.add(fl);
            } else if (event instanceof RawInput.SystemEvent se) {
                systemEvents.add(se);
            }
        }

        EnumSet<InputAction> held = EnumSet.noneOf(InputAction.class);
        for (InputAction a : InputAction.values()) {
            if (isActionHeld(a)) {
                held.add(a);
            }
        }
        return new InputFrame(pressCounts, held, released, mouseX, mouseY, justPressedMask,
                mouseHeldMask, justReleasedMask, wheel, rawKeyDowns, systemEvents);
    }

    /**
     * Handles a {@code KeyDown}: ignored while the key is held (auto-repeat); a silent re-arm
     * when the key is a ghost inside its window; otherwise a press edge.
     */
    private void keyDown(RawInput.KeyDown down, int[] pressCounts, List<Integer> rawKeyDowns) {
        int code = down.code();
        if (heldCodes.contains(code)) {
            return;
        }
        Ghost ghost = ghosts.remove(code);
        heldCodes.add(code);
        if (ghost != null && ghost.covers(down.whenMs())) {
            return;
        }
        rawKeyDowns.add(code);
        for (InputAction a : bindings.actionsFor(code)) {
            pressCounts[a.ordinal()]++;
        }
    }

    /** Releases every held key; keeps them as ghosts when the event carries a timestamp. */
    private void focusLost(RawInput.FocusLost event, EnumSet<InputAction> released) {
        boolean keepGhosts = event.whenMs() != RawInput.FocusLost.UNKNOWN_WHEN;
        for (Integer code : new ArrayList<>(heldCodes)) {
            releaseKey(code, released);
            if (keepGhosts) {
                ghosts.put(code, new Ghost(event.whenMs(), FOCUS_GHOST_WINDOW_MS));
            }
        }
    }

    /**
     * Detects the release/press pair some servers emit for keyboard auto-repeat (E30.e): a
     * {@code KeyUp} immediately followed by a {@code KeyDown} of the same code with the same
     * timestamp. Both events are dropped and the key stays held.
     */
    private static boolean isAutoRepeatPair(RawInput first, RawInput second) {
        return first instanceof RawInput.KeyUp up && second instanceof RawInput.KeyDown down
                && up.code() == down.code() && up.whenMs() == down.whenMs();
    }

    private void releaseKey(int code, EnumSet<InputAction> released) {
        if (!heldCodes.remove(code)) {
            return;
        }
        for (InputAction a : bindings.actionsFor(code)) {
            if (!isActionHeld(a)) {
                released.add(a);
            }
        }
    }

    private boolean releaseButton(int button, EnumSet<InputAction> released) {
        int bit = 1 << button;
        if ((mouseHeldMask & bit) == 0) {
            return false;
        }
        mouseHeldMask &= ~bit;
        InputAction action = mouseAction(button);
        if (action != null && !isActionHeld(action)) {
            released.add(action);
        }
        return true;
    }

    private boolean isActionHeld(InputAction action) {
        for (Integer code : heldCodes) {
            if (bindings.isBound(code, action)) {
                return true;
            }
        }
        if (action == InputAction.FLAP && (mouseHeldMask & (1 << Keys.BUTTON_LEFT)) != 0) {
            return true;
        }
        return action == InputAction.ABILITY && (mouseHeldMask & (1 << Keys.BUTTON_RIGHT)) != 0;
    }

    /**
     * Fixed mouse mapping (E29): left = FLAP, right = ABILITY, others unmapped.
     *
     * @param button the button id
     * @return the action or {@code null}
     */
    public static InputAction mouseAction(int button) {
        if (button == Keys.BUTTON_LEFT) {
            return InputAction.FLAP;
        }
        if (button == Keys.BUTTON_RIGHT) {
            return InputAction.ABILITY;
        }
        return null;
    }
}
