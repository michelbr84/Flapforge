package io.github.michelbr84.flapforge.ui.screens.goals;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.component.TabBar;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;

/**
 * The Goals section's tab row: four separate pills with the sky showing between them, the
 * active one filled gold (the visual spec's canonical choice, section 9.1 — three of the four
 * references fill it, and it matches the gold primary navigation item).
 *
 * <p>It is a {@link TabBar}, not a parallel widget: the selection model, the arrow stepping,
 * the click hit-testing and the focus behaviour are all the inherited ones. Only the paint
 * differs — the shared bar marks its selected tab with a gold <em>underline</em>, a restraint
 * the Forge chose so no screen carried two gold plates; the Goals references measure a gold
 * <em>fill</em>, and this screen has no gold plate of its own to collide with. The per-tab
 * clip that keeps a long pt_BR label inside its cell is kept, for the same reason the shared
 * bar has it.
 */
public final class GoalsTabBar extends TabBar {

    /** Sky showing between two pills, from the reference's ~5px gap. */
    public static final int PILL_GAP = 3;

    private static final Stroke FOCUS_STROKE = new BasicStroke(2f);

    @Override
    public void render(Graphics2D g) {
        int count = size();
        if (count == 0) {
            return;
        }
        double cell = width() / count;
        int barH = (int) Math.round(height());
        int barY = (int) Math.round(y());
        g.setFont(Fonts.bold(FONT_SIZE));
        for (int i = 0; i < count; i++) {
            int tx = (int) Math.round(x() + i * cell + PILL_GAP / 2.0);
            int tw = (int) Math.round(cell - PILL_GAP);
            boolean active = i == selectedIndex();
            GoalsArt.tab(g, tx, barY, tw, barH, active, !active && isHovered());
            Color ink = active ? ProceduralArt.TEXT_DARK : GoalsArt.TAB_INK;
            Shape unclipped = g.getClip();
            g.clipRect(tx + 2, barY, Math.max(0, tw - 4), barH);
            g.setColor(ink);
            double baseline = TextPainter.centeredBaseline(g, barY + barH / 2.0);
            TextPainter.draw(g, tabs().get(i).label(), tx + tw / 2.0, baseline, Align.CENTER);
            g.setClip(unclipped);
        }
        if (isFocused()) {
            Stroke old = g.getStroke();
            g.setStroke(FOCUS_STROKE);
            g.setColor(ProceduralArt.COIN_GOLD);
            int sx = (int) Math.round(x() + selectedIndex() * cell + PILL_GAP / 2.0);
            g.drawRoundRect(sx, barY + 1, (int) Math.round(cell - PILL_GAP) - 2, barH - 2,
                    barH / 2, barH / 2);
            g.setStroke(old);
        }
    }
}
