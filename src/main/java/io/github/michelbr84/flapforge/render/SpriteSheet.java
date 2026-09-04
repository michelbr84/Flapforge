package io.github.michelbr84.flapforge.render;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * A grid of equally sized frames cut out of one {@link Sprite} (D18).
 *
 * <p>The frames are laid out left to right, then top to bottom, and are cut lazily with
 * {@link BufferedImage#getSubimage} (which shares pixels, so a sheet allocates nothing per
 * frame after the first). {@link Animation} turns simulation ticks into a frame index; the
 * renderers use a sheet only when {@link AssetResolver} finds one, and draw
 * {@link ProceduralArt} otherwise.
 */
public final class SpriteSheet {

    private final Sprite sheet;
    private final int frameWidth;
    private final int frameHeight;
    private final int columns;
    private final int frameCount;
    private final BufferedImage[] frames;

    /**
     * Cuts a sheet into frames.
     *
     * @param sheet the whole image
     * @param frameWidth the frame width in pixels (at least 1)
     * @param frameHeight the frame height in pixels (at least 1)
     * @param frameCount how many frames to expose, or {@code 0} for "every whole cell"
     */
    public SpriteSheet(Sprite sheet, int frameWidth, int frameHeight, int frameCount) {
        this.sheet = Objects.requireNonNull(sheet, "sheet");
        if (frameWidth < 1 || frameHeight < 1) {
            throw new IllegalArgumentException("frame size must be positive, got "
                    + frameWidth + "x" + frameHeight);
        }
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.columns = Math.max(1, sheet.width() / frameWidth);
        int rows = Math.max(1, sheet.height() / frameHeight);
        int available = columns * rows;
        this.frameCount = frameCount <= 0 ? available : Math.min(frameCount, available);
        this.frames = new BufferedImage[this.frameCount];
    }

    /**
     * The whole sheet.
     *
     * @return the sprite
     */
    public Sprite sheet() {
        return sheet;
    }

    /**
     * Width of one frame.
     *
     * @return pixels
     */
    public int frameWidth() {
        return frameWidth;
    }

    /**
     * Height of one frame.
     *
     * @return pixels
     */
    public int frameHeight() {
        return frameHeight;
    }

    /**
     * Number of frames.
     *
     * @return the count
     */
    public int frameCount() {
        return frameCount;
    }

    /**
     * One frame, cut on first use and cached.
     *
     * @param index the frame index (wrapped into range)
     * @return the frame image
     */
    public BufferedImage frame(int index) {
        int i = Math.floorMod(index, frameCount);
        BufferedImage cached = frames[i];
        if (cached == null) {
            int x = (i % columns) * frameWidth;
            int y = (i / columns) * frameHeight;
            cached = sheet.image().getSubimage(x, y, frameWidth, frameHeight);
            frames[i] = cached;
        }
        return cached;
    }

    /**
     * Draws one frame centred on a point, scaled to a box.
     *
     * @param g the context in logical coordinates
     * @param index the frame index
     * @param cx the centre x
     * @param cy the centre y
     * @param w the target width
     * @param h the target height
     */
    public void drawFrame(Graphics2D g, int index, double cx, double cy, double w, double h) {
        g.drawImage(frame(index), (int) Math.round(cx - w / 2), (int) Math.round(cy - h / 2),
                (int) Math.round(w), (int) Math.round(h), null);
    }

    @Override
    public String toString() {
        return "SpriteSheet[" + sheet.id() + " " + frameCount + "x" + frameWidth + "x"
                + frameHeight + "]";
    }
}
