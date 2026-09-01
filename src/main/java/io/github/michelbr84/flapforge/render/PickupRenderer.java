package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.pickup.Coin;
import io.github.michelbr84.flapforge.gameplay.pickup.PickupLayer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.util.List;

/**
 * Draws the coins of a run (D18, M3): each one a gold disc turning on its vertical axis, drawn
 * between its previous and its current tick position with the frame alpha (E30.g).
 *
 * <p>Three properties are load-bearing:
 * <ul>
 *   <li><b>The spin is a function of the simulation tick</b>, not of wall-clock time or of the
 *       number of frames drawn, so two captures of the same tick are pixel-identical — which is
 *       what {@code ProceduralRenderTest} and the smoke screenshots rely on. All live coins turn
 *       in step: a per-coin offset would have to come from an identity the pure {@link Coin} does
 *       not carry, and inventing one from its position would make the phase jitter as the magnet
 *       moves it.</li>
 *   <li><b>Nothing is allocated per frame.</b> One {@link Ellipse2D} is reused for every coin and
 *       for the hitbox outline, and the squash comes from {@link ProceduralArt#coinSpin(long)}'s
 *       table.</li>
 *   <li><b>The pickup is visible.</b> A coin the bird takes is dropped from the layer on the next
 *       simulation tick, so it would simply blink out; {@link #tick(PickupLayer, ParticleSystem)}
 *       turns each one into a flourish in the shared particle pool instead. What decides that a
 *       pickup is new is the layer's own {@code collectedCount()}: only a tick that raised it
 *       emits, so the coins still marked collected in the list cannot produce a second flourish
 *       whatever order the caller ticks the model and the renderer in.</li>
 * </ul>
 *
 * <p>The magnet needs no code here: it moves the model's position, and the interpolation between
 * {@link Coin#prevX()} and {@link Coin#x()} is what makes the coin's flight to the bird smooth at
 * any frame rate.
 */
public final class PickupRenderer {

    /** Ticks one coin needs for a full turn. */
    public static final int SPIN_TICKS = ProceduralArt.COIN_SPIN_TICKS;

    private static final Color HITBOX = new Color(0xFF, 0xD1, 0x3B, 0xC0);
    private static final Stroke OUTLINE = new BasicStroke(1f);

    private final Ellipse2D.Double disc = new Ellipse2D.Double();
    private long ticks;
    private int lastCollected;
    private int flourishes;

    /** Creates a renderer. */
    public PickupRenderer() {
    }

    /**
     * Advances the spin by one simulation tick and turns every coin collected in that tick into a
     * flourish.
     *
     * @param layer the live coins
     * @param particles the pool the flourish is emitted into, or {@code null} for none
     */
    public void tick(PickupLayer layer, ParticleSystem particles) {
        ticks++;
        if (layer == null) {
            return;
        }
        // The layer's own counter says whether anything was picked up since the last tick, so a
        // coin whose corpse is still in the list (the layer drops it on its next update) can never
        // be counted twice, however the caller schedules the two.
        int collected = layer.collectedCount();
        if (collected <= lastCollected) {
            lastCollected = collected;
            return;
        }
        lastCollected = collected;
        if (particles == null) {
            return;
        }
        List<Coin> coins = layer.coins();
        for (int i = 0; i < coins.size(); i++) {
            Coin coin = coins.get(i);
            if (coin.isCollected()) {
                particles.emitCoinPickup(coin.x(), coin.y());
                flourishes++;
            }
        }
    }

    /** Puts the spin and the flourish counter back to their start state (a new run). */
    public void reset() {
        ticks = 0;
        lastCollected = 0;
        flourishes = 0;
    }

    /**
     * The animation phase, in simulation ticks since the last {@link #reset()}.
     *
     * @return the tick count
     */
    public long ticks() {
        return ticks;
    }

    /**
     * How many collect flourishes have been emitted (tests).
     *
     * @return the count
     */
    public int flourishes() {
        return flourishes;
    }

    /**
     * Draws every live coin.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param layer the live coins
     * @param debugHitboxes {@code true} to outline the pickup radius ({@code F3})
     */
    public void render(Graphics2D g, double alpha, PickupLayer layer, boolean debugHitboxes) {
        List<Coin> coins = layer.coins();
        if (coins.isEmpty()) {
            return;
        }
        double spin = ProceduralArt.coinSpin(ticks);
        for (int i = 0; i < coins.size(); i++) {
            Coin coin = coins.get(i);
            if (coin.isCollected()) {
                // Taken this tick: the flourish stands in for it, so the pickup reads as instant.
                continue;
            }
            double x = MathUtil.lerp(coin.prevX(), coin.x(), alpha);
            double y = MathUtil.lerp(coin.prevY(), coin.y(), alpha);
            ProceduralArt.drawCoin(g, disc, x, y, Coin.RADIUS, spin);
            if (debugHitboxes) {
                Stroke old = g.getStroke();
                g.setStroke(OUTLINE);
                g.setColor(HITBOX);
                disc.setFrame(x - Coin.RADIUS, y - Coin.RADIUS, 2 * Coin.RADIUS, 2 * Coin.RADIUS);
                g.draw(disc);
                g.setStroke(old);
            }
        }
    }
}
