package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.KeyRepeatFilter;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

/**
 * The bridge-side auto-repeat filter (E30.e) driven with real {@link KeyEvent}s on the
 * event-dispatch thread, headless: a release immediately followed by a press with the same
 * timestamp is dropped as a pair; a lone release is flushed at the end of the event batch.
 */
class KeyRepeatFilterTest {

    private final InputQueue queue = new InputQueue(KeyBindings.defaults());
    private final KeyRepeatFilter filter = new KeyRepeatFilter(queue);
    private final Canvas source = new Canvas();

    private KeyEvent event(int id, int code, long when) {
        return new KeyEvent(source, id, when, 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    private void onEdt(Runnable task) throws Exception {
        EventQueue.invokeAndWait(task);
    }

    @Test
    void releasePressPairWithTheSameTimestampIsDropped() throws Exception {
        onEdt(() -> {
            assertTrue(filter.accept(event(KeyEvent.KEY_PRESSED, Keys.SPACE, 100)));
            assertTrue(filter.accept(event(KeyEvent.KEY_RELEASED, Keys.SPACE, 200)));
            assertTrue(filter.hasPendingRelease(), "the release is held back");
            assertTrue(filter.accept(event(KeyEvent.KEY_PRESSED, Keys.SPACE, 200)));
            assertFalse(filter.hasPendingRelease(), "the pair was dropped");
        });
        onEdt(() -> { }); // runs the deferred flush, which must find nothing
        assertEquals(1, queue.pendingCount(), "only the first press reached the queue");
        InputFrame f = queue.nextTick();
        assertEquals(1, f.pressCount(InputAction.FLAP));
        assertTrue(f.isHeld(InputAction.FLAP));
        assertFalse(f.isJustReleased(InputAction.FLAP));
    }

    @Test
    void loneReleaseIsFlushedAtTheEndOfTheBatch() throws Exception {
        onEdt(() -> {
            filter.accept(event(KeyEvent.KEY_PRESSED, Keys.SPACE, 100));
            filter.accept(event(KeyEvent.KEY_RELEASED, Keys.SPACE, 200));
            assertEquals(1, queue.pendingCount(), "the release is not queued yet");
        });
        onEdt(() -> { }); // the invokeLater flush has run
        assertEquals(2, queue.pendingCount());
        InputFrame f = queue.nextTick();
        assertTrue(f.isJustPressed(InputAction.FLAP));
        assertTrue(f.isJustReleased(InputAction.FLAP));
        assertFalse(f.isHeld(InputAction.FLAP));
    }

    @Test
    void releaseFollowedByADifferentEventIsFlushedInOrder() throws Exception {
        onEdt(() -> {
            filter.accept(event(KeyEvent.KEY_PRESSED, Keys.SPACE, 100));
            filter.accept(event(KeyEvent.KEY_RELEASED, Keys.SPACE, 200));
            filter.accept(event(KeyEvent.KEY_PRESSED, Keys.SPACE, 260));
            filter.accept(event(KeyEvent.KEY_PRESSED, Keys.X, 200));
        });
        onEdt(() -> { });
        assertEquals(4, queue.pendingCount(), "release flushed before the later presses");
        InputFrame f = queue.nextTick();
        assertEquals(2, f.pressCount(InputAction.FLAP), "a real re-press is a second flap");
        assertEquals(1, f.pressCount(InputAction.ABILITY), "same timestamp, other key: not a pair");
        assertTrue(f.isJustReleased(InputAction.FLAP));
    }

    @Test
    void typedEventsAreSwallowedAndOthersIgnored() throws Exception {
        onEdt(() -> {
            KeyEvent typed = new KeyEvent(source, KeyEvent.KEY_TYPED, 1, 0, KeyEvent.VK_UNDEFINED, ' ');
            assertTrue(filter.accept(typed));
            assertTrue(typed.isConsumed());
            assertFalse(filter.accept(event(KeyEvent.KEY_FIRST - 1, Keys.SPACE, 1)));
        });
        assertEquals(0, queue.pendingCount());
    }
}
