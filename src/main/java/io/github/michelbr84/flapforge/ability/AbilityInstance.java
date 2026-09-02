package io.github.michelbr84.flapforge.ability;

import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.AbilityLevelDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.Objects;

/**
 * One equipped ability during a run (D9): its definition, the owned level and every counter it
 * needs, all in ticks.
 *
 * <p>Cooldowns and durations are the authored numbers scaled by {@code ABILITY_COOLDOWN_MULT} and
 * {@code ABILITY_DURATION_MULT} (D8), resolved from the sheet at the moment the ability is
 * activated rather than once at run start — a modifier that changes the multipliers mid-run
 * therefore applies to the next activation, not retroactively to a burst already running. The
 * scaled value is rounded and never falls below 1 tick when the authored value is positive, so a
 * multiplier can shorten a burst but can never delete it.
 *
 * <p>Charges are generic: an ability whose level declares {@code charges} is charge-gated
 * (activation needs one and spends one) and regains one charge every {@code rechargeEveryGates}
 * passed gates, up to the maximum. The double flap is the one that uses it; anything else leaves
 * both keys out and is gated by its cooldown alone.
 */
public final class AbilityInstance {

    /** Level parameter holding the number of charges an activation spends one of. */
    public static final String PARAM_CHARGES = "charges";
    /** Level parameter holding how many passed gates give one charge back. */
    public static final String PARAM_RECHARGE_EVERY_GATES = "rechargeEveryGates";

    private final AbilityDef def;
    private final int level;
    private final AbilityBehavior behavior;
    private final int maxCharges;
    private final int rechargeEveryGates;
    private int charges;
    private int cooldownRemaining;
    private int durationRemaining;
    private int activations;

    /**
     * Creates an instance at a level.
     *
     * @param def the definition
     * @param level the owned level, 1-based, clamped into the levels the definition ships
     * @param behavior the behaviour implementation
     */
    public AbilityInstance(AbilityDef def, int level, AbilityBehavior behavior) {
        this.def = Objects.requireNonNull(def, "def");
        this.behavior = Objects.requireNonNull(behavior, "behavior");
        if (def.levels().isEmpty()) {
            throw new IllegalArgumentException("ability '" + def.id() + "' has no level");
        }
        this.level = MathUtil.clamp(level, 1, def.levels().size());
        this.maxCharges = (int) param(PARAM_CHARGES, 0);
        this.rechargeEveryGates = (int) param(PARAM_RECHARGE_EVERY_GATES, 0);
        this.charges = maxCharges;
    }

    /**
     * The definition.
     *
     * @return the definition
     */
    public AbilityDef def() {
        return def;
    }

    /**
     * The ability id.
     *
     * @return the id
     */
    public String id() {
        return def.id();
    }

    /**
     * Active or passive.
     *
     * @return the kind
     */
    public AbilityKind kind() {
        return def.kind();
    }

    /**
     * The owned level (1-based).
     *
     * @return the level
     */
    public int level() {
        return level;
    }

    /**
     * The definition of the owned level.
     *
     * @return the level definition
     */
    public AbilityLevelDef levelDef() {
        return def.levels().get(level - 1);
    }

    /**
     * The behaviour implementation.
     *
     * @return the behaviour
     */
    public AbilityBehavior behavior() {
        return behavior;
    }

    /**
     * A level parameter.
     *
     * @param key the parameter name
     * @param fallback the value to use when the level does not declare it
     * @return the value
     */
    public double param(String key, double fallback) {
        Double value = levelDef().params().get(key);
        return value == null ? fallback : value;
    }

    /**
     * A level parameter truncated to an int.
     *
     * @param key the parameter name
     * @param fallback the value to use when the level does not declare it
     * @return the value
     */
    public int intParam(String key, int fallback) {
        return (int) param(key, fallback);
    }

    /**
     * Whether an activation is possible right now: an active ability, off cooldown, not already
     * running and with a charge left when it is charge-gated.
     *
     * @return {@code true} when {@link #activate(StatSheet)} would succeed
     */
    public boolean isReady() {
        return def.kind() == AbilityKind.ACTIVE && cooldownRemaining == 0 && durationRemaining == 0
                && (maxCharges == 0 || charges > 0);
    }

    /**
     * Whether the ability's effect window is running.
     *
     * @return {@code true} while the duration has not elapsed
     */
    public boolean isActive() {
        return durationRemaining > 0;
    }

    /**
     * Spends a charge and starts the duration and the cooldown, both scaled by the sheet.
     *
     * @param stats the resolved stats
     * @return {@code true} when the activation was accepted
     */
    public boolean activate(StatSheet stats) {
        if (!isReady()) {
            return false;
        }
        if (maxCharges > 0) {
            charges--;
        }
        durationRemaining = scale(levelDef().durationTicks(),
                stats.resolve(StatId.ABILITY_DURATION_MULT));
        cooldownRemaining = scale(levelDef().cooldownTicks(),
                stats.resolve(StatId.ABILITY_COOLDOWN_MULT));
        activations++;
        return true;
    }

