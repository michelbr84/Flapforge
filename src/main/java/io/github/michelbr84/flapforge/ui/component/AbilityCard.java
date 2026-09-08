package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * One ability card (M11): a glyph, what the slot is, the ability in it and the level it is owned
 * at, on a {@link ProceduralArt#chip} plate.
 *
 * <p>The {@link Tone} decides the border and the ink: a fixed slot (a passive the bird grants) is
 * outlined in green and an ability the run's rules would strip in red, which is what "greyed out"
 * means for a chip the player can only step through. Every measured string is cached on the text,
 * the room it has and the text scale, so the draw path allocates nothing.
 */
public class AbilityCard extends UiNode {

    /** Point size of the slot label. */
    public static final int ROLE_SIZE = 9;
    /** Point size of the ability name. */
    public static final int NAME_SIZE = 11;
    /** Point size of the level. */
    public static final int LEVEL_SIZE = 9;
    /** Size of the glyph. */
    public static final int ICON_SIZE = 16;
    /** Horizontal padding. */
    public static final int PADDING = 6;
    /** Left edge of the text column. */
    public static final int TEXT_LEFT = 28;

    /** Border of a slot nothing can change. */
    private static final Color FIXED = new Color(0x6F, 0xD1, 0xA8);
    /** Border and ink of a slot the run's rules would strip. */
    private static final Color BLOCKED = new Color(0xE8, 0x5A, 0x4A);
    private static final Stroke BORDER = new java.awt.BasicStroke(2f);

    /** What a card's colours say about its slot. */
    public enum Tone {
        /** An ability the run will fly with. */
        NORMAL,
        /** An empty slot. */
        EMPTY,
        /** A passive the bird grants and nothing can unequip. */
        FIXED,
        /** An ability the run's rules would strip. */
        BLOCKED
    }

    private String label = "";
    private String value = "";
    private String levelText = "";
    private IconPainter icon;
    private Tone tone = Tone.EMPTY;
    private String shownValue = "";
    private String shownSource;
    private int shownWidth = -1;
    private double shownScale;
    private String shownLabel = "";
    private String shownLabelSource;
    private int shownLabelWidth = -1;
    private double shownLabelScale;

    /**
     * Points the card at a slot.
     *
     * @param newLabel the translated slot label
     * @param newValue the translated ability name, or the word for an empty slot
     * @param newLevelText the level line, empty when the slot holds nothing
     * @param newIcon the glyph, or {@code null} for the empty-slot ring
     * @param newTone what the card's colours say
     */
    public void bind(String newLabel, String newValue, String newLevelText, IconPainter newIcon,
            Tone newTone) {
        this.label = newLabel == null ? "" : newLabel;
        this.value = newValue == null ? "" : newValue;
        this.levelText = newLevelText == null ? "" : newLevelText;
        this.icon = newIcon;
        this.tone = newTone == null ? Tone.EMPTY : newTone;
    }

    /**
     * The slot label, as drawn.
     *
     * @return the text
     */
    public String label() {
        return label;
    }

    /**
     * The ability name, as drawn.
     *
     * @return the text, the word for "empty" when the slot holds nothing
     */
    public String value() {
        return value;
    }

    /**
     * The level line, as drawn.
     *
     * @return the text, empty when the slot holds nothing
     */
    public String levelText() {
        return levelText;
    }

    /**
     * The glyph.
     *
     * @return the painter, or {@code null} when the empty-slot ring is drawn
     */
    public IconPainter icon() {
        return icon;
    }

    /**
     * What the card's colours say.
     *
     * @return the tone
     */
    public Tone tone() {
        return tone;
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
        ProceduralArt.chip(g, bx, by, bw, bh, state());
        Color accent = switch (tone) {
            case BLOCKED -> Accessibility.tone(BLOCKED);
            case FIXED -> Accessibility.tone(FIXED);
            default -> null;
        };
        if (accent != null) {
            Stroke old = g.getStroke();
            g.setStroke(BORDER);
            g.setColor(accent);
            g.drawRoundRect(bx, by, bw - 1, bh - 1, ProceduralArt.CHIP_RADIUS,
                    ProceduralArt.CHIP_RADIUS);
            g.setStroke(old);
        }
        double cy = centerY();
        Color ink = switch (tone) {
            case BLOCKED -> Accessibility.tone(BLOCKED);
            case EMPTY -> ProceduralArt.TEXT_MUTED;
            default -> ProceduralArt.TEXT_LIGHT;
        };
        if (icon == null) {
            ProceduralArt.drawSlotRing(g, bx + PADDING + ICON_SIZE / 2.0, cy, ICON_SIZE,
                    ProceduralArt.TEXT_MUTED);
        } else {
            icon.paint(g, bx + PADDING + ICON_SIZE / 2.0, cy, ICON_SIZE,
                    tone == Tone.BLOCKED ? ink : Accessibility.tone(ProceduralArt.COIN_GOLD));
        }
        int room = bw - TEXT_LEFT - PADDING;
        double scale = Fonts.textScale();
        // The level owns the right end of the top line; the slot label is clipped to what is
        // left, so a large text scale cannot run the two together.
        double levelWidth = 0;
        if (!levelText.isEmpty()) {
            g.setFont(Fonts.regular(LEVEL_SIZE));
            levelWidth = TextPainter.width(g, levelText) + 6;
            g.setColor(Accessibility.tone(ProceduralArt.COIN_GOLD));
            TextPainter.draw(g, levelText, bx + bw - (double) PADDING, by + 13.0, Align.RIGHT);
        }
        int labelRoom = (int) Math.round(room - levelWidth);
        g.setFont(Fonts.regular(ROLE_SIZE));
        if (shownLabelSource != label || shownLabelWidth != labelRoom
                || shownLabelScale != scale) {
            // Measured only when the label, the room or the text scale changed.
            shownLabel = TextPainter.ellipsise(g, label, Math.max(0, labelRoom));
            shownLabelSource = label;
            shownLabelWidth = labelRoom;
            shownLabelScale = scale;
        }
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, shownLabel, bx + (double) TEXT_LEFT, by + 13.0);
        g.setFont(Fonts.bold(NAME_SIZE));
        if (shownSource != value || shownWidth != room || shownScale != scale) {
            // Measured only when the name, the room or the text scale changed.
            shownValue = TextPainter.ellipsise(g, value, Math.max(0, room));
            shownSource = value;
            shownWidth = room;
            shownScale = scale;
        }
        g.setColor(ink);
        TextPainter.draw(g, shownValue, bx + (double) TEXT_LEFT, by + bh - 7.0);
    }
}
