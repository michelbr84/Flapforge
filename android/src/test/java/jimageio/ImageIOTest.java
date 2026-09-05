package jimageio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;

import awt.image.BufferedImage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proofs of the {@link ImageIO} shim: a PNG encoded in-test from a bitmap with
 * distinct opaque, transparent and translucent pixels reads back with the right size and
 * {@code getRGB} values, undecodable bytes yield {@code null} exactly as AssetManager.java:433
 * expects, and a {@code null} stream is refused like AWT does.
 *
 * <p>Precision: the shim's {@code BufferedImage} stores premultiplied pixels, so a translucent
 * pixel's colour channels can drift by one unit at alpha 128 (see ImageIO's javadoc). The
 * fixture therefore uses channels at 0 or 255 — exact through every premultiply round trip —
 * for the strict assertions, and one arbitrary translucent colour with a tolerance of 1.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageIOTest {

    private static final int W = 4;
    private static final int H = 3;
    private static final int[] PIXELS = {
        0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF, // row 0: opaque primaries + white
        0x00000000, 0x80FF0000, 0x8000FF00, 0x40FFFFFF, // row 1: clear, half red/green, 1/4 white
        0xFF808080, 0x80123456, 0xFF000000, 0xFF123456, // row 2: greys, arbitrary translucent
    };

    private static byte[] png() {
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                bitmap.setPixel(x, y, PIXELS[y * W + x]);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        bitmap.recycle();
        return out.toByteArray();
    }

    private static void assertArgbClose(String where, int expected, int actual, int tolerance) {
        assertEquals(where + " alpha", expected >>> 24, actual >>> 24);
        for (int shift = 16; shift >= 0; shift -= 8) {
            int e = (expected >> shift) & 0xFF;
            int a = (actual >> shift) & 0xFF;
            assertTrue(where + ": expected " + Integer.toHexString(expected) + " got "
                    + Integer.toHexString(actual), Math.abs(e - a) <= tolerance);
        }
    }

    @Test
    public void readsAPngBackWithItsSizeAndPixels() throws Exception {
        byte[] png = png();
        assertTrue(png.length > 8);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image);
        assertEquals(W, image.getWidth());
        assertEquals(H, image.getHeight());
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int expected = PIXELS[y * W + x];
                int actual = image.getRGB(x, y);
                String where = "(" + x + "," + y + ")";
                if (expected == 0x80123456) {
                    assertArgbClose(where, expected, actual, 1);
                } else {
                    assertEquals(where + ": expected " + Integer.toHexString(expected) + " got "
                            + Integer.toHexString(actual), expected, actual);
                }
            }
        }
    }

    @Test
    public void decodedImageIsAnArgbImageThatDrawsAndSubimages() throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png()));
        assertNotNull(image);
        BufferedImage frame = image.getSubimage(1, 0, 2, 2); // SpriteSheet.java:96 style
        assertEquals(0xFF00FF00, frame.getRGB(0, 0));
        assertEquals(0x8000FF00, frame.getRGB(1, 1));
        assertNotNull(image.createGraphics());
    }

    @Test
    public void undecodableBytesReadAsNull() throws Exception {
        byte[] notAnImage = "this is a manifest entry pointing at a text file"
                .getBytes(StandardCharsets.US_ASCII);
        assertNull(ImageIO.read(new ByteArrayInputStream(notAnImage)));
        assertNull(ImageIO.read(new ByteArrayInputStream(new byte[0])));
        byte[] truncated = new byte[24];
        System.arraycopy(png(), 0, truncated, 0, truncated.length);
        assertNull(ImageIO.read(new ByteArrayInputStream(truncated)));
    }

    @Test
    public void nullStreamIsRefusedLikeAwt() throws Exception {
        try {
            ImageIO.read(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("input == null!", expected.getMessage());
        }
    }

    @Test
    public void useCacheSwitchIsAccepted() throws Exception {
        ImageIO.setUseCache(false); // AssetManager.java:476
        ImageIO.setUseCache(true);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(png())));
    }
}
