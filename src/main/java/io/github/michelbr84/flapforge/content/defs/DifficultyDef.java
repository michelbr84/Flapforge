package io.github.michelbr84.flapforge.content.defs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole of {@code difficulty.json} (D20, §4): the named curves, the speed-ramp rate used by
 * the {@code SPEED_RAMP} rule flag (E32.b), the tier list and the reserved generator key.
 *
 * @param curves curve lines per curve id, in file order
 * @param speedRampPerTick scroll multiplier added per tick alive while {@code SPEED_RAMP} is on
 * @param tiers the tiers in file order
 * @param tierGenerator reserved for endless tiers; {@code null} in 1.0
 */
public record DifficultyDef(Map<String, List<CurveEntryDef>> curves, double speedRampPerTick,
        List<TierDef> tiers, TierGeneratorDef tierGenerator) {

    /**
     * Copies the collections while keeping file order.
     *
     * @param curves the curves
     * @param speedRampPerTick the speed ramp rate
     * @param tiers the tiers
     * @param tierGenerator the reserved generator block
     */
    public DifficultyDef {
        Map<String, List<CurveEntryDef>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<CurveEntryDef>> e : curves.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        curves = Collections.unmodifiableMap(copy);
        tiers = List.copyOf(tiers);
    }

    /**
     * The curves as registry entries, in file order.
     *
     * @return the curve definitions
     */
    public List<CurveDef> curveDefs() {
        List<CurveDef> out = new ArrayList<>(curves.size());
        for (Map.Entry<String, List<CurveEntryDef>> e : curves.entrySet()) {
            out.add(new CurveDef(e.getKey(), e.getValue()));
        }
        return out;
    }
}
