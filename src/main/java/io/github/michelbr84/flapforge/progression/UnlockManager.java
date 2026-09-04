package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The shop half of the meta-progression (D13, D14): buying an unlockable that carries a
 * {@code purchase} branch — a bird, a palette, an ability, a world, a tier, an upgrade tree or a
 * feature.
 *
 * <p>One purchase is one atomic step, in this order and no other:
 * <ol>
 *   <li><b>check</b> — the id exists, is not owned, is for sale, and the wallet holds the price;
 *       any failure returns a {@link PurchaseResult} and touches nothing;</li>
 *   <li><b>deduct</b> — {@link Wallet#spend} debits the price;</li>
 *   <li><b>grant</b> — the id lands in {@code profile.unlocked};</li>
 *   <li><b>account</b> — {@code statistics.coinsSpent} counts the coins;</li>
 *   <li><b>propagate</b> — {@link ProgressionManager#applyPurchase} runs the achievement and
 *       unlock evaluators and marks the profile dirty, so buying the last bird fires its
 *       collection immediately (E17);</li>
 *   <li><b>save</b> — the {@link SaveTrigger} writes the profile now (D15).</li>
 * </ol>
 * There is no window in which the coins are gone and the unlock is missing: the debit is the last
 * thing that can fail, and it is all-or-nothing.
 */
public final class UnlockManager {

    private final ProgressionManager progression;
    private final SaveTrigger save;
    private GameContent cachedContent;
    private UnlockEvaluator cachedEvaluator;

    /**
     * Creates a manager.
     *
     * @param progression the write path that propagates a purchase (D14)
     * @param save the write trigger, or {@code null} for {@link SaveTrigger#NONE}
     */
    public UnlockManager(ProgressionManager progression, SaveTrigger save) {
        this.progression = Objects.requireNonNull(progression, "progression");
        this.save = save == null ? SaveTrigger.NONE : save;
    }

    /**
     * Buys an unlockable.
     *
     * @param profile the profile to charge and grant into
     * @param unlockId the namespaced id, for example {@code bird:guardian}
     * @param content the loaded content
     * @return what happened; only {@link PurchaseStatus#OK} changed the profile
     */
    public PurchaseResult purchase(PlayerProfile profile, String unlockId, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        UnlockEvaluator evaluator = evaluator(content);
        Wallet wallet = Wallet.of(profile);
        String currency = currencyOf(content);
        long balance = wallet.balance(currency);
        UnlockConditionDef condition = evaluator.conditionOf(unlockId);
        if (condition == null) {
            return PurchaseResult.refused(PurchaseStatus.UNKNOWN_ID, unlockId, -1, balance);
        }
        if (profile.isUnlocked(unlockId)) {
            return PurchaseResult.refused(PurchaseStatus.ALREADY_OWNED, unlockId,
                    UnlockEvaluator.priceOf(condition), balance);
        }
        long price = UnlockEvaluator.priceOf(condition);
        if (price < 0) {
            return PurchaseResult.refused(PurchaseStatus.NOT_FOR_SALE, unlockId, -1, balance);
        }
        if (!wallet.canAfford(currency, price)) {
            return PurchaseResult.refused(PurchaseStatus.INSUFFICIENT_FUNDS, unlockId, price,
                    balance);
        }
        if (!wallet.spend(currency, price)) {
            // Unreachable while canAfford and spend read the same balance; kept because a silent
            // half-purchase would be the one bug this class exists to make impossible.
            return PurchaseResult.refused(PurchaseStatus.INSUFFICIENT_FUNDS, unlockId, price,
                    wallet.balance(currency));
        }
        profile.unlock(unlockId);
        profile.statistics.addCoinsSpent(price);
        ProgressionOutcome outcome = progression.applyPurchase(profile);
        save.saveNow();
        return new PurchaseResult(PurchaseStatus.OK, unlockId, 0, price,
                wallet.balance(currency), List.of(unlockId), outcome);
    }

    /**
     * The price of an unlockable.
     *
     * @param unlockId the namespaced id
     * @param content the loaded content
     * @return the price in coins, or {@code -1} when the id is unknown or is not for sale
     */
    public long priceOf(String unlockId, GameContent content) {
        return evaluator(content).priceOf(unlockId);
    }

    /**
     * Everything the shop can offer a profile right now: the unlockables that carry a price and
     * are not owned yet, cheapest first, ties broken by content order (D13 "Shop = purchase-type
     * unlocks + ability levels"; ability levels arrive with {@code AbilityManager} in M5).
     *
     * @param profile the profile to price against
     * @param content the loaded content
     * @return the offers, in a deterministic order
     */
    public List<Offer> offers(PlayerProfile profile, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        UnlockEvaluator evaluator = evaluator(content);
        Wallet wallet = Wallet.of(profile);
        String currency = currencyOf(content);
        List<Offer> offers = new ArrayList<>();
        for (Map.Entry<String, UnlockConditionDef> entry : evaluator.conditions().entrySet()) {
            String id = entry.getKey();
            long price = UnlockEvaluator.priceOf(entry.getValue());
            if (price < 0 || profile.isUnlocked(id)) {
                continue;
            }
            offers.add(new Offer(id, evaluator.kindOf(id), price,
                    wallet.canAfford(currency, price)));
        }
        offers.sort((a, b) -> a.cost() == b.cost() ? 0 : (a.cost() < b.cost() ? -1 : 1));
        return Collections.unmodifiableList(offers);
    }

    /**
     * The evaluator over a content set, rebuilt only when the content changes.
     *
     * @param content the loaded content
     * @return the evaluator
     */
    public UnlockEvaluator evaluator(GameContent content) {
        Objects.requireNonNull(content, "content");
        if (cachedEvaluator == null || cachedContent != content) {
            cachedContent = content;
            cachedEvaluator = new UnlockEvaluator(content);
        }
        return cachedEvaluator;
    }

    /**
     * The currency a purchase is paid in: the first of {@code economy.json.currencies}.
     *
     * @param content the loaded content
     * @return the currency id
     */
    static String currencyOf(GameContent content) {
        if (content.economy() == null || content.economy().primaryCurrency() == null) {
            return PlayerProfile.CURRENCY_COINS;
        }
        return content.economy().primaryCurrency();
    }

    /**
     * One line of the shop.
     *
     * @param id the namespaced unlockable id
     * @param kind what kind of thing it is, or {@code null} when the content does not say
     * @param cost the price in coins
     * @param affordable whether the wallet holds the price right now
     */
    public record Offer(String id, ContentKind kind, long cost, boolean affordable) {

        /**
         * Checks the id.
         *
         * @param id the unlockable id
         * @param kind the kind
         * @param cost the price
         * @param affordable whether it can be paid now
         */
        public Offer {
            Objects.requireNonNull(id, "id");
        }
    }
}
