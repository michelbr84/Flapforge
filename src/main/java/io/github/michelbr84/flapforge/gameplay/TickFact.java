package io.github.michelbr84.flapforge.gameplay;

import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.List;

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
     * @param kind the family of the obstacle that was hit, or {@code null} for the ground and
     *     the ceiling (M7: the balancing report groups deaths by kind)
     */
    record Crashed(CollisionCause cause, ObstacleKind kind) implements TickFact {

        /**
         * A hit with no obstacle behind it (the ground, the ceiling).
         *
         * @param cause what was hit
         */
        public Crashed(CollisionCause cause) {
            this(cause, null);
        }
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

    /**
     * The player activated the equipped active ability (D9); {@code Run} counts it in
     * {@code RunStats.abilitiesUsed}.
     *
     * @param abilityId the ability that was activated
     */
    record AbilityActivated(String abilityId) implements TickFact {
    }

    /**
     * An ability became usable again — a cooldown elapsed or a charge came back (D9, D17: the
     * HUD ring closes and the audio cue plays on this fact).
     *
     * @param abilityId the ability that is ready
     */
    record AbilityReady(String abilityId) implements TickFact {
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

    /**
     * A modifier draft opened; the simulation is frozen until a choice is made (D11, M6).
     *
     * <p>{@code gate} is the gate the run had actually passed when the air cleared, which is a
     * few gates past the schedule entry — the spawner has obstacles queued when the breather
     * starts. What the player is shown is the scheduled gate ({@code ModifierOffer.gate()}); this
     * one is the telemetry number.
     *
     * @param offerIndex the position of the draft in {@code offerSchedule}, counting from 0
     * @param gate the gate count the draft opened at
     * @param cardIds the cards on the table, in draw order
     */
    record ModifierOffered(int offerIndex, int gate, List<String> cardIds)
            implements TickFact {

        /**
         * Copies the card list, so a fact is a value.
         *
         * @param offerIndex the position in the schedule
         * @param gate the gate the draft opened at
         * @param cardIds the cards on the table
         */
        public ModifierOffered {
            cardIds = List.copyOf(cardIds);
        }
    }

    /**
     * A card was taken (M6). Forced modifiers are taken before the first tick and produce no
     * fact; {@code RunStats} reads them from the director instead.
     *
     * @param modifierId the card
     * @param stacks how many stacks of it the run now holds
     */
    record ModifierChosen(String modifierId, int stacks) implements TickFact {
    }

    /**
     * A draft was closed without taking anything: the player skipped it, or nothing on the table
     * was eligible (E12, M6).
     *
     * @param offerIndex the position of the draft in {@code offerSchedule}
     */
    record ModifierSkipped(int offerIndex) implements TickFact {
    }

    /**
     * A boss warning started (D11, M8): {@code boss.atGate} was reached, spawning is suppressed
     * for {@code warningTicks} and the run is in {@code BOSS_WARNING}. The presentation renders
     * the banner and the HUD countdown from this fact.
     *
     * @param bossId the owner's id — the world of a world boss, the challenge of a challenge boss
     * @param worldId the world the encounter clears, or {@code null} for a challenge boss (E26)
     * @param warningTicks flying ticks until the fight starts
     */
    record BossWarning(String bossId, String worldId, int warningTicks) implements TickFact {
    }

    /**
     * A boss encounter started (D11, M8): the phases are streaming and the run is in
     * {@code BOSS} for {@code surviveTicks} flying ticks.
     *
     * @param bossId the owner's id
     * @param surviveTicks flying ticks the fight lasts
     */
    record BossStarted(String bossId, int surviveTicks) implements TickFact {
    }

    /**
     * A boss encounter was survived (D11, M8). {@code Run} records a world boss in
     * {@code RunStats.bossesCleared} from this fact; a challenge boss ({@code worldId == null})
     * only satisfies a {@code BOSS_CLEARED} objective (E26).
     *
     * @param bossId the owner's id
     * @param worldId the world cleared, or {@code null} for a challenge boss
     */
    record BossCleared(String bossId, String worldId) implements TickFact {
    }

    /**
     * The challenge objective was met (D11, M8). Emitted once per run; the run continues, and
     * {@code RunStats.objectiveMet} stays set whatever happens afterwards.
     *
     * @param challengeId the challenge
     */
    record ObjectiveMet(String challengeId) implements TickFact {
    }

    /**
     * A world rule cycle picked its next option and started the telegraph (M7, the Void). The
     * option lands {@code telegraphTicks} flying ticks later — never inside a draft; a freeze or
     * a breather defers it to the next {@code FLYING} tick — when its flags join the run's rules
     * and its effects replace the previous option's in the {@code WORLD_CYCLE} layer. The
     * presentation renders the banner and its countdown from this fact.
     *
     * @param flags the rule flags the option turns on, in declaration order
     * @param effects the stat modifiers the option applies
     * @param telegraphTicks flying ticks between this fact and the shift landing
     */
    record RuleShift(List<RuleFlag> flags, List<StatModifier> effects, int telegraphTicks)
            implements TickFact {

        /**
         * Copies the lists, so a fact is a value.
         *
         * @param flags the flags
         * @param effects the effects
         * @param telegraphTicks the countdown start
         */
        public RuleShift {
            flags = List.copyOf(flags);
            effects = List.copyOf(effects);
        }
    }

    /**
     * The sky flashed (M7, E8): Storm Sky's {@code ambient.lightningEveryGates} is cosmetic — a
     * flash and a thunder cue, honouring {@code reduceFlashing}, with no hitbox behind it. Lethal
     * bolts announce themselves with {@link LightningWarning} instead.
     */
    record AmbientFlash() implements TickFact {
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
