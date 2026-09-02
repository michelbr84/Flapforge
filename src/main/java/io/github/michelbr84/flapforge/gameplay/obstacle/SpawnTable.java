package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Weighted choice of what to spawn (D7): kind weights from the world, then the per-kind geometry.
 * For gates it is the Green Fields mix that reproduces upstream exactly — {@code P(moving) =
 * MOVING_CHANCE}; a moving gate is floating with probability ¼ (upstream
 * {@code isInProbability(1, 4)}) and standard otherwise; a static gate is standard with
 * probability ½ and floating otherwise.
 *
 * <p>The very first obstacle of a run is the exception, and it is upstream's:
 * {@code GameElementLayer.pipeBornLogic} has a dedicated {@code pipes.size() == 0} branch that
 * emits a static, standard pair and rolls no probability at all. {@link #rollFirst} reproduces
 * it in every world.
 *
 * <p>The class is open for one reason (E17): a test needs a world it can predict, and
 * {@code FixedSpawnTable} gets it by overriding {@link #roll} and {@link #rollFirst} to answer
 * with the same standard gate every time. {@link #materialize} is deliberately not part of that
 * seam — a subclass changes what is decided, never how a decision becomes an obstacle.
 *
 * <p>Two streams are consumed. {@code spawn} decides the kind (one {@code nextInt} when the
 * world mixes kinds; nothing when it has a single kind, which keeps Green Fields bit-identical to
 * M1), the moving flag of a gate or the rail of a gear (one {@code nextDouble} against
 * {@code MOVING_CHANCE}) and the gate layout (one {@code nextDouble}). {@code obstacle} decides
 * the geometry:
 *
 * <ul>
 *   <li>gate: {@code top ∈ [80, 400]}, or floating {@code h ∈ [106, 160)} then
 *       {@code y ∈ [53, 106)} in upstream's draw order;</li>
 *   <li>gear: radius {@code ∈ [24, 56]}, then the sweep centre uniformly in the band that keeps
 *       a railed circle {@link #EDGE_CLEARANCE_PX} clear of the ceiling and the ground — the
 *       band is the rail band whether or not the gear got a rail, so the flag changes the rail and
 *       nothing else; rail = amplitude 60, speed 40;</li>
 *   <li>piston: side ½/½, length {@code ∈ [120, 300]}, phase offset {@code ∈ [0, 102)} (the
 *       default cycle), default D6 timings;</li>
 *   <li>wind zone: width {@code ∈ [100, 200]}, height {@code ∈ [200, 320]}, centre in the middle
 *       band {@code [200, 398]}, then one of four effects: updraft or downdraft
 *       {@code ∓500 px/s²}, tailwind or headwind {@code ∓40 px/s};</li>
 *   <li>lightning: side ½/½, lit fraction {@code ∈ [0.35, 0.65]} in hundredths, warning
 *       {@value LightningStrike#TABLE_WARNING_TICKS} ticks, strike 10 ticks — then the
 *       reachability rule below.</li>
 * </ul>
 *
 * <p><b>A decision is what the streams drew, nothing more (E32.d).</b> {@code ALL_OBSTACLES_MOVE}
 * is applied by {@link #materialize}, per kind (D7): a gate oscillates, a gear gets the default
 * rail, a piston's telegraph shrinks to 25 ticks, a bolt and a wind zone are unchanged. The flag
 * can arrive through a Void rule cycle whose landing tick depends on how the run was played (a
 * draft holds it, a scroll card moves it), so a decision that folded it would make the decision
 * hash depend on the pilot. The draws are the same whether or not the flag is on, so the streams
 * stay aligned whatever rules are active.
 *
 * <p><b>Lightning is reachable from the column before it.</b> A bolt spawned by the table takes
 * the slot 160 px after the previous column, and its side is only readable once it warns, so a
 * bolt whose unlit band is on the far side of the previous gap would ask for more climb than the
 * scroll between the two columns allows. {@link #roll} therefore takes the reference band of the
 * previous decision ({@link SpawnDecision#referenceBandY}): the rolled side and fraction are
 * drawn exactly as before, then the side is swapped to the one whose unlit band is nearer that
 * reference and the fraction is capped so the bird has at most {@link #LIGHTNING_MAX_TRAVEL_PX}
 * of vertical travel from the reference to the safe band (never below {@code 0.30}). The
 * reference comes from the decision, not the live obstacle, so the bolt still depends on the seed
 * alone. Patterns author their bolts and are checked by the validator instead.
 *
 * <p><b>Scoring rule.</b> Every lethal column the table draws — gate, gear, piston, lightning —
 * is a scoring column: it takes a gate's slot in the cursor cadence, so clearing it must advance
 * {@code gatesPassed} or the gate-keyed difficulty curve (D20) would stall in a gear-heavy world
 * and the coin trail (E2) would skip it. A wind zone is not lethal and never scores; it is a
 * stretch of sky, not a hazard.
 */
public class SpawnTable {

    /** Smallest top of a standard gap (upstream {@code MIN_HEIGHT = 640 >> 3}). */
    public static final int STANDARD_TOP_MIN = 80;
    /** Largest top of a standard gap, inclusive (upstream {@code MAX_HEIGHT = (640 >> 3) * 5}). */
    public static final int STANDARD_TOP_MAX = 400;
    /** Smallest floating y (upstream {@code 640 / 12}). */
    public static final int FLOATING_Y_MIN = 53;
    /** Exclusive upper bound of the floating y (upstream {@code 640 / 6}). */
    public static final int FLOATING_Y_MAX = 106;
    /** Smallest floating height (upstream {@code 640 / 6}). */
    public static final int FLOATING_H_MIN = 106;
    /** Exclusive upper bound of the floating height (upstream {@code 640 / 4}). */
    public static final int FLOATING_H_MAX = 160;
    /** Share of moving gates that float (upstream ¼). */
    public static final double MOVING_FLOATING_SHARE = 0.25;
    /** Share of static gates that are standard (upstream ½). */
    public static final double STATIC_STANDARD_SHARE = 0.5;

    /** Clearance a spawned gear's sweep keeps from the ceiling and the ground, in px. */
    public static final int EDGE_CLEARANCE_PX = 16;
    /** Smallest spawn-table gear radius. */
    public static final int GEAR_RADIUS_MIN = 24;
    /** Largest spawn-table gear radius, inclusive. */
    public static final int GEAR_RADIUS_MAX = 56;
    /** Smallest spawn-table piston length. */
    public static final int PISTON_LENGTH_MIN = 120;
    /** Largest spawn-table piston length, inclusive. */
    public static final int PISTON_LENGTH_MAX = 300;
    /** Smallest spawn-table wind zone width. */
    public static final int WIND_WIDTH_MIN = 100;
    /** Largest spawn-table wind zone width, inclusive. */
    public static final int WIND_WIDTH_MAX = 200;
    /** Smallest spawn-table wind zone height. */
    public static final int WIND_HEIGHT_MIN = 200;
    /** Largest spawn-table wind zone height, inclusive. */
    public static final int WIND_HEIGHT_MAX = 320;
    /** Top of the middle band a spawn-table wind zone is centred in. */
    public static final int WIND_CY_MIN = 200;
    /** Bottom of the middle band a spawn-table wind zone is centred in, inclusive. */
    public static final int WIND_CY_MAX = 398;
    /** Vertical push of a spawn-table wind zone, in px/s². */
    public static final double WIND_ACCEL = 500;
    /** Scroll change of a spawn-table wind zone, in px/s. */
    public static final double WIND_SCROLL_DELTA = 40;
    /** Smallest spawn-table lit fraction, in hundredths. */
    public static final int LIGHTNING_FRAC_MIN_PCT = 35;
    /** Largest spawn-table lit fraction, in hundredths, inclusive. */
    public static final int LIGHTNING_FRAC_MAX_PCT = 65;
    /**
     * Room the bird's box needs between the reference band and the edge of a bolt's lit span,
     * in px: half the classic hitbox height plus a landing margin.
     */
    public static final double LIGHTNING_BAND_MARGIN_PX = 24;
    /**
     * Most vertical travel a spawn-table bolt may ask for between the previous column's
     * reference band and its own safe band, in px. The scroll between a 40 px column clearing
     * the bird's box and the bolt's strike is 115 px: at the 360 px/s scroll cap that is 19
     * ticks, in which the bird climbs about 119 px flapping every tick or falls 95 px from
     * rest, and the bird is free to start moving while it is still inside the previous gap.
     */
    public static final double LIGHTNING_MAX_TRAVEL_PX = 80;

    /** Green Fields: pipe gates only. */
    public static final SpawnTable GREEN_FIELDS = new SpawnTable(Map.of(ObstacleKind.PIPE_GATE, 100));

    private final EnumMap<ObstacleKind, Integer> weights = new EnumMap<>(ObstacleKind.class);
    private final int totalWeight;
    private final ObstacleKind onlyKind;

    /**
     * Creates a table.
     *
     * @param weights positive weight per kind; kinds with weight 0 are dropped
     */
    public SpawnTable(Map<ObstacleKind, Integer> weights) {
        int total = 0;
        ObstacleKind single = null;
        for (ObstacleKind kind : ObstacleKind.values()) {
            Integer w = weights.get(kind);
            if (w == null || w <= 0) {
                continue;
            }
            this.weights.put(kind, w);
            total += w;
            single = kind;
        }
        if (total == 0) {
            throw new IllegalArgumentException("A spawn table needs at least one positive weight");
        }
        this.totalWeight = total;
        this.onlyKind = this.weights.size() == 1 ? single : null;
    }

    /**
     * Draws the opening obstacle of a run, the way upstream's empty-container branch did: always
     * a standard pipe gate with {@code top ∈ [80, 400]}, never floating and never oscillating,
     * in every world. The {@code spawn} stream is not touched, because upstream rolled neither
     * the moving nor the layout probability for this pair. Under {@code ALL_OBSTACLES_MOVE} the
     * opening gate oscillates too — applied by {@link #materialize}, so the decision is the same.
     *
     * @param obstacle the {@code obstacle} stream
     * @return the decision
     */
    public SpawnDecision rollFirst(Random obstacle) {
        int top = STANDARD_TOP_MIN + obstacle.nextInt(STANDARD_TOP_MAX - STANDARD_TOP_MIN + 1);
        return new SpawnDecision(ObstacleKind.PIPE_GATE, PipeGate.Layout.STANDARD, false, top, 0,
                0);
    }

    /**
     * Draws the next spawn with no previous column to be fair to (a table drawn in isolation).
     *
     * @param spawn the {@code spawn} stream
     * @param obstacle the {@code obstacle} stream
     * @param movingChance the resolved {@code MOVING_CHANCE}
     * @return the decision
     */
    public SpawnDecision roll(Random spawn, Random obstacle, double movingChance) {
        return roll(spawn, obstacle, movingChance, Double.NaN);
    }

    /**
     * Draws the next spawn.
     *
     * @param spawn the {@code spawn} stream
     * @param obstacle the {@code obstacle} stream
     * @param movingChance the resolved {@code MOVING_CHANCE}
     * @param previousBandY the reference band of the last lethal column
     *     ({@link SpawnDecision#referenceBandY}), or {@code NaN} when there is none; only a
     *     lightning draw reads it
     * @return the decision
     */
    public SpawnDecision roll(Random spawn, Random obstacle, double movingChance,
            double previousBandY) {
        ObstacleKind kind = onlyKind != null ? onlyKind : weightedKind(spawn);
        switch (kind) {
            case GEAR:
                return rollGear(spawn, obstacle, movingChance);
            case PISTON:
                return rollPiston(obstacle);
            case WIND_ZONE:
                return rollWind(obstacle);
            case LIGHTNING:
                return rollLightning(obstacle, previousBandY);
            case PIPE_GATE:
            default:
                return rollGate(spawn, obstacle, movingChance);
        }
    }

    private static SpawnDecision rollGate(Random spawn, Random obstacle, double movingChance) {
        boolean moving = spawn.nextDouble() < movingChance;
        double layoutRoll = spawn.nextDouble();
        PipeGate.Layout layout;
        if (moving) {
            layout = layoutRoll < MOVING_FLOATING_SHARE ? PipeGate.Layout.FLOATING
                    : PipeGate.Layout.STANDARD;
        } else {
            layout = layoutRoll < STATIC_STANDARD_SHARE ? PipeGate.Layout.STANDARD
                    : PipeGate.Layout.FLOATING;
        }
        if (layout == PipeGate.Layout.STANDARD) {
            int top = STANDARD_TOP_MIN + obstacle.nextInt(STANDARD_TOP_MAX - STANDARD_TOP_MIN + 1);
            return new SpawnDecision(ObstacleKind.PIPE_GATE, layout, moving, top, 0, 0);
        }
        int h = FLOATING_H_MIN + obstacle.nextInt(FLOATING_H_MAX - FLOATING_H_MIN);
        int y = FLOATING_Y_MIN + obstacle.nextInt(FLOATING_Y_MAX - FLOATING_Y_MIN);
        return new SpawnDecision(ObstacleKind.PIPE_GATE, layout, moving, 0, y, h);
    }

    private static SpawnDecision rollGear(Random spawn, Random obstacle, double movingChance) {
        boolean rail = spawn.nextDouble() < movingChance;
        int radius = GEAR_RADIUS_MIN + obstacle.nextInt(GEAR_RADIUS_MAX - GEAR_RADIUS_MIN + 1);
        int halfSweep = (int) Math.ceil(Gear.DEFAULT_RAIL_AMPLITUDE / 2);
        int lo = EDGE_CLEARANCE_PX + radius + halfSweep;
        int hi = Playfield.GROUND_Y - EDGE_CLEARANCE_PX - radius - halfSweep;
        int cy = lo + obstacle.nextInt(hi - lo + 1);
        return SpawnDecision.gear(new KindParams.GearSpec(cy, radius,
                rail ? Gear.DEFAULT_RAIL_AMPLITUDE : 0, rail ? Gear.DEFAULT_RAIL_SPEED : 0));
    }

    private static SpawnDecision rollPiston(Random obstacle) {
        Side side = obstacle.nextInt(2) == 0 ? Side.TOP : Side.BOTTOM;
        int length = PISTON_LENGTH_MIN + obstacle.nextInt(PISTON_LENGTH_MAX - PISTON_LENGTH_MIN + 1);
        int phaseOffset = obstacle.nextInt(Piston.DEFAULT_CYCLE_TICKS);
        return SpawnDecision.piston(new KindParams.PistonSpec(side, length,
                Piston.DEFAULT_TELEGRAPH_TICKS, Piston.DEFAULT_EXTEND_TICKS,
                Piston.DEFAULT_HOLD_TICKS, Piston.DEFAULT_RETRACT_TICKS, phaseOffset));
    }

    private static SpawnDecision rollWind(Random obstacle) {
        int width = WIND_WIDTH_MIN + obstacle.nextInt(WIND_WIDTH_MAX - WIND_WIDTH_MIN + 1);
        int height = WIND_HEIGHT_MIN + obstacle.nextInt(WIND_HEIGHT_MAX - WIND_HEIGHT_MIN + 1);
        int cy = WIND_CY_MIN + obstacle.nextInt(WIND_CY_MAX - WIND_CY_MIN + 1);
        int effect = obstacle.nextInt(4);
        double accelY = effect == 0 ? -WIND_ACCEL : effect == 1 ? WIND_ACCEL : 0;
        double scrollDelta = effect == 2 ? -WIND_SCROLL_DELTA : effect == 3 ? WIND_SCROLL_DELTA : 0;
        return SpawnDecision.wind(new KindParams.WindSpec(width, cy, height, accelY, scrollDelta));
    }

    private static SpawnDecision rollLightning(Random obstacle, double previousBandY) {
        Side side = obstacle.nextInt(2) == 0 ? Side.TOP : Side.BOTTOM;
        int pct = LIGHTNING_FRAC_MIN_PCT
                + obstacle.nextInt(LIGHTNING_FRAC_MAX_PCT - LIGHTNING_FRAC_MIN_PCT + 1);
        double frac = pct / 100.0;
        if (!Double.isNaN(previousBandY)) {
            side = reachableSide(side, frac, previousBandY);
            frac = reachableFraction(side, frac, previousBandY);
        }
        return SpawnDecision.lightning(new KindParams.LightningSpec(side, frac,
                LightningStrike.TABLE_WARNING_TICKS, LightningStrike.DEFAULT_STRIKE_TICKS));
    }

    /**
     * The vertical travel a bolt asks for from a reference band: how far the bird's box has to
     * move from {@code fromY} to sit {@link #LIGHTNING_BAND_MARGIN_PX} clear of the lit span.
     *
     * @param side the edge the bolt hangs from
     * @param frac the lit fraction
     * @param fromY the reference band
     * @return the travel in px, 0 when the reference is already in the safe band
     */
    public static double lightningTravel(Side side, double frac, double fromY) {
        double lit = frac * Playfield.GROUND_Y;
        if (side == Side.TOP) {
            return Math.max(0, lit + LIGHTNING_BAND_MARGIN_PX - fromY);
        }
        return Math.max(0, fromY - (Playfield.GROUND_Y - lit - LIGHTNING_BAND_MARGIN_PX));
    }

    /** The side whose unlit band is nearer the reference; the rolled side on a tie. */
    private static Side reachableSide(Side rolled, double frac, double fromY) {
        double top = lightningTravel(Side.TOP, frac, fromY);
        double bottom = lightningTravel(Side.BOTTOM, frac, fromY);
        if (top == bottom) {
            return rolled;
        }
        return top < bottom ? Side.TOP : Side.BOTTOM;
    }

    /**
     * The rolled fraction, shortened until the travel fits {@link #LIGHTNING_MAX_TRAVEL_PX}, in
     * hundredths like the roll, never below the smallest authored fraction.
     */
    private static double reachableFraction(Side side, double rolled, double fromY) {
        double frac = rolled;
        while (frac > LightningStrike.MIN_LENGTH_FRAC + 1e-9
                && lightningTravel(side, frac, fromY) > LIGHTNING_MAX_TRAVEL_PX) {
            frac = Math.round((frac - 0.01) * 100) / 100.0;
        }
        return Math.max(LightningStrike.MIN_LENGTH_FRAC, frac);
    }

    /**
     * Turns the typed parameters of a pattern step into a decision (the {@code PatternStreamer}'s
     * entry point, M7). A gate's {@code "random"} gap centre is rolled from the given stream
     * with upstream's draws (standard: {@code top}; floating: {@code h} then {@code y}); an
     * authored centre is placed so a gap of {@code gapSize} (or the default {@code GAP_SIZE}) is
     * centred on it, clamped into the classic ranges. The decision never reads the resolved gap:
     * {@link #materialize} scales the authored gap by the run's multiplier and keeps the centre
     * where it was authored, so the decision depends on the seed alone (E32.d).
     *
     * @param params the step parameters
     * @param geometry the {@code obstacle} stream — the stream every geometry draw comes from —
     *     read only for a gate whose centre is {@code "random"}; an authored step draws nothing
     * @return the decision
     */
    public static SpawnDecision decisionFor(KindParams params, Random geometry) {
        if (params instanceof KindParams.GateSpec g) {
            double base = authoredGap(g);
            if (g.layout() == PipeGate.Layout.STANDARD) {
                int top = g.randomGapCenter()
                        ? STANDARD_TOP_MIN + geometry.nextInt(STANDARD_TOP_MAX - STANDARD_TOP_MIN + 1)
                        : (int) Math.round(MathUtil.clamp(
                                g.gapCenter() * Playfield.GROUND_Y - base / 2,
                                STANDARD_TOP_MIN, STANDARD_TOP_MAX));
                return new SpawnDecision(ObstacleKind.PIPE_GATE, PipeGate.Layout.STANDARD,
                        g.oscillate(), top, 0, 0, g);
            }
            int h;
            int y;
            if (g.randomGapCenter()) {
                h = FLOATING_H_MIN + geometry.nextInt(FLOATING_H_MAX - FLOATING_H_MIN);
                y = FLOATING_Y_MIN + geometry.nextInt(FLOATING_Y_MAX - FLOATING_Y_MIN);
            } else {
                h = ObstacleParams.PATTERN_FLOAT_H;
                y = (int) Math.round(MathUtil.clamp(
                        g.gapCenter() * Playfield.GROUND_Y - base / 2 - h,
                        FLOATING_Y_MIN, FLOATING_Y_MAX - 1));
            }
            return new SpawnDecision(ObstacleKind.PIPE_GATE, PipeGate.Layout.FLOATING,
                    g.oscillate(), 0, y, h, g);
        }
        if (params instanceof KindParams.GearSpec g) {
            return SpawnDecision.gear(g);
        }
        if (params instanceof KindParams.PistonSpec p) {
            return SpawnDecision.piston(p);
        }
        if (params instanceof KindParams.WindSpec w) {
            return SpawnDecision.wind(w);
        }
        return SpawnDecision.lightning((KindParams.LightningSpec) params);
    }

    /** The gap a pattern gate was authored with: {@code gapSize}, or the default {@code GAP_SIZE}. */
    private static double authoredGap(KindParams.GateSpec g) {
        return g.gapSize() > 0 ? g.gapSize() : StatId.GAP_SIZE.defaultValue();
    }

    /**
     * Builds the obstacle a decision describes, with no rule forcing motion.
     *
     * @param decision the decision
     * @param x the left edge to spawn at
     * @param gap the resolved {@code GAP_SIZE}
     * @return the obstacle
     */
    public Obstacle materialize(SpawnDecision decision, double x, double gap) {
        return materialize(decision, x, gap, false);
    }

    /**
     * Builds the obstacle a decision describes. {@code ALL_OBSTACLES_MOVE} applies here, per kind
     * (D7): a gate oscillates, a gear rides the default rail, a piston's telegraph shrinks to
     * {@link Piston#FORCED_TELEGRAPH_TICKS} (an authored shorter one is kept), a bolt and a wind
     * zone are unchanged. A pattern gate's authored {@code gapSize} is scaled by the run's gap
     * multiplier ({@code gap / 128}: tier, curve, cycle and cards alike), so the validator's
     * {@code gapSize × tightest tier multiplier × 0.9 ≥ 54.5} describes what the run does; the
     * gap stays centred where it was authored.
     *
     * @param decision the decision
     * @param x the left edge to spawn at
     * @param gap the resolved {@code GAP_SIZE}
     * @param forceMoving {@code true} under {@code ALL_OBSTACLES_MOVE}
     * @return the obstacle
     */
    public Obstacle materialize(SpawnDecision decision, double x, double gap, boolean forceMoving) {
        KindParams params = decision.params();
        switch (decision.kind()) {
            case GEAR: {
                KindParams.GearSpec g = (KindParams.GearSpec) params;
                if (forceMoving) {
                    g = g.withRail();
                }
                return new Gear(x, g.cy(), g.radius(), g.railAmplitude(), g.railSpeed());
            }
            case PISTON: {
                KindParams.PistonSpec p = (KindParams.PistonSpec) params;
                if (forceMoving) {
                    p = p.withForcedTelegraph();
                }
                return new Piston(x, p.side(), p.length(), p.telegraphTicks(), p.extendTicks(),
                        p.holdTicks(), p.retractTicks(), p.phaseOffset());
            }
            case WIND_ZONE: {
                KindParams.WindSpec w = (KindParams.WindSpec) params;
                return new WindZone(x, w.width(), w.cy(), w.height(), w.accelY(), w.scrollDelta());
            }
            case LIGHTNING: {
                KindParams.LightningSpec l = (KindParams.LightningSpec) params;
                return new LightningStrike(x, l.side(), l.lengthFrac(), l.warningTicks(),
                        l.strikeTicks());
            }
            case PIPE_GATE:
            default:
                return materializeGate(decision, x, gap, forceMoving);
        }
    }

    /**
     * The gap a pattern gate gets in a run: the authored {@code gapSize} scaled by the resolved
     * {@code GAP_SIZE} over its default.
     *
     * @param authored the authored gap
     * @param gap the resolved {@code GAP_SIZE}
     * @return the gap in px
     */
    public static double scaledPatternGap(double authored, double gap) {
        return authored * gap / StatId.GAP_SIZE.defaultValue();
    }

    private static Obstacle materializeGate(SpawnDecision decision, double x, double gap,
            boolean forceMoving) {
        KindParams.GateSpec extra = decision.params() instanceof KindParams.GateSpec g ? g : null;
        double gapUsed = gap;
        double shift = 0;
        if (extra != null && extra.gapSize() > 0) {
            gapUsed = scaledPatternGap(extra.gapSize(), gap);
            if (!extra.randomGapCenter()) {
                // The decision placed a gap of the authored size on the authored centre; a
                // scaled gap is moved by half the difference so the centre stays put.
                shift = (extra.gapSize() - gapUsed) / 2;
            }
        }
        boolean moving = decision.moving() || forceMoving;
        Oscillator osc = null;
        double speed = 0;
        if (moving) {
            osc = extra != null ? new Oscillator(extra.amplitude()) : Oscillator.classic();
            speed = extra != null ? extra.speed() : 0;
        }
        if (decision.layout() == PipeGate.Layout.STANDARD) {
            return PipeGate.standard(x, decision.top() + shift, gapUsed, osc, speed);
        }
        return PipeGate.floating(x, decision.floatY() + shift, decision.floatH(), gapUsed, osc,
                speed);
    }

    /**
     * Read-only view of the weights.
     *
     * @return the weights in kind order
     */
    public Map<ObstacleKind, Integer> weights() {
        return Collections.unmodifiableMap(weights);
    }

    private ObstacleKind weightedKind(Random spawn) {
        int roll = spawn.nextInt(totalWeight);
        for (Map.Entry<ObstacleKind, Integer> e : weights.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) {
                return e.getKey();
            }
        }
        throw new IllegalStateException("weighted draw out of range");
    }
}
