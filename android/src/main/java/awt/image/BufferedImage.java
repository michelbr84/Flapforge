package awt.image;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import awt.Graphics2D;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.image.BufferedImage}, backed by a mutable
 * {@code Bitmap.Config.ARGB_8888} bitmap. The pixel API works on non-premultiplied ARGB ints
 * exactly like AWT's default {@code TYPE_INT_ARGB} colour model (android's
 * {@code getPixel/setPixels} translate premultiplication at the API boundary).
 *
 * <p>Census surface: constructors {@code (w, h, TYPE_INT_ARGB)} (render/ProceduralArt.java:688,
 * render/DarknessOverlay.java:54, app/BootSequence.java:88) and {@code (w, h, TYPE_INT_RGB)}
 * (app/NullPresenter.java:63); {@code createGraphics()} — render/ProceduralArt.java:689 (the
 * icon renderer), app/NullPresenter.java:70, app/BootSequence.java:89 (the font warm-up probe),
 * render/AssetManager.java:462 (inside {@code compatible()}, which never executes because
 * {@code GraphicsEnvironment.isHeadless()} is {@code true}); {@code setRGB(0, y, w, 1, row,
 * offset, scansize)} — the array-row variant, render/DarknessOverlay.java:76; {@code getRGB(x,
 * y)} — render/DarknessOverlay.java:114; {@code getSubimage(x, y, w, h)} —
 * render/SpriteSheet.java:96 (AWT parity: the subimage SHARES the parent pixels, no copy);
 * {@code getWidth()}/{@code getHeight()} (AssetManager.java:460, NullPresenter.java:68-69,
 * SpriteSheet). Nothing in the census touches {@code getRaster}/{@code getType}/{@code flush},
 * so those do not exist here.
 *
 * <p>Notes: {@code TYPE_INT_RGB} allocates the same ARGB_8888 storage but, like AWT, starts
 * opaque black, ignores the alpha byte of every value written through {@link #setRGB} (the RGB
 * bytes are stored opaque) and reports every pixel opaque through {@link #getRGB(int, int)} (the
 * census only ever draws opaque content into RGB images — the NullPresenter letterbox).
 * Precision caveat for {@code TYPE_INT_ARGB}: android stores premultiplied pixels, so a
 * translucent colour written with {@link #setRGB} may read back with colour channels off by one
 * after the premultiply/unpremultiply round trip; opaque values and alpha-only values (RGB 0,
 * the DarknessOverlay mask) round-trip exactly. AWT's non-premultiplied
 * {@code TYPE_INT_ARGB} raster has no such loss.
 *
 * <p>Subimage decision (semantics 10): {@link #getSubimage(int, int, int, int)} SHARES the parent
 * bitmap exactly like AWT — the view is {@code (bitmap, offsetX, offsetY, width, height)} and no
 * pixels are copied ({@code Bitmap.createBitmap(src, x, y, w, h)} would copy). Every consumer of
 * the backing bitmap must honour the offset: {@link #getRGB}/{@link #setRGB} shift their
 * coordinates, {@link #createGraphics()} clips the canvas to the sub-rectangle and translates it
 * so the view's (0,0) is the sub-rectangle's corner, and {@code Graphics2D.drawImage} reads the
 * source region through {@link #offsetX()}/{@link #offsetY()}. Writes through a view are visible
 * in the parent and vice versa (AWT parity; the game only reads frames, SpriteSheet.java:96).
 */
public class BufferedImage {

    /** AWT parity: no alpha channel (census: NullPresenter letterbox image). */
    public static final int TYPE_INT_RGB = 1;

    /** AWT parity: 8-bit ARGB with alpha (census: masks, icons, probes). */
    public static final int TYPE_INT_ARGB = 2;

    private final Bitmap bitmap;
    private final boolean opaque;
    private final int offsetX;
    private final int offsetY;
    private final int width;
    private final int height;

    /**
     * Creates an image of the given size and type.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param imageType {@link #TYPE_INT_ARGB} or {@link #TYPE_INT_RGB}
     */
    public BufferedImage(int width, int height, int imageType) {
        if (imageType != TYPE_INT_ARGB && imageType != TYPE_INT_RGB) {
            throw new UnsupportedOperationException(
                    "Flapforge shim: BufferedImage type " + imageType
                            + " is not part of the census surface");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Flapforge shim: BufferedImage size must be positive");
        }
        this.bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        this.opaque = imageType == TYPE_INT_RGB;
        if (opaque) {
            // AWT parity: a fresh TYPE_INT_RGB image reads 0xFF000000 everywhere.
            bitmap.eraseColor(0xFF000000);
        }
        this.offsetX = 0;
        this.offsetY = 0;
        this.width = width;
        this.height = height;
    }

    private BufferedImage(Bitmap shared, boolean opaque, int offsetX, int offsetY, int width,
            int height) {
        this.bitmap = shared;
        this.opaque = opaque;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;
    }

    /**
     * A {@link Graphics2D} context that draws into this image (census: 4 sites).
     *
     * @return a fresh context over this image's pixels
     */
    public Graphics2D createGraphics() {
        Canvas canvas = new Canvas(bitmap);
        if (offsetX != 0 || offsetY != 0 || width != bitmap.getWidth()
                || height != bitmap.getHeight()) {
            // Subimage view: fence the drawing to the sub-rectangle (in bitmap space, before
            // the translation), then shift user (0,0) onto the sub-rectangle's corner. Both sit
            // in the persistent canvas state under every Graphics2D save/restore.
            canvas.clipRect(offsetX, offsetY, offsetX + width, offsetY + height);
            canvas.translate(offsetX, offsetY);
        }
        return new Graphics2D(canvas);
    }

    /**
     * The ARGB pixel at a coordinate (census: DarknessOverlay.java:114).
     *
     * @param x the x within this image
     * @param y the y within this image
     * @return the non-premultiplied ARGB value
     */
    public int getRGB(int x, int y) {
        int argb = bitmap.getPixel(offsetX + x, offsetY + y);
        return opaque ? (argb | 0xFF000000) : argb;
    }

    /**
     * Sets a rectangular block of pixels from an array of ARGB values, row-major with a
     * scansize stride (census: the row variant DarknessOverlay.java:76). AWT semantics: no
     * colour-model conversion happens beyond the default ARGB interpretation; on a
     * {@code TYPE_INT_RGB} image the alpha byte is dropped (the pixel is stored opaque), exactly
     * as AWT's alpha-less {@code DirectColorModel} does.
     *
     * @param startX the start x within this image
     * @param startY the start y within this image
     * @param w the block width
     * @param h the block height
     * @param rgbArray the source values
     * @param offset the index of the first value
     * @param scansize the distance between consecutive rows in the array
     */
    public void setRGB(int startX, int startY, int w, int h, int[] rgbArray, int offset,
            int scansize) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int[] block = new int[w * h];
        for (int row = 0; row < h; row++) {
            System.arraycopy(rgbArray, offset + row * scansize, block, row * w, w);
        }
        if (opaque) {
            // AWT parity: an RGB image has no alpha channel, so the source alpha is ignored.
            // Without this a 0x00RRGGBB value would premultiply to nothing in the bitmap.
            for (int i = 0; i < block.length; i++) {
                block[i] |= 0xFF000000;
            }
        }
        bitmap.setPixels(block, 0, w, offsetX + startX, offsetY + startY, w, h);
    }

    /**
     * A sub-rectangle view that shares this image's pixels (AWT parity; census:
     * SpriteSheet.java:96 — "a sheet allocates nothing per frame").
     *
     * @param x the sub-rectangle x
     * @param y the sub-rectangle y
     * @param w the sub-rectangle width
     * @param h the sub-rectangle height
     * @return the shared view
     */
    public BufferedImage getSubimage(int x, int y, int w, int h) {
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > width || y + h > height) {
            throw new IllegalArgumentException(
                    "Flapforge shim: getSubimage rectangle outside the image");
        }
        return new BufferedImage(bitmap, opaque, offsetX + x, offsetY + y, w, h);
    }

    /** @return the width in pixels (subimage views report their own width) */
    public int getWidth() {
        return width;
    }

    /** @return the height in pixels (subimage views report their own height) */
    public int getHeight() {
        return height;
    }

    /**
     * Shim infrastructure (public: Graphics2D lives in {@code awt}): the backing bitmap. For a
     * subimage view this is the PARENT bitmap; pair it with {@link #offsetX()}/{@link #offsetY()}.
     *
     * @return the shared backing bitmap
     */
    public Bitmap bitmap() {
        return bitmap;
    }

    /** Shim infrastructure: the x of this view's top-left corner inside {@link #bitmap()}. */
    public int offsetX() {
        return offsetX;
    }

    /** Shim infrastructure: the y of this view's top-left corner inside {@link #bitmap()}. */
    public int offsetY() {
        return offsetY;
    }
}
