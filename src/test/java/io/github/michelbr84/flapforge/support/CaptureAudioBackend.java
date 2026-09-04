package io.github.michelbr84.flapforge.support;

import io.github.michelbr84.flapforge.audio.AudioBackend;
import io.github.michelbr84.flapforge.audio.SoundBank;
import io.github.michelbr84.flapforge.audio.SoftwareMixer;
import io.github.michelbr84.flapforge.audio.Voice;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final List<Played> loopPlays = new ArrayList<>();
    private final Map<String, Voice> loops = new LinkedHashMap<>();
    private final Map<String, float[]> registeredLoops = new LinkedHashMap<>();
    private final Set<String> stoppedLoops = new LinkedHashSet<>();
    private float[] mixed = new float[0];
    private float masterGain = 1.0f;
    private boolean open;
    private boolean closed;
    private int stops;
    private int warmUps;
    private int loopStops;

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
        for (Voice voice : loops.values()) {
            voice.stop();
        }
    }

    // ---------------------------------------------------------------- music loops (M8, D19)

    @Override
    public void registerLoop(String id, float[] samples) {
        registeredLoops.put(id, samples.clone());
    }

    @Override
    public boolean hasLoop(String id) {
        return registeredLoops.containsKey(id);
    }

    @Override
    public void playLooping(String id, float gain, float pan) {
        loopPlays.add(new Played(id, gain, pan));
        stoppedLoops.remove(id);
        Voice existing = loops.get(id);
        if (existing != null && !existing.finished()) {
            if (existing.isStopping()) {
                existing.revive();
            }
            existing.rampTo(gain * masterGain, pan, SoftwareMixer.MUSIC_RAMP_FRAMES);
            return;
        }
        float[] samples = registeredLoops.get(id);
        if (samples == null) {
            samples = bank.samples(id);
        }
        Voice voice = Voice.loop(id, samples, gain * masterGain, pan,
                SoftwareMixer.MUSIC_RAMP_FRAMES);
        loops.put(id, voice);
    }

    @Override
    public void stopLooping(String id) {
        loopStops++;
        stoppedLoops.add(id);
        Voice voice = loops.get(id);
        if (voice != null && !voice.finished()) {
            voice.fadeOut(SoftwareMixer.MUSIC_RAMP_FRAMES);
        }
    }

    /**
     * Mixes every active looping voice forward by the asked-for wall of time — through the same
     * {@link Voice} ramp and wrap the mixer uses — and returns the limited stereo result. This
     * is a recorder, not a timeline: each call advances the voices, so two calls of one second
     * are the second and the third second of the loop.
     *
     * @param seconds how much loop to mix
     * @return interleaved stereo samples, limited like the mixer's output
     */
    public float[] mixedLoopSeconds(double seconds) {
        int frames = (int) Math.round(seconds * SoundBank.SAMPLE_RATE);
        float[] acc = new float[frames * SoundBank.CHANNELS];
        for (Voice voice : loops.values()) {
            int done = 0;
            while (done < frames && !voice.finished()) {
                int mixedFrames = voice.mixInto(acc, done, frames - done);
                if (mixedFrames <= 0) {
                    break;
                }
                done += mixedFrames;
            }
        }
        for (int i = 0; i < acc.length; i++) {
            acc[i] = SoftwareMixer.limit(acc[i]);
        }
        return acc;
    }

    /**
     * Every looping request, in order — starts, retargets (the crossfade, the duck, a volume
     * change) included.
     *
     * @return an immutable snapshot
     */
    public List<Played> loopPlayList() {
        return List.copyOf(loopPlays);
    }

    /**
     * How many looping requests were recorded.
     *
     * @return the count
     */
    public int loopPlayCount() {
        return loopPlays.size();
    }

    /**
     * How many looping fade-outs were asked for.
     *
     * @return the count
     */
    public int loopStopCount() {
        return loopStops;
    }

    /**
     * The ids currently looping (a faded-out voice is dropped, as the mixer drops it).
     *
     * @return the ids, in registration order
     */
    public List<String> activeLoopIds() {
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Voice> entry : loops.entrySet()) {
            if (!entry.getValue().finished()) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    /**
     * Whether a stop was asked for a loop id (it may still be fading).
     *
     * @param id the loop id
     * @return {@code true} once {@code stopLooping} named it
     */
    public boolean isLoopStopped(String id) {
        return stoppedLoops.contains(id);
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
        loopPlays.clear();
        loops.clear();
        registeredLoops.clear();
        stoppedLoops.clear();
        mixed = new float[0];
        stops = 0;
        warmUps = 0;
        loopStops = 0;
    }
}
