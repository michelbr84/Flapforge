package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.image.BufferStrategy;
import java.util.Objects;

/**
 * {@link FramePresenter} drawing through the canvas {@link BufferStrategy} (D4, D24).
 *
 * <p>{@link #present(double)} follows the canonical contract: draw, repeat while
 * {@code contentsRestored()}, show, repeat while {@code contentsLost()}, then
 * {@code Toolkit.sync()}. It skips frames while the window is iconified, while a fullscreen
 * handshake is in progress, and whenever the strategy is missing or the canvas is not
 * displayable. The letterbox is filled with the renderer's colour (cached by value, D18: no
 * per-frame allocation), then the viewport transform and clip are applied and the renderer
 * draws the logical playfield.
 */
public final class BufferStrategyPresenter implements FramePresenter {

    private final GameWindow window;
    private final Viewport viewport;
    private final FrameRenderer renderer;
    private volatile boolean renderSuspended;
    private volatile boolean disposed;
    private long presentCount;
    private long skippedCount;
    private int letterboxRgb = -1;
    private Color letterboxColor;

    /**
     * Creates a presenter.
     *
     * @param window the window whose canvas is drawn
     * @param viewport the loop-owned viewport
     * @param renderer the frame renderer
     */
    public BufferStrategyPresenter(GameWindow window, Viewport viewport, FrameRenderer renderer) {
        this.window = Objects.requireNonNull(window, "window");
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public void present(double alpha) {
        if (disposed || renderSuspended || window.isIconified()) {
            skippedCount++;
            return;
        }
        Canvas canvas = window.canvas();
        BufferStrategy bs = canvas.getBufferStrategy();
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (bs == null || !canvas.isDisplayable() || w <= 0 || h <= 0) {
            skippedCount++;
            return;
        }
        do {
            do {
                Graphics2D g = (Graphics2D) bs.getDrawGraphics();
                try {
                    paint(g, w, h, alpha);
                } finally {
                    g.dispose();
                }
            } while (bs.contentsRestored());
            bs.show();
        } while (bs.contentsLost());
        Toolkit.getDefaultToolkit().sync();
        presentCount++;
    }

    /**
     * Draws one frame into an arbitrary context of the given size (also used by the smoke test
     * to render the same frame into an image).
     *
     * @param g the context in window coordinates
     * @param width the surface width
     * @param height the surface height
     * @param alpha the interpolation factor
     */
    public void paint(Graphics2D g, int width, int height, double alpha) {
        g.setColor(letterbox());
        g.fillRect(0, 0, width, height);
        viewport.publishOverscan();
        viewport.apply(g);
        renderer.render(g, alpha);
    }

    private Color letterbox() {
        int rgb = renderer.letterboxRgb();
        if (letterboxColor == null || rgb != letterboxRgb) {
            letterboxRgb = rgb;
            letterboxColor = new Color(rgb);
        }
        return letterboxColor;
    }

    @Override
    public void onResize(int width, int height) {
        // The viewport is resized by the screen manager when the event is drained; the buffer
        // strategy follows the canvas size automatically.
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        if (disposed || window.isFullscreen() == fullscreen) {
            return;
        }
        renderSuspended = true;
        try {
            window.setFullscreen(fullscreen);
        } finally {
            renderSuspended = false;
        }
    }

    @Override
    public boolean isFullscreen() {
        return window.isFullscreen();
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    /**
     * Frames actually shown.
     *
     * @return the count
     */
    public long presentCount() {
        return presentCount;
    }

    /**
     * Frames skipped (iconified, suspended, no strategy).
     *
     * @return the count
     */
    public long skippedCount() {
        return skippedCount;
    }

    /**
     * Whether a fullscreen handshake is in progress.
     *
     * @return {@code true} while suspended
     */
    public boolean isRenderSuspended() {
        return renderSuspended;
    }
}
