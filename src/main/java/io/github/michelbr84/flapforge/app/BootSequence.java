package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.TextPainter;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The warm-up the boot splash runs before the menu opens (M2).
 *
 * <p>Everything expensive that would otherwise stutter the first seconds of play happens here:
 * deriving and rasterising the fonts, and — once the audio milestone is wired in — opening the
 * mixer line, which can block for hundreds of milliseconds on a busy device. None of it may run
 * on the loop thread, so the whole sequence is handed to an {@link Executor} owned by
 * {@link Threads} and the screen only ever <em>reads</em> the progress: {@link #completed()},
 * {@link #currentLabel()} and {@link #isDone()} are safe to poll once per frame.
 *
 * <p>A step that throws does not abort the boot: the failure is recorded in {@link #errors()} and
 * the next step runs, because a game that cannot warm up its audio must still reach its menu.
 */
public final class BootSequence {

    /**
     * One warm-up task.
     *
     * @param label the string key the splash shows while the task runs
     * @param action the work, run on the background thread
     */
    public record Step(StringKey label, Runnable action) {

        /** Validates the components. */
        public Step {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(action, "action");
        }
    }

    /** Text the font warm-up measures: Latin letters, Portuguese accents and digits. */
    private static final String WARM_UP_TEXT = "Flapforge ação 0123456789";

    private final Executor executor;
    private final List<Step> steps;
    private final AtomicInteger completed = new AtomicInteger();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private volatile StringKey current;
    private volatile boolean started;
    private volatile boolean done;

    /**
     * Creates a sequence.
     *
     * @param executor where the steps run (never the loop thread)
     * @param steps the steps, in order
     */
    public BootSequence(Executor executor, List<Step> steps) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.steps = List.copyOf(steps);
        this.current = this.steps.isEmpty() ? StringKey.BOOT_READY : this.steps.get(0).label();
    }

    /**
     * The steps the game boots with: the fonts it draws every frame with.
     *
     * <p>The audio warm-up is appended by the integrator as a step of its own, which is why this
     * returns a mutable list.
     *
     * @return a fresh mutable list
     */
    public static List<Step> defaultSteps() {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(StringKey.BOOT_FONTS, BootSequence::warmUpFonts));
        return steps;
    }

    /**
     * Derives and rasterises the font sizes the UI draws with, so the first menu frame does not
     * pay for the toolkit's font cache.
     */
    public static void warmUpFonts() {
        BufferedImage probe = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        try {
            TextPainter.prepare(g);
            int[] sizes = {11, 12, 13, 14, 15, 16, 20, 30, 32, 40, 58};
            for (int size : sizes) {
                g.setFont(Fonts.regular(size));
                g.getFontMetrics().stringWidth(WARM_UP_TEXT);
                g.setFont(Fonts.bold(size));
                g.getFontMetrics().stringWidth(WARM_UP_TEXT);
            }
            g.setFont(Fonts.mono(11));
            g.getFontMetrics().stringWidth(WARM_UP_TEXT);
        } finally {
            g.dispose();
        }
    }

    /**
     * The steps, in order.
     *
     * @return an unmodifiable list
     */
    public List<Step> steps() {
        return steps;
    }

    /** Submits the sequence. Calling it twice does nothing. */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        if (steps.isEmpty()) {
            current = StringKey.BOOT_READY;
            done = true;
            return;
        }
        executor.execute(this::runAll);
    }

    private void runAll() {
        for (Step step : steps) {
            current = step.label();
            try {
                step.action().run();
            } catch (RuntimeException | Error e) {
                errors.add(step.label().key() + ": " + e);
            }
            completed.incrementAndGet();
        }
        current = StringKey.BOOT_READY;
        done = true;
    }

    /**
     * Whether {@link #start()} has been called.
     *
     * @return {@code true} once submitted
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Whether every step has finished (successfully or not).
     *
     * @return {@code true} when the warm-up is over
     */
    public boolean isDone() {
        return done;
    }

    /**
     * How many steps have finished.
     *
     * @return the count
     */
    public int completed() {
        return completed.get();
    }

    /**
     * How many steps there are.
     *
     * @return the count
     */
    public int total() {
        return steps.size();
    }

    /**
     * Progress through the sequence.
     *
     * @return a value in {@code [0, 1]}
     */
    public double progress() {
        return steps.isEmpty() ? 1.0 : Math.min(1.0, completed.get() / (double) steps.size());
    }

    /**
     * The label of the step running now, or {@link StringKey#BOOT_READY} once it is over.
     *
     * @return the key the splash draws
     */
    public StringKey currentLabel() {
        return current;
    }

    /**
     * Everything a step threw.
     *
     * @return an unmodifiable snapshot, empty when every step succeeded
     */
    public List<String> errors() {
        return Collections.unmodifiableList(new ArrayList<>(errors));
    }

    @Override
    public String toString() {
        return "BootSequence[" + completed.get() + "/" + steps.size()
                + (done ? " done]" : " running]");
    }
}
