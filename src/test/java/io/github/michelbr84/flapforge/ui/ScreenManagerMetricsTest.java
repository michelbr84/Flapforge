package io.github.michelbr84.flapforge.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import io.github.michelbr84.flapforge.ui.layout.SafeInsets;
import org.junit.jupiter.api.Test;

/**
 * The surface a screen lays out against, as the manager derives it from the viewport (E31). At
 * the classic 420x640 surface the metrics are the classic band; on a tall phone the band grows
 * above and below the playfield; turning "fill screen" off falls back to the classic band; and
 * the safe insets, set in physical pixels, take exactly their logical share of that band.
 */
class ScreenManagerMetricsTest {

    /** Logical columns of the 420-wide design space. */
    private static final int DESIGN_W = LayoutMetrics.DESIGN_W;

    @Test
    void atTheClassicSurfaceTheMetricsAreTheClassicBand() {
        ScreenManager screens = new ScreenManager(new Viewport(DESIGN_W, 640, false));

        assertEquals(LayoutMetrics.classic(), screens.metrics());
    }

    @Test
    void onATallPhoneTheBandGrowsAboveAndBelowThePlayfield() {
        ScreenManager screens = new ScreenManager(new Viewport(1080, 2400, false));

        LayoutMetrics metrics = screens.metrics();
        assertEquals(-147, metrics.top(), "the band reaches 147 rows above row 0");
        assertEquals(933, metrics.height(), "the surface grew, not stretched");
        assertEquals(-147, metrics.contentTop(), "content starts above the playfield");
        assertEquals(786, metrics.contentBottom(), "content ends below row 640");
        assertEquals(728, metrics.navTop(), "the navigation sits on the physical bottom");
        assertEquals(DESIGN_W, metrics.width(), "the design width is untouched");
    }

    @Test
    void turningFillScreenOffFallsBackToTheClassicBand() {
        ScreenManager screens = new ScreenManager(new Viewport(1080, 2400, false));
        screens.viewport().setExtendVertical(false);

        LayoutMetrics metrics = screens.metrics();
        assertEquals(LayoutMetrics.classic(), metrics, "the band is the playfield, letterboxed");
        assertEquals(582, metrics.navTop(), "the nav stays where it always has");
        assertEquals(0, metrics.top());
        assertEquals(640, metrics.height());
    }

    @Test
    void aBottomSafeInsetLiftsTheNavigationByItsLogicalRows() {
        ScreenManager screens = new ScreenManager(new Viewport(1080, 2400, false));
        screens.setSafeInsetsPx(0, 74, 0, 0);

        LayoutMetrics metrics = screens.metrics();
        assertEquals(29, metrics.insets().bottom(),
                "74 physical pixels at scale 18/7 are 29 logical rows");
        assertEquals(699, metrics.navTop(), "the nav lifts by the inset, not twice it");
        assertEquals(757, metrics.contentBottom(), "content stops one inset early");
    }

    @Test
    void aTopSafeInsetPushesTheContentTopAboveTheBandEdge() {
        ScreenManager screens = new ScreenManager(new Viewport(1080, 2400, false));
        screens.setSafeInsetsPx(60, 0, 0, 0);

        LayoutMetrics metrics = screens.metrics();
        assertEquals(23, metrics.insets().top(),
                "60 physical pixels at scale 18/7 are 23 logical rows");
        assertEquals(-124, metrics.contentTop(), "content starts 23 rows below the band's top");
        assertEquals(728, metrics.navTop(), "a top inset leaves the nav alone");
    }

    @Test
    void zeroInsetsChangeNothing() {
        ScreenManager screens = new ScreenManager(new Viewport(1080, 2400, false));

        LayoutMetrics before = screens.metrics();
        screens.setSafeInsetsPx(0, 0, 0, 0);
        assertEquals(before, screens.metrics(), "zeros are the default, pixel for pixel");

        LayoutMetrics direct = LayoutMetrics.ofBand(DESIGN_W, screens.viewport().visibleTopY(),
                screens.viewport().visibleBottomY(), SafeInsets.ZERO);
        assertEquals(direct, screens.metrics(), "the manager is the band the viewport sees");
    }
}