package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Objects;

/**
 * Overlay shown while a run is paused (D2). It dims the frozen game underneath — the
 * {@link GameScreen} is not ticked while an overlay is on top, so the simulation is genuinely
 * stopped, not slowed — and waits for an explicit resume.
 *
 * <p>Resuming zeroes the loop accumulator through
 * {@link ScreenManager#requestAccumulatorReset()}: while the window was unfocused or minimised
 * the presenter skipped frames and the operating system may have starved the loop thread, so
 * without the reset the first frame back would run a burst of up to six catch-up ticks and kill
 * the player before the screen is even visible.
 */
public final class PauseOverlay implements Screen {

    /** Height of the panel. */
    public static final int PANEL_H = 132;

    private static final Color DIM = new Color(0, 0, 0, 0x8C);
    private static final int PANEL_X = 34;
    private static final int PANEL_Y = 250;
    private static final int PANEL_W = Playfield.WIDTH - 2 * PANEL_X;

    private final ScreenManager screens;
    private final Strings strings;

    /**
     * Creates the overlay with the active string table.
     *
     * @param screens the screen stack
     */
    public PauseOverlay(ScreenManager screens) {
        this(screens, Strings.active());
    }

    /**
     * Creates the overlay.
     *
     * @param screens the screen stack
     * @param strings the string table its three lines come from
     */
    public PauseOverlay(ScreenManager screens, Strings strings) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    @Override
    public void tick(InputFrame input) {
        if (input.isJustPressed(InputAction.PAUSE) || input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
            screens.pop();
            return;
        }
        boolean resume = input.isJustPressed(InputAction.FLAP)
                || input.isJustPressed(InputAction.CONFIRM)
                || input.isMouseJustPressed(Keys.BUTTON_LEFT);
        if (resume) {
            screens.pop();
            screens.requestAccumulatorReset();
        }
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        g.setColor(DIM);
        g.fillRect(0, 0, Playfield.WIDTH, Playfield.HEIGHT);
        ProceduralArt.panel(g, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        g.setFont(Fonts.bold(30));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, strings.get(StringKey.PAUSE_TITLE), Playfield.WIDTH / 2.0,
                PANEL_Y + 52);
        g.setFont(Fonts.regular(14));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.drawCentered(g, strings.get(StringKey.PAUSE_RESUME_HINT),
                Playfield.WIDTH / 2.0, PANEL_Y + 84);
        TextPainter.drawCentered(g, strings.get(StringKey.PAUSE_QUIT_HINT),
                Playfield.WIDTH / 2.0, PANEL_Y + 106);
    }
}
