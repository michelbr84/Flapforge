package io.github.michelbr84.flapforge.gameplay.difficulty;

import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pushes the difficulty curve, tier and world effects into their layers (D20).
 *
 * <p>{@link #refresh(int, int)} writes the curve evaluated at {@code gates} into
 * {@code DIFFICULTY}, the tier effects into {@code TIER} and the world effects into
 * {@code WORLD}. Under {@link RuleFlag#SPEED_RAMP} (E32.b) the {@code DIFFICULTY} layer also
 * carries {@code SCROLL_SPEED MULTIPLY (1 + speedRampPerTick × ticksAlive)}, so the owner must
 * refresh every tick while the flag is active ({@link #needsTickRefresh()}); otherwise a refresh
 * per passed gate is enough.
 */
public final class DifficultyState {

    /** {@code difficulty.json.speedRampPerTick} shipped value. */
    public static final double DEFAULT_SPEED_RAMP_PER_TICK = 0.0005;

    private static final String RAMP_SOURCE = "speed_ramp";

    private final EffectStack stack;
    private final DifficultyCurve curve;
    private final List<StatModifier> tierEffects;
    private final List<StatModifier> worldEffects;
    private final double speedRampPerTick;
    private RuleSet rules;

    /**
     * Creates the state.
     *
     * @param stack the stack to write into
     * @param curve the difficulty curve
     * @param tierEffects effects of the tier
     * @param worldEffects effects of the world
     * @param speedRampPerTick growth of the scroll multiplier per tick under {@code SPEED_RAMP}
     * @param rules the active rules
     */
    public DifficultyState(EffectStack stack, DifficultyCurve curve,
            List<StatModifier> tierEffects, List<StatModifier> worldEffects,
            double speedRampPerTick, RuleSet rules) {
        this.stack = Objects.requireNonNull(stack, "stack");
        this.curve = Objects.requireNonNull(curve, "curve");
        this.tierEffects = List.copyOf(tierEffects);
        this.worldEffects = List.copyOf(worldEffects);
        this.speedRampPerTick = speedRampPerTick;
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * Recomputes the layers.
     *
     * @param gates gates passed so far
     * @param ticksAlive raw ticks alive (for the speed ramp)
     */
    public void refresh(int gates, int ticksAlive) {
        List<StatModifier> difficulty = new ArrayList<>(curve.at(gates));
        if (rules.contains(RuleFlag.SPEED_RAMP)) {
            difficulty.add(new StatModifier(StatId.SCROLL_SPEED, StatOp.MULTIPLY,
                    1 + speedRampPerTick * ticksAlive, RAMP_SOURCE));
        }
        stack.setLayer(Layer.DIFFICULTY, difficulty);
        stack.setLayer(Layer.TIER, tierEffects);
        stack.setLayer(Layer.WORLD, worldEffects);
    }

    /**
     * Tells whether the layers change every tick (speed ramp active).
     *
     * @return {@code true} when a per-tick refresh is required
     */
    public boolean needsTickRefresh() {
        return rules.contains(RuleFlag.SPEED_RAMP);
    }

    /**
     * Replaces the active rules (rule cycles).
     *
     * @param rules the new rules
     */
    public void setRules(RuleSet rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * The curve in use.
     *
     * @return the curve
     */
    public DifficultyCurve curve() {
        return curve;
    }

    /**
     * Growth of the scroll multiplier per tick under {@code SPEED_RAMP}.
     *
     * @return the per-tick ramp
     */
    public double speedRampPerTick() {
        return speedRampPerTick;
    }
}
