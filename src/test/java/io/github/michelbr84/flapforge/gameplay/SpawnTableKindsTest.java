package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.KindParams;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnDecision;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The M7 spawn table: every kind rolls from the specified streams and materialises. */
class SpawnTableKindsTest {

    private static final double EPS = 1e-9;

    /**
     * The first 50 decisions of seed 42 as the M6 code drew them ({@code rollFirst}, then 49
     * {@code roll}s at {@code MOVING_CHANCE 0.5}), recorded before the other families existed:
     * S/F = standard/floating, m/s = moving/static, then {@code top} or {@code y/h}.
     */
    private static final String GREEN_FIELDS_SEED_42 = "Ss313 Sm276 Fs99/149 Sm180 Sm387 Sm249 "
            + "Ss240 Sm150 Fs75/128 Sm253 Ss302 Sm187 Ss230 Fs60/142 Sm332 Fs90/113 Sm298 Sm157 "
            + "Ss139 Sm233 Fs77/156 Sm190 Ss98 Sm223 Sm168 Fs83/134 Sm266 Fs71/154 Fs101/147 "
            + "Sm122 Sm168 Ss252 Fm71/150 Fs71/123 Fs72/127 Ss193 Fs62/110 Fs67/112 Sm324 "
            + "Fm72/141 Sm144 Sm372 Fs82/116 Sm326 Sm359 Sm278 Fs64/136 Sm329 Sm395 Fs60/135";
    /** The fold of those 50 decisions from the seed {@code 0x12345}, recorded with the M6 code. */
    private static final long GREEN_FIELDS_SEED_42_FOLD = 0x6b0ac49584a70a42L;

    private static SpawnTable only(ObstacleKind kind) {
        return new SpawnTable(Map.of(kind, 100));
    }

    private static String fmt(SpawnDecision d) {
        boolean standard = d.layout() == PipeGate.Layout.STANDARD;
        return (standard ? "S" : "F") + (d.moving() ? "m" : "s")
                + (int) (standard ? d.top() : d.floatY()) + (standard ? "" : "/" + (int) d.floatH());
    }

    @Test
    void greenFieldsDecisionsAreBitIdenticalToTheM6Sequence() {
        Random spawn = new RandomProvider(42).stream(RandomProvider.SPAWN);
        Random obstacle = new RandomProvider(42).stream(RandomProvider.OBSTACLE);
        List<String> seen = new ArrayList<>();
        long fold = 0x12345L;
        SpawnDecision d = SpawnTable.GREEN_FIELDS.rollFirst(obstacle);
        seen.add(fmt(d));
        fold = d.fold(fold);
        for (int i = 1; i < 50; i++) {
            d = SpawnTable.GREEN_FIELDS.roll(spawn, obstacle, 0.5);
            assertNull(d.params(), "a stream gate carries no typed params");
            seen.add(fmt(d));
            fold = d.fold(fold);
        }
        assertEquals(GREEN_FIELDS_SEED_42, String.join(" ", seen));
        assertEquals(GREEN_FIELDS_SEED_42_FOLD, fold, "the decision fold is unchanged too");
    }

    @Test
    void theFirstObstacleIsAStandardGateInEveryWorld() {
        SpawnTable mixed = new SpawnTable(Map.of(ObstacleKind.PIPE_GATE, 40, ObstacleKind.GEAR, 20,
                ObstacleKind.PISTON, 20, ObstacleKind.WIND_ZONE, 10, ObstacleKind.LIGHTNING, 10));
        for (long seed = 1; seed <= 50; seed++) {
            SpawnDecision first = mixed.rollFirst(new Random(seed));
            assertEquals(ObstacleKind.PIPE_GATE, first.kind());
            assertEquals(PipeGate.Layout.STANDARD, first.layout());
            assertFalse(first.moving());
        }
        for (ObstacleKind kind : ObstacleKind.values()) {
            assertEquals(ObstacleKind.PIPE_GATE, only(kind).rollFirst(new Random(7)).kind());
        }
    }

