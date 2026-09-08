package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityTag;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import java.awt.geom.Ellipse2D;

/**
 * The glyph of an ability, by what the ability does (M11): a heart for a revive, a shield for a
 * defensive one, a wing for a movement one, a magnet for an economy one and an hourglass for a
 * tempo one. Nothing in the content files names an icon, so the mapping lives here, keyed on the
 * tags the abilities already carry.
 *
 * <p>Every painter is a constant, so binding a card allocates nothing.
 */
final class AbilityIcons {

    /** The bird the profile would fly with (the call to action of an owned bird). */
    static final IconPainter BIRD = ProceduralArt::drawBirdSilhouette;
    /** The bird the profile already flies with. */
    static final IconPainter CHECK = ProceduralArt::drawCheck;
    /** Something not open yet. */
    static final IconPainter PADLOCK = ProceduralArt::drawPadlock;

    private static final IconPainter SHIELD = ProceduralArt::drawShield;
    private static final IconPainter HEART = ProceduralArt::drawHeart;
    private static final IconPainter WING = ProceduralArt::drawWing;
    private static final IconPainter MAGNET = ProceduralArt::drawMagnet;
    private static final IconPainter HOURGLASS = ProceduralArt::drawHourglass;
    private static final IconPainter SPARK = ProceduralArt::drawSpark;

    private AbilityIcons() {
    }

    /**
     * The glyph of one ability.
     *
     * @param def the ability, may be {@code null}
     * @return the painter, or {@code null} for no ability at all
     */
    static IconPainter of(AbilityDef def) {
        if (def == null) {
            return null;
        }
        if (def.has(AbilityTag.REVIVE)) {
            return HEART;
        }
        if (def.has(AbilityTag.DEFENSIVE)) {
            return SHIELD;
        }
        if (def.has(AbilityTag.MOVEMENT)) {
            return WING;
        }
        if (def.has(AbilityTag.ECONOMY)) {
            return MAGNET;
        }
        if (def.has(AbilityTag.TEMPO)) {
            return HOURGLASS;
        }
        return SPARK;
    }

    /**
     * The coin glyph of the buy call to action, drawn through a scratch ellipse the caller owns
     * so the draw path allocates nothing.
     *
     * @param scratch the ellipse the coin is drawn with
     * @return the painter
     */
    static IconPainter coin(Ellipse2D.Double scratch) {
        return (g, cx, cy, size, color) -> ProceduralArt.drawCoin(g, scratch, cx, cy, size / 2, 1);
    }
}
