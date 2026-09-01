package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.difficulty.DifficultyCurve;
import io.github.michelbr84.flapforge.gameplay.difficulty.DifficultyState;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.spec.CurveEntry;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DifficultyCurveTest {

    @Test
    void classicCurveValues() {
        DifficultyCurve curve = DifficultyCurve.CLASSIC;
        assertEquals(0.05, valueAt(curve, 0), 1e-12);
        assertEquals(0.55, valueAt(curve, 10), 1e-12);
        assertEquals(1.0, valueAt(curve, 19), 1e-12);
        assertEquals(1.0, valueAt(curve, 25), 1e-12);
        assertEquals(1, curve.at(0).size());
        assertEquals(StatOp.FLAT_ADD, curve.at(0).get(0).op());
        assertEquals("curve:classic", curve.at(0).get(0).source());
    }

    private static double valueAt(DifficultyCurve curve, int gates) {
        return curve.at(gates).get(0).value();
    }

    @Test
    void resolvedMovingChanceThroughTheSheet() {
        EffectStack stack = new EffectStack();
        StatSheet sheet = new StatSheet(Map.of(), stack, RuleSet.EMPTY);
        DifficultyState state = new DifficultyState(stack, DifficultyCurve.CLASSIC, List.of(),
                List.of(), DifficultyState.DEFAULT_SPEED_RAMP_PER_TICK, RuleSet.EMPTY);
        state.refresh(0, 0);
        assertEquals(0.05, sheet.resolve(StatId.MOVING_CHANCE), 1e-12);
        state.refresh(25, 5000);
        assertEquals(1.0, sheet.resolve(StatId.MOVING_CHANCE), 1e-12);
        assertEquals(120, sheet.resolve(StatId.SCROLL_SPEED), 0.0, "no ramp without the flag");
        assertFalse(state.needsTickRefresh());
    }

    @Test
    void curveEntriesClampAndApplyTheirOp() {
        CurveEntry scroll = new CurveEntry(StatId.SCROLL_SPEED, StatOp.MULTIPLY, 1.0, 0.004, 1.0, 1.5);
        assertEquals(1.0, scroll.valueAt(0), 0.0);
        assertEquals(1.4, scroll.valueAt(100), 1e-12);
        assertEquals(1.5, scroll.valueAt(1000), 0.0);
        CurveEntry gap = new CurveEntry(StatId.GAP_SIZE, StatOp.MULTIPLY, 1.0, -0.002, 0.8, 1.0);
        assertEquals(0.8, gap.valueAt(500), 1e-12);
        assertEquals(1.0, gap.valueAt(0), 0.0);
        StatModifier m = gap.at(50, "s");
        assertEquals(0.9, m.value(), 1e-12);
        assertEquals(StatOp.MULTIPLY, m.op());
    }

    @Test
    void tierAndWorldLayersArePushed() {
        EffectStack stack = new EffectStack();
        StatSheet sheet = new StatSheet(Map.of(), stack, RuleSet.EMPTY);
        List<StatModifier> tier = List.of(StatModifier.multiply(StatId.SCROLL_SPEED, 1.15, "hard"),
                StatModifier.multiply(StatId.GAP_SIZE, 0.9, "hard"));
        List<StatModifier> world = List.of(StatModifier.multiply(StatId.SCROLL_SPEED, 1.15, "storm"));
        DifficultyState state = new DifficultyState(stack, new DifficultyCurve(CurveSpec.FLAT), tier,
                world, 0.0005, RuleSet.EMPTY);
        state.refresh(0, 0);
        assertEquals(tier, stack.layer(Layer.TIER));
        assertEquals(world, stack.layer(Layer.WORLD));
        assertTrue(stack.layer(Layer.DIFFICULTY).isEmpty());
        assertEquals(120 * 1.15 * 1.15, sheet.resolve(StatId.SCROLL_SPEED), 1e-9);
        assertEquals(128 * 0.9, sheet.resolve(StatId.GAP_SIZE), 1e-9);
    }

    @Test
    void speedRampMultipliesScrollByTicksAlive() {
        EffectStack stack = new EffectStack();
        RuleSet rules = RuleSet.of(RuleFlag.SPEED_RAMP);
        StatSheet sheet = new StatSheet(Map.of(), stack, rules);
        DifficultyState state = new DifficultyState(stack, DifficultyCurve.CLASSIC, List.of(),
                List.of(), 0.0005, rules);
        assertTrue(state.needsTickRefresh());
        state.refresh(0, 0);
        assertEquals(120, sheet.resolve(StatId.SCROLL_SPEED), 1e-9);
        state.refresh(0, 1000);
        assertEquals(180, sheet.resolve(StatId.SCROLL_SPEED), 1e-9, "120 × (1 + 0.0005 × 1000)");
        state.refresh(3, 100_000);
        assertEquals(360, sheet.resolve(StatId.SCROLL_SPEED), 0.0, "clamped by the stat max");
        assertEquals(2, stack.layer(Layer.DIFFICULTY).size(), "curve entry + ramp");
    }

    @Test
    void runRefreshesTheRampEveryTick() {
        RunConfig config = RunConfig.builder(1).rules(RuleSet.of(RuleFlag.SPEED_RAMP)).build();
        Run run = Run.classic(config);
        run.simulation().spawner().setSuppressed(true);
        run.tick(RunInput.FLAP);
        for (int i = 0; i < 99; i++) {
            run.tick(RunInput.AUTO_FLAP); // hold-to-flap keeps the bird airborne
        }
        assertEquals(100, run.simulation().tick());
        assertEquals(120 * (1 + 0.0005 * 100), run.simulation().stats().resolve(StatId.SCROLL_SPEED),
                1e-9);
    }
}
