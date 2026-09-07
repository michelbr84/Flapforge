package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Objects;

/**
 * One item of a {@link NavBar} (D17): an icon over a short label. The primary item (Play) sits
 * on the gold call-to-action plate; the others on the teal plate with a white focus ring.
 *
 * <p>Gold comes only from {@link #isPrimary()}, never from the hover flag: a touch leaves hover
 * stuck on the last item pressed, and a second gold item would read as a second Play.
 */
public class NavButton extends UiNode {

    /** Point size of the label of a side item. */
    public static final int LABEL_SIZE = 11;
    /** Point size of the label of the primary item. */
    public static final int PRIMARY_LABEL_SIZE = 12;
    /** Icon size of a side item. */
    public static final int ICON_SIZE = 16;
    /** Icon size of the primary item. */
    public static final int PRIMARY_ICON_SIZE = 22;
    /** Vertical position of the icon centre as a fraction of the height. */
    public static final double ICON_Y = 0.36;
    /** Distance of the label baseline from the bottom edge. */
    public static final int LABEL_BASELINE_INSET = 7;
    /** Horizontal padding kept clear of the label. */
    public static final int LABEL_PADDING = 4;

    private final String id;
    private String text;
    private IconPainter icon;
    private boolean primary;
    private String shown = "";
    private String shownSource;
    private int shownWidth = -1;
    private double shownScale;

    /**
     * Creates a navigation item.
     *
     * @param id the stable id a screen looks the item up by
     * @param label the translated label
     * @param icon the icon
     * @param onAction the action run on activation, or {@code null}
     */
    public NavButton(String id, String label, IconPainter icon, Runnable onAction) {
        this.id = Objects.requireNonNull(id, "id");
        this.text = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        setOnAction(onAction);
    }

    /**
     * The id.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * The label.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * Changes the label.
     *
     * @param text the new text
     */
    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * The icon.
     *
     * @return the painter
     */
    public IconPainter icon() {
        return icon;
    }

    /**
     * Replaces the icon.
     *
     * @param icon the painter
     */
    public void setIcon(IconPainter icon) {
        this.icon = Objects.requireNonNull(icon, "icon");
    }

    /**
     * Whether this is the primary (gold) item.
     *
     * @return {@code true} for the primary item
     */
    public boolean isPrimary() {
        return primary;
    }

    /**
     * Makes this the primary item, or a side item again.
     *
     * @param primary the flag
     */
    public void setPrimary(boolean primary) {
        this.primary = primary;
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
        ButtonState state = state();
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        ProceduralArt.navButton(g, bx, by, bw, bh, state, primary);
        Color ink = primary ? ProceduralArt.ctaTextColor(state)
                : state == ButtonState.DISABLED ? ProceduralArt.TEXT_MUTED
                : ProceduralArt.TEXT_LIGHT;
        icon.paint(g, centerX(), y() + height() * ICON_Y,
                primary ? PRIMARY_ICON_SIZE : ICON_SIZE, ink);
        g.setFont(Fonts.bold(primary ? PRIMARY_LABEL_SIZE : LABEL_SIZE));
        g.setColor(ink);
        int textWidth = bw - 2 * LABEL_PADDING;
        double scale = Fonts.textScale();
        if (shownSource != text || shownWidth != textWidth || shownScale != scale) {
            // Measured only when the label, the cell or the text scale changed.
            shown = TextPainter.ellipsise(g, text, textWidth);
            shownSource = text;
            shownWidth = textWidth;
            shownScale = scale;
        }
        Shape unclipped = g.getClip();
        g.clipRect(bx + 1, by, bw - 2, bh);
        TextPainter.drawCentered(g, shown, centerX(), by + bh - LABEL_BASELINE_INSET);
        g.setClip(unclipped);
    }
}
