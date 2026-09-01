package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;

/**
 * Everything the random streams decided for one spawn (E32.d): the kind, the gate layout, whether
 * it moves and its geometry. The sequence of decisions is what {@code DeterminismTest} hashes to
 * prove the obstacle stream is invariant under modifier choices.
 *
 * @param kind the obstacle family
 * @param layout the gate layout (gates only)
 * @param moving whether the obstacle oscillates
 * @param top the top of the gap for a standard gate
 * @param floatY the top edge of the upper floating pipe
 * @param floatH the height of the upper floating pipe
 */
public record SpawnDecision(ObstacleKind kind, PipeGate.Layout layout, boolean moving, double top,
        double floatY, double floatH) {

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
        return MathUtil.fold(h, Double.doubleToLongBits(floatH));
    }
}
