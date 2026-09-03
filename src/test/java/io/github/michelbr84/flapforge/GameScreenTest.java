package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.CloudLayer;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.DraftRuns;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.BossBanner;
import io.github.michelbr84.flapforge.ui.screens.ChallengeRunSource;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.ModifierChoiceOverlay;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.SeededRunSource;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The game screen driven headlessly through the queue and the loop (the same path
 * {@code MenuNavigationTest} uses for the menu): the run only starts on the first flap, losing
 * the window pauses it, resuming asks the loop to drop its banked time, game over offers an
 * instant retry with a fresh seed, and the focus loss the {@code F11} handshake produces must not
 * pause anything (D2, D29).
 */
class GameScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;
    /** Enough ticks for an unflapped bird to fall from 320 to the ground line and land. */
    private static final int FALL_TICKS = 200;

    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private GameLoop loop;
    private MainMenuScreen menu;
    private GameScreen game;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter();
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);
        // Exactly what GameApplication wires for `--seed 42`.
        SeedSequence seeds = SeedSequence.from(42L);
        menu = new MainMenuScreen(screens, new ClassicRunFactory(RunMode.SEEDED), seeds);
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        openGame();
    }

    private void openGame() {
        tap(Keys.ENTER);
        ticks(GRACE);
        assertTrue(screens.top() instanceof GameScreen, "Play opened the game screen");
        game = (GameScreen) screens.top();
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

    private void clickLeft() {
        input.offer(new RawInput.MouseDown(Keys.BUTTON_LEFT, 200, 300));
        input.offer(new RawInput.MouseUp(Keys.BUTTON_LEFT, 200, 300));
        ticks(1);
    }

    /**
     * Advances {@code n} ticks, tapping Space often enough to keep the bird airborne (the first
     * gate only reaches the bird after about 157 ticks, so a short flight cannot hit anything).
     */
    private void fly(int n) {
        for (int i = 0; i < n; i++) {
            if (i % 25 == 0) {
                tap(Keys.SPACE);
            } else {
                ticks(1);
            }
        }
    }

    private void runUntilGameOver() {
        for (int i = 0; i < FALL_TICKS && !(screens.top() instanceof GameOverOverlay); i++) {
            ticks(1);
        }
    }

    @Test
    void theSeedSequenceFollowsTheLaunchOption() {
        SeedSequence explicit = SeedSequence.from(42L);
        assertTrue(explicit.isExplicit(), "--seed N is an explicit sequence");
        assertEquals(42L, explicit.next());
        assertEquals(43L, explicit.next(), "instant retries walk N, N+1, N+2 (D29)");
        assertEquals(44L, explicit.next());

        SeedSequence random = SeedSequence.from(null);
        assertFalse(random.isExplicit(), "without --seed the seeds are clock derived");
        assertNotEquals(random.next(), random.next(), "consecutive seeds differ");
    }

    @Test
    void theRunStartsOnTheFirstFlapAndNotBefore() {
        assertEquals(RunPhase.READY, game.run().phase());
        assertEquals(42L, game.seed(), "--seed 42 reached the first run");
        double startY = game.run().simulation().bird().y();

        ticks(30);
        assertEquals(RunPhase.READY, game.run().phase(), "waiting does not start the run");
        assertEquals(startY, game.run().simulation().bird().y(), 0.0, "the bird floats in READY");
        assertTrue(game.run().simulation().obstacles().isEmpty(), "nothing spawns in READY");

        tap(Keys.SPACE);
        assertEquals(RunPhase.FLYING, game.run().phase(), "the first flap starts the run");
        assertTrue(game.run().simulation().bird().vy() < 0, "the flap was applied on the same tick");
        assertEquals(1, game.run().simulation().flaps());
    }

    @Test
    void aLeftClickFlapsLikeSpace() {
        clickLeft();
        assertEquals(RunPhase.FLYING, game.run().phase());
        assertEquals(1, game.run().simulation().flaps());
    }

    @Test
    void groundScrollsWhileFlyingAndStopsOnDeath() {
        tap(Keys.SPACE);
        double before = game.renderer().background().distance();
        ticks(10);
        assertEquals(before + 10 * 120.0 / Playfield.TICK_RATE,
                game.renderer().background().distance(), 1e-9,
                "the ground scrolls at SCROLL_SPEED");

        runUntilGameOver();
        double atDeath = game.renderer().background().distance();
        ticks(20);
        assertEquals(atDeath, game.renderer().background().distance(), 0.0,
                "the ground stops the moment the run ends, as upstream");
    }

    @Test
    void losingTheWindowFocusPausesAndResumingDropsTheBankedTime() {
        tap(Keys.SPACE);
        ticks(5);
        assertEquals(RunPhase.FLYING, game.run().phase());
        int tickOfPause = game.run().tick();

        input.offer(new RawInput.FocusLost(stamp++));
        ticks(1);
        assertTrue(screens.top() instanceof PauseOverlay, "focus loss pauses a flying run");

        ticks(20);
        assertEquals(tickOfPause, game.run().tick(), "the simulation is frozen while paused");

        // Resuming needs an explicit key: the pause overlay must survive idle ticks.
        assertTrue(screens.top() instanceof PauseOverlay);
        tap(Keys.SPACE);
        assertSame(game, screens.top(), "Space resumed the run");
        assertFalse(screens.consumeAccumulatorReset(),
                "the loop already consumed the accumulator reset the resume asked for");

        ticks(5);
        assertTrue(game.run().tick() > tickOfPause, "the simulation runs again");
    }

    @Test
    void iconifyingPausesAFlyingRun() {
        tap(Keys.SPACE);
        ticks(5);
        input.offer(new RawInput.Iconified(true));
        ticks(1);
        assertTrue(screens.top() instanceof PauseOverlay, "iconify pauses a flying run");
    }

    @Test
    void escapePausesWhileFlyingAndLeavesTheGameFromThePauseOverlay() {
        tap(Keys.SPACE);
        ticks(5);
        tap(Keys.ESCAPE);
        assertTrue(screens.top() instanceof PauseOverlay);
        ticks(GRACE);
        tap(Keys.ESCAPE);
        ticks(1);
        assertSame(menu, screens.top(), "Esc on the pause overlay quits to the menu");
        assertEquals(1, screens.depth());
    }

    @Test
    void escapeInReadyLeavesStraightToTheMenu() {
        assertEquals(RunPhase.READY, game.run().phase());
        tap(Keys.ESCAPE);
        ticks(1);
        assertSame(menu, screens.top());
    }

    @Test
    void theFocusLossOfTheFullscreenHandshakeDoesNotPause() {
        tap(Keys.SPACE);
        fly(5);
        assertEquals(RunPhase.FLYING, game.run().phase());

        tap(Keys.F11);
        assertTrue(screens.isFullscreenHandshake(), "F11 opened the handshake window");
        // The toolkit delivers the focus loss the handshake caused a few ticks later.
        fly(3);
        input.offer(new RawInput.FocusLost(stamp++));
        ticks(1);
        assertSame(game, screens.top(), "the handshake's focus loss must not pause the run");
        assertEquals(RunPhase.FLYING, game.run().phase());

        // Once the handshake window is over, a real focus loss pauses again.
        fly(ScreenManager.FULLSCREEN_GRACE_TICKS);
        assertFalse(screens.isFullscreenHandshake());
        input.offer(new RawInput.FocusLost(stamp++));
        ticks(1);
        assertTrue(screens.top() instanceof PauseOverlay, "a genuine focus loss still pauses");
    }

    @Test
    void gameOverOffersAnInstantRetryWithANewSeed() {
        long firstSeed = game.seed();
        tap(Keys.SPACE);
        runUntilGameOver();

        assertTrue(screens.top() instanceof GameOverOverlay, "the run ended on the ground");
        GameOverOverlay over = (GameOverOverlay) screens.top();
        assertEquals(RunPhase.FINISHED, game.run().phase());
        assertEquals(0, over.result().gatesPassed(), "a plain dive clears no gate");
        assertTrue(over.result().stats().ticksAlive() > 0);
        assertEquals(1, game.runsStarted());

        int frozen = game.run().tick();
        ticks(20);
        assertEquals(frozen, game.run().tick(), "the finished run is frozen under the overlay");

        tap(Keys.SPACE);
        assertSame(game, screens.top(), "retry popped the overlay");
        assertEquals(2, game.runsStarted());
        assertEquals(RunPhase.READY, game.run().phase(), "the retry starts a fresh run");
        assertNotEquals(firstSeed, game.seed(), "the retry uses a new seed");
        assertEquals(firstSeed + 1, game.seed(), "--seed N retries with N+1 (D29)");
    }

    @Test
    void theSkyKeepsDriftingUnderTheGameOverOverlayAndSurvivesTheRetry() {
        // The sky fills while the run waits in READY (6 % per 100 ms, unseeded: bounded wait).
        for (int i = 0; i < 6000 && game.renderer().clouds().size() == 0; i++) {
            ticks(1);
        }
        assertTrue(game.renderer().clouds().size() > 0, "no cloud spawned in 100 s of READY");

        tap(Keys.SPACE);
        runUntilGameOver();
        assertTrue(screens.top() instanceof GameOverOverlay);
        double x = game.renderer().clouds().cloudX(0);
        ticks(10);
        assertEquals(x - 10 * CloudLayer.DEAD_SPEED / Playfield.TICK_RATE,
                game.renderer().clouds().cloudX(0), 1e-9,
                "upstream kept the clouds drifting at 30 px/s on the game-over screen");

        tap(Keys.SPACE);
        assertEquals(2, game.runsStarted());
        assertTrue(game.renderer().clouds().size() > 0,
                "an instant retry must not empty the sky (upstream reset only bird and pipes)");
    }

    @Test
    void escapeOnTheGameOverOverlayReturnsToTheMenu() {
        tap(Keys.SPACE);
        runUntilGameOver();
        assertTrue(screens.top() instanceof GameOverOverlay);
        ticks(GRACE);
        tap(Keys.ESCAPE);
        ticks(1);
        assertSame(menu, screens.top());
        assertEquals(1, screens.depth());
    }

    @Test
    void theGameOverPromptBlinksOnTheUpstreamPeriod() {
        tap(Keys.SPACE);
        runUntilGameOver();
        GameOverOverlay over = (GameOverOverlay) screens.top();
        assertFalse(over.promptVisible(), "the prompt starts hidden, as upstream");
        ticks(60);
        assertTrue(over.promptVisible(), "shown after 60 ticks");
        ticks(60);
        assertFalse(over.promptVisible(), "hidden again after 120");
    }

    // ------------------------------------------------------------------ abilities (M5)

    /**
     * Replaces the screen on the stack with one whose runs carry a loadout, built from the shipped
     * content so the numbers under test are the shipped ones.
     *
     * @param activeAbilityId the active ability to equip
     * @param rules the rules the run carries
     */
    private void openGameWith(String activeAbilityId, RuleSet rules) {
        GameContent content = GameContent.load();
        RunFactory runs = new RunFactory(content);
        SeededRunSource source = seed -> runs.newRun(RunConfig.builder(seed)
                .mode(RunMode.SEEDED)
                .activeAbilityId(activeAbilityId)
                .rules(rules)
                .build());
        screens.pop();
        ticks(GRACE);
        game = new GameScreen(screens, source, SeedSequence.from(42L));
        screens.push(game);
        ticks(GRACE);
    }

    // ------------------------------------------------------------------ drafts (M6)

    /** Gate the M6 test schedule opens its only draft at. */
    private static final int DRAFT_GATE = 2;

    /**
     * Replaces the screen with one whose runs draft from the shipped cards on a flat corridor
     * (E17), so a draft opens within a few hundred ticks instead of at gate 10.
     */
    private void openDraftGame() {
        screens.pop();
        ticks(GRACE);
        game = new GameScreen(screens,
                DraftRuns.source(DraftRuns.catalog(GameContent.load(), DRAFT_GATE, 3)),
                SeedSequence.from(42L));
        screens.push(game);
        ticks(GRACE);
    }

    /** Flies the flat corridor until the draft opens, flapping when the bird sinks. */
    private void flyToDraft() {
        tap(Keys.SPACE);
        for (int i = 0; i < 4000 && !(screens.top() instanceof ModifierChoiceOverlay); i++) {
            if (game.run().simulation().bird().y() > Playfield.BIRD_START_Y + 10) {
                tap(Keys.SPACE);
            } else {
                ticks(1);
            }
        }
        assertTrue(screens.top() instanceof ModifierChoiceOverlay,
                () -> "no draft after 4000 frames; the run is in " + game.run().phase());
    }

    @Test
    void aDraftOpensAtTheScheduledGateAndTheRunResumesAfterTheCountdown() {
        openDraftGame();
        flyToDraft();
        assertEquals(RunPhase.CHOOSING_MODIFIER, game.run().phase());
        assertTrue(game.run().stats().gatesPassed() >= DRAFT_GATE,
                "the draft waited for the scheduled gate");
        assertTrue(game.blocksAutosave(), "an open draft is a live run (D15)");
        assertTrue(screens.top().blocksAutosave(), "and so is the overlay on top of it");

        // The game screen is not ticked while the overlay is up, and the overlay's own ticks move
        // nothing: the draft costs the world no tick (D11).
        ModifierChoiceOverlay overlay = (ModifierChoiceOverlay) screens.top();
        int simTick = game.run().simulation().tick();
        int gates = game.run().stats().gatesPassed();
        ticks(GRACE + 20);
        assertEquals(simTick, game.run().simulation().tick());
        assertEquals(RunPhase.CHOOSING_MODIFIER, game.run().phase());

        tap(Keys.ENTER);
        assertEquals(RunPhase.RESUME_HOLD, game.run().phase(), "Enter took the focused card");
        assertEquals(1, game.run().stats().modifiersTaken().size());
        assertEquals(overlay.takenName(),
                game.renderer().hud().buildChips().get(0), "the HUD shows what was taken");

        for (int i = 0; i < ModifierDirector.RESUME_HOLD_TICKS + 4
                && screens.top() instanceof ModifierChoiceOverlay; i++) {
            ticks(1);
        }
        assertSame(game, screens.top(), "the overlay left when the countdown ended");
        assertEquals(RunPhase.FLYING, game.run().phase());
        assertEquals(simTick, game.run().simulation().tick(),
                "and the whole draft cost the simulation nothing");
        assertEquals(ModifierDirector.RESUME_IFRAMES,
                game.run().simulation().invulnerableTicks(), "the resume grants i-frames");

        // The run really goes on: more gates are passed after the resume.
        for (int i = 0; i < 1500 && game.run().stats().gatesPassed() <= gates; i++) {
            if (game.run().simulation().bird().y() > Playfield.BIRD_START_Y + 10) {
                tap(Keys.SPACE);
            } else {
                ticks(1);
            }
        }
        assertTrue(game.run().stats().gatesPassed() > gates, "the run resumed and kept scoring");
        assertTrue(game.run().simulation().tick() > simTick);
    }

    /**
     * D2, against the phase M6 added: a breather is a live phase — the simulation runs exactly as
     * in {@code FLYING} while the draft waits for clear air — so a lost focus has to pause it. It
     * lasts about four seconds of every schedule entry, which is a tenth of a drafting run that
     * would otherwise be un-pausable and lethal to an alt-tab.
     */
    @Test
    void aFocusLossDuringTheBreatherPausesTheRun() {
        openDraftGame();
        flyUntilPhase(RunPhase.BREATHER);
        int tickOfPause = game.run().tick();

        input.offer(new RawInput.FocusLost(stamp++));
        ticks(1);
        assertTrue(screens.top() instanceof PauseOverlay,
                () -> "a focus loss in " + game.run().phase() + " left the run running");
        ticks(20);
        assertEquals(tickOfPause, game.run().tick(), "the breather is frozen while paused");
        assertEquals(RunPhase.BREATHER, game.run().phase(), "and it is still the same breather");

        tap(Keys.SPACE);
        ticks(GRACE);
        assertSame(game, screens.top(), "the run resumes where it was");
        ticks(5);
        assertTrue(game.run().tick() > tickOfPause, "and the world ticks again");
    }

    /** The same for {@code Esc}, which is the other half of D2's pause rule. */
    @Test
    void escapePausesDuringTheBreatherToo() {
        openDraftGame();
        flyUntilPhase(RunPhase.BREATHER);
        tap(Keys.ESCAPE);
        assertTrue(screens.top() instanceof PauseOverlay,
                () -> "Esc in " + game.run().phase() + " did not pause");
    }

    /** Flies the flat corridor until the run reaches a phase, flapping when the bird sinks. */
    private void flyUntilPhase(RunPhase target) {
        tap(Keys.SPACE);
        for (int i = 0; i < 4000 && game.run().phase() != target; i++) {
            if (game.run().simulation().bird().y() > Playfield.BIRD_START_Y + 10) {
                tap(Keys.SPACE);
            } else {
                ticks(1);
            }
        }
        assertEquals(target, game.run().phase(), "the run never reached " + target);
    }

    @Test
    void anOpenDraftSwallowsTheKeysThatWouldOtherwisePauseOrFlap() {
        openDraftGame();
        flyToDraft();
        ticks(GRACE);
        int flaps = game.run().simulation().flaps();

        input.offer(new RawInput.FocusLost(stamp++));
        ticks(2);
        assertTrue(screens.top() instanceof ModifierChoiceOverlay,
                "a focus loss must not stack a pause overlay on top of a draft");
        assertEquals(flaps, game.run().simulation().flaps());

        tap(Keys.ESCAPE);
        assertEquals(RunPhase.RESUME_HOLD, game.run().phase(), "Esc skips the draft");
        assertEquals(List.of(), game.run().stats().modifiersTaken());
    }

    @Test
    void theAbilityKeyActivatesTheEquippedActiveAbility() {
        openGameWith("double_flap", RuleSet.EMPTY);
        assertEquals("double_flap", game.run().simulation().abilities().active().id());
        assertEquals(2, game.run().simulation().abilities().active().charges(),
                "the double flap ships two charges at level 1");

        tap(Keys.SPACE);
        fly(20);
        double before = game.run().simulation().bird().vy();
        assertTrue(before > 0, "the bird is falling twenty ticks after its flap");

        tap(Keys.X);
        assertEquals(1, game.run().simulation().abilities().active().charges(),
                "the press spent a charge");
        assertEquals(1, game.run().stats().abilitiesUsed().getOrDefault("double_flap", 0),
                "and the run counted the use");
        assertTrue(game.run().simulation().bird().vy() < 0,
                "the double flap cancelled the fall and flapped again (D9)");
        assertEquals("", game.lastAbilityRefusal(), "an accepted press is not a refusal");
        assertFalse(game.renderer().hud().isAbilityRefused());
        assertTrue(game.renderer().hud().isAbilityFlashing(),
                "the HUD flashes the activation");
    }

    @Test
    void theRightMouseButtonActivatesTheAbilityToo() {
        openGameWith("double_flap", RuleSet.EMPTY);
        tap(Keys.SPACE);
        fly(20);
        input.offer(new RawInput.MouseDown(Keys.BUTTON_RIGHT, 200, 300));
        input.offer(new RawInput.MouseUp(Keys.BUTTON_RIGHT, 200, 300));
        ticks(1);
        assertEquals(1, game.run().simulation().abilities().active().charges(),
                "the right button is the ability button (E29)");
    }

    @Test
    void anAbilityOnCooldownIsRefusedWithACue() {
        openGameWith("dash", RuleSet.EMPTY);
        tap(Keys.SPACE);
        fly(10);
        tap(Keys.X);
        assertTrue(game.run().simulation().abilities().active().isActive(), "the dash is running");

        // A second press while the burst runs and the 600-tick cooldown is on buys nothing.
        tap(Keys.X);
        assertEquals(1, game.run().stats().abilitiesUsed().getOrDefault("dash", 0),
                "the second press did not activate anything");
        assertTrue(game.renderer().hud().isAbilityRefused(), "the badge blinks the refusal");
        assertEquals(Strings.active().get(StringKey.TOAST_ABILITY_COOLDOWN),
                game.lastAbilityRefusal());
    }

    @Test
    void anAbilityTheRulesStripIsRefusedWithTheRule() {
        // NO_DEFENSIVE_ABILITIES strips the invulnerability ability outright (D9), so the run
        // carries no active ability at all and the cue has to name the rule, not the cooldown.
        openGameWith("invulnerability", RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES));
        assertNull(game.run().simulation().abilities().active(), "the ability was stripped");
        assertEquals(List.of("invulnerability"), game.run().simulation().abilities().strippedIds());
        assertEquals("", game.renderer().hud().abilityName(),
                "a stripped ability gets no HUD badge");

        tap(Keys.SPACE);
        fly(5);
        tap(Keys.X);
        assertTrue(game.renderer().hud().isAbilityRefused());
        assertEquals(Strings.active().format(StringKey.TOAST_ABILITY_BLOCKED,
                        Strings.active().get(StringKey.RULE_NO_DEFENSIVE_ABILITIES)),
                game.lastAbilityRefusal(),
                "the cue names the rule that took the ability away");
    }

    @Test
    void withoutAnAbilityThePressSaysThereIsNothingToUse() {
        // The default screen of this test class plays the classic seam: no loadout at all.
        tap(Keys.SPACE);
        fly(5);
        tap(Keys.X);
        assertTrue(game.renderer().hud().isAbilityRefused());
        assertEquals(Strings.active().get(StringKey.TOAST_ABILITY_NONE),
                game.lastAbilityRefusal());
        assertEquals("", game.renderer().hud().abilityName());
    }

    @Test
    void holdToFlapIsOffByDefaultAndIssuesSyntheticFlapsWhenOn() {
        assertFalse(game.isHoldToFlap(), "no settings store before M2: hold-to-flap is off");
        input.offer(new RawInput.KeyDown(Keys.SPACE, stamp++));
        ticks(1);
        assertEquals(RunPhase.FLYING, game.run().phase());
        ticks(Playfield.AUTO_FLAP_PERIOD_TICKS + 2);
        assertEquals(1, game.run().simulation().flaps(),
                "a key held down never repeats on its own (upstream's keyFlag)");

        game.setHoldToFlap(true);
        int before = game.run().simulation().flaps();
        ticks(Playfield.AUTO_FLAP_PERIOD_TICKS + 2);
        assertTrue(game.run().simulation().flaps() > before,
                "hold-to-flap issues a synthetic flap every "
                        + Playfield.AUTO_FLAP_PERIOD_TICKS + " ticks");
        input.offer(new RawInput.KeyUp(Keys.SPACE, stamp++));
        ticks(1);
    }

    // ------------------------------------------------------------------ boss flow (M8, D17)

    /** Gate at which the shipped corridor challenge warns its boss. */
    private static final int CORRIDOR_BOSS_GATE = 20;

    /**
     * Replaces the screen with one playing the shipped corridor challenge over its forced pattern
     * — boss at gate {@value #CORRIDOR_BOSS_GATE}, objective "clear the boss" (E17, E26).
     */
    private void openBossChallengeGame() {
        screens.pop();
        ticks(GRACE);
        game = new GameScreen(screens, new ChallengeRunSource(GameContent.load(), null,
                "boss_corridor_1"), SeedSequence.from(42L));
        screens.push(game);
        ticks(GRACE);
        assertEquals("boss_corridor_1", game.run().config().challengeId());
        assertTrue(game.run().config().bossEnabled());
    }

    /**
     * Flies with the perfect pilot, feeding its per-tick decision through the input queue the way
     * a player's key arrives, until the condition holds or the budget runs out.
     *
     * @param done the stop condition, polled before every tick
     * @param limit the tick budget
     * @return {@code true} when the condition held; {@code false} on budget exhaustion or death
     */
    private boolean flyWithTheBotUntil(java.util.function.BooleanSupplier done, int limit) {
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 42L);
        for (int i = 0; i < limit; i++) {
            if (done.getAsBoolean()) {
                return true;
            }
            if (!(screens.top() instanceof GameScreen)) {
                return false;
            }
            if (bot.decide(game.run()).flap()) {
                input.offer(new RawInput.KeyDown(Keys.SPACE, stamp++));
                input.offer(new RawInput.KeyUp(Keys.SPACE, stamp++));
            }
            ticks(1);
        }
        return false;
    }

    /** Draws the game screen once and returns the frame (the HUD texts build during render). */
    private BufferedImage renderFrame() {
        BufferedImage image = new BufferedImage(Playfield.WIDTH, Playfield.HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            game.render(g, 1.0);
        } finally {
            g.dispose();
        }
        return image;
    }

    @Test
    void theBossChallengeTelegraphsOnTheBannerAndTheHudTimer() {
        openBossChallengeGame();
        BossBanner banner = game.bossBanner();
        assertEquals(BossBanner.Phase.HIDDEN, banner.phase(), "nothing announced in READY");

        assertTrue(flyWithTheBotUntil(() -> banner.phase() == BossBanner.Phase.WARNING, 9000),
                () -> "no warning; the run is in " + game.run().phase() + " at gate "
                        + game.run().stats().gatesPassed());
        assertTrue(banner.isVisible());
        assertEquals("Corridor Boss", banner.bossName(), "E26: the challenge owns the encounter");
        assertTrue(banner.line().endsWith("in 2s"), banner.line());

        renderFrame();
        assertFalse(game.renderer().hud().bossText().isEmpty(),
                "the HUD runs its own countdown beside the banner");
        assertEquals(Strings.active().get(StringKey.HUD_OBJECTIVE_BOSS),
                game.renderer().hud().objectiveText());
    }

    @Test
    void survivingTheBossClearsTheObjectiveAndFlashesTheBanner() {
        openBossChallengeGame();
        BossBanner banner = game.bossBanner();
        assertTrue(flyWithTheBotUntil(() -> banner.phase() == BossBanner.Phase.ACTIVE, 9000),
                () -> "the fight never started; the run is in " + game.run().phase() + " at gate "
                        + game.run().stats().gatesPassed());
        assertTrue(game.run().simulation().boss().isFighting());

        assertTrue(flyWithTheBotUntil(() -> banner.phase() == BossBanner.Phase.CLEARED, 4000),
                () -> "the fight never ended; the run is in " + game.run().phase() + " at gate "
                        + game.run().stats().gatesPassed());
        assertTrue(banner.line().endsWith("cleared!"), banner.line());
        renderFrame();
        assertEquals(Strings.active().get(StringKey.HUD_OBJECTIVE_COMPLETE),
                game.renderer().hud().objectiveText(), "the objective latched on the clear");

        assertTrue(flyWithTheBotUntil(() -> !banner.isVisible(), 300),
                "the flash ages out while the flight goes on");
    }
}