    @Test
    void gearRollsTheRailFromSpawnAndTheGeometryFromObstacle() {
        Random spawn = new Random(11);
        Random obstacle = new Random(12);
        Random refSpawn = new Random(11);
        Random refObstacle = new Random(12);
        for (int i = 0; i < 200; i++) {
            SpawnDecision d = only(ObstacleKind.GEAR).roll(spawn, obstacle, 0.4);
            boolean rail = refSpawn.nextDouble() < 0.4;
            int radius = SpawnTable.GEAR_RADIUS_MIN + refObstacle.nextInt(
                    SpawnTable.GEAR_RADIUS_MAX - SpawnTable.GEAR_RADIUS_MIN + 1);
            int lo = SpawnTable.EDGE_CLEARANCE_PX + radius + 30;
            int hi = Playfield.GROUND_Y - SpawnTable.EDGE_CLEARANCE_PX - radius - 30;
            int cy = lo + refObstacle.nextInt(hi - lo + 1);
            KindParams.GearSpec g = (KindParams.GearSpec) d.params();
            assertEquals(ObstacleKind.GEAR, d.kind());
            assertEquals(rail, d.moving());
            assertEquals(rail, g.hasRail());
            assertEquals(radius, g.radius(), EPS);
            assertEquals(cy, g.cy(), EPS);
            assertTrue(cy - 30 - radius >= SpawnTable.EDGE_CLEARANCE_PX, "clear of the ceiling");
            assertTrue(cy + 30 + radius <= Playfield.GROUND_Y - SpawnTable.EDGE_CLEARANCE_PX,
                    "clear of the ground");
        }
    }

    @Test
    void pistonRollsSideLengthAndOffsetFromObstacleOnly() {
        Random spawn = new Random(21);
        Random obstacle = new Random(22);
        Random refObstacle = new Random(22);
        double spawnFirst = new Random(21).nextDouble();
        for (int i = 0; i < 200; i++) {
            SpawnDecision d = only(ObstacleKind.PISTON).roll(spawn, obstacle, 0.9);
            Side side = refObstacle.nextInt(2) == 0 ? Side.TOP : Side.BOTTOM;
            int length = SpawnTable.PISTON_LENGTH_MIN + refObstacle.nextInt(
                    SpawnTable.PISTON_LENGTH_MAX - SpawnTable.PISTON_LENGTH_MIN + 1);
            int offset = refObstacle.nextInt(Piston.DEFAULT_CYCLE_TICKS);
            KindParams.PistonSpec p = (KindParams.PistonSpec) d.params();
            assertEquals(side, p.side());
            assertEquals(length, p.length(), EPS);
            assertEquals(offset, p.phaseOffset());
            assertEquals(Piston.DEFAULT_TELEGRAPH_TICKS, p.telegraphTicks());
            assertEquals(Piston.DEFAULT_EXTEND_TICKS, p.extendTicks());
            assertEquals(Piston.DEFAULT_HOLD_TICKS, p.holdTicks());
            assertEquals(Piston.DEFAULT_RETRACT_TICKS, p.retractTicks());
        }
        assertEquals(spawnFirst, spawn.nextDouble(), 0.0, "the spawn stream was never touched");
    }

