package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;

/**
 * Triangle-wave offset in {@code [0, amplitude]} driving moving obstacles (D6, §5).
 *
 * <p>Reproduces upstream's {@code MovingPipe}: the offset starts at 0 moving downwards
 * ({@code DIR_DOWN}), grows by the oscillation speed until it reaches the amplitude (51 px:
 * upstream flips when {@code dealtY > 50}), then shrinks back to 0 and repeats. The speed is a
 * per-tick distance supplied by the caller ({@code OSCILLATION_SPEED × worldDt / 60}; 30 px/s
 * gives upstream's 1 px per 30 Hz frame and a 3.4 s period). The wave is evaluated from an
 * accumulated phase, so no trigonometry is involved and every step is exact for
 * half-pixel speeds.
 */
public final class Oscillator {

    /** Upstream's travel: {@code MAX_DELTA 50} with a {@code > 50} flip gives 51 px. */
    public static final double DEFAULT_AMPLITUDE = 51;

    private final double amplitude;
    private double phase;
    private double offset;
    private double prevOffset;

    /**
     * Creates an oscillator at offset 0 moving downwards.
     *
     * @param amplitude the peak offset in px (must be positive)
     */
    public Oscillator(double amplitude) {
        if (amplitude <= 0) {
            throw new IllegalArgumentException("Amplitude must be positive: " + amplitude);
        }
        this.amplitude = amplitude;
    }

    /**
     * Creates an oscillator with {@link #DEFAULT_AMPLITUDE}.
     *
     * @return the oscillator
     */
    public static Oscillator classic() {
        return new Oscillator(DEFAULT_AMPLITUDE);
    }

    /**
     * Advances the wave by a distance along its path.
     *
     * @param px the distance travelled this tick (non-negative)
     */
    public void advance(double px) {
        prevOffset = offset;
        double period = 2 * amplitude;
        phase = (phase + px) % period;
        offset = phase <= amplitude ? phase : period - phase;
    }

    /** Makes the previous offset equal to the current one (world freeze). */
    public void settle() {
        prevOffset = offset;
    }

    /**
     * The offset of a triangle wave at an arbitrary phase (the bot's rail oracle, D21): the same
     * formula {@link #advance} applies, so predicting {@code phase() + k × step} gives exactly the
     * offset the oscillator will report after {@code k} advances of {@code step}.
     *
     * @param phase a distance along the wave path (any sign)
     * @param amplitude the peak offset
     * @return the offset in {@code [0, amplitude]}
     */
    public static double offsetForPhase(double phase, double amplitude) {
        double period = 2 * amplitude;
        double p = phase % period;
        if (p < 0) {
            p += period;
        }
        return p <= amplitude ? p : period - p;
    }

    /**
     * Current vertical offset in {@code [0, amplitude]}.
     *
     * @return the offset
     */
    public double offset() {
        return offset;
    }

    /**
     * Offset at the start of the current tick.
     *
     * @return the previous offset
     */
    public double prevOffset() {
        return prevOffset;
    }

    /**
     * Offset interpolated between the previous and the current tick state.
     *
     * @param t the interpolation factor in {@code [0, 1]}
     * @return the offset
     */
    public double offsetAt(double t) {
        return prevOffset + (offset - prevOffset) * t;
    }

    /**
     * Distance along the wave path since creation, modulo the period.
     *
     * @return the phase
     */
    public double phase() {
        return phase;
    }

    /**
     * Peak offset.
     *
     * @return the amplitude
     */
    public double amplitude() {
        return amplitude;
    }

    /**
     * Direction of travel: {@code +1} while the offset grows (moving down), {@code −1} while it
     * shrinks.
     *
     * @return the direction sign
     */
    public int direction() {
        return phase < amplitude ? 1 : -1;
    }

    /**
     * Folds the phase into a hash.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        return MathUtil.fold(hash, Double.doubleToLongBits(phase));
    }
}
