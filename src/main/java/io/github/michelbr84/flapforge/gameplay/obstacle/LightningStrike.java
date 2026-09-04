package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import java.util.List;

/**
 * A partial-height lightning bolt (D6, E8): a column {@link #WIDTH} px wide scrolling with the
 * world that is {@code IDLE} until it is {@code warningTicks} of scroll away from the bird, then
 * {@code WARNING} (no hitbox; a {@link ObstacleSignal#LIGHTNING_WARNING} is raised once), then
 * {@code STRIKE} for {@code strikeTicks} of world time once the column centre reaches
 * {@link Playfield#BIRD_X}, then {@code SPENT} until it leaves the playfield.
 *
 * <p>The bolt is partial: side {@code TOP} lights {@code [0, lengthFrac × 598]}, side
 * {@code BOTTOM} lights {@code [598 − lengthFrac × 598, 598]}, and {@code lengthFrac ≤ 0.7} keeps
 * a safe band on the other side — a full-height bolt would be unavoidable at the bird's fixed x.
 * The timing is bird-relative: the warning distance is measured in ticks of the <em>current</em>
 * scroll (so {@code TIME_SCALE} and a headwind stretch it in ticks and keep it in pixels), and
 * the strike lasts {@code strikeTicks} of world time.
 *
 * <p>The column scores like a gate. The safe band (E32.c) is the centre of the unlit span.
 * Ambient sky flashes ({@code worlds.json.ambient.lightningEveryGates}) are cosmetic and are not
 * this class (E8).
 */
public final class LightningStrike extends Obstacle {

    /** Bolt lifecycle. */
    public enum State {
        /** Far from the bird: nothing shows. */
        IDLE,
        /** The warning is on: the side and extent are visible, no hitbox. */
        WARNING,
        /** The bolt is lethal. */
        STRIKE,
        /** The bolt is over. */
        SPENT
    }

    /** Width of the column and of the bolt hitbox. */
    public static final double WIDTH = 24;
    /** Smallest lit fraction (§4 {@code ParamSpec}). */
    public static final double MIN_LENGTH_FRAC = 0.3;
    /** Largest lit fraction (§4 {@code ParamSpec}): a safe band always exists. */
    public static final double MAX_LENGTH_FRAC = 0.7;
    /** Shortest warning the content validator accepts (§4 {@code ParamSpec}). */
    public static final int MIN_WARNING_TICKS = 30;
    /** Shortest strike (§4 {@code ParamSpec}). */
    public static final int MIN_STRIKE_TICKS = 6;
    /** Longest strike (§4 {@code ParamSpec}). */
    public static final int MAX_STRIKE_TICKS = 16;
    /** Warning length {@link #standard} and the authored patterns default to. */
    public static final int DEFAULT_WARNING_TICKS = 45;
    /**
     * Warning length the spawn table uses (M7 fairness): a table bolt is drawn against the
     * previous column with no author to read it in context, so it warns from 75 ticks of
     * scroll out — before the bird has committed to the gap in front of it — where an authored
     * pattern keeps its own {@code warningTicks}.
     */
    public static final int TABLE_WARNING_TICKS = 75;
    /** Strike length the spawn table uses. */
    public static final int DEFAULT_STRIKE_TICKS = 10;

    private final Side side;
    private final double lengthFrac;
    private final int warningTicks;
    private final int strikeTicks;
    private State state = State.IDLE;
    private double strikeClock;

    /**
     * Creates a bolt column.
     *
     * @param x the left edge of the column
     * @param side the edge the bolt hangs from
     * @param lengthFrac the lit fraction of the playable height, in {@code (0, 1)}
     * @param warningTicks ticks of warning at the current scroll
     * @param strikeTicks ticks the bolt stays lethal
     */
    public LightningStrike(double x, Side side, double lengthFrac, int warningTicks,
            int strikeTicks) {
        super(ObstacleKind.LIGHTNING, x, WIDTH, true);
        if (lengthFrac <= 0 || lengthFrac >= 1) {
            throw new IllegalArgumentException("lengthFrac must be in (0, 1): " + lengthFrac);
        }
        if (warningTicks < 0 || strikeTicks < 1) {
            throw new IllegalArgumentException("Invalid lightning timing: warning " + warningTicks
                    + ", strike " + strikeTicks);
        }
        this.side = side;
        this.lengthFrac = lengthFrac;
        this.warningTicks = warningTicks;
        this.strikeTicks = strikeTicks;
    }

    /**
     * Creates a bolt with the spawn-table timings.
     *
     * @param x the left edge of the column
     * @param side the edge the bolt hangs from
     * @param lengthFrac the lit fraction
     * @return the bolt
     */
    public static LightningStrike standard(double x, Side side, double lengthFrac) {
        return new LightningStrike(x, side, lengthFrac, DEFAULT_WARNING_TICKS,
                DEFAULT_STRIKE_TICKS);
    }

