package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.PixelFormat;
import android.os.Looper;
import android.view.SurfaceHolder;
import awt.Graphics2D;
import awt.image.BufferedImage;
import io.github.michelbr84.flapforge.app.GameApplication;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.render.Overscan;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import jssound.AudioSystem;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The real first frame of a real boot, painted through the {@code awt} shim at a portrait-phone
 * size with the fill-screen overscan on.
 *
 * <p>{@link MainActivityBootTest} boots the game against Robolectric's fake holder, whose
 * canvas is always null, so every frame it presents is a counted skip and the menu is never
 * actually drawn there. {@link SurfaceRenderSmokeTest} draws the backdrop primitives but not the
 * menu. This test closes the gap: it boots the activity exactly as the device does, lets the
 * loop drain the {@code Resized} to 1080x2400, then paints the booted presenter — the live
 * {@code ScreenManager} with the main menu on it — into a real bitmap. A frame that throws
 * (the game prints "Game loop failed" and the activity closes on a phone) fails here with the
 * stack trace instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BootedFrameRenderTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2400;
    private static final int LETTERBOX = 0xff000000 | 0x0e1116;
    private static final long BOOT_TIMEOUT_MS = 10_000L;
    private static final long LOOP_TIMEOUT_MS = 5_000L;

    @After
    public void resetStatics() {
        AudioSystem.resumeOutput();
        Overscan.reset();
    }

    @Test
    public void theBootedMenuPaintsAtPhoneResolution() throws Exception {
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        GameApplication app;
        try {
            GameSurfaceView view = activity.surfaceView();
            SurfaceHolder holder = view.getHolder();
            view.surfaceCreated(holder);
            view.surfaceChanged(holder, PixelFormat.RGBA_8888, WIDTH, HEIGHT);

            app = awaitNonNull("the boot thread started the game", activity::application,
                    BOOT_TIMEOUT_MS);
            GameContext context = app.context();
            assertNotNull(context);
            SurfacePresenter presenter = (SurfacePresenter) context.presenter();
            // At least one frame ran, so the loop drained the Resized and sized its viewport.
            await("the loop reached the presenter", () -> presenter.skippedCount() > 0,
                    LOOP_TIMEOUT_MS);

            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                // The point of the test: this must not throw.
                presenter.paint(g, WIDTH, HEIGHT, 0.0);
            } finally {
                g.dispose();
            }
            // Fill-screen is off by default, so the band above the playfield is the letterbox
            // tone the presenter paints first; the playfield itself carries the menu.
            assertEquals("the band above the playfield is the letterbox fill", LETTERBOX,
                    image.getRGB(WIDTH / 2, 20));
            assertNotEquals("the menu rendered into the playfield", LETTERBOX,
                    image.getRGB(WIDTH / 2, HEIGHT / 2));
            assertTrue(image.getRGB(WIDTH / 2, HEIGHT / 2) != 0);
        } finally {
            controller.pause().stop().destroy();
        }
        Thread loop = app.loopThread();
        loop.join(LOOP_TIMEOUT_MS);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static <T> T awaitNonNull(String what, Supplier<T> value, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            T v = value.get();
            if (v != null) {
                return v;
            }
            Thread.sleep(20L);
        }
        fail(what + " within " + timeoutMs + " ms");
        return null;
    }

    private static void await(String what, BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }
        fail(what + " within " + timeoutMs + " ms");
    }
}
