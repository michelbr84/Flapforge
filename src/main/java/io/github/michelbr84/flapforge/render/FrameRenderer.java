package io.github.michelbr84.flapforge.render;

import java.awt.Graphics2D;

/**
 * Draws one frame into a graphics context already transformed to logical playfield coordinates
 * (D24). The presenter applies the {@link Viewport} transform and clip before calling
 * {@link #render(Graphics2D, double)}; implementations draw the 420x640 logical playfield.
 */
public interface FrameRenderer {

    /**
     * Renders the current frame.
     *
     * @param g the graphics context in logical coordinates (disposed by the caller)
     * @param alpha interpolation factor in {@code [0, 1)} between the previous and current tick
     */
    void render(Graphics2D g, double alpha);

    /**
     * Colour used to fill the letterbox area outside the logical playfield.
     *
     * @return the colour as {@code 0xRRGGBB}
     */
    default int letterboxRgb() {
        return 0x0e1116;
    }
}
