package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.GradientPaint}, a two-point linear gradient that
 * {@link Graphics2D} renders through an {@code android.graphics.LinearGradientShader} whose two
 * end points go through the current transform (in double space) before the shader is built.
 *
 * <p>Census surface: exactly one constructor, {@code (float x1, float y1, Color c1, float x2,
 * float y2, Color c2)} — render/ProceduralArt.java:248 (the world sky gradient) and :659 (the icon
 * tile gradient). Both call sites use vertical, non-cyclic gradients, so the cyclic flag is always
 * false; the AWT cyclic constructor and the point/colour getters are not exercised and are absent.
 */
public final class GradientPaint implements Paint {

    // Package-visible for the Graphics2D shader pipeline.
    final float x1;
    final float y1;
    final float x2;
    final float y2;
    final Color color1;
    final Color color2;
    final boolean cyclic;

    /**
     * Creates a non-cyclic linear gradient between two user-space points.
     *
     * @param x1 the x of the first point
     * @param y1 the y of the first point
     * @param color1 the colour at the first point
     * @param x2 the x of the second point
     * @param y2 the y of the second point
     * @param color2 the colour at the second point
     */
    public GradientPaint(float x1, float y1, Color color1, float x2, float y2, Color color2) {
        this(x1, y1, color1, x2, y2, color2, false);
    }

    private GradientPaint(float x1, float y1, Color color1, float x2, float y2, Color color2,
            boolean cyclic) {
        if (color1 == null || color2 == null) {
            throw new NullPointerException("Flapforge shim: GradientPaint colours must not be null");
        }
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.color1 = color1;
        this.color2 = color2;
        this.cyclic = cyclic;
    }
}
