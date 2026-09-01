package io.github.michelbr84.flapforge.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The generated sound effects must be audible, safe, reproducible and free of the two artefacts
 * that make procedural audio sound broken: a click at the buffer edges and a DC offset.
 */
class ToneSynthTest {

    private final ToneSynth synth = new ToneSynth();

    static List<String> ids() {
        return ToneSynth.IDS;
    }

    @Test
    void coversEverySoundTheGameAsksFor() {
        assertTrue(ToneSynth.IDS.containsAll(List.of("flap", "score", "coin", "crash", "ability",
                "shield", "revive", "ui_move", "ui_select", "ui_back", "unlock", "boss_warning",
                "rule_shift", "streak", "synergy", "level_up")), ToneSynth.IDS::toString);
        assertEquals(ToneSynth.IDS.size(), java.util.Set.copyOf(ToneSynth.IDS).size(),
                "ids must be unique");
    }

    @ParameterizedTest
    @MethodSource("ids")
    void producesAudibleSamplesWithABoundedPeak(String id) {
        float[] samples = synth.render(id);
        assertNotNull(samples);
        assertTrue(samples.length > 1000, id + " is too short: " + samples.length + " samples");

        float peak = peak(samples);
        assertTrue(peak >= 0.2f, id + " is effectively silent, peak " + peak);
        assertTrue(peak <= (float) ToneSynth.MAX_PEAK + 1e-6f,
                id + " exceeds the headroom budget, peak " + peak);
        for (float sample : samples) {
            assertTrue(Float.isFinite(sample), id + " produced a non-finite sample");
        }
    }

    @ParameterizedTest
    @MethodSource("ids")
    void hasNoDcOffset(String id) {
        float[] samples = synth.render(id);
        double mean = 0.0;
        for (float sample : samples) {
            mean += sample;
        }
        mean /= samples.length;
        // The envelope-weighted correction in ToneSynth.finish makes the sum exactly zero before
        // the float cast, so anything above rounding noise means the correction was lost.
        assertTrue(Math.abs(mean) < 1e-4, id + " carries a DC offset of " + mean);
    }

    @ParameterizedTest
    @MethodSource("ids")
    void startsAndEndsAtSilenceSoThereIsNoClick(String id) {
        float[] samples = synth.render(id);
        int n = samples.length;
        assertEquals(0.0f, samples[0], 0.0f, id + " starts mid-waveform");
        assertEquals(0.0f, samples[n - 1], 0.0f, id + " ends mid-waveform");

        float peak = peak(samples);
        assertTrue(Math.abs(samples[1]) < 0.05f * peak,
                id + " attacks too abruptly: second sample " + samples[1] + " of peak " + peak);
        assertTrue(Math.abs(samples[n - 2]) < 0.05f * peak,
                id + " releases too abruptly: penultimate sample " + samples[n - 2]);
    }

    @ParameterizedTest
    @MethodSource("ids")
    void rendersTheSameSamplesAndBytesEveryTime(String id) {
        assertArrayEquals(synth.render(id), synth.render(id), id + " is not deterministic");
        assertArrayEquals(new ToneSynth().renderPcm16(id, 0), synth.renderPcm16(id, 0),
                id + " encodes differently across instances");
    }

    @Test
    void everyIdSoundsDifferent() {
        List<String> ids = ToneSynth.IDS;
        for (int i = 0; i < ids.size(); i++) {
            float[] a = synth.render(ids.get(i));
            for (int j = i + 1; j < ids.size(); j++) {
                float[] b = synth.render(ids.get(j));
                assertFalse(Arrays.equals(a, b),
                        ids.get(i) + " and " + ids.get(j) + " render identical waveforms");
            }
        }
    }

    @Test
    void unknownIdsStillRenderSomethingAudible() {
        assertFalse(synth.knows("not_a_sound"));
        float[] samples = synth.render("not_a_sound");
        assertTrue(peak(samples) >= 0.2f, "an unknown id must not be silent");
        assertArrayEquals(samples, synth.render("not_a_sound"));
        assertFalse(Arrays.equals(samples, synth.render("also_not_a_sound")),
                "unknown ids are still seeded by their name");
    }

    @Test
    void variantsAreDistinctButStillDeterministic() {
        float[] canonical = synth.render(ToneSynth.FLAP, ToneSynth.DEFAULT_VARIANT);
        float[] variant = synth.render(ToneSynth.FLAP, 3);
        assertFalse(Arrays.equals(canonical, variant), "a variant must change the timbre");
        assertArrayEquals(variant, synth.render(ToneSynth.FLAP, 3));
        assertEquals(0.0f, variant[0], 0.0f, "a variant keeps the click-free edges");
        assertTrue(peak(variant) <= (float) ToneSynth.MAX_PEAK + 1e-6f);
    }

    @Test
    void encodesToLittleEndianSixteenBitPcm() {
        byte[] pcm = synth.renderPcm16(ToneSynth.SCORE, 0);
        float[] samples = synth.render(ToneSynth.SCORE);
        assertEquals(samples.length * 2, pcm.length);
        int middle = samples.length / 2;
        int decoded = (short) ((pcm[middle * 2] & 0xFF) | (pcm[middle * 2 + 1] << 8));
        assertEquals(Math.round(samples[middle] * Short.MAX_VALUE), decoded);
    }

    private static float peak(float[] samples) {
        float peak = 0.0f;
        for (float sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}
