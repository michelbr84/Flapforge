package io.github.michelbr84.flapforge.ui.layout;

/**
 * The parts of the physical screen a screen must not put controls in: the notch or cutout at the
 * top, the system gesture bar at the bottom, and the rounded corners at the sides.
 *
 * <p>Values are <strong>logical</strong> units of the 420-wide design space, not physical pixels:
 * the host measures the cutout in pixels and the viewport divides by its scale, so a screen never
 * converts between the two. They are always non-negative; a negative inset is clamped to zero,
 * because an inset that says "there is room here" is worse than one that says nothing.
 *
 * <p>The top and the bottom are the ones a screen feels: they are what {@code LayoutMetrics}
 * takes off the visible band to place the header and the navigation. The sides are reported
 * too, but the design width is fixed and the surface letterboxes horizontally, so on a landscape
 * phone a cutout falls in the letterbox bar rather than over the playfield.
 *
 * <p>Desktop reports {@link #ZERO} — the window's own decorations are outside the canvas. Android
 * reports what {@code WindowInsets} says; until it does, the game behaves exactly as it does today.
 */
public record SafeInsets(int top, int bottom, int left, int right) {

    /** The insets of a surface with no cutout, no gesture bar and no rounded corners. */
    public static final SafeInsets ZERO = new SafeInsets(0, 0, 0, 0);

    /**
     * Creates insets, clamping any negative part to zero.
     *
     * @param top the height of the unusable band at the top
     * @param bottom the height of the unusable band at the bottom
     * @param left the width of the unusable band at the left
     * @param right the width of the unusable band at the right
     */
    public SafeInsets {
        top = Math.max(0, top);
        bottom = Math.max(0, bottom);
        left = Math.max(0, left);
        right = Math.max(0, right);
    }
}
