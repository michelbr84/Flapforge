package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.GraphicsEnvironment}. On Android there is no AWT toolkit, so
 * the shim reports headless unconditionally — this is load-bearing: render/AssetManager.java:452
 * and app/GameApplication.java:836 branch on {@code isHeadless()}, and the Android port relies on
 * the {@code true} result to keep the desktop's screen-formatting path
 * ({@code AssetManager.compatible}) from executing.
 *
 * <p>Census surface: {@code isHeadless()} (AssetManager.java:452, GameApplication.java:836),
 * {@code getLocalGraphicsEnvironment().getDefaultScreenDevice()} (AssetManager.java:455-456;
 * GameApplication.java:840-841) and {@code getDefaultConfiguration()} on the device
 * (AssetManager.java:456). A display-mode query exists at GameApplication.java:840-841
 * ({@code getDisplayMode()}), but it sits behind the {@code isHeadless()} early return and the
 * shim carries no {@code DisplayMode} type — see the shim package notes.
 */
public final class GraphicsEnvironment {

    private static final GraphicsEnvironment INSTANCE = new GraphicsEnvironment();
    private static final GraphicsDevice DEVICE = new GraphicsDevice();

    private GraphicsEnvironment() {
    }

    /**
     * Always {@code true}: the Android port has no AWT display, and the census depends on the
     * early return (semantics 9).
     *
     * @return {@code true}, always
     */
    public static boolean isHeadless() {
        return true;
    }

    /**
     * The shared environment instance.
     *
     * @return the local environment stub
     */
    public static GraphicsEnvironment getLocalGraphicsEnvironment() {
        return INSTANCE;
    }

    /**
     * The screen-device stub (never a real screen on Android).
     *
     * @return the device stub
     */
    public GraphicsDevice getDefaultScreenDevice() {
        return DEVICE;
    }
}
