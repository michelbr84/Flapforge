package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import awt.Color;
import awt.Graphics2D;
import awt.image.BufferedImage;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.Overscan;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The first real frame, rendered through the {@code awt} shim at a portrait-phone resolution
 * with the fill-screen overscan on (regression guard for the P4/P5 shim and the D3 revision).
 *
 * <p>The Robolectric boot test presents against a fake holder whose canvas is always null, so
 * every frame there is a counted skip and the real render path is never exercised — which is
 * how a shim regression could ship an APK that closes on launch. This test drives
 * {@link SurfacePresenter#paint} into a real bitmap-backed shim canvas at 1080x2400, painting
 * the world backdrop (gradient sky, hill and cloud ovals), the extended-region scrim and a line
 * of text — the sub-shims the menu's first frame uses — from the overscanned logical range. A
 * frame that throws (a shim path that does not match {@code java.awt}) fails here instead of
 * on a device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SurfaceRenderSmokeTest {

    /** A tall portrait phone: scale 1080/420, ~377 px letterbox bands top and bottom. */
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2400;
    private static final int LETTERBOX = 0x0e1116;
    private static final int OPAQUE = 0xff000000;

    @After
    public void resetOverscan() {
        Overscan.reset();
    }

    private static GameSurfaceView view() {
        return new GameSurfaceView(RuntimeEnvironment.getApplication());
    }

    /** Paints the world backdrop, the extended scrim and a line of text through the shim. */
    private static final class MenuLikeRenderer implements FrameRenderer {
        @Override
        public void render(Graphics2D g, double alpha) {
            ProceduralArt.prepare(g);
            ProceduralArt.fillBackground(g, WorldPalette.GREEN_FIELDS);
            Overscan.fillVisible(g, new Color(0, 0, 0, 0x40));
            g.setFont(Fonts.bold(30));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.drawCentered(g, "Flapforge", Playfield.WIDTH / 2.0, 200);
        }
    }

    @Test
    public void theFirstFrameRendersThroughTheShimOnATallPhone() {
        Viewport viewport = new Viewport(WIDTH, HEIGHT, false);
        SurfacePresenter presenter = new SurfacePresenter(new AndroidWindow(view()), viewport,
                new MenuLikeRenderer(), () -> { });
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            // No exception is the point: the weaker shim threw here on a real device.
            presenter.paint(g, WIDTH, HEIGHT, 0.0);
        } finally {
            g.dispose();
        }

        // The extended top band, once a letterbox bar, now carries painted sky.
        assertNotEquals("the former top bar is painted, not letterbox",
                OPAQUE | LETTERBOX, image.getRGB(WIDTH / 2, 20));
        // The playfield renders.
        assertTrue(image.getRGB(WIDTH / 2, HEIGHT / 2) != 0);
    }
}
