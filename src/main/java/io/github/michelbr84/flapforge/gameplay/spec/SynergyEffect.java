package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.Objects;

/**
 * A bird effect that scales with the total of owned upgrade levels (D8,
 * {@code BirdDef.synergyEffects}), resolved <em>once</em> at run start into the
 * {@code BIRD_SYNERGY} layer.
 *
 * <p>It is the mirror of {@link RampEffect}, with the upgrade total in place of the gate count and
 * one deliberate difference: a ramp is re-evaluated on every passed gate, a synergy is not.
 * Buying a node mid-session changes the next run, never the one in progress — Cinder's
 * "upgrade synergy" is a build decision, not a live one.
 *
 * <p>The amount is {@code perUpgradeLevel × levels} capped at {@code max} (towards zero, so a
 * negative growth is floored at a negative cap). For {@code FLAT_ADD} and {@code PERCENT_ADD} the
 * amount is the modifier value; for {@code MULTIPLY} the value is {@code 1 + amount}, so a player
 * with no upgrades gets a no-op rather than a multiplication by zero.
 *
 * @param stat the stat affected
 * @param op how the effect combines
 * @param perUpgradeLevel the growth per owned upgrade level
 * @param max the cap of the accumulated amount
 */
public record SynergyEffect(StatId stat, StatOp op, double perUpgradeLevel, double max) {

    /**
     * Validates the components.
     *
     * @param stat the stat affected
     * @param op how the effect combines
     * @param perUpgradeLevel the growth per level
     * @param max the cap
     */
    public SynergyEffect {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(op, "op");
    }

    /**
     * Effect amount for a total of owned upgrade levels.
     *
     * @param upgradeLevels the total of owned upgrade levels
     * @return {@code perUpgradeLevel × upgradeLevels} capped at {@code max}
     */
    public double amountAt(int upgradeLevels) {
        double amount = perUpgradeLevel * Math.max(0, upgradeLevels);
        return perUpgradeLevel >= 0 ? Math.min(amount, max) : Math.max(amount, max);
    }

    /**
     * The modifier to push into the {@code BIRD_SYNERGY} layer.
     *
     * @param upgradeLevels the total of owned upgrade levels
     * @param source the origin label
     * @return the modifier
     */
    public StatModifier at(int upgradeLevels, String source) {
        double amount = amountAt(upgradeLevels);
        double value = op == StatOp.MULTIPLY ? 1 + amount : amount;
        return new StatModifier(stat, op, value, source);
    }

    /**
     * Specified hash (enums by ordinal, not by identity), like every seam record.
     *
     * @return the hash
     */
    @Override
    public int hashCode() {
        int h = stat.ordinal();
        h = 31 * h + op.ordinal();
        h = 31 * h + Double.hashCode(perUpgradeLevel);
        return 31 * h + Double.hashCode(max);
    }
}
