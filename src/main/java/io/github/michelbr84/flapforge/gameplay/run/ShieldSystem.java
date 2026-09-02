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
 * <p><b>Known limit for M6.</b> {@code maxCharges} is the {@code SHIELD_CHARGES} the sheet
 * resolved at run start; nothing in M5 can change that stat mid-run, so the snapshot is exact
 * today. A drafted modifier that adds {@code SHIELD_CHARGES} mid-run (E12) would not be seen, so
 * the M6 director must either re-resolve the stat when it applies a card or keep those stats out
 * of the pool.
 */
public final class ShieldSystem {

    /** Invulnerability ticks an absorb grants when no ability configured it (D9). */
    public static final int DEFAULT_INVULN_TICKS = 45;

    /** The ability id the shield reports its regenerated charge under (HUD and audio cue). */
    public static final String ABILITY_ID = "shield";

    private final int maxCharges;
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
