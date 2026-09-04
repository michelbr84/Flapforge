package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Circle;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import java.util.List;
import java.util.Optional;

/**
 * A rotating gear (D6): a {@link Circle} hitbox of radius {@code 24–56} whose left edge sits at
 * the column {@code x}, optionally sweeping a vertical rail.
 *
 * <p>The rail is a triangle-wave {@link Oscillator} of {@code railAmplitude} px travelled at
 * {@code railSpeed} px/s of world time, centred on {@code cy}: the centre starts at the top of the
 * sweep ({@code cy − amplitude / 2}) and moves down first, exactly like a moving gate. The
 * {@link #angle()} is cosmetic — the hitbox is a circle — and is kept in <em>turns</em> so no
 * trigonometry runs in the simulation (E30.c); the renderer converts it. Both the rail and the
 * rotation advance by {@code worldDt}, so {@code TIME_SCALE} slows them.
 *
 * <p>The lethal column is {@code 2 × radius} wide and scores: a gear replaces a gate in the spawn
 * cadence, so clearing it advances the gate-keyed difficulty curve. The safe band (E32.c) is the
 * larger of the free spaces above and below the <em>whole</em> sweep, which is what the coin
 * trail and the bot aim at.
 */
public final class Gear extends Obstacle {

    /** Smallest radius (§4 {@code ParamSpec}). */
    public static final double MIN_RADIUS = 24;
    /** Largest radius (§4 {@code ParamSpec}). */
    public static final double MAX_RADIUS = 56;
    /** Rail travel used by the spawn table and by {@code ALL_OBSTACLES_MOVE}. */
    public static final double DEFAULT_RAIL_AMPLITUDE = 60;
    /** Rail speed used by the spawn table and by {@code ALL_OBSTACLES_MOVE}, in px/s. */
    public static final double DEFAULT_RAIL_SPEED = 40;
    /** Rotation speed in turns per second of world time (cosmetic). */
    public static final double TURNS_PER_SECOND = 0.5;

    private final double cy;
    private final double radius;
    private final double railAmplitude;
    private final double railSpeed;
    private final Oscillator rail;
    private double angle;
    private double prevAngle;

    /**
     * Creates a gear.
     *
     * @param x the left edge of the column
     * @param cy the centre y (the sweep centre when a rail is present)
     * @param radius the radius in px
     * @param railAmplitude the rail travel in px, or {@code 0} for a fixed gear
     * @param railSpeed the rail speed in px/s
     */
    public Gear(double x, double cy, double radius, double railAmplitude, double railSpeed) {
        super(ObstacleKind.GEAR, x, 2 * radius, true);
        if (radius <= 0) {
            throw new IllegalArgumentException("Gear radius must be positive: " + radius);
        }
        if (railAmplitude < 0 || railSpeed < 0) {
            throw new IllegalArgumentException("Rail values must not be negative: "
                    + railAmplitude + "/" + railSpeed);
        }
        this.cy = cy;
        this.radius = radius;
        this.railAmplitude = railAmplitude;
        this.railSpeed = railSpeed;
        this.rail = railAmplitude > 0 ? new Oscillator(railAmplitude) : null;
    }

    /**
     * Creates a fixed gear.
     *
     * @param x the left edge of the column
     * @param cy the centre y
     * @param radius the radius in px
     * @return the gear
     */
    public static Gear fixed(double x, double cy, double radius) {
        return new Gear(x, cy, radius, 0, 0);
    }

    /**
     * Creates a gear on the default rail.
     *
     * @param x the left edge of the column
     * @param cy the sweep centre y
     * @param radius the radius in px
     * @return the gear
     */
    public static Gear onRail(double x, double cy, double radius) {
        return new Gear(x, cy, radius, DEFAULT_RAIL_AMPLITUDE, DEFAULT_RAIL_SPEED);
    }

    @Override
    public void update(SimContext ctx) {
        super.update(ctx);
        prevAngle = angle;
        angle = (angle + ctx.perTick(TURNS_PER_SECOND)) % 1.0;
        if (rail != null) {
            rail.advance(ctx.perTick(railSpeed));
        }
    }

    @Override
    public void settle() {
        super.settle();
        prevAngle = angle;
        if (rail != null) {
            rail.settle();
        }
    }

    @Override
    public List<Hitbox> hitboxesAt(double t) {
        double atX = prevX() + (x() - prevX()) * t;
        return List.of(new Circle(atX + radius, centerYAt(t), radius));
    }

    @Override
    public double maxDisplacement() {
        double dx = Math.abs(x() - prevX());
        double dy = rail == null ? 0 : Math.abs(rail.offset() - rail.prevOffset());
        return Math.max(dx, dy);
    }

