package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.StatBreakdown;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import io.github.michelbr84.flapforge.progression.SelectionManager;
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
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Tooltip;
import io.github.michelbr84.flapforge.ui.screens.BirdSelectionScreen;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The bird selection (M4), driven headlessly through the input queue and the loop.
 *
 * <p>What is asserted is what the screen promises the player: the roster shows every bird with the
 * cheapest way to open the locked ones; selecting writes {@code profile.selected} and saves; a
 * purchase that can be paid for moves the wallet, the profile and the cards; one that cannot is
 * refused without touching anything; and the breakdown panel is the same arithmetic
 * {@code StatSheet.breakdown} produces for the run that would start right now.
 */
class BirdSelectionScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    private ManualClock clock;
    private InputQueue input;
    private Viewport viewport;
    private ScreenManager screens;
    private GameLoop loop;
    private Strings strings;
    private GameContent content;
    private PlayerProfile profile;
    private ProgressionManager progression;
    private SelectionManager selection;
    private UnlockManager unlocks;
    private UpgradeManager upgradeManager;
    private ToastLayer toasts;
    private BirdSelectionScreen screen;
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
        // The same wiring GameApplication uses: the unlock evaluator is the pipeline's unlock
        // step (D14), so a purchase grants what it implies -- a bird's default palette included.
        progression = new ProgressionManager(time, ProgressionManager.AchievementHook.NONE,
                UnlockEvaluator.of(content));
        selection = new SelectionManager(progression, () -> saves++);
        unlocks = new UnlockManager(progression, () -> saves++);
        upgradeManager = new UpgradeManager(progression, () -> saves++);
        toasts = new ToastLayer();
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
    }

    private void open() {
        screen = new BirdSelectionScreen(screens, strings, content, profile, selection, unlocks,
                toasts);
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
        Vec2 w = viewport.toWindow(node.centerX(), node.centerY());
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
    void theRosterShowsEveryBirdWithTheCheapestPathToTheLockedOnes() {
        open();
        assertEquals(content.birds().size(), screen.roster().size(), "one card per bird");
        assertEquals(7, screen.roster().size(), "the M4 roster is seven birds");

        CardGrid.Card classic = screen.roster().card("classic");
        assertNotNull(classic);
        assertFalse(classic.isLocked(), "the starter bird is owned");
        assertTrue(classic.isSelected(), "and selected on a fresh profile");
        assertEquals(strings.get(StringKey.COMMON_SELECTED), classic.badge());

        // A fresh profile is equally far from "3 runs" and from "150 coins"; the first branch of
        // the any_of wins the tie, which is the skill one (D13's cheapest path, in content order).
        CardGrid.Card guardian = screen.roster().card("guardian");
        assertNotNull(guardian);
        assertTrue(guardian.isLocked(), "Ironbeak is not owned yet");
        assertEquals(strings.format(StringKey.UNLOCK_RUNS, 3), guardian.subtitle());
        assertEquals("150", guardian.badge(), "the card carries its shop price");

        // With the coins in hand the purchase branch is the near one, and the card says so.
        credit(150);
        screen.refreshState();
        assertEquals(strings.format(StringKey.SHOP_PRICE, 150),
                screen.roster().card("guardian").subtitle());
    }

    @Test
    void arrowKeysWalkTheGridAndSelectingAnOwnedBirdWritesTheProfile() {
        profile.unlock("bird:heavy");
        profile.unlock("cosmetic:heavy:default");
        open();
        assertSame(screen.roster().card("classic"), screen.focusRing().focused(),
                "the selected bird is focused on entry");

        // The roster is two columns in content order: classic, swift / heavy, guardian / ...
        tap(Keys.DOWN);
        assertSame(screen.roster().card("heavy"), screen.focusRing().focused(),
                "Down moves one row");
        assertEquals("heavy", screen.currentBirdId());

        int savesBefore = saves;
        tap(Keys.ENTER);
        assertEquals("heavy", profile.selected.birdId, "activating an owned card selects it");
        assertEquals("default", profile.selected.paletteId, "the palette follows the bird");
        assertTrue(saves > savesBefore, "a selection is written to the disk at once (D15)");
        assertTrue(screen.roster().card("heavy").isSelected());
        assertFalse(screen.roster().card("classic").isSelected());
    }

    @Test
    void buyingABirdMovesTheWalletTheProfileAndTheCard() {
        credit(500);
        open();
        click(screen.roster().card("guardian"));
        assertEquals("guardian", screen.currentBirdId(), "the card is the current one");
        assertTrue(screen.buyButton().isEnabled(), "150 of 500 coins is affordable");

        long toastsBefore = toasts.pushedCount();
        click(screen.buyButton());
        assertTrue(profile.isUnlocked("bird:guardian"), "the bird was granted");
        assertEquals(350, coins(), "the price left the wallet");
        assertFalse(screen.roster().card("guardian").isLocked(), "the card is open now");
        assertTrue(toasts.pushedCount() > toastsBefore, "the purchase raised a toast");
        assertTrue(saves > 0, "a purchase is written to the disk at once (D15)");

        // The palette of a bird bought this way comes with it, so the row is not empty.
        click(screen.roster().card("guardian"));
        assertTrue(profile.isUnlocked("cosmetic:guardian:default"),
                "the evaluator granted the default palette after the purchase");
    }

    @Test
    void aPurchaseThatCannotBePaidForChangesNothing() {
        credit(10);
        open();
        click(screen.roster().card("guardian"));
        assertFalse(screen.buyButton().isEnabled(), "10 coins do not buy a 150 coin bird");

        long before = coins();
        long toastsBefore = toasts.pushedCount();
        click(screen.buyButton());
        assertEquals(before, coins(), "the wallet is untouched");
        assertFalse(profile.isUnlocked("bird:guardian"), "nothing was granted");
        assertEquals(toastsBefore, toasts.pushedCount(), "a disabled button says nothing");
        assertTrue(screen.roster().card("guardian").isLocked());
    }

    @Test
    void theBreakdownIsTheSameArithmeticAsStatSheetBreakdown() {
        open();
        StatBreakdown before = RunLoadout.previewStats(profile, content).breakdown(StatId.GRAVITY);
        assertEquals(1800, before.value(), 1e-9, "the classic bird starts at the classic gravity");
        assertEquals(ProgressionText.number(before.value()), screen.row("stat.GRAVITY").value());

        // Buy the node the milestone is named after and let the screen re-read the profile.
        credit(1000);
        assertTrue(upgradeManager.buy(profile, "feather_1", content).ok());
        screen.refreshState();

        StatBreakdown after = RunLoadout.previewStats(profile, content).breakdown(StatId.GRAVITY);
        assertEquals(1746, after.value(), 1e-9, "feather_1 level 1 is -3% gravity");
        BirdSelectionScreen.Row header = screen.row("stat.GRAVITY");
        assertNotNull(header);
        assertTrue(header.header());
        assertEquals(ProgressionText.number(after.value()), header.value(),
                "the panel shows the resolved value");

        BirdSelectionScreen.Row source = screen.row("stat.GRAVITY.upgrade:feather_1");
        assertNotNull(source, () -> "the node must be listed as a source: " + ids());
        assertEquals(ProgressionText.name(strings, ContentKind.UPGRADE, "feather_1"),
                source.label());

        // Every contribution of every listed stat has a row, and the numbers are the sheet's.
        for (StatId stat : StatId.values()) {
            BirdSelectionScreen.Row row = screen.row("stat." + stat.name());
            if (row == null) {
                continue;
            }
            StatBreakdown breakdown = RunLoadout.previewStats(profile, content).breakdown(stat);
            assertEquals(ProgressionText.number(breakdown.value()), row.value(), stat.name());
            for (EffectStack.Entry entry : breakdown.contributions()) {
                assertNotNull(screen.row("stat." + stat.name() + "."
                                + entry.modifier().source()),
                        () -> "missing source row for " + entry.modifier());
            }
        }
    }

    private List<String> ids() {
        return screen.rows().stream().map(BirdSelectionScreen.Row::id).toList();
    }

    @Test
    void theAbilitySlotAreaSaysTheSlotsArriveInTheNextMilestone() {
        open();
        assertFalse(content.playable(ContentKind.ABILITY),
                "M4 ships ability content but no ability system (E19)");
        assertTrue(screen.abilityLine().contains(strings.format(StringKey.COMMON_SOON, "M5")),
                () -> "the slot area must say so: " + screen.abilityLine());
        assertTrue(screen.abilityLine().contains(
                strings.format(StringKey.BIRDS_PASSIVE_SLOTS, 2)));
    }

    @Test
    void aLockedPaletteIsShownWithItsConditionAndCannotBeSelected() {
        open();
        List<UiNode> swatches = screen.paletteSwatches();
        assertEquals(BirdSelectionScreen.MAX_SWATCHES, swatches.size());
        assertTrue(swatches.get(0).isVisible(), "the default palette is always there");
        assertTrue(swatches.get(1).isVisible(), "the classic bird ships more than one palette");

        String palette = profile.selected.paletteId;
        click(swatches.get(1));
        assertEquals(palette, profile.selected.paletteId,
                "a locked palette cannot become the selected one");

        // Hovering it explains what would open it -- the classic bird's second palette is a
        // challenge reward, and the tooltip says which challenge.
        Vec2 w = viewport.toWindow(swatches.get(1).centerX(), swatches.get(1).centerY());
        input.offer(new RawInput.MouseMove((int) Math.round(w.x()), (int) Math.round(w.y())));
        ticks(Tooltip.DELAY_TICKS + 2);
        assertTrue(screen.tooltip().isShowing());
        assertTrue(screen.tooltip().text().contains(strings.format(StringKey.UNLOCK_CHALLENGE,
                        ProgressionText.name(strings, ContentKind.CHALLENGE, "no_shield_1"))),
                () -> "the swatch carries its condition: " + screen.tooltip().text());
    }

    @Test
    void theTooltipExplainsALockedCardAfterTheDelayAndStaysInsideThePlayfield() {
        open();
        Tooltip tooltip = screen.tooltip();
        CardGrid.Card guardian = screen.roster().card("guardian");
        Vec2 w = viewport.toWindow(guardian.centerX(), guardian.centerY());
        input.offer(new RawInput.MouseMove((int) Math.round(w.x()), (int) Math.round(w.y())));
        ticks(1);
        assertFalse(tooltip.isShowing(), "a tooltip waits before it appears");
        ticks(Tooltip.DELAY_TICKS + 1);
        assertTrue(tooltip.isShowing(), "and appears once the pointer rests");
        assertTrue(tooltip.text().contains(strings.get(StringKey.COMMON_LOCKED)),
                () -> "it explains the lock: " + tooltip.text());

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(Playfield.WIDTH,
                Playfield.HEIGHT, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            tooltip.layout(g);
        } finally {
            g.dispose();
        }
        assertTrue(tooltip.x() >= 0 && tooltip.y() >= 0, "the box starts inside the playfield");
        assertTrue(tooltip.x() + tooltip.width() <= Playfield.WIDTH,
                "and does not run off the right edge");
        assertTrue(tooltip.y() + tooltip.height() <= Playfield.HEIGHT,
                "nor off the bottom edge");
        assertTrue(tooltip.lines().size() > 1, "long text is wrapped");
    }

    @Test
    void aLockedTierIsOfferedButRefused() {
        open();
        assertEquals("normal", profile.selected.tierId);
        assertTrue(screen.tierList().options().stream()
                        .anyMatch(option -> option.contains(strings.get(StringKey.COMMON_LOCKED))),
                () -> "the locked tiers are visible (E19): " + screen.tierList().options());
        screen.tierList().select(1);
        ticks(1);
        assertEquals("normal", profile.selected.tierId, "a locked tier is not selected");
    }
}
