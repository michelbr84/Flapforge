package jssound;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.AudioTrack;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAudioTrack;

/**
 * Robolectric proofs of the {@link SourceDataLine} shim over {@code ShadowAudioTrack}: the
 * exact lifecycle {@code SoftwareMixer} drives (open with four passes of buffer, start, write,
 * stop, flush, close), the bytes and track configuration that reach the platform, the bounded
 * {@link SourceDataLine#drain()}, and the validation of formats and write ranges.
 *
 * <p>Shadow facts these tests lean on (Robolectric 4.16): {@code getMinBufferSize} answers a
 * settable constant (1024 by default), a write is always taken in full, and the playback head
 * sits at the last written frame — the shadow consumes instantly — so a drain finishes on its
 * first poll and {@link SourceDataLine#available()} is the whole buffer after every write.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SourceDataLineTest {

    /** SoftwareMixer.FORMAT: 44.1 kHz, 16-bit, stereo, signed, little-endian. */
    private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);
    private static final AudioFormat MONO_22K = new AudioFormat(22050, 16, 1, true, false);
    /** SoftwareMixer: FRAMES_PER_PASS (1323) * CHANNELS * (BITS / 8) * BUFFER_PASSES. */
    private static final int MIXER_BUFFER = 1323 * 2 * 2 * 4;
    /** Slack for the drain timing assertions: poll granularity plus JVM scheduling. */
    private static final long TIMING_SLACK_MS = 100;

    private final ByteArrayOutputStream received = new ByteArrayOutputStream();
    private final List<android.media.AudioFormat> trackFormats = new ArrayList<>();
    private final ShadowAudioTrack.OnAudioDataWrittenListener listener =
            (track, data, format) -> {
                received.write(data, 0, data.length);
                trackFormats.add(format);
            };

    @Before
    public void listen() {
        ShadowAudioTrack.addAudioDataListener(listener);
    }

    @After
    public void reset() {
        ShadowAudioTrack.removeAudioDataListener(listener);
        ShadowAudioTrack.resetTest();
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    @Test
    public void mixerLifecycleReachesTheTrackByteForByte() throws Exception {
        SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
        assertNotNull(line);
        assertEquals(0, line.getBufferSize());
        assertEquals(0, line.available());

        line.open(FORMAT, MIXER_BUFFER); // SoftwareMixer.java:213
        assertEquals(MIXER_BUFFER, line.getBufferSize());
        line.start(); // :214

        byte[] pass = WaveFixtures.sinePcm(2, 44100, 1323, 440); // one 30 ms mixer pass
        for (int i = 0; i < 4; i++) {
            assertTrue(line.available() >= 0 && line.available() <= MIXER_BUFFER);
            assertEquals(pass.length, line.write(pass, 0, pass.length)); // :578
        }
        assertArrayEquals(WaveFixtures.concat(pass, pass, pass, pass), received.toByteArray());
        assertFalse(trackFormats.isEmpty());
        android.media.AudioFormat onTrack = trackFormats.get(0);
        assertEquals(44100, onTrack.getSampleRate());
        assertEquals(android.media.AudioFormat.ENCODING_PCM_16BIT, onTrack.getEncoding());
        assertEquals(android.media.AudioFormat.CHANNEL_OUT_STEREO, onTrack.getChannelMask());

        long start = System.nanoTime();
        line.drain();
        long bound = SourceDataLine.drainBoundMillis(4 * 1323, 44100);
        assertEquals(120 + SourceDataLine.DRAIN_GRACE_MS, bound);
        assertTrue("drain took " + elapsedMillis(start) + " ms, bound " + bound,
                elapsedMillis(start) <= bound + TIMING_SLACK_MS);
        // The shadow consumed everything: nothing is queued, the whole buffer is free.
        assertEquals(MIXER_BUFFER, line.available());

        line.stop(); // SoftwareMixer.close, :318
        line.flush(); // :319
        line.close(); // :332
        line.close(); // idempotent
        assertEquals(0, line.available());
        try {
            line.write(pass, 0, pass.length);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("not open"));
        }
        line.stop(); // nothing to stop, nothing thrown
        line.flush();
        line.drain();
    }

    @Test
    public void monoLineConfiguresAMonoTrackAndDefaultBufferIsHalfASecond() throws Exception {
        SourceDataLine line = AudioSystem.getSourceDataLine(MONO_22K);
        line.open(MONO_22K);
        assertEquals(22050 / 2 * 2, line.getBufferSize()); // 500 ms of 2-byte frames
        line.start();
        byte[] pcm = WaveFixtures.sinePcm(1, 22050, 2205, 220);
        assertEquals(pcm.length, line.write(pcm, 0, pcm.length));
        assertArrayEquals(pcm, received.toByteArray());
        android.media.AudioFormat onTrack = trackFormats.get(0);
        assertEquals(22050, onTrack.getSampleRate());
        assertEquals(android.media.AudioFormat.CHANNEL_OUT_MONO, onTrack.getChannelMask());
        line.close();
    }

    @Test
    public void bufferIsRaisedToThePlatformMinimumAndRoundedToFrames() throws Exception {
        ShadowAudioTrack.setMinBufferSize(65538); // not a multiple of the 4-byte frame
        SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
        line.open(FORMAT, 4096);
        assertEquals(65540, line.getBufferSize());
        line.close();

        ShadowAudioTrack.setMinBufferSize(1024);
        SourceDataLine larger = AudioSystem.getSourceDataLine(FORMAT);
        larger.open(FORMAT, 4098); // rounded to frames, still above the minimum
        assertEquals(4100, larger.getBufferSize());
        larger.close();
    }

    @Test
    public void platformWithoutABufferSizeMakesTheLineUnavailable() {
        ShadowAudioTrack.setMinBufferSize(AudioTrack.ERROR_BAD_VALUE);
        try {
            AudioSystem.getSourceDataLine(FORMAT);
            fail("expected LineUnavailableException");
        } catch (LineUnavailableException expected) {
            assertTrue(expected.getMessage().contains("no buffer size"));
        }
    }

    @Test
    public void unsupportedFormatsAreRejectedLikeAwtsMixerLookup() throws Exception {
        AudioFormat[] rejected = {
            new AudioFormat(8000, 8, 1, true, false), // 8-bit
            new AudioFormat(44100, 16, 2, true, true), // big-endian
            new AudioFormat(44100, 16, 6, true, false), // 5.1
            new AudioFormat(44100.5f, 16, 2, true, false), // fractional rate
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 6, 44100f, false),
        };
        for (AudioFormat format : rejected) {
            try {
                AudioSystem.getSourceDataLine(format);
                fail("expected IllegalArgumentException for " + format);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("no output line"));
            }
        }
        SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
        try {
            line.open(rejected[0], MIXER_BUFFER);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("no output line"));
        }
        try {
            line.open(FORMAT, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("buffer size 0"));
        }
        line.close();
    }

    @Test
    public void writeValidatesStateFramesAndRange() throws Exception {
        SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
        byte[] pcm = new byte[16];
        try {
            line.write(pcm, 0, pcm.length);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("not open"));
        }
        try {
            line.start();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("not open"));
        }
        line.open(FORMAT, MIXER_BUFFER);
        line.start();
        try {
            line.write(pcm, 0, 6);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("frames"));
        }
        try {
            line.write(pcm, 8, 12);
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // outside the array
        }
        assertEquals(0, line.write(pcm, 4, 0));
        assertEquals(8, line.write(pcm, 4, 8));
        assertEquals(8, received.size());
        line.close();
    }

    @Test
    public void openTwiceOrAfterCloseIsRefused() throws Exception {
        SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
        line.open(FORMAT, MIXER_BUFFER);
        try {
            line.open(FORMAT, MIXER_BUFFER);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("already open"));
        }
        line.close();
        try {
            line.open(FORMAT, MIXER_BUFFER);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
        // A never-opened line closes quietly, twice.
        SourceDataLine unopened = AudioSystem.getSourceDataLine(FORMAT);
        unopened.close();
        unopened.close();
    }

    @Test
    public void drainReturnsAtOnceOnAPausedOrIdleLine() throws Exception {
        SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
        long start = System.nanoTime();
        line.drain(); // not open
        line.open(FORMAT, MIXER_BUFFER);
        line.drain(); // open, never started
        line.start();
        byte[] pass = WaveFixtures.sinePcm(2, 44100, 1323, 440);
        line.write(pass, 0, pass.length);
        line.stop();
        line.drain(); // paused: the head cannot advance, so there is nothing to wait for
        assertTrue("drain calls took " + elapsedMillis(start) + " ms",
                elapsedMillis(start) < TIMING_SLACK_MS);
        line.close();
    }

    @Test
    public void flushWhilePausedResetsTheQueueBookkeeping() throws Exception {
        SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
        line.open(FORMAT, MIXER_BUFFER);
        line.start();
        byte[] pass = WaveFixtures.sinePcm(2, 44100, 1323, 440);
        line.write(pass, 0, pass.length);
        line.stop();
        line.flush(); // the shadow resets its head to 0 here, as the platform does when paused
        assertEquals(MIXER_BUFFER, line.available());
        line.start();
        assertEquals(pass.length, line.write(pass, 0, pass.length));
        assertEquals(MIXER_BUFFER, line.available());
        line.close();
    }

    @Test
    public void drainBoundIsQueuedDurationRoundedUpPlusGrace() {
        assertEquals(250, SourceDataLine.DRAIN_GRACE_MS);
        assertEquals(250, SourceDataLine.drainBoundMillis(0, 44100));
        assertEquals(251, SourceDataLine.drainBoundMillis(1, 44100));
        assertEquals(350, SourceDataLine.drainBoundMillis(4410, 44100));
        assertEquals(1250, SourceDataLine.drainBoundMillis(22050, 22050));
    }
}
