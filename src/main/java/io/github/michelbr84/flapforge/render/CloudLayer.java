package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decorative clouds, reproducing upstream's {@code GameForeground} + {@code Cloud} (plan
 * section 5 cosmetic rows).
 *
 * <p>Upstream ran the spawn logic every 100 ms of wall time: with at most
 * {@link Playfield#CLOUD_MAX} clouds alive it added one with {@link Playfield#CLOUD_SPAWN_PCT}
 * probability at {@code x = 420} and {@code y} in the top third of the screen, picking one of two
 * images and a scale in {@code [1, 2)}; clouds moved at {@code GAME_SPEED x 2} and slowed to
 * 1 px/frame once the bird died. Flapforge keeps every number, drives the 100 ms period from the
 * presentation clock as {@value #SPAWN_INTERVAL_TICKS} ticks (100 ms at 60 Hz) so no wall clock
 * enters the loop, and draws the two silhouettes procedurally.
 *
 * <p>Upstream's {@code GAME_SPEED} was a constant, so {@code x 2} could be baked in. Flapforge's
 * scroll is a resolved stat — the hard and nightmare tiers multiply {@code SCROLL_SPEED}, Slow
 * Time scales {@code TIME_SCALE} — so {@link #tick(double, boolean)} takes the run's ground
 * scroll and derives the cloud step from it ({@link Playfield#CLOUD_SPEED_FACTOR} times the
 * ground). Otherwise the sky would keep full speed while the world slowed down. The dead drift
 * stays absolute, as upstream's {@code speed = 1} was.
 *
 * <p>This is render-side decoration, so it owns an <em>unseeded</em> {@link Random} and never
 * touches the run's seeded streams (D12): clouds must not be able to change a simulation. Each
 * cloud keeps its previous x so {@link #render} can interpolate with the frame alpha; nothing is
 * allocated per frame.
 */
public final class CloudLayer {

    /**
     * Cloud speed in px/s at the classic ground scroll (120 px/s): twice the ground, as upstream.
     * The layer derives its actual step from the scroll it is ticked with; this is the reference
     * value the cosmetic row of {@code docs/BALANCING.md} quotes.
     */
    public static final double SPEED = Playfield.CLOUD_SPEED_FACTOR * 120.0;
    /** Cloud speed in px/s once the bird is dead (upstream's 1 px per 30 Hz frame). */
    public static final double DEAD_SPEED = 30.0;
    /** Ticks between spawn checks: 100 ms at 60 Hz. */
    public static final int SPAWN_INTERVAL_TICKS = Playfield.TICK_RATE / 10;
    /** Lowest y a cloud can spawn at (upstream's {@code TOP_BAR_HEIGHT}). */
    public static final int SPAWN_TOP_Y = 20;
    /** Exclusive lower bound of the spawn band: the top third of the playfield. */
    public static final int SPAWN_BOTTOM_Y = Playfield.HEIGHT / 3;

    private static final double BASE_W_0 = 48;
    private static final double BASE_H_0 = 33;
    private static final double BASE_W_1 = 40;
    private static final double BASE_H_1 = 32;

    /** One live cloud. */
    private static final class Cloud {
        double x;
        double prevX;
        double y;
        double w;
        double h;
        int variant;
    }

    private final Random rng;
    private final List<Cloud> clouds = new ArrayList<>(Playfield.CLOUD_MAX);
    private final Ellipse2D.Double scratch = new Ellipse2D.Double();
    private int spawnTimer;

    /** Creates an empty layer with its own unseeded random source. */
    public CloudLayer() {
        this(new Random());
    }

    /**
     * Creates an empty layer with an explicit random source (tests only; production uses the
     * unseeded constructor so clouds never correlate with the run seed).
     *
     * @param rng the random source
     */
    public CloudLayer(Random rng) {
        this.rng = rng;
    }

    /**
     * Advances every cloud by one tick and runs the spawn check every
     * {@value #SPAWN_INTERVAL_TICKS} ticks.
     *
     * @param groundScrollPerTick the world scroll of this tick in px; clouds move
     *     {@link Playfield#CLOUD_SPEED_FACTOR} times as fast. Ignored when {@code birdDead}
     * @param birdDead {@code true} once the bird died: clouds drift at {@link #DEAD_SPEED}
     *     regardless of the world scroll, which has stopped
     */
    public void tick(double groundScrollPerTick, boolean birdDead) {
        double step = birdDead ? DEAD_SPEED / Playfield.TICK_RATE
                : groundScrollPerTick * Playfield.CLOUD_SPEED_FACTOR;
        for (int i = clouds.size() - 1; i >= 0; i--) {
            Cloud c = clouds.get(i);
            c.prevX = c.x;
            c.x -= step;
            if (c.x < -c.w) {
                clouds.remove(i);
            }
        }
        if (++spawnTimer < SPAWN_INTERVAL_TICKS) {
            return;
        }
        spawnTimer = 0;
        if (clouds.size() >= Playfield.CLOUD_MAX) {
            return;
        }
        if (rng.nextInt(100) < Playfield.CLOUD_SPAWN_PCT) {
            spawn();
        }
    }

    private void spawn() {
        Cloud c = new Cloud();
        c.variant = rng.nextInt(2);
        double scale = 1 + rng.nextDouble();
        c.w = (c.variant == 0 ? BASE_W_0 : BASE_W_1) * scale;
        c.h = (c.variant == 0 ? BASE_H_0 : BASE_H_1) * scale;
        c.x = Playfield.WIDTH;
        c.prevX = c.x;
        c.y = SPAWN_TOP_Y + rng.nextInt(SPAWN_BOTTOM_Y - SPAWN_TOP_Y);
        clouds.add(c);
    }

    /** Removes every cloud and restarts the spawn period (a new run). */
    public void reset() {
        clouds.clear();
        spawnTimer = 0;
    }

    /**
     * Number of live clouds.
     *
     * @return the count, never above {@link Playfield#CLOUD_MAX}
     */
    public int size() {
        return clouds.size();
    }

    /**
     * The current x of one cloud, oldest first (the cosmetic tests measure the step with it).
     *
     * @param index the cloud index
     * @return the x in logical pixels
     */
    public double cloudX(int index) {
        return clouds.get(index).x;
    }

    /**
     * Draws every cloud, interpolated between the previous and the current tick.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param palette the world palette (clouds use its fog tone)
     */
    public void render(Graphics2D g, double alpha, WorldPalette palette) {
        if (clouds.isEmpty()) {
            return;
        }
        g.setColor(ProceduralArt.color(palette, ProceduralArt.Tone.CLOUD));
        for (int i = 0; i < clouds.size(); i++) {
            Cloud c = clouds.get(i);
            double x = MathUtil.lerp(c.prevX, c.x, alpha);
            ProceduralArt.drawCloud(g, scratch, x, c.y, c.w, c.h, c.variant);
        }
    }
}
