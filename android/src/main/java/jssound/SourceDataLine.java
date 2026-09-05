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
 *
 * <p><b>Host-side output gate</b> (M10, P3). Nothing in the game can silence the mixer — its
 * thread writes for as long as the line is open — so {@link AudioSystem#suspendOutput()} does it
 * from outside, per open line: the track is <em>paused</em> (never stopped or flushed, so the
 * queued audio and the playback head survive) and every {@link #write} parks on the line's
 * monitor until {@link AudioSystem#resumeOutput()} plays the track again and lets the write
 * through. That leaves the mixer thread asleep with one rendered pass in hand, not spinning,
 * which is what a backgrounded activity wants. The gate follows the line's own lifecycle: a
 * {@link #start()} while suspended only records the intent and the track plays on resume; a
 * {@link #stop()} releases a parked writer with a short count of {@code 0} (the stopped-track
 * answer AWT documents, with nothing enqueued); a {@link #close()} releases it into the
 * closed-line {@link IllegalStateException}. The mixer's own shutdown — stop, flush, join, close
 * — therefore ends its thread whether or not the output is suspended. A track write in flight at
 * the moment of the suspend returns short, as it does for any pause from another thread; the
 * tail of that one pass is dropped, which is inaudible on an output that is being silenced. The
 * wait itself is not interruptible, like the track write it fronts: an interrupt is remembered
 * while the write is parked and re-asserted on the thread once the wait ends.
 *
 * <p>Lock order: {@code AudioSystem}'s output lock is always taken <em>before</em> this line's
 * monitor and never after it — the registry calls into a line while holding its lock, a line
 * registers and unregisters itself only after leaving its own monitor.
 */
public final class SourceDataLine {

    /** Extra wait {@link #drain()} allows past the queued duration, in milliseconds. */
    static final long DRAIN_GRACE_MS = 250;
    /** How often {@link #drain()} polls the playback head, in milliseconds. */
    static final long DRAIN_POLL_MS = 5;
    /** Buffer a one-argument {@link #open(AudioFormat)} asks for: AWT's default line buffer. */
    static final int DEFAULT_BUFFER_MS = 500;

    /**
     * Where the line stands in its start/stop cycle. Kept apart from the track's own play state
     * because a suspended line defers {@code play()}: the intent is what the resume replays.
     */
    private enum Run {
        /** Opened, never started: AWT's fresh line, whose writes queue but do not play. */
        IDLE,
        /** Between {@link #start()} and {@link #stop()}. */
        STARTED,
        /** After a {@link #stop()}: a parked writer is released with a short count. */
        STOPPED
    }

    private final Object lock = new Object();
    private volatile AudioTrack track;
    private volatile int bufferSize;
    private volatile int frameSize;
    private volatile int sampleRate;
    /** Frames written since open or the last honoured flush; guarded by {@link #lock}. */
    private long framesWritten;
    /** Guarded by {@link #lock}. */
    private boolean closed;
    /** Guarded by {@link #lock}. */
    private Run run = Run.IDLE;
    /**
     * This line's copy of the host's output flag, set only by {@link #applyOutputState(boolean)}
     * under {@link #lock}; volatile so {@link #drain()} can read it without the monitor.
     */
    private volatile boolean suspended;

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
        // Outside the monitor (lock order): the registry hands the line the host's current
        // output state, so a line opened while suspended starts suspended.
        AudioSystem.register(this);
    }

    /**
     * Starts playback (census: SoftwareMixer.java:214). In streaming mode the track plays as
     * soon as data is written. While the host has the output suspended only the intent is
     * recorded; the track plays on {@link AudioSystem#resumeOutput()}.
     *
     * @throws IllegalStateException when the line is not open
     */
    public void start() {
        synchronized (lock) {
            AudioTrack current = openTrack();
            run = Run.STARTED;
            if (!suspended) {
                current.play();
            }
        }
    }

    /**
     * Pauses playback, keeping the queued data (census: SoftwareMixer.java:318). A line that
     * is not open has nothing to stop. A writer parked by a suspended output is released with a
     * short count.
     */
    public void stop() {
        synchronized (lock) {
            AudioTrack current = track;
            if (current == null) {
                return;
            }
            run = Run.STOPPED;
            current.pause();
            lock.notifyAll();
        }
    }

    /**
     * Writes PCM frames, blocking until the track has taken them (census:
     * SoftwareMixer.java:578). While the host has the output suspended the call parks until
     * {@link AudioSystem#resumeOutput()}, {@link #stop()} or {@link #close()} — see the class
     * javadoc.
     *
     * @param buffer the frames
     * @param offset the first byte
     * @param length the byte count, a whole number of frames
     * @return the bytes the track accepted: {@code length} normally, fewer when the track was
     *     paused or stopped on entry or while blocked, {@code 0} when the line was stopped while
     *     the output was suspended
     * @throws IllegalStateException when the line is not open (or was closed while the write
     *     was parked), or the track reports an error
     * @throws IllegalArgumentException when {@code length} is not a whole number of frames
     * @throws ArrayIndexOutOfBoundsException when the range is outside the array
     */
    public int write(byte[] buffer, int offset, int length) {
        Objects.requireNonNull(buffer, "buffer");
        if (offset < 0 || length < 0 || length > buffer.length - offset) {
            throw new ArrayIndexOutOfBoundsException(
                    "offset " + offset + ", length " + length + ", array " + buffer.length);
        }
        openTrack();
        int frame = frameSize;
        if (length % frame != 0) {
            throw new IllegalArgumentException("Flapforge shim: write length " + length
                    + " is not a whole number of " + frame + "-byte frames");
        }
        if (length == 0) {
            return 0;
        }
        AudioTrack current = awaitOutput();
        if (current == null) {
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
     * {@value #DRAIN_GRACE_MS} ms. Returns at once when the line is not open or not playing —
     * which includes a suspended output, whose track is paused or was never played — and at the
     * next poll when the output is suspended while it waits: a paused head never advances.
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
                if (track != current || suspended) {
                    return; // closed or suspended meanwhile
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
     * no-op for a line that was never opened; the line cannot be reopened afterwards. A writer
     * parked by a suspended output is released into the closed-line exception.
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
            lock.notifyAll();
        }
        if (current != null) {
            // Outside the monitor (lock order); the registry can no longer reach the track.
            AudioSystem.unregister(this);
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

    /**
     * Registry callback, called with {@code AudioSystem}'s output lock held (lock order): takes
     * the host's output state for this line. Suspending pauses a started track and resuming
     * plays it again and releases the parked writer; a line that was never started has nothing
     * to pause, and one that was stopped keeps its pause. Idempotent for a repeated state.
     *
     * @param outputSuspended the host's flag
     * @return {@code false} when the line is closed and must not be registered
     */
    boolean applyOutputState(boolean outputSuspended) {
        synchronized (lock) {
            AudioTrack current = track;
            if (current == null) {
                return false;
            }
            if (suspended == outputSuspended) {
                return true;
            }
            suspended = outputSuspended;
            if (run == Run.STARTED) {
                if (outputSuspended) {
                    current.pause();
                } else {
                    current.play();
                }
            }
            if (!outputSuspended) {
                lock.notifyAll();
            }
            return true;
        }
    }

    /**
     * Shim infrastructure for the jssound tests, which assert on the platform's play state.
     *
     * @return the track, or {@code null} when the line is not open
     */
    AudioTrack track() {
        return track;
    }

    /**
     * The gate in front of the track write: parks while the output is suspended and the line is
     * neither stopped nor closed. Not interruptible (see the class javadoc).
     *
     * @return the track to write to, or {@code null} for a stopped line under a suspended
     *     output, whose write is a short count of {@code 0}
     * @throws IllegalStateException when the line was closed meanwhile
     */
    private AudioTrack awaitOutput() {
        boolean interrupted = false;
        try {
            synchronized (lock) {
                while (suspended && track != null && run != Run.STOPPED) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                AudioTrack current = openTrack();
                return suspended ? null : current;
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
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
