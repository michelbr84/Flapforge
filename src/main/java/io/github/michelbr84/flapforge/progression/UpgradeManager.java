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
