package io.github.michelbr84.flapforge.gameplay.run;

/** How a run was started (D11). */
public enum RunMode {
    /** Free play with a fresh random seed. */
    STANDARD,
    /** Free play with a player-chosen seed. */
    SEEDED,
    /** The daily challenge configuration. */
    DAILY,
    /** A challenge from {@code challenges.json}. */
    CHALLENGE
}
