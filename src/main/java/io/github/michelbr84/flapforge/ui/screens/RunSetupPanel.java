package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.Panel;
import java.awt.Graphics2D;

/**
 * Where the next run is configured (M11): the world, the difficulty tier and the run mode, on a
 * panel the bird selection's summary bar opens.
 *
 * <p>The three rows are the screen's own — the panel positions, shows and draws them, it does not
 * own them — so every refusal, snap-back and daily settlement they carry is unchanged. The panel
 * has its own {@link FocusRing}: while it is up, that ring is the one taking input, so nothing
 * under the veil can be hovered, clicked or reached with an arrow, and the screen's ring keeps
 * pointing at the bar that opened it. While the panel is closed its nodes are invisible, which is
 * what makes a focus request for a hidden row drop rather than stick.
 */
final class RunSetupPanel extends Panel {

    /** Left edge. */
    static final int X = 8;
    /** Top edge. */
    static final int Y = 60;
    /** Width. */
    static final int W = 404;
    /** Height. */
    static final int H = 248;
    /** Baseline of the title. */
    static final int TITLE_BASELINE = Y + 28;
    /** Point size of the title. */
    static final int TITLE_SIZE = 18;

    private final Button done;
    private final FocusRing ring = new FocusRing();
    private String title = "";

    /**
     * Creates the panel around the screen's three rows.
     *
     * @param world the world row
     * @param tier the tier row
     * @param mode the run-mode row
     * @param onDone what the Done button does
     */
    RunSetupPanel(UiNode world, UiNode tier, UiNode mode, Runnable onDone) {
        setBounds(X, Y, W, H);
        setPadding(16);
        add(world).setBounds(X + 16, Y + 44, W - 32, 40);
        add(tier).setBounds(X + 16, Y + 90, W - 32, 40);
        add(mode).setBounds(X + 16, Y + 136, W - 32, 40);
        done = add(new Button("", onDone));
        done.setFontSize(16);
        done.setBounds(X + 16, Y + 192, W - 32, 40);
        registerFocusables(ring);
        setOpen(false);
    }

    /**
     * The ring the panel takes its input on.
     *
     * @return the ring
     */
    FocusRing ring() {
        return ring;
    }

    /**
     * The Done button.
     *
     * @return the button
     */
    Button doneButton() {
        return done;
    }

    /**
     * Changes the title (a language switch).
     *
     * @param newTitle the text
     */
    void setTitle(String newTitle) {
        this.title = newTitle == null ? "" : newTitle;
    }

    /**
     * Changes the Done label (a language switch).
     *
     * @param text the text
     */
    void setDoneText(String text) {
        done.setText(text);
    }

    /**
     * Whether the panel is up.
     *
     * @return {@code true} while it is shown
     */
    boolean isOpen() {
        return isVisible();
    }

    /**
     * Shows or hides the panel and everything on it.
     *
     * @param open whether the panel is up
     */
    void setOpen(boolean open) {
        setVisible(open);
        for (UiNode child : children()) {
            child.setVisible(open);
        }
        if (open) {
            ring.resetTransition();
            ring.focus(null);
            // The first row that can take focus: under a settled daily the world and the tier
            // are read-only, and the mode row is what the player came for.
            ring.focusFirst();
        }
    }

    @Override
    public void render(Graphics2D g) {
        super.render(g);
        g.setFont(Fonts.bold(TITLE_SIZE));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, title, X + W / 2.0, TITLE_BASELINE);
    }
}
