package jssound;

import static jssound.WaveFixtures.TAG_EXTENSIBLE;
import static jssound.WaveFixtures.TAG_IEEE_FLOAT;
import static jssound.WaveFixtures.TAG_PCM;
import static jssound.WaveFixtures.canonical;
import static jssound.WaveFixtures.chunk;
import static jssound.WaveFixtures.fmt16;
import static jssound.WaveFixtures.fmt18;
import static jssound.WaveFixtures.fmt40;
import static jssound.WaveFixtures.riff;
import static jssound.WaveFixtures.sinePcm;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

/**
 * Pure-JVM proofs of the RIFF/WAVE reader behind {@link AudioSystem#getAudioInputStream(
 * InputStream)} and of the {@link AudioInputStream} it returns: the format fields SoundBank
 * reads, a byte-for-byte PCM round trip, the tolerated container variants (extra chunks before
 * {@code data}, the 18- and 40-byte {@code fmt } forms, the streaming data size), and the
 * rejections that must surface as {@link UnsupportedAudioFileException}.
 */
public class AudioInputStreamTest {

    private static final int STEREO_FRAMES = 441;

    private static AudioInputStream open(byte[] bytes)
            throws UnsupportedAudioFileException, IOException {
        return AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
    }

    private static void assertPcmFormat(AudioFormat format, int channels, float rate) {
        assertSame(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
        assertEquals(rate, format.getSampleRate(), 0f);
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(channels, format.getChannels());
        assertEquals(channels * 2, format.getFrameSize());
        assertEquals(rate, format.getFrameRate(), 0f);
        assertFalse(format.isBigEndian());
    }

    private static void assertUnsupported(byte[] bytes, String messagePart) throws IOException {
        try (AudioInputStream in = open(bytes)) {
            fail("expected UnsupportedAudioFileException, got " + in.getFormat());
        } catch (UnsupportedAudioFileException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
        }
    }

    // ------------------------------------------------------------------ happy paths

    @Test
    public void stereoCanonicalWaveRoundTripsFormatAndBytes() throws Exception {
        byte[] pcm = sinePcm(2, 44100, STEREO_FRAMES, 440);
        try (AudioInputStream in = open(canonical(2, 44100, pcm))) {
            assertPcmFormat(in.getFormat(), 2, 44100f);
            assertEquals(STEREO_FRAMES, in.getFrameLength());
            assertArrayEquals(pcm, in.readAllBytes());
            assertEquals(-1, in.read());
        }
    }

    @Test
    public void monoCanonicalWaveRoundTripsFormatAndBytes() throws Exception {
        byte[] pcm = sinePcm(1, 22050, 300, 220);
        try (AudioInputStream in = open(canonical(1, 22050, pcm))) {
            assertPcmFormat(in.getFormat(), 1, 22050f);
            assertEquals(300, in.getFrameLength());
            assertArrayEquals(pcm, in.readAllBytes());
        }
    }

    @Test
    public void chunksBeforeDataAreSkippedPadByteIncluded() throws Exception {
        byte[] pcm = sinePcm(2, 48000, 100, 1000);
        byte[] list = chunk("LIST", "INFOISFT   Lavf59 "
                .getBytes(StandardCharsets.ISO_8859_1)); // 19 bytes: odd, so padded
        byte[] fact = chunk("fact", new byte[] {100, 0, 0, 0});
        byte[] junk = chunk("JUNK", new byte[13]);
        byte[] wave = riff(junk, fmt16(TAG_PCM, 2, 48000, 16), list, fact, chunk("data", pcm));
        try (AudioInputStream in = open(wave)) {
            assertPcmFormat(in.getFormat(), 2, 48000f);
            assertEquals(100, in.getFrameLength());
            assertArrayEquals(pcm, in.readAllBytes());
        }
    }

    @Test
    public void eighteenByteAndExtensibleFmtChunksAreAccepted() throws Exception {
        byte[] pcm = sinePcm(2, 44100, 50, 440);
        try (AudioInputStream in = open(riff(fmt18(TAG_PCM, 2, 44100, 16), chunk("data", pcm)))) {
            assertPcmFormat(in.getFormat(), 2, 44100f);
            assertArrayEquals(pcm, in.readAllBytes());
        }
        try (AudioInputStream in = open(riff(fmt40(2, 44100, 16, TAG_PCM), chunk("data", pcm)))) {
            assertPcmFormat(in.getFormat(), 2, 44100f);
            assertArrayEquals(pcm, in.readAllBytes());
        }
        byte[] mono = sinePcm(1, 8000, 50, 440);
        try (AudioInputStream in = open(riff(fmt40(1, 8000, 16, TAG_PCM), chunk("data", mono)))) {
            assertPcmFormat(in.getFormat(), 1, 8000f);
            assertArrayEquals(mono, in.readAllBytes());
        }
    }

    @Test
    public void streamingDataSizeReadsToEndOfStream() throws Exception {
        byte[] pcm = sinePcm(2, 44100, 70, 440);
        byte[] wave = riff(fmt16(TAG_PCM, 2, 44100, 16), chunk("data", pcm, 0xFFFFFFFFL));
        try (AudioInputStream in = open(wave)) {
            assertEquals(AudioInputStream.NOT_SPECIFIED, in.getFrameLength());
            assertEquals(-1, in.getFrameLength());
            assertArrayEquals(pcm, in.readAllBytes());
        }
    }

    @Test
    public void trailingPartialFrameIsDroppedLikeAwt() throws Exception {
        byte[] pcm = sinePcm(2, 44100, 2, 440); // 8 bytes, two frames
        byte[] wave = riff(fmt16(TAG_PCM, 2, 44100, 16), chunk("data", pcm, 7));
        try (AudioInputStream in = open(wave)) {
            assertEquals(1, in.getFrameLength());
            assertArrayEquals(Arrays.copyOf(pcm, 4), in.readAllBytes());
        }
    }

    @Test
    public void shortReadsSingleBytesAndAvailableStayInsideTheDataChunk() throws Exception {
        byte[] pcm = sinePcm(1, 8000, 11, 440); // 22 bytes
        byte[] trailer = chunk("LIST", new byte[6]); // must never leak into the audio
        byte[] wave = WaveFixtures.concat(canonical(1, 8000, pcm), trailer);
        try (AudioInputStream in = open(wave)) {
            assertEquals(22, in.available());
            ByteArrayOutputStream got = new ByteArrayOutputStream();
            got.write(in.read()); // one byte
            byte[] small = new byte[3];
            int n;
            while ((n = in.read(small, 0, small.length)) > 0) {
                got.write(small, 0, n);
                assertTrue(in.available() <= 22 - got.size());
            }
            assertEquals(-1, n);
            assertEquals(-1, in.read());
            assertEquals(0, in.available());
            assertArrayEquals(pcm, got.toByteArray());
            assertEquals(0, in.read(small, 0, 0));
        }
    }

    @Test
    public void closeIsIdempotentAndReadsAfterCloseFail() throws Exception {
        AudioInputStream in = open(canonical(2, 44100, sinePcm(2, 44100, 5, 440)));
        in.close();
        in.close();
        try {
            in.read(new byte[4], 0, 4);
            fail("expected IOException");
        } catch (IOException expected) {
            // closed
        }
    }

    @Test
    public void readRejectsBadRanges() throws Exception {
        try (AudioInputStream in = open(canonical(2, 44100, sinePcm(2, 44100, 5, 440)))) {
            try {
                in.read(new byte[4], 2, 4);
                fail("expected IndexOutOfBoundsException");
            } catch (IndexOutOfBoundsException expected) {
                // range outside the array
            }
        }
    }

    @Test
    public void soundBankDecodeSequenceTakesTheAlreadyPcmBranch() throws Exception {
        // Mirrors SoundBank.decode (audio/SoundBank.java:311-329) statement by statement.
        byte[] pcm = sinePcm(2, 22050, 120, 330);
        InputStream raw = new ByteArrayInputStream(canonical(2, 22050, pcm));
        byte[] bytes;
        AudioFormat format;
        try (AudioInputStream in =
                AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
            AudioFormat source = in.getFormat();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(), 16, source.getChannels(), source.getChannels() * 2,
                    source.getSampleRate(), false);
            boolean alreadyPcm = AudioFormat.Encoding.PCM_SIGNED.equals(source.getEncoding())
                    && source.getSampleSizeInBits() == 16 && !source.isBigEndian();
            assertTrue(alreadyPcm);
            try (AudioInputStream pcmStream =
                    alreadyPcm ? in : AudioSystem.getAudioInputStream(target, in)) {
                bytes = pcmStream.readAllBytes();
                format = pcmStream.getFormat();
            }
        }
        assertArrayEquals(pcm, bytes);
        assertEquals(2, format.getChannels());
        assertEquals(22050f, format.getSampleRate(), 0f);
    }

