package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.gameplay.obstacle.KindParams;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import java.util.List;
import java.util.Objects;

/**
 * An authored obstacle set piece as the simulation sees it (§4 {@code patterns.json}, D7, M7):
 * the seam between {@code PatternDef} and the {@code PatternStreamer}. The parameters of every
 * step are already typed ({@link KindParams}), so the streamer never sees JSON.
 *
 * @param id the pattern id
 * @param weight the draw weight against the world's plain spawns; {@code 0} for a pattern that is
 *     only streamed on demand (boss phases, a challenge's forced pattern)
 * @param minGate the gate count from which the world may draw it
 * @param scoringSteps whether the steps score at all
 * @param steps the columns in streaming order, never empty
 */
public record PatternSpec(String id, int weight, int minGate, boolean scoringSteps,
        List<Step> steps) {

    /**
     * One column of a pattern.
     *
     * @param dx the distance from the previous column's left edge to this one, in px
     * @param kind the obstacle family
     * @param params the typed geometry (a gate's {@code "random"} centre is still unresolved
     *     here — the streamer rolls it from the {@code obstacle} stream when the column spawns)
     * @param scoring whether clearing the column awards a gate (the pattern's
     *     {@code scoringSteps} can still veto it)
     */
    public record Step(double dx, ObstacleKind kind, KindParams params, boolean scoring) {

        /**
         * Validates the components.
         *
         * @param dx the distance
         * @param kind the kind
         * @param params the parameters
         * @param scoring whether the step scores
         */
        public Step {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(params, "params");
            if (!(dx > 0)) {
                throw new IllegalArgumentException("a step's dx must be positive: " + dx);
            }
        }
    }

    /**
     * Validates the components.
     *
     * @param id the id
     * @param weight the draw weight
     * @param minGate the first gate the pattern may start at
     * @param scoringSteps whether the steps score
     * @param steps the steps
     */
    public PatternSpec {
        Objects.requireNonNull(id, "id");
        if (weight < 0 || minGate < 0) {
            throw new IllegalArgumentException("weight and minGate must not be negative: "
                    + weight + "/" + minGate);
        }
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("pattern '" + id + "' has no steps");
        }
    }

    /**
     * Whether a step of this pattern scores: the step's own flag under the pattern's veto.
     *
     * @param index the step index
     * @return {@code true} when clearing that column awards a gate
     */
    public boolean stepScores(int index) {
        return scoringSteps && steps.get(index).scoring();
    }

    /**
     * The distance from the column before the pattern to its last step, in px.
     *
     * @return the sum of every step's {@code dx}
     */
    public double totalDx() {
        double total = 0;
        for (Step step : steps) {
            total += step.dx();
        }
        return total;
    }
}
