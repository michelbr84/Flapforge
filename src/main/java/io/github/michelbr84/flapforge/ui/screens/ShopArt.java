package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import java.awt.Color;
import java.util.Objects;

/**
 * The picture on a shop card (M12): what the thing being sold looks like, resolved once when the
 * card is built.
 *
 * <p>The shop sells seven kinds of thing and only two of them ship art of their own, so this is
 * where the mapping lives — the same place {@link AbilityIcons} keeps the tag-to-glyph mapping,
 * and for the same reason: nothing in the content files names a picture, and inventing a field
 * for one would be authoring what the code can derive.
 *
 * <table>
 *   <caption>What each kind draws</caption>
 *   <tr><th>Kind</th><th>Art</th></tr>
 *   <tr><td>Bird</td><td>its procedural portrait in its own first palette</td></tr>
 *   <tr><td>Palette</td><td>the two colours it changes, as a plate and a wing</td></tr>
 *   <tr><td>Ability</td><td>the glyph of what it does, through {@link AbilityIcons}</td></tr>
 *   <tr><td>World</td><td>its sky, ground and sun, through {@link WorldSwatch}</td></tr>
 *   <tr><td>Tier</td><td>a chevron — the difficulty rows use the same mark</td></tr>
 *   <tr><td>Challenge</td><td>a scroll, as the Goals navigation item does</td></tr>
 *   <tr><td>Tree, feature, modifier</td><td>a hammer, a gear and a spark</td></tr>
 * </table>
 *
 * <p>Every painter allocates its colours at construction, never in the render path.
 */
public final class ShopArt {

    /** A gear: a feature, and the Features tab. */
    private static final IconPainter GEAR = (g, cx, cy, size, color) ->
            ProceduralArt.drawGear(g, cx, cy, size, color, ProceduralArt.TEXT_DARK);
    /** A hammer over a wooden handle: an upgrade tree. */
    private static final IconPainter HAMMER = (g, cx, cy, size, color) ->
            ProceduralArt.drawHammer(g, cx, cy, size, 0.5, color, ProceduralArt.WOOD);
    /** The shop's own sign, for anything else that carries a price. */
    private static final IconPainter AWNING = (g, cx, cy, size, color) ->
            ProceduralArt.drawAwning(g, cx, cy, size, color, ProceduralArt.TEXT_DARK);

    private ShopArt() {
    }

