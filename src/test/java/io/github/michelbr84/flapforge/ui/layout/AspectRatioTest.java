package io.github.michelbr84.flapforge.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The elastic surface across the aspect ratios a phone actually ships (M11). Every device here is
 * 1080 physical pixels wide, so the scale is fixed at {@code 1080 / 420} and the logical band is
 * simply the device's height over that scale — the band therefore grows with the aspect ratio,
 * which is the property the whole responsive surface rests on.
 *
 * <p>What each device must prove is that the band reaches the physical edges: the classic 420x640
 * playfield sits <em>inside</em> it on a tall device ({@code contentTop() <= 0} and
 * {@code contentBottom() >= 640}) rather than being centred inside a dead letterbox, and on a
 * device shorter than the playfield's own ratio the band is exactly the classic one instead of
 * being squashed. The navigation is pinned to the band's bottom edge everywhere, and the room
 * above it never falls under {@link LayoutMetrics#MIN_CONTENT_H}.
 */
class AspectRatioTest {

    /** A 74-pixel gesture bar, the height a modern Android navigation bar reports. */
    private static final int GESTURE_BAR_PX = 74;

    /**
     * The devices, in ascending order of aspect ratio: a short one (1.48, below the playfield's
     * own 1.52, so the band stays classic), then 16:9, 18:9, 19.5:9, 20:9 and 21:9.
     */
    private static final List<Device> DEVICES = List.of(
            new Device("short 1080x1600", 1080, 1600),
            new Device("16:9 1080x1920", 1080, 1920),
            new Device("18:9 1080x2160", 1080, 2160),
            new Device("19.5:9 1080x2340", 1080, 2340),
            new Device("20:9 1080x2400", 1080, 2400),
            new Device("21:9 1080x2520", 1080, 2520));

    @Test
    void everyDeviceUsesItsWholeBandInsteadOfCentringABox() {
        for (Device device : DEVICES) {
            ScreenManager screens = new ScreenManager(device.viewport());
            LayoutMetrics metrics = screens.metrics();

            assertTrue(metrics.contentTop() <= 0,
                    () -> device + ": the band starts at or above the playfield's first row, got "
                            + metrics.contentTop());
            assertTrue(metrics.contentBottom() >= Playfield.HEIGHT,
                    () -> device + ": the band reaches at least to row " + Playfield.HEIGHT
                            + ", got " + metrics.contentBottom());
            assertEquals(LayoutMetrics.DESIGN_W, metrics.width(), () -> device + ": design width");
            assertTrue(metrics.height() >= LayoutMetrics.MIN_H
                    && metrics.height() <= LayoutMetrics.MAX_H,
                    () -> device + ": the height is clamped, got " + metrics.height());
        }
    }

    @Test
    void theNavigationIsPinnedToTheBottomEdgeOfEveryBand() {
        for (Device device : DEVICES) {
            ScreenManager screens = new ScreenManager(device.viewport());
            LayoutMetrics metrics = screens.metrics();

            assertEquals(metrics.contentBottom() - LayoutMetrics.NAV_BAND_H, metrics.navTop(),
                    () -> device + ": the band ends on the surface's bottom edge");
            assertTrue(metrics.navTop() > metrics.contentTop(),
                    () -> device + ": the navigation never overlaps the header");
            assertTrue(metrics.aboveNavHeight() >= LayoutMetrics.MIN_CONTENT_H,
                    () -> device + ": the content region keeps its minimum, got "
                            + metrics.aboveNavHeight());
            assertEquals(LayoutMetrics.NAV_BAND_H, metrics.navHeight(),
                    () -> device + ": the touch target does not shrink on a short screen");
        }
    }

    @Test
    void aTallerDeviceGetsATallerBandNotABiggerScale() {
        int previous = 0;
        for (Device device : DEVICES) {
            LayoutMetrics metrics = new ScreenManager(device.viewport()).metrics();
            assertTrue(metrics.height() >= previous,
                    () -> device + ": the band grows with the aspect ratio");
            previous = metrics.height();
        }
    }

    @Test
    void aGestureBarLiftsTheNavigationOnEveryDevice() {
        for (Device device : DEVICES) {
            ScreenManager screens = new ScreenManager(device.viewport());
            int without = screens.metrics().navTop();
            screens.setSafeInsetsPx(0, GESTURE_BAR_PX, 0, 0);
            LayoutMetrics metrics = screens.metrics();

            assertTrue(metrics.insets().bottom() > 0,
                    () -> device + ": the gesture bar is measured in logical rows");
            assertTrue(metrics.navTop() < without,
                    () -> device + ": the navigation lifts clear of the gesture bar");
            assertEquals(metrics.contentBottom() - LayoutMetrics.NAV_BAND_H, metrics.navTop(),
                    () -> device + ": and still ends the band");
        }
    }

    @Test
    void aDeviceShorterThanThePlayfieldKeepsTheClassicBand() {
        Device shortDevice = DEVICES.get(0);
        LayoutMetrics metrics = new ScreenManager(shortDevice.viewport()).metrics();

        assertEquals(LayoutMetrics.classic(), metrics,
                "a 1.48 device letterboxes horizontally instead of squashing the band");
        assertEquals(0, metrics.contentTop());
        assertEquals(Playfield.HEIGHT, metrics.contentBottom());
        assertEquals(582, metrics.navTop(), "the navigation keeps its classic row");
    }

    /**
     * One device: a name for the failure messages and the physical size to lay out against.
     *
     * @param name the name
     * @param width the width in pixels
     * @param height the height in pixels
     */
    private record Device(String name, int width, int height) {

        /**
         * The viewport of this device.
         *
         * @return the viewport
         */
        private Viewport viewport() {
            return new Viewport(width, height, false);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
