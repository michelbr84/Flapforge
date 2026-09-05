package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;

/**
 * The platform seam of a windowed launch (M10, D8): everything {@link GameApplication} needs
 * from the machine it runs on, and nothing it can build itself.
 *
 * <p>The application owns the order of operations — content, settings, profile, then window,
 * presenter and bridge, then the loop thread — and the host owns the toolkit behind each of the
 * three objects. {@code AwtHost} is the desktop: a {@code GameWindow} on the event-dispatch
 * thread, a {@code BufferStrategyPresenter} and an {@code AwtInputBridge}. The Android host
 * builds the same three around a view, and the transformed game runs unchanged on top of it.
 * The refresh rate lives here too, because reading it is a toolkit round trip the application
 * caches ({@link GameApplication#detectRefreshRate()}).
 *
 * <p>A host is stateless from the application's point of view: it is asked for each object once
 * per launch and never told when they are disposed — the application calls {@code dispose()} on
 * the objects themselves, in the shutdown order D4 fixes.
 */
public interface GameHost {

    /**
     * Creates and shows the window, or reports that there is no display to show it on.
     *
     * @param title the window title
     * @param requestedScale the {@code --scale} value, or {@code null} for the host's default
     * @param fullscreen whether to start in fullscreen
     * @return the window, or {@code null} when no display is available (a headless desktop JVM,
     *     an {@code AWTError} from the toolkit)
     */
    AppWindow createWindow(String title, Integer requestedScale, boolean fullscreen);

    /**
     * Creates the presenter that shows the renderer's frames on the window (D24).
     *
     * @param window a window this host created
     * @param viewport the loop-owned viewport
     * @param renderer the frame renderer
     * @return the presenter
     */
    FramePresenter createPresenter(AppWindow window, Viewport viewport, FrameRenderer renderer);

    /**
     * Creates the bridge that turns the host's input events into {@code RawInput} on the queue.
     *
     * @param queue the queue
     * @return the bridge, not yet attached
     */
    InputBridge createInputBridge(InputQueue queue);

    /**
     * Refresh rate of the display the window is shown on.
     *
     * @return the rate in Hz, or {@code 0} when unknown (the caller falls back to
     *     {@link FrameLimiter#DEFAULT_FPS} through {@link FrameLimiter#refreshRateOrDefault(int)})
     */
    int displayRefreshRateHz();
}
