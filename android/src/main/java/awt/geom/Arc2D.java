package awt.geom;

import awt.Shape;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.geom.Arc2D}. The game always uses the {@link Double} form
 * (census fact: 2 usage sites, both in render/HudRenderer.java): the {@code (Arc2D.OPEN)}
 * constructor on a scratch field (:193) plus {@code setArc(x, y, w, h, start, extent, type)}
 * (:971, :975) and stroking via {@code Graphics2D.draw} (:973, :977). Only the {@link #OPEN}
 * closure type is exercised by the game; {@link #CHORD} and {@link #PIE} exist (with AWT's
 * values and closure geometry) for the orientation contract test, which fills a PIE quadrant —
 * the same precedent as {@code Path2D.WIND_EVEN_ODD}.
 *
 * <p>Angle semantics (semantics 1): AWT 0 degrees sits at 3 o'clock and POSITIVE sweep runs toward
 * 12 o'clock (visually counterclockwise on the y-down screen). The conversion to android happens
 * inside {@link Path2D#appendArc}, which builds cubic Bézier segments on
 * {@code (cx + rx*cos a, cy - ry*sin a)} exactly like {@code java.awt.geom.ArcIterator} — the
 * equivalent of the float-space {@code Path.addArc(oval, -awtStart, -awtSweep)} prescription,
 * generalised so an arbitrary Graphics2D transform (rotation included) stays exact: every control
 * point is transformed in double space before the android path is built. The Robolectric pixel
 * test {@code Graphics2DPixelTest} proves the orientation: an arc of (0 deg, 90 deg) inks the TOP
 * of the circle and not the bottom.
 */
public abstract class Arc2D implements Shape {

    /** AWT parity: an open arc with no segment joining its ends (the only census'd type). */
    public static final int OPEN = 0;

    /** AWT parity: the arc closed by a straight segment between its ends (test-covered). */
    public static final int CHORD = 1;

    /** AWT parity: the arc closed through the ellipse centre, a pie slice (test-covered). */
    public static final int PIE = 2;

    /** The {@code Double} precision form the game uses. */
    public static class Double extends Arc2D {

        /** The x of the upper-left corner of the framing rectangle. */
        public double x;
        /** The y of the upper-left corner of the framing rectangle. */
        public double y;
        /** The width of the framing rectangle. */
        public double width;
        /** The height of the framing rectangle. */
        public double height;
        /** The start angle in AWT degrees (0 at 3 o'clock, positive toward 12). */
        public double start;
        /** The angular extent in AWT degrees (positive toward 12 o'clock). */
        public double extent;
        /** The closure type: {@link #OPEN} (census), {@link #CHORD} or {@link #PIE}. */
        public int type;

        /**
         * Creates an empty arc with the given closure type (census:
         * {@code new Arc2D.Double(Arc2D.OPEN)}).
         *
         * @param type {@link #OPEN}, {@link #CHORD} or {@link #PIE}
         */
        public Double(int type) {
            this.type = checkType(type);
        }

        private static int checkType(int type) {
            if (type != OPEN && type != CHORD && type != PIE) {
                throw new IllegalArgumentException(
                        "Flapforge shim: unknown Arc2D closure type " + type);
            }
            return type;
        }

        /**
         * Sets the arc (census: the one mutator the game uses).
         *
         * @param x the framing rectangle x
         * @param y the framing rectangle y
         * @param w the framing rectangle width
         * @param h the framing rectangle height
         * @param startAngle the start angle in AWT degrees
         * @param arcAngle the angular extent in AWT degrees
         * @param type {@link #OPEN}, {@link #CHORD} or {@link #PIE}
         */
        public void setArc(double x, double y, double w, double h, double startAngle,
                double arcAngle, int type) {
            checkType(type);
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.start = startAngle;
            this.extent = arcAngle;
            this.type = type;
        }

        @Override
        public awt.geom.Rectangle2D getBounds2D() {
            // Sampled bounds over the swept curve (64 samples, exact at the endpoints and at any
            // axis crossing the sampling hits; no census site reads them); a PIE spans the centre.
            double cx = x + width / 2d;
            double cy = y + height / 2d;
            double rx = width / 2d;
            double ry = height / 2d;
            double minX = type == PIE ? cx : java.lang.Double.POSITIVE_INFINITY;
            double minY = type == PIE ? cy : java.lang.Double.POSITIVE_INFINITY;
            double maxX = type == PIE ? cx : java.lang.Double.NEGATIVE_INFINITY;
            double maxY = type == PIE ? cy : java.lang.Double.NEGATIVE_INFINITY;
            int steps = 64;
            for (int i = 0; i <= steps; i++) {
                double a = Math.toRadians(start + extent * i / steps);
                double px = cx + rx * Math.cos(a);
                double py = cy - ry * Math.sin(a);
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
            return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
        }

        @Override
        public void appendTo(Path2D.Double sink) {
            double cx = x + width / 2d;
            double cy = y + height / 2d;
            Path2D.appendArc(sink, cx, cy, width / 2d, height / 2d, start, extent, true);
            if (type == PIE && sink.hasCurrentPoint()) {
                sink.lineTo(cx, cy);
            }
            if (type != OPEN) {
                sink.closePriv();
            }
        }
    }
}
