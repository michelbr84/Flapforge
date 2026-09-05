package io.github.michelbr84.flapforge.android;

import android.app.Activity;
import android.view.Display;
import android.view.ViewConfiguration;
import io.github.michelbr84.flapforge.app.AppWindow;
import io.github.michelbr84.flapforge.app.FramePresenter;
import io.github.michelbr84.flapforge.app.GameHost;
import io.github.michelbr84.flapforge.app.InputBridge;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import java.util.Objects;

/**
 * The Android {@link GameHost} (M10, D8, P2/P3): an {@link AndroidWindow} over the activity's
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
 * pause, stop, destroy) into the queue.
 *
 * <p>The host is also the go-between for the two things the bridge needs from the game and
 * cannot get from a touch event. The viewport arrives with {@link #createPresenter}, which
 * {@code GameApplication.start} calls before {@link #createInputBridge} — the bridge maps a
 * touch onto the playfield with it to recognise the HUD ability badge. And whether a run is on
 * top of the screen stack, which decides whether that badge is a touch target at all, is
 * sampled on the loop thread: the stack belongs to the loop and may only be read there, so the
 * presenter's per-frame hook — the host's one foothold on that thread — reads
 * {@link ScreenManager#top()} and hands the answer to the bridge as a volatile flag. The
 * activity hands the host the {@link ScreenManager} through {@link #observeScreens} once the
 * game has started; until then the flag stays off, which is right, because the boot screen is
 * on top.
 */
public final class AndroidHost implements GameHost {

    private final Activity activity;
    private final GameSurfaceView view;
    private final int touchSlop;
    private volatile Viewport viewport;
    private volatile AndroidInputBridge bridge;
    private volatile ScreenManager screens;

    /**
     * Creates the host.
     *
     * @param activity the activity, asked for its display and its touch slop
     * @param view the view the game draws into
     */
    public AndroidHost(Activity activity, GameSurfaceView view) {
        this.activity = Objects.requireNonNull(activity, "activity");
        this.view = Objects.requireNonNull(view, "view");
        this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
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

    /**
     * {@inheritDoc}
     *
     * <p>Keeps the viewport for the input bridge and gives the presenter the hook that samples
     * the screen stack once per frame (see the class javadoc).
     */
    @Override
    public FramePresenter createPresenter(AppWindow window, Viewport viewport,
            FrameRenderer renderer) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        return new SurfacePresenter(androidWindow(window), viewport, renderer,
                this::sampleActiveScreen);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException when no presenter was created yet: the bridge is built
     *     around the presenter's viewport, and {@code GameApplication.start} creates the
     *     presenter first
     */
    @Override
    public InputBridge createInputBridge(InputQueue queue) {
        Viewport known = viewport;
        if (known == null) {
            throw new IllegalStateException("AndroidHost builds the input bridge around the "
                    + "presenter's viewport; GameApplication creates the presenter first");
        }
        AndroidInputBridge created = new AndroidInputBridge(queue, known, touchSlop);
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
     * Hands the host the screen stack of the started game, so the bridge learns when a run is on
     * top (see the class javadoc). Any thread; the stack itself is only ever read on the loop
     * thread.
     *
     * @param screens the game's screen manager
     */
    public void observeScreens(ScreenManager screens) {
        this.screens = Objects.requireNonNull(screens, "screens");
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

    /** Loop thread, once per present: the one place the host reads the loop-owned stack. */
    private void sampleActiveScreen() {
        ScreenManager stack = screens;
        AndroidInputBridge target = bridge;
        if (stack != null && target != null) {
            target.setRunActive(stack.top() instanceof GameScreen);
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
