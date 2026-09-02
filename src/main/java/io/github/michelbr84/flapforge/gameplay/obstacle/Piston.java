package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import java.util.List;

/**
 * A piston (D6): a head that extends from the top edge or the ground line after a telegraph,
 * cycling {@code TELEGRAPH → EXTEND → HOLD → RETRACT} from the moment it spawns.
 *
 * <p>The phase clock advances by {@code worldDt} per tick, so {@code TIME_SCALE} slows the
 * cycle; {@code phaseOffset} says where in the cycle the piston starts. The lethal hitbox is the
 * extended part of the head only — an {@link Aabb} {@link #WIDTH} wide spanning
 * {@code [0, extension]} from the top or {@code [598 − extension, 598]} from the ground — so a
 * retracted piston is a free column. The head moves up to {@code length / extendTicks} px per
 * tick (25 px for the longest spawn-table piston), which is why {@link #hitboxesAt} interpolates
 * the extension: the tunnelling guard sub-steps through it.
 *
 * <p>A {@link ObstacleSignal#PISTON_TELEGRAPH} is raised every time a telegraph phase starts
 * while the column is on the playfield ({@code 0 < x < 420}), the spawn included when the piston
 * spawns telegraphing inside it: the signal becomes the audio cue, and a cue for a glow the
 * player cannot see (a column still off the right edge, or one already behind the bird) would
 * only mislead. The safe band (E32.c) is the centre of the span the head never reaches. The
 * column scores like a gate.
 */
public final class Piston extends Obstacle {

    /** The cycle phases, in order. */
    public enum Phase {
        /** Warning: the head is retracted and glowing. */
        TELEGRAPH,
        /** The head is moving out. */
        EXTEND,
        /** The head is fully out. */
        HOLD,
        /** The head is moving back. */
        RETRACT
    }

    /** Width of the head and its lethal column (a pipe body). */
    public static final double WIDTH = Playfield.PIPE_BODY_W;
    /** Smallest extension (§4 {@code ParamSpec}). */
    public static final double MIN_LENGTH = 80;
    /** Largest extension (§4 {@code ParamSpec}). */
    public static final double MAX_LENGTH = 360;
    /** Shortest telegraph the content validator accepts (D10 feasibility). */
    public static final int MIN_TELEGRAPH_TICKS = 15;
    /** Default telegraph length (D6). */
    public static final int DEFAULT_TELEGRAPH_TICKS = 40;
    /** Default extend length (D6). */
    public static final int DEFAULT_EXTEND_TICKS = 12;
    /** Default hold length (D6). */
    public static final int DEFAULT_HOLD_TICKS = 30;
    /** Default retract length (D6). */
    public static final int DEFAULT_RETRACT_TICKS = 20;
    /** Telegraph length under {@code ALL_OBSTACLES_MOVE} (D7). */
    public static final int FORCED_TELEGRAPH_TICKS = 25;
    /** Length of the default cycle in ticks. */
    public static final int DEFAULT_CYCLE_TICKS = DEFAULT_TELEGRAPH_TICKS + DEFAULT_EXTEND_TICKS
            + DEFAULT_HOLD_TICKS + DEFAULT_RETRACT_TICKS;

    private final Side side;
    private final double length;
    private final int telegraphTicks;
    private final int extendTicks;
    private final int holdTicks;
    private final int retractTicks;
    private final int phaseOffset;
    private final double cycle;
    private double clock;
    private Phase phase;
    private double extension;
    private double prevExtension;

