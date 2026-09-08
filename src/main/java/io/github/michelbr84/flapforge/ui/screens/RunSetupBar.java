package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.defs.WorldPaletteDef;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * The run in one line (M11): a swatch of the world, "World · Difficulty · Mode", a second line
 * saying what that run actually is — the hazards, the seed it replays, today's daily, or what
 * opens a locked mode — and a chevron, because activating the bar opens the panel where those
 * three are chosen.
 *
 * <p>It replaces three picker rows on the screen itself with one line: the choice is read far
 * more often than it is changed. Everything it draws is prepared in {@link #bind}: the four
 * swatch colours and both ellipsised lines, so the draw path allocates nothing.
 */
final class RunSetupBar extends UiNode {

    /** Side of the world swatch. */
    static final int SWATCH = 18;
    /** Left edge of the text column. */
    static final int TEXT_LEFT = 36;
    /** Point size of the first line. */
    static final int LINE1_SIZE = 13;
    /** Point size of the second line. */
    static final int LINE2_SIZE = 10;
    /** Room the chevron takes at the right edge. */
    static final int CHEVRON_ROOM = 26;

    private static final Color WARN = new Color(0xE8, 0x5A, 0x4A);

    private String line1 = "";
    private String line2 = "";
    private String tooltip = "";
    private boolean warn;
    private Color swatchSky;
    private Color swatchPipe;
    private Color swatchAccent;
    private Color swatchEdge;
    private String shown1 = "";
    private String shown2 = "";
    private String source1;
    private String source2;
    private int shownWidth = -1;
    private double shownScale;

    /**
     * Points the bar at a run.
     *
     * @param palette the world's palette, or {@code null} for no swatch
     * @param newLine1 the world, tier and mode in one line
     * @param newLine2 what that run is
     * @param isWarn whether the second line is a refusal (a locked mode)
     * @param tip the hover text
     */
    void bind(WorldPaletteDef palette, String newLine1, String newLine2, boolean isWarn,
            String tip) {
        this.line1 = newLine1 == null ? "" : newLine1;
        this.line2 = newLine2 == null ? "" : newLine2;
        this.warn = isWarn;
        this.tooltip = tip == null ? "" : tip;
        if (palette == null) {
            swatchSky = null;
            swatchPipe = null;
            swatchAccent = null;
            swatchEdge = null;
            return;
        }
        // Cached here rather than in render: the palette changes when the selection does, never
        // per frame.
        swatchSky = new Color(WorldPaletteDef.rgb(palette.skyTop()));
        swatchPipe = new Color(WorldPaletteDef.rgb(palette.pipe()));
        swatchAccent = new Color(WorldPaletteDef.rgb(palette.accent()));
        swatchEdge = new Color(WorldPaletteDef.rgb(palette.letterbox()));
    }

    /**
     * The world, tier and mode line, as drawn.
     *
     * @return the text
     */
    String line1() {
        return line1;
    }

    /**
     * The line under it, as drawn.
     *
     * @return the text
     */
    String line2() {
        return line2;
    }

    /**
     * The hover text.
     *
     * @return the tooltip
     */
    String tooltip() {
        return tooltip;
    }

    /**
     * Current visual state.
     *
     * @return the state derived from the flags
     */
    ButtonState state() {
        return ButtonState.of(isEnabled(), isFocused(), isHovered());
    }

    @Override
    public void render(Graphics2D g) {
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        ProceduralArt.chip(g, bx, by, bw, bh, state());
        if (swatchSky != null) {
            int sx = bx + 10;
            int sy = by + (bh - SWATCH) / 2;
            g.setColor(swatchSky);
            g.fillRoundRect(sx, sy, SWATCH, SWATCH, 5, 5);
            g.setColor(swatchPipe);
            g.fillRect(sx + 3, sy + SWATCH / 2, SWATCH - 6, SWATCH / 2 - 3);
            g.setColor(swatchAccent);
            g.fillOval(sx + SWATCH - 9, sy + 3, 5, 5);
            g.setColor(swatchEdge);
            g.drawRoundRect(sx, sy, SWATCH, SWATCH, 5, 5);
        }
        int room = bw - TEXT_LEFT - CHEVRON_ROOM;
        double scale = Fonts.textScale();
        if (source1 != line1 || source2 != line2 || shownWidth != room || shownScale != scale) {
            // Measured only when a line, the room or the text scale changed.
            g.setFont(Fonts.bold(LINE1_SIZE));
            shown1 = TextPainter.ellipsise(g, line1, Math.max(0, room));
            g.setFont(Fonts.regular(LINE2_SIZE));
            shown2 = TextPainter.ellipsise(g, line2, Math.max(0, room));
            source1 = line1;
            source2 = line2;
            shownWidth = room;
            shownScale = scale;
        }
        g.setFont(Fonts.bold(LINE1_SIZE));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, shown1, bx + (double) TEXT_LEFT, by + 16.0);
        if (!shown2.isEmpty()) {
            g.setFont(Fonts.regular(LINE2_SIZE));
            g.setColor(warn ? WARN : ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, shown2, bx + (double) TEXT_LEFT, by + 30.0);
        }
        ProceduralArt.drawChevron(g, bx + bw - 16.0, by + bh / 2.0, 12,
                isFocused() || isHovered() ? ProceduralArt.TEXT_LIGHT
                        : ProceduralArt.TEXT_MUTED);
    }
}
