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
 * The upgrade trees (M4), driven headlessly through the input queue and the loop.
 *
 * <p>The screen is where coins become physics, so that is what is asserted: the three trees are
 * tabs and a locked one says how it opens; the nodes are laid out by tier and a node whose
 * prerequisite is missing says which one; a level that can be paid for moves the wallet, the card
 * and the live stat panel in the same tick; and one that cannot changes nothing at all.
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

    private long coins() {
        return Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
    }

    private void credit(long amount) {
        Wallet.of(profile).add(PlayerProfile.CURRENCY_COINS, amount);
    }

    @Test
    void theThreeTreesAreTabsAndALockedOneShowsItsCondition() {
        open();
        assertEquals(3, screen.tabBar().size(), "flight, economy and forge");
        assertEquals("flight", screen.treeId(), "a fresh profile lands on the tree it owns");
        assertTrue(screen.tabBar().tabs().get(0).isEnabled(), "flight is unlocked by default");
        assertFalse(screen.tabBar().tabs().get(1).isEnabled(), "economy is earned");
        assertFalse(screen.tabBar().tabs().get(2).isEnabled(), "forge is earned");
        assertEquals("", screen.treeLockedText(), "the open tree is not locked");

        // A locked tab is still clickable: reading why it is locked is the reason to go there.
        double tabWidth = screen.tabBar().width() / screen.tabBar().size();
        clickAt(screen.tabBar().x() + 1.5 * tabWidth, screen.tabBar().centerY());
        assertEquals("economy", screen.treeId());
        assertEquals(strings.format(StringKey.UPGRADES_TREE_LOCKED,
                        strings.format(StringKey.UNLOCK_LEVEL, 3)),
                screen.treeLockedText(),
                "the cheapest path to the economy tree is level 3 (E18)");
        assertTrue(screen.nodeGrid().cards().stream().allMatch(CardGrid.Card::isLocked),
                "no node of a locked tree can be bought");
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
        click(feather);
        assertTrue(screen.detailLines().contains(
                        strings.format(StringKey.UPGRADES_PER_LEVEL, gravity)),
                () -> "the detail panel carries the full phrase: " + screen.detailLines());
        assertEquals("50", feather.badge(), "and the price of the next level");
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
     * is earned the node can buy nothing, so the card says so and the click takes no coins.
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
        long before = coins();
        click(hardTier);
        assertEquals(before, coins(), "the click cannot spend 400 coins on nothing");
        assertEquals(0, profile.upgradeLevel("hard_tier_1"));
    }

    @Test
    void buyingANodeMovesTheWalletTheCardAndTheLiveStats() {
        credit(200);
        open();
        assertEquals(ProgressionText.number(1800), screen.statRow(StatId.GRAVITY).value(),
                "the live panel starts at the classic gravity");

        long toastsBefore = toasts.pushedCount();
        click(screen.nodeGrid().card("feather_1"));
        assertEquals(1, profile.upgradeLevel("feather_1"), "the level was raised");
        assertEquals(150, coins(), "the first level costs 50");
        assertTrue(saves > 0, "a purchase is written to the disk at once (D15)");
        assertTrue(toasts.pushedCount() > toastsBefore, "and raises a toast");

        CardGrid.Card feather = screen.nodeGrid().card("feather_1");
        assertTrue(feather.subtitle().contains(strings.format(StringKey.UPGRADES_LEVEL, 1, 3)));
        assertEquals("120", feather.badge(), "the badge is the next level's price");
        assertEquals(ProgressionText.number(1746), screen.statRow(StatId.GRAVITY).value(),
                "and the physics the player is about to fly moved with it");
        assertEquals(ProgressionText.number(
                        RunLoadout.previewStats(profile, content).resolve(StatId.GRAVITY)),
                screen.statRow(StatId.GRAVITY).value(),
                "the panel is the run's own stat sheet, not a second formula");

        // The node it opens is now buyable, which is the point of a tree.
        assertFalse(screen.nodeGrid().card("slim_frame_1").isLocked());
    }

    @Test
    void aNodeThatCannotBePaidForChangesNothing() {
        credit(10);
        open();
        long toastsBefore = toasts.pushedCount();
        click(screen.nodeGrid().card("feather_1"));
        assertEquals(0, profile.upgradeLevel("feather_1"), "nothing was bought");
        assertEquals(10, coins(), "and nothing was spent");
        assertEquals(0, saves, "a refused purchase writes nothing");
        assertTrue(toasts.pushedCount() > toastsBefore, "the refusal is explained");
        assertEquals(ProgressionText.number(1800), screen.statRow(StatId.GRAVITY).value());
        // The node is open -- no padlock -- but it is dimmed, because the wallet cannot pay for it.
        assertFalse(screen.nodeGrid().card("feather_1").isLocked());
        assertTrue(screen.nodeGrid().card("feather_1").isDimmed());
        assertTrue(screen.detailLines().contains(strings.format(StringKey.SHOP_PRICE, 50)
                        + "  (" + strings.get(StringKey.SHOP_CANNOT_AFFORD) + ")"),
                () -> "the detail panel says the coins are missing: " + screen.detailLines());
    }

    @Test
    void aNodeWithAMissingPrerequisiteIsRefusedEvenWithTheCoins() {
        credit(5000);
        open();
        click(screen.nodeGrid().card("slim_frame_1"));
        assertEquals(0, profile.upgradeLevel("slim_frame_1"));
        assertEquals(5000, coins(), "a refusal never debits");
    }

    @Test
    void aMaxedNodeSaysSoAndStopsCharging() {
        credit(5000);
        open();
        for (int i = 0; i < 3; i++) {
            click(screen.nodeGrid().card("feather_1"));
        }
        assertEquals(3, profile.upgradeLevel("feather_1"), "three levels, three prices");
        assertEquals(5000 - 50 - 120 - 250, coins());
        assertEquals(strings.get(StringKey.UPGRADES_MAXED),
                screen.nodeGrid().card("feather_1").badge());
        long before = coins();
        click(screen.nodeGrid().card("feather_1"));
        assertEquals(before, coins(), "a maxed node cannot be bought again");
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
}
