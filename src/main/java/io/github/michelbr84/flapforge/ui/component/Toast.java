package io.github.michelbr84.flapforge.ui.component;

import java.util.Objects;

/**
 * One transient message in the {@link ToastLayer} queue (D16, E31.d).
 *
 * <p>A toast is driven in simulation ticks, not in wall-clock milliseconds, so a paused or
 * stalled game does not eat its message and a headless test can step it deterministically.
 */
public final class Toast {

    /** How long a toast stays up by default, in ticks (about three seconds at 60 Hz). */
    public static final int DEFAULT_TICKS = 180;
    /** Ticks spent fading in. */
    public static final int FADE_IN_TICKS = 12;
    /** Ticks spent fading out. */
    public static final int FADE_OUT_TICKS = 24;

    /** What a toast is about, which is all its colour means. */
    public enum Kind {
        /** Something happened (a language switch, a rebind). */
        INFO,
        /** Something was refused or failed (a key conflict, a failed write). */
        WARNING
    }

    private final String text;
    private final Kind kind;
    private final int totalTicks;
    private int ticksLeft;

    /**
     * Creates an informational toast of the default duration.
     *
     * @param text the message, already localised
     */
    public Toast(String text) {
        this(text, Kind.INFO, DEFAULT_TICKS);
    }

    /**
     * Creates a toast.
     *
     * @param text the message, already localised
     * @param kind what it is about
     * @param ticks how long it stays up (at least 1)
     */
    public Toast(String text, Kind kind, int ticks) {
        this.text = Objects.requireNonNull(text, "text");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.totalTicks = Math.max(1, ticks);
        this.ticksLeft = this.totalTicks;
    }

    /**
     * The message.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * What the message is about.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Ticks remaining.
     *
     * @return the count
     */
    public int ticksLeft() {
        return ticksLeft;
    }

    /**
     * Whether the toast is done and can be dropped.
     *
     * @return {@code true} when its time ran out
     */
    public boolean isExpired() {
        return ticksLeft <= 0;
    }

    /** Advances the toast by one tick. */
    public void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    /** Starts the fade-out now (used when the queue is full and the oldest must go). */
    public void expireSoon() {
        ticksLeft = Math.min(ticksLeft, FADE_OUT_TICKS);
    }

    /**
     * Opacity of the toast: it fades in, holds, then fades out.
     *
     * @return a value in {@code [0, 1]}
     */
    public double alpha() {
        int elapsed = totalTicks - ticksLeft;
        double in = FADE_IN_TICKS <= 0 ? 1 : Math.min(1, elapsed / (double) FADE_IN_TICKS);
        double out = FADE_OUT_TICKS <= 0 ? 1 : Math.min(1, ticksLeft / (double) FADE_OUT_TICKS);
        return Math.max(0, Math.min(in, out));
    }

    @Override
    public String toString() {
        return "Toast[" + kind + " " + ticksLeft + "t " + text + "]";
    }
}
