package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;

/**
 * The revives of a run (D9). Stat-driven like {@link ShieldSystem}: a bare {@code REVIVES > 0} —
 * the forge node {@code second_chance_1}, say — absorbs one lethal hit, zeroes the velocity and
 * grants {@link #DEFAULT_INVULN_TICKS} invulnerability ticks, with no ability equipped;
 * {@code NO_REVIVE} removes it by zeroing the stat.
 *
 * <p>The {@code emergency_recovery} ability is the behaviour on top: at equip time it
 * {@linkplain #configure raises} the invulnerability to 90 ticks and turns the zeroed velocity
 * into the auto-flap kick that gives the player a chance to fly out of where it died.
 *
 * <p>{@link #safeY(double)} is the other half of the contract: a bird revived <em>on the ground</em>
 * is lifted back above the ground line, because the M1 ground rule kills anything at or below it
 * at the start of the next tick, which would make a revive on the ground worth exactly one tick.
 * A mid-air revive is never moved — it gets its velocity kick and stays in the column it was in.
 *
 * <p><b>Mid-run charges (M6).</b> Like {@link ShieldSystem}, {@code maxCharges} starts as the
 * {@code REVIVES} the sheet resolved at run start and {@link #raiseTo(int)} moves it when a
 * drafted card ({@code second_wind}, {@code phoenix}) raises the stat (E12).
 */
public final class ReviveSystem {

    /** Invulnerability ticks a revive grants when no ability configured it (D9). */
    public static final int DEFAULT_INVULN_TICKS = 60;

    /** How far above the ground line a revived bird is placed, in px. */
    public static final double GROUND_CLEARANCE_PX = 80;

    private int maxCharges;
    private int charges;
    private int invulnTicks = DEFAULT_INVULN_TICKS;
    private double kickMultiplier;
    private int revives;

    /**
     * Creates the system with the charges the sheet resolved.
     *
     * @param charges the resolved {@code REVIVES} (negative counts as none)
     */
    public ReviveSystem(int charges) {
        this.maxCharges = Math.max(0, charges);
        this.charges = maxCharges;
    }

    /**
     * Raises the ceiling to a freshly resolved {@code REVIVES} and hands the difference over as
     * usable charges (M6).
     *
     * <p>This is the answer to the limit M5 recorded above: the roguelite draft can add revives
     * in the middle of a run, and a snapshot taken at run start would silently swallow the card.
     * It only ever raises — a card cannot take a charge the player has already been given, and a
     * spent charge stays spent.
     *
     * @param resolved the {@code REVIVES} the sheet resolves now
     * @return {@code true} when the ceiling moved
     */
    public boolean raiseTo(int resolved) {
        int target = Math.max(0, resolved);
        if (target <= maxCharges) {
            return false;
        }
        charges += target - maxCharges;
        maxCharges = target;
        return true;
    }

    /**
     * Follows a freshly resolved {@code REVIVES} in both directions (M7): a rise is
     * {@link #raiseTo}, a drop lowers the ceiling and clips the usable charges to it — what a
     * rule shift that cycles {@code NO_REVIVE} in does, until it cycles out again.
     *
     * @param resolved the {@code REVIVES} the sheet resolves now
     * @return {@code true} when the ceiling moved either way
     */
    public boolean syncTo(int resolved) {
        int target = Math.max(0, resolved);
        if (target > maxCharges) {
            return raiseTo(target);
        }
        if (target == maxCharges) {
            return false;
        }
        maxCharges = target;
        charges = Math.min(charges, target);
        return true;
    }

    /**
     * Applies the {@code emergency_recovery} ability's level parameters (D9).
     *
     * @param invulnTicksValue invulnerability granted by a revive, in ticks
     * @param kickMultiplierValue upward kick as a factor of {@code FLAP_VELOCITY}; {@code 0}
     *     keeps the bare behaviour of zeroing the velocity
     */
    public void configure(int invulnTicksValue, double kickMultiplierValue) {
        this.invulnTicks = Math.max(0, invulnTicksValue);
        this.kickMultiplier = Math.max(0, kickMultiplierValue);
    }

    /**
     * Spends one revive on a lethal hit.
     *
     * @return {@code true} when the bird was brought back
     */
    public boolean consume() {
        if (charges <= 0) {
            return false;
        }
        charges--;
        revives++;
        return true;
    }

    /**
     * The velocity a revived bird starts with: zero, or the auto-flap kick when the recovery
     * ability configured one.
     *
     * @param flapVelocity the resolved {@code FLAP_VELOCITY}
     * @return the vertical velocity in px/s (negative is upwards)
     */
    public double reviveVelocity(double flapVelocity) {
        return kickMultiplier <= 0 ? 0 : -flapVelocity * kickMultiplier;
    }

    /**
     * The y a revived bird is placed at: where it died, unless that is inside the ground band.
     *
     * @param y the y the bird died at
     * @return a y strictly above the ground line
     */
    public static double safeY(double y) {
        double ceiling = Playfield.GROUND_DEATH_Y - GROUND_CLEARANCE_PX;
        return y > ceiling ? ceiling : y;
    }

    /**
     * Whether a lethal hit would be survived right now.
     *
     * @return {@code true} when a revive is left
     */
    public boolean hasCharge() {
        return charges > 0;
    }

    /**
     * Revives left.
     *
     * @return the count
     */
    public int charges() {
        return charges;
    }

    /**
     * Revives the run started with.
     *
     * @return the count
     */
    public int maxCharges() {
        return maxCharges;
    }

    /**
     * Invulnerability a revive grants.
     *
     * @return the ticks
     */
    public int invulnTicks() {
        return invulnTicks;
    }

    /**
     * The upward kick as a factor of {@code FLAP_VELOCITY}; {@code 0} when there is none.
     *
     * @return the factor
     */
    public double kickMultiplier() {
        return kickMultiplier;
    }

    /**
     * Revives used this run.
     *
     * @return the count
     */
    public int revives() {
        return revives;
    }

    /**
     * Folds the state into a hash (D12).
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, charges);
        return MathUtil.fold(h, revives);
    }

    @Override
    public String toString() {
        return "ReviveSystem{" + charges + "/" + maxCharges + ", revives=" + revives + '}';
    }
}
