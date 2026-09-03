package io.github.michelbr84.flapforge.gameplay.run;

/**
 * Lifecycle of a run (D11):
 * {@code READY → FLYING ↔ {BREATHER → CHOOSING_MODIFIER → RESUME_HOLD} ↔ {BOSS_WARNING → BOSS}
 * → DYING → FINISHED}.
 */
public enum RunPhase {
    /** Waiting for the first flap; the bird floats, nothing scrolls. */
    READY,
    /** The simulation runs. */
    FLYING,
    /** Spawning is deferred ahead of a modifier offer. */
    BREATHER,
    /** A modifier offer is open; the simulation is frozen. */
    CHOOSING_MODIFIER,
    /** Countdown after a choice; bird held, obstacles frozen. */
    RESUME_HOLD,
    /**
     * Boss banner (M8): {@code boss.atGate} was passed, spawning is suppressed for
     * {@code warningTicks} and the simulation keeps running — the bird flies through empty sky.
     */
    BOSS_WARNING,
    /**
     * Boss patterns streaming (M8): the phases loop through the spawner until
     * {@code surviveTicks} of flying time have passed; scoring, coins, streaks and the difficulty
     * curve all keep going.
     */
    BOSS,
    /** Lethal hit taken; the bird falls to the ground line while the world is frozen. */
    DYING,
    /** The run is over; the result is final. */
    FINISHED;

    /**
     * Tells whether the run is over.
     *
     * @return {@code true} for {@link #FINISHED}
     */
    public boolean isTerminal() {
        return this == FINISHED;
    }
}
