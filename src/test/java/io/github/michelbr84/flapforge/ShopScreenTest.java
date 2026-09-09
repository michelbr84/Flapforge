package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UnlockManager;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.CtaButton;
import io.github.michelbr84.flapforge.ui.component.SectionNav;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import io.github.michelbr84.flapforge.ui.screens.ShopScreen;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The shop (M4, rebuilt in M12), driven headlessly through the input queue and the loop.
 *
 * <p>The catalogue is derived from the content — everything with a {@code purchase} branch,
 * whether or not the profile owns it — so the assertions are about that rule and about the money:
 * the four tabs group what the rule produces, what is for sale comes first and cheapest first, a
 * purchase that can be paid for moves the wallet and the profile and turns its card into an owned
 * one, and one that cannot changes nothing.
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
    private UpgradeManager upgrades;
    private ToastLayer toasts;
    private ShopScreen screen;
    private int saves;
    private long stamp;

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
        upgrades = new UpgradeManager(progression, () -> saves++);
        toasts = new ToastLayer();
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
    }

    private void open() {
        screen = new ShopScreen(screens, strings, content, profile, unlocks, upgrades, toasts);
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

    private void tap(int keyCode) {
        input.offer(new RawInput.KeyDown(keyCode, stamp++));
        input.offer(new RawInput.KeyUp(keyCode, stamp++));
        ticks(1);
    }

    private void wheel(int rotation) {
        Vec2 w = viewport.toWindow(Playfield.WIDTH / 2.0,
                (ShopScreen.GRID_TOP + ShopScreen.GRID_BOTTOM) / 2.0);
        input.offer(new RawInput.MouseMove((int) Math.round(w.x()), (int) Math.round(w.y())));
        input.offer(new RawInput.Wheel(rotation));
        ticks(1);
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
        assertNull(screen.offer("bird:classic"),
                "the bird the profile starts with was never for sale, so it is not in the shop");
        assertEquals("150", screen.offerGrid().cards().get(0).badge());
        // The second line of a card that is not yours yet is the road that costs no coins, which
        // is the shop's honest answer to "why is this not mine": everything for sale is also
        // earnable, so nothing here is ever shut.
        assertEquals("Play 3 runs", screen.offerGrid().cards().get(0).subtitle());
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
    void buyingAnOfferMovesTheWalletAndMarksTheCardOwned() {
        credit(200);
        open();
        long toastsBefore = toasts.pushedCount();
        click(screen.offerGrid().card("bird:guardian"));

        assertTrue(profile.isUnlocked(BirdDef.NAMESPACE + "guardian"), "the bird was granted");
        assertEquals(50, coins(), "the price left the wallet");
        assertTrue(saves > 0, "the purchase was written to the disk at once (D15)");
        assertTrue(toasts.pushedCount() > toastsBefore, "and raised a toast");
        ShopScreen.Offer bought = screen.offer("bird:guardian");
        assertNotNull(bought, "an owned bird stays in the catalogue");
        assertFalse(bought.available(), "there is nothing left to sell about it");
        assertEquals(6, screen.offers().size(), "the catalogue does not shrink when you buy");
        assertEquals(strings.get(StringKey.COMMON_OWNED),
                screen.offerGrid().card("bird:guardian").badge(), "the price becomes a word");
        assertFalse(screen.offerGrid().card("bird:guardian").hasCoinBadge(),
                "an owned bird has no price");
        assertEquals("bird:guardian", screen.offers().get(screen.offers().size() - 1).id(),
                "and it sorts to the end, where a collection belongs");
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
        assertEquals(8, screen.offers().size(),
                "seven locked abilities carry a price, and the default one carries its level");
        assertEquals("ability:coin_magnet", screen.offers().get(0).id(),
                "cheapest first: M5 repriced the magnet down to what it measures (120)");
        assertTrue(content.playable(ContentKind.ABILITY), "M5 turned the ability system on");
        assertFalse(screen.offerGrid().card("ability:shield").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M5")),
                () -> "abilities work now: " + screen.offerGrid().card("ability:shield")
                        .subtitle());

        openTab(2);
        assertEquals(4, screen.offers().size(), "the four worlds behind Green Fields");
        assertEquals("world:wind_valley", screen.offers().get(0).id());
        assertFalse(screen.offerGrid().card("world:wind_valley").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M7")),
                () -> "worlds play now (M7): "
                        + screen.offerGrid().card("world:wind_valley").subtitle());

        openTab(3);
        assertEquals(7, screen.offers().size(),
                "two trees, two features and the three legendary modifiers (M6)");
        assertEquals("feature:seeded_runs", screen.offers().get(0).id(), "cheapest first");
        assertNotNull(screen.offer("tree:economy"));
        assertNotNull(screen.offer("feature:modifiers"));
        // M6: the three legendaries are the only modifiers that are not unlocked by default, and
        // §4 prices them at 300 coins each. The draft system exists, so they carry no "soon" note.
        assertNotNull(screen.offer("modifier:gold_rush"));
        assertNotNull(screen.offer("modifier:phoenix"));
        assertEquals(300, screen.offer("modifier:stormrider").cost());
        assertFalse(screen.offerGrid().card("modifier:stormrider").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M6")),
                () -> screen.offerGrid().card("modifier:stormrider").subtitle());
        // E19: a feature's card said which milestone starts using it rather than presenting a
        // working switch. M6 shipped the draft overlay and M9 the mode picker, so both features
        // are working switches now and neither card carries a note.
        assertTrue(content.playable(ContentKind.FEATURE, "modifiers"),
                "M6 turned the modifier drafts on");
        assertTrue(content.playable(ContentKind.FEATURE, "seeded_runs"),
                "M9 turned Seeded and Daily mode on");
        assertFalse(screen.offerGrid().card("feature:modifiers").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M6")),
                () -> screen.offerGrid().card("feature:modifiers").subtitle());
        assertFalse(screen.offerGrid().card("feature:seeded_runs").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M9")),
                () -> screen.offerGrid().card("feature:seeded_runs").subtitle());
        assertFalse(screen.offerGrid().card("tree:economy").subtitle()
                        .contains(strings.format(StringKey.COMMON_SOON, "M")),
                "an upgrade tree works today, so its card carries no milestone note");
        assertEquals("Reach level 3", screen.offerGrid().card("tree:economy").subtitle(),
                "it carries the road that costs no coins instead");
    }

    @Test
    void aTabWithNothingLeftSaysSoAndStillShowsTheCollection() {
        for (String id : List.of("bird:swift", "bird:heavy", "bird:guardian", "bird:gambler",
                "bird:mystic", "bird:forge")) {
            profile.unlock(id);
        }
        open();
        assertEquals(6, screen.offers().size(), "the catalogue is still the catalogue");
        for (ShopScreen.Offer offer : screen.offers()) {
            assertFalse(offer.available(), () -> offer.id() + " is owned");
            assertEquals(strings.get(StringKey.COMMON_OWNED),
                    screen.offerGrid().card(offer.id()).badge());
        }
        assertEquals(strings.get(StringKey.SHOP_EMPTY), screen.emptyText(),
                "with nothing left to sell the tab says so");
        assertFalse(screen.ctaButton().isEnabled(), "and there is nothing to press");
    }

    @Test
    void aFeaturePurchaseUnlocksTheFeatureAndTheTreeBehindIt() {
        credit(1000);
        open();
        openTab(3);
        click(screen.offerGrid().card("tree:economy"));
        assertTrue(profile.isUnlocked("tree:economy"), "the tree is a purchase like any other");
        assertEquals(880, coins());
        assertFalse(RunLoadout.allowOffers(profile, content),
                "before the purchase the next run cannot draft");
        click(screen.offerGrid().card("feature:modifiers"));
        assertTrue(profile.isUnlocked("feature:modifiers"));
        assertEquals(730, coins());
        assertFalse(screen.offer("tree:economy").available(), "both are owned now");
        assertFalse(screen.offer("feature:modifiers").available());
        // M6: buying it is the whole gate — the very next run opens its drafts (D11).
        assertTrue(RunLoadout.allowOffers(profile, content),
                "the purchase must turn the drafts on");
        assertTrue(RunLoadout.configFor(profile, content, 42L, RunMode.STANDARD).allowOffers());
        assertFalse(RunLoadout.availableModifiers(profile, content).isEmpty(),
                "and the run carries the cards the profile owns");
    }

    // ------------------------------------------------------------------ ability levels (M5, E3)

    @Test
    void theAbilitiesTabSellsTheNextLevelOfAnOwnedAbility() {
        credit(1000);
        open();
        openTab(1);
        ShopScreen.Offer level = screen.offer("ability:double_flap");
        assertNotNull(level, "the default ability is owned, so it is sold by the level");
        assertTrue(level.isAbilityLevel());
        assertEquals(2, level.level(), "level 1 came with the unlock, so level 2 is next");
        assertEquals(300, level.cost(), "data/abilities.json prices level 2 at 300");
        assertEquals(0, profile.abilityLevel("double_flap"), "no level entry was ever written");
        assertEquals(1, UpgradeManager.abilityLevelOwned(profile,
                        content.abilities().get("double_flap")),
                "yet an unlocked ability is owned at level 1: level 1 comes with the unlock");

        CardGrid.Card card = screen.offerGrid().card("ability:double_flap");
        assertNotNull(card);
        assertEquals("300", card.badge());
        assertTrue(card.subtitle().contains(strings.format(StringKey.ABILITY_LEVEL, 1, 3)),
                () -> "the card shows the owned level: " + card.subtitle());
        assertTrue(card.subtitle().contains(strings.format(StringKey.SHOP_ABILITY_CAP, 2)),
                () -> "and the E3 cap: " + card.subtitle());

        long toastsBefore = toasts.pushedCount();
        click(card);
        assertEquals(2, profile.abilityLevel("double_flap"), "the level was bought");
        assertEquals(700, coins(), "the price left the wallet");
        assertTrue(saves > 0, "and it was written at once (D15)");
        assertTrue(toasts.pushedCount() > toastsBefore, "the purchase raised a toast");
    }

    @Test
    void anAbilityAtTheCapIsShownAndNotSold() {
        credit(2000);
        open();
        openTab(1);
        click(screen.offerGrid().card("ability:double_flap"));
        assertEquals(2, profile.abilityLevel("double_flap"));
        assertEquals(PlayerProfile.DEFAULT_ABILITY_LEVEL_CAP, profile.abilityLevelCap);

        ShopScreen.Offer capped = screen.offer("ability:double_flap");
        assertNotNull(capped, "a capped ability stays on the card list, it just cannot be bought");
        assertFalse(capped.available());
        assertEquals(0, capped.level());
        CardGrid.Card card = screen.offerGrid().card("ability:double_flap");
        assertEquals(strings.get(StringKey.SHOP_ABILITY_CAPPED), card.badge());

        // Capped, it sorted to the end of the tab — the fourth row, below the grid's band — so
        // the click has to scroll it into view first, or it would miss and prove nothing.
        assertTrue(screen.revealOffer("ability:double_flap"));
        assertTrue(card.contains(card.centerX(), card.centerY()), "the card is in the band now");
        long before = coins();
        click(card);
        assertEquals(before, coins(), "clicking a capped level costs nothing");
        assertEquals(2, profile.abilityLevel("double_flap"), "and buys nothing");

        // The cap is exactly what the single ability_cap grant of E3 raises.
        assertEquals(3, UpgradeManager.abilityLevelCeiling(content),
                "the forge tree ships one ability_cap grant");
    }

    @Test
    void anAbilityLevelThatCannotBePaidForChangesNothing() {
        credit(10);
        open();
        openTab(1);
        CardGrid.Card card = screen.offerGrid().card("ability:double_flap");
        assertTrue(card.isDimmed(), "10 coins do not buy a 300 coin level");
        long toastsBefore = toasts.pushedCount();
        click(card);
        assertEquals(10, coins(), "nothing was spent");
        assertEquals(0, profile.abilityLevel("double_flap"), "and nothing was raised");
        assertTrue(toasts.pushedCount() > toastsBefore, "the refusal is explained");
        assertTrue(screen.detailLines().stream()
                        .anyMatch(line -> line.contains(strings.get(
                                StringKey.SHOP_CANNOT_AFFORD))),
                () -> "the detail panel says so: " + screen.detailLines());
    }

    @Test
    void aLockedAbilityStillShowsItsUnlockPath() {
        open();
        openTab(1);
        ShopScreen.Offer shield = screen.offer("ability:shield");
        assertNotNull(shield);
        assertFalse(shield.isAbilityLevel(), "a locked ability is bought whole, not by the level");
        assertEquals(200, shield.cost());
        assertTrue(screen.offerGrid().card("ability:shield").tooltip()
                        .contains(ProgressionText.price(strings, 200)),
                () -> screen.offerGrid().card("ability:shield").tooltip());
        // Its description carries level 1's numbers (M5) and no leftover placeholder.
        assertFalse(screen.offerGrid().card("ability:shield").tooltip().contains("{"),
                () -> screen.offerGrid().card("ability:shield").tooltip());
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

    // ------------------------------------------------------------------ the hub's shell (M12)

    @Test
    void theShopWearsTheHubShellWithShopOnTheGoldPlate() {
        open();
        assertEquals(strings.get(StringKey.SHOP_TITLE), screen.header().title());
        assertFalse(screen.header().chip().isFocusable(),
                "from inside the shop the coin chip has nowhere to go: it is a readout");
        assertFalse(screen.focusRing().nodes().contains(screen.header().chip()),
                "the wallet appears once, in the header, and is not a control");
        assertTrue(screen.nav().button(SectionNav.SHOP).isPrimary(),
                "the gold plate reads as 'you are here'");
        assertFalse(screen.nav().button(SectionNav.PLAY).isPrimary());
        assertFalse(screen.nav().button(SectionNav.BIRDS).isEnabled(),
                "built without an application context the sibling sections are inert");
        assertTrue(screen.nav().button(SectionNav.SHOP).isEnabled(),
                "but the section itself is not");
        assertEquals(strings.get(StringKey.MENU_SHOP), screen.nav().button(SectionNav.SHOP).text());
        assertTrue(screen.focusRing().nodes().contains(screen.ctaButton()),
                "the call to action is on the one ring");
        assertTrue(screen.focusRing().nodes().contains(screen.nav().button(SectionNav.PLAY)),
                "and so is the navigation, so Down from the last card reaches it");
    }

    @Test
    void theCategoryBarKeepsFourUniformTabsWithAGlyphEach() {
        open();
        assertEquals(4, screen.tabBar().size());
        assertTrue(screen.tabBar().isAccented(), "the selected tab wears the gold accent");
        double tabWidth = screen.tabBar().width() / screen.tabBar().size();
        for (int i = 0; i < 4; i++) {
            assertEquals(i, screen.tabBar().indexAt(screen.tabBar().x() + (i + 0.5) * tabWidth,
                    screen.tabBar().centerY()), "tab " + i + " owns a quarter of the bar");
            assertNotNull(screen.tabBar().tabs().get(i).icon(), "tab " + i + " has a glyph");
        }
    }

    @Test
    void theCallToActionNamesTheVerbOfTheTab() {
        credit(1000);
        open();
        assertEquals(strings.get(StringKey.SHOP_CTA_BIRD), screen.ctaButton().text());
        assertEquals("Ironbeak", screen.ctaButton().subtitle(),
                "the plate names what it would buy");
        assertTrue(screen.ctaButton().isEnabled());

        openTab(1);
        assertEquals(strings.get(StringKey.SHOP_CTA_ABILITY), screen.ctaButton().text(),
                "a locked ability is unlocked");
        assertTrue(screen.revealOffer("ability:double_flap"));
        assertEquals(strings.get(StringKey.SHOP_CTA_ABILITY_UPGRADE),
                screen.ctaButton().text(), "an owned one is upgraded");

        openTab(2);
        assertEquals(strings.get(StringKey.SHOP_CTA_WORLD), screen.ctaButton().text());
        openTab(3);
        assertEquals(strings.get(StringKey.SHOP_CTA_FEATURE), screen.ctaButton().text());

        // No verb carries a number: the price is on the card, and only there.
        for (int i = 0; i < 4; i++) {
            openTab(i);
            assertFalse(screen.ctaButton().text().matches(".*\\d.*"),
                    () -> "no price on the plate: " + screen.ctaButton().text());
        }
    }

    @Test
    void theCallToActionBuysWhatThePlaqueDescribesAndGoesQuietOnceOwned() {
        credit(200);
        open();
        assertEquals("bird:guardian", screen.currentId(), "the cheapest bird is described first");
        click(screen.ctaButton());
        assertTrue(profile.isUnlocked(BirdDef.NAMESPACE + "guardian"), "the plate bought it");
        assertEquals(50, coins());
        assertEquals("bird:guardian", screen.currentId(), "the plaque stays on what was bought");
        assertEquals(strings.get(StringKey.COMMON_OWNED), screen.ctaButton().text());
        assertFalse(screen.ctaButton().isEnabled(), "and there is nothing more to press");
        long toastsBefore = toasts.pushedCount();
        int savesBefore = saves;
        click(screen.offerGrid().card("bird:guardian"));
        assertEquals(50, coins(), "an owned card is not sold twice");
        assertEquals(savesBefore, saves);
        assertEquals(toastsBefore, toasts.pushedCount(), "and it does not nag about it");
    }

    @Test
    void theDetailPlaqueNeverRepeatsThePrice() {
        credit(1000);
        open();
        List<String> lines = screen.detailLines();
        assertEquals("Ironbeak", lines.get(0));
        assertFalse(lines.stream().anyMatch(line -> line.contains("150")),
                () -> "the price is on the card, not on the plaque: " + lines);
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                        strings.format(StringKey.SHOP_EARN, "Play 3 runs"))),
                () -> "the plaque carries the road that costs no coins: " + lines);

        openTab(1);
        assertTrue(screen.revealOffer("ability:double_flap"));
        assertTrue(screen.detailLines().stream().anyMatch(line -> line.startsWith(
                        strings.format(StringKey.SHOP_NEXT_LEVEL_EFFECT, ""))),
                () -> "an owned ability's plaque says what the next level does: "
                        + screen.detailLines());
    }

    @Test
    void theGridScrollsWhenATabHasMoreRowsThanTheBand() {
        open();
        assertEquals(0, screen.maxScroll(), "six birds are three rows: they fit");
        openTab(1);
        assertTrue(screen.maxScroll() > 0, "eight abilities are four rows: they do not");
        CardGrid.Card last = screen.offerGrid().cards().get(7);
        assertFalse(last.contains(last.centerX(), last.centerY()),
                "a card below the band does not answer the pointer");
        double before = screen.scroll();
        wheel(-1);
        assertTrue(screen.scroll() > before,
                "the wheel scrolls the grid, with the sign every scrolling screen uses");
        wheel(10);
        assertEquals(0, screen.scroll(), "and clamps at the top");
        assertTrue(screen.revealOffer(last.id()));
        assertEquals(screen.maxScroll(), screen.scroll(), "revealing the last row scrolls fully");
        assertTrue(last.contains(last.centerX(), last.centerY()), "and the card answers now");
        assertEquals(last.id(), screen.currentId(), "with the plaque on it");
        openTab(0);
        assertEquals(0, screen.scroll(), "a tab change starts at the top");
    }

    @Test
    void aLanguageSwitchRelabelsTheWholeShell() {
        open();
        Strings pt = Strings.load("pt_BR");
        Strings.use(pt);
        // The screen holds the table it was given; the switch reaches it through the shared
        // instance the application reloads in place (D25), which the test stands in for here.
        ShopScreen ptScreen = new ShopScreen(screens, pt, content, profile, unlocks, upgrades,
                toasts);
        assertEquals(pt.get(StringKey.SHOP_TITLE), ptScreen.header().title());
        assertEquals("Loja", ptScreen.header().title());
        assertEquals(pt.get(StringKey.SHOP_TAB_BIRDS), ptScreen.tabBar().tabs().get(0).label());
        assertEquals(pt.get(StringKey.MENU_NAV_GOALS),
                ptScreen.nav().button(SectionNav.GOALS).text());
        assertEquals(pt.get(StringKey.SHOP_CTA_BIRD), ptScreen.ctaButton().text());
        assertEquals("Comprar ave", ptScreen.ctaButton().text());
        assertEquals("Jogue 3 partidas", ptScreen.offerGrid().cards().get(0).subtitle());
    }

    @Test
    void escapeLeavesTheShopWithoutAButton() {
        open();
        assertTrue(screen.focusRing().nodes().stream()
                        .noneMatch(node -> node instanceof io.github.michelbr84.flapforge.ui.component.Button
                                && !(node instanceof CtaButton)),
                "there is no Back button: the navigation and Esc are the ways out");
        tap(Keys.ESCAPE);
        ticks(2);
        assertNotSame(screen, screens.top(), "Esc pops the shop");
    }
}
