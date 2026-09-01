package io.github.michelbr84.flapforge.audio;

import io.github.michelbr84.flapforge.core.MathUtil;
import java.util.Objects;

/**
 * One sound in flight: a decoded PCM buffer plus a playback cursor (D19).
 *
 * <p>The buffer is shared, never copied — {@link SoundBank} hands the same array to every voice
 * of the same id — so a voice is cheap to create on the mixer thread. All the per-playback state
 * is the cursor and the two channel gains, which are resolved once in the constructor: the mixing
 * inner loop is then two multiply-adds per frame and nothing else.
 *
 * <p>Samples are interleaved stereo at {@link SoundBank#SAMPLE_RATE}, because both channel
 * widening and rate conversion happen once at load time. A voice never resamples.
 *
 * <p>Panning uses the constant-power law {@code left = gain·cos(a)}, {@code right = gain·sin(a)}
 * with {@code a = (pan + 1)·π/4}: total power is the same at every pan position, and neither
 * channel can exceed {@code gain}, so a hard-panned voice cannot push the mixer into its limiter
 * on its own. A centred voice therefore plays at {@code 0.707·gain} — the usual −3 dB centre of
 * this law.
 *
 * <p>Instances are confined to the thread that mixes them.
 */
public final class Voice {

    private final String id;
    private final float[] samples;
    private final float leftGain;
    private final float rightGain;
    private int frame;

    /**
     * Creates a voice positioned at the start of the buffer.
     *
     * @param id the sound id, for diagnostics
     * @param samples interleaved stereo samples at {@link SoundBank#SAMPLE_RATE}, in
     *     {@code [-1, 1]}; kept by reference and never modified
     * @param gain linear gain, clamped to {@code [0, 1]}
     * @param pan {@code -1} hard left, {@code 0} centre, {@code +1} hard right
     */
    public Voice(String id, float[] samples, float gain, float pan) {
        this.id = Objects.requireNonNull(id, "id");
        this.samples = Objects.requireNonNull(samples, "samples");
        if ((samples.length & 1) != 0) {
            throw new IllegalArgumentException(
                    "stereo buffers hold an even number of samples, got " + samples.length);
        }
        float g = (float) MathUtil.clamp(finite(gain), 0.0, 1.0);
        float p = (float) MathUtil.clamp(finite(pan), -1.0, 1.0);
        double angle = (p + 1.0) * StrictMath.PI / 4.0;
        this.leftGain = (float) (g * StrictMath.cos(angle));
        this.rightGain = (float) (g * StrictMath.sin(angle));
    }

    /**
     * The sound id this voice plays.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Total length of the buffer in stereo frames.
     *
     * @return the frame count
     */
    public int frameCount() {
        return samples.length / 2;
    }

    /**
     * Cursor position in stereo frames.
     *
     * @return frames already mixed
     */
    public int position() {
        return frame;
    }

    /**
     * Gain applied to the left channel after the pan law.
     *
     * @return the left gain
     */
    public float leftGain() {
        return leftGain;
    }

    /**
     * Gain applied to the right channel after the pan law.
     *
     * @return the right gain
     */
    public float rightGain() {
        return rightGain;
    }

    /**
     * Whether the cursor reached the end of the buffer.
     *
     * @return {@code true} when nothing is left to mix
     */
    public boolean finished() {
        return frame >= frameCount();
    }

    /** Moves the cursor to the end so the next drop pass removes this voice. */
    public void stop() {
        frame = frameCount();
    }

    /**
     * Adds this voice's next frames into an interleaved stereo accumulator. The accumulator is
     * <em>not</em> cleared: summing is the whole point.
     *
     * @param out the accumulator, at least {@code frames * 2} long
     * @param frames how many stereo frames to mix
     * @return how many frames were actually mixed (fewer than {@code frames} at the tail)
     */
    public int mixInto(float[] out, int frames) {
        return mixInto(out, 0, frames);
    }

    /**
     * Adds this voice's next frames into an interleaved stereo accumulator at a frame offset.
     *
     * @param out the accumulator
     * @param frameOffset first accumulator frame to write
     * @param frames how many stereo frames to mix
     * @return how many frames were actually mixed
     */
    public int mixInto(float[] out, int frameOffset, int frames) {
        Objects.requireNonNull(out, "out");
        if (frameOffset < 0 || frames < 0) {
            throw new IllegalArgumentException("frameOffset and frames must not be negative");
        }
        int available = Math.min(frames, frameCount() - frame);
        available = Math.min(available, out.length / 2 - frameOffset);
        for (int i = 0; i < available; i++) {
            int src = (frame + i) * 2;
            int dst = (frameOffset + i) * 2;
            out[dst] += samples[src] * leftGain;
            out[dst + 1] += samples[src + 1] * rightGain;
        }
        frame += Math.max(0, available);
        return Math.max(0, available);
    }

    private static double finite(float value) {
        return Float.isFinite(value) ? value : 0.0;
    }

    @Override
    public String toString() {
        return "Voice{" + id + ' ' + frame + '/' + frameCount() + '}';
    }
}
