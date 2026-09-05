package io.github.michelbr84.flapforge.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import awt.Shims;
import io.github.michelbr84.flapforge.app.GameApplication;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Android entry point (M10, P2): hosts the transformed {@link GameApplication} on an
 * {@link AndroidHost} the way {@code Flapforge.main} hosts it on {@code AwtHost}.
 *
 * <p><b>Lifecycle.</b>
 * <ul>
 *   <li>{@code onCreate}: before anything game-related, {@link Shims#init} hands the shim
 *       packages the application context and {@link SavePaths#override} points every save at
 *       {@code getFilesDir()} — unconditionally, so neither a device nor a Robolectric test can
 *       ever reach the desktop's {@code ~/.flapforge}. The window is immersive sticky
 *       fullscreen (system bars hidden, back with a swipe) and keeps the screen on; the content
 *       view is the {@link GameSurfaceView}; the system back gesture is registered on the
 *       {@link OnBackInvokedDispatcher} (the manifest opts into it).</li>
 *   <li>Boot: the game starts only once the surface exists with a real size — the first
 *       {@code surfaceChanged} with positive dimensions — because {@code GameApplication} seeds
 *       its viewport from the window size at start. A dedicated thread ({@value #BOOT_THREAD_NAME})
 *       calls {@link GameApplication#start(LaunchOptions, io.github.michelbr84.flapforge.app.GameHost)}
 *       exactly once with {@link LaunchOptions#DEFAULTS} (whose {@code home} is {@code null}, so
 *       the override above is what routes the files); the UI thread never blocks on content
 *       loading or the profile read. The same thread then waits for the loop thread and, when it
 *       ends — the game's own quit path: {@code CloseRequested} from the menu's Quit stops the
 *       loop, the loop thread disposes the presenter, detaches the bridge and drains the saves
 *       — it finishes the activity from the UI thread. A launch that aborts before the loop
 *       starts (content that fails to load) finishes the activity too, after the game printed
 *       why.</li>
 *   <li>Back gesture: an {@code ESCAPE} tap on the queue through the bridge ({@code BACK} on the
 *       menus, {@code PAUSE} in a run). The activity never finishes itself here: the game decides
 *       what back means, and quitting goes through its own path above. Before the game is up
 *       there is no queue to tap, and the gesture is ignored.</li>
 *   <li>{@code onPause}: a {@code FocusLost} on the queue, so the game pauses as it does when
 *       the desktop window loses focus. The loop keeps running while the activity is in the
 *       background (frames are skipped once the surface is destroyed); pausing the loop thread
 *       itself is P3.</li>
 *   <li>{@code onDestroy}: when the game is running, a {@code CloseRequested} on the queue and a
 *       bounded wait ({@value #SHUTDOWN_WAIT_MS} ms) for the loop thread, so the exit save
 *       drains before the process may go. A destroy that races the boot waits (bounded) for the
 *       boot thread to come out of {@code start} first; should the boot outlast that wait, the
 *       boot thread sees the destroy and quits the game it just started itself, so a game that
 *       was still loading is never left running behind a dead activity.</li>
 * </ul>
 */
public final class MainActivity extends Activity implements GameSurfaceView.SurfaceListener {

    /** Log tag. */
    static final String TAG = "Flapforge";
    /** Name of the thread that starts the game and then waits for the loop to end. */
    static final String BOOT_THREAD_NAME = "flapforge-boot";
    /** How long {@code onDestroy} waits for the loop thread to finish its shutdown sequence. */
    static final long SHUTDOWN_WAIT_MS = 3_000L;
    /** Poll interval of the boot thread's wait for the loop thread. */
    private static final long WATCH_POLL_MS = 1_000L;

    private GameSurfaceView surface;
    private AndroidHost host;
    private OnBackInvokedCallback backCallback;
    private final AtomicBoolean bootStarted = new AtomicBoolean();
    /** Counted down when {@link GameApplication#start} returned or failed. */
    private final CountDownLatch bootFinished = new CountDownLatch(1);
    /** Set by {@code onDestroy}; a boot that finishes after it quits the game it just started. */
    private volatile boolean destroyed;
    private volatile GameApplication application;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shims.init(getApplicationContext());
        SavePaths.override(getFilesDir().toPath());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        surface = new GameSurfaceView(this);
        host = new AndroidHost(this, surface);
        surface.addSurfaceListener(this);
        setContentView(surface);
        enterImmersiveMode();
        backCallback = this::onBackInvoked;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AndroidInputBridge bridge = host.inputBridge();
        if (bridge != null) {
            bridge.focusLost();
        }
    }

    @Override
    protected void onDestroy() {
        if (backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
        shutdownGame();
        super.onDestroy();
    }

    /** The first sized surface starts the game; later sizes reach the queue via the bridge. */
    @Override
    public void surfaceSized(int width, int height) {
        if (width > 0 && height > 0 && bootStarted.compareAndSet(false, true)) {
            Thread boot = new Thread(this::bootGame, BOOT_THREAD_NAME);
            boot.start();
        }
    }

    /**
     * The system back gesture: an {@code ESCAPE} tap for the game, nothing before the game is up.
     * Package-private so a test can trigger it without the dispatcher.
     */
    void onBackInvoked() {
        AndroidInputBridge bridge = host.inputBridge();
        if (bridge != null && bridge.isAttached()) {
            bridge.key(Keys.ESCAPE);
        }
    }

    /**
     * The running application.
     *
     * @return the application, or {@code null} before the boot thread started it
     */
    GameApplication application() {
        return application;
    }

    /**
     * The host the game runs on.
     *
     * @return the host
     */
    AndroidHost host() {
        return host;
    }

    /**
     * The view the game draws into.
     *
     * @return the view
     */
    GameSurfaceView surfaceView() {
        return surface;
    }

    private void enterImmersiveMode() {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller == null) {
            Log.w(TAG, "No insets controller yet; the system bars stay visible");
            return;
        }
        controller.hide(WindowInsets.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /** Body of the boot thread: start once, then watch the loop and finish when it ends. */
    private void bootGame() {
        GameApplication started = null;
        try {
            started = GameApplication.start(LaunchOptions.DEFAULTS, host);
            application = started;
        } catch (RuntimeException | Error e) {
            Log.e(TAG, "Flapforge failed to start", e);
        } finally {
            bootFinished.countDown();
        }
        if (started == null || started.loopThread() == null) {
            if (started != null) {
                Log.w(TAG, "The launch aborted before the game loop started (see System.err)");
            }
            runOnUiThread(this::finishIfAlive);
            return;
        }
        if (destroyed) {
            // onDestroy gave up waiting for this start: quit now rather than keep a loop running
            // behind an activity that is already gone (the duplicate close it may also have
            // queued is harmless).
            requestClose(started);
        }
        try {
            while (!started.awaitShutdown(WATCH_POLL_MS)) {
                // Still running: keep waiting. awaitShutdown also cancels the exit watchdog
                // once the loop thread has ended.
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        runOnUiThread(this::finishIfAlive);
    }

    /** Asks the game to quit and waits, bounded, for its shutdown sequence (the exit save). */
    private void shutdownGame() {
        destroyed = true;
        if (!bootStarted.get()) {
            return;
        }
        try {
            if (!bootFinished.await(SHUTDOWN_WAIT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "The game was still starting after " + SHUTDOWN_WAIT_MS
                        + " ms; leaving it to finish on its own");
                return;
            }
            GameApplication app = application;
            if (app == null) {
                return;
            }
            Thread loop = app.loopThread();
            if (loop == null || !loop.isAlive()) {
                return;
            }
            requestClose(app);
            if (!app.awaitShutdown(SHUTDOWN_WAIT_MS)) {
                Log.w(TAG, "The game loop did not stop within " + SHUTDOWN_WAIT_MS + " ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Queues the game's own quit event; the loop stops after the current frame (D4). */
    private static void requestClose(GameApplication app) {
        app.context().input().offer(new RawInput.CloseRequested());
    }

    private void finishIfAlive() {
        if (!isFinishing() && !isDestroyed()) {
            finish();
        }
    }
}
