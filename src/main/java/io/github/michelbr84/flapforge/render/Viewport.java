package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the fixed 420x640 logical playfield onto a window of arbitrary size (D3).
 *
 * <p>The scale is {@code min(w / 420, h / 640)}, optionally snapped down to an integer when
 * integer scaling is enabled (and the scale is at least 1). The playfield is centred and the
 * remaining area is letterboxed. The viewport is owned by the game loop: it changes only when the
 * loop drains a {@code Resized} event (E30.a), so input mapping and rendering always agree.
 *
 * <p>Any HiDPI transform installed by the JDK on the graphics context composes underneath the
 * transform applied by {@link #apply(Graphics2D)}; this class works purely in window (device
 * independent) pixels.
 */
public final class Viewport {

    private int windowWidth;
    private int windowHeight;
    private boolean integerScaling;
    private double scale;
    private double offsetX;
    private double offsetY;

    /**
     * Creates a viewport for the given window size.
     *
     * @param windowWidth the window width in pixels
     * @param windowHeight the window height in pixels
     * @param integerScaling whether to snap the scale down to an integer
     */
    public Viewport(int windowWidth, int windowHeight, boolean integerScaling) {
        this.integerScaling = integerScaling;
        resize(windowWidth, windowHeight);
    }

    /**
     * Updates the window size and recomputes scale and offsets.
     *
     * @param width the new width in pixels
     * @param height the new height in pixels
     */
    public void resize(int width, int height) {
        this.windowWidth = Math.max(1, width);
        this.windowHeight = Math.max(1, height);
        recompute();
    }

    /**
     * Enables or disables integer scale snapping.
     *
     * @param integerScaling {@code true} to snap
     */
    public void setIntegerScaling(boolean integerScaling) {
        this.integerScaling = integerScaling;
        recompute();
    }

    private void recompute() {
        double s = Math.min(windowWidth / (double) Playfield.WIDTH,
                windowHeight / (double) Playfield.HEIGHT);
        if (integerScaling && s >= 1) {
            s = Math.floor(s);
        }
        scale = s;
        offsetX = Math.floor((windowWidth - Playfield.WIDTH * s) / 2);
        offsetY = Math.floor((windowHeight - Playfield.HEIGHT * s) / 2);
    }

    /**
     * Tells whether integer scale snapping is enabled.
     *
     * @return {@code true} when enabled
     */
    public boolean isIntegerScaling() {
        return integerScaling;
    }

    /**
     * Current window width.
     *
     * @return pixels
     */
    public int windowWidth() {
        return windowWidth;
    }

    /**
     * Current window height.
     *
     * @return pixels
     */
    public int windowHeight() {
        return windowHeight;
    }

    /**
     * Scale from logical to window pixels.
     *
     * @return the scale factor
     */
    public double scale() {
        return scale;
    }

    /**
     * Horizontal letterbox offset of the playfield in window pixels.
     *
     * @return pixels
     */
    public double offsetX() {
        return offsetX;
    }

    /**
     * Vertical letterbox offset of the playfield in window pixels.
     *
     * @return pixels
     */
    public double offsetY() {
        return offsetY;
    }

    /**
     * Width of the scaled playfield in window pixels.
     *
     * @return pixels
     */
    public double scaledWidth() {
        return Playfield.WIDTH * scale;
    }

    /**
     * Height of the scaled playfield in window pixels.
     *
     * @return pixels
     */
    public double scaledHeight() {
        return Playfield.HEIGHT * scale;
    }

    /**
     * Converts window pixels to logical coordinates.
     *
     * @param windowX the window x
     * @param windowY the window y
     * @return the logical point (may lie outside the playfield)
     */
    public Vec2 toLogical(double windowX, double windowY) {
        return new Vec2((windowX - offsetX) / scale, (windowY - offsetY) / scale);
    }

    /**
     * Converts logical coordinates to window pixels.
     *
     * @param logicalX the logical x
     * @param logicalY the logical y
     * @return the window point
     */
    public Vec2 toWindow(double logicalX, double logicalY) {
        return new Vec2(offsetX + logicalX * scale, offsetY + logicalY * scale);
    }

    /**
     * Tells whether a logical point lies inside the playfield.
     *
     * @param logicalX the logical x
     * @param logicalY the logical y
     * @return {@code true} when inside {@code [0, 420) x [0, 640)}
     */
    public boolean containsLogical(double logicalX, double logicalY) {
        return logicalX >= 0 && logicalX < Playfield.WIDTH && logicalY >= 0
                && logicalY < Playfield.HEIGHT;
    }

    /**
     * Letterbox bars in window pixels (at most two: left/right or top/bottom).
     *
     * @return the bars, empty when the window matches the playfield aspect
     */
    public List<Aabb> letterboxBars() {
        List<Aabb> bars = new ArrayList<>(2);
        double sw = scaledWidth();
        double sh = scaledHeight();
        if (offsetX > 0) {
            bars.add(new Aabb(0, 0, offsetX, windowHeight));
        }
        if (offsetX + sw < windowWidth) {
            bars.add(new Aabb(offsetX + sw, 0, windowWidth - offsetX - sw, windowHeight));
        }
        if (offsetY > 0) {
            bars.add(new Aabb(0, 0, windowWidth, offsetY));
        }
        if (offsetY + sh < windowHeight) {
            bars.add(new Aabb(0, offsetY + sh, windowWidth, windowHeight - offsetY - sh));
        }
        return bars;
    }

    /**
     * Applies translate, scale and clip so subsequent drawing happens in logical coordinates.
     *
     * @param g the graphics context in window coordinates
     */
    public void apply(Graphics2D g) {
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);
        g.clipRect(0, 0, Playfield.WIDTH, Playfield.HEIGHT);
    }

    @Override
    public String toString() {
        return "Viewport{" + windowWidth + "x" + windowHeight + ", scale=" + scale + ", offset=("
                + offsetX + "," + offsetY + "), integer=" + integerScaling + '}';
    }
}
