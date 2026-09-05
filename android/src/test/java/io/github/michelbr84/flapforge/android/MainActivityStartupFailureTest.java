package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.PixelFormat;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
 * Robolectric proof that a start failure is shown, not swallowed (M10 hotfix): with
 * {@link MainActivity#gameStarter} replaced by a call that throws the very error every device
 * hit (a {@code NoSuchMethodError} out of the content binder), the boot thread writes
 * {@value MainActivity#STARTUP_FAILURE_FILE} under the files dir and replaces the surface with
 * the report — title, exception, stack frames, the file's location — and the activity is not
 * finished until back is pressed.
 *
 * <p>The desktop's {@code ~/.flapforge} is fingerprinted around the boot
 * ({@link DesktopProfileGuard}); the first-run settings the boot thread seeds before the start
 * land under the files dir as on the happy path.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityStartupFailureTest {

    private static final int WIDTH = 840;
    private static final int HEIGHT = 1280;
    private static final long REPORT_TIMEOUT_MS = 10_000L;
    private static final String INJECTED_MESSAGE =
            "No virtual method isRecord()Z in class Ljava/lang/Class; (injected)";

    /** The activity's {@code onPause} suspends the shim's audio output, a static flag. */
    @After
    public void resumeAudioOutput() {
        AudioSystem.resumeOutput();
    }

    @Test
    public void aStartThatThrowsIsShownWrittenAndClosedByBack() throws Exception {
        Map<String, String> desktopBefore = DesktopProfileGuard.fingerprint();
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        try {
            assertNull(activity.startupFailureView());
            activity.gameStarter = (options, host) -> {
                throw new NoSuchMethodError(INJECTED_MESSAGE);
            };
            GameSurfaceView view = activity.surfaceView();
            SurfaceHolder holder = view.getHolder();
            view.surfaceCreated(holder);
            view.surfaceChanged(holder, PixelFormat.RGBA_8888, WIDTH, HEIGHT);

            TextView shown = awaitReport(activity);
            String text = shown.getText().toString();
            assertTrue(text, text.startsWith(MainActivity.FAILURE_TITLE));
            assertTrue(text, text.contains("java.lang.NoSuchMethodError: " + INJECTED_MESSAGE));
            assertTrue("a stack frame of the failing call: " + text,
                    text.contains("at " + getClass().getName()));
            assertTrue(text, text.contains("This text was also written to "));
            assertTrue(text, text.contains(MainActivity.STARTUP_FAILURE_FILE));

            // The report is the activity's content: a scroll view over the text.
            assertTrue(shown.getParent() instanceof ScrollView);
            ViewGroup content = activity.findViewById(android.R.id.content);
            assertEquals(1, content.getChildCount());
            assertTrue(content.getChildAt(0) instanceof ScrollView);

            // The same text is on disk, under the files dir, never under ~/.flapforge.
            Path file = activity.getFilesDir().toPath().resolve(MainActivity.STARTUP_FAILURE_FILE);
            assertTrue(file + " exists", Files.isRegularFile(file));
            String written = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            assertTrue(written, written.startsWith(MainActivity.FAILURE_TITLE));
            assertTrue(written, written.contains(INJECTED_MESSAGE));
            assertTrue("the screen carries the file's text", text.startsWith(written));

            // No game, no finish — until back.
            assertNull(activity.application());
            assertFalse("the activity waits for the player", activity.isFinishing());
            activity.onBackInvoked();
            assertTrue("back closes the report", activity.isFinishing());
        } finally {
            controller.pause().stop().destroy();
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("the desktop profile is untouched", desktopBefore,
                DesktopProfileGuard.fingerprint());
    }

    /** Runs the main looper until the boot thread's posted report has been shown. */
    private static TextView awaitReport(MainActivity activity) throws InterruptedException {
        long deadline = System.nanoTime() + REPORT_TIMEOUT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            TextView shown = activity.startupFailureView();
            if (shown != null) {
                return shown;
            }
            Thread.sleep(20L);
        }
        fail("the failure report was not shown within " + REPORT_TIMEOUT_MS + " ms");
        return null;
    }
}
