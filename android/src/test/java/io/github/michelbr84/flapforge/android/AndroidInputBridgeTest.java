package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import awt.image.BufferedImage;
import io.github.michelbr84.flapforge.app.AppWindow;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proofs of the {@link AndroidInputBridge} (M10, P2): real {@link MotionEvent}s
 * dispatched to the view arrive on the {@link InputQueue} as the desktop's left-button events
 * with the touched pixel, the surface size reaches it as {@code Resized}, and the activity's
 * helpers ({@code key}, {@code focusLost}, {@code closeRequested}) produce what the screens
 * expect. Everything is observed the way the loop observes it: through
 * {@link InputQueue#nextTick()}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AndroidInputBridgeTest {

    private static final int WIDTH = 840;
    private static final int HEIGHT = 1280;

    private InputQueue queue;
    private GameSurfaceView view;
    private SurfaceHolder holder;
    private AndroidInputBridge bridge;

    @Before
    public void setUp() {
        queue = new InputQueue(KeyBindings.defaults());
        view = new GameSurfaceView(RuntimeEnvironment.getApplication());
        holder = view.getHolder();
        // Robolectric's fake holder never fires the callbacks: deliver them as the platform would.
        view.surfaceCreated(holder);
        view.surfaceChanged(holder, PixelFormat.RGBA_8888, WIDTH, HEIGHT);
        bridge = new AndroidInputBridge(queue);
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

    private void attachAndDrain() {
        bridge.attach(new AndroidWindow(view));
        queue.nextTick();
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
    public void aTouchIsTheLeftButtonAtTheTouchedPixel() {
        attachAndDrain();

        assertTrue(touch(MotionEvent.ACTION_DOWN, 100.7f, 200.2f));
        InputFrame down = queue.nextTick();
        assertTrue(down.isMouseJustPressed(Keys.BUTTON_LEFT));
        assertTrue("left button is FLAP (E29)", down.isJustPressed(InputAction.FLAP));
        assertEquals(100, down.mouseX(), 0.0);
        assertEquals(200, down.mouseY(), 0.0);

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
    public void aCancelledTouchReleasesTheButton() {
        attachAndDrain();
        touch(MotionEvent.ACTION_DOWN, 10f, 20f);
        queue.nextTick();

        touch(MotionEvent.ACTION_CANCEL, 12f, 22f);

        InputFrame frame = queue.nextTick();
        assertTrue(frame.isMouseJustReleased(Keys.BUTTON_LEFT));
        assertFalse(frame.isMouseHeld(Keys.BUTTON_LEFT));
        assertFalse(frame.isHeld(InputAction.FLAP));
    }

    @Test
    public void aSecondFingerIsConsumedButIgnoredUntilP3() {
        attachAndDrain();
        touch(MotionEvent.ACTION_DOWN, 10f, 20f);
        queue.nextTick();

        int secondPointerDown = MotionEvent.ACTION_POINTER_DOWN
                | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        assertTrue(touch(secondPointerDown, 300f, 400f));

        assertEquals(0, queue.pendingCount());
        InputFrame frame = queue.nextTick();
        assertTrue(frame.isMouseHeld(Keys.BUTTON_LEFT));
        assertEquals(10, frame.mouseX(), 0.0);
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
    public void focusLostAndCloseRequestedReachTheQueue() {
        bridge.focusLost();
        bridge.closeRequested();

        InputFrame frame = queue.nextTick();
        assertTrue(frame.hasSystemEvent(RawInput.FocusLost.class));
        assertTrue(frame.hasSystemEvent(RawInput.CloseRequested.class));
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
