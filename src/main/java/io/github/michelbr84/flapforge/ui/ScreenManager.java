package io.github.michelbr84.flapforge.ui;

import io.github.michelbr84.flapforge.app.FramePresenter;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Stack of {@link Screen}s driven by the game loop (D17, E30.a).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>tick only the top screen (overlays swallow input) and apply push/pop requests between
 *       ticks, never in the middle of one;</li>
 *   <li>after a transition, strip key edges from the first frame and ignore {@code CONFIRM}
 *       presses for {@value #TRANSITION_GRACE_TICKS} ticks so a key held across screens does not
 *       trigger twice;</li>
 *   <li>map mouse coordinates from window to logical space through the loop-owned
 *       {@link Viewport};</li>
 *   <li>handle window events: {@code Resized} updates the viewport and the presenter,
 *       {@code FullscreenToggled} or the {@code FULLSCREEN} action toggles fullscreen through the
 *       presenter (which is the source of truth for the current state, so a window started with
 *       {@code --fullscreen} leaves it on the first toggle), {@code CloseRequested} asks the
 *       loop to stop;</li>
 *   <li>render the topmost full screen and every overlay above it, without allocating.</li>
 * </ul>
 * It implements {@link FrameRenderer} so a presenter can draw the stack directly; the debug
 * overlay toggle ({@code F3}) is tracked here and read by whoever renders it. The stack is a
 * list whose last element is the top.
 */
public final class ScreenManager implements FrameRenderer {

    /** Ticks after a transition during which {@code CONFIRM} presses are ignored. */
    public static final int TRANSITION_GRACE_TICKS = 9;
    /**
     * Ticks after a fullscreen request during which a focus loss is attributed to the handshake.
     *
     * <p>Entering or leaving borderless fullscreen disposes the frame, re-creates it undecorated
     * (or decorated) and shows it again, so the toolkit delivers a {@code FocusLost} the player
     * never asked for, a few ticks after the toggle. {@link #isFullscreenHandshake()} is true for
     * this window so a running game does not pause on it (D2).
     */
    public static final int FULLSCREEN_GRACE_TICKS = 45;

    private final Viewport viewport;
    private final List<Screen> stack = new ArrayList<>();
    private final List<Runnable> pendingOps = new ArrayList<>();
    private FramePresenter presenter;
    private Runnable closeHandler;
    private boolean fullscreen;
    private boolean iconified;
    private boolean debugOverlay;
    private boolean closeRequested;
    private boolean accumulatorResetRequested;
    private int fullscreenGraceTicks;
    private int graceTicks;
    private boolean stripEdgesNextTick;
    private long tickCount;
    private double mouseX;
    private double mouseY;
    private int letterboxRgb = 0x0e1116;

    /**
     * Creates a manager mapping input through the given viewport.
     *
     * @param viewport the loop-owned viewport
     */
    public ScreenManager(Viewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
    }

    /**
     * Installs the presenter that receives resize and fullscreen requests.
     *
     * @param presenter the presenter, or {@code null} for none
     */
    public void setPresenter(FramePresenter presenter) {
        this.presenter = presenter;
    }

    /**
     * Installs the callback run when the window asks to close (normally {@code loop::stop}).
     *
     * @param closeHandler the callback, or {@code null}
     */
    public void setCloseHandler(Runnable closeHandler) {
        this.closeHandler = closeHandler;
    }

    /**
     * Sets the letterbox colour reported to the presenter.
     *
     * @param rgb the colour as {@code 0xRRGGBB}
     */
    public void setLetterboxRgb(int rgb) {
        this.letterboxRgb = rgb;
    }

    @Override
    public int letterboxRgb() {
        return letterboxRgb;
    }

    /**
     * The loop-owned viewport.
     *
     * @return the viewport
     */
    public Viewport viewport() {
        return viewport;
    }

    /**
     * Requests a push; applied before the next tick (or immediately when no tick is running).
     *
     * @param screen the screen
     */
    public void push(Screen screen) {
        Objects.requireNonNull(screen, "screen");
        pendingOps.add(() -> doPush(screen));
    }

    /** Requests a pop of the top screen; applied before the next tick. */
    public void pop() {
        pendingOps.add(this::doPop);
    }

    /**
     * Requests replacing the top screen; applied before the next tick.
     *
     * @param screen the replacement
     */
    public void replace(Screen screen) {
        Objects.requireNonNull(screen, "screen");
        pendingOps.add(() -> {
            doPop();
            doPush(screen);
        });
    }

    /**
     * Requests clearing the stack and pushing a single root screen; applied before the next tick.
     *
     * @param screen the new root
     */
    public void setRoot(Screen screen) {
        Objects.requireNonNull(screen, "screen");
        pendingOps.add(() -> {
            while (!stack.isEmpty()) {
                doPop();
            }
            doPush(screen);
        });
    }

    /** Applies queued push/pop requests now. The loop calls this between ticks. */
    public void applyPending() {
        if (pendingOps.isEmpty()) {
            return;
        }
        List<Runnable> ops = new ArrayList<>(pendingOps);
        pendingOps.clear();
        for (Runnable op : ops) {
            op.run();
        }
    }

    private void doPush(Screen screen) {
        stack.add(screen);
        screen.onEnter();
        markTransition();
    }

    private void doPop() {
        if (stack.isEmpty()) {
            return;
        }
        Screen top = stack.remove(stack.size() - 1);
        top.onExit();
        markTransition();
    }

    private void markTransition() {
        graceTicks = TRANSITION_GRACE_TICKS;
        stripEdgesNextTick = true;
    }

    /**
     * Top of the stack.
     *
     * @return the top screen, or {@code null} when empty
     */
    public Screen top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /**
     * Number of screens on the stack.
     *
     * @return the count
     */
    public int depth() {
        return stack.size();
    }

    /**
     * Tells whether the stack is empty (after pending operations are applied).
     *
     * @return {@code true} when nothing is on the stack
     */
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    /**
     * Snapshot of the stack from bottom to top.
     *
     * @return a copy
     */
    public List<Screen> screens() {
        return new ArrayList<>(stack);
    }

    /** Asks the application to quit (same path as the window close button). */
    public void requestClose() {
        if (closeRequested) {
            return;
        }
        closeRequested = true;
        if (closeHandler != null) {
            closeHandler.run();
        }
    }

    /**
     * Tells whether a close was requested.
     *
     * @return {@code true} once {@link #requestClose()} ran or a close event was drained
     */
    public boolean isCloseRequested() {
        return closeRequested;
    }

    /**
     * Asks the game loop to discard the time in its accumulator before the next frame.
     *
     * <p>Used when a run resumes from a pause: the loop may have been starved while the window
     * was unfocused or minimised, and without the reset the first frame back would run a burst of
     * catch-up ticks and kill the player (D2). The flag is consumed once, by
     * {@link #consumeAccumulatorReset()}.
     */
    public void requestAccumulatorReset() {
        accumulatorResetRequested = true;
    }

    /**
     * Reads and clears the accumulator-reset request. The game loop calls it once per frame.
     *
     * @return {@code true} when a reset was requested since the last call
     */
    public boolean consumeAccumulatorReset() {
        boolean requested = accumulatorResetRequested;
        accumulatorResetRequested = false;
        return requested;
    }

    /**
     * Whether a fullscreen handshake is still settling.
     *
     * @return {@code true} for {@value #FULLSCREEN_GRACE_TICKS} ticks after a fullscreen request
     * @see #FULLSCREEN_GRACE_TICKS
     */
    public boolean isFullscreenHandshake() {
        return fullscreenGraceTicks > 0;
    }

    /**
     * Requests a fullscreen state change through the presenter.
     *
     * @param fullscreen the desired state
     */
    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        fullscreenGraceTicks = FULLSCREEN_GRACE_TICKS;
        if (presenter != null) {
            presenter.setFullscreen(fullscreen);
        }
    }

    /** Toggles fullscreen, reading the current state from the presenter. */
    public void toggleFullscreen() {
        setFullscreen(!isFullscreen());
    }

    /**
     * Current fullscreen state: the presenter's when one is installed (it owns the window),
     * otherwise the last state requested through this manager.
     *
     * @return {@code true} when fullscreen
     */
    public boolean isFullscreen() {
        return presenter != null ? presenter.isFullscreen() : fullscreen;
    }

    /**
     * Whether the window is currently iconified (from the last drained event).
     *
     * @return {@code true} when minimised
     */
    public boolean isIconified() {
        return iconified;
    }

    /**
     * Whether the debug overlay is toggled on ({@code F3}).
     *
     * @return {@code true} when visible
     */
    public boolean isDebugOverlayVisible() {
        return debugOverlay;
    }

    /**
     * Sets the debug overlay flag.
     *
     * @param visible the new state
     */
    public void setDebugOverlayVisible(boolean visible) {
        this.debugOverlay = visible;
    }

    /**
     * Number of ticks processed so far.
     *
     * @return the count
     */
    public long tickCount() {
        return tickCount;
    }

    /**
     * Pointer x in logical coordinates as of the last tick (read by the debug overlay).
     *
     * @return the x
     */
    public double mouseX() {
        return mouseX;
    }

    /**
     * Pointer y in logical coordinates as of the last tick (read by the debug overlay).
     *
     * @return the y
     */
    public double mouseY() {
        return mouseY;
    }

    /**
     * Advances the stack by one tick with the given raw-coordinate frame.
     *
     * @param frame the frame from the input queue (mouse in window pixels)
     */
    public void tick(InputFrame frame) {
        tickCount++;
        if (fullscreenGraceTicks > 0) {
            fullscreenGraceTicks--;
        }
        applyPending();
        handleSystemEvents(frame);

        InputFrame mapped = mapMouse(frame);
        mouseX = mapped.mouseX();
        mouseY = mapped.mouseY();
        if (mapped.isJustPressed(InputAction.FULLSCREEN)) {
            toggleFullscreen();
        }
        if (mapped.isJustPressed(InputAction.DEBUG)) {
            debugOverlay = !debugOverlay;
        }
        if (stripEdgesNextTick) {
            stripEdgesNextTick = false;
            mapped = mapped.withoutKeyEdges();
        }
        if (graceTicks > 0) {
            graceTicks--;
            mapped = mapped.withoutPresses(EnumSet.of(InputAction.CONFIRM));
        }

        Screen top = top();
        if (top != null) {
            top.tick(mapped);
        }
        applyPending();
    }

    private void handleSystemEvents(InputFrame frame) {
        for (RawInput.SystemEvent event : frame.systemEvents()) {
            if (event instanceof RawInput.Resized r) {
                viewport.resize(r.width(), r.height());
                if (presenter != null) {
                    presenter.onResize(r.width(), r.height());
                }
            } else if (event instanceof RawInput.FullscreenToggled) {
                toggleFullscreen();
            } else if (event instanceof RawInput.CloseRequested) {
                requestClose();
            } else if (event instanceof RawInput.Iconified ic) {
                iconified = ic.iconified();
            }
        }
    }

    private InputFrame mapMouse(InputFrame frame) {
        Vec2 logical = viewport.toLogical(frame.mouseX(), frame.mouseY());
        return frame.withMouse(logical.x(), logical.y());
    }

    /**
     * Renders the topmost full screen and the overlays above it.
     *
     * @param g the graphics context in logical coordinates
     * @param alpha interpolation factor
     */
    @Override
    public void render(Graphics2D g, double alpha) {
        int size = stack.size();
        int first = size - 1;
        while (first > 0 && stack.get(first).isOverlay()) {
            first--;
        }
        for (int i = Math.max(first, 0); i < size; i++) {
            stack.get(i).render(g, alpha);
        }
    }
}
