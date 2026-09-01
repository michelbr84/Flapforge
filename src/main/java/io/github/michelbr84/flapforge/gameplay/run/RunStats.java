package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable tally of a run (D11). {@link Run} updates it every tick; {@link RunResult} carries a
 * {@link #copy()} so the summary never changes under the UI.
 */
public final class RunStats {

    private int gatesPassed;
    private double points;
    private int coinsCollected;
    private int streak;
    private int streakBest;
    private int streakSteps;
    private int ticksAlive;
    private CollisionCause deathCause;
    private final Map<String, Integer> abilitiesUsed = new LinkedHashMap<>();
    private final List<String> modifiersTaken = new ArrayList<>();
    private final List<String> synergiesActivated = new ArrayList<>();
    private final List<String> bossesCleared = new ArrayList<>();
    private int phasesReached;
    private boolean objectiveMet;
    private int shieldAbsorbs;
    private int revives;
    private int nearMisses;

    /** Creates empty stats. */
    public RunStats() {
    }

    /**
     * Deep copy.
     *
     * @return an independent copy
     */
    public RunStats copy() {
        RunStats c = new RunStats();
        c.gatesPassed = gatesPassed;
        c.points = points;
        c.coinsCollected = coinsCollected;
        c.streak = streak;
        c.streakBest = streakBest;
        c.streakSteps = streakSteps;
        c.ticksAlive = ticksAlive;
        c.deathCause = deathCause;
        c.abilitiesUsed.putAll(abilitiesUsed);
        c.modifiersTaken.addAll(modifiersTaken);
        c.synergiesActivated.addAll(synergiesActivated);
        c.bossesCleared.addAll(bossesCleared);
        c.phasesReached = phasesReached;
        c.objectiveMet = objectiveMet;
        c.shieldAbsorbs = shieldAbsorbs;
        c.revives = revives;
        c.nearMisses = nearMisses;
        return c;
    }

    /**
     * Gates passed.
     *
     * @return the count
     */
    public int gatesPassed() {
        return gatesPassed;
    }

    /**
     * Sets the gates passed.
     *
     * @param value the count
     */
    public void setGatesPassed(int value) {
        gatesPassed = value;
    }

    /**
     * Points scored (gates × {@code SCORE_MULT}).
     *
     * @return the points
     */
    public double points() {
        return points;
    }

    /**
     * Sets the points.
     *
     * @param value the points
     */
    public void setPoints(double value) {
        points = value;
    }

    /**
     * Coins picked up during the run.
     *
     * @return the count
     */
    public int coinsCollected() {
        return coinsCollected;
    }

    /**
     * Adds collected coins.
     *
     * @param value the amount
     */
    public void addCoinsCollected(int value) {
        coinsCollected += value;
    }

    /**
     * Current clean-gate streak.
     *
     * @return the streak
     */
    public int streak() {
        return streak;
    }

    /**
     * Sets the streak (and tracks the best).
     *
     * @param value the streak
     */
    public void setStreak(int value) {
        streak = value;
        if (value > streakBest) {
            streakBest = value;
        }
    }

    /**
     * Best streak of the run.
     *
     * @return the best
     */
    public int streakBest() {
        return streakBest;
    }

    /**
     * Streak steps reached (economy bonus units).
     *
     * @return the count
     */
    public int streakSteps() {
        return streakSteps;
    }

    /**
     * Sets the streak steps.
     *
     * @param value the count
     */
    public void setStreakSteps(int value) {
        streakSteps = value;
    }

    /**
     * Ticks the bird was alive and flying.
     *
     * @return the count
     */
    public int ticksAlive() {
        return ticksAlive;
    }

    /** Counts one tick alive. */
    public void tickAlive() {
        ticksAlive++;
    }

    /**
     * What killed the bird.
     *
     * @return the cause, or {@code null} while alive
     */
    public CollisionCause deathCause() {
        return deathCause;
    }

    /**
     * Sets the death cause.
     *
     * @param value the cause
     */
    public void setDeathCause(CollisionCause value) {
        deathCause = value;
    }

    /**
     * Activations per ability id.
     *
     * @return an unmodifiable view
     */
    public Map<String, Integer> abilitiesUsed() {
        return Collections.unmodifiableMap(abilitiesUsed);
    }

    /**
     * Counts one ability activation.
     *
     * @param abilityId the ability
     */
    public void countAbilityUse(String abilityId) {
        abilitiesUsed.merge(abilityId, 1, Integer::sum);
    }

    /**
     * Modifiers taken, in order.
     *
     * @return an unmodifiable view
     */
    public List<String> modifiersTaken() {
        return Collections.unmodifiableList(modifiersTaken);
    }

    /**
     * Records a taken modifier.
     *
     * @param modifierId the modifier
     */
    public void addModifierTaken(String modifierId) {
        modifiersTaken.add(modifierId);
    }

    /**
     * Synergies activated, in order.
     *
     * @return an unmodifiable view
     */
    public List<String> synergiesActivated() {
        return Collections.unmodifiableList(synergiesActivated);
    }

    /**
     * Records an activated synergy (once per id).
     *
     * @param synergyId the synergy
     */
    public void addSynergyActivated(String synergyId) {
        if (!synergiesActivated.contains(synergyId)) {
            synergiesActivated.add(synergyId);
        }
    }

    /**
     * World bosses cleared this run.
     *
     * @return an unmodifiable view
     */
    public List<String> bossesCleared() {
        return Collections.unmodifiableList(bossesCleared);
    }

    /**
     * Records a cleared world boss (once per id).
     *
     * @param worldId the world
     */
    public void addBossCleared(String worldId) {
        if (!bossesCleared.contains(worldId)) {
            bossesCleared.add(worldId);
        }
    }

    /**
     * Boss phases (pattern index) reached.
     *
     * @return the count
     */
    public int phasesReached() {
        return phasesReached;
    }

    /**
     * Sets the boss phases reached.
     *
     * @param value the count
     */
    public void setPhasesReached(int value) {
        phasesReached = value;
    }

    /**
     * Whether the challenge objective was met.
     *
     * @return {@code true} when met
     */
    public boolean objectiveMet() {
        return objectiveMet;
    }

    /**
     * Sets the objective flag.
     *
     * @param value {@code true} when met
     */
    public void setObjectiveMet(boolean value) {
        objectiveMet = value;
    }

    /**
     * Shield absorbs.
     *
     * @return the count
     */
    public int shieldAbsorbs() {
        return shieldAbsorbs;
    }

    /** Counts one shield absorb. */
    public void countShieldAbsorb() {
        shieldAbsorbs++;
    }

    /**
     * Revives used.
     *
     * @return the count
     */
    public int revives() {
        return revives;
    }

    /** Counts one revive. */
    public void countRevive() {
        revives++;
    }

    /**
     * Near misses.
     *
     * @return the count
     */
    public int nearMisses() {
        return nearMisses;
    }

    /** Counts one near miss. */
    public void countNearMiss() {
        nearMisses++;
    }

    @Override
    public String toString() {
        return "RunStats{gates=" + gatesPassed + ", points=" + points + ", ticksAlive=" + ticksAlive
                + ", deathCause=" + deathCause + ", nearMisses=" + nearMisses + '}';
    }
}
