package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.PixelFormat;
import android.view.SurfaceHolder;
import awt.Color;
import awt.Graphics2D;
import awt.image.BufferedImage;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proofs of the {@link SurfacePresenter} (M10, P2): the frame body paints the
 * letterbox and the viewport-transformed playfield exactly like the desktop presenter, and
 * {@code present} skips instead of throwing whenever there is nothing to draw on.
 *
 * <p>Robolectric's {@code ShadowSurfaceView} hands out a fake holder whose {@code lockCanvas}
 * always answers {@code null}, so a present on a "created" surface is still a skip here; the
 * paint path is exercised through a {@link BufferedImage}-backed {@link Graphics2D} instead,
 * which is the same shim context the surface canvas gets at run time.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SurfacePresenterTest {

    /** 840x1400: scale 2 (limited by the width), a 840x1280 playfield, 60 px bars top and bottom. */
    private static final int WIDTH = 840;
    private static final int HEIGHT = 1400;
    private static final int BAR = 60;
    private static final int LETTERBOX = 0x112233;
    private static final int INSIDE = 0x00ff00;
    private static final int OPAQUE = 0xff000000;

    /** Fills the whole logical playfield with one colour and counts the calls. */
    private static final class SolidRenderer implements FrameRenderer {

        int renders;

        @Override
        public void render(Graphics2D g, double alpha) {
            renders++;
            g.setColor(new Color(INSIDE));
            g.fillRect(0, 0, Playfield.WIDTH, Playfield.HEIGHT);
        }

        @Override
        public int letterboxRgb() {
            return LETTERBOX;
        }
    }

    private static GameSurfaceView view() {
        return new GameSurfaceView(RuntimeEnvironment.getApplication());
    }

    @Test
    public void paintFillsTheLetterboxAndRendersInsideTheViewport() {
        Viewport viewport = new Viewport(WIDTH, HEIGHT, false);
        SolidRenderer renderer = new SolidRenderer();
        SurfacePresenter presenter = new SurfacePresenter(new AndroidWindow(view()), viewport,
                renderer);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            presenter.paint(g, WIDTH, HEIGHT, 0.0);
        } finally {
            g.dispose();
        }

        assertEquals(1, renderer.renders);
        assertEquals(2.0, viewport.scale(), 1e-9);
        assertEquals(BAR, viewport.offsetY(), 1e-9);
        // The bars above and below the playfield carry the renderer's letterbox colour ...
        assertEquals(OPAQUE | LETTERBOX, image.getRGB(WIDTH / 2, BAR / 2));
        assertEquals(OPAQUE | LETTERBOX, image.getRGB(WIDTH / 2, HEIGHT - BAR / 2));
        assertEquals(OPAQUE | LETTERBOX, image.getRGB(0, BAR - 1));
        // ... and the playfield, after the viewport's translate/scale/clip, the renderer's.
        assertEquals(OPAQUE | INSIDE, image.getRGB(WIDTH / 2, HEIGHT / 2));
        assertEquals(OPAQUE | INSIDE, image.getRGB(0, BAR));
        assertEquals(OPAQUE | INSIDE, image.getRGB(WIDTH - 1, HEIGHT - BAR - 1));
    }

    @Test
    public void presentWithoutASurfaceIsSkippedAndDoesNotThrow() {
        SolidRenderer renderer = new SolidRenderer();
        SurfacePresenter presenter = new SurfacePresenter(new AndroidWindow(view()),
                new Viewport(WIDTH, HEIGHT, false), renderer);

        presenter.present(0.5);
        presenter.present(0.25);

        assertEquals(0, presenter.presentCount());
        assertEquals(2, presenter.skippedCount());
        assertEquals("nothing was drawn on a surface that does not exist", 0, renderer.renders);
    }

    @Test
    public void presentOnRobolectricsFakeSurfaceIsSkippedBecauseTheHolderHasNoCanvas() {
        GameSurfaceView view = view();
        SurfaceHolder holder = view.getHolder();
        view.surfaceCreated(holder);
        view.surfaceChanged(holder, PixelFormat.RGBA_8888, WIDTH, HEIGHT);
        assertTrue(view.isSurfaceAlive());
        SolidRenderer renderer = new SolidRenderer();
        SurfacePresenter presenter = new SurfacePresenter(new AndroidWindow(view),
                new Viewport(WIDTH, HEIGHT, false), renderer);

        presenter.present(0.0);

        assertEquals(0, presenter.presentCount());
        assertEquals(1, presenter.skippedCount());
        assertEquals(0, renderer.renders);

        view.surfaceDestroyed(holder);
        assertFalse(view.isSurfaceAlive());
        presenter.present(0.0);
        assertEquals(2, presenter.skippedCount());
    }

    @Test
    public void disposeStopsPresenting() {
        SurfacePresenter presenter = new SurfacePresenter(new AndroidWindow(view()),
                new Viewport(WIDTH, HEIGHT, false), new SolidRenderer());
        presenter.dispose();
        presenter.present(0.0);
        assertEquals(0, presenter.presentCount());
        assertEquals(1, presenter.skippedCount());
    }

    @Test
    public void isAlwaysFullscreenAndIgnoresToggles() {
        SurfacePresenter presenter = new SurfacePresenter(new AndroidWindow(view()),
                new Viewport(WIDTH, HEIGHT, false), new SolidRenderer());
        assertTrue(presenter.isFullscreen());
        presenter.setFullscreen(false);
        assertTrue(presenter.isFullscreen());
        presenter.onResize(1, 1);
        assertTrue(presenter.isFullscreen());
    }
}
