package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.RawInput;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.util.Objects;

/**
 * Turns the key events of one window into {@link RawInput} key records while dropping the
 * release/press pairs some servers emit for keyboard auto-repeat (E30.e).
 *
 * <p>XWayland and VNC deliver a {@code KEY_RELEASED} immediately followed by a
 * {@code KEY_PRESSED} with the same key code and timestamp for every repeat. A release is
 * therefore held back until the next key event or the end of the current event batch
 * ({@link EventQueue#invokeLater(Runnable)}); when the very next key event is a press of the
 * same code with the same timestamp, both are dropped and the key stays held. Every method must
 * be called on the event-dispatch thread; {@link AwtInputBridge} owns one instance.
 */
public final class KeyRepeatFilter {

    private final InputQueue queue;
    private RawInput.KeyUp pendingRelease;

    /**
     * Creates a filter feeding the given queue.
     *
     * @param queue the queue
     */
    public KeyRepeatFilter(InputQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    /**
     * Handles one key event: presses and releases are queued (subject to the pair filter),
     * typed events are swallowed.
     *
     * @param e the event
     * @return {@code true} when the event was consumed
     */
    public boolean accept(KeyEvent e) {
        int id = e.getID();
        if (id == KeyEvent.KEY_PRESSED) {
            RawInput.KeyUp held = pendingRelease;
            if (held != null && held.code() == e.getKeyCode() && held.whenMs() == e.getWhen()) {
                pendingRelease = null;
                e.consume();
                return true;
            }
            flush();
            queue.offer(new RawInput.KeyDown(e.getKeyCode(), e.getWhen()));
            e.consume();
            return true;
        }
        if (id == KeyEvent.KEY_RELEASED) {
            flush();
            pendingRelease = new RawInput.KeyUp(e.getKeyCode(), e.getWhen());
            EventQueue.invokeLater(this::flush);
            e.consume();
            return true;
        }
        if (id == KeyEvent.KEY_TYPED) {
            e.consume();
            return true;
        }
        return false;
    }

    /** Queues the held-back release, if any. */
    public void flush() {
        RawInput.KeyUp held = pendingRelease;
        if (held != null) {
            pendingRelease = null;
            queue.offer(held);
        }
    }

    /**
     * Whether a release is currently held back.
     *
     * @return {@code true} while a release waits for the next event
     */
    public boolean hasPendingRelease() {
        return pendingRelease != null;
    }
}
