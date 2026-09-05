package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.GraphicsDevice}. Census: only
 * {@code getDefaultConfiguration()} flows through it (render/AssetManager.java:455-456, inside
 * the dead-at-runtime {@code compatible()} path); GameApplication.java:841 also calls
 * {@code getDisplayMode()} behind the {@code isHeadless()} early return, which the shim does not
 * carry (no {@code DisplayMode} type is in the frozen shim list).
 */
public final class GraphicsDevice {

    private static final GraphicsConfiguration CONFIGURATION = new GraphicsConfiguration();

    GraphicsDevice() {
    }

    /**
     * The configuration stub (census: AssetManager.java:456, never executed because
     * {@code isHeadless()} returns {@code true} first).
     *
     * @return the configuration stub
     */
    public GraphicsConfiguration getDefaultConfiguration() {
        return CONFIGURATION;
    }
}
