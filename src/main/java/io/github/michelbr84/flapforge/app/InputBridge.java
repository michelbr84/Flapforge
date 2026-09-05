package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.input.InputQueue;

/**
 * Feeds a host's input events into the {@link InputQueue} as {@code RawInput} records (D2,
 * M10): the desktop bridge listens to the AWT toolkit, the Android bridge to the view's touch
 * and key callbacks. {@link GameApplication} only attaches it to the window it got from the
 * {@link GameHost} before the loop starts and detaches it on the loop thread during shutdown.
 *
 * <p>The contract every bridge honours: {@link #attach(AppWindow)} ends by queueing a synthetic
 * {@code Resized} with the current canvas size, so the first tick reconciles the loop-owned
 * viewport with whatever happened between window creation and attachment; a second attach is a
 * no-op; {@link #detach()} without an attach is a no-op too.
 */
public interface InputBridge {

    /**
     * Registers the bridge on the window and queues a {@code Resized} with the current canvas
     * size.
     *
     * @param window the window the {@link GameHost} that built this bridge also created
     * @throws IllegalArgumentException when the window is not one this bridge can listen to
     */
    void attach(AppWindow window);

    /** Removes the bridge from the window it was attached to; a no-op when not attached. */
    void detach();

    /**
     * Whether {@link #attach(AppWindow)} succeeded and {@link #detach()} was not called.
     *
     * @return {@code true} when attached
     */
    boolean isAttached();
}
