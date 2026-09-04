package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.obstacle.KindParams;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleParamSpec;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleParams;
import io.github.michelbr84.flapforge.gameplay.obstacle.Oscillator;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The §4 {@code ParamSpec} contract per obstacle kind: keys, ranges, defaults, messages. */
class ObstacleParamsTest {

    private static final double EPS = 1e-9;

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static boolean mentions(List<String> errors, String key, String fragment) {
        for (String e : errors) {
            if (e.startsWith(key + ":") && e.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyKindDeclaresTheKeysOfTheSchema() {
        assertEquals(List.of("layout", "gapCenter", "gapSize", "oscillate", "amplitude", "speed"),
                ObstacleParams.keys(ObstacleKind.PIPE_GATE));
        assertEquals(List.of("cy", "radius", "rail"), ObstacleParams.keys(ObstacleKind.GEAR));
        assertEquals(List.of("side", "length", "telegraphTicks", "extendTicks", "holdTicks",
                "retractTicks", "phaseOffset"), ObstacleParams.keys(ObstacleKind.PISTON));
        assertEquals(List.of("width", "cy", "height", "accelY", "scrollDelta"),
                ObstacleParams.keys(ObstacleKind.WIND_ZONE));
        assertEquals(List.of("side", "lengthFrac", "warningTicks", "strikeTicks"),
                ObstacleParams.keys(ObstacleKind.LIGHTNING));
        for (ObstacleKind kind : ObstacleKind.values()) {
            for (ObstacleParamSpec spec : ObstacleParams.specs(kind)) {
                assertTrue(spec.min() <= spec.max(), spec.key());
            }
        }
    }

    @Test
    void rangesFollowTheSchema() {
        assertRange(ObstacleKind.GEAR, "radius", 24, 56);
        assertRange(ObstacleKind.PISTON, "length", 80, 360);
        assertRange(ObstacleKind.PISTON, "telegraphTicks", 15, ObstacleParams.MAX_TICKS);
        assertRange(ObstacleKind.WIND_ZONE, "width", 60, 240);
        assertRange(ObstacleKind.WIND_ZONE, "accelY", -900, 900);
        assertRange(ObstacleKind.WIND_ZONE, "scrollDelta", -60, 60);
        assertRange(ObstacleKind.LIGHTNING, "lengthFrac", 0.3, 0.7);
        assertRange(ObstacleKind.LIGHTNING, "warningTicks", 30, ObstacleParams.MAX_TICKS);
        assertRange(ObstacleKind.LIGHTNING, "strikeTicks", 6, 16);
        assertRange(ObstacleKind.PIPE_GATE, "gapCenter", 0, 1);
    }

    private static void assertRange(ObstacleKind kind, String key, double min, double max) {
        for (ObstacleParamSpec spec : ObstacleParams.specs(kind)) {
            if (spec.key().equals(key)) {
                assertEquals(min, spec.min(), EPS, key + ".min");
                assertEquals(max, spec.max(), EPS, key + ".max");
                return;
            }
        }
        throw new AssertionError(kind + " has no key " + key);
    }

    @Test
    void unknownKeysMissingKeysAndOutOfRangeValuesAreReportedWithTheKey() {
        List<String> errors = ObstacleParams.validate(ObstacleKind.PISTON,
                map("side", "TOP", "length", 500, "telegraphTicks", 10, "bogus", 1));
        assertTrue(mentions(errors, "bogus", "no such parameter"), errors.toString());
        assertTrue(mentions(errors, "length", "outside [80.0, 360.0]"), errors.toString());
        assertTrue(mentions(errors, "telegraphTicks", "outside [15.0"), errors.toString());
        assertEquals(3, errors.size(), errors.toString());

        List<String> missing = ObstacleParams.validate(ObstacleKind.LIGHTNING, map());
        assertTrue(mentions(missing, "side", "required"), missing.toString());
        assertTrue(mentions(missing, "lengthFrac", "required"), missing.toString());
        assertEquals(2, missing.size());

        List<String> shapes = ObstacleParams.validate(ObstacleKind.LIGHTNING,
                map("side", "LEFT", "lengthFrac", "wide", "strikeTicks", 8.5));
        assertTrue(mentions(shapes, "side", "one of [TOP, BOTTOM]"), shapes.toString());
        assertTrue(mentions(shapes, "lengthFrac", "expected a number"), shapes.toString());
        assertTrue(mentions(shapes, "strikeTicks", "whole number"), shapes.toString());

        List<String> nested = ObstacleParams.validate(ObstacleKind.GEAR,
                map("cy", 0.3, "radius", 36, "rail", map("amplitude", 60, "speed", 300, "x", 1)));
        assertTrue(mentions(nested, "rail.speed", "outside [1.0, 120.0]"), nested.toString());
        assertTrue(mentions(nested, "rail.x", "no such parameter"), nested.toString());
        assertEquals(2, nested.size());
        List<String> notObject = ObstacleParams.validate(ObstacleKind.GEAR,
                map("cy", 0.3, "radius", 36, "rail", 5));
        assertTrue(mentions(notObject, "rail", "expected an object"), notObject.toString());

        List<String> bool = ObstacleParams.validate(ObstacleKind.PIPE_GATE, map("oscillate", 1));
        assertTrue(mentions(bool, "oscillate", "true or false"), bool.toString());
        assertTrue(ObstacleParams.validate(ObstacleKind.PIPE_GATE, null).isEmpty(),
                "a gate step needs no params at all");
    }

    @Test
    void randomIsAcceptedForTheGapCentreOnly() {
        assertTrue(ObstacleParams.validate(ObstacleKind.PIPE_GATE, map("gapCenter", "random"))
                .isEmpty());
        List<String> errors = ObstacleParams.validate(ObstacleKind.GEAR,
                map("cy", "random", "radius", 30));
        assertTrue(mentions(errors, "cy", "expected a number"), errors.toString());
        assertFalse(errors.get(0).contains("or \"random\""), "cy does not offer the sentinel");
        List<String> gate = ObstacleParams.validate(ObstacleKind.PIPE_GATE, map("gapCenter", "x"));
        assertTrue(gate.get(0).contains("or \"random\""), gate.toString());
    }

    @Test
    void resolveFillsTheDefaultsAndConvertsFractionsToPixels() {
        KindParams.GateSpec gate = (KindParams.GateSpec) ObstacleParams.resolve(
                ObstacleKind.PIPE_GATE, map());
        assertEquals(PipeGate.Layout.STANDARD, gate.layout());
        assertTrue(gate.randomGapCenter());
        assertEquals(0, gate.gapSize(), EPS);
        assertFalse(gate.oscillate());
        assertEquals(Oscillator.DEFAULT_AMPLITUDE, gate.amplitude(), EPS);
        assertEquals(0, gate.speed(), EPS);

        KindParams.GearSpec gear = (KindParams.GearSpec) ObstacleParams.resolve(ObstacleKind.GEAR,
                map("cy", 0.3, "radius", 36, "rail", map("amplitude", 60, "speed", 40)));
        assertEquals(0.3 * Playfield.GROUND_Y, gear.cy(), EPS);
        assertEquals(36, gear.radius(), EPS);
        assertEquals(60, gear.railAmplitude(), EPS);
        assertEquals(40, gear.railSpeed(), EPS);
        KindParams.GearSpec fixed = (KindParams.GearSpec) ObstacleParams.resolve(ObstacleKind.GEAR,
                map("cy", 0.5, "radius", 24L));
        assertFalse(fixed.hasRail());
        assertEquals(24, fixed.radius(), EPS, "Gson's LONG_OR_DOUBLE longs are numbers too");

        KindParams.PistonSpec piston = (KindParams.PistonSpec) ObstacleParams.resolve(
                ObstacleKind.PISTON, map("side", "BOTTOM", "length", 220));
        assertEquals(Side.BOTTOM, piston.side());
        assertEquals(Piston.DEFAULT_TELEGRAPH_TICKS, piston.telegraphTicks());
        assertEquals(Piston.DEFAULT_EXTEND_TICKS, piston.extendTicks());
        assertEquals(Piston.DEFAULT_HOLD_TICKS, piston.holdTicks());
        assertEquals(Piston.DEFAULT_RETRACT_TICKS, piston.retractTicks());
        assertEquals(0, piston.phaseOffset());

        KindParams.WindSpec wind = (KindParams.WindSpec) ObstacleParams.resolve(
                ObstacleKind.WIND_ZONE, map("width", 120, "cy", 0.5, "height", 240,
                        "scrollDelta", -30));
        assertEquals(0.5 * Playfield.GROUND_Y, wind.cy(), EPS);
        assertEquals(0, wind.accelY(), EPS);
        assertEquals(-30, wind.scrollDelta(), EPS);

        KindParams.LightningSpec bolt = (KindParams.LightningSpec) ObstacleParams.resolve(
                ObstacleKind.LIGHTNING, map("side", "TOP", "lengthFrac", 0.6));
        assertEquals(LightningStrike.DEFAULT_WARNING_TICKS, bolt.warningTicks());
        assertEquals(LightningStrike.DEFAULT_STRIKE_TICKS, bolt.strikeTicks());
        assertEquals(0.6, bolt.lengthFrac(), EPS);
    }

    @Test
    void resolveRefusesAnInvalidMapWithEveryProblem() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ObstacleParams.resolve(ObstacleKind.WIND_ZONE,
                        map("width", 10, "cy", 2, "height", 100)));
        assertTrue(e.getMessage().contains("width:"), e.getMessage());
        assertTrue(e.getMessage().contains("cy:"), e.getMessage());
    }
}
