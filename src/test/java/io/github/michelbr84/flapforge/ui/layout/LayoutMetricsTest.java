package io.github.michelbr84.flapforge.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The elastic surface's contract, in isolation (E31). Height is clamped into
 * {@code [640, 1000]}; a visible band's edges round into {@code top} and {@code height}; each
 * safe inset is applied exactly once to the usable area; the persistent navigation pins itself
 * to the bottom of that area, {@code NAV_BAND_H} rows tall, wherever the surface's bottom ends
 * up; and the insets are clamped so at least {@code MIN_CONTENT_H} of usable height always
 * survives.
 */
class LayoutMetricsTest {

    @Test
    void classicIsThe420By640BandWithNoInsets() {
        LayoutMetrics classic = LayoutMetrics.classic();

        assertEquals(420, classic.width(), "the design width");
        assertEquals(640, classic.height(), "the design height");
        assertEquals(0, classic.top(), "the band starts at the playfield's first row");
        assertEquals(SafeInsets.ZERO, classic.insets(), "no cutout, no gesture bar");
        assertEquals(0, classic.contentTop(), "content starts at the band's first row");
        assertEquals(640, classic.contentBottom(), "content ends at the band's last row");
        assertEquals(640, classic.contentHeight());
        assertEquals(582, classic.navTop(), "navigation sits exactly where it always did");
        assertEquals(58, classic.navHeight(), "the band is one navigation tall");
        assertEquals(582, classic.aboveNavHeight(), "everything between content and nav");
    }

    @Test
    void ofClampsTheHeightIntoTheDesignRange() {
        assertEquals(640, LayoutMetrics.of(420, 200, SafeInsets.ZERO).height(),
                "a 200-tall request clamps up to the shortest surface");
        assertEquals(1000, LayoutMetrics.of(420, 5000, SafeInsets.ZERO).height(),
                "a 5000-tall request clamps down to the tallest surface");
        assertEquals(640, LayoutMetrics.of(420, 640, SafeInsets.ZERO).height(),
                "a 640-tall request is untouched");
        assertEquals(1, LayoutMetrics.of(0, 640, SafeInsets.ZERO).width(),
                "a zero width still leaves one logical column");
    }

    @Test
    void ofBandRoundsTheEdgesIntoTheRecord() {
        LayoutMetrics metrics = LayoutMetrics.ofBand(420, -146.6, 786.4, SafeInsets.ZERO);

        assertEquals(-147, metrics.top(), "the band's first row rounds up to -147");
        assertEquals(933, metrics.height(), "the band is 933 logical rows tall");
        assertEquals(420, metrics.width());
    }

    @Test
    void aNegativeTopPutsTheContentAboveRowZeroAndTheNavAtTheBottom() {
        LayoutMetrics metrics = LayoutMetrics.of(420, 933, -147, SafeInsets.ZERO);

        assertEquals(-147, metrics.contentTop(), "the surface reaches above the playfield");
        assertEquals(-147 + 933 - LayoutMetrics.NAV_BAND_H, metrics.navTop(),
                "nav pinned to top + height - the nav band");
        assertEquals(728, metrics.navTop());
        assertEquals(875, metrics.aboveNavHeight(), "content takes everything above the nav");
    }

    @Test
    void theInsetsTakeExactlyOneBiteEach() {
        LayoutMetrics plain = LayoutMetrics.classic();
        LayoutMetrics cut = LayoutMetrics.of(420, 640, new SafeInsets(10, 20, 0, 0));

        assertEquals(plain.contentTop() + 10, cut.contentTop(),
                "a top inset moves content down by exactly the inset");
        assertEquals(plain.navTop() - 20, cut.navTop(),
                "a bottom inset lifts the nav by exactly the inset, never twice");
        assertEquals(plain.contentBottom() - 20, cut.contentBottom(),
                "content stops exactly the inset early");
    }

    @Test
    void absurdInsetsAreClampedSoAtLeastTheMinimumContentSurvives() {
        LayoutMetrics shortSurface = LayoutMetrics.of(420, 640, new SafeInsets(1000, 1000, 0, 0));
        assertEquals(80, shortSurface.insets().top(), "top clamped to half the slack");
        assertEquals(80, shortSurface.insets().bottom(), "bottom clamped to half the slack");
        assertEquals(LayoutMetrics.MIN_CONTENT_H, shortSurface.contentHeight(),
                "the shortest surface keeps its minimum content height");

        LayoutMetrics tallSurface = LayoutMetrics.of(420, 1000, new SafeInsets(999, 999, 0, 0));
        assertEquals(LayoutMetrics.MIN_CONTENT_H, tallSurface.contentHeight(),
                "even the tallest surface never loses content to the insets");
    }

    @Test
    void aboveNavHeightIsTheRoomBetweenContentTopAndNavTop() {
        assertEquals(582, LayoutMetrics.classic().aboveNavHeight());
        assertEquals(640 - 10 - 58 - 20, LayoutMetrics.of(420, 640,
                new SafeInsets(10, 20, 0, 0)).aboveNavHeight(), "insets shrink the room once each");
        assertEquals(875, LayoutMetrics.of(420, 933, -147, SafeInsets.ZERO).aboveNavHeight());
    }

    @Test
    void safeInsetsClampNegativesToZeroAndZeroIsEmpty() {
        assertEquals(SafeInsets.ZERO, new SafeInsets(-1, -2, -3, -4),
                "an all-negative inset is indistinguishable from none");
        assertEquals(new SafeInsets(5, 0, 5, 0), new SafeInsets(5, -5, 5, -5),
                "only the negative parts clamp");
        assertEquals(0, SafeInsets.ZERO.top());
        assertEquals(0, SafeInsets.ZERO.bottom());
        assertEquals(0, SafeInsets.ZERO.left());
        assertEquals(0, SafeInsets.ZERO.right());
    }
}