package io.github.michelbr84.flapforge.audio;

import io.github.michelbr84.flapforge.content.defs.MusicDef;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Renders a world's chiptune loop from its {@code worlds.json} music block (M8, D19).
 *
 * <p>A {@link MusicDef} — {@code tempo, scale, seed, layers} — fully determines the output: the
 * loop is {@value #BARS} bars of 4/4, scheduled on an eighth-note grid, and every layer draws
 * from its own {@link Random} stream salted with the block's seed and the layer name, so layers
 * never steal draws from each other and adding one cannot change what another plays. Two renders
 * of one block produce identical samples, byte for byte; there is no wall clock and no thread
 * state anywhere, so a test can call the renderer directly and compare arrays.
 *
 * <p>The voices are the chip classics the plan names: square and triangle oscillators built from
 * a free-running phase accumulator (no per-sample trigonometry) and white noise for the drums,
 * each note carrying a linear attack/decay envelope. The generator writes interleaved stereo —
 * the only shape {@link SoundBank} and the mixer accept — with a fixed per-layer pan, then
 * normalises the loop's peak to {@value #PEAK} so no block can clip the limiter by construction.
 * A note whose tail runs past the loop's end wraps to its beginning, so the loop point is
 * seamless by construction rather than by luck.
 *
 * <p>The <strong>boss variant</strong> of a loop is the same block at {@value #BOSS_TEMPO_FACTOR}
 * the tempo, capped at {@value #BOSS_TEMPO_CAP} BPM: the fight should feel driven, not new, and
 * one knob keeps the variant as cheap and as predictable as the base loop. The application plays
 * it from {@code BossStarted} and returns to the base loop at {@code BossCleared}; the mixer's
 * music ramp is what turns the switch into a crossfade.
 *
 * <p>Render cost scales with loop length, so the tempo range the validator accepts (60–200 BPM)
 * bounds it: even the slowest allowed tempo renders in far less than the 150 ms budget on this
 * machine (measured in {@code MusicSequencerTest}). Rendering happens synchronously on the
 * calling thread — at boot for the menu loop, at run start for the world loop — never on a new
 * thread and never on the mixing thread.
 */
public final class MusicSequencer {

    /** Sample rate of the rendered loop, the mixer's own. */
    public static final int SAMPLE_RATE = SoundBank.SAMPLE_RATE;
    /** Bars the loop spans. */
    public static final int BARS = 8;
    /** Beats per bar (4/4). */
    public static final int BEATS_PER_BAR = 4;
    /** Grid resolution: eighth notes. */
    public static final int STEPS_PER_BEAT = 2;
    /** Peak the rendered loop is normalised to, leaving limiter headroom. */
    public static final double PEAK = 0.72;
    /** Tempo factor of the boss variant of a loop. */
    public static final double BOSS_TEMPO_FACTOR = 1.15;
    /** Ceiling of the boss variant's tempo, in BPM. */
    public static final double BOSS_TEMPO_CAP = 170.0;

    /** Bank id prefix of every rendered loop. */
    public static final String ID_PREFIX = "music/";
    /** Bank id segment marking the boss variant. */
    public static final String BOSS_SEGMENT = "boss";
    /** Gain the menu loop plays at: −6 dB, beneath the run loop's voice. */
    public static final float MENU_GAIN = 0.5f;
    /** Gain the run loop plays at. */
    public static final float RUN_GAIN = 0.65f;
    /** Gain factor the pause duck retargets the loop to. */
    public static final float PAUSE_DUCK = 0.35f;

    /** MIDI note of the bass root. */
    private static final int BASS_ROOT = 45;
    /** MIDI note of the melody root. */
    private static final int MELODY_ROOT = 69;

    /** The oscillators the loop's voices use. Deliberately not {@code ToneSynth.Wave}: music
     * wants raw chip edges, not the band-limited partials the effect synth spends its budget on. */
    private enum Wave {
        /** Hard-edged square: the lead, the arp and the kick. */
        SQUARE,
        /** Triangle: the bass and the pad. */
        TRIANGLE
    }

    private MusicSequencer() {
    }

    /**
     * Whether a music block may name this scale.
     *
     * @param scale the scale name
     * @return {@code true} when known
     * @deprecated use {@link MusicDef#isKnownScale(String)} — the vocabulary is the content's
     */
    @Deprecated
    public static boolean isKnownScale(String scale) {
        return MusicDef.isKnownScale(scale);
    }

    /**
     * Whether a music block may name this layer.
     *
     * @param layer the layer name
     * @return {@code true} when known
     * @deprecated use {@link MusicDef#isKnownLayer(String)} — the vocabulary is the content's
     */
    @Deprecated
    public static boolean isKnownLayer(String layer) {
        return MusicDef.isKnownLayer(layer);
    }

    /**
     * The bank id a world's base loop is registered under.
     *
     * @param worldId the world id
     * @return the id, for {@link SoundBank#register(String, float[])}
     */
    public static String idForWorld(String worldId) {
        return ID_PREFIX + worldId;
    }

    /**
     * The bank id a world's boss variant is registered under.
     *
     * @param worldId the world id
     * @return the id
     */
    public static String bossIdForWorld(String worldId) {
        return ID_PREFIX + worldId + "/" + BOSS_SEGMENT;
    }

    /**
     * Renders a world's base loop.
     *
     * @param music the music block
     * @return interleaved stereo samples at {@link #SAMPLE_RATE}
     */
    public static float[] render(MusicDef music) {
        return render(music, false);
    }

    /**
     * Renders a loop. Deterministic for the block and the boss flag.
     *
     * @param music the music block
     * @param boss {@code true} for the boss variant (the tempo raised)
     * @return interleaved stereo samples at {@link #SAMPLE_RATE}
     */
    public static float[] render(MusicDef music, boolean boss) {
        Objects.requireNonNull(music, "music");
        int[] scale = music.scaleOffsets();
        double tempo = boss
                ? Math.min(BOSS_TEMPO_CAP, music.tempo() * BOSS_TEMPO_FACTOR)
                : music.tempo();
        long stepFrames = Math.round(SAMPLE_RATE * 60.0 / (tempo * STEPS_PER_BEAT));
        long frames = stepFrames * BARS * BEATS_PER_BAR * STEPS_PER_BEAT;
        float[] out = new float[(int) (frames * SoundBank.CHANNELS)];

        List<String> layers = music.layers();
        for (int i = 0; i < layers.size(); i++) {
            String layer = layers.get(i);
            if (!MusicDef.isKnownLayer(layer)) {
                throw new IllegalArgumentException("unknown music layer: " + layer);
            }
            // One stream per layer, salted by name: layer independence, not shared draws.
            Random rng = new Random(music.seed() ^ salt(layer));
            switch (layer) {
                case "bass":
                    renderBass(out, (int) frames, stepFrames, scale, rng);
                    break;
                case "lead":
                    renderLead(out, (int) frames, stepFrames, scale, rng);
                    break;
                case "arp":
                    renderArp(out, (int) frames, stepFrames, scale, rng);
                    break;
                case "pad":
                    renderPad(out, (int) frames, stepFrames, scale, rng);
                    break;
                default:
                    renderDrums(out, (int) frames, stepFrames, rng);
                    break;
            }
        }
        normalise(out);
        return out;
    }

    /** FNV-1a salt of a layer name, so streams never collide across layers. */
    private static long salt(String layer) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < layer.length(); i++) {
            hash ^= layer.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** Bass: root or fifth on every beat, a triangle low in the register. */
    private static void renderBass(float[] out, int frames, long stepFrames, int[] scale,
            Random rng) {
        int beats = BARS * BEATS_PER_BAR;
        int degree = 0;
        for (int beat = 0; beat < beats; beat++) {
            if (beat % 4 == 0 && rng.nextDouble() < 0.35) {
                degree += rng.nextBoolean() ? 2 : 4;
            }
            int midi = BASS_ROOT - 12 + scaleAt(scale, degree);
            writeNote(out, frames, midi, beat * (int) stepFrames, (int) stepFrames, 0.30,
                    Wave.TRIANGLE, 0.0, 0.002);
        }
    }

    /** Lead: a wandering melody on the eighth grid that rests between phrases. */
    private static void renderLead(float[] out, int frames, long stepFrames, int[] scale,
            Random rng) {
        int steps = BARS * BEATS_PER_BAR * STEPS_PER_BEAT;
        int degree = 0;
        int step = 0;
        while (step < steps) {
            if (rng.nextDouble() < 0.18) {
                // A rest of an eighth or a quarter keeps phrases breathing.
                step += 1 + (rng.nextBoolean() ? 1 : 0);
                continue;
            }
            int move = rng.nextInt(5) - 2;
            degree = Math.max(-7, Math.min(9, degree + (move == 0 ? 1 : move)));
            boolean longNote = rng.nextDouble() < 0.3;
            int length = longNote ? (int) (2 * stepFrames) : (int) stepFrames;
            int midi = MELODY_ROOT + scaleAt(scale, degree);
            writeNote(out, frames, midi, step * (int) stepFrames, length, 0.22,
                    Wave.SQUARE, 0.0, 0.004);
            step += longNote ? 2 : 1;
        }
    }

    /** Arp: the chord's degrees, up and down, on every eighth, panned right and quiet. */
    private static void renderArp(float[] out, int frames, long stepFrames, int[] scale,
            Random rng) {
        int steps = BARS * BEATS_PER_BAR * STEPS_PER_BEAT;
        int[] shape = {0, 2, 4, 2};
        for (int step = 0; step < steps; step++) {
            int degree = shape[step % shape.length];
            int midi = MELODY_ROOT - 12 + scaleAt(scale, degree);
            writeNote(out, frames, midi, step * (int) stepFrames, (int) stepFrames, 0.12,
                    Wave.SQUARE, 0.45, 0.002);
        }
    }

    /** Pad: one low chord per two bars, a slow triangle swell panned left. */
    private static void renderPad(float[] out, int frames, long stepFrames, int[] scale,
            Random rng) {
        int twoBars = (int) (BEATS_PER_BAR * 2 * stepFrames);
        for (int bar = 0; bar < BARS; bar += 2) {
            long start = (long) bar * BEATS_PER_BAR * stepFrames;
            for (int degree = 0; degree < 3; degree++) {
                int midi = BASS_ROOT + 12 + scaleAt(scale, degree * 2);
                writeNote(out, frames, midi, (int) start, twoBars, 0.09,
                        Wave.TRIANGLE, -0.3, 0.35);
            }
        }
    }

    /** Drums: a square kick on the downbeats, a noise hat on the off-beats. */
    private static void renderDrums(float[] out, int frames, long stepFrames, Random rng) {
        int beats = BARS * BEATS_PER_BAR;
        int hatFrames = (int) (0.04 * SAMPLE_RATE);
        int kickFrames = (int) (0.25 * SAMPLE_RATE);
        for (int beat = 0; beat < beats; beat++) {
            long start = beat * stepFrames;
            if (beat % 4 == 0) {
                // A kick two octaves below the bass root.
                writeNote(out, frames, 33, (int) start, kickFrames, 0.34,
                        Wave.SQUARE, 0.0, 0.001);
            }
            if (beat % 2 == 1) {
                writeNoise(out, frames, start, hatFrames, beat % 8 == 3 ? 0.15 : 0.07, rng);
            }
        }
    }

    /**
     * The scale degree's semitone offset, octave-wrapping so a melody can walk past the ends of
     * one octave.
     *
     * @param scale the scale's semitone offsets, ascending
     * @param degree the degree, may be negative or beyond the scale
     * @return the semitone offset from the root
     */
    private static int scaleAt(int[] scale, int degree) {
        int size = scale.length;
        int octave = Math.floorDiv(degree, size);
        int index = Math.floorMod(degree, size);
        return scale[index] + 12 * octave;
    }

    /**
     * Writes one enveloped oscillator note, wrapping the loop point: a note whose tail would run
     * past the end of the loop finishes at the beginning, which is what makes the loop seamless.
     *
     * @param out the interleaved stereo accumulator
     * @param frames the loop's frame count
     * @param midi the note's MIDI number
     * @param start the first frame, from the loop's start
     * @param lengthFrames the note's length in frames
     * @param gain the layer's gain
     * @param wave the oscillator
     * @param pan {@code -1} left to {@code +1} right, applied as fixed channel gains
     * @param attackFraction the attack as a fraction of the note, capped away from both ends
     */
    private static void writeNote(float[] out, int frames, int midi, int start, int lengthFrames,
            double gain, Wave wave, double pan, double attackFraction) {
        double frequency = 440.0 * StrictMath.pow(2.0, (midi - 69) / 12.0);
        double phaseStep = frequency / SAMPLE_RATE;
        int length = Math.max(1, lengthFrames);
        int attack = Math.max(1, (int) (length * Math.min(0.5, Math.max(0.001, attackFraction))));
        // Fixed, non-negative channel gains that sum to the layer gain: centre plays both, a pan
        // moves energy to one side without the other falling silent.
        double panT = (pan + 1.0) / 2.0;
        double leftGain = gain * (1.0 - panT * 0.8);
        double rightGain = gain * (0.2 + panT * 0.8);
        double phase = 0.0;
        for (int i = 0; i < length; i++) {
            int index = (start + i) % frames;
            double envelope;
            if (i < attack) {
                envelope = i / (double) attack;
            } else {
                envelope = 1.0 - (i - attack) / (double) (length - attack);
            }
            double sample = oscillator(wave, phase) * envelope;
            int base = index * 2;
            out[base] += (float) (sample * leftGain);
            out[base + 1] += (float) (sample * rightGain);
            phase += phaseStep;
            if (phase >= 1.0) {
                phase -= Math.floor(phase);
            }
        }
    }

    /**
     * Writes one enveloped noise burst (a drum hit).
     *
     * @param out the interleaved stereo accumulator
     * @param frames the loop's frame count
     * @param start the first frame
     * @param lengthFrames the burst's length in frames
     * @param gain the hit's gain
     * @param rng the layer's stream
     */
    private static void writeNoise(float[] out, int frames, long start, int lengthFrames,
            double gain, Random rng) {
        int length = Math.max(1, lengthFrames);
        for (int i = 0; i < length; i++) {
            int index = (int) ((start + i) % frames);
            double envelope = 1.0 - i / (double) length;
            double sample = (rng.nextDouble() * 2.0 - 1.0) * envelope * gain;
            int base = index * 2;
            out[base] += (float) sample;
            out[base + 1] += (float) sample;
        }
    }

    /** One oscillator sample from a phase in {@code [0, 1)}; no trigonometry per sample. */
    private static double oscillator(Wave wave, double phase) {
        switch (wave) {
            case SQUARE:
                return phase < 0.5 ? 1.0 : -1.0;
            default:
                return phase < 0.25 ? phase * 4.0
                        : (phase < 0.75 ? 2.0 - phase * 4.0 : phase * 4.0 - 4.0);
        }
    }

    /** Scales the finished loop so its peak is {@link #PEAK}, if it has one. */
    private static void normalise(float[] out) {
        double peak = 0.0;
        for (int i = 0; i < out.length; i++) {
            double magnitude = Math.abs(out[i]);
            if (magnitude > peak) {
                peak = magnitude;
            }
        }
        if (peak <= 1.0e-9) {
            return;
        }
        double scale = PEAK / peak;
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (out[i] * scale);
        }
    }
}
