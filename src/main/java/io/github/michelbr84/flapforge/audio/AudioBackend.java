package io.github.michelbr84.flapforge.audio;

import java.awt.AWTError;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import javax.sound.sampled.LineUnavailableException;

/**
 * The audio output the rest of the game talks to (D19). Three implementations exist:
 * {@link SoftwareMixer} (one pre-opened line and a daemon mixing thread), {@link NullAudio}
 * (no output at all) and the test-only capture backend.
 *
 * <p>Every method is safe to call from the game loop and none of them may block it:
 * {@link #play(String, float, float)} hands the request to a bounded queue and returns, dropping
 * the request when the queue is full rather than making the loop wait for the mixer.
 *
 * <p>Use {@link #create(boolean, ThreadFactory)} rather than a constructor. It is the one place
 * that knows a machine may have no sound card, no permission to open one, or no AWT at all
 * (E30.j): every such failure becomes a single logged line and a {@link NullAudio}, so CI and
 * headless machines run the real code path with silent output instead of crashing.
 */
public interface AudioBackend {

    /**
     * Acquires the output device and starts mixing. Calling it twice is a no-op.
     *
     * @throws LineUnavailableException when no output line can be opened — the reason
     *     {@link #create(boolean, ThreadFactory)} exists
     */
    void open() throws LineUnavailableException;

    /**
     * Queues one sound. Never blocks; a request that does not fit in the queue is dropped.
     *
     * @param id the sound id, resolved through {@link SoundBank}
     * @param gain linear gain in {@code [0, 1]}
     * @param pan {@code -1} hard left, {@code 0} centre, {@code +1} hard right
     */
    void play(String id, float gain, float pan);

    /**
     * Queues one sound at full gain, centred.
     *
     * @param id the sound id
     */
    default void play(String id) {
        play(id, 1.0f, 0.0f);
    }

    /** Silences every voice currently in flight and discards anything queued. */
    void stopAll();

    /**
     * Sets the global fader applied after every per-voice gain.
     *
     * @param gain linear gain in {@code [0, 1]}
     */
    void setMasterGain(float gain);

    /**
     * Decodes or generates every known sound ahead of time so the first play does not pay for it.
     * D19 puts this on the audio thread during the boot screen. The default does nothing.
     */
    default void warmUp() {
    }

    /**
     * Decodes or generates every known sound <em>on the calling thread</em> and returns when the
     * bank is warm. The boot step calls this rather than {@link #warmUp()}: queueing the work
     * would let the splash claim "audio ready" in nanoseconds and drop the decode between two
     * device writes. The default falls back to the queued form, which is right for a backend with
     * nothing to decode.
     */
    default void warmUpBlocking() {
        warmUp();
    }

    /** Releases the device and stops the mixing thread. Calling it twice is a no-op. */
    void close();

    /**
     * Whether this backend actually reaches a sound device.
     *
     * @return {@code false} for {@link NullAudio} and the test capture backend
     */
    boolean isRealDevice();

    /**
     * Builds the backend for this process: a {@link SoftwareMixer} when audio is enabled and a
     * line opens, {@link NullAudio} otherwise (E30.j). It never throws.
     *
     * @param enabled {@code false} for {@code --no-audio}, which selects {@link NullAudio}
     *     without touching the sound system at all
     * @param threadFactory supplies the daemon mixing thread, normally
     *     {@code Threads.audioThreadFactory()}
     * @return an open backend, ready to play
     */
    static AudioBackend create(boolean enabled, ThreadFactory threadFactory) {
        return create(enabled, threadFactory, new SoundBank(), null);
    }

    /**
     * Builds the backend with the bank and log sink injected, for tests and tooling.
     *
     * @param enabled {@code false} selects {@link NullAudio}
     * @param threadFactory supplies the daemon mixing thread
     * @param bank the sound bank the mixer resolves ids through
     * @param log receives the single fallback line, or {@code null} for {@code System.err}
     * @return an open backend, ready to play
     */
    static AudioBackend create(boolean enabled, ThreadFactory threadFactory, SoundBank bank,
            Consumer<String> log) {
        Objects.requireNonNull(bank, "bank");
        Consumer<String> sink = log != null ? log : System.err::println;
        if (!enabled) {
            return new NullAudio();
        }
        Objects.requireNonNull(threadFactory, "threadFactory");
        SoftwareMixer mixer = new SoftwareMixer(bank, threadFactory);
        try {
            mixer.open();
            return mixer;
            // AWTError shows up on stripped headless images where the sound stack drags in AWT;
            // SecurityException on locked-down JVMs; IllegalArgumentException when no installed
            // mixer supports the format. All three mean the same thing: play the game silently.
        } catch (LineUnavailableException | IllegalArgumentException | IllegalStateException
                | SecurityException | UnsupportedOperationException | AWTError e) {
            closeQuietly(mixer);
            sink.accept("Audio: no output device (" + e.getClass().getSimpleName() + ": "
                    + e.getMessage() + "); continuing without sound.");
            return new NullAudio();
        }
    }

    /** Closes a half-open mixer without letting a second failure mask the first. */
    private static void closeQuietly(SoftwareMixer mixer) {
        try {
            mixer.close();
        } catch (RuntimeException | AWTError ignored) {
            // The fallback line is already being logged; a failure to clean up adds nothing.
        }
    }
}
