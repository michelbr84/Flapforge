package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import java.util.List;
import java.util.Objects;

/**
 * Base class of every world hazard (D6). An obstacle is a column at {@code x} scrolling left at
 * the world scroll speed; subclasses add geometry, phases and hitboxes.
 *
 * <p>Obstacles are plain-allocated (no pool) and keep {@code prevX} plus a per-kind previous
 * state so the renderer can interpolate and {@code CollisionSystem} can sub-step along the tick.
 * Flags: {@code scoring} says the column awards a gate when cleared (D7), {@code scored} that it
 * already did, {@code dirty} that the bird grazed it (near miss; streak hook, D26) and
 * {@code streakResolved} that the streak has already counted it — which happens later than the
 * score, because the graze window outlives the score line (see {@code Simulation.tick}).
 */
public abstract class Obstacle {

    private final ObstacleKind kind;
    private final double width;
    private boolean scoring;
    private double x;
    private double prevX;
    private boolean scored;
    private boolean dirty;
    private boolean nearMissReported;
    private boolean streakResolved;
    private ObstacleSignal pendingSignal;

    /**
     * Creates an obstacle.
     *
     * @param kind the family
     * @param x the initial left edge
     * @param width the lethal column width
     * @param scoring whether clearing the column awards a gate
     */
    protected Obstacle(ObstacleKind kind, double x, double width, boolean scoring) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.x = x;
        this.prevX = x;
        this.width = width;
        this.scoring = scoring;
    }

    /**
     * Advances the obstacle by one tick: records the previous state and scrolls left by
     * {@link SimContext#scrollPerTick()}. Subclasses override to advance phases and must call
     * {@code super.update(ctx)} first.
     *
     * @param ctx the tick context
     */
    public void update(SimContext ctx) {
        prevX = x;
        x -= ctx.scrollPerTick();
    }

    /**
     * Makes the previous state equal to the current one (used when the world freezes in DYING so
     * interpolation shows no motion).
     */
    public void settle() {
        prevX = x;
    }

    /**
     * Current lethal hitboxes.
     *
     * @return the hitboxes (possibly empty)
     */
    public List<Hitbox> hitboxes() {
        return hitboxesAt(1.0);
    }

    /**
     * Hitboxes interpolated between the previous tick state ({@code t = 0}) and the current one
     * ({@code t = 1}); used by the tunnelling guard.
     *
     * @param t the interpolation factor in {@code [0, 1]}
     * @return the hitboxes
     */
    public abstract List<Hitbox> hitboxesAt(double t);

    /**
     * Largest distance any hitbox moved during the last tick (horizontal scroll included).
     *
     * @return the displacement in px
     */
    public abstract double maxDisplacement();

    /**
     * Tells whether touching the hitboxes kills the bird.
     *
     * @return {@code true} for lethal obstacles
     */
    public abstract boolean lethal();

    /**
     * Applies a non-lethal effect on the bird (wind); default does nothing.
     *
     * @param bird the bird
     * @param ctx the tick context
     */
    public void affectBird(Bird bird, SimContext ctx) {
    }

    /**
     * Queues a signal for the simulation to announce (M7). A tick holds at most one signal per
     * obstacle; the simulation drains it with {@link #takeSignal()} after the world moved.
     *
     * @param signal the signal
     */
    protected void raise(ObstacleSignal signal) {
        pendingSignal = signal;
    }

    /**
     * Takes the pending signal, if any, and clears it.
     *
     * @return the signal, or {@code null}
     */
    public ObstacleSignal takeSignal() {
        ObstacleSignal signal = pendingSignal;
        pendingSignal = null;
        return signal;
    }

    /**
     * The x the bird's hitbox left edge must reach for the column to count as cleared (D7).
     *
     * @return {@code x + width}
     */
    public double scoreLineX() {
        return x + width;
    }

    /**
     * Tells whether the obstacle has fully left the playfield and can be removed.
     *
     * @return {@code true} when {@code x < −PIPE_CAP_W}
     */
    public boolean offscreen() {
        return x < -Playfield.PIPE_CAP_W;
    }

    /**
     * Vertical centre of the safe band a bird should aim for when crossing at {@code atX}
     * (E32.c): gap centre for a gate, free side for a piston, unlit side for lightning.
     *
     * @param atX the x at which the bird crosses (normally {@link Playfield#BIRD_X})
     * @return the safe y
     */
    public abstract double safeBandY(double atX);

    /**
     * Folds the obstacle state into a hash: kind, x and the subclass geometry/phase.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, kind.ordinal());
        h = MathUtil.fold(h, Double.doubleToLongBits(x));
        h = MathUtil.fold(h, (scored ? 1 : 0) | (dirty ? 2 : 0) | (streakResolved ? 4 : 0));
        return hashGeometry(h);
    }

    /**
     * Folds subclass geometry and phase into a hash.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    protected abstract long hashGeometry(long hash);

    /**
     * The family.
     *
     * @return the kind
     */
    public ObstacleKind kind() {
        return kind;
    }

    /**
     * Current left edge.
     *
     * @return the x
     */
    public double x() {
        return x;
    }

    /**
     * Left edge at the start of the current tick.
     *
     * @return the previous x
     */
    public double prevX() {
        return prevX;
    }

    /**
     * Lethal column width.
     *
     * @return the width
     */
    public double width() {
        return width;
    }

    /**
     * Sets the position directly (tests and pattern streaming).
     *
     * @param x the new left edge
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Tells whether clearing this column awards a gate.
     *
     * @return {@code true} for scoring obstacles
     */
    public boolean isScoring() {
        return scoring;
    }

    /**
     * Turns scoring off for a column a pattern streams with {@code "scoring": false} (or under
     * {@code scoringSteps: false}, M7). Called once, when the column spawns; a column that was
     * never scoring (a wind zone) stays that way.
     */
    public void markNonScoring() {
        scoring = false;
    }

    /**
     * Tells whether the gate has already been awarded.
     *
     * @return {@code true} once scored
     */
    public boolean isScored() {
        return scored;
    }

    /** Marks the gate as awarded; a gate scores once. */
    public void markScored() {
        scored = true;
    }

    /**
     * Tells whether the bird grazed this obstacle (near miss) — a gate passed dirty breaks the
     * streak (D26).
     *
     * @return {@code true} when grazed
     */
    public boolean isDirty() {
        return dirty;
    }

    /** Records a near miss with this obstacle. */
    public void markDirty() {
        dirty = true;
    }

    /**
     * Tells whether a {@code NearMiss} fact was already emitted for this obstacle.
     *
     * @return {@code true} once reported
     */
    public boolean isNearMissReported() {
        return nearMissReported;
    }

    /** Records that the near-miss fact for this obstacle was emitted. */
    public void markNearMissReported() {
        nearMissReported = true;
    }

    /**
     * Tells whether the streak has already counted this column (D26). The streak is resolved one
     * step after the score, once the column has left the inflated hitbox for good, so a graze in
     * that trailing window still costs the gate it happened on.
     *
     * @return {@code true} once the column reached the streak
     */
    public boolean isStreakResolved() {
        return streakResolved;
    }

    /** Marks the column as counted by the streak. */
    public void markStreakResolved() {
        streakResolved = true;
    }

    @Override
    public String toString() {
        return kind + "@" + x;
    }
}
