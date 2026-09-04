package io.github.michelbr84.flapforge.gameplay.run;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Everything the reward formula needs that is not in the {@link RunResult} or in
 * {@code economy.json} (E32.a): what the profile already knows, what the content pays for a
 * first clear and the multipliers resolved for the run. The progression layer builds it; the
 * calculator stays pure.
 *
 * <p>The two first-clear coin terms are resolved here rather than in the calculator (M8): the
 * amounts live in {@code worlds.json} ({@code boss.reward.coins}) and {@code challenges.json}
 * ({@code rewards.coins}), which the calculator — a function of the result, the economy and this
 * context — never opens. {@code ProgressionManager.rewardContext} sums
 * {@code boss.reward.coins} over {@link #firstBossClears} into {@link #firstBossClearCoins} and
 * reads the challenge's {@code rewards.coins} into {@link #firstChallengeCoins} when
 * {@link #firstChallengeCompletion} holds; both are 0 otherwise, so a repeat pays only the
 * economy's {@code bossBonus} / {@code challengeBonus} (E11, E26).
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
 * @param firstBossClearCoins {@code Σ boss.reward.coins} over {@link #firstBossClears}
 * @param firstChallengeCoins the challenge's {@code rewards.coins}, only on the first completion
 */
public record RewardContext(boolean firstRun, boolean firstChallengeCompletion,
        Set<String> firstBossClears, double coinMult, double xpMult, double tierRewardMult,
        double dailyRewardMult, long firstBossClearCoins, long firstChallengeCoins) {

    /**
     * Copies the boss set into a deterministic, unmodifiable one and rejects negative coins.
     *
     * @param firstRun whether this is the profile's first run
     * @param firstChallengeCompletion whether the challenge was completed for the first time
     * @param firstBossClears the worlds cleared for the first time
     * @param coinMult the coin multiplier
     * @param xpMult the XP multiplier
     * @param tierRewardMult the tier reward multiplier
     * @param dailyRewardMult the daily reward multiplier
     * @param firstBossClearCoins the first-clear boss coins
     * @param firstChallengeCoins the first-completion challenge coins
     */
    public RewardContext {
        firstBossClears = firstBossClears == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(firstBossClears));
        if (firstBossClearCoins < 0 || firstChallengeCoins < 0) {
            throw new IllegalArgumentException("first-clear coins must not be negative: "
                    + firstBossClearCoins + "/" + firstChallengeCoins);
        }
    }

    /**
     * A context with no first-clear coins (the M3 shape, kept for the tests and the tools that
     * only care about the flags and the multipliers).
     *
     * @param firstRun whether this is the profile's first run
     * @param firstChallengeCompletion whether the challenge was completed for the first time
     * @param firstBossClears the worlds cleared for the first time
     * @param coinMult the coin multiplier
     * @param xpMult the XP multiplier
     * @param tierRewardMult the tier reward multiplier
     * @param dailyRewardMult the daily reward multiplier
     */
    public RewardContext(boolean firstRun, boolean firstChallengeCompletion,
            Set<String> firstBossClears, double coinMult, double xpMult, double tierRewardMult,
            double dailyRewardMult) {
        this(firstRun, firstChallengeCompletion, firstBossClears, coinMult, xpMult,
                tierRewardMult, dailyRewardMult, 0, 0);
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
                newXpMult, newTierRewardMult, newDailyRewardMult, firstBossClearCoins,
                firstChallengeCoins);
    }

    /**
     * Copy with the first-clear coin terms (M8).
     *
     * @param newFirstBossClearCoins {@code Σ boss.reward.coins} over the first clears
     * @param newFirstChallengeCoins the challenge's first-completion coins
     * @return the copy
     */
    public RewardContext withFirstClearCoins(long newFirstBossClearCoins,
            long newFirstChallengeCoins) {
        return new RewardContext(firstRun, firstChallengeCompletion, firstBossClears, coinMult,
                xpMult, tierRewardMult, dailyRewardMult, newFirstBossClearCoins,
                newFirstChallengeCoins);
    }
}
