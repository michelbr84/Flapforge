package io.github.michelbr84.flapforge.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The view the game draws into on Android (M10, P2): a {@link SurfaceView} whose software
 * canvas the {@link SurfacePresenter} paints from the {@code flapforge-loop} thread, and the one
 * owner of the surface's lifecycle state.
 *
 * <p>A {@code Surface} exists only between {@code surfaceCreated} and {@code surfaceDestroyed},
 * both delivered on the UI thread, and Android requires that no other thread touches it once
 * {@code surfaceDestroyed} has returned. The view therefore keeps an {@code alive} flag guarded
 * by a lock that {@link #draw(FrameDrawer)} holds for the whole of one frame: a
 * {@code surfaceDestroyed} that arrives mid-frame blocks until that frame is posted, and a frame
 * that starts after it finds the flag down and is skipped. Holding the lock in the view rather
 * than in the presenter means there is exactly one copy of the state and no window between
 * "read the current state" and "start listening" in which a presenter created after
 * {@code surfaceCreated} (which is always the case: the game boots only once a sized surface
 * exists) could miss a transition.
 *
 * <p>The current size is what {@link AndroidWindow#canvasWidth()} reports and what the
 * {@link AndroidInputBridge} turns into {@code Resized} events, so the loop-owned viewport
 * follows the surface exactly as it follows the AWT canvas on the desktop. Listeners hear about
 * the lifecycle on the UI thread: {@link MainActivity} uses the first sized surface to start the
 * game, the bridge uses every later size to queue a {@code Resized}.
 *
 * <p>Touches reach the bridge through {@link #setTouchHandler(View.OnTouchListener)} rather
 * than {@link #setOnTouchListener}: the bridge installs its handler on the game's boot thread
 * and removes it on the loop thread, while the framework reads {@code View}'s own listener
 * field on the UI thread without any synchronisation, so a listener set there is visible to
 * the next touch by practice only. The view's slot is volatile, which makes the install (and
 * the removal) visible to the very next dispatch under the memory model, and
 * {@link #onTouchEvent(MotionEvent)} routes every event through it on the UI thread.
 */
public final class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    /** Paints one frame into a canvas locked on the live surface (the presenter). */
    public interface FrameDrawer {

        /**
         * Draws the frame.
         *
         * @param canvas the locked canvas, in surface pixels
         * @param width the surface width in pixels
         * @param height the surface height in pixels
         */
        void draw(Canvas canvas, int width, int height);
    }

    /** Hears the surface lifecycle; every call arrives on the UI thread. */
    public interface SurfaceListener {

        /**
         * The surface exists and has this size (delivered for every {@code surfaceChanged},
         * which the platform guarantees at least once after {@code surfaceCreated}).
         *
         * @param width the surface width in pixels
         * @param height the surface height in pixels
         */
        void surfaceSized(int width, int height);

        /** The surface is gone; every draw until the next {@code surfaceSized} is skipped. */
        default void surfaceGone() {
        }
    }

    private final Object surfaceLock = new Object();
    private final List<SurfaceListener> listeners = new CopyOnWriteArrayList<>();
    /** Written under {@link #surfaceLock}; volatile so the accessor needs no lock. */
    private volatile boolean surfaceAlive;
    private volatile int surfaceWidth;
    private volatile int surfaceHeight;
    /** The bridge's handler; volatile so any thread's install is seen by the UI thread's dispatch. */
    private volatile View.OnTouchListener touchHandler;

    /**
     * Creates the view and registers it as its own holder callback.
     *
     * @param context the activity
     */
    public GameSurfaceView(Context context) {
        super(context);
        SurfaceHolder holder = getHolder();
        // A software canvas over an 8-bit-per-channel surface: the shim draws anti-aliased
        // shapes and blends sprites, which RGB_565 would band.
        holder.setFormat(PixelFormat.RGBA_8888);
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    /**
     * Adds a lifecycle listener.
     *
     * @param listener the listener
     */
    public void addSurfaceListener(SurfaceListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes a lifecycle listener; a no-op when it was not added.
     *
     * @param listener the listener
     */
    public void removeSurfaceListener(SurfaceListener listener) {
        listeners.remove(listener);
    }

    /**
     * Installs the handler every touch event is routed to, or removes it with {@code null}. Any
     * thread (see the class javadoc); the handler itself always runs on the UI thread, inside
     * {@link #onTouchEvent(MotionEvent)}.
     *
     * @param handler the handler, or {@code null} to route touches to the platform default
     */
    public void setTouchHandler(View.OnTouchListener handler) {
        touchHandler = handler;
    }

    /**
     * The installed touch handler.
     *
     * @return the handler, or {@code null} when none is installed
     */
    public View.OnTouchListener touchHandler() {
        return touchHandler;
    }

    /**
     * Routes the event to the installed handler; without one, or when it declines the event,
     * the platform default applies (the view is not clickable, so the event is not consumed).
     */
    // The view is a game surface, not a widget: it has no click semantics to mirror for
    // accessibility services, so the ClickableViewAccessibility advice does not apply.
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        View.OnTouchListener handler = touchHandler;
        if (handler != null && handler.onTouch(this, event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Whether a surface exists right now.
     *
     * @return {@code true} between {@code surfaceCreated} and {@code surfaceDestroyed}
     */
    public boolean isSurfaceAlive() {
        return surfaceAlive;
    }

    /**
     * Width of the last {@code surfaceChanged}, in pixels.
     *
     * @return pixels, {@code 0} before the first {@code surfaceChanged}
     */
    public int surfaceWidth() {
        return surfaceWidth;
    }

    /**
     * Height of the last {@code surfaceChanged}, in pixels.
     *
     * @return pixels, {@code 0} before the first {@code surfaceChanged}
     */
    public int surfaceHeight() {
        return surfaceHeight;
    }

    /**
     * Locks the surface's canvas, hands it to the drawer and posts it, all under the lifecycle
     * lock so a concurrent {@code surfaceDestroyed} waits for the frame to finish.
     *
     * <p>Called from the loop thread. A dead surface, a surface without a size and a holder that
     * refuses to lock (the surface is being torn down, or a test double without a real surface)
     * all answer {@code false} without touching the drawer.
     *
     * @param drawer the frame painter
     * @return {@code true} when a frame was drawn and posted
     */
    public boolean draw(FrameDrawer drawer) {
        Objects.requireNonNull(drawer, "drawer");
        synchronized (surfaceLock) {
            int width = surfaceWidth;
            int height = surfaceHeight;
            if (!surfaceAlive || width <= 0 || height <= 0) {
                return false;
            }
            SurfaceHolder holder = getHolder();
            Canvas canvas = holder.lockCanvas();
            if (canvas == null) {
                return false;
            }
            try {
                drawer.draw(canvas, width, height);
            } finally {
                holder.unlockCanvasAndPost(canvas);
            }
            return true;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (surfaceLock) {
            surfaceAlive = true;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Both under the lock draw() reads them with: a frame must never see a torn pair
        // (new width, old height) on a platform-forced resize.
        synchronized (surfaceLock) {
            surfaceWidth = width;
            surfaceHeight = height;
        }
        for (SurfaceListener listener : listeners) {
            listener.surfaceSized(width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Blocks while a frame is in flight: after this returns the loop thread never touches
        // the surface again, which is the contract surfaceDestroyed asks for.
        synchronized (surfaceLock) {
            surfaceAlive = false;
        }
        for (SurfaceListener listener : listeners) {
            listener.surfaceGone();
        }
    }
}
