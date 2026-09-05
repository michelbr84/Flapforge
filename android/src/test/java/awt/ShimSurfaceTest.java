package awt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import awt.geom.Arc2D;
import awt.geom.Ellipse2D;
import awt.geom.Line2D;
import awt.geom.Path2D;
import awt.geom.Rectangle2D;
import awt.geom.RoundRectangle2D;

import org.junit.Test;

/**
 * Pure-JVM proofs of the non-pixel shim surface: the headless {@link GraphicsEnvironment}
 * contract (semantics 9), {@link BasicStroke} constants and validation (semantics 5),
 * {@link RenderingHints} identity keys, and the {@code awt.geom} bounds / path bookkeeping the
 * Graphics2D pipeline relies on.
 */
public class ShimSurfaceTest {

    private static final double EPS = 1e-9;

    // ---------------------------------------------------------------- GraphicsEnvironment

    @Test
    public void environmentIsAlwaysHeadless() {
        assertTrue(GraphicsEnvironment.isHeadless());
    }

    @Test
    public void environmentStubsExistAndCreateCompatibleImageIsOutsideTheCensus() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        assertNotNull(env);
        assertSame(env, GraphicsEnvironment.getLocalGraphicsEnvironment());
        GraphicsDevice device = env.getDefaultScreenDevice();
        assertNotNull(device);
        GraphicsConfiguration configuration = device.getDefaultConfiguration();
        assertNotNull(configuration);
        try {
            configuration.createCompatibleImage(8, 8, Transparency.TRANSLUCENT);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("census"));
        }
    }

    @Test
    public void transparencyConstantsMatchAwt() {
        assertEquals(1, Transparency.OPAQUE);
        assertEquals(2, Transparency.BITMASK);
        assertEquals(3, Transparency.TRANSLUCENT);
    }

    @Test
    public void errorTypesCarryTheirMessage() {
        assertEquals("boom", new AWTError("boom").getMessage());
        assertEquals("bad", new FontFormatException("bad").getMessage());
        assertTrue(new AWTError("x") instanceof Error);
        assertTrue(new FontFormatException("x") instanceof Exception);
    }

    // ---------------------------------------------------------------- BasicStroke

    @Test
    public void strokeConstantsMatchAwtValues() {
        assertEquals(0, BasicStroke.CAP_BUTT);
        assertEquals(1, BasicStroke.CAP_ROUND);
        assertEquals(2, BasicStroke.CAP_SQUARE);
        assertEquals(0, BasicStroke.JOIN_MITER);
        assertEquals(1, BasicStroke.JOIN_ROUND);
        assertEquals(2, BasicStroke.JOIN_BEVEL);
    }

    @Test
    public void strokeDefaultsMatchAwt() {
        BasicStroke s = new BasicStroke(1.5f);
        assertEquals(1.5f, s.width, 0f);
        assertEquals(BasicStroke.CAP_SQUARE, s.cap);
        assertEquals(BasicStroke.JOIN_MITER, s.join);
        assertEquals(10f, s.miterLimit, 0f);
        assertEquals(null, s.dash);
        assertEquals(0f, s.dashPhase, 0f);

        BasicStroke round = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        assertEquals(BasicStroke.CAP_ROUND, round.cap);
        assertEquals(BasicStroke.JOIN_ROUND, round.join);

        // WindZoneRenderer.java:32
        BasicStroke dashed = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {6f, 5f}, 0f);
        assertEquals(2, dashed.dash.length);
        assertEquals(6f, dashed.dash[0], 0f);
        assertEquals(5f, dashed.dash[1], 0f);
    }

    @Test
    public void strokeValidationMatchesAwt() {
        expectIae(() -> new BasicStroke(-1f));
        expectIae(() -> new BasicStroke(1f, 7, BasicStroke.JOIN_MITER));
        expectIae(() -> new BasicStroke(1f, BasicStroke.CAP_BUTT, 7));
        expectIae(() -> new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 0.5f,
                null, 0f));
        expectIae(() -> new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[0], 0f));
        expectIae(() -> new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {1f, -1f}, 0f));
        expectIae(() -> new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {1f, 1f}, -1f));
        // Width 0 is legal: the hairline.
        assertEquals(0f, new BasicStroke(0f).width, 0f);
    }

    private static void expectIae(Runnable action) {
        try {
            action.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // AWT parity
        }
    }

    // ---------------------------------------------------------------- RenderingHints

    @Test
    public void hintKeysAreDistinctIdentities() {
        RenderingHints.Key[] keys = {RenderingHints.KEY_ANTIALIASING,
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.KEY_STROKE_CONTROL, RenderingHints.KEY_INTERPOLATION,
                RenderingHints.KEY_RENDERING};
        for (int i = 0; i < keys.length; i++) {
            for (int j = i + 1; j < keys.length; j++) {
                assertNotSame(keys[i], keys[j]);
                assertTrue(keys[i].intKey() != keys[j].intKey());
            }
        }
        assertNotSame(RenderingHints.VALUE_ANTIALIAS_ON, RenderingHints.VALUE_ANTIALIAS_OFF);
        assertNotSame(RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }

    // ---------------------------------------------------------------- awt.geom bounds

    @Test
    public void rectangleAndEllipseBoundsAreTheirFrames() {
        Rectangle2D r = new Rectangle2D.Double(1, 2, 3, 4).getBounds2D();
        assertFrame(1, 2, 3, 4, r);
        Rectangle2D.Double scratch = new Rectangle2D.Double();
        scratch.setFrame(5, 6, 7, 8);
        assertFrame(5, 6, 7, 8, scratch.getBounds2D());
        Ellipse2D.Double e = new Ellipse2D.Double();
        e.setFrame(-1, -2, 10, 20);
        assertFrame(-1, -2, 10, 20, e.getBounds2D());
        RoundRectangle2D.Double rr = new RoundRectangle2D.Double();
        rr.setRoundRect(0, 0, 10, 4, 8, 8); // fields hold the raw values like AWT; the arc is
        assertFrame(0, 0, 10, 4, rr.getBounds2D()); // clamped to the frame only at draw time
        assertEquals(8d, rr.arcwidth, EPS); // (Graphics2DSemanticsTest proves the clamp)
        assertEquals(8d, rr.archeight, EPS);
    }

    @Test
    public void lineBoundsAreNormalised() {
        assertFrame(2, 1, 8, 4, new Line2D.Double(10, 5, 2, 1).getBounds2D());
    }

    @Test
    public void arcBoundsFollowTheSweptQuadrant() {
        Arc2D.Double arc = new Arc2D.Double(Arc2D.OPEN);
        arc.setArc(0, 0, 20, 20, 0, 90, Arc2D.OPEN); // upper-right quadrant of the circle
        Rectangle2D.Double b = frame(arc.getBounds2D());
        assertEquals(10d, b.x, 1e-6);
        assertEquals(0d, b.y, 1e-6);
        assertEquals(10d, b.width, 1e-6);
        assertEquals(10d, b.height, 1e-6);

        Arc2D.Double lower = new Arc2D.Double(Arc2D.OPEN);
        lower.setArc(0, 0, 20, 20, 0, -90, Arc2D.OPEN); // negative sweep: lower-right
        assertEquals(10d, frame(lower.getBounds2D()).y, 1e-6);

        Arc2D.Double pie = new Arc2D.Double(Arc2D.PIE);
        pie.setArc(0, 0, 20, 20, 30, 30, Arc2D.PIE); // a thin slice still spans the centre
        Rectangle2D.Double pb = frame(pie.getBounds2D());
        assertEquals(10d, pb.x, 1e-6);
        assertEquals(10d, pb.y + pb.height, 1e-6);
    }

    @Test
    public void arcRejectsUnknownClosureTypes() {
        expectIae(() -> new Arc2D.Double(7));
        Arc2D.Double arc = new Arc2D.Double(Arc2D.OPEN);
        expectIae(() -> arc.setArc(0, 0, 1, 1, 0, 90, 7));
        assertEquals(0, Arc2D.OPEN);
        assertEquals(1, Arc2D.CHORD);
        assertEquals(2, Arc2D.PIE);
    }

    @Test
    public void pathBoundsAndWindingRule() {
        Path2D.Double path = new Path2D.Double();
        assertEquals(Path2D.WIND_NON_ZERO, path.getWindingRule());
        assertFrame(0, 0, 0, 0, path.getBounds2D());
        path.moveTo(3, 4);
        path.lineTo(-1, 10);
        path.lineTo(7, 2);
        path.closePath();
        assertFrame(-1, 2, 8, 8, path.getBounds2D());

        Path2D.Double evenOdd = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        assertEquals(Path2D.WIND_EVEN_ODD, evenOdd.getWindingRule());
        expectIae(() -> new Path2D.Double(5));
    }

    @Test
    public void pathLineToWithoutASubpathIsAnError() {
        Path2D.Double path = new Path2D.Double();
        try {
            path.lineTo(1, 1);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // AWT parity: "missing initial moveto"
        }
        path.closePath(); // no-op without a subpath
        assertFrame(0, 0, 0, 0, path.getBounds2D());
    }

    @Test
    public void pathAppendCopiesShapesAndConnectJoinsSubpaths() {
        Path2D.Double path = new Path2D.Double();
        path.append(new Rectangle2D.Double(0, 0, 4, 4), false);
        path.append(new Ellipse2D.Double(10, 10, 6, 6), false);
        assertFrame(0, 0, 16, 16, path.getBounds2D());

        // Copying a path into another path preserves it verbatim.
        Path2D.Double copy = new Path2D.Double();
        copy.append(path, false);
        assertFrame(0, 0, 16, 16, copy.getBounds2D());

        Path2D.Double connected = new Path2D.Double();
        connected.moveTo(0, 0);
        connected.lineTo(2, 0);
        connected.append(new Rectangle2D.Double(5, 5, 1, 1), true);
        assertFrame(0, 0, 6, 6, connected.getBounds2D());
    }

    @Test
    public void appendToRoundTripsEveryShape() {
        Shape[] shapes = {new Rectangle2D.Double(1, 1, 2, 2), new Ellipse2D.Double(0, 0, 4, 2),
                new RoundRectangle2D.Double(0, 0, 10, 10, 4, 4), new Line2D.Double(0, 0, 3, 3),
                pie()};
        for (Shape shape : shapes) {
            Path2D.Double sink = new Path2D.Double();
            shape.appendTo(sink);
            Rectangle2D.Double expected = frame(shape.getBounds2D());
            Rectangle2D.Double actual = frame(sink.getBounds2D());
            assertEquals(shape.getClass().getName(), expected.x, actual.x, 1e-6);
            assertEquals(shape.getClass().getName(), expected.y, actual.y, 1e-6);
            assertEquals(shape.getClass().getName(), expected.width, actual.width, 1e-6);
            assertEquals(shape.getClass().getName(), expected.height, actual.height, 1e-6);
        }
    }

    private static Arc2D.Double pie() {
        Arc2D.Double pie = new Arc2D.Double(Arc2D.PIE);
        pie.setArc(0, 0, 20, 20, 0, 90, Arc2D.PIE);
        return pie;
    }

    /** Every shim {@code getBounds2D()} hands back the {@code Double} form. */
    private static Rectangle2D.Double frame(Rectangle2D bounds) {
        return (Rectangle2D.Double) bounds;
    }

    private static void assertFrame(double x, double y, double w, double h, Rectangle2D r) {
        Rectangle2D.Double d = frame(r);
        assertEquals("x", x, d.x, EPS);
        assertEquals("y", y, d.y, EPS);
        assertEquals("width", w, d.width, EPS);
        assertEquals("height", h, d.height, EPS);
    }
}
