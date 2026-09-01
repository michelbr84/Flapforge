package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.HudRenderer;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiText;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Locale;
import java.util.Objects;

/**
 * Overlay shown when a run reaches {@code FINISHED} (D29).
 *
 * <p>It is a compact strip, not a screen: gates, points and time survived (in seconds and in
 * simulation ticks, which is the unit every M1 test speaks), over the frozen last frame of the
 * run. Coins, XP, streak and the level-up toasts join it in M3, when
 * {@code ProgressionManager.apply} runs here (rewards are granted before the overlay is shown, so
 * an instant retry never loses them); the full breakdown behind {@code Enter} is
 * {@code RunSummaryScreen}, also M3.
 *
 * <p>The prompt blinks on the same 60-off/60-on period as upstream's game-over prompt
 * ({@link HudRenderer#BLINK_HALF_TICKS}). {@code Space} or a left click retries immediately with a
 * new seed and the same configuration; {@code Esc} returns to the menu.
 *
 * <p>The overlay also drives {@link GameRenderer#tickFrozen()} on every tick: the ground, the
 * hills and the obstacles stay frozen, as upstream, but the clouds keep drifting at 30 px/s
 * behind the panel instead of the picture standing perfectly still.
 */
public final class GameOverOverlay implements Screen {

    /** Height of the panel. */
    public static final int PANEL_H = 190;

    private static final Color DIM = new Color(0, 0, 0, 0x73);
    private static final int PANEL_X = 30;
    private static final int PANEL_Y = 200;
    private static final int PANEL_W = Playfield.WIDTH - 2 * PANEL_X;
    private static final int ROW_LABEL_X = PANEL_X + 34;
    private static final int ROW_VALUE_X = PANEL_X + PANEL_W - 34;
    private static final int FIRST_ROW_BASELINE = PANEL_Y + 86;
    private static final int ROW_STEP = 26;

    private final ScreenManager screens;
    private final RunResult result;
    private final Runnable onRetry;
    private final GameRenderer renderer;
    private final String gatesValue;
    private final String pointsValue;
    private final String timeValue;
    private int ticks;

    /**
     * Creates the overlay.
     *
     * @param screens the screen stack
     * @param result the finished run's result
     * @param onRetry starts a new run on the game screen below (D29: new seed, same config)
     * @param renderer the game renderer below, kept drifting its clouds while the overlay is up
     */
    public GameOverOverlay(ScreenManager screens, RunResult result, Runnable onRetry,
            GameRenderer renderer) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.result = Objects.requireNonNull(result, "result");
        this.onRetry = Objects.requireNonNull(onRetry, "onRetry");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.gatesValue = Integer.toString(result.gatesPassed());
        this.pointsValue = Long.toString(Math.round(result.stats().points()));
        int ticksAlive = result.stats().ticksAlive();
        this.timeValue = String.format(Locale.ROOT, "%.1f s (%d ticks)",
                ticksAlive / (double) Playfield.TICK_RATE, ticksAlive);
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    /**
     * The result being shown.
     *
     * @return the result
     */
    public RunResult result() {
        return result;
    }

    /**
     * Whether the blinking prompt is currently drawn.
     *
     * @return {@code true} during the second half of the blink period
     */
    public boolean promptVisible() {
        return ticks >= HudRenderer.BLINK_HALF_TICKS;
    }

    @Override
    public void tick(InputFrame input) {
        // The screen manager ticks only the top screen, so the frozen run below would stand
        // perfectly still; upstream kept its clouds drifting on the game-over screen (§5).
        renderer.tickFrozen();
        ticks++;
        if (ticks >= HudRenderer.BLINK_PERIOD_TICKS) {
            ticks = 0;
        }
        if (input.isJustPressed(InputAction.PAUSE) || input.isJustPressed(InputAction.BACK)) {
            screens.pop();
            screens.pop();
            return;
        }
        boolean retry = input.isJustPressed(InputAction.FLAP)
                || input.isMouseJustPressed(Keys.BUTTON_LEFT);
        if (retry) {
            screens.pop();
            onRetry.run();
        }
        // InputAction.CONFIRM opens RunSummaryScreen in M3; the prompt already advertises it.
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        g.setColor(DIM);
        g.fillRect(0, 0, Playfield.WIDTH, Playfield.HEIGHT);
        ProceduralArt.panel(g, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        g.setFont(Fonts.bold(30));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, UiText.GAME_OVER, Playfield.WIDTH / 2.0, PANEL_Y + 48);

        g.setFont(Fonts.regular(15));
        row(g, 0, UiText.GATES, gatesValue);
        row(g, 1, UiText.POINTS, pointsValue);
        row(g, 2, UiText.TIME_ALIVE, timeValue);

        if (promptVisible()) {
            g.setFont(Fonts.regular(13));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.drawCentered(g, UiText.GAME_OVER_PROMPT, Playfield.WIDTH / 2.0,
                    PANEL_Y + PANEL_H - 16.0);
        }
    }

    private void row(Graphics2D g, int index, String label, String value) {
        double baseline = FIRST_ROW_BASELINE + index * (double) ROW_STEP;
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, label, ROW_LABEL_X, baseline);
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, value, ROW_VALUE_X, baseline, Align.RIGHT);
    }
}
