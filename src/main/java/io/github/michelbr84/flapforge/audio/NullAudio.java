package io.github.michelbr84.flapforge.audio;

/**
 * The backend that plays nothing (D19). It is what {@code --no-audio} selects, what
 * {@link AudioBackend#create(boolean, java.util.concurrent.ThreadFactory)} falls back to when no
 * line opens (E30.j), and what the headless runner and the window smoke test use.
 *
 * <p>It is deliberately not a no-op stub: it counts calls, so a test can still assert that the
 * game asked for a sound without owning a sound card, and {@link #isRealDevice()} lets the
 * settings screen tell the player that audio is unavailable rather than silently ignoring the
 * volume sliders.
 */
public final class NullAudio implements AudioBackend {

    private long plays;
    private long stops;
    private float masterGain = 1.0f;
    private boolean open;
    private boolean closed;
    private String lastId;

    /** Creates the backend. Nothing is acquired. */
    public NullAudio() {
    }

    @Override
    public void open() {
        open = true;
    }

    @Override
    public void play(String id, float gain, float pan) {
        plays++;
        lastId = id;
    }

    @Override
    public void stopAll() {
        stops++;
    }

    @Override
    public void setMasterGain(float gain) {
        masterGain = gain;
    }

    @Override
    public void close() {
        open = false;
        closed = true;
    }

    @Override
    public boolean isRealDevice() {
        return false;
    }

    /**
     * How many sounds were requested.
     *
     * @return the count
     */
    public long plays() {
        return plays;
    }

    /**
     * How many times every voice was stopped.
     *
     * @return the count
     */
    public long stops() {
        return stops;
    }

    /**
     * The id of the last requested sound.
     *
     * @return the id, or {@code null} before the first play
     */
    public String lastId() {
        return lastId;
    }

    /**
     * The last master gain set.
     *
     * @return the gain
     */
    public float masterGain() {
        return masterGain;
    }

    /**
     * Whether {@link #open()} ran and {@link #close()} has not.
     *
     * @return {@code true} while open
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Whether {@link #close()} ran.
     *
     * @return {@code true} once closed
     */
    public boolean isClosed() {
        return closed;
    }
}
