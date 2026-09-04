package io.github.michelbr84.flapforge.content.defs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The {@code xp} block of {@code economy.json} (§4, E32.a): {@code xp = round((participation +
 * perGate × gates + bossBonus × bosses) × XP_MULT)}, the level curve and the level rewards.
 *
 * @param participation XP paid once per run
 * @param perGate XP per gate passed
 * @param bossBonus XP per world boss cleared
 * @param curve the level curve
 * @param levelRewards what each level grants, keyed by the level written as a string
 */
public record XpDef(long participation, long perGate, long bossBonus, XpCurveDef curve,
        Map<String, LevelRewardDef> levelRewards) {

    /**
     * Copies the reward map while keeping file order.
     *
     * @param participation the participation XP
     * @param perGate the XP per gate
     * @param bossBonus the XP per boss
     * @param curve the level curve
     * @param levelRewards the level rewards
     */
    public XpDef {
        Objects.requireNonNull(curve, "curve");
        if (participation < 0 || perGate < 0 || bossBonus < 0) {
            throw new IllegalArgumentException("xp rewards must not be negative");
        }
        levelRewards = Collections.unmodifiableMap(new LinkedHashMap<>(levelRewards));
    }
}
