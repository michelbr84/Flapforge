package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiText;
import io.github.michelbr84.flapforge.ui.component.Button;
import java.awt.Graphics2D;

/**
 * Placeholder pushed by the main menu's Play button until M1 delivers {@code GameScreen}: the
 * Green Fields backdrop, a bobbing bird at the start position (interpolated with the frame alpha
 * so the loop timing is visible) and a Back button. {@code Esc} also returns to the menu.
 */
public final class GameStubScreen implements Screen {

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int BOB_PERIOD_TICKS = 72;
    private static final double BOB_AMPLITUDE = 8;
    private static final int WING_PERIOD_TICKS = 24;
    private static final int PANEL_X = 40;
    private static final int PANEL_Y = 110;
    private static final int PANEL_W = Playfield.WIDTH - 2 * PANEL_X;
    private static final int PANEL_H = 130;

    private final ScreenManager screens;
    private final FocusRing ring = new FocusRing();
    private final Button back;
    private long ticks;
    private double prevBob;
    private double bob;

    /**
     * Creates the screen.
     *
     * @param screens the manager used to pop back
     */
    public GameStubScreen(ScreenManager screens) {
        this.screens = screens;
        back = ring.add(new Button(UiText.BACK, screens::pop));
        back.setBounds(150, 420, 120, 44);
    }

    /**
     * The Back button (for tests that click it).
     *
     * @return the button
     */
    public Button backButton() {
        return back;
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(back);
        screens.setLetterboxRgb(PALETTE.letterbox());
    }

    @Override
    public void tick(InputFrame input) {
        ticks++;
        prevBob = bob;
        bob = bobAt(ticks);
        ring.handle(input);
        if (input.isJustPressed(InputAction.BACK)) {
            screens.pop();
        }
    }

    private static double bobAt(long tick) {
        long t = tick % BOB_PERIOD_TICKS;
        double half = BOB_PERIOD_TICKS / 2.0;
        double wave = t < half ? t / half : (BOB_PERIOD_TICKS - t) / half;
        return -BOB_AMPLITUDE / 2 + BOB_AMPLITUDE * wave;
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);

        double y = Playfield.BIRD_START_Y + Playfield.SPRITE_H / 2.0
                + MathUtil.lerp(prevBob, bob, alpha);
        double phase = (ticks % WING_PERIOD_TICKS) / (double) WING_PERIOD_TICKS;
        ProceduralArt.drawBird(g, Playfield.BIRD_X + Playfield.SPRITE_W / 2.0, y,
                Playfield.SPRITE_W, phase, PALETTE);

        ProceduralArt.panel(g, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        g.setFont(Fonts.bold(22));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, UiText.GAME_STUB_MESSAGE, Playfield.WIDTH / 2.0,
                PANEL_Y + 52);
        g.setFont(Fonts.regular(14));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.drawCentered(g, UiText.GAME_STUB_HINT, Playfield.WIDTH / 2.0, PANEL_Y + 92);

        ring.render(g);
    }
}
