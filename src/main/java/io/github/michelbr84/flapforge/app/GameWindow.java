package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.core.Playfield;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.BufferStrategy;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;

/**
 * The game window: a plain {@link Frame} with a single {@link Canvas} child (D4, E30.i).
 *
 * <p>Both components ignore repaints; the canvas is focusable with focus traversal keys disabled
 * and is sized to the logical playfield times an integer scale. Everything that touches the
 * toolkit runs on the event-dispatch thread through {@code invokeAndWait}. The fullscreen
 * handshake saves the windowed bounds and decoration state, disposes the peer, toggles
 * decorations, re-shows the frame and recreates the buffer strategy; leaving fullscreen restores
 * the saved state with {@code setUndecorated(false)} and {@code pack()}.
 */
public final class GameWindow {

    /**
     * Vertical room reserved for the title bar and borders when choosing the default scale.
     * Decorations are unknown before the frame is shown; 48 px covers every mainstream window
     * manager (GNOME/KDE about 37 px, Windows 10/11 about 39 px, macOS 28 px).
     */
    public static final int DECORATION_ALLOWANCE_PX = 48;

    private final Frame frame;
    private final Canvas canvas;
    private volatile boolean iconified;
    private volatile boolean fullscreen;
    private Rectangle windowedBounds;
    private Dimension windowedCanvasSize;
    private boolean windowedDecorated = true;
    private int windowedExtendedState = Frame.NORMAL;
    private Dimension appliedMinimumSize;

