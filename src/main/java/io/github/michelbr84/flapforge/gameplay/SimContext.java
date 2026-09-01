package io.github.michelbr84.flapforge.gameplay;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;

/**
 * Read-only view of the simulation handed to obstacles and systems during a tick (D6).
 *
 * <p>{@code worldDt} is the {@code TIME_SCALE} stat: it scales the world clock (scroll, obstacle
 * phases, pickups, streaming) but never the tick length or the bird integration, so the flap apex
 * stays 42 px under Slow Time (D8).
 *
 * @param tick the simulation tick being processed (1 for the first tick)
 * @param worldDt the world clock scale for this tick
 * @param stats the resolved stats
 * @param rules the active rules
 * @param rng the run's random streams (obstacles must hold their own stream, not re-derive one
 *     per tick)
 * @param bird the bird
 */
public record SimContext(int tick, double worldDt, StatSheet stats, RuleSet rules,
        RandomProvider rng, Bird bird) {

    /**
     * Converts a per-second world rate into this tick's displacement, honouring the world clock
     * scale. Uses a division by {@link Playfield#TICK_RATE} so exact rates (120 px/s = 2 px/tick)
     * produce exact steps.
     *
     * @param perSecond a rate in units per second
     * @return the displacement for this tick
     */
    public double perTick(double perSecond) {
        return perSecond * worldDt / Playfield.TICK_RATE;
    }

    /**
     * Horizontal scroll of the world this tick, in px.
     *
     * @return {@code SCROLL_SPEED × worldDt / TICK_RATE}
     */
    public double scrollPerTick() {
        return perTick(stats.resolve(StatId.SCROLL_SPEED));
    }

    /**
     * Distance a moving obstacle travels this tick, in px.
     *
     * @return {@code OSCILLATION_SPEED × worldDt / TICK_RATE}
     */
    public double oscillationPerTick() {
        return perTick(stats.resolve(StatId.OSCILLATION_SPEED));
    }
}
