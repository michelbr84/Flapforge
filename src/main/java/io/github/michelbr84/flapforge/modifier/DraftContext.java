package io.github.michelbr84.flapforge.modifier;

import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import java.util.Objects;

/**
 * What {@link ModifierPool} has to know about the run it is drawing cards for, to answer E12's
 * derived half of eligibility: a card whose whole effect list is a no-op in <em>this</em> run is
 * not worth a slot on the table.
 *
 * <p>It is read live, never snapshotted. {@code RuleSet} is immutable and the simulation replaces
 * the field when a card or a synergy turns a flag on, so a pool holding a copy would answer for
 * the run as it started rather than for the run as it is.
 *
 * <p>Two things decide inertness today. The rules are the half the errata names: a flag that
 * zeroes the stat a card touches ({@code NO_REVIVE}, {@code NO_DEFENSIVE_ABILITIES}, D8), or
 * {@code NO_COINS} against the coin stats. The loadout is the other half: {@code
 * ABILITY_COOLDOWN_MULT} and {@code ABILITY_DURATION_MULT} are read by {@code AbilityInstance}
 * and by nothing else, so with no equipped ability declaring a cooldown — the default loadout is
 * {@code double_flap}, whose cooldown and duration are zero at every level — a card that scales
 * them is exactly as blank as {@code temp_shield} under {@code NO_DEFENSIVE_ABILITIES}.
 */
public interface DraftContext {

    /**
     * The rules in force right now.
     *
     * @return the active rules
     */
    RuleSet rules();

    /**
     * Whether any equipped ability declares a cooldown, which is what
     * {@code ABILITY_COOLDOWN_MULT} scales.
     *
     * @return {@code true} when the stat can change something
     */
    default boolean abilityCooldownMatters() {
        return true;
    }

    /**
     * Whether any equipped ability declares a duration, which is what
     * {@code ABILITY_DURATION_MULT} scales.
     *
     * @return {@code true} when the stat can change something
     */
    default boolean abilityDurationMatters() {
        return true;
    }

    /**
     * A context that knows the rules and assumes the loadout can use every stat — the shape the
     * rule-level tests and the static helpers work in.
     *
     * @param rules the active rules
     * @return the context
     */
    static DraftContext of(RuleSet rules) {
        Objects.requireNonNull(rules, "rules");
        return () -> rules;
    }
}