    /**
     * Keeps the minimum size at {@code 420x640 + insets} using the insets the window manager
     * actually reports. The insets known at {@code pack()} time are only a guess; a minimum
     * computed from that guess (for example 37 px too tall) is enforced by the manager after the
     * real insets arrive and permanently inflates the client area.
     */
    private final ComponentListener minimumSizeTracker = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            updateMinimumSize();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
            updateMinimumSize();
        }

        @Override
        public void componentShown(ComponentEvent e) {
            updateMinimumSize();
        }
    };

    private GameWindow(Frame frame, Canvas canvas) {
        this.frame = frame;
        this.canvas = canvas;
    }

    private void updateMinimumSize() {
        if (!frame.isShowing() || frame.isUndecorated() && fullscreen) {
            return;
        }
        Insets insets = frame.getInsets();
        Dimension min = new Dimension(Playfield.WIDTH + insets.left + insets.right,
                Playfield.HEIGHT + insets.top + insets.bottom);
        if (!min.equals(appliedMinimumSize)) {
            appliedMinimumSize = min;
            frame.setMinimumSize(min);
        }
    }

    /**
     * Largest integer scale whose decorated window fits the usable screen height (the display
     * minus taskbars, as reported by {@code getMaximumWindowBounds()}), never below 1.
     *
     * @return the scale
     * @throws java.awt.HeadlessException without a display
     */
    public static int defaultScale() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        return scaleFor(bounds.height);
    }

    /**
     * Largest integer scale {@code s} with {@code 640 * s + DECORATION_ALLOWANCE_PX} not above
     * the given usable height, never below 1 (a 1408 px usable height gives 2, a 1040 px one
     * gives 1).
     *
     * @param usableHeight the usable screen height in pixels
     * @return the scale
     */
    public static int scaleFor(int usableHeight) {
        int scale = (usableHeight - DECORATION_ALLOWANCE_PX) / Playfield.HEIGHT;
        return Math.max(1, scale);
    }

    /**
     * Creates, packs and shows the window on the event-dispatch thread.
     *
     * @param title the window title
     * @param scale the integer scale of the initial canvas size
     * @param startFullscreen whether to enter fullscreen right away
     * @return the shown window with a buffer strategy created
     */
    public static GameWindow create(String title, int scale, boolean startFullscreen) {
        GameWindow[] holder = new GameWindow[1];
        onEdt(() -> {
            Frame frame = new Frame(title);
            frame.setIgnoreRepaint(true);
            frame.setLayout(new BorderLayout());
            frame.setBackground(Color.BLACK);
            frame.setFocusTraversalKeysEnabled(false);
            frame.setResizable(true);

            Canvas canvas = new Canvas();
            canvas.setIgnoreRepaint(true);
            canvas.setFocusable(true);
            canvas.setFocusTraversalKeysEnabled(false);
            canvas.setBackground(Color.BLACK);
            canvas.setPreferredSize(new Dimension(Playfield.WIDTH * scale, Playfield.HEIGHT * scale));
            frame.add(canvas, BorderLayout.CENTER);
            frame.pack();

            GameWindow window = new GameWindow(frame, canvas);
            frame.addComponentListener(window.minimumSizeTracker);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            canvas.createBufferStrategy(2);
            canvas.requestFocusInWindow();
            if (startFullscreen) {
                window.applyFullscreen(true);
            }
            holder[0] = window;
        });
        return holder[0];
    }

    /**
     * The frame.
     *
     * @return the frame
     */
    public Frame frame() {
        return frame;
    }

    /**
     * The canvas that receives all drawing and input.
     *
     * @return the canvas
     */
    public Canvas canvas() {
        return canvas;
    }

    /**
     * Current canvas width in window pixels.
     *
     * @return pixels
     */
    public int canvasWidth() {
        return canvas.getWidth();
    }

    /**
     * Current canvas height in window pixels.
     *
     * @return pixels
     */
    public int canvasHeight() {
        return canvas.getHeight();
    }

    /**
     * Sets the window icons on the event-dispatch thread and waits (D4: done before the loop
     * starts).
     *
     * @param icons the icon images, largest last or in any order
     */
    public void setIcons(List<? extends Image> icons) {
        List<Image> copy = List.copyOf(icons);
        onEdt(() -> frame.setIconImages(copy));
    }

    /**
     * Whether the window is iconified (updated by the input bridge from window events).
     *
     * @return {@code true} when minimised
     */
    public boolean isIconified() {
        return iconified;
    }

    /**
     * Records the iconified state.
     *
     * @param iconified the new state
     */
    public void setIconified(boolean iconified) {
        this.iconified = iconified;
    }

    /**
     * Whether the window is in borderless fullscreen.
     *
     * @return {@code true} when fullscreen
     */
    public boolean isFullscreen() {
        return fullscreen;
    }

    /**
     * Enters or leaves borderless fullscreen. Blocks the calling (loop) thread until the
     * event-dispatch thread has rebuilt the window; the caller must have suspended rendering.
     *
     * @param fullscreen the desired state
     */
    public void setFullscreen(boolean fullscreen) {
        onEdt(() -> applyFullscreen(fullscreen));
    }

    private void applyFullscreen(boolean enter) {
        if (enter == fullscreen) {
            return;
        }
        BufferStrategy old = canvas.getBufferStrategy();
        if (old != null) {
            old.dispose();
        }
        fullscreen = enter;
        if (enter) {
            windowedBounds = frame.getBounds();
            windowedCanvasSize = canvas.getSize();
            windowedDecorated = !frame.isUndecorated();
            windowedExtendedState = frame.getExtendedState();
            GraphicsConfiguration gc = frame.getGraphicsConfiguration();
            Rectangle device = gc != null ? gc.getBounds()
                    : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                            .getDefaultConfiguration().getBounds();
            frame.dispose();
            frame.setUndecorated(true);
            frame.setExtendedState(Frame.NORMAL);
            frame.setBounds(device);
            frame.setVisible(true);
        } else {
            frame.dispose();
            // Window managers may have maximised the screen-sized undecorated frame; the
            // recorded state would otherwise be re-applied when the peer is recreated.
            frame.setExtendedState(Frame.NORMAL);
            frame.setUndecorated(!windowedDecorated);
            if (windowedCanvasSize != null) {
                canvas.setPreferredSize(windowedCanvasSize);
            }
            // pack() sizes the frame from the canvas preferred size plus the current insets; an
            // explicit setSize() here races with the late inset update of the recreated peer.
            frame.pack();
            if (windowedBounds != null) {
                frame.setLocation(new Point(windowedBounds.x, windowedBounds.y));
            }
            frame.setVisible(true);
            if ((windowedExtendedState & Frame.MAXIMIZED_BOTH) != 0) {
                frame.setExtendedState(windowedExtendedState);
            }
        }
        canvas.createBufferStrategy(2);
        canvas.requestFocusInWindow();
        fullscreen = enter;
        updateMinimumSize();
    }

    /** Disposes the frame asynchronously on the event-dispatch thread. */
    public void dispose() {
        EventQueue.invokeLater(frame::dispose);
    }

    /** Disposes the frame and waits for the event-dispatch thread to finish doing so. */
    public void disposeAndWait() {
        onEdt(frame::dispose);
    }

    /**
     * Runs a task on the event-dispatch thread and waits for it; runs inline when already there.
     *
     * @param task the task
     */
    public static void onEdt(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (EventQueue.isDispatchThread()) {
            task.run();
            return;
        }
        try {
            EventQueue.invokeAndWait(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the event thread", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException("Event thread task failed", cause);
        }
    }
}
