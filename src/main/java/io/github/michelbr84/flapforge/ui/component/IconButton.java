package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import java.awt.Graphics2D;
import java.util.Objects;

/**
 * A square button showing an icon instead of a label (D17): the gear of the home hub. The label
 * handed to the constructor is the accessible name ({@link #text()}), never drawn. The plate is
 * {@link ProceduralArt#chip}, whose focus look is a white ring, so the button never competes with
 * the hub's gold call to action.
 */
public class IconButton extends Button {

    /** Fraction of the shorter side the icon takes. */
    public static final double ICON_FRACTION = 0.55;

    private IconPainter icon;

    /**
     * Creates an icon button.
     *
     * @param name the accessible name (not drawn)
     * @param icon the icon
     * @param onAction the action run on activation, or {@code null}
     */
    public IconButton(String name, IconPainter icon, Runnable onAction) {
        super(name, onAction);
        this.icon = Objects.requireNonNull(icon, "icon");
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

    @Override
    public void render(Graphics2D g) {
        ButtonState state = state();
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        ProceduralArt.chip(g, bx, by, bw, bh, state);
        double size = Math.min(width(), height()) * ICON_FRACTION;
        icon.paint(g, centerX(), centerY(), size,
                state == ButtonState.DISABLED ? ProceduralArt.TEXT_MUTED : ProceduralArt.TEXT_LIGHT);
    }
}
