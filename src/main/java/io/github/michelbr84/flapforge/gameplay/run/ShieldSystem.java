package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.core.MathUtil;

/**
 * The shield charges of a run (D9). Stat-driven: a shield exists whenever {@code SHIELD_CHARGES}
 * resolves above zero, so the forge node "start with one shield charge" works on its own, with no
 * ability equipped, and {@code NO_DEFENSIVE_ABILITIES} removes it by zeroing the stat (D8) rather
 * than by a second rule here.
 *
 * <p>The {@code shield} ability adds behaviour on top and nothing else: at equip time it
 * {@linkplain #configure configures} how long the absorb makes the bird invulnerable and, from
 * level 2, how often a spent charge grows back. Both have defaults, which is exactly what a bare
 * stat-driven shield uses.
 *
 * <p><b>Mid-run charges (M6).</b> {@code maxCharges} starts as the {@code SHIELD_CHARGES} the
 * sheet resolved at run start, and {@link #raiseTo(int)} moves it when a drafted card raises the
 * stat (E12) — {@code Simulation.refreshDefensiveCharges} calls it every time the modifier
 * director takes something. It only raises: nothing gives a spent charge back except the
 * ability's own regeneration.
 */
public final class ShieldSystem {

    /** Invulnerability ticks an absorb grants when no ability configured it (D9). */
    public static final int DEFAULT_INVULN_TICKS = 45;

    /** The ability id the shield reports its regenerated charge under (HUD and audio cue). */
    public static final String ABILITY_ID = "shield";

    private int maxCharges;
    private int charges;
    private int invulnTicks = DEFAULT_INVULN_TICKS;
    private int regenEveryGates;
    private int absorbs;
    private int regenerated;

    /**
     * Creates the system with the charges the sheet resolved.
     *
     * @param charges the resolved {@code SHIELD_CHARGES} (negative counts as none)
     */
    public ShieldSystem(int charges) {
        this.maxCharges = Math.max(0, charges);
        this.charges = maxCharges;
    }

    /**
     * Raises the ceiling to a freshly resolved {@code SHIELD_CHARGES} and hands the difference over as
     * usable charges (M6).
     *
     * <p>This is the answer to the limit M5 recorded above: the roguelite draft can add shield charges
     * in the middle of a run, and a snapshot taken at run start would silently swallow the card.
     * It only ever raises — a card cannot take a charge the player has already been given, and a
     * spent charge stays spent.
     *
     * @param resolved the {@code SHIELD_CHARGES} the sheet resolves now
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
     * Applies the {@code shield} ability's level parameters (D9).
     *
     * @param invulnTicksValue invulnerability granted by an absorb, in ticks
     * @param regenEveryGatesValue passed gates that give one charge back; {@code 0} never
     */
    public void configure(int invulnTicksValue, int regenEveryGatesValue) {
        this.invulnTicks = Math.max(0, invulnTicksValue);
        this.regenEveryGates = Math.max(0, regenEveryGatesValue);
    }

    /**
     * Spends one charge on a lethal hit.
     *
     * @return {@code true} when a charge absorbed the hit
     */
    public boolean absorb() {
        if (charges <= 0) {
            return false;
        }
        charges--;
        absorbs++;
        return true;
    }

    /**
     * Gives one charge back when the gate count reaches the regeneration cadence.
     *
     * <p>The caller must report a restored charge: the simulation turns a {@code true} into a
     * {@code TickFact.AbilityReady(}{@link #ABILITY_ID}{@code )}, which is what the HUD pip and
     * the shield sound react to.
     *
     * @param gatesPassed the gates passed so far
     * @return {@code true} when a charge was restored
     */
    public boolean onGatePassed(int gatesPassed) {
        if (regenEveryGates <= 0 || charges >= maxCharges || gatesPassed <= 0
                || gatesPassed % regenEveryGates != 0) {
            return false;
        }
        charges++;
        regenerated++;
        return true;
    }

    /**
     * Whether a lethal hit would be absorbed right now.
     *
     * @return {@code true} when a charge is left
     */
    public boolean hasCharge() {
        return charges > 0;
    }

    /**
     * Charges left.
     *
     * @return the count
     */
    public int charges() {
        return charges;
    }

    /**
     * Charges the run started with, and the ceiling regeneration restores up to.
     *
     * @return the count
     */
    public int maxCharges() {
        return maxCharges;
    }

    /**
     * Invulnerability an absorb grants.
     *
     * @return the ticks
     */
    public int invulnTicks() {
        return invulnTicks;
    }

    /**
     * Passed gates between two restored charges; {@code 0} when charges never come back.
     *
     * @return the cadence
     */
    public int regenEveryGates() {
        return regenEveryGates;
    }

    /**
     * Hits absorbed this run.
     *
     * @return the count
     */
    public int absorbs() {
        return absorbs;
    }

    /**
     * Charges regenerated this run.
     *
     * @return the count
     */
    public int regenerated() {
        return regenerated;
    }

    /**
     * Folds the state into a hash (D12).
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, charges);
        return MathUtil.fold(h, absorbs);
    }

    @Override
    public String toString() {
        return "ShieldSystem{" + charges + "/" + maxCharges + ", absorbs=" + absorbs + '}';
    }
}
