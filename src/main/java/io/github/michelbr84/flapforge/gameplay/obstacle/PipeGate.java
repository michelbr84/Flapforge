package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import java.util.List;
import java.util.Optional;

/**
 * A pair of pipe segments with a gap (D6), replacing upstream's {@code Pipe/MovingPipe} and hover
 * variants.
 *
 * <ul>
 *   <li>{@link Layout#STANDARD}: upper segment {@code [−100, top]}, lower segment
 *       {@code [top + gap, 640]}, {@code top ∈ [80, 400]} (inclusive).</li>
 *   <li>{@link Layout#FLOATING}: upper segment {@code [y, y + h]}, lower segment
 *       {@code [y + h + gap, 640 − y]}, {@code y ∈ [53, 106)}, {@code h ∈ [106, 160)} — passable
 *       above and below exactly like upstream's {@code addHoverPipe}.</li>
 * </ul>
 *
 * <p>An optional {@link Oscillator} moves the pair rigidly by its offset (upstream's 2-frame lag
 * of the top column is dropped, §5). The lethal hitbox width is {@link Playfield#PIPE_BODY_W}
 * (40); the 44 px cap is decorative.
 */
public final class PipeGate extends Obstacle {

    /** Segment arrangement. */
    public enum Layout {
        /** Top and bottom pipes anchored to the playfield edges. */
        STANDARD,
        /** Two floating pipes with free space above the upper one and below the lower one. */
        FLOATING
    }

    private final Layout layout;
    private final double top;
    private final double floatY;
    private final double floatH;
    private final double gap;
    private final Oscillator oscillator;
    private final double speed;

    private PipeGate(double x, Layout layout, double top, double floatY, double floatH,
            double gap, Oscillator oscillator, double speed) {
        super(ObstacleKind.PIPE_GATE, x, Playfield.PIPE_BODY_W, true);
        if (speed < 0) {
            throw new IllegalArgumentException("Oscillation speed must not be negative: " + speed);
        }
        this.layout = layout;
        this.top = top;
        this.floatY = floatY;
        this.floatH = floatH;
        this.gap = gap;
        this.oscillator = oscillator;
        this.speed = speed;
    }

    /**
     * Creates a standard gate.
     *
     * @param x the left edge
     * @param top the bottom edge of the upper pipe (top of the gap)
     * @param gap the gap height
     * @param oscillator the motion, or {@code null} for a static gate
     * @return the gate
     */
    public static PipeGate standard(double x, double top, double gap, Oscillator oscillator) {
        return new PipeGate(x, Layout.STANDARD, top, 0, 0, gap, oscillator, 0);
    }

    /**
     * Creates a standard gate with its own oscillation speed (pattern gates, M7).
     *
     * @param x the left edge
     * @param top the bottom edge of the upper pipe (top of the gap)
     * @param gap the gap height
     * @param oscillator the motion, or {@code null} for a static gate
     * @param speed the oscillation speed in px/s, or {@code 0} for the {@code OSCILLATION_SPEED}
     *     stat
     * @return the gate
     */
    public static PipeGate standard(double x, double top, double gap, Oscillator oscillator,
            double speed) {
        return new PipeGate(x, Layout.STANDARD, top, 0, 0, gap, oscillator, speed);
    }

    /**
     * Creates a floating gate.
     *
     * @param x the left edge
     * @param y the top edge of the upper floating pipe
     * @param h the height of the upper floating pipe
     * @param gap the gap height
     * @param oscillator the motion, or {@code null} for a static gate
     * @return the gate
     */
    public static PipeGate floating(double x, double y, double h, double gap,
            Oscillator oscillator) {
        return new PipeGate(x, Layout.FLOATING, 0, y, h, gap, oscillator, 0);
    }

    /**
     * Creates a floating gate with its own oscillation speed (pattern gates, M7).
     *
     * @param x the left edge
     * @param y the top edge of the upper floating pipe
     * @param h the height of the upper floating pipe
     * @param gap the gap height
     * @param oscillator the motion, or {@code null} for a static gate
     * @param speed the oscillation speed in px/s, or {@code 0} for the {@code OSCILLATION_SPEED}
     *     stat
     * @return the gate
     */
    public static PipeGate floating(double x, double y, double h, double gap,
            Oscillator oscillator, double speed) {
        return new PipeGate(x, Layout.FLOATING, 0, y, h, gap, oscillator, speed);
    }

    @Override
    public void update(SimContext ctx) {
        super.update(ctx);
        if (oscillator != null) {
            oscillator.advance(speed > 0 ? ctx.perTick(speed) : ctx.oscillationPerTick());
        }
    }

    @Override
    public void settle() {
        super.settle();
        if (oscillator != null) {
            oscillator.settle();
        }
    }

    @Override
    public List<Hitbox> hitboxesAt(double t) {
        double atX = prevX() + (x() - prevX()) * t;
        double dy = oscillator == null ? 0 : oscillator.offsetAt(t);
        return List.of(upperSegment(atX, dy), lowerSegment(atX, dy));
    }

