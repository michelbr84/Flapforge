package io.github.michelbr84.flapforge.gameplay.obstacle;

/**
 * Something an obstacle wants the simulation to announce (M7). An obstacle raises at most one
 * signal per tick through {@link Obstacle#takeSignal()}; the simulation drains it into the
 * matching {@code TickFact}, so the obstacle package never names a fact type.
 */
public enum ObstacleSignal {
    /** A piston entered its telegraph phase (once per cycle). */
    PISTON_TELEGRAPH,
    /** A lightning column started its warning (once per bolt). */
    LIGHTNING_WARNING
}
