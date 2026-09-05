package awt;

import static awt.PixelTestSupport.SIZE;
import static awt.PixelTestSupport.argb;
import static awt.PixelTestSupport.assertBlock;
import static awt.PixelTestSupport.assertInked;
import static awt.PixelTestSupport.assertNotInked;
import static awt.PixelTestSupport.blue;
import static awt.PixelTestSupport.countInked;
import static awt.PixelTestSupport.crisp;
import static awt.PixelTestSupport.inked;
import static awt.PixelTestSupport.red;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import awt.geom.Rectangle2D;
import awt.geom.RoundRectangle2D;
import awt.image.BufferedImage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric pixel proofs of the AWT conventions the census review found the shim had to
 * match beyond what {@link Graphics2DPixelTest} covers: {@code fillPolygon}'s even-odd rule
 * ({@code java.awt.Polygon}), negative-extent rectangles drawing nothing ({@code RectIterator}
 * / {@code RoundRectIterator}), round-rectangle arcs clamped only at draw time, the
 * {@code drawLine} endpoint convention under the default stroke, integer {@code translate}
 * widening, and a gradient paint under {@code drawString} staying in user space.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Graphics2DSemanticsTest {

    private static final Color RED = new Color(255, 0, 0);
    private static final Color BLUE = new Color(0, 0, 255);
    private static final int OPAQUE_RED = 0xFFFF0000;

    // ---------------------------------------------------------------- fillPolygon

    @Test
    public void fillPolygonUsesTheEvenOddRuleLikeAwtPolygon() {
        // A pentagram (every second vertex of a regular pentagon) winds twice around its
        // centre: AWT's Polygon leaves the inner pentagon EMPTY, the five tips are filled.
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        int[] xs = new int[5];
        int[] ys = new int[5];
        double cx = 32;
        double cy = 32;
        double r = 28;
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(-90 + 144 * i); // skip one vertex each step
            xs[i] = (int) Math.round(cx + r * Math.cos(angle));
            ys[i] = (int) Math.round(cy + r * Math.sin(angle));
        }
        g.fillPolygon(xs, ys, 5);

        assertNotInked(image, 32, 32); // the centre of the inner pentagon
        assertNotInked(image, 32, 28);
        assertNotInked(image, 36, 34);
        assertInked(image, 32, 9); // inside the top tip (tip at (32,4))
        assertInked(image, 32, 14);
        assertInked(image, 56, 24); // inside the right tip
        assertInked(image, 8, 24); // inside the left tip
        assertTrue(countInked(image) > 200);
    }

    // ---------------------------------------------------------------- negative extents

    @Test
    public void negativeWidthOrHeightRectanglesDrawNothing() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setStroke(new BasicStroke(2f));
        g.fillRect(32, 32, -16, 16);
        g.fillRect(32, 32, 16, -16);
        g.fill(new Rectangle2D.Double(32, 32, -16, 16));
        g.draw(new Rectangle2D.Double(32, 32, 16, -16));
        g.fillRoundRect(32, 32, -16, 16, 6, 6);
        g.drawRoundRect(32, 32, 16, -16, 6, 6);
        g.fill(new RoundRectangle2D.Double(32, 32, -16, -16, 6, 6));
        assertEquals("AWT draws nothing for a negative extent", 0, countInked(image));

        // The positive counterpart is the reference: exactly w*h pixels.
        g.fillRect(32, 32, 16, 16);
        assertBlock(image, 32, 32, 48, 48, OPAQUE_RED);
        assertEquals(256, countInked(image));
    }

    @Test
    public void negativeClipRectClipsEverythingLikeAwt() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.clipRect(10, 10, -5, 20);
        g.setColor(RED);
        g.fillRect(0, 0, SIZE, SIZE);
        assertEquals("an empty clip lets nothing through", 0, countInked(image));
    }

    // ---------------------------------------------------------------- round rectangle arcs

    @Test
    public void oversizedRoundRectArcsAreClampedToTheFrameAtDrawTime() {
        // RoundRectIterator: arc extents are min(|arc|, side); a 64x64 frame with 200x200 arcs
        // is therefore a circle of radius 32 — and the fields still hold the raw 200.
        RoundRectangle2D.Double round = new RoundRectangle2D.Double(0, 0, 64, 64, 200, 200);
        assertEquals(200d, round.arcwidth, 0d);
        assertEquals(200d, round.archeight, 0d);

        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.fill(round);

        assertInked(image, 32, 32);
        assertInked(image, 32, 1); // 12 o'clock on the circle
        assertInked(image, 1, 32); // 9 o'clock
        assertInked(image, 62, 32);
        assertInked(image, 32, 62);
        assertNotInked(image, 1, 1); // corners are outside a radius-32 circle
        assertNotInked(image, 62, 62);
        assertNotInked(image, 8, 8); // (8.5,8.5) is 33.2 from the centre: outside
        assertNotInked(image, 55, 8);
        int count = countInked(image);
        assertTrue("about pi * 32^2 = 3217 pixels, got " + count,
                count > 3100 && count < 3330);

        // A negative arc extent means the same corner radius (abs), as in AWT.
        RoundRectangle2D.Double negative = new RoundRectangle2D.Double();
        negative.setRoundRect(0, 0, 64, 64, -32, -32);
        BufferedImage other = argb();
        g = crisp(other);
        g.setColor(RED);
        g.fill(negative);
        assertNotInked(other, 1, 1);
        assertInked(other, 32, 32);
        assertInked(other, 16, 16);
    }

    // ---------------------------------------------------------------- drawLine + translate

    @Test
    public void drawLineWithTheDefaultStrokeInksBothEndpoints() {
        // AWT's thin-line drawLine(x1, y, x2, y) inks x1..x2 inclusive on one row; the default
        // BasicStroke(1f) is CAP_SQUARE, whose half-pixel overhang gives the same coverage.
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(8, 4, 55, 4);

        int inkedRows = 0;
        for (int y = 0; y < SIZE; y++) {
            if (inked(image, 30, y)) {
                inkedRows++;
            }
        }
        assertEquals("one pixel tall", 1, inkedRows);
        assertInked(image, 8, 4);
        assertInked(image, 55, 4);
        assertNotInked(image, 7, 4);
        assertNotInked(image, 56, 4);
        assertEquals(48, countInked(image));
    }

    @Test
    public void integerTranslateWidensToTheDoubleForm() {
        BufferedImage image = argb();
        Graphics2D g = crisp(image);
        g.setColor(RED);
        g.translate(10, 20);
        g.fillRect(0, 0, 2, 2);
        assertBlock(image, 10, 20, 12, 22, OPAQUE_RED);
        assertEquals(4, countInked(image));
    }

    // ---------------------------------------------------------------- gradient text

    @Test
    public void gradientPaintUnderDrawStringIsRampedInUserSpace() {
        // Text is drawn under canvas.concat(matrix), so the ramp must NOT be pre-transformed
        // (that would shift it by the translation a second time).
        BufferedImage image = argb(64, 16);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.translate(32, 0);
        g.setPaint(new GradientPaint(-32f, 0f, RED, 31f, 0f, BLUE));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("MMMMMMMMMMMMMMMM", -32, 13);

        int leftSamples = 0;
        int rightSamples = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 64; x++) {
                int p = image.getRGB(x, y);
                if ((p >>> 24) != 0xFF) {
                    continue;
                }
                if (x < 8) {
                    leftSamples++;
                    assertTrue("left ink is red-dominant at (" + x + "," + y + "): "
                            + PixelTestSupport.hex(p), red(p) > blue(p));
                } else if (x >= 56) {
                    rightSamples++;
                    assertTrue("right ink is blue-dominant at (" + x + "," + y + "): "
                            + PixelTestSupport.hex(p), blue(p) > red(p));
                }
            }
        }
        assertTrue("ink at the left edge", leftSamples > 0);
        assertTrue("ink at the right edge", rightSamples > 0);
    }
}
