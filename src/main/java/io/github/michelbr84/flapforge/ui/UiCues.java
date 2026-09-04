package io.github.michelbr84.flapforge.ui;

import java.util.Objects;

/**
 * The three menu sounds the UI asks for, behind an indirection the {@code ui} package can call
 * without knowing that an audio backend exists (D17, D19).
 *
 * <p>Focus moves, confirmations and cancellations happen inside {@link FocusRing} and the
 * components, none of which is handed an application context; a screen-by-screen callback would
 * have to be threaded through every constructor to reach them. The application therefore installs
 * one {@link Sink} at start-up — {@code UiCues.use(...)} in {@code GameApplication}, pointing at
 * the audio manager — exactly as it installs the active string table with {@code Strings.use}.
 *
 * <p>The default sink is silent, so tests, tools and the headless launch make no sound and need no
 * set-up. Everything here runs on the loop thread; the field is {@code volatile} only so the
 * thread that assembles the application can install the sink before the loop starts.
 */
public final class UiCues {

    /** Receiver of the three cues. */
    public interface Sink {

        /** Focus moved to another node, or an adjustable value changed. */
        void move();

        /** A node was activated. */
        void select();

        /** A screen was dismissed or an action cancelled. */
        void back();
    }

    /** The sink installed when the game has no audio: it does nothing at all. */
    public static final Sink SILENT = new Sink() {

        @Override
        public void move() {
            // No audio installed.
        }

        @Override
        public void select() {
            // No audio installed.
        }

        @Override
        public void back() {
            // No audio installed.
        }
    };

    private static volatile Sink sink = SILENT;

    private UiCues() {
    }

    /**
     * Installs the sink every later cue goes to.
     *
     * @param newSink the sink, or {@code null} for {@link #SILENT}
     */
    public static void use(Sink newSink) {
        sink = newSink == null ? SILENT : newSink;
    }

    /**
     * Builds a sink from three actions.
     *
     * @param move the focus-move action
     * @param select the confirm action
     * @param back the cancel action
     * @return the sink
     */
    public static Sink of(Runnable move, Runnable select, Runnable back) {
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(select, "select");
        Objects.requireNonNull(back, "back");
        return new Sink() {

            @Override
            public void move() {
                move.run();
            }

            @Override
            public void select() {
                select.run();
            }

            @Override
            public void back() {
                back.run();
            }
        };
    }

    /** Restores the silent sink (test teardown, shutdown). */
    public static void silence() {
        sink = SILENT;
    }

    /**
     * The installed sink.
     *
     * @return the sink, never {@code null}
     */
    public static Sink active() {
        return sink;
    }

    /** Plays the focus-move cue. */
    public static void move() {
        sink.move();
    }

    /** Plays the confirm cue. */
    public static void select() {
        sink.select();
    }

    /** Plays the cancel cue. */
    public static void back() {
        sink.back();
    }
}
