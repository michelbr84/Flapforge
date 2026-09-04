package io.github.michelbr84.flapforge.gameplay.run;

/**
 * The coins and XP one run earned, term by term (E32.a). Every term of the coin formula is kept
 * so the game-over strip and the run summary can show the breakdown instead of one opaque number.
 *
 * <p>The terms before the multipliers add up to {@link #baseCoins()}; the paid amount is
 * {@code round(baseCoins × coinMult × tierMult × dailyMult) + coinsCollected}, because the coins
 * picked up in the world are already coins and are never multiplied.
 *
 * @param coins the coins the run pays, multipliers and collected coins included
 * @param xp the XP the run pays
 * @param coinsCollected coins picked up in the world (added after the multipliers)
 * @param participation the participation term (0 when the run was too short, E32.a)
 * @param gateCoins {@code coinsPerGate × gates}
 * @param pointCoins {@code coinsPerPoint × points}
 * @param streakCoins {@code streak.coins × streakSteps} plus the modifier streak bonuses
 * @param bossCoins {@code bossBonus × bosses} plus first-clear boss rewards
 * @param challengeCoins {@code challengeBonus} when the objective was met, plus the first
 *     completion reward
 * @param firstRunBonus the first-run bonus term
 * @param coinMult the {@code COIN_MULT} stat applied
 * @param tierMult the tier reward multiplier applied
 * @param dailyMult the daily reward multiplier applied (1 outside a daily run)
 */
public record RewardSummary(long coins, long xp, long coinsCollected, long participation,
        long gateCoins, long pointCoins, long streakCoins, long bossCoins, long challengeCoins,
        long firstRunBonus, double coinMult, double tierMult, double dailyMult) {

    /** A run that earned nothing. */
    public static final RewardSummary NONE =
            new RewardSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1);

    /**
     * The sum of the terms before any multiplier.
     *
     * @return the base coins
     */
    public long baseCoins() {
        return participation + firstRunBonus + gateCoins + pointCoins + streakCoins + bossCoins
                + challengeCoins;
    }

    /**
     * The product of the three reward multipliers.
     *
     * @return {@code coinMult × tierMult × dailyMult}
     */
    public double totalMultiplier() {
        return coinMult * tierMult * dailyMult;
    }

    /**
     * The coins the formula produced before the collected coins were added.
     *
     * @return {@code coins − coinsCollected}
     */
    public long earnedCoins() {
        return coins - coinsCollected;
    }
}