    @Test
    public void conversionOverloadReturnsAMatchingStreamAndRefusesConversion()
            throws Exception {
        try (AudioInputStream in = open(canonical(2, 44100, sinePcm(2, 44100, 5, 440)))) {
            AudioFormat same = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2,
                    4, 44100f, false);
            assertSame(in, AudioSystem.getAudioInputStream(same, in));
            try {
                AudioSystem.getAudioInputStream(new AudioFormat(22050, 16, 1, true, false), in);
                fail("expected UnsupportedOperationException");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("census"));
            }
        }
    }

    // ------------------------------------------------------------------ rejections

    @Test
    public void nonRiffStreamsAreUnsupported() throws Exception {
        assertUnsupported("OggS ... an ogg page, or anything else, really"
                .getBytes(StandardCharsets.ISO_8859_1), "not a RIFF/WAVE");
        assertUnsupported("RIFF".getBytes(StandardCharsets.US_ASCII), "too short");
        assertUnsupported(new byte[0], "too short");
        byte[] avi = canonical(1, 8000, new byte[4]);
        System.arraycopy("AVI ".getBytes(StandardCharsets.US_ASCII), 0, avi, 8, 4);
        assertUnsupported(avi, "not a RIFF/WAVE");
    }

    @Test
    public void nonPcmAndNon16BitWavesAreUnsupported() throws Exception {
        byte[] body = new byte[8];
        assertUnsupported(riff(fmt16(TAG_IEEE_FLOAT, 2, 44100, 32), chunk("data", body)),
                "is not PCM");
        assertUnsupported(riff(fmt16(TAG_PCM, 1, 8000, 8), chunk("data", body)), "8-bit");
        assertUnsupported(riff(fmt16(TAG_PCM, 2, 48000, 24), chunk("data", body)), "24-bit");
        assertUnsupported(riff(fmt40(2, 44100, 32, TAG_IEEE_FLOAT), chunk("data", body)),
                "is not PCM");
        assertUnsupported(riff(fmt16(TAG_EXTENSIBLE, 2, 44100, 16), chunk("data", body)),
                "extensible fmt chunk");
        assertUnsupported(riff(fmt16(TAG_PCM, 0, 44100, 16), chunk("data", body)),
                "no channels");
        assertUnsupported(riff(fmt16(TAG_PCM, 2, 0, 16), chunk("data", body)), "sample rate");
    }

    @Test
    public void missingOrMisorderedChunksAreUnsupported() throws Exception {
        byte[] body = new byte[8];
        assertUnsupported(riff(fmt16(TAG_PCM, 2, 44100, 16)), "no data chunk");
        assertUnsupported(riff(chunk("data", body), fmt16(TAG_PCM, 2, 44100, 16)),
                "before the fmt chunk");
        assertUnsupported(riff(chunk("fmt ", new byte[12]), chunk("data", body)),
                "fmt chunk of 12 bytes");
    }

    @Test
    public void truncationInsideADeclaredChunkIsAnIoException() throws Exception {
        byte[] wave = riff(fmt16(TAG_PCM, 2, 44100, 16), chunk("data", new byte[8]));
        byte[] cutInsideFmt = Arrays.copyOf(wave, 24);
        try (AudioInputStream in = open(cutInsideFmt)) {
            fail("expected EOFException, got " + in.getFormat());
        } catch (EOFException expected) {
            assertTrue(expected.getMessage().contains("fmt"));
        }
        byte[] withList = riff(chunk("LIST", new byte[40]), fmt16(TAG_PCM, 2, 44100, 16),
                chunk("data", new byte[8]));
        try (AudioInputStream in = open(Arrays.copyOf(withList, 30))) {
            fail("expected EOFException, got " + in.getFormat());
        } catch (EOFException expected) {
            assertTrue(expected.getMessage().contains("chunk"));
        }
    }

    @Test
    public void nullStreamIsRejected() throws Exception {
        try {
            AudioSystem.getAudioInputStream((InputStream) null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // AWT parity
        }
    }
}
