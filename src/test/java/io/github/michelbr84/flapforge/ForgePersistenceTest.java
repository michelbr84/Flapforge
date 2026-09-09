package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.persistence.SaveManager;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.PurchaseStatus;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Forge's purchases across a restart (M13): a tree bought on the upgrade screen and a level
 * bought inside it are both written by the same atomic route the shop uses, so a profile reloaded
 * from the disk carries the tree unlock, the level and the spent coins, and a refused purchase
 * leaves neither the wallet nor the save file different.
 *
 * <p>Every write goes to a {@code @TempDir}: {@link #setUp()} proves the override took and
 * {@link #tearDown()} proves the real profile directory was neither created nor touched.
 */
class ForgePersistenceTest {

    /** The economy tree's unlock price, from {@code upgrades.json}. */
    private static final long ECONOMY_UNLOCK = 120;
    /** The forge tree's unlock price, from {@code upgrades.json}. */
    private static final long FORGE_UNLOCK = 900;
    /** The first level of {@code feather_1}, from {@code upgrades.json}. */
    private static final long FEATHER_FIRST_LEVEL = 50;

    @TempDir
    private Path home;

    /** The real profile directory as it was before the test: absent, or every byte of it. */
    private List<String> realHomeBefore;
    private GameContent content;
    private SaveManager save;
    private PlayerProfile profile;
    private UpgradeManager upgrades;

    @BeforeEach
    void setUp() {
        SavePaths.clearOverride();
        realHomeBefore = fingerprint(SavePaths.profileDir());
        SavePaths.override(home);
        assertEquals(home.toAbsolutePath().normalize(), SavePaths.profileDir(),
                "the profile directory must be the temporary one");

        content = GameContent.load();
        FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
        save = new SaveManager(new DirectExecutor(), time);
        save.load();
        profile = save.profile();
        upgrades = new UpgradeManager(
                new ProgressionManager(time, ProgressionManager.AchievementHook.NONE,
                        UnlockEvaluator.of(content)),
                save::save);
    }

    @AfterEach
    void tearDown() {
        SavePaths.clearOverride();
        assertEquals(realHomeBefore, fingerprint(SavePaths.profileDir()),
                "the real profile directory must be byte-for-byte what it was before the test");
    }

    /**
     * Everything about a directory a stray write would change: which files it holds, how large
     * they are and when they were last written. {@code List.of("<absent>")} for a directory that
     * does not exist, so "was absent, is still absent" and "was there, is untouched" are both one
     * comparison.
     *
     * @param dir the directory to read
     * @return the fingerprint
     */
    private static List<String> fingerprint(Path dir) {
        if (!Files.exists(dir)) {
            return List.of("<absent>");
        }
        List<Path> paths;
        try (var walk = Files.walk(dir)) {
            paths = walk.sorted().toList();
        } catch (IOException e) {
            throw new AssertionError("could not read " + dir, e);
        }
        List<String> lines = new ArrayList<>();
        for (Path path : paths) {
            try {
                lines.add(dir.relativize(path) + " " + Files.size(path) + " "
                        + Files.getLastModifiedTime(path));
            } catch (IOException e) {
                throw new AssertionError("could not read " + path, e);
            }
        }
        return lines;
    }

    @Test
    void aBoughtTreeSurvivesARestart() {
        earn(ECONOMY_UNLOCK);

        assertTrue(UpgradeManager.isTreeLocked(profile, "economy"), "the economy tree starts locked");
        assertEquals(PurchaseStatus.OK, upgrades.buyTree(profile, "economy", content).status());

        PlayerProfile reloaded = reload();
        assertFalse(UpgradeManager.isTreeLocked(reloaded, "economy"),
                "the tree unlock must survive a restart");
        assertEquals(0, Wallet.of(reloaded).balance(PlayerProfile.CURRENCY_COINS),
                "the coins spent on the tree must be gone from the reloaded wallet");
        assertTrue(reloaded.isUnlocked(UpgradeManager.treeUnlockId("economy")),
                "the unlock id is the same one the shop writes");
    }

    @Test
    void aBoughtLevelSurvivesARestart() {
        earn(FEATHER_FIRST_LEVEL);

        assertEquals(PurchaseStatus.OK, upgrades.buy(profile, "feather_1", content).status());

        PlayerProfile reloaded = reload();
        assertEquals(1, reloaded.upgradeLevel("feather_1"),
                "the level must survive a restart under its bare id");
        assertEquals(0, Wallet.of(reloaded).balance(PlayerProfile.CURRENCY_COINS));
    }

    @Test
    void aTreeAndALevelBoughtTogetherSurviveARestart() {
        earn(ECONOMY_UNLOCK + 80);
        assertEquals(PurchaseStatus.OK, upgrades.buyTree(profile, "economy", content).status());
        assertEquals(PurchaseStatus.OK, upgrades.buy(profile, "coin_purse_1", content).status());

        PlayerProfile reloaded = reload();
        assertFalse(UpgradeManager.isTreeLocked(reloaded, "economy"));
        assertEquals(1, reloaded.upgradeLevel("coin_purse_1"));
        assertEquals(0, Wallet.of(reloaded).balance(PlayerProfile.CURRENCY_COINS));
    }

    @Test
    void aTreeThatCannotBePaidForChangesNothingAndNothingIsWritten() {
        earn(ECONOMY_UNLOCK - 1);

        assertEquals(PurchaseStatus.INSUFFICIENT_FUNDS,
                upgrades.buyTree(profile, "economy", content).status());
        assertTrue(UpgradeManager.isTreeLocked(profile, "economy"));
        assertEquals(ECONOMY_UNLOCK - 1, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS),
                "a refused purchase must not touch the wallet");

        PlayerProfile reloaded = reload();
        assertTrue(UpgradeManager.isTreeLocked(reloaded, "economy"));
        assertEquals(ECONOMY_UNLOCK - 1, Wallet.of(reloaded).balance(PlayerProfile.CURRENCY_COINS));
    }

    @Test
    void buyingATreeTwiceIsRefusedTheSecondTime() {
        earn(ECONOMY_UNLOCK * 2);
        assertEquals(PurchaseStatus.OK, upgrades.buyTree(profile, "economy", content).status());
        assertEquals(PurchaseStatus.ALREADY_OWNED,
                upgrades.buyTree(profile, "economy", content).status());
        assertEquals(ECONOMY_UNLOCK, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS),
                "the second refusal must not charge twice");
    }

    @Test
    void theForgeTreeIsPricedFromItsOwnUnlockCondition() {
        assertEquals(ECONOMY_UNLOCK, upgrades.treeUnlockPrice("economy", content));
        assertEquals(FORGE_UNLOCK, upgrades.treeUnlockPrice("forge", content));
        assertFalse(upgrades.canAffordTreeUnlock(profile, "forge", content),
                "a fresh wallet cannot open the forge tree");
        earn(FORGE_UNLOCK);
        assertTrue(upgrades.canAffordTreeUnlock(profile, "forge", content));
    }

    @Test
    void aTreeEarnedByPlayIsNeverChargedFor() {
        profile.statistics.bossesCleared.add("wind_valley");
        earn(FORGE_UNLOCK);

        assertTrue(upgrades.isTreeEarned(profile, "forge", content),
                "clearing wind_valley is the other branch of the forge tree's condition");
        assertTrue(upgrades.canAffordTreeUnlock(profile, "forge", content),
                "an earned tree needs no coins");

        assertEquals(PurchaseStatus.OK, upgrades.buyTree(profile, "forge", content).status());

        assertEquals(FORGE_UNLOCK, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS),
                "the coin shortcut must not be charged for a tree already earned in play");
        assertFalse(UpgradeManager.isTreeLocked(profile, "forge"));

        PlayerProfile reloaded = reload();
        assertFalse(UpgradeManager.isTreeLocked(reloaded, "forge"),
                "the free grant must be written like any other unlock");
        assertEquals(FORGE_UNLOCK, Wallet.of(reloaded).balance(PlayerProfile.CURRENCY_COINS));
    }

    @Test
    void aTreeNobodyHasEarnedIsStillBoughtWithCoins() {
        earn(FORGE_UNLOCK);

        assertFalse(upgrades.isTreeEarned(profile, "forge", content),
                "a world never cleared leaves only the purchase branch");
        assertEquals(List.of(), upgrades.claimEarnedTrees(profile, content),
                "reconciling a profile that owes nothing grants nothing");
        assertTrue(UpgradeManager.isTreeLocked(profile, "forge"));

        assertEquals(PurchaseStatus.OK, upgrades.buyTree(profile, "forge", content).status());
        assertEquals(0, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS),
                "without the earned branch the price is the price");
    }

    @Test
    void anUnknownTreeIsNotForSale() {
        assertEquals(-1, upgrades.treeUnlockPrice("anvil", content));
        assertFalse(upgrades.canAffordTreeUnlock(profile, "anvil", content));
        assertEquals(PurchaseStatus.UNKNOWN_ID,
                upgrades.buyTree(profile, "anvil", content).status());
    }

    private void earn(long coins) {
        Wallet.of(profile).add(PlayerProfile.CURRENCY_COINS, coins);
    }

    /**
     * Writes the profile and reads it back through a brand-new manager, the way a restarted game
     * would.
     *
     * @return the profile as the disk has it
     */
    private PlayerProfile reload() {
        assertTrue(save.save(), "the write must succeed");
        SaveManager reader = new SaveManager(new DirectExecutor(), new FixedTimeSource(0));
        return reader.load().profile();
    }
}
