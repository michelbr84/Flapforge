package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Upstream's spawn cursor (D7): when the layer is empty the first obstacle appears at
 * {@code x = 420}; afterwards, as soon as the last obstacle is fully inside the playfield
 * ({@code last.x + 40 < 420}) the next one is placed {@code GATE_INTERVAL} after the previous
 * column's right edge measured as if it were a pipe body — {@code last.x + (last.width − 40) +
 * GATE_INTERVAL}. For a 40 px gate that is upstream's {@code last.x + GATE_INTERVAL} to the
 * pixel, so Green Fields is untouched; a 112 px gear or a 200 px wind zone pushes the next
 * column out by its extra width, so two big gears keep the same 120 px of clear air between them
 * that two gates have and a zone never covers the approach to the gate after it (M7 fairness).
 * A 24 px bolt is never pulled closer than a gate would be.
 *
 * <p>The empty-layer spawn also takes upstream's dedicated branch through
 * {@link SpawnTable#rollFirst}: the opening obstacle of every run is a static standard gate and
 * draws nothing from the {@code spawn} stream, because {@code pipeBornLogic} rolled no
 * probability for its first pair. Every later spawn goes through {@link SpawnTable#roll}, which
 * also gets the reference band of the last lethal decision so a lightning column is drawn where
 * the bird can reach its safe band.
 *
 * <p>Patterns (M7, D7) ride the same cursor: before every spawn the {@link PatternStreamer} says
 * whether a set piece is being streamed, and if so the next column is that pattern's next step
 * at {@code last.x + step.dx} instead of the table's draw. The step's geometry goes through
 * {@link SpawnTable#decisionFor} — the {@code obstacle} stream is read only for a gate whose
 * centre is {@code "random"} — and folds into the decision hash like any other decision, so
 * E32.d holds for pattern steps too. A run with no streamer, or a world with no patterns, spawns
 * exactly as it did before patterns existed.
 *
 * <p>{@code ALL_OBSTACLES_MOVE} is read from the tick's rules and handed to
 * {@link SpawnTable#materialize}: the decision itself never carries it (E32.d).
 *
 * <p>Hooks for later milestones: {@link #deferNextSpawn(double, double)} pushes the next spawn
 * further out (modifier breathers, M6) and {@link #setSuppressed(boolean)} stops spawning
 * altogether (boss warnings, M8). {@link #decisionHash()} folds every {@link SpawnDecision}
 * drawn so far.
 */
public final class ObstacleSpawner {

    private static final long HASH_SEED = MathUtil.fnv1a64("spawn-decisions");

    private final ObstacleLayer layer;
    private final SpawnTable table;
    private final PatternStreamer streamer;
    private final Random spawnRng;
    private final Random obstacleRng;
    private final List<Long> decisionHashes = new ArrayList<>();
    private boolean suppressed;
    private double deferredIntervals;
    private double deferredClearancePx;
    private long decisionHash = HASH_SEED;
    private int spawnCount;
    private PatternStreamer.Placement lastPlacement;
    private double lastBandY = Double.NaN;

    /**
     * Creates a spawner drawing from the run's {@code spawn} and {@code obstacle} streams, with
     * no patterns.
     *
     * @param layer the layer to fill
     * @param table the spawn table
     * @param rng the run's random provider
     */
    public ObstacleSpawner(ObstacleLayer layer, SpawnTable table, RandomProvider rng) {
        this(layer, table, rng, null);
    }

    /**
     * Creates a spawner that also streams patterns (M7).
     *
     * @param layer the layer to fill
     * @param table the spawn table
     * @param rng the run's random provider
     * @param streamer the pattern streamer, or {@code null} for a world without patterns
     */
    public ObstacleSpawner(ObstacleLayer layer, SpawnTable table, RandomProvider rng,
            PatternStreamer streamer) {
        this.layer = layer;
        this.table = table;
        this.streamer = streamer != null && streamer.hasWork() ? streamer : null;
        this.spawnRng = rng.stream(RandomProvider.SPAWN);
        this.obstacleRng = rng.stream(RandomProvider.OBSTACLE);
    }

    /**
     * Applies the cursor rule for this tick with no gates passed (the M1 seam; a world with
     * {@code minGate} patterns wants {@link #update(SimContext, int)}).
     *
     * @param ctx the tick context
     * @return the obstacle spawned this tick, or {@code null}
     */
    public Obstacle update(SimContext ctx) {
        return update(ctx, 0);
    }

    /**
     * Applies the cursor rule for this tick (call after the layer scrolled).
     *
     * @param ctx the tick context
     * @param gatesPassed gates passed so far, for the patterns' {@code minGate}
     * @return the obstacle spawned this tick, or {@code null}
     */
    public Obstacle update(SimContext ctx, int gatesPassed) {
        if (suppressed) {
            return null;
        }
        Obstacle last = layer.last();
        boolean first = last == null;
        if (!first && !(last.x() + Playfield.PIPE_BODY_W < Playfield.WIDTH)) {
            return null;
        }
        double interval = ctx.stats().resolve(StatId.GATE_INTERVAL);
        double gap = ctx.stats().resolve(StatId.GAP_SIZE);
        boolean forceMoving = ctx.rules().contains(RuleFlag.ALL_OBSTACLES_MOVE);
        PatternStreamer.Placement placement =
                streamer == null ? null : streamer.next(first, gatesPassed);
        lastPlacement = placement;
        SpawnDecision decision;
        double x;
        if (placement != null) {
            decision = SpawnTable.decisionFor(placement.step().params(), obstacleRng);
            // A breather's deferral still applies inside a set piece: the draft needs its clear
            // window whatever the world was streaming when the schedule gate came up.
            x = first ? Playfield.WIDTH : deferred(last, last.x() + placement.step().dx(),
                    interval);
        } else if (first) {
            decision = table.rollFirst(obstacleRng);
            x = Playfield.WIDTH;
        } else {
            double movingChance = ctx.stats().resolve(StatId.MOVING_CHANCE);
            decision = table.roll(spawnRng, obstacleRng, movingChance, lastBandY);
            x = deferred(last, last.x() + extraWidth(last) + interval, interval);
        }
        if (!first) {
            deferredIntervals = 0;
            deferredClearancePx = 0;
        }
        double band = decision.referenceBandY();
        if (!Double.isNaN(band)) {
            lastBandY = band;
        }
        decisionHash = decision.fold(decisionHash);
        decisionHashes.add(decisionHash);
        spawnCount++;
        Obstacle spawned = table.materialize(decision, x, gap, forceMoving);
        if (placement != null && !placement.scores()) {
            spawned.markNonScoring();
        }
        layer.add(spawned);
        return spawned;
    }

    /**
     * How much wider than a pipe body the last column is: the cursor measures the interval from
     * where a 40 px gate's right edge would be, so a wide kind pushes the next column out by
     * this much and a narrow one is never pulled closer.
     */
    private static double extraWidth(Obstacle last) {
        return Math.max(0, last.width() - Playfield.PIPE_BODY_W);
    }

    /**
     * Applies a pending breather deferral to a natural cursor position: the D11 push of
     * {@code deferredIntervals} gate intervals, and never less than the absolute clear air the
     * draft asked for behind the last column, so the window {@code isDraftPathClear} needs
     * exists whatever the last column's width or the pattern step's {@code dx}.
     */
    private double deferred(Obstacle last, double natural, double interval) {
        double x = natural + interval * deferredIntervals;
        if (deferredClearancePx > 0) {
            x = Math.max(x, last.x() + last.width() + deferredClearancePx);
        }
        return x;
    }

    /**
     * Pushes the next cursor spawn further out by a fraction of the gate interval (a breather,
     * D11). Accumulates until the next spawn consumes it; the "empty layer" spawn ignores it.
     *
     * @param extraIntervals extra distance in gate intervals (1.5 for a modifier breather)
     */
    public void deferNextSpawn(double extraIntervals) {
        deferredIntervals += extraIntervals;
    }

    /**
     * Pushes the next cursor spawn further out by a fraction of the gate interval and, whatever
     * that comes to, at least {@code clearancePx} of clear air after the last column's right
     * edge (M7: a 112 px gear or a 130 px pattern step would otherwise leave the breather no
     * window to open in). Both accumulate until the next spawn consumes them.
     *
     * @param extraIntervals extra distance in gate intervals (1.5 for a modifier breather)
     * @param clearancePx the clear air the next spawn must leave behind the last column, in px
     */
    public void deferNextSpawn(double extraIntervals, double clearancePx) {
        deferredIntervals += extraIntervals;
        deferredClearancePx = Math.max(deferredClearancePx, clearancePx);
    }

    /**
     * Pending deferral in gate intervals.
     *
     * @return the deferral
     */
    public double deferredIntervals() {
        return deferredIntervals;
    }

    /**
     * Pending absolute clearance behind the last column, in px.
     *
     * @return the clearance, 0 when none is pending
     */
    public double deferredClearancePx() {
        return deferredClearancePx;
    }

    /**
     * Stops or resumes spawning.
     *
     * @param suppressed {@code true} to stop
     */
    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }

    /**
     * Tells whether spawning is stopped.
     *
     * @return {@code true} when suppressed
     */
    public boolean isSuppressed() {
        return suppressed;
    }

    /**
     * Hash of every decision drawn so far (E32.d).
     *
     * @return the hash
     */
    public long decisionHash() {
        return decisionHash;
    }

    /**
     * The running hash after each spawn, oldest first: element {@code i} folds decisions
     * {@code 0..i}. Two runs on the same seed must agree on the common prefix of this list
     * however differently they are played (E32.d) — comparing prefixes keeps the invariance
     * checkable when one of the two dies first.
     *
     * @return a read-only view, one entry per spawn
     */
    public List<Long> decisionHashes() {
        return Collections.unmodifiableList(decisionHashes);
    }

    /**
     * Number of obstacles spawned so far.
     *
     * @return the count
     */
    public int spawnCount() {
        return spawnCount;
    }

    /**
     * The table in use.
     *
     * @return the table
     */
    public SpawnTable table() {
        return table;
    }

    /**
     * The pattern streamer, when the world has patterns or the run forces one (M7).
     *
     * @return the streamer, or {@code null}
     */
    public PatternStreamer streamer() {
        return streamer;
    }

    /**
     * The pattern step the most recent spawn came from (M7).
     *
     * @return the placement, or {@code null} when the last spawn was a table draw
     */
    public PatternStreamer.Placement lastPlacement() {
        return lastPlacement;
    }

    /**
     * The reference band of the last lethal decision, the one the next lightning draw is made
     * reachable from.
     *
     * @return the y, or {@code NaN} before the first spawn
     */
    public double lastBandY() {
        return lastBandY;
    }

    /**
     * Folds the streaming state and any pending deferral into a hash (D12); nothing when the run
     * streams no patterns and no breather is pending, so a classic run hashes what it hashed
     * before either existed.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = streamer == null ? hash : streamer.hashState(hash);
        if (deferredIntervals > 0 || deferredClearancePx > 0) {
            h = MathUtil.fold(h, Double.doubleToLongBits(deferredIntervals));
            h = MathUtil.fold(h, Double.doubleToLongBits(deferredClearancePx));
        }
        return h;
    }
}
