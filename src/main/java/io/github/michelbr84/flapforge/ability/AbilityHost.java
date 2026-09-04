package io.github.michelbr84.flapforge.ability;

import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.run.ReviveSystem;
import io.github.michelbr84.flapforge.gameplay.run.ShieldSystem;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;

/**
 * What an {@link AbilityManager} is allowed to see and touch inside the run it belongs to (D9).
 *
 * <p>The simulation implements it. The seam exists so behaviours can read the bird, the resolved
 * stats and the run counters, and can ask for the two things only the collision owner can grant —
 * invulnerability ticks and the "ghost until the hitboxes no longer overlap" state — without the
 * {@code ability} package having to know how a tick is ordered.
 *
 * <p>Everything here is either a read or an idempotent request. A behaviour never removes an
 * obstacle, never scores, never draws from a random stream and never writes the run stats: those
 * belong to the simulation and to {@code Run}, which folds the emitted facts into
 * {@code RunStats}.
 */
public interface AbilityHost {

    /**
     * The bird of the run.
     *
     * @return the bird
     */
    Bird bird();

    /**
     * The resolved stats.
     *
     * @return the sheet
     */
    StatSheet stats();

    /**
     * The active rules.
     *
     * @return the rules
     */
    RuleSet rules();

    /**
     * The shield charges of the run (D9: stat-driven, an ability only configures it).
     *
     * @return the system
     */
    ShieldSystem shield();

    /**
     * The revives of the run (D9: stat-driven, an ability only configures it).
     *
     * @return the system
     */
    ReviveSystem revive();

    /**
     * Gates passed so far.
     *
     * @return the count
     */
    int gatesPassed();

    /**
     * Points scored so far.
     *
     * @return the points
     */
    double points();

    /**
     * Total value of the coins picked up so far.
     *
     * @return the value
     */
    int coinsCollected();

    /**
     * The simulation tick being processed.
     *
     * @return the tick
     */
    int tick();

    /**
     * Grants invulnerability ticks; the longest grant wins, a shorter one is ignored.
     *
     * @param ticks how many ticks the bird ignores lethal hits
     */
    void grantIFrames(int ticks);

    /**
     * Makes the bird non-lethal-collidable until no lethal hitbox overlaps it any more (the
     * "ghost until clear" of the shield absorb, D9). Independent of the invulnerability ticks:
     * whichever lasts longer keeps the bird alive.
     *
     * <p>It covers <em>one</em> hazard: the first obstacle the bird meets while ghosting, and
     * only that one. A different obstacle hitting the bird drops the ghost, so a single grant can
     * never become open-ended immunity inside a dense pattern.
     */
    void ghostUntilClear();

    /**
     * Whether a lethal hit would currently be ignored (invulnerability ticks or ghost).
     *
     * @return {@code true} when the bird cannot be killed by an obstacle this tick
     */
    boolean isInvulnerable();

    /**
     * Remaining invulnerability ticks.
     *
     * @return the count, 0 when none
     */
    int invulnerableTicks();
}
