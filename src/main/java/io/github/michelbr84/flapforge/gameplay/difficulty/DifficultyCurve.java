package io.github.michelbr84.flapforge.gameplay.difficulty;

import io.github.michelbr84.flapforge.gameplay.spec.CurveEntry;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates a {@link CurveSpec} at a gate count (D20): every entry becomes a modifier with
 * {@code value = clamp(base + perGate × gates, min, max)}.
 */
public final class DifficultyCurve {

    /** The classic curve. */
    public static final DifficultyCurve CLASSIC = new DifficultyCurve(CurveSpec.CLASSIC);

    private final CurveSpec spec;

    /**
     * Wraps a curve spec.
     *
     * @param spec the spec
     */
    public DifficultyCurve(CurveSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    /**
     * Modifiers for a gate count, in entry order.
     *
     * @param gates gates passed
     * @return the modifiers (source {@code curve:<id>})
     */
    public List<StatModifier> at(int gates) {
        List<StatModifier> out = new ArrayList<>(spec.entries().size());
        String source = "curve:" + spec.id();
        for (CurveEntry e : spec.entries()) {
            out.add(e.at(gates, source));
        }
        return out;
    }

    /**
     * The underlying spec.
     *
     * @return the spec
     */
    public CurveSpec spec() {
        return spec;
    }
}
