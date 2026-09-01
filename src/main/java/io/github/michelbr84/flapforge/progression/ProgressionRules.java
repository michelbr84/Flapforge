package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.content.defs.LevelRewardDef;
import io.github.michelbr84.flapforge.content.defs.XpCurveDef;
import io.github.michelbr84.flapforge.gameplay.run.RewardContext;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunRewardCalculator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The economy numbers the progression pipeline reads (D13, D14).
 *
 * <p>{@link #fromEconomy(EconomyDef)} is the one adapter between {@code economy.json} and the
 * write path: it takes the currencies, turns {@code xp.curve} plus {@code xp.levelRewards} into a
 * {@link PlayerLevel}, and points the {@link RewardSource} at
 * {@link RunRewardCalculator#compute(RunResult, EconomyDef, RewardContext)}. Tests that only care
 * about the write order build one from three literals instead, which is why the reward formula
 * stays behind a functional interface rather than being called inline.
 *
 * <p>{@code LevelRewardDef.unlocks} is not read here: unlockable ids are granted by the unlock
 * evaluator (M4), which runs later in the same pass; this record only carries what a level
 * <em>pays</em>.
 *
 * @param currencies the currency ids, in {@code economy.json} order; the first is the one the
 *     coin rewards are paid in
 * @param levels the experience curve and its level rewards
 * @param rewards how a finished run turns into coins and experience
 */
public record ProgressionRules(List<String> currencies, PlayerLevel levels, RewardSource rewards) {

    /**
     * Copies the currency list and rejects nulls.
     *
     * @param currencies the currency ids
     * @param levels the experience curve
     * @param rewards the reward source
     */
    public ProgressionRules {
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(rewards, "rewards");
        currencies = List.copyOf(currencies);
        if (currencies.isEmpty()) {
            throw new IllegalArgumentException("at least one currency is required");
        }
    }

    /**
     * Rules over the single shipped currency.
     *
     * @param levels the experience curve
     * @param rewards the reward source
     * @return the rules
     */
    public static ProgressionRules of(PlayerLevel levels, RewardSource rewards) {
        return new ProgressionRules(List.of(PlayerProfile.CURRENCY_COINS), levels, rewards);
    }

    /**
     * The rules the shipped {@code economy.json} describes: its currencies, its XP curve with the
     * level rewards, and the real reward formula (E32.a) closed over the same economy.
     *
     * @param economy the loaded economy
     * @return the rules
     */
    public static ProgressionRules fromEconomy(EconomyDef economy) {
        Objects.requireNonNull(economy, "economy");
        String currency = economy.primaryCurrency() == null
                ? PlayerProfile.CURRENCY_COINS : economy.primaryCurrency();
        List<String> currencies = economy.currencies().isEmpty()
                ? List.of(currency) : economy.currencies();
        XpCurveDef curve = economy.xp().curve();
        PlayerLevel levels = new PlayerLevel(curve.base(), curve.growth(), curve.maxLevel(),
                levelRewards(economy, currency));
        return new ProgressionRules(currencies, levels,
                (result, ctx) -> RunRewardCalculator.compute(result, economy, ctx));
    }

    /**
     * Turns {@code economy.json.xp.levelRewards} — keyed by the level written as a string — into
     * the currency grants {@link PlayerLevel} understands. The validator has already rejected a
     * key that is not an integer in {@code [2, maxLevel]}; a stray one is skipped here rather
     * than crashing a launch.
     *
     * @param economy the economy
     * @param currency the currency the grants are paid in
     * @return the grants, keyed by level
     */
    private static Map<Integer, Map<String, Long>> levelRewards(EconomyDef economy,
            String currency) {
        Map<Integer, Map<String, Long>> out = new LinkedHashMap<>();
        for (Map.Entry<String, LevelRewardDef> entry : economy.xp().levelRewards().entrySet()) {
            int level;
            try {
                level = Integer.parseInt(entry.getKey().trim());
            } catch (NumberFormatException e) {
                continue;
            }
            long coins = entry.getValue().coins();
            if (coins > 0) {
                out.put(level, Map.of(currency, coins));
            }
        }
        return out;
    }

    /**
     * Rules that pay nothing, over the shipped currency and the §4 curve. Useful to a screen that
     * only reads statistics and to a test that only cares about the write order.
     *
     * @return the rules
     */
    public static ProgressionRules none() {
        return of(PlayerLevel.defaults(), (result, ctx) -> RewardSummary.NONE);
    }

    /**
     * The currency coin rewards are paid in.
     *
     * @return the first declared currency
     */
    public String primaryCurrency() {
        return currencies.get(0);
    }

    /**
     * Turns a finished run into the coins and experience it pays.
     *
     * <p>The shipped implementation is
     * {@code (result, ctx) -> RunRewardCalculator.compute(result, economy, ctx)}. It must be pure
     * — the same run and the same context always pay the same rewards — because
     * {@link ProgressionManager#apply} may be called only once per run and must be reproducible in
     * a headless simulation.
     */
    @FunctionalInterface
    public interface RewardSource {

        /**
         * Computes what a run pays.
         *
         * @param result the finished run
         * @param ctx everything the formula needs that the run itself does not carry
         * @return the rewards; never {@code null}
         */
        RewardSummary compute(RunResult result, RewardContext ctx);
    }

    /**
     * The multipliers a run was played under, handed to {@link ProgressionManager#apply} by the
     * caller that owns the run's stat sheet (D14: progression writes, it does not resolve stats).
     *
     * @param coinMult the run's resolved {@code COIN_MULT}
     * @param xpMult the run's resolved {@code XP_MULT}
     * @param tierRewardMult {@code tier.rewardMult}
     * @param dailyRewardMult {@code economy.daily.rewardMult}, or 1 outside a daily run
     */
    public record RewardMultipliers(double coinMult, double xpMult, double tierRewardMult,
            double dailyRewardMult) {

        /** Every multiplier at 1. */
        public static final RewardMultipliers NEUTRAL = new RewardMultipliers(1, 1, 1, 1);

        /**
         * Replaces a non-finite or negative multiplier with 1, so a broken stat sheet cannot make
         * a reward negative or {@code NaN}.
         *
         * @param coinMult the coin multiplier
         * @param xpMult the experience multiplier
         * @param tierRewardMult the tier multiplier
         * @param dailyRewardMult the daily multiplier
         */
        public RewardMultipliers {
            coinMult = sane(coinMult);
            xpMult = sane(xpMult);
            tierRewardMult = sane(tierRewardMult);
            dailyRewardMult = sane(dailyRewardMult);
        }

        private static double sane(double value) {
            return Double.isFinite(value) && value >= 0 ? value : 1;
        }
    }
}
