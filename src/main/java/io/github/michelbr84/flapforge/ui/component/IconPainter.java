package io.github.michelbr84.flapforge.ui.component;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Draws a procedural icon centred on a point (D17, D18). The hub's buttons take one of these
 * rather than an image, so every icon is a {@code ProceduralArt} helper drawn in the colour the
 * button's state asks for and the Android build carries no bitmap.
 */
@FunctionalInterface
public interface IconPainter {

    /**
     * Draws the icon.
     *
     * @param g the context in logical coordinates
     * @param cx the centre x
     * @param cy the centre y
     * @param size the icon's width and height
     * @param color the colour the icon is drawn in
     */
    void paint(Graphics2D g, double cx, double cy, double size, Color color);
}
