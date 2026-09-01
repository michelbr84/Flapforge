package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UnlockManager;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.ShopScreen;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The shop (M4), driven headlessly through the input queue and the loop.
 *
 * <p>The list is derived from the content — everything with a {@code purchase} branch the profile
 * does not own — so the assertions are about that rule and about the money: the four tabs group
 * what the rule produces, the cheapest offer of a tab comes first, a purchase that can be paid for
 * moves the wallet and the profile and drops out of the list, and one that cannot changes nothing.
 */
class ShopScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    private ManualClock clock;
    private InputQueue input;
    private Viewport viewport;
    private ScreenManager screens;
    private GameLoop loop;
    private Strings strings;
    private GameContent content;
    private PlayerProfile profile;
    private UnlockManager unlocks;
    private ToastLayer toasts;
    private ShopScreen screen;
    private int saves;

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
        unlocks = new UnlockManager(progression, () -> saves++);
        toasts = new ToastLayer();
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
    }

    private void open() {
        screen = new ShopScreen(screens, strings, content, profile, unlocks, toasts);
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

    private void openTab(int index) {
        double tabWidth = screen.tabBar().width() / screen.tabBar().size();
        clickAt(screen.tabBar().x() + (index + 0.5) * tabWidth, screen.tabBar().centerY());
        assertEquals(index, screen.tabBar().selectedIndex());
    }

    private long coins() {
        return Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
    }

    private void credit(long amount) {
        Wallet.of(profile).add(PlayerProfile.CURRENCY_COINS, amount);
    }

    @Test
    void theBirdsTabListsEveryBirdForSaleCheapestFirst() {
        open();
        assertEquals(4, screen.tabBar().size(), "birds, abilities, worlds, features");
        assertEquals(ShopScreen.TAB_BIRDS, screen.tabBar().selectedId());
        List<ShopScreen.Offer> offers = screen.offers();
        assertEquals(6, offers.size(), "six of the seven birds carry a price");
        assertEquals("bird:guardian", offers.get(0).id(), "the cheapest bird comes first");
        assertEquals(150, offers.get(0).cost());
        assertEquals("bird:forge", offers.get(offers.size() - 1).id());
        assertNull(screen.offer("bird:classic"), "an owned bird is not for sale");
        assertEquals("150", screen.offerGrid().cards().get(0).badge());
        assertEquals(strings.get(StringKey.SHOP_TAB_BIRDS),
                screen.offerGrid().cards().get(0).subtitle());
    }

    @Test
    void affordabilityIsShownPerOffer() {
        credit(200);
        open();
        assertFalse(screen.offerGrid().card("bird:guardian").isDimmed(), "150 of 200 is payable");
        assertFalse(screen.offerGrid().card("bird:heavy").isDimmed(), "200 of 200 is payable");
        assertTrue(screen.offerGrid().card("bird:swift").isDimmed(), "300 of 200 is not");
        assertFalse(screen.offerGrid().card("bird:swift").isLocked(),
                "an offer is never padlocked: it is for sale, just not affordable yet");
        assertTrue(screen.offerGrid().card("bird:swift").tooltip()
                        .contains(strings.get(StringKey.SHOP_CANNOT_AFFORD)),
                () -> screen.offerGrid().card("bird:swift").tooltip());
    }

    @Test
    void buyingAnOfferMovesTheWalletAndDropsItFromTheList() {
        credit(200);
        open();
        long toastsBefore = toasts.pushedCount();
        click(screen.offerGrid().card("bird:guardian"));

        assertTrue(profile.isUnlocked(BirdDef.NAMESPACE + "guardian"), "the bird was granted");
        assertEquals(50, coins(), "the price left the wallet");
        assertTrue(saves > 0, "the purchase was written to the disk at once (D15)");
        assertTrue(toasts.pushedCount() > toastsBefore, "and raised a toast");
        assertNull(screen.offer("bird:guardian"), "an owned bird leaves the shop");
        assertEquals(5, screen.offers().size());
        assertEquals(50, screen.walletDisplay().amount());
    }

    @Test
    void anOfferThatCannotBePaidForChangesNothing() {
        credit(10);
        open();
        long toastsBefore = toasts.pushedCount();
        click(screen.offerGrid().card("bird:guardian"));
        assertFalse(profile.isUnlocked(BirdDef.NAMESPACE + "guardian"), "nothing was granted");
        assertEquals(10, coins(), "and nothing was spent");
        assertEquals(0, saves, "a refused purchase writes nothing");
        assertTrue(toasts.pushedCount() > toastsBefore, "the refusal is explained");
        assertNotNull(screen.offer("bird:guardian"), "the offer stays where it was");
    }

    @Test
    void theOtherTabsCarryTheRestOfTheShopAndSayWhatIsNotPlayableYet() {
        open();
        openTab(1);
        assertEquals(7, screen.offers().size(), "seven abilities carry a price");
        assertEquals("ability:shield", screen.offers().get(0).id());
        assertFalse(content.playable(ContentKind.ABILITY));
        assertTrue(screen.offerGrid().card("ability:shield").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M5")),
                () -> screen.offerGrid().card("ability:shield").subtitle());

        openTab(2);
        assertEquals(4, screen.offers().size(), "the four worlds behind Green Fields");
        assertEquals("world:wind_valley", screen.offers().get(0).id());
        assertTrue(screen.offerGrid().card("world:wind_valley").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M7")),
                () -> screen.offerGrid().card("world:wind_valley").subtitle());

        openTab(3);
        assertEquals(4, screen.offers().size(), "two trees and two features");
        assertEquals("feature:seeded_runs", screen.offers().get(0).id(), "cheapest first");
        assertNotNull(screen.offer("tree:economy"));
        assertNotNull(screen.offer("feature:modifiers"));
        // E19: both features are buyable in M4 and read by nothing until M6 and M9, so the card
        // says which milestone starts using them rather than presenting a working switch.
        assertFalse(content.playable(ContentKind.FEATURE, "modifiers"));
        assertFalse(content.playable(ContentKind.FEATURE, "seeded_runs"));
        assertTrue(screen.offerGrid().card("feature:modifiers").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M6")),
                () -> screen.offerGrid().card("feature:modifiers").subtitle());
        assertTrue(screen.offerGrid().card("feature:seeded_runs").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M9")),
                () -> screen.offerGrid().card("feature:seeded_runs").subtitle());
        assertEquals(strings.get(StringKey.UPGRADES_TITLE),
                screen.offerGrid().card("tree:economy").subtitle(),
                "an upgrade tree works today, so its card carries no milestone note");
    }

    @Test
    void aTabWithNothingLeftSaysSo() {
        for (String id : List.of("bird:swift", "bird:heavy", "bird:guardian", "bird:gambler",
                "bird:mystic", "bird:forge")) {
            profile.unlock(id);
        }
        open();
        assertEquals(0, screen.offers().size());
        assertEquals(strings.get(StringKey.SHOP_EMPTY), screen.emptyText());
        assertTrue(screen.detailLines().contains(strings.get(StringKey.SHOP_EMPTY)));
    }

    @Test
    void aFeaturePurchaseUnlocksTheFeatureAndTheTreeBehindIt() {
        credit(1000);
        open();
        openTab(3);
        click(screen.offerGrid().card("tree:economy"));
        assertTrue(profile.isUnlocked("tree:economy"), "the tree is a purchase like any other");
        assertEquals(880, coins());
        click(screen.offerGrid().card("feature:modifiers"));
        assertTrue(profile.isUnlocked("feature:modifiers"));
        assertEquals(730, coins());
        assertNull(screen.offer("tree:economy"));
        assertNull(screen.offer("feature:modifiers"));
    }

    @Test
    void theCardsCarryEveryOfferOfTheOpenTabAndNoOther() {
        open();
        for (CardGrid.Card card : screen.offerGrid().cards()) {
            assertNotNull(screen.offer(card.id()), card.id());
            assertTrue(card.id().startsWith("bird:") || card.id().startsWith("cosmetic:"),
                    card.id());
        }
        assertEquals(screen.offers().size(), screen.offerGrid().size());
    }
}
