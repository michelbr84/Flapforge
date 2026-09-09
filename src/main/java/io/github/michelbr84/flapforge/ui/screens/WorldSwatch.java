package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.defs.WorldPaletteDef;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Objects;

/**
 * The thumbnail of a world (M10, shared in M12): its sky over its pipe colour, with the accent as
 * a sun and the letterbox as the edge.
 *
 * <p>Four colours are enough to tell the five worlds apart at 34 px, which is why the world select
 * and the shop both draw the same square rather than a scaled backdrop: a real
 * {@link io.github.michelbr84.flapforge.render.ProceduralArt#fillBackground fillBackground} needs
 * a band of screen to read as a place, and neither screen has one to spare.
 *
 * <p>The four colours are unpacked once, in the constructor, so painting a card allocates
 * nothing.
 */
public final class WorldSwatch implements CardGrid.ArtPainter {

    /** Corner radius of the square. */
    public static final int RADIUS = 6;

    private final Color sky;
    private final Color pipe;
    private final Color accent;
    private final Color edge;

    /**
     * Creates the thumbnail of one world.
     *
     * @param palette the world's palette
     */
    public WorldSwatch(WorldPaletteDef palette) {
        Objects.requireNonNull(palette, "palette");
        sky = new Color(WorldPaletteDef.rgb(palette.skyTop()));
        pipe = new Color(WorldPaletteDef.rgb(palette.pipe()));
        accent = new Color(WorldPaletteDef.rgb(palette.accent()));
        edge = new Color(WorldPaletteDef.rgb(palette.letterbox()));
    }

    @Override
    public void paint(Graphics2D g, CardGrid.Card card, double cx, double cy, double size) {
        int s = (int) Math.round(size);
        int sx = (int) Math.round(cx - size / 2);
        int sy = (int) Math.round(cy - size / 2);
        g.setColor(sky);
        g.fillRoundRect(sx, sy, s, s, RADIUS, RADIUS);
        g.setColor(pipe);
        g.fillRect(sx + 3, sy + s / 2, s - 6, s / 2 - 3);
        g.setColor(accent);
        g.fillOval(sx + s - s / 3 - 3, sy + 4, s / 3, s / 3);
        g.setColor(edge);
        g.drawRoundRect(sx, sy, s - 1, s - 1, RADIUS, RADIUS);
    }
}
