package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.BasicStroke}. {@link Graphics2D} converts the stroke to android
 * paint state at draw time: width scaled by the current transform ({@link AwtMatrix#averageScale}),
 * cap/join mapped 1:1 onto {@code android.graphics.Paint.Cap/Join}, the miter limit onto
 * {@code Paint.setStrokeMiter}, and a dash array + phase onto a {@code DashPathEffect} (a solid
 * stroke installs no path effect at all).
 *
 * <p>Census surface: constructors {@code (float width)} (25+ sites, widths 0.05f..9f),
 * {@code (float, int cap, int join)} (ui/screens/ModifierChoiceOverlay.java:104,
 * render/GearRenderer.java:35, render/HudRenderer.java:184, render/LightningRenderer.java:50-52),
 * and {@code (float, int cap, int join, float miter, float[] dash, float dashPhase)} —
 * render/WindZoneRenderer.java:32, cap {@code CAP_BUTT}, join {@code JOIN_MITER}, dash
 * {@code {6f, 5f}}, phase {@code 0f}. Caps actually used: {@code CAP_ROUND}, {@code CAP_BUTT};
 * joins: {@code JOIN_ROUND}, {@code JOIN_MITER} ({@code CAP_SQUARE}/{@code JOIN_BEVEL} exist for
 * AWT parity of the constants). The AWT getter methods and {@code createStrokedShape} are not
 * exercised by the census; the parameters are read package-privately by Graphics2D.
 */
public class BasicStroke implements Stroke {

    /** AWT parity (value 0): ends unclosed segments with no decoration (census). */
    public static final int CAP_BUTT = 0;
    /** AWT parity (value 1): ends segments with a semicircle (census). */
    public static final int CAP_ROUND = 1;
    /** AWT parity (value 2): ends segments with a half-square (the AWT default). */
    public static final int CAP_SQUARE = 2;

    /** AWT parity: joins segments with sharp corners extended to the miter limit (census). */
    public static final int JOIN_MITER = 0;
    /** AWT parity: joins segments with rounded corners (census). */
    public static final int JOIN_ROUND = 1;
    /** AWT parity: joins segments with a bevelled corner. */
    public static final int JOIN_BEVEL = 2;

    /** AWT default miter limit. */
    static final float DEFAULT_MITER_LIMIT = 10f;

    // Package-visible for the Graphics2D stroke pipeline.
    final float width;
    final int cap;
    final int join;
    final float miterLimit;
    final float[] dash;
    final float dashPhase;

    /** Creates a solid stroke of the given width (census: the dominant form). */
    public BasicStroke(float width) {
        this(width, CAP_SQUARE, JOIN_MITER, DEFAULT_MITER_LIMIT, null, 0f);
    }

    /** Creates a solid stroke with the given cap and join (census). */
    public BasicStroke(float width, int cap, int join) {
        this(width, cap, join, DEFAULT_MITER_LIMIT, null, 0f);
    }

    /**
     * Creates a stroke with full AWT parameters (census: WindZoneRenderer's dashed wind strokes).
     *
     * @param width the pen width in user-space units
     * @param cap one of {@code CAP_SQUARE}, {@code CAP_ROUND}, {@code CAP_BUTT}
     * @param join one of {@code JOIN_MITER}, {@code JOIN_ROUND}, {@code JOIN_BEVEL}
     * @param miterlimit the miter clip threshold, {@code >= 1} (checked only when {@code join} is
     *     {@code JOIN_MITER}, AWT parity)
     * @param dash the alternating on/off lengths, or {@code null} for a solid stroke
     * @param dashPhase the offset into the dash pattern where the stroke starts
     */
    public BasicStroke(float width, int cap, int join, float miterlimit, float[] dash,
            float dashPhase) {
        if (width < 0f) {
            throw new IllegalArgumentException("Flapforge shim: negative BasicStroke width");
        }
        if (cap != CAP_BUTT && cap != CAP_ROUND && cap != CAP_SQUARE) {
            throw new IllegalArgumentException("Flapforge shim: unknown BasicStroke cap " + cap);
        }
        if (join != JOIN_MITER && join != JOIN_ROUND && join != JOIN_BEVEL) {
            throw new IllegalArgumentException("Flapforge shim: unknown BasicStroke join " + join);
        }
        if (join == JOIN_MITER && miterlimit < 1f) {
            throw new IllegalArgumentException("Flapforge shim: miter limit must be >= 1");
        }
        if (dash != null) {
            if (dash.length == 0) {
                throw new IllegalArgumentException("Flapforge shim: empty dash array");
            }
            for (float d : dash) {
                if (d < 0f) {
                    throw new IllegalArgumentException("Flapforge shim: negative dash length");
                }
            }
        }
        if (dashPhase < 0f) {
            throw new IllegalArgumentException("Flapforge shim: negative dash phase");
        }
        this.width = width;
        this.cap = cap;
        this.join = join;
        this.miterLimit = miterlimit;
        this.dash = dash;
        this.dashPhase = dashPhase;
    }
}
