package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.modifier.DraftContext;

/**
 * What {@link ModifierDirector} needs from the world it is about to pause (D11, M6).
 *
 * <p>{@code Simulation} implements it. The seam exists so the draft can be reasoned about — and
 * tested — as "a schedule, a pool and a handful of things it may do to the run" rather than as a
 * second owner of the simulation. It extends {@link DraftContext} because the pool asks the same
 * world what the run's rules and loadout make possible (E12), and asks it every time rather than
 * once, so a rule a taken card turned on is visible to the next draft.
 *
 * <p>{@link #bossPending()} and {@link #bossActive()} are E7's gate. {@code Simulation}
 * answers them from its {@link BossEncounter} (M8) and the director refuses to open a breather —
 * or to freeze the run on an offer already waiting for clear air — while either is true. The
 * schedule entry is not consumed, and because the director compares {@code gatesPassed} against
 * the schedule with {@code >=}, a gate reached inside the warning or the fight fires on the
 * first tick after {@code BossCleared}: the offer opens in the first spawn interval after the
 * boss. The defaults stay {@code false} for the seams that have no boss at all.
 */
public interface DraftWorld extends DraftContext {

    /**
     * Whether the air ahead of the bird is empty, which is when a draft may freeze the run: no
     * obstacle overlaps the bird or stands between it and the right edge of the playfield.
     *
     * @return {@code true} when the run can be frozen without freezing a gap the player is inside
     */
    boolean isDraftPathClear();

    /**
     * Pushes the next obstacle further out (the breather, D11).
     *
     * @param intervals extra distance in gate intervals
     */
    void deferSpawn(double intervals);

    /**
     * The extra gate intervals a spawn has to be pushed out by for a window where
     * {@link #isDraftPathClear()} answers {@code true} to exist at all.
     *
     * <p>The breather widens one spacing; the window it opens is that spacing minus the distance
     * between the bird and the right edge of the playfield, so a world whose {@code GATE_INTERVAL}
     * is small enough closes the window altogether and the run waits for clear air that never
     * comes. With the fixed 1.5 intervals the window shuts at about 147 px, and
     * {@code ModifierDirectorTest.aTightCorridorStillOpensItsDraft} plays a 128 px corridor: with
     * this method returning 0 it opens no draft at all. The director takes the larger of this and
     * {@link ModifierDirector#BREATHER_INTERVALS}, so the shipped 160 px world keeps the 1.5 D11
     * writes down and a tighter world gets what it needs.
     *
     * @return the extra intervals, 0 when the world cannot say
     */
    default double clearanceIntervals() {
        return 0;
    }

    /**
     * Grants invulnerability ticks (the 30 that come with the resume, D11).
     *
     * @param ticks the ticks
     */
    void grantIFrames(int ticks);

    /**
     * Adds rule flags a card or a synergy turned on.
     *
     * @param extra the flags
     */
    void addRules(RuleSet extra);

    /**
     * Re-resolves {@code SHIELD_CHARGES} and {@code REVIVES} after a card changed them, so a
     * shield drafted mid-run is a shield the bird actually has (the limit M5 recorded in
     * {@link ShieldSystem} and {@link ReviveSystem}).
     */
    void refreshDefensiveCharges();

    /**
     * E7: whether a boss encounter is about to start — {@code gatesPassed >= boss.atGate - 1}
     * while the boss is still ahead; {@code false} once it has been cleared.
     *
     * @return {@code true} when a boss is one gate away or closer
     */
    default boolean bossPending() {
        return false;
    }

    /**
     * E7: whether a boss encounter is running ({@code BOSS_WARNING} or {@code BOSS}).
     *
     * @return {@code true} during the warning and the fight
     */
    default boolean bossActive() {
        return false;
    }
}
