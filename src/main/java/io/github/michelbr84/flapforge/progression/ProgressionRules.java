package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.content.defs.LevelRewardDef;
import io.github.michelbr84.flapforge.content.defs.RewardDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
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
 * <p>{@link #fromContent(GameContent)} is the adapter the game uses: it takes the currencies of
 * {@code economy.json}, turns {@code xp.curve} plus {@code xp.levelRewards} into a
 * {@link PlayerLevel}, points the {@link RewardSource} at
 * {@link RunRewardCalculator#compute(RunResult, EconomyDef, RewardContext)}, and reads the
 * first-clear rewards of {@code worlds.json} ({@code boss.reward}) and {@code challenges.json}
 * ({@code rewards}) into a {@link FirstClearRewards} (M8, E11, E26).
 * {@link #fromEconomy(EconomyDef)} is the same over the economy alone, with no first-clear
 * rewards — the shape every milestone before M8 used and the one tools without the full content
 * still use. Tests that only care about the write order build one from three literals instead,
 * which is why the reward formula stays behind a functional interface rather than being called
 * inline.
 *
 * <p>{@code LevelRewardDef.unlocks} is not read here: unlockable ids are granted by the unlock
 * evaluator (M4), which runs later in the same pass; this record only carries what a level
 * <em>pays</em>. The first-clear <em>unlocks</em>, on the other hand, are read from
 * {@link #firstClears} by the unlock step itself, which grants them before the evaluator runs.
 *
 * @param currencies the currency ids, in {@code economy.json} order; the first is the one the
 *     coin rewards are paid in
 * @param levels the experience curve and its level rewards
 * @param rewards how a finished run turns into coins and experience
 * @param firstClears what a first boss clear and a first challenge completion pay
 */
public record ProgressionRules(List<String> currencies, PlayerLevel levels, RewardSource rewards,
        FirstClearRewards firstClears) {

    /**
     * Copies the currency list and rejects nulls.
     *
     * @param currencies the currency ids
     * @param levels the experience curve
     * @param rewards the reward source
     * @param firstClears the first-clear rewards, or {@code null} for none
     */
    public ProgressionRules {
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(rewards, "rewards");
        currencies = List.copyOf(currencies);
        if (currencies.isEmpty()) {
            throw new IllegalArgumentException("at least one currency is required");
        }
        firstClears = firstClears == null ? FirstClearRewards.NONE : firstClears;
    }

    /**
     * Rules without first-clear rewards (the pre-M8 shape).
     *
     * @param currencies the currency ids
     * @param levels the experience curve
     * @param rewards the reward source
     */
    public ProgressionRules(List<String> currencies, PlayerLevel levels, RewardSource rewards) {
        this(currencies, levels, rewards, FirstClearRewards.NONE);
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
     * level rewards, and the real reward formula (E32.a) closed over the same economy — with no
     * first-clear rewards, because those live in {@code worlds.json} and {@code challenges.json}
     * ({@link #fromContent}).
     *
     * @param economy the loaded economy
     * @return the rules
     */
    public static ProgressionRules fromEconomy(EconomyDef economy) {
        return fromEconomy(economy, FirstClearRewards.NONE);
    }

    /**
     * The rules a whole content set describes (M8): {@link #fromEconomy} plus the boss and
     * challenge first-clear rewards read from the world and challenge registries.
     *
     * @param content the loaded content
     * @return the rules
     */
    public static ProgressionRules fromContent(GameContent content) {
        Objects.requireNonNull(content, "content");
        return fromEconomy(content.economy(), FirstClearRewards.of(content));
    }

    private static ProgressionRules fromEconomy(EconomyDef economy, FirstClearRewards firstClears) {
        Objects.requireNonNull(economy, "economy");
        String currency = economy.primaryCurrency() == null
                ? PlayerProfile.CURRENCY_COINS : economy.primaryCurrency();
        List<String> currencies = economy.currencies().isEmpty()
                ? List.of(currency) : economy.currencies();
        XpCurveDef curve = economy.xp().curve();
        PlayerLevel levels = new PlayerLevel(curve.base(), curve.growth(), curve.maxLevel(),
                levelRewards(economy, currency));
        return new ProgressionRules(currencies, levels,
                (result, ctx) -> RunRewardCalculator.compute(result, economy, ctx), firstClears);
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
     * What the content pays for a first boss clear and a first challenge completion (M8):
     * {@code worlds.json.boss.reward} keyed by world id and {@code challenges.json.rewards} keyed
     * by challenge id. The coins enter the reward formula through
     * {@link RewardContext#firstBossClearCoins()} / {@link RewardContext#firstChallengeCoins()};
     * the unlocks are granted by the unlock step of {@link ProgressionManager#apply}, once,
     * because a first clear is by definition a thing that happens once (E11, E26).
     */
    public interface FirstClearRewards {

        /** No first-clear rewards at all: every query answers {@link RewardDef#NONE}. */
        FirstClearRewards NONE = new FirstClearRewards() {
            @Override
            public RewardDef bossReward(String worldId) {
                return RewardDef.NONE;
            }

            @Override
            public RewardDef challengeReward(String challengeId) {
                return RewardDef.NONE;
            }
        };

        /**
         * What clearing a world's boss pays the first time.
         *
         * @param worldId the world
         * @return the reward, {@link RewardDef#NONE} for an unknown world or a world without a
         *     boss reward
         */
        RewardDef bossReward(String worldId);

        /**
         * What completing a challenge pays the first time.
         *
         * @param challengeId the challenge
         * @return the reward, {@link RewardDef#NONE} for an unknown challenge
         */
        RewardDef challengeReward(String challengeId);

        /**
         * The first-clear rewards of a content set.
         *
         * @param content the loaded content
         * @return the rewards, read from the world and challenge registries on every query
         */
        static FirstClearRewards of(GameContent content) {
            Objects.requireNonNull(content, "content");
            return new FirstClearRewards() {
                @Override
                public RewardDef bossReward(String worldId) {
                    if (worldId == null || !content.has(GameContent.WORLDS)
                            || !content.worlds().contains(worldId)) {
                        return RewardDef.NONE;
                    }
                    WorldDef world = content.worlds().get(worldId);
                    return world.boss() == null || world.boss().reward() == null
                            ? RewardDef.NONE : world.boss().reward();
                }

                @Override
                public RewardDef challengeReward(String challengeId) {
                    if (challengeId == null || !content.has(GameContent.CHALLENGES)
                            || !content.challenges().contains(challengeId)) {
                        return RewardDef.NONE;
                    }
                    ChallengeDef challenge = content.challenges().get(challengeId);
                    return challenge.rewardsOrNone();
                }
            };
        }
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
