package io.github.michelbr84.flapforge.content.defs;

import java.util.List;
import java.util.Objects;

/**
 * One authored obstacle set piece of {@code patterns.json} (§4, D7, M7): a corridor, a gear run,
 * a bolt lane, a boss phase. The spawner streams it lazily through the same cursor as the plain
 * spawns ({@code PatternStreamer}), one step per cursor spawn.
 *
 * <p>A pattern with a positive {@link #weight} is drawn by its world once {@link #minGate} gates
 * have been passed; the world has to list it in {@code WorldDef.patterns}. A pattern with weight
 * {@code 0} is never drawn by the world: it is a boss phase ({@code boss.patterns}) or the forced
 * pattern of a challenge, streamed on demand.
 *
 * @param id the pattern id
 * @param world the world the pattern belongs to
 * @param weight the draw weight against the world's plain spawns; {@code 0} for boss and forced
 *     patterns
 * @param minGate the gate count from which the world may draw it
 * @param scoringSteps whether the steps score at all; {@code null} (absent) means {@code true}.
 *     A pattern that says {@code false} never advances {@code gatesPassed}, whatever its steps say
 * @param steps the columns, in streaming order
 */
public record PatternDef(String id, String world, int weight, int minGate, Boolean scoringSteps,
        List<PatternStepDef> steps) {

    /**
     * Copies the step list and checks the required fields.
     *
     * @throws NullPointerException when the id or the world is missing
     * @throws IllegalArgumentException when the weight or the gate is negative
     */
    public PatternDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        if (weight < 0) {
            throw new IllegalArgumentException("pattern.weight must not be negative: " + weight);
        }
        if (minGate < 0) {
            throw new IllegalArgumentException("pattern.minGate must not be negative: " + minGate);
        }
        steps = List.copyOf(steps);
    }

    /**
     * Whether the steps score — {@code scoringSteps} with its default applied.
     *
     * @return {@code true} unless the pattern says {@code "scoringSteps": false}
     */
    public boolean stepsScore() {
        return scoringSteps == null || scoringSteps;
    }

    /**
     * The distance from the column before the pattern to its last step, in px.
     *
     * @return the sum of every step's {@code dx}
     */
    public int totalDx() {
        int total = 0;
        for (PatternStepDef step : steps) {
            total += step.dx();
        }
        return total;
    }
}
