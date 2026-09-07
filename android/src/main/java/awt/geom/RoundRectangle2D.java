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
 * The outline starts where {@code RoundRectIterator} starts (the top of the left edge) and runs
 * the way it runs (down that edge first, counterclockwise on screen), whatever the arc extents
 * — zero included — so a stroke's dash phase and the winding under a non-zero fill match
 * Java2D.
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
            if (width < 0d || height < 0d || (width == 0d && height == 0d)) {
                // RoundRectIterator parity: a negative extent yields no segments. A frame that
                // is a point yields a point-sized outline there, which Java2D strokes as nothing
                // (Skia would cap the closed point into a dot), so nothing is appended either.
                return;
            }
            double aw = Math.min(Math.abs(arcwidth), width);
            double ah = Math.min(Math.abs(archeight), height);
            double rx = aw / 2d;
            double ry = ah / 2d;
            // Corner ellipse centres; each corner is one 90 deg AWT-convention arc span, walked
            // in RoundRectIterator's order: from the top of the left edge DOWN that edge, along
            // the bottom, up the right edge and back along the top — counterclockwise on the
            // y-down screen, the opposite sense of RectIterator and EllipseIterator, so a round
            // rectangle sharing a non-zero path with those shapes cancels their overlap exactly
            // as Java2D does (no census site does; the parity is for the fill-rule contract).
            // A zero arc width or height changes none of that: RoundRectIterator keeps the same
            // start, direction and segment list, its corner cubics merely collapse onto the
            // edges (appendArc emits those, and nothing at all for a corner with no extent on
            // either axis), so a stroke's dash phase and the winding under a non-zero fill stay
            // where Java2D puts them — the clockwise rectangle outline is not a substitute.
            double rightCx = x + width - rx;
            double leftCx = x + rx;
            double topCy = y + ry;
            double bottomCy = y + height - ry;
            sink.moveTo(x, topCy);
            sink.lineTo(x, bottomCy);
            Path2D.appendArc(sink, leftCx, bottomCy, rx, ry, 180d, 90d, false);
            sink.lineTo(rightCx, y + height);
            Path2D.appendArc(sink, rightCx, bottomCy, rx, ry, 270d, 90d, false);
            sink.lineTo(x + width, topCy);
            Path2D.appendArc(sink, rightCx, topCy, rx, ry, 0d, 90d, false);
            sink.lineTo(leftCx, y);
            Path2D.appendArc(sink, leftCx, topCy, rx, ry, 90d, 90d, false);
            sink.closePriv();
        }
    }
}
