package io.github.michelbr84.flapforge.content.defs;

/**
 * The streak block of {@code economy.json.rewards} (D26, §4): every {@code step} clean gates pay
 * {@code coins} once.
 *
 * @param step the streak length that pays one reward step
 * @param coins the coins one step is worth
 */
public record StreakRewardDef(int step, long coins) {

    /**
     * Validates the components.
     *
     * @param step the streak step
     * @param coins the coins per step
     */
    public StreakRewardDef {
        if (step < 1) {
            throw new IllegalArgumentException("streak.step must be at least 1: " + step);
        }
        if (coins < 0) {
            throw new IllegalArgumentException("streak.coins must not be negative: " + coins);
        }
    }
}
