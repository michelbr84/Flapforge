package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.michelbr84.flapforge.app.GameWindow;
import io.github.michelbr84.flapforge.core.Playfield;
import org.junit.jupiter.api.Test;

/** Default window scale rule (D3): the largest integer scale whose decorated window fits. */
class WindowScaleTest {

    @Test
    void largestDecoratedWindowThatFitsTheUsableHeight() {
        int allowance = GameWindow.DECORATION_ALLOWANCE_PX;
        assertEquals(2, GameWindow.scaleFor(1_408), "5120x1440 desktop with a panel");
        assertEquals(2, GameWindow.scaleFor(2 * Playfield.HEIGHT + allowance));
        assertEquals(1, GameWindow.scaleFor(2 * Playfield.HEIGHT + allowance - 1));
        assertEquals(1, GameWindow.scaleFor(1_040), "1080p with a taskbar");
        assertEquals(3, GameWindow.scaleFor(2_160), "4K");
        assertEquals(1, GameWindow.scaleFor(480), "never below 1");
        assertEquals(1, GameWindow.scaleFor(0));
    }
}
