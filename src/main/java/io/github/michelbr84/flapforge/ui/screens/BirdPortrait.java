package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import java.awt.Graphics2D;

/**
 * Draws the profile's selected bird in its selected palette (D17, M10): the avatar of the player
 * card on the hub and of the Profile header. Without content, or when the selection names
 * nothing the content knows, the classic bird in its default colours is drawn, which is also
 * what a brand-new profile selects.
 */
final class BirdPortrait {

    /** Body colour of the classic bird's default palette. */
    static final int CLASSIC_BODY = 0xF5C542;
    /** Wing colour of the classic bird's default palette. */
    static final int CLASSIC_WING = 0xE09A2B;
    /** Eye colour of the classic bird's default palette. */
    static final int CLASSIC_EYE = 0x222222;
    /** Accent colour of the classic bird's default palette. */
    static final int CLASSIC_ACCENT = 0xFFF3C4;
    /** Silhouette of the classic bird. */
    static final String CLASSIC_SHAPE = "balanced";

    private BirdPortrait() {
    }

    /**
     * Draws the portrait.
     *
     * @param g the context
     * @param content the loaded content, or {@code null}
     * @param profile the profile whose selection is drawn, or {@code null} for the classic bird
     * @param cx the centre x
     * @param cy the centre y
     * @param size the portrait size
     * @param wingPhase animation phase in {@code [0, 1)}
     */
    static void draw(Graphics2D g, GameContent content, PlayerProfile profile, double cx,
            double cy, double size, double wingPhase) {
        BirdDef bird = null;
        if (content != null && profile != null && content.has(GameContent.BIRDS)
                && content.birds().contains(profile.selected.birdId)) {
            bird = content.birds().get(profile.selected.birdId);
        }
        PaletteDef palette = null;
        if (bird != null) {
            palette = bird.palette(profile.selected.paletteId);
            if (palette == null && !bird.palettes().isEmpty()) {
                palette = bird.palettes().get(0);
            }
        }
        if (palette == null) {
            ProceduralArt.drawBirdPortrait(g, cx, cy + size * 0.05, size * 0.9, wingPhase,
                    CLASSIC_BODY, CLASSIC_WING, CLASSIC_EYE, CLASSIC_ACCENT,
                    bird == null ? CLASSIC_SHAPE : bird.shape());
            return;
        }
        ProceduralArt.drawBirdPortrait(g, cx, cy + size * 0.05, size * 0.9, wingPhase,
                palette.bodyRgb(), palette.wingRgb(), palette.eyeRgb(), palette.accentRgb(),
                bird.shape());
    }
}
