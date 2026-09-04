package io.github.michelbr84.flapforge.content.defs;

/**
 * What a challenge asks for (§4). Evaluated by {@code ObjectiveEvaluator} from M8.
 */
public enum ObjectiveType {

    /** Pass at least {@code value} gates in one run. */
    SURVIVE_GATES,
    /** Stay alive for at least {@code value} ticks in one run (D11). */
    SURVIVE_TICKS,
    /** Pick up at least {@code value} coins in one run (E2). */
    COLLECT_COINS,
    /** Score at least {@code value} points in one run (E1). */
    REACH_POINTS,
    /** Clear the challenge's own boss (E26: it does not clear the world). */
    BOSS_CLEARED
}