    @Override
    public void update(SimContext ctx) {
        super.update(ctx);
        switch (state) {
            case IDLE:
                if (centerX() - Playfield.BIRD_X <= warningTicks * ctx.scrollPerTick()) {
                    state = State.WARNING;
                    raise(ObstacleSignal.LIGHTNING_WARNING);
                    maybeStrike();
                }
                break;
            case WARNING:
                maybeStrike();
                break;
            case STRIKE:
                strikeClock += ctx.worldDt();
                if (strikeClock >= strikeTicks) {
                    state = State.SPENT;
                }
                break;
            case SPENT:
            default:
                break;
        }
    }

    private void maybeStrike() {
        if (centerX() <= Playfield.BIRD_X) {
            state = State.STRIKE;
            strikeClock = 0;
        }
    }

    @Override
    public List<Hitbox> hitboxesAt(double t) {
        if (state != State.STRIKE) {
            return List.of();
        }
        double atX = prevX() + (x() - prevX()) * t;
        return List.of(boltSpanAt(atX));
    }

    @Override
    public double maxDisplacement() {
        return Math.abs(x() - prevX());
    }

    @Override
    public boolean lethal() {
        return true;
    }

    /**
     * The centre of the unlit span (E32.c).
     *
     * @param atX ignored: the band does not depend on x
     * @return the safe y
     */
    @Override
    public double safeBandY(double atX) {
        return side == Side.TOP ? (boltHeight() + Playfield.GROUND_Y) / 2
                : (Playfield.GROUND_Y - boltHeight()) / 2;
    }

    @Override
    protected long hashGeometry(long hash) {
        long h = MathUtil.fold(hash, side.ordinal());
        h = MathUtil.fold(h, Double.doubleToLongBits(lengthFrac));
        h = MathUtil.fold(h, warningTicks);
        h = MathUtil.fold(h, strikeTicks);
        h = MathUtil.fold(h, state.ordinal());
        return MathUtil.fold(h, Double.doubleToLongBits(strikeClock));
    }

    /**
     * Current column centre x.
     *
     * @return {@code x + WIDTH / 2}
     */
    public double centerX() {
        return x() + WIDTH / 2;
    }

    /**
     * Height of the lit part.
     *
     * @return {@code lengthFrac × 598}
     */
    public double boltHeight() {
        return lengthFrac * Playfield.GROUND_Y;
    }

    /**
     * Top edge of the lit part.
     *
     * @return 0 for a top bolt, {@code 598 − height} for a bottom one
     */
    public double boltTopY() {
        return side == Side.TOP ? 0 : Playfield.GROUND_Y - boltHeight();
    }

    /**
     * Bottom edge of the lit part.
     *
     * @return {@code height} for a top bolt, 598 for a bottom one
     */
    public double boltBottomY() {
        return side == Side.TOP ? boltHeight() : Playfield.GROUND_Y;
    }

    /**
     * The span the bolt lights, whatever the state (the warning shows it before the strike).
     *
     * @return the box at the current x
     */
    public Aabb boltSpan() {
        return boltSpanAt(x());
    }

    /**
     * The bolt span at a column x.
     *
     * @param atX the left edge
     * @return the box
     */
    public Aabb boltSpanAt(double atX) {
        return new Aabb(atX, boltTopY(), WIDTH, boltHeight());
    }

    /**
     * Ticks until the column centre reaches the bird at a given scroll.
     *
     * @param scrollPerTick the world scroll per tick
     * @return the ticks (negative once past the bird; {@code +inf} when the world is still)
     */
    public double ticksToStrike(double scrollPerTick) {
        double distance = centerX() - Playfield.BIRD_X;
        return scrollPerTick > 0 ? distance / scrollPerTick
                : (distance <= 0 ? 0 : Double.POSITIVE_INFINITY);
    }

    /**
     * The lifecycle state.
     *
     * @return the state
     */
    public State state() {
        return state;
    }

    /**
     * Tells whether the bolt is lethal right now.
     *
     * @return {@code true} in {@code STRIKE}
     */
    public boolean isStriking() {
        return state == State.STRIKE;
    }

    /**
     * Tells whether the bolt is over.
     *
     * @return {@code true} in {@code SPENT}
     */
    public boolean isSpent() {
        return state == State.SPENT;
    }

    /**
     * World time elapsed in the strike.
     *
     * @return the ticks since the strike started (0 outside a strike)
     */
    public double strikeClock() {
        return strikeClock;
    }

    /**
     * The edge the bolt hangs from.
     *
     * @return the side
     */
    public Side side() {
        return side;
    }

    /**
     * The lit fraction of the playable height.
     *
     * @return the fraction
     */
    public double lengthFrac() {
        return lengthFrac;
    }

    /**
     * Ticks of warning at the current scroll.
     *
     * @return the warning length
     */
    public int warningTicks() {
        return warningTicks;
    }

    /**
     * Ticks the bolt stays lethal.
     *
     * @return the strike length
     */
    public int strikeTicks() {
        return strikeTicks;
    }

    @Override
    public String toString() {
        return "Lightning{" + side + ", x=" + x() + ", frac=" + lengthFrac + ", " + state + '}';
    }
}
