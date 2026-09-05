package awt;

import static awt.PixelTestSupport.assertPixel;
import static awt.PixelTestSupport.blue;
import static awt.PixelTestSupport.green;
import static awt.PixelTestSupport.hex;
import static awt.PixelTestSupport.red;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import awt.geom.Ellipse2D;
import awt.image.BufferedImage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proofs of the {@link BufferedImage} pixel-model semantics (contract item 10) that
 * {@link BufferedImageTest} does not cover: the row {@code setRGB} / {@code getRGB} round trip on
 * both census image types (alpha preserved on {@code TYPE_INT_ARGB}, forced opaque on
 * {@code TYPE_INT_RGB}), the premultiplication tolerance for translucent ARGB values, and a
 * miniature of {@code ProceduralArt.icon} (fill + 2 px stroke of an ellipse through
 * {@code createGraphics()}) checked at the centre and the corners.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BufferedImageSemanticsTest {

    @Test
    public void argbRowsRoundTripWithAlphaPreserved() {
        // DarknessOverlay.java:76 — setRGB(0, y, w, 1, row, 0, w), one row per call.
        int w = 6;
        int h = 3;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[][] rows = {
            {0xFF102030, 0xFF405060, 0xFF708090, 0xFFA0B0C0, 0xFFD0E0F0, 0xFFFFFFFF},
            {0x00000000, 0x11000000, 0x80000000, 0xC0000000, 0xFE000000, 0xFF000000},
            {0xFF000000, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF7F7F7F, 0x00000000},
        };
        for (int y = 0; y < h; y++) {
            image.setRGB(0, y, w, 1, rows[y], 0, w);
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                assertEquals("(" + x + "," + y + ")", hex(rows[y][x]), hex(image.getRGB(x, y)));
            }
        }
    }

    @Test
    public void rgbRowsRoundTripWithAlphaForcedOpaque() {
        // AWT TYPE_INT_RGB: the alpha byte of a written value is ignored, and every read is
        // 0xFFRRGGBB. (Without the shim forcing the alpha, 0x00123456 would premultiply to
        // nothing and read back as opaque BLACK.)
        int w = 5;
        BufferedImage image = new BufferedImage(w, 2, BufferedImage.TYPE_INT_RGB);
        int[] row = {0x00123456, 0x80ABCDEF, 0xFF010203, 0x00000000, 0x7FFFFFFF};
        image.setRGB(0, 1, w, 1, row, 0, w);
        int[] expected = {0xFF123456, 0xFFABCDEF, 0xFF010203, 0xFF000000, 0xFFFFFFFF};
        for (int x = 0; x < w; x++) {
            assertEquals("(" + x + ",1)", hex(expected[x]), hex(image.getRGB(x, 1)));
            assertEquals("untouched row stays opaque black", hex(0xFF000000),
                    hex(image.getRGB(x, 0)));
        }
    }

    @Test
    public void translucentArgbValuesRoundTripWithinPremultiplyRounding() {
        // Documented caveat: android stores premultiplied pixels, so a translucent colour may
        // come back with its colour channels off by one; alpha itself is exact.
        BufferedImage image = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        int[] row = {0x80123456, 0x40FFFFFF, 0x02FF8000};
        image.setRGB(0, 0, 3, 1, row, 0, 3);
        for (int x = 0; x < 3; x++) {
            int back = image.getRGB(x, 0);
            assertEquals("alpha exact at " + x, row[x] >>> 24, back >>> 24);
            assertTrue("red within 1 at " + x + ": " + hex(row[x]) + " -> " + hex(back),
                    Math.abs(red(row[x]) - red(back)) <= 1);
            assertTrue("green within 1 at " + x, Math.abs(green(row[x]) - green(back)) <= 1);
            assertTrue("blue within 1 at " + x, Math.abs(blue(row[x]) - blue(back)) <= 1);
        }
    }

    @Test
    public void miniIconFillsAndStrokesAnEllipseOnATransparentArgbImage() {
        // ProceduralArt.icon(size): ARGB image, createGraphics, prepare (antialias on), fill a
        // shape, stroke its outline, dispose in finally.
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Color fill = new Color(0x2E, 0x8B, 0x57);
        Color edge = new Color(0, 0, 255);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Ellipse2D.Double disc = new Ellipse2D.Double(8, 8, 48, 48); // centre (32,32), r 24
            g.setColor(fill);
            g.fill(disc);
            g.setStroke(new BasicStroke(2f));
            g.setColor(edge);
            g.draw(disc);
        } finally {
            g.dispose();
        }

        assertPixel(image, 32, 32, 0xFF2E8B57); // centre: the fill colour, opaque
        assertPixel(image, 20, 40, 0xFF2E8B57); // well inside, away from the stroke band
        assertEquals("top-left corner stays fully transparent", 0, image.getRGB(0, 0) >>> 24);
        assertEquals("bottom-right corner stays fully transparent", 0,
                image.getRGB(63, 63) >>> 24);
        assertEquals(0, image.getRGB(63, 0) >>> 24);
        assertEquals(0, image.getRGB(0, 63) >>> 24);
        assertEquals("the corner never got colour either", 0, image.getRGB(0, 0));

        // 12 o'clock on the outline: inside the 2 px stroke band, so the edge colour wins.
        int top = image.getRGB(32, 8);
        assertEquals("outline pixel is opaque", 0xFF, top >>> 24);
        assertTrue("outline pixel is the stroke colour: " + hex(top),
                blue(top) > 200 && red(top) < 60 && green(top) < 90);
        // Just outside the stroke band: transparent (r = 24 + 1 = 25 is the band's outer edge).
        assertEquals("outside the disc", 0, image.getRGB(32, 4) >>> 24);
    }
}
