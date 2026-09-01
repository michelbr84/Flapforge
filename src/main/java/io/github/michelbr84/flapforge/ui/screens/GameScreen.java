package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiText;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Objects;

/**
 * The screen a run is played on (M1). It owns one {@link Run} and one {@link GameRenderer} and
 * does nothing else: every rule lives in the simulation, every pixel in the renderers.
 *
 * <h2>Input (D2)</h2>
 * A flap is a press edge of {@link InputAction#FLAP} or of the left mouse button — never a held
 * key, which is upstream's {@code keyFlag}. Hold-to-flap is an accessibility setting realised
 * inside the simulation ({@code RunInput.autoFlapHeld}); there is no settings store before M2, so
 * {@link #setHoldToFlap(boolean)} defaults to {@code false} and only tests turn it on.
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
    private final SeededRunSource runFactory;
    private final SeedSequence seeds;
    private final GameRenderer renderer;

    private Run run;
    private long seed;
    private String seedText;
    private boolean holdToFlap;
    private boolean gameOverShown;
    private int runsStarted;

    /**
     * Creates a screen playing classic runs with clock-derived seeds.
     *
     * @param screens the screen stack
     */
    public GameScreen(ScreenManager screens) {
        this(screens, new ClassicRunFactory(), SeedSequence.random());
    }

    /**
     * Creates a screen.
     *
     * @param screens the screen stack
     * @param runFactory builds each run (the integrator swaps in the content-backed one)
     * @param seeds the seed source
     */
    public GameScreen(ScreenManager screens, SeededRunSource runFactory, SeedSequence seeds) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.runFactory = Objects.requireNonNull(runFactory, "runFactory");
        this.seeds = Objects.requireNonNull(seeds, "seeds");
        this.renderer = new GameRenderer(WorldPalette.GREEN_FIELDS, UiText.READY_HINT);
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
        seedText = seeds.isExplicit() ? UiText.SEED_PREFIX + seed : null;
        run = runFactory.newRun(seed);
        renderer.reset();
        gameOverShown = false;
        runsStarted++;
    }

    @Override
    public void onEnter() {
        screens.setLetterboxRgb(renderer.palette().letterbox());
        renderer.resetAll();
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
        renderer.tick(run, report.has(TickFact.Flapped.class));
        if (run.isFinished() && !gameOverShown) {
            gameOverShown = true;
            screens.push(new GameOverOverlay(screens, run.result(), this::restart, renderer));
        }
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
            screens.pop();
            return true;
        }
        if (run.phase() == RunPhase.FLYING && (escape || lostAttention)) {
            screens.push(new PauseOverlay(screens));
            return true;
        }
        return false;
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        renderer.render(g, alpha, run, seedText, screens.isDebugOverlayVisible());
    }
}
