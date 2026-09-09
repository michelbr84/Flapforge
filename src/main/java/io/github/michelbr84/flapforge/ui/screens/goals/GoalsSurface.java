package io.github.michelbr84.flapforge.ui.screens.goals;

import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import java.util.Objects;

/**
 * The vertical surface the Goals section lays itself out in: the whole visible logical band,
 * not the fixed 420x640 playfield. A thin adapter over the hub-wide layout metrics: the screen
 * builds one of these from {@code LayoutMetrics} and reads the header row from
 * {@link #contentTop()}, the navigation band from {@link #navTop()} and the elastic content
 * panel from {@link #contentHeight()}, so the Goals section and the rest of the hub can never
 * disagree about where the band is — least of all about the safe area, which only the metrics
 * know.
 *
 * <p>The band is already clamped by the time it reaches here: never shorter than the playfield,
 * never taller than {@link LayoutMetrics#MAX_H}. On a 1080x2400 phone it runs from about
 * {@code -147} to {@code 786} instead of {@code 0} to {@code 640}.
 */
public final class GoalsSurface {

    private final LayoutMetrics metrics;

    private GoalsSurface(LayoutMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * The surface of the shared hub metrics: the band the responsive layout publishes. This is
     * the factory a screen uses once it can reach a {@code ScreenManager}, so the Goals
     * section reads the same band every other section does.
     *
     * @param metrics the metrics of the current surface
     * @return the surface
     */
    public static GoalsSurface of(LayoutMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics");
        return new GoalsSurface(metrics);
    }

    /**
     * The surface width: the design width, whose columns the x-clipping already fixes.
     *
     * @return logical pixels
     */
    public int width() {
        return metrics.width();
    }

    /**
     * The first logical row the surface covers — a negative value when the visible band
     * reaches above the playfield. Above the safe area: a screen draws its background here
     * and its header no higher than {@link #contentTop()}.
     *
     * @return logical pixels
     */
    public int top() {
        return metrics.top();
    }

    /**
     * The height of the covered band, after the clamp. The screen scales its row heights
     * against this, not against the playfield, so a taller surface grows its rows.
     *
     * @return logical pixels
     */
    public int height() {
        return metrics.height();
    }

    /**
     * The last logical row the surface covers: the safe area's bottom edge.
     *
     * @return logical pixels
     */
    public int bottom() {
        return metrics.contentBottom();
    }

    /**
     * The first row the screen's own content may occupy: the header pins here, clear of the
     * cutout.
     *
     * @return logical pixels
     */
    public int contentTop() {
        return metrics.contentTop();
    }

    /**
     * The height of the navigation band at the bottom of the surface.
     *
     * @return logical pixels
     */
    public int navHeight() {
        return metrics.navHeight();
    }

    /**
     * The first row of the navigation band: pinned to the bottom of the usable area, so the
     * band clears the gesture bar instead of sitting under it.
     *
     * @return logical pixels
     */
    public int navTop() {
        return metrics.navTop();
    }

    /**
     * The last row of the content area, just above the navigation band.
     *
     * @return logical pixels
     */
    public int contentBottom() {
        return metrics.navTop();
    }

    /**
     * The rows the content panel may span when nothing else takes them.
     *
     * @return logical pixels
     */
    public int contentHeight() {
        return metrics.aboveNavHeight();
    }
}
