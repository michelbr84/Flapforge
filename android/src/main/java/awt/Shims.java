package awt;

import android.content.Context;

import java.io.File;
import java.util.Objects;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Host bootstrap state for the shim packages. The Android host (P2) calls
 * {@link #init(Context)} once before the game starts; the shims use the stored context for
 * platform paths — today only {@link Font#createFont(int, java.io.InputStream)}, which copies the
 * font stream into a temp file under the app cache dir so {@code Typeface.createFromFile} can
 * parse it. Not thread-configured: the game's boot sequence initialises everything on one thread.
 */
public final class Shims {

    private static volatile Context appContext;

    private Shims() {
    }

    /**
     * Stores the application context. Called by the Android host before the game starts; also
     * called by the shim tests (Robolectric supplies the context).
     *
     * @param context the application context
     */
    public static void init(Context context) {
        appContext = Objects.requireNonNull(context, "context");
    }

    /**
     * The context installed by {@link #init(Context)}.
     *
     * @return the application context
     * @throws IllegalStateException when the host has not initialised the shims yet
     */
    public static Context context() {
        Context context = appContext;
        if (context == null) {
            throw new IllegalStateException(
                    "Flapforge shim: Shims.init(context) has not run yet");
        }
        return context;
    }

    /**
     * The cache directory font streams are copied into before
     * {@code Typeface.createFromFile} parses them.
     *
     * @return the app cache dir
     * @throws IllegalStateException when the host has not initialised the shims yet
     */
    static File cacheDir() {
        return context().getCacheDir();
    }

    /**
     * Test hook: forgets the installed context so a test can prove the not-initialised failure
     * mode (Robolectric keeps static state alive across tests in one sandbox). Package-private
     * on purpose — the host never un-initialises the shims.
     */
    static void reset() {
        appContext = null;
    }
}
