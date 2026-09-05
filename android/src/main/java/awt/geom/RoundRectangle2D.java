package awt.geom;

import awt.Shape;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.geom.RoundRectangle2D}. The game always uses the {@link Double}
 * form (census fact: 7 usage sites). Census surface: the no-arg constructor plus
 * {@code setRoundRect(x, y, w, h, arcw, arch)} (render/ObstacleRenderer.java:42, :128, :148;
 * render/PistonRenderer.java:43, :117, :128; render/WindZoneRenderer.java:36, :105) and the
 * {@code (x, y, w, h, arcw, arch)} constructor (render/ProceduralArt.java:654). Public double
 * fields mirror AWT and hold the values as given (AWT stores them raw too); the clamping of arc
 * extents to the frame — {@code min(|arc|, side)}, exactly {@code RoundRectIterator} — happens
 * only when the outline is produced, and a negative width or height produces no outline at all.
 */
public abstract class RoundRectangle2D implements Shape {

    /** The {@code Double} precision form the game uses. */
    public static class Double extends RoundRectangle2D {

        /** The x of the upper-left corner. */
        public double x;
        /** The y of the upper-left corner. */
        public double y;
        /** The width. */
        public double width;
        /** The height. */
        public double height;
        /** The arc width (diameter of the corner ellipses). */
        public double arcwidth;
        /** The arc height (diameter of the corner ellipses). */
        public double archeight;

        /** Creates a plain rectangle at the origin. */
        public Double() {
        }

        /** Creates a rounded rectangle (fields hold the given values, AWT parity). */
        public Double(double x, double y, double w, double h, double arcw, double arch) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.arcwidth = arcw;
            this.archeight = arch;
        }

        /** Sets the rounded rectangle (census: the one mutator the game uses). */
        public void setRoundRect(double x, double y, double w, double h, double arcw,
                double arch) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.arcwidth = arcw;
            this.archeight = arch;
        }

        @Override
        public awt.geom.Rectangle2D getBounds2D() {
            return new Rectangle2D.Double(x, y, width, height);
        }

        @Override
        public void appendTo(Path2D.Double sink) {
            if (width < 0d || height < 0d) {
                // RoundRectIterator parity: a negative extent yields no segments.
                return;
            }
            double aw = Math.min(Math.abs(arcwidth), width);
            double ah = Math.min(Math.abs(archeight), height);
            if (aw <= 0d || ah <= 0d) {
                sink.rectangle(x, y, width, height);
                return;
            }
            double rx = aw / 2d;
            double ry = ah / 2d;
            // Corner ellipse centres; each corner is one 90 deg AWT-convention arc span.
            double rightCx = x + width - rx;
            double leftCx = x + rx;
            double topCy = y + ry;
            double bottomCy = y + height - ry;
            sink.moveTo(x + rx, y);
            sink.lineTo(rightCx, y);
            Path2D.appendArc(sink, rightCx, topCy, rx, ry, 90d, -90d, false);
            sink.lineTo(x + width, bottomCy);
            Path2D.appendArc(sink, rightCx, bottomCy, rx, ry, 0d, -90d, false);
            sink.lineTo(leftCx, y + height);
            Path2D.appendArc(sink, leftCx, bottomCy, rx, ry, -90d, -90d, false);
            sink.lineTo(x, topCy);
            Path2D.appendArc(sink, leftCx, topCy, rx, ry, -180d, -90d, false);
            sink.closePriv();
        }
    }
}
