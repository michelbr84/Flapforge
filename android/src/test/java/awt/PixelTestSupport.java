package awt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import awt.image.BufferedImage;

/**
 * Pixel assertions shared by the shim tests. Every image under test is a fresh
 * {@code TYPE_INT_ARGB} {@link BufferedImage}, so "background" is the fully transparent
 * {@code 0x00000000} and "inked" is any pixel whose alpha is non-zero. Solid interiors are
 * compared exactly; edges of antialiased shapes are never sampled (the tests draw with
 * antialiasing off unless they are testing antialiasing itself).
 */
final class PixelTestSupport {

    static final int SIZE = 64;

    private PixelTestSupport() {
    }

    static BufferedImage argb(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    static BufferedImage argb() {
        return argb(SIZE, SIZE);
    }

    /** A context with antialiasing OFF so solid interiors are exact. */
    static Graphics2D crisp(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        return g;
    }

    static boolean inked(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) != 0;
    }

    static void assertInked(BufferedImage image, int x, int y) {
        assertTrue("expected ink at (" + x + "," + y + ") but found "
                + hex(image.getRGB(x, y)), inked(image, x, y));
    }

    static void assertNotInked(BufferedImage image, int x, int y) {
        assertTrue("expected background at (" + x + "," + y + ") but found "
                + hex(image.getRGB(x, y)), !inked(image, x, y));
    }

    static void assertPixel(BufferedImage image, int x, int y, int expectedArgb) {
        int actual = image.getRGB(x, y);
        if (actual != expectedArgb) {
            fail("pixel (" + x + "," + y + ") expected " + hex(expectedArgb) + " but was "
                    + hex(actual));
        }
    }

    /** Asserts every pixel of the block is exactly {@code expectedArgb}. */
    static void assertBlock(BufferedImage image, int x0, int y0, int x1, int y1,
            int expectedArgb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                assertPixel(image, x, y, expectedArgb);
            }
        }
    }

    /** Asserts no pixel of the block is inked. */
    static void assertBlockClear(BufferedImage image, int x0, int y0, int x1, int y1) {
        assertBlock(image, x0, y0, x1, y1, 0);
    }

    static int countInked(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (inked(image, x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Number of inked pixels whose centre lies within {@code [rMin, rMax]} of (cx, cy). */
    static int countInkedInAnnulus(BufferedImage image, double cx, double cy, double rMin,
            double rMax) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                double dx = x + 0.5 - cx;
                double dy = y + 0.5 - cy;
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r >= rMin && r <= rMax && inked(image, x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    static void assertOpaque(BufferedImage image, int x, int y) {
        assertEquals("alpha at (" + x + "," + y + ")", 0xFF, image.getRGB(x, y) >>> 24);
    }

    static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    static int blue(int argb) {
        return argb & 0xFF;
    }

    static String hex(int argb) {
        return String.format("0x%08X", argb);
    }
}
