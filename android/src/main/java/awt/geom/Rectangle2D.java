package awt.geom;

import awt.Shape;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.geom.Rectangle2D}. The game always uses the {@link Double} form
 * (census fact: 19 usage sites). Census surface: the no-arg constructor plus
 * {@code setFrame(x, y, w, h)} on scratch fields (render/BackgroundRenderer.java:111, :261-337;
 * render/BirdRenderer.java:39; render/GearRenderer.java:44; render/LightningRenderer.java:58;
 * render/ObstacleRenderer.java:43; render/ObstacleRendererRegistry.java:39;
 * render/PistonRenderer.java:44; render/WindZoneRenderer.java:37) and the
 * {@code (x, y, w, h)} constructor (render/ProceduralArt.java:666-668). Public double fields
 * mirror AWT. The AWT query methods ({@code getX/getWidth/intersects/contains/outcode}) are not
 * exercised and are absent; the only method Graphics2D consumes is the {@link Shape} contract.
 */
public abstract class Rectangle2D implements Shape {

    /** The {@code Double} precision form the game uses. */
    public static class Double extends Rectangle2D {

        /** The x of the upper-left corner. */
        public double x;
        /** The y of the upper-left corner. */
        public double y;
        /** The width. */
        public double width;
        /** The height. */
        public double height;

        /** Creates a zero-sized rectangle at the origin. */
        public Double() {
        }

        /** Creates a rectangle with the given frame. */
        public Double(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        /** Sets the frame (census: the one mutator the game uses). */
        public void setFrame(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        @Override
        public awt.geom.Rectangle2D getBounds2D() {
            return new Double(x, y, width, height);
        }

        @Override
        public void appendTo(Path2D.Double sink) {
            sink.rectangle(x, y, width, height);
        }
    }
}
