package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * What clearing something pays: coins plus namespaced unlockable ids (§4). It is the reward shape
 * of a world boss, a challenge and an achievement; {@link LevelRewardDef} is the same pair under
 * {@code economy.xp.levelRewards}.
 *
 * <p>Every coin here goes through {@code Wallet.add} and counts in {@code statistics.coinsEarned}
 * (E32.a), and every id here is an edge of the unlock graph.
 *
 * @param coins the coins paid
 * @param unlocks the namespaced unlockable ids granted
 */
public record RewardDef(long coins, List<String> unlocks) {

    /** A reward that pays nothing. */
    public static final RewardDef NONE = new RewardDef(0, List.of());

    /**
     * Copies the id list.
     *
     * @throws IllegalArgumentException when the coin amount is negative
     */
    public RewardDef {
        if (coins < 0) {
            throw new IllegalArgumentException("reward.coins must not be negative: " + coins);
        }
        unlocks = List.copyOf(unlocks);
    }
}
