package io.github.michelbr84.flapforge.content.defs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One level of an ability (§4). Level 1 comes with the unlock and therefore costs nothing; the
 * levels above it are bought in the shop, capped by {@code profile.abilityLevelCap} (E3).
 *
 * <p>M4 ships and validates the {@link #cost} column only (E19); {@link #cooldownTicks},
 * {@link #durationTicks} and {@link #params} are filled in M5 together with the behaviours that
 * read them, which is also when {@code ParamSpec} starts checking the parameter names.
 *
 * @param cooldownTicks ticks before the ability can be used again ({@code 0} for a passive)
 * @param durationTicks how long the effect lasts ({@code 0} for a passive)
 * @param params behaviour-specific numbers, read by the M5 behaviour of the ability
 * @param cost the coin price of this level; {@code 0} for level 1
 */
public record AbilityLevelDef(int cooldownTicks, int durationTicks, Map<String, Double> params,
        long cost) {

    /**
     * Copies the parameter map and checks the ranges.
     *
     * @throws IllegalArgumentException when a tick count or the cost is negative
     */
    public AbilityLevelDef {
        if (cooldownTicks < 0 || durationTicks < 0) {
            throw new IllegalArgumentException("ability tick counts must not be negative");
        }
        if (cost < 0) {
            throw new IllegalArgumentException("ability level cost must not be negative: " + cost);
        }
        params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }
}
