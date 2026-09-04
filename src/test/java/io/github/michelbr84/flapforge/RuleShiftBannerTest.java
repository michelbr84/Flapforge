package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.FixedSpawnTable;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import io.github.michelbr84.flapforge.ui.screens.RuleShiftBanner;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.SeededRunSource;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The rule-shift banner (M7, D17): it appears on the fact, names the option in words, counts the
 * telegraph down in seconds, holds at "now" while the simulation defers the landing, flashes
 * "in effect" once the shift lands, and — being a banner rather than a screen — consumes no
 * input: the run keeps flying and every key still reaches it.
 */
class RuleShiftBannerTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    private long stamp = 1;

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
    }

    private static TickFact.RuleShift shift(int telegraph) {
        return new TickFact.RuleShift(List.of(RuleFlag.ALL_OBSTACLES_MOVE),
                List.of(new StatModifier(StatId.GAP_SIZE, StatOp.MULTIPLY, 0.85, "cycle")),
                telegraph);
    }

    @Test
    void appearsOnTheFactNamesTheOptionAndCountsDown() {
        Strings strings = Strings.load("en");
        RuleShiftBanner banner = new RuleShiftBanner(strings);
        assertFalse(banner.isVisible());
        assertEquals("", banner.line());

        banner.announce(shift(90));
        assertTrue(banner.isVisible(), "the banner is up on the fact");
        assertEquals(RuleShiftBanner.Phase.TELEGRAPH, banner.phase());
        assertEquals(90, banner.remaining());
        assertEquals(1, banner.announcements());
        String flag = strings.get(StringKey.RULE_ALL_OBSTACLES_MOVE);
        assertTrue(banner.ruleText().contains(flag), banner.ruleText());
        assertTrue(banner.ruleText().contains("0.85"), "the effect is spelt out: "
                + banner.ruleText());
        assertEquals(strings.format(StringKey.RULE_SHIFT_COUNTDOWN, banner.ruleText(), 2),
                banner.line(), "90 ticks is 2 s, rounded up");

        for (int i = 0; i < 30; i++) {
            banner.tick();
        }
        assertEquals(60, banner.remaining());
        assertEquals(strings.format(StringKey.RULE_SHIFT_COUNTDOWN, banner.ruleText(), 1),
                banner.line(), "60 ticks is 1 s");

        for (int i = 0; i < 60; i++) {
            banner.tick();
        }
        assertEquals(RuleShiftBanner.Phase.IN_EFFECT, banner.phase(),
                "the countdown ran out and the shift landed");
        assertEquals(strings.format(StringKey.RULE_SHIFT_IN_EFFECT, banner.ruleText()),
                banner.line());
        for (int i = 0; i < RuleShiftBanner.IN_EFFECT_TICKS; i++) {
            banner.tick();
        }
        assertEquals(RuleShiftBanner.Phase.HIDDEN, banner.phase(), "the flash is over");
        assertFalse(banner.isVisible());
    }

    @Test
    void holdsAtNowWhileTheSimulationDefersTheLandingAndLandsOnCommand() {
        Strings strings = Strings.load("en");
        RuleShiftBanner banner = new RuleShiftBanner(strings);
        banner.announce(shift(2));
        banner.tick(true);
        banner.tick(true);
        assertEquals(0, banner.remaining());
        assertEquals(RuleShiftBanner.Phase.TELEGRAPH, banner.phase(),
                "a deferred landing keeps the telegraph up");
        assertEquals(strings.format(StringKey.RULE_SHIFT_NOW, banner.ruleText()),
                banner.line());
        for (int i = 0; i < 40; i++) {
            banner.tick(true);
        }
        assertEquals(RuleShiftBanner.Phase.TELEGRAPH, banner.phase(), "still deferred");

        banner.land();
        assertEquals(RuleShiftBanner.Phase.IN_EFFECT, banner.phase());
        banner.reset();
        assertFalse(banner.isVisible());
        assertEquals("", banner.ruleText());
    }

    @Test
    void namesTheOptionInBothLanguages() {
        RuleShiftBanner en = new RuleShiftBanner(Strings.load("en"));
        RuleShiftBanner pt = new RuleShiftBanner(Strings.load("pt_BR"));
        TickFact.RuleShift fact = new TickFact.RuleShift(List.of(RuleFlag.LETHAL_CEILING),
                List.of(), 90);
        en.announce(fact);
        pt.announce(fact);
        assertEquals("Lethal ceiling", en.ruleText());
        assertEquals("Teto letal", pt.ruleText());
        assertFalse(en.line().equals(pt.line()));
        assertEquals(Strings.load("pt_BR").get(StringKey.RULE_SHIFT_TITLE), "Mudança de regra");
    }

    @Test
    void doesNotConsumeInputWhileTheRunKeepsFlying() {
        Strings.use(Strings.load("en"));
        ManualClock clock = new ManualClock(1_000_000_000L);
        InputQueue input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter();
        screens.setPresenter(presenter);
        GameLoop loop = new GameLoop(clock, input, screens, presenter,
                FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);
        GameScreen game = new GameScreen(screens, new ClassicRunFactory(RunMode.SEEDED),
                SeedSequence.of(42));
        screens.push(game);
        screens.applyPending();
        loop.start();
        ticks(clock, loop, GRACE);

        game.banner().announce(shift(90));
        assertTrue(game.banner().isVisible());
        // A flap through the queue reaches the run with the banner up: it is not a screen on
        // the stack, so nothing sits between the key and the simulation.
        tap(clock, loop, input, Keys.SPACE);
        assertEquals(RunPhase.FLYING, game.run().phase(), "the flap started the run");
        assertEquals(1, game.run().simulation().flaps());
        assertTrue(game.banner().isVisible(), "and the banner is still up");
        int before = game.banner().remaining();
        ticks(clock, loop, 10);
        assertEquals(before - 10, game.banner().remaining(), "the countdown follows the run");
        // The pause key still pauses: the banner never takes a key.
        tap(clock, loop, input, Keys.ESCAPE);
        assertTrue(screens.top() instanceof PauseOverlay, "Esc reached the game screen");
        int frozen = game.banner().remaining();
        ticks(clock, loop, 10);
        assertEquals(frozen, game.banner().remaining(), "a paused run pauses the countdown too");
    }

    /**
     * The banner against the real thing: a run in the Void on a flat corridor passes its fifth
     * gate, the simulation announces a shift, the screen opens the banner on that fact and lands
     * it exactly when the world effects say so.
     */
    @Test
    void opensOnTheVoidsOwnRuleShiftAndLandsWithTheSimulation() {
        Strings.use(Strings.load("en"));
        GameContent content = GameContent.load();
        RunFactory factory = new RunFactory(content);
        SeededRunSource voidRuns = seed -> {
            RunConfig config = RunConfig.builder(seed).mode(RunMode.SEEDED).worldId("void")
                    .build();
            return new Run(config, factory.setup(config), new FixedSpawnTable());
        };
        ManualClock clock = new ManualClock(1_000_000_000L);
        InputQueue input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter();
        screens.setPresenter(presenter);
        GameLoop loop = new GameLoop(clock, input, screens, presenter,
                FrameLimiter.uncapped(clock));
        GameScreen game = new GameScreen(screens, voidRuns, SeedSequence.of(42));
        screens.push(game);
        screens.applyPending();
        loop.start();
        ticks(clock, loop, GRACE);
        assertEquals("void", game.run().config().worldId());

        tap(clock, loop, input, Keys.SPACE);
        int flew = 0;
        while (flew < 6000 && !game.banner().isVisible() && !game.run().isFinished()) {
            if (game.run().simulation().bird().y() > Playfield.BIRD_START_Y + 10) {
                tap(clock, loop, input, Keys.SPACE);
            } else {
                ticks(clock, loop, 1);
            }
            flew++;
        }
        int ticksFlown = flew;
        assertTrue(game.banner().isVisible(), () -> "no banner after " + ticksFlown
                + " ticks; gates " + game.run().stats().gatesPassed() + ", phase "
                + game.run().phase());
        assertEquals(5, game.run().stats().gatesPassed(), "the Void shifts every 5 gates");
        assertEquals(90, game.banner().telegraphTicks(), "the authored telegraph");
        assertTrue(game.run().simulation().worldEffects().isTelegraphing());

        int guard = 0;
        while (guard++ < 400 && game.banner().phase() == RuleShiftBanner.Phase.TELEGRAPH
                && !game.run().isFinished()) {
            if (game.run().simulation().bird().y() > Playfield.BIRD_START_Y + 10) {
                tap(clock, loop, input, Keys.SPACE);
            } else {
                ticks(clock, loop, 1);
            }
        }
        assertEquals(RuleShiftBanner.Phase.IN_EFFECT, game.banner().phase(),
                () -> "the banner should have landed; run " + game.run().phase());
        assertEquals(1, game.run().simulation().worldEffects().shifts(),
                "and the simulation landed the same shift");
    }

    private void ticks(ManualClock clock, GameLoop loop, int n) {
        for (int i = 0; i < n; i++) {
            clock.advance(Playfield.TICK_NS);
            loop.frame();
        }
    }

    private void tap(ManualClock clock, GameLoop loop, InputQueue input, int keyCode) {
        input.offer(new RawInput.KeyDown(keyCode, stamp++));
        input.offer(new RawInput.KeyUp(keyCode, stamp++));
        ticks(clock, loop, 1);
    }
}
