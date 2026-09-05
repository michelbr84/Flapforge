package io.github.michelbr84.flapforge.android;

import android.graphics.Canvas;
import awt.Color;
import awt.Graphics2D;
import io.github.michelbr84.flapforge.app.FramePresenter;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import java.util.Objects;

/**
 * {@link FramePresenter} drawing through the {@link GameSurfaceView}'s software canvas (M10,
 * P2): the Android counterpart of the desktop {@code BufferStrategyPresenter}, with the same
 * frame contract and the same {@link #paint(Graphics2D, int, int, double)} body.
 *
 * <p>{@link #present(double)} locks the surface canvas, paints, and posts it; it skips (and
 * counts the skip) while disposed, while no surface exists, before the surface has a size, and
 * when the holder refuses to lock. The surface lifecycle lock lives in the view — see
 * {@link GameSurfaceView#draw(GameSurfaceView.FrameDrawer)} — so a {@code surfaceDestroyed} on
 * the UI thread blocks until the frame in flight on the loop thread is posted, and the loop
 * never draws on a dead surface. The letterbox is filled with the renderer's colour (cached by
 * value, D18: no per-frame allocation), then the viewport transform and clip are applied and
 * the renderer draws the logical playfield, exactly as on the desktop.
 *
 * <p>Every present, drawn or skipped, first runs the host's frame hook on the loop thread: the
 * loop calls no other host code per frame, so the hook is where the {@link AndroidHost} looks at
 * loop-owned state (the screen stack) on the only thread allowed to. It runs before the frame
 * so what it samples is at most one frame old at the next touch.
 *
 * <p>There is no fullscreen handshake on Android — the activity is immersive from
 * {@code onCreate} on — so the presenter reports fullscreen and ignores requests to change it,
 * and there is no resize to react to either: the view tracks the surface size itself and the
 * screen manager resizes the viewport when the {@code Resized} event is drained.
 */
public final class SurfacePresenter implements FramePresenter, GameSurfaceView.FrameDrawer {

    private final GameSurfaceView view;
    private final Viewport viewport;
    private final FrameRenderer renderer;
    private final Runnable frameHook;
    private volatile boolean disposed;
    private volatile long presentCount;
    private volatile long skippedCount;
    /** The alpha of the frame being presented, handed to {@link #draw} without a lambda. */
    private double frameAlpha;
    private int letterboxRgb = -1;
    private Color letterboxColor;

    /**
     * Creates a presenter.
     *
     * @param window the window whose surface is drawn
     * @param viewport the loop-owned viewport
     * @param renderer the frame renderer
     * @param frameHook run on the loop thread at the start of every {@link #present(double)},
     *     drawn or skipped (see the class javadoc)
     */
    public SurfacePresenter(AndroidWindow window, Viewport viewport, FrameRenderer renderer,
            Runnable frameHook) {
        this.view = Objects.requireNonNull(window, "window").view();
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.frameHook = Objects.requireNonNull(frameHook, "frameHook");
    }

    @Override
    public void present(double alpha) {
        frameHook.run();
        if (disposed) {
            skippedCount++;
            return;
        }
        frameAlpha = alpha;
        if (view.draw(this)) {
            presentCount++;
        } else {
            skippedCount++;
        }
    }

    /**
     * The {@link GameSurfaceView.FrameDrawer} half of a present: wraps the locked canvas in the
     * shim's {@link Graphics2D} and paints. Only {@link #present(double)} calls this.
     */
    @Override
    public void draw(Canvas canvas, int width, int height) {
        Graphics2D g = new Graphics2D(canvas);
        try {
            paint(g, width, height, frameAlpha);
        } finally {
            g.dispose();
        }
    }

    /**
     * Draws one frame into an arbitrary context of the given size (also what the tests render
     * into an image).
     *
     * @param g the context in surface coordinates
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
        // The viewport is resized by the screen manager when the event is drained; the view
        // already tracks the surface size the next present will use.
    }

    /**
     * {@inheritDoc}
     *
     * <p>Ignored on Android: the activity is immersive fullscreen for its whole life.
     */
    @Override
    public void setFullscreen(boolean fullscreen) {
        // Intentionally empty (see the javadoc).
    }

    @Override
    public boolean isFullscreen() {
        return true;
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    /**
     * Frames actually drawn and posted.
     *
     * @return the count
     */
    public long presentCount() {
        return presentCount;
    }

    /**
     * Frames skipped (disposed, no surface, no size, canvas not lockable).
     *
     * @return the count
     */
    public long skippedCount() {
        return skippedCount;
    }
}
