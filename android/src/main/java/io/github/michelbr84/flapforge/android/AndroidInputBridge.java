package io.github.michelbr84.flapforge.android;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import io.github.michelbr84.flapforge.app.AppWindow;
import io.github.michelbr84.flapforge.app.InputBridge;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.HudRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import java.util.Objects;

/**
 * Translates the view's touch events and the surface's size changes into {@link RawInput}
 * records on the {@link InputQueue} (D2, M10, P2/P3): the Android {@link InputBridge}, built by
 * {@link AndroidHost}.
 *
 * <p><b>The finger is the mouse (P2).</b> {@code ACTION_DOWN} queues a {@code MouseMove} to the
 * touched pixel and then a {@code MouseDown(BUTTON_LEFT)} — the desktop pointer always moves
 * before it clicks, and the screens rely on the position being current when the press edge is
 * seen. The press goes out on the {@code ACTION_DOWN} itself, never after a gesture has been
 * classified: a run flaps on the press edge, and a flap that waits for a classifier is a flap
 * that comes late. {@code ACTION_MOVE} is a {@code MouseMove}; {@code ACTION_UP} and
 * {@code ACTION_CANCEL} both release, so a touch the system takes away (a gesture, a
 * notification shade) never leaves a button held. Coordinates are surface pixels; the screen
 * manager maps them to the logical playfield through the viewport, exactly as it maps canvas
 * pixels on the desktop. The surface size is queued as {@code Resized} once at
 * {@link #attach(AppWindow)} and again on every {@code surfaceChanged}, the contract every
 * bridge honours.
 *
 * <p><b>Gestures (P3).</b> Three, all resolved here without touching the queue's contract:
 * <ul>
 *   <li><em>A vertical drag is the wheel.</em> Once the first finger has travelled more than
 *       the touch slop, mostly vertically, its vertical displacement from the press point is
 *       turned into {@code Wheel} notches, one per {@value #DRAG_PX_PER_NOTCH} surface pixels;
 *       what goes out is the growth of the truncated total, so the remainder is never lost and
 *       dragging back un-scrolls. Sign, derived from the screens: every scrollable screen
 *       applies {@code scroll -= wheel * WHEEL_STEP} and draws its rows at {@code y - scroll},
 *       so a larger {@code scroll} shows what lies below and a <em>negative</em> notch scrolls
 *       down. A finger moving up ({@code y} decreasing) must carry the content up with it —
 *       reveal what lies below — so the notch is {@code (y - downY) / DRAG_PX_PER_NOTCH}:
 *       negative when the finger goes up, exactly the notch the desktop gets from the wheel
 *       rolled away from the user. A drag that goes mostly sideways first is locked as
 *       horizontal for the rest of the touch and never scrolls: a slider knob is dragged, not
 *       the list under it. {@code MouseMove}s keep flowing during a drag.</li>
 *   <li><em>A second finger is the ability.</em> An {@code ACTION_POINTER_DOWN} while the
 *       first finger is down presses {@code BUTTON_RIGHT} at the second finger's pixel and its
 *       {@code ACTION_POINTER_UP} releases it there; the first finger keeps
 *       {@code BUTTON_LEFT} held throughout, and a first finger that lifts before the second
 *       releases its own button and leaves the other held. Only one extra finger counts, a
 *       finger added after the first one left is ignored, and a second finger is ignored while
 *       the first is itself the badge press below (the queue would drop a second
 *       {@code BUTTON_RIGHT} press anyway).</li>
 *   <li><em>The HUD badge is the ability.</em> A first-finger press whose logical position lies
 *       within {@link HudRenderer#ABILITY_RADIUS} + {@value #BADGE_TOUCH_MARGIN} of the badge
 *       centre ({@link HudRenderer#ABILITY_CX}, {@link HudRenderer#ABILITY_CY}) is
 *       {@code BUTTON_RIGHT} instead of {@code BUTTON_LEFT}, press and release alike. The zone
 *       exists only while {@linkplain #isRunActive() a run is the active screen}: the tab bars
 *       of the shop, the upgrades and the achievements, the bird roster and the challenge list
 *       all begin inside that corner, and an unconditional zone would swallow their first item;
 *       the pause and game-over overlays, which resume or retry on any left click, sit on top
 *       of the run and switch the zone off too. The host samples the screen stack on the loop
 *       thread and flips the flag through {@link #setRunActive(boolean)}. The viewport is the
 *       loop's, read here without a lock: at worst a press mapped through the size from one
 *       resize ago.</li>
 * </ul>
 *
 * <p><b>Clicks resolve on the press edge.</b> {@code FocusRing}, {@code ListView},
 * {@code TabBar} and {@code Slider} all act on {@code isMouseJustPressed(BUTTON_LEFT)}, never
 * on the release, so a drag that starts on a list item has selected it before it scrolls, and
 * one that starts on a slider knob nudges the knob; the release after a drag is a plain
 * {@code MouseUp} at the lift position. Moving the menus to click-on-release is a desktop-side
 * decision this bridge does not make.
 *
 * <p>The activity feeds the events the view cannot see through the helpers: {@link #key(int)}
 * (the system back gesture, as an {@code ESCAPE} tap), {@link #focusLost()} ({@code onPause}),
 * {@link #iconified(boolean)} ({@code onStop} / {@code onStart}) and {@link #closeRequested()}
 * ({@code onDestroy}). They write to the queue whether or not the bridge is attached; the queue
 * is the game's, and the loop drains it. Every touch callback runs on the UI thread, the only
 * thread that touches the gesture state.
 */