    @Test
    void windRollsGeometryThenOneOfFourEffectsFromObstacle() {
        Random spawn = new Random(31);
        Random obstacle = new Random(32);
        Random refObstacle = new Random(32);
        int[] effects = new int[4];
        for (int i = 0; i < 400; i++) {
            SpawnDecision d = only(ObstacleKind.WIND_ZONE).roll(spawn, obstacle, 0.9);
            int width = SpawnTable.WIND_WIDTH_MIN + refObstacle.nextInt(
                    SpawnTable.WIND_WIDTH_MAX - SpawnTable.WIND_WIDTH_MIN + 1);
            int height = SpawnTable.WIND_HEIGHT_MIN + refObstacle.nextInt(
                    SpawnTable.WIND_HEIGHT_MAX - SpawnTable.WIND_HEIGHT_MIN + 1);
            int cy = SpawnTable.WIND_CY_MIN + refObstacle.nextInt(
                    SpawnTable.WIND_CY_MAX - SpawnTable.WIND_CY_MIN + 1);
            int effect = refObstacle.nextInt(4);
            effects[effect]++;
            KindParams.WindSpec w = (KindParams.WindSpec) d.params();
            assertEquals(width, w.width(), EPS);
            assertEquals(height, w.height(), EPS);
            assertEquals(cy, w.cy(), EPS);
            double accel = effect == 0 ? -500 : effect == 1 ? 500 : 0;
            double scroll = effect == 2 ? -40 : effect == 3 ? 40 : 0;
            assertEquals(accel, w.accelY(), EPS);
            assertEquals(scroll, w.scrollDelta(), EPS);
            assertTrue(cy - height / 2.0 >= 0 && cy + height / 2.0 <= Playfield.GROUND_Y,
                    "inside the playfield");
        }
        for (int e = 0; e < 4; e++) {
            assertTrue(effects[e] > 60, "effect " + e + " drawn " + effects[e]);
        }
    }

    @Test
    void lightningRollsSideAndFractionFromObstacle() {
        Random spawn = new Random(41);
        Random obstacle = new Random(42);
        Random refObstacle = new Random(42);
        for (int i = 0; i < 200; i++) {
            SpawnDecision d = only(ObstacleKind.LIGHTNING).roll(spawn, obstacle, 0.9);
            Side side = refObstacle.nextInt(2) == 0 ? Side.TOP : Side.BOTTOM;
            int pct = SpawnTable.LIGHTNING_FRAC_MIN_PCT + refObstacle.nextInt(
                    SpawnTable.LIGHTNING_FRAC_MAX_PCT - SpawnTable.LIGHTNING_FRAC_MIN_PCT + 1);
            KindParams.LightningSpec l = (KindParams.LightningSpec) d.params();
            assertEquals(side, l.side());
            assertEquals(pct / 100.0, l.lengthFrac(), 0.0);
            assertTrue(l.lengthFrac() <= LightningStrike.MAX_LENGTH_FRAC);
            assertEquals(LightningStrike.TABLE_WARNING_TICKS, l.warningTicks(),
                    "a table bolt warns from further out than an authored one");
            assertEquals(LightningStrike.DEFAULT_STRIKE_TICKS, l.strikeTicks());
        }
    }

    @Test
    void mixedTableDrawsTheKindFromSpawnWithTheWorldWeights() {
        Map<ObstacleKind, Integer> weights = new EnumMap<>(ObstacleKind.class);
        weights.put(ObstacleKind.PIPE_GATE, 40);
        weights.put(ObstacleKind.GEAR, 20);
        weights.put(ObstacleKind.PISTON, 20);
        weights.put(ObstacleKind.WIND_ZONE, 10);
        weights.put(ObstacleKind.LIGHTNING, 10);
        SpawnTable table = new SpawnTable(weights);
        Random spawn = new Random(5);
        Random obstacle = new Random(6);
        EnumMap<ObstacleKind, Integer> seen = new EnumMap<>(ObstacleKind.class);
        int n = 4000;
        for (int i = 0; i < n; i++) {
            seen.merge(table.roll(spawn, obstacle, 0.3).kind(), 1, Integer::sum);
        }
        for (ObstacleKind kind : ObstacleKind.values()) {
            assertEquals(weights.get(kind) / 100.0, seen.getOrDefault(kind, 0) / (double) n, 0.03,
                    kind.toString());
        }
    }

