package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.core.MathUtil;

/**
 * The clean-gate streak (D26, E26).
 *
 * <p>A gate is <em>clean</em> when it was passed with no near miss, no shield absorb and no
 * revive. The near-miss half is scoped to the column: the simulation marks the obstacle the bird
 * grazed as dirty (the inflated bird box overlapped one of that column's lethal hitboxes, which
 * can only happen while the bird's x-range overlaps {@code [gate.x, gate.x + PIPE_BODY_W]}), and
 * that flag is what {@code cleanColumn} carries here. The absorb/revive half is not tied to one
 * column — the charge is spent between two gates — so it dirties the window through
 * {@link #markShieldAbsorb()} / {@link #markRevive()} and is cleared by the next gate.
 *
 * <p>On each gate: clean → {@code streak++}, dirty → {@code streak = 0}; {@code streakBest} is the
 * maximum ever reached and {@code streakSteps} counts how many times the streak landed on a
 * multiple of {@code economy.rewards.streak.step} — the unit the reward calculator pays
 * {@code streak.coins} for.
 */
public final class StreakTracker {

    /** Step used when no economy is bound ({@code economy.json.rewards.streak.step}). */
    public static final int DEFAULT_STEP = 5;

    private final int step;
    private int streak;
    private int best;
    private int steps;
    private boolean windowDirty;

    /**
     * Creates a tracker.
     *
     * @param step the streak length that pays one reward step; values below 1 disable the steps
     */
    public StreakTracker(int step) {
        this.step = step;
    }

    /** Creates a tracker with {@link #DEFAULT_STEP}. */
    public StreakTracker() {
        this(DEFAULT_STEP);
    }

    /**
     * Records a gate the bird just cleared.
     *
     * @param cleanColumn {@code true} when the gate's column was never grazed
     * @return {@code true} when the streak value changed (the caller emits
     *     {@code TickFact.StreakChanged})
     */
    public boolean onGatePassed(boolean cleanColumn) {
        boolean clean = cleanColumn && !windowDirty;
        windowDirty = false;
        if (!clean) {
            boolean changed = streak != 0;
            streak = 0;
            return changed;
        }
        streak++;
        if (streak > best) {
            best = streak;
        }
        if (step > 0 && streak % step == 0) {
            steps++;
        }
        return true;
    }

    /** Dirties the current window: a shield charge absorbed a hit (D26). */
    public void markShieldAbsorb() {
        windowDirty = true;
    }

    /** Dirties the current window: a revive saved the bird (D26). */
    public void markRevive() {
        windowDirty = true;
    }

    /** Dirties the current window for any other streak breaker. */
    public void markDirty() {
        windowDirty = true;
    }

    /**
     * Current streak.
     *
     * @return the count of consecutive clean gates
     */
    public int streak() {
        return streak;
    }

    /**
     * Best streak of the run.
     *
     * @return the maximum ever reached
     */
    public int best() {
        return best;
    }

    /**
     * Reward steps reached.
     *
     * @return the number of times the streak landed on a multiple of {@link #step()}
     */
    public int steps() {
        return steps;
    }

    /**
     * The streak length that pays one reward step.
     *
     * @return the step
     */
    public int step() {
        return step;
    }

    /**
     * Tells whether an absorb or a revive already dirtied the gate being flown.
     *
     * @return {@code true} when the next gate cannot be clean
     */
    public boolean isWindowDirty() {
        return windowDirty;
    }

    /**
     * Folds the tracker state into a hash.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, streak);
        h = MathUtil.fold(h, best);
        h = MathUtil.fold(h, steps);
        return MathUtil.fold(h, windowDirty ? 1 : 0);
    }

    @Override
    public String toString() {
        return "StreakTracker{streak=" + streak + ", best=" + best + ", steps=" + steps
                + ", step=" + step + '}';
    }
}
