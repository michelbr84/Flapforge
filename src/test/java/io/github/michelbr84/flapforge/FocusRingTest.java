package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Keyboard, hover and click behaviour of {@link FocusRing} (D17) driven with hand-built
 * {@link InputFrame}s: arrows move to the nearest node and wrap, Tab cycles, Enter/Space
 * activate after the transition grace, hover follows pointer movement and a click activates.
 */
class FocusRingTest {

    /** Node that counts activations. */
    static final class Probe extends UiNode {
        final String name;
        int activations;

        Probe(String name, double x, double y, double w, double h) {
            super(x, y, w, h);
            this.name = name;
            setOnAction(() -> activations++);
        }

        @Override
        public void render(Graphics2D g) {
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private FocusRing ring;
    private Probe a;
    private Probe b;
    private Probe c;
    private Probe side;

    @BeforeEach
    void setUp() {
        ring = new FocusRing();
        a = ring.add(new Probe("a", 100, 100, 200, 40));
        b = ring.add(new Probe("b", 100, 160, 200, 40));
        c = ring.add(new Probe("c", 100, 220, 200, 40));
        side = ring.add(new Probe("side", 320, 160, 60, 40));
    }

    @Test
    void firstHandleFocusesFirstNode() {
        assertNull(ring.focused());
        ring.handle(frame(EnumSet.noneOf(InputAction.class), 0, 0, false));
        assertSame(a, ring.focused());
        assertTrue(a.isFocused());
    }

    @Test
    void downMovesToNearestBelowAndWraps() {
        ring.focus(a);
        press(InputAction.DOWN);
        assertSame(b, ring.focused());
        press(InputAction.DOWN);
        assertSame(c, ring.focused());
        press(InputAction.DOWN);
        assertSame(a, ring.focused(), "wraps to the top");
        assertFalse(c.isFocused());
    }

    @Test
    void upFromFirstWrapsToLast() {
        ring.focus(a);
        press(InputAction.UP);
        assertSame(c, ring.focused());
    }

    @Test
    void rightAndLeftReachTheSideNode() {
        ring.focus(b);
        press(InputAction.RIGHT);
        assertSame(side, ring.focused());
        press(InputAction.LEFT);
        assertSame(b, ring.focused());
    }

    @Test
    void tabCyclesInInsertionOrder() {
        ring.focus(a);
        tab();
        assertSame(b, ring.focused());
        tab();
        assertSame(c, ring.focused());
        tab();
        assertSame(side, ring.focused());
        tab();
        assertSame(a, ring.focused());
    }

    @Test
    void disabledNodesAreSkipped() {
        b.setEnabled(false);
        ring.focus(a);
        press(InputAction.DOWN);
        assertSame(c, ring.focused());
        tab();
        assertSame(side, ring.focused());
        b.setEnabled(true);
        ring.focus(b);
        b.setEnabled(false);
        ring.handle(frame(EnumSet.noneOf(InputAction.class), 0, 0, false));
        assertSame(a, ring.focused(), "focus leaves a node that became disabled");
    }

    @Test
    void enterActivatesAfterTransitionGrace() {
        ring.focus(b);
        ring.resetTransition();
        for (int i = 0; i < ScreenManager.TRANSITION_GRACE_TICKS; i++) {
            assertNull(ring.handle(frame(EnumSet.of(InputAction.CONFIRM), 0, 0, false)));
        }
        assertEquals(0, b.activations, "confirm ignored during the grace");
        assertSame(b, ring.handle(frame(EnumSet.of(InputAction.CONFIRM), 0, 0, false)));
        assertEquals(1, b.activations);
    }

    @Test
    void spaceActivatesThroughRawKeyDowns() {
        ring.focus(c);
        UiNode activated = ring.handle(frame(EnumSet.noneOf(InputAction.class), 0, 0, false,
                Keys.SPACE));
        assertSame(c, activated);
        assertEquals(1, c.activations);
    }

    @Test
    void upArrowMovesFocusWithoutActivating() {
        ring.focus(b);
        ring.handle(frame(EnumSet.of(InputAction.UP, InputAction.FLAP), 0, 0, false, Keys.UP));
        assertSame(a, ring.focused());
        assertEquals(0, a.activations + b.activations);
    }

    @Test
    void hoverMovesFocusOnlyWhenThePointerMoves() {
        ring.focus(a);
        ring.handle(frame(EnumSet.noneOf(InputAction.class), 0, 0, false));
        ring.handle(frame(EnumSet.noneOf(InputAction.class), c.centerX(), c.centerY(), false));
        assertSame(c, ring.focused(), "pointer moved onto c");
        assertTrue(c.isHovered());
        assertFalse(a.isHovered());
        ring.handle(frame(EnumSet.of(InputAction.UP), c.centerX(), c.centerY(), false));
        assertSame(b, ring.focused(), "keyboard moved focus while the pointer rests on c");
        ring.handle(frame(EnumSet.noneOf(InputAction.class), c.centerX(), c.centerY(), false));
        assertSame(b, ring.focused(), "a resting pointer does not pull focus back");
        assertTrue(c.isHovered());
    }

    @Test
    void pointerRestingOverNodeOnEntryDoesNotStealFocus() {
        ring.focus(a);
        ring.resetTransition();
        ring.handle(frame(EnumSet.noneOf(InputAction.class), c.centerX(), c.centerY(), false));
        assertSame(a, ring.focused());
        ring.handle(frame(EnumSet.noneOf(InputAction.class), c.centerX() + 1, c.centerY(), false));
        assertSame(c, ring.focused());
    }

    @Test
    void clickActivatesTheNodeUnderThePointer() {
        ring.focus(a);
        UiNode activated = ring.handle(frame(EnumSet.noneOf(InputAction.class), b.centerX(),
                b.centerY(), true));
        assertSame(b, activated);
        assertSame(b, ring.focused());
        assertEquals(1, b.activations);
        assertEquals(0, a.activations);
        assertNull(ring.handle(frame(EnumSet.noneOf(InputAction.class), 10, 10, true)),
                "click on nothing activates nothing");
    }

    @Test
    void clickDuringGraceStillActivates() {
        ring.resetTransition();
        ring.handle(frame(EnumSet.noneOf(InputAction.class), a.centerX(), a.centerY(), true));
        assertEquals(1, a.activations);
    }

    @Test
    void hiddenNodesAreNeitherHitNorFocused() {
        b.setVisible(false);
        assertFalse(b.contains(b.centerX(), b.centerY()));
        ring.focus(a);
        press(InputAction.DOWN);
        assertSame(c, ring.focused());
        assertNull(ring.nodeAt(b.centerX(), b.centerY()));
    }

    @Test
    void removeAndClearDropFocus() {
        ring.focus(b);
        ring.remove(b);
        assertNull(ring.focused());
        assertFalse(b.isFocused());
        assertEquals(3, ring.nodes().size());
        ring.clear();
        assertTrue(ring.nodes().isEmpty());
    }

    private void press(InputAction action) {
        ring.handle(frame(EnumSet.of(action), 0, 0, false));
    }

    private void tab() {
        ring.handle(frame(EnumSet.noneOf(InputAction.class), 0, 0, false, Keys.TAB));
    }

    private static InputFrame frame(EnumSet<InputAction> pressed, double mx, double my,
            boolean click, int... rawKeys) {
        int[] counts = new int[InputAction.values().length];
        for (InputAction a : pressed) {
            counts[a.ordinal()] = 1;
        }
        List<Integer> raw = new ArrayList<>();
        for (int k : rawKeys) {
            raw.add(k);
        }
        int clickMask = click ? 1 << Keys.BUTTON_LEFT : 0;
        return new InputFrame(counts, EnumSet.noneOf(InputAction.class),
                EnumSet.noneOf(InputAction.class), mx, my, clickMask, clickMask, 0, 0, raw,
                List.of());
    }
}
