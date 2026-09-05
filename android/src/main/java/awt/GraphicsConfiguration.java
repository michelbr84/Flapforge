package awt;

import awt.image.BufferedImage;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.GraphicsConfiguration}. Census: only the existence of
 * {@code createCompatibleImage(int, int, int)} matters — render/AssetManager.java:460 calls it
 * with {@code Transparency.TRANSLUCENT} inside {@code compatible()}, which never executes because
 * {@link GraphicsEnvironment#isHeadless()} returns {@code true} first (the method must be
 * declared for compilation but its body is unreachable, semantics 9).
 */
public final class GraphicsConfiguration {

    GraphicsConfiguration() {
    }

    /**
     * Declared for the census compile surface (AssetManager.java:460); unreachable at runtime
     * because the census guards the call behind an always-true headless check.
     *
     * @param width ignored
     * @param height ignored
     * @param transparency ignored
     * @return never
     */
    public BufferedImage createCompatibleImage(int width, int height, int transparency) {
        throw new UnsupportedOperationException(
                "Flapforge shim: GraphicsConfiguration.createCompatibleImage is not part of the "
                        + "census surface");
    }
}
