package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * What reaching one player level grants (§4, D13): coins and unlock ids. The map key in
 * {@code economy.json.xp.levelRewards} is the level itself, written as a string.
 *
 * @param coins coins paid on the level-up (through {@code Wallet.add}, E32.a)
 * @param unlocks unlockable ids granted on the level-up
 */
public record LevelRewardDef(long coins, List<String> unlocks) {

    /**
     * Copies the unlock list.
     *
     * @param coins the coins granted
     * @param unlocks the ids granted
     */
    public LevelRewardDef {
        if (coins < 0) {
            throw new IllegalArgumentException("levelReward.coins must not be negative: " + coins);
        }
        unlocks = List.copyOf(unlocks);
    }
}
