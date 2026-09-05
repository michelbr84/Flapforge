package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.Overscan;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.component.Button;
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
 *
 * <p>The panel carries two buttons — resume and menu — hit-tested through a {@link FocusRing},
 * so a touch or click lands on the action it names (on Android every tap arrives as a left
 * click at its true position). {@code Space}, {@code Enter} or a click outside the buttons
 * resumes; {@code Esc} quits to the menu, exactly as before.
 */
public final class PauseOverlay implements Screen {

    /** Height of the panel. */
    public static final int PANEL_H = 134;

    private static final Color DIM = new Color(0, 0, 0, 0x8C);
    private static final int PANEL_X = 34;
    private static final int PANEL_Y = 250;
    private static final int PANEL_W = Playfield.WIDTH - 2 * PANEL_X;
    private static final int BUTTON_H = 42;
    private static final int BUTTON_GAP = 8;
    private static final int BUTTON_INSET = 14;
    private static final int BUTTON_Y = PANEL_Y + 76;
    private static final int BUTTON_W = (PANEL_W - 2 * BUTTON_INSET - BUTTON_GAP) / 2;

    private final ScreenManager screens;
    private final Strings strings;
    private final Runnable onLeave;
    private final FocusRing ring = new FocusRing();
    private final Button resumeButton;
    private final Button menuButton;

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
     * @param strings the string table its title and buttons come from
     */
    public PauseOverlay(ScreenManager screens, Strings strings) {
        this(screens, strings, null);
    }

    /**
     * Creates the overlay with a leave callback.
     *
     * @param screens the screen stack
     * @param strings the string table its title and buttons come from
     * @param onLeave runs once when the overlay leaves the stack — resume or quit alike. The
     *     game screen uses it to un-duck the music (M8, D19)
     */
    public PauseOverlay(ScreenManager screens, Strings strings, Runnable onLeave) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.onLeave = onLeave;
        this.resumeButton = new Button(strings.get(StringKey.PAUSE_RESUME), this::resume);
        this.menuButton = new Button(strings.get(StringKey.SUMMARY_MENU), this::quitToMenu);
        resumeButton.setFontSize(16);
        menuButton.setFontSize(16);
        resumeButton.setBounds(PANEL_X + BUTTON_INSET, BUTTON_Y, BUTTON_W, BUTTON_H);
        menuButton.setBounds(PANEL_X + BUTTON_INSET + BUTTON_W + BUTTON_GAP, BUTTON_Y,
                BUTTON_W, BUTTON_H);
        ring.add(resumeButton);
        ring.add(menuButton);
    }

    /**
     * The resume button, for tests.
     *
     * @return the button
     */
    public Button resumeButton() {
        return resumeButton;
    }

    /**
     * The menu button, for tests.
     *
     * @return the button
     */
    public Button menuButton() {
        return menuButton;
    }

    /** Pops the overlay and hands the run back with a zeroed accumulator. */
    private void resume() {
        leave(1);
        screens.requestAccumulatorReset();
    }

    /** Abandons the paused run for the menu. */
    private void quitToMenu() {
        UiCues.back();
        leave(2);
    }

    /** Pops the asked-for number of screens and runs the leave callback, if there is one. */
    private void leave(int pops) {
        for (int i = 0; i < pops; i++) {
            screens.pop();
        }
        if (onLeave != null) {
            onLeave.run();
        }
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(resumeButton);
    }

    @Override
    public void tick(InputFrame input) {
        if (input.isJustPressed(InputAction.PAUSE) || input.isJustPressed(InputAction.BACK)) {
            quitToMenu();
            return;
        }
        // The ring before the tap-anywhere fallback: a left click also arrives as FLAP, so a
        // tap that lands on a button must be consumed here or it would resume instead.
        if (ring.handle(input) != null) {
            return;
        }
        if (input.isJustPressed(InputAction.FLAP)
                || input.isJustPressed(InputAction.CONFIRM)
                || input.isMouseJustPressed(Keys.BUTTON_LEFT)) {
            resume();
        }
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        Overscan.fillVisible(g, DIM);
        ProceduralArt.panel(g, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        g.setFont(Fonts.bold(30));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, strings.get(StringKey.PAUSE_TITLE), Playfield.WIDTH / 2.0,
                PANEL_Y + 52);
        ring.render(g);
    }
}
