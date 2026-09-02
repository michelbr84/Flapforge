package io.github.michelbr84.flapforge.gameplay.bird;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import java.util.Objects;

/**
 * The player's bird: a vertical position and velocity at the fixed column
 * {@link Playfield#BIRD_X} (D3), a life state and a hitbox specification.
 *
 * <p>Coordinates are logical pixels with y growing downwards; {@code vy > 0} means falling
 * (px/s). {@link #prevY()} holds the position at the start of the current tick so the renderer can
 * interpolate (E30.g) and the collision system can sub-step (D7). Integration lives in
 * {@link BirdPhysics}; this class only holds state.
 */
public final class Bird {

    /** Life state of the bird. */
    public enum State {
        /** Flying and collidable. */
        ALIVE,
        /** Hit something; falling to the ground line. */
        DYING,
        /** Landed after dying. */
        DEAD
    }

    private final HitboxSpec hitboxSpec;
    private double y;
    private double prevY;
    private double vy;
    private State state = State.ALIVE;
    private double windAccelY;
    private double windScroll;

    /**
     * Creates a bird at rest.
     *
     * @param hitboxSpec the hitbox geometry
     * @param startY the initial y
     */
    public Bird(HitboxSpec hitboxSpec, double startY) {
        this.hitboxSpec = Objects.requireNonNull(hitboxSpec, "hitboxSpec");
        this.y = startY;
        this.prevY = startY;
    }

    /**
     * Creates the classic bird at {@link Playfield#BIRD_START_Y}.
     *
     * @return the bird
     */
    public static Bird classic() {
        return new Bird(HitboxSpec.CLASSIC, Playfield.BIRD_START_Y);
    }

    /**
     * Fixed x of the bird origin.
     *
     * @return {@link Playfield#BIRD_X}
     */
    public double x() {
        return Playfield.BIRD_X;
    }

    /**
     * Current y of the bird origin.
     *
     * @return the y
     */
    public double y() {
        return y;
    }

    /**
     * Y at the start of the current tick.
     *
     * @return the previous y
     */
    public double prevY() {
        return prevY;
    }

    /**
     * Vertical velocity in px/s (positive = falling).
     *
     * @return the velocity
     */
    public double vy() {
        return vy;
    }

    /**
     * Life state.
     *
     * @return the state
     */
    public State state() {
        return state;
    }

    /**
     * Tells whether the bird is alive (collidable, controllable).
     *
     * @return {@code true} when {@link State#ALIVE}
     */
    public boolean isAlive() {
        return state == State.ALIVE;
    }

    /**
     * Hitbox geometry.
     *
     * @return the spec
     */
    public HitboxSpec hitboxSpec() {
        return hitboxSpec;
    }

    /**
     * Current hitbox scaled about its centre.
     *
     * @param scale the {@code HITBOX_SCALE} factor
     * @return the box
     */
    public Aabb hitbox(double scale) {
        return hitboxSpec.at(x(), y, scale);
    }

    /**
     * Current unscaled hitbox.
     *
     * @return the box
     */
    public Aabb hitbox() {
        return hitbox(1.0);
    }

    /**
     * Hitbox the bird would have at another y (used for sub-stepping).
     *
     * @param atY the y to evaluate at
     * @param scale the {@code HITBOX_SCALE} factor
     * @return the box
     */
    public Aabb hitboxAt(double atY, double scale) {
        return hitboxSpec.at(x(), atY, scale);
    }

    /**
     * Sets the position.
     *
     * @param y the new y
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * Sets the velocity.
     *
     * @param vy the new velocity in px/s
     */
    public void setVy(double vy) {
        this.vy = vy;
    }

    /**
     * Sets the life state.
     *
     * @param state the new state
     */
    public void setState(State state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    /**
     * Records the current position as the tick start (call once per tick before integrating) and
     * clears the wind sampled last tick.
     */
    public void beginTick() {
        prevY = y;
        windAccelY = 0;
        windScroll = 0;
    }

    /**
     * Adds a wind zone's push for this tick (D6, M7). Sampled by the simulation at the start of
     * the tick from the overlapping zones; {@link #windAccelY()} joins gravity in this tick's
     * integration and {@link #windScroll()} joins the scroll speed of this tick's world scroll.
     *
     * @param accelY vertical acceleration in px/s² (positive = down)
     * @param scrollDelta change of the relative scroll speed in px/s
     */
    public void applyWind(double accelY, double scrollDelta) {
        windAccelY += accelY;
        windScroll += scrollDelta;
    }

    /**
     * Vertical wind acceleration sampled for this tick.
     *
     * @return px/s², 0 outside every zone
     */
    public double windAccelY() {
        return windAccelY;
    }

    /**
     * Scroll speed change sampled for this tick.
     *
     * @return px/s, 0 outside every zone
     */
    public double windScroll() {
        return windScroll;
    }

    /**
     * Folds the bird state into a hash.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, Double.doubleToLongBits(y));
        h = MathUtil.fold(h, Double.doubleToLongBits(vy));
        return MathUtil.fold(h, state.ordinal());
    }

    @Override
    public String toString() {
        return "Bird{y=" + y + ", vy=" + vy + ", state=" + state + '}';
    }
}
