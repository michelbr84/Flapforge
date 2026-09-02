package io.github.michelbr84.flapforge.audio;

import io.github.michelbr84.flapforge.content.defs.SfxSet;
import io.github.michelbr84.flapforge.core.MathUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Deterministic procedural sound-effect generator (D19). It is the reason nothing in the game is
 * ever silent by accident: when {@link SoundBank} finds no {@code .wav} for an id it asks this
 * class, which always answers with a short, distinct, pleasant blip.
 *
 * <p>Every id has its own hand-written {@link Spec} — waveform, note sequence, sweep, vibrato,
 * noise, envelope — so two ids never render the same samples. Rendering is a pure function of
 * {@code (id, variant)}: the only randomness is a {@link Random} seeded from
 * {@link MathUtil#fnv1a64(String)}, and every trigonometric call goes through {@link StrictMath},
 * so the bytes are identical on every JVM and platform.
 *
 * <p>Three properties are guaranteed for every id and asserted by {@code ToneSynthTest}:
 * <ul>
 *   <li>the first and last samples are exactly {@code 0}, because the amplitude envelope starts
 *       and ends at zero — a buffer that begins mid-waveform is a click;</li>
 *   <li>the sum of the samples is zero to within float rounding, because a final
 *       envelope-weighted pass removes the DC component without disturbing the zero edges — DC
 *       in a short blip is a thump on the speaker cone;</li>
 *   <li>the peak is exactly {@link Spec#peak}, which never exceeds {@value #MAX_PEAK}, leaving
 *       headroom for the mixer to sum several voices.</li>
 * </ul>
 *
 * <p>The {@code variant} argument is the numeric hook for {@code worlds.json.sfxSet} (E31.g):
 * variant {@code 0} is the canonical timbre, and a non-zero variant transposes and re-seeds the
 * same spec. From M7 the sets are named: {@link #render(String, SfxSet)} gives every id one
 * flavour per {@link SfxSet} — {@code FIELDS} is the canonical sound, {@code CANYON} is wider
 * and airier (detuned, longer tail), {@code FACTORY} harder and more percussive (square-ish
 * waves, more noise, lower), {@code STORM} sharper and noisier (higher, darker noise) and
 * {@code VOID} hollow and detuned (lower, wide detune, slow vibrato). A set is a deterministic
 * recipe on top of the id's spec, so the bytes stay a pure function of {@code (id, set)}.
 */
public final class ToneSynth {

    /** Sample rate of every buffer this class produces, in hertz. */
    public static final int SAMPLE_RATE = 44_100;
    /** The canonical timbre; the only variant milestone M2 uses. */
    public static final int DEFAULT_VARIANT = 0;
    /** Highest peak amplitude any generated effect reaches. */
    public static final double MAX_PEAK = 0.85;

    /** Wing beat, on every flap. */
    public static final String FLAP = "flap";
    /** Gate cleared. */
    public static final String SCORE = "score";
    /** Coin picked up. */
    public static final String COIN = "coin";
    /** Collision. */
    public static final String CRASH = "crash";
    /** Ability activated. */
    public static final String ABILITY = "ability";
    /** Shield absorbed a hit. */
    public static final String SHIELD = "shield";
    /** Revive spent. */
    public static final String REVIVE = "revive";
    /** Menu focus moved. */
    public static final String UI_MOVE = "ui_move";
    /** Menu item confirmed. */
    public static final String UI_SELECT = "ui_select";
    /** Menu dismissed. */
    public static final String UI_BACK = "ui_back";
    /** Something was unlocked. */
    public static final String UNLOCK = "unlock";
    /** A boss is approaching. */
    public static final String BOSS_WARNING = "boss_warning";
    /** The Void rule cycle changed. */
    public static final String RULE_SHIFT = "rule_shift";
    /** A clean-gate streak stepped up. */
    public static final String STREAK = "streak";
    /** A modifier synergy activated. */
    public static final String SYNERGY = "synergy";
    /** The player levelled up. */
    public static final String LEVEL_UP = "level_up";
    /** A lightning column started its warning (M7). */
    public static final String LIGHTNING_WARNING = "lightning_warning";
    /** The sky flashed: the cosmetic storm thunder (M7, E8). */
    public static final String THUNDER = "thunder";
    /** A piston started its telegraph (M7). */
    public static final String PISTON_TELEGRAPH = "piston_telegraph";
    /** The bird flew into a wind zone (M7). */
    public static final String WIND = "wind";

    /** Every id this synth knows, in a stable order (warm-up and tests iterate it). */
    public static final List<String> IDS = List.of(FLAP, SCORE, COIN, CRASH, ABILITY, SHIELD,
            REVIVE, UI_MOVE, UI_SELECT, UI_BACK, UNLOCK, BOSS_WARNING, RULE_SHIFT, STREAK, SYNERGY,
            LEVEL_UP, LIGHTNING_WARNING, THUNDER, PISTON_TELEGRAPH, WIND);

    /** Shortest buffer the synth will produce, in samples (guards against degenerate specs). */
    private static final int MIN_SAMPLES = 64;
    /** Fraction of a note segment spent fading in, to hide the phase jump between notes. */
    private static final double NOTE_FADE_IN = 0.04;
    /** Fraction of a note segment spent fading out. */
    private static final double NOTE_FADE_OUT = 0.10;
    /** Highest harmonic used by the band-limited square and saw waves. */
    private static final int PARTIALS = 7;

    /** The waveform of a spec's tonal component. */
    private enum Wave {
        /** Pure sine: soft, no upper harmonics. */
        SINE,
        /** Triangle: hollow, gentle odd harmonics. */
        TRIANGLE,
        /** Band-limited square (odd harmonics only): hard, chiptune. */
        SQUARE,
        /** Band-limited saw (all harmonics): bright, buzzy. */
        SAW
    }

    /**
     * The recipe for one sound effect. Fields are set through the fluent setters; the defaults
     * describe a plain 120 ms sine blip.
     */
    private static final class Spec {

        private Wave wave = Wave.SINE;
        /** Note frequencies in hertz, played in order over the sound's duration. */
        private double[] notes = {440.0};
        private double durationMs = 120.0;
        /** Frequency multiplier swept linearly from {@code 1} to this value across the sound. */
        private double sweep = 1.0;
        private double vibratoHz;
        private double vibratoDepth;
        /** Detune of the doubled voice, as a fraction of the frequency ({@code 0.004} ≈ 7 cents). */
        private double detune;
        /** Amount of filtered noise mixed in, {@code 0}‥{@code 1}. */
        private double noise;
        /** One-pole coefficient of the noise filter: lower is darker. */
        private double noiseTone = 0.45;
        private double attackMs = 3.0;
        /** Fraction of the sound spent in the final fade-out. */
        private double releaseFraction = 0.55;
        /** Body decay exponent, {@code 0}‥{@code 3}; {@code 0} keeps a flat body. */
        private int decayExponent = 1;
        private double peak = 0.6;

        private Spec wave(Wave value) {
            this.wave = value;
            return this;
        }

        private Spec notes(double... value) {
            this.notes = value;
            return this;
        }

        private Spec durationMs(double value) {
            this.durationMs = value;
            return this;
        }

        private Spec sweep(double value) {
            this.sweep = value;
            return this;
        }

        private Spec vibrato(double hz, double depth) {
            this.vibratoHz = hz;
            this.vibratoDepth = depth;
            return this;
        }

        private Spec detune(double value) {
            this.detune = value;
            return this;
        }

        private Spec noise(double amount, double tone) {
            this.noise = amount;
            this.noiseTone = tone;
            return this;
        }

        private Spec envelope(double attackMs, double releaseFraction, int decayExponent) {
            this.attackMs = attackMs;
            this.releaseFraction = releaseFraction;
            this.decayExponent = decayExponent;
            return this;
        }

        private Spec peak(double value) {
            this.peak = value;
            return this;
        }

        /** A detached copy, so a set can bend a recipe without touching the shared table. */
        private Spec copy() {
            Spec c = new Spec();
            c.wave = wave;
            c.notes = notes.clone();
            c.durationMs = durationMs;
            c.sweep = sweep;
            c.vibratoHz = vibratoHz;
            c.vibratoDepth = vibratoDepth;
            c.detune = detune;
            c.noise = noise;
            c.noiseTone = noiseTone;
            c.attackMs = attackMs;
            c.releaseFraction = releaseFraction;
            c.decayExponent = decayExponent;
            c.peak = peak;
            return c;
        }
    }

    private static final Map<String, Spec> SPECS = Map.ofEntries(
            Map.entry(FLAP, new Spec().wave(Wave.TRIANGLE).notes(560.0).durationMs(90.0)
                    .sweep(0.55).noise(0.12, 0.30).envelope(3.0, 0.70, 1).peak(0.55)),
            Map.entry(SCORE, new Spec().wave(Wave.SINE).notes(880.0, 1174.66).durationMs(130.0)
                    .detune(0.004).envelope(2.0, 0.55, 1).peak(0.60)),
            Map.entry(COIN, new Spec().wave(Wave.SINE).notes(987.77, 1318.51).durationMs(150.0)
                    .detune(0.002).envelope(2.0, 0.50, 1).peak(0.60)),
            Map.entry(CRASH, new Spec().wave(Wave.SAW).notes(196.0).durationMs(340.0).sweep(0.35)
                    .noise(0.55, 0.22).envelope(1.0, 0.85, 2).peak(0.80)),
            Map.entry(ABILITY, new Spec().wave(Wave.SQUARE).notes(329.63, 493.88).durationMs(180.0)
                    .sweep(1.35).envelope(3.0, 0.50, 1).peak(0.58)),
            Map.entry(SHIELD, new Spec().wave(Wave.SINE).notes(440.0, 659.25).durationMs(240.0)
                    .vibrato(7.0, 0.006).detune(0.006).envelope(8.0, 0.60, 1).peak(0.55)),
            Map.entry(REVIVE, new Spec().wave(Wave.SINE).notes(392.0, 523.25, 659.25, 783.99)
                    .durationMs(420.0).detune(0.003).envelope(4.0, 0.35, 0).peak(0.60)),
            Map.entry(UI_MOVE, new Spec().wave(Wave.TRIANGLE).notes(660.0).durationMs(55.0)
                    .envelope(2.0, 0.70, 1).peak(0.40)),
            Map.entry(UI_SELECT, new Spec().wave(Wave.SINE).notes(783.99, 1046.50).durationMs(95.0)
                    .envelope(2.0, 0.60, 1).peak(0.45)),
            Map.entry(UI_BACK, new Spec().wave(Wave.SINE).notes(587.33, 392.0).durationMs(95.0)
                    .envelope(2.0, 0.60, 1).peak(0.45)),
            Map.entry(UNLOCK, new Spec().wave(Wave.SINE)
                    .notes(523.25, 659.25, 783.99, 1046.50).durationMs(480.0).detune(0.003)
                    .envelope(3.0, 0.30, 0).peak(0.62)),
            Map.entry(BOSS_WARNING, new Spec().wave(Wave.SQUARE)
                    .notes(110.0, 164.81, 110.0, 164.81).durationMs(720.0).noise(0.08, 0.15)
                    .envelope(6.0, 0.25, 0).peak(0.65)),
            Map.entry(RULE_SHIFT, new Spec().wave(Wave.SAW).notes(300.0).durationMs(300.0)
                    .sweep(2.60).vibrato(11.0, 0.020).envelope(4.0, 0.50, 1).peak(0.55)),
            Map.entry(STREAK, new Spec().wave(Wave.TRIANGLE).notes(659.25, 880.0).durationMs(150.0)
                    .envelope(2.0, 0.50, 1).peak(0.50)),
            Map.entry(SYNERGY, new Spec().wave(Wave.SINE).notes(523.25, 783.99, 1046.50)
                    .durationMs(300.0).vibrato(6.0, 0.004).detune(0.005).envelope(5.0, 0.45, 1)
                    .peak(0.58)),
            Map.entry(LEVEL_UP, new Spec().wave(Wave.SINE)
                    .notes(523.25, 659.25, 783.99, 1046.50, 1318.51).durationMs(620.0).detune(0.004)
                    .envelope(3.0, 0.28, 0).peak(0.65)),
            Map.entry(LIGHTNING_WARNING, new Spec().wave(Wave.SQUARE).notes(1318.51, 1318.51)
                    .durationMs(260.0).vibrato(18.0, 0.012).noise(0.10, 0.6)
                    .envelope(2.0, 0.35, 0).peak(0.5)),
            Map.entry(THUNDER, new Spec().wave(Wave.SAW).notes(55.0).durationMs(900.0).sweep(0.6)
                    .noise(0.7, 0.08).envelope(10.0, 0.75, 2).peak(0.7)),
            Map.entry(PISTON_TELEGRAPH, new Spec().wave(Wave.TRIANGLE).notes(220.0, 293.66)
                    .durationMs(200.0).noise(0.2, 0.35).envelope(2.0, 0.45, 1).peak(0.5)),
            Map.entry(WIND, new Spec().wave(Wave.SINE).notes(180.0).durationMs(520.0).sweep(1.8)
                    .noise(0.85, 0.05).envelope(40.0, 0.6, 1).peak(0.45)));

    /** Root note of the fallback blip, before the per-id transposition. */
    private static final double UNKNOWN_ROOT = 523.25;
    /** Second note of the fallback blip, before the per-id transposition. */
    private static final double UNKNOWN_SECOND = 392.0;
    /** Semitone range the fallback blip is transposed over, so unknown ids stay distinguishable. */
    private static final int UNKNOWN_RANGE = 12;

    /**
     * Creates a synth. Instances hold no state; one per process is enough, but constructing
     * several is harmless.
     */
    public ToneSynth() {
    }

    /**
     * Whether this synth has a hand-written recipe for an id. Unknown ids still render (with
     * the fallback recipe built from {@code UNKNOWN_ROOT}), so this is a diagnostic, not a precondition.
     *
     * @param id the sound id
     * @return {@code true} when the id is one of {@link #IDS}
     */
    public boolean knows(String id) {
        return SPECS.containsKey(id);
    }

    /**
     * Renders the canonical timbre of an id.
     *
     * @param id the sound id, ideally one of {@link #IDS}
     * @return a fresh mono buffer at {@link #SAMPLE_RATE}, in {@code [-1, 1]}
     */
    public float[] render(String id) {
        return render(id, DEFAULT_VARIANT);
    }

    /**
     * Renders one timbre variant of an id.
     *
     * @param id the sound id, ideally one of {@link #IDS}
     * @param variant {@link #DEFAULT_VARIANT} for the canonical sound, or a small non-negative
     *     number selecting a transposed, re-seeded flavour of the same recipe (the future
     *     {@code worlds.json.sfxSet} hook, E31.g)
     * @return a fresh mono buffer at {@link #SAMPLE_RATE}, in {@code [-1, 1]}
     */
    public float[] render(String id, int variant) {
        Objects.requireNonNull(id, "id");
        Spec spec = SPECS.get(id);
        if (spec == null) {
            spec = unknownSpec(id);
        }
        int v = Math.max(0, variant);
        return synthesise(spec, MathUtil.fold(MathUtil.fnv1a64(id), v), 1.0 + 0.06 * v);
    }

    /**
     * Renders the flavour of an id in a world's sound set (E31.g, M7). {@link SfxSet#FIELDS}
     * is the canonical timbre, byte for byte the same as {@link #render(String)}; every other set
     * bends the recipe deterministically.
     *
     * @param id the sound id
     * @param set the world's set, or {@code null} for the canonical sound
     * @return a fresh mono buffer at {@link #SAMPLE_RATE}, in {@code [-1, 1]}
     */
    public float[] render(String id, SfxSet set) {
        Objects.requireNonNull(id, "id");
        if (set == null || set == SfxSet.FIELDS) {
            return render(id, DEFAULT_VARIANT);
        }
        Spec spec = SPECS.get(id);
        if (spec == null) {
            spec = unknownSpec(id);
        }
        Spec bent = bend(spec.copy(), set);
        long seed = MathUtil.fold(MathUtil.fnv1a64(id), MathUtil.fnv1a64(set.name()));
        return synthesise(bent, seed, transposeOf(set));
    }

    /**
     * Renders every set of an id, in {@link SfxSet} order (tests and tooling).
     *
     * @param id the sound id
     * @return one buffer per set
     */
    public float[][] renderAllSets(String id) {
        SfxSet[] sets = SfxSet.values();
        float[][] out = new float[sets.length][];
        for (int i = 0; i < sets.length; i++) {
            out[i] = render(id, sets[i]);
        }
        return out;
    }

    /** The pitch ratio of a set: the whole set sits a little higher or lower than the fields. */
    private static double transposeOf(SfxSet set) {
        switch (set) {
            case CANYON:
                return 0.94;
            case FACTORY:
                return 0.86;
            case STORM:
                return 1.07;
            case VOID:
                return 0.79;
            case FIELDS:
            default:
                return 1.0;
        }
    }

    /**
     * The set's recipe on top of an id's spec: a handful of parameter bends chosen so the cue
     * stays recognisable (same notes, same length class) while the world's character shows.
     */
    private static Spec bend(Spec spec, SfxSet set) {
        switch (set) {
            case CANYON:
                // Wider and airier: a detuned double, a longer tail, a breath of bright noise.
                spec.detune = Math.max(spec.detune, 0.007);
                spec.releaseFraction = Math.min(0.9, spec.releaseFraction + 0.15);
                spec.noise = Math.min(1.0, spec.noise + 0.08);
                spec.noiseTone = Math.max(spec.noiseTone, 0.5);
                spec.decayExponent = Math.max(0, spec.decayExponent - 1);
                break;
            case FACTORY:
                // Harder and more percussive: sines become squares, more mid noise, faster decay.
                if (spec.wave == Wave.SINE) {
                    spec.wave = Wave.SQUARE;
                } else if (spec.wave == Wave.TRIANGLE) {
                    spec.wave = Wave.SAW;
                }
                spec.noise = Math.min(1.0, spec.noise + 0.15);
                spec.noiseTone = 0.3;
                spec.attackMs = Math.max(0.5, spec.attackMs * 0.5);
                spec.decayExponent = Math.min(3, spec.decayExponent + 1);
                break;
            case STORM:
                // Sharper and noisier: dark noise on everything, a touch of fast vibrato.
                spec.noise = Math.min(1.0, spec.noise + 0.22);
                spec.noiseTone = Math.min(spec.noiseTone, 0.18);
                if (spec.vibratoDepth == 0.0) {
                    spec.vibratoHz = 13.0;
                    spec.vibratoDepth = 0.006;
                }
                spec.releaseFraction = Math.max(0.2, spec.releaseFraction - 0.1);
                break;
            case VOID:
                // Hollow and detuned: a wide double, slow vibrato, long tail, no hard edges.
                if (spec.wave == Wave.SAW || spec.wave == Wave.SQUARE) {
                    spec.wave = Wave.TRIANGLE;
                }
                spec.detune = Math.max(spec.detune, 0.014);
                spec.vibratoHz = spec.vibratoDepth > 0.0 ? spec.vibratoHz * 0.5 : 4.5;
                spec.vibratoDepth = Math.max(spec.vibratoDepth, 0.009);
                spec.releaseFraction = Math.min(0.92, spec.releaseFraction + 0.2);
                spec.durationMs = spec.durationMs * 1.2;
                break;
            case FIELDS:
            default:
                break;
        }
        return spec;
    }

    /**
     * The recipe for an id nobody wrote one for. It is transposed by a semitone offset taken from
     * the id's hash, so a cue added to the game before its spec exists still gets its own
     * recognisable blip instead of sounding exactly like every other unknown id.
     */
    private static Spec unknownSpec(String id) {
        int semitones = (int) Math.floorMod(MathUtil.fnv1a64(id), (long) UNKNOWN_RANGE)
                - UNKNOWN_RANGE / 2;
        double ratio = StrictMath.pow(2.0, semitones / 12.0);
        return new Spec().wave(Wave.TRIANGLE)
                .notes(UNKNOWN_ROOT * ratio, UNKNOWN_SECOND * ratio)
                .durationMs(140.0).envelope(2.0, 0.55, 1).peak(0.45);
    }

    /**
     * Renders an id straight to signed 16-bit little-endian mono PCM. Same determinism guarantee
     * as {@link #render(String, int)}: the bytes are a pure function of {@code (id, variant)}.
     *
     * @param id the sound id
     * @param variant the timbre variant
     * @return the encoded samples
     */
    public byte[] renderPcm16(String id, int variant) {
        float[] mono = render(id, variant);
        byte[] out = new byte[mono.length * 2];
        for (int i = 0; i < mono.length; i++) {
            int s = Math.round(mono[i] * Short.MAX_VALUE);
            s = MathUtil.clamp(s, Short.MIN_VALUE, Short.MAX_VALUE);
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }

    private static float[] synthesise(Spec spec, long seed, double transpose) {
        int n = Math.max(MIN_SAMPLES, (int) Math.round(spec.durationMs / 1000.0 * SAMPLE_RATE));
        // The transposition is a fixed ratio per variant or per set, so the cue stays recognisable.
        Random random = new Random(seed);

        double[] body = new double[n];
        double[] envelope = new double[n];
        double phase = 0.0;
        double detunedPhase = 0.0;
        double noiseState = 0.0;
        int noteCount = spec.notes.length;
        int attackSamples = Math.max(1, (int) Math.round(spec.attackMs / 1000.0 * SAMPLE_RATE));
        int releaseSamples = Math.max(1, (int) (n * clampUnit(spec.releaseFraction)));

        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            int note = Math.min(noteCount - 1, (int) (t * noteCount));
            double inNote = t * noteCount - note;

            double frequency = spec.notes[note] * transpose * (1.0 + (spec.sweep - 1.0) * t);
            if (spec.vibratoDepth > 0.0) {
                frequency *= 1.0 + spec.vibratoDepth
                        * StrictMath.sin(2.0 * StrictMath.PI * spec.vibratoHz * i / SAMPLE_RATE);
            }

            phase += frequency / SAMPLE_RATE;
            double sample = wave(spec.wave, phase);
            if (spec.detune > 0.0) {
                detunedPhase += frequency * (1.0 + spec.detune) / SAMPLE_RATE;
                sample = 0.5 * (sample + wave(spec.wave, detunedPhase));
            }
            if (spec.noise > 0.0) {
                double white = random.nextDouble() * 2.0 - 1.0;
                noiseState += spec.noiseTone * (white - noiseState);
                sample = (1.0 - spec.noise) * sample + spec.noise * noiseState;
            }
            body[i] = sample;
            envelope[i] = envelopeAt(i, n, attackSamples, releaseSamples, t, spec.decayExponent)
                    * (noteCount > 1 ? noteFade(inNote) : 1.0);
        }

        return finish(body, envelope, spec.peak);
    }

    /**
     * Multiplies body by envelope, removes the DC component in a way that keeps both edges at
     * exactly zero, and normalises the result to the requested peak.
     */
    private static float[] finish(double[] body, double[] envelope, double peak) {
        int n = body.length;
        double[] mixed = new double[n];
        double sum = 0.0;
        double envelopeSum = 0.0;
        for (int i = 0; i < n; i++) {
            mixed[i] = body[i] * envelope[i];
            sum += mixed[i];
            envelopeSum += envelope[i];
        }
        // Subtracting a *constant* offset would lift the first and last samples off zero and put
        // the click straight back; subtracting an offset shaped like the envelope cancels the DC
        // (the corrected samples sum to exactly zero) while leaving both edges untouched.
        if (envelopeSum > 0.0) {
            double offset = sum / envelopeSum;
            for (int i = 0; i < n; i++) {
                mixed[i] -= offset * envelope[i];
            }
        }

        double max = 0.0;
        for (int i = 0; i < n; i++) {
            max = Math.max(max, Math.abs(mixed[i]));
        }
        float[] out = new float[n];
        if (max <= 0.0) {
            return out;
        }
        double scale = Math.min(peak, MAX_PEAK) / max;
        for (int i = 0; i < n; i++) {
            out[i] = (float) (mixed[i] * scale);
        }
        // The envelope is zero at both ends by construction; make it exact after the float cast.
        out[0] = 0.0f;
        out[n - 1] = 0.0f;
        return out;
    }

    private static double envelopeAt(int i, int n, int attackSamples, int releaseSamples, double t,
            int decayExponent) {
        double gate;
        if (i < attackSamples) {
            gate = i / (double) attackSamples;
        } else {
            int remaining = n - i;
            gate = remaining <= releaseSamples ? (remaining - 1) / (double) releaseSamples : 1.0;
        }
        double decay = 1.0;
        double falling = 1.0 - t;
        for (int k = 0; k < decayExponent; k++) {
            decay *= falling;
        }
        return Math.max(0.0, gate) * decay;
    }

    /** Short fade at both ends of a note so an arpeggio does not click between steps. */
    private static double noteFade(double inNote) {
        double in = Math.min(1.0, inNote / NOTE_FADE_IN);
        double out = Math.min(1.0, (1.0 - inNote) / NOTE_FADE_OUT);
        return Math.max(0.0, Math.min(in, out));
    }

    private static double wave(Wave wave, double phase) {
        double p = phase - Math.floor(phase);
        switch (wave) {
            case SINE:
                return StrictMath.sin(2.0 * StrictMath.PI * p);
            case TRIANGLE:
                return 4.0 * Math.abs(p - 0.5) - 1.0;
            case SQUARE:
                return bandLimited(p, 2);
            case SAW:
                return bandLimited(p, 1);
            default:
                return 0.0;
        }
    }

    /**
     * Additive band-limited waveform: {@code step == 2} sums odd harmonics (square),
     * {@code step == 1} sums every harmonic (saw). Building them from sines instead of hard edges
     * keeps the spectrum below Nyquist, so nothing aliases into an audible whistle.
     */
    private static double bandLimited(double p, int step) {
        double sum = 0.0;
        double norm = 0.0;
        for (int k = 1; k <= PARTIALS; k += step) {
            sum += StrictMath.sin(2.0 * StrictMath.PI * k * p) / k;
            norm += 1.0 / k;
        }
        return norm > 0.0 ? sum / norm : 0.0;
    }

    private static double clampUnit(double value) {
        return MathUtil.clamp(value, 0.0, 1.0);
    }
}
