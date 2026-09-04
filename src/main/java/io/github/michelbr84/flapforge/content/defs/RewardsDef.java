package io.github.michelbr84.flapforge.content.defs;

import java.util.Objects;

/**
 * The {@code rewards} block of {@code economy.json} (§4, E1, E11, E32.a): the coin terms of the
 * run reward formula. Every term is a whole number of coins, so the only rounding in the formula
 * is the one the multipliers cause.
 *
 * @param participation paid once per run that passed a gate or lasted 180 ticks
 * @param firstRunBonus paid once, on the very first run of a profile
 * @param coinsPerGate paid per gate passed
 * @param coinsPerPoint paid per point scored (points carry {@code SCORE_MULT}, E1)
 * @param bossBonus paid per world boss cleared during the run
 * @param challengeBonus paid whenever a challenge objective was met, repeats included (E11)
 * @param streak the clean-gate streak block (D26)
 */
public record RewardsDef(long participation, long firstRunBonus, long coinsPerGate,
        long coinsPerPoint, long bossBonus, long challengeBonus, StreakRewardDef streak) {

    /**
     * Validates the components.
     *
     * @param participation the participation reward
     * @param firstRunBonus the first-run bonus
     * @param coinsPerGate coins per gate
     * @param coinsPerPoint coins per point
     * @param bossBonus coins per boss cleared
     * @param challengeBonus coins per challenge completion
     * @param streak the streak block
     */
    public RewardsDef {
        Objects.requireNonNull(streak, "streak");
        requireNotNegative("participation", participation);
        requireNotNegative("firstRunBonus", firstRunBonus);
        requireNotNegative("coinsPerGate", coinsPerGate);
        requireNotNegative("coinsPerPoint", coinsPerPoint);
        requireNotNegative("bossBonus", bossBonus);
        requireNotNegative("challengeBonus", challengeBonus);
    }

    private static void requireNotNegative(String name, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
    }
}
