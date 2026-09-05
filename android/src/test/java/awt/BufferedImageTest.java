package awt;

import static awt.PixelTestSupport.argb;
import static awt.PixelTestSupport.assertBlock;
import static awt.PixelTestSupport.assertBlockClear;
import static awt.PixelTestSupport.assertPixel;
import static awt.PixelTestSupport.countInked;
import static awt.PixelTestSupport.crisp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import awt.image.BufferedImage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proofs for the {@link BufferedImage} shim (semantics 10): ARGB pixel access, the
 * array {@code setRGB} variant, the shared-raster {@code getSubimage} view, and the three
 * {@code Graphics2D.drawImage} census variants including a subimage source.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BufferedImageTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int WHITE = 0xFFFFFFFF;

    @Test
    public void argbImageStartsTransparentAndRgbImageStartsOpaqueBlack() {
        BufferedImage argb = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        assertEquals(0x00000000, argb.getRGB(0, 0));
        assertEquals(4, argb.getWidth());
        assertEquals(4, argb.getHeight());

        BufferedImage rgb = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        assertEquals(0xFF000000, rgb.getRGB(0, 0));
        assertEquals(0xFF000000, rgb.getRGB(3, 2));
        assertEquals(4, rgb.getWidth());
        assertEquals(3, rgb.getHeight());
    }

    @Test
    public void rgbImageReportsEveryPixelOpaque() {
        // NullPresenter fills a TYPE_INT_RGB image with the letterbox colour.
        BufferedImage rgb = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = crisp(rgb);
        g.setColor(new Color(0x203040));
        g.fillRect(0, 0, 8, 8);
        assertBlock(rgb, 0, 0, 8, 8, 0xFF203040);
    }

    @Test
    public void unsupportedTypeAndBadSizeAreRejected() {
        try {
            new BufferedImage(4, 4, 5);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("census"));
        }
        try {
            new BufferedImage(0, 4, BufferedImage.TYPE_INT_ARGB);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // AWT rejects a zero-sized image too.
        }
    }

    @Test
    public void setRgbRowVariantHonoursOffsetAndScansize() {
        // DarknessOverlay.java:76 writes one row at a time: setRGB(0, y, w, 1, row, 0, w).
        BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_ARGB);
        int[] row = {0x11000000, 0x22000000, 0x33000000, 0x44000000, 0x55000000, 0x66000000};
        image.setRGB(0, 2, 6, 1, row, 0, 6);
        for (int x = 0; x < 6; x++) {
            assertEquals(row[x], image.getRGB(x, 2));
            assertEquals(0, image.getRGB(x, 1));
            assertEquals(0, image.getRGB(x, 3));
        }

        // A 3x2 block read from a wider array with an offset and a stride.
        int[] data = new int[2 * 8];
        for (int i = 0; i < data.length; i++) {
            data[i] = 0xFF000000 | i;
        }
        BufferedImage block = new BufferedImage(6, 4, BufferedImage.TYPE_INT_ARGB);
        block.setRGB(1, 1, 3, 2, data, 2, 8);
        assertEquals(data[2], block.getRGB(1, 1));
        assertEquals(data[3], block.getRGB(2, 1));
        assertEquals(data[4], block.getRGB(3, 1));
        assertEquals(data[10], block.getRGB(1, 2));
        assertEquals(data[12], block.getRGB(3, 2));
        assertEquals(0, block.getRGB(0, 1));
        assertEquals(0, block.getRGB(4, 1));
        assertEquals(0, block.getRGB(1, 3));
    }

    @Test
    public void getRgbRoundTripsAlphaOnlyMaskValues() {
        // The darkness mask stores (a << 24) and reads back (getRGB >>> 24).
        BufferedImage image = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 3, 1, new int[] {0x00000000, 0x80000000, 0xFF000000}, 0, 3);
        assertEquals(0x00, image.getRGB(0, 0) >>> 24);
        assertEquals(0x80, image.getRGB(1, 0) >>> 24);
        assertEquals(0xFF, image.getRGB(2, 0) >>> 24);
    }

    @Test
    public void subimageViewSharesTheParentRaster() {
        BufferedImage sheet = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = crisp(sheet);
        g.setColor(new Color(255, 0, 0));
        g.fillRect(0, 0, 4, 4);
        g.setColor(new Color(0, 0, 255));
        g.fillRect(4, 0, 4, 4);

        BufferedImage frame = sheet.getSubimage(4, 0, 4, 4);
        assertEquals(4, frame.getWidth());
        assertEquals(4, frame.getHeight());
        assertEquals(BLUE, frame.getRGB(0, 0));
        assertEquals(BLUE, frame.getRGB(3, 3));
        assertSame("shared backing bitmap", sheet.bitmap(), frame.bitmap());
        assertEquals(4, frame.offsetX());
        assertEquals(0, frame.offsetY());

        // A write through the parent is visible in the view ...
        sheet.setRGB(5, 1, 1, 1, new int[] {GREEN}, 0, 1);
        assertEquals(GREEN, frame.getRGB(1, 1));
        // ... and a write through the view is visible in the parent.
        frame.setRGB(2, 2, 1, 1, new int[] {WHITE}, 0, 1);
        assertEquals(WHITE, sheet.getRGB(6, 2));

        // Nested views compose their offsets.
        BufferedImage corner = frame.getSubimage(2, 2, 2, 2);
        assertEquals(WHITE, corner.getRGB(0, 0));
        assertEquals(6, corner.offsetX());
        assertEquals(2, corner.offsetY());
    }

    @Test
    public void subimageRejectsRectanglesOutsideTheImage() {
        BufferedImage sheet = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        try {
            sheet.getSubimage(6, 0, 4, 4);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // AWT throws RasterFormatException, a RuntimeException as well.
        }
    }

    @Test
    public void subimageGraphicsDrawsInsideTheSubRectangleOnly() {
        BufferedImage sheet = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        BufferedImage view = sheet.getSubimage(4, 2, 4, 4);
        Graphics2D g = crisp(view);
        g.setColor(new Color(255, 0, 0));
        g.fillRect(-10, -10, 100, 100); // far larger than the view

        assertBlock(sheet, 4, 2, 8, 6, RED); // exactly the sub-rectangle
        assertEquals(16, countInked(sheet));
        assertBlockClear(sheet, 0, 0, 4, 8);
        assertBlockClear(sheet, 4, 0, 8, 2);
        assertBlockClear(sheet, 4, 6, 8, 8);

        // The view's own origin is the sub-rectangle corner.
        g.setColor(new Color(0, 0, 255));
        g.fillRect(0, 0, 1, 1);
        assertPixel(sheet, 4, 2, BLUE);
        assertPixel(view, 0, 0, BLUE);
    }

    @Test
    public void drawImageAtNaturalSizeCopiesPixels() {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 2, 2, new int[] {RED, GREEN, BLUE, WHITE}, 0, 2);

        BufferedImage target = argb(8, 8);
        Graphics2D g = crisp(target);
        g.drawImage(source, 3, 4, null);

        assertPixel(target, 3, 4, RED);
        assertPixel(target, 4, 4, GREEN);
        assertPixel(target, 3, 5, BLUE);
        assertPixel(target, 4, 5, WHITE);
        assertEquals(4, countInked(target));
    }

    @Test
    public void drawImageScaledStretchesToTheDestination() {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 2, 2, new int[] {RED, GREEN, BLUE, WHITE}, 0, 2);

        BufferedImage target = argb(8, 8);
        Graphics2D g = crisp(target);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source, 2, 2, 4, 4, null);

        assertBlock(target, 2, 2, 4, 4, RED);
        assertBlock(target, 4, 2, 6, 4, GREEN);
        assertBlock(target, 2, 4, 4, 6, BLUE);
        assertBlock(target, 4, 4, 6, 6, WHITE);
        assertEquals(16, countInked(target));
    }

    @Test
    public void drawImageOfASubimageReadsTheSubRectangleNotTheSheetOrigin() {
        // SpriteSheet.frame(i) is a getSubimage view; drawFrame draws it scaled (:113).
        BufferedImage sheet = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = crisp(sheet);
        sg.setColor(new Color(255, 0, 0));
        sg.fillRect(0, 0, 4, 4); // frame 0: red
        sg.setColor(new Color(0, 0, 255));
        sg.fillRect(4, 0, 4, 4); // frame 1: blue
        BufferedImage frame1 = sheet.getSubimage(4, 0, 4, 4);

        BufferedImage target = argb(8, 8);
        Graphics2D g = crisp(target);
        g.drawImage(frame1, 0, 0, null);
        assertBlock(target, 0, 0, 4, 4, BLUE);
        assertEquals(16, countInked(target));

        BufferedImage scaled = argb(8, 8);
        g = crisp(scaled);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frame1, 0, 0, 8, 8, null);
        assertBlock(scaled, 0, 0, 8, 8, BLUE);
    }

    @Test
    public void drawImageSourceRegionBlitsTheRequestedSlice() {
        // DarknessOverlay.java:128: drawImage(mask, 0, 0, W, H, 0, sy, W, sy + H, null).
        BufferedImage mask = new BufferedImage(4, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = crisp(mask);
        mg.setColor(new Color(255, 0, 0));
        mg.fillRect(0, 0, 4, 4); // top half red
        mg.setColor(new Color(0, 0, 255));
        mg.fillRect(0, 4, 4, 4); // bottom half blue

        BufferedImage target = argb(4, 4);
        Graphics2D g = crisp(target);
        g.drawImage(mask, 0, 0, 4, 4, 0, 4, 4, 8, null);
        assertBlock(target, 0, 0, 4, 4, BLUE);

        BufferedImage top = argb(4, 4);
        g = crisp(top);
        g.drawImage(mask, 0, 0, 4, 4, 0, 0, 4, 4, null);
        assertBlock(top, 0, 0, 4, 4, RED);
    }

    @Test
    public void drawImageSourceRegionOfASubimageIsRelativeToTheView() {
        BufferedImage sheet = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = crisp(sheet);
        sg.setColor(new Color(0, 255, 0));
        sg.fillRect(4, 4, 4, 4); // only the bottom-right quadrant is green
        BufferedImage view = sheet.getSubimage(4, 4, 4, 4);

        BufferedImage target = argb(2, 2);
        Graphics2D g = crisp(target);
        g.drawImage(view, 0, 0, 2, 2, 0, 0, 2, 2, null);
        assertBlock(target, 0, 0, 2, 2, GREEN);
    }

    @Test
    public void drawImageFlippedRectanglesAreOutsideTheCensus() {
        BufferedImage source = argb(4, 4);
        Graphics2D g = crisp(argb(4, 4));
        try {
            g.drawImage(source, 4, 0, 0, 4, 0, 0, 4, 4, null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("census"));
        }
    }

    @Test
    public void drawImageCompositesTranslucentPixelsOverTheDestination() {
        BufferedImage veil = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        veil.setRGB(0, 0, 2, 2, new int[] {0x80000000, 0x80000000, 0x80000000, 0x80000000},
                0, 2);

        BufferedImage target = argb(2, 2);
        Graphics2D g = crisp(target);
        g.setColor(new Color(255, 255, 255));
        g.fillRect(0, 0, 2, 2);
        g.drawImage(veil, 0, 0, null);

        int pixel = target.getRGB(1, 1);
        assertEquals(0xFF, pixel >>> 24);
        int grey = (pixel >> 16) & 0xFF;
        assertTrue("50% black over white gives mid grey, got " + PixelTestSupport.hex(pixel),
                Math.abs(grey - 127) <= 2);
    }

    @Test
    public void drawImageHonoursTheTransformAndClip() {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 2, 2, new int[] {RED, RED, RED, RED}, 0, 2);

        BufferedImage target = argb(8, 8);
        Graphics2D g = crisp(target);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setClip(new awt.geom.Rectangle2D.Double(0, 0, 8, 4)); // top half only
        g.translate(2, 2);
        g.scale(2, 2);
        g.drawImage(source, 0, 0, null); // device: [2,6) x [2,6), clipped to y < 4

        assertBlock(target, 2, 2, 6, 4, RED);
        assertBlockClear(target, 2, 4, 6, 6);
        assertBlockClear(target, 0, 0, 2, 8);
        assertEquals(8, countInked(target));
    }

    @Test
    public void nullImageIsIgnoredLikeAwt() {
        BufferedImage target = argb(4, 4);
        Graphics2D g = crisp(target);
        g.drawImage(null, 0, 0, null);
        g.drawImage(null, 0, 0, 4, 4, null);
        g.drawImage(null, 0, 0, 4, 4, 0, 0, 4, 4, null);
        assertEquals(0, countInked(target));
    }
}
