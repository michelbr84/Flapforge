package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * {@link FramePresenter} without a window: counts presents and, when a renderer is supplied,
 * draws each frame into a {@link BufferedImage} that tests can inspect or save (D24).
 */
public final class NullPresenter implements FramePresenter {

    private final FrameRenderer renderer;
    private final Viewport viewport;
    private int width;
    private int height;
    private BufferedImage image;
    private long presentCount;
    private double lastAlpha;
    private boolean fullscreen;
    private int fullscreenToggles;
    private int resizes;
    private boolean disposed;
    private int letterboxRgb = -1;
    private Color letterboxColor;

    /** Creates a presenter that only counts. */
    public NullPresenter() {
        this(null, null, 0, 0);
    }

    /**
     * Creates a presenter that renders into an off-screen image.
     *
     * @param renderer the renderer, or {@code null} to only count
     * @param viewport the viewport applied before rendering (required with a renderer)
     * @param width the image width
     * @param height the image height
     */
    public NullPresenter(FrameRenderer renderer, Viewport viewport, int width, int height) {
        if (renderer != null && viewport == null) {
            throw new IllegalArgumentException("a viewport is required with a renderer");
        }
        this.renderer = renderer;
        this.viewport = viewport;
        this.width = width;
        this.height = height;
    }

    @Override
    public void present(double alpha) {
        if (disposed) {
            return;
        }
        presentCount++;
        lastAlpha = alpha;
        if (renderer == null || width <= 0 || height <= 0) {
            return;
        }
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
        int rgb = renderer.letterboxRgb();
        if (letterboxColor == null || rgb != letterboxRgb) {
            letterboxRgb = rgb;
            letterboxColor = new Color(rgb);
        }
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(letterboxColor);
            g.fillRect(0, 0, width, height);
            viewport.apply(g);
            renderer.render(g, alpha);
        } finally {
            g.dispose();
        }
    }

    @Override
    public void onResize(int width, int height) {
        this.width = width;
        this.height = height;
        resizes++;
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        fullscreenToggles++;
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    /**
     * Number of presents so far.
     *
     * @return the count
     */
    public long presentCount() {
        return presentCount;
    }

    /**
     * Alpha passed to the last present.
     *
     * @return the alpha
     */
    public double lastAlpha() {
        return lastAlpha;
    }

    /**
     * The last rendered image, or {@code null} when not rendering.
     *
     * @return the image
     */
    public BufferedImage image() {
        return image;
    }

    @Override
    public boolean isFullscreen() {
        return fullscreen;
    }

    /**
     * Number of {@link #setFullscreen(boolean)} calls.
     *
     * @return the count
     */
    public int fullscreenToggles() {
        return fullscreenToggles;
    }

    /**
     * Number of {@link #onResize(int, int)} calls.
     *
     * @return the count
     */
    public int resizes() {
        return resizes;
    }

    /**
     * Whether {@link #dispose()} was called.
     *
     * @return {@code true} once disposed
     */
    public boolean isDisposed() {
        return disposed;
    }
}
