package io.github.michelbr84.flapforge.render;

/**
 * Frame timing in simulation ticks (D18): {@code frames} frames each held
 * {@code ticksPerFrame} ticks, advanced once per {@link #tick()} so an animation is as
 * deterministic as the simulation that drives it (the bird sheet is 8 frames of 20 ticks, the
 * cadence upstream used at 30 Hz).
 *
 * <p>The class holds an index, never a clock: a caller that wants a real-time animation converts
 * elapsed time into ticks itself.
 */
public final class Animation {

    private final int frames;
    private final int ticksPerFrame;
    private final boolean looping;
    private int tick;

    /**
     * Creates a looping animation.
     *
     * @param frames the number of frames (at least 1)
     * @param ticksPerFrame how many ticks each frame is held (at least 1)
     */
    public Animation(int frames, int ticksPerFrame) {
        this(frames, ticksPerFrame, true);
    }

    /**
     * Creates an animation.
     *
     * @param frames the number of frames (at least 1)
     * @param ticksPerFrame how many ticks each frame is held (at least 1)
     * @param looping {@code true} to wrap, {@code false} to stop on the last frame
     */
    public Animation(int frames, int ticksPerFrame, boolean looping) {
        this.frames = Math.max(1, frames);
        this.ticksPerFrame = Math.max(1, ticksPerFrame);
        this.looping = looping;
    }

    /**
     * Number of frames.
     *
     * @return the count
     */
    public int frames() {
        return frames;
    }

    /**
     * Ticks each frame is held.
     *
     * @return the count
     */
    public int ticksPerFrame() {
        return ticksPerFrame;
    }

    /**
     * Whether the animation wraps.
     *
     * @return {@code true} when looping
     */
    public boolean isLooping() {
        return looping;
    }

    /** Advances by one tick. */
    public void tick() {
        int last = frames * ticksPerFrame;
        if (looping) {
            tick = (tick + 1) % last;
        } else if (tick < last - 1) {
            tick++;
        }
    }

    /** Restarts at frame 0. */
    public void reset() {
        tick = 0;
    }

    /**
     * The frame currently shown.
     *
     * @return an index in {@code [0, frames)}
     */
    public int frame() {
        return Math.min(frames - 1, tick / ticksPerFrame);
    }

    /**
     * Progress through the whole cycle.
     *
     * @return a value in {@code [0, 1)}
     */
    public double phase() {
        return tick / (double) (frames * ticksPerFrame);
    }

    /**
     * Whether a non-looping animation reached its last frame.
     *
     * @return {@code true} when finished (never for a looping animation)
     */
    public boolean isFinished() {
        return !looping && tick >= frames * ticksPerFrame - 1;
    }

    @Override
    public String toString() {
        return "Animation[" + frame() + "/" + frames + "]";
    }
}