    /**
     * Creates a piston.
     *
     * @param x the left edge of the column
     * @param side the anchoring edge
     * @param length the full extension in px
     * @param telegraphTicks ticks of warning (at least 1)
     * @param extendTicks ticks to extend (at least 1)
     * @param holdTicks ticks held out (0 allowed)
     * @param retractTicks ticks to retract (at least 1)
     * @param phaseOffset where in the cycle the piston starts, in ticks (taken modulo the cycle)
     */
    public Piston(double x, Side side, double length, int telegraphTicks, int extendTicks,
            int holdTicks, int retractTicks, int phaseOffset) {
        super(ObstacleKind.PISTON, x, WIDTH, true);
        if (length <= 0) {
            throw new IllegalArgumentException("Piston length must be positive: " + length);
        }
        if (telegraphTicks < 1 || extendTicks < 1 || holdTicks < 0 || retractTicks < 1) {
            throw new IllegalArgumentException("Invalid piston phases: " + telegraphTicks + "/"
                    + extendTicks + "/" + holdTicks + "/" + retractTicks);
        }
        if (phaseOffset < 0) {
            throw new IllegalArgumentException("phaseOffset must not be negative: " + phaseOffset);
        }
        this.side = side;
        this.length = length;
        this.telegraphTicks = telegraphTicks;
        this.extendTicks = extendTicks;
        this.holdTicks = holdTicks;
        this.retractTicks = retractTicks;
        this.cycle = telegraphTicks + extendTicks + holdTicks + retractTicks;
        this.phaseOffset = phaseOffset % (int) cycle;
        this.clock = this.phaseOffset;
        this.phase = phaseAtClock(clock);
        this.extension = extensionAtClock(clock);
        this.prevExtension = extension;
        if (phase == Phase.TELEGRAPH && onPlayfield()) {
            raise(ObstacleSignal.PISTON_TELEGRAPH);
        }
    }

    /** Whether any of the column is inside the playfield, where its glow can be seen. */
    private boolean onPlayfield() {
        return x() + WIDTH > 0 && x() < Playfield.WIDTH;
    }

    /**
     * Creates a piston with the default D6 timings.
     *
     * @param x the left edge of the column
     * @param side the anchoring edge
     * @param length the full extension in px
     * @param phaseOffset where in the cycle the piston starts
     * @return the piston
     */
    public static Piston standard(double x, Side side, double length, int phaseOffset) {
        return new Piston(x, side, length, DEFAULT_TELEGRAPH_TICKS, DEFAULT_EXTEND_TICKS,
                DEFAULT_HOLD_TICKS, DEFAULT_RETRACT_TICKS, phaseOffset);
    }

    @Override
    public void update(SimContext ctx) {
        super.update(ctx);
        prevExtension = extension;
        Phase before = phase;
        clock = (clock + ctx.worldDt()) % cycle;
        phase = phaseAtClock(clock);
        extension = extensionAtClock(clock);
        if (phase == Phase.TELEGRAPH && before != Phase.TELEGRAPH && onPlayfield()) {
            raise(ObstacleSignal.PISTON_TELEGRAPH);
        }
    }

    @Override
    public void settle() {
        super.settle();
        prevExtension = extension;
    }

    @Override
    public List<Hitbox> hitboxesAt(double t) {
        double ext = extensionAt(t);
        if (ext <= 0) {
            return List.of();
        }
        double atX = prevX() + (x() - prevX()) * t;
        return List.of(headBox(atX, ext));
    }

    @Override
    public double maxDisplacement() {
        return Math.max(Math.abs(x() - prevX()), Math.abs(extension - prevExtension));
    }

    @Override
    public boolean lethal() {
        return true;
    }

    /**
     * The centre of the span the head never reaches (E32.c).
     *
     * @param atX ignored: the band does not depend on x
     * @return the safe y
     */
    @Override
    public double safeBandY(double atX) {
        return side == Side.TOP ? (length + Playfield.GROUND_Y) / 2
                : (Playfield.GROUND_Y - length) / 2;
    }

    @Override
    protected long hashGeometry(long hash) {
        long h = MathUtil.fold(hash, side.ordinal());
        h = MathUtil.fold(h, Double.doubleToLongBits(length));
        h = MathUtil.fold(h, telegraphTicks);
        h = MathUtil.fold(h, extendTicks);
        h = MathUtil.fold(h, holdTicks);
        h = MathUtil.fold(h, retractTicks);
        h = MathUtil.fold(h, phaseOffset);
        h = MathUtil.fold(h, Double.doubleToLongBits(clock));
        return MathUtil.fold(h, Double.doubleToLongBits(extension));
    }