    @Test
    void theDecisionHashChangesWhenAnyParameterChanges() {
        KindParams.GearSpec gear = new KindParams.GearSpec(300, 40, 60, 40);
        assertDistinct(SpawnDecision.gear(gear),
                SpawnDecision.gear(new KindParams.GearSpec(301, 40, 60, 40)),
                SpawnDecision.gear(new KindParams.GearSpec(300, 41, 60, 40)),
                SpawnDecision.gear(new KindParams.GearSpec(300, 40, 61, 40)),
                SpawnDecision.gear(new KindParams.GearSpec(300, 40, 60, 41)),
                SpawnDecision.gear(new KindParams.GearSpec(300, 40, 0, 0)));
        KindParams.PistonSpec piston = new KindParams.PistonSpec(Side.TOP, 200, 40, 12, 30, 20, 0);
        assertDistinct(SpawnDecision.piston(piston),
                SpawnDecision.piston(new KindParams.PistonSpec(Side.BOTTOM, 200, 40, 12, 30, 20, 0)),
                SpawnDecision.piston(new KindParams.PistonSpec(Side.TOP, 201, 40, 12, 30, 20, 0)),
                SpawnDecision.piston(new KindParams.PistonSpec(Side.TOP, 200, 41, 12, 30, 20, 0)),
                SpawnDecision.piston(new KindParams.PistonSpec(Side.TOP, 200, 40, 13, 30, 20, 0)),
                SpawnDecision.piston(new KindParams.PistonSpec(Side.TOP, 200, 40, 12, 31, 20, 0)),
                SpawnDecision.piston(new KindParams.PistonSpec(Side.TOP, 200, 40, 12, 30, 21, 0)),
                SpawnDecision.piston(new KindParams.PistonSpec(Side.TOP, 200, 40, 12, 30, 20, 1)));
        KindParams.WindSpec wind = new KindParams.WindSpec(120, 300, 240, -500, 0);
        assertDistinct(SpawnDecision.wind(wind),
                SpawnDecision.wind(new KindParams.WindSpec(121, 300, 240, -500, 0)),
                SpawnDecision.wind(new KindParams.WindSpec(120, 301, 240, -500, 0)),
                SpawnDecision.wind(new KindParams.WindSpec(120, 300, 241, -500, 0)),
                SpawnDecision.wind(new KindParams.WindSpec(120, 300, 240, 500, 0)),
                SpawnDecision.wind(new KindParams.WindSpec(120, 300, 240, -500, 40)));
        KindParams.LightningSpec bolt = new KindParams.LightningSpec(Side.TOP, 0.5, 45, 10);
        assertDistinct(SpawnDecision.lightning(bolt),
                SpawnDecision.lightning(new KindParams.LightningSpec(Side.BOTTOM, 0.5, 45, 10)),
                SpawnDecision.lightning(new KindParams.LightningSpec(Side.TOP, 0.51, 45, 10)),
                SpawnDecision.lightning(new KindParams.LightningSpec(Side.TOP, 0.5, 46, 10)),
                SpawnDecision.lightning(new KindParams.LightningSpec(Side.TOP, 0.5, 45, 11)));
        KindParams.GateSpec gate = new KindParams.GateSpec(PipeGate.Layout.STANDARD, 0.5, 128,
                true, 51, 30);
        Random none = new Random(1);
        assertDistinct(SpawnTable.decisionFor(gate, none),
                SpawnTable.decisionFor(new KindParams.GateSpec(PipeGate.Layout.STANDARD, 0.5, 130,
                        true, 51, 30), none),
                SpawnTable.decisionFor(new KindParams.GateSpec(PipeGate.Layout.STANDARD, 0.5, 128,
                        true, 52, 30), none),
                SpawnTable.decisionFor(new KindParams.GateSpec(PipeGate.Layout.STANDARD, 0.5, 128,
                        true, 51, 31), none),
                SpawnTable.decisionFor(new KindParams.GateSpec(PipeGate.Layout.STANDARD, 0.5, 128,
                        false, 51, 30), none));
    }

