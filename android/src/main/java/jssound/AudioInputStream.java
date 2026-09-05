package jssound;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * android.media shim for the M10 build-time source transform
 * ({@code javax.sound.sampled.*} -> {@code jssound.*}).
 *
 * <p>Stand-in for {@code javax.sound.sampled.AudioInputStream}: an {@link InputStream} of raw
 * PCM frames plus the {@link AudioFormat} describing them. Instances come only from
 * {@link AudioSystem#getAudioInputStream(InputStream)}, which parses the RIFF/WAVE container and
 * positions the underlying stream at the first sample; this class then bounds reads to the
 * declared data-chunk length (rounded down to whole frames, as AWT's WAVE reader does).
 *
 * <p>Census surface (audio/SoundBank.java:312-323): {@link #getFormat()} (:314, :323),
 * {@code readAllBytes()} (:322 — inherited from {@link InputStream}, which drives it through
 * {@link #read(byte[], int, int)}), and {@link #close()} through two nested try-with-resources
 * (:312, :320 — the same stream is closed twice when it is already PCM, so close is
 * idempotent). {@link #read()}, {@link #available()} and {@link #getFrameLength()} complete the
 * stream contract over the same bookkeeping. Nothing here converts formats: the reader only ever
 * yields 16-bit signed little-endian PCM, so {@code SoundBank.decode}'s {@code alreadyPcm} branch
 * is always taken.
 *
 * <p>Unlike AWT, reads are not forced onto frame boundaries (AWT trims a request to a whole
 * number of frames and can return 0 for a short one, violating the {@link InputStream}
 * contract); the total is still a whole number of frames because the bound is.
 */
public class AudioInputStream extends InputStream {

    /** AWT's {@code AudioSystem.NOT_SPECIFIED}: the container declared no usable data length. */
    static final long NOT_SPECIFIED = -1;

    private final InputStream source;
    private final AudioFormat format;
    private final long frameLength;
    /** Bytes still readable, or {@code -1} when the stream runs to EOF. */
    private long remaining;
    private boolean closed;

    /**
     * Shim infrastructure: wraps a stream positioned at the first PCM frame.
     *
     * @param source the underlying stream, owned (closed) by this one
     * @param format the PCM format of the bytes
     * @param frameLength frames in the data chunk, or {@link #NOT_SPECIFIED} to read until EOF
     */
    AudioInputStream(InputStream source, AudioFormat format, long frameLength) {
        this.source = Objects.requireNonNull(source, "source");
        this.format = Objects.requireNonNull(format, "format");
        this.frameLength = frameLength;
        this.remaining = frameLength == NOT_SPECIFIED ? -1 : frameLength * format.getFrameSize();
    }

    /** @return the PCM format of the bytes this stream yields (census: SoundBank.java:314) */
    public AudioFormat getFormat() {
        return format;
    }

    /** @return the frame count declared by the container, or {@link #NOT_SPECIFIED} ({@code -1}) */
    public long getFrameLength() {
        return frameLength;
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        if (remaining == 0) {
            return -1;
        }
        int value = source.read();
        if (value >= 0 && remaining > 0) {
            remaining--;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.requireNonNull(buffer, "buffer");
        if (offset < 0 || length < 0 || length > buffer.length - offset) {
            throw new IndexOutOfBoundsException(
                    "offset " + offset + ", length " + length + ", array " + buffer.length);
        }
        ensureOpen();
        if (length == 0) {
            return 0;
        }
        if (remaining == 0) {
            return -1;
        }
        int wanted = remaining > 0 ? (int) Math.min(length, remaining) : length;
        int read = source.read(buffer, offset, wanted);
        if (read > 0 && remaining > 0) {
            remaining -= read;
        }
        return read;
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        int fromSource = source.available();
        return remaining >= 0 ? (int) Math.min(fromSource, remaining) : fromSource;
    }

    /** Closes the underlying stream; a second call does nothing (census: SoundBank.java:312/320). */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        source.close();
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Flapforge shim: AudioInputStream is closed");
        }
    }
}
