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
            // A full sweep of the ellipse, AWT angle convention: 0 deg at 3 o'clock, positive
            // sweep visually counterclockwise (toward 12 o'clock).
            Path2D.appendArc(sink, x + width / 2d, y + height / 2d,
                    width / 2d, height / 2d, 0d, 360d, true);
            sink.closePriv();
        }
    }
}