public final class AndroidInputBridge implements InputBridge {

    /**
     * Surface pixels of vertical drag per wheel notch. A notch scrolls {@code WHEEL_STEP} (28 to
     * 30 logical pixels) on every scrollable screen, which at the 2x scale of a 840x1280 surface
     * is 56 to 60 surface pixels of content per 24 of finger: the list travels a little faster
     * than the finger, the feel of a flung list, while one notch still takes a deliberate move.
     */
    public static final int DRAG_PX_PER_NOTCH = 24;
    /** Logical pixels added to the badge radius for the touch target: a fingertip is no pointer. */
    public static final int BADGE_TOUCH_MARGIN = 10;
    /** A pointer slot that holds no pointer. */
    private static final int NO_POINTER = -1;

    /** How the first finger's travel was classified, once it left the slop. */
    private enum Drag { UNDECIDED, VERTICAL, HORIZONTAL }

    private final InputQueue queue;
    private final Viewport viewport;
    private final int touchSlop;
    private final View.OnTouchListener touchHandler;
    private final GameSurfaceView.SurfaceListener surfaceHandler;
    /** Guarded by {@code this}; the listeners it registers are removed on {@link #detach()}. */
    private GameSurfaceView view;
    private volatile boolean attached;
    /** Written by the host on the loop thread, read on the UI thread at every press. */
    private volatile boolean runActive;

    // Gesture state; UI thread only.
    private int primaryPointer = NO_POINTER;
    private int primaryButton = Keys.BUTTON_LEFT;
    private int secondaryPointer = NO_POINTER;
    private float downX;
    private float downY;
    private Drag drag = Drag.UNDECIDED;
    /** Notches already queued for the current drag, so only the growth of the total goes out. */
    private int notchesSent;

    /**
     * Creates a bridge feeding the given queue.
     *
     * @param queue the queue
     * @param viewport the loop-owned viewport, which maps a touch onto the playfield for the
     *     ability badge
     * @param touchSlop the platform's touch slop in surface pixels
     *     ({@code ViewConfiguration.getScaledTouchSlop()}): travel within it is a tap, not a drag
     */
    public AndroidInputBridge(InputQueue queue, Viewport viewport, int touchSlop) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        if (touchSlop < 0) {
            throw new IllegalArgumentException("touchSlop must not be negative: " + touchSlop);
        }
        this.touchSlop = touchSlop;
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
     * Tells the bridge whether a run is the active screen, which is when the HUD ability badge
     * is a touch target. The host calls this on the loop thread after sampling the screen stack.
     *
     * @param runActive {@code true} while a {@code GameScreen} is on top of the stack
     */
    public void setRunActive(boolean runActive) {
        this.runActive = runActive;
    }

