package io.github.michelbr84.flapforge.gameplay.obstacle;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Weighted choice of what to spawn (D7): kind weights from the world, then the Green Fields mix
 * that reproduces upstream exactly — {@code P(moving) = MOVING_CHANCE}; a moving gate is floating
 * with probability ¼ (upstream {@code isInProbability(1, 4)}) and standard otherwise; a static
 * gate is standard with probability ½ and floating otherwise.
 *
 * <p>The very first obstacle of a run is the exception, and it is upstream's:
 * {@code GameElementLayer.pipeBornLogic} has a dedicated {@code pipes.size() == 0} branch that
 * emits a static, standard pair and rolls no probability at all. {@link #rollFirst} reproduces
 * it — see that method for the one Flapforge addition.
 *
 * <p>The class is open for one reason (E17): a test needs a world it can predict, and
 * {@code FixedSpawnTable} gets it by overriding {@link #roll} and {@link #rollFirst} to answer
 * with the same standard gate every time. {@link #materialize} is deliberately not part of that
 * seam — a subclass changes what is decided, never how a decision becomes an obstacle.
 *
 * <p>Two streams are consumed: {@code spawn} decides kind, moving flag and layout;
 * {@code obstacle} decides the geometry ({@code top ∈ [80, 400]}, floating {@code h ∈ [106, 160)}
 * then {@code y ∈ [53, 106)} in upstream's draw order). The moving draw is always consumed, even
 * when {@code ALL_OBSTACLES_MOVE} forces the result, so the streams stay aligned whatever rules
 * are active.
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
     * a standard pipe gate with {@code top ∈ [80, 400]}, never floating and never oscillating.
     * The {@code spawn} stream is not touched, because upstream rolled neither the moving nor
     * the layout probability for this pair.
     *
     * <p>The one addition is {@code forceMoving}: the rule flag
     * {@code ALL_OBSTACLES_MOVE} means what it says, so under that rule the opening gate
     * oscillates too. It is off in Green Fields, so the classic opening is bit-identical to
     * upstream's.
     *
     * @param obstacle the {@code obstacle} stream
     * @param forceMoving {@code true} under {@code ALL_OBSTACLES_MOVE}
     * @return the decision
     */
    public SpawnDecision rollFirst(Random obstacle, boolean forceMoving) {
        int top = STANDARD_TOP_MIN + obstacle.nextInt(STANDARD_TOP_MAX - STANDARD_TOP_MIN + 1);
        return new SpawnDecision(ObstacleKind.PIPE_GATE, PipeGate.Layout.STANDARD, forceMoving,
                top, 0, 0);
    }

    /**
     * Draws the next spawn.
     *
     * @param spawn the {@code spawn} stream
     * @param obstacle the {@code obstacle} stream
     * @param movingChance the resolved {@code MOVING_CHANCE}
     * @param forceMoving {@code true} under {@code ALL_OBSTACLES_MOVE}
     * @return the decision
     */
    public SpawnDecision roll(Random spawn, Random obstacle, double movingChance,
            boolean forceMoving) {
        ObstacleKind kind = onlyKind != null ? onlyKind : weightedKind(spawn);
        if (kind != ObstacleKind.PIPE_GATE) {
            throw new UnsupportedOperationException("Obstacle kind not available yet: " + kind);
        }
        boolean moving = spawn.nextDouble() < movingChance;
        moving = moving || forceMoving;
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
            return new SpawnDecision(kind, layout, moving, top, 0, 0);
        }
        int h = FLOATING_H_MIN + obstacle.nextInt(FLOATING_H_MAX - FLOATING_H_MIN);
        int y = FLOATING_Y_MIN + obstacle.nextInt(FLOATING_Y_MAX - FLOATING_Y_MIN);
        return new SpawnDecision(kind, layout, moving, 0, y, h);
    }

    /**
     * Builds the obstacle a decision describes.
     *
     * @param decision the decision
     * @param x the left edge to spawn at
     * @param gap the resolved {@code GAP_SIZE}
     * @return the obstacle
     */
    public Obstacle materialize(SpawnDecision decision, double x, double gap) {
        Oscillator osc = decision.moving() ? Oscillator.classic() : null;
        if (decision.layout() == PipeGate.Layout.STANDARD) {
            return PipeGate.standard(x, decision.top(), gap, osc);
        }
        return PipeGate.floating(x, decision.floatY(), decision.floatH(), gap, osc);
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
