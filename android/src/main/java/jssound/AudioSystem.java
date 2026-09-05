package jssound;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * android.media shim for the M10 build-time source transform
 * ({@code javax.sound.sampled.*} -> {@code jssound.*}).
 *
 * <p>Stand-in for the three {@code javax.sound.sampled.AudioSystem} entry points the game uses,
 * plus the host-only output gate ({@link #suspendOutput()} / {@link #resumeOutput()}) that has
 * no AWT counterpart — see below.
 *
 * <ul>
 *   <li>{@link #getAudioInputStream(InputStream)} — audio/SoundBank.java:313 — parses a
 *       RIFF/WAVE container: {@code RIFF....WAVE}, then chunks until {@code data}, tolerating
 *       any chunk in between ({@code LIST}, {@code fact}, {@code JUNK}, ...) and the three
 *       {@code fmt } sizes (16, 18, 40 — the 40-byte {@code WAVE_FORMAT_EXTENSIBLE} form is
 *       accepted when its sub-format GUID is PCM). Only 16-bit integer PCM is accepted; any other
 *       signature, tag, or sample size is an {@link UnsupportedAudioFileException}. The stream is
 *       read sequentially and never buffered into memory: the caller (SoundBank) drains it with
 *       {@code readAllBytes()} into its own array.</li>
 *   <li>{@link #getAudioInputStream(AudioFormat, AudioInputStream)} — SoundBank.java:321 — the
 *       conversion overload. The reader above only ever yields 16-bit signed little-endian PCM,
 *       so SoundBank's {@code alreadyPcm} test is always true and this call is never reached at
 *       run time. It keeps AWT's identity case (a stream already in the target format is returned
 *       as is) and throws {@link UnsupportedOperationException} for a real conversion.</li>
 *   <li>{@link #getSourceDataLine(AudioFormat)} — audio/SoftwareMixer.java:158 — hands out an
 *       unopened {@link SourceDataLine} after checking the platform can play the format.</li>
 * </ul>
 *
 * <p><b>Output gate (M10, P3; not part of {@code javax.sound.sampled}).</b> The game never
 * calls {@link #suspendOutput()} or {@link #resumeOutput()}: they exist for the Android host,
 * whose activity must silence and freeze the audio output while it is paused or stopped, and
 * the game offers no way to do that ({@code audio/AudioManager} has no suspend API; the mixer
 * thread writes for as long as its line is open). The two methods act on every open line at
 * once, through a registry every {@link SourceDataLine} joins on open and leaves on close
 * (explicit, so the registry mirrors the open tracks exactly and a line missed by a suspend is
 * impossible): a suspend pauses each line's track and parks its writer, a resume plays and
 * releases them, and a line opened in between takes the flag at registration. Both are
 * idempotent — suspending twice is one suspend, resuming an output that is not suspended does
 * nothing — and neither blocks: the flag and the registry are guarded by one static lock that
 * is taken <em>before</em> any line's monitor and never after one, so a suspend or resume can
 * never wait on a line that is itself waiting on the registry.
 */
public final class AudioSystem {

    /**
     * Guards {@link #openLines} and {@link #outputSuspended}. Lock order: this lock, then a
     * line's monitor — never the reverse (see {@link SourceDataLine}).
     */
    private static final Object OUTPUT_LOCK = new Object();
    /** Every open line, in open order; guarded by {@link #OUTPUT_LOCK}. */
    private static final Set<SourceDataLine> openLines = new LinkedHashSet<>();
    /** The host's flag; guarded by {@link #OUTPUT_LOCK}. */
    private static boolean outputSuspended;

    private static final int RIFF = 0x52494646; // "RIFF"
    private static final int WAVE = 0x57415645; // "WAVE"
    private static final int FMT = 0x666d7420; // "fmt "
    private static final int DATA = 0x64617461; // "data"
    private static final int WAVE_FORMAT_PCM = 0x0001;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;
    /** A data chunk of this size means "until EOF" (files written to a pipe). */
    private static final long STREAMING_CHUNK_SIZE = 0xFFFFFFFFL;
    /** Largest {@code fmt } chunk read into memory; real ones are 16, 18 or 40 bytes. */
    private static final long MAX_FMT_SIZE = 0xFFFF;
    /** Bytes 2..15 of {@code KSDATAFORMAT_SUBTYPE_PCM} (00000001-0000-0010-8000-00AA00389B71). */
    private static final byte[] PCM_SUBFORMAT_TAIL = {
        0x00, 0x00, 0x00, 0x00, 0x10, 0x00, (byte) 0x80, 0x00,
        0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71
    };

    private AudioSystem() {
    }

    /**
     * Opens a RIFF/WAVE stream carrying 16-bit PCM (census: SoundBank.java:313).
     *
     * @param stream the container, positioned at its first byte; it becomes the returned stream's
     *     source and is closed with it
     * @return a stream of the PCM frames whose format reports the sample rate and channel count
     *     of the file, 16-bit signed, little-endian, frame size {@code channels * 2}
     * @throws UnsupportedAudioFileException when the bytes are not a RIFF/WAVE container, the
     *     encoding is not integer PCM, the sample size is not 16 bits, or the mandatory chunks
     *     are missing
     * @throws IOException when the stream fails or ends inside a chunk header it declared
     */
    public static AudioInputStream getAudioInputStream(InputStream stream)
            throws UnsupportedAudioFileException, IOException {
        Objects.requireNonNull(stream, "stream");
        byte[] head = new byte[12];
        if (!readFully(stream, head, head.length)) {
            throw new UnsupportedAudioFileException("stream is too short to be a RIFF/WAVE file");
        }
        if (be32(head, 0) != RIFF || be32(head, 8) != WAVE) {
            throw new UnsupportedAudioFileException("stream is not a RIFF/WAVE file");
        }
        byte[] chunk = new byte[8];
        AudioFormat format = null;
        while (true) {
            if (!readFully(stream, chunk, chunk.length)) {
                throw new UnsupportedAudioFileException("RIFF/WAVE stream has no data chunk");
            }
            int id = be32(chunk, 0);
            long size = le32(chunk, 4) & 0xFFFFFFFFL;
            if (id == FMT) {
                format = readFmt(stream, size);
            } else if (id == DATA) {
                if (format == null) {
                    throw new UnsupportedAudioFileException(
                            "RIFF/WAVE data chunk comes before the fmt chunk");
                }
                long frames = size == STREAMING_CHUNK_SIZE
                        ? AudioInputStream.NOT_SPECIFIED
                        : size / format.getFrameSize();
                return new AudioInputStream(stream, format, frames);
            } else {
                // Any other chunk (LIST, fact, JUNK, PEAK, cue, ...) is skipped, pad byte included.
                skipFully(stream, size + (size & 1));
            }
        }
    }

    /**
     * AWT's conversion overload (census: SoundBank.java:321). A source already in the target
     * format is returned unchanged, as AWT does; nothing else is convertible.
     *
     * @param targetFormat the format wanted
     * @param sourceStream the stream to convert
     * @return {@code sourceStream} when its format already matches
     * @throws UnsupportedOperationException for any actual conversion — the WAVE reader only
     *     yields 16-bit signed little-endian PCM, so the game never needs one
     */
    public static AudioInputStream getAudioInputStream(AudioFormat targetFormat,
            AudioInputStream sourceStream) {
        Objects.requireNonNull(targetFormat, "targetFormat");
        Objects.requireNonNull(sourceStream, "sourceStream");
        if (sourceStream.getFormat().matches(targetFormat)) {
            return sourceStream;
        }
        throw new UnsupportedOperationException(
                "Flapforge shim: AudioSystem.getAudioInputStream(AudioFormat, AudioInputStream)"
                        + " format conversion is not part of the census surface ("
                        + sourceStream.getFormat() + " -> " + targetFormat + ")");
    }

    /**
     * An unopened output line for a format (census: SoftwareMixer.java:158).
     *
     * @param format the format the line will be opened with: 16-bit signed little-endian PCM,
     *     mono or stereo, at an integral sample rate
     * @return the line; open it with {@link SourceDataLine#open(AudioFormat, int)}
     * @throws IllegalArgumentException when no line supports the format (AWT parity)
     * @throws LineUnavailableException when {@code android.media.AudioTrack} reports it cannot
     *     size a buffer for the format
     */
    public static SourceDataLine getSourceDataLine(AudioFormat format)
            throws LineUnavailableException {
        return SourceDataLine.forFormat(format);
    }

    // ------------------------------------------------------------------ host-only output gate

    /**
     * Silences and freezes every open line (host only; no AWT counterpart — see the class
     * javadoc): each started track is paused with its queued audio kept, and every
     * {@link SourceDataLine#write} parks until {@link #resumeOutput()}, or until its own line is
     * stopped or closed. Lines opened afterwards start suspended. Idempotent, never blocks.
     */
    public static void suspendOutput() {
        synchronized (OUTPUT_LOCK) {
            if (outputSuspended) {
                return;
            }
            outputSuspended = true;
            for (SourceDataLine line : openLines) {
                line.applyOutputState(true);
            }
        }
    }

    /**
     * Undoes {@link #suspendOutput()}: every started track plays again from where it was
     * paused and the parked writers go through. A no-op when the output is not suspended.
     */
    public static void resumeOutput() {
        synchronized (OUTPUT_LOCK) {
            if (!outputSuspended) {
                return;
            }
            outputSuspended = false;
            for (SourceDataLine line : openLines) {
                line.applyOutputState(false);
            }
        }
    }

    /**
     * Shim infrastructure for the jssound tests.
     *
     * @return whether {@link #suspendOutput()} is in effect
     */
    static boolean isOutputSuspended() {
        synchronized (OUTPUT_LOCK) {
            return outputSuspended;
        }
    }

    /**
     * Joins a line that has just opened its track (called outside the line's monitor), handing
     * it the current output state so a line opened while suspended starts suspended. A line
     * closed before it got here is not kept.
     */
    static void register(SourceDataLine line) {
        synchronized (OUTPUT_LOCK) {
            if (line.applyOutputState(outputSuspended)) {
                openLines.add(line);
            }
        }
    }

    /** Removes a line whose track is closed (called outside the line's monitor). */
    static void unregister(SourceDataLine line) {
        synchronized (OUTPUT_LOCK) {
            openLines.remove(line);
        }
    }

    // ------------------------------------------------------------------ WAVE parsing

    private static AudioFormat readFmt(InputStream stream, long size)
            throws UnsupportedAudioFileException, IOException {
        if (size < 16 || size > MAX_FMT_SIZE) {
            throw new UnsupportedAudioFileException("RIFF/WAVE fmt chunk of " + size + " bytes");
        }
        byte[] fmt = new byte[(int) size];
        if (!readFully(stream, fmt, fmt.length)) {
            throw new EOFException("RIFF/WAVE stream ends inside the fmt chunk");
        }
        if ((size & 1) != 0) {
            skipFully(stream, 1);
        }
        int tag = le16(fmt, 0);
        int channels = le16(fmt, 2);
        long sampleRate = le32(fmt, 4) & 0xFFFFFFFFL;
        int bits = le16(fmt, 14);
        if (tag == WAVE_FORMAT_EXTENSIBLE) {
            if (size < 40) {
                throw new UnsupportedAudioFileException(
                        "RIFF/WAVE extensible fmt chunk of " + size + " bytes");
            }
            for (int i = 0; i < PCM_SUBFORMAT_TAIL.length; i++) {
                if (fmt[26 + i] != PCM_SUBFORMAT_TAIL[i]) {
                    throw new UnsupportedAudioFileException(
                            "RIFF/WAVE extensible sub-format is not PCM");
                }
            }
            tag = le16(fmt, 24);
        }
        if (tag != WAVE_FORMAT_PCM) {
            throw new UnsupportedAudioFileException(
                    "RIFF/WAVE format tag 0x" + Integer.toHexString(tag) + " is not PCM");
        }
        if (bits != 16) {
            throw new UnsupportedAudioFileException(
                    "RIFF/WAVE " + bits + "-bit PCM; only 16-bit PCM is supported");
        }
        if (channels < 1) {
            throw new UnsupportedAudioFileException("RIFF/WAVE fmt chunk declares no channels");
        }
        if (sampleRate < 1 || sampleRate > Integer.MAX_VALUE) {
            throw new UnsupportedAudioFileException(
                    "RIFF/WAVE sample rate " + sampleRate + " is out of range");
        }
        // The block-align field is ignored: for 16-bit PCM it is channels * 2 by definition.
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels,
                channels * 2, sampleRate, false);
    }

    /** Fills {@code buffer[0..length)} from the stream; {@code false} when EOF comes first. */
    private static boolean readFully(InputStream stream, byte[] buffer, int length)
            throws IOException {
        int filled = 0;
        while (filled < length) {
            int read = stream.read(buffer, filled, length - filled);
            if (read < 0) {
                return false;
            }
            filled += read;
        }
        return true;
    }

    /** Skips exactly {@code count} bytes, reading through streams whose {@code skip} is lazy. */
    private static void skipFully(InputStream stream, long count) throws IOException {
        long left = count;
        while (left > 0) {
            long skipped = stream.skip(left);
            if (skipped <= 0) {
                if (stream.read() < 0) {
                    throw new EOFException("RIFF/WAVE stream ends inside a chunk it declared");
                }
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static int be32(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8
                | (b[at + 3] & 0xFF);
    }

    private static int le32(byte[] b, int at) {
        return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16
                | (b[at + 3] & 0xFF) << 24;
    }

    private static int le16(byte[] b, int at) {
        return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8;
    }
}
