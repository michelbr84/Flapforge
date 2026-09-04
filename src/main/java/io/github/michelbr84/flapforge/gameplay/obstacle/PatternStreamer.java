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
 * <p><b>Boss phases (M8).</b> Between {@link #startBoss()} and {@link #endBoss()} the streamer
 * plays the boss phases it was built with, in order and looped, and nothing else: a forced
 * pattern and the world's own patterns are both set aside for the fight and come back afterwards
 * (the forced loop restarts from its first step; a world pattern is preceded by one plain spawn
 * like any chunk that just ended). The phases draw nothing from the stream — they are authored
 * like a forced pattern — so a boss run's spawn decisions still depend on the seed alone
 * (E32.d). {@link #bossPhasesStarted()} counts every phase start, loops included, which is what
 * {@code RunStats.phasesReached} is derived from.
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
    private static final long BOSS_HASH_SEED = MathUtil.fnv1a64("boss-phases");

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
    private final List<PatternSpec> bossPhases;
    private final Random rng;
    private final int totalWeight;
    private PatternSpec active;
    private int stepIndex;
    private boolean cooldown;
    private int patternsStarted;
    private int stepsStreamed;
    private boolean bossActive;
    private int bossPhase;
    private int bossStep;
    private int bossPhasesStarted;

    /**
     * Creates a streamer with no boss phases.
     *
     * @param worldPatterns the world's patterns; entries with weight 0 are kept but never drawn
     * @param forced the pattern to loop instead of the table, or {@code null}
     * @param patterns the run's {@code patterns} stream
     */
    public PatternStreamer(List<PatternSpec> worldPatterns, PatternSpec forced, Random patterns) {
        this(worldPatterns, forced, List.of(), patterns);
    }

    /**
     * Creates a streamer (M8).
     *
     * @param worldPatterns the world's patterns; entries with weight 0 are kept but never drawn
     * @param forced the pattern to loop instead of the table, or {@code null}
     * @param bossPhases the boss phases {@link #startBoss()} plays, in order; empty for a run
     *     without a boss
     * @param patterns the run's {@code patterns} stream
     */
    public PatternStreamer(List<PatternSpec> worldPatterns, PatternSpec forced,
            List<PatternSpec> bossPhases, Random patterns) {
        this.patterns = List.copyOf(worldPatterns);
        this.forced = forced;
        this.bossPhases = List.copyOf(bossPhases);
        this.rng = Objects.requireNonNull(patterns, "patterns");
        int total = 0;
        for (PatternSpec p : this.patterns) {
            total += p.weight();
        }
        this.totalWeight = total;
    }

    /**
     * Whether the streamer can ever place a step: a forced pattern, boss phases, or at least one
     * world pattern with a positive weight.
     *
     * @return {@code true} when it has work
     */
    public boolean hasWork() {
        return forced != null || totalWeight > 0 || !bossPhases.isEmpty();
    }

    /**
     * Decides the step of the spawn about to happen.
     *
     * @param first whether the layer is empty (the opening spawn of the run)
     * @param gatesPassed gates passed so far, for {@code minGate}
     * @return the placement, or {@code null} for a plain table spawn
     */
    public Placement next(boolean first, int gatesPassed) {
        if (bossActive) {
            return placeBoss();
        }
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
     * The next boss step: the phases in order, each from its first step to its last, looping
     * back to the first phase after the last one (D11).
     */
    private Placement placeBoss() {
        PatternSpec phase = bossPhases.get(bossPhase);
        if (bossStep == 0) {
            bossPhasesStarted++;
            patternsStarted++;
        }
        int index = bossStep++;
        stepsStreamed++;
        Placement placement = new Placement(phase, index, phase.steps().get(index),
                phase.stepScores(index));
        if (bossStep >= phase.steps().size()) {
            bossStep = 0;
            bossPhase = (bossPhase + 1) % bossPhases.size();
        }
        return placement;
    }

    /**
     * Starts streaming the boss phases (M8): the next spawn is the first step of the first phase.
     * Whatever chunk was active is dropped — the warning suppressed spawning anyway — and a
     * forced pattern waits until {@link #endBoss()}.
     *
     * @throws IllegalStateException when the streamer was built without boss phases
     */
    public void startBoss() {
        if (bossPhases.isEmpty()) {
            throw new IllegalStateException("this streamer has no boss phases");
        }
        bossActive = true;
        bossPhase = 0;
        bossStep = 0;
        active = null;
        stepIndex = 0;
    }

    /**
     * Stops streaming the boss phases (M8): no further boss step is placed, the columns already
     * in the world scroll out on their own, and the run goes back to its forced pattern (from its
     * first step) or to the world's table with the usual one plain spawn before the next chunk.
     */
    public void endBoss() {
        bossActive = false;
        bossStep = 0;
        stepIndex = 0;
        cooldown = true;
    }

    /**
     * Whether the boss phases are streaming.
     *
     * @return {@code true} between {@link #startBoss()} and {@link #endBoss()}
     */
    public boolean isBossActive() {
        return bossActive;
    }

    /**
     * The boss phases this streamer was built with.
     *
     * @return the phases, empty for a run without a boss
     */
    public List<PatternSpec> bossPhases() {
        return bossPhases;
    }

    /**
     * The index of the phase the next boss step comes from.
     *
     * @return the 0-based phase index, 0 outside a fight
     */
    public int bossPhase() {
        return bossPhase;
    }

    /**
     * How many boss phases started so far, loops included: phase {@code k} (1-based) of the
     * authored list has been reached exactly when this is at least {@code k}.
     *
     * @return the count
     */
    public int bossPhasesStarted() {
        return bossPhasesStarted;
    }

    /**
     * The chunk being streamed.
     *
     * @return the pattern, or {@code null} between chunks (the forced pattern is always active
     *     outside a fight; a boss phase is the answer during one)
     */
    public PatternSpec active() {
        if (bossActive) {
            return bossPhases.get(bossPhase);
        }
        return forced != null ? forced : active;
    }

    /**
     * The index of the next step of the active chunk.
     *
     * @return the index, 0 when no chunk is active
     */
    public int stepIndex() {
        return bossActive ? bossStep : stepIndex;
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
     * How many chunks started so far (a looped forced pattern counts every loop, and so does
     * every boss phase).
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
     * many were streamed — and, for a streamer with boss phases, where the fight is.
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
        h = MathUtil.fold(h, stepsStreamed);
        if (!bossPhases.isEmpty()) {
            h = MathUtil.fold(h, BOSS_HASH_SEED);
            h = MathUtil.fold(h, bossActive ? 1 : 0);
            h = MathUtil.fold(h, bossPhase);
            h = MathUtil.fold(h, bossStep);
            h = MathUtil.fold(h, bossPhasesStarted);
        }
        return h;
    }
}
