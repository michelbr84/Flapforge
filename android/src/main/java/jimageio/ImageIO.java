package jimageio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import awt.image.BufferedImage;

import java.io.IOException;
import java.io.InputStream;

/**
 * android.graphics shim for the M10 build-time source transform
 * ({@code javax.imageio.*} -> {@code jimageio.*}).
 *
 * <p>Stand-in for the two {@code javax.imageio.ImageIO} members the game uses
 * (render/AssetManager.java): {@link #read(InputStream)} (:432) decodes a manifest asset —
 * PNG in practice, anything {@link BitmapFactory} knows in general — into an
 * {@link BufferedImage}, and {@link #setUseCache(boolean)} (:476) is AWT's disk-cache switch,
 * which has nothing to switch here.
 *
 * <p>Decoding choices: the stream goes to {@code BitmapFactory.decodeStream} asking for
 * {@code ARGB_8888} with {@code inPremultiplied = false} and {@code inScaled = false}, so the
 * pixels read back are the file's own straight (non-premultiplied) ARGB values with no density
 * scaling. {@link BufferedImage} has no constructor over a bitmap, so the decoded pixels are
 * copied into a fresh {@code TYPE_INT_ARGB} image through its block {@code setRGB} and the
 * bitmap is recycled. That copy is where the one precision loss lives: the image's bitmap is
 * premultiplied (see {@code BufferedImage}), so a translucent pixel can read back with colour
 * channels off by up to {@code 255 / (2 * alpha)} — one unit at alpha 128; opaque and fully
 * transparent pixels, and channels at 0 or 255, are exact. AWT's PNG reader has no such loss.
 *
 * <p>Failure contract, as AssetManager expects (:432-439): a stream {@code BitmapFactory} cannot
 * decode yields {@code null} ("no decoder for this file"), a {@code null} stream is an
 * {@link IllegalArgumentException} (AWT parity, caught there as a {@code RuntimeException}), and
 * the declared {@link IOException} keeps the call site's catch clause valid.
 */
public final class ImageIO {

    private ImageIO() {
    }

    /**
     * Decodes an image (census: AssetManager.java:432).
     *
     * @param input the encoded bytes; read to the end of the image, not closed
     * @return a {@code TYPE_INT_ARGB} image, or {@code null} when nothing could decode the bytes
     * @throws IllegalArgumentException when {@code input} is {@code null}
     * @throws IOException declared for AWT parity; the decoder reports failures as {@code null}
     */
    public static BufferedImage read(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inPremultiplied = false;
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
        if (bitmap == null) {
            return null;
        }
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            return image;
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * AWT's switch between a disk cache and memory for decoder scratch space (census:
     * AssetManager.java:476, always {@code false}). {@link BitmapFactory} decodes in memory
     * whatever the value, so there is nothing to set.
     *
     * @param useCache ignored
     */
    public static void setUseCache(boolean useCache) {
        // Nothing to configure: android decodes in memory, which is what the census asks for.
    }
}
