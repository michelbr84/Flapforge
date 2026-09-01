package io.github.michelbr84.flapforge.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;

/**
 * The bank has one job with three outcomes — override, classpath, generated — and one hard
 * requirement: it must never hand back {@code null} or a buffer in the wrong shape, because the
 * mixer's inner loop trusts both.
 *
 * <p>The override cases build a WAV in memory with {@link AudioSystem#write}, which touches no
 * device, so the whole test runs on a headless machine with no sound card.
 */
class SoundBankTest {

    private static final int OVERRIDE_RATE = 8_000;
    private static final int OVERRIDE_FRAMES = 800;

    @Test
    void synthesisesAnythingWithoutAnOverride() {
        SoundBank bank = new SoundBank();
        float[] samples = bank.samples(ToneSynth.FLAP);
        assertTrue(samples.length > 0);
        assertEquals(0, samples.length % SoundBank.CHANNELS, "buffers are interleaved stereo");
        assertTrue(bank.isSynthesised(ToneSynth.FLAP));

        // Mono is widened, so both channels carry the same signal.
        for (int i = 0; i < samples.length; i += 2) {
            assertEquals(samples[i], samples[i + 1], 0.0f);
        }
    }

    @Test
    void cachesEachBufferExactlyOnce() {
        SoundBank bank = new SoundBank();
        assertSame(bank.samples(ToneSynth.COIN), bank.samples(ToneSynth.COIN));
        bank.clear();
        assertEquals(0, bank.loadedIds().size());
    }

    @Test
    void warmUpLoadsEveryKnownSound() {
        SoundBank bank = new SoundBank();
        assertEquals(ToneSynth.IDS.size(), bank.warmUp());
        assertTrue(bank.loadedIds().containsAll(ToneSynth.IDS));
    }

    @Test
    void decodesAnOverrideAndConvertsItToTheMixerFormat() throws IOException {
        byte[] wav = monoWav(OVERRIDE_RATE, OVERRIDE_FRAMES);
        SoundBank bank = new SoundBank(assets(Map.of("sfx/flap", wav)));

        float[] samples = bank.samples(ToneSynth.FLAP);
        assertFalse(bank.isSynthesised(ToneSynth.FLAP), "the override must win over the synth");

        int frames = samples.length / SoundBank.CHANNELS;
        double expected = OVERRIDE_FRAMES * (SoundBank.SAMPLE_RATE / (double) OVERRIDE_RATE);
        assertEquals(expected, frames, 2.0, "8 kHz must be resampled up to 44.1 kHz");
        for (int i = 0; i < samples.length; i += 2) {
            assertEquals(samples[i], samples[i + 1], 0.0f, "mono must be widened to both channels");
        }
        assertTrue(peak(samples) > 0.4f, "the override kept its level");
    }

    @Test
    void ignoresOverridesForOtherIds() throws IOException {
        SoundBank bank = new SoundBank(assets(Map.of("sfx/flap", monoWav(OVERRIDE_RATE, 200))));
        bank.samples(ToneSynth.SCORE);
        assertTrue(bank.isSynthesised(ToneSynth.SCORE));
        bank.samples(ToneSynth.FLAP);
        assertFalse(bank.isSynthesised(ToneSynth.FLAP));
    }

    @Test
    void fallsBackToTheSynthWhenAnOverrideCannotBeDecoded() {
        List<String> log = new ArrayList<>();
        SoundBank bank = new SoundBank(
                assets(Map.of("sfx/crash", "this is not a wav file".getBytes())), new ToneSynth(),
                log::add);

        float[] samples = bank.samples(ToneSynth.CRASH);
        assertTrue(peak(samples) > 0.2f, "a broken override must not produce silence");
        assertTrue(bank.isSynthesised(ToneSynth.CRASH));
        assertEquals(1, log.size(), () -> "expected one line, got " + log);
        assertTrue(log.get(0).contains("crash"), log.get(0));
    }

    /** An asset opener over an in-memory map, standing in for the manifest-backed one. */
    private static java.util.function.Function<String, InputStream> assets(
            Map<String, byte[]> files) {
        return id -> {
            byte[] bytes = files.get(id);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        };
    }

    /** A mono 16-bit WAV holding a triangle ramp, built without touching a sound device. */
    private static byte[] monoWav(int rate, int frames) throws IOException {
        byte[] pcm = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double t = i / (double) frames;
            int sample = (int) ((0.5 - Math.abs(t - 0.5)) * 2.0 * Short.MAX_VALUE);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        AudioFormat format = new AudioFormat(rate, 16, 1, true, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (AudioInputStream in =
                new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, out);
        }
        return out.toByteArray();
    }

    private static float peak(float[] samples) {
        float peak = 0.0f;
        for (float sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}
