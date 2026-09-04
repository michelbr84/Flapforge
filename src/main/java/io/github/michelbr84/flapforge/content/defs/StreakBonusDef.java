package io.github.michelbr84.flapforge.content.defs;

/**
 * The extra coins one modifier pays per clean-gate streak step (D26, E32.a):
 * {@code "streakBonus": { "coins": 10 }}.
 *
 * <p>It is a block rather than a bare number so a later balance pass can pay a streak in something
 * else (XP, a charge) without changing the shape of every modifier that already has one. A missing
 * block binds to {@code null}, which is what every modifier but {@code streak_bounty} ships.
 *
 * @param coins coins added to {@code economy.rewards.streak.coins} for each streak step
 */
public record StreakBonusDef(long coins) {
}
