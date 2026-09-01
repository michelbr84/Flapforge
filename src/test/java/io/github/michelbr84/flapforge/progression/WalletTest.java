package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link Wallet}: balances are per currency, never negative, and spending is all or nothing. */
class WalletTest {

    private static final String COINS = PlayerProfile.CURRENCY_COINS;

    @Test
    void unknownCurrencyReadsAsZero() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        assertEquals(0, wallet.balance(COINS));
        assertEquals(0, wallet.balance("gems"));
    }

    @Test
    void addAccumulates() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        assertEquals(50, wallet.add(COINS, 50));
        assertEquals(125, wallet.add(COINS, 75));
        assertEquals(125, wallet.balance(COINS));
    }

    @Test
    void addRefusesNegativeAndZeroAmounts() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        wallet.add(COINS, 100);
        assertEquals(100, wallet.add(COINS, -40), "a credit is never a debit");
        assertEquals(100, wallet.add(COINS, 0));
        assertEquals(100, wallet.balance(COINS));
    }

    @Test
    void spendDebitsWhenTheBalanceCovers() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        wallet.add(COINS, 300);
        assertTrue(wallet.spend(COINS, 120));
        assertEquals(180, wallet.balance(COINS));
    }

    @Test
    void spendIsAllOrNothing() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        wallet.add(COINS, 100);
        assertFalse(wallet.spend(COINS, 101), "an unaffordable purchase must not partially debit");
        assertEquals(100, wallet.balance(COINS));
        assertFalse(wallet.canAfford(COINS, 101));
        assertTrue(wallet.canAfford(COINS, 100));
    }

    @Test
    void spendOfNothingAlwaysSucceeds() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        assertTrue(wallet.spend(COINS, 0));
        assertTrue(wallet.spend(COINS, -5));
        assertEquals(0, wallet.balance(COINS));
    }

    @Test
    void balanceNeverGoesNegative() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        wallet.add(COINS, 10);
        for (int i = 0; i < 5; i++) {
            wallet.spend(COINS, 4);
        }
        assertEquals(2, wallet.balance(COINS));
        wallet.set(COINS, -1);
        assertEquals(0, wallet.balance(COINS), "set clamps at zero too");
    }

    @Test
    void currenciesAreIndependent() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        wallet.add(COINS, 100);
        wallet.add("gems", 5);
        assertFalse(wallet.spend("gems", 6));
        assertTrue(wallet.spend("gems", 5));
        assertEquals(100, wallet.balance(COINS));
        assertEquals(0, wallet.balance("gems"));
    }

    @Test
    void declareCreatesEveryDeclaredCurrencyWithoutClearingBalances() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        wallet.add(COINS, 40);
        wallet.declare(List.of(COINS, "gems"));
        assertEquals(40, wallet.balance(COINS));
        assertEquals(0, wallet.balance("gems"));
        assertEquals(List.of(COINS, "gems"), List.copyOf(wallet.balances().keySet()));
    }

    @Test
    void writesThroughToTheProfileItWraps() {
        PlayerProfile profile = new PlayerProfile();
        Wallet wallet = Wallet.of(profile);
        wallet.add(COINS, 250);
        assertEquals(250L, profile.wallet.get(COINS),
                "the wallet is a view of the persisted map, not a copy");
    }

    @Test
    void balancesViewIsUnmodifiable() {
        Wallet wallet = new Wallet(new LinkedHashMap<>());
        wallet.add(COINS, 1);
        Map<String, Long> view = wallet.balances();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> view.put(COINS, 999L));
    }
}