    private static void assertDistinct(SpawnDecision... decisions) {
        long seed = 0x9876L;
        for (int i = 0; i < decisions.length; i++) {
            for (int j = i + 1; j < decisions.length; j++) {
                assertNotEquals(decisions[i].fold(seed), decisions[j].fold(seed),
                        decisions[i] + " vs " + decisions[j]);
            }
        }
    }

    @Test
    void materializeBuildsEveryKindFromItsDecision() {
        Random spawn = new Random(1);
        Random obstacle = new Random(2);
        Obstacle gear = only(ObstacleKind.GEAR).materialize(
                only(ObstacleKind.GEAR).roll(spawn, obstacle, 1.0), 420, 128);
        assertTrue(gear instanceof Gear);
        assertTrue(gear.isScoring(), "a gear takes a gate's slot in the cadence and scores");
        assertTrue(((Gear) gear).isMoving());
        assertEquals(420, gear.x(), EPS);

        Obstacle piston = only(ObstacleKind.PISTON).materialize(
                only(ObstacleKind.PISTON).roll(spawn, obstacle, 0), 420, 128);
        assertTrue(piston instanceof Piston);
        assertTrue(piston.isScoring());

        Obstacle wind = only(ObstacleKind.WIND_ZONE).materialize(
                only(ObstacleKind.WIND_ZONE).roll(spawn, obstacle, 0), 420, 128);
        assertTrue(wind instanceof WindZone);
        assertFalse(wind.isScoring(), "wind never scores");
        assertFalse(wind.lethal());

        Obstacle bolt = only(ObstacleKind.LIGHTNING).materialize(
                only(ObstacleKind.LIGHTNING).roll(spawn, obstacle, 0), 420, 128);
        assertTrue(bolt instanceof LightningStrike);
        assertTrue(bolt.isScoring());
        assertEquals(LightningStrike.State.IDLE, ((LightningStrike) bolt).state());
    }

    /**
     * M7 fairness: a spawn-table bolt is reachable from the column before it. Over every gate
     * geometry the table can draw and every rolled bolt, the side is the one whose unlit band is
     * nearer the previous decision's reference band and the lit fraction is shortened until the
     * travel from that band fits {@link SpawnTable#LIGHTNING_MAX_TRAVEL_PX}; the streams are
     * consumed exactly as without the rule, and a bolt with no previous column is untouched.
     */
    @Test
    void aTableBoltIsAlwaysReachableFromThePreviousColumnsBand() {
        SpawnTable gates = SpawnTable.GREEN_FIELDS;
        SpawnTable bolts = only(ObstacleKind.LIGHTNING);
        Random spawn = new Random(51);
        Random obstacle = new Random(52);
        Random ref = null;
        int swapped = 0;
        int shortened = 0;
        double worst = 0;
        for (int i = 0; i < 2000; i++) {
            SpawnDecision gate = gates.roll(spawn, obstacle, 0.5);
            // Fork a reference stream at the bolt's draw so the roll can be replayed.
            long fork = obstacle.nextLong();
            obstacle.setSeed(fork);
            ref = new Random(fork);
            double band = gate.referenceBandY();
            SpawnDecision bolt = bolts.roll(spawn, obstacle, 0.5, band);
            Side rolledSide = ref.nextInt(2) == 0 ? Side.TOP : Side.BOTTOM;
            double rolledFrac = (SpawnTable.LIGHTNING_FRAC_MIN_PCT + ref.nextInt(31)) / 100.0;
            KindParams.LightningSpec l = (KindParams.LightningSpec) bolt.params();
            double travel = SpawnTable.lightningTravel(l.side(), l.lengthFrac(), band);
            assertTrue(travel <= SpawnTable.LIGHTNING_MAX_TRAVEL_PX + EPS,
                    "gate band " + band + " then " + l + ": travel " + travel);
            assertTrue(l.lengthFrac() >= LightningStrike.MIN_LENGTH_FRAC - EPS);
            assertTrue(l.lengthFrac() <= rolledFrac + EPS, "only ever shortened");
            assertTrue(SpawnTable.lightningTravel(l.side(), l.lengthFrac(), band)
                    <= SpawnTable.lightningTravel(l.side() == Side.TOP ? Side.BOTTOM : Side.TOP,
                            l.lengthFrac(), band) + EPS, "the nearer side");
            worst = Math.max(worst, travel);
            swapped += l.side() != rolledSide ? 1 : 0;
            shortened += l.lengthFrac() < rolledFrac - EPS ? 1 : 0;
            assertEquals(LightningStrike.TABLE_WARNING_TICKS, l.warningTicks());
        }
        assertTrue(swapped > 100, "the rule bites on the far side: " + swapped);
        assertTrue(shortened > 50, "and shortens long bolts: " + shortened);
        assertTrue(worst <= SpawnTable.LIGHTNING_MAX_TRAVEL_PX);
        // The streams were consumed exactly as before the rule existed, so they stay aligned.
        assertEquals(ref.nextDouble(), obstacle.nextDouble(), 0.0);

        Random a = new Random(9);
        Random b = new Random(9);
        SpawnDecision free = bolts.roll(new Random(1), a, 0.5);
        SpawnDecision plain = bolts.roll(new Random(1), b, 0.5, Double.NaN);
        assertEquals(free.params(), plain.params(), "no previous column: the roll stands");
    }

