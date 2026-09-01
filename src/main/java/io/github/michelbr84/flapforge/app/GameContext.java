package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.ScreenManager;

/**
 * Shared services handed to screens and subsystems (M0 set; later milestones add content,
 * profile, audio and settings).
 *
 * @param options the parsed launch options
 * @param clock the monotonic clock
 * @param timeSource the wall clock for the pure packages
 * @param threads the thread owner
 * @param input the input queue
 * @param viewport the loop-owned viewport
 * @param screens the screen stack
 * @param presenter the frame presenter
 * @param window the window, or {@code null} when headless
 * @param loop the game loop
 */
public record GameContext(LaunchOptions options, Clock clock, TimeSource timeSource,
        Threads threads, InputQueue input, Viewport viewport, ScreenManager screens,
        FramePresenter presenter, GameWindow window, GameLoop loop) {

    /**
     * Whether the application runs without a window.
     *
     * @return {@code true} when headless
     */
    public boolean isHeadless() {
        return window == null;
    }

    /** Asks the application to quit cleanly. */
    public void requestQuit() {
        screens.requestClose();
    }
}
