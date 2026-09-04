package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The first fifteen runs of a new player, played by the novice bot on the shipped economy (§6 M4,
 * E17).
 *
 * <p>It is the end-to-end check of the milestone: the run pays what {@code economy.json} says, the
 * progression pipeline writes it, the greedy shopper spends it through {@link UnlockManager} and
 * {@link UpgradeManager}, and the next run is built from what the profile now owns. If any link
 * breaks — a reward, an unlock condition, a price, the upgrade layer — the pace of the first three
 * runs changes and this test says so, with the run-by-run table in the failure message.
 *
 * <p>M4 asserted two of the four milestones of the README's opening hour: an upgrade by run 2–3
 * and Ironbeak by run 3; M5 added the shield by run 5, now that a shield charge does something,
 * and M6 adds {@code feature:modifiers} by run 7, now that a draft exists to be gated (E17).
 *
 * <p>The shopping policy is "cheapest first" rather than the class-ordered spender of E25: this is
 * a new player emptying their pockets on whatever they can afford, which is what makes the first
 * upgrade land on run 2. {@code MetaSim} (M9) is where the E25 policies are simulated.
 */
@Tag("sim")
class NewPlayerJourneyTest {

    /** Runs the novice plays. */
    private static final int RUNS = 15;
    /** Seed of the first run; every later run adds its index (the instant-retry sequence). */
    private static final long BASE_SEED = 42;
    /** A novice never survives this long; the budget only stops a runaway. */
    private static final int MAX_TICKS = 20_000;

    private final GameContent content = GameContent.load();
    private final FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
    private final ProgressionManager progression = new ProgressionManager(time,
            ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
    private final ProgressionRules rules = ProgressionRules.fromEconomy(content.economy());
    private final UnlockManager shop = new UnlockManager(progression, SaveTrigger.NONE);
    private final UpgradeManager upgrades = new UpgradeManager(progression, SaveTrigger.NONE);
    private final RunFactory runs = new RunFactory(content);
    private final StringBuilder table = new StringBuilder();

    @Test
    void aNoviceBuysAnUpgradeByRunThreeAndOwnsIronbeakByRunThree() {
        PlayerProfile profile = PlayerProfile.fresh(time.epochMillis()).normalize();
        int firstUpgradeAfterRun = -1;
        int ironbeakAfterRun = -1;
        int shieldAfterRun = -1;
        int modifiersAfterRun = -1;
        String firstNodeBought = null;
        header();

        for (int run = 1; run <= RUNS; run++) {
            RunConfig config = RunLoadout.configFor(profile, content, BASE_SEED + run,
                    RunMode.STANDARD);
            Run play = runs.newRun(config);
            HeadlessRunner.Outcome outcome = HeadlessRunner.run(play,
                    new BotPilot(BotPilot.Preset.NOVICE, BASE_SEED + run), MAX_TICKS);
            ProgressionRules.RewardMultipliers multipliers =
                    new ProgressionRules.RewardMultipliers(
                            play.simulation().stats().resolve(StatId.COIN_MULT),
                            play.simulation().stats().resolve(StatId.XP_MULT),
                            play.setup().tier().rewardMult(),
                            content.economy().daily().rewardMult());
            time.advance(60_000);
            ProgressionOutcome progressed =
                    progression.apply(profile, outcome.result(), rules, multipliers);
            List<String> bought = spendEverything(profile);

            if (firstUpgradeAfterRun < 0 && !profile.upgrades.isEmpty()) {
                firstUpgradeAfterRun = run;
                firstNodeBought = profile.upgrades.keySet().iterator().next();
            }
            if (ironbeakAfterRun < 0 && profile.isUnlocked("bird:guardian")) {
                ironbeakAfterRun = run;
            }
            if (shieldAfterRun < 0 && profile.isUnlocked("ability:shield")) {
                shieldAfterRun = run;
            }
            if (modifiersAfterRun < 0 && profile.isUnlocked("feature:modifiers")) {
                modifiersAfterRun = run;
            }
            row(run, outcome, progressed, profile, bought);
        }

        final int firstUpgrade = firstUpgradeAfterRun;
        final int ironbeak = ironbeakAfterRun;
        final int shield = shieldAfterRun;
        final int modifiers = modifiersAfterRun;
        final String firstNode = firstNodeBought;
        Supplier<String> report = () -> "the novice's first " + RUNS + " runs:\n" + table;
        assertTrue(firstUpgrade > 0, report);
        assertTrue(firstUpgrade <= 2, () -> "the first upgrade node was bought after run "
                + firstUpgrade + ", so it is only owned from run " + (firstUpgrade + 1)
                + " on; M4 wants it by run 2-3\n" + report.get());
        assertTrue(ironbeak > 0 && ironbeak <= 3,
                () -> "Ironbeak was owned only after run " + ironbeak + "\n" + report.get());
        // Which node, on which run. Without both, doubling a price is invisible here: another
        // node simply becomes the cheapest one, the "first upgrade" milestone is still met, and
        // the feather_1 assertion below is only reached after fifteen runs of income.
        assertEquals("feather_1", firstNode,
                () -> "the cheapest node is the one a new player buys first\n" + report.get());
        assertEquals(1, firstUpgrade,
                () -> "and at 50 coins it is affordable out of the first run's pay\n"
                        + report.get());
        assertTrue(profile.upgradeLevel("feather_1") >= 1,
                () -> "the cheapest node is the one a new player can afford first\n"
                        + report.get());
        // E17/M5: the README's third milestone. The unlock is any_of[runs 5, purchase 200], so a
        // novice reaches it by playing; and it has to be worth reaching, which is the second half
        // of the assertion — equipping it puts a real charge in the next run (D9).
        assertTrue(shield > 0 && shield <= 5,
                () -> "ability:shield was unlocked only after run " + shield + "\n"
                        + report.get());
        profile.selected.passiveAbilityIds = List.of("shield");
        Run withShield = runs.newRun(RunLoadout.configFor(profile, content, BASE_SEED,
                RunMode.STANDARD));
        assertEquals(1, withShield.simulation().shield().maxCharges(),
                () -> "the unlocked shield must absorb a hit in the next run\n" + report.get());
        // E17/M6: the README's fourth milestone. feature:modifiers is any_of[runs 7,
        // purchase 150], so a novice reaches it by playing; and it has to be worth reaching,
        // which is the second half again — the next run really can draft (D11).
        assertTrue(modifiers > 0 && modifiers <= 7,
                () -> "feature:modifiers was unlocked only after run " + modifiers + "\n"
                        + report.get());
        Run withDrafts = runs.newRun(RunLoadout.configFor(profile, content, BASE_SEED,
                RunMode.STANDARD));
        assertTrue(withDrafts.setup().modifiers().modifiers().size() >= 14,
                () -> "the next run carries at least the fourteen cards that ship unlocked\n"
                        + report.get());
        assertEquals(RunLoadout.availableModifiers(profile, content).size(),
                withDrafts.setup().modifiers().modifiers().size(),
                () -> "and exactly what the profile owns, legendaries included\n" + report.get());
        assertEquals(List.of(10, 25, 45, 70, 100, 140), withDrafts.setup().modifiers()
                .offerSchedule(), report);
        // The milestone gate itself: M6 shipped ModifierChoiceOverlay, so feature:modifiers is
        // playable and the very next run really opens its drafts (E19, D11).
        assertTrue(RunLoadout.allowOffers(profile, content),
                () -> "the unlocked feature must turn drafts on\n" + report.get());
        assertTrue(withDrafts.config().allowOffers(),
                () -> "and the next run must be configured to draft\n" + report.get());
        assertTrue(profile.statistics.coinsSpent > 0, report);
        assertTrue(Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS) >= 0, report);
        assertTrue(profile.statistics.totalRuns == RUNS, report);
    }

