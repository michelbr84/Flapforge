package io.github.michelbr84.flapforge.ui.screens;

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
import io.github.michelbr84.flapforge.ui.component.Label;
import io.github.michelbr84.flapforge.ui.component.Panel;
import java.awt.Graphics2D;

/**
 * Settings stub for M0: a title, a note that the real options arrive in M2 and a Back button.
 * {@code Esc} also returns to the menu.
 */
public final class SettingsScreen implements Screen {

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 150;
    private static final int PANEL_X = 50;
    private static final int PANEL_Y = 220;
    private static final int PANEL_W = Playfield.WIDTH - 2 * PANEL_X;
    private static final int ROW_H = 44;
    private static final int GAP = 20;

    private final ScreenManager screens;
    private final FocusRing ring = new FocusRing();
    private final Panel panel = new Panel();
    private final Button back;

    /**
     * Creates the screen.
     *
     * @param screens the manager used to pop back
     */
    public SettingsScreen(ScreenManager screens) {
        this.screens = screens;
        Label note = panel.add(new Label(UiText.SETTINGS_STUB_MESSAGE, Align.CENTER));
        note.setColor(ProceduralArt.TEXT_MUTED);
        back = panel.add(new Button(UiText.BACK, screens::pop));
        panel.setBounds(PANEL_X, PANEL_Y, PANEL_W,
                Panel.columnHeight(2, ROW_H, GAP, Panel.DEFAULT_PADDING));
        panel.layoutColumn(ROW_H, GAP);
        panel.registerFocusables(ring);
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
        ring.handle(input);
        if (input.isJustPressed(InputAction.BACK)) {
            screens.pop();
        }
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        g.setFont(Fonts.bold(40));
        TextPainter.drawOutlined(g, UiText.SETTINGS, Playfield.WIDTH / 2.0, TITLE_BASELINE,
                Align.CENTER, ProceduralArt.TEXT_LIGHT, ProceduralArt.letterboxColor(PALETTE), 2);
        panel.render(g);
    }
}
