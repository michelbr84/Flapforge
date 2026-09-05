package awt;

import static awt.PixelTestSupport.SIZE;
import static awt.PixelTestSupport.argb;
import static awt.PixelTestSupport.assertBlock;
import static awt.PixelTestSupport.assertBlockClear;
import static awt.PixelTestSupport.assertInked;
import static awt.PixelTestSupport.assertNotInked;
import static awt.PixelTestSupport.assertOpaque;
import static awt.PixelTestSupport.assertPixel;
import static awt.PixelTestSupport.blue;
import static awt.PixelTestSupport.countInked;
import static awt.PixelTestSupport.countInkedInAnnulus;
import static awt.PixelTestSupport.crisp;
import static awt.PixelTestSupport.green;
import static awt.PixelTestSupport.inked;
import static awt.PixelTestSupport.red;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import awt.geom.Arc2D;
import awt.geom.Ellipse2D;
import awt.geom.Path2D;
import awt.geom.Rectangle2D;
import awt.geom.RoundRectangle2D;
import awt.image.BufferedImage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric pixel proofs of the M10 shim semantics contract for {@link Graphics2D}: arc
 * orientation, fill rules, gradients, dashes, the transform chain, device-space clipping,
 * stroke-versus-fill, and colour layout. Every test rasterises into an ARGB
 * {@link BufferedImage} through {@code createGraphics()} and reads the pixels back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Graphics2DPixelTest {

    private static final Color RED = new Color(255, 0, 0);
    private static final Color BLUE = new Color(0, 0, 255);
    private static final int OPAQUE_RED = 0xFFFF0000;
    private static final int OPAQUE_BLUE = 0xFF0000FF;

    // ---------------------------------------------------------------- (a) arc orientation

    @Test
    public void pieArcFromZeroToNinetyFillsTheUpperRightQuadrant() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        Arc2D.Double pie = new Arc2D.Double(Arc2D.PIE);
        pie.setArc(8, 8, 48, 48, 0, 90, Arc2D.PIE); // circle centred (32,32), r = 24
        g.fill(pie);

        assertInked(image, 44, 20); // upper-right, near the top of the slice
        assertInked(image, 34, 30); // upper-right, next to the centre
        assertInked(image, 50, 30); // upper-right, along the 3 o'clock edge
        assertNotInked(image, 44, 44); // lower-right mirror: NOT inked
        assertNotInked(image, 20, 20); // upper-left
        assertNotInked(image, 20, 44); // lower-left
        assertNotInked(image, 34, 34); // just below the centre, outside the slice
        assertPixel(image, 44, 20, OPAQUE_RED);
    }

    @Test
    public void chordArcClosesWithAStraightSegment() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        Arc2D.Double chord = new Arc2D.Double(Arc2D.CHORD);
        chord.setArc(8, 8, 48, 48, 0, 90, Arc2D.CHORD);
        g.fill(chord);

        // Chord from (56,32) to (32,8): the region between it and the arc is inked ...
        assertInked(image, 48, 18);
        // ... but the centre side of the chord is not (a PIE would ink it).
        assertNotInked(image, 34, 30);
        assertNotInked(image, 44, 44);
    }

    @Test
    public void openArcStrokeFollowsTheHudRingDirection() {
        // HudRenderer: setArc(..., 90, -90, OPEN) sweeps from 12 o'clock toward 3 o'clock.
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setStroke(new BasicStroke(3f));
        Arc2D.Double ring = new Arc2D.Double(Arc2D.OPEN);
        ring.setArc(8, 8, 48, 48, 90, -90, Arc2D.OPEN);
        g.draw(ring);

        double c = 32d;
        double r = 24d;
        double d = r / Math.sqrt(2d);
        assertInked(image, (int) (c + d), (int) (c - d)); // 45 deg, upper-right
        assertNotInked(image, (int) (c - d), (int) (c - d)); // upper-left
        assertNotInked(image, (int) (c + d), (int) (c + d)); // lower-right
        assertNotInked(image, (int) (c - d), (int) (c + d)); // lower-left
        assertNotInked(image, 32, 32); // an OPEN arc has no interior
    }

    @Test
    public void drawArcInksTheTopHalfForStartZeroExtentOneEighty() {
        // CardGrid.java:560 draws the lock shackle with drawArc(..., 0, 180).
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setStroke(new BasicStroke(3f));
        g.drawArc(8, 8, 48, 48, 0, 180);

        assertInked(image, 32, 8); // 12 o'clock
        assertInked(image, 15, 15); // 10-11 o'clock
        assertInked(image, 48, 15); // 1-2 o'clock
        assertNotInked(image, 32, 55); // 6 o'clock
        assertNotInked(image, 15, 48);
        assertNotInked(image, 48, 48);
    }

    @Test
    public void drawArcNegativeExtentSweepsClockwiseOnScreen() {
        // ModifierChoiceOverlay.java:424: drawArc(x, y, size, size, 90, -angle) is a timer ring
        // that empties clockwise from 12 o'clock.
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setStroke(new BasicStroke(3f));
        g.drawArc(8, 8, 48, 48, 90, -90);

        assertInked(image, 48, 15); // between 12 and 3 o'clock
        assertNotInked(image, 15, 15); // between 9 and 12 o'clock
        assertNotInked(image, 48, 48);
        assertNotInked(image, 15, 48);
    }

    // ---------------------------------------------------------------- (b) winding rules

    @Test
    public void evenOddPathLeavesTheInnerCircleUnfilled() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.fill(concentricCircles(Path2D.WIND_EVEN_ODD));

        assertNotInked(image, 32, 32); // the hole
        assertNotInked(image, 36, 30);
        assertInked(image, 32, 16); // the ring (16 px from the centre)
        assertInked(image, 48, 32);
        assertNotInked(image, 32, 4); // outside the outer circle
    }

    @Test
    public void nonZeroPathFillsTheInnerCircle() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.fill(concentricCircles(Path2D.WIND_NON_ZERO));

        assertInked(image, 32, 32);
        assertInked(image, 32, 16);
        assertNotInked(image, 32, 4);
    }

    private static Path2D.Double concentricCircles(int windingRule) {
        Path2D.Double path = new Path2D.Double(windingRule);
        path.append(new Ellipse2D.Double(8, 8, 48, 48), false); // r = 24
        path.append(new Ellipse2D.Double(24, 24, 16, 16), false); // r = 8
        return path;
    }

    // ---------------------------------------------------------------- (c) linear gradient

    @Test
    public void gradientPaintRampsFromColorOneToColorTwo() {
        BufferedImage image = argb(64, 16);
        Graphics2D g = crisp(image);
        g.setPaint(new GradientPaint(0f, 8f, RED, 63f, 8f, BLUE));
        g.fillRect(0, 0, 64, 16);

        int left = image.getRGB(1, 8);
        int middle = image.getRGB(32, 8);
        int right = image.getRGB(62, 8);
        assertOpaque(image, 1, 8);
        assertOpaque(image, 32, 8);
        assertOpaque(image, 62, 8);
        assertTrue("left should be red-dominant: " + PixelTestSupport.hex(left),
                red(left) > 200 && blue(left) < 60 && green(left) < 20);
        assertTrue("right should be blue-dominant: " + PixelTestSupport.hex(right),
                blue(right) > 200 && red(right) < 60 && green(right) < 20);
        assertTrue("middle should be mixed: " + PixelTestSupport.hex(middle),
                red(middle) > 80 && red(middle) < 180 && blue(middle) > 80 && blue(middle) < 180);
        // Monotonic along x: red never increases, blue never decreases.
        for (int x = 1; x < 64; x++) {
            assertTrue(red(image.getRGB(x, 8)) <= red(image.getRGB(x - 1, 8)) + 1);
            assertTrue(blue(image.getRGB(x, 8)) + 1 >= blue(image.getRGB(x - 1, 8)));
        }
    }

    @Test
    public void gradientEndPointsGoThroughTheTransform() {
        // ProceduralArt draws the sky gradient in world space under the viewport transform.
        BufferedImage image = argb(64, 16);
        Graphics2D g = crisp(image);
        g.translate(32, 0);
        g.setPaint(new GradientPaint(-32f, 0f, RED, 31f, 0f, BLUE));
        g.fillRect(-32, 0, 64, 16);

        assertTrue(red(image.getRGB(1, 8)) > 200);
        assertTrue(blue(image.getRGB(62, 8)) > 200);
        assertSame("getPaint hands the gradient back",
                GradientPaint.class, g.getPaint().getClass());
    }

    @Test
    public void setColorAfterSetPaintReplacesTheGradient() {
        BufferedImage image = argb(64, 16);
        Graphics2D g = crisp(image);
        g.setPaint(new GradientPaint(0f, 0f, RED, 63f, 0f, BLUE));
        g.setColor(BLUE);
        g.fillRect(0, 0, 64, 16);
        assertBlock(image, 0, 0, 64, 16, OPAQUE_BLUE);
        assertSame(BLUE, g.getPaint());
    }

    // ---------------------------------------------------------------- (d) dashes

    @Test
    public void dashedStrokeAlternatesInkAndGaps() {
        BufferedImage image = argb(64, 8);
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {6f, 6f}, 0f));
        g.drawLine(0, 4, 63, 4); // width 2 centred on y = 4 covers rows 3 and 4

        // First 6 px segment inked, first gap clear, second segment inked.
        assertBlock(image, 0, 3, 6, 5, OPAQUE_RED);
        assertBlockClear(image, 6, 3, 12, 5);
        assertBlock(image, 12, 3, 18, 5, OPAQUE_RED);
        assertBlockClear(image, 18, 3, 24, 5);
        // Nothing above or below the 2 px band.
        assertBlockClear(image, 0, 0, 64, 3);
        assertBlockClear(image, 0, 5, 64, 8);

        int inkedAlong = 0;
        int clearAlong = 0;
        for (int x = 0; x < 64; x++) {
            if (inked(image, x, 4)) {
                inkedAlong++;
            } else {
                clearAlong++;
            }
        }
        assertTrue("both inked and clear pixels along the line", inkedAlong > 0 && clearAlong > 0);
        // 6 on / 6 off along the 63 px line: 0-5, 12-17, 24-29, 36-41, 48-53 and 60-62.
        assertEquals(33, inkedAlong);
    }

    @Test
    public void dashPhaseShiftsThePattern() {
        BufferedImage image = argb(64, 8);
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {6f, 6f}, 6f)); // start inside the gap
        g.drawLine(0, 4, 63, 4);

        assertBlockClear(image, 0, 3, 6, 5);
        assertBlock(image, 6, 3, 12, 5, OPAQUE_RED);
    }

    @Test
    public void solidStrokeHasNoGaps() {
        BufferedImage image = argb(64, 8);
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.drawLine(0, 4, 64, 4);
        assertBlock(image, 0, 3, 64, 5, OPAQUE_RED);
        assertBlockClear(image, 0, 0, 64, 3);
        assertBlockClear(image, 0, 5, 64, 8);
    }

    @Test
    public void dashLengthsScaleWithTheTransform() {
        BufferedImage image = argb(64, 8);
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.scale(2, 2);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {3f, 3f}, 0f)); // 3 user px = 6 device px; width 1 -> 2 px
        g.drawLine(0, 2, 32, 2); // device: y = 4, rows 3 and 4

        assertBlock(image, 0, 3, 6, 5, OPAQUE_RED);
        assertBlockClear(image, 6, 3, 12, 5);
        assertBlock(image, 12, 3, 18, 5, OPAQUE_RED);
        assertBlockClear(image, 0, 0, 64, 3);
        assertBlockClear(image, 0, 5, 64, 8);
    }

    // ---------------------------------------------------------------- (e) transform chain

    @Test
    public void translateRotateScaleApplyNewestFirstLikeAwt() {
        // p -> T * R * S * p: (x, y) -> (32 - 2y, 32 + 2x). fillRect(0,0,4,2) lands on
        // device x in [28, 32), y in [32, 40).
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.translate(32, 32);
        g.rotate(Math.PI / 2);
        g.scale(2, 2);
        g.fillRect(0, 0, 4, 2);

        assertBlock(image, 28, 32, 32, 40, OPAQUE_RED);
        assertNotInked(image, 32, 36);
        assertNotInked(image, 27, 36);
        assertNotInked(image, 30, 31);
        assertNotInked(image, 30, 40);
        assertEquals(4 * 8, countInked(image));
    }

    @Test
    public void rotateAboutAPivotKeepsThePivotFixed() {
        // rotate(PI/2, 32, 32): (dx, dy) about the pivot -> (-dy, dx). A rect right of the
        // pivot at dy in [-8,-4], dx in [0,8] moves to dx' in [4,8], dy' in [0,8].
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.rotate(Math.PI / 2, 32, 32);
        g.fillRect(32, 24, 8, 4);

        assertBlock(image, 36, 32, 40, 40, OPAQUE_RED);
        assertBlockClear(image, 32, 24, 40, 28); // the untransformed spot stays clear
        assertEquals(32, countInked(image));
    }

    @Test
    public void rotationIsClockwiseOnTheYDownScreen() {
        // AWT rotate(+theta) turns +x toward +y. A bar along +x from the origin ends up along +y.
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.translate(8, 8);
        g.rotate(Math.PI / 2);
        g.fillRect(0, -1, 20, 2); // bar along +x, 2 px thick, from the origin

        assertBlock(image, 7, 8, 9, 28, OPAQUE_RED); // now along +y (downwards)
        assertBlockClear(image, 9, 7, 28, 9); // not along +x
    }

    @Test
    public void nonUniformScaleStretchesAxesIndependently() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.scale(2, 0.5);
        g.fillRect(0, 0, 4, 8);

        assertBlock(image, 0, 0, 8, 4, OPAQUE_RED);
        assertBlockClear(image, 8, 0, 16, 8);
        assertBlockClear(image, 0, 4, 8, 8);
    }

    @Test
    public void inverseScaleAndTranslateRestoreTheFrame() {
        // The bird renderer brackets its drawing with scale(s) ... scale(1/s) and
        // translate(c) ... translate(-c); afterwards drawing must land where it did before.
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        double sx = 1.06 * 17;
        double sy = 17;
        g.translate(20.5, 30.25);
        g.scale(sx, sy);
        g.scale(1 / sx, 1 / sy);
        g.translate(-20.5, -30.25);
        g.fillRect(4, 4, 8, 8);

        assertBlock(image, 4, 4, 12, 12, OPAQUE_RED);
        assertEquals(64, countInked(image));
    }

    @Test
    public void strokeWidthFollowsTheTransformScale() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.scale(4, 4);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.drawLine(0, 4, 16, 4); // device: y = 16, width 8 -> rows 12..19

        assertBlock(image, 0, 12, 64, 20, OPAQUE_RED);
        assertBlockClear(image, 0, 0, 64, 12);
        assertBlockClear(image, 0, 20, 64, 64);
    }

    @Test
    public void zeroWidthStrokeIsAOnePixelHairlineEvenWhenScaled() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.scale(4, 4);
        g.setStroke(new BasicStroke(0f));
        g.drawLine(0, 4, 16, 4);

        int inkedRows = 0;
        for (int y = 0; y < SIZE; y++) {
            if (inked(image, 32, y)) {
                inkedRows++;
            }
        }
        assertEquals("a hairline is exactly one pixel tall", 1, inkedRows);
    }

    // ---------------------------------------------------------------- (f) clipping

    @Test
    public void setClipLimitsFillsToTheClipRectangle() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setClip(new Rectangle2D.Double(16, 16, 32, 32));
        g.fillRect(0, 0, SIZE, SIZE);

        assertBlock(image, 16, 16, 48, 48, OPAQUE_RED);
        assertNotInked(image, 8, 8);
        assertNotInked(image, 56, 56);
        assertNotInked(image, 15, 32);
        assertNotInked(image, 48, 32);
        assertEquals(32 * 32, countInked(image));
    }

    @Test
    public void clipIntersectsWithTheCurrentClip() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setClip(new Rectangle2D.Double(16, 16, 32, 32));
        g.clip(new Rectangle2D.Double(0, 0, 32, 32)); // intersection: [16,32) x [16,32)
        g.setColor(RED);
        g.fillRect(0, 0, SIZE, SIZE);

        assertBlock(image, 16, 16, 32, 32, OPAQUE_RED);
        assertNotInked(image, 40, 40); // inside the first clip, outside the second
        assertNotInked(image, 8, 8); // inside the second clip, outside the first
        assertEquals(16 * 16, countInked(image));
    }

    @Test
    public void clipRectIntersectsLikeClip() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setClip(new Rectangle2D.Double(16, 16, 32, 32));
        g.clipRect(0, 0, 32, 32);
        g.setColor(RED);
        g.fillRect(0, 0, SIZE, SIZE);
        assertEquals(16 * 16, countInked(image));
    }

    @Test
    public void setClipNullClearsTheClip() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setClip(new Rectangle2D.Double(16, 16, 32, 32));
        g.setClip(null);
        assertNull(g.getClip());
        g.setColor(RED);
        g.fillRect(0, 0, SIZE, SIZE);
        assertEquals(SIZE * SIZE, countInked(image));
    }

    @Test
    public void clipIsStoredInDeviceSpaceSoALaterTranslateDoesNotMoveIt() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setClip(new Rectangle2D.Double(16, 16, 32, 32));
        g.translate(100, 100);
        g.setColor(RED);
        g.fillRect(-100, -100, SIZE, SIZE); // device: the whole image

        assertBlock(image, 16, 16, 48, 48, OPAQUE_RED);
        assertNotInked(image, 8, 8);
        assertNotInked(image, 56, 56);
        assertEquals(32 * 32, countInked(image));
    }

    @Test
    public void setClipAppliesTheTransformInForceAtSetClipTime() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.translate(16, 16);
        g.setClip(new Rectangle2D.Double(0, 0, 32, 32)); // device [16,48)^2
        g.translate(-16, -16);
        g.setColor(RED);
        g.fillRect(0, 0, SIZE, SIZE);

        assertBlock(image, 16, 16, 48, 48, OPAQUE_RED);
        assertNotInked(image, 8, 8);
        assertEquals(32 * 32, countInked(image));
    }

    @Test
    public void getClipRoundTripsThroughSetClip() {
        // The census pattern: Shape old = g.getClip(); g.clipRect(...); ...; g.setClip(old).
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        assertNull(g.getClip());
        g.setClip(new Rectangle2D.Double(16, 16, 32, 32));
        Shape saved = g.getClip();
        assertNotNull(saved);
        Rectangle2D.Double bounds = (Rectangle2D.Double) saved.getBounds2D();
        assertEquals(16d, bounds.x, 1e-6);
        assertEquals(32d, bounds.width, 1e-6);

        g.clipRect(0, 0, 24, 24); // narrow to [16,24)^2
        g.setColor(BLUE);
        g.fillRect(0, 0, SIZE, SIZE);
        assertNotInked(image, 40, 40);

        g.setClip(saved); // restore the wider clip
        g.setColor(RED);
        g.fillRect(0, 0, SIZE, SIZE);
        assertPixel(image, 40, 40, OPAQUE_RED);
        assertNotInked(image, 8, 8);
        assertEquals(32 * 32, countInked(image));
    }

    @Test
    public void clipWithANonRectangularShapeFollowsItsOutline() {
        // ProceduralArt.drawIcon clips to a rounded tile before painting the sky.
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.clip(new Ellipse2D.Double(8, 8, 48, 48));
        g.setColor(RED);
        g.fillRect(0, 0, SIZE, SIZE);

        assertInked(image, 32, 32);
        assertInked(image, 32, 10);
        assertNotInked(image, 10, 10); // corner outside the circle
        assertNotInked(image, 54, 54);
    }

    // ---------------------------------------------------------------- (g) stroke versus fill

    @Test
    public void drawEllipseInksOnlyTheOutline() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Ellipse2D.Double(7.5, 7.5, 48, 48)); // centre (31.5,31.5), r = 24

        assertTrue("outline inked", countInkedInAnnulus(image, 31.5, 31.5, 22.5, 25.5) > 40);
        assertEquals("interior clear", 0, countInkedInAnnulus(image, 31.5, 31.5, 0, 21));
        assertNotInked(image, 31, 31);
        assertInked(image, 31, 7); // 12 o'clock on the outline
        assertInked(image, 55, 31); // 3 o'clock
        assertInked(image, 31, 55); // 6 o'clock
        assertInked(image, 7, 31); // 9 o'clock
    }

    @Test
    public void fillEllipseInksTheInterior() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.fill(new Ellipse2D.Double(8, 8, 48, 48));

        assertInked(image, 32, 32);
        assertTrue(countInkedInAnnulus(image, 32, 32, 0, 22) > 1400); // ~pi * 22^2 = 1520
        assertEquals(0, countInkedInAnnulus(image, 32, 32, 25, 64));
    }

    @Test
    public void fillOvalAndDrawOvalMatchTheShapeForms() {
        BufferedImage filled = argb();
        Graphics2D g = crisp(filled);
        g.setColor(RED);
        g.fillOval(8, 8, 48, 48);
        assertInked(filled, 32, 32);
        assertNotInked(filled, 10, 10);

        BufferedImage drawn = argb();
        g = crisp(drawn);
        g.setColor(RED);
        g.setStroke(new BasicStroke(3f));
        g.drawOval(8, 8, 48, 48);
        assertNotInked(drawn, 32, 32);
        assertInked(drawn, 32, 8);
    }

    @Test
    public void roundRectangleFillsTheBodyButNotTheCorners() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.fill(new RoundRectangle2D.Double(0, 0, 64, 64, 32, 32)); // corner radius 16

        assertInked(image, 32, 32);
        assertInked(image, 1, 32); // edge midpoints are square
        assertInked(image, 32, 1);
        assertNotInked(image, 1, 1); // corners are cut
        assertNotInked(image, 62, 1);
        assertNotInked(image, 1, 62);
        assertNotInked(image, 62, 62);
        assertInked(image, 16, 16); // the corner arc centre is inside
    }

    @Test
    public void fillRoundRectAndDrawRoundRectUseTheSameGeometry() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.fillRoundRect(0, 0, 64, 64, 32, 32);
        assertNotInked(image, 1, 1);
        assertInked(image, 32, 32);

        BufferedImage outline = argb();
        g = crisp(outline);
        g.setColor(RED);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(1, 1, 62, 62, 32, 32);
        assertNotInked(outline, 32, 32);
        assertInked(outline, 32, 1);
        assertNotInked(outline, 1, 1);
    }

    @Test
    public void fillPolygonClosesAndFillsWhileDrawPolylineStaysOpen() {
        BufferedImage filled = argb();
        Graphics2D g = crisp(filled);
        g.setColor(RED);
        g.fillPolygon(new int[] {0, 64, 0}, new int[] {0, 0, 64}, 3);
        assertInked(filled, 10, 10);
        assertInked(filled, 30, 20);
        assertNotInked(filled, 50, 50);

        BufferedImage polyline = argb();
        g = crisp(polyline);
        g.setColor(RED);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.drawPolyline(new int[] {4, 60, 60}, new int[] {4, 4, 60}, 3);
        assertInked(polyline, 32, 4); // along the top edge
        assertInked(polyline, 60, 32); // down the right edge
        assertNotInked(polyline, 32, 32); // the closing diagonal is NOT drawn
        assertNotInked(polyline, 4, 32); // nor a left edge
    }

    // ---------------------------------------------------------------- (h) colour

    @Test
    public void colorLayoutMatchesAwtArgb() {
        assertEquals(0xFF123456, new Color(0x123456).getRGB());
        assertEquals(0xFF123456, new Color(0x12, 0x34, 0x56).getRGB());
        assertEquals(255, new Color(1, 2, 3).getAlpha());
        assertEquals(255, new Color(0x00123456).getAlpha());
        assertEquals(0x280A141E, new Color(10, 20, 30, 40).getRGB());
        assertEquals(0x80112233, new Color(0x80112233, true).getRGB());
        assertEquals(0xFF112233, new Color(0x80112233, false).getRGB());
        Color c = new Color(0x80112233, true);
        assertEquals(0x80, c.getAlpha());
        assertEquals(0x11, c.getRed());
        assertEquals(0x22, c.getGreen());
        assertEquals(0x33, c.getBlue());
    }

    @Test
    public void setColorFillRectWritesExactlyThatArgb() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(new Color(0x12, 0x34, 0x56));
        g.fillRect(4, 4, 8, 8);

        assertBlock(image, 4, 4, 12, 12, 0xFF123456);
        assertBlockClear(image, 0, 0, 64, 4);
        assertBlockClear(image, 12, 4, 64, 12);
        assertEquals(64, countInked(image));
    }

    @Test
    public void translucentColorCompositesOverTheDestination() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(new Color(255, 255, 255));
        g.fillRect(0, 0, 64, 64);
        g.setColor(new Color(0, 0, 0, 128));
        g.fillRect(0, 0, 64, 64);

        int pixel = image.getRGB(32, 32);
        assertEquals(0xFF, pixel >>> 24);
        assertTrue("mid grey after 50% black over white: " + PixelTestSupport.hex(pixel),
                Math.abs(red(pixel) - 127) <= 2 && red(pixel) == green(pixel)
                        && green(pixel) == blue(pixel));
    }

    // ---------------------------------------------------------------- rendering hints

    @Test
    public void antialiasingHintControlsEdgeCoverage() {
        BufferedImage aliased = argb();
        Graphics2D g = aliased.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(RED);
        g.fill(new Ellipse2D.Double(8, 8, 48, 48));
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int p = aliased.getRGB(x, y);
                assertTrue("aliased pixels are all-or-nothing: " + PixelTestSupport.hex(p),
                        p == 0 || p == OPAQUE_RED);
            }
        }

        BufferedImage smooth = argb();
        g = smooth.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(RED);
        g.fill(new Ellipse2D.Double(8, 8, 48, 48));
        int partial = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int alpha = smooth.getRGB(x, y) >>> 24;
                if (alpha > 0 && alpha < 255) {
                    partial++;
                }
            }
        }
        assertTrue("antialiased edge has partial coverage", partial > 0);
        assertPixel(smooth, 32, 32, OPAQUE_RED);
    }

    @Test
    public void everyCensusHintIsAccepted() {
        Graphics2D g = argb().createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.dispose();
    }

    // ---------------------------------------------------------------- state accessors

    @Test
    public void strokeAndPaintStateRoundTrip() {
        Graphics2D g = argb().createGraphics();
        Stroke stroke = new BasicStroke(1.6f);
        g.setStroke(stroke);
        assertSame(stroke, g.getStroke());
        g.setColor(RED);
        assertSame(RED, g.getPaint());
        GradientPaint gradient = new GradientPaint(0f, 0f, RED, 0f, 10f, BLUE);
        g.setPaint(gradient);
        assertSame(gradient, g.getPaint());
    }
}
