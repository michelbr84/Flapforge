package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.PurchaseResult;
import io.github.michelbr84.flapforge.progression.PurchaseStatus;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import io.github.michelbr84.flapforge.ui.screens.UpgradeTreeScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Forge (M13), driven headlessly through the input queue and the loop.
 *
 * <p>Buying is deliberate here, so that is what is asserted: the three trees are tabs, a locked
 * one says its price and is bought from the panel's call to action, and a node is bought by
 * selecting it and pressing that call to action — one press on a card never spends a coin. What
 * the purchases move is the same triple as ever: the wallet, the card and the live stat panel,
 * in the same tick; and every refusal — no coins, missing prerequisite, maxed, a grant already
 * owned — moves nothing at all.
 */
class UpgradeTreeScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    private ManualClock clock;
    private InputQueue input;
    private Viewport viewport;
    private ScreenManager screens;
    private GameLoop loop;
    private Strings strings;
    private GameContent content;
    private PlayerProfile profile;
    private UpgradeManager upgrades;
    private ToastLayer toasts;
    private UpgradeTreeScreen screen;
    private int saves;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);
        strings = Strings.load("en");
        Strings.use(strings);
        content = GameContent.load();
        FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);
        profile = PlayerProfile.fresh(time.epochMillis()).normalize();
        ProgressionManager progression = new ProgressionManager(time,
                ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
        upgrades = new UpgradeManager(progression, () -> saves++);
        toasts = new ToastLayer();
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
    }

    private void open() {
        screen = new UpgradeTreeScreen(screens, strings, content, profile, upgrades, toasts);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            clock.advance(Playfield.TICK_NS);
            loop.frame();
        }
    }

    private void tap(int keyCode) {
        input.offer(new RawInput.KeyDown(keyCode, stamp++));
        input.offer(new RawInput.KeyUp(keyCode, stamp++));
        ticks(1);
    }

    private void click(UiNode node) {
        clickAt(node.centerX(), node.centerY());
    }

    private void clickAt(double x, double y) {
        Vec2 w = viewport.toWindow(x, y);
        int wx = (int) Math.round(w.x());
        int wy = (int) Math.round(w.y());
        input.offer(new RawInput.MouseMove(wx, wy));
        input.offer(new RawInput.MouseDown(Keys.BUTTON_LEFT, wx, wy));
        input.offer(new RawInput.MouseUp(Keys.BUTTON_LEFT, wx, wy));
        ticks(1);
    }

    /** Clicks one tree tab, locked or not: reading a locked tree is why it stays clickable. */
    private void clickTab(int index) {
        double tabWidth = screen.tabBar().width() / screen.tabBar().size();
        clickAt(screen.tabBar().x() + (index + 0.5) * tabWidth, screen.tabBar().centerY());
    }

    private long coins() {
        return Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
    }

    private void credit(long amount) {
        Wallet.of(profile).add(PlayerProfile.CURRENCY_COINS, amount);
    }

    @Test
    void theThreeTreesAreTabsAndALockedOneShowsItsPrice() {
        open();
        assertEquals(3, screen.tabBar().size(), "flight, economy and forge");
        assertEquals("flight", screen.treeId(), "a fresh profile lands on the tree it owns");
        assertTrue(screen.tabBar().tabs().get(0).isEnabled(), "flight is unlocked by default");
        assertTrue(screen.tabBar().tabs().get(1).isEnabled(),
                "a locked tab stays enabled: reading why it is locked is the reason to go there");
        assertTrue(screen.tabBar().tabs().get(2).isEnabled());
        assertEquals("", screen.treeLockedText(), "the open tree is not locked");

        clickTab(1);
        assertEquals("economy", screen.treeId());
        assertEquals(strings.format(StringKey.UPGRADES_TREE_LOCKED,
                        strings.format(StringKey.SHOP_PRICE, 120)),
                screen.treeLockedText(),
                "the tree is for sale here, so the lock sentence carries the price");
        assertTrue(screen.nodeGrid().cards().stream().allMatch(CardGrid.Card::isLocked),
                "no node of a locked tree can be bought");
        assertEquals(strings.format(StringKey.UPGRADES_CTA_UNLOCK_TREE, "120"),
                screen.ctaButton().text(), "and the panel sells the tree itself");
    }

    @Test
    void nodesAreLaidOutByTierAndSayWhichPrerequisiteIsMissing() {
        open();
        assertEquals(6, screen.nodeGrid().size(), "the flight tree ships six nodes");
        CardGrid.Card feather = screen.nodeGrid().card("feather_1");
        CardGrid.Card slim = screen.nodeGrid().card("slim_frame_1");
        CardGrid.Card updraft = screen.nodeGrid().card("updraft_1");
        assertNotNull(feather);
        assertNotNull(slim);
        assertNotNull(updraft);
        assertTrue(feather.y() < slim.y(), "tier 1 sits above tier 2");
        assertTrue(slim.y() < updraft.y(), "tier 2 sits above tier 3");

        assertFalse(feather.isLocked(), "a tier-1 node has no prerequisite");
        assertTrue(slim.isLocked(), "slim_frame_1 needs feather_1 at level 1");
        assertTrue(slim.tooltip().contains(strings.format(StringKey.UPGRADES_NEEDS,
                        ProgressionText.name(strings, ContentKind.UPGRADE, "feather_1"))),
                () -> "the tooltip names the missing node: " + slim.tooltip());
        String gravity = ProgressionText.effect(strings, StatId.GRAVITY,
                io.github.michelbr84.flapforge.gameplay.stats.StatOp.PERCENT_ADD, -0.03);
        assertTrue(feather.subtitle().contains(strings.format(StringKey.UPGRADES_LEVEL, 0, 3)),
                () -> "the card shows level and maximum: " + feather.subtitle());
        // The card carries the short form because the badge column clips it; the "per level"
        // suffix is the first thing to be cut off mid-word, and the detail panel repeats it.
        assertTrue(feather.subtitle().contains(gravity),
                () -> "and what one level does: " + feather.subtitle());
        assertFalse(feather.subtitle().contains(
                        strings.format(StringKey.UPGRADES_PER_LEVEL, gravity)),
                () -> "the card does not repeat the suffix it cannot fit: " + feather.subtitle());

        // Selecting is not buying: the detail panel turns, the wallet stands still.
        click(feather);
        assertTrue(screen.detailLines().contains(
                        strings.format(StringKey.UPGRADES_PER_LEVEL, gravity)),
                () -> "the detail panel carries the full phrase: " + screen.detailLines());
        assertEquals(0, profile.upgradeLevel("feather_1"), "a selection bought nothing");
        assertEquals(0, coins(), "and spent nothing");
        assertEquals("50", feather.badge(), "the card still shows the next level's price");
        assertEquals(strings.format(StringKey.UPGRADES_CTA_LEVEL, "50"),
                screen.ctaButton().text(), "and the call to action is the buy, with the price");
        assertTrue(screen.ctaButton().isEnabled());
    }

    /**
     * E19, now that M5 landed: seven of the eighteen nodes sell ability cooldowns, durations,
     * shield charges, revives or a slot. Until M5 they carried an "Arrives in M5" note because no
     * system read them; the abilities exist now, so the note must be gone — an honest screen says
     * "later" only while it is still true.
     */
    @Test
    void theAbilityNodesNoLongerCarryAMilestoneNote() {
        open();
        assertTrue(content.playable(ContentKind.ABILITY), "M5 turned the ability system on");
        String soon = strings.format(StringKey.COMMON_SOON, "M5");
        CardGrid.Card quickRecharge = screen.nodeGrid().card("quick_recharge_1");
        assertNotNull(quickRecharge);
        assertFalse(quickRecharge.subtitle().contains(soon),
                () -> "the ability cooldown node works now: " + quickRecharge.subtitle());
        assertFalse(screen.statRow(StatId.ABILITY_COOLDOWN_MULT).label().contains(soon),
                () -> "and so does its stat row: "
                        + screen.statRow(StatId.ABILITY_COOLDOWN_MULT).label());
        assertFalse(screen.nodeGrid().card("feather_1").subtitle().contains(soon));
        assertFalse(screen.statRow(StatId.GRAVITY).label().contains(soon));
    }

    /**
     * {@code hard_tier_1} grants only {@code tier:hard}, which is also earned by playing. Once it
     * is earned the node can buy nothing, so the card says so and the call to action — not the
     * click — is switched off, because there is no purchase to make.
     */
    @Test
    void aNodeWhoseGrantIsAlreadyOwnedIsMarkedAndCannotBeBought() {
        credit(5000);
        profile.unlock("tree:economy");
        profile.upgrades.put("coin_purse_1", 1);
        open();
        tap(Keys.RIGHT);
        assertEquals("economy", screen.treeId());
        CardGrid.Card hardTier = screen.nodeGrid().card("hard_tier_1");
        assertNotNull(hardTier);
        assertEquals("400", hardTier.badge(), "while tier:hard is not owned it is a normal buy");

        profile.unlock("tier:hard");
        screen.refreshState();
        assertEquals(strings.get(StringKey.UPGRADES_ALREADY_OWNED), hardTier.badge());
        click(hardTier);
        assertFalse(screen.ctaButton().isEnabled(),
                "a purchase that would change nothing has no call to action");
        assertEquals(strings.get(StringKey.UPGRADES_ALREADY_OWNED),
                screen.ctaButton().text());
        long before = coins();
        click(screen.ctaButton());
        assertEquals(before, coins(), "the disabled call to action cannot spend 400 coins");
        assertEquals(0, profile.upgradeLevel("hard_tier_1"));
    }

    @Test
    void buyingANodeMovesTheWalletTheCardAndTheLiveStats() {
        credit(200);
        open();
        assertEquals(ProgressionText.number(1800), screen.statRow(StatId.GRAVITY).value(),
                "the live panel starts at the classic gravity");

        // Select first: the card press only turns the panel.
        CardGrid.Card feather = screen.nodeGrid().card("feather_1");
        click(feather);
        assertEquals(0, profile.upgradeLevel("feather_1"));
        assertEquals(200, coins(), "selection is free");

        long toastsBefore = toasts.pushedCount();
        click(screen.ctaButton());
        assertEquals(1, profile.upgradeLevel("feather_1"), "the level was raised");
        assertEquals(150, coins(), "the first level costs 50");
        assertTrue(saves > 0, "a purchase is written to the disk at once (D15)");
        assertTrue(toasts.pushedCount() > toastsBefore, "and raises a toast");

        assertTrue(feather.subtitle().contains(strings.format(StringKey.UPGRADES_LEVEL, 1, 3)));
        assertEquals("120", feather.badge(), "the badge is the next level's price");
        assertEquals(ProgressionText.number(1746), screen.statRow(StatId.GRAVITY).value(),
                "and the physics the player is about to fly moved with it");
        assertEquals(ProgressionText.number(
                        RunLoadout.previewStats(profile, content).resolve(StatId.GRAVITY)),
                screen.statRow(StatId.GRAVITY).value(),
                "the panel is the run's own stat sheet, not a second formula");
        assertEquals(UpgradeTreeScreen.statPips(
                        RunLoadout.previewStats(profile, content).resolve(StatId.GRAVITY),
                        StatId.GRAVITY),
                screen.statRow(StatId.GRAVITY).pips(),
                "the pip fill is the same normalised measure the row shows");

        // The node it opens is now buyable, which is the point of a tree.
        assertFalse(screen.nodeGrid().card("slim_frame_1").isLocked());
    }

    @Test
    void aNodeThatCannotBePaidForChangesNothing() {
        credit(10);
        open();
        long toastsBefore = toasts.pushedCount();
        click(screen.nodeGrid().card("feather_1"));
        // The route is valid and the call stays pressable: the refusal is how "not yet" is said.
        assertTrue(screen.ctaButton().isEnabled());
        click(screen.ctaButton());
        assertEquals(0, profile.upgradeLevel("feather_1"), "nothing was bought");
        assertEquals(10, coins(), "and nothing was spent");
        assertEquals(0, saves, "a refused purchase writes nothing");
        assertTrue(toasts.pushedCount() > toastsBefore, "the refusal is explained");
        assertEquals(ProgressionText.number(1800), screen.statRow(StatId.GRAVITY).value());
        // The node is open -- no padlock -- but it is dimmed, because the wallet cannot pay for it.
        assertFalse(screen.nodeGrid().card("feather_1").isLocked());
        assertTrue(screen.nodeGrid().card("feather_1").isDimmed());
        assertTrue(screen.detailLines().contains(strings.get(StringKey.UPGRADES_NO_COINS)),
                () -> "the panel says the coins are missing: " + screen.detailLines());
        assertEquals(strings.format(StringKey.UPGRADES_CTA_LEVEL, "50"),
                screen.ctaButton().text(), "the call still names the price it could not pay");
    }

    @Test
    void aNodeWithAMissingPrerequisiteIsRefusedEvenWithTheCoins() {
        credit(5000);
        open();
        CardGrid.Card slim = screen.nodeGrid().card("slim_frame_1");
        click(slim);
        assertTrue(slim.isLocked(), "the padlock says the prerequisite is missing");
        assertFalse(screen.ctaButton().isEnabled(), "so there is no purchase to press");
        assertEquals(strings.format(StringKey.UPGRADES_NEEDS,
                        ProgressionText.name(strings, ContentKind.UPGRADE, "feather_1")),
                screen.ctaButton().text(), "and the call names the missing node instead");
        click(screen.ctaButton());
        assertEquals(0, profile.upgradeLevel("slim_frame_1"));
        assertEquals(5000, coins(), "a refusal never debits");
    }

    /**
     * The refusal the call to action surfaces is the purchase route's own answer, not the
     * button's disabled flag. Driven directly — the same two routes {@code activateCta} and
     * {@code unlockTree} call — a wallet that cannot pay is refused with a status, and neither
     * the node route nor the tree route moves a coin or writes a save.
     */
    @Test
    void thePurchaseRoutesRefuseAWalletThatCannotPayDirectly() {
        credit(10);
        open();
        long before = coins();
        click(screen.nodeGrid().card("feather_1"));
        PurchaseResult node = upgrades.buy(profile, screen.currentNodeId(), content);
        assertEquals(PurchaseStatus.INSUFFICIENT_FUNDS, node.status(),
                "the node route refuses a wallet that cannot pay");
        assertEquals(before, coins(), "the refused buy debits nothing");
        assertEquals(0, profile.upgradeLevel("feather_1"));
        assertEquals(0, saves, "a refused route writes nothing");

        clickTab(1);
        assertEquals("economy", screen.treeId());
        PurchaseResult tree = upgrades.buyTree(profile, screen.treeId(), content);
        assertEquals(PurchaseStatus.INSUFFICIENT_FUNDS, tree.status(),
                "the tree route refuses the same wallet");
        assertEquals(before, coins(), "the refused unlock debits nothing");
        assertTrue(UpgradeManager.isTreeLocked(profile, "economy"), "the tree is still shut");
        assertEquals(0, saves, "and still nothing is written");
    }

    /**
     * A node behind a still-locked tree has no route at all: driven directly — the call the
     * CTA's buy path makes — it is refused as {@code TREE_LOCKED} with the wallet untouched,
     * even with the price in it.
     */
    @Test
    void theNodeRouteRefusesANodeBehindAStillLockedTree() {
        credit(5000);
        open();
        clickTab(1);
        assertEquals("economy", screen.treeId());
        assertNotNull(screen.nodeGrid().card("coin_purse_1"));
        long before = coins();
        PurchaseResult locked = upgrades.buy(profile, screen.currentNodeId(), content);
        assertEquals(PurchaseStatus.TREE_LOCKED, locked.status(),
                "the shut tree is the route's own refusal");
        assertEquals(before, coins(), "a refused buy debits nothing");
        assertEquals(0, profile.upgradeLevel("coin_purse_1"));
        assertEquals(0, saves, "and writes nothing");
    }

    @Test
    void aMaxedNodeSaysSoAndStopsCharging() {
        credit(5000);
        open();
        click(screen.nodeGrid().card("feather_1"));
        for (int i = 0; i < 3; i++) {
            click(screen.ctaButton());
        }
        assertEquals(3, profile.upgradeLevel("feather_1"), "three levels, three prices");
        assertEquals(5000 - 50 - 120 - 250, coins());
        assertEquals(strings.get(StringKey.UPGRADES_MAXED),
                screen.nodeGrid().card("feather_1").badge());
        assertFalse(screen.ctaButton().isEnabled(), "there is no level left to sell");
        assertEquals(strings.get(StringKey.UPGRADES_CTA_MAXED), screen.ctaButton().text());
        long before = coins();
        click(screen.ctaButton());
        assertEquals(before, coins(), "a maxed node cannot be bought again");
    }

    /**
     * The economy tree is locked for a fresh profile and for sale for 120: the panel's call to
     * action sells it, the wallet is charged once, and the same press that opened the tree hands
     * the call back to the selected node.
     */
    @Test
    void aLockedTreeIsBoughtThroughTheCtaAndThenOpens() {
        open();
        clickTab(1);
        assertEquals("economy", screen.treeId());
        assertTrue(screen.nodeGrid().cards().stream().allMatch(CardGrid.Card::isLocked),
                "every node of the locked tree wears the padlock");
        assertTrue(screen.nodeGrid().cards().stream().allMatch(card -> card.badge().isEmpty()),
                "and none of them shows a price of its own");
        assertEquals(strings.format(StringKey.UPGRADES_CTA_UNLOCK_TREE, "120"),
                screen.ctaButton().text());
        assertTrue(screen.ctaButton().isEnabled());

        long toastsBefore = toasts.pushedCount();
        click(screen.ctaButton());
        assertEquals(0, coins(), "a refused unlock debits nothing");
        assertEquals(0, saves, "and writes nothing");
        assertTrue(toasts.pushedCount() > toastsBefore, "the refusal is explained");
        assertTrue(UpgradeManager.isTreeLocked(profile, "economy"), "the tree is still shut");
        assertTrue(screen.detailLines().contains(strings.get(StringKey.UPGRADES_NO_COINS)),
                () -> "the panel says the coins are missing: " + screen.detailLines());

        credit(120);
        screen.refreshState();
        assertFalse(screen.detailLines().contains(strings.get(StringKey.UPGRADES_NO_COINS)),
                "with the price in the wallet the red note stands down");
        click(screen.ctaButton());
        assertFalse(UpgradeManager.isTreeLocked(profile, "economy"), "the tree opened");
        assertEquals(0, coins(), "120 coins for the tree, to the coin");
        assertTrue(saves > 0, "and the unlock is written at once");
        assertEquals("", screen.treeLockedText());
        assertFalse(screen.nodeGrid().card("coin_purse_1").isLocked(),
                "the tree's nodes are buyable at once");
        assertEquals(strings.format(StringKey.UPGRADES_CTA_LEVEL, Long.toString(
                        content.upgrades().get("coin_purse_1").costOf(1))),
                screen.ctaButton().text(), "and the call to action is the selected node's again");
    }

    /** The forge tree sells for 900 — the second price the panel offers a locked tree for. */
    @Test
    void theForgeTreeSellsItselfForNineHundred() {
        credit(1000);
        open();
        clickTab(2);
        assertEquals("forge", screen.treeId());
        assertEquals(strings.format(StringKey.UPGRADES_TREE_LOCKED,
                        strings.format(StringKey.SHOP_PRICE, 900)), screen.treeLockedText());
        assertEquals(strings.format(StringKey.UPGRADES_CTA_UNLOCK_TREE, "900"),
                screen.ctaButton().text());
        click(screen.ctaButton());
        assertFalse(UpgradeManager.isTreeLocked(profile, "forge"), "the tree opened");
        assertEquals(100, coins(), "900 coins for the forge tree");
        assertTrue(saves > 0);
    }

    /**
     * A profile carried over from an older build can have earned a tree in play without owning
     * it: economy is any_of[level 3, purchase 120] and the evaluator never reports a purchase
     * branch as satisfied. Entering the Forge grants what play already paid for — and writes
     * the grant — before the panel offers the 120-coin shortcut for it.
     */
    @Test
    void enteringTheForgeGrantsATreeTheProfileAlreadyEarnedInPlay() {
        profile.level = 3;
        open();
        assertFalse(UpgradeManager.isTreeLocked(profile, "economy"),
                "the tree the profile earned in play is granted on entry");
        assertTrue(saves > 0, "the grant is written at once");
        assertEquals("flight", screen.treeId(), "the screen still opens on the tree it landed on");
        clickTab(1);
        assertEquals("economy", screen.treeId());
        assertEquals("", screen.treeLockedText(), "no lock sentence for a tree the player owns");
        assertFalse(screen.nodeGrid().card("coin_purse_1").isLocked(),
                "the earned tree's nodes are buyable at once");
    }

    /**
     * The pip bars normalise the resolved value over the stat's own clamp range, five segments,
     * rounded (M13 architecture decision) — the reference mock's literal fills are unreliable,
     * the range is not.
     */
    @Test
    void statPipsNormaliseTheValueOverTheStatsOwnRange() {
        double min = StatId.GRAVITY.min();
        double max = StatId.GRAVITY.max();
        assertTrue(min < max, "the stat under test has a range");
        assertEquals(0, UpgradeTreeScreen.statPips(min, StatId.GRAVITY),
                "the floor of the range fills no pip");
        assertEquals(5, UpgradeTreeScreen.statPips(max, StatId.GRAVITY),
                "the ceiling fills all five");
        assertEquals(0, UpgradeTreeScreen.statPips(min - 1000, StatId.GRAVITY),
                "below the range is clamped to none");
        assertEquals(5, UpgradeTreeScreen.statPips(max + 1000, StatId.GRAVITY),
                "above the range is clamped to all");
        assertEquals(2, UpgradeTreeScreen.statPips(min + 0.4 * (max - min), StatId.GRAVITY),
                "0.4 of the range is 2 of 5 pips");
        assertEquals(3, UpgradeTreeScreen.statPips(min + (max - min) / 2, StatId.GRAVITY),
                "half rounds up to 3");
    }

    @Test
    void theArrowsSwitchTabsOnceAnotherTreeIsUnlocked() {
        profile.unlock("tree:economy");
        open();
        assertTrue(screen.tabBar().tabs().get(1).isEnabled());
        assertEquals("flight", screen.treeId());
        tap(Keys.RIGHT);
        assertEquals("economy", screen.treeId(), "the tab bar owns the arrows while focused");
        assertNotNull(screen.nodeGrid().card("coin_purse_1"), "the economy nodes are shown");
        assertEquals("", screen.treeLockedText());
    }

    /** M2's live switch: the table reloads in place and the screen follows in the same tick. */
    @Test
    void theScreenFollowsTheLanguageInTheSameTick() {
        credit(200);
        open();
        click(screen.nodeGrid().card("feather_1"));
        assertEquals("en", strings.language());

        strings.reload("pt_BR");
        ticks(1);
        assertEquals("pt_BR", strings.language(), "the screen's table follows the switch");
        assertEquals(strings.format(StringKey.UPGRADES_CTA_LEVEL, "50"),
                screen.ctaButton().text(), "the call to action is the new words");
        assertTrue(screen.nodeGrid().card("feather_1").subtitle().contains(
                        strings.format(StringKey.UPGRADES_LEVEL, 0, 3)),
                () -> "so is the card: " + screen.nodeGrid().card("feather_1").subtitle());
        assertEquals(ProgressionText.statLabel(strings, StatId.GRAVITY),
                screen.statRow(StatId.GRAVITY).label(), "and so is the attribute summary");
    }
}
