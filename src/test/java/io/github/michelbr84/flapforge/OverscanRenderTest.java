package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Overscan;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The presenter chain on a tall window (D3 revision): {@code publishOverscan} plus the widened
 * viewport clip let the world backdrop paint the rows that used to be letterbox bars, and the
 * {@code fillScreen} toggle restores them.
 *
 * <p>The window is 420x940 — scale 1, 150 px bars top and bottom — so device rows map straight
 * onto logical rows minus 150.
 */
class OverscanRenderTest {

    /** {@code FrameRenderer.letterboxRgb()}'s default, as a TYPE_INT_RGB pixel. */
    private static final int LETTERBOX = 0xFF000000 | 0x0e1116;

    @AfterEach
    void resetOverscan() {
        Overscan.reset();
    }

    private static BufferedImage frame(boolean fillScreen) {
        Viewport viewport = new Viewport(420, 940, false);
        viewport.setExtendVertical(fillScreen);
        FrameRenderer renderer =
                (g, alpha) -> ProceduralArt.fillBackground(g, WorldPalette.GREEN_FIELDS);
        NullPresenter presenter = new NullPresenter(renderer, viewport, 420, 940);
        presenter.present(0);
        return presenter.image();
    }

    @Test
    void theBackdropPaintsTheFormerBars() {
        BufferedImage image = frame(true);
        assertNotEquals(LETTERBOX, image.getRGB(210, 5), "the top bar shows sky, not letterbox");
        assertEquals(image.getRGB(210, 5), image.getRGB(210, 60),
                "the extended sky is the gradient's clamped top colour");
        assertNotEquals(LETTERBOX, image.getRGB(210, 935),
                "the bottom bar shows earth, not letterbox");
        assertEquals(image.getRGB(210, 935), image.getRGB(210, 860),
                "the extended earth is the plain ground tone");
    }

    @Test
    void theFillScreenToggleRestoresTheLetterbox() {
        BufferedImage image = frame(false);
        assertEquals(LETTERBOX, image.getRGB(210, 5), "toggled off, the top bar returns");
        assertEquals(LETTERBOX, image.getRGB(210, 935), "toggled off, the bottom bar returns");
        assertNotEquals(LETTERBOX, image.getRGB(210, 470), "the playfield still renders");
    }
}