    /**
     * The head box for an extension, at a column x.
     *
     * @param atX the column left edge
     * @param ext the extension
     * @return the box (empty when {@code ext ≤ 0})
     */
    public Aabb headBox(double atX, double ext) {
        if (side == Side.TOP) {
            return new Aabb(atX, 0, WIDTH, ext);
        }
        return new Aabb(atX, Playfield.GROUND_Y - ext, WIDTH, ext);
    }

    /**
     * The phase the clock is in.
     *
     * @param atClock a clock value in ticks (wrapped into the cycle)
     * @return the phase
     */
    public Phase phaseAtClock(double atClock) {
        double c = wrap(atClock);
        if (c < telegraphTicks) {
            return Phase.TELEGRAPH;
        }
        if (c < telegraphTicks + extendTicks) {
            return Phase.EXTEND;
        }
        if (c < telegraphTicks + extendTicks + holdTicks) {
            return Phase.HOLD;
        }
        return Phase.RETRACT;
    }

    /**
     * The head extension at a clock value — the oracle the bot uses to predict the head at the
     * crossing tick (D21).
     *
     * @param atClock a clock value in ticks (wrapped into the cycle)
     * @return the extension in px
     */
    public double extensionAtClock(double atClock) {
        double c = wrap(atClock);
        if (c < telegraphTicks) {
            return 0;
        }
        c -= telegraphTicks;
        if (c < extendTicks) {
            return length * c / extendTicks;
        }
        c -= extendTicks;
        if (c < holdTicks) {
            return length;
        }
        c -= holdTicks;
        return length * (1 - c / retractTicks);
    }

    private double wrap(double atClock) {
        double c = atClock % cycle;
        return c < 0 ? c + cycle : c;
    }

    /**
     * Extension interpolated between the previous tick state ({@code t = 0}) and the current one.
     *
     * @param t the interpolation factor in {@code [0, 1]}
     * @return the extension in px
     */
    public double extensionAt(double t) {
        return prevExtension + (extension - prevExtension) * t;
    }

    /**
     * Current extension.
     *
     * @return the extension in px
     */
    public double extension() {
        return extension;
    }

    /**
     * Current phase (for the renderer's telegraph glow).
     *
     * @return the phase
     */
    public Phase phase() {
        return phase;
    }

    /**
     * How far into the current phase the clock is.
     *
     * @return a fraction in {@code [0, 1)}
     */
    public double phaseProgress() {
        double c = wrap(clock);
        switch (phase) {
            case TELEGRAPH:
                return c / telegraphTicks;
            case EXTEND:
                return (c - telegraphTicks) / extendTicks;
            case HOLD:
                return holdTicks == 0 ? 0 : (c - telegraphTicks - extendTicks) / holdTicks;
            case RETRACT:
            default:
                return (c - telegraphTicks - extendTicks - holdTicks) / retractTicks;
        }
    }

    /**
     * The phase clock, in ticks of world time since the cycle start.
     *
     * @return the clock in {@code [0, cycle)}
     */
    public double clock() {
        return clock;
    }

    /**
     * Length of one cycle.
     *
     * @return the ticks
     */
    public double cycleTicks() {
        return cycle;
    }

    /**
     * The anchoring edge.
     *
     * @return the side
     */
    public Side side() {
        return side;
    }

    /**
     * The full extension.
     *
     * @return the length in px
     */
    public double length() {
        return length;
    }

    /**
     * Ticks of warning per cycle.
     *
     * @return the telegraph length
     */
    public int telegraphTicks() {
        return telegraphTicks;
    }

    /**
     * Ticks the head takes to extend.
     *
     * @return the extend length
     */
    public int extendTicks() {
        return extendTicks;
    }

    /**
     * Ticks the head stays out.
     *
     * @return the hold length
     */
    public int holdTicks() {
        return holdTicks;
    }

    /**
     * Ticks the head takes to retract.
     *
     * @return the retract length
     */
    public int retractTicks() {
        return retractTicks;
    }

    /**
     * Where in the cycle the piston started.
     *
     * @return the offset in ticks
     */
    public int phaseOffset() {
        return phaseOffset;
    }

    @Override
    public String toString() {
        return "Piston{" + side + ", x=" + x() + ", length=" + length + ", " + phase + " ext="
                + extension + '}';
    }
}
