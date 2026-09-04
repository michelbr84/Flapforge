package io.github.michelbr84.flapforge.app;

/**
 * Presents one rendered frame per loop iteration (D24). The window implementation wraps a
 * buffer strategy; {@link NullPresenter} counts calls for tests and headless runs.
 */
public interface FramePresenter {

    /**
     * Renders and shows one frame.
     *
     * @param alpha interpolation factor in {@code [0, 1)}
     */
    void present(double alpha);

    /**
     * Notifies the presenter that the canvas was resized (called from the loop thread when the
     * corresponding event is drained).
     *
     * @param width the new width in window pixels
     * @param height the new height in window pixels
     */
    void onResize(int width, int height);

    /**
     * Enters or leaves borderless fullscreen (called from the loop thread).
     *
     * @param fullscreen the desired state
     */
    void setFullscreen(boolean fullscreen);

    /**
     * Current fullscreen state. The presenter is the source of truth: a window created with
     * {@code --fullscreen} reports {@code true} before anyone toggled it.
     *
     * @return {@code true} when fullscreen
     */
    boolean isFullscreen();

    /** Releases resources; later calls to {@link #present(double)} are ignored. */
    void dispose();
}