    /**
     * E32.d for gears and pistons: the rule {@code ALL_OBSTACLES_MOVE} never enters a decision
     * — a gear rolled at chance 1.0 rides its rail with or without the rule, a pattern gear or
     * piston is the same decision under the rule, and only {@link SpawnTable#materialize}
     * applies it.
     */
    @Test
    void theRuleIsAppliedToGearsAndPistonsAtMaterialisationOnly() {
        SpawnTable gears = only(ObstacleKind.GEAR);
        Random s1 = new Random(5);
        Random o1 = new Random(6);
        Random s2 = new Random(5);
        Random o2 = new Random(6);
        for (int i = 0; i < 100; i++) {
            SpawnDecision a = gears.roll(s1, o1, 1.0);
            SpawnDecision b = gears.roll(s2, o2, 1.0);
            assertEquals(a, b);
            assertTrue(a.moving(), "chance 1.0 rolls a rail");
            Gear ruled = (Gear) gears.materialize(a, 420, 128, true);
            Gear free = (Gear) gears.materialize(b, 420, 128, false);
            assertEquals(free.railAmplitude(), ruled.railAmplitude(), EPS);
            assertEquals(free.cy(), ruled.cy(), EPS);
        }
        assertEquals(s2.nextDouble(), s1.nextDouble(), 0.0, "the spawn streams are aligned");
        assertEquals(o2.nextDouble(), o1.nextDouble(), 0.0, "and so are the obstacle streams");

        KindParams.GearSpec fixedGear = new KindParams.GearSpec(300, 36, 0, 0);
        SpawnDecision gearStep = SpawnTable.decisionFor(fixedGear, new Random(1));
        assertEquals(gearStep, SpawnTable.decisionFor(fixedGear, new Random(1)));
        assertFalse(gearStep.moving());
        assertTrue(((Gear) gears.materialize(gearStep, 420, 128, true)).isMoving(), "rule → rail");
        assertFalse(((Gear) gears.materialize(gearStep, 420, 128, false)).isMoving());

        KindParams.PistonSpec piston = new KindParams.PistonSpec(Side.TOP, 200, 40, 12, 30, 20, 0);
        SpawnDecision pistonStep = SpawnTable.decisionFor(piston, new Random(1));
        assertEquals(40, ((KindParams.PistonSpec) pistonStep.params()).telegraphTicks());
        assertEquals(25, ((Piston) only(ObstacleKind.PISTON).materialize(pistonStep, 420, 128,
                true)).telegraphTicks());
        assertEquals(40, ((Piston) only(ObstacleKind.PISTON).materialize(pistonStep, 420, 128,
                false)).telegraphTicks());
    }

