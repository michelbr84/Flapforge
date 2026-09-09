package io.github.michelbr84.flapforge.ui.layout;

import io.github.michelbr84.flapforge.core.Playfield;

/**
 * The space a screen may use, in logical units of the 420-wide design space.
 *
 * <p>The design was fixed at 420x640 and every screen laid out against it, so on a tall phone the
 * viewport letterboxed the surface and centred it: on 1080x2400 that left 377 physical pixels of
 * dead space above <em>and</em> below, and the bottom navigation floated 377 px above the physical
 * bottom. The fix is not to stretch that rectangle — it is to keep the logical <em>width</em> and
 * let the logical <em>height</em> be elastic, so the surface grows into the space it used to waste.
 * A screen therefore lays out against {@link #contentTop()} and {@link #navTop()} and lets the
 * content between them be whatever is left, instead of assuming 640.
 *
 * <p>The band order a screen must honour, top to bottom: the safe-area inset, the header region,
 * the flexible content region, the persistent bottom navigation, the safe-area inset again.
 *
 * <p>This is a UI-only value. No AWT type reaches {@code core}, {@code gameplay} or
 * {@code progression} through it, and nothing here is simulation geometry.
 */
public record LayoutMetrics(int width, int height, int top, SafeInsets insets) {

    /** The logical width every screen lays out against; the design width is fixed. */
    public static final int DESIGN_W = Playfield.WIDTH;
    /** The shortest logical surface: the classic 420x640 rectangle, unchanged from today. */
    public static final int MIN_H = Playfield.HEIGHT;
    /**
     * The tallest logical surface: 21:9 portrait is 420x980, so 1000 is the last height worth
     * laying out for. Beyond it the surface letterboxes again rather than growing cards, icons
     * and type without bound.
     */
    public static final int MAX_H = 1000;
    /** The height of the persistent bottom navigation band. */
    public static final int NAV_BAND_H = 58;
    /**
     * The least content height a screen is allowed: the insets are clamped so that header, content
     * and navigation always fit inside even the shortest surface.
     */
    public static final int MIN_CONTENT_H = 480;

    /** The classic 420x640 surface with no insets: today's geometry, exactly. */
    public static final LayoutMetrics CLASSIC = of(DESIGN_W, MIN_H, 0, SafeInsets.ZERO);

    /**
     * Creates metrics for a band starting at the playfield's own top, clamping the height into
     * {@code [MIN_H, MAX_H]} and the insets so that at least {@link #MIN_CONTENT_H} of usable
     * height survives.
     *
     * @param width the logical width of the surface
     * @param height the logical height of the surface
     * @param insets the unusable bands, in logical units
     * @return the metrics
     */
    public static LayoutMetrics of(int width, int height, SafeInsets insets) {
        return of(width, height, 0, insets);
    }

    /**
     * Creates metrics for a band starting at {@code top}, which is negative whenever the visible
     * surface reaches above the playfield — the whole point of the elastic surface: on a tall
     * phone the band runs from about {@code -147} to {@code 786} instead of {@code 0} to
     * {@code 640}, and the navigation pinned to {@link #navTop()} lands on the physical bottom
     * instead of floating over a letterbox bar.
     *
     * <p>The height is clamped into {@code [MIN_H, MAX_H]} and each inset is clamped so that at
     * least {@link #MIN_CONTENT_H} of usable height survives; beyond {@link #MAX_H} the band is
     * centred on the playfield rather than growing without bound.
     *
     * @param width the logical width of the surface
     * @param height the logical height of the surface
     * @param top the first visible logical row, usually negative or zero
     * @param insets the unusable bands, in logical units
     * @return the metrics
     */
    public static LayoutMetrics of(int width, int height, int top, SafeInsets insets) {
        int h = Math.min(MAX_H, Math.max(MIN_H, height));
        int slack = Math.max(0, h - MIN_CONTENT_H) / 2;
        SafeInsets safe = new SafeInsets(Math.min(insets.top(), slack),
                Math.min(insets.bottom(), slack), insets.left(), insets.right());
        return new LayoutMetrics(Math.max(1, width), h, top, safe);
    }

    /**
     * The metrics of a visible band given by its edges, the way {@code Viewport} sees it.
     *
     * @param width the logical width of the surface
     * @param top the first visible logical row
     * @param bottom the last visible logical row, exclusive
     * @param insets the unusable bands, in logical units
     * @return the metrics
     */
    public static LayoutMetrics ofBand(int width, double top, double bottom, SafeInsets insets) {
        return of(width, (int) Math.round(bottom - top), (int) Math.round(top), insets);
    }

    /**
     * The classic 420x640 surface with no insets: the fixture that keeps every existing render
     * test, and the pinned run hash, on the geometry they were recorded against.
     *
     * @return the metrics of the classic surface
     */
    public static LayoutMetrics classic() {
        return CLASSIC;
    }

    /**
     * The top of the usable area: the band's first row plus the top inset, where the header
     * region starts. Negative on a tall surface, because the band reaches above the playfield.
     *
     * @return the first logical y a screen may draw at
     */
    public int contentTop() {
        return top + insets.top();
    }

    /**
     * The bottom of the usable area: the band's last row minus the bottom inset, where the
     * navigation band ends.
     *
     * @return the last logical y a screen may draw at, exclusive
     */
    public int contentBottom() {
        return top + height - insets.bottom();
    }

    /** The height between {@link #contentTop()} and {@link #contentBottom()}. */
    public int contentHeight() {
        return contentBottom() - contentTop();
    }

    /** The height of the persistent bottom navigation band. */
    public int navHeight() {
        return NAV_BAND_H;
    }

    /**
     * The top of the navigation band: pinned to the bottom of the usable area, so the navigation
     * sits at the physical bottom of the screen instead of floating over a dead band.
     *
     * @return the logical y the navigation band starts at
     */
    public int navTop() {
        return contentBottom() - navHeight();
    }

    /**
     * The bottom of the flexible content region: where the navigation band begins. A screen's
     * content ends here, not at {@code 582}.
     *
     * @return the logical y the content region ends at, exclusive
     */
    public int contentLimit() {
        return navTop();
    }

    /**
     * The room a header, a tab row and a content panel must share.
     *
     * @return the height between {@link #contentTop()} and {@link #navTop()}
     */
    public int aboveNavHeight() {
        return navTop() - contentTop();
    }
}