    /**
     * The art of one offer.
     *
     * @param content the loaded content
     * @param kind the kind of the unlockable, may be {@code null}
     * @param unlockId the namespaced id
     * @return a painter, or {@code null} when the id resolves to nothing drawable
     */
    public static CardGrid.ArtPainter of(GameContent content, ContentKind kind, String unlockId) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(unlockId, "unlockId");
        if (kind == null) {
            return glyph(GEAR);
        }
        String bareId = unlockId.startsWith(namespaceOf(kind))
                ? unlockId.substring(namespaceOf(kind).length()) : unlockId;
        switch (kind) {
            case BIRD:
                return birdArt(content, bareId);
            case COSMETIC:
                return paletteArt(content, bareId);
            case ABILITY:
                return abilityArt(content, bareId);
            case WORLD:
                return worldArt(content, bareId);
            case TIER:
                return glyph(ProceduralArt::drawChevron);
            case CHALLENGE:
                return (g, card, cx, cy, size) ->
                        ProceduralArt.drawScroll(g, cx, cy, size, ProceduralArt.TEXT_LIGHT,
                                ProceduralArt.TEXT_DARK);
            case TREE:
                return glyph(HAMMER);
            case MODIFIER:
                return glyph(ProceduralArt::drawCrown);
            case FEATURE:
                return glyph(GEAR);
            default:
                return glyph(AWNING);
        }
    }

    /**
     * The glyph of one shop category, for the tabs.
     *
     * @param tabId one of the shop's four tab ids
     * @return the painter
     * @throws IllegalArgumentException when the id is not one of the four
     */
    public static IconPainter tab(String tabId) {
        switch (Objects.requireNonNull(tabId, "tabId")) {
            case ShopScreen.TAB_BIRDS:
                return ProceduralArt::drawBirdSilhouette;
            case ShopScreen.TAB_ABILITIES:
                return ProceduralArt::drawSpark;
            case ShopScreen.TAB_WORLDS:
                return ProceduralArt::drawChevron;
            case ShopScreen.TAB_FEATURES:
                return GEAR;
            default:
                throw new IllegalArgumentException("no shop tab " + tabId);
        }
    }

    /**
     * The namespace of a kind, or the empty string for one that has none.
     *
     * @param kind the kind
     * @return the prefix
     */
    private static String namespaceOf(ContentKind kind) {
        return kind.namespace() == null ? "" : kind.namespace();
    }

    /**
     * A bird's portrait in the palette it ships first — the shop sells the bird, not a colour, so
     * it always shows the bird's own look rather than the profile's selection.
     *
     * @param content the loaded content
     * @param birdId the bare bird id
     * @return the painter, or {@code null} when the bird is unknown
     */
    private static CardGrid.ArtPainter birdArt(GameContent content, String birdId) {
        if (!content.has(GameContent.BIRDS) || !content.birds().contains(birdId)) {
            return null;
        }
        BirdDef bird = content.birds().get(birdId);
        PaletteDef palette = bird.palette(ProgressionText.defaultPaletteId(bird));
        if (palette == null) {
            return null;
        }
        int body = palette.bodyRgb();
        int wing = palette.wingRgb();
        int eye = palette.eyeRgb();
        int accent = palette.accentRgb();
        String shape = bird.shape();
        return (g, card, cx, cy, size) -> ProceduralArt.drawBirdPortrait(g, cx,
                cy + size * 0.05, size * 0.9, 0.0, body, wing, eye, accent, shape);
    }

    /**
     * A palette, as the two colours it changes: the body as a plate and the wing as a corner.
     *
     * @param content the loaded content
     * @param bareId the cosmetic id, {@code <bird>:<palette>}
     * @return the painter, or {@code null} when either half is unknown
     */
    private static CardGrid.ArtPainter paletteArt(GameContent content, String bareId) {
        int split = bareId.indexOf(':');
        if (split < 0 || !content.has(GameContent.BIRDS)) {
            return null;
        }
        String birdId = bareId.substring(0, split);
        if (!content.birds().contains(birdId)) {
            return null;
        }
        PaletteDef palette = content.birds().get(birdId).palette(bareId.substring(split + 1));
        if (palette == null) {
            return null;
        }
        Color body = new Color(palette.bodyRgb());
        Color wing = new Color(palette.wingRgb());
        Color accent = new Color(palette.accentRgb());
        return (g, card, cx, cy, size) -> {
            int s = (int) Math.round(size);
            int sx = (int) Math.round(cx - size / 2);
            int sy = (int) Math.round(cy - size / 2);
            g.setColor(body);
            g.fillRoundRect(sx, sy, s, s, 6, 6);
            g.setColor(wing);
            g.fillRect(sx + s / 2, sy + s / 2, s / 2, s / 2);
            g.setColor(accent);
            g.drawRoundRect(sx, sy, s - 1, s - 1, 6, 6);
        };
    }

    /**
     * An ability's glyph, by what the ability does.
     *
     * @param content the loaded content
     * @param abilityId the bare ability id
     * @return the painter, or {@code null} when the ability is unknown
     */
    private static CardGrid.ArtPainter abilityArt(GameContent content, String abilityId) {
        if (!content.has(GameContent.ABILITIES) || !content.abilities().contains(abilityId)) {
            return null;
        }
        AbilityDef def = content.abilities().get(abilityId);
        IconPainter icon = AbilityIcons.of(def);
        // The glyph in the gold the loadout cards of the Birds screen use, inside the ring an
        // empty slot wears, so an ability tile reads as a socket the ability fits.
        return (g, card, cx, cy, size) -> {
            ProceduralArt.drawSlotRing(g, cx, cy, size, ProceduralArt.TEXT_MUTED);
            icon.paint(g, cx, cy, size * 0.6, Accessibility.tone(ProceduralArt.COIN_GOLD));
        };
    }

    /**
     * A world's thumbnail.
     *
     * @param content the loaded content
     * @param worldId the bare world id
     * @return the painter, or {@code null} when the world is unknown
     */
    private static CardGrid.ArtPainter worldArt(GameContent content, String worldId) {
        if (!content.has(GameContent.WORLDS) || !content.worlds().contains(worldId)) {
            return null;
        }
        WorldDef def = content.worlds().get(worldId);
        return new WorldSwatch(def.palette());
    }

    /**
     * Wraps a glyph painter as card art, drawn in the light ink every other card glyph uses.
     *
     * @param painter the glyph
     * @return the card art
     */
    private static CardGrid.ArtPainter glyph(IconPainter painter) {
        return (g, card, cx, cy, size) ->
                painter.paint(g, cx, cy, size, ProceduralArt.TEXT_LIGHT);
    }
}
