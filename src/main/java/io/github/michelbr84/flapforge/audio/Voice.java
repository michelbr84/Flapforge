package io.github.michelbr84.flapforge.audio;

import io.github.michelbr84.flapforge.core.MathUtil;
import java.util.Objects;

/**
 * One sound in flight: a decoded PCM buffer plus a playback cursor (D19).
 *
 * <p>The buffer is shared, never copied — {@link SoundBank} hands the same array to every voice
 * of the same id — so a voice is cheap to create on the mixer thread. All the per-playback state
 * is the cursor and the two channel gains; the mixing inner loop is then two multiply-adds per
 * frame and nothing else.
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
 * <p>M8 adds the two things music needs (D19): <em>looping</em> — the cursor wraps to frame zero
 * instead of stopping, so a rendered bar cycle plays seamlessly — and a <em>linear gain ramp</em>
 * — the current gains walk towards target gains over a fixed number of frames, which is how the
 * music crossfades, ducks for the pause menu and brings the boss layer in and out without a
 * click. A looping voice asked to fade out keeps mixing (wrapping) until its ramp reaches zero,
 * and only then reports itself finished; a retarget starts the new ramp from wherever the old one
 * had got to, so stacking commands never jumps in gain.
 *
 * <p>Instances are confined to the thread that mixes them.
 */
public final class Voice {

    private final String id;
    private final float[] samples;
    private float leftGain;
    private float rightGain;
    private int frame;
    private boolean looping;
    private boolean stopping;
    private float targetLeftGain;
    private float targetRightGain;
    private float rampFromLeftGain;
    private float rampFromRightGain;
    private int rampRemaining;
    private int rampFrames;

    /**
     * Creates a one-shot voice positioned at the start of the buffer.
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
        this.targetLeftGain = leftGain;
        this.targetRightGain = rightGain;
    }

    /**
     * Creates a looping voice that fades in from silence (M8, D19): the music voice the mixer
     * keeps until it is told to stop.
     *
     * @param id the sound id, for diagnostics
     * @param samples interleaved stereo samples, as for {@link #Voice(String, float[], float,
     *     float)}
     * @param gain the target linear gain, clamped to {@code [0, 1]}
     * @param pan the target pan
     * @param rampFrames how many frames the fade-in takes
     * @return the looping voice
     */
    public static Voice loop(String id, float[] samples, float gain, float pan, int rampFrames) {
        Voice voice = new Voice(id, samples, 0.0f, pan);
        voice.looping = true;
        voice.rampTo(gain, pan, rampFrames);
        return voice;
    }

    /**
     * Whether the cursor wraps instead of stopping at the end of the buffer.
     *
     * @return {@code true} for a looping voice
     */
    public boolean isLooping() {
        return looping;
    }

    /**
     * Whether this looping voice is fading out and will finish when the ramp ends.
     *
     * @return {@code true} once {@link #fadeOut} was called
     */
    public boolean isStopping() {
        return stopping;
    }

    /**
     * Clears the fade-out flag so a retarget can bring a stopping loop back (M8): a crossfade
     * that reverses mid-fade — the player leaves and re-enters a screen within the ramp — must
     * resurrect the old voice rather than start a second copy of the same id.
     */
    public void revive() {
        stopping = false;
    }

    /**
     * Starts or retargets the linear gain ramp: from the gains this voice carries <em>right
     * now</em> to the asked-for gain and pan over the given number of frames.
     *
     * @param gain the target linear gain, clamped to {@code [0, 1]}
     * @param pan the target pan
     * @param rampFrames ramp length in frames; {@code 0} or less jumps there immediately
     */
    public void rampTo(float gain, float pan, int rampFrames) {
        float g = (float) MathUtil.clamp(finite(gain), 0.0, 1.0);
        float p = (float) MathUtil.clamp(finite(pan), -1.0, 1.0);
        double angle = (p + 1.0) * StrictMath.PI / 4.0;
        this.rampFromLeftGain = leftGain;
        this.rampFromRightGain = rightGain;
        this.targetLeftGain = (float) (g * StrictMath.cos(angle));
        this.targetRightGain = (float) (g * StrictMath.sin(angle));
        this.rampFrames = Math.max(0, rampFrames);
        this.rampRemaining = this.rampFrames;
        if (this.rampFrames == 0) {
            leftGain = targetLeftGain;
            rightGain = targetRightGain;
        }
    }

