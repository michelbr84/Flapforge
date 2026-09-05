package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.Transparency}. Census: render/AssetManager.java:463 passes
 * {@code Transparency.TRANSLUCENT} to
 * {@link GraphicsConfiguration#createCompatibleImage(int, int, int)} inside
 * {@code AssetManager.compatible}, which never executes because
 * {@link GraphicsEnvironment#isHeadless()} returns {@code true} on Android. The constant set is
 * kept at the three AWT values; only {@link #TRANSLUCENT} is exercised.
 */
public interface Transparency {

    /** AWT parity: image pixels are fully opaque. */
    int OPAQUE = 1;

    /** AWT parity: every pixel is either fully opaque or fully transparent. */
    int BITMASK = 2;

    /** AWT parity: pixels may carry arbitrary alpha (the value the census uses). */
    int TRANSLUCENT = 3;
}
