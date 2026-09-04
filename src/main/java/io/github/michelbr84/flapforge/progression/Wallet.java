package io.github.michelbr84.flapforge.progression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The player's balances, one per currency (D13). {@code economy.json.currencies} declares the
 * currencies; 1.0 ships exactly one ({@code coins}), and the map keeps the shape open so an added
 * currency is a data change rather than a code change.
 *
 * <p>A wallet is a <em>view</em> over {@link PlayerProfile#wallet}: it holds the very map the
 * profile persists, so crediting through the wallet is what the next save writes. That is
 * deliberate — there is one place a balance lives, and no copy to keep in step.
 *
 * <p>The two operations are total and never throw. {@link #add(String, long)} refuses a negative
 * amount (a refund is a credit, a price is a spend — a negative credit would be a bug that silently
 * ate coins) and {@link #spend(String, long)} returns {@code false} and changes nothing when the
 * balance would go below zero. A balance is therefore never negative, at any point, in memory or
 * on disk.
 */
public final class Wallet {

    private final Map<String, Long> balances;

    /**
     * Wraps an existing balance map.
     *
     * @param balances the live map; entries are created on demand
     */
    public Wallet(Map<String, Long> balances) {
        this.balances = Objects.requireNonNull(balances, "balances");
    }

    /**
     * The wallet of a profile, backed by {@link PlayerProfile#wallet}.
     *
     * @param profile the profile
     * @return the wallet
     */
    public static Wallet of(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (profile.wallet == null) {
            profile.wallet = new LinkedHashMap<>();
        }
        return new Wallet(profile.wallet);
    }

    /**
     * The balance of a currency.
     *
     * @param currency the currency id
     * @return the balance, 0 when the currency has never been credited
     */
    public long balance(String currency) {
        Long value = balances.get(currency);
        return value == null ? 0 : value;
    }

    /**
     * Whether the wallet holds at least an amount.
     *
     * @param currency the currency id
     * @param amount the amount; a non-positive amount is always affordable
     * @return {@code true} when {@link #spend(String, long)} would succeed
     */
    public boolean canAfford(String currency, long amount) {
        return amount <= 0 || balance(currency) >= amount;
    }

    /**
     * Credits an amount.
     *
     * @param currency the currency id
     * @param amount the amount; zero is a no-op and a negative amount is refused
     * @return the new balance
     */
    public long add(String currency, long amount) {
        if (currency == null || currency.isBlank() || amount <= 0) {
            return balance(currency);
        }
        long updated = balance(currency) + amount;
        if (updated < 0) {
            updated = Long.MAX_VALUE;
        }
        balances.put(currency, updated);
        return updated;
    }

    /**
     * Debits an amount, all or nothing.
     *
     * @param currency the currency id
     * @param amount the amount; zero or less always succeeds and changes nothing
     * @return {@code true} when the balance covered the amount and was debited
     */
    public boolean spend(String currency, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (currency == null || currency.isBlank()) {
            return false;
        }
        long current = balance(currency);
        if (current < amount) {
            return false;
        }
        balances.put(currency, current - amount);
        return true;
    }

    /**
     * Sets a balance outright (prestige resets and the save inspector).
     *
     * @param currency the currency id
     * @param amount the balance; a negative amount becomes zero
     */
    public void set(String currency, long amount) {
        if (currency != null && !currency.isBlank()) {
            balances.put(currency, Math.max(0, amount));
        }
    }

    /**
     * Makes sure every declared currency has an entry, so a fresh wallet shows the full list.
     *
     * @param currencies the currency ids from {@code economy.json}
     */
    public void declare(Iterable<String> currencies) {
        for (String currency : currencies) {
            if (currency != null && !currency.isBlank()) {
                balances.putIfAbsent(currency, 0L);
            }
        }
    }

    /**
     * The balances, in insertion order.
     *
     * @return an unmodifiable view of the live map
     */
    public Map<String, Long> balances() {
        return Collections.unmodifiableMap(balances);
    }

    @Override
    public String toString() {
        return "Wallet" + balances;
    }
}
