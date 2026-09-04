package io.github.michelbr84.flapforge.gameplay.harness;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.progression.AchievementEvaluator;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import io.github.michelbr84.flapforge.progression.SaveTrigger;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UnlockManager;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.progression.Wallet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The run-cycles simulation of the meta-progression (E25, D21, M9): a fresh profile plays run
 * after run through the <em>real</em> progression stack — {@link ProgressionManager#apply} with
 * the shipped {@link AchievementEvaluator} and {@link UnlockEvaluator}, purchases through
 * {@link UnlockManager} and {@link UpgradeManager} — under one of two purchase policies, until
 * the run budget is spent or there is nothing left to buy.
 *
 * <p><b>What the policies buy.</b> Both fly the same runs; they differ only at the shop.
 * {@code spender} empties its pockets every run by priority class — features, then worlds, then
 * birds and abilities, then ability levels, then trees and upgrade nodes — always the cheapest
 * affordable item of the current class, skipping to the next class when nothing there is
 * affordable and never hoarding. Cosmetics and the three purchasable modifiers are outside the
 * classes E25 names, so the spender never buys them; the modifiers arrive through their level
 * branch instead. {@code saver} buys at most one item per run, the cheapest not-yet-owned world
 * or feature, and keeps the rest — the player who saves for the next world.
 *
 * <p><b>What the policies fly.</b> A fixed, documented rule, so the numbers of the
 * runs-to-unlock table mean one thing: the default cell (classic bird, green fields), the
 * {@code hard} tier as soon as {@code tier:hard} is owned — E25 asks the spender to max the
 * trees "playing tier:hard once unlocked" — and the owned abilities auto-equipped in content
 * order, the active slot to the first owned {@code ACTIVE} kind and the passive slots to every
 * owned {@code PASSIVE} kind (the run strips what does not fit, D9). Nothing here tunes the
 * pilot: the skill is a {@link BotPilot.Preset} chosen by the caller, and the thresholds are
 * reached by prices and rewards in data, never by weakening the bot (E25).
 *
 * <p><b>Determinism.</b> Every seed line plays run {@code i} on seed
 * {@code seedSpan * (firstSeed + line) + i}; iteration is content order everywhere; the injected
 * {@link TimeSource} only advances. The output is a function of the content and the settings.
 */
public final class MetaSim {

    /** Default run budget of one seed line (E25's spender-average maxing budget). */
    public static final int DEFAULT_MAX_RUNS = 600;
    /** Default tick budget of one run, the same cell budget {@link HeadlessRunner} callers use. */
    public static final int MAX_TICKS = 20_000;
    /** Stride between seed lines, so run seeds of different lines never collide. */
    public static final long SEED_SPAN = 1_000_000;
    /** Wall-clock step per simulated run; timestamps only, nothing schedules against it. */
    private static final long CLOCK_STEP_MS = 60_000;
    /** Hard ceiling on purchases per run; the shipped shop empties a wallet long before it. */
    private static final int PURCHASE_GUARD = 64;

    /** The purchase policy of a simulation (E25). */
    public enum Policy {
        /** Empties the wallet every run by priority class, cheapest affordable per class. */
        SPENDER,
        /** Buys only the cheapest not-yet-owned world or feature, at most one per run. */
        SAVER
    }

    /** One simulation's settings. */
    public record Settings(Policy policy, BotPilot.Preset preset, int seeds, long firstSeed,
            int maxRuns, int maxTicks) {

        public Settings {
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(preset, "preset");
            if (seeds <= 0) {
                throw new IllegalArgumentException("seeds must be positive");
            }
            if (maxRuns <= 0 || maxTicks <= 0) {
                throw new IllegalArgumentException("run and tick budgets must be positive");
            }
        }
    }

    /**
     * What a simulation measured, aggregated over its seed lines. Run indices count runs: an id
     * first owned by the shopping pass after run 3 is recorded as 3.
     */
    public static final class Outcome {

        private final List<String> unlockIds;
        private final List<String> nodeIds;
        private final int[] ownedRunsTotal;
        private final int[] ownedRunsMax;
        private final int[] ownedSeeds;
        private final int[] nodeBuyTotal;
        private final int[] nodeBuyMax;
        private final int[] nodeBuySeeds;
        private final int[] completedRun;
        private final int[] maxedRun;
        private long runsReachingOffer3;
        private long runsReachingOffer3WithSynergy;
        private long totalRuns;

        Outcome(List<String> unlockIds, List<String> nodeIds, int seeds) {
            this.unlockIds = unlockIds;
            this.nodeIds = nodeIds;
            this.ownedRunsTotal = new int[unlockIds.size()];
            this.ownedRunsMax = new int[unlockIds.size()];
            this.ownedSeeds = new int[unlockIds.size()];
            this.nodeBuyTotal = new int[nodeIds.size()];
            this.nodeBuyMax = new int[nodeIds.size()];
            this.nodeBuySeeds = new int[nodeIds.size()];
            this.completedRun = new int[seeds];
            this.maxedRun = new int[seeds];
        }

        /** The tracked unlockable ids (non-cosmetic, content order). */
        public List<String> unlockIds() {
            return unlockIds;
        }

        /** The tracked upgrade node ids (content order). */
        public List<String> nodeIds() {
            return nodeIds;
        }

        /**
         * Mean run index at which the node was first bought, over the seeds that bought it (0
         * when no seed did — the fresh profile owns no node).
         */
        public double meanFirstBuy(String nodeId) {
            int i = nodeIds.indexOf(nodeId);
            if (i < 0 || nodeBuySeeds[i] == 0) {
                return 0;
            }
            return (double) nodeBuyTotal[i] / nodeBuySeeds[i];
        }

        /** The latest first-buy run index of the node over the seeds that bought it. */
        public int maxFirstBuy(String nodeId) {
            int i = nodeIds.indexOf(nodeId);
            return i < 0 ? 0 : nodeBuyMax[i];
        }

        /** How many seeds bought the node at least once. */
        public int seedsBuying(String nodeId) {
            int i = nodeIds.indexOf(nodeId);
            return i < 0 ? 0 : nodeBuySeeds[i];
        }

        /** Mean run index at which the id was first owned, over the seeds that owned it. */
        public double meanFirstOwned(String unlockId) {
            int i = unlockIds.indexOf(unlockId);
            if (i < 0 || ownedSeeds[i] == 0) {
                return 0;
            }
            return (double) ownedRunsTotal[i] / ownedSeeds[i];
        }

        /** The latest first-owned run index of the id over the seeds that owned it. */
        public int maxFirstOwned(String unlockId) {
            int i = unlockIds.indexOf(unlockId);
            return i < 0 ? 0 : ownedRunsMax[i];
        }

        /** How many seeds owned the id, and in how many seeds. */
        public int seedsOwning(String unlockId) {
            int i = unlockIds.indexOf(unlockId);
            return i < 0 ? 0 : ownedSeeds[i];
        }

        /** How many seeds ended with every non-cosmetic unlockable owned. */
        public int seedsCompleted() {
            int n = 0;
            for (int run : completedRun) {
                if (run > 0) {
                    n++;
                }
            }
            return n;
        }

        /** Mean run index at which every non-cosmetic unlockable was owned (0 seeds → 0). */
        public double meanCompletedRun() {
            return mean(completedRun);
        }

        /** How many seeds ended with every node and ability level maxed. */
        public int seedsMaxed() {
            int n = 0;
            for (int run : maxedRun) {
                if (run > 0) {
                    n++;
                }
            }
            return n;
        }

        /** Mean run index at which every node and ability level was maxed (0 seeds → 0). */
        public double meanMaxedRun() {
            return mean(maxedRun);
        }

        /** How many runs opened their third modifier offer. */
        public long runsReachingOffer3() {
            return runsReachingOffer3;
        }

        /** Of those, how many activated at least one synergy. */
        public long runsReachingOffer3WithSynergy() {
            return runsReachingOffer3WithSynergy;
        }

        /** How many runs were played in total. */
        public long totalRuns() {
            return totalRuns;
        }

        private double mean(int[] values) {
            long total = 0;
            long seeds = 0;
            for (int v : values) {
                if (v > 0) {
                    total += v;
                    seeds++;
                }
            }
            return seeds == 0 ? 0 : (double) total / seeds;
        }
    }

    /** A fake clock: pure, injected, advanced one step per simulated run (D23). */
    private static final class SimClock implements TimeSource {

        private long epochMillis;

        SimClock(long epochMillis) {
            this.epochMillis = epochMillis;
        }

        void advance(long millis) {
            epochMillis += millis;
        }

        @Override
        public long epochMillis() {
            return epochMillis;
        }
    }

    private MetaSim() {
    }

    /**
     * Runs the simulation.
     *
     * @param content the shipped content
     * @param settings the policy, pilot skill, seed family and budgets
     * @return what was measured
     */
    public static Outcome simulate(GameContent content, Settings settings) {
        UnlockEvaluator evaluator = UnlockEvaluator.of(content);
        List<String> unlockIds = nonCosmeticIds(evaluator);
        List<String> nodeIds = nodeIds(content);
        Outcome outcome = new Outcome(unlockIds, nodeIds, settings.seeds());
        for (int line = 0; line < settings.seeds(); line++) {
            simulateLine(content, evaluator, unlockIds, nodeIds, settings, line, outcome);
        }
        return outcome;
    }

    private static void simulateLine(GameContent content, UnlockEvaluator evaluator,
            List<String> unlockIds, List<String> nodeIds, Settings settings, int line,
            Outcome outcome) {
        SimClock clock = new SimClock(1_700_000_000_000L);
        PlayerProfile profile = PlayerProfile.fresh(clock.epochMillis()).normalize();
        ProgressionManager progression = new ProgressionManager(clock,
                AchievementEvaluator.of(content), evaluator);
        ProgressionRules rules = ProgressionRules.fromContent(content);
        UnlockManager shop = new UnlockManager(progression, SaveTrigger.NONE);
        UpgradeManager upgrades = new UpgradeManager(progression, SaveTrigger.NONE);
        RunFactory factory = new RunFactory(content);
        Map<String, Integer> indexOf = new LinkedHashMap<>();
        for (int i = 0; i < unlockIds.size(); i++) {
            indexOf.put(unlockIds.get(i), i);
        }
        Set<String> seen = new LinkedHashSet<>();
        // What the fresh profile already owns (E18) counts as owned at run 0, so the table's
        // defaults read as owned everywhere rather than as never owned.
        recordOwned(profile, seen, indexOf, 0, outcome);
        Set<String> seenNodes = new LinkedHashSet<>();

        for (int run = 1; run <= settings.maxRuns(); run++) {
            long seed = (settings.firstSeed() + line) * SEED_SPAN + run;
            equipSelection(profile, content);
            Run play = factory.newRun(
                    RunLoadout.configFor(profile, content, seed, RunMode.STANDARD));
            HeadlessRunner.Outcome played = HeadlessRunner.run(play,
                    new BotPilot(settings.preset(), seed), settings.maxTicks());
            clock.advance(CLOCK_STEP_MS);
            progression.apply(profile, played.result(), rules, multipliers(content, play));
            if (play.simulation().modifiers().offersOpened() >= 3) {
                outcome.runsReachingOffer3++;
                if (!played.result().stats().synergiesActivated().isEmpty()) {
                    outcome.runsReachingOffer3WithSynergy++;
                }
            }
            recordOwned(profile, seen, indexOf, run, outcome);
            spend(profile, content, shop, upgrades, settings.policy());
            recordOwned(profile, seen, indexOf, run, outcome);
            recordNodes(profile, seenNodes, nodeIds, run, outcome);
            outcome.totalRuns++;
            if (outcome.maxedRun[line] == 0 && isMaxed(profile, content)) {
                outcome.maxedRun[line] = run;
            }
            if (outcome.completedRun[line] == 0 && allOwned(unlockIds, profile)) {
                outcome.completedRun[line] = run;
            }
            if (outcome.maxedRun[line] > 0 && outcome.completedRun[line] > 0) {
                break;
            }
        }
    }

    /**
     * The reward multipliers the run was played under: the bird's own economy stats, the tier's
     * reward multiplier and the daily multiplier (which a STANDARD run does not use, but the
     * pipeline reads).
     */
    private static ProgressionRules.RewardMultipliers multipliers(GameContent content, Run play) {
        return new ProgressionRules.RewardMultipliers(
                play.simulation().stats().resolve(StatId.COIN_MULT),
                play.simulation().stats().resolve(StatId.XP_MULT),
                play.setup().tier().rewardMult(),
                content.economy().daily().rewardMult());
    }

    /**
     * The sim's own selection rule: the hard tier once owned, the first owned {@code ACTIVE}
     * ability in the active slot and every owned {@code PASSIVE} ability in the passive slots
     * (content order; the run strips what the bird's slots cannot hold).
     */
    private static void equipSelection(PlayerProfile profile, GameContent content) {
        profile.selected.tierId = profile.isUnlocked("tier:hard")
                ? "hard" : profile.selected.tierId;
        String active = null;
        List<String> passives = new ArrayList<>();
        for (AbilityDef ability : content.abilities()) {
            if (!profile.isUnlocked(ability.unlockableId())) {
                continue;
            }
            if (ability.kind() == AbilityKind.ACTIVE && active == null) {
                active = ability.id();
            } else if (ability.kind() == AbilityKind.PASSIVE) {
                passives.add(ability.id());
            }
        }
        profile.selected.activeAbilityId = active;
        profile.selected.passiveAbilityIds = passives;
    }

    /** The non-cosmetic unlockable ids the content ships, in content order. */
    private static List<String> nonCosmeticIds(UnlockEvaluator evaluator) {
        List<String> ids = new ArrayList<>();
        for (String id : evaluator.conditions().keySet()) {
            if (evaluator.kindOf(id) != ContentKind.COSMETIC) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private static void recordOwned(PlayerProfile profile, Set<String> seen,
            Map<String, Integer> indexOf, int run, Outcome outcome) {
        for (String id : profile.unlocked) {
            if (!seen.add(id)) {
                continue;
            }
            Integer index = indexOf.get(id);
            if (index != null) {
                outcome.ownedRunsTotal[index] += run;
                outcome.ownedRunsMax[index] = Math.max(outcome.ownedRunsMax[index], run);
                outcome.ownedSeeds[index]++;
            }
        }
    }

    /** Records the run at which each node was first bought (E17's upgrade milestones). */
    private static void recordNodes(PlayerProfile profile, Set<String> seenNodes,
            List<String> nodeIds, int run, Outcome outcome) {
        for (int i = 0; i < nodeIds.size(); i++) {
            if (profile.upgradeLevel(nodeIds.get(i)) < 1 || !seenNodes.add(nodeIds.get(i))) {
                continue;
            }
            outcome.nodeBuyTotal[i] += run;
            outcome.nodeBuyMax[i] = Math.max(outcome.nodeBuyMax[i], run);
            outcome.nodeBuySeeds[i]++;
        }
    }

    /** The node ids the content ships, in content order. */
    private static List<String> nodeIds(GameContent content) {
        List<String> ids = new ArrayList<>();
        for (UpgradeDef node : content.upgrades()) {
            ids.add(node.id());
        }
        return List.copyOf(ids);
    }

    private static boolean allOwned(List<String> unlockIds, PlayerProfile profile) {
        for (String id : unlockIds) {
            if (!profile.isUnlocked(id)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether every node is at its maximum level and every owned ability at its capped level —
     * E25's "all nodes and ability levels maxed". A node the shop refuses to sell any more counts
     * as satisfied: {@code hard_tier_1} is a pure-loss purchase ({@code UpgradeManager#isRedundant})
     * once {@code tier:hard} has been earned by play, which is the very state this clause plays
     * in, and the profile must not be asked to buy what {@code UpgradeManager} will not sell.
     */
    private static boolean isMaxed(PlayerProfile profile, GameContent content) {
        for (UpgradeDef node : content.upgrades()) {
            if (profile.upgradeLevel(node.id()) < node.maxLevel()
                    && !UpgradeManager.isRedundant(profile, node.id(), content)) {
                return false;
            }
        }
        for (AbilityDef ability : content.abilities()) {
            if (!profile.isUnlocked(ability.unlockableId())) {
                continue;
            }
            int cap = Math.min(ability.levels().size(),
                    UpgradeManager.abilityLevelCap(profile, content));
            if (UpgradeManager.abilityLevelOwned(profile, ability) < cap) {
                return false;
            }
        }
        return true;
    }

    /** Applies the policy's shopping pass after a run. */
    private static void spend(PlayerProfile profile, GameContent content, UnlockManager shop,
            UpgradeManager upgrades, Policy policy) {
        if (policy == Policy.SAVER) {
            spendSaver(profile, content, shop);
            return;
        }
        spendSpender(profile, content, shop, upgrades);
    }

    /**
     * The spender: repeat until nothing is affordable — the cheapest affordable offer of the
     * highest-priority class that still has one, one purchase at a time. Trees ride with the
     * node class: a node whose tree is locked is bought by unlocking the tree first.
     */
    private static void spendSpender(PlayerProfile profile, GameContent content,
            UnlockManager shop, UpgradeManager upgrades) {
        for (int guard = 0; guard < PURCHASE_GUARD; guard++) {
            UnlockManager.Offer features = cheapestOffer(shop.offers(profile, content),
                    ContentKind.FEATURE);
            UnlockManager.Offer worlds = cheapestOffer(shop.offers(profile, content),
                    ContentKind.WORLD);
            UnlockManager.Offer birdsAbilities = cheapestOfKinds(shop.offers(profile, content),
                    ContentKind.BIRD, ContentKind.ABILITY);
            UnlockManager.Offer trees = cheapestOffer(shop.offers(profile, content),
                    ContentKind.TREE);
            String node = cheapestNode(profile, content);
            String abilityLevel = cheapestAbilityLevel(profile, content);
            if (features != null) {
                shop.purchase(profile, features.id(), content);
            } else if (worlds != null) {
                shop.purchase(profile, worlds.id(), content);
            } else if (birdsAbilities != null) {
                shop.purchase(profile, birdsAbilities.id(), content);
            } else if (abilityLevel != null) {
                upgrades.buyAbilityLevel(profile, abilityLevel, content);
            } else if (trees != null && (node == null || trees.cost() <= nodeCost(profile,
                    content, node))) {
                shop.purchase(profile, trees.id(), content);
            } else if (node != null) {
                upgrades.buy(profile, node, content);
            } else {
                return;
            }
        }
    }

    /**
     * The saver: one purchase per run at most — the cheapest not-yet-owned world or feature the
     * wallet can pay for, nothing else, and the balance stays when neither is affordable.
     */
    private static void spendSaver(PlayerProfile profile, GameContent content,
            UnlockManager shop) {
        UnlockManager.Offer cheapest = cheapestOfKinds(shop.offers(profile, content),
                ContentKind.WORLD, ContentKind.FEATURE);
        if (cheapest != null) {
            shop.purchase(profile, cheapest.id(), content);
        }
    }

    /** The cheapest affordable offer of one kind, or {@code null}. */
    private static UnlockManager.Offer cheapestOffer(List<UnlockManager.Offer> offers,
            ContentKind kind) {
        for (UnlockManager.Offer offer : offers) {
            if (offer.kind() == kind && offer.affordable()) {
                return offer;
            }
        }
        return null;
    }

    /** The cheapest affordable offer of any of the kinds, or {@code null}. */
    private static UnlockManager.Offer cheapestOfKinds(List<UnlockManager.Offer> offers,
            ContentKind first, ContentKind second) {
        for (UnlockManager.Offer offer : offers) {
            if ((offer.kind() == first || offer.kind() == second) && offer.affordable()) {
                return offer;
            }
        }
        return null;
    }

    /** The cheapest open node the wallet can pay for, or {@code null}. */
    private static String cheapestNode(PlayerProfile profile, GameContent content) {
        String best = null;
        for (String nodeId : content.upgrades().ids()) {
            if (!UpgradeManager.isAvailable(profile, nodeId, content)) {
                continue;
            }
            long cost = UpgradeManager.nextCost(profile, nodeId, content);
            if (cost >= 0 && cost <= balance(profile) && (best == null
                    || cost < nodeCost(profile, content, best))) {
                best = nodeId;
            }
        }
        return best;
    }

    private static long nodeCost(PlayerProfile profile, GameContent content, String nodeId) {
        return UpgradeManager.nextCost(profile, nodeId, content);
    }

    /**
     * The cheapest next ability level the wallet can pay for, over the abilities the profile
     * owns, or {@code null}. The cap (E3) decides with the price: a capped level simply prices
     * at {@code -1}.
     */
    private static String cheapestAbilityLevel(PlayerProfile profile, GameContent content) {
        String best = null;
        long bestCost = Long.MAX_VALUE;
        for (AbilityDef ability : content.abilities()) {
            long cost = UpgradeManager.nextAbilityLevelCost(profile, ability.id(), content);
            if (cost >= 0 && cost <= balance(profile) && cost < bestCost) {
                bestCost = cost;
                best = ability.id();
            }
        }
        return best;
    }

    private static long balance(PlayerProfile profile) {
        return Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
    }
}
