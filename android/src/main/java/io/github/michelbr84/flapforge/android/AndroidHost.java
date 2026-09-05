package io.github.michelbr84.flapforge.android;

import android.app.Activity;
import android.view.Display;
import io.github.michelbr84.flapforge.app.AppWindow;
import io.github.michelbr84.flapforge.app.FramePresenter;
import io.github.michelbr84.flapforge.app.GameHost;
import io.github.michelbr84.flapforge.app.InputBridge;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import java.util.Objects;

/**
 * The Android {@link GameHost} (M10, D8, P2): an {@link AndroidWindow} over the activity's
 * {@link GameSurfaceView}, a {@link SurfacePresenter} on its canvas and an
 * {@link AndroidInputBridge} on its touch events. The transformed {@code GameApplication} runs
 * unchanged on top of these three, exactly as it runs on {@code AwtHost} on the desktop.
 *
 * <p>Unlike the desktop host this one does not create anything: the activity built the view in
 * {@code onCreate}, and the platform decides its size and shows it fullscreen. So the title,
 * the {@code --scale} and the fullscreen flag {@code createWindow} receives are ignored — there
 * is no title bar to put the text on, no window to scale (the viewport letterboxes the
 * playfield into whatever the surface measures) and no windowed mode to leave. The host keeps
 * the last bridge it built so the activity can route the events only it can see (back gesture,
 * pause, destroy) into the queue.
 */
public final class AndroidHost implements GameHost {

    private final Activity activity;
    private final GameSurfaceView view;
    private volatile AndroidInputBridge bridge;

    /**
     * Creates the host.
     *
     * @param activity the activity, asked for its display
     * @param view the view the game draws into
     */
    public AndroidHost(Activity activity, GameSurfaceView view) {
        this.activity = Objects.requireNonNull(activity, "activity");
        this.view = Objects.requireNonNull(view, "view");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Answers the activity's view; every parameter is ignored (see the class javadoc). Never
     * {@code null}: an Android activity always has a display.
     */
    @Override
    public AppWindow createWindow(String title, Integer requestedScale, boolean fullscreen) {
        return new AndroidWindow(view);
    }

    @Override
    public FramePresenter createPresenter(AppWindow window, Viewport viewport,
            FrameRenderer renderer) {
        return new SurfacePresenter(androidWindow(window), viewport, renderer);
    }

    @Override
    public InputBridge createInputBridge(InputQueue queue) {
        AndroidInputBridge created = new AndroidInputBridge(queue);
        bridge = created;
        return created;
    }

    /**
     * The bridge of the last {@link #createInputBridge(InputQueue)}, through which the activity
     * feeds the queue.
     *
     * @return the bridge, or {@code null} before the application asked for one
     */
    public AndroidInputBridge inputBridge() {
        return bridge;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The refresh rate of the display the activity is on, rounded to whole hertz (a 59.94 Hz
     * panel is 60). A context without a display, or any other failure of the query, answers
     * {@code 0} and the application falls back to its default.
     */
    @Override
    public int displayRefreshRateHz() {
        try {
            Display display = activity.getDisplay();
            return display == null ? 0 : Math.max(0, Math.round(display.getRefreshRate()));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static AndroidWindow androidWindow(AppWindow window) {
        Objects.requireNonNull(window, "window");
        if (window instanceof AndroidWindow androidWindow) {
            return androidWindow;
        }
        throw new IllegalArgumentException("AndroidHost needs the AndroidWindow it created, not "
                + window.getClass().getName());
    }
}
