package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.Paint}. Census: the type flows through
 * {@code Graphics2D.setPaint/getPaint} only (render/ProceduralArt.java:322-325, :656-662 and
 * render/BackgroundRenderer.java:259-263 save and restore a {@code Paint}); the only concrete
 * {@code Paint} values the game ever passes are {@link Color} and {@link GradientPaint}. The real
 * AWT interface carries {@code createContext(...)} plumbing that nothing in the census touches, so
 * the shim keeps it a marker interface and lets {@link Graphics2D} dispatch on the concrete type.
 */
public interface Paint {
}
