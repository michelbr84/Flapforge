package jssound;

import android.media.AudioAttributes;
import android.media.AudioTrack;

import java.util.Objects;

/**
 * android.media shim for the M10 build-time source transform
 * ({@code javax.sound.sampled.*} -> {@code jssound.*}).
 *
 * <p>Stand-in for {@code javax.sound.sampled.SourceDataLine}: one output line over an
 * {@link AudioTrack} in {@code MODE_STREAM}. AWT's is an interface; nothing in the game
 * implements it (the fake lines live in the desktop test tree), so this is a final class that
 * {@link AudioSystem#getSourceDataLine(AudioFormat)} hands out unopened.
 *
 * <p>Census surface (audio/SoftwareMixer.java): {@link #open(AudioFormat, int)} (:213),
 * {@link #start()} (:214), {@link #close()} (:216, :332), {@link #stop()} (:318),
 * {@link #flush()} (:319), {@link #available()} and {@link #getBufferSize()} (:573 — the
 * underrun check {@code available() >= getBufferSize()}), {@link #write(byte[], int, int)}
 * (:578). {@link #open(AudioFormat)} and {@link #drain()} complete the line lifecycle (M10
 * semantics 1); no other AWT member exists here.
 *
 * <p>Mapping onto the track:
 * <ul>
 *   <li>{@code open} builds the track — {@code ENCODING_PCM_16BIT}, the format's sample rate
 *       and a mono/stereo channel mask, {@code USAGE_GAME}/{@code CONTENT_TYPE_MUSIC},
 *       low-latency performance mode — with a buffer of {@code max(requested,
 *       AudioTrack.getMinBufferSize)} bytes rounded to whole frames. A one-argument open asks for
 *       {@value #DEFAULT_BUFFER_MS} ms, AWT's default line buffer. {@link LineUnavailableException}
 *       when the builder fails or the track is not {@code STATE_INITIALIZED} afterwards.</li>
 *   <li>{@code start} is {@code play()}; {@code stop} is {@code pause()} (AWT's stop keeps the
 *       queued data, so does pause); {@code flush} is {@code flush()}, which the track honours
 *       only while paused/stopped — exactly the order {@code SoftwareMixer.close} uses. A stop or
 *       flush from another thread is what returns a blocked {@link #write} early, which is what
 *       the mixer relies on to shut its thread down.</li>
 *   <li>{@code write} is a {@code WRITE_BLOCKING} write and returns the byte count the track
 *       took (short when the track was paused or stopped meanwhile); a negative track result is
 *       an {@link IllegalStateException}, which the mixer treats as an output failure.</li>
 *   <li>{@code available} is the buffer size minus the bytes written but not yet passed by the
 *       playback head, clamped to {@code [0, bufferSize]}; the head position is read as an
 *       unsigned 32-bit counter and compared modulo 2^32.</li>
 *   <li>{@code drain} polls the playback head every {@value #DRAIN_POLL_MS} ms until it reaches
 *       the last written frame, giving up after the queued duration plus
 *       {@value #DRAIN_GRACE_MS} ms ({@link #drainBoundMillis(long, int)}). It returns at once
 *       when the line is not playing: a paused track never advances.</li>
 *   <li>{@code close} stops and releases the track; a second close, or a close of a line that
 *       was never opened, does nothing. A closed line cannot be reopened.</li>
 * </ul>
 *
 * <p>Threads: the mixer thread writes while the game thread stops, flushes and closes. The track
 * is thread-safe for that, and {@link #write} never holds the line's monitor around the blocking
 * track write, so a concurrent {@link #stop()} can always get in to unblock it.
 */
public final class SourceDataLine {

    /** Extra wait {@link #drain()} allows past the queued duration, in milliseconds. */
    static final long DRAIN_GRACE_MS = 250;
    /** How often {@link #drain()} polls the playback head, in milliseconds. */
    static final long DRAIN_POLL_MS = 5;
    /** Buffer a one-argument {@link #open(AudioFormat)} asks for: AWT's default line buffer. */
    static final int DEFAULT_BUFFER_MS = 500;

    private final Object lock = new Object();
    private volatile AudioTrack track;
    private volatile int bufferSize;
    private volatile int frameSize;
    private volatile int sampleRate;
    /** Frames written since open or the last honoured flush; guarded by {@link #lock}. */
    private long framesWritten;
    /** Guarded by {@link #lock}. */
    private boolean closed;

    private SourceDataLine() {
    }

    /**
     * Shim infrastructure for {@link AudioSystem#getSourceDataLine(AudioFormat)}: validates the
     * format the way AWT's mixer lookup would, then asks the platform for a minimum buffer size
     * to be sure it can play it.
     */
    static SourceDataLine forFormat(AudioFormat format) throws LineUnavailableException {
        checkFormat(format);
        minBufferSize(format);
        return new SourceDataLine();
    }

