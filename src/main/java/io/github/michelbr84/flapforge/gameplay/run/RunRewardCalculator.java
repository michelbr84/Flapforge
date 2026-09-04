package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.content.defs.RewardsDef;
import io.github.michelbr84.flapforge.content.defs.XpDef;
import java.util.Objects;

/**
 * The run reward formula (E32.a), pure: no I/O, no clock, no randomness.
 *
 * <pre>
 * coins = round((participation·[gates ≥ 1 ∨ ticksAlive ≥ 180]
 *              + firstRunBonus·[first run]
 *              + coinsPerGate × gates
 *              + coinsPerPoint × points
 *              + streak.coins × streakSteps
 *              + Σ modifier.streakBonus.coins × streakSteps
 *              + bossBonus × worldBossesClearedThisRun
 *              + boss.reward.coins·[first clear]
 *              + challenge.rewards.coins·[first completion]
 *              + challengeBonus·[objectiveMet])
 *              × COIN_MULT × tier.rewardMult × daily.rewardMult·[daily]) + coinsCollected
 * xp    = round((xp.participation·[gates ≥ 1 ∨ ticksAlive ≥ 180]
 *              + xp.perGate × gates + xp.bossBonus × bosses) × XP_MULT)
 * </pre>
 *
 * <p>The modifier streak bonuses landed in M6 and are read from
 * {@link RunStats#modifierStreakCoins()}. The first-clear boss and first-completion challenge
 * rewards landed in M8 and are read from the context — {@link RewardContext#firstBossClearCoins()}
 * and {@link RewardContext#firstChallengeCoins()}, resolved by the progression layer from
 * {@code worlds.json} and {@code challenges.json}, which this class never opens. They are folded
 * into {@link RewardSummary#bossCoins()} and {@link RewardSummary#challengeCoins()} beside the
 * economy's per-clear bonuses. {@code bosses} counts world bosses only ({@code
 * RunStats.bossesCleared}): a challenge boss never enters either boss term, nor the XP one
 * (E26).
 *
 * <p>Participation is gated (E32.a) so an instant-retry dive that passes no gate and lasts less
 * than {@value #PARTICIPATION_TICKS} ticks earns nothing; the first-run bonus is not gated, so a
 * profile's very first run always pays. The gate covers the <em>XP</em> participation too, which
 * E32.a's formula does not say: XP buys levels and levels pay coins, so an ungated XP
 * participation hands the 0-gate dive back everything the coin gate takes from it: 400 zero-gate
 * dives measured 511 coins/min against 251 for a bot that actually played (docs/BALANCING.md).
 * The line above the {@code xpBase} term records the amendment.
 */
public final class RunRewardCalculator {

    /** A run at least this many ticks long earns the participation reward even with 0 gates. */
    public static final int PARTICIPATION_TICKS = 180;

    private RunRewardCalculator() {
    }

    /**
     * Computes what a finished run pays.
     *
     * @param result the run result
     * @param economy the economy content
     * @param ctx what the profile knows and the multipliers to apply
     * @return the breakdown
     */
    public static RewardSummary compute(RunResult result, EconomyDef economy, RewardContext ctx) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(ctx, "ctx");
        RunStats stats = result.stats();
        RewardsDef rewards = economy.rewards();

        long gates = stats.gatesPassed();
        long points = Math.round(stats.points());
        long bosses = stats.bossesCleared().size();
        long steps = stats.streakSteps();

        boolean participated = gates >= 1 || stats.ticksAlive() >= PARTICIPATION_TICKS;
        long participation = participated ? rewards.participation() : 0;
        long firstRunBonus = ctx.firstRun() ? rewards.firstRunBonus() : 0;
        long gateCoins = rewards.coinsPerGate() * gates;
        long pointCoins = rewards.coinsPerPoint() * points;
        long streakCoins = (rewards.streak().coins() + modifierStreakBonus(stats)) * steps;
        long bossCoins = rewards.bossBonus() * bosses + firstBossClearCoins(ctx);
        long challengeCoins = (stats.objectiveMet() ? rewards.challengeBonus() : 0)
                + firstChallengeCompletionCoins(ctx);

        double dailyMult = result.config().mode() == RunMode.DAILY ? ctx.dailyRewardMult() : 1;
        double multiplier = ctx.coinMult() * ctx.tierRewardMult() * dailyMult;
        long base = participation + firstRunBonus + gateCoins + pointCoins + streakCoins
                + bossCoins + challengeCoins;
        long collected = stats.coinsCollected();
        long coins = Math.round(base * multiplier) + collected;

        XpDef xp = economy.xp();
        // Gated exactly like the coin participation, and for the same reason: ungated it is worth
        // more than the term it guards, because 400 zero-gate dives pay 400 x 15 XP and that is
        // several level rewards. A deliberate amendment to E32.a's literal XP formula.
        long xpBase = (participated ? xp.participation() : 0)
                + xp.perGate() * gates + xp.bossBonus() * bosses;
        long xpEarned = Math.round(xpBase * ctx.xpMult());

        return new RewardSummary(coins, xpEarned, collected, participation, gateCoins, pointCoins,
                streakCoins, bossCoins, challengeCoins, firstRunBonus, ctx.coinMult(),
                ctx.tierRewardMult(), dailyMult);
    }

    /**
     * The modifier streak bonus, per step (E32.a).
     *
     * <p>M6 fills the term the milestone before left at zero. The sum is accumulated by
     * {@code ModifierDirector} as the cards are taken — one entry per stack, so two stacks of a
     * bonus pay twice — and travels in the stats, which keeps the calculator a pure function of
     * the result rather than something that has to look modifier ids up in content.
     *
     * @param stats the run stats (the taken modifiers live here)
     * @return the extra coins one streak step pays
     */
    private static long modifierStreakBonus(RunStats stats) {
        return stats.modifierStreakCoins();
    }

    /**
     * The first-clear reward of the world bosses this run cleared (E32.a, M8): the sum of
     * {@code boss.reward.coins} over {@link RewardContext#firstBossClears()}, resolved by the
     * progression layer into {@link RewardContext#firstBossClearCoins()}. A context with first
     * clears but no coins resolved (a test, a tool without content) pays 0 here and still pays
     * the economy's {@code bossBonus}.
     *
     * @param ctx the reward context
     * @return the coins the first clears pay
     */
    private static long firstBossClearCoins(RewardContext ctx) {
        return ctx.firstBossClears().isEmpty() ? 0 : ctx.firstBossClearCoins();
    }

    /**
     * The first-completion reward of the run's challenge (E11, M8): the challenge's
     * {@code rewards.coins}, paid once, on the run that completes it first; every completion
     * after that pays {@code challengeBonus} alone.
     *
     * @param ctx the reward context
     * @return the coins the first completion pays
     */
    private static long firstChallengeCompletionCoins(RewardContext ctx) {
        return ctx.firstChallengeCompletion() ? ctx.firstChallengeCoins() : 0;
    }
}
