package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.RawInput;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Frame;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Objects;

/**
 * Translates toolkit events into {@link RawInput} records on the event-dispatch thread and
 * pushes them into the {@link InputQueue} (D2, E30.a, E30.e): the desktop {@link InputBridge},
 * built by {@link AwtHost} (M10).
 *
 * <p>Keys are captured with a {@link KeyEventDispatcher} on the keyboard focus manager so no
 * component-level focus quirk can swallow them, and pass through a {@link KeyRepeatFilter};
 * mouse, component and focus listeners sit on the canvas and the window listener on the frame.
 * Coordinates are canvas pixels. {@link #attach(AppWindow)} ends by queueing a synthetic
 * {@code Resized} with the current canvas size, so the first tick reconciles the loop-owned
 * viewport with whatever the window manager did between window creation and attachment.
 * {@code FocusLost} carries a wall-clock stamp comparable with {@link KeyEvent#getWhen()} so
 * the queue can recognise the resumed auto-repeat of a key still held when focus returns.
 */
public final class AwtInputBridge implements KeyEventDispatcher, InputBridge {

    private final InputQueue queue;
    private final KeyRepeatFilter keys;
    private GameWindow window;
    private boolean attached;

    private final MouseAdapter mouseHandler = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            if (window != null) {
                window.canvas().requestFocusInWindow();
            }
            queue.offer(new RawInput.MouseDown(e.getButton(), e.getX(), e.getY()));
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            queue.offer(new RawInput.MouseUp(e.getButton(), e.getX(), e.getY()));
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            queue.offer(new RawInput.MouseMove(e.getX(), e.getY()));
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            queue.offer(new RawInput.MouseMove(e.getX(), e.getY()));
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            queue.offer(new RawInput.Wheel(e.getWheelRotation()));
        }
    };

    private final ComponentListener componentHandler = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            Component c = e.getComponent();
            queue.offer(new RawInput.Resized(c.getWidth(), c.getHeight()));
        }
    };

    private final FocusListener focusHandler = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            keys.flush();
            queue.offer(new RawInput.FocusLost(System.currentTimeMillis()));
        }
    };

    private final WindowListener windowHandler = new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
            queue.offer(new RawInput.CloseRequested());
        }

        @Override
        public void windowIconified(WindowEvent e) {
            if (window != null) {
                window.setIconified(true);
            }
            queue.offer(new RawInput.Iconified(true));
        }

        @Override
        public void windowDeiconified(WindowEvent e) {
            if (window != null) {
                window.setIconified(false);
            }
            queue.offer(new RawInput.Iconified(false));
        }
    };

    /**
     * Creates a bridge feeding the given queue.
     *
     * @param queue the queue
     */
    public AwtInputBridge(InputQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.keys = new KeyRepeatFilter(queue);
    }

    /**
     * Registers every listener on the window (on the event-dispatch thread) and queues a
     * {@code Resized} with the current canvas size.
     *
     * @param window the window; it must be the {@link GameWindow} the desktop host created,
     *     because the listeners go on its canvas and its frame
     * @throws IllegalArgumentException when the window is not a {@link GameWindow}
     */
    @Override
    public void attach(AppWindow window) {
        Objects.requireNonNull(window, "window");
        if (!(window instanceof GameWindow gameWindow)) {
            throw new IllegalArgumentException("AwtInputBridge listens to a GameWindow, not "
                    + window.getClass().getName());
        }
        GameWindow.onEdt(() -> {
            if (attached) {
                return;
            }
            this.window = gameWindow;
            Canvas canvas = gameWindow.canvas();
            Frame frame = gameWindow.frame();
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
            canvas.addMouseListener(mouseHandler);
            canvas.addMouseMotionListener(mouseHandler);
            canvas.addMouseWheelListener(mouseHandler);
            canvas.addComponentListener(componentHandler);
            canvas.addFocusListener(focusHandler);
            frame.addWindowListener(windowHandler);
            attached = true;
            queue.offer(new RawInput.Resized(canvas.getWidth(), canvas.getHeight()));
        });
    }

    /** Removes every listener (on the event-dispatch thread). */
    @Override
    public void detach() {
        GameWindow.onEdt(() -> {
            if (!attached) {
                return;
            }
            Canvas canvas = window.canvas();
            Frame frame = window.frame();
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
            canvas.removeMouseListener(mouseHandler);
            canvas.removeMouseMotionListener(mouseHandler);
            canvas.removeMouseWheelListener(mouseHandler);
            canvas.removeComponentListener(componentHandler);
            canvas.removeFocusListener(focusHandler);
            frame.removeWindowListener(windowHandler);
            keys.flush();
            attached = false;
            window = null;
        });
    }

    /**
     * Whether {@link #attach(AppWindow)} succeeded and {@link #detach()} was not called.
     *
     * @return {@code true} when attached
     */
    @Override
    public boolean isAttached() {
        return attached;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!attached || !belongsToWindow(e.getComponent())) {
            return false;
        }
        return keys.accept(e);
    }

    private boolean belongsToWindow(Component component) {
        GameWindow w = window;
        if (w == null) {
            return false;
        }
        Component c = component;
        while (c != null) {
            if (c == w.frame()) {
                return true;
            }
            c = c.getParent();
        }
        return w.frame().isFocused();
    }
}
