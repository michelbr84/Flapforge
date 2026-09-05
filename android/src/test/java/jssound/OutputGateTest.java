package jssound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.AudioTrack;
import io.github.michelbr84.flapforge.audio.SoftwareMixer;
import io.github.michelbr84.flapforge.audio.SoundBank;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAudioTrack;

/**
 * Robolectric proofs of the host-only output gate, {@link AudioSystem#suspendOutput()} /
 * {@link AudioSystem#resumeOutput()}, over {@code ShadowAudioTrack}: a suspend pauses the
 * track and parks a writer, a resume plays and releases it, a line opened while suspended
 * stays silent until the resume, and the mixer's own shutdown order — stop, flush, join, close
 * — ends a parked writer within {@link SoftwareMixer#CLOSE_TIMEOUT_MS}, proved both against a
 * bare thread and against the real {@link SoftwareMixer} running over the shim.
 *
 * <p>Shadow facts these tests lean on (Robolectric 4.16): {@code pause}, {@code play},
 * {@code getPlayState} and {@code release} are the platform's own Java (the natives behind
 * them are no-ops), so play states are real and a released track refuses to play; a write is
 * always taken in full and at once, whatever the play state, so every wait observed here is
 * the shim's own. The gate's flag is static and the sandbox class loader is shared by the test
 * classes of one configuration, so every test resumes the output and closes its lines on the
 * way out.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OutputGateTest {

    /** SoftwareMixer.FORMAT: 44.1 kHz, 16-bit, stereo, signed, little-endian. */
    private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);
    /** SoftwareMixer: FRAMES_PER_PASS (1323) * CHANNELS * (BITS / 8) * BUFFER_PASSES. */
    private static final int MIXER_BUFFER = 1323 * 2 * 2 * 4;
    /** One 30 ms mixer pass. */
    private static final byte[] PASS = WaveFixtures.sinePcm(2, 44100, 1323, 440);
    /** How long a parked write must stay parked before a test believes it is parked. */
    private static final long PARK_MS = 300;
    /** Upper bound for anything that should happen promptly once released. */
    private static final long RELEASE_MS = 2_000;
    /** Slack for "returns at once": poll granularity plus JVM scheduling. */
    private static final long TIMING_SLACK_MS = 100;

    private final ByteArrayOutputStream received = new ByteArrayOutputStream();
    private final ShadowAudioTrack.OnAudioDataWrittenListener listener =
            (track, data, format) -> received.write(data, 0, data.length);
    private final List<SourceDataLine> lines = new ArrayList<>();
    private final List<Thread> workers = new ArrayList<>();
    private SoftwareMixer mixer;

    @Before
    public void listen() {
        ShadowAudioTrack.addAudioDataListener(listener);
    }

    @After
    public void releaseEverything() throws InterruptedException {
        AudioSystem.resumeOutput();
        if (mixer != null) {
            mixer.close();
        }
        for (SourceDataLine line : lines) {
            line.close();
        }
        for (Thread worker : workers) {
            worker.join(RELEASE_MS);
        }
        ShadowAudioTrack.removeAudioDataListener(listener);
        ShadowAudioTrack.resetTest();
    }

    // ------------------------------------------------------------------ (a) suspend / resume

    @Test
    public void suspendPausesAStartedLineAndParksItsWriterUntilResume() throws Exception {
        SourceDataLine line = openLine();
        line.start();
        assertEquals(PASS.length, line.write(PASS, 0, PASS.length));
        assertEquals(AudioTrack.PLAYSTATE_PLAYING, line.track().getPlayState());
        assertFalse(AudioSystem.isOutputSuspended());

        AudioSystem.suspendOutput();
        assertTrue(AudioSystem.isOutputSuspended());
        assertEquals(AudioTrack.PLAYSTATE_PAUSED, line.track().getPlayState());

        Writer writer = writeAsync(line);
        writer.assertParked();
        assertEquals("nothing may reach the track while suspended", PASS.length,
                received.size());

        AudioSystem.resumeOutput();
        assertFalse(AudioSystem.isOutputSuspended());
        assertEquals(PASS.length, (int) writer.result());
        assertEquals(AudioTrack.PLAYSTATE_PLAYING, line.track().getPlayState());
        assertEquals(2 * PASS.length, received.size());
    }

    @Test
    public void theParkedWriteIsNotInterruptibleButRemembersTheInterrupt() throws Exception {
        SourceDataLine line = openLine();
        line.start();
        AudioSystem.suspendOutput();
        Writer writer = writeAsync(line);
        writer.assertParked();

        writer.thread.interrupt();
        writer.assertParked();

        AudioSystem.resumeOutput();
        assertEquals(PASS.length, (int) writer.result());
        assertTrue("the interrupt is re-asserted when the write returns",
                writer.interruptedOnReturn.get());
    }

    // ------------------------------------------------------------------ (b) stop / close release

    @Test
    public void stopReleasesAParkedWriterAndTheMixersShutdownOrderEndsIt() throws Exception {
        SourceDataLine line = openLine();
        line.start();
        AudioSystem.suspendOutput();
        Writer writer = writeAsync(line);
        writer.assertParked();

        // SoftwareMixer.close: stop (:318), flush (:319), join (:326), close (:332).
        line.stop();
        assertEquals("a stopped line answers a short count of 0", 0, (int) writer.result());
        line.flush();
        writer.thread.join(SoftwareMixer.CLOSE_TIMEOUT_MS);
        assertFalse(writer.thread.isAlive());
        line.close();
        assertEquals(0, received.size());
        assertTrue(AudioSystem.isOutputSuspended());

        try {
            line.write(PASS, 0, PASS.length);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("not open"));
        }
        line.stop(); // nothing to stop, nothing thrown
        line.flush();
        line.drain();
    }

    @Test
    public void closeReleasesAParkedWriterIntoTheClosedLineException() throws Exception {
        SourceDataLine line = openLine();
        line.start();
        AudioSystem.suspendOutput();
        Writer writer = writeAsync(line);
        writer.assertParked();

        line.close();
        try {
            writer.result();
            fail("expected IllegalStateException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertTrue(e.getCause().getMessage().contains("not open"));
        }
        assertEquals(0, received.size());
    }

    /**
     * The real mixer over the shim: its thread parks inside the suspended write (state
     * {@code WAITING} — an {@code Object.wait}, not a spin), no pass completes while it is
     * parked, and {@link SoftwareMixer#close()} returns with the thread gone in well under its
     * join timeout. The shadow takes every write at once, so before the suspend the mixer runs
     * flat out and counts an underrun per pass ({@code available() == getBufferSize()} after
     * every write); neither matters here.
     */
    @Test
    public void softwareMixerClosesWithinItsJoinTimeoutWhileSuspended() throws Exception {
        List<Thread> created = new ArrayList<>();
        List<String> log = new ArrayList<>();
        mixer = new SoftwareMixer(new SoundBank(), r -> {
            Thread t = new Thread(r, "test-mixer");
            created.add(t);
            return t;
        }, () -> AudioSystem.getSourceDataLine(SoftwareMixer.format()), log::add);
        mixer.open();
        assertEquals(1, created.size());
        Thread mixing = created.get(0);
        workers.add(mixing);
        assertTrue(awaitCondition(() -> mixer.passes() > 0));

        AudioSystem.suspendOutput();
        assertTrue("the mixer thread must park in the suspended write",
                awaitCondition(() -> mixing.getState() == Thread.State.WAITING));
        long parkedAt = mixer.passes();
        Thread.sleep(PARK_MS);
        assertEquals("no pass completes while parked", parkedAt, mixer.passes());
        assertEquals(Thread.State.WAITING, mixing.getState());

        long start = System.nanoTime();
        mixer.close(); // stop, flush, join(CLOSE_TIMEOUT_MS), close
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertFalse("the mixer thread ended before the join timeout", mixing.isAlive());
        assertTrue("close took " + elapsed + " ms", elapsed < SoftwareMixer.CLOSE_TIMEOUT_MS);
        assertFalse(mixer.isRunning());
        assertEquals("nothing is logged: the stopped line answers 0, no exception", 0,
                log.size());
        assertTrue("a close does not resume the output", AudioSystem.isOutputSuspended());
    }

    // ------------------------------------------------------------------ (c) opened while suspended

    @Test
    public void aLineOpenedWhileSuspendedStaysSilentUntilResume() throws Exception {
        AudioSystem.suspendOutput();
        SourceDataLine line = openLine();
        line.start();
        assertEquals("play() is deferred: the track was never played",
                AudioTrack.PLAYSTATE_STOPPED, line.track().getPlayState());

        Writer writer = writeAsync(line);
        writer.assertParked();
        assertEquals(0, received.size());

        AudioSystem.resumeOutput();
        assertEquals(PASS.length, (int) writer.result());
        assertEquals(AudioTrack.PLAYSTATE_PLAYING, line.track().getPlayState());
        assertEquals(PASS.length, received.size());
    }

    // ------------------------------------------------------------------ (d) idempotence, no-ops

    @Test
    public void suspendIsIdempotentAndResumeIsNotACounter() throws Exception {
        AudioSystem.resumeOutput(); // nothing suspended, no lines: a no-op
        assertFalse(AudioSystem.isOutputSuspended());
        AudioSystem.suspendOutput(); // no lines yet
        AudioSystem.resumeOutput();

        SourceDataLine line = openLine();
        line.start();
        AudioSystem.suspendOutput();
        AudioSystem.suspendOutput();
        assertTrue(AudioSystem.isOutputSuspended());
        assertEquals(AudioTrack.PLAYSTATE_PAUSED, line.track().getPlayState());

        AudioSystem.resumeOutput(); // one resume undoes two suspends
        assertFalse(AudioSystem.isOutputSuspended());
        assertEquals(AudioTrack.PLAYSTATE_PLAYING, line.track().getPlayState());
        AudioSystem.resumeOutput();
        assertEquals(AudioTrack.PLAYSTATE_PLAYING, line.track().getPlayState());
        assertEquals(PASS.length, line.write(PASS, 0, PASS.length));
    }

    @Test
    public void onlyStartedLinesArePausedAndReplayed() throws Exception {
        SourceDataLine idle = openLine(); // opened, never started
        SourceDataLine stopped = openLine();
        stopped.start();
        stopped.stop();
        assertEquals(AudioTrack.PLAYSTATE_STOPPED, idle.track().getPlayState());
        assertEquals(AudioTrack.PLAYSTATE_PAUSED, stopped.track().getPlayState());

        AudioSystem.suspendOutput();
        assertEquals(AudioTrack.PLAYSTATE_STOPPED, idle.track().getPlayState());
        assertEquals(AudioTrack.PLAYSTATE_PAUSED, stopped.track().getPlayState());
        // A stopped line does not park: its write is the short count, nothing enqueued.
        assertEquals(0, stopped.write(PASS, 0, PASS.length));
        assertEquals(0, received.size());

        AudioSystem.resumeOutput();
        assertEquals("a resume does not start what was never started",
                AudioTrack.PLAYSTATE_STOPPED, idle.track().getPlayState());
        assertEquals("a resume does not restart what was stopped",
                AudioTrack.PLAYSTATE_PAUSED, stopped.track().getPlayState());
        idle.start();
        assertEquals(AudioTrack.PLAYSTATE_PLAYING, idle.track().getPlayState());
    }

    @Test
    public void aLineClosedWhileSuspendedLeavesTheRegistry() throws Exception {
        SourceDataLine line = openLine();
        line.start();
        AudioTrack track = line.track();
        AudioSystem.suspendOutput();
        line.close();
        assertNull(line.track());
        // The resume must not reach the released track: playing it is the platform's
        // IllegalStateException, which is what a registry leak would surface as.
        try {
            track.play();
            fail("expected IllegalStateException from a released track");
        } catch (IllegalStateException expected) {
            // uninitialised after release()
        }
        AudioSystem.resumeOutput();
        assertFalse(AudioSystem.isOutputSuspended());
    }

    @Test
    public void drainAndAvailabilityKeepTheirContractWhileSuspended() throws Exception {
        SourceDataLine line = openLine();
        line.start();
        assertEquals(PASS.length, line.write(PASS, 0, PASS.length));
        AudioSystem.suspendOutput();
        // Opened while suspended, but outside the timed region: building an AudioTrack under
        // Robolectric is the slow part and has nothing to do with drain()'s bound.
        SourceDataLine deferred = openLine();

        long start = System.nanoTime();
        line.drain(); // paused track: nothing can advance, so nothing to wait for
        deferred.start(); // play() deferred: the track never played
        deferred.drain();
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue("drain calls took " + elapsed + " ms", elapsed < TIMING_SLACK_MS);

        assertEquals(MIXER_BUFFER, line.getBufferSize());
        assertTrue(line.available() >= 0 && line.available() <= MIXER_BUFFER);
        assertEquals(MIXER_BUFFER, deferred.available());
    }

    // ------------------------------------------------------------------ helpers

    private SourceDataLine openLine() throws LineUnavailableException {
        SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
        assertNotNull(line);
        line.open(FORMAT, MIXER_BUFFER); // SoftwareMixer.java:213
        lines.add(line);
        return line;
    }

    /** One pass written from a daemon thread, the way the mixer thread writes. */
    private Writer writeAsync(SourceDataLine line) {
        Writer writer = new Writer(line);
        workers.add(writer.thread);
        writer.thread.start();
        return writer;
    }

    /** A write in flight on its own thread: what it returned, and whether it was interrupted. */
    private static final class Writer {

        private final AtomicBoolean interruptedOnReturn = new AtomicBoolean();
        private final FutureTask<Integer> task;
        private final Thread thread;

        private Writer(SourceDataLine line) {
            task = new FutureTask<>(() -> {
                int written = line.write(PASS, 0, PASS.length);
                interruptedOnReturn.set(Thread.currentThread().isInterrupted());
                return written;
            });
            thread = new Thread(task, "test-writer");
            thread.setDaemon(true);
        }

        /** The write has not returned after {@link #PARK_MS} and the thread is in a wait. */
        private void assertParked() throws Exception {
            try {
                task.get(PARK_MS, TimeUnit.MILLISECONDS);
                fail("the write returned while the output was suspended");
            } catch (TimeoutException parked) {
                // still inside write(): the expected outcome
            }
            assertEquals("parked in Object.wait, not spinning", Thread.State.WAITING,
                    thread.getState());
        }

        private Integer result() throws Exception {
            return task.get(RELEASE_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** Polls a condition every few milliseconds for up to {@link #RELEASE_MS}. */
    private static boolean awaitCondition(Condition condition) throws InterruptedException {
        long deadline = System.nanoTime() + RELEASE_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.holds()) {
                return true;
            }
            Thread.sleep(5);
        }
        return condition.holds();
    }

    @FunctionalInterface
    private interface Condition {
        boolean holds();
    }
}
