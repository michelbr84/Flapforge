package jssound;

import java.util.Objects;

/**
 * android.media shim for the M10 build-time source transform
 * ({@code javax.sound.sampled.*} -> {@code jssound.*}).
 *
 * <p>Stand-in for {@code javax.sound.sampled.AudioFormat}: an immutable description of a PCM
 * stream. The only encoding the game ever names is {@link Encoding#PCM_SIGNED}, so that is the
 * only one that exists here.
 *
 * <p>Census surface: the five-argument constructor — audio/SoftwareMixer.java:86,
 * {@code new AudioFormat(SAMPLE_RATE, BITS, CHANNELS, true, false)}; the seven-argument
 * {@link Encoding} constructor — audio/SoundBank.java:315; {@link Encoding#PCM_SIGNED} and
 * {@link Encoding#equals(Object)} — SoundBank.java:318; {@link #getSampleRate()} (:316, :317,
 * :326), {@link #getChannels()} (:316, :324), {@link #getSampleSizeInBits()} (:319),
 * {@link #isBigEndian()} (:319), {@link #getEncoding()} (:318). {@link #getFrameSize()} and
 * {@link #getFrameRate()} read back the two remaining values the constructors store; the shim's
 * own {@link SourceDataLine} and WAVE reader use them.
 *
 * <p>Deviations from AWT, all outside the census: the five-argument constructor rejects
 * {@code signed == false} (AWT would pick {@code PCM_UNSIGNED}) with an
 * {@link UnsupportedOperationException}; {@code AudioSystem.NOT_SPECIFIED} placeholders are not
 * interpreted — the frame size is always {@code ceil(bits / 8) * channels}.
 */
public class AudioFormat {

    /**
     * Stand-in for {@code javax.sound.sampled.AudioFormat.Encoding}. Only the census member
     * exists; instances compare by name exactly like AWT's.
     */
    public static class Encoding {

        /** Signed linear PCM — the game's only encoding (SoftwareMixer, SoundBank, WAVE). */
        public static final Encoding PCM_SIGNED = new Encoding("PCM_SIGNED");

        private final String name;

        private Encoding(String name) {
            this.name = name;
        }

        @Override
        public final boolean equals(Object other) {
            return other instanceof Encoding && ((Encoding) other).name.equals(name);
        }

        @Override
        public final int hashCode() {
            return name.hashCode();
        }

        @Override
        public final String toString() {
            return name;
        }
    }

    private final Encoding encoding;
    private final float sampleRate;
    private final int sampleSizeInBits;
    private final int channels;
    private final int frameSize;
    private final float frameRate;
    private final boolean bigEndian;

    /**
     * Creates a linear PCM format whose frame size is derived from the sample size and channel
     * count and whose frame rate equals the sample rate (census: SoftwareMixer.java:86).
     *
     * @param sampleRate samples per second per channel
     * @param sampleSizeInBits bits per sample
     * @param channels channel count
     * @param signed must be {@code true}: signed PCM is the only census encoding
     * @param bigEndian byte order of multi-byte samples
     * @throws UnsupportedOperationException for {@code signed == false} — {@code PCM_UNSIGNED} is
     *     not part of the census surface
     */
    public AudioFormat(float sampleRate, int sampleSizeInBits, int channels, boolean signed,
            boolean bigEndian) {
        this(signedEncoding(signed), sampleRate, sampleSizeInBits, channels,
                ((sampleSizeInBits + 7) / 8) * channels, sampleRate, bigEndian);
    }

    /**
     * Creates a format with every field given (census: SoundBank.java:315).
     *
     * @param encoding the encoding, {@link Encoding#PCM_SIGNED}
     * @param sampleRate samples per second per channel
     * @param sampleSizeInBits bits per sample
     * @param channels channel count
     * @param frameSize bytes per frame (one sample of every channel)
     * @param frameRate frames per second
     * @param bigEndian byte order of multi-byte samples
     */
    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels,
            int frameSize, float frameRate, boolean bigEndian) {
        this.encoding = Objects.requireNonNull(encoding, "encoding");
        this.sampleRate = sampleRate;
        this.sampleSizeInBits = sampleSizeInBits;
        this.channels = channels;
        this.frameSize = frameSize;
        this.frameRate = frameRate;
        this.bigEndian = bigEndian;
    }

    private static Encoding signedEncoding(boolean signed) {
        if (!signed) {
            throw new UnsupportedOperationException(
                    "Flapforge shim: AudioFormat PCM_UNSIGNED (signed == false) is not part of"
                            + " the census surface");
        }
        return Encoding.PCM_SIGNED;
    }

    /** @return the encoding (census: SoundBank.java:318) */
    public Encoding getEncoding() {
        return encoding;
    }

    /** @return samples per second per channel (census: SoundBank.java:316, :317, :326) */
    public float getSampleRate() {
        return sampleRate;
    }

    /** @return bits per sample (census: SoundBank.java:319) */
    public int getSampleSizeInBits() {
        return sampleSizeInBits;
    }

    /** @return the channel count (census: SoundBank.java:316, :324) */
    public int getChannels() {
        return channels;
    }

    /** @return bytes per frame */
    public int getFrameSize() {
        return frameSize;
    }

    /** @return frames per second */
    public float getFrameRate() {
        return frameRate;
    }

    /** @return {@code true} for big-endian multi-byte samples (census: SoundBank.java:319) */
    public boolean isBigEndian() {
        return bigEndian;
    }

    /**
     * Shim infrastructure — AWT's {@code matches}: whether a stream in this format is already in
     * the other one, so {@link AudioSystem#getAudioInputStream(AudioFormat, AudioInputStream)} can
     * return it unchanged. Byte order only matters above 8 bits, as in AWT.
     *
     * @param other the target format
     * @return {@code true} when every field agrees
     */
    boolean matches(AudioFormat other) {
        return encoding.equals(other.encoding)
                && sampleRate == other.sampleRate
                && sampleSizeInBits == other.sampleSizeInBits
                && channels == other.channels
                && frameSize == other.frameSize
                && frameRate == other.frameRate
                && (sampleSizeInBits <= 8 || bigEndian == other.bigEndian);
    }

    /** AWT-style description, e.g. {@code PCM_SIGNED 44100.0 Hz, 16 bit, stereo, 4 bytes/frame, little-endian}. */
    @Override
    public String toString() {
        String channelText = channels == 1 ? "mono" : channels == 2 ? "stereo"
                : channels + " channels";
        String text = encoding + " " + sampleRate + " Hz, " + sampleSizeInBits + " bit, "
                + channelText + ", " + frameSize + " bytes/frame";
        return sampleSizeInBits > 8
                ? text + ", " + (bigEndian ? "big-endian" : "little-endian")
                : text;
    }
}
