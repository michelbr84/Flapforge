package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The live obstacles of a run in spawn (= x) order (D6). A plain list: obstacles are added by the
 * spawner and dropped once {@link Obstacle#offscreen()}.
 */
public final class ObstacleLayer {

    private final ArrayList<Obstacle> obstacles = new ArrayList<>();

    /**
     * Advances every obstacle with the world clock and removes the ones that left the playfield.
     *
     * @param ctx the tick context
     */
    public void update(SimContext ctx) {
        for (Obstacle o : obstacles) {
            o.update(ctx);
        }
        obstacles.removeIf(Obstacle::offscreen);
    }

    /** Freezes interpolation state on every obstacle (world freeze in DYING). */
    public void settle() {
        for (Obstacle o : obstacles) {
            o.settle();
        }
    }

    /**
     * Appends an obstacle.
     *
     * @param obstacle the obstacle
     */
    public void add(Obstacle obstacle) {
        obstacles.add(obstacle);
    }

    /**
     * Read-only view in spawn order.
     *
     * @return the obstacles
     */
    public List<Obstacle> obstacles() {
        return Collections.unmodifiableList(obstacles);
    }

    /**
     * The most recently spawned obstacle.
     *
     * @return the last obstacle, or {@code null} when empty
     */
    public Obstacle last() {
        return obstacles.isEmpty() ? null : obstacles.get(obstacles.size() - 1);
    }

    /**
     * Tells whether no obstacle is alive.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return obstacles.isEmpty();
    }

    /**
     * Number of live obstacles.
     *
     * @return the count
     */
    public int size() {
        return obstacles.size();
    }

    /** Removes every obstacle. */
    public void clear() {
        obstacles.clear();
    }

    /**
     * Folds every obstacle into a hash.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, obstacles.size());
        for (Obstacle o : obstacles) {
            h = o.hashState(h);
        }
        return h;
    }
}
