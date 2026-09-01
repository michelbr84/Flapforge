package io.github.michelbr84.flapforge.render;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * One bitmap loaded through the {@link AssetManager} (D18).
 *
 * <p>A sprite is an optional override of procedural art: renderers ask the {@link AssetResolver}
 * for one and draw {@link ProceduralArt} when the answer is empty, which is what the shipped
 * empty {@code assets/manifest.json} always produces. The image is immutable as far as callers
 * are concerned: nothing here draws into it.
 */
public final class Sprite {

    private final String id;
    private final BufferedImage image;

    /**
     * Wraps an image.
     *
     * @param id the manifest id the image was loaded for
     * @param image the image
     */
    public Sprite(String id, BufferedImage image) {
        this.id = Objects.requireNonNull(id, "id");
        this.image = Objects.requireNonNull(image, "image");
    }

    /**
     * The manifest id.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * The backing image.
     *
     * @return the image
     */
    public BufferedImage image() {
        return image;
    }

    /**
     * Width in pixels.
     *
     * @return the width
     */
    public int width() {
        return image.getWidth();
    }

    /**
     * Height in pixels.
     *
     * @return the height
     */
    public int height() {
        return image.getHeight();
    }

    /**
     * Draws the sprite with its top-left corner at a point.
     *
     * @param g the context in logical coordinates
     * @param x the left edge
     * @param y the top edge
     */
    public void draw(Graphics2D g, double x, double y) {
        g.drawImage(image, (int) Math.round(x), (int) Math.round(y), null);
    }

    /**
     * Draws the sprite centred on a point, scaled to a box.
     *
     * @param g the context in logical coordinates
     * @param cx the centre x
     * @param cy the centre y
     * @param w the target width
     * @param h the target height
     */
    public void drawCentered(Graphics2D g, double cx, double cy, double w, double h) {
        int dx = (int) Math.round(cx - w / 2);
        int dy = (int) Math.round(cy - h / 2);
        g.drawImage(image, dx, dy, (int) Math.round(w), (int) Math.round(h), null);
    }

    @Override
    public String toString() {
        return "Sprite[" + id + " " + width() + "x" + height() + "]";
    }
}