    @Override
    public boolean lethal() {
        return true;
    }

    @Override
    public boolean offscreen() {
        return x() + width() < 0;
    }

    /**
     * The larger of the free spaces above and below the whole sweep (E32.c), as its centre.
     *
     * @param atX ignored: the band does not depend on x
     * @return the safe y
     */
    @Override
    public double safeBandY(double atX) {
        double above = sweepTopY();
        double below = Playfield.GROUND_Y - sweepBottomY();
        return above >= below ? sweepTopY() / 2 : (sweepBottomY() + Playfield.GROUND_Y) / 2;
    }

    /**
     * Tells whether the safe band lies above the sweep.
     *
     * @return {@code true} when the free space above is the larger one
     */
    public boolean safeBandAbove() {
        return sweepTopY() >= Playfield.GROUND_Y - sweepBottomY();
    }

    @Override
    protected long hashGeometry(long hash) {
        long h = MathUtil.fold(hash, Double.doubleToLongBits(cy));
        h = MathUtil.fold(h, Double.doubleToLongBits(radius));
        h = MathUtil.fold(h, Double.doubleToLongBits(railAmplitude));
        h = MathUtil.fold(h, Double.doubleToLongBits(railSpeed));
        h = MathUtil.fold(h, Double.doubleToLongBits(angle));
        return rail == null ? MathUtil.fold(h, -1) : rail.hashState(h);
    }

    /**
     * Current centre x.
     *
     * @return {@code x + radius}
     */
    public double centerX() {
        return x() + radius;
    }

    /**
     * Current centre y (rail included).
     *
     * @return the y
     */
    public double centerY() {
        return centerYAt(1.0);
    }

    /**
     * Centre y interpolated between the previous tick state ({@code t = 0}) and the current one.
     *
     * @param t the interpolation factor in {@code [0, 1]}
     * @return the y
     */
    public double centerYAt(double t) {
        return rail == null ? cy : cy - railAmplitude / 2 + rail.offsetAt(t);
    }

    /**
     * Centre y the rail will have {@code ticks} ticks from now at a given per-tick rail travel
     * (the bot's rail oracle, D21).
     *
     * @param ticks ticks ahead (0 = now)
     * @param railPerTick the rail travel per tick ({@code railSpeed × worldDt / 60})
     * @return the predicted y
     */
    public double predictedCenterY(int ticks, double railPerTick) {
        if (rail == null) {
            return cy;
        }
        return cy - railAmplitude / 2
                + Oscillator.offsetForPhase(rail.phase() + ticks * railPerTick, railAmplitude);
    }

    /**
     * Top of the band the circle can ever occupy.
     *
     * @return {@code cy − amplitude / 2 − radius}
     */
    public double sweepTopY() {
        return cy - railAmplitude / 2 - radius;
    }

    /**
     * Bottom of the band the circle can ever occupy.
     *
     * @return {@code cy + amplitude / 2 + radius}
     */
    public double sweepBottomY() {
        return cy + railAmplitude / 2 + radius;
    }

    /**
     * The radius.
     *
     * @return the radius in px
     */
    public double radius() {
        return radius;
    }

    /**
     * The sweep centre (or the fixed centre) y.
     *
     * @return the y
     */
    public double cy() {
        return cy;
    }

    /**
     * Rail travel.
     *
     * @return the amplitude in px, 0 without a rail
     */
    public double railAmplitude() {
        return railAmplitude;
    }

    /**
     * Rail speed.
     *
     * @return the speed in px/s
     */
    public double railSpeed() {
        return railSpeed;
    }

    /**
     * The rail driver.
     *
     * @return the oscillator when the gear rides a rail
     */
    public Optional<Oscillator> rail() {
        return Optional.ofNullable(rail);
    }

    /**
     * Tells whether the gear rides a rail.
     *
     * @return {@code true} with a rail
     */
    public boolean isMoving() {
        return rail != null;
    }

    /**
     * Current rotation in turns ({@code [0, 1)}), for the renderer.
     *
     * @return the angle
     */
    public double angle() {
        return angle;
    }

    /**
     * Rotation interpolated between the previous tick state and the current one, in turns,
     * unwrapped across the {@code 1 → 0} seam so a frame never spins backwards.
     *
     * @param t the interpolation factor in {@code [0, 1]}
     * @return the angle in turns (may exceed 1 by less than one step)
     */
    public double angleAt(double t) {
        double to = angle < prevAngle ? angle + 1 : angle;
        return prevAngle + (to - prevAngle) * t;
    }

    @Override
    public String toString() {
        return "Gear{x=" + x() + ", cy=" + centerY() + ", r=" + radius
                + (rail != null ? ", rail " + railAmplitude : "") + '}';
    }
}
