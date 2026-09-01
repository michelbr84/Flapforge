package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.awt.Graphics2D;
import java.util.Objects;

/**
 * Composes the whole in-run picture from the M1 renderers (D18).
 *
 * <p>Draw order: backdrop (sky, hills, ground), clouds, obstacles, bird, HUD. Upstream drew its
 * clouds as a "foreground" layer between background and pipes; keeping them behind the pipes is
 * the same visual result (clouds live in the top third and pipes are opaque) and avoids clouds
 * crossing in front of a gate the player is aiming at.
 *
 * <p>{@link #tick(Run, boolean)} advances every animation by one simulation tick;
 * {@link #render(Graphics2D, double, Run, String, boolean)} draws with the frame alpha, so the
 * whole picture interpolates consistently (E30.g). All state lives here, so a screen only has to
 * call the two methods and {@link #reset()} between runs.
 */
public final class GameRenderer {

    private final WorldPalette palette;
    private final BackgroundRenderer background = new BackgroundRenderer();
    private final CloudLayer clouds = new CloudLayer();
    private final ObstacleRenderer obstacles = new ObstacleRenderer();
    private final BirdRenderer bird = new BirdRenderer();
    private final HudRenderer hud;

    /**
     * Creates a renderer for one world.
     *
     * @param palette the world palette
     * @param readyHint the text blinked while the run waits for its first flap
     */
    public GameRenderer(WorldPalette palette, String readyHint) {
        this.palette = Objects.requireNonNull(palette, "palette");
        this.hud = new HudRenderer(readyHint);
    }

    /**
     * The palette in use.
     *
     * @return the palette
     */
    public WorldPalette palette() {
        return palette;
    }

    /**
     * The backdrop layer.
     *
     * @return the backdrop
     */
    public BackgroundRenderer background() {
        return background;
    }

    /**
     * The cloud layer.
     *
     * @return the clouds
     */
    public CloudLayer clouds() {
        return clouds;
    }

    /**
     * The bird renderer (its wing animation).
     *
     * @return the bird renderer
     */
    public BirdRenderer bird() {
        return bird;
    }

    /**
     * The HUD.
     *
     * @return the HUD
     */
    public HudRenderer hud() {
        return hud;
    }

    /**
     * Advances every animation by one simulation tick.
     *
     * <p>The ground and the hills scroll while the run is {@code READY} or flying and stop in
     * {@code DYING}/{@code FINISHED}, exactly as upstream's background did; the clouds keep
     * drifting at their reduced speed once the bird is dead.
     *
     * @param run the run being played
     * @param flapped {@code true} when this tick produced a {@code Flapped} fact
     */
    public void tick(Run run, boolean flapped) {
        RunPhase phase = run.phase();
        boolean frozen = phase == RunPhase.DYING || phase == RunPhase.FINISHED;
        double scroll = scrollPerTick(run);
        background.tick(scroll, frozen);
        clouds.tick(scroll, frozen);
        bird.tick(flapped);
        hud.tick();
    }

    /**
     * Advances only what keeps moving after the run ended: the clouds, at their dead drift.
     *
     * <p>Upstream returned early from {@code GameBackground} and the pipe layer on the game-over
     * screen but kept drawing {@code GameForeground}, so the sky went on drifting at 1 px/frame
     * behind the game-over art. {@code GameOverOverlay} calls this for the same effect, because
     * the {@link io.github.michelbr84.flapforge.ui.ScreenManager} only ticks the top screen.
     */
    public void tickFrozen() {
        clouds.tick(0, true);
    }

    /**
     * Puts the per-run animations back to their start state (a new run).
     *
     * <p>The cloud layer is deliberately left alone: upstream's {@code resetGame()} reset the bird
     * and the pipes but not {@code GameForeground}, so the sky never emptied between attempts.
     * Clearing it here would leave every instant retry (D29) starting under a blank sky that takes
     * about 17 s to refill at 6 % per 100 ms. {@link #resetAll()} is the full reset.
     */
    public void reset() {
        background.reset();
        bird.reset();
        hud.reset();
    }

    /** {@link #reset()} plus an empty sky: the state a screen starts from when it is entered. */
    public void resetAll() {
        reset();
        clouds.reset();
    }

    /**
     * Draws the frame.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param run the run being played
     * @param seedText the pre-formatted seed line, or {@code null} when the run is not seeded
     * @param debugBoxes {@code true} to outline the hitboxes ({@code F3})
     */
    public void render(Graphics2D g, double alpha, Run run, String seedText, boolean debugBoxes) {
        ProceduralArt.prepare(g);
        background.render(g, alpha, palette);
        clouds.render(g, alpha, palette);
        obstacles.render(g, alpha, run.simulation().obstacles(), palette, debugBoxes);
        Bird b = run.simulation().bird();
        double hitboxScale = run.simulation().stats().resolve(StatId.HITBOX_SCALE);
        bird.render(g, alpha, b, palette, hitboxScale, debugBoxes);
        hud.render(g, run, palette, seedText);
    }

    /**
     * World scroll of one tick for a run, honouring {@code TIME_SCALE}.
     *
     * @param run the run
     * @return the displacement in px
     */
    public static double scrollPerTick(Run run) {
        return run.simulation().stats().resolve(StatId.SCROLL_SPEED)
                * run.simulation().stats().resolve(StatId.TIME_SCALE) / Playfield.TICK_RATE;
    }
}
