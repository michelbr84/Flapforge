package io.github.michelbr84.flapforge.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Resolves a sound id to a ready-to-mix PCM buffer, and caches the result (D19).
 *
 * <p>Resolution order for id {@code flap}:
 * <ol>
 *   <li>the injected asset opener, asked for {@code sfx/flap} — this is where
 *       {@code render.AssetManager} plugs the {@code assets/manifest.json} override in;</li>
 *   <li>the classpath resource {@code assets/audio/sfx/flap.wav};</li>
 *   <li>{@link ToneSynth}, which always answers.</li>
 * </ol>
 * Because step 3 never fails, {@link #samples(String)} never returns {@code null} and no cue is
 * ever silent by accident. A {@code .wav} that exists but cannot be decoded logs one line and
 * falls through to the synth rather than taking the game down.
 *
 * <p>Every buffer leaves this class in exactly one shape — interleaved stereo at
 * {@value #SAMPLE_RATE} Hz — so channel widening and sample-rate conversion are paid once, at
 * load time, and never per frame. Streams are wrapped in a {@link BufferedInputStream} before
 * {@link AudioSystem#getAudioInputStream(InputStream)} because that call needs {@code mark}
 * support to sniff the header, and a raw jar entry stream has none.
 *
 * <p>The cache is a {@link ConcurrentHashMap}: the mixer thread reads it while the loop thread
 * may trigger a warm-up. Buffers are immutable once published.
 */
public final class SoundBank {

    /** Sample rate every buffer is converted to, in hertz. */
    public static final int SAMPLE_RATE = ToneSynth.SAMPLE_RATE;
    /** Channel count every buffer is converted to. */
    public static final int CHANNELS = 2;
    /** Classpath folder holding optional sound-effect overrides. */
    public static final String SFX_RESOURCE_PREFIX = "assets/audio/sfx/";
    /** Extension of the only container format the game reads (D19: no OGG, no MP3). */
    public static final String SFX_RESOURCE_SUFFIX = ".wav";
    /** Manifest id prefix an asset opener is asked for. */
    public static final String SFX_ASSET_PREFIX = "sfx/";

    private final ToneSynth synth;
    private final Function<String, InputStream> assetOpener;
    private final Consumer<String> log;
    private final Map<String, float[]> cache = new ConcurrentHashMap<>();
    private final Set<String> synthesised = ConcurrentHashMap.newKeySet();

    /** Creates a bank that reads overrides from the classpath and synthesises everything else. */
    public SoundBank() {
        this(null, new ToneSynth(), null);
    }

    /**
     * Creates a bank backed by an asset opener.
     *
     * @param assetOpener resolves a manifest asset id (for example {@code sfx/flap}) to an open
     *     stream, or {@code null} when the asset is unknown; may itself be {@code null} to use
     *     the classpath only
     */
    public SoundBank(Function<String, InputStream> assetOpener) {
        this(assetOpener, new ToneSynth(), null);
    }

    /**
     * Creates a bank with every collaborator injected.
     *
     * @param assetOpener the manifest-backed opener, or {@code null}
     * @param synth the fallback generator
     * @param log receives one line per decode failure, or {@code null} for {@code System.err}
     */
    public SoundBank(Function<String, InputStream> assetOpener, ToneSynth synth,
            Consumer<String> log) {
        this.assetOpener = assetOpener;
        this.synth = Objects.requireNonNull(synth, "synth");
        this.log = log != null ? log : System.err::println;
    }

    /**
     * The buffer for an id, decoding or synthesising it on first use.
     *
     * @param id the sound id
     * @return interleaved stereo samples at {@link #SAMPLE_RATE}, never {@code null} and never
     *     empty
     */
    public float[] samples(String id) {
        Objects.requireNonNull(id, "id");
        return cache.computeIfAbsent(id, this::load);
    }

    /**
     * Decodes or synthesises every id {@link ToneSynth} knows. D19 calls for this on the audio
     * thread during the boot screen, so the first flap never pays for a decode.
     *
     * @return the number of ids in the cache afterwards
     */
    public int warmUp() {
        for (String id : ToneSynth.IDS) {
            samples(id);
        }
        return cache.size();
    }

    /**
     * Whether an already-loaded id came from {@link ToneSynth} rather than a {@code .wav}.
     *
     * @param id the sound id
     * @return {@code true} when the id was synthesised
     */
    public boolean isSynthesised(String id) {
        return synthesised.contains(id);
    }

    /**
     * Ids loaded so far.
     *
     * @return an immutable snapshot
     */
    public Set<String> loadedIds() {
        return Set.copyOf(new HashSet<>(cache.keySet()));
    }

    /** Drops every cached buffer. */
    public void clear() {
        cache.clear();
        synthesised.clear();
    }

    private float[] load(String id) {
        float[] decoded = decodeOverride(id);
        if (decoded != null) {
            synthesised.remove(id);
            return decoded;
        }
        synthesised.add(id);
        return toStereo(synth.render(id), 1, SAMPLE_RATE);
    }

    /** Returns the decoded override for an id, or {@code null} when there is none or it failed. */
    private float[] decodeOverride(String id) {
        InputStream stream = openOverride(id);
        if (stream == null) {
            return null;
        }
        try (InputStream raw = stream) {
            return decode(raw);
        } catch (UnsupportedAudioFileException | IOException | IllegalArgumentException e) {
            log.accept("Audio: cannot decode override for '" + id + "' ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "); using the generated sound.");
            return null;
        }
    }

    private InputStream openOverride(String id) {
        if (assetOpener != null) {
            InputStream fromManifest = assetOpener.apply(SFX_ASSET_PREFIX + id);
            if (fromManifest != null) {
                return fromManifest;
            }
        }
        return SoundBank.class.getClassLoader()
                .getResourceAsStream(SFX_RESOURCE_PREFIX + id + SFX_RESOURCE_SUFFIX);
    }

    /**
     * Reads one audio stream fully and converts it to the mixer's format.
     *
     * @param raw the encoded stream
     * @return interleaved stereo samples at {@link #SAMPLE_RATE}
     * @throws UnsupportedAudioFileException when the container or encoding is unreadable
     * @throws IOException when the stream fails
     */
    static float[] decode(InputStream raw) throws UnsupportedAudioFileException, IOException {
        try (AudioInputStream in =
                AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
            AudioFormat source = in.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(), 16, source.getChannels(), source.getChannels() * 2,
                    source.getSampleRate(), false);
            boolean alreadyPcm = AudioFormat.Encoding.PCM_SIGNED.equals(source.getEncoding())
                    && source.getSampleSizeInBits() == 16 && !source.isBigEndian();
            try (AudioInputStream pcmStream =
                    alreadyPcm ? in : AudioSystem.getAudioInputStream(pcm, in)) {
                byte[] bytes = pcmStream.readAllBytes();
                AudioFormat format = pcmStream.getFormat();
                int channels = Math.max(1, format.getChannels());
                float[][] planes = split(bytes, channels);
                float rate = format.getSampleRate() > 0 ? format.getSampleRate() : SAMPLE_RATE;
                return toStereo(planes, rate);
            }
        }
    }

    /** Splits interleaved signed 16-bit little-endian bytes into one float plane per channel. */
    private static float[][] split(byte[] bytes, int channels) {
        int frames = bytes.length / (2 * channels);
        float[][] planes = new float[channels][Math.max(1, frames)];
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) {
                int i = (f * channels + c) * 2;
                short value = (short) ((bytes[i] & 0xFF) | (bytes[i + 1] << 8));
                planes[c][f] = value / (float) -Short.MIN_VALUE;
            }
        }
        return planes;
    }

    /** Widens a mono buffer and resamples it in one pass. */
    private static float[] toStereo(float[] mono, int channels, float sourceRate) {
        return toStereo(channels == 1 ? new float[][] {mono} : new float[][] {mono, mono},
                sourceRate);
    }

    /**
     * Converts channel planes to interleaved stereo at {@link #SAMPLE_RATE}. Channels above the
     * second are folded into both outputs; a mono plane is copied to both. Rate conversion is
     * linear interpolation, which is inaudible on short effects and costs nothing at load time.
     */
    private static float[] toStereo(float[][] planes, float sourceRate) {
        float[] left = planes[0];
        float[] right = planes.length > 1 ? planes[1] : planes[0];
        if (planes.length > 2) {
            left = fold(planes, 0);
            right = fold(planes, 1);
        }
        int sourceFrames = Math.min(left.length, right.length);
        if (sourceFrames <= 0) {
            return new float[2];
        }
        double ratio = SAMPLE_RATE / (double) (sourceRate > 0 ? sourceRate : SAMPLE_RATE);
        int frames = Math.max(1, (int) Math.round(sourceFrames * ratio));
        float[] out = new float[frames * CHANNELS];
        for (int i = 0; i < frames; i++) {
            double position = i / ratio;
            int i0 = (int) position;
            if (i0 >= sourceFrames) {
                i0 = sourceFrames - 1;
            }
            int i1 = Math.min(i0 + 1, sourceFrames - 1);
            float t = (float) (position - i0);
            out[i * 2] = left[i0] + (left[i1] - left[i0]) * t;
            out[i * 2 + 1] = right[i0] + (right[i1] - right[i0]) * t;
        }
        return out;
    }

    /**
     * Averages one side's own plane with every channel above the second into one plane.
     *
     * <p>Channels 0 and 1 are the two sides; everything above them (centre, LFE, surrounds) has no
     * side of its own, so it goes to <em>both</em> outputs. Splitting the upper channels by index
     * parity instead would send a 5.1 file's centre channel only to the left and its LFE only to
     * the right.
     *
     * @param planes the channel planes
     * @param side {@code 0} for left, {@code 1} for right
     * @return the folded plane
     */
    private static float[] fold(float[][] planes, int side) {
        int frames = planes[0].length;
        for (float[] plane : planes) {
            frames = Math.min(frames, plane.length);
        }
        float[] out = new float[frames];
        int used = 1;
        System.arraycopy(planes[side], 0, out, 0, frames);
        for (int c = 2; c < planes.length; c++) {
            used++;
            for (int i = 0; i < frames; i++) {
                out[i] += planes[c][i];
            }
        }
        if (used > 1) {
            for (int i = 0; i < frames; i++) {
                out[i] /= used;
            }
        }
        return out;
    }
}
