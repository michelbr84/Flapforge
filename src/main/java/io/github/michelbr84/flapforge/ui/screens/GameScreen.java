package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.AssetResolver;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The screen a run is played on (M1). It owns one {@link Run} and one {@link GameRenderer} and
 * does nothing else: every rule lives in the simulation, every pixel in the renderers.
 *
 * <h2>Input (D2)</h2>
 * A flap is a press edge of {@link InputAction#FLAP} or of the left mouse button — never a held
 * key, which is upstream's {@code keyFlag}. Hold-to-flap is an accessibility setting realised
 * inside the simulation ({@code RunInput.autoFlapHeld}); it is read from
 * {@code settings.holdToFlap} when the screen is built from a {@link GameContext}, and defaults
 * to {@code false} otherwise.
 *
 * <h2>Pausing (D2)</h2>
 * While {@code FLYING}, losing the window focus, being iconified or pressing {@code Esc} pushes a
 * {@link PauseOverlay}; resuming needs an explicit key or click and zeroes the loop accumulator so
 * no time accrued while paused is replayed as a burst of ticks.
 *
 * <p>The one focus loss that must <em>not</em> pause is the one the {@code F11} handshake causes:
 * entering or leaving borderless fullscreen disposes and re-shows the frame, so the toolkit
 * delivers a {@code FocusLost} a few ticks later that the player never asked for. The
 * {@link ScreenManager} marks a window of {@value ScreenManager#FULLSCREEN_GRACE_TICKS} ticks
 * after every fullscreen request ({@link ScreenManager#isFullscreenHandshake()}); a focus loss
 * inside that window is ignored. An {@code Esc} press or an iconify is always honoured, since
 * neither can be produced by the handshake.
 *
 * <h2>Game over (D29)</h2>
 * The tick that moves the run to {@code FINISHED} pushes a {@link GameOverOverlay}. Retry starts a
 * new run with the next seed from the {@link SeedSequence} and the same configuration; quitting
 * pops back to the menu.
 */
public final class GameScreen implements Screen {

    private final ScreenManager screens;
    private final GameContext context;
    private final SeededRunSource runFactory;
    private final SeedSequence seeds;
    private final GameRenderer renderer;
    private final Strings strings;
    private final ToastLayer toasts;

    private Run run;
    private long seed;
    private String seedText;
    private String shownLanguage;
    private boolean holdToFlap;
    private boolean gameOverShown;
    private int runsStarted;

    /**
     * Creates a screen playing classic runs with clock-derived seeds.
     *
     * @param screens the screen stack
     */
    public GameScreen(ScreenManager screens) {
        this(screens, null, new ClassicRunFactory(), SeedSequence.random());
    }

    /**
     * Creates a screen.
     *
     * @param screens the screen stack
     * @param runFactory builds each run (the integrator swaps in the content-backed one)
     * @param seeds the seed source
     */
    public GameScreen(ScreenManager screens, SeededRunSource runFactory, SeedSequence seeds) {
        this(screens, null, runFactory, seeds);
    }

    /**
     * Creates a screen for a wired application: the run honours {@code settings.holdToFlap} and
     * the run's facts are published on the presentation bus (D16), which is where the audio
     * manager and the toast layer pick them up.
     *
     * @param context the application services
     * @param runFactory builds each run
     * @param seeds the seed source
     */
    public GameScreen(GameContext context, SeededRunSource runFactory, SeedSequence seeds) {
        this(Objects.requireNonNull(context, "context").screens(), context, runFactory, seeds);
    }

    private GameScreen(ScreenManager screens, GameContext context, SeededRunSource runFactory,
            SeedSequence seeds) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.context = context;
        this.runFactory = Objects.requireNonNull(runFactory, "runFactory");
        this.seeds = Objects.requireNonNull(seeds, "seeds");
        this.strings = context != null && context.strings() != null
                ? context.strings() : Strings.active();
        this.toasts = context != null ? context.toasts() : null;
        this.renderer = new GameRenderer(WorldPalette.GREEN_FIELDS,
                strings.get(StringKey.GAME_READY_HINT));
        this.shownLanguage = strings.language();
        this.holdToFlap = context != null && context.settings().holdToFlap;
        startRun();
    }

    /**
     * The run being played.
     *
     * @return the run, never {@code null}
     */
    public Run run() {
        return run;
    }

    /**
     * Seed of the run being played.
     *
     * @return the seed
     */
    public long seed() {
        return seed;
    }

    /**
     * How many runs this screen has started (1 after construction).
     *
     * @return the count
     */
    public int runsStarted() {
        return runsStarted;
    }

    /**
     * The renderer, for tests inspecting the animation state.
     *
     * @return the renderer
     */
    public GameRenderer renderer() {
        return renderer;
    }

    /**
     * Whether hold-to-flap is engaged (accessibility, D2; a settings flag from M2 on).
     *
     * @return {@code true} when a held flap key issues synthetic flaps
     */
    public boolean isHoldToFlap() {
        return holdToFlap;
    }

    /**
     * Turns hold-to-flap on or off.
     *
     * @param value the new state
     */
    public void setHoldToFlap(boolean value) {
        this.holdToFlap = value;
    }

    /** Starts a fresh run with the next seed (D29: instant retry, new seed, same config). */
    public void restart() {
        startRun();
    }

    private void startRun() {
        seed = seeds.next();
        seedText = seeds.isExplicit() ? strings.format(StringKey.HUD_SEED, seed) : null;
        run = runFactory.newRun(seed);
        renderer.reset();
        // A manifest entry may override the procedural bird per bird and per world (D18); with the
        // shipped empty manifest this resolves to nothing and the art stays procedural.
        renderer.applyAssets(AssetResolver.active(), run.config().birdId(),
                run.config().worldId());
        gameOverShown = false;
        runsStarted++;
        publish(new GameEvent.RunStarted(run.config().birdId(), run.config().worldId(),
                run.config().tierId(), seed));
    }

    private void publish(GameEvent event) {
        if (context != null) {
            context.publish(event);
        }
    }

    /** Re-reads the texts the HUD draws (a live language switch, D25). */
    private void refreshTexts() {
        renderer.setReadyHint(strings.get(StringKey.GAME_READY_HINT));
        seedText = seeds.isExplicit() ? strings.format(StringKey.HUD_SEED, seed) : null;
        shownLanguage = strings.language();
    }

    @Override
    public void onEnter() {
        screens.setLetterboxRgb(renderer.palette().letterbox());
        renderer.resetAll();
        if (context != null) {
            holdToFlap = context.settings().holdToFlap;
        }
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
    }

    @Override
    public void tick(InputFrame input) {
        if (run.isFinished()) {
            // The game-over overlay is on its way onto the stack; freeze until it takes over.
            return;
        }
        if (handleInterrupts(input)) {
            return;
        }
        TickReport report = run.tick(readIntent(input));
        boolean flapped = report.has(TickFact.Flapped.class);
        boolean crashed = report.has(TickFact.Crashed.class);
        renderer.tick(run, flapped, crashed);
        if (toasts != null) {
            toasts.tick();
        }
        publishFacts(report, flapped, crashed);
        if (run.isFinished() && !gameOverShown) {
            gameOverShown = true;
            publish(new GameEvent.RunEnded(run.result().gatesPassed(),
                    (int) Math.round(run.result().stats().points()),
                    run.result().stats().ticksAlive(), false));
            screens.push(new GameOverOverlay(screens, run.result(), this::restart, renderer,
                    strings));
        }
    }

    /**
     * Turns the tick's facts into presentation events (D16, E31.b): gameplay never imports the
     * bus, so this is the one seam where a simulation fact becomes something the audio manager,
     * the toast layer or the debug overlay can hear.
     *
     * <p>Facts are published in the order the simulation produced them, so a shield absorb is
     * heard before the crash it prevented. Several of the cases below cannot fire yet — coins are
     * M3, shields and revives M5, synergies M6, rule shifts M7 — but the mapping is written once,
     * here, so a milestone that adds the fact does not have to remember to add its sound.
     *
     * <p>Three facts stay deliberately unmapped: {@code OfferOpened} (the event carries the
     * offered card ids, which the modifier director owns from M6), and the boss trio (the event
     * carries the boss id, which lives in the world definition from M8). Mapping them now would
     * mean inventing identifiers.
     */
    private void publishFacts(TickReport report, boolean flapped, boolean crashed) {
        if (context == null) {
            return;
        }
        if (flapped) {
            publish(new GameEvent.Flapped(false));
        }
        List<TickFact> facts = report.facts();
        for (int i = 0; i < facts.size(); i++) {
            TickFact fact = facts.get(i);
            if (fact instanceof TickFact.GatePassed gate) {
                publish(new GameEvent.GatePassed(run.stats().gatesPassed(), gate.clean()));
            } else if (fact instanceof TickFact.NearMiss) {
                publish(new GameEvent.NearMiss(run.stats().gatesPassed()));
            } else if (fact instanceof TickFact.CoinCollected coin) {
                publish(new GameEvent.CoinCollected(coin.value(),
                        run.stats().coinsCollected()));
            } else if (fact instanceof TickFact.StreakChanged streak) {
                publish(new GameEvent.StreakChanged(streak.streak(),
                        run.stats().streakSteps()));
            } else if (fact instanceof TickFact.ShieldAbsorbed) {
                publish(new GameEvent.ShieldAbsorbed(
                        remaining(StatId.SHIELD_CHARGES, run.stats().shieldAbsorbs())));
            } else if (fact instanceof TickFact.Revived) {
                publish(new GameEvent.Revived(
                        remaining(StatId.REVIVES, run.stats().revives())));
            } else if (fact instanceof TickFact.SynergyActivated synergy) {
                publish(new GameEvent.SynergyActivated(synergy.id()));
            } else if (fact instanceof TickFact.RuleShift) {
                publish(new GameEvent.RuleShift(activeRuleFlags()));
            }
        }
        if (crashed) {
            publish(new GameEvent.Crashed(causeOf(report), run.stats().gatesPassed()));
        }
    }

    /**
     * Charges of a consumable stat left after this run has spent some of them.
     *
     * @param stat the stat holding the granted amount
     * @param used how many the run has consumed
     * @return the remainder, never negative
     */
    private int remaining(StatId stat, int used) {
        int granted = (int) Math.round(run.simulation().stats().resolve(stat));
        return Math.max(0, granted - used);
    }

    /**
     * The rule flags in force, as the names the event carries.
     *
     * @return the names, in the enum's order
     */
    private List<String> activeRuleFlags() {
        Set<RuleFlag> flags = run.simulation().rules().flags();
        List<String> names = new ArrayList<>(flags.size());
        for (RuleFlag flag : flags) {
            names.add(flag.name());
        }
        return names;
    }

    private static String causeOf(TickReport report) {
        List<TickFact> facts = report.facts();
        for (int i = 0; i < facts.size(); i++) {
            if (facts.get(i) instanceof TickFact.Crashed crash) {
                return crash.cause().name();
            }
        }
        return "";
    }

    private RunInput readIntent(InputFrame input) {
        boolean flap = input.isJustPressed(InputAction.FLAP)
                || input.isMouseJustPressed(Keys.BUTTON_LEFT);
        boolean ability = input.isJustPressed(InputAction.ABILITY)
                || input.isMouseJustPressed(Keys.BUTTON_RIGHT);
        boolean auto = holdToFlap && input.isHeld(InputAction.FLAP);
        return new RunInput(flap, ability, RunInput.NO_CHOICE, auto);
    }

    /**
     * Handles the events that take the screen out of play before the run is ticked.
     *
     * @return {@code true} when the run must not be ticked this tick
     */
    private boolean handleInterrupts(InputFrame input) {
        boolean escape = input.isJustPressed(InputAction.PAUSE);
        boolean lostAttention = false;
        List<RawInput.SystemEvent> events = input.systemEvents();
        for (int i = 0; i < events.size(); i++) {
            RawInput.SystemEvent e = events.get(i);
            if (e instanceof RawInput.Iconified ic) {
                lostAttention |= ic.iconified();
            } else if (e instanceof RawInput.FocusLost && !screens.isFullscreenHandshake()) {
                lostAttention = true;
            }
        }
        if (run.phase() == RunPhase.READY && escape) {
            UiCues.back();
            screens.pop();
            return true;
        }
        if (run.phase() == RunPhase.FLYING && (escape || lostAttention)) {
            screens.push(new PauseOverlay(screens, strings));
            return true;
        }
        return false;
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        renderer.render(g, alpha, run, seedText, screens.isDebugOverlayVisible());
        if (toasts != null) {
            toasts.render(g);
        }
    }
}
