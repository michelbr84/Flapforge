package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;

/**
 * Everything the random streams decided for one spawn (E32.d): the kind, the gate layout, whether
 * it moves, the gate geometry and — for every kind but the classic stream gate — the typed
 * {@link KindParams}. The sequence of decisions is what {@code DeterminismTest} hashes to prove
 * the obstacle stream is invariant under the player's inputs and choices.
 *
 * <p>{@link #fold} writes the six classic fields first and the params after them, so a Green
 * Fields decision (params {@code null}) folds exactly what it folded before the other families
 * existed, and any parameter of any family is part of the hash.
 *
 * <p>A decision records what the streams drew and nothing else (E32.d): the {@code moving} flag
 * is the rolled one, a gear's rail is the rolled rail, a piston's telegraph is the authored one.
 * {@code ALL_OBSTACLES_MOVE} is applied when the decision is materialised
 * ({@link SpawnTable#materialize}), like {@code GAP_SIZE} is, because the flag can arrive through
 * a rule cycle whose landing tick depends on how the run was played, and a decision that folded
 * it would make the decision hash depend on the pilot.
 *
 * @param kind the obstacle family
 * @param layout the gate layout (gates only, else {@code null})
 * @param moving whether the obstacle moves (a gate oscillates, a gear rides a rail)
 * @param top the top of the gap for a standard gate
 * @param floatY the top edge of the upper floating pipe
 * @param floatH the height of the upper floating pipe
 * @param params the typed geometry of the other kinds, or a pattern gate's extras; {@code null}
 *     for a classic stream gate
 */
public record SpawnDecision(ObstacleKind kind, PipeGate.Layout layout, boolean moving, double top,
        double floatY, double floatH, KindParams params) {

    /**
     * A classic gate decision (M1): no typed params.
     *
     * @param kind the obstacle family
     * @param layout the gate layout
     * @param moving whether the gate oscillates
     * @param top the top of the gap for a standard gate
     * @param floatY the top edge of the upper floating pipe
     * @param floatH the height of the upper floating pipe
     */
    public SpawnDecision(ObstacleKind kind, PipeGate.Layout layout, boolean moving, double top,
            double floatY, double floatH) {
        this(kind, layout, moving, top, floatY, floatH, null);
    }

    /**
     * A gear decision.
     *
     * @param spec the geometry
     * @return the decision ({@code moving} mirrors the rail)
     */
    public static SpawnDecision gear(KindParams.GearSpec spec) {
        return new SpawnDecision(ObstacleKind.GEAR, null, spec.hasRail(), 0, 0, 0, spec);
    }

    /**
     * A piston decision.
     *
     * @param spec the geometry
     * @return the decision
     */
    public static SpawnDecision piston(KindParams.PistonSpec spec) {
        return new SpawnDecision(ObstacleKind.PISTON, null, false, 0, 0, 0, spec);
    }

    /**
     * A wind zone decision.
     *
     * @param spec the geometry
     * @return the decision
     */
    public static SpawnDecision wind(KindParams.WindSpec spec) {
        return new SpawnDecision(ObstacleKind.WIND_ZONE, null, false, 0, 0, 0, spec);
    }

    /**
     * A lightning decision.
     *
     * @param spec the geometry
     * @return the decision
     */
    public static SpawnDecision lightning(KindParams.LightningSpec spec) {
        return new SpawnDecision(ObstacleKind.LIGHTNING, null, false, 0, 0, 0, spec);
    }

    /**
     * The vertical centre of the band a bird is expected to cross this column in, derived from
     * the decision alone (M7 fairness): the gap centre of a gate at the default {@code GAP_SIZE}
     * (or the authored {@code gapSize}), the larger free side of a gear's whole sweep, the free
     * side of a piston, the unlit side of a bolt. It is what the spawn table looks at when it
     * rolls the next lightning column so the bolt's safe band is reachable from here
     * ({@link SpawnTable#roll}). It deliberately ignores the resolved gap, the oscillator and
     * the tick the column is crossed on — everything that could make the next decision depend
     * on the pilot (E32.d).
     *
     * @return the reference y, or {@code NaN} for a wind zone (sky, not a hazard)
     */
    public double referenceBandY() {
        switch (kind) {
            case PIPE_GATE: {
                double gapUsed = params instanceof KindParams.GateSpec g && g.gapSize() > 0
                        ? g.gapSize() : StatId.GAP_SIZE.defaultValue();
                double gapTop = layout == PipeGate.Layout.STANDARD ? top : floatY + floatH;
                return gapTop + gapUsed / 2;
            }
            case GEAR: {
                KindParams.GearSpec g = (KindParams.GearSpec) params;
                double half = g.railAmplitude() / 2 + g.radius();
                double sweepTop = g.cy() - half;
                double sweepBottom = g.cy() + half;
                double above = sweepTop;
                double below = Playfield.GROUND_Y - sweepBottom;
                return above >= below ? sweepTop / 2 : (sweepBottom + Playfield.GROUND_Y) / 2;
            }
            case PISTON: {
                KindParams.PistonSpec p = (KindParams.PistonSpec) params;
                return p.side() == Side.TOP ? (p.length() + Playfield.GROUND_Y) / 2
                        : (Playfield.GROUND_Y - p.length()) / 2;
            }
            case LIGHTNING: {
                KindParams.LightningSpec l = (KindParams.LightningSpec) params;
                double lit = l.lengthFrac() * Playfield.GROUND_Y;
                return l.side() == Side.TOP ? (lit + Playfield.GROUND_Y) / 2
                        : (Playfield.GROUND_Y - lit) / 2;
            }
            case WIND_ZONE:
            default:
                return Double.NaN;
        }
    }

    /**
     * Folds the decision into a hash.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long fold(long hash) {
        long h = MathUtil.fold(hash, kind.ordinal());
        h = MathUtil.fold(h, layout == null ? -1 : layout.ordinal());
        h = MathUtil.fold(h, moving ? 1 : 0);
        h = MathUtil.fold(h, Double.doubleToLongBits(top));
        h = MathUtil.fold(h, Double.doubleToLongBits(floatY));
        h = MathUtil.fold(h, Double.doubleToLongBits(floatH));
        return params == null ? h : params.fold(h);
    }
}
