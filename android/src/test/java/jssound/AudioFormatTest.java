package jssound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Pure-JVM proofs of the {@link AudioFormat} shim: the two census constructors store what
 * SoftwareMixer.java:86 and SoundBank.java:315 expect to read back, {@link
 * AudioFormat.Encoding#PCM_SIGNED} compares like AWT's, and the one non-census path
 * ({@code signed == false}) is refused.
 */
public class AudioFormatTest {

    @Test
    public void fiveArgumentConstructorStoresTheMixerFormat() {
        // SoftwareMixer.java:86: new AudioFormat(SAMPLE_RATE, BITS, CHANNELS, true, false)
        AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
        assertEquals(44100f, format.getSampleRate(), 0f);
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(2, format.getChannels());
        assertEquals(4, format.getFrameSize());
        assertEquals(44100f, format.getFrameRate(), 0f);
        assertFalse(format.isBigEndian());
        assertSame(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
        assertEquals("PCM_SIGNED 44100.0 Hz, 16 bit, stereo, 4 bytes/frame, little-endian",
                format.toString());
    }

    @Test
    public void sevenArgumentConstructorStoresTheSoundBankFormat() {
        // SoundBank.java:315-317, for a 22.05 kHz mono source.
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050f, 16, 1,
                2, 22050f, false);
        assertEquals(22050f, format.getSampleRate(), 0f);
        assertEquals(1, format.getChannels());
        assertEquals(2, format.getFrameSize());
        assertEquals(22050f, format.getFrameRate(), 0f);
        assertTrue(AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()));
        assertTrue(format.toString().startsWith("PCM_SIGNED 22050.0 Hz, 16 bit, mono"));
    }

    @Test
    public void unsignedPcmIsOutsideTheCensus() {
        try {
            new AudioFormat(8000, 8, 1, false, false);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("census"));
        }
    }

    @Test
    public void encodingComparesByName() {
        assertEquals(AudioFormat.Encoding.PCM_SIGNED, AudioFormat.Encoding.PCM_SIGNED);
        assertEquals(AudioFormat.Encoding.PCM_SIGNED.hashCode(),
                AudioFormat.Encoding.PCM_SIGNED.hashCode());
        assertEquals("PCM_SIGNED", AudioFormat.Encoding.PCM_SIGNED.toString());
        assertNotEquals(AudioFormat.Encoding.PCM_SIGNED, "PCM_SIGNED");
    }

    @Test
    public void matchesComparesEveryField() {
        AudioFormat stereo = new AudioFormat(44100, 16, 2, true, false);
        assertTrue(stereo.matches(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16,
                2, 4, 44100f, false)));
        assertFalse(stereo.matches(new AudioFormat(44100, 16, 1, true, false)));
        assertFalse(stereo.matches(new AudioFormat(22050, 16, 2, true, false)));
        assertFalse(stereo.matches(new AudioFormat(44100, 16, 2, true, true)));
    }
}
