package io.github.michelbr84.flapforge.support;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.SourceDataLine;

/**
 * A {@link SourceDataLine} that keeps the bytes instead of playing them, so
 * {@code SoftwareMixerTest} can exercise the real open/write/close path with no sound card and no
 * {@code AudioSystem} call at all.
 *
 * <p>It also has to <em>pace</em> the mixer. A real line blocks in {@code write} until the buffer
 * it already holds has played; a fake that always returns immediately would spin the mixing
 * thread at full speed and fill the heap with captured bytes. So the first
 * {@value #FREE_WRITES} writes return at once — enough for a test to capture output — and every
 * write after that parks until {@link #stop()} or {@link #close()}, which is exactly how the real
 * line behaves when the mixer gets ahead of playback.
 *
 * <p>{@link #available()} reports {@code 0} by default — a device buffer that is full, which is
 * the healthy state and the one the mixer must not count as an underrun.
 * {@link #setAvailable(java.util.function.IntSupplier)} models a draining device so the underrun
 * counter can actually be asserted.
 */
public final class FakeSourceDataLine implements SourceDataLine {

    /** Writes that return immediately before the line starts blocking. */
    public static final int FREE_WRITES = 4;
    /** How long a parked write sleeps between checks, in milliseconds. */
    private static final long PARK_MS = 5L;

    private final Object lock = new Object();
    private volatile IntSupplier availability;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final CountDownLatch firstWrite = new CountDownLatch(1);
    private AudioFormat format;
    private int bufferSize;
    private boolean open;
    private boolean running;
    private boolean closed;
    private int writes;
    private int opens;
    private int closes;
    private int flushes;
    private long framePosition;

    /** Creates an unopened line. */
    public FakeSourceDataLine() {
    }

    /**
     * Replaces what {@link #available()} reports, so a test can model a device that has drained.
     *
     * @param availability the supplier, or {@code null} for the default full buffer
     */
    public void setAvailable(IntSupplier availability) {
        this.availability = availability;
    }

    @Override
    public void open(AudioFormat format, int bufferSize) {
        synchronized (lock) {
            this.format = format;
            this.bufferSize = bufferSize;
            open = true;
            opens++;
        }
    }

    @Override
    public void open(AudioFormat format) {
        open(format, 4096);
    }

    @Override
    public void open() {
        open(format == null ? new AudioFormat(44_100f, 16, 2, true, false) : format);
    }

    @Override
    public int write(byte[] b, int off, int len) {
        synchronized (lock) {
            captured.write(b, off, len);
            writes++;
            framePosition += format == null ? 0 : len / Math.max(1, format.getFrameSize());
            lock.notifyAll();
        }
        firstWrite.countDown();
        while (true) {
            synchronized (lock) {
                if (!running || closed || writes <= FREE_WRITES) {
                    return len;
                }
                try {
                    lock.wait(PARK_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return len;
                }
            }
        }
    }

    @Override
    public void drain() {
        // Nothing is buffered downstream: every written byte is already captured.
    }

    @Override
    public void flush() {
        synchronized (lock) {
            flushes++;
            lock.notifyAll();
        }
    }

    @Override
    public void start() {
        synchronized (lock) {
            running = true;
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            running = false;
            lock.notifyAll();
        }
    }

    @Override
    public boolean isRunning() {
        synchronized (lock) {
            return running;
        }
    }

    @Override
    public boolean isActive() {
        return isRunning();
    }

    @Override
    public AudioFormat getFormat() {
        synchronized (lock) {
            return format;
        }
    }

    @Override
    public int getBufferSize() {
        synchronized (lock) {
            return bufferSize;
        }
    }

    @Override
    public int available() {
        // A full device buffer by default: the mixer only consults this to detect an underrun,
        // and a healthy line always has audio left to play. FREE_WRITES is what models the write
        // pacing.
        IntSupplier supplier = availability;
        return supplier == null ? 0 : supplier.getAsInt();
    }

    @Override
    public int getFramePosition() {
        return (int) getLongFramePosition();
    }

    @Override
    public long getLongFramePosition() {
        synchronized (lock) {
            return framePosition;
        }
    }

    @Override
    public long getMicrosecondPosition() {
        AudioFormat f = getFormat();
        float rate = f == null ? 44_100f : f.getSampleRate();
        return (long) (getLongFramePosition() / rate * 1_000_000L);
    }

    @Override
    public float getLevel() {
        return AudioSystem.NOT_SPECIFIED;
    }

    @Override
    public Line.Info getLineInfo() {
        return new Line.Info(SourceDataLine.class);
    }

    @Override
    public void close() {
        synchronized (lock) {
            open = false;
            running = false;
            closed = true;
            closes++;
            lock.notifyAll();
        }
    }

    @Override
    public boolean isOpen() {
        synchronized (lock) {
            return open;
        }
    }

    @Override
    public Control[] getControls() {
        return new Control[0];
    }

    @Override
    public boolean isControlSupported(Control.Type control) {
        return false;
    }

    @Override
    public Control getControl(Control.Type control) {
        throw new IllegalArgumentException("unsupported control: " + control);
    }

    @Override
    public void addLineListener(LineListener listener) {
        // Nothing here ever fires a line event.
    }

    @Override
    public void removeLineListener(LineListener listener) {
        // Nothing here ever fires a line event.
    }

    /**
     * Waits until the mixer has written at least once.
     *
     * @param millis how long to wait
     * @return {@code true} when a write happened in time
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public boolean awaitFirstWrite(long millis) throws InterruptedException {
        return firstWrite.await(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Everything the mixer has written so far.
     *
     * @return a copy of the captured bytes
     */
    public byte[] captured() {
        synchronized (lock) {
            return captured.toByteArray();
        }
    }

    /**
     * How many times {@link #write(byte[], int, int)} was called.
     *
     * @return the count
     */
    public int writes() {
        synchronized (lock) {
            return writes;
        }
    }

    /**
     * How many times the line was opened.
     *
     * @return the count
     */
    public int opens() {
        synchronized (lock) {
            return opens;
        }
    }

    /**
     * How many times the line was closed.
     *
     * @return the count
     */
    public int closes() {
        synchronized (lock) {
            return closes;
        }
    }

    /**
     * How many times the line was flushed.
     *
     * @return the count
     */
    public int flushes() {
        synchronized (lock) {
            return flushes;
        }
    }

    /**
     * Whether {@link #close()} ran.
     *
     * @return {@code true} once closed
     */
    public boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }
}
