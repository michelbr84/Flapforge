package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What one pass of {@link ProgressionManager} changed (D14, E31.b).
 *
 * <p>It carries plain facts and nothing else: no events, no strings meant for a player, no
 * references to the toast layer. {@code progression} must not import {@code event} — the screen
 * that called {@code apply} maps these facts to {@code GameEvent}s and to the wording of the
 * moment, which is what keeps the whole pipeline testable headless.
 *
 * @param rewardSummary what the run paid, term by term (E32.a)
 * @param levelUps the levels reached by this pass, ascending; empty when no level was crossed
 * @param levelRewardsGranted currency grants paid by those levels, summed per currency
 * @param achievementsUnlocked achievement ids unlocked by this pass
 * @param unlocksGranted namespaced unlock ids granted by this pass
 * @param challengeFirstCompleted whether this run completed its challenge for the first time (E11)
 * @param dailyRecorded whether the run counted as an attempt of the daily pick (E27)
 * @param achievementRewardsGranted the coins each unlocked achievement paid, by achievement id
 *     (M8, E32.a: paid through the wallet and counted in {@code coinsEarned}); an achievement
 *     that pays no coins is absent
 */
public record ProgressionOutcome(RewardSummary rewardSummary, List<Integer> levelUps,
        Map<String, Long> levelRewardsGranted, List<String> achievementsUnlocked,
        List<String> unlocksGranted, boolean challengeFirstCompleted, boolean dailyRecorded,
        Map<String, Long> achievementRewardsGranted) {

    /** Nothing happened: no rewards, no levels, no unlocks. */
    public static final ProgressionOutcome EMPTY = new ProgressionOutcome(RewardSummary.NONE,
            List.of(), Map.of(), List.of(), List.of(), false, false, Map.of());

    /**
     * Copies the collections into deterministic, unmodifiable ones.
     *
     * @param rewardSummary the rewards
     * @param levelUps the levels crossed
     * @param levelRewardsGranted the level grants
     * @param achievementsUnlocked the achievements
     * @param unlocksGranted the unlocks
     * @param challengeFirstCompleted the first-completion flag
     * @param dailyRecorded the daily flag
     * @param achievementRewardsGranted the coins the achievements paid
     */
    public ProgressionOutcome {
        Objects.requireNonNull(rewardSummary, "rewardSummary");
        levelUps = List.copyOf(levelUps);
        levelRewardsGranted =
                Collections.unmodifiableMap(new LinkedHashMap<>(levelRewardsGranted));
        achievementsUnlocked = List.copyOf(achievementsUnlocked);
        unlocksGranted = List.copyOf(unlocksGranted);
        achievementRewardsGranted = Collections.unmodifiableMap(
                new LinkedHashMap<>(achievementRewardsGranted));
    }

    /**
     * The pre-M8 shape: an outcome whose achievements paid nothing.
     *
     * @param rewardSummary the rewards
     * @param levelUps the levels crossed
     * @param levelRewardsGranted the level grants
     * @param achievementsUnlocked the achievements
     * @param unlocksGranted the unlocks
     * @param challengeFirstCompleted the first-completion flag
     * @param dailyRecorded the daily flag
     */
    public ProgressionOutcome(RewardSummary rewardSummary, List<Integer> levelUps,
            Map<String, Long> levelRewardsGranted, List<String> achievementsUnlocked,
            List<String> unlocksGranted, boolean challengeFirstCompleted,
            boolean dailyRecorded) {
        this(rewardSummary, levelUps, levelRewardsGranted, achievementsUnlocked, unlocksGranted,
                challengeFirstCompleted, dailyRecorded, Map.of());
    }

    /**
     * The coins the achievements of this pass paid, summed (E32.a).
     *
     * @return the total, 0 when no achievement paid anything
     */
    public long achievementCoins() {
        long total = 0;
        for (Long coins : achievementRewardsGranted.values()) {
            total += coins == null ? 0 : coins;
        }
        return total;
    }

    /**
     * The coins the level rewards of this pass paid, summed over every currency.
     *
     * @return the total, 0 when no level paid anything
     */
    public long levelRewardCoins() {
        long total = 0;
        for (Long coins : levelRewardsGranted.values()) {
            total += coins == null ? 0 : coins;
        }
        return total;
    }

    /**
     * Whether any level was reached.
     *
     * @return {@code true} when {@link #levelUps()} is not empty
     */
    public boolean leveledUp() {
        return !levelUps.isEmpty();
    }

    /**
     * The highest level reached by this pass.
     *
     * @return the level, or 0 when none was crossed
     */
    public int highestLevel() {
        return levelUps.isEmpty() ? 0 : levelUps.get(levelUps.size() - 1);
    }

    /**
     * Whether anything at all happened, which is what a screen checks before showing a strip.
     *
     * @return {@code true} when a reward, a level, an achievement or an unlock landed
     */
    public boolean isEmpty() {
        return rewardSummary.coins() == 0 && rewardSummary.xp() == 0 && levelUps.isEmpty()
                && achievementsUnlocked.isEmpty() && unlocksGranted.isEmpty()
                && !challengeFirstCompleted && !dailyRecorded;
    }
}
