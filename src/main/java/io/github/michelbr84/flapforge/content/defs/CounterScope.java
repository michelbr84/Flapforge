package io.github.michelbr84.flapforge.content.defs;

/**
 * Where an achievement counter is read from (§4, E5).
 */
public enum CounterScope {

    /** A {@code StatisticKey} field, a map entry of one, or a profile-root scalar (E5). */
    LIFETIME,
    /** A value of the run that just finished, addressed as {@code run.<name>}. */
    RUN,
    /** Collection progress, addressed as {@code collection.<category>.percent} (D13). */
    COLLECTION
}
