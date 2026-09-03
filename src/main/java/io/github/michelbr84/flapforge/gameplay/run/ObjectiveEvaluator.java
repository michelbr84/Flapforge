package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.content.defs.ObjectiveDef;
import io.github.michelbr84.flapforge.content.defs.ObjectiveType;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import java.util.List;
import java.util.Objects;

/**
 * Judges a challenge objective every tick (D11, M8): {@code SURVIVE_GATES},
 * {@code SURVIVE_TICKS}, {@code COLLECT_COINS}, {@code REACH_POINTS} and {@code BOSS_CLEARED},
 * against the run's own tallies — the gates, points and coins {@code RunStats} mirrors from the
 * simulation, the ticks the run has flown, and the boss the run actually spawned (a challenge
 * boss when the challenge carries a {@code boss} block, E26).
 *
 * <p>The objective is met once. The evaluator latches on the first tick the condition holds,
 * emits one {@link TickFact.ObjectiveMet} and never fires again; the run continues — the player
 * keeps flying for the streak, the coins and the record, and {@code RunStats.objectiveMet} stays
 * set however the run ends. A profile-less run without a challenge has no evaluator at all.
 *
 * <p>{@link #progress} is the HUD's number: how far the run is towards {@code value}, capped at
 * the target once met.
 */
public final class ObjectiveEvaluator {

    private static final long HASH_SEED = MathUtil.fnv1a64("flapforge-objective");

    private final String challengeId;
    private final ObjectiveDef objective;
    private boolean met;

    /**
     * Creates the evaluator of one challenge run.
     *
     * @param challengeId the challenge
     * @param objective what completes it
     */
    public ObjectiveEvaluator(String challengeId, ObjectiveDef objective) {
        this.challengeId = Objects.requireNonNull(challengeId, "challengeId");
        this.objective = Objects.requireNonNull(objective, "objective");
    }

    /**
     * Whether an objective holds for a set of tallies (pure).
     *
     * @param objective the objective
     * @param gatesPassed gates passed
     * @param points points scored
     * @param coinsCollected coins picked up
     * @param ticksAlive ticks the run has flown
     * @param bossCleared whether the run's boss was cleared
     * @return {@code true} when the objective is satisfied
     */
    public static boolean isMet(ObjectiveDef objective, long gatesPassed, double points,
            long coinsCollected, long ticksAlive, boolean bossCleared) {
        switch (objective.type()) {
            case SURVIVE_GATES:
                return gatesPassed >= objective.value();
            case SURVIVE_TICKS:
                return ticksAlive >= objective.value();
            case COLLECT_COINS:
                return coinsCollected >= objective.value();
            case REACH_POINTS:
                return points >= objective.value();
            case BOSS_CLEARED:
            default:
                return bossCleared;
        }
    }

    /**
     * The tally an objective is measured on (pure).
     *
     * @param objective the objective
     * @param gatesPassed gates passed
     * @param points points scored
     * @param coinsCollected coins picked up
     * @param ticksAlive ticks the run has flown
     * @param bossCleared whether the run's boss was cleared
     * @return the current value towards {@code objective.value()}
     */
    public static long currentOf(ObjectiveDef objective, long gatesPassed, double points,
            long coinsCollected, long ticksAlive, boolean bossCleared) {
        switch (objective.type()) {
            case SURVIVE_GATES:
                return gatesPassed;
            case SURVIVE_TICKS:
                return ticksAlive;
            case COLLECT_COINS:
                return coinsCollected;
            case REACH_POINTS:
                return (long) Math.floor(points);
            case BOSS_CLEARED:
            default:
                return bossCleared ? 1 : 0;
        }
    }

    /**
     * Judges the objective on this tick's tallies.
     *
     * @param gatesPassed gates passed
     * @param points points scored
     * @param coinsCollected coins picked up
     * @param ticksAlive ticks the run has flown, this one included
     * @param bossCleared whether the run's boss was cleared
     * @param facts where the {@link TickFact.ObjectiveMet} goes, once
     * @return {@code true} on the tick the objective is met, {@code false} before and after
     */
    public boolean tick(long gatesPassed, double points, long coinsCollected, long ticksAlive,
            boolean bossCleared, List<TickFact> facts) {
        if (met) {
            return false;
        }
        if (!isMet(objective, gatesPassed, points, coinsCollected, ticksAlive, bossCleared)) {
            return false;
        }
        met = true;
        facts.add(new TickFact.ObjectiveMet(challengeId));
        return true;
    }

    /**
     * Judges the objective on a run's stats — the same test as {@link #tick}, for a caller that
     * holds a {@link RunStats} (tests, tools).
     *
     * @param stats the run stats
     * @param bossCleared whether the run's boss was cleared
     * @param facts where the {@link TickFact.ObjectiveMet} goes, once
     * @return {@code true} on the tick the objective is met
     */
    public boolean tick(RunStats stats, boolean bossCleared, List<TickFact> facts) {
        return tick(stats.gatesPassed(), stats.points(), stats.coinsCollected(),
                stats.ticksAlive(), bossCleared, facts);
    }

    /**
     * How far the run is towards the objective, capped at the target.
     *
     * @param gatesPassed gates passed
     * @param points points scored
     * @param coinsCollected coins picked up
     * @param ticksAlive ticks the run has flown
     * @param bossCleared whether the run's boss was cleared
     * @return the current value, at most {@link #target()}
     */
    public long progress(long gatesPassed, double points, long coinsCollected, long ticksAlive,
            boolean bossCleared) {
        return Math.min(objective.value(),
                currentOf(objective, gatesPassed, points, coinsCollected, ticksAlive,
                        bossCleared));
    }

    /**
     * The number the objective asks for.
     *
     * @return {@code objective.value()}
     */
    public long target() {
        return objective.value();
    }

    /**
     * The objective's type.
     *
     * @return the type
     */
    public ObjectiveType type() {
        return objective.type();
    }

    /**
     * The objective.
     *
     * @return the definition
     */
    public ObjectiveDef objective() {
        return objective;
    }

    /**
     * The challenge being judged.
     *
     * @return the id
     */
    public String challengeId() {
        return challengeId;
    }

    /**
     * Whether the objective has been met.
     *
     * @return {@code true} once latched
     */
    public boolean isMet() {
        return met;
    }

    /**
     * Folds the latch into the run's state hash (D12).
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, HASH_SEED);
        return MathUtil.fold(h, met ? 1 : 0);
    }
}
