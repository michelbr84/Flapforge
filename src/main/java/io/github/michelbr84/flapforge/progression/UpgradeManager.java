package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AliasDef;
import io.github.michelbr84.flapforge.content.defs.GrantDef;
import io.github.michelbr84.flapforge.content.defs.GrantType;
import io.github.michelbr84.flapforge.content.defs.TreeDef;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The upgrade half of the meta-progression (D13, E21, E31.f): buying one level of one node of one
 * tree.
 *
 * <p>A node is not an unlockable. {@code profile.unlocked} never holds an {@code upgrade:} id;
 * ownership is {@code profile.upgrades.get(nodeId)}, and what has to be unlocked first is
 * {@code tree:&lt;tree&gt;}. {@link #buy} therefore checks, in this order: the node exists, the node
 * is below its maximum level, buying it would actually do something ({@link #isRedundant}), its
 * tree is unlocked, every prerequisite is owned at level 1 or higher, and the wallet holds
 * {@code costs[level]}. Only then does it debit, raise the
 * level, count the coins in {@code statistics.coinsSpent}, apply the node's grants, run
 * {@link ProgressionManager#applyPurchase} and save (D14, D15) — the same atomic shape as
 * {@link UnlockManager}, and for the same reason.
 *
 * <p>Grants (E31.f) are applied exactly once, when the node reaches level 1: {@code UNLOCK} adds
 * an unlockable id, {@code ABILITY_CAP} raises {@code profile.abilityLevelCap} and
 * {@code PASSIVE_SLOT} raises {@code profile.passiveSlotBonus}. Both counters are clamped to the
 * E3 ceilings — the cap can never exceed the number of levels the thinnest ability ships, and the
 * slot bonus can never exceed {@link PlayerProfile#MAX_PASSIVE_SLOT_BONUS}. The validator proves
 * the shipped data respects those ceilings; the clamp here means a hand-edited or future data set
 * cannot push a profile into a state the game cannot render.
 *
 * <p>{@link #effectsOf} is the other half of the class: it turns owned levels into the
 * {@code UPGRADES} layer of a run, which is what makes a bought node change the physics.
 */
public final class UpgradeManager {

    private final ProgressionManager progression;
    private final SaveTrigger save;
    /** The tree-unlock route, built on first use and sharing this manager's write path. */
    private UnlockManager unlocks;

    /**
     * Creates a manager.
     *
     * @param progression the write path that propagates a purchase (D14)
     * @param save the write trigger, or {@code null} for {@link SaveTrigger#NONE}
     */
    public UpgradeManager(ProgressionManager progression, SaveTrigger save) {
        this.progression = Objects.requireNonNull(progression, "progression");
        this.save = save == null ? SaveTrigger.NONE : save;
    }

    /**
     * Buys the next level of an upgrade node.
     *
     * @param profile the profile to charge and raise
     * @param nodeId the bare node id, for example {@code feather_1}
     * @param content the loaded content
     * @return what happened; only {@link PurchaseStatus#OK} changed the profile
     */
    public PurchaseResult buy(PlayerProfile profile, String nodeId, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        Wallet wallet = Wallet.of(profile);
        String currency = UnlockManager.currencyOf(content);
        long balance = wallet.balance(currency);
        UpgradeDef node = nodeOrNull(content, nodeId);
        if (node == null) {
            return PurchaseResult.refused(PurchaseStatus.UNKNOWN_ID, nodeId, -1, balance);
        }
        int owned = profile.upgradeLevel(nodeId);
        if (owned >= node.maxLevel()) {
            return PurchaseResult.refused(PurchaseStatus.MAX_LEVEL, nodeId, -1, balance);
        }
        if (isRedundant(profile, node, content)) {
            return PurchaseResult.refused(PurchaseStatus.ALREADY_OWNED, nodeId,
                    node.costOf(owned + 1), balance);
        }
        if (!profile.isUnlocked(TreeDef.NAMESPACE + node.tree())) {
            return PurchaseResult.refused(PurchaseStatus.TREE_LOCKED, nodeId,
                    node.costOf(owned + 1), balance);
        }
        for (String prereq : node.prereqs()) {
            if (profile.upgradeLevel(prereq) < 1) {
                return PurchaseResult.refused(PurchaseStatus.MISSING_PREREQ, nodeId,
                        node.costOf(owned + 1), balance);
            }
        }
        int level = owned + 1;
        long price = node.costOf(level);
        if (!wallet.canAfford(currency, price) || !wallet.spend(currency, price)) {
            return PurchaseResult.refused(PurchaseStatus.INSUFFICIENT_FUNDS, nodeId, price,
                    wallet.balance(currency));
        }
        profile.upgrades.put(nodeId, level);
        profile.statistics.addCoinsSpent(price);
        List<String> granted = level == 1 ? applyGrants(profile, node, content) : List.of();
        ProgressionOutcome outcome = progression.applyPurchase(profile);
        save.saveNow();
        return new PurchaseResult(PurchaseStatus.OK, nodeId, level, price,
                wallet.balance(currency), granted, outcome);
    }

    /**
     * Buys the next level of an ability (D9, E3): the second half of what the shop sells.
     *
     * <p>Level 1 comes with the unlock and costs nothing, so the profile may hold no entry at all
     * for an ability it owns; {@link #abilityLevelOwned} is what that means in numbers. Above it
     * the levels are bought here, one at a time, and never past
     * {@link #abilityLevelCap(PlayerProfile, GameContent)} — the E3 cap, which starts at 2 and is
     * raised only by the single {@code ability_cap} grant in the forge tree.
     *
     * <p>The shape is {@link #buy}'s, for the same reason: check everything, then debit, raise,
     * count the coins, propagate and save, all or nothing.
     *
     * @param profile the profile to charge and raise
     * @param abilityId the bare ability id, for example {@code shield}
     * @param content the loaded content
     * @return what happened; only {@link PurchaseStatus#OK} changed the profile
     */
    public PurchaseResult buyAbilityLevel(PlayerProfile profile, String abilityId,
            GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        Wallet wallet = Wallet.of(profile);
        String currency = UnlockManager.currencyOf(content);
        long balance = wallet.balance(currency);
        AbilityDef ability = abilityOrNull(content, abilityId);
        if (ability == null) {
            return PurchaseResult.refused(PurchaseStatus.UNKNOWN_ID, abilityId, -1, balance);
        }
        if (!profile.isUnlocked(ability.unlockableId())) {
            // The ability itself is a shop unlockable; UnlockManager sells that, not this.
            return PurchaseResult.refused(PurchaseStatus.NOT_FOR_SALE, abilityId, -1, balance);
        }
        int owned = abilityLevelOwned(profile, ability);
        int level = owned + 1;
        if (level > ability.levels().size()) {
            return PurchaseResult.refused(PurchaseStatus.MAX_LEVEL, abilityId, -1, balance);
        }
        if (level > abilityLevelCap(profile, content)) {
            return PurchaseResult.refused(PurchaseStatus.LEVEL_CAPPED, abilityId,
                    ability.levels().get(level - 1).cost(), balance);
        }
        long price = ability.levels().get(level - 1).cost();
        if (!wallet.canAfford(currency, price) || !wallet.spend(currency, price)) {
            return PurchaseResult.refused(PurchaseStatus.INSUFFICIENT_FUNDS, abilityId, price,
                    wallet.balance(currency));
        }
        profile.abilityLevels.put(ability.id(), level);
        profile.statistics.addCoinsSpent(price);
        ProgressionOutcome outcome = progression.applyPurchase(profile);
        save.saveNow();
        return new PurchaseResult(PurchaseStatus.OK, abilityId, level, price,
                wallet.balance(currency), List.of(), outcome);
    }

    /**
     * The level an unlocked ability is owned at: level 1 comes with the unlock, so an ability
     * without an entry in {@code profile.abilityLevels} is owned at level 1, not at level 0.
     *
     * @param profile the profile
     * @param ability the ability
     * @return the owned level, {@code 0} when the ability is not unlocked
     */
    public static int abilityLevelOwned(PlayerProfile profile, AbilityDef ability) {
        if (!profile.isUnlocked(ability.unlockableId())) {
            return 0;
        }
        return Math.max(1, Math.min(ability.levels().size(), profile.abilityLevel(ability.id())));
    }

    /**
     * The price of the next level of an ability.
     *
     * @param profile the profile that owns the levels
     * @param abilityId the ability id
     * @param content the loaded content
     * @return the price in coins, or {@code -1} when there is no next level to buy (unknown,
     *     locked, maxed or capped)
     */
    public static long nextAbilityLevelCost(PlayerProfile profile, String abilityId,
            GameContent content) {
        AbilityDef ability = abilityOrNull(content, abilityId);
        if (ability == null) {
            return -1;
        }
        int owned = abilityLevelOwned(profile, ability);
        int level = owned + 1;
        if (owned == 0 || level > ability.levels().size()
                || level > abilityLevelCap(profile, content)) {
            return -1;
        }
        return ability.levels().get(level - 1).cost();
    }

    /**
     * The highest ability level this profile may buy (E3): its earned cap, never above the number
     * of levels the content actually ships.
     *
     * @param profile the profile
     * @param content the loaded content
     * @return the cap
     */
    public static int abilityLevelCap(PlayerProfile profile, GameContent content) {
        return Math.min(profile.abilityLevelCap, maxAbilityLevelCap(content));
    }

    private static AbilityDef abilityOrNull(GameContent content, String abilityId) {
        if (abilityId == null || abilityId.isBlank() || !content.has(GameContent.ABILITIES)
                || !content.abilities().contains(abilityId)) {
            return null;
        }
        AbilityDef ability = content.abilities().get(abilityId);
        return ability.levels().isEmpty() ? null : ability;
    }

    /**
     * The price of the next level of a node.
     *
     * @param profile the profile that owns the levels
     * @param nodeId the node id
     * @param content the loaded content
     * @return the price in coins, or {@code -1} when the node is unknown or already maxed
     */
    public static long nextCost(PlayerProfile profile, String nodeId, GameContent content) {
        UpgradeDef node = nodeOrNull(content, nodeId);
        if (node == null) {
            return -1;
        }
        int owned = profile.upgradeLevel(nodeId);
        return owned >= node.maxLevel() ? -1 : node.costOf(owned + 1);
    }

    /**
     * Whether a node could be bought right now, ignoring the price.
     *
     * @param profile the profile
     * @param nodeId the node id
     * @param content the loaded content
     * @return {@code true} when the tree is unlocked, the prerequisites are owned and the node is
     *     below its maximum level
     */
    public static boolean isAvailable(PlayerProfile profile, String nodeId, GameContent content) {
        UpgradeDef node = nodeOrNull(content, nodeId);
        if (node == null || profile.upgradeLevel(nodeId) >= node.maxLevel()
                || !profile.isUnlocked(TreeDef.NAMESPACE + node.tree())
                || isRedundant(profile, node, content)) {
            return false;
        }
        for (String prereq : node.prereqs()) {
            if (profile.upgradeLevel(prereq) < 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the tree a node belongs to is still locked for a profile (M13).
     *
     * <p>A node is never an unlockable; what gates it is {@code tree:<tree>}. A locked tree cannot
     * be bought through {@link #buy} at all, so the Forge offers the tree itself instead.
     *
     * @param profile the profile to ask
     * @param treeId the bare tree id, for example {@code economy}
     * @return {@code true} when {@code tree:<treeId>} is not in {@code profile.unlocked}
     */
    public static boolean isTreeLocked(PlayerProfile profile, String treeId) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(treeId, "treeId");
        return !profile.isUnlocked(TreeDef.NAMESPACE + treeId);
    }

    /**
     * The unlock id of a tree.
     *
     * @param treeId the bare tree id
     * @return the namespaced id, {@code tree:<treeId>}
     */
    public static String treeUnlockId(String treeId) {
        Objects.requireNonNull(treeId, "treeId");
        return TreeDef.NAMESPACE + treeId;
    }

    /**
     * The price of opening a tree, or {@code -1} when the tree is unknown or is not for sale
     * (M13).
     *
     * @param treeId the bare tree id
     * @param content the loaded content
     * @return the price in coins, or {@code -1}
     */
    public long treeUnlockPrice(String treeId, GameContent content) {
        return unlocks().priceOf(treeUnlockId(treeId), content);
    }

    /**
     * Whether the wallet covers opening a tree right now (M13).
     *
     * <p>A tree nobody owns and nobody can pay for can still be free: {@code false} is also the
     * answer for a tree the profile already owns, because there is nothing left to buy.
     *
     * @param profile the profile to price against
     * @param treeId the bare tree id
     * @param content the loaded content
     * @return {@code true} when the tree is for sale and either already earned or affordable
     */
    public boolean canAffordTreeUnlock(PlayerProfile profile, String treeId, GameContent content) {
        if (!isTreeLocked(profile, treeId)) {
            return false;
        }
        if (isTreeEarned(profile, treeId, content)) {
            return true;
        }
        long price = treeUnlockPrice(treeId, content);
        if (price < 0) {
            return false;
        }
        return Wallet.of(profile).canAfford(UnlockManager.currencyOf(content), price);
    }

    /**
     * Whether a locked tree has already been earned by playing and is only missing the bookkeeping
     * (M13).
     *
     * <p>{@code tree:forge} is an {@code any_of} of {@code world_cleared wind_valley} and a
     * {@code purchase}: the coin branch is the shortcut, not the requirement. {@link
     * UnlockEvaluator} never reports a {@code purchase} as satisfied, so a profile that has
     * cleared the world satisfies the tree and would be granted it by the unlock step of the next
     * run or purchase. Until that step runs the unlock is still missing from
     * {@code profile.unlocked} — which is exactly what a save carried over from a build that
     * predates the tree looks like. Reading the condition here is what stops the Forge from
     * charging 900 coins for something the player already earned.
     *
     * @param profile the profile to ask
     * @param treeId the bare tree id
     * @param content the loaded content
     * @return {@code true} when the tree is locked but its non-purchase branch is satisfied
     */
    public boolean isTreeEarned(PlayerProfile profile, String treeId, GameContent content) {
        if (!isTreeLocked(profile, treeId)) {
            return false;
        }
        return unlocks().evaluator(content).evaluate(profile).contains(treeUnlockId(treeId));
    }

    /**
     * Grants every tree unlock a profile has already earned and writes it once (M13).
     *
     * <p>This is the same reconciliation the unlock step of a finished run performs, run on
     * demand and limited to trees: a screen that is about to offer {@code tree:<id>} for coins
     * first gives away what the player has already paid for in play. It is idempotent — a profile
     * with nothing owed produces no grant and no write — so a screen may call it whenever it
     * rebuilds.
     *
     * @param profile the profile to reconcile
     * @param content the loaded content
     * @return the tree unlock ids granted by this call, empty when there was nothing to grant
     */
    public List<String> claimEarnedTrees(PlayerProfile profile, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        List<String> granted = new ArrayList<>();
        for (String id : unlocks().evaluator(content).evaluate(profile)) {
            if (id.startsWith(TreeDef.NAMESPACE) && profile.unlock(id)) {
                granted.add(id);
            }
        }
        if (!granted.isEmpty()) {
            // The same trailing step a purchase runs: a tree in the collection counters can
            // satisfy the next unlockable, and an achievement can read the trees owned.
            progression.applyPurchase(profile);
            save.saveNow();
        }
        return Collections.unmodifiableList(granted);
    }

    /**
     * Buys the tree unlock that lets one of its nodes be bought (M13): the same atomic route the
     * shop uses, reached through {@link UnlockManager#purchase}.
     *
     * <p>The Forge is the second screen that sells a tree. Going through the same manager — rather
     * than unlocking the profile and spending the wallet by hand — is what makes a tree bought
     * here land in the save byte-for-byte as one bought in the shop. {@code ALREADY_OWNED} and
     * {@code INSUFFICIENT_FUNDS} are refused before the debit, exactly as they are there.
     *
     * <p>A tree already earned by play is never charged for: {@link #claimEarnedTrees} runs first
     * and, when it grants this very tree, the purchase is reported as a free {@code OK} at cost
     * {@code 0} — the trailing pipeline has already run inside the claim, which is why the
     * {@link ProgressionOutcome} here is {@link ProgressionOutcome#EMPTY}. That is the guard
     * against a profile carried over from a build without the tree paying the coin shortcut for
     * something it had already unlocked.
     *
     * @param profile the profile to charge and grant into
     * @param treeId the bare tree id, for example {@code economy}
     * @param content the loaded content
     * @return what happened; only {@link PurchaseStatus#OK} changed the profile
     */
    public PurchaseResult buyTree(PlayerProfile profile, String treeId, GameContent content) {
        if (isTreeLocked(profile, treeId)
                && claimEarnedTrees(profile, content).contains(treeUnlockId(treeId))) {
            return new PurchaseResult(PurchaseStatus.OK, treeUnlockId(treeId), 0, 0,
                    Wallet.of(profile).balance(UnlockManager.currencyOf(content)),
                    List.of(treeUnlockId(treeId)), ProgressionOutcome.EMPTY);
        }
        return unlocks().purchase(profile, treeUnlockId(treeId), content);
    }

    /**
     * The unlock manager behind the tree-unlock route, sharing this manager's progression and save
     * triggers so both routes persist identically.
     *
     * @return the manager
     */
    private UnlockManager unlocks() {
        if (unlocks == null) {
            unlocks = new UnlockManager(progression, save);
        }
        return unlocks;
    }

    /**
     * The {@code UPGRADES} layer of a run: every owned node's effects at the level it is owned at
     * (D8).
     *
     * <p>{@code FLAT_ADD} and {@code PERCENT_ADD} scale linearly with the level and
     * {@code MULTIPLY} compounds, which {@link UpgradeDef#effectsAt(int)} does; the order is
     * content order, so the same profile always produces the same list — and therefore the same
     * breakdown in the selection screen.
     *
     * @param profile the profile whose levels to read
     * @param content the loaded content
     * @return the modifiers, sourced as {@code upgrade:&lt;node&gt;}
     */
    public static List<StatModifier> effectsOf(PlayerProfile profile, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        List<StatModifier> out = new ArrayList<>();
        for (UpgradeDef node : content.upgrades()) {
            int level = profile.upgradeLevel(node.id());
            if (level > 0) {
                out.addAll(node.effectsAt(Math.min(level, node.maxLevel())));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Whether buying a node would change nothing at all: it has no stat effects and every one of
     * its grants is already at its ceiling.
     *
     * <p>{@code hard_tier_1} is the shipped case. It has no effects and its only value is granting
     * {@code tier:hard}, which {@code difficulty.json} also gives away for 40 gates in a run or
     * 400 across the profile — the intended cumulative path. A player who walks that path first
     * would otherwise still be offered the node, pay 400 coins and receive nothing: the grant loop
     * finds the id already owned and the wallet is simply lighter. Refusing it before the debit is
     * the only place the money can still be saved.
     *
     * @param profile the profile
     * @param nodeId the node id
     * @param content the loaded content
     * @return {@code true} when the purchase would be a pure loss
     */
    public static boolean isRedundant(PlayerProfile profile, String nodeId, GameContent content) {
        UpgradeDef node = nodeOrNull(content, nodeId);
        return node != null && isRedundant(profile, node, content);
    }

    private static boolean isRedundant(PlayerProfile profile, UpgradeDef node,
            GameContent content) {
        if (!node.effectsPerLevel().isEmpty() || profile.upgradeLevel(node.id()) > 0) {
            // Grants land when the node reaches level 1; a node with effects always does
            // something, and one that is already owned is judged by its maximum level.
            return false;
        }
        for (GrantDef grant : node.grants()) {
            switch (grant.type()) {
                case UNLOCK:
                    if (!profile.isUnlocked(grant.id())) {
                        return false;
                    }
                    break;
                case ABILITY_CAP:
                    if (profile.abilityLevelCap < abilityLevelCeiling(content)) {
                        return false;
                    }
                    break;
                case PASSIVE_SLOT:
                default:
                    if (profile.passiveSlotBonus < PlayerProfile.MAX_PASSIVE_SLOT_BONUS) {
                        return false;
                    }
                    break;
            }
        }
        return true;
    }

    /**
     * Applies the grants of a node that just reached level 1 (E31.f).
     *
     * @param profile the profile to grant into
     * @param node the node bought
     * @param content the loaded content, for the E3 ability-level ceiling
     * @return the unlock ids the grants added, in node order
     */
    private static List<String> applyGrants(PlayerProfile profile, UpgradeDef node,
            GameContent content) {
        List<String> granted = new ArrayList<>();
        for (GrantDef grant : node.grants()) {
            switch (grant.type()) {
                case UNLOCK:
                    if (profile.unlock(grant.id())) {
                        granted.add(grant.id());
                    }
                    break;
                case ABILITY_CAP:
                    profile.abilityLevelCap = (int) Math.min(
                            profile.abilityLevelCap + grant.amount(),
                            abilityLevelCeiling(content));
                    break;
                case PASSIVE_SLOT:
                default:
                    profile.passiveSlotBonus = (int) Math.min(
                            profile.passiveSlotBonus + grant.amount(),
                            PlayerProfile.MAX_PASSIVE_SLOT_BONUS);
                    break;
            }
        }
        return Collections.unmodifiableList(granted);
    }

    /**
     * The highest {@code profile.abilityLevelCap} the shipped content can produce (E3): the base
     * cap plus every {@code ability_cap} grant in the trees, never above the number of levels the
     * thinnest ability ships.
     *
     * <p>This is the number E3 states and the validator proves ({@code baseCap + Σ ability_cap
     * grants ≤ min levels over abilities}), so it is the number both {@link #applyGrants} and
     * {@link ProfileSchema} clamp to. {@link #maxAbilityLevelCap} is the second half of that
     * inequality on its own; the two happen to agree on the shipped data and would not on a data
     * set that ships a fourth ability level without a node to reach it.
     *
     * @param content the loaded content
     * @return the ceiling for {@code profile.abilityLevelCap}
     */
    public static int abilityLevelCeiling(GameContent content) {
        int ceiling = PlayerProfile.DEFAULT_ABILITY_LEVEL_CAP;
        for (UpgradeDef node : content.upgrades()) {
            for (GrantDef grant : node.grants()) {
                if (grant.type() == GrantType.ABILITY_CAP) {
                    ceiling += (int) grant.amount();
                }
            }
        }
        return Math.min(ceiling, maxAbilityLevelCap(content));
    }

    /**
     * The number of levels the thinnest ability ships, never below the base cap (E3).
     *
     * @param content the loaded content
     * @return the highest ability level the content could ever offer
     */
    public static int maxAbilityLevelCap(GameContent content) {
        int min = Integer.MAX_VALUE;
        for (AbilityDef ability : content.abilities()) {
            min = Math.min(min, ability.levels().size());
        }
        return min == Integer.MAX_VALUE ? PlayerProfile.DEFAULT_ABILITY_LEVEL_CAP
                : Math.max(PlayerProfile.DEFAULT_ABILITY_LEVEL_CAP, min);
    }

    /**
     * Applies {@code aliases.json} to a bound profile (E21): renames, removed nodes and their
     * refunds.
     *
     * <p>Renames rewrite {@code unlocked}, the keys of {@code upgrades} and {@code abilityLevels}
     * and the fields of {@code selected}, each from its own table, because the same word means
     * different things in different places. A removed node is dropped and its refund is credited
     * <em>once</em>: {@code profile.reconciled} records every entry that has already been applied,
     * so a second load pays nothing again.
     *
     * @param profile the bound profile to reconcile in place
     * @param aliases the table, {@link AliasDef#EMPTY} when the content ships none
     * @param currency the currency refunds are paid in
     * @return one English line per change, in the order the changes were made
     */
    public static List<String> reconcile(PlayerProfile profile, AliasDef aliases, String currency) {
        Objects.requireNonNull(profile, "profile");
        if (aliases == null || aliases.isEmpty()) {
            return List.of();
        }
        List<String> report = new ArrayList<>();
        renameList(profile.unlocked, aliases.unlocked(), "unlocked", report);
        renameKeys(profile.upgrades, aliases.upgrades(), "upgrades", report);
        renameKeys(profile.abilityLevels, aliases.abilityLevels(), "abilityLevels", report);
        renameSelection(profile, aliases.selected(), report);
        Wallet wallet = Wallet.of(profile);
        for (String nodeId : aliases.removedUpgrades()) {
            // The refund is what was spent on the node, so it is owed only to a profile that
            // owned it. Paying it to everyone would hand every player free coins on the first
            // launch after a content removal — and, because a refund counts in coinsEarned,
            // free unlocks with them.
            Integer owned = profile.upgrades.remove(nodeId);
            if (owned != null) {
                report.add("upgrades dropped the removed node " + nodeId);
            }
            Long refund = aliases.refunds().get(nodeId);
            String token = "refund:" + nodeId;
            if (owned != null && refund != null && refund > 0
                    && !profile.reconciled.contains(token)) {
                wallet.add(currency, refund);
                profile.statistics.addCoinsEarned(refund);
                profile.reconciled.add(token);
                report.add("refunded " + refund + " " + currency + " for " + nodeId);
            }
        }
        return Collections.unmodifiableList(report);
    }

    private static void renameList(List<String> ids, Map<String, String> table, String field,
            List<String> report) {
        for (int i = 0; i < ids.size(); i++) {
            String replacement = table.get(ids.get(i));
            if (replacement != null) {
                report.add(field + " renamed " + ids.get(i) + " to " + replacement);
                ids.set(i, replacement);
            }
        }
    }

    private static <V> void renameKeys(Map<String, V> values, Map<String, String> table,
            String field, List<String> report) {
        if (table.isEmpty()) {
            return;
        }
        Map<String, V> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, V> entry : values.entrySet()) {
            String replacement = table.get(entry.getKey());
            if (replacement == null) {
                renamed.put(entry.getKey(), entry.getValue());
            } else {
                report.add(field + " renamed " + entry.getKey() + " to " + replacement);
                renamed.put(replacement, entry.getValue());
            }
        }
        values.clear();
        values.putAll(renamed);
    }

    private static void renameSelection(PlayerProfile profile,
            Map<String, Map<String, String>> tables, List<String> report) {
        PlayerProfile.Selection selection = profile.selected;
        selection.birdId = renamed(tables, "birdId", selection.birdId, report);
        selection.paletteId = renamed(tables, "paletteId", selection.paletteId, report);
        selection.worldId = renamed(tables, "worldId", selection.worldId, report);
        selection.tierId = renamed(tables, "tierId", selection.tierId, report);
        selection.activeAbilityId =
                renamed(tables, "activeAbilityId", selection.activeAbilityId, report);
        Map<String, String> passives = tables.get("passiveAbilityIds");
        if (passives != null) {
            renameList(selection.passiveAbilityIds, passives, "selected.passiveAbilityIds", report);
        }
    }

    private static String renamed(Map<String, Map<String, String>> tables, String field,
            String value, List<String> report) {
        Map<String, String> table = tables.get(field);
        if (table == null || value == null) {
            return value;
        }
        String replacement = table.get(value);
        if (replacement == null) {
            return value;
        }
        report.add("selected." + field + " renamed " + value + " to " + replacement);
        return replacement;
    }

    private static UpgradeDef nodeOrNull(GameContent content, String nodeId) {
        if (nodeId == null || nodeId.isBlank() || !content.upgrades().contains(nodeId)) {
            return null;
        }
        return content.upgrades().get(nodeId);
    }
}
