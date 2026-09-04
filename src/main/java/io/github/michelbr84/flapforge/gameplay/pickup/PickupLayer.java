package io.github.michelbr84.flapforge.gameplay.pickup;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The live coins of a run (D7, E2). Coins are spawned as a trail through the safe band of each
 * scoring obstacle, scroll with the world, are attracted by the magnet and are picked up by the
 * bird's hitbox.
 *
 * <p>Spawn rule (E2): {@code COIN_SPAWN_RATE} is the <em>expected number of coins per scoring
 * gate</em> in {@code [0, 5]}, so a gate gets {@code floor(rate)} coins plus one more with
 * probability {@code frac(rate)}. Exactly one {@code nextDouble()} is drawn from the
 * {@code coins} stream per scoring spawn — always, even when the fraction is 0 — so the stream
 * advances at the same pace whatever a modifier does to the rate. Nothing at all is drawn when
 * {@link RuleFlag#NO_COINS} is active, which is a run-long flag.
 *
 * <p>Layout: the trail is centred on the obstacle column and spread along x in
 * {@link #TRAIL_SPACING_PX} steps; each coin sits on {@link Obstacle#safeBandY(double)} at its own
 * x (E32.c), which is the gap centre for a {@code PipeGate}, and keeps riding that band as the
 * column moves ({@link Coin#follow(Obstacle)}).
 */
public final class PickupLayer {

    /** Horizontal distance between two coins of the same trail, in px. */
    public static final double TRAIL_SPACING_PX = 24;

    private final ArrayList<Coin> coins = new ArrayList<>();
    private final Random rng;
    private int spawned;
    private int collected;
    private int collectedValue;

    /**
     * Creates a layer drawing from the run's {@code coins} stream.
     *
     * @param rng the run's random provider
     */
    public PickupLayer(RandomProvider rng) {
        Objects.requireNonNull(rng, "rng");
        this.rng = rng.stream(RandomProvider.COINS);
    }

    /**
     * Advances every coin with the world clock and drops the ones that were collected or left the
     * playfield.
     *
     * @param ctx the tick context
     */
    public void update(SimContext ctx) {
        double magnetRadius = ctx.stats().resolve(StatId.MAGNET_RADIUS);
        for (int i = 0; i < coins.size(); i++) {
            coins.get(i).update(ctx, magnetRadius);
        }
        coins.removeIf(c -> c.isCollected() || c.offscreen());
    }

    /**
     * Picks up every coin the bird's hitbox overlaps. Collected coins stay in the list until the
     * next {@link #update(SimContext)} so the renderer can still see them this frame.
     *
     * @param birdBox the bird hitbox, already scaled by {@code HITBOX_SCALE}
     * @return the coins collected this tick, in layer order (empty when none)
     */
    public List<Coin> collect(Aabb birdBox) {
        List<Coin> taken = null;
        for (int i = 0; i < coins.size(); i++) {
            Coin coin = coins.get(i);
            if (coin.isCollected() || !coin.hitbox().intersects(birdBox)) {
                continue;
            }
            coin.collect();
            collected++;
            collectedValue += coin.value();
            if (taken == null) {
                taken = new ArrayList<>(2);
            }
            taken.add(coin);
        }
        return taken == null ? List.of() : taken;
    }

    /**
     * Lays a coin trail through a freshly spawned scoring obstacle (E2).
     *
     * @param obstacle the obstacle the trail belongs to
     * @param ctx the tick context
     * @return the number of coins spawned
     */
    public int spawnFor(Obstacle obstacle, SimContext ctx) {
        if (ctx.rules().contains(RuleFlag.NO_COINS)) {
            return 0;
        }
        int count = rollCount(ctx.stats().resolve(StatId.COIN_SPAWN_RATE));
        double centerX = obstacle.x() + obstacle.width() / 2;
        for (int i = 0; i < count; i++) {
            double x = centerX + (i - (count - 1) / 2.0) * TRAIL_SPACING_PX;
            coins.add(new Coin(x, obstacle.safeBandY(x)).follow(obstacle));
        }
        spawned += count;
        return count;
    }

    /**
     * Appends one coin, bypassing the spawn roll (authored trails and tests).
     *
     * @param coin the coin
     */
    public void add(Coin coin) {
        coins.add(Objects.requireNonNull(coin, "coin"));
        spawned++;
    }

    /**
     * Draws the number of coins one scoring gate gets: {@code floor(rate)} plus a Bernoulli draw
     * on {@code frac(rate)} (E2). Exactly one value is consumed from the {@code coins} stream.
     *
     * @param rate the {@code COIN_SPAWN_RATE} stat
     * @return the coin count
     */
    public int rollCount(double rate) {
        double clamped = Math.max(0, rate);
        int floor = (int) Math.floor(clamped);
        double fraction = clamped - floor;
        return rng.nextDouble() < fraction ? floor + 1 : floor;
    }

    /** Freezes interpolation state on every coin (world freeze in DYING). */
    public void settle() {
        for (int i = 0; i < coins.size(); i++) {
            coins.get(i).settle();
        }
    }

    /**
     * Read-only view of the live coins, in spawn order.
     *
     * @return the coins
     */
    public List<Coin> coins() {
        return Collections.unmodifiableList(coins);
    }

    /**
     * Number of live coins.
     *
     * @return the count
     */
    public int size() {
        return coins.size();
    }

    /**
     * Tells whether no coin is alive.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return coins.isEmpty();
    }

    /**
     * Coins spawned so far.
     *
     * @return the count
     */
    public int spawnedCount() {
        return spawned;
    }

    /**
     * Coins collected so far.
     *
     * @return the count
     */
    public int collectedCount() {
        return collected;
    }

    /**
     * Total value of the coins collected so far.
     *
     * @return the value
     */
    public int collectedValue() {
        return collectedValue;
    }

    /** Removes every coin (the counters are kept). */
    public void clear() {
        coins.clear();
    }

    /**
     * Folds every coin and the collected total into a hash.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, coins.size());
        for (int i = 0; i < coins.size(); i++) {
            h = coins.get(i).hashState(h);
        }
        return MathUtil.fold(h, collectedValue);
    }
}
