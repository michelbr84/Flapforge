package awt.geom;

import awt.Shape;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.geom.Ellipse2D}. The game always uses the {@link Double} form
 * (census fact: 29 usage sites). Census surface: the no-arg constructor plus
 * {@code setFrame(x, y, w, h)} on scratch fields (ui/component/CardGrid.java:275,
 * ui/component/CurrencyDisplay.java:38, render/CloudLayer.java:69, render/BackgroundRenderer.java:112,
 * render/HudRenderer.java:191-192, render/PickupRenderer.java:48, render/ProceduralArt.java:347,596)
 * and the {@code (x, y, w, h)} constructor (render/ProceduralArt.java:175-178, :869-877;
 * render/GearRenderer.java:39-41; render/BackgroundRenderer.java:561 via
 * {@code Path2D.append}). Public double fields mirror AWT; the AWT query methods are not
 * exercised and are absent.
 *
 * <p>Outline orientation: {@code java.awt.geom.EllipseIterator} starts at 3 o'clock and runs
 * through 6 o'clock (clockwise on screen, like {@code RectIterator}); the shim's
 * {@link Double#appendTo} reproduces that sense, so an ellipse appended into a non-zero
 * {@link Path2D} together with rectangles winds the same way they do.
 */
public abstract class Ellipse2D implements Shape {

    /** The {@code Double} precision form the game uses. */
    public static class Double extends Ellipse2D {

        /** The x of the upper-left corner of the framing rectangle. */
        public double x;
        /** The y of the upper-left corner of the framing rectangle. */
        public double y;
        /** The width of the framing rectangle. */
        public double width;
        /** The height of the framing rectangle. */
        public double height;

        /** Creates a zero-sized ellipse at the origin. */
        public Double() {
        }

        /** Creates an ellipse inside the given framing rectangle. */
        public Double(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        /** Sets the framing rectangle (census: the one mutator the game uses). */
        public void setFrame(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        @Override
        public awt.geom.Rectangle2D getBounds2D() {
            return new Rectangle2D.Double(x, y, width, height);
        }

        @Override
        public void appendTo(Path2D.Double sink) {
            // A full sweep of the ellipse in EllipseIterator's direction: from 3 o'clock through
            // 6 o'clock, i.e. a NEGATIVE sweep in the AWT angle convention (0 deg at 3 o'clock,
            // positive toward 12), which is visually clockwise on the y-down screen — the same
            // sense as RectIterator's outline. The winding shows wherever an ellipse shares a
            // non-zero path with other shapes (render/BackgroundRenderer.java:561 cloudBank
            // appends five ellipses and a rectangle strip into one path): the strip-over-ellipse
            // overlap must count +2 and stay filled, where the opposite sweep would leave it at
            // 0, a hole. Only the subpath the sweep opened is closed. An ellipse with a negative
            // extent appends nothing and leaves a subpath the sink already had open
            // (Path2D.append) untouched; a zero width or height still appends EllipseIterator's
            // degenerate outline — a line across the other extent, which draw strokes and fill
            // ignores (Path2D.appendArc has the cases).
            if (Path2D.appendArc(sink, x + width / 2d, y + height / 2d,
                    width / 2d, height / 2d, 0d, -360d, true)) {
                sink.closePriv();
            }
        }
    }
}
