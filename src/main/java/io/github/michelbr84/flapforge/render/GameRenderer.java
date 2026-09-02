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
 * <p>Draw order: backdrop (sky, hills, ground), clouds, obstacles, coins, bird, HUD. Upstream
 * drew its clouds as a "foreground" layer between background and pipes; keeping them behind the pipes is
 * the same visual result (clouds live in the top third and pipes are opaque) and avoids clouds
 * crossing in front of a gate the player is aiming at.
 *
 * <p>Art comes from {@link ProceduralArt} unless {@code assets/manifest.json} declares an
 * override for the id, which {@link #applyAssets(AssetResolver, String, String)} resolves once per
 * run (D18). The shipped manifest is empty, so the shipped game is fully procedural.
 *
 * <p>{@link #tick(Run, boolean)} advances every animation by one simulation tick;
 * {@link #render(Graphics2D, double, Run, String, boolean)} draws with the frame alpha, so the
 * whole picture interpolates consistently (E30.g). All state lives here, so a screen only has to
 * call the two methods and {@link #reset()} between runs.
 */
public final class GameRenderer {

    /** Shake a crash asks the camera for, in logical pixels. */
    public static final double CRASH_SHAKE = 5.0;
    /** Manifest id of the bird's wing sheet, tried as {@code bird/&lt;birdId&gt;} first. */
    public static final String BIRD_SHEET_ID = "bird";

    private final WorldPalette palette;
    private final BackgroundRenderer background = new BackgroundRenderer();
    private final CloudLayer clouds = new CloudLayer();
    private final ObstacleRenderer obstacles = new ObstacleRenderer();
    private final PickupRenderer pickups = new PickupRenderer();
    private final BirdRenderer bird = new BirdRenderer();
    private final HudRenderer hud;
    private final ParticleSystem particles = new ParticleSystem();
    private final Camera camera = new Camera();

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
     * The particle pool the flap puffs and the crash burst come from.
     *
     * @return the pool
     */
    public ParticleSystem particles() {
        return particles;
    }

    /**
     * The camera: it only shakes, it never moves the playfield (D18).
     *
     * @return the camera
     */
    public Camera camera() {
        return camera;
    }

    /**
     * Resolves the art this renderer may override with a real sprite sheet, and installs it.
     *
     * <p>The lookup is {@code bird/&lt;birdId&gt;} then the bare {@code bird}, each of them first
     * as a world override (D18). Nothing is found with the shipped empty manifest, so the bird
     * keeps being drawn by {@link ProceduralArt}; dropping a sheet into the manifest is all it
     * takes to replace it.
     *
     * @param resolver the resolver, normally {@link AssetResolver#active()}
     * @param birdId the bird being flown, may be {@code null}
     * @param worldId the world being flown in, may be {@code null}
     */
    public void applyAssets(AssetResolver resolver, String birdId, String worldId) {
        if (resolver == null) {
            bird.setSheet(null);
            return;
        }
        SpriteSheet sheet = null;
        if (birdId != null && !birdId.isBlank()) {
            sheet = resolver.sheet(BIRD_SHEET_ID + "/" + birdId, worldId).orElse(null);
        }
        if (sheet == null) {
            sheet = resolver.sheet(BIRD_SHEET_ID, worldId).orElse(null);
        }
        bird.setSheet(sheet);
    }

    /**
     * Replaces the HUD's streak template (a live language switch, D25).
     *
     * @param streakLabel the translated {@code hud.streak} pattern, {@code {0}} the length
     */
    public void setStreakLabel(String streakLabel) {
        hud.setStreakLabel(streakLabel);
    }

    /**
     * Replaces the READY hint (a live language switch, D25).
     *
     * @param readyHint the new text
     */
    public void setReadyHint(String readyHint) {
        hud.setReadyHint(readyHint);
    }

    /**
     * Replaces the HUD's coin-counter template (a live language switch, D25).
     *
     * @param coinLabel the translated {@code hud.coins} pattern, {@code {0}} the count
     */
    public void setCoinLabel(String coinLabel) {
        hud.setCoinLabel(coinLabel);
    }

    /**
     * Tells the HUD which streak length pays a reward step, so it can mark it (E32.a).
     *
     * @param step {@code economy.rewards.streak.step}
     */
    public void setStreakStep(int step) {
        hud.setStreakStep(step);
    }

    /**
     * Names the equipped active ability on the HUD badge (M5, D25); an empty name hides it.
     *
     * @param name the translated ability name, or {@code null} for none
     */
    public void setAbilityName(String name) {
        hud.setAbilityName(name);
    }

    /**
     * Replaces the HUD's ability state labels (a live language switch, D25).
     *
     * @param readyLabel the translated {@code hud.ability.ready} word
     * @param cooldownLabel the translated {@code hud.ability.cooldown} pattern
     */
    public void setAbilityStateLabels(String readyLabel, String cooldownLabel) {
        hud.setAbilityStateLabels(readyLabel, cooldownLabel);
    }

    /**
     * Replaces the HUD's shield readout template (a live language switch, D25).
     *
     * @param shieldLabel the translated {@code hud.shield.charges} pattern
     */
    public void setShieldLabel(String shieldLabel) {
        hud.setShieldLabel(shieldLabel);
    }

    /**
     * The coin renderer (its spin and flourish state).
     *
     * @return the pickup renderer
     */
    public PickupRenderer pickups() {
        return pickups;
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
        tick(run, flapped, false);
    }

    /**
     * Advances every animation by one simulation tick, including the decorations a tick's facts
     * ask for: a puff behind a flap, and a burst plus a short camera shake on a crash.
     *
     * @param run the run being played
     * @param flapped {@code true} when this tick produced a {@code Flapped} fact
     * @param crashed {@code true} when this tick produced a {@code Crashed} fact
     */
    public void tick(Run run, boolean flapped, boolean crashed) {
        RunPhase phase = run.phase();
        boolean frozen = phase == RunPhase.DYING || phase == RunPhase.FINISHED;
        double scroll = scrollPerTick(run);
        background.tick(scroll, frozen);
        clouds.tick(scroll, frozen);
        bird.tick(flapped);
        // The coins are ticked before the HUD so a coin taken this tick has already produced its
        // flourish when the counter next to the icon changes.
        pickups.tick(run.simulation().pickups(), particles);
        hud.tick(run);
        camera.tick();
        particles.update(1.0 / Playfield.TICK_RATE);
        double birdY = run.simulation().bird().y();
        if (flapped) {
            particles.emitFlapPuff(Playfield.BIRD_X - Playfield.SPRITE_W * 0.35, birdY);
        }
        if (crashed) {
            particles.emitCrashBurst(Playfield.BIRD_X, birdY, palette.accent());
            camera.shake(CRASH_SHAKE);
        }
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
        camera.tick();
        particles.update(1.0 / Playfield.TICK_RATE);
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
        pickups.reset();
        particles.clear();
        camera.reset();
        particles.setReduceFlashing(ParticleSystem.defaultReduceFlashing());
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
        // The backdrop is drawn unshaken so a shake never uncovers the letterbox behind it; only
        // the things the player is aiming at move.
        background.render(g, alpha, palette);
        clouds.render(g, alpha, palette);
        camera.apply(g, alpha);
        obstacles.render(g, alpha, run.simulation().obstacles(), palette, debugBoxes);
        pickups.render(g, alpha, run.simulation().pickups(), debugBoxes);
        Bird b = run.simulation().bird();
        double hitboxScale = run.simulation().stats().resolve(StatId.HITBOX_SCALE);
        bird.render(g, alpha, b, palette, hitboxScale, debugBoxes);
        particles.render(g);
        camera.unapply(g);
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
