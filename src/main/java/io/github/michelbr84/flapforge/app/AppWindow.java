package io.github.michelbr84.flapforge.app;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The surface the game draws into and reads its pixel size from, as {@link GameApplication}
 * sees it (M10, D8): a host-neutral view of {@code GameWindow} on the desktop and of the Android
 * view on the phone.
 *
 * <p>The application needs exactly four things from a window: the canvas size to seed the
 * loop-owned viewport with, the icons to install before the loop starts (D4), and a way to let
 * go of it at the end of the shutdown sequence. Everything else a window does — focus, buffer
 * strategy, fullscreen handshake — is reached through the {@link FramePresenter} and the
 * {@link InputBridge} the {@link GameHost} builds around it, so a host that has no notion of
 * one of those (there is no title bar to iconify on Android) simply answers what it can.
 */
public interface AppWindow {

    /**
     * Current canvas width in window pixels.
     *
     * @return pixels
     */
    int canvasWidth();

    /**
     * Current canvas height in window pixels.
     *
     * @return pixels
     */
    int canvasHeight();

    /**
     * Installs the window icons (D4: done before the loop starts). A host without window icons
     * ignores the call.
     *
     * @param icons the icon images, in any order
     */
    void setIcons(List<? extends BufferedImage> icons);

    /** Releases the window; the last step of the shutdown sequence after the loop ended. */
    void dispose();
}
