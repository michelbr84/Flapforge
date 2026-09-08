package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import java.util.Objects;

/**
 * Builds the five-item bottom navigation the home hub and its section screens share (M11): Shop,
 * Birds, Play, Forge and Goals, in that order, with one item marked as the primary one.
 *
 * <p>On the hub the primary item is Play, the run the hub exists to start; on a section screen it
 * is the section itself, so the gold plate reads as "you are here". The geometry is the hub's:
 * the band spans the playfield at {@link #TOP} and {@link NavBar#layoutRow} places the items.
 */
public final class SectionNav {

    /** Navigation item id: the shop. */
    public static final String SHOP = "shop";
    /** Navigation item id: the bird selection. */
    public static final String BIRDS = "birds";
    /** Navigation item id: start a run. */
    public static final String PLAY = "play";
    /** Navigation item id: the upgrade trees. */
    public static final String FORGE = "forge";
    /** Navigation item id: the goals. */
    public static final String GOALS = "goals";

    /** Top edge of the band. */
    public static final int TOP = 582;
    /** Height of the band. */
    public static final int HEIGHT = 58;

    private SectionNav() {
    }

    /**
     * What each item does. A {@code null} route makes that item inert: it is still drawn and
     * still walked over, it simply has nothing to do (the current section).
     *
     * @param shop the shop route
     * @param birds the bird selection route
     * @param play the run route
     * @param forge the upgrade trees route
     * @param goals the goals route
     */
    public record Routes(Runnable shop, Runnable birds, Runnable play, Runnable forge,
            Runnable goals) {
    }

    /**
     * Fills a bar with the five items and lays them out.
     *
     * @param nav the bar
     * @param primaryId the item drawn on the gold plate
     * @param routes what each item does
     * @return the bar, for chaining
     */
    public static NavBar build(NavBar nav, String primaryId, Routes routes) {
        Objects.requireNonNull(nav, "nav");
        Objects.requireNonNull(primaryId, "primaryId");
        Objects.requireNonNull(routes, "routes");
        nav.setBounds(0, TOP, Playfield.WIDTH, HEIGHT);
        nav.add(new NavButton(SHOP, "", icon(SHOP), routes.shop()));
        nav.add(new NavButton(BIRDS, "", icon(BIRDS), routes.birds()));
        nav.add(new NavButton(PLAY, "", icon(PLAY), routes.play()));
        nav.add(new NavButton(FORGE, "", icon(FORGE), routes.forge()));
        nav.add(new NavButton(GOALS, "", icon(GOALS), routes.goals()));
        // Before layoutRow, which gives the primary item its own width and height.
        nav.button(primaryId).setPrimary(true);
        nav.layoutRow(8, 44, 2, 56, 76, 84, 4, 8);
        return nav;
    }

    /**
     * The glyph of one navigation item.
     *
     * @param id the item id
     * @return the painter
     * @throws IllegalArgumentException when the id is not one of the five
     */
    public static IconPainter icon(String id) {
        switch (Objects.requireNonNull(id, "id")) {
            case SHOP:
                return (g, cx, cy, size, color) ->
                        ProceduralArt.drawAwning(g, cx, cy, size, color, ProceduralArt.TEXT_DARK);
            case BIRDS:
                return (g, cx, cy, size, color) ->
                        ProceduralArt.drawBirdSilhouette(g, cx, cy, size, color);
            case PLAY:
                return (g, cx, cy, size, color) ->
                        ProceduralArt.drawCrossedHammers(g, cx, cy, size, color);
            case FORGE:
                return (g, cx, cy, size, color) ->
                        ProceduralArt.drawHammer(g, cx, cy, size, 0.5, color, color);
            case GOALS:
                return (g, cx, cy, size, color) ->
                        ProceduralArt.drawScroll(g, cx, cy, size, color, ProceduralArt.TEXT_DARK);
            default:
                throw new IllegalArgumentException("no navigation item " + id);
        }
    }
}