    /**
     * Opens the line with AWT's default buffer ({@value #DEFAULT_BUFFER_MS} ms of frames).
     *
     * @param format the line format
     * @throws LineUnavailableException when the track cannot be created
     */
    public void open(AudioFormat format) throws LineUnavailableException {
        open(format, -1);
    }

    /**
     * Opens the line: builds the streaming {@link AudioTrack} (census: SoftwareMixer.java:213).
     *
     * @param format 16-bit signed little-endian PCM, mono or stereo, integral sample rate
     * @param bufferSize the buffer wanted in bytes; raised to the platform minimum and rounded to
     *     whole frames; a negative value (AWT's {@code NOT_SPECIFIED}) selects the default
     * @throws LineUnavailableException when the track cannot be created or does not initialise
     * @throws IllegalArgumentException when no line supports the format, or the buffer size is 0
     * @throws IllegalStateException when the line is already open or was closed
     */
    public void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
        checkFormat(format);
        if (bufferSize == 0) {
            throw new IllegalArgumentException("Flapforge shim: SourceDataLine buffer size 0");
        }
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Flapforge shim: SourceDataLine is closed");
            }
            if (track != null) {
                throw new IllegalStateException("Flapforge shim: SourceDataLine is already open");
            }
            int rate = (int) format.getSampleRate();
            int frame = format.getFrameSize();
            int minimum = minBufferSize(format);
            int wanted = bufferSize < 0 ? (int) (rate * (long) DEFAULT_BUFFER_MS / 1000) * frame
                    : bufferSize;
            int size = Math.max(minimum, wanted);
            size = ((size + frame - 1) / frame) * frame;
            AudioTrack built;
            try {
                built = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new android.media.AudioFormat.Builder()
                                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(rate)
                                .setChannelMask(channelMask(format))
                                .build())
                        .setBufferSizeInBytes(size)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .build();
            } catch (UnsupportedOperationException | IllegalArgumentException
                    | IllegalStateException e) {
                throw new LineUnavailableException(
                        "cannot create an AudioTrack for " + format + ": " + e.getMessage());
            }
            int state = built.getState();
            if (state != AudioTrack.STATE_INITIALIZED) {
                built.release();
                throw new LineUnavailableException(
                        "AudioTrack for " + format + " did not initialise (state " + state + ")");
            }
            this.frameSize = frame;
            this.sampleRate = rate;
            this.bufferSize = size;
            this.framesWritten = 0;
            this.track = built;
        }
    }

    /**
     * Starts playback (census: SoftwareMixer.java:214). In streaming mode the track plays as
     * soon as data is written.
     *
     * @throws IllegalStateException when the line is not open
     */
    public void start() {
        openTrack().play();
    }

    /**
     * Pauses playback, keeping the queued data (census: SoftwareMixer.java:318). A line that
     * is not open has nothing to stop.
     */
    public void stop() {
        AudioTrack current = track;
        if (current != null) {
            current.pause();
        }
    }

    /**
     * Writes PCM frames, blocking until the track has taken them (census:
     * SoftwareMixer.java:578).
     *
     * @param buffer the frames
     * @param offset the first byte
     * @param length the byte count, a whole number of frames
     * @return the bytes the track accepted: {@code length} normally, fewer when the track was
     *     paused or stopped on entry or while blocked
     * @throws IllegalStateException when the line is not open, or the track reports an error
     * @throws IllegalArgumentException when {@code length} is not a whole number of frames
     * @throws ArrayIndexOutOfBoundsException when the range is outside the array
     */
    public int write(byte[] buffer, int offset, int length) {
        Objects.requireNonNull(buffer, "buffer");
        if (offset < 0 || length < 0 || length > buffer.length - offset) {
            throw new ArrayIndexOutOfBoundsException(
                    "offset " + offset + ", length " + length + ", array " + buffer.length);
        }
        AudioTrack current = openTrack();
        int frame = frameSize;
        if (length % frame != 0) {
            throw new IllegalArgumentException("Flapforge shim: write length " + length
                    + " is not a whole number of " + frame + "-byte frames");
        }
        if (length == 0) {
            return 0;
        }
        int written = current.write(buffer, offset, length, AudioTrack.WRITE_BLOCKING);
        if (written < 0) {
            throw new IllegalStateException(
                    "Flapforge shim: AudioTrack.write failed with error " + written);
        }
        synchronized (lock) {
            framesWritten += written / frame;
        }
        return written;
    }

    /**
     * Waits for the playback head to pass the last written frame: polls every
     * {@value #DRAIN_POLL_MS} ms and gives up after the queued duration plus
     * {@value #DRAIN_GRACE_MS} ms. Returns at once when the line is not open or not playing.
     */
    public void drain() {
        AudioTrack current = track;
        if (current == null) {
            return;
        }
        try {
            if (current.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                return;
            }
            long queued = queuedFrames(current);
            if (queued == 0) {
                return;
            }
            long deadline = System.nanoTime()
                    + drainBoundMillis(queued, sampleRate) * 1_000_000L;
            while (queuedFrames(current) > 0 && System.nanoTime() < deadline) {
                if (track != current) {
                    return; // closed meanwhile
                }
                try {
                    Thread.sleep(DRAIN_POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } catch (IllegalStateException released) {
            // The track was released by a concurrent close: nothing left to wait for.
        }
    }

    /**
     * Discards the queued data (census: SoftwareMixer.java:319). The track honours a flush only
     * while paused or stopped (it resets its playback head then), so the frame bookkeeping is
     * reset under the same condition; a flush of a playing or unopened line is a no-op, as it
     * is for the track.
     */
    public void flush() {
        synchronized (lock) {
            AudioTrack current = track;
            if (current == null) {
                return;
            }
            boolean honoured = current.getPlayState() != AudioTrack.PLAYSTATE_PLAYING;
            current.flush();
            if (honoured) {
                framesWritten = 0;
            }
        }
    }

    /**
     * Stops and releases the track (census: SoftwareMixer.java:216, :332). Idempotent, and a
     * no-op for a line that was never opened; the line cannot be reopened afterwards.
     */
    public void close() {
        AudioTrack current;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            current = track;
            track = null;
        }
        if (current != null) {
            try {
                current.stop();
            } catch (IllegalStateException alreadyGone) {
                // Only an uninitialised track refuses to stop; releasing it is all that is left.
            }
            current.release();
        }
    }

    /**
     * Bytes the line can take without blocking (census: SoftwareMixer.java:573): the buffer
     * minus what is written but not yet played. {@code 0} for a line that is not open.
     *
     * @return the free byte count, within {@code [0, getBufferSize()]}
     */
    public int available() {
        AudioTrack current = track;
        if (current == null) {
            return 0;
        }
        int size = bufferSize;
        try {
            long queuedBytes = queuedFrames(current) * frameSize;
            return (int) Math.max(0, Math.min(size, size - queuedBytes));
        } catch (IllegalStateException released) {
            return 0;
        }
    }

    /**
     * The buffer the line was opened with, in bytes (census: SoftwareMixer.java:573).
     *
     * @return the size, or {@code 0} before {@code open}
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * The longest {@link #drain()} waits for a number of queued frames: their duration, rounded
     * up to a millisecond, plus {@value #DRAIN_GRACE_MS} ms.
     *
     * @param queuedFrames frames written but not yet played
     * @param sampleRate frames per second
     * @return the bound in milliseconds
     */
    static long drainBoundMillis(long queuedFrames, int sampleRate) {
        return (queuedFrames * 1000L + sampleRate - 1) / sampleRate + DRAIN_GRACE_MS;
    }

    private AudioTrack openTrack() {
        AudioTrack current = track;
        if (current == null) {
            throw new IllegalStateException("Flapforge shim: SourceDataLine is not open");
        }
        return current;
    }

    /**
     * Frames written but not yet passed by the playback head. The head is an unsigned 32-bit
     * counter; a difference at or above 2^31 can only mean the head is ahead of the count (the
     * platform reset it), which is "nothing queued".
     */
    private long queuedFrames(AudioTrack current) {
        long head = current.getPlaybackHeadPosition() & 0xFFFFFFFFL;
        long written;
        synchronized (lock) {
            written = framesWritten;
        }
        long queued = (written - head) & 0xFFFFFFFFL;
        return queued > Integer.MAX_VALUE ? 0 : queued;
    }

    private static void checkFormat(AudioFormat format) {
        Objects.requireNonNull(format, "format");
        float rate = format.getSampleRate();
        boolean supported = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 16
                && !format.isBigEndian()
                && (format.getChannels() == 1 || format.getChannels() == 2)
                && format.getFrameSize() == format.getChannels() * 2
                && rate >= 1 && rate <= Integer.MAX_VALUE && rate == (int) rate;
        if (!supported) {
            throw new IllegalArgumentException("Flapforge shim: no output line supports " + format
                    + " (16-bit signed little-endian mono/stereo PCM at an integral rate only)");
        }
    }

    private static int channelMask(AudioFormat format) {
        return format.getChannels() == 1
                ? android.media.AudioFormat.CHANNEL_OUT_MONO
                : android.media.AudioFormat.CHANNEL_OUT_STEREO;
    }

    private static int minBufferSize(AudioFormat format) throws LineUnavailableException {
        int minimum = AudioTrack.getMinBufferSize((int) format.getSampleRate(),
                channelMask(format), android.media.AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) {
            throw new LineUnavailableException(
                    "AudioTrack reports no buffer size for " + format + " (" + minimum + ")");
        }
        return minimum;
    }
}
