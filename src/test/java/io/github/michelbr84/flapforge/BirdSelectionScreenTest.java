package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
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
import io.github.michelbr84.flapforge.support.TestContent;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Tooltip;
import io.github.michelbr84.flapforge.ui.screens.BirdSelectionScreen;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // ------------------------------------------------------------------ loadout (M5)

    @Test
    void theLoadoutRowShowsOneActiveSlotAndTheBirdsPassiveSlots() {
        open();
        assertTrue(content.playable(ContentKind.ABILITY), "M5 turned the ability system on");
        assertFalse(screen.abilityLine().contains(strings.format(StringKey.COMMON_SOON, "M5")),
                () -> "the slots exist now: " + screen.abilityLine());
        assertTrue(screen.abilityLine().contains(
                strings.format(StringKey.BIRDS_PASSIVE_SLOTS, 2)));

        // Forgewing: one active slot plus two passive slots, no innate passive.
        assertEquals(BirdSelectionScreen.SlotRole.ACTIVE, screen.activeSlot().role());
        assertNotNull(screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 0));
        assertNotNull(screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 1));
        assertNull(screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 2),
                "the classic bird carries two passive slots");
        assertNull(screen.slot(BirdSelectionScreen.SlotRole.INNATE, 0));
        assertEquals(ProgressionText.name(strings, ContentKind.ABILITY, "double_flap"),
                screen.activeSlot().value(),
                "a fresh profile flies with the default active ability (E18)");
    }

    /**
     * E3: {@code economy/ability_scholar_1} grants {@code passive_slot:1} and "BirdSelection shows
     * the extra slot". Nothing asserted the screen half of that, so deleting the bonus from
     * {@code passiveSlotsOf} left the suite green.
     */
    @Test
    void thePassiveSlotGrantAddsAChipToTheRow() {
        profile.passiveSlotBonus = 1;
        open();
        assertNotNull(screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 2),
                "the granted slot is a chip the player can cycle");
        assertTrue(screen.abilityLine().contains(
                strings.format(StringKey.BIRDS_PASSIVE_SLOTS, 3)),
                () -> "and the line counts it: " + screen.abilityLine());

        profile.passiveSlotBonus = 0;
        screen.refreshState();
        assertNull(screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 2),
                "and it goes away with the grant");
        assertTrue(screen.abilityLine().contains(
                strings.format(StringKey.BIRDS_PASSIVE_SLOTS, 2)));
    }

    /**
     * A bird that hides one of the chosen passives — Ironbeak grants the shield innately, so the
     * chips show the other two — must not delete it from the profile when another slot is cycled
     * ({@code SelectionManager.setPassiveAbilities}: "the profile keeps what the player chose even
     * when they switch to a bird with fewer slots and back").
     */
    @Test
    void cyclingASlotKeepsThePassivesTheBirdCannotShow() {
        profile.unlock("ability:shield");
        profile.unlock("ability:coin_magnet");
        profile.unlock("ability:emergency_recovery");
        profile.unlock("bird:guardian");
        profile.unlock("cosmetic:guardian:default");
        open();
        selection.setPassiveAbilities(profile,
                List.of("shield", "coin_magnet", "emergency_recovery"), content);
        selection.selectBird(profile, "guardian", content);
        screen.refreshState();
        assertEquals("shield", screen.slot(BirdSelectionScreen.SlotRole.INNATE, 0).abilityId());
        assertEquals("coin_magnet",
                screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 0).abilityId(),
                "the chips skip the shield the bird grants for free");

        click(screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 0));

        assertTrue(profile.selected.passiveAbilityIds.contains("shield"),
                () -> "the choice the bird hid survived the cycle: "
                        + profile.selected.passiveAbilityIds);
        selection.selectBird(profile, "classic", content);
        screen.refreshState();
        assertEquals(List.of("emergency_recovery", "shield"), profile.selected.passiveAbilityIds,
                "the cycled chips keep their order and what was hidden follows them");
        assertEquals("shield", screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 1).abilityId(),
                "and it is equipped again on a bird that does not grant it");
    }

    @Test
    void activatingTheActiveSlotCyclesTheAbilityAndSavesIt() {
        open();
        int savesBefore = saves;
        assertEquals("double_flap", profile.selected.activeAbilityId, "the E18 default");

        // The only ACTIVE ability a fresh profile owns is the double flap, so the cycle is
        // "double flap -> nothing -> double flap".
        click(screen.activeSlot());
        assertNull(profile.selected.activeAbilityId, "the slot was emptied");
        assertTrue(saves > savesBefore, "an equipped ability is written at once (D15)");
        assertEquals(strings.get(StringKey.BIRDS_SLOT_EMPTY), screen.activeSlot().value());

        click(screen.activeSlot());
        assertEquals("double_flap", profile.selected.activeAbilityId, "and equipped again");
        assertEquals(ProgressionText.name(strings, ContentKind.ABILITY, "double_flap"),
                screen.activeSlot().value());

        // A run built from the profile now carries it as the active ability (D9).
        assertEquals("double_flap",
                RunLoadout.previewRun(profile, content).simulation().abilities().active().id());
    }

    @Test
    void buyingAnAbilityLevelChangesWhatTheAbilityPanelShows() {
        profile.unlock("ability:shield");
        credit(1000);
        open();
        assertEquals(strings.format(StringKey.ABILITY_LEVEL, 1, 3),
                screen.row("ability.shield").value());
        assertTrue(upgradeManager.buyAbilityLevel(profile, "shield", content).ok());
        screen.refreshState();
        assertEquals(strings.format(StringKey.ABILITY_LEVEL, 2, 3),
                screen.row("ability.shield").value(), "the panel re-reads the profile");
        // Level 2 is the one that regenerates a charge every 15 gates (data/abilities.json).
        assertTrue(screen.row("ability.shield.effect").label().contains("15"),
                () -> screen.row("ability.shield.effect").label());
    }

    @Test
    void aPassiveSlotOnlyOffersUnlockedPassivesAndNeverTheSameOneTwice() {
        profile.unlock("ability:shield");
        profile.unlock("ability:coin_magnet");
        open();
        BirdSelectionScreen.AbilitySlot first =
                screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 0);
        BirdSelectionScreen.AbilitySlot second =
                screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 1);
        assertNotNull(first);
        assertNotNull(second);

        click(first);
        assertEquals(List.of("shield"), profile.selected.passiveAbilityIds);
        click(second);
        assertEquals(List.of("shield", "coin_magnet"), profile.selected.passiveAbilityIds,
                "the second slot skipped the passive the first one already holds");

        // The run built from the profile carries both, and the shield's charge with them (D9).
        assertEquals(1, RunLoadout.previewRun(profile, content).simulation().shield().maxCharges());
    }

    @Test
    void anInnateSlotIsFixedAndDoesNotSpendASlot() {
        profile.unlock("bird:guardian");
        profile.unlock("cosmetic:guardian:default");
        open();
        selection.selectBird(profile, "guardian", content);
        screen.refreshState();

        BirdSelectionScreen.AbilitySlot innate =
                screen.slot(BirdSelectionScreen.SlotRole.INNATE, 0);
        assertNotNull(innate, "Ironbeak grants a shield");
        assertEquals("shield", innate.abilityId());
        assertFalse(innate.isEnabled(), "an innate passive cannot be unequipped (D9)");
        assertNotNull(screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 1),
                "and it does not eat one of the two slots");

        click(innate);
        assertEquals("shield", innate.abilityId(), "clicking a fixed slot changes nothing");
        assertTrue(profile.selected.passiveAbilityIds.isEmpty());
    }

    @Test
    void theAbilityPanelListsEveryUnlockedAbilityWithItsLevelKindTagsAndEffect() {
        profile.unlock("ability:shield");
        open();
        BirdSelectionScreen.Row header = screen.row("ability.shield");
        assertNotNull(header, () -> "the unlocked shield must be listed: " + ids());
        assertTrue(header.header());
        assertEquals(strings.format(StringKey.ABILITY_LEVEL, 1, 3), header.value(),
                "the panel shows the owned level of three");
        assertTrue(header.label().startsWith(
                ProgressionText.name(strings, ContentKind.ABILITY, "shield")));

        BirdSelectionScreen.Row kind = screen.row("ability.shield.kind");
        assertNotNull(kind);
        assertTrue(kind.label().contains(strings.get(StringKey.ABILITY_KIND_PASSIVE)));
        assertTrue(kind.label().contains(strings.get(StringKey.ABILITY_TAG_DEFENSIVE)));

        // The description carries the level's own numbers (M5): 45 invulnerability ticks at L1.
        BirdSelectionScreen.Row desc = screen.row("ability.shield.desc");
        assertNotNull(desc);
        assertTrue(desc.label().contains("45"), () -> "the level parameters are substituted: "
                + desc.label());
        assertFalse(desc.label().contains("{"), () -> "and no placeholder is left: " + desc.label());
        assertNotNull(screen.row("ability.shield.effect"));

        // A locked ability is not in the panel at all.
        assertNull(screen.row("ability.dash"), "the dash is not unlocked yet");
        assertNotNull(screen.row("ability.double_flap"), "the default ability always is");
    }

    @Test
    void anAbilityTheRunsRulesWouldStripIsGreyedOutWithTheReason() {
        // No shipped tier strips abilities, so the case is driven with a content set whose hard
        // tier carries NO_DEFENSIVE_ABILITIES -- which is what a challenge does from M8 (D9).
        GameContent strict = contentWithDefensiveBan();
        profile.unlock("ability:shield");
        profile.unlock("tier:hard");
        profile.selected.tierId = "hard";
        screen = new BirdSelectionScreen(screens, strings, strict, profile, selection, unlocks,
                toasts);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);

        assertTrue(screen.previewRules().contains(RuleFlag.NO_DEFENSIVE_ABILITIES),
                "the selected tier strips defensive abilities");
        BirdSelectionScreen.Row header = screen.row("ability.shield");
        assertNotNull(header);
        assertTrue(header.dimmed(), "a stripped ability is greyed out");
        BirdSelectionScreen.Row why = screen.row("ability.shield.blocked");
        assertNotNull(why, () -> "and says why: " + ids());
        assertTrue(why.label().contains(
                strings.get(StringKey.RULE_NO_DEFENSIVE_ABILITIES)), why.label());

        // And it cannot be equipped: the slot cycle skips it, so it stays empty.
        BirdSelectionScreen.AbilitySlot slot =
                screen.slot(BirdSelectionScreen.SlotRole.PASSIVE, 0);
        assertNotNull(slot);
        click(slot);
        assertTrue(profile.selected.passiveAbilityIds.isEmpty(),
                "a greyed-out ability is not offered by the slot");
    }

    /**
     * The shipped content with {@code NO_DEFENSIVE_ABILITIES} added to the hard tier.
     *
     * @return the content
     */
    private static GameContent contentWithDefensiveBan() {
        Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.shippedJson());
        JsonObject difficulty = files.get("difficulty").getAsJsonObject();
        for (JsonElement tier : difficulty.getAsJsonArray("tiers")) {
            if ("hard".equals(tier.getAsJsonObject().get("id").getAsString())) {
                JsonArray flags = new JsonArray();
                flags.add("NO_DEFENSIVE_ABILITIES");
                tier.getAsJsonObject().add("flags", flags);
            }
        }
        return GameContent.fromJson(files);
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

    // ------------------------------------------------------------------ world picker (M7)

    @Test
    void theWorldPickerListsTheFiveWorldsInOrderWithTheirHazardsAndLocks() {
        open();
        assertEquals(List.of("green_fields", "wind_valley", "iron_forge", "storm_sky", "void"),
                screen.worldIds(), "worlds.json order");
        assertEquals(strings.get(StringKey.BIRDS_WORLD), screen.worldList().label());
        assertEquals("green_fields", screen.currentWorldId(), "a fresh profile flies the fields");
        assertFalse(screen.worldList().isLocked());
        assertEquals(strings.format(StringKey.BIRDS_WORLD_HAZARDS,
                strings.get(StringKey.OBSTACLE_PIPE_GATE)), screen.worldDetail(),
                "Green Fields spawns pipes only");
        assertEquals(ProgressionText.name(strings, ContentKind.WORLD, "green_fields"),
                screen.worldList().selectedOption());

        // Wind Valley is locked: the row says how to open it (the cheapest branch, D13).
        int savesBefore = saves;
        screen.focusRing().focus(screen.worldList());
        tap(Keys.RIGHT);
        assertEquals("green_fields", profile.selected.worldId,
                "stepping onto a locked world writes nothing");
        assertEquals(savesBefore, saves);
        assertEquals("green_fields", screen.currentWorldId(), "the row snapped back");
        assertTrue(screen.worldList().options().get(1)
                .contains(strings.get(StringKey.COMMON_LOCKED)), "locked worlds are marked");
    }

    @Test
    void steppingOntoAnOwnedWorldSelectsItAndTheSelectionPersists() {
        profile.unlock("world:iron_forge");
        open();
        screen.focusRing().focus(screen.worldList());
        // green_fields -> wind_valley (locked, refused) ... the row cannot pass a locked world,
        // so the owned one is reached by selecting its index directly, as a click on it would.
        int savesBefore = saves;
        screen.worldList().select(2);
        ticks(2);
        assertEquals("iron_forge", profile.selected.worldId, "the selection was written");
        assertTrue(saves > savesBefore, "and saved at once (D15)");
        assertEquals("iron_forge", screen.currentWorldId());
        assertFalse(screen.worldList().isLocked());
        String hazards = screen.worldDetail();
        assertTrue(hazards.contains(strings.get(StringKey.OBSTACLE_GEAR)), hazards);
        assertTrue(hazards.contains(strings.get(StringKey.OBSTACLE_PISTON)), hazards);
        assertTrue(hazards.contains(strings.get(StringKey.OBSTACLE_PIPE_GATE)), hazards);
        assertEquals("iron_forge", RunLoadout.configFor(profile, content, 1,
                io.github.michelbr84.flapforge.gameplay.run.RunMode.STANDARD).worldId(),
                "the next run is played there");

        // Reopening the screen shows the persisted choice.
        screens.pop();
        ticks(GRACE);
        open();
        assertEquals("iron_forge", screen.currentWorldId(), "the selection survived");

        // A locked world reached by index is refused and reported.
        long toastsBefore = toasts.pushedCount();
        screen.worldList().select(3);
        ticks(2);
        assertEquals("iron_forge", profile.selected.worldId);
        assertEquals("iron_forge", screen.currentWorldId());
        assertTrue(toasts.pushedCount() > toastsBefore, "the refusal raised a toast");
        assertTrue(screen.worldList().tooltip().isEmpty()
                || !screen.worldList().tooltip().isEmpty());
    }

    @Test
    void arrowKeysStepTheWorldAndTierRowsInsteadOfMovingTheFocus() {
        profile.unlock("world:wind_valley");
        open();
        screen.focusRing().focus(screen.worldList());
        tap(Keys.RIGHT);
        assertSame(screen.worldList(), screen.focusRing().focused(),
                "Right on the world row steps the row; the Buy button beside it does not take "
                        + "the focus");
        assertEquals("wind_valley", profile.selected.worldId, "the owned world was selected");
        assertEquals("wind_valley", screen.currentWorldId());
        tap(Keys.LEFT);
        assertEquals("green_fields", profile.selected.worldId, "and Left steps back");
        assertSame(screen.worldList(), screen.focusRing().focused());

        screen.focusRing().focus(screen.tierList());
        tap(Keys.RIGHT);
        assertSame(screen.tierList(), screen.focusRing().focused(), "the tier row too");
        assertEquals("normal", profile.selected.tierId, "a locked tier is still refused");
        assertEquals(0, screen.tierList().selectedIndex(), "and the row snapped back");
    }

    @Test
    void theWorldPickerFollowsALanguageSwitch() {
        open();
        // M2's live switch: the settings screen reloads the shared table in place and
        // re-publishes it (GameContext.applyLanguage); the screen notices on its next tick.
        strings.reload("pt_BR");
        Strings.use(strings);
        ticks(2);
        Strings pt = Strings.load("pt_BR");
        assertEquals("pt_BR", strings.language(), "the screen's table follows the switch");
        assertEquals(pt.get(StringKey.BIRDS_WORLD), screen.worldList().label());
        assertEquals(pt.name("world", "green_fields"), screen.worldList().selectedOption());
        assertTrue(screen.worldDetail().startsWith(
                pt.format(StringKey.BIRDS_WORLD_HAZARDS, "").trim()), screen.worldDetail());
    }
}
