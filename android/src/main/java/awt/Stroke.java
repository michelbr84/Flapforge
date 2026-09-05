package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.Stroke}. Census: the type flows through
 * {@code Graphics2D.setStroke/getStroke} only (75 and 35 call sites across render/ and ui/, all
 * saving/restoring stroke state or installing {@link BasicStroke} constants); nothing ever calls
 * {@code createStrokedShape}. The shim therefore keeps it a marker interface; {@link Graphics2D}
 * reads the stroke parameters through package-private accessors on {@link BasicStroke}.
 */
public interface Stroke {
}
