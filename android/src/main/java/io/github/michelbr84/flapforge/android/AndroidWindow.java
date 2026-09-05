package io.github.michelbr84.flapforge.android;

import awt.image.BufferedImage;
import io.github.michelbr84.flapforge.app.AppWindow;
import java.util.List;
import java.util.Objects;

/**
 * The Android {@link AppWindow} (M10, P2): the {@link GameSurfaceView} seen through the four
 * things {@code GameApplication} needs from a window.
 *
 * <p>The canvas size is the surface size, which is greater than zero from the first
 * {@code surfaceChanged} on — and the game is only started after one, so the viewport the
 * application seeds from these two numbers is never empty. Icons and disposal have no Android
 * counterpart: the launcher icon is a manifest resource, and the view belongs to the activity,
 * which outlives the game's shutdown sequence and tears the view down itself.
 */
public final class AndroidWindow implements AppWindow {

    private final GameSurfaceView view;

    /**
     * Wraps the view.
     *
     * @param view the surface view the game draws into
     */
    public AndroidWindow(GameSurfaceView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    /**
     * The view behind this window, for the presenter and the input bridge the
     * {@link AndroidHost} builds around it.
     *
     * @return the view
     */
    public GameSurfaceView view() {
        return view;
    }

    @Override
    public int canvasWidth() {
        return view.surfaceWidth();
    }

    @Override
    public int canvasHeight() {
        return view.surfaceHeight();
    }

    /**
     * {@inheritDoc}
     *
     * <p>A no-op on Android: there is no title bar or task switcher image to install at run
     * time, the launcher icon is the {@code android:icon} resource of the manifest.
     */
    @Override
    public void setIcons(List<? extends BufferedImage> icons) {
        // Intentionally empty (see the javadoc).
    }

    /**
     * {@inheritDoc}
     *
     * <p>A no-op on Android: the activity owns the view and removes it with its own lifecycle,
     * after the loop thread that calls this has ended.
     */
    @Override
    public void dispose() {
        // Intentionally empty (see the javadoc).
    }
}
