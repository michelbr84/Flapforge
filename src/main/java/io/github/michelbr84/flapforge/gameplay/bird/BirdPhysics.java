package io.github.michelbr84.flapforge.gameplay.bird;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;

/**
 * The converted classic flight model (§5): velocity-first (semi-implicit) Euler at
 * {@link Playfield#TICK_RATE} Hz.
 *
 * <pre>
 * vy = min(vy + GRAVITY / 60, MAX_FALL_SPEED)
 * y  = y + vy / 60
 * </pre>
 *
 * <p>A flap sets {@code vy = −FLAP_VELOCITY} (never adds) and is refused while
 * {@code y ≤ CEILING_FLAP_Y (32)}, upstream's {@code rect.y > 20} gate. With the classic values
 * ({@code 1800 / 405 / 1500}) two ticks reproduce one upstream 30 Hz frame exactly: a flap moves
 * {@code 6.25 + 5.75 = 12} px on the first frame and the apex is 42.25 px at tick 13 / 42.0 px at
 * tick 14 (upstream: 42 at frames 6–7). {@code TIME_SCALE} never touches this integration.
 *
 * <p>Per-tick deltas are computed as {@code v / TICK_RATE} rather than {@code v × (1.0 / 60)}:
 * division is correctly rounded, so every multiple of 15 px/s yields an exact quarter-pixel step
 * and the parity with the integer upstream loop holds bit for bit.
 */
public final class BirdPhysics {

    private final StatSheet stats;

    /**
     * Creates a physics driver reading its parameters from a stat sheet.
     *
     * @param stats the sheet
     */
    public BirdPhysics(StatSheet stats) {
        this.stats = stats;
    }

    /**
     * Advances the bird by one tick under gravity.
     *
     * @param bird the bird
     */
    public void step(Bird bird) {
        integrate(bird, stats.resolve(StatId.GRAVITY), stats.resolve(StatId.MAX_FALL_SPEED));
    }

    /**
     * Applies a flap with the sheet's {@code FLAP_VELOCITY}.
     *
     * @param bird the bird
     * @return {@code true} when accepted, {@code false} when refused by the ceiling gate
     */
    public boolean flap(Bird bird) {
        return flap(bird, stats.resolve(StatId.FLAP_VELOCITY));
    }

    /**
     * One velocity-first Euler tick.
     *
     * @param bird the bird
     * @param gravity downward acceleration in px/s²
     * @param maxFallSpeed clamp on the downward velocity in px/s
     */
    public static void integrate(Bird bird, double gravity, double maxFallSpeed) {
        double vy = bird.vy() + gravity / Playfield.TICK_RATE;
        if (vy > maxFallSpeed) {
            vy = maxFallSpeed;
        }
        bird.setVy(vy);
        bird.setY(bird.y() + vy / Playfield.TICK_RATE);
    }

    /**
     * Sets the upward velocity when the ceiling gate allows it.
     *
     * @param bird the bird
     * @param flapVelocity the upward speed in px/s (positive number)
     * @return {@code true} when accepted, {@code false} when refused
     */
    public static boolean flap(Bird bird, double flapVelocity) {
        if (!canFlap(bird)) {
            return false;
        }
        bird.setVy(-flapVelocity);
        return true;
    }

    /**
     * Upstream's ceiling gate: a flap is accepted only while {@code y > CEILING_FLAP_Y}.
     *
     * @param bird the bird
     * @return {@code true} when a flap would be accepted
     */
    public static boolean canFlap(Bird bird) {
        return bird.y() > Playfield.CEILING_FLAP_Y;
    }

    /**
     * Ground rule (D7): the bird touches the ground when {@code y ≥ GROUND_DEATH_Y (581.5)}.
     *
     * @param bird the bird
     * @return {@code true} on contact
     */
    public static boolean groundContact(Bird bird) {
        return bird.y() >= Playfield.GROUND_DEATH_Y;
    }

    /**
     * Projects a free fall (no flaps) a number of ticks ahead with the same integrator.
     *
     * @param y the starting y
     * @param vy the starting velocity
     * @param ticks how many ticks to project (0 returns {@code y})
     * @param gravity downward acceleration in px/s²
     * @param maxFallSpeed clamp on the downward velocity
     * @return the projected y
     */
    public static double projectY(double y, double vy, int ticks, double gravity,
            double maxFallSpeed) {
        double py = y;
        double pv = vy;
        for (int i = 0; i < ticks; i++) {
            pv += gravity / Playfield.TICK_RATE;
            if (pv > maxFallSpeed) {
                pv = maxFallSpeed;
            }
            py += pv / Playfield.TICK_RATE;
        }
        return py;
    }
}
