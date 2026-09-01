package io.github.michelbr84.flapforge.gameplay;

import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;

/**
 * Immutable facts a tick produced (D5). The whole family is declared here so later milestones
 * only add producers; the presentation layer maps facts to events, gameplay never imports the
 * event bus.
 */
public sealed interface TickFact {

    /**
     * The bird cleared a scoring column.
     *
     * @param clean {@code true} when no near miss, shield absorb or revive touched the gate (D26)
     */
    record GatePassed(boolean clean) implements TickFact {
    }

    /** A flap was accepted. */
    record Flapped() implements TickFact {
    }

    /** A flap was refused by the ceiling gate. */
    record FlapRefused() implements TickFact {
    }

    /**
     * The bird took a lethal hit.
     *
     * @param cause what was hit
     */
    record Crashed(CollisionCause cause) implements TickFact {
    }

    /**
     * Points were awarded.
     *
     * @param points the points awarded this tick ({@code 1 × SCORE_MULT} per gate)
     */
    record Scored(double points) implements TickFact {
    }

    /**
     * The run changed phase.
     *
     * @param from the previous phase
     * @param to the new phase
     */
    record PhaseChanged(RunPhase from, RunPhase to) implements TickFact {
    }

    /**
     * An obstacle entered the world.
     *
     * @param kind the obstacle family
     */
    record ObstacleSpawned(ObstacleKind kind) implements TickFact {
    }

    /** The bird grazed a lethal hitbox without dying (first tick of contact per obstacle). */
    record NearMiss() implements TickFact {
    }

    /** A shield charge absorbed a lethal hit. */
    record ShieldAbsorbed() implements TickFact {
    }

    /** A revive charge saved the bird. */
    record Revived() implements TickFact {
    }

    /**
     * The clean-gate streak changed.
     *
     * @param streak the new streak length
     */
    record StreakChanged(int streak) implements TickFact {
    }

    /**
     * A coin was picked up.
     *
     * @param value the coin value
     */
    record CoinCollected(int value) implements TickFact {
    }

    /** A modifier offer opened (the simulation is frozen until a choice is made). */
    record OfferOpened() implements TickFact {
    }

    /** A boss warning started. */
    record BossWarning() implements TickFact {
    }

    /** A boss encounter started. */
    record BossStarted() implements TickFact {
    }

    /** A boss encounter was survived. */
    record BossCleared() implements TickFact {
    }

    /** A world rule cycle shifted the active rules. */
    record RuleShift() implements TickFact {
    }

    /**
     * A modifier synergy activated.
     *
     * @param id the synergy id
     */
    record SynergyActivated(String id) implements TickFact {
    }

    /** A lightning strike started its warning. */
    record LightningWarning() implements TickFact {
    }

    /** A piston started its telegraph. */
    record PistonTelegraph() implements TickFact {
    }
}
