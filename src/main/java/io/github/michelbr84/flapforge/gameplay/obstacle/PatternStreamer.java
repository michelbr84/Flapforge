package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.spec.PatternSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Streams authored set pieces through the spawn cursor (D7, M7). The {@link ObstacleSpawner}
 * asks it before every cursor spawn: while a chunk is active the answer is the chunk's next step,
 * placed at {@code last.x + dx}; between chunks the streamer decides — from the {@code patterns}
 * stream alone — whether a new one starts.
 *
 * <p><b>Selection rule.</b> At a cursor spawn with no active chunk the eligible patterns are the
 * world's patterns with a positive weight and {@code minGate ≤ gatesPassed}. With none eligible
 * nothing is drawn at all — a world with no patterns (Green Fields) never touches the stream, so
 * its runs hash exactly what they hashed before patterns existed. Otherwise one
 * {@code nextDouble()} decides whether a pattern starts, with probability
 * {@code Σ eligible weights / (Σ eligible weights + PLAIN_SHARE)} — the plain spawn table holds a
 * fixed share of {@value #PLAIN_SHARE}, so a world whose patterns weigh 40 in total starts one at
 * about 29 % of its free spawns — and, when it does, one {@code nextInt(Σ weights)} picks the
 * pattern, weighted. A chunk that just ended is followed by at least one plain spawn before the
 * next draw, so two set pieces never run into each other unreadably.
 *
 * <p><b>Forced pattern.</b> A run with a forced pattern (a challenge's {@code forcedPattern}, or
 * the balancing tool's {@code --pattern}) streams that pattern and nothing else, looping it
 * forever, from the very first spawn; the spawn table is never consulted and the stream is never
 * drawn.
 *
 * <p><b>Scoring.</b> A step scores when its own {@code scoring} flag and the pattern's
 * {@code scoringSteps} both allow it ({@link PatternSpec#stepScores}); the spawner turns scoring
 * off on the column otherwise, so it awards no gate and gets no coin trail.
 *
 * <p>The streamer decides only <em>which</em> step spawns; the geometry goes through
 * {@link SpawnTable#decisionFor} and the {@code obstacle} stream like every other spawn, so the
 * decision hash of E32.d folds every pattern step the same way it folds a table draw.
 */
public final class PatternStreamer {

    /** The plain spawn table's fixed weight in the start-a-pattern draw. */
    public static final int PLAIN_SHARE = 100;

    private static final long HASH_SEED = MathUtil.fnv1a64("pattern-streamer");

    /**
     * One step the spawner is about to place.
     *
     * @param pattern the pattern the step belongs to
     * @param index the step's position in the pattern
     * @param step the step
     * @param scores whether the column awards a gate
     */
    public record Placement(PatternSpec pattern, int index, PatternSpec.Step step,
            boolean scores) {
    }

    private final List<PatternSpec> patterns;
    private final PatternSpec forced;
    private final Random rng;
    private final int totalWeight;
    private PatternSpec active;
    private int stepIndex;
    private boolean cooldown;
    private int patternsStarted;
    private int stepsStreamed;

    /**
     * Creates a streamer.
     *
     * @param worldPatterns the world's patterns; entries with weight 0 are kept but never drawn
     * @param forced the pattern to loop instead of the table, or {@code null}
     * @param patterns the run's {@code patterns} stream
     */
    public PatternStreamer(List<PatternSpec> worldPatterns, PatternSpec forced, Random patterns) {
        this.patterns = List.copyOf(worldPatterns);
        this.forced = forced;
        this.rng = Objects.requireNonNull(patterns, "patterns");
        int total = 0;
        for (PatternSpec p : this.patterns) {
            total += p.weight();
        }
        this.totalWeight = total;
    }

    /**
     * Whether the streamer can ever place a step: a forced pattern, or at least one world pattern
     * with a positive weight.
     *
     * @return {@code true} when it has work
     */
    public boolean hasWork() {
        return forced != null || totalWeight > 0;
    }

    /**
     * Decides the step of the spawn about to happen.
     *
     * @param first whether the layer is empty (the opening spawn of the run)
     * @param gatesPassed gates passed so far, for {@code minGate}
     * @return the placement, or {@code null} for a plain table spawn
     */
    public Placement next(boolean first, int gatesPassed) {
        if (forced != null) {
            return placeForced();
        }
        if (active != null) {
            return placeActive();
        }
        if (first) {
            // Upstream's opening gate: always plain, and no draw (D7).
            return null;
        }
        if (cooldown) {
            cooldown = false;
            return null;
        }
        int eligibleWeight = 0;
        List<PatternSpec> eligible = null;
        for (PatternSpec p : patterns) {
            if (p.weight() > 0 && p.minGate() <= gatesPassed) {
                if (eligible == null) {
                    eligible = new ArrayList<>(patterns.size());
                }
                eligible.add(p);
                eligibleWeight += p.weight();
            }
        }
        if (eligible == null) {
            return null;
        }
        double start = rng.nextDouble();
        if (start >= eligibleWeight / (double) (eligibleWeight + PLAIN_SHARE)) {
            return null;
        }
        int roll = rng.nextInt(eligibleWeight);
        PatternSpec chosen = eligible.get(eligible.size() - 1);
        for (PatternSpec p : eligible) {
            roll -= p.weight();
            if (roll < 0) {
                chosen = p;
                break;
            }
        }
        active = chosen;
        stepIndex = 0;
        patternsStarted++;
        return placeActive();
    }

    private Placement placeForced() {
        if (stepIndex >= forced.steps().size()) {
            stepIndex = 0;
        }
        if (stepIndex == 0) {
            patternsStarted++;
        }
        int index = stepIndex++;
        stepsStreamed++;
        return new Placement(forced, index, forced.steps().get(index), forced.stepScores(index));
    }

    private Placement placeActive() {
        PatternSpec pattern = active;
        int index = stepIndex++;
        stepsStreamed++;
        if (stepIndex >= pattern.steps().size()) {
            active = null;
            stepIndex = 0;
            cooldown = true;
        }
        return new Placement(pattern, index, pattern.steps().get(index),
                pattern.stepScores(index));
    }

    /**
     * The chunk being streamed.
     *
     * @return the pattern, or {@code null} between chunks (the forced pattern is always active)
     */
    public PatternSpec active() {
        return forced != null ? forced : active;
    }

    /**
     * The index of the next step of the active chunk.
     *
     * @return the index, 0 when no chunk is active
     */
    public int stepIndex() {
        return stepIndex;
    }

    /**
     * Whether the run loops one pattern instead of spawning from its table.
     *
     * @return {@code true} for a forced pattern
     */
    public boolean isForced() {
        return forced != null;
    }

    /**
     * The forced pattern.
     *
     * @return the pattern, or {@code null}
     */
    public PatternSpec forced() {
        return forced;
    }

    /**
     * The world's patterns, as given.
     *
     * @return the patterns
     */
    public List<PatternSpec> patterns() {
        return patterns;
    }

    /**
     * How many chunks started so far (a looped forced pattern counts every loop).
     *
     * @return the count
     */
    public int patternsStarted() {
        return patternsStarted;
    }

    /**
     * How many steps were placed so far.
     *
     * @return the count
     */
    public int stepsStreamed() {
        return stepsStreamed;
    }

    /**
     * Folds the streaming state into a hash (D12): which chunk is active, where it is and how
     * many were streamed.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, HASH_SEED);
        h = MathUtil.fold(h, active == null ? -1 : MathUtil.fnv1a64(active.id()));
        h = MathUtil.fold(h, stepIndex);
        h = MathUtil.fold(h, cooldown ? 1 : 0);
        h = MathUtil.fold(h, patternsStarted);
        return MathUtil.fold(h, stepsStreamed);
    }
}