    @Override
    public double maxDisplacement() {
        double dx = Math.abs(x() - prevX());
        double dy = oscillator == null ? 0 : Math.abs(oscillator.offset() - oscillator.prevOffset());
        return Math.max(dx, dy);
    }

    @Override
    public boolean lethal() {
        return true;
    }

    @Override
    public double safeBandY(double atX) {
        return gapCenterY();
    }

    @Override
    protected long hashGeometry(long hash) {
        long h = MathUtil.fold(hash, layout.ordinal());
        h = MathUtil.fold(h, Double.doubleToLongBits(top));
        h = MathUtil.fold(h, Double.doubleToLongBits(floatY));
        h = MathUtil.fold(h, Double.doubleToLongBits(floatH));
        h = MathUtil.fold(h, Double.doubleToLongBits(gap));
        if (speed > 0) {
            // Only a pattern gate carries its own speed; the classic fold stays what M1 hashed.
            h = MathUtil.fold(h, Double.doubleToLongBits(speed));
        }
        return oscillator == null ? MathUtil.fold(h, -1) : oscillator.hashState(h);
    }

    /**
     * The gate's own oscillation speed.
     *
     * @return px/s, or {@code 0} when the {@code OSCILLATION_SPEED} stat drives it
     */
    public double oscillationSpeed() {
        return speed;
    }

    /**
     * Current upper segment.
     *
     * @return the box
     */
    public Aabb upperSegment() {
        return upperSegment(x(), offsetY());
    }

    /**
     * Current lower segment.
     *
     * @return the box
     */
    public Aabb lowerSegment() {
        return lowerSegment(x(), offsetY());
    }

    private Aabb upperSegment(double atX, double dy) {
        if (layout == Layout.STANDARD) {
            double y0 = -Playfield.TOP_PIPE_EXTRA + dy;
            return new Aabb(atX, y0, Playfield.PIPE_BODY_W, top + Playfield.TOP_PIPE_EXTRA);
        }
        return new Aabb(atX, floatY + dy, Playfield.PIPE_BODY_W, floatH);
    }

    private Aabb lowerSegment(double atX, double dy) {
        if (layout == Layout.STANDARD) {
            double y0 = top + gap + dy;
            return new Aabb(atX, y0, Playfield.PIPE_BODY_W, Playfield.HEIGHT - top - gap);
        }
        double y0 = floatY + floatH + gap + dy;
        double h = Playfield.HEIGHT - 2 * floatY - floatH - gap;
        return new Aabb(atX, y0, Playfield.PIPE_BODY_W, h);
    }

    /**
     * Top edge of the gap (bottom of the upper segment), including the oscillator offset.
     *
     * @return the y
     */
    public double gapTopY() {
        return (layout == Layout.STANDARD ? top : floatY + floatH) + offsetY();
    }

    /**
     * Bottom edge of the gap (top of the lower segment), including the oscillator offset.
     *
     * @return the y
     */
    public double gapBottomY() {
        return gapTopY() + gap;
    }

    /**
     * Vertical centre of the gap.
     *
     * @return the y
     */
    public double gapCenterY() {
        return gapTopY() + gap / 2;
    }

    /**
     * The arrangement.
     *
     * @return the layout
     */
    public Layout layout() {
        return layout;
    }

    /**
     * Gap height.
     *
     * @return the gap
     */
    public double gap() {
        return gap;
    }

    /**
     * Unshifted top of the gap ({@code top} for standard, {@code y + h} for floating).
     *
     * @return the y
     */
    public double baseGapTopY() {
        return layout == Layout.STANDARD ? top : floatY + floatH;
    }

    /**
     * Top edge of the upper floating pipe (floating layout only).
     *
     * @return the y
     */
    public double floatY() {
        return floatY;
    }

    /**
     * Height of the upper floating pipe (floating layout only).
     *
     * @return the height
     */
    public double floatH() {
        return floatH;
    }

    /**
     * The motion driver.
     *
     * @return the oscillator when the gate moves
     */
    public Optional<Oscillator> oscillator() {
        return Optional.ofNullable(oscillator);
    }

    /**
     * Tells whether the gate moves.
     *
     * @return {@code true} when it has an oscillator
     */
    public boolean isMoving() {
        return oscillator != null;
    }

    /**
     * Current vertical offset of the pair.
     *
     * @return 0 for a static gate
     */
    public double offsetY() {
        return oscillator == null ? 0 : oscillator.offset();
    }

    /**
     * Vertical offset of the pair interpolated between the previous tick state ({@code t = 0})
     * and the current one ({@code t = 1}); the renderer uses it so a moving gate is as smooth as
     * a static one (E30.g). Allocation-free, unlike {@link #oscillator()}.
     *
     * @param t the interpolation factor in {@code [0, 1]}
     * @return 0 for a static gate
     */
    public double offsetYAt(double t) {
        return oscillator == null ? 0 : oscillator.offsetAt(t);
    }

    @Override
    public String toString() {
        return "PipeGate{" + layout + (oscillator != null ? "/moving" : "") + ", x=" + x()
                + ", gapTop=" + gapTopY() + ", gap=" + gap + '}';
    }
}
