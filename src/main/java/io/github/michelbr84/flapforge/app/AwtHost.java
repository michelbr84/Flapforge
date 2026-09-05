package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import java.awt.AWTError;
import java.awt.DisplayMode;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.util.Objects;

/**
 * The desktop {@link GameHost} (M10, D8): a {@link GameWindow} built on the event-dispatch
 * thread, a {@link BufferStrategyPresenter} on its canvas and an {@link AwtInputBridge} on the
 * keyboard focus manager.
 *
 * <p>This is a desktop-only file, excluded from the Android source transform together with the
 * four AWT-backed classes it wires up: it is the one place besides them that may name
 * {@code java.awt.AWTError}, {@code DisplayMode}, {@code GraphicsEnvironment} and
 * {@code HeadlessException}, so that {@link GameApplication} itself compiles against the
 * Android shims without ever seeing a toolkit type.
 */
public final class AwtHost implements GameHost {

    /** Creates the desktop host. */
    public AwtHost() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>The scale defaults to {@link GameWindow#defaultScale()} (the largest integer scale whose
     * decorated window fits the usable screen height, D3). A JVM without a display answers
     * {@code null}: both the {@code HeadlessException} of a headless toolkit and the
     * {@code AWTError} a toolkit that could not initialise throws are swallowed here, and the
     * caller prints the hint.
     */
    @Override
    public AppWindow createWindow(String title, Integer requestedScale, boolean fullscreen) {
        try {
            int scale = requestedScale != null ? requestedScale : GameWindow.defaultScale();
            return GameWindow.create(title, scale, fullscreen);
        } catch (HeadlessException | AWTError e) {
            return null;
        }
    }

    @Override
    public FramePresenter createPresenter(AppWindow window, Viewport viewport,
            FrameRenderer renderer) {
        return new BufferStrategyPresenter(gameWindow(window), viewport, renderer);
    }

    @Override
    public InputBridge createInputBridge(InputQueue queue) {
        return new AwtInputBridge(queue);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Asks the default screen device for its display mode — an XRandR round trip on Linux,
     * which is why the application caches the answer. A headless JVM, a {@code HeadlessException}
     * and a device that does not know its rate ({@link DisplayMode#REFRESH_RATE_UNKNOWN}) all
     * report {@code 0}.
     */
    @Override
    public int displayRefreshRateHz() {
        if (GraphicsEnvironment.isHeadless()) {
            return 0;
        }
        try {
            DisplayMode mode = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDisplayMode();
            return mode.getRefreshRate();
        } catch (HeadlessException e) {
            return 0;
        }
    }

    /**
     * The window as the {@link GameWindow} this host created.
     *
     * @param window the window handed back by the application
     * @return the same window, typed
     * @throws IllegalArgumentException when the window did not come from this host
     */
    private static GameWindow gameWindow(AppWindow window) {
        Objects.requireNonNull(window, "window");
        if (window instanceof GameWindow gameWindow) {
            return gameWindow;
        }
        throw new IllegalArgumentException("AwtHost needs the GameWindow it created, not "
                + window.getClass().getName());
    }
}
