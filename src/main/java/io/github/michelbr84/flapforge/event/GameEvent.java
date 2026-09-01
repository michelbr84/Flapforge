package io.github.michelbr84.flapforge.event;

import io.github.michelbr84.flapforge.persistence.Settings;
import java.util.List;
import java.util.Objects;

/**
 * Every notification the presentation layer reacts to (D16), as records nested in one sealed
 * interface. Audio, particles, toasts and the debug overlay subscribe; the simulation and the
 * progression pipeline never publish and never import this package (E31.b) — {@code GameScreen}
 * converts a {@code TickReport} or a {@code ProgressionOutcome} into the events below.
 *
 * <p>The full list is defined here from milestone M2 even though M2 only has producers for a
 * handful of them, so that later milestones add subscribers instead of reopening this file.
 */
public sealed interface GameEvent {

    /**
     * A run started.
     *
     * @param birdId the bird flown
     * @param worldId the world
     * @param tierId the difficulty tier
     * @param seed the run seed
     */
    record RunStarted(String birdId, String worldId, String tierId, long seed)
            implements GameEvent {
    }

    /**
     * A scoring gate was cleared.
     *
     * @param gatesPassed how many gates the run has cleared, including this one
     * @param clean whether the gate was cleared without losing the streak
     */
    record GatePassed(int gatesPassed, boolean clean) implements GameEvent {
    }

    /**
     * A coin was picked up.
     *
     * @param amount the coins this pickup was worth
     * @param collectedThisRun the run total after the pickup
     */
    record CoinCollected(int amount, int collectedThisRun) implements GameEvent {
    }

    /**
     * The bird flapped.
     *
     * @param automatic whether hold-to-flap issued it rather than a key press (D2)
     */
    record Flapped(boolean automatic) implements GameEvent {
    }

    /**
     * An ability was activated.
     *
     * @param abilityId the ability
     * @param level the owned level
     */
    record AbilityActivated(String abilityId, int level) implements GameEvent {
    }

    /**
     * An ability finished its cooldown.
     *
     * @param abilityId the ability
     */
    record AbilityReady(String abilityId) implements GameEvent {
    }

    /**
     * A shield charge absorbed a lethal hit.
     *
     * @param chargesLeft charges remaining afterwards
     */
    record ShieldAbsorbed(int chargesLeft) implements GameEvent {
    }

    /**
     * The bird was revived after a lethal hit.
     *
     * @param revivesLeft revives remaining afterwards
     */
    record Revived(int revivesLeft) implements GameEvent {
    }

    /**
     * The bird passed close enough to an obstacle to count as a near miss.
     *
     * @param gatesPassed the gate count when it happened
     */
    record NearMiss(int gatesPassed) implements GameEvent {
    }

    /**
     * The clean-gate streak changed.
     *
     * @param streak the new streak length
     * @param step the reward step the streak has reached
     */
    record StreakChanged(int streak, int step) implements GameEvent {
    }

    /**
     * A build synergy became active (E16).
     *
     * @param synergyId the synergy
     */
    record SynergyActivated(String synergyId) implements GameEvent {
    }

    /**
     * The bird died.
     *
     * @param cause the collision cause, as its enum name
     * @param gatesPassed the gate count reached
     */
    record Crashed(String cause, int gatesPassed) implements GameEvent {
    }

    /**
     * A run ended and its result is final.
     *
     * @param gatesPassed gates cleared
     * @param points points scored
     * @param ticksAlive how long the bird survived, in ticks
     * @param objectiveMet whether a challenge objective was met
     */
    record RunEnded(int gatesPassed, int points, long ticksAlive, boolean objectiveMet)
            implements GameEvent {
    }

    /**
     * A modifier draft opened.
     *
     * @param modifierIds the offered cards, in display order
     * @param gate the gate the offer belongs to
     */
    record ModifierOffered(List<String> modifierIds, int gate) implements GameEvent {

        /**
         * Copies the list so the event stays immutable.
         *
         * @param modifierIds the offered cards
         * @param gate the gate the offer belongs to
         */
        public ModifierOffered {
            modifierIds = List.copyOf(Objects.requireNonNull(modifierIds, "modifierIds"));
        }
    }

    /**
     * A modifier card was taken.
     *
     * @param modifierId the chosen card
     * @param gate the gate the offer belonged to
     */
    record ModifierChosen(String modifierId, int gate) implements GameEvent {
    }

    /**
     * A boss is approaching.
     *
     * @param bossId the boss
     * @param gatesAway how many gates away it is
     */
    record BossWarning(String bossId, int gatesAway) implements GameEvent {
    }

    /**
     * A boss encounter started.
     *
     * @param bossId the boss
     */
    record BossStarted(String bossId) implements GameEvent {
    }

    /**
     * A boss encounter was survived.
     *
     * @param bossId the boss
     * @param worldId the world the boss belongs to, or {@code null} for a challenge boss (E26)
     */
    record BossCleared(String bossId, String worldId) implements GameEvent {
    }

    /**
     * A Void rule cycle changed the active rules.
     *
     * @param flags the rule flags now in force, as enum names
     */
    record RuleShift(List<String> flags) implements GameEvent {

        /**
         * Copies the list so the event stays immutable.
         *
         * @param flags the rule flags now in force
         */
        public RuleShift {
            flags = List.copyOf(Objects.requireNonNull(flags, "flags"));
        }
    }

    /**
     * A challenge objective was met during a run.
     *
     * @param challengeId the challenge
     */
    record ObjectiveMet(String challengeId) implements GameEvent {
    }

    /**
     * The wallet changed.
     *
     * @param currency the currency id
     * @param delta how much was added (negative when spent)
     * @param total the balance afterwards
     */
    record CurrencyChanged(String currency, long delta, long total) implements GameEvent {
    }

    /**
     * Experience was awarded.
     *
     * @param amount the experience gained
     * @param total the lifetime total afterwards
     */
    record XpGained(long amount, long total) implements GameEvent {
    }

    /**
     * The player level went up.
     *
     * @param level the new level
     */
    record LevelUp(int level) implements GameEvent {
    }

    /**
     * An achievement was unlocked.
     *
     * @param achievementId the achievement
     */
    record AchievementUnlocked(String achievementId) implements GameEvent {
    }

    /**
     * Something was unlocked.
     *
     * @param unlockId the namespaced id, for example {@code bird:ironbeak}
     */
    record UnlockGranted(String unlockId) implements GameEvent {
    }

    /**
     * A challenge was completed.
     *
     * @param challengeId the challenge
     * @param firstCompletion whether this was the first time (E11)
     */
    record ChallengeCompleted(String challengeId, boolean firstCompletion) implements GameEvent {
    }

    /**
     * A daily attempt was recorded (E27).
     *
     * @param date the daily date, {@code yyyy-MM-dd}
     * @param gatesPassed gates cleared in the attempt
     */
    record DailyRecorded(String date, int gatesPassed) implements GameEvent {
    }

    /**
     * The settings changed and were stored.
     *
     * @param settings the state now in force
     */
    record SettingsChanged(Settings settings) implements GameEvent {
    }

    /**
     * The active language changed and the string table was swapped (D25).
     *
     * @param language the resolved language, never {@code auto}
     */
    record LanguageChanged(String language) implements GameEvent {
    }

    /**
     * The screen stack changed.
     *
     * @param screen simple name of the screen now on top
     */
    record ScreenChanged(String screen) implements GameEvent {
    }

    /**
     * A write to disk failed (D15).
     *
     * @param file the file that could not be written
     * @param detail an English explanation
     */
    record SaveFailed(String file, String detail) implements GameEvent {
    }
}
