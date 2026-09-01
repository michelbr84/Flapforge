package io.github.michelbr84.flapforge.support;

import io.github.michelbr84.flapforge.audio.AudioBackend;
import io.github.michelbr84.flapforge.audio.SoundBank;
import io.github.michelbr84.flapforge.audio.SoftwareMixer;
import io.github.michelbr84.flapforge.audio.Voice;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@link AudioBackend} tests listen to (D19, §3 test support). It records every request and
 * also mixes the real decoded samples, through the same {@link Voice} and limiter the
 * {@link SoftwareMixer} uses, so a test can assert both <em>which</em> cue fired and that the cue
 * actually carries signal.
 *
 * <p>It lives in the test source set rather than in {@code audio/} because nothing shipped needs
 * it: the balancing tool runs headless simulations with no audio at all, and CI uses
 * {@link io.github.michelbr84.flapforge.audio.NullAudio}. Keeping it here also keeps a
 * sample-accumulating buffer out of the fat jar.
 *
 * <p>Every voice is mixed from frame zero — this is a recorder, not a timeline — so
 * {@link #peak()} answers "did anything audible reach the output" rather than reproducing what a
 * listener would hear at a given moment. No thread is started and no device is touched.
 */
public final class CaptureAudioBackend implements AudioBackend {

    /** One recorded request, exactly as the manager asked for it. */
    public record Played(String id, float gain, float pan) {
    }

    private final SoundBank bank;
    private final List<Played> played = new ArrayList<>();
    private float[] mixed = new float[0];
    private float masterGain = 1.0f;
    private boolean open;
    private boolean closed;
    private int stops;
    private int warmUps;

    /** Creates a backend over a bank that synthesises everything. */
    public CaptureAudioBackend() {
        this(new SoundBank());
    }

    /**
     * Creates a backend over a specific bank.
     *
     * @param bank resolves ids to samples
     */
    public CaptureAudioBackend(SoundBank bank) {
        this.bank = Objects.requireNonNull(bank, "bank");
    }

    @Override
    public void open() {
        open = true;
    }

    @Override
    public void play(String id, float gain, float pan) {
        played.add(new Played(id, gain, pan));
        float[] samples = bank.samples(id);
        Voice voice = new Voice(id, samples, gain * masterGain, pan);
        int frames = voice.frameCount();
        if (mixed.length < frames * 2) {
            float[] grown = new float[frames * 2];
            System.arraycopy(mixed, 0, grown, 0, mixed.length);
            mixed = grown;
        }
        voice.mixInto(mixed, frames);
    }

    @Override
    public void stopAll() {
        stops++;
    }

    @Override
    public void setMasterGain(float gain) {
        masterGain = gain;
    }

    @Override
    public void warmUp() {
        warmUps++;
        bank.warmUp();
    }

    @Override
    public void close() {
        open = false;
        closed = true;
    }

    @Override
    public boolean isRealDevice() {
        return false;
    }

    /**
     * Every recorded request, in order.
     *
     * @return an immutable snapshot
     */
    public List<Played> plays() {
        return List.copyOf(played);
    }

    /**
     * The ids of every recorded request, in order.
     *
     * @return an immutable snapshot
     */
    public List<String> ids() {
        return played.stream().map(Played::id).toList();
    }

    /**
     * The id of the last request.
     *
     * @return the id, or {@code null} when nothing was played
     */
    public String lastId() {
        return played.isEmpty() ? null : played.get(played.size() - 1).id();
    }

    /**
     * The gain of the last request, before the master fader.
     *
     * @return the gain, or {@code 0} when nothing was played
     */
    public float lastGain() {
        return played.isEmpty() ? 0.0f : played.get(played.size() - 1).gain();
    }

    /**
     * Number of recorded requests.
     *
     * @return the count
     */
    public int playCount() {
        return played.size();
    }

    /**
     * The accumulated stereo mix, limited exactly as the software mixer would limit it.
     *
     * @return a fresh interleaved stereo buffer
     */
    public float[] mixedSamples() {
        float[] out = new float[mixed.length];
        for (int i = 0; i < mixed.length; i++) {
            out[i] = SoftwareMixer.limit(mixed[i]);
        }
        return out;
    }

    /**
     * The loudest sample in the accumulated mix.
     *
     * @return the peak amplitude, {@code 0} when nothing was played
     */
    public float peak() {
        float peak = 0.0f;
        for (float sample : mixedSamples()) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    /**
     * Root-mean-square level of the accumulated mix.
     *
     * @return the RMS, {@code 0} when nothing was played
     */
    public double rms() {
        float[] samples = mixedSamples();
        if (samples.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (float sample : samples) {
            sum += (double) sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }

    /**
     * The last master gain set.
     *
     * @return the gain
     */
    public float masterGain() {
        return masterGain;
    }

    /**
     * How many times every voice was stopped.
     *
     * @return the count
     */
    public int stops() {
        return stops;
    }

    /**
     * How many warm-ups were requested.
     *
     * @return the count
     */
    public int warmUps() {
        return warmUps;
    }

    /**
     * Whether {@link #open()} ran and {@link #close()} has not.
     *
     * @return {@code true} while open
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Whether {@link #close()} ran.
     *
     * @return {@code true} once closed
     */
    public boolean isClosed() {
        return closed;
    }

    /** Forgets every recorded request and the accumulated mix. */
    public void reset() {
        played.clear();
        mixed = new float[0];
        stops = 0;
        warmUps = 0;
    }
}