    @Test
    void patternGatesResolveTheirGapCentreOrRollItFromThePatternsStream() {
        Random patterns = new Random(9);
        KindParams.GateSpec authored = new KindParams.GateSpec(PipeGate.Layout.STANDARD, 0.45, 128,
                false, 51, 0);
        SpawnDecision d = SpawnTable.decisionFor(authored, patterns);
        assertEquals(Math.round(0.45 * Playfield.GROUND_Y - 64), d.top(), EPS,
                "centred on 45 % of the playable height with the authored 128 gap");
        PipeGate gate = (PipeGate) SpawnTable.GREEN_FIELDS.materialize(d, 420, 128);
        assertEquals(128, gate.gap(), EPS, "the authored gap at the default GAP_SIZE");
        assertFalse(gate.isMoving());
        // The authored gap is a base value: the run's gap multiplier scales it (a tier's ×0.8,
        // the curve's ramp, a cycle option, a card), and the gap stays centred where authored.
        PipeGate tight = (PipeGate) SpawnTable.GREEN_FIELDS.materialize(d, 420, 100);
        assertEquals(100, tight.gap(), EPS, "128 × 100 / 128");
        assertEquals(gate.gapCenterY(), tight.gapCenterY(), EPS, "the centre does not move");
        PipeGate wide = (PipeGate) SpawnTable.GREEN_FIELDS.materialize(d, 420, 160);
        assertEquals(160, wide.gap(), EPS);
        assertEquals(gate.gapCenterY(), wide.gapCenterY(), EPS);
        // A random centre keeps its rolled top like a table gate: the gap scales from it.
        SpawnDecision randomTop = SpawnTable.decisionFor(new KindParams.GateSpec(
                PipeGate.Layout.STANDARD, Double.NaN, 128, false, 51, 0), new Random(3));
        PipeGate rolledTight = (PipeGate) SpawnTable.GREEN_FIELDS.materialize(randomTop, 420, 100);
        assertEquals(randomTop.top(), rolledTight.baseGapTopY(), EPS);
        assertEquals(100, rolledTight.gap(), EPS);

        KindParams.GateSpec statGap = new KindParams.GateSpec(PipeGate.Layout.STANDARD, 0.9, 0,
                true, 80, 60);
        SpawnDecision clamped = SpawnTable.decisionFor(statGap, patterns);
        assertEquals(SpawnTable.STANDARD_TOP_MAX, clamped.top(), EPS, "clamped into [80, 400]");
        PipeGate moving = (PipeGate) SpawnTable.GREEN_FIELDS.materialize(clamped, 420, 128);
        assertEquals(128, moving.gap(), EPS, "no gapSize: the resolved GAP_SIZE");
        assertTrue(moving.isMoving());
        assertEquals(80, moving.oscillator().orElseThrow().amplitude(), EPS);
        assertEquals(60, moving.oscillationSpeed(), EPS);

        KindParams.GateSpec random = new KindParams.GateSpec(PipeGate.Layout.STANDARD, Double.NaN,
                0, false, 51, 0);
        Random ref = new Random(9);
        patterns = new Random(9);
        SpawnDecision rolled = SpawnTable.decisionFor(random, patterns);
        assertEquals(SpawnTable.STANDARD_TOP_MIN + ref.nextInt(321), rolled.top(), EPS);

        KindParams.GateSpec floating = new KindParams.GateSpec(PipeGate.Layout.FLOATING,
                Double.NaN, 0, false, 51, 0);
        SpawnDecision rolledFloating = SpawnTable.decisionFor(floating, patterns);
        assertEquals(SpawnTable.FLOATING_H_MIN + ref.nextInt(54), rolledFloating.floatH(), EPS);
        assertEquals(SpawnTable.FLOATING_Y_MIN + ref.nextInt(53), rolledFloating.floatY(), EPS);
        assertEquals(PipeGate.Layout.FLOATING, rolledFloating.layout());
    }
}
