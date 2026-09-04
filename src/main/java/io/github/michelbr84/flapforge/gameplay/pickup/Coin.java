package io.github.michelbr84.flapforge.gameplay.pickup;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.geom.Circle;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;

/**
 * One collectable coin (D7, E2): a {@link Circle} of radius {@link #RADIUS} scrolling left with
 * the world and, once the bird carries a magnet ({@code MAGNET_RADIUS &gt; 0}), drifting towards
 * the bird at a fixed speed.
 *
 * <p>The attraction is a fixed {@link #MAGNET_SPEED} px/s step along the straight line to the
 * bird, never a spring or an acceleration: a constant speed keeps the motion exactly reproducible
 * from the tick count and the two positions, which is what the determinism guarantee needs. The
 * step is capped at the remaining distance so a coin never overshoots and oscillates.
 *
 * <p>A coin laid through an obstacle keeps a reference to it and re-reads
 * {@link Obstacle#safeBandY(double)} every tick, so a trail through a <em>moving</em> gate moves
 * with the gate. Without that the coin stays where the gate was at spawn time while the gate swings
 * {@link io.github.michelbr84.flapforge.gameplay.obstacle.Oscillator#DEFAULT_AMPLITUDE} px around
 * it, and the worst-case clearance is {@code gap / 2 - 51 - RADIUS}: exactly 5.0 px at the shipped
 * {@code normal} gap of 128, but −1.4 px at {@code hard} (gap x0.9) and −7.8 px at
 * {@code nightmare} (x0.8) — the coin sits inside a lethal hitbox for part of every swing, a
 * pickup you could only take by dying. The magnet detaches the coin from its gate the first time
 * it pulls, since a coin being dragged to the bird has left the band by definition.
 *
 * <p>{@link #prevX()}/{@link #prevY()} hold the position at the start of the current tick so the
 * renderer can interpolate (E30.g), exactly like {@code Obstacle.prevX}.
 */
public final class Coin {

    /** Radius of the coin hitbox, in logical pixels. */
    public static final double RADIUS = 8;
    /** Coins are worth one currency unit each until {@code modifiers.json} says otherwise. */
    public static final int DEFAULT_VALUE = 1;
    /** Speed of the magnet attraction, in px/s of world time. */
    public static final double MAGNET_SPEED = 240;

    private final int value;
    private Obstacle owner;
    private double bandOffsetY;
    private double x;
    private double prevX;
    private double y;
    private double prevY;
    private boolean collected;

    /**
     * Creates a coin worth {@link #DEFAULT_VALUE}.
     *
     * @param x the centre x
     * @param y the centre y
     */
    public Coin(double x, double y) {
        this(x, y, DEFAULT_VALUE);
    }

    /**
     * Creates a coin.
     *
     * @param x the centre x
     * @param y the centre y
     * @param value the currency value
     */
    public Coin(double x, double y, int value) {
        this.x = x;
        this.prevX = x;
        this.y = y;
        this.prevY = y;
        this.value = value;
    }

    /**
     * Binds the coin to the obstacle its trail was laid through, so it follows the safe band as
     * the column moves (E2, E32.c).
     *
     * @param owner the obstacle, or {@code null} to leave the coin where it is
     * @return this coin
     */
    public Coin follow(Obstacle owner) {
        this.owner = owner;
        this.bandOffsetY = owner == null ? 0 : y - owner.safeBandY(x);
        return this;
    }

    /**
     * The obstacle whose safe band the coin rides.
     *
     * @return the obstacle, or {@code null} once the coin is on its own
     */
    public Obstacle owner() {
        return owner;
    }

    /**
     * Advances the coin by one tick: it scrolls left with the world and is then pulled towards
     * the bird when the bird is within {@code magnetRadius}.
     *
     * @param ctx the tick context
     * @param magnetRadius the {@code MAGNET_RADIUS} stat (0 disables the attraction)
     */
    public void update(SimContext ctx, double magnetRadius) {
        prevX = x;
        prevY = y;
        x -= ctx.scrollPerTick();
        if (owner != null) {
            // The obstacle layer has already advanced this tick, so this is the band as it is now.
            y = owner.safeBandY(x) + bandOffsetY;
        }
        if (magnetRadius <= 0) {
            return;
        }
        Bird bird = ctx.bird();
        double dx = bird.x() - x;
        double dy = bird.y() - y;
        // StrictMath, not Math: hypot is only specified to within 1 ulp, so the fast intrinsic can
        // differ between JVMs and the state hash with it (E30.c allows either).
        double distance = StrictMath.hypot(dx, dy);
        if (distance <= 0 || distance > magnetRadius) {
            return;
        }
        double step = Math.min(ctx.perTick(MAGNET_SPEED), distance);
        x += dx / distance * step;
        y += dy / distance * step;
        // Being pulled means the coin has left its band; it never snaps back to it.
        owner = null;
    }

    /** Makes the previous position equal to the current one (world freeze in DYING). */
    public void settle() {
        prevX = x;
        prevY = y;
    }

    /**
     * The current hitbox.
     *
     * @return the circle
     */
    public Circle hitbox() {
        return new Circle(x, y, RADIUS);
    }

    /**
     * Tells whether the coin left the playfield on the left.
     *
     * @return {@code true} when it can be dropped
     */
    public boolean offscreen() {
        return x < -RADIUS;
    }

    /** Marks the coin as picked up; it is dropped on the next layer update. */
    public void collect() {
        collected = true;
    }

    /**
     * Tells whether the coin was picked up.
     *
     * @return {@code true} once collected
     */
    public boolean isCollected() {
        return collected;
    }

    /**
     * The currency value.
     *
     * @return the value
     */
    public int value() {
        return value;
    }

    /**
     * Current centre x.
     *
     * @return the x
     */
    public double x() {
        return x;
    }

    /**
     * Current centre y.
     *
     * @return the y
     */
    public double y() {
        return y;
    }

    /**
     * Centre x at the start of the current tick.
     *
     * @return the previous x
     */
    public double prevX() {
        return prevX;
    }

    /**
     * Centre y at the start of the current tick.
     *
     * @return the previous y
     */
    public double prevY() {
        return prevY;
    }

    /**
     * Folds the coin state into a hash.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, Double.doubleToLongBits(x));
        h = MathUtil.fold(h, Double.doubleToLongBits(y));
        return MathUtil.fold(h, (collected ? 1 : 0) | (value << 1));
    }

    @Override
    public String toString() {
        return "Coin{x=" + x + ", y=" + y + (collected ? ", collected" : "") + '}';
    }
}
