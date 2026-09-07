package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Objects;

/**
 * The gold call to action of the home hub (D17): a title over an optional subtitle on the
 * {@link ProceduralArt#ctaButton} plate, with an optional icon before the title and a slow
 * pulsing glow around the plate.
 *
 * <p>The glow is a triangle wave on the tick the screen hands in through {@link #setTicks}, so
 * it is deterministic and the same on every refresh rate; under reduce flashing its peak is
 * capped below the particle system's reduced peak. The title steps its font down until it fits
 * the plate, so a long translation or a large text scale never overflows.
 */
public class CtaButton extends Button {

    /** Title sizes tried in order until the title fits. */
    public static final int[] TITLE_SIZES = {24, 20, 16};
    /** Point size of the subtitle. */
    public static final int SUBTITLE_SIZE = 13;
    /** Size of the icon before the title. */
    public static final int ICON_SIZE = 18;
    /** Gap between the icon and the title. */
    public static final int ICON_GAP = 8;
    /** Ticks of one glow pulse. */
    public static final int GLOW_PERIOD = 90;
    /** Peak alpha of the glow. */
    public static final double GLOW_PEAK = 0.35;
    /** Peak alpha of the glow under reduce flashing. */
    public static final double GLOW_PEAK_REDUCED = Math.min(GLOW_PEAK,
            ParticleSystem.REDUCED_PEAK_ALPHA * 0.5);
    /** Horizontal padding kept clear of the title. */
    public static final int PADDING = 12;
    /** Distance of the title baseline above the centre when a subtitle is shown. */
    public static final int TITLE_LIFT = 4;
    /** Distance of the subtitle baseline below the centre. */
    public static final int SUBTITLE_DROP = 18;

    private static final Color[] GLOW = ProceduralArt.alphaRamp(ProceduralArt.COIN_GOLD, 16);

    private String subtitle = "";
    private IconPainter icon;
    private long ticks;
    private boolean reduceFlashing;
    private Font titleFont;
    private String titleFor;
    private int titleRoom = -1;
    private double titleScale;

    /**
     * Creates a call to action.
     *
     * @param text the title
     * @param onAction the action run on activation, or {@code null}
     */
    public CtaButton(String text, Runnable onAction) {
        super(text, onAction);
    }

    /**
     * The subtitle.
     *
     * @return the text, empty when none is shown
     */
    public String subtitle() {
        return subtitle;
    }

    /**
     * Changes the subtitle.
     *
     * @param subtitle the text; empty hides it and centres the title
     */
    public void setSubtitle(String subtitle) {
        this.subtitle = Objects.requireNonNull(subtitle, "subtitle");
    }

    /**
     * The icon drawn before the title.
     *
     * @return the painter, or {@code null}
     */
    public IconPainter icon() {
        return icon;
    }

    /**
     * Sets the icon drawn before the title.
     *
     * @param icon the painter, or {@code null} for none
     */
    public void setIcon(IconPainter icon) {
        this.icon = icon;
    }

    /**
     * Advances the glow to a tick of the screen's clock.
     *
     * @param ticks the tick count
     */
    public void setTicks(long ticks) {
        this.ticks = ticks;
    }

    /**
     * Caps the glow for players who asked for less flashing.
     *
     * @param reduceFlashing the flag
     */
    public void setReduceFlashing(boolean reduceFlashing) {
        this.reduceFlashing = reduceFlashing;
    }

    /**
     * Whether the glow is capped.
     *
     * @return the flag
     */
    public boolean isReduceFlashing() {
        return reduceFlashing;
    }

    /**
     * The glow alpha at the current tick: a triangle wave from 0 to the peak and back over
     * {@link #GLOW_PERIOD} ticks, 0 while disabled.
     *
     * @return the alpha in {@code [0, 1]}
     */
    public double glowAlpha() {
        if (!isEnabled()) {
            return 0;
        }
        double phase = (ticks % GLOW_PERIOD) / (double) GLOW_PERIOD;
        double wave = phase < 0.5 ? phase * 2 : 2 - phase * 2;
        return (reduceFlashing ? GLOW_PEAK_REDUCED : GLOW_PEAK) * wave;
    }

    @Override
    public void render(Graphics2D g) {
        ButtonState state = state();
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        ProceduralArt.ctaGlow(g, bx, by, bw, bh, GLOW, glowAlpha());
        ProceduralArt.ctaButton(g, bx, by, bw, bh, state);
        Color ink = ProceduralArt.ctaTextColor(state);
        String title = text();
        int room = bw - 2 * PADDING - (icon == null ? 0 : ICON_SIZE + ICON_GAP);
        double scale = Fonts.textScale();
        if (titleFont == null || titleFor != title || titleRoom != room || titleScale != scale) {
            // Measured only when the title, the room or the text scale changed.
            titleFont = Fonts.bold(TITLE_SIZES[TITLE_SIZES.length - 1]);
            for (int size : TITLE_SIZES) {
                Font candidate = Fonts.bold(size);
                if (TextPainter.width(g, candidate, title) <= room) {
                    titleFont = candidate;
                    break;
                }
            }
            titleFor = title;
            titleRoom = room;
            titleScale = scale;
        }
        g.setFont(titleFont);
        double cy = centerY();
        double titleBaseline = subtitle.isEmpty()
                ? TextPainter.centeredBaseline(g, cy) : cy - TITLE_LIFT;
        double titleWidth = TextPainter.width(g, title);
        double total = titleWidth + (icon == null ? 0 : ICON_SIZE + ICON_GAP);
        double left = centerX() - total / 2;
        if (icon != null) {
            double iconCy = subtitle.isEmpty() ? cy : titleBaseline - ICON_SIZE * 0.4;
            icon.paint(g, left + ICON_SIZE / 2.0, iconCy, ICON_SIZE, ink);
            left += ICON_SIZE + ICON_GAP;
        }
        g.setColor(ink);
        TextPainter.draw(g, title, left, titleBaseline);
        if (!subtitle.isEmpty()) {
            g.setFont(Fonts.regular(SUBTITLE_SIZE));
            TextPainter.drawCentered(g, subtitle, centerX(), cy + SUBTITLE_DROP);
        }
    }
}
