package io.github.michelbr84.flapforge.android;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import io.github.michelbr84.flapforge.app.AppWindow;
import io.github.michelbr84.flapforge.app.InputBridge;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import java.util.Objects;

/**
 * Translates the view's touch events and the surface's size changes into {@link RawInput}
 * records on the {@link InputQueue} (D2, M10, P2): the Android {@link InputBridge}, built by
 * {@link AndroidHost}.
 *
 * <p>A finger is the left mouse button. {@code ACTION_DOWN} queues a {@code MouseMove} to the
 * touched pixel and then a {@code MouseDown(BUTTON_LEFT)} — the desktop pointer always moves
 * before it clicks, and the screens rely on the position being current when the press edge is
 * seen; {@code ACTION_MOVE} is a {@code MouseMove}; {@code ACTION_UP} and {@code ACTION_CANCEL}
 * both release the button, so a touch the system takes away (a gesture, a notification shade)
 * never leaves the flap held. Coordinates are surface pixels; the screen manager maps them to
 * the logical playfield through the viewport, exactly as it maps canvas pixels on the desktop.
 * The surface size is queued as {@code Resized} once at {@link #attach(AppWindow)} and again on
 * every {@code surfaceChanged}, the contract every bridge honours.
 *
 * <p>The activity feeds the events the view cannot see through the three helpers:
 * {@link #key(int)} (the system back gesture, as an {@code ESCAPE} tap), {@link #focusLost()}
 * ({@code onPause}) and {@link #closeRequested()} ({@code onDestroy}). They write to the queue
 * whether or not the bridge is attached; the queue is the game's, and the loop drains it.
 *
 * <p>TODO(P3): gestures. A vertical drag should become {@code Wheel} notches for the lists
 * and the shop, and a second finger (or an on-screen button) the {@code ABILITY} action
 * ({@code BUTTON_RIGHT}); today {@code ACTION_POINTER_DOWN}/{@code ACTION_POINTER_UP} are
 * consumed and ignored, and only the primary pointer's position is read. That work lands in
 * {@link #onTouch(View, MotionEvent)} without touching the queue's contract.
 */
public final class AndroidInputBridge implements InputBridge {

    private final InputQueue queue;
    private final View.OnTouchListener touchHandler;
    private final GameSurfaceView.SurfaceListener surfaceHandler;
    /** Guarded by {@code this}; the listeners it registers are removed on {@link #detach()}. */
    private GameSurfaceView view;
    private volatile boolean attached;

    /**
     * Creates a bridge feeding the given queue.
     *
     * @param queue the queue
     */
    public AndroidInputBridge(InputQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.touchHandler = this::onTouch;
        this.surfaceHandler = (width, height) -> queue.offer(new RawInput.Resized(width, height));
    }

    /**
     * Registers the touch and surface listeners on the window's view and queues a
     * {@code Resized} with the current surface size. Called from the thread that boots the game;
     * the listeners themselves run on the UI thread.
     *
     * @param window the window; it must be the {@link AndroidWindow} the Android host created,
     *     because the listeners go on its view
     * @throws IllegalArgumentException when the window is not an {@link AndroidWindow}
     */
    // The view is a game surface, not a widget: it has no click semantics to mirror for
    // accessibility services, so the ClickableViewAccessibility advice does not apply.
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public synchronized void attach(AppWindow window) {
        Objects.requireNonNull(window, "window");
        if (!(window instanceof AndroidWindow androidWindow)) {
            throw new IllegalArgumentException("AndroidInputBridge listens to an AndroidWindow, not "
                    + window.getClass().getName());
        }
        if (attached) {
            return;
        }
        GameSurfaceView surface = androidWindow.view();
        surface.setOnTouchListener(touchHandler);
        surface.addSurfaceListener(surfaceHandler);
        view = surface;
        attached = true;
        queue.offer(new RawInput.Resized(surface.surfaceWidth(), surface.surfaceHeight()));
    }

    /** Removes both listeners (called on the loop thread during shutdown). */
    @Override
    public synchronized void detach() {
        if (!attached) {
            return;
        }
        view.setOnTouchListener(null);
        view.removeSurfaceListener(surfaceHandler);
        view = null;
        attached = false;
    }

    @Override
    public boolean isAttached() {
        return attached;
    }

    /**
     * Queues a tap of a key: a {@code KeyDown} and a {@code KeyUp} with the same timestamp. The
     * queue turns the pair into one press edge even when both are drained in the same tick
     * (D2), which is how the system back gesture becomes the {@code ESCAPE} the screens already
     * understand ({@code BACK} on menus, {@code PAUSE} in a run).
     *
     * @param code the key code ({@link Keys})
     */
    public void key(int code) {
        long now = System.currentTimeMillis();
        queue.offer(new RawInput.KeyDown(code, now));
        queue.offer(new RawInput.KeyUp(code, now));
    }

    /**
     * Queues a {@code FocusLost} with the current wall-clock time: the activity is being paused,
     * and the game pauses on focus loss the way it does when the desktop window loses focus.
     */
    public void focusLost() {
        queue.offer(new RawInput.FocusLost(System.currentTimeMillis()));
    }

    /** Queues a {@code CloseRequested}: the activity is being destroyed and the game must quit. */
    public void closeRequested() {
        queue.offer(new RawInput.CloseRequested());
    }

    private boolean onTouch(View v, MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                queue.offer(new RawInput.MouseMove(x, y));
                queue.offer(new RawInput.MouseDown(Keys.BUTTON_LEFT, x, y));
            }
            case MotionEvent.ACTION_MOVE -> queue.offer(new RawInput.MouseMove(x, y));
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    queue.offer(new RawInput.MouseUp(Keys.BUTTON_LEFT, x, y));
            default -> {
                // ACTION_POINTER_DOWN / ACTION_POINTER_UP and the rest: consumed, unused (P3).
            }
        }
        return true;
    }
}
