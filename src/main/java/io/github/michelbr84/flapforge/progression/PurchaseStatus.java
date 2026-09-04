package io.github.michelbr84.flapforge.progression;

/**
 * Why a purchase went through or did not (D14). Every failure is a value rather than an exception,
 * because a shop screen has to explain the refusal and a bot policy has to branch on it.
 *
 * <p>Only {@link #OK} touches the profile: {@link UnlockManager} and {@link UpgradeManager} check
 * everything before they debit, so a refused purchase leaves the wallet, the unlock list, the
 * levels and the statistics exactly as they were.
 */
public enum PurchaseStatus {

    /** The purchase was applied: coins debited, the thing granted, the profile marked dirty. */
    OK,
    /** No unlockable or upgrade node carries that id. */
    UNKNOWN_ID,
    /** The player already owns it (an unlockable, or the node's last level). */
    ALREADY_OWNED,
    /** The unlockable has no {@code purchase} branch, so it can only be earned. */
    NOT_FOR_SALE,
    /** The node's tree has not been unlocked yet. */
    TREE_LOCKED,
    /** A prerequisite node is not owned at level 1 or higher. */
    MISSING_PREREQ,
    /** The node is already at its maximum level. */
    MAX_LEVEL,
    /** The ability is at {@code profile.abilityLevelCap} and needs an {@code ability_cap} grant
     * before the next level can be bought (E3). */
    LEVEL_CAPPED,
    /** The wallet does not hold the price. */
    INSUFFICIENT_FUNDS
}
