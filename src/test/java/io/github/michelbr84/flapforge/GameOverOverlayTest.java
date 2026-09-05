package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.RunSummaryScreen;
import java.awt.Graphics2D;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The game-over strip's boss row (D29, M8 review pass): it is information about the encounter,
 * so a run whose boss never warned shows no boss row at all instead of a meaningless
 * "Phase 0", and the row only says "Cleared" once a world boss was actually survived.
 *
 * <p>Also the strip's buttons: a tap or click on retry, summary or menu lands on that action
 * — the touch build has no keyboard — while a tap outside the buttons keeps the tap-anywhere
 * retry, and Space and Enter keep their old meanings.
 */
class GameOverOverlayTest {

    private ScreenManager screens;
    private Strings strings;

    @BeforeEach
    void setUp() {
        screens = new ScreenManager(new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false));
        strings = Strings.load("en");
    }

    private GameOverOverlay overlay(RunResult result) {
        GameRenderer renderer = new GameRenderer(WorldPalette.GREEN_FIELDS, "ready");
        return new GameOverOverlay(screens, result, () -> { }, renderer, strings);
    }

    private static RunResult result(boolean bossEnabled, int phasesReached,
            List<String> bossesCleared) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(12);
        stats.setPoints(12);
        for (int i = 0; i < 12 * 60; i++) {
            stats.tickAlive();
        }
        stats.setDeathCause(CollisionCause.OBSTACLE);
        stats.setPhasesReached(phasesReached);
        for (String worldId : bossesCleared) {
            stats.addBossCleared(worldId);
        }
        return new RunResult(RunConfig.builder(42L).bossEnabled(bossEnabled).build(), stats,
                Map.of());
    }

    private static String bossRow(Strings strings, GameOverOverlay overlay) {
        String label = strings.get(StringKey.STAT_BOSS);
        return overlay.rowTexts().stream()
                .filter(row -> row.startsWith(label + " "))
                .findFirst()
                .orElse(null);
    }

    @Test
    void aBossRunThatEndedBeforeTheBossWarnedShowsNoBossRow() {
        GameOverOverlay overlay = overlay(result(true, 0, List.of()));
        assertNull(bossRow(strings, overlay),
                () -> "no boss row before the encounter began: " + overlay.rowTexts());
    }

    @Test
    void aBossRunInTheFightShowsThePhaseReached() {
        GameOverOverlay overlay = overlay(result(true, 2, List.of()));
        String expected = strings.get(StringKey.STAT_BOSS) + " "
                + strings.format(StringKey.STAT_BOSS_PHASE, 2);
        assertTrue(overlay.rowTexts().contains(expected),
                () -> "the boss row names the phase reached: " + overlay.rowTexts());
    }

    @Test
    void aClearedWorldBossShowsClearedInsteadOfAPhase() {
        GameOverOverlay overlay = overlay(result(true, 2, List.of(RunConfig.DEFAULT_WORLD)));
        String expected = strings.get(StringKey.STAT_BOSS) + " "
                + strings.get(StringKey.STAT_BOSS_CLEARED);
        assertTrue(overlay.rowTexts().contains(expected),
                () -> "the boss row says cleared: " + overlay.rowTexts());
    }

    @Test
    void aRunWithoutBossesNeverShowsABossRow() {
        GameOverOverlay overlay = overlay(result(false, 0, List.of()));
        assertNull(bossRow(strings, overlay),
                () -> "a bossless run has no boss row: " + overlay.rowTexts());
    }

    // ------------------------------------------------------------------ buttons (touch)

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
    private GameOverOverlay pushedOverlay(Runnable onRetry) {
        GameRenderer renderer = new GameRenderer(WorldPalette.GREEN_FIELDS, "ready");
        GameOverOverlay overlay = new GameOverOverlay(screens, result(false, 0, List.of()),
                onRetry, renderer, strings);
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
    void aTapOnTheSummaryButtonOpensTheSummaryInsteadOfRetrying() {
        boolean[] retried = {false};
        GameOverOverlay overlay = pushedOverlay(() -> retried[0] = true);
        screens.tick(click(centerX(overlay.summaryButton()), centerY(overlay.summaryButton())));
        assertFalse(retried[0], "the tap landed on a button, not on the tap-anywhere retry");
        assertTrue(screens.top() instanceof RunSummaryScreen,
                "the summary is reachable without a keyboard");
    }

    @Test
    void aTapOnTheMenuButtonLeavesForTheMenuInsteadOfRetrying() {
        boolean[] retried = {false};
        GameOverOverlay overlay = pushedOverlay(() -> retried[0] = true);
        screens.tick(click(centerX(overlay.menuButton()), centerY(overlay.menuButton())));
        assertFalse(retried[0], "the tap landed on a button, not on the tap-anywhere retry");
        assertEquals(1, screens.depth(), "menu popped the overlay and the game screen");
    }

    @Test
    void aTapOnTheRetryButtonRetries() {
        boolean[] retried = {false};
        GameOverOverlay overlay = pushedOverlay(() -> retried[0] = true);
        screens.tick(click(centerX(overlay.retryButton()), centerY(overlay.retryButton())));
        assertTrue(retried[0], "the retry button retries");
        assertEquals(2, screens.depth(), "retry popped only the overlay");
    }

    @Test
    void aTapOutsideTheButtonsStillRetries() {
        boolean[] retried = {false};
        pushedOverlay(() -> retried[0] = true);
        screens.tick(click(Playfield.WIDTH / 2.0, 100));
        assertTrue(retried[0], "the tap-anywhere retry survives the buttons");
        assertEquals(2, screens.depth(), "retry popped only the overlay");
    }

    @Test
    void spaceStillRetries() {
        boolean[] retried = {false};
        pushedOverlay(() -> retried[0] = true);
        screens.tick(press(InputAction.FLAP));
        assertTrue(retried[0], "Space keeps its old meaning");
        assertEquals(2, screens.depth(), "retry popped only the overlay");
    }

    @Test
    void enterStillOpensTheSummary() {
        boolean[] retried = {false};
        pushedOverlay(() -> retried[0] = true);
        for (int i = 0; i < ScreenManager.TRANSITION_GRACE_TICKS; i++) {
            screens.tick(InputFrame.EMPTY);
        }
        screens.tick(press(InputAction.CONFIRM));
        assertFalse(retried[0]);
        assertTrue(screens.top() instanceof RunSummaryScreen, "Enter keeps its old meaning");
    }
}
