package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.CloudLayer;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
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
}
