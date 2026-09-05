package awt.geom;

import awt.Shape;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.geom.Line2D}. The game always uses the {@link Double} form
 * (census fact: 7 usage sites), all through the {@code (x1, y1, x2, y2)} constructor:
 * render/ProceduralArt.java:194-195 (the eye-cross shapes) and :881-891 (wing details), drawn via
 * {@code Graphics2D.draw}. Public double fields mirror AWT; the AWT query methods and point
 * overloads are not exercised and are absent.
 */
public abstract class Line2D implements Shape {

    /** The {@code Double} precision form the game uses. */
    public static class Double extends Line2D {

        /** The x of the start point. */
        public double x1;
        /** The y of the start point. */
        public double y1;
        /** The x of the end point. */
        public double x2;
        /** The y of the end point. */
        public double y2;

        /** Creates a zero-length line at the origin. */
        public Double() {
        }

        /** Creates a line between the two points. */
        public Double(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public awt.geom.Rectangle2D getBounds2D() {
            double minX = Math.min(x1, x2);
            double minY = Math.min(y1, y2);
            return new Rectangle2D.Double(minX, minY,
                    Math.abs(x2 - x1), Math.abs(y2 - y1));
        }

        @Override
        public void appendTo(Path2D.Double sink) {
            // AWT parity: a line outline is an open two-point subpath (stroke only, no close).
            sink.moveTo(x1, y1);
            sink.lineTo(x2, y2);
        }
    }
}
