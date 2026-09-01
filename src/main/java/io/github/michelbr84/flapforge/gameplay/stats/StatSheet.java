package io.github.michelbr84.flapforge.gameplay.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves stats from a base map, an {@link EffectStack} and a {@link RuleSet} (D8):
 * {@code resolve(stat) = zeroedBy(rules, stat) ? 0 : clamp((base + ΣFLAT_ADD) × (1 + ΣPERCENT_ADD)
 * × ΠMULTIPLY)}.
 *
 * <p>The pipeline is commutative, so the layer a modifier sits in and the order of modifiers never
 * change the result. Resolved values are cached in a {@code double[]} that is rebuilt lazily when
 * the stack version or the rules change; {@link #resolve(StatId)} is therefore an array read in
 * the hot path.
 */
public final class StatSheet {

    private final double[] base = new double[StatId.COUNT];
    private final EffectStack stack;
    private final double[] cache = new double[StatId.COUNT];
    private RuleSet rules;
    private long cachedVersion = -1;

    /**
     * Creates a sheet.
     *
     * @param baseStats base values per stat; a missing stat uses {@link StatId#defaultValue()}
     * @param stack the modifier stack (shared with its owners, observed by version)
     * @param rules the active rules
     */
    public StatSheet(Map<StatId, Double> baseStats, EffectStack stack, RuleSet rules) {
        this.stack = Objects.requireNonNull(stack, "stack");
        this.rules = Objects.requireNonNull(rules, "rules");
        for (StatId id : StatId.values()) {
            Double v = baseStats.get(id);
            base[id.ordinal()] = v == null ? id.defaultValue() : v;
        }
    }

    /**
     * Creates a sheet with default base stats, an empty stack and no rules.
     *
     * @return the sheet
     */
    public static StatSheet defaults() {
        return new StatSheet(Map.of(), new EffectStack(), RuleSet.EMPTY);
    }

    /**
     * Resolves a stat.
     *
     * @param stat the stat
     * @return the clamped value, or 0 when a rule zeroes the stat
     */
    public double resolve(StatId stat) {
        if (cachedVersion != stack.version()) {
            rebuild();
        }
        return cache[stat.ordinal()];
    }

    /**
     * Base value of a stat (before any modifier).
     *
     * @param stat the stat
     * @return the base
     */
    public double base(StatId stat) {
        return base[stat.ordinal()];
    }

    /**
     * The modifier stack this sheet reads.
     *
     * @return the stack
     */
    public EffectStack stack() {
        return stack;
    }

    /**
     * The active rules.
     *
     * @return the rules
     */
    public RuleSet rules() {
        return rules;
    }

    /**
     * Replaces the active rules (world rule cycles change them mid-run).
     *
     * @param rules the new rules
     */
    public void setRules(RuleSet rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
        cachedVersion = -1;
    }

    /**
     * Explains how a stat resolves, listing every contributing modifier.
     *
     * @param stat the stat
     * @return the breakdown
     */
    public StatBreakdown breakdown(StatId stat) {
        List<EffectStack.Entry> contributions = new ArrayList<>();
        double flat = 0;
        double pct = 0;
        double mul = 1;
        for (EffectStack.Entry e : stack.entries()) {
            StatModifier m = e.modifier();
            if (m.stat() != stat) {
                continue;
            }
            contributions.add(e);
            switch (m.op()) {
                case FLAT_ADD -> flat += m.value();
                case PERCENT_ADD -> pct += m.value();
                case MULTIPLY -> mul *= m.value();
            }
        }
        double b = base[stat.ordinal()];
        double unclamped = (b + flat) * (1 + pct) * mul;
        boolean zeroed = rules.zeroes(stat);
        double value = zeroed ? 0 : stat.clamp(unclamped);
        return new StatBreakdown(stat, b, contributions, flat, pct, mul, unclamped, zeroed, value);
    }

    private void rebuild() {
        double[] flat = new double[StatId.COUNT];
        double[] pct = new double[StatId.COUNT];
        double[] mul = new double[StatId.COUNT];
        Arrays.fill(mul, 1.0);
        for (List<StatModifier> layer : stack.layers().values()) {
            for (StatModifier m : layer) {
                int i = m.stat().ordinal();
                switch (m.op()) {
                    case FLAT_ADD -> flat[i] += m.value();
                    case PERCENT_ADD -> pct[i] += m.value();
                    case MULTIPLY -> mul[i] *= m.value();
                }
            }
        }
        for (StatId id : StatId.values()) {
            int i = id.ordinal();
            if (rules.zeroes(id)) {
                cache[i] = 0;
            } else {
                cache[i] = id.clamp((base[i] + flat[i]) * (1 + pct[i]) * mul[i]);
            }
        }
        cachedVersion = stack.version();
    }
}
