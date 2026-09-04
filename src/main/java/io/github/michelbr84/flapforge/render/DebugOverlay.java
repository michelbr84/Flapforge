package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.Playfield;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The {@code F3} diagnostics layer (D17): wraps the frame renderer, samples the time between
 * presents and, when the source says it is visible, draws tps, fps, the last frame time, the
 * tick accumulator, a histogram of the last {@value #HISTORY} frame times, the screen stack and
 * the pointer in logical coordinates.
 *
 * <p>The overlay never touches the loop or the screen stack directly: the application hands it a
 * {@link Source} adapter, so the render package stays free of dependencies on {@code app} and
 * {@code ui}. Sampling runs every frame (cheap, allocation-free); drawing only when visible.
 */
public final class DebugOverlay implements FrameRenderer {

    /** Facts the overlay shows, provided by the application. */
    public interface Source {

        /**
         * Whether the overlay is toggled on.
         *
         * @return {@code true} to draw
         */
        boolean isVisible();

        /**
         * Simulation ticks run so far.
         *
         * @return the count
         */
        long tickCount();

        /**
         * Time waiting in the loop accumulator after the last frame.
         *
         * @return nanoseconds
         */
        long accumulatorNs();

        /**
         * Ticks run by the last frame.
         *
         * @return the count
         */
        int lastTicks();

        /**
         * Names of the screens on the stack, bottom first.
         *
         * @return the names
         */
        List<String> screenNames();

        /**
         * Pointer x in logical coordinates.
         *
         * @return the x
         */
        double mouseX();

        /**
         * Pointer y in logical coordinates.
         *
         * @return the y
         */
        double mouseY();
    }

    /** Number of frame times kept for the histogram. */
    public static final int HISTORY = 120;

    private static final long WINDOW_NS = 1_000_000_000L;
    private static final double BAR_FULL_NS = 2.0 * Playfield.TICK_NS;
    private static final int MARGIN = 8;
    private static final int PAD = 8;
    private static final int LINE_HEIGHT = 14;
    private static final int BAR_WIDTH = 2;
    private static final int HIST_HEIGHT = 40;
    private static final int PANEL_WIDTH = HISTORY * BAR_WIDTH + 2 * PAD;
    private static final int FONT_SIZE = 11;
    private static final Color BACK = new Color(0, 0, 0, 0xA8);
    private static final Color TEXT = new Color(0xE8F4F5);
    private static final Color BAR_GOOD = new Color(0x5FBF3A);
    private static final Color BAR_WARN = new Color(0xF5C542);
    private static final Color BAR_BAD = new Color(0xE8562A);
    private static final Color REFERENCE = new Color(0xFF, 0xFF, 0xFF, 0x80);

    private final FrameRenderer inner;
    private final LongSupplier nanos;
    private volatile Source source;
    private final long[] frameNs = new long[HISTORY];
    private final int[] frameTicks = new int[HISTORY];
    private int head;
    private int filled;
    private boolean primed;
    private long lastNanos;
    private double fps;
    private double tps;
    private double lastFrameMs;
    private final StringBuilder line = new StringBuilder(96);

    /**
     * Creates the overlay around a renderer.
     *
     * @param inner the renderer drawn underneath
     * @param nanos monotonic nanosecond clock used to time presents
     */
    public DebugOverlay(FrameRenderer inner, LongSupplier nanos) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.nanos = Objects.requireNonNull(nanos, "nanos");
    }

    /**
     * Installs the facts source (may be set after construction because the loop is built after
     * the presenter).
     *
     * @param source the source, or {@code null} to draw nothing
     */
    public void setSource(Source source) {
        this.source = source;
    }

    /**
     * The wrapped renderer.
     *
     * @return the renderer
     */
    public FrameRenderer inner() {
        return inner;
    }

    /**
     * Presents per second over the last second of samples.
     *
     * @return frames per second, {@code 0} before two samples exist
     */
    public double fps() {
        return fps;
    }

    /**
     * Ticks per second over the last second of samples.
     *
     * @return ticks per second, {@code 0} before two samples exist
     */
    public double tps() {
        return tps;
    }

    /**
     * Number of frame times sampled so far, at most {@value #HISTORY}.
     *
     * @return the count
     */
    public int sampleCount() {
        return filled;
    }

    @Override
    public int letterboxRgb() {
        return inner.letterboxRgb();
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        inner.render(g, alpha);
        Source s = source;
        sample(s == null ? 0 : s.lastTicks());
        if (s != null && s.isVisible()) {
            draw(g, s, alpha);
        }
    }

    private void sample(int ticks) {
        long now = nanos.getAsLong();
        if (primed) {
            long dt = now - lastNanos;
            frameNs[head] = dt < 0 ? 0 : dt;
            frameTicks[head] = ticks;
            head = (head + 1) % HISTORY;
            if (filled < HISTORY) {
                filled++;
            }
        }
        primed = true;
        lastNanos = now;

        long sum = 0;
        long tickSum = 0;
        int n = 0;
        for (int i = 0; i < filled; i++) {
            int idx = (head - 1 - i + HISTORY) % HISTORY;
            sum += frameNs[idx];
            tickSum += frameTicks[idx];
            n++;
            if (sum >= WINDOW_NS) {
                break;
            }
        }
        if (n > 0 && sum > 0) {
            fps = n * 1e9 / sum;
            tps = tickSum * 1e9 / sum;
            lastFrameMs = frameNs[(head - 1 + HISTORY) % HISTORY] / 1e6;
        } else {
            fps = 0;
            tps = 0;
            lastFrameMs = 0;
        }
    }

    private void draw(Graphics2D g, Source s, double alpha) {
        List<String> names = s.screenNames();
        int lines = 4;
        int panelHeight = 2 * PAD + lines * LINE_HEIGHT + 6 + HIST_HEIGHT + 4;
        g.setColor(BACK);
        g.fillRoundRect(MARGIN, MARGIN, PANEL_WIDTH, panelHeight, 8, 8);
        g.setFont(Fonts.mono(FONT_SIZE));
        g.setColor(TEXT);
        int x = MARGIN + PAD;
        int y = MARGIN + PAD + FONT_SIZE;

        line.setLength(0);
        line.append("tps ");
        appendFixed(line, tps, 1);
        line.append("  fps ");
        appendFixed(line, fps, 1);
        line.append("  frame ");
        appendFixed(line, lastFrameMs, 2);
        line.append(" ms");
        g.drawString(line.toString(), x, y);
        y += LINE_HEIGHT;

        line.setLength(0);
        line.append("acc ");
        appendFixed(line, s.accumulatorNs() / 1e6, 2);
        line.append(" ms  ticks/frame ").append(s.lastTicks()).append("  tick ")
                .append(s.tickCount());
        g.drawString(line.toString(), x, y);
        y += LINE_HEIGHT;

        line.setLength(0);
        line.append("alpha ");
        appendFixed(line, alpha, 2);
        line.append("  mouse ").append((int) Math.floor(s.mouseX())).append(',')
                .append((int) Math.floor(s.mouseY()));
        g.drawString(line.toString(), x, y);
        y += LINE_HEIGHT;

        line.setLength(0);
        line.append("screens ");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                line.append(" > ");
            }
            line.append(names.get(i));
        }
        g.drawString(line.toString(), x, y);
        y += 6;

        int baseY = y + HIST_HEIGHT;
        for (int i = 0; i < filled; i++) {
            int idx = (head - filled + i + HISTORY) % HISTORY;
            long ns = frameNs[idx];
            int h = (int) Math.min(HIST_HEIGHT, Math.round(ns / BAR_FULL_NS * HIST_HEIGHT));
            if (h <= 0) {
                h = 1;
            }
            g.setColor(ns <= Playfield.TICK_NS + Playfield.TICK_NS / 4 ? BAR_GOOD
                    : (ns <= 2 * Playfield.TICK_NS ? BAR_WARN : BAR_BAD));
            g.fillRect(x + i * BAR_WIDTH, baseY - h, BAR_WIDTH, h);
        }
        g.setColor(REFERENCE);
        int refH = (int) Math.round(Playfield.TICK_NS / BAR_FULL_NS * HIST_HEIGHT);
        g.fillRect(x, baseY - refH, HISTORY * BAR_WIDTH, 1);
    }

    /** Appends a non-negative double with a fixed number of decimals without formatting APIs. */
    private static void appendFixed(StringBuilder sb, double value, int decimals) {
        double v = value < 0 ? 0 : value;
        long scale = 1;
        for (int i = 0; i < decimals; i++) {
            scale *= 10;
        }
        long scaled = Math.round(v * scale);
        sb.append(scaled / scale);
        if (decimals > 0) {
            sb.append('.');
            long frac = scaled % scale;
            for (long p = scale / 10; p > 1; p /= 10) {
                if (frac < p) {
                    sb.append('0');
                }
            }
            sb.append(frac);
        }
    }
}
