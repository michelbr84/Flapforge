package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One node of an upgrade tree (D13, §4). Nodes are bought level by level with coins; their
 * effects land in the {@code UPGRADES} layer at run start.
 *
 * <p>A node is <em>not</em> an unlockable (E21): ownership lives in {@code profile.upgrades} as
 * {@code <node id> -> level}, and {@code tree:<tree>} is what has to be unlocked first.
 *
 * <p>Level scaling follows D8: {@code FLAT_ADD} and {@code PERCENT_ADD} values scale linearly
 * with the level, {@code MULTIPLY} compounds ({@code value^level}). {@link #levelOverrides}
 * replaces that arithmetic for one level when a node needs a hand-authored step; the shipped
 * nodes use the linear rule and ship an empty map.
 *
 * @param id the node id, unique across every tree
 * @param tree the id of the {@link TreeDef} the node belongs to
 * @param tier the row in the tree; a prerequisite always sits in a lower tier
 * @param maxLevel how many times the node can be bought
 * @param prereqs node ids that must be owned at level 1 or higher
 * @param costs the coin price of each level; exactly {@code maxLevel} entries
 * @param effectsPerLevel the stat modifiers of one level, scaled per D8
 * @param levelOverrides level number to the modifiers that replace the scaled ones at that level
 * @param grants what buying the node hands over (E31.f)
 */
public record UpgradeDef(String id, String tree, int tier, int maxLevel, List<String> prereqs,
        List<Long> costs, List<StatModifierDef> effectsPerLevel,
        Map<String, List<StatModifierDef>> levelOverrides, List<GrantDef> grants) {

    /**
     * Copies the collections and checks the shape that does not need other files to be known.
     *
     * @throws NullPointerException when the id or the tree is missing
     * @throws IllegalArgumentException when the tier, the level count or a cost is out of range
     */
    public UpgradeDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tree, "tree");
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be at least 1: " + tier);
        }
        if (maxLevel < 1) {
            throw new IllegalArgumentException("maxLevel must be at least 1: " + maxLevel);
        }
        prereqs = List.copyOf(prereqs);
        costs = List.copyOf(costs);
        for (Long cost : costs) {
            if (cost == null || cost < 0) {
                throw new IllegalArgumentException("costs must not be negative: " + cost);
            }
        }
        effectsPerLevel = List.copyOf(effectsPerLevel);
        Map<String, List<StatModifierDef>> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, List<StatModifierDef>> e : levelOverrides.entrySet()) {
            overrides.put(e.getKey(), List.copyOf(e.getValue()));
        }
        levelOverrides = Collections.unmodifiableMap(overrides);
        grants = List.copyOf(grants);
    }

    /**
     * The price of one level.
     *
     * @param level the level being bought, 1-based
     * @return the cost in coins
     * @throws IllegalArgumentException when the level is outside {@code [1, maxLevel]}
     */
    public long costOf(int level) {
        if (level < 1 || level > costs.size()) {
            throw new IllegalArgumentException(
                    "level " + level + " is outside 1.." + costs.size() + " for node '" + id + "'");
        }
        return costs.get(level - 1);
    }

    /**
     * The effects a node owned at {@code level} contributes (D8).
     *
     * <p>{@code FLAT_ADD} and {@code PERCENT_ADD} scale linearly, {@code MULTIPLY} compounds, and
     * a {@link #levelOverrides} entry for the level replaces the computed list wholesale.
     *
     * @param level the owned level; {@code 0} or less contributes nothing
     * @return the modifiers, sourced as {@code upgrade:<id>}
     */
    public List<StatModifier> effectsAt(int level) {
        if (level <= 0) {
            return List.of();
        }
        String source = "upgrade:" + id;
        List<StatModifierDef> override = levelOverrides.get(Integer.toString(level));
        if (override != null) {
            List<StatModifier> out = new ArrayList<>(override.size());
            for (StatModifierDef def : override) {
                out.add(def.toModifier(source));
            }
            return Collections.unmodifiableList(out);
        }
        List<StatModifier> out = new ArrayList<>(effectsPerLevel.size());
        for (StatModifierDef def : effectsPerLevel) {
            double value = def.op() == StatOp.MULTIPLY
                    ? StrictMath.pow(def.value(), level) : def.value() * level;
            out.add(new StatModifier(def.stat(), def.op(), value, source));
        }
        return Collections.unmodifiableList(out);
    }
}
