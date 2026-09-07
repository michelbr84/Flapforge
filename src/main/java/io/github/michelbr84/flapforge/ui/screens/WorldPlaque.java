package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.defs.WorldPaletteDef;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;

/**
 * The wooden world sign of the home hub (D17, M7, M10): the selected world's number and name
 * with a swatch of its palette and a chevron saying it opens something. Activating it opens the
 * World Select.
 */
public final class WorldPlaque extends UiNode {

    /** Side of the palette swatch. */
    public static final int SWATCH = 14;
    /** Size of the chevron. */
    public static final int CHEVRON = 8;

    private static final int PADDING = 10;

    private String text = "";
    private String shown = "";
    private String shownSource;
    private int shownWidth = -1;
    private double shownScale;
    private Color sky;
    private Color pipe;
    private Color accent;

    /**
     * Creates the plaque.
     *
     * @param onAction the action run on activation, or {@code null}
     */
    WorldPlaque(Runnable onAction) {
        setOnAction(onAction);
    }

    /**
     * Points the plaque at a world.
     *
     * @param newText the sign's text
     * @param palette the world's palette, or {@code null} for no swatch
     */
    void bind(String newText, WorldPaletteDef palette) {
        this.text = newText == null ? "" : newText;
        if (palette == null) {
            sky = null;
            pipe = null;
            accent = null;
        } else {
            sky = new Color(WorldPaletteDef.rgb(palette.skyTop()));
            pipe = new Color(WorldPaletteDef.rgb(palette.pipe()));
            accent = new Color(WorldPaletteDef.rgb(palette.accent()));
        }
    }

    /**
     * The sign's text.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * Current visual state.
     *
     * @return the state derived from the flags
     */
    public ButtonState state() {
        return ButtonState.of(isEnabled(), isFocused(), isHovered());
    }

    @Override
    public void render(Graphics2D g) {
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        ProceduralArt.plaque(g, bx, by, bw, bh, state());
        int left = bx + PADDING;
        if (sky != null) {
            int sy = by + (bh - SWATCH) / 2;
            g.setColor(sky);
            g.fillRoundRect(left, sy, SWATCH, SWATCH, 4, 4);
            g.setColor(pipe);
            g.fillRect(left + 2, sy + SWATCH / 2, SWATCH - 4, SWATCH / 2 - 2);
            g.setColor(accent);
            g.fillOval(left + SWATCH - 6, sy + 2, 4, 4);
            left += SWATCH + 6;
        }
        int right = bx + bw - PADDING - CHEVRON;
        ProceduralArt.drawChevron(g, right + CHEVRON / 2.0, by + bh / 2.0, CHEVRON,
                ProceduralArt.TEXT_LIGHT);
        int textWidth = right - 6 - left;
        g.setFont(Fonts.bold(14));
        double scale = Fonts.textScale();
        if (shownSource != text || shownWidth != textWidth || shownScale != scale) {
            // Measured only when the text, the width or the text scale changed.
            shown = TextPainter.ellipsise(g, text, textWidth);
            shownSource = text;
            shownWidth = textWidth;
            shownScale = scale;
        }
        Shape unclipped = g.getClip();
        g.clipRect(left, by, Math.max(0, textWidth), bh);
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, shown, left + textWidth / 2.0,
                TextPainter.centeredBaseline(g, by + bh / 2.0));
        g.setClip(unclipped);
    }
}
