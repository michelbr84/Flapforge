package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The safe area crosses from the platform to the layout (M11): what {@code WindowInsets} measures
 * in physical pixels becomes the logical band {@link LayoutMetrics} takes off the visible surface,
 * so the navigation band clears the gesture bar and the header clears the cutout.
 *
 * <p>The sums are those of a 1080x2400 phone. Its scale is {@code 1080 / 420 = 2.571}, so the
 * visible logical band runs from about {@code -147} to {@code 786} and a 74-pixel gesture bar is
 * 29 logical rows: the band's bottom edge, and with it the navigation, sits 29 rows higher than
 * the physical bottom instead of under the swipe.
 *
 * <p>The activity here is a bare one: the host needs a {@code Context} for its touch slop and
 * nothing else, and booting the real {@link MainActivity} would start a game this test has no
 * use for. The one step these tests do not cover is {@code WindowInsets} itself, which needs a
 * window the platform has laid out — everything from the pixel values onwards is exercised.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SafeInsetsTest {

    /** Width of the phone the numbers below are measured on. */
    private static final int PHONE_W = 1080;
    /** Height of the phone the numbers below are measured on. */
    private static final int PHONE_H = 2400;
    /** The band without any inset: {@code -147 .. 786}, navigation at 728. */
    private static final int NAV_TOP_NO_INSETS = 728;
    /** A 74-pixel gesture bar at this scale, in logical rows. */
    private static final int GESTURE_BAR_LOGICAL = 29;

    @Test
    public void anInsetMeasuredBeforeTheGameStartsIsNotLost() {
        ScreenManager screens = screens();
        AndroidHost host = host();
        host.reportSafeInsetsPx(0, 74, 0, 0);
        host.observeScreens(screens);

        LayoutMetrics metrics = screens.metrics();
        assertEquals("a 74-pixel gesture bar is 29 logical rows", GESTURE_BAR_LOGICAL,
                metrics.insets().bottom());
        assertEquals("the navigation lifts clear of the gesture bar",
                NAV_TOP_NO_INSETS - GESTURE_BAR_LOGICAL, metrics.navTop());
    }

    @Test
    public void anInsetReportedWhileTheGameRunsMovesTheNavigationAtOnce() {
        ScreenManager screens = screens();
        AndroidHost host = host();
        host.observeScreens(screens);
        assertEquals("no inset yet", NAV_TOP_NO_INSETS, screens.metrics().navTop());

        host.reportSafeInsetsPx(0, 74, 0, 0);
        assertEquals(NAV_TOP_NO_INSETS - GESTURE_BAR_LOGICAL, screens.metrics().navTop());
    }

    @Test
    public void theCutoutPushesTheHeaderDownAndTheGestureBarLiftsTheNavigation() {
        ScreenManager screens = screens();
        AndroidHost host = host();
        host.observeScreens(screens);
        host.reportSafeInsetsPx(60, 74, 0, 0);

        LayoutMetrics metrics = screens.metrics();
        assertEquals("a 60-pixel cutout is 23 logical rows", 23, metrics.insets().top());
        assertEquals(GESTURE_BAR_LOGICAL, metrics.insets().bottom());
        assertEquals("the header starts below the cutout", -147 + 23, metrics.contentTop());
        assertEquals(NAV_TOP_NO_INSETS - GESTURE_BAR_LOGICAL, metrics.navTop());
        assertEquals(metrics.navTop() - metrics.contentTop(), metrics.aboveNavHeight());
    }

    @Test
    public void aPhoneThatReportsNothingKeepsTheWholeBand() {
        ScreenManager screens = screens();
        AndroidHost host = host();
        host.observeScreens(screens);
        host.reportSafeInsetsPx(0, 0, 0, 0);

        LayoutMetrics metrics = screens.metrics();
        assertEquals(0, metrics.insets().top());
        assertEquals(0, metrics.insets().bottom());
        assertEquals("the band is the whole visible surface", NAV_TOP_NO_INSETS,
                metrics.navTop());
    }

    @Test
    public void negativeMeasurementsAreClampedRatherThanTrustingThem() {
        ScreenManager screens = screens();
        AndroidHost host = host();
        host.observeScreens(screens);
        host.reportSafeInsetsPx(-40, -40, -40, -40);

        assertEquals("an inset that claims there is room where the platform took it away is "
                + "worse than one that says nothing", NAV_TOP_NO_INSETS,
                screens.metrics().navTop());
    }

    private static ScreenManager screens() {
        return new ScreenManager(new Viewport(PHONE_W, PHONE_H, false));
    }

    private static AndroidHost host() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        return new AndroidHost(activity, new GameSurfaceView(activity));
    }
}
