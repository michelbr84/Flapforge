package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import java.util.Objects;

/**
 * Builds the five-item bottom navigation the home hub and its section screens share (M11): Shop,
 * Birds, Play, Forge and Goals, in that order, with one item marked as the primary one.
 *
 * <p>On the hub the primary item is Play, the run the hub exists to start; on a section screen it
 * is the section itself, so the gold plate reads as "you are here". The geometry is the hub's:
 * the band spans the surface at the bottom of {@link LayoutMetrics} and {@link #layoutRow} lays
 * the five items out on it, so every section's navigation is the same band in the same place.
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
    public static final int HEIGHT = LayoutMetrics.NAV_BAND_H;

    /** Top of every side item inside the band. */
    private static final double SIDE_TOP = 5;
    /** Height of every side item. */
    private static final double SIDE_H = 44;
    /** Top of the primary item, which rides higher than the rest on its gold plate. */
    private static final double PRIMARY_TOP = 2;
    /**
     * Height of the primary item: with {@link #SIDE_TOP} it puts the gold plate's centre on the
     * side items' centre line, so the row still reads as one line and an arrow out of it is
     * never stolen by the plate, while leaving the band a margin below the row instead of
     * running the plate into the screen's last row.
     */
    private static final double PRIMARY_H = 50;
    /** Width of every side item. */
    private static final double SIDE_W = 76;
    /** Width of the primary item. */
    private static final double PRIMARY_W = 84;
    /** Gap between two items. */
    private static final double GAP = 4;
    /** Margin left of the first item and right of the last. */
    private static final double MARGIN = 8;

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
     * Fills a bar with the five items and lays them out on the classic 420x640 surface.
     *
     * @param nav the bar
     * @param primaryId the item drawn on the gold plate
     * @param routes what each item does
     * @return the bar, for chaining
     */
    public static NavBar build(NavBar nav, String primaryId, Routes routes) {
        return build(nav, primaryId, routes, LayoutMetrics.classic());
    }

    /**
     * Fills a bar with the five items and lays them out on the given surface.
     *
     * <p>The band is pinned to the bottom of the usable area, so on a tall phone the navigation
     * sits on the physical bottom edge instead of floating over the letterbox bar the fixed
     * 420x640 surface leaves below it.
     *
     * @param nav the bar
     * @param primaryId the item drawn on the gold plate
     * @param routes what each item does
     * @param metrics the space the screen is laying out in
     * @return the bar, for chaining
     */
    public static NavBar build(NavBar nav, String primaryId, Routes routes,
            LayoutMetrics metrics) {
        Objects.requireNonNull(nav, "nav");
        Objects.requireNonNull(primaryId, "primaryId");
        Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(metrics, "metrics");
        nav.setBounds(0, metrics.navTop(), metrics.width(), metrics.navHeight());
        nav.add(new NavButton(SHOP, "", icon(SHOP), routes.shop()));
        nav.add(new NavButton(BIRDS, "", icon(BIRDS), routes.birds()));
        nav.add(new NavButton(PLAY, "", icon(PLAY), routes.play()));
        nav.add(new NavButton(FORGE, "", icon(FORGE), routes.forge()));
        nav.add(new NavButton(GOALS, "", icon(GOALS), routes.goals()));
        // Before layoutRow, which gives the primary item its own width and height.
        nav.button(primaryId).setPrimary(true);
        layoutRow(nav, metrics);
        return nav;
    }

    /**
     * Pins a bar to the bottom of the surface and lays the five items out on it. A screen calls
     * this whenever the band may have moved — after {@link #build}, and again after a resize —
     * instead of repeating the row's numbers, which is how five screens drift apart.
     *
     * @param nav the bar
     * @param metrics the space the screen is laying out in
     * @return the bar, for chaining
     */
    public static NavBar layoutRow(NavBar nav, LayoutMetrics metrics) {
        Objects.requireNonNull(nav, "nav");
        Objects.requireNonNull(metrics, "metrics");
        nav.setBounds(0, metrics.navTop(), metrics.width(), metrics.navHeight());
        nav.layoutRow(SIDE_TOP, SIDE_H, PRIMARY_TOP, PRIMARY_H, SIDE_W, PRIMARY_W, GAP, MARGIN);
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
