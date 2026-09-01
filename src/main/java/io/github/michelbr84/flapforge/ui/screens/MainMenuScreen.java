package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.Flapforge;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiText;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.Panel;
import java.awt.Graphics2D;

/**
 * Minimal main menu (D17, M0): the Green Fields backdrop, a bird perched on an anvil above the
 * procedurally drawn title, and a panel with Play, Settings and Quit. Play pushes
 * {@link GameStubScreen}, Settings pushes {@link SettingsScreen}, Quit asks the
 * {@link ScreenManager} to close (the same path as the window button). Arrows/Tab, Enter/Space,
 * hover and click all work through the {@link FocusRing}; {@code Esc} moves focus to Quit. M2
 * completes this screen — it is not a throwaway.
 */
public final class MainMenuScreen implements Screen {

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int BOB_PERIOD_TICKS = 96;
    private static final double BOB_AMPLITUDE = 6;
    private static final int WING_PERIOD_TICKS = 48;
    private static final double EMBLEM_CX = Playfield.WIDTH / 2.0;
    private static final double ANVIL_TOP_Y = 150;
    private static final double ANVIL_W = 96;
    private static final double BIRD_SIZE = 64;
    private static final int TITLE_BASELINE = 262;
    private static final int TAGLINE_BASELINE = 290;
    private static final int PANEL_X = 70;
    private static final int PANEL_Y = 316;
    private static final int PANEL_W = Playfield.WIDTH - 2 * PANEL_X;
    private static final int BUTTON_H = 52;
    private static final int BUTTON_GAP = 16;
    private static final int FOOTER_BASELINE = Playfield.HEIGHT - 14;

    private final ScreenManager screens;
    private final FocusRing ring = new FocusRing();
    private final Panel panel = new Panel();
    private final Button play;
    private final Button settings;
    private final Button quit;
    private final String versionLine;
    private long ticks;
    private double prevBob;
    private double bob;

    /**
     * Creates the menu.
     *
     * @param screens the manager used to push screens and request quitting
     */
    public MainMenuScreen(ScreenManager screens) {
        this.screens = screens;
        play = panel.add(new Button(UiText.PLAY, () -> screens.push(new GameStubScreen(screens))));
        settings = panel.add(new Button(UiText.SETTINGS,
                () -> screens.push(new SettingsScreen(screens))));
        quit = panel.add(new Button(UiText.QUIT, screens::requestClose));
        panel.setBounds(PANEL_X, PANEL_Y, PANEL_W,
                Panel.columnHeight(3, BUTTON_H, BUTTON_GAP, Panel.DEFAULT_PADDING));
        panel.layoutColumn(BUTTON_H, BUTTON_GAP);
        panel.registerFocusables(ring);
        versionLine = UiText.VERSION_PREFIX + Flapforge.version();
    }

    /**
     * The Play button.
     *
     * @return the button
     */
    public Button playButton() {
        return play;
    }

    /**
     * The Settings button.
     *
     * @return the button
     */
    public Button settingsButton() {
        return settings;
    }

    /**
     * The Quit button.
     *
     * @return the button
     */
    public Button quitButton() {
        return quit;
    }

    /**
     * The focus ring (for tests inspecting focus).
     *
     * @return the ring
     */
    public FocusRing focusRing() {
        return ring;
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(play);
        screens.setLetterboxRgb(PALETTE.letterbox());
    }

    @Override
    public void tick(InputFrame input) {
        ticks++;
        prevBob = bob;
        bob = bobAt(ticks);
        ring.handle(input);
        if (input.isJustPressed(InputAction.BACK)) {
            ring.focus(quit);
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

        ProceduralArt.drawAnvil(g, EMBLEM_CX, ANVIL_TOP_Y, ANVIL_W,
                ProceduralArt.letterboxColor(PALETTE));
        double birdY = ANVIL_TOP_Y - BIRD_SIZE * 0.38 + MathUtil.lerp(prevBob, bob, alpha);
        double phase = (ticks % WING_PERIOD_TICKS) / (double) WING_PERIOD_TICKS;
        ProceduralArt.drawBird(g, EMBLEM_CX, birdY, BIRD_SIZE, phase, PALETTE);

        g.setFont(Fonts.bold(58));
        TextPainter.drawOutlined(g, UiText.TITLE, EMBLEM_CX, TITLE_BASELINE, Align.CENTER,
                ProceduralArt.accentColor(PALETTE), ProceduralArt.letterboxColor(PALETTE), 3);
        g.setFont(Fonts.regular(16));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, UiText.TAGLINE, EMBLEM_CX, TAGLINE_BASELINE);

        panel.render(g);

        g.setFont(Fonts.regular(12));
        g.setColor(ProceduralArt.TEXT_DARK);
        TextPainter.draw(g, versionLine, 12, FOOTER_BASELINE);
        TextPainter.drawRight(g, UiText.KEYS_HINT, Playfield.WIDTH - 12, FOOTER_BASELINE);
    }
}
