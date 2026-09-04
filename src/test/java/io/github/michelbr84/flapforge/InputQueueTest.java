package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InputQueueTest {

    private final InputQueue queue = new InputQueue(KeyBindings.defaults());

    private void down(int code, long when) {
        queue.offer(new RawInput.KeyDown(code, when));
    }

    private void up(int code, long when) {
        queue.offer(new RawInput.KeyUp(code, when));
    }

    @Test
    void pressPressPressReleasePressYieldsExactlyTwoFlapsInOneTick() {
        down(Keys.SPACE, 1);
        down(Keys.SPACE, 2);
        down(Keys.SPACE, 3);
        up(Keys.SPACE, 4);
        down(Keys.SPACE, 5);
        InputFrame f = queue.nextTick();
        assertEquals(2, f.pressCount(InputAction.FLAP));
        assertTrue(f.isJustPressed(InputAction.FLAP));
        assertTrue(f.isJustReleased(InputAction.FLAP));
        assertTrue(f.isHeld(InputAction.FLAP));
    }

    @Test
    void pressPressPressReleasePressYieldsExactlyTwoFlapsAcrossTicks() {
        int flaps = 0;
        down(Keys.SPACE, 1);
        flaps += queue.nextTick().pressCount(InputAction.FLAP);
        down(Keys.SPACE, 2);
        flaps += queue.nextTick().pressCount(InputAction.FLAP);
        down(Keys.SPACE, 3);
        InputFrame repeat = queue.nextTick();
        flaps += repeat.pressCount(InputAction.FLAP);
        assertTrue(repeat.isHeld(InputAction.FLAP), "auto-repeat keeps the key held");
        up(Keys.SPACE, 4);
        InputFrame released = queue.nextTick();
        flaps += released.pressCount(InputAction.FLAP);
        assertTrue(released.isJustReleased(InputAction.FLAP));
        assertFalse(released.isHeld(InputAction.FLAP));
        down(Keys.SPACE, 5);
        flaps += queue.nextTick().pressCount(InputAction.FLAP);
        assertEquals(2, flaps);
    }

    @Test
    void pressDuringZeroTickFrameIsConsumedByTheNextTick() {
        down(Keys.SPACE, 10);
        up(Keys.SPACE, 11);
        assertEquals(2, queue.pendingCount(), "a frame without ticks leaves events queued");
        InputFrame f = queue.nextTick();
        assertTrue(f.isJustPressed(InputAction.FLAP));
        assertTrue(f.isJustReleased(InputAction.FLAP));
        assertFalse(f.isHeld(InputAction.FLAP));
        assertEquals(0, queue.pendingCount());
        assertFalse(queue.nextTick().hasEdges());
    }

    @Test
    void focusLostSynthesisesReleases() {
        down(Keys.SPACE, 1);
        down(Keys.X, 2);
        queue.offer(new RawInput.MouseDown(Keys.BUTTON_RIGHT, 5, 6));
        InputFrame held = queue.nextTick();
        assertTrue(held.isHeld(InputAction.FLAP));
        assertTrue(held.isHeld(InputAction.ABILITY));
        assertTrue(held.isMouseHeld(Keys.BUTTON_RIGHT));

        queue.offer(new RawInput.FocusLost());
        InputFrame lost = queue.nextTick();
        assertTrue(lost.isJustReleased(InputAction.FLAP));
        assertTrue(lost.isJustReleased(InputAction.ABILITY));
        assertTrue(lost.isMouseJustReleased(Keys.BUTTON_RIGHT));
        assertTrue(lost.held().isEmpty());
        assertTrue(lost.hasSystemEvent(RawInput.FocusLost.class));
        assertTrue(queue.heldCodes().isEmpty());

        up(Keys.SPACE, 3);
        InputFrame after = queue.nextTick();
        assertFalse(after.isJustReleased(InputAction.FLAP), "the real release produces no second edge");
    }

    @Test
    void releaseThenPressWithSameTimestampIsNotAnEdge() {
        down(Keys.SPACE, 100);
        assertTrue(queue.nextTick().isJustPressed(InputAction.FLAP));

        up(Keys.SPACE, 200);
        down(Keys.SPACE, 200);
        InputFrame repeat = queue.nextTick();
        assertEquals(0, repeat.pressCount(InputAction.FLAP));
        assertFalse(repeat.isJustReleased(InputAction.FLAP));
        assertTrue(repeat.isHeld(InputAction.FLAP));

        up(Keys.SPACE, 300);
        down(Keys.SPACE, 340);
        InputFrame real = queue.nextTick();
        assertEquals(1, real.pressCount(InputAction.FLAP));
        assertTrue(real.isJustReleased(InputAction.FLAP));
    }

    @Test
    void keyHeldAcrossFocusLossIsReArmedWithoutASecondEdge() {
        // F11 held through the fullscreen handshake: the window is disposed (FocusLost), then
        // the still-held key auto-repeats into the recreated window.
        down(Keys.F11, 1);
        assertEquals(1, queue.nextTick().pressCount(InputAction.FULLSCREEN));

        queue.offer(new RawInput.FocusLost(100));
        InputFrame lost = queue.nextTick();
        assertTrue(lost.isJustReleased(InputAction.FULLSCREEN));
        assertFalse(lost.isHeld(InputAction.FULLSCREEN));

        down(Keys.F11, 600);
        InputFrame resumed = queue.nextTick();
        assertEquals(0, resumed.pressCount(InputAction.FULLSCREEN), "resumed auto-repeat is not a press");
        assertTrue(resumed.isHeld(InputAction.FULLSCREEN), "the key is re-armed");
        assertTrue(resumed.rawKeyDowns().isEmpty(), "no capture entry for a re-arm");
        down(Keys.F11, 700);
        assertEquals(0, queue.nextTick().pressCount(InputAction.FULLSCREEN), "still held");

        up(Keys.F11, 900);
        assertTrue(queue.nextTick().isJustReleased(InputAction.FULLSCREEN));

        down(Keys.F11, 1500);
        assertEquals(1, queue.nextTick().pressCount(InputAction.FULLSCREEN), "a real press after the release");
    }

    @Test
    void ghostExpiresAfterTheFocusWindow() {
        down(Keys.SPACE, 1);
        queue.nextTick();
        queue.offer(new RawInput.FocusLost(1_000));
        queue.nextTick();
        down(Keys.SPACE, 1_000 + InputQueue.FOCUS_GHOST_WINDOW_MS + 1);
        InputFrame late = queue.nextTick();
        assertEquals(1, late.pressCount(InputAction.FLAP), "a press well after the focus loss is real");
        assertEquals(List.of(Keys.SPACE), late.rawKeyDowns());
    }

    @Test
    void focusLostWithoutTimestampKeepsNoGhost() {
        down(Keys.SPACE, 1);
        queue.nextTick();
        queue.offer(new RawInput.FocusLost());
        queue.nextTick();
        down(Keys.SPACE, 2);
        assertEquals(1, queue.nextTick().pressCount(InputAction.FLAP));
    }

    @Test
    void releaseThenPressWithSameTimestampAcrossTicksIsNotAPress() {
        down(Keys.SPACE, 100);
        assertTrue(queue.nextTick().isJustPressed(InputAction.FLAP));

        up(Keys.SPACE, 200);
        InputFrame first = queue.nextTick();
        assertTrue(first.isJustReleased(InputAction.FLAP), "the split pair still shows the release");

        down(Keys.SPACE, 200);
        InputFrame second = queue.nextTick();
        assertEquals(0, second.pressCount(InputAction.FLAP), "same timestamp = auto-repeat, no press");
        assertTrue(second.isHeld(InputAction.FLAP), "the key is re-armed");
        assertTrue(second.rawKeyDowns().isEmpty());

        up(Keys.SPACE, 300);
        queue.nextTick();
        down(Keys.SPACE, 340);
        assertEquals(1, queue.nextTick().pressCount(InputAction.FLAP));
    }

    @Test
    void consecutiveMouseMovesAndResizesAreCoalesced() {
        for (int i = 0; i < 1_000; i++) {
            queue.offer(new RawInput.MouseMove(i, i));
        }
        assertEquals(1, queue.pendingCount(), "only the last position is kept");
        assertEquals(999, queue.coalescedCount());
        assertEquals(0, queue.droppedCount(), "no key event can be pushed out by pointer motion");
        queue.offer(new RawInput.Resized(100, 100));
        queue.offer(new RawInput.Resized(200, 300));
        queue.offer(new RawInput.MouseDown(Keys.BUTTON_LEFT, 5, 5));
        queue.offer(new RawInput.MouseMove(7, 8));
        assertEquals(4, queue.pendingCount());
        InputFrame f = queue.nextTick();
        assertEquals(1, f.systemEvents().size());
        assertEquals(new RawInput.Resized(200, 300), f.systemEvents().get(0));
        assertEquals(7, f.mouseX(), 0);
        assertEquals(1, f.pressCount(InputAction.FLAP));
    }

    @Test
    void captureAndRebindThroughTheQueue() {
        down(Keys.Z, 1);
        InputFrame capture = queue.nextTick();
        assertEquals(List.of(Keys.Z), capture.rawKeyDowns());
        assertEquals(0, capture.pressCount(InputAction.FLAP), "Z is unbound by default");
        assertFalse(capture.isHeld(InputAction.FLAP));

        queue.setBindings(queue.bindings().withBinding(InputAction.FLAP, List.of(Keys.Z)));
        assertFalse(queue.nextTick().hasEdges(), "rebinding alone produces no edge");

        up(Keys.Z, 2);
        InputFrame release = queue.nextTick();
        assertTrue(release.isJustReleased(InputAction.FLAP), "held state survives rebinding");

        down(Keys.Z, 3);
        assertEquals(1, queue.nextTick().pressCount(InputAction.FLAP));

        down(Keys.SPACE, 4);
        InputFrame space = queue.nextTick();
        assertEquals(0, space.pressCount(InputAction.FLAP), "Space no longer flaps");
        assertEquals(List.of(Keys.SPACE), space.rawKeyDowns());
    }

    @Test
    void keyBindingsRoundTripThroughMaps() {
        KeyBindings defaults = KeyBindings.defaults();
        Map<String, List<Integer>> map = defaults.toMap();
        assertEquals(List.of(Keys.SPACE, Keys.UP), map.get("FLAP"));
        assertEquals(defaults, KeyBindings.fromMap(map));
        KeyBindings custom = KeyBindings.fromMap(Map.of("flap", List.of(Keys.A), "bogus", List.of(1)));
        assertEquals(List.of(Keys.A), custom.keysFor(InputAction.FLAP));
        assertEquals(List.of(Keys.ESCAPE), custom.keysFor(InputAction.PAUSE));
        assertTrue(defaults.actionsFor(Keys.ESCAPE).containsAll(
                List.of(InputAction.PAUSE, InputAction.BACK)));
    }

    @Test
    void mouseButtonsAreFixedToFlapAndAbility() {
        queue.offer(new RawInput.MouseMove(30, 40));
        queue.offer(new RawInput.MouseDown(Keys.BUTTON_LEFT, 31, 41));
        InputFrame f = queue.nextTick();
        assertEquals(1, f.pressCount(InputAction.FLAP));
        assertTrue(f.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertEquals(31, f.mouseX(), 0);
        assertEquals(41, f.mouseY(), 0);

        queue.offer(new RawInput.MouseUp(Keys.BUTTON_LEFT, 31, 41));
        queue.offer(new RawInput.MouseDown(Keys.BUTTON_RIGHT, 31, 41));
        queue.offer(new RawInput.Wheel(-2));
        InputFrame g = queue.nextTick();
        assertTrue(g.isJustReleased(InputAction.FLAP));
        assertEquals(1, g.pressCount(InputAction.ABILITY));
        assertEquals(-2, g.wheel());
    }

    @Test
    void queueDropsOldestBeyondCapacity() {
        int total = 600;
        for (int i = 0; i < total; i++) {
            down(1000 + i, i);
        }
        InputFrame f = queue.nextTick();
        assertEquals(InputQueue.DEFAULT_CAPACITY, f.rawKeyDowns().size());
        assertEquals(total - InputQueue.DEFAULT_CAPACITY, queue.droppedCount());
        assertEquals(1000 + (total - InputQueue.DEFAULT_CAPACITY), f.rawKeyDowns().get(0));
        assertEquals(1000 + total - 1, f.rawKeyDowns().get(f.rawKeyDowns().size() - 1));
    }

    @Test
    void systemEventsAreForwardedInOrder() {
        queue.offer(new RawInput.Resized(800, 600));
        queue.offer(new RawInput.Iconified(true));
        queue.offer(new RawInput.CloseRequested());
        InputFrame f = queue.nextTick();
        assertEquals(3, f.systemEvents().size());
        assertEquals(new RawInput.Resized(800, 600), f.systemEvents().get(0));
        assertEquals(new RawInput.Iconified(true), f.systemEvents().get(1));
        assertTrue(f.hasSystemEvent(RawInput.CloseRequested.class));
    }

    @Test
    void frameCopiesAreIndependent() {
        down(Keys.ENTER, 1);
        InputFrame f = queue.nextTick();
        InputFrame moved = f.withMouse(7, 9);
        assertEquals(7, moved.mouseX(), 0);
        assertTrue(moved.isJustPressed(InputAction.CONFIRM));
        InputFrame stripped = f.withoutPresses(java.util.EnumSet.of(InputAction.CONFIRM));
        assertFalse(stripped.isJustPressed(InputAction.CONFIRM));
        assertTrue(stripped.isHeld(InputAction.CONFIRM));
        assertTrue(f.isJustPressed(InputAction.CONFIRM), "the original is untouched");
        assertFalse(f.withoutKeyEdges().hasEdges());
    }
}
