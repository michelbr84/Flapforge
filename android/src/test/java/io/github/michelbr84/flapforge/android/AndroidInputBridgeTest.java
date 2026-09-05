package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import awt.image.BufferedImage;
import io.github.michelbr84.flapforge.app.AppWindow;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.HudRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proofs of the {@link AndroidInputBridge} (M10, P2/P3): real {@link MotionEvent}s
 * dispatched to the view arrive on the {@link InputQueue} as the desktop's mouse events with
 * the touched pixel — a tap is the left button, a vertical drag is the wheel with the sign the
 * scrollable screens expect, a second finger and a tap on the HUD ability badge are the right
 * button — the surface size reaches it as {@code Resized}, and the activity's helpers
 * ({@code key}, {@code focusLost}, {@code iconified}, {@code closeRequested}) produce what the
 * screens expect. Everything is observed the way the loop observes it: through
 * {@link InputQueue#nextTick()}.
 *
 * <p>The viewport is the one a 840x1280 surface gets: scale 2, no letterbox, so a logical point
 * is exactly twice its surface pixel and the badge geometry can be checked to the pixel. The
 * touch slop is an explicit {@value #SLOP} rather than the platform's, so the drag proofs do
 * not depend on Robolectric's display density.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AndroidInputBridgeTest {

    private static final int WIDTH = 840;
    private static final int HEIGHT = 1280;
    private static final int SLOP = 16;

    private InputQueue queue;
    private Viewport viewport;
    private GameSurfaceView view;
    private SurfaceHolder holder;
    private AndroidInputBridge bridge;

    @Before
    public void setUp() {
        queue = new InputQueue(KeyBindings.defaults());
        viewport = new Viewport(WIDTH, HEIGHT, false);
        view = new GameSurfaceView(RuntimeEnvironment.getApplication());
        holder = view.getHolder();
        // Robolectric's fake holder never fires the callbacks: deliver them as the platform would.
        view.surfaceCreated(holder);
        view.surfaceChanged(holder, PixelFormat.RGBA_8888, WIDTH, HEIGHT);
        bridge = new AndroidInputBridge(queue, viewport, SLOP);
    }

    private boolean touch(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try {
            return view.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /**
     * Dispatches a multi-pointer event: {@code ids[i]} at {@code (xs[i], ys[i])}, the action's
     * pointer index (for {@code ACTION_POINTER_DOWN} / {@code ACTION_POINTER_UP}) in the action.
     */
    private boolean touch(int action, int actionIndex, int[] ids, float[] xs, float[] ys) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = ids[i];
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xs[i];
            coords[i].y = ys[i];
            coords[i].pressure = 1f;
            coords[i].size = 1f;
        }
        long now = SystemClock.uptimeMillis();
        int fullAction = action | (actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        MotionEvent event = MotionEvent.obtain(now, now, fullAction, ids.length, props, coords,
                0, 0, 1f, 1f, 0, 0, 0, 0);
        try {
            return view.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private void attachAndDrain() {
        bridge.attach(new AndroidWindow(view));
        queue.nextTick();
    }

    private Vec2 surfacePointOfBadge(double logicalDx, double logicalDy) {
        return viewport.toWindow(HudRenderer.ABILITY_CX + logicalDx,
                HudRenderer.ABILITY_CY + logicalDy);
    }

    @Test
    public void attachQueuesTheCurrentSurfaceSizeOnce() {
        bridge.attach(new AndroidWindow(view));
        bridge.attach(new AndroidWindow(view));

        assertTrue(bridge.isAttached());
        InputFrame frame = queue.nextTick();
        assertEquals(List.of(new RawInput.Resized(WIDTH, HEIGHT)), frame.systemEvents());
    }

    @Test
    public void attachAndDetachFromOtherThreadsReachTheUiThreadsNextDispatch() throws Exception {
        // attach() runs on the game's boot thread and detach() on the loop thread, while every
        // touch is dispatched on the UI thread. The bridge therefore installs its handler
        // through the view's volatile slot, never through View.setOnTouchListener, whose
        // listener field the framework reads without synchronisation (visible in practice, not
        // under the memory model).
        onAnotherThread(() -> bridge.attach(new AndroidWindow(view)));
        assertTrue(bridge.isAttached());
        assertNotNull("the handler sits in the view's volatile slot", view.touchHandler());
        assertNull("and not in View's own listener field", shadowOf(view).getOnTouchListener());
        queue.nextTick(); // the attach Resized

        assertTrue(touch(MotionEvent.ACTION_DOWN, 100f, 200f));
        InputFrame down = queue.nextTick();
        assertTrue(down.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertEquals(100, down.mouseX(), 0.0);
        touch(MotionEvent.ACTION_UP, 100f, 200f);
        queue.nextTick();

        onAnotherThread(bridge::detach);
        assertFalse(bridge.isAttached());
        assertNull(view.touchHandler());
        assertFalse("nothing consumes a touch once detached",
                touch(MotionEvent.ACTION_DOWN, 100f, 200f));
        assertEquals(0, queue.pendingCount());
    }

    private static void onAnotherThread(Runnable action) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "flapforge-test-other-thread");
        thread.start();
        thread.join(5_000L);
        assertFalse("the action finished", thread.isAlive());
        if (failure.get() != null) {
            throw new AssertionError("the action failed on the other thread", failure.get());
        }
    }

    @Test
    public void aTouchIsTheLeftButtonAtTheTouchedPixel() {
        attachAndDrain();

        assertTrue(touch(MotionEvent.ACTION_DOWN, 100.7f, 200.2f));
        InputFrame down = queue.nextTick();
        assertTrue(down.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertTrue("left button is FLAP (E29)", down.isJustPressed(InputAction.FLAP));
        assertFalse(down.isMouseJustPressed(Keys.BUTTON_RIGHT));
        assertEquals(100, down.mouseX(), 0.0);
        assertEquals(200, down.mouseY(), 0.0);
        assertEquals(0, down.wheel());

        assertTrue(touch(MotionEvent.ACTION_MOVE, 150f, 250f));
        InputFrame move = queue.nextTick();
        assertTrue(move.isMouseHeld(Keys.BUTTON_LEFT));
        assertFalse(move.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertEquals(150, move.mouseX(), 0.0);
        assertEquals(250, move.mouseY(), 0.0);

        assertTrue(touch(MotionEvent.ACTION_UP, 160f, 260f));
        InputFrame up = queue.nextTick();
        assertTrue(up.isMouseJustReleased(Keys.BUTTON_LEFT));
        assertFalse(up.isMouseHeld(Keys.BUTTON_LEFT));
        assertEquals(160, up.mouseX(), 0.0);
        assertEquals(260, up.mouseY(), 0.0);
    }

    @Test
    public void aTapThatWobblesInsideTheSlopNeverScrolls() {
        attachAndDrain();

        touch(MotionEvent.ACTION_DOWN, 300f, 500f);
        touch(MotionEvent.ACTION_MOVE, 305f, 510f);
        touch(MotionEvent.ACTION_MOVE, 298f, 495f);
        touch(MotionEvent.ACTION_UP, 299f, 496f);

        InputFrame frame = queue.nextTick();
        assertTrue(frame.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertTrue(frame.isMouseJustReleased(Keys.BUTTON_LEFT));
        assertEquals("a tap is never a wheel", 0, frame.wheel());
        assertEquals(299, frame.mouseX(), 0.0);
        assertEquals(496, frame.mouseY(), 0.0);
    }

    @Test
    public void aVerticalDragIsTheWheelWithTheScreensSign() {
        attachAndDrain();

        // The press edge goes out at once, before any drag can be told from a tap.
        touch(MotionEvent.ACTION_DOWN, 300f, 700f);
        InputFrame down = queue.nextTick();
        assertTrue(down.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertEquals(0, down.wheel());

        // Within the slop: still a tap, no notch.
        touch(MotionEvent.ACTION_MOVE, 300f, 690f);
        assertEquals(0, queue.nextTick().wheel());

        // 60 px up from the press point: two full notches, negative — the finger moving up
        // carries the content up, which is scroll += WHEEL_STEP on every scrollable screen
        // (they apply scroll -= wheel * WHEEL_STEP).
        touch(MotionEvent.ACTION_MOVE, 300f, 640f);
        InputFrame first = queue.nextTick();
        assertEquals(-60 / AndroidInputBridge.DRAG_PX_PER_NOTCH, first.wheel());
        assertEquals(-2, first.wheel());
        assertTrue("the button stays held through the drag", first.isMouseHeld(Keys.BUTTON_LEFT));
        assertEquals("MouseMove keeps flowing", 640, first.mouseY(), 0.0);

        // 200 px up in total: eight notches, so six more; the 8 px remainder is carried.
        touch(MotionEvent.ACTION_MOVE, 300f, 500f);
        assertEquals(-200 / AndroidInputBridge.DRAG_PX_PER_NOTCH - first.wheel(),
                queue.nextTick().wheel());

        // Back to the press point un-scrolls exactly what was scrolled.
        touch(MotionEvent.ACTION_MOVE, 300f, 700f);
        assertEquals(8, queue.nextTick().wheel());

        // Down past the press point: positive notches (wheel towards the user).
        touch(MotionEvent.ACTION_MOVE, 300f, 760f);
        assertEquals(2, queue.nextTick().wheel());

        touch(MotionEvent.ACTION_UP, 300f, 760f);
        InputFrame up = queue.nextTick();
        assertTrue("the release lands at the lift position", up.isMouseJustReleased(Keys.BUTTON_LEFT));
        assertEquals(300, up.mouseX(), 0.0);
        assertEquals(760, up.mouseY(), 0.0);
        assertEquals(0, up.wheel());
    }

    @Test
    public void aDragThatStartsSidewaysNeverScrolls() {
        attachAndDrain();

        touch(MotionEvent.ACTION_DOWN, 100f, 500f);
        touch(MotionEvent.ACTION_MOVE, 140f, 503f);
        InputFrame sideways = queue.nextTick();
        assertTrue(sideways.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertEquals(0, sideways.wheel());

        // Locked as horizontal: a long vertical leg afterwards is still not a scroll.
        touch(MotionEvent.ACTION_MOVE, 140f, 300f);
        InputFrame vertical = queue.nextTick();
        assertEquals("a slider drag is not a scroll", 0, vertical.wheel());
        assertEquals(300, vertical.mouseY(), 0.0);

        touch(MotionEvent.ACTION_UP, 140f, 300f);
        InputFrame up = queue.nextTick();
        assertTrue(up.isMouseJustReleased(Keys.BUTTON_LEFT));
        assertEquals(0, up.wheel());
    }

    @Test
    public void aSecondFingerIsTheRightButtonAtItsOwnPixel() {
        attachAndDrain();
        touch(MotionEvent.ACTION_DOWN, 10f, 20f);
        queue.nextTick();

        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}, new float[] {10f, 300f},
                new float[] {20f, 400f});
        InputFrame press = queue.nextTick();
        assertTrue(press.isMouseJustPressed(Keys.BUTTON_RIGHT));
        assertTrue("right button is ABILITY (E29)", press.isJustPressed(InputAction.ABILITY));
        assertTrue("the first finger keeps the flap held", press.isMouseHeld(Keys.BUTTON_LEFT));
        assertFalse(press.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertEquals(300, press.mouseX(), 0.0);
        assertEquals(400, press.mouseY(), 0.0);

        touch(MotionEvent.ACTION_POINTER_UP, 1, new int[] {0, 1}, new float[] {10f, 310f},
                new float[] {20f, 410f});
        InputFrame release = queue.nextTick();
        assertTrue(release.isMouseJustReleased(Keys.BUTTON_RIGHT));
        assertFalse(release.isHeld(InputAction.ABILITY));
        assertTrue(release.isMouseHeld(Keys.BUTTON_LEFT));
        assertTrue(release.isHeld(InputAction.FLAP));
        assertEquals(310, release.mouseX(), 0.0);
        assertEquals(410, release.mouseY(), 0.0);

        touch(MotionEvent.ACTION_UP, 12f, 22f);
        InputFrame up = queue.nextTick();
        assertTrue(up.isMouseJustReleased(Keys.BUTTON_LEFT));
        assertFalse(up.isMouseHeld(Keys.BUTTON_RIGHT));
        assertEquals(0, up.wheel());
    }

    @Test
    public void theFirstFingerMayLiftBeforeTheSecond() {
        attachAndDrain();
        touch(MotionEvent.ACTION_DOWN, 10f, 20f);
        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}, new float[] {10f, 300f},
                new float[] {20f, 400f});
        queue.nextTick();

        touch(MotionEvent.ACTION_POINTER_UP, 0, new int[] {0, 1}, new float[] {11f, 300f},
                new float[] {21f, 400f});
        InputFrame firstUp = queue.nextTick();
        assertTrue(firstUp.isMouseJustReleased(Keys.BUTTON_LEFT));
        assertTrue("the second finger's ability stays held", firstUp.isMouseHeld(Keys.BUTTON_RIGHT));
        assertEquals(11, firstUp.mouseX(), 0.0);

        // The remaining finger moves: the pointer stays where the first finger lifted.
        touch(MotionEvent.ACTION_MOVE, 0, new int[] {1}, new float[] {320f}, new float[] {420f});
        assertEquals(0, queue.pendingCount());

        // Its lift is the plain ACTION_UP of the last pointer, which releases the right button.
        touch(MotionEvent.ACTION_UP, 0, new int[] {1}, new float[] {330f}, new float[] {430f});
        InputFrame lastUp = queue.nextTick();
        assertTrue(lastUp.isMouseJustReleased(Keys.BUTTON_RIGHT));
        assertFalse(lastUp.isMouseHeld(Keys.BUTTON_LEFT));
        assertFalse(lastUp.isMouseHeld(Keys.BUTTON_RIGHT));
        assertEquals(330, lastUp.mouseX(), 0.0);
    }

    @Test
    public void aThirdFingerIsIgnored() {
        attachAndDrain();
        touch(MotionEvent.ACTION_DOWN, 10f, 20f);
        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}, new float[] {10f, 300f},
                new float[] {20f, 400f});
        queue.nextTick();

        touch(MotionEvent.ACTION_POINTER_DOWN, 2, new int[] {0, 1, 2},
                new float[] {10f, 300f, 500f}, new float[] {20f, 400f, 600f});
        assertEquals(0, queue.pendingCount());
        touch(MotionEvent.ACTION_POINTER_UP, 2, new int[] {0, 1, 2},
                new float[] {10f, 300f, 500f}, new float[] {20f, 400f, 600f});
        assertEquals(0, queue.pendingCount());
        InputFrame frame = queue.nextTick();
        assertTrue(frame.isMouseHeld(Keys.BUTTON_LEFT));
        assertTrue(frame.isMouseHeld(Keys.BUTTON_RIGHT));
    }

    @Test
    public void aCancelledTouchReleasesEveryButton() {
        attachAndDrain();
        touch(MotionEvent.ACTION_DOWN, 10f, 20f);
        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}, new float[] {10f, 300f},
                new float[] {20f, 400f});
        queue.nextTick();

        touch(MotionEvent.ACTION_CANCEL, 0, new int[] {0, 1}, new float[] {12f, 300f},
                new float[] {22f, 400f});

        InputFrame frame = queue.nextTick();
        assertTrue(frame.isMouseJustReleased(Keys.BUTTON_LEFT));
        assertTrue(frame.isMouseJustReleased(Keys.BUTTON_RIGHT));
        assertFalse(frame.isMouseHeld(Keys.BUTTON_LEFT));
        assertFalse(frame.isMouseHeld(Keys.BUTTON_RIGHT));
        assertFalse(frame.isHeld(InputAction.FLAP));
        assertFalse(frame.isHeld(InputAction.ABILITY));
    }

    @Test
    public void aTapOnTheAbilityBadgeIsTheRightButtonDuringARun() {
        attachAndDrain();
        bridge.setRunActive(true);
        Vec2 centre = surfacePointOfBadge(0, 0);
        assertEquals(2 * HudRenderer.ABILITY_CX, centre.x(), 0.0);
        assertEquals(2 * HudRenderer.ABILITY_CY, centre.y(), 0.0);

        touch(MotionEvent.ACTION_DOWN, (float) centre.x(), (float) centre.y());
        InputFrame down = queue.nextTick();
        assertTrue(down.isMouseJustPressed(Keys.BUTTON_RIGHT));
        assertTrue(down.isJustPressed(InputAction.ABILITY));
        assertFalse("the badge never flaps", down.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertFalse(down.isJustPressed(InputAction.FLAP));
        assertEquals(centre.x(), down.mouseX(), 0.0);
        assertEquals(centre.y(), down.mouseY(), 0.0);

        touch(MotionEvent.ACTION_UP, (float) centre.x(), (float) centre.y());
        InputFrame up = queue.nextTick();
        assertTrue(up.isMouseJustReleased(Keys.BUTTON_RIGHT));
        assertFalse(up.isMouseHeld(Keys.BUTTON_RIGHT));
        assertFalse(up.isMouseJustReleased(Keys.BUTTON_LEFT));
    }

    @Test
    public void theBadgeZoneEndsAtTheRadiusPlusTheTouchMargin() {
        attachAndDrain();
        bridge.setRunActive(true);
        double reach = HudRenderer.ABILITY_RADIUS + AndroidInputBridge.BADGE_TOUCH_MARGIN;

        Vec2 inside = surfacePointOfBadge(reach, 0);
        touch(MotionEvent.ACTION_DOWN, (float) inside.x(), (float) inside.y());
        touch(MotionEvent.ACTION_UP, (float) inside.x(), (float) inside.y());
        InputFrame edge = queue.nextTick();
        assertTrue(edge.isMouseJustPressed(Keys.BUTTON_RIGHT));
        assertFalse(edge.isMouseJustPressed(Keys.BUTTON_LEFT));

        Vec2 outside = surfacePointOfBadge(reach + 1, 0);
        touch(MotionEvent.ACTION_DOWN, (float) outside.x(), (float) outside.y());
        touch(MotionEvent.ACTION_UP, (float) outside.x(), (float) outside.y());
        InputFrame beyond = queue.nextTick();
        assertTrue(beyond.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertFalse(beyond.isMouseJustPressed(Keys.BUTTON_RIGHT));
    }

    @Test
    public void theBadgeZoneIsOffOutsideARun() {
        attachAndDrain();
        assertFalse(bridge.isRunActive());
        Vec2 centre = surfacePointOfBadge(0, 0);

        touch(MotionEvent.ACTION_DOWN, (float) centre.x(), (float) centre.y());
        touch(MotionEvent.ACTION_UP, (float) centre.x(), (float) centre.y());

        InputFrame frame = queue.nextTick();
        assertTrue("on a menu the corner is a plain click", frame.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertFalse(frame.isMouseJustPressed(Keys.BUTTON_RIGHT));

        bridge.setRunActive(true);
        touch(MotionEvent.ACTION_DOWN, (float) centre.x(), (float) centre.y());
        touch(MotionEvent.ACTION_UP, (float) centre.x(), (float) centre.y());
        assertTrue(queue.nextTick().isMouseJustPressed(Keys.BUTTON_RIGHT));
    }

    @Test
    public void aSecondFingerDuringABadgePressIsIgnored() {
        attachAndDrain();
        bridge.setRunActive(true);
        Vec2 centre = surfacePointOfBadge(0, 0);
        touch(MotionEvent.ACTION_DOWN, (float) centre.x(), (float) centre.y());
        queue.nextTick();

        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1},
                new float[] {(float) centre.x(), 300f}, new float[] {(float) centre.y(), 400f});
        assertEquals(0, queue.pendingCount());
        touch(MotionEvent.ACTION_POINTER_UP, 1, new int[] {0, 1},
                new float[] {(float) centre.x(), 300f}, new float[] {(float) centre.y(), 400f});
        assertEquals(0, queue.pendingCount());

        touch(MotionEvent.ACTION_UP, (float) centre.x(), (float) centre.y());
        InputFrame up = queue.nextTick();
        assertTrue(up.isMouseJustReleased(Keys.BUTTON_RIGHT));
        assertFalse(up.isMouseHeld(Keys.BUTTON_RIGHT));
    }

    @Test
    public void aSurfaceResizeAfterAttachIsQueued() {
        attachAndDrain();

        view.surfaceChanged(holder, PixelFormat.RGBA_8888, 720, 1080);

        InputFrame frame = queue.nextTick();
        assertTrue(frame.systemEvents().contains(new RawInput.Resized(720, 1080)));
    }

    @Test
    public void keyTapsPressAndReleaseInOneTick() {
        bridge.key(Keys.ESCAPE);

        InputFrame frame = queue.nextTick();
        assertTrue(frame.rawKeyDowns().contains(Keys.ESCAPE));
        assertTrue("Escape is BACK in the default bindings", frame.isJustPressed(InputAction.BACK));
        assertTrue(frame.isJustReleased(InputAction.BACK));
        assertFalse(frame.isHeld(InputAction.BACK));
    }

    @Test
    public void lifecycleHelpersReachTheQueue() {
        bridge.focusLost();
        bridge.iconified(true);
        bridge.closeRequested();

        InputFrame frame = queue.nextTick();
        assertTrue(frame.hasSystemEvent(RawInput.FocusLost.class));
        assertTrue(frame.systemEvents().contains(new RawInput.Iconified(true)));
        assertTrue(frame.hasSystemEvent(RawInput.CloseRequested.class));

        bridge.iconified(false);
        assertEquals(List.of(new RawInput.Iconified(false)), queue.nextTick().systemEvents());
    }

    @Test
    public void rejectsANegativeTouchSlop() {
        assertThrows(IllegalArgumentException.class,
                () -> new AndroidInputBridge(queue, viewport, -1));
    }

    @Test
    public void attachRejectsAWindowThatIsNotAnAndroidWindow() {
        AppWindow foreign = new AppWindow() {
            @Override
            public int canvasWidth() {
                return 1;
            }

            @Override
            public int canvasHeight() {
                return 1;
            }

            @Override
            public void setIcons(List<? extends BufferedImage> icons) {
            }

            @Override
            public void dispose() {
            }
        };

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> bridge.attach(foreign));
        assertTrue(e.getMessage(), e.getMessage().contains("AndroidWindow"));
        assertFalse(bridge.isAttached());
        assertEquals(0, queue.pendingCount());
    }

    @Test
    public void detachStopsListeningAndIsIdempotent() {
        attachAndDrain();

        bridge.detach();
        bridge.detach();

        assertFalse(bridge.isAttached());
        assertFalse("no listener consumes the touch any more", touch(MotionEvent.ACTION_DOWN, 5f, 5f));
        view.surfaceChanged(holder, PixelFormat.RGBA_8888, 100, 100);
        assertEquals(0, queue.pendingCount());
    }

    @Test
    public void detachBeforeAttachIsANoOp() {
        bridge.detach();
        assertFalse(bridge.isAttached());
        assertEquals(0, queue.pendingCount());
    }
}