    /**
     * Whether the ability badge is currently a touch target.
     *
     * @return the last value handed to {@link #setRunActive(boolean)}, {@code false} initially
     */
    public boolean isRunActive() {
        return runActive;
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

    /**
     * Queues an {@code Iconified}: the activity was stopped (no longer visible) or started
     * again, which the game treats like a desktop window minimised and restored — a live run
     * pauses, the menu's attract demo waits.
     *
     * @param iconified {@code true} when the activity is stopped
     */
    public void iconified(boolean iconified) {
        queue.offer(new RawInput.Iconified(iconified));
    }

    /** Queues a {@code CloseRequested}: the activity is being destroyed and the game must quit. */
    public void closeRequested() {
        queue.offer(new RawInput.CloseRequested());
    }

    private boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> press(event);
            case MotionEvent.ACTION_POINTER_DOWN -> pressSecondary(event);
            case MotionEvent.ACTION_MOVE -> move(event);
            case MotionEvent.ACTION_POINTER_UP -> liftOne(event);
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> liftAll(event);
            default -> {
                // Hover, scroll and button events of a pointing device: consumed, unused.
            }
        }
        return true;
    }

    /** The first finger: the left button, or the right one on the HUD ability badge. */
    private void press(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        primaryPointer = event.getPointerId(0);
        primaryButton = onAbilityBadge(x, y) ? Keys.BUTTON_RIGHT : Keys.BUTTON_LEFT;
        secondaryPointer = NO_POINTER;
        downX = event.getX();
        downY = event.getY();
        drag = Drag.UNDECIDED;
        notchesSent = 0;
        queue.offer(new RawInput.MouseMove(x, y));
        queue.offer(new RawInput.MouseDown(primaryButton, x, y));
    }

    /** A second finger while the first holds the left button: the right button at its pixel. */
    private void pressSecondary(MotionEvent event) {
        if (primaryPointer == NO_POINTER || secondaryPointer != NO_POINTER
                || primaryButton != Keys.BUTTON_LEFT) {
            return;
        }
        int index = event.getActionIndex();
        secondaryPointer = event.getPointerId(index);
        queue.offer(new RawInput.MouseDown(Keys.BUTTON_RIGHT, (int) event.getX(index),
                (int) event.getY(index)));
    }

    /** The first finger moved: a pointer move, and the wheel once the drag is vertical. */
    private void move(MotionEvent event) {
        int index = event.findPointerIndex(primaryPointer);
        if (index < 0) {
            // Only the second finger is left; the pointer stays where the first one lifted.
            return;
        }
        float x = event.getX(index);
        float y = event.getY(index);
        queue.offer(new RawInput.MouseMove((int) x, (int) y));
        if (drag == Drag.UNDECIDED) {
            drag = classify(x - downX, y - downY);
        }
        if (drag == Drag.VERTICAL) {
            // Truncation toward zero on both sides of the press point (see the class javadoc).
            int total = (int) ((y - downY) / DRAG_PX_PER_NOTCH);
            if (total != notchesSent) {
                queue.offer(new RawInput.Wheel(total - notchesSent));
                notchesSent = total;
            }
        }
    }

    private Drag classify(float dx, float dy) {
        float ax = Math.abs(dx);
        float ay = Math.abs(dy);
        if (ay > touchSlop && ay > ax) {
            return Drag.VERTICAL;
        }
        if (ax > touchSlop) {
            return Drag.HORIZONTAL;
        }
        return Drag.UNDECIDED;
    }

    /** One of two fingers lifted: release its button where it lifted, keep the other held. */
    private void liftOne(MotionEvent event) {
        int index = event.getActionIndex();
        int id = event.getPointerId(index);
        int x = (int) event.getX(index);
        int y = (int) event.getY(index);
        if (id == secondaryPointer) {
            secondaryPointer = NO_POINTER;
            queue.offer(new RawInput.MouseUp(Keys.BUTTON_RIGHT, x, y));
        } else if (id == primaryPointer) {
            primaryPointer = NO_POINTER;
            drag = Drag.UNDECIDED;
            queue.offer(new RawInput.MouseUp(primaryButton, x, y));
        }
    }

    /** The last finger lifted, or the touch was cancelled: release whatever is still held. */
    private void liftAll(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        if (secondaryPointer != NO_POINTER) {
            queue.offer(new RawInput.MouseUp(Keys.BUTTON_RIGHT, x, y));
        }
        if (primaryPointer != NO_POINTER) {
            queue.offer(new RawInput.MouseUp(primaryButton, x, y));
        }
        primaryPointer = NO_POINTER;
        secondaryPointer = NO_POINTER;
        drag = Drag.UNDECIDED;
    }

    private boolean onAbilityBadge(int x, int y) {
        if (!runActive) {
            return false;
        }
        Vec2 logical = viewport.toLogical(x, y);
        double dx = logical.x() - HudRenderer.ABILITY_CX;
        double dy = logical.y() - HudRenderer.ABILITY_CY;
        double reach = HudRenderer.ABILITY_RADIUS + BADGE_TOUCH_MARGIN;
        return dx * dx + dy * dy <= reach * reach;
    }
}