    /**
     * Advances the cooldown and the duration by one tick.
     *
     * <p>"Ready" is a transition, computed from {@link #isReady()} before and after both counters
     * moved, not from the cooldown alone: a level whose duration outlasts its cooldown becomes
     * usable when the <em>duration</em> ends, and reporting only the cooldown edge would leave the
     * HUD ring and the audio cue silent on exactly that level.
     *
     * @return what changed this tick
     */
    public Tick advance() {
        boolean wasReady = isReady();
        boolean expired = false;
        if (durationRemaining > 0) {
            durationRemaining--;
            expired = durationRemaining == 0;
        }
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
        }
        return new Tick(expired, !wasReady && isReady());
    }

    /**
     * What one tick of bookkeeping produced.
     *
     * @param expired the effect window ended this tick
     * @param ready the ability became usable again this tick
     */
    public record Tick(boolean expired, boolean ready) {
    }

    /**
     * Gives one charge back when the gate count reaches the recharge cadence.
     *
     * @param gatesPassed the gates passed so far
     * @return {@code true} when a charge was restored
     */
    public boolean recharge(int gatesPassed) {
        if (maxCharges == 0 || rechargeEveryGates <= 0 || charges >= maxCharges
                || gatesPassed <= 0 || gatesPassed % rechargeEveryGates != 0) {
            return false;
        }
        charges++;
        return true;
    }

    /**
     * Remaining cooldown in ticks.
     *
     * @return the count
     */
    public int cooldownRemaining() {
        return cooldownRemaining;
    }

    /**
     * Remaining effect window in ticks.
     *
     * @return the count
     */
    public int durationRemaining() {
        return durationRemaining;
    }

    /**
     * Charges left; {@code 0} for an ability that is not charge-gated.
     *
     * @return the count
     */
    public int charges() {
        return charges;
    }

    /**
     * Charges the ability starts with; {@code 0} when it is not charge-gated.
     *
     * @return the count
     */
    public int maxCharges() {
        return maxCharges;
    }

    /**
     * Passed gates between two restored charges; {@code 0} when charges never come back.
     *
     * @return the cadence
     */
    public int rechargeEveryGates() {
        return rechargeEveryGates;
    }

    /**
     * How many times the ability was activated this run.
     *
     * @return the count
     */
    public int activations() {
        return activations;
    }

    /**
     * How much of the cooldown is left, for the HUD ring.
     *
     * @param stats the resolved stats (the ring scales with the same multiplier the cooldown did)
     * @return a fraction in {@code [0, 1]}; 0 when ready
     */
    public double cooldownFraction(StatSheet stats) {
        if (cooldownRemaining == 0) {
            return 0;
        }
        int full = scale(levelDef().cooldownTicks(), stats.resolve(StatId.ABILITY_COOLDOWN_MULT));
        return full <= 0 ? 0 : MathUtil.clamp((double) cooldownRemaining / full, 0, 1);
    }

    /**
     * How much of the effect window is left, for the HUD's duration bar.
     *
     * @param stats the resolved stats (the bar scales with the same multiplier the duration did)
     * @return a fraction in {@code [0, 1]}; 0 when the ability is not running
     */
    public double durationFraction(StatSheet stats) {
        if (durationRemaining == 0) {
            return 0;
        }
        int full = scale(levelDef().durationTicks(), stats.resolve(StatId.ABILITY_DURATION_MULT));
        return full <= 0 ? 0 : MathUtil.clamp((double) durationRemaining / full, 0, 1);
    }

    /**
     * Scales an authored tick count by an ability multiplier (D8): rounded, and never rounded
     * down to nothing.
     *
     * @param ticks the authored count
     * @param multiplier the resolved multiplier
     * @return the scaled count
     */
    public static int scale(int ticks, double multiplier) {
        if (ticks <= 0) {
            return 0;
        }
        long scaled = Math.round(ticks * multiplier);
        return (int) Math.max(1, scaled);
    }

    /**
     * Folds the instance state into a hash (D12).
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, MathUtil.fnv1a64(def.id()));
        h = MathUtil.fold(h, level);
        h = MathUtil.fold(h, charges);
        h = MathUtil.fold(h, cooldownRemaining);
        h = MathUtil.fold(h, durationRemaining);
        return MathUtil.fold(h, activations);
    }

    @Override
    public String toString() {
        return "AbilityInstance{" + def.id() + " L" + level + ", cd=" + cooldownRemaining
                + ", dur=" + durationRemaining + ", charges=" + charges + '}';
    }
}
