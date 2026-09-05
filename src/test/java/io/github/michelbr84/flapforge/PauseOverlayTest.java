package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import java.awt.Graphics2D;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The pause panel's buttons: a tap or click on resume or menu lands on that action — the touch
 * build has no {@code Esc} beyond the system back gesture — while a tap outside the buttons
 * keeps the tap-anywhere resume, and Space, Enter and Esc keep their old meanings.
 */
class PauseOverlayTest {

    private ScreenManager screens;
    private Strings strings;

    @BeforeEach
    void setUp() {
        screens = new ScreenManager(new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false));
        strings = Strings.load("en");
    }

    /** A stack filler standing in for the menu and the game screen below the overlay. */
    private static final class StubScreen implements Screen {
        @Override
        public void tick(InputFrame input) {
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }
    }

    /**
     * Pushes menu, game and overlay onto the stack and ticks once so the pushes apply and
     * {@code onEnter} runs; the stack is then menu / game / overlay, depth 3.
     */
    private PauseOverlay pushedOverlay(boolean[] left) {
        PauseOverlay overlay = new PauseOverlay(screens, strings, () -> left[0] = true);
        screens.push(new StubScreen());
        screens.push(new StubScreen());
        screens.push(overlay);
        screens.tick(InputFrame.EMPTY);
        return overlay;
    }

    /** A left click at the given logical position, carrying FLAP as the input queue would. */
    private static InputFrame click(double x, double y) {
        int[] counts = new int[InputAction.values().length];
        counts[InputAction.FLAP.ordinal()] = 1;
        return new InputFrame(counts, EnumSet.of(InputAction.FLAP),
                EnumSet.noneOf(InputAction.class), x, y, 1 << Keys.BUTTON_LEFT,
                1 << Keys.BUTTON_LEFT, 0, 0, List.of(), List.of());
    }

    private static InputFrame press(InputAction action) {
        int[] counts = new int[InputAction.values().length];
        counts[action.ordinal()] = 1;
        return new InputFrame(counts, EnumSet.of(action), EnumSet.noneOf(InputAction.class),
                0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    private static double centerX(Button button) {
        return button.x() + button.width() / 2;
    }

    private static double centerY(Button button) {
        return button.y() + button.height() / 2;
    }

    @Test
    void aTapOnTheResumeButtonResumes() {
        boolean[] left = {false};
        PauseOverlay overlay = pushedOverlay(left);
        screens.tick(click(centerX(overlay.resumeButton()), centerY(overlay.resumeButton())));
        assertEquals(2, screens.depth(), "resume popped only the overlay");
        assertTrue(left[0], "the leave callback ran");
        assertTrue(screens.consumeAccumulatorReset(),
                "resuming zeroes the accumulator so no catch-up burst kills the player");
    }

    @Test
    void aTapOnTheMenuButtonQuitsToTheMenu() {
        boolean[] left = {false};
        PauseOverlay overlay = pushedOverlay(left);
        screens.tick(click(centerX(overlay.menuButton()), centerY(overlay.menuButton())));
        assertEquals(1, screens.depth(),
                "the menu button is reachable without a keyboard and pops both screens");
        assertTrue(left[0], "the leave callback ran");
        assertFalse(screens.consumeAccumulatorReset(), "quitting resumes nothing");
    }

    @Test
    void aTapOutsideTheButtonsStillResumes() {
        boolean[] left = {false};
        pushedOverlay(left);
        screens.tick(click(Playfield.WIDTH / 2.0, 150));
        assertEquals(2, screens.depth(), "the tap-anywhere resume survives the buttons");
        assertTrue(left[0], "the leave callback ran");
        assertTrue(screens.consumeAccumulatorReset());
    }

    @Test
    void spaceStillResumes() {
        boolean[] left = {false};
        pushedOverlay(left);
        screens.tick(press(InputAction.FLAP));
        assertEquals(2, screens.depth(), "Space keeps its old meaning");
        assertTrue(screens.consumeAccumulatorReset());
    }

    @Test
    void enterStillResumes() {
        boolean[] left = {false};
        pushedOverlay(left);
        // Past the transition grace, so CONFIRM reaches the overlay and activates the focused
        // Resume button through the ring rather than the tap-anywhere fallback.
        for (int i = 0; i < ScreenManager.TRANSITION_GRACE_TICKS; i++) {
            screens.tick(InputFrame.EMPTY);
        }
        screens.tick(press(InputAction.CONFIRM));
        assertEquals(2, screens.depth(), "Enter keeps its old meaning");
        assertTrue(left[0], "the leave callback ran");
        assertTrue(screens.consumeAccumulatorReset());
    }

    @Test
    void escapeStillQuitsToTheMenu() {
        boolean[] left = {false};
        pushedOverlay(left);
        screens.tick(press(InputAction.PAUSE));
        assertEquals(1, screens.depth(), "Esc keeps its old meaning");
        assertTrue(left[0], "the leave callback ran");
    }
}
