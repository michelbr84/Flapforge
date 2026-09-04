package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * The daily challenge block of {@code economy.json} (§4, E27): which tiers the daily pick may
 * draw from, how many modifiers it forces, and the reward multiplier a daily run pays.
 *
 * @param tierPool tier ids the daily may pick
 * @param forcedModifierCount modifiers pre-taken at the start of a daily run
 * @param rewardMult multiplier applied to the coin reward of a daily run
 */
public record DailyDef(List<String> tierPool, int forcedModifierCount, double rewardMult) {

    /**
     * Copies the tier pool.
     *
     * @param tierPool the tier ids
     * @param forcedModifierCount the forced modifier count
     * @param rewardMult the reward multiplier
     */
    public DailyDef {
        tierPool = List.copyOf(tierPool);
        if (forcedModifierCount < 0) {
            throw new IllegalArgumentException(
                    "daily.forcedModifierCount must not be negative: " + forcedModifierCount);
        }
        if (rewardMult <= 0) {
            throw new IllegalArgumentException("daily.rewardMult must be positive: " + rewardMult);
        }
    }
}
