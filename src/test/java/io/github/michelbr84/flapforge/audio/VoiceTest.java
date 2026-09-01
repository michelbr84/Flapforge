package io.github.michelbr84.flapforge.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The cursor arithmetic and the pan law, which the mixer's inner loop takes on trust. */
class VoiceTest {

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
}
