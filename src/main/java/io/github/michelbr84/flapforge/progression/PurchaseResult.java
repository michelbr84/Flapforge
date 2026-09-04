package io.github.michelbr84.flapforge.progression;

import java.util.List;
import java.util.Objects;

/**
 * What one call to {@link UnlockManager#purchase} or {@link UpgradeManager#buy} did (D14).
 *
 * <p>Like {@link ProgressionOutcome} it carries facts only — no events, no player-facing wording:
 * the screen that asked for the purchase decides what to say about it.
 *
 * @param status whether the purchase went through, and why not when it did not
 * @param id the unlockable id or the upgrade node id the call was about
 * @param level the level bought, 1-based; {@code 0} for an unlockable and for a refusal
 * @param cost what it cost, or would have cost; {@code -1} when there is no price at all
 * @param balance the coin balance after the call
 * @param granted the ids this purchase added to {@code profile.unlocked} directly — the thing
 *     bought, plus the {@code UNLOCK} grants of an upgrade node (E31.f); unlocks the evaluator
 *     added afterwards are in {@code outcome.unlocksGranted()}
 * @param outcome what {@link ProgressionManager#applyPurchase} granted after it,
 *     {@link ProgressionOutcome#EMPTY} for a refusal
 */
public record PurchaseResult(PurchaseStatus status, String id, int level, long cost, long balance,
        List<String> granted, ProgressionOutcome outcome) {

    /**
     * Copies the collections.
     *
     * @param status the status
     * @param id the id
     * @param level the level bought
     * @param cost the price
     * @param balance the balance after the call
     * @param granted the ids granted directly
     * @param outcome the trailing pipeline outcome
     */
    public PurchaseResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(outcome, "outcome");
        granted = List.copyOf(granted);
    }

    /**
     * A refusal that changed nothing.
     *
     * @param status why the purchase was refused
     * @param id the id the call was about
     * @param cost the price it would have had, or {@code -1}
     * @param balance the untouched balance
     * @return the result
     */
    public static PurchaseResult refused(PurchaseStatus status, String id, long cost,
            long balance) {
        return new PurchaseResult(status, id, 0, cost, balance, List.of(),
                ProgressionOutcome.EMPTY);
    }

    /**
     * Whether the purchase was applied.
     *
     * @return {@code true} when the status is {@link PurchaseStatus#OK}
     */
    public boolean ok() {
        return status == PurchaseStatus.OK;
    }
}
