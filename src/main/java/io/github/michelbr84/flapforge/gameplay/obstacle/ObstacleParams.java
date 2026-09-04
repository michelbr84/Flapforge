package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.Playfield;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code patterns.json} parameter contract of every obstacle kind (§4 {@code ParamSpec},
 * D10): which keys a step may carry, their ranges and their defaults.
 *
 * <p>{@link #validate} reports every problem of a params map with the offending key in front of
 * the message ({@code "rail.speed: 300 is outside [1, 120]"}), so the content validator can
 * prefix it with the JSON pointer of the step. {@link #resolve} turns a valid map into the typed
 * {@link KindParams} the spawn table materialises, with fractions of the playable height
 * ({@code cy}, {@code gapCenter}) converted to pixels.
 *
 * <p>Values are read as the generic shapes Gson produces for {@code Map<String, Object>}:
 * {@link Number}, {@link String}, {@link Boolean} and a nested {@link Map}. A wrong shape is a
 * validation message, never an exception.
 *
 * <table>
 * <caption>Keys per kind</caption>
 * <tr><th>kind</th><th>keys</th></tr>
 * <tr><td>pipe_gate</td><td>{@code layout} STANDARD|FLOATING (STANDARD), {@code gapCenter} 0..1
 * or "random" ("random"), {@code gapSize} 72–220 (the {@code GAP_SIZE} stat), {@code oscillate}
 * (false), {@code amplitude} 1–200 (51), {@code speed} 0–120 px/s (the {@code OSCILLATION_SPEED}
 * stat)</td></tr>
 * <tr><td>gear</td><td>{@code cy} 0..1, {@code radius} 24–56, {@code rail} {amplitude 1–200,
 * speed 1–120}</td></tr>
 * <tr><td>piston</td><td>{@code side} TOP|BOTTOM, {@code length} 80–360, {@code telegraphTicks}
 * ≥ 15 (40), {@code extendTicks} ≥ 1 (12), {@code holdTicks} ≥ 0 (30), {@code retractTicks} ≥ 1
 * (20), {@code phaseOffset} ≥ 0 (0)</td></tr>
 * <tr><td>wind_zone</td><td>{@code width} 60–240, {@code cy} 0..1, {@code height} 40–598,
 * {@code accelY} −900..900 (0), {@code scrollDelta} −60..60 (0)</td></tr>
 * <tr><td>lightning</td><td>{@code side} TOP|BOTTOM, {@code lengthFrac} 0.3–0.7,
 * {@code warningTicks} ≥ 30 (45), {@code strikeTicks} 6–16 (10)</td></tr>
 * </table>
 */
public final class ObstacleParams {

    /** The sentinel a {@code gapCenter} may carry instead of a number. */
    public static final String RANDOM = "random";
    /** Longest tick count a phase parameter accepts. */
    public static final int MAX_TICKS = 600;
    /** Largest phase offset a piston step accepts. */
    public static final int MAX_PHASE_OFFSET = 10_000;
    /** Largest oscillation amplitude a gate or a rail accepts, in px. */
    public static final double MAX_AMPLITUDE = 200;
    /** Fastest oscillation a gate or a rail accepts, in px/s. */
    public static final double MAX_OSCILLATION_SPEED = 120;
    /** Smallest wind zone height, in px. */
    public static final double MIN_WIND_HEIGHT = 40;
    /** Height of a pattern-placed floating gate's upper pipe. */
    public static final int PATTERN_FLOAT_H = 133;

    private static final List<String> SIDES = List.of(Side.TOP.name(), Side.BOTTOM.name());
    private static final List<String> LAYOUTS = List.of(PipeGate.Layout.STANDARD.name(),
            PipeGate.Layout.FLOATING.name());
    private static final Map<ObstacleKind, List<ObstacleParamSpec>> SPECS;

    static {
        EnumMap<ObstacleKind, List<ObstacleParamSpec>> specs = new EnumMap<>(ObstacleKind.class);
        specs.put(ObstacleKind.PIPE_GATE, List.of(
                ObstacleParamSpec.enumOf("layout", LAYOUTS, false),
                ObstacleParamSpec.numberOrRandom("gapCenter", 0, 1, false),
                ObstacleParamSpec.number("gapSize", 72, 220, false),
                ObstacleParamSpec.bool("oscillate"),
                ObstacleParamSpec.number("amplitude", 1, MAX_AMPLITUDE, false),
                ObstacleParamSpec.number("speed", 0, MAX_OSCILLATION_SPEED, false)));
        specs.put(ObstacleKind.GEAR, List.of(
                ObstacleParamSpec.number("cy", 0, 1, true),
                ObstacleParamSpec.number("radius", Gear.MIN_RADIUS, Gear.MAX_RADIUS, true),
                ObstacleParamSpec.object("rail", List.of(
                        ObstacleParamSpec.number("amplitude", 1, MAX_AMPLITUDE, true),
                        ObstacleParamSpec.number("speed", 1, MAX_OSCILLATION_SPEED, true)),
                        false)));
        specs.put(ObstacleKind.PISTON, List.of(
                ObstacleParamSpec.enumOf("side", SIDES, true),
                ObstacleParamSpec.number("length", Piston.MIN_LENGTH, Piston.MAX_LENGTH, true),
                ObstacleParamSpec.integer("telegraphTicks", Piston.MIN_TELEGRAPH_TICKS, MAX_TICKS,
                        false),
                ObstacleParamSpec.integer("extendTicks", 1, MAX_TICKS, false),
                ObstacleParamSpec.integer("holdTicks", 0, MAX_TICKS, false),
                ObstacleParamSpec.integer("retractTicks", 1, MAX_TICKS, false),
                ObstacleParamSpec.integer("phaseOffset", 0, MAX_PHASE_OFFSET, false)));
        specs.put(ObstacleKind.WIND_ZONE, List.of(
                ObstacleParamSpec.number("width", WindZone.MIN_WIDTH, WindZone.MAX_WIDTH, true),
                ObstacleParamSpec.number("cy", 0, 1, true),
                ObstacleParamSpec.number("height", MIN_WIND_HEIGHT, Playfield.GROUND_Y, true),
                ObstacleParamSpec.number("accelY", WindZone.MIN_ACCEL_Y, WindZone.MAX_ACCEL_Y,
                        false),
                ObstacleParamSpec.number("scrollDelta", WindZone.MIN_SCROLL_DELTA,
                        WindZone.MAX_SCROLL_DELTA, false)));
        specs.put(ObstacleKind.LIGHTNING, List.of(
                ObstacleParamSpec.enumOf("side", SIDES, true),
                ObstacleParamSpec.number("lengthFrac", LightningStrike.MIN_LENGTH_FRAC,
                        LightningStrike.MAX_LENGTH_FRAC, true),
                ObstacleParamSpec.integer("warningTicks", LightningStrike.MIN_WARNING_TICKS,
                        MAX_TICKS, false),
                ObstacleParamSpec.integer("strikeTicks", LightningStrike.MIN_STRIKE_TICKS,
                        LightningStrike.MAX_STRIKE_TICKS, false)));
        SPECS = Collections.unmodifiableMap(specs);
    }

    private ObstacleParams() {
    }

    /**
     * The parameter contract of a kind.
     *
     * @param kind the obstacle kind
     * @return the specs, in declaration order
     */
    public static List<ObstacleParamSpec> specs(ObstacleKind kind) {
        return SPECS.get(kind);
    }

    /**
     * The keys a kind reads.
     *
     * @param kind the obstacle kind
     * @return the key names
     */
    public static List<String> keys(ObstacleKind kind) {
        return keysOf(specs(kind));
    }

    /**
     * Checks a params map against the kind's contract.
     *
     * @param kind the obstacle kind
     * @param params the step parameters ({@code null} counts as empty)
     * @return the problems, each starting with the key; empty when valid
     */
    public static List<String> validate(ObstacleKind kind, Map<String, ?> params) {
        List<String> errors = new ArrayList<>();
        validateInto(errors, "", kind.name().toLowerCase(java.util.Locale.ROOT), specs(kind),
                params == null ? Map.of() : params);
        return errors;
    }

    private static void validateInto(List<String> errors, String prefix, String owner,
            List<ObstacleParamSpec> specs, Map<String, ?> params) {
        List<String> known = keysOf(specs);
        for (String key : params.keySet()) {
            if (!known.contains(key)) {
                errors.add(prefix + key + ": " + owner + " reads no such parameter; it reads "
                        + known);
            }
        }
        for (ObstacleParamSpec spec : specs) {
            String at = prefix + spec.key();
            Object value = params.get(spec.key());
            if (value == null) {
                if (spec.required()) {
                    errors.add(at + ": required by " + owner);
                }
                continue;
            }
            switch (spec.type()) {
                case NUMBER:
                case INTEGER:
                    checkNumber(errors, at, spec, value);
                    break;
                case ENUM:
                    if (!(value instanceof String name) || !spec.allowed().contains(name)) {
                        errors.add(at + ": expected one of " + spec.allowed() + ", was " + value);
                    }
                    break;
                case BOOLEAN:
                    if (!(value instanceof Boolean)) {
                        errors.add(at + ": expected true or false, was " + value);
                    }
                    break;
                case OBJECT:
                default:
                    if (value instanceof Map<?, ?> nested) {
                        validateInto(errors, at + ".", owner + "." + spec.key(), spec.children(),
                                stringKeyed(nested));
                    } else {
                        errors.add(at + ": expected an object with " + keysOf(spec.children())
                                + ", was " + value);
                    }
                    break;
            }
        }
    }

    private static void checkNumber(List<String> errors, String at, ObstacleParamSpec spec,
            Object value) {
        if (value instanceof String text && spec.allowsRandom() && RANDOM.equals(text)) {
            return;
        }
        if (!(value instanceof Number number)) {
            errors.add(at + ": expected a number" + (spec.allowsRandom() ? " or \"random\"" : "")
                    + ", was " + value);
            return;
        }
        double v = number.doubleValue();
        if (Double.isNaN(v) || !spec.accepts(v)) {
            errors.add(at + ": " + v + " is outside [" + spec.min() + ", " + spec.max() + "]");
            return;
        }
        if (spec.type() == ObstacleParamSpec.Type.INTEGER && v != Math.rint(v)) {
            errors.add(at + ": " + v + " is not a whole number of ticks");
        }
    }

    /**
     * Turns a valid params map into the typed geometry of the kind.
     *
     * @param kind the obstacle kind
     * @param params the step parameters ({@code null} counts as empty)
     * @return the typed parameters, defaults filled in
     * @throws IllegalArgumentException listing every problem when the map is not valid
     */
    public static KindParams resolve(ObstacleKind kind, Map<String, ?> params) {
        Map<String, ?> p = params == null ? Map.of() : params;
        List<String> errors = validate(kind, p);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid " + kind + " params: "
                    + String.join("; ", errors));
        }
        switch (kind) {
            case PIPE_GATE:
                return new KindParams.GateSpec(
                        PipeGate.Layout.valueOf(text(p, "layout", PipeGate.Layout.STANDARD.name())),
                        gapCenter(p.get("gapCenter")),
                        number(p, "gapSize", 0),
                        bool(p, "oscillate"),
                        number(p, "amplitude", Oscillator.DEFAULT_AMPLITUDE),
                        number(p, "speed", 0));
            case GEAR: {
                Object rail = p.get("rail");
                double amplitude = 0;
                double speed = 0;
                if (rail instanceof Map<?, ?> nested) {
                    Map<String, ?> r = stringKeyed(nested);
                    amplitude = number(r, "amplitude", 0);
                    speed = number(r, "speed", 0);
                }
                return new KindParams.GearSpec(number(p, "cy", 0.5) * Playfield.GROUND_Y,
                        number(p, "radius", Gear.MIN_RADIUS), amplitude, speed);
            }
            case PISTON:
                return new KindParams.PistonSpec(Side.valueOf(text(p, "side", Side.TOP.name())),
                        number(p, "length", Piston.MIN_LENGTH),
                        integer(p, "telegraphTicks", Piston.DEFAULT_TELEGRAPH_TICKS),
                        integer(p, "extendTicks", Piston.DEFAULT_EXTEND_TICKS),
                        integer(p, "holdTicks", Piston.DEFAULT_HOLD_TICKS),
                        integer(p, "retractTicks", Piston.DEFAULT_RETRACT_TICKS),
                        integer(p, "phaseOffset", 0));
            case WIND_ZONE:
                return new KindParams.WindSpec(number(p, "width", WindZone.MIN_WIDTH),
                        number(p, "cy", 0.5) * Playfield.GROUND_Y,
                        number(p, "height", MIN_WIND_HEIGHT),
                        number(p, "accelY", 0), number(p, "scrollDelta", 0));
            case LIGHTNING:
            default:
                return new KindParams.LightningSpec(Side.valueOf(text(p, "side", Side.TOP.name())),
                        number(p, "lengthFrac", LightningStrike.MIN_LENGTH_FRAC),
                        integer(p, "warningTicks", LightningStrike.DEFAULT_WARNING_TICKS),
                        integer(p, "strikeTicks", LightningStrike.DEFAULT_STRIKE_TICKS));
        }
    }

    private static double gapCenter(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.NaN;
    }

    private static double number(Map<String, ?> params, String key, double fallback) {
        Object value = params.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static int integer(Map<String, ?> params, String key, int fallback) {
        Object value = params.get(key);
        return value instanceof Number number ? (int) Math.rint(number.doubleValue()) : fallback;
    }

    private static String text(Map<String, ?> params, String key, String fallback) {
        Object value = params.get(key);
        return value instanceof String text ? text : fallback;
    }

    private static boolean bool(Map<String, ?> params, String key) {
        Object value = params.get(key);
        return value instanceof Boolean flag && flag;
    }

    private static Map<String, ?> stringKeyed(Map<?, ?> map) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static List<String> keysOf(List<ObstacleParamSpec> specs) {
        List<String> keys = new ArrayList<>(specs.size());
        for (ObstacleParamSpec spec : specs) {
            keys.add(spec.key());
        }
        return keys;
    }
}
