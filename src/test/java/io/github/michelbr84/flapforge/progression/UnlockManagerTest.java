package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link UnlockManager}: the shop is atomic (D14) — a purchase either debits, grants, accounts,
 * propagates and saves, or changes nothing at all.
 */
class UnlockManagerTest {

    private static final String COINS = PlayerProfile.CURRENCY_COINS;

    private static GameContent content;

    private PlayerProfile profile;
    private ProgressionManager progression;
    private UnlockManager manager;
    private int saves;

    @BeforeAll
    static void loadContent() {
        content = GameContent.load();
    }

    @BeforeEach
    void setUp() {
        profile = PlayerProfile.fresh(0).normalize();
        progression = new ProgressionManager(new FixedTimeSource(1_700_000_000_000L),
                ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
        saves = 0;
        manager = new UnlockManager(progression, () -> saves++);
    }

    private void give(long coins) {
        Wallet.of(profile).add(COINS, coins);
    }

    private long balance() {
        return Wallet.of(profile).balance(COINS);
    }

    @Test
    void buyingABirdDebitsGrantsAccountsAndSaves() {
        give(200);
        PurchaseResult result = manager.purchase(profile, "bird:guardian", content);

        assertTrue(result.ok(), () -> "refused with " + result.status());
        assertEquals(150, result.cost());
        assertEquals(50, result.balance());
        assertEquals(50, balance());
        assertTrue(profile.isUnlocked("bird:guardian"));
        assertEquals(List.of("bird:guardian"), result.granted());
        assertEquals(150, profile.statistics.coinsSpent);
        assertTrue(progression.isDirty(), "a purchase leaves the profile to be written");
        assertEquals(1, saves, "D15: a purchase is written now, not at the end of the next run");
    }

    @Test
    void thePurchaseRunsTheUnlockEvaluatorRightAway() {
        give(200);
        PurchaseResult result = manager.purchase(profile, "bird:guardian", content);

        assertTrue(result.outcome().unlocksGranted().contains("cosmetic:guardian:default"),
                "owning the bird makes its default palette earned, and E17 wants it now");
        assertTrue(profile.isUnlocked("cosmetic:guardian:default"));
        assertEquals(List.of(ProgressionManager.Step.ACHIEVEMENTS,
                ProgressionManager.Step.UNLOCKS, ProgressionManager.Step.DIRTY),
                progression.lastSteps(), "D14's trailing steps, in order");
    }

    @Test
    void anUnaffordablePurchaseChangesNothing() {
        give(149);
        PurchaseResult result = manager.purchase(profile, "bird:guardian", content);

        assertEquals(PurchaseStatus.INSUFFICIENT_FUNDS, result.status());
        assertEquals(150, result.cost());
        assertEquals(149, balance());
        assertFalse(profile.isUnlocked("bird:guardian"));
        assertEquals(0, profile.statistics.coinsSpent);
        assertFalse(progression.isDirty());
        assertEquals(0, saves);
        assertSame(ProgressionOutcome.EMPTY, result.outcome());
    }

    @Test
    void anIdTheContentDoesNotKnowIsRefused() {
        give(1000);
        PurchaseResult result = manager.purchase(profile, "bird:phoenix", content);

        assertEquals(PurchaseStatus.UNKNOWN_ID, result.status());
        assertEquals(-1, result.cost());
        assertEquals(1000, balance());
        assertEquals(0, saves);
    }

    @Test
    void somethingAlreadyOwnedIsRefused() {
        give(1000);
        PurchaseResult result = manager.purchase(profile, "bird:classic", content);

        assertEquals(PurchaseStatus.ALREADY_OWNED, result.status());
        assertEquals(1000, balance());
        assertEquals(0, saves);
    }

    @Test
    void somethingWithNoPriceIsNotForSale() {
        give(10_000);
        profile.statistics.recordBossClear("void");
        // Every cosmetic is earned, never bought (D13): the shop has no line for it.
        PurchaseResult result = manager.purchase(profile, "cosmetic:classic:voidglass", content);

        assertEquals(PurchaseStatus.NOT_FOR_SALE, result.status());
        assertEquals(-1, result.cost());
        assertEquals(10_000, balance());
        assertFalse(profile.isUnlocked("cosmetic:classic:voidglass"));
    }

    @Test
    void theShopListsUnownedPricedThingsCheapestFirst() {
        give(150);
        List<UnlockManager.Offer> offers = manager.offers(profile, content);

        assertFalse(offers.isEmpty());
        long previous = Long.MIN_VALUE;
        for (UnlockManager.Offer offer : offers) {
            assertTrue(offer.cost() >= previous, "offers are sorted by price");
            previous = offer.cost();
            assertFalse(profile.isUnlocked(offer.id()), "an owned id is not on sale");
            assertEquals(offer.cost() <= 150, offer.affordable());
        }
        assertEquals("feature:seeded_runs", offers.get(0).id(), "the cheapest line ships at 100");
        manager.purchase(profile, "feature:seeded_runs", content);
        assertFalse(manager.offers(profile, content).stream()
                .anyMatch(offer -> "feature:seeded_runs".equals(offer.id())),
                "a bought feature leaves the shop");
    }

    @Test
    void buyingATreeOpensItsNodes() {
        give(120);
        assertFalse(UpgradeManager.isAvailable(profile, "coin_purse_1", content));
        PurchaseResult result = manager.purchase(profile, "tree:economy", content);

        assertTrue(result.ok());
        assertTrue(UpgradeManager.isAvailable(profile, "coin_purse_1", content),
                "tree:economy is what gates the economy nodes (E21)");
    }
}
