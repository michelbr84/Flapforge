package io.github.michelbr84.flapforge.audio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.support.FakeSourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import javax.sound.sampled.LineUnavailableException;
import org.junit.jupiter.api.Test;

/**
 * The mixer is exercised without a sound card. Most cases drive
 * {@link SoftwareMixer#render(float[], int)} directly on the test thread — that method is the
 * whole signal path (queue drain, voice summing, master gain, limiter) minus the line — and the
 * one case that needs the real open/write/close path injects a {@link FakeSourceDataLine} and a
 * thread factory, so no test ever asks {@code AudioSystem} for anything.
 */
class SoftwareMixerTest {

    private static final int FRAMES = 512;
    /** Per-channel factor of a centred voice under the constant-power pan law. */
    private static final float CENTRE = (float) StrictMath.cos(StrictMath.PI / 4.0);

    /** A factory that hands out a thread but never lets one escape the test unnoticed. */
    private static final class RecordingThreadFactory implements ThreadFactory {

        private final List<Thread> created = new ArrayList<>();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "test-audio");
            t.setDaemon(true);
            created.add(t);
            return t;
        }
    }

    /** A mixer that can never open a line, for the cases that only need the signal path. */
    private static SoftwareMixer offline(SoundBank bank) {
        return new SoftwareMixer(bank, r -> {
            throw new IllegalStateException("this test must not start a mixing thread");
        }, () -> {
            throw new LineUnavailableException("this test must not open a line");
        }, message -> {
        });
    }

    @Test
    void mixesTwoVoicesTogetherWithoutClippingPastTheLimiter() {
        SoftwareMixer mixer = offline(new SoundBank());
        float[] one = new float[FRAMES * 2];
        mixer.play(ToneSynth.CRASH, 1.0f, 0.0f);
        mixer.render(one, FRAMES);
        double singleEnergy = energy(one, 0) + energy(one, 1);

        SoftwareMixer both = offline(new SoundBank());
        both.play(ToneSynth.CRASH, 1.0f, 0.0f);
        both.play(ToneSynth.BOSS_WARNING, 1.0f, 0.0f);
        float[] summed = new float[FRAMES * 2];
        assertEquals(2, both.render(summed, FRAMES), "both voices stay in flight");
        double summedEnergy = energy(summed, 0) + energy(summed, 1);

        // Energy, not peak: two uncorrelated sounds always add power, while their peaks can
        // happen to cancel at the one sample the maximum lands on.
        assertTrue(summedEnergy > singleEnergy,
                "two voices must carry more energy than one: " + summedEnergy + " vs "
                        + singleEnergy);
        assertTrue(peak(summed) < 1.0f,
                "the limiter must keep the sum inside full scale: " + peak(summed));
        for (float sample : summed) {
            assertTrue(Float.isFinite(sample));
        }
    }

    @Test
    void limiterIsTransparentBelowTheKneeAndBoundedAboveIt() {
        assertEquals(0.5f, SoftwareMixer.limit(0.5f), 0.0f);
        assertEquals(-SoftwareMixer.LIMITER_KNEE, SoftwareMixer.limit(-SoftwareMixer.LIMITER_KNEE),
                0.0f);
        // Bounded by full scale, however loud the sum gets, and smooth on the way there.
        assertTrue(SoftwareMixer.limit(4.0f) <= 1.0f);
        assertTrue(SoftwareMixer.limit(Float.MAX_VALUE) <= 1.0f);
        assertTrue(SoftwareMixer.limit(0.9f) > SoftwareMixer.LIMITER_KNEE);
        assertTrue(SoftwareMixer.limit(0.9f) < 0.9f, "above the knee the signal is compressed");
        assertEquals(-SoftwareMixer.limit(4.0f), SoftwareMixer.limit(-4.0f), 1e-7f);
        assertEquals(0.0f, SoftwareMixer.limit(Float.NaN), 0.0f);
    }

    @Test
    void appliesGainAndPan() {
        SoftwareMixer left = offline(new SoundBank());
        left.play(ToneSynth.FLAP, 1.0f, -1.0f);
        float[] buffer = new float[FRAMES * 2];
        left.render(buffer, FRAMES);
        assertTrue(energy(buffer, 0) > 0.0, "a hard-left voice must reach the left channel");
        assertEquals(0.0, energy(buffer, 1), 1e-9, "a hard-left voice must not leak to the right");

        SoftwareMixer right = offline(new SoundBank());
        right.play(ToneSynth.FLAP, 1.0f, 1.0f);
        float[] rightBuffer = new float[FRAMES * 2];
        right.render(rightBuffer, FRAMES);
        assertEquals(0.0, energy(rightBuffer, 0), 1e-9);
        assertTrue(energy(rightBuffer, 1) > 0.0);

        SoftwareMixer quiet = offline(new SoundBank());
        quiet.play(ToneSynth.FLAP, 0.25f, 0.0f);
        float[] quietBuffer = new float[FRAMES * 2];
        quiet.render(quietBuffer, FRAMES);
        SoftwareMixer loud = offline(new SoundBank());
        loud.play(ToneSynth.FLAP, 1.0f, 0.0f);
        float[] loudBuffer = new float[FRAMES * 2];
        loud.render(loudBuffer, FRAMES);
        assertEquals(0.25, peak(quietBuffer) / (double) peak(loudBuffer), 0.02,
                "gain must scale the output linearly below the limiter knee");
    }

    @Test
    void masterGainScalesEverything() {
        SoftwareMixer mixer = offline(new SoundBank());
        mixer.play(ToneSynth.FLAP, 1.0f, 0.0f);
        float[] full = new float[FRAMES * 2];
        mixer.render(full, FRAMES);

        SoftwareMixer halved = offline(new SoundBank());
        halved.setMasterGain(0.5f);
        halved.play(ToneSynth.FLAP, 1.0f, 0.0f);
        float[] half = new float[FRAMES * 2];
        halved.render(half, FRAMES);
        assertEquals(0.5, peak(half) / (double) peak(full), 0.02);

        SoftwareMixer silent = offline(new SoundBank());
        silent.setMasterGain(0.0f);
        silent.play(ToneSynth.FLAP, 1.0f, 0.0f);
        float[] none = new float[FRAMES * 2];
        silent.render(none, FRAMES);
        assertEquals(0.0f, peak(none), 0.0f);
    }

    @Test
    void commandQueueDropsInsteadOfBlockingWhenFull() {
        SoftwareMixer mixer = offline(new SoundBank());
        int requests = SoftwareMixer.COMMAND_CAPACITY * 4;
        long start = System.nanoTime();
        for (int i = 0; i < requests; i++) {
            mixer.play(ToneSynth.FLAP, 1.0f, 0.0f);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(mixer.droppedCommands() > 0,
                "a full queue must drop, not grow: dropped " + mixer.droppedCommands());
        assertEquals(requests - SoftwareMixer.COMMAND_CAPACITY, mixer.droppedCommands());
        assertTrue(elapsedMs < 1_000L, "play() blocked for " + elapsedMs + " ms");

        float[] buffer = new float[FRAMES * 2];
        assertEquals(SoftwareMixer.MAX_VOICES, mixer.render(buffer, FRAMES),
                "voice count is capped");
        assertTrue(mixer.stolenVoices() > 0);
    }

    @Test
    void stopAllDropsQueuedAndActiveVoices() {
        SoftwareMixer mixer = offline(new SoundBank());
        mixer.play(ToneSynth.CRASH, 1.0f, 0.0f);
        float[] buffer = new float[FRAMES * 2];
        assertEquals(1, mixer.render(buffer, FRAMES));

        mixer.play(ToneSynth.CRASH, 1.0f, 0.0f);
        mixer.stopAll();
        assertEquals(0, mixer.render(buffer, FRAMES), "stopAll cancels queued plays too");
        assertEquals(0.0f, peak(buffer), 0.0f);
    }

    @Test
    void unknownIdsAreSynthesisedRatherThanDroppingTheVoice() {
        SoundBank bank = new SoundBank();
        SoftwareMixer mixer = offline(bank);
        mixer.play("a_sound_nobody_authored", 1.0f, 0.0f);
        float[] buffer = new float[FRAMES * 2];
        assertEquals(1, mixer.render(buffer, FRAMES));
        assertTrue(peak(buffer) > 0.0f);
        assertTrue(bank.isSynthesised("a_sound_nobody_authored"));
    }

    @Test
    void opensWritesToTheLineAndReleasesItOnClose() throws Exception {
        FakeSourceDataLine line = new FakeSourceDataLine();
        RecordingThreadFactory factory = new RecordingThreadFactory();
        List<String> log = new ArrayList<>();
        SoftwareMixer mixer = new SoftwareMixer(new SoundBank(), factory, () -> line, log::add);

        // Queued before the thread starts, so the very first buffer written already has signal.
        mixer.play(ToneSynth.SCORE, 1.0f, 0.0f);
        mixer.open();
        assertTrue(mixer.isRunning());
        assertTrue(mixer.isRealDevice());
        assertEquals(1, line.opens());
        assertSame(SoftwareMixer.format(), line.getFormat());
        assertEquals(1, factory.created.size());
        assertTrue(factory.created.get(0).isDaemon(), "the mixing thread must be a daemon");

        assertTrue(line.awaitFirstWrite(5_000L), "the mixer never wrote to the line");
        byte[] written = line.captured();
        assertTrue(written.length > 0);
        assertTrue(hasSignal(written), "the first buffer was silent");

        mixer.close();
        assertFalse(mixer.isRunning());
        assertEquals(1, line.closes(), "close() must release the line exactly once");
        assertTrue(line.flushes() > 0, "close() flushes so a parked write returns");
        factory.created.get(0).join(5_000L);
        assertFalse(factory.created.get(0).isAlive(), "the mixing thread must stop");
        assertEquals(List.of(), log, "a clean run must not log");

        mixer.close();
        assertEquals(1, line.closes(), "close() is idempotent");
    }

    @Test
    void openFailsWithoutLeavingAThreadOrALineBehind() {
        FakeSourceDataLine line = new FakeSourceDataLine();
        SoftwareMixer mixer = new SoftwareMixer(new SoundBank(), r -> {
            throw new SecurityException("threads are not allowed here");
        }, () -> line, message -> {
        });
        SecurityException thrown = assertThrows(SecurityException.class, mixer::open);
        assertNotNull(thrown.getMessage());
        assertEquals(0, line.opens(), "the line must not be touched when no thread can be made");
        assertFalse(mixer.isRunning());
    }

    @Test
    void aNullThreadFromTheFactoryIsAFailureNotANullPointer() {
        SoftwareMixer mixer = new SoftwareMixer(new SoundBank(), r -> null,
                FakeSourceDataLine::new, message -> {
                });
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, mixer::open);
        assertTrue(thrown.getMessage().contains("thread"));
    }

    @Test
    void aDrainedLineIsCountedAsAnUnderrunAndAFullOneIsNot() throws Exception {
        FakeSourceDataLine line = new FakeSourceDataLine();
        RecordingThreadFactory factory = new RecordingThreadFactory();
        SoftwareMixer mixer = new SoftwareMixer(new SoundBank(), factory, () -> line, message -> {
        });
        // Every pass finds the device completely free, which is what running out of audio looks
        // like. The check has to sit after the mixing work: straight after the previous blocking
        // write returned, the buffer is as full as it will ever be.
        line.setAvailable(line::getBufferSize);
        mixer.open();
        assertTrue(line.awaitFirstWrite(5_000L), "the mixer never wrote to the line");
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (mixer.underruns() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(mixer.underruns() > 0, "a completely drained line is an underrun");
        mixer.close();
        factory.created.get(0).join(5_000L);

        FakeSourceDataLine healthy = new FakeSourceDataLine();
        RecordingThreadFactory healthyFactory = new RecordingThreadFactory();
        SoftwareMixer fed = new SoftwareMixer(new SoundBank(), healthyFactory, () -> healthy,
                message -> {
                });
        fed.open();
        assertTrue(healthy.awaitFirstWrite(5_000L), "the mixer never wrote to the line");
        while (fed.passes() < 3) {
            Thread.sleep(1);
        }
        assertEquals(0, fed.underruns(), "a line with audio left to play is not an underrun");
        fed.close();
        healthyFactory.created.get(0).join(5_000L);
    }

    @Test
    void theLineIsOpenedWithHeadroomOverOneWrite() {
        FakeSourceDataLine line = new FakeSourceDataLine();
        RecordingThreadFactory factory = new RecordingThreadFactory();
        SoftwareMixer mixer = new SoftwareMixer(new SoundBank(), factory, () -> line, message -> {
        });
        assertDoesNotThrow(mixer::open);
        int pass = SoftwareMixer.framesPerPass() * SoftwareMixer.CHANNELS
                * (SoftwareMixer.BITS / 8);
        assertEquals(pass * SoftwareMixer.BUFFER_PASSES, line.getBufferSize(),
                "one pass of buffer would starve the device on any slow pass");
        mixer.close();
    }

    @Test
    void reportsItsOutputFormat() {
        assertEquals(SoundBank.SAMPLE_RATE, (int) SoftwareMixer.format().getSampleRate());
        assertEquals(SoftwareMixer.BITS, SoftwareMixer.format().getSampleSizeInBits());
        assertEquals(SoundBank.CHANNELS, SoftwareMixer.format().getChannels());
        assertFalse(SoftwareMixer.format().isBigEndian());
        assertEquals(SoundBank.SAMPLE_RATE * SoftwareMixer.BUFFER_MS / 1000,
                SoftwareMixer.framesPerPass());
        assertSame(SoftwareMixer.format(), SoftwareMixer.format());
    }

    private static float peak(float[] samples) {
        float peak = 0.0f;
        for (float sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    private static double energy(float[] interleaved, int channel) {
        double sum = 0.0;
        for (int i = channel; i < interleaved.length; i += 2) {
            sum += (double) interleaved[i] * interleaved[i];
        }
        return sum;
    }

    private static boolean hasSignal(byte[] pcm) {
        for (byte value : pcm) {
            if (value != 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ music loops (M8, D19)

    /** A constant full-scale stereo loop of the given frame count. */
    private static float[] flatLoop(int frames) {
        float[] samples = new float[frames * SoundBank.CHANNELS];
        java.util.Arrays.fill(samples, 1.0f);
        return samples;
    }

    @Test
    void aRegisteredLoopPlaysOnceAndRetargetingDoesNotDoubleIt() {
        SoftwareMixer mixer = offline(new SoundBank());
        mixer.registerLoop("music/test", flatLoop(FRAMES));
        assertTrue(mixer.hasLoop("music/test"));

        float[] out = new float[FRAMES * 2];
        mixer.playLooping("music/test", 0.5f, 0.0f);
        for (int i = 0; i < SoftwareMixer.MUSIC_RAMP_FRAMES / FRAMES + 1; i++) {
            mixer.render(out, FRAMES);
        }
        // A centred voice splits its gain across both channels (constant power), so the flat
        // loop's peak is the gain times the centre factor.
        assertEquals(0.5f * CENTRE, peak(out), 0.02f, "one looping voice at its target gain");

        // A retarget moves the same voice; it must not start a second copy.
        mixer.playLooping("music/test", 0.3f, 0.0f);
        for (int i = 0; i < SoftwareMixer.MUSIC_RAMP_FRAMES / FRAMES + 1; i++) {
            mixer.render(out, FRAMES);
        }
        assertEquals(0.3f * CENTRE, peak(out), 0.02f,
                "a retarget walked the one voice, it did not stack a second");
    }

    @Test
    void stoppingALoopFadesItToSilenceAndDropsIt() {
        SoftwareMixer mixer = offline(new SoundBank());
        mixer.registerLoop("music/test", flatLoop(FRAMES));
        mixer.playLooping("music/test", 0.6f, 0.0f);
        float[] out = new float[FRAMES * 2];
        for (int i = 0; i < SoftwareMixer.MUSIC_RAMP_FRAMES / FRAMES; i++) {
            mixer.render(out, FRAMES);
        }
        assertTrue(peak(out) > 0.1f, "the loop is audible before the stop");

        mixer.stopLooping("music/test");
        for (int i = 0; i < SoftwareMixer.MUSIC_RAMP_FRAMES / FRAMES + 1; i++) {
            mixer.render(out, FRAMES);
        }
        assertTrue(peak(out) < 1e-3f, () -> "the faded loop reaches silence and is dropped: "
                + peak(out));
        assertTrue(mixer.hasLoop("music/test"), "the bank keeps the rendered loop registered");
    }

    @Test
    void aCrossfadeBetweenTwoLoopsHoldsTheLoudness() {
        SoftwareMixer mixer = offline(new SoundBank());
        mixer.registerLoop("music/from", flatLoop(FRAMES));
        mixer.registerLoop("music/to", flatLoop(FRAMES));
        mixer.playLooping("music/from", 0.5f, 0.0f);
        float[] out = new float[FRAMES * 2];
        for (int i = 0; i < SoftwareMixer.MUSIC_RAMP_FRAMES / FRAMES + 1; i++) {
            mixer.render(out, FRAMES);
        }
        assertEquals(0.5f * CENTRE, peak(out), 0.02f, "the outgoing loop is settled");

        // What AudioManager does on a screen change: fade the old loop out while the new fades
        // in. Two equal linear ramps at the same target sum to roughly constant loudness.
        mixer.stopLooping("music/from");
        mixer.playLooping("music/to", 0.5f, 0.0f);
        int half = SoftwareMixer.MUSIC_RAMP_FRAMES / 2 / FRAMES;
        for (int i = 0; i < half; i++) {
            mixer.render(out, FRAMES);
        }
        assertTrue(peak(out) > 0.22f && peak(out) < 0.42f,
                () -> "mid-crossfade the loudness is held: " + peak(out));
        for (int i = 0; i < SoftwareMixer.MUSIC_RAMP_FRAMES / FRAMES + 1; i++) {
            mixer.render(out, FRAMES);
        }
        assertEquals(0.5f * CENTRE, peak(out), 0.02f,
                "only the incoming loop survives the crossfade");
    }

    /**
     * The crossfade, the fade-out and the duck all walk at the same speed —
     * {@link SoftwareMixer#MUSIC_RAMP_FRAMES} — so the length is pinned exactly here: one frame
     * short of the ramp both loops are still moving, and the ramp's last frame lands on the
     * target (the outgoing loop dropped, the incoming one settled).
     */
    @Test
    void theMusicRampIsExactlyMUSIC_RAMP_FRAMESLong() {
        SoftwareMixer mixer = offline(new SoundBank());
        mixer.registerLoop("music/only", flatLoop(FRAMES));
        float[] out = new float[FRAMES * 2];

        // Fade-in: still short of the target one frame before the ramp is over...
        mixer.playLooping("music/only", 0.5f, 0.0f);
        renderFrames(mixer, out, SoftwareMixer.MUSIC_RAMP_FRAMES - 1);
        float before = peak(out);
        assertTrue(before > 0.0f && before < 0.5f * CENTRE,
                () -> "one frame short of the ramp the fade-in is still moving: " + before);
        // ...and the ramp's last frame lands exactly on it.
        renderFrames(mixer, out, 1);
        assertEquals(0.5f * CENTRE, peak(out), 1e-4f,
                "the fade-in takes exactly MUSIC_RAMP_FRAMES frames");

        // Fade-out, the other half of a crossfade: still audible one frame before the end...
        mixer.stopLooping("music/only");
        renderFrames(mixer, out, SoftwareMixer.MUSIC_RAMP_FRAMES - 1);
        assertTrue(peak(out) > 0.0f, "one frame short of the ramp the fade-out still sounds");
        // ...and silent on the ramp's last frame, which is where the voice is dropped.
        renderFrames(mixer, out, 1);
        assertEquals(0.0f, peak(out), 0.0f,
                "the fade-out reaches silence after exactly MUSIC_RAMP_FRAMES frames");
    }

    /** Renders exactly the asked-for frame count, one frame at a time for ramp precision. */
    private static void renderFrames(SoftwareMixer mixer, float[] out, int frames) {
        for (int i = 0; i < frames; i++) {
            mixer.render(out, 1);
        }
    }
}