    /**
     * Fades a looping voice to silence over the given number of frames; it finishes — and the
     * mixer drops it — when the ramp reaches zero.
     *
     * @param rampFrames fade length in frames; {@code 0} or less stops it on this pass
     */
    public void fadeOut(int rampFrames) {
        stopping = true;
        rampTo(0.0f, currentPan(), rampFrames);
    }

    /**
     * The pan the voice is currently aimed at, recovered from its target channel gains (tests,
     * and {@link #fadeOut} so a fade does not drift the voice towards centre).
     *
     * @return a pan in {@code [-1, 1]}
     */
    public float currentPan() {
        if (targetRightGain <= 0.0f && targetLeftGain <= 0.0f) {
            return 0.0f;
        }
        double angle = StrictMath.atan2(targetRightGain, targetLeftGain);
        return (float) MathUtil.clamp(angle / (StrictMath.PI / 4.0) - 1.0, -1.0, 1.0);
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
     * Cursor position in stereo frames. For a looping voice this is the position within the
     * current pass over the buffer, not the total played.
     *
     * @return frames already mixed this pass
     */
    public int position() {
        return frame;
    }

    /**
     * Gain applied to the left channel after the pan law, at the current ramp position.
     *
     * @return the left gain
     */
    public float leftGain() {
        return leftGain;
    }

    /**
     * Gain applied to the right channel after the pan law, at the current ramp position.
     *
     * @return the right gain
     */
    public float rightGain() {
        return rightGain;
    }

    /**
     * Whether nothing is left to mix: the cursor ran off the end of a one-shot buffer, or a
     * looping voice finished its fade-out.
     *
     * @return {@code true} when the mixer can drop this voice
     */
    public boolean finished() {
        if (looping) {
            return stopping && rampRemaining <= 0;
        }
        return frame >= frameCount();
    }

    /** Moves the cursor to the end so the next drop pass removes this voice. */
    public void stop() {
        if (looping) {
            stopping = true;
            rampRemaining = 0;
            leftGain = 0.0f;
            rightGain = 0.0f;
        }
        frame = frameCount();
    }

    /**
     * Adds this voice's next frames into an interleaved stereo accumulator. The accumulator is
     * <em>not</em> cleared: summing is the whole point. A looping voice keeps wrapping until it
     * runs out of requested frames or finishes its fade.
     *
     * @param out the accumulator, at least {@code frames * 2} long
     * @param frames how many stereo frames to mix
     * @return how many frames were actually mixed (fewer than {@code frames} at the tail)
     */
    public int mixInto(float[] out, int frames) {
        return mixInto(out, 0, frames);
    }

    /**
     * Adds this voice's next frames into an interleaved stereo accumulator at a frame offset,
     * advancing the gain ramp as it goes.
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
        if (finished()) {
            return 0;
        }
        int capacity = out.length / 2 - frameOffset;
        int mixed = 0;
        while (mixed < frames && mixed < capacity) {
            int wanted = Math.min(frames - mixed, capacity - mixed);
            // No span ever crosses the buffer's end: a looping voice wraps here, re-entering the
            // loop with the cursor at frame zero, so every read stays in bounds by construction.
            wanted = Math.min(wanted, frameCount() - frame);
            if (wanted <= 0) {
                break;
            }
            mixSpan(out, frameOffset + mixed, wanted);
            mixed += wanted;
            frame += wanted;
            if (finished()) {
                break;
            }
            if (frame >= frameCount()) {
                if (!looping) {
                    break;
                }
                frame = 0;
            }
        }
        return mixed;
    }

    /** Mixes one in-bounds span and steps the ramp once per frame. */
    private void mixSpan(float[] out, int frameOffset, int frames) {
        for (int i = 0; i < frames; i++) {
            if (rampRemaining > 0) {
                float t = (rampFrames - rampRemaining + 1) / (float) rampFrames;
                leftGain = rampFromLeftGain + (targetLeftGain - rampFromLeftGain) * t;
                rightGain = rampFromRightGain + (targetRightGain - rampFromRightGain) * t;
                rampRemaining--;
                if (rampRemaining == 0) {
                    leftGain = targetLeftGain;
                    rightGain = targetRightGain;
                }
            }
            int src = (frame + i) * 2;
            int dst = (frameOffset + i) * 2;
            out[dst] += samples[src] * leftGain;
            out[dst + 1] += samples[src + 1] * rightGain;
        }
    }

    private static double finite(float value) {
        return Float.isFinite(value) ? value : 0.0;
    }

    @Override
    public String toString() {
        return "Voice{" + id + ' ' + frame + '/' + frameCount()
                + (looping ? " loop" + (stopping ? " stopping" : "") : "") + '}';
    }
}
