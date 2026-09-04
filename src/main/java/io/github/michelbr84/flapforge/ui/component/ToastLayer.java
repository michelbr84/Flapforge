package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * The queue of transient messages drawn in the top-right corner (D16, E31.d).
 *
 * <p>Screens that draw a centred title pass its height to {@link #render(Graphics2D, int)} so a
 * long translated title is never covered.
 *
 * <p>{@link Toast} is one entry; this is the queue and the renderer. At most
 * {@value #MAX_VISIBLE} toasts are on screen at a time and at most {@value #MAX_QUEUED} wait
 * behind them — a burst of events (a settings reset, a failed write, a language switch) must
 * never turn into a wall of text or an unbounded list. Everything is driven in ticks by
 * {@link #tick()}, so the layer is deterministic and testable headlessly.
 *
 * <p>The layer is shared by the screens through the application context, so a message pushed on
 * one screen is still readable after the player moved to the next one.
 */
public final class ToastLayer {

    /** How many toasts are shown at once. */
    public static final int MAX_VISIBLE = 3;
    /** How many toasts may wait behind the visible ones. */
    public static final int MAX_QUEUED = 8;
    /** Height of one toast. */
    public static final int TOAST_HEIGHT = 28;
    /** Gap between two toasts. */
    public static final int TOAST_GAP = 6;
    /** Distance from the right edge of the playfield. */
    public static final int MARGIN_X = 10;
    /** Distance from the top edge of the playfield. */
    public static final int MARGIN_Y = 10;
    /** Horizontal padding inside a toast. */
    public static final int PADDING_X = 12;
    /** Point size of the toast text. */
    public static final int FONT_SIZE = 13;

    private static final Color INFO_BG = new Color(0x16, 0x2B, 0x2E);
    private static final Color WARNING_BG = new Color(0x5A, 0x2C, 0x1E);
    private static final Color BORDER = new Color(0x6F, 0xD1, 0xA8);
    private static final Color WARNING_BORDER = new Color(0xE0, 0x8A, 0x5A);

    private final List<Toast> visible = new ArrayList<>();
    private final Deque<Toast> queued = new ArrayDeque<>();
    private long pushed;

    /** Creates an empty layer. */
    public ToastLayer() {
    }

    /**
     * Queues an informational message.
     *
     * @param text the message, already localised
     * @return the toast (queued, possibly not visible yet)
     */
    public Toast push(String text) {
        return push(new Toast(text));
    }

    /**
     * Queues a message.
     *
     * @param text the message, already localised
     * @param kind what it is about
     * @return the toast
     */
    public Toast push(String text, Toast.Kind kind) {
        return push(new Toast(text, kind, Toast.DEFAULT_TICKS));
    }

    /**
     * Queues a toast, dropping the oldest waiting one when the queue is full.
     *
     * @param toast the toast
     * @return the same toast
     */
    public Toast push(Toast toast) {
        Objects.requireNonNull(toast, "toast");
        pushed++;
        if (visible.size() < MAX_VISIBLE) {
            visible.add(toast);
            return toast;
        }
        while (queued.size() >= MAX_QUEUED) {
            queued.pollFirst();
        }
        queued.addLast(toast);
        // Hurry the oldest visible one along so a queued message is not stuck behind a full
        // three-second hold.
        visible.get(0).expireSoon();
        return toast;
    }

    /** Advances every visible toast and promotes waiting ones. */
    public void tick() {
        for (int i = visible.size() - 1; i >= 0; i--) {
            Toast toast = visible.get(i);
            toast.tick();
            if (toast.isExpired()) {
                visible.remove(i);
            }
        }
        while (visible.size() < MAX_VISIBLE && !queued.isEmpty()) {
            visible.add(queued.pollFirst());
        }
    }

    /** Drops everything immediately. */
    public void clear() {
        visible.clear();
        queued.clear();
    }

    /**
     * The toasts currently on screen, oldest first.
     *
     * @return a snapshot
     */
    public List<Toast> visibleToasts() {
        return List.copyOf(visible);
    }

    /**
     * How many toasts are on screen.
     *
     * @return the count
     */
    public int visibleCount() {
        return visible.size();
    }

    /**
     * How many toasts are waiting.
     *
     * @return the count
     */
    public int queuedCount() {
        return queued.size();
    }

    /**
     * How many toasts were pushed since the layer was created (tests).
     *
     * @return the count
     */
    public long pushedCount() {
        return pushed;
    }

    /**
     * Whether anything is on screen.
     *
     * @return {@code true} when nothing is visible
     */
    public boolean isEmpty() {
        return visible.isEmpty();
    }

    /**
     * Draws the visible toasts stacked down from the top-right corner.
     *
     * @param g the context in logical coordinates
     */
    public void render(Graphics2D g) {
        render(g, 0);
    }

    /**
     * Draws the visible toasts stacked down from the top-right corner, below a band the caller
     * keeps for itself.
     *
     * <p>The playfield is only {@value io.github.michelbr84.flapforge.core.Playfield#WIDTH} px
     * wide and screen titles are centred, so a wide title — every one of them in a language with
     * longer words than English — reaches the right edge and a toast pinned to the corner covers
     * it. A screen that owns the top band passes its height here; the toasts then overlay the
     * screen's own content, which reads as a notification, instead of its title, which reads as
     * a rendering bug.
     *
     * @param g       the context in logical coordinates
     * @param topInset height of the band to keep free, in logical pixels
     */
    public void render(Graphics2D g, int topInset) {
        if (visible.isEmpty()) {
            return;
        }
        g.setFont(Fonts.regular(FONT_SIZE));
        int y = MARGIN_Y + Math.max(0, topInset);
        for (int i = 0; i < visible.size(); i++) {
            Toast toast = visible.get(i);
            double alpha = toast.alpha();
            if (alpha <= 0.01) {
                y += TOAST_HEIGHT + TOAST_GAP;
                continue;
            }
            int textWidth = TextPainter.width(g, toast.text());
            int w = Math.min(Playfield.WIDTH - 2 * MARGIN_X, textWidth + 2 * PADDING_X);
            int x = Playfield.WIDTH - MARGIN_X - w;
            int a = (int) Math.round(alpha * 255);
            Color bg = toast.kind() == Toast.Kind.WARNING ? WARNING_BG : INFO_BG;
            Color border = toast.kind() == Toast.Kind.WARNING ? WARNING_BORDER : BORDER;
            g.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
                    (int) Math.round(a * 0.92)));
            g.fillRoundRect(x, y, w, TOAST_HEIGHT, 10, 10);
            g.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), a));
            g.drawRoundRect(x, y, w, TOAST_HEIGHT, 10, 10);
            g.setColor(new Color(ProceduralArt.TEXT_LIGHT.getRed(),
                    ProceduralArt.TEXT_LIGHT.getGreen(), ProceduralArt.TEXT_LIGHT.getBlue(), a));
            TextPainter.draw(g, toast.text(), x + w - PADDING_X,
                    TextPainter.centeredBaseline(g, y + TOAST_HEIGHT / 2.0), Align.RIGHT);
            y += TOAST_HEIGHT + TOAST_GAP;
        }
    }

    @Override
    public String toString() {
        return "ToastLayer[visible=" + visible.size() + " queued=" + queued.size() + "]";
    }
}
