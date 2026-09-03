package io.github.michelbr84.flapforge.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The cursor arithmetic and the pan law, which the mixer's inner loop takes on trust. */
class VoiceTest {

    /** Per-channel factor of a centred voice under the constant-power pan law. */
    private static final float CENTRE = (float) StrictMath.cos(StrictMath.PI / 4.0);

    /** Four stereo frames of constant full-scale signal, so gains are read straight off. */
    private static float[] flat() {
        return new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f};
    }

    @Test
    void centrePanIsEqualAndConstantPower() {
        Voice voice = new Voice("t", flat(), 1.0f, 0.0f);
        assertEquals(voice.leftGain(), voice.rightGain(), 1e-6f);
        // Constant power: the two channels together carry the full gain.
        double power = voice.leftGain() * voice.leftGain() + voice.rightGain() * voice.rightGain();
        assertEquals(1.0, power, 1e-6);
    }

    @Test
    void hardPansSendEverythingToOneSide() {
        Voice left = new Voice("t", flat(), 1.0f, -1.0f);
        assertEquals(1.0f, left.leftGain(), 1e-6f);
        assertEquals(0.0f, left.rightGain(), 1e-6f);

        Voice right = new Voice("t", flat(), 1.0f, 1.0f);
        assertEquals(0.0f, right.leftGain(), 1e-6f);
        assertEquals(1.0f, right.rightGain(), 1e-6f);
    }

    @Test
    void clampsGainAndPanAndSurvivesNonFiniteValues() {
        assertEquals(0.0f, new Voice("t", flat(), -3.0f, 0.0f).leftGain(), 1e-6f);
        Voice loud = new Voice("t", flat(), 9.0f, 0.0f);
        assertTrue(loud.leftGain() <= 1.0f);
        assertEquals(0.0f, new Voice("t", flat(), Float.NaN, 0.0f).leftGain(), 1e-6f);
        Voice wild = new Voice("t", flat(), 1.0f, Float.POSITIVE_INFINITY);
        assertEquals(new Voice("t", flat(), 1.0f, 0.0f).leftGain(), wild.leftGain(), 1e-6f);
    }

    @Test
    void addsIntoTheAccumulatorRatherThanOverwritingIt() {
        float[] out = new float[8];
        java.util.Arrays.fill(out, 0.25f);
        new Voice("t", flat(), 1.0f, -1.0f).mixInto(out, 4);
        assertEquals(1.25f, out[0], 1e-6f, "the left channel is summed on top");
        assertEquals(0.25f, out[1], 1e-6f, "the right channel is untouched by a hard-left voice");
    }

    @Test
    void stopsAtTheEndOfTheBufferInsteadOfReadingPastIt() {
        Voice voice = new Voice("t", flat(), 1.0f, 0.0f);
        assertEquals(4, voice.frameCount());
        float[] out = new float[20];
        assertEquals(4, voice.mixInto(out, 10), "only the frames that exist are mixed");
        assertTrue(voice.finished());
        assertEquals(4, voice.position());
        assertEquals(0, voice.mixInto(out, 10), "a finished voice contributes nothing more");
        assertEquals(0.0f, out[8], 0.0f, "nothing was written past the buffer");
    }

    @Test
    void respectsTheAccumulatorLength() {
        Voice voice = new Voice("t", flat(), 1.0f, 0.0f);
        float[] out = new float[4];
        assertEquals(2, voice.mixInto(out, 4), "a short accumulator caps the mix");
        assertFalse(voice.finished());
    }

    @Test
    void stopEndsAVoiceImmediately() {
        Voice voice = new Voice("t", flat(), 1.0f, 0.0f);
        voice.stop();
        assertTrue(voice.finished());
        assertTrue(voice.toString().contains("t"));
    }

    @Test
    void rejectsABufferThatIsNotStereo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Voice("t", new float[] {1f, 1f, 1f}, 1.0f, 0.0f));
    }

    // ------------------------------------------------------------------ music loops (M8, D19)

    /** A countable stereo buffer: frame n carries the value n in both channels. */
    private static float[] countable(int frames) {
        float[] samples = new float[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            samples[frame * 2] = frame;
            samples[frame * 2 + 1] = frame;
        }
        return samples;
    }

    @Test
    void aLoopingVoiceWrapsAndPlaysSeamlessly() {
        Voice voice = Voice.loop("music", countable(16), 0.5f, 0.0f, 0);
        assertTrue(voice.isLooping());
        assertFalse(voice.finished(), "a loop never runs off its buffer");

        float[] out = new float[64];
        assertEquals(32, voice.mixInto(out, 32), "the loop mixes every frame asked of it");
        for (int frame = 0; frame < 16; frame++) {
            assertEquals(out[frame * 2], out[(frame + 16) * 2], 1e-6f,
                    "the second pass over the loop repeats the first, frame " + frame);
        }
    }

    @Test
    void theFadeInRampIsMonotonicAndReachesItsTarget() {
        Voice voice = Voice.loop("music", countable(8), 0.6f, 0.0f, 1000);
        assertEquals(0.0f, voice.leftGain(), 1e-6f, "a loop fades in from silence");
        float[] out = new float[2];
        float previous = -1.0f;
        for (int i = 0; i < 100; i++) {
            voice.mixInto(out, 1);
            final float before = previous;
            assertTrue(voice.leftGain() >= previous - 1e-6f,
                    () -> "the fade-in never dips: " + before + " -> " + voice.leftGain());
            previous = voice.leftGain();
        }
        voice.mixInto(new float[(1000 - 100) * 2], 1000 - 100);
        assertEquals(0.6f * CENTRE, voice.leftGain(), 1e-3f,
                "the ramp lands on the target (centre pan splits it across both channels)");
        assertFalse(voice.finished());
    }

    @Test
    void aFadeOutMonotonicallyEndsTheLoop() {
        Voice voice = Voice.loop("music", countable(8), 0.8f, 0.0f, 0);
        voice.fadeOut(200);
        assertTrue(voice.isStopping(), "a fading loop reports itself stopping");
        float[] out = new float[2];
        float previous = Float.MAX_VALUE;
        for (int i = 0; i < 200; i++) {
            voice.mixInto(out, 1);
            final float before = previous;
            assertTrue(voice.leftGain() <= previous + 1e-6f,
                    () -> "the fade-out never rises: " + before + " -> " + voice.leftGain());
            previous = voice.leftGain();
        }
        assertTrue(voice.finished(), "the loop is dropped when its ramp reaches silence");
        assertEquals(0, voice.mixInto(out, 1), "a finished loop mixes nothing");
        assertEquals(0.0f, voice.leftGain(), 1e-6f);
    }

    @Test
    void aRetargetContinuesFromTheCurrentGainAndAReviveStopsTheFade() {
        Voice voice = Voice.loop("music", countable(8), 1.0f, 0.0f, 100);
        voice.mixInto(new float[100 * 2], 100);
        assertEquals(1.0f * CENTRE, voice.leftGain(), 1e-3f, "the fade-in completed");

        voice.rampTo(0.4f, 0.0f, 100);
        voice.mixInto(new float[50 * 2], 50);
        float halfway = voice.leftGain();
        assertTrue(halfway > 0.4f && halfway < 1.0f,
                () -> "halfway down the ramp: " + halfway);
        voice.rampTo(0.7f, 0.0f, 100);
        assertEquals(halfway, voice.leftGain(), 1e-6f,
                "a retarget starts from wherever the old ramp stood");
        voice.mixInto(new float[100 * 2], 100);
        assertEquals(0.7f * CENTRE, voice.leftGain(), 1e-3f, "and lands on the new target");

        voice.fadeOut(100);
        assertTrue(voice.isStopping());
        voice.revive();
        assertFalse(voice.isStopping(), "a crossfade that reverses cancels the fade");
        voice.rampTo(0.9f, 0.0f, 0);
        assertEquals(0.9f * CENTRE, voice.leftGain(), 1e-6f,
                "a zero-frame ramp jumps to the target");
    }

    @Test
    void aSilentLoopMixesNothingUntilItIsStopped() {
        Voice voice = Voice.loop("music", countable(8), 0.0f, 0.0f, 0);
        assertFalse(voice.finished(), "a quiet loop is alive, only silent");
        float[] out = new float[16];
        assertEquals(8, voice.mixInto(out, 8), "the cursor still runs");
        for (float sample : out) {
            assertEquals(0.0f, sample, 0.0f, "a zero-gain loop contributes silence");
        }
        voice.stop();
        assertTrue(voice.finished(), "an explicit stop ends it");
    }
}
