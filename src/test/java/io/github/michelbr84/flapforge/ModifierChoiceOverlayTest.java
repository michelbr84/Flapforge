package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.Clock;
import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.app.Threads;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.audio.NullAudio;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.DraftRuns;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.ModifierChoiceOverlay;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.SeededRunSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The draft overlay driven headlessly through the input queue and the loop (M6, D11, D17): the
 * cards a run really drew are drawn, the keyboard and the pointer both take one, Skip takes
 * nothing, the 3-2-1 runs and — the property that makes the whole thing safe — nothing the player
 * presses over the cards reaches the run underneath.
 *
 * <p>The world is {@link io.github.michelbr84.flapforge.support.FixedSpawnTable} and the cards are
 * the shipped ones (E17), so the words asserted here are the words a player reads.
 */
class ModifierChoiceOverlayTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;
    /** Gate the test schedule opens its only draft at. */
    private static final int OFFER_GATE = 2;
    /** Frames a scripted flight may take to reach the draft. */
    private static final int FLIGHT_BUDGET = 4000;

    private final GameContent content = GameContent.load();
    private Strings strings;
    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private GameLoop loop;
    private GameScreen game;
    private ToastLayer toasts;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        strings = Strings.active();
    }

    /**
     * Starts a game screen on a run source, wired the way the application wires one.
     *
     * @param source the run source
     */
    private void start(SeededRunSource source) {
        start(source, false);
    }

    /**
     * Starts a game screen, optionally over a full {@link GameContext} so the run's facts reach
     * the presentation bus and the toast layer the way they do in the running game (D16).
     *
     * @param source the run source
     * @param wired whether to build the context
     */
    private void start(SeededRunSource source, boolean wired) {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter();
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        toasts = new ToastLayer();
        if (wired) {
            Strings.use(strings);
            GameContext context = new GameContext(LaunchOptions.DEFAULTS, (Clock) clock,
                    () -> 0L, new Threads(), input, viewport, screens, presenter, null, loop,
                    FrameLimiter.uncapped(clock), null, new EventBus(),
                    new AudioManager(new NullAudio()), strings, toasts, content, null, null, null);
            game = new GameScreen(context, source, SeedSequence.of(42));
        } else {
            game = new GameScreen(screens, source, SeedSequence.of(42));
        }
        screens.push(game);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
    }

    private void start(ModifierCatalog catalog, List<String> forced) {
        start(DraftRuns.source(catalog, forced));
    }

    private void start(String... ids) {
        start(DraftRuns.catalog(content, OFFER_GATE, 3, ids), List.of());
    }

    /** A run holding one stack of {@code coin_drops} whose draft shows both economy cards. */
    private void startEconomyDraft() {
        start(DraftRuns.catalog(content, OFFER_GATE, 2, "coin_drops", "magnet_burst"),
                List.of("coin_drops"));
    }

    /**
     * One card of the open draft by modifier id.
     *
     * @param overlay the draft
     * @param id the modifier id
     * @return the card
     */
    private static ModifierChoiceOverlay.Card card(ModifierChoiceOverlay overlay, String id) {
        for (ModifierChoiceOverlay.Card card : overlay.cards()) {
            if (card.id().equals(id)) {
                return card;
            }
        }
        throw new AssertionError(id + " is not on the table: " + overlay.cards().stream()
                .map(ModifierChoiceOverlay.Card::id).toList());
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

    private void clickAt(double x, double y) {
        int wx = (int) Math.round(x);
        int wy = (int) Math.round(y);
        input.offer(new RawInput.MouseMove(wx, wy));
        ticks(1);
        input.offer(new RawInput.MouseDown(Keys.BUTTON_LEFT, wx, wy));
        input.offer(new RawInput.MouseUp(Keys.BUTTON_LEFT, wx, wy));
        ticks(1);
    }

    private void click(UiNode node) {
        clickAt(node.centerX(), node.centerY());
    }

    /**
     * Flies the flat corridor until the draft opens, flapping whenever the bird has sunk below the
     * gap centre.
     *
     * @return the overlay on top of the stack
     */
    private ModifierChoiceOverlay flyToDraft() {
        tap(Keys.SPACE);
        for (int i = 0; i < FLIGHT_BUDGET
                && !(screens.top() instanceof ModifierChoiceOverlay); i++) {
            RunPhase phase = game.run().phase();
            boolean flying = phase == RunPhase.FLYING || phase == RunPhase.BREATHER;
            if (flying && game.run().simulation().bird().y() > Playfield.BIRD_START_Y + 10) {
                tap(Keys.SPACE);
            } else {
                ticks(1);
            }
        }
        assertTrue(screens.top() instanceof ModifierChoiceOverlay,
                () -> "the draft never opened; the run is in " + game.run().phase() + " at gate "
                        + game.run().stats().gatesPassed());
        assertEquals(RunPhase.CHOOSING_MODIFIER, game.run().phase());
        ticks(GRACE);
        return (ModifierChoiceOverlay) screens.top();
    }

    @Test
    void theDraftShowsOneCardPerDrawnModifierWithItsWords() {
        start();
        ModifierChoiceOverlay overlay = flyToDraft();
        assertEquals(3, overlay.cards().size(), "choicesPerOffer cards are on the table");
        assertEquals(game.run().simulation().modifiers().offer().ids(),
                overlay.cards().stream().map(ModifierChoiceOverlay.Card::id).toList(),
                "and they are the cards the pool actually drew, in draw order");
        for (ModifierChoiceOverlay.Card card : overlay.cards()) {
            assertEquals(strings.name(ContentKind.MODIFIER.key(), card.id()), card.name(),
                    "the card is named by the string table");
            assertEquals(strings.desc(ContentKind.MODIFIER.key(), card.id()), card.description(),
                    "and its effect is described in words");
            assertEquals(strings.get(StringKey.valueOf("RARITY_" + card.rarity().name())),
                    card.rarityLabel(), "with its rarity spelled out");
            assertFalse(card.tags().isEmpty(), card.id() + " shows its tags");
            assertFalse(card.numbers().isEmpty(), card.id() + " shows its effect in numbers");
            assertFalse(card.lines().contains(""), "no line of " + card.id() + " is blank");
        }
        assertEquals(strings.get(StringKey.DRAFT_SKIP), overlay.skipButton().text());
        assertSame(overlay.cards().get(0), overlay.focusRing().focused(),
                "the first card is focused when the draft opens");
    }

    @Test
    void aCardThatWouldCompleteASynergySaysSoAndASecondStackDoesNot() {
        // coin_drops is pre-taken and the draft shows both ECONOMY cards. Taking magnet_burst
        // completes coin_engine across two distinct entries; taking a second stack of coin_drops
        // does not, because a stack is one entry (E16), and the cards have to say which is which.
        startEconomyDraft();
        ModifierChoiceOverlay overlay = flyToDraft();
        assertEquals(2, overlay.cards().size(), "both economy cards are eligible");
        ModifierChoiceOverlay.Card magnet = card(overlay, "magnet_burst");
        ModifierChoiceOverlay.Card drops = card(overlay, "coin_drops");
        assertEquals(strings.format(StringKey.DRAFT_SYNERGY,
                        strings.name(ContentKind.SYNERGY.key(), "coin_engine")),
                magnet.synergy(), "the card promises the set bonus it would complete");
        assertEquals("", drops.synergy(),
                "a second stack of a card the run already holds completes nothing (E16)");
        assertEquals(strings.format(StringKey.DRAFT_STACKS, 2, 3), drops.stacks(),
                "and it says which stack it would be");

        click(magnet);
        assertEquals(List.of("coin_engine"), game.run().stats().synergiesActivated(),
                "and taking it activates exactly that one");
    }

    /**
     * D27: "{@code SynergyActivated} facts drive a toast and the HUD list". The chip is asserted
     * above; this is the announcement, which is the half a player actually notices — the set bonus
     * completes on a frozen tick under the open draft, and the toast layer renders under the
     * overlay.
     */
    @Test
    void takingACardThatCompletesASetBonusRaisesAToast() {
        start(DraftRuns.source(DraftRuns.catalog(content, OFFER_GATE, 2, "coin_drops",
                "magnet_burst"), List.of("coin_drops")), true);
        ModifierChoiceOverlay overlay = flyToDraft();
        long before = toasts.pushedCount();

        click(card(overlay, "magnet_burst"));
        assertEquals(List.of("coin_engine"), game.run().stats().synergiesActivated());
        assertEquals(before + 1, toasts.pushedCount(), "the set bonus is announced");
        assertEquals(strings.format(StringKey.TOAST_SYNERGY,
                        strings.name(ContentKind.SYNERGY.key(), "coin_engine")),
                toasts.visibleToasts().get(toasts.visibleCount() - 1).text(),
                "and it names the bonus in the player's language");
    }

    /** A card that completes nothing says nothing. */
    @Test
    void takingACardThatCompletesNothingRaisesNoToast() {
        start(DraftRuns.source(DraftRuns.catalog(content, OFFER_GATE, 2, "coin_drops",
                "magnet_burst"), List.of("coin_drops")), true);
        ModifierChoiceOverlay overlay = flyToDraft();
        long before = toasts.pushedCount();

        click(card(overlay, "coin_drops"));
        assertEquals(List.of(), game.run().stats().synergiesActivated());
        assertEquals(before, toasts.pushedCount(), "a second stack completes nothing (E16)");
    }

    @Test
    void theArrowsMoveFocusAndEnterTakesTheFocusedCard() {
        start();
        ModifierChoiceOverlay overlay = flyToDraft();
        tap(Keys.RIGHT);
        assertSame(overlay.cards().get(1), overlay.focusRing().focused(), "Right moved focus");
        String wanted = overlay.cards().get(1).id();

        tap(Keys.ENTER);
        assertEquals(RunPhase.RESUME_HOLD, game.run().phase(), "Enter answered the draft");
        assertEquals(List.of(wanted), game.run().stats().modifiersTaken());
        assertEquals(wanted, game.run().simulation().modifiers().stacks().keySet().iterator()
                .next());
        assertSame(overlay, screens.top(), "the overlay stays up for the countdown");
    }

    @Test
    void aClickTakesTheCardUnderThePointer() {
        start();
        ModifierChoiceOverlay overlay = flyToDraft();
        ModifierChoiceOverlay.Card third = overlay.cards().get(2);
        click(third);
        assertEquals(List.of(third.id()), game.run().stats().modifiersTaken(),
                "the pointer takes the card it is over");
        assertEquals(third.name(), overlay.takenName());
    }

    @Test
    void skipTakesNothingAndStillRunsTheHold() {
        start();
        ModifierChoiceOverlay overlay = flyToDraft();
        click(overlay.skipButton());
        assertTrue(overlay.isSkipped());
        assertEquals(List.of(), game.run().stats().modifiersTaken(), "Skip takes nothing");
        assertEquals(1, game.run().simulation().modifiers().offersSkipped());
        assertEquals(RunPhase.RESUME_HOLD, game.run().phase(), "and the hold still runs");
    }

    @Test
    void escapeSkipsTheDraftInsteadOfPausingTheRun() {
        start();
        flyToDraft();
        tap(Keys.ESCAPE);
        assertEquals(RunPhase.RESUME_HOLD, game.run().phase(),
                "Esc over the cards skips the draft; it must not open the pause overlay");
        assertEquals(List.of(), game.run().stats().modifiersTaken());
    }

    @Test
    void theCountdownRunsThreeTwoOneAndThenTheOverlayLeaves() {
        start();
        ModifierChoiceOverlay overlay = flyToDraft();
        click(overlay.cards().get(0));
        assertEquals(ModifierDirector.COUNTDOWN_STEPS, overlay.countdown(),
                "the hold opens on 3");

        boolean sawTwo = false;
        boolean sawOne = false;
        for (int i = 0; i < ModifierDirector.RESUME_HOLD_TICKS; i++) {
            if (!(screens.top() instanceof ModifierChoiceOverlay)) {
                break;
            }
            sawTwo |= overlay.countdown() == 2;
            sawOne |= overlay.countdown() == 1;
            ticks(1);
        }
        assertTrue(sawTwo, "the countdown passed through 2");
        assertTrue(sawOne, "and through 1");
        assertSame(game, screens.top(), "the overlay left when the run resumed");
        assertEquals(RunPhase.FLYING, game.run().phase());
        assertEquals(ModifierDirector.RESUME_IFRAMES,
                game.run().simulation().invulnerableTicks(),
                "and the run came back with its i-frames");
        assertEquals(1, screens.depth(), "nothing was left on the stack");
    }

    @Test
    void noInputLeaksIntoTheRunUnderneath() {
        start();
        ModifierChoiceOverlay overlay = flyToDraft();
        int simTick = game.run().simulation().tick();
        int flaps = game.run().simulation().flaps();
        int alive = game.run().stats().ticksAlive();
        double y = game.run().simulation().bird().y();
        double gateX = game.run().simulation().obstacles().last().x();

        // The ability key, the pointer moving over nothing and forty idle ticks: the world must
        // not move by a pixel, and the draft must still be waiting for an answer.
        for (int i = 0; i < 4; i++) {
            tap(Keys.X);
        }
        clickAt(4, 4);
        ticks(40);
        assertEquals(RunPhase.CHOOSING_MODIFIER, game.run().phase(), "no answer, no resume");
        assertEquals(simTick, game.run().simulation().tick(), "the simulation did not tick");
        assertEquals(flaps, game.run().simulation().flaps(), "no flap reached the bird");
        assertEquals(alive, game.run().stats().ticksAlive(), "no tick was counted as flown");
        assertEquals(y, game.run().simulation().bird().y(), 0.0, "the bird did not move");
        assertEquals(gateX, game.run().simulation().obstacles().last().x(), 0.0,
                "and neither did the gate ahead of it");
        assertTrue(game.run().stats().abilitiesUsed().isEmpty(), "no ability was activated");

        // Space is the confirm key on every screen, so over the cards it takes one -- and even
        // that must not be a flap.
        tap(Keys.SPACE);
        assertEquals(RunPhase.RESUME_HOLD, game.run().phase(), "Space took the focused card");
        assertEquals(flaps, game.run().simulation().flaps(), "and still flapped nothing");
        assertEquals(1, game.run().stats().modifiersTaken().size());
        assertEquals(overlay.cards().get(0).id(), game.run().stats().modifiersTaken().get(0));
    }

    @Test
    void theHudCarriesTheBuildTheDraftProduced() {
        startEconomyDraft();
        ModifierChoiceOverlay overlay = flyToDraft();
        assertEquals(List.of(strings.name(ContentKind.MODIFIER.key(), "coin_drops")),
                game.renderer().hud().buildChips(),
                "the forced card is on the HUD before the draft even opens");

        click(card(overlay, "magnet_burst"));
        assertEquals(List.of(strings.name(ContentKind.MODIFIER.key(), "coin_drops"),
                        strings.name(ContentKind.MODIFIER.key(), "magnet_burst")),
                game.renderer().hud().buildChips(), "the taken card joined the strip");
        assertEquals(List.of(strings.name(ContentKind.SYNERGY.key(), "coin_engine")),
                game.renderer().hud().synergyChips(), "and the set bonus is listed (D27)");
        assertNotNull(game.renderer().hud().streakBonusText());
    }

    @Test
    void aSecondStackIsCountedOnTheHudChip() {
        startEconomyDraft();
        ModifierChoiceOverlay overlay = flyToDraft();
        click(card(overlay, "coin_drops"));
        assertEquals(List.of(strings.format(StringKey.HUD_MODIFIER_STACK,
                        strings.name(ContentKind.MODIFIER.key(), "coin_drops"), 2)),
                game.renderer().hud().buildChips(),
                "one chip per modifier, carrying its stack count");
        assertEquals(List.of(), game.renderer().hud().synergyChips(),
                "and a stack is still one entry, so no set bonus (E16)");
    }

    @Test
    void aCardThatPaysPerStreakStepShowsItsBonusOnTheHud() {
        // E32.a: the streak term of the coin formula is the one number a card can change mid-run,
        // which is why the indicator M3 deferred belongs to M6.
        start(DraftRuns.catalog(content, OFFER_GATE, 1, "streak_bounty"), List.of("streak_bounty"));
        tap(Keys.SPACE);
        ticks(5);
        assertEquals(10, game.run().stats().modifierStreakCoins(),
                "the shipped streak_bounty pays 10 coins a step");
        assertEquals(strings.format(StringKey.HUD_STREAK_BONUS, 10),
                game.renderer().hud().streakBonusText());
    }

    @Test
    void aRunWithoutTheFeatureNeverOpensADraft() {
        // The same corridor and the same cards, but allowOffers is false: the gate passes and
        // nothing happens, which is what a profile without feature:modifiers plays.
        start(DraftRuns.source(DraftRuns.catalog(content, OFFER_GATE, 3), List.of(), false));

        tap(Keys.SPACE);
        for (int i = 0; i < 1200; i++) {
            if (game.run().simulation().bird().y() > Playfield.BIRD_START_Y + 10) {
                tap(Keys.SPACE);
            } else {
                ticks(1);
            }
            assertFalse(screens.top() instanceof ModifierChoiceOverlay,
                    "a locked feature must never open a draft");
        }
        assertTrue(game.run().stats().gatesPassed() > OFFER_GATE,
                "and the run flew well past the scheduled gate");
        assertEquals(List.of(), game.renderer().hud().buildChips());
    }
}
