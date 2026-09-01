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
 * ({@code last.x + 40 < 420}) the next one is placed at {@code last.x + GATE_INTERVAL}.
 *
 * <p>The empty-layer spawn also takes upstream's dedicated branch through
 * {@link SpawnTable#rollFirst}: the opening obstacle of every run is a static standard gate and
 * draws nothing from the {@code spawn} stream, because {@code pipeBornLogic} rolled no
 * probability for its first pair. Every later spawn goes through {@link SpawnTable#roll}.
 *
 * <p>Hooks for later milestones: {@link #deferNextSpawn(double)} pushes the next spawn further out
 * (modifier breathers, M6) and {@link #setSuppressed(boolean)} stops spawning altogether (boss
 * warnings, M8). {@link #decisionHash()} folds every {@link SpawnDecision} drawn so far.
 */
public final class ObstacleSpawner {

    private static final long HASH_SEED = MathUtil.fnv1a64("spawn-decisions");

    private final ObstacleLayer layer;
    private final SpawnTable table;
    private final Random spawnRng;
    private final Random obstacleRng;
    private final List<Long> decisionHashes = new ArrayList<>();
    private boolean suppressed;
    private double deferredIntervals;
    private long decisionHash = HASH_SEED;
    private int spawnCount;

    /**
     * Creates a spawner drawing from the run's {@code spawn} and {@code obstacle} streams.
     *
     * @param layer the layer to fill
     * @param table the spawn table
     * @param rng the run's random provider
     */
    public ObstacleSpawner(ObstacleLayer layer, SpawnTable table, RandomProvider rng) {
        this.layer = layer;
        this.table = table;
        this.spawnRng = rng.stream(RandomProvider.SPAWN);
        this.obstacleRng = rng.stream(RandomProvider.OBSTACLE);
    }

    /**
     * Applies the cursor rule for this tick (call after the layer scrolled).
     *
     * @param ctx the tick context
     * @return the obstacle spawned this tick, or {@code null}
     */
    public Obstacle update(SimContext ctx) {
        if (suppressed) {
            return null;
        }
        double x;
        boolean first;
        Obstacle last = layer.last();
        if (last == null) {
            x = Playfield.WIDTH;
            first = true;
        } else {
            if (!(last.x() + Playfield.PIPE_BODY_W < Playfield.WIDTH)) {
                return null;
            }
            double interval = ctx.stats().resolve(StatId.GATE_INTERVAL);
            x = last.x() + interval * (1 + deferredIntervals);
            deferredIntervals = 0;
            first = false;
        }
        boolean forceMoving = ctx.rules().contains(RuleFlag.ALL_OBSTACLES_MOVE);
        SpawnDecision decision;
        if (first) {
            decision = table.rollFirst(obstacleRng, forceMoving);
        } else {
            double movingChance = ctx.stats().resolve(StatId.MOVING_CHANCE);
            decision = table.roll(spawnRng, obstacleRng, movingChance, forceMoving);
        }
        decisionHash = decision.fold(decisionHash);
        decisionHashes.add(decisionHash);
        spawnCount++;
        Obstacle spawned = table.materialize(decision, x, ctx.stats().resolve(StatId.GAP_SIZE));
        layer.add(spawned);
        return spawned;
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
     * Pending deferral in gate intervals.
     *
     * @return the deferral
     */
    public double deferredIntervals() {
        return deferredIntervals;
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
}