    /**
     * The greedy shopper: buy the cheapest thing the wallet can pay for, over and over, across
     * both the shop and the open upgrade nodes, until nothing is affordable.
     *
     * @param profile the profile to spend from
     * @return what was bought, in the order it was bought
     */
    private List<String> spendEverything(PlayerProfile profile) {
        List<String> bought = new ArrayList<>();
        for (int guard = 0; guard < 64; guard++) {
            String cheapestNode = null;
            long cheapestNodeCost = Long.MAX_VALUE;
            for (String nodeId : content.upgrades().ids()) {
                if (!UpgradeManager.isAvailable(profile, nodeId, content)) {
                    continue;
                }
                long cost = UpgradeManager.nextCost(profile, nodeId, content);
                if (cost >= 0 && cost < cheapestNodeCost) {
                    cheapestNodeCost = cost;
                    cheapestNode = nodeId;
                }
            }
            UnlockManager.Offer cheapestOffer = null;
            for (UnlockManager.Offer offer : shop.offers(profile, content)) {
                if (offer.affordable()) {
                    cheapestOffer = offer;
                    break;
                }
            }
            long balance = Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
            boolean nodeAffordable = cheapestNode != null && cheapestNodeCost <= balance;
            boolean offerAffordable = cheapestOffer != null;
            if (!nodeAffordable && !offerAffordable) {
                return bought;
            }
            PurchaseResult result;
            if (nodeAffordable && (!offerAffordable || cheapestNodeCost <= cheapestOffer.cost())) {
                result = upgrades.buy(profile, cheapestNode, content);
                bought.add(cheapestNode + " L" + result.level() + " (" + result.cost() + ")");
            } else {
                result = shop.purchase(profile, cheapestOffer.id(), content);
                bought.add(cheapestOffer.id() + " (" + result.cost() + ")");
            }
            assertTrue(result.ok(), () -> "the shopper bought something it could not afford: "
                    + result.status() + "\n" + table);
        }
        return bought;
    }

    private void header() {
        table.append(String.format("%4s %6s %7s %8s %8s  %s%n",
                "run", "gates", "coins", "balance", "unlocks", "bought"));
    }

    private void row(int run, HeadlessRunner.Outcome outcome, ProgressionOutcome progressed,
            PlayerProfile profile, List<String> bought) {
        table.append(String.format("%4d %6d %7d %8d %8d  %s%n", run,
                outcome.result().gatesPassed(), progressed.rewardSummary().coins(),
                Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS),
                profile.unlocked.size(), String.join(", ", bought)));
    }
}
