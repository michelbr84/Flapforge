package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.TierSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatBreakdown;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StatSheetTest {

    private static List<StatModifier> sampleModifiers() {
        List<StatModifier> mods = new ArrayList<>();
        mods.add(StatModifier.flat(StatId.GRAVITY, 100, "a"));
        mods.add(StatModifier.percent(StatId.GRAVITY, 0.10, "b"));
        mods.add(StatModifier.percent(StatId.GRAVITY, -0.05, "c"));
        mods.add(StatModifier.multiply(StatId.GRAVITY, 1.2, "d"));
        mods.add(StatModifier.flat(StatId.FLAP_VELOCITY, -30, "e"));
        mods.add(StatModifier.multiply(StatId.FLAP_VELOCITY, 1.1, "f"));
        mods.add(StatModifier.multiply(StatId.SCROLL_SPEED, 1.15, "g"));
        mods.add(StatModifier.multiply(StatId.SCROLL_SPEED, 1.3, "h"));
        mods.add(StatModifier.percent(StatId.COIN_MULT, 0.3, "i"));
        mods.add(StatModifier.percent(StatId.COIN_MULT, -0.2, "j"));
        mods.add(StatModifier.flat(StatId.SHIELD_CHARGES, 1, "k"));
        mods.add(StatModifier.flat(StatId.REVIVES, 1, "l"));
        mods.add(StatModifier.multiply(StatId.GAP_SIZE, 0.9, "m"));
        mods.add(StatModifier.flat(StatId.HITBOX_SCALE, 0.1, "n"));
        mods.add(StatModifier.percent(StatId.SCORE_MULT, 0.3, "o"));
        return mods;
    }

    private static StatSheet sheetOf(List<StatModifier> ordered) {
        EffectStack stack = new EffectStack();
        Layer[] layers = Layer.values();
        Map<Layer, List<StatModifier>> byLayer = new EnumMap<>(Layer.class);
        for (int i = 0; i < ordered.size(); i++) {
            byLayer.computeIfAbsent(layers[i % layers.length], l -> new ArrayList<>())
                    .add(ordered.get(i));
        }
        byLayer.forEach(stack::setLayer);
        return new StatSheet(Map.of(), stack, RuleSet.EMPTY);
    }

    @Test
    void pipelineFormula() {
        StatSheet sheet = sheetOf(sampleModifiers());
        assertEquals((1800 + 100) * (1 + 0.10 - 0.05) * 1.2, sheet.resolve(StatId.GRAVITY), 1e-9);
        assertEquals((405 - 30) * 1.1, sheet.resolve(StatId.FLAP_VELOCITY), 1e-9);
        assertEquals(120 * 1.15 * 1.3, sheet.resolve(StatId.SCROLL_SPEED), 1e-9);
        assertEquals(1 * (1 + 0.3 - 0.2), sheet.resolve(StatId.COIN_MULT), 1e-9);
        assertEquals(1500, sheet.resolve(StatId.MAX_FALL_SPEED), 0.0, "untouched stat");
    }

    @Test
    void shuffledOrderAndLayersGiveIdenticalResults() {
        List<StatModifier> mods = sampleModifiers();
        StatSheet reference = sheetOf(mods);
        double[] expected = new double[StatId.COUNT];
        for (StatId id : StatId.values()) {
            expected[id.ordinal()] = reference.resolve(id);
        }
        Random random = new Random(42);
        for (int round = 0; round < 100; round++) {
            List<StatModifier> shuffled = new ArrayList<>(mods);
            Collections.shuffle(shuffled, random);
            StatSheet sheet = sheetOf(shuffled);
            for (StatId id : StatId.values()) {
                assertEquals(expected[id.ordinal()], sheet.resolve(id), 1e-9,
                        "round " + round + " " + id);
            }
        }
    }

    @Test
    void clampsToTheStatRange() {
        EffectStack stack = new EffectStack();
        stack.setLayer(Layer.MODIFIERS, List.of(
                StatModifier.flat(StatId.GRAVITY, 10_000, "x"),
                StatModifier.multiply(StatId.SCROLL_SPEED, 5, "dash"),
                StatModifier.flat(StatId.GAP_SIZE, -500, "y"),
                StatModifier.percent(StatId.COIN_MULT, -3, "z")));
        StatSheet sheet = new StatSheet(Map.of(), stack, RuleSet.EMPTY);
        assertEquals(4000, sheet.resolve(StatId.GRAVITY), 0.0);
        assertEquals(360, sheet.resolve(StatId.SCROLL_SPEED), 0.0);
        assertEquals(72, sheet.resolve(StatId.GAP_SIZE), 0.0);
        assertEquals(0, sheet.resolve(StatId.COIN_MULT), 0.0);
        StatBreakdown b = sheet.breakdown(StatId.GRAVITY);
        assertTrue(b.clamped());
        assertEquals(11_800, b.unclamped(), 0.0);
    }

    @Test
    void rulesZeroTheirStats() {
        EffectStack stack = new EffectStack();
        stack.setLayer(Layer.UPGRADES, List.of(
                StatModifier.flat(StatId.SHIELD_CHARGES, 2, "shield_node"),
                StatModifier.flat(StatId.REVIVES, 1, "second_chance")));
        StatSheet sheet = new StatSheet(Map.of(), stack, RuleSet.EMPTY);
        assertEquals(2, sheet.resolve(StatId.SHIELD_CHARGES), 0.0);
        assertEquals(1, sheet.resolve(StatId.REVIVES), 0.0);
        sheet.setRules(RuleSet.of(RuleFlag.NO_REVIVE));
        assertEquals(2, sheet.resolve(StatId.SHIELD_CHARGES), 0.0);
        assertEquals(0, sheet.resolve(StatId.REVIVES), 0.0);
        sheet.setRules(RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES, RuleFlag.NO_REVIVE));
        assertEquals(0, sheet.resolve(StatId.SHIELD_CHARGES), 0.0);
        assertEquals(0, sheet.resolve(StatId.REVIVES), 0.0);
        assertTrue(sheet.breakdown(StatId.REVIVES).zeroedByRule());
        assertEquals(1, sheet.breakdown(StatId.REVIVES).contributions().size());
        assertFalse(RuleSet.EMPTY.zeroes(StatId.REVIVES));
        assertTrue(RuleSet.of(RuleFlag.NO_REVIVE).union(RuleSet.EMPTY).zeroes(StatId.REVIVES));
    }

    @Test
    void breakdownListsEveryContributor() {
        StatSheet sheet = sheetOf(sampleModifiers());
        StatBreakdown b = sheet.breakdown(StatId.GRAVITY);
        assertEquals(1800, b.base(), 0.0);
        assertEquals(4, b.contributions().size());
        assertEquals(100, b.flatSum(), 1e-12);
        assertEquals(0.05, b.percentSum(), 1e-12);
        assertEquals(1.2, b.multiplyProduct(), 1e-12);
        assertEquals(sheet.resolve(StatId.GRAVITY), b.value(), 0.0);
        assertFalse(b.clamped());
        List<String> sources = new ArrayList<>();
        for (EffectStack.Entry e : b.contributions()) {
            sources.add(e.modifier().source());
        }
        assertEquals(List.of("a", "b", "c", "d"), sources);
        Layer previous = null;
        for (EffectStack.Entry e : b.contributions()) {
            assertTrue(previous == null || e.layer().compareTo(previous) >= 0, "layer order");
            previous = e.layer();
        }
    }

    @Test
    void baseStatsComeFromTheBirdAndDefaultOtherwise() {
        StatSheet sheet = new StatSheet(Map.of(StatId.FLAP_VELOCITY, 470.0), new EffectStack(),
                RuleSet.EMPTY);
        assertEquals(470, sheet.resolve(StatId.FLAP_VELOCITY), 0.0);
        assertEquals(470, sheet.base(StatId.FLAP_VELOCITY), 0.0);
        assertEquals(StatId.GRAVITY.defaultValue(), sheet.resolve(StatId.GRAVITY), 0.0);
        for (StatId id : StatId.values()) {
            assertTrue(id.defaultValue() >= id.min() && id.defaultValue() <= id.max(), id.name());
        }
    }

    @Test
    void cacheFollowsTheStackVersion() {
        EffectStack stack = new EffectStack();
        StatSheet sheet = new StatSheet(Map.of(), stack, RuleSet.EMPTY);
        assertEquals(1800, sheet.resolve(StatId.GRAVITY), 0.0);
        long v0 = stack.version();
        stack.setLayer(Layer.ABILITY, List.of(StatModifier.multiply(StatId.GRAVITY, 0.5, "dash")));
        assertTrue(stack.version() > v0);
        assertEquals(900, sheet.resolve(StatId.GRAVITY), 0.0);
        long v1 = stack.version();
        stack.setLayer(Layer.ABILITY, List.of(StatModifier.multiply(StatId.GRAVITY, 0.5, "dash")));
        assertEquals(v1, stack.version(), "identical content does not bump the version");
        stack.clearLayer(Layer.ABILITY);
        assertEquals(1800, sheet.resolve(StatId.GRAVITY), 0.0);
        assertEquals(0, stack.size());
    }

    @Test
    void statModifierRejectsNonFiniteValues() {
        boolean thrown = false;
        try {
            new StatModifier(StatId.GRAVITY, StatOp.FLAT_ADD, Double.NaN, "x");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    /**
     * The seam records must hash to a <em>specified</em> value: {@link Enum#hashCode()} is an
     * identity hash, so an {@code EnumSet}- or {@code EnumMap}-derived hash changes from JVM to
     * JVM (measured: the same two-flag set hashed 2092173651 on one JDK and -2024069911 on
     * another). The simulation does not hash them today, but the moment one becomes a map key a
     * JDK-dependent iteration order would break the cross-OS determinism guarantee.
     */
    @Test
    void ruleSetsAndSeamRecordsHashByValueNotByIdentity() {
        assertEquals(0, RuleSet.EMPTY.hashCode());
        assertEquals(4, RuleSet.of(RuleFlag.ALL_OBSTACLES_MOVE).hashCode(),
                "1 << ordinal(ALL_OBSTACLES_MOVE)");
        RuleSet two = RuleSet.of(RuleFlag.ALL_OBSTACLES_MOVE, RuleFlag.LETHAL_CEILING);
        assertEquals(12, two.hashCode(),
                "the mask is (1 << 2) | (1 << 3); the old EnumSet sum was JDK dependent");
        assertEquals(two.hashCode(),
                RuleSet.of(RuleFlag.LETHAL_CEILING, RuleFlag.ALL_OBSTACLES_MOVE).hashCode());

        assertEquals(new StatModifier(StatId.GRAVITY, StatOp.FLAT_ADD, 100, "a").hashCode(),
                StatModifier.flat(StatId.GRAVITY, 100, "a").hashCode());
        assertEquals(RunSetup.CLASSIC.hashCode(),
                new RunSetup(BirdProfile.CLASSIC, WorldSpec.GREEN_FIELDS, TierSpec.NORMAL,
                        RunSetup.CLASSIC.speedRampPerTick()).hashCode(),
                "equal setups hash equal through the whole record chain");
        assertEquals(BirdProfile.CLASSIC.hashCode(),
                new BirdProfile("classic", new EnumMap<>(BirdProfile.CLASSIC.baseStats()),
                        BirdProfile.CLASSIC.hitbox(), List.of(), List.of(), 2).hashCode(),
                "a different map instance with the same content hashes the same");
    }
}
