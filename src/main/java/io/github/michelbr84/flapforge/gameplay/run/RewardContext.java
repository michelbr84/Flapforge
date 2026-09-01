package io.github.michelbr84.flapforge.gameplay.run;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Everything the reward formula needs that is not in the {@link RunResult} or in
 * {@code economy.json} (E32.a): what the profile already knows and the multipliers resolved for
 * the run. The progression layer builds it; the calculator stays pure.
 *
 * @param firstRun {@code true} when the profile has no finished run yet
 *     ({@code statistics.totalRuns == 0}), which pays {@code firstRunBonus}
 * @param firstChallengeCompletion {@code true} when this run is the first completion of its
 *     challenge (E11)
 * @param firstBossClears the world ids this run cleared for the first time ever (E26)
 * @param coinMult the resolved {@code COIN_MULT} stat
 * @param xpMult the resolved {@code XP_MULT} stat
 * @param tierRewardMult {@code difficulty.json.tiers[].rewardMult} of the tier played
 * @param dailyRewardMult {@code economy.json.daily.rewardMult}, applied only to a daily run
 */
public record RewardContext(boolean firstRun, boolean firstChallengeCompletion,
        Set<String> firstBossClears, double coinMult, double xpMult, double tierRewardMult,
        double dailyRewardMult) {

    /**
     * Copies the boss set into a deterministic, unmodifiable one.
     *
     * @param firstRun whether this is the profile's first run
     * @param firstChallengeCompletion whether the challenge was completed for the first time
     * @param firstBossClears the worlds cleared for the first time
     * @param coinMult the coin multiplier
     * @param xpMult the XP multiplier
     * @param tierRewardMult the tier reward multiplier
     * @param dailyRewardMult the daily reward multiplier
     */
    public RewardContext {
        firstBossClears = firstBossClears == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(firstBossClears));
    }

    /**
     * A context with no first-time bonuses and every multiplier at 1.
     *
     * @return the context
     */
    public static RewardContext plain() {
        return new RewardContext(false, false, Set.of(), 1, 1, 1, 1);
    }

    /**
     * A context for a profile's very first run, every multiplier at 1.
     *
     * @return the context
     */
    public static RewardContext ofFirstRun() {
        return new RewardContext(true, false, Set.of(), 1, 1, 1, 1);
    }

    /**
     * Copy with other multipliers.
     *
     * @param newCoinMult the coin multiplier
     * @param newXpMult the XP multiplier
     * @param newTierRewardMult the tier reward multiplier
     * @param newDailyRewardMult the daily reward multiplier
     * @return the copy
     */
    public RewardContext withMultipliers(double newCoinMult, double newXpMult,
            double newTierRewardMult, double newDailyRewardMult) {
        return new RewardContext(firstRun, firstChallengeCompletion, firstBossClears, newCoinMult,
                newXpMult, newTierRewardMult, newDailyRewardMult);
    }
}
