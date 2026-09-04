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
 * <p>{@code windScroll} is the horizontal wind sampled for this tick (D6, M7): the bird's x is
 * fixed, so a wind zone's {@code scrollDelta} shows up as a change of the relative scroll speed
 * of the whole world. It is {@code 0} outside every zone, which leaves {@link #scrollPerTick()}
 * bit-identical to the wind-free value.
 *
 * @param tick the simulation tick being processed (1 for the first tick)
 * @param worldDt the world clock scale for this tick
 * @param stats the resolved stats
 * @param rules the active rules
 * @param rng the run's random streams (obstacles must hold their own stream, not re-derive one
 *     per tick)
 * @param bird the bird
 * @param windScroll the scroll speed change of this tick's wind, in px/s
 */
public record SimContext(int tick, double worldDt, StatSheet stats, RuleSet rules,
        RandomProvider rng, Bird bird, double windScroll) {

    /**
     * A context with no wind.
     *
     * @param tick the simulation tick being processed
     * @param worldDt the world clock scale for this tick
     * @param stats the resolved stats
     * @param rules the active rules
     * @param rng the run's random streams
     * @param bird the bird
     */
    public SimContext(int tick, double worldDt, StatSheet stats, RuleSet rules,
            RandomProvider rng, Bird bird) {
        this(tick, worldDt, stats, rules, rng, bird, 0);
    }

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
     * Horizontal scroll of the world this tick, in px: the scroll speed plus the wind, never
     * negative.
     *
     * @return {@code max(0, SCROLL_SPEED + windScroll) × worldDt / TICK_RATE}
     */
    public double scrollPerTick() {
        return perTick(Math.max(0, stats.resolve(StatId.SCROLL_SPEED) + windScroll));
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
