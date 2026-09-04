package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.BirdPhysics;
import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.harness.Oracles;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.CurveSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.support.FixedSpawnTable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D21: each oracle predicts its hazard at the crossing tick on a synthetic layout. */
class BotOracleTest {

    private static final double SCROLL = 2;
    private static final double EPS = 1e-9;
    private static final HitboxSpec SPEC = HitboxSpec.CLASSIC;

    private static Bird birdAt(double y) {
        Bird bird = new Bird(SPEC, y);
        bird.beginTick();
        return bird;
    }

    @Test
    void theCrossingWindowCountsTheTicksTheColumnOverlapsTheBird() {
        Bird bird = birdAt(320);
        // Box spans x [88, 121]; a 40 px column at 181 enters after 30 ticks and leaves at 66.
        Piston piston = Piston.standard(181, Side.TOP, 200, 0);
        Oracles.Window window = Oracles.crossingWindow(piston, bird.hitbox(), SCROLL);
        assertEquals(31, window.enterTick(), "181 − 31 × 2 = 119 < 121; at 30 it only touches");
        assertEquals(66, window.exitTick(), "221 − 66 × 2 = 89 > 88; at 67 it has left");
        Oracles.Window now = Oracles.crossingWindow(Piston.standard(100, Side.TOP, 200, 0),
                bird.hitbox(), SCROLL);
        assertEquals(0, now.enterTick(), "already overlapping");
        assertEquals(new Oracles.Window(0, 0),
                Oracles.crossingWindow(piston, bird.hitbox(), 0), "a still world has no window");
    }

    @Test
    void aPistonRetractedNowButExtendedAtTheCrossingIsAvoided() {
        Bird bird = birdAt(150);
        Piston piston = Piston.standard(181, Side.TOP, 300, 0);
        assertEquals(0, piston.extension(), EPS, "telegraphing now, head retracted");
        Oracles.Corridor corridor = Oracles.corridorOf(piston, bird, 1.0, 0, SCROLL, 1.0);
        assertNotNull(corridor);
        // Clock 31..66 spans the extend (40..52) and the hold: the head reaches 300 px.
        assertEquals(300 - SPEC.oy() + BotPilot.CORRIDOR_MARGIN_PX, corridor.ceilY(), EPS,
                "the corridor starts below the head's extent at the crossing, not at 0");
        assertTrue(corridor.floorY() < Playfield.GROUND_DEATH_Y);

        Piston holding = Piston.standard(181, Side.TOP, 300, 40 + 12 + 25);
        assertEquals(300, holding.extension(), EPS, "out now");
        // Clock 31..66 ticks later (108..143 → wraps to 6..41): telegraph, then 1 px of extend.
        Oracles.Corridor later = Oracles.corridorOf(holding, bird, 1.0, 0, SCROLL, 1.0);
        double extentAtCrossing = holding.extensionAtClock(holding.clock() + 66);
        assertTrue(extentAtCrossing < 300 && extentAtCrossing > 0, "" + extentAtCrossing);
        assertEquals(extentAtCrossing - SPEC.oy() + BotPilot.CORRIDOR_MARGIN_PX, later.ceilY(),
                EPS, "a head that is out now but retracted at the crossing frees the column");

        Oracles.Corridor slow = Oracles.corridorOf(piston, bird, 1.0, 0, SCROLL, 0.5);
        assertTrue(slow.ceilY() < corridor.ceilY(), "under TIME_SCALE 0.5 the head is not out yet");

        Piston bottom = Piston.standard(181, Side.BOTTOM, 300, 0);
        Oracles.Corridor bottomCorridor = Oracles.corridorOf(bottom, bird, 1.0, 0, SCROLL, 1.0);
        assertEquals(Playfield.GROUND_Y - 300 - (SPEC.oy() + SPEC.h()) - BotPilot.CORRIDOR_MARGIN_PX,
                bottomCorridor.floorY(), EPS);
    }

    @Test
    void aTopBoltIsAvoidedByAimingLowEvenBeforeTheStrike() {
        Bird bird = birdAt(150);
        LightningStrike bolt = LightningStrike.standard(300, Side.TOP, 0.5);
        assertEquals(LightningStrike.State.IDLE, bolt.state());
        Oracles.Corridor corridor = Oracles.corridorOf(bolt, bird, 1.0, 0, SCROLL, 1.0);
        double boltBottom = 0.5 * Playfield.GROUND_Y;
        assertEquals(boltBottom - SPEC.oy() + BotPilot.CORRIDOR_MARGIN_PX, corridor.ceilY(), EPS,
                "the bolt band is off limits from the start");
        assertTrue(bolt.safeBandY(Playfield.BIRD_X) > boltBottom, "the aim is the unlit half");

        LightningStrike bottom = LightningStrike.standard(300, Side.BOTTOM, 0.6);
        Oracles.Corridor bottomCorridor = Oracles.corridorOf(bottom, bird, 1.0, 0, SCROLL, 1.0);
        assertTrue(bottomCorridor.floorY() + SPEC.oy() + SPEC.h() <= bottom.boltTopY(),
                "the box bottom stays above a bottom bolt");

        Run run = new Run(RunConfig.classic(3), RunSetup.CLASSIC, new FixedSpawnTable());
        run.tick(RunInput.FLAP);
        run.simulation().spawner().setSuppressed(true);
        run.simulation().obstacles().clear();
        run.simulation().obstacles().add(LightningStrike.standard(300, Side.TOP, 0.5));
        BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, 1);
        for (int t = 0; t < 130; t++) {
            run.tick(bot.decide(run));
            assertTrue(run.simulation().bird().isAlive(), "tick " + t);
            if (t > 5) {
                assertTrue(bot.lastAim() > boltBottom, "tick " + t + " aim " + bot.lastAim());
            }
        }
        assertTrue(run.simulation().bird().y() > boltBottom + 30, "" + run.simulation().bird().y());
    }

    @Test
    void aGearOnARailIsClearedByRadiusPlusSixteen() {
        Bird bird = birdAt(150);
        Gear gear = Gear.onRail(181, 300, 40);
        assertTrue(gear.safeBandAbove());
        Oracles.Window window = Oracles.crossingWindow(gear, bird.hitbox(), SCROLL);
        Oracles.Corridor corridor = Oracles.corridorOf(gear, bird, 1.0, 0, SCROLL, 1.0);
        double railPerTick = Gear.DEFAULT_RAIL_SPEED / Playfield.TICK_RATE;
        double boxBottom = corridor.floorY() + SPEC.oy() + SPEC.h();
        // M7: the oracle keeps the clearance from the chord the circle cuts through the box's x
        // range on each tick of the crossing — the full diameter while the centre is over the
        // box, less as the rim only grazes it — so the closest approach is measured to that
        // chord, and it is exactly the clearance.
        double closest = Double.POSITIVE_INFINITY;
        for (int k = window.enterTick(); k <= window.exitTick(); k++) {
            double chordTop = gear.predictedCenterY(k, railPerTick) - halfChord(gear, bird, k);
            closest = Math.min(closest, chordTop - boxBottom);
        }
        assertEquals(Oracles.GEAR_CLEARANCE_PX + BotPilot.CORRIDOR_MARGIN_PX, closest, 1e-6,
                "the box bottom clears the predicted circle by exactly the clearance");
        assertTrue(corridor.floorY() < gear.sweepTopY(), "and the corridor is tighter than the sweep");
        Oracles.Corridor conservative = Oracles.gearCorridor(gear, window, railPerTick, SPEC, 1.0);
        assertTrue(conservative.floorY() <= corridor.floorY(),
                "the whole-circle corridor is never looser than the chord one");

        Gear low = Gear.onRail(181, 200, 40);
        Oracles.Corridor below = Oracles.corridorOf(low, bird, 1.0, 0, SCROLL, 1.0);
        double boxTop = below.ceilY() + SPEC.oy();
        for (int k = window.enterTick(); k <= window.exitTick(); k++) {
            double chordBottom = low.predictedCenterY(k, railPerTick) + halfChord(low, bird, k);
            assertTrue(boxTop - chordBottom >= Oracles.GEAR_CLEARANCE_PX, "tick " + k);
        }
    }

    /** Half the chord a gear's circle cuts through the bird box's x range {@code k} ticks on. */
    private static double halfChord(Gear gear, Bird bird, int k) {
        double cx = gear.centerX() - k * SCROLL;
        double boxX = bird.hitbox().x();
        double boxMaxX = bird.hitbox().maxX();
        double d = cx < boxX ? boxX - cx : (cx > boxMaxX ? cx - boxMaxX : 0);
        return d >= gear.radius() ? Double.NaN : Math.sqrt(gear.radius() * gear.radius() - d * d);
    }

    @Test
    void theGateOracleIsTheM1Corridor() {
        Bird bird = birdAt(320);
        PipeGate gate = PipeGate.standard(200, 250, 128, null);
        Oracles.Corridor corridor = Oracles.corridorOf(gate, bird, 1.0, 13, SCROLL, 1.0);
        assertEquals(250 - SPEC.oy() + BotPilot.CORRIDOR_MARGIN_PX, corridor.ceilY(), EPS);
        assertEquals(378 - (SPEC.oy() + SPEC.h()) - BotPilot.CORRIDOR_MARGIN_PX, corridor.floorY(),
                EPS);
        assertNull(Oracles.corridorOf(new WindZone(200, 100, 320, 200, 0, 0), bird, 1.0, 0, SCROLL,
                1.0), "a wind zone bounds nothing");
    }

    @Test
    void windBendsTheProjection() {
        WindZone updraft = new WindZone(60, 120, 320, 240, -500, 0);
        double calm = BirdPhysics.projectY(320, 0, 30, 1800, 1500);
        double windy = Oracles.projectY(320, 0, 30, 1800, 1500, List.of(updraft), SCROLL, SPEC);
        assertTrue(windy < calm, "an updraft keeps the bird higher: " + windy + " vs " + calm);
        assertEquals(calm, Oracles.projectY(320, 0, 30, 1800, 1500, List.of(), SCROLL, SPEC), 0.0,
                "no zones: bit for bit the plain projection");
        WindZone ahead = new WindZone(400, 60, 320, 240, -500, 0);
        assertEquals(BirdPhysics.projectY(320, 0, 5, 1800, 1500),
                Oracles.projectY(320, 0, 5, 1800, 1500, List.of(ahead), SCROLL, SPEC), 0.0,
                "a zone the bird has not reached changes nothing yet");
        double oneTickWindy = Oracles.projectY(320, 0, 1, 1800, 1500, List.of(updraft), SCROLL,
                SPEC);
        assertEquals((1800 - 500.0) / 60 / 60, oneTickWindy - 320, EPS,
                "the wind joins gravity from the first tick inside the zone");
    }

    /**
     * Twenty seeds per kind, 3000 ticks each: every kind is survived every time. Gears were the
     * one kind under 20/20 (14/20) while the bot aimed at the larger free side of every gear:
     * two big gears with their larger sides on opposite sides looked like a 172 px crossing in
     * 48 px of scroll, although a band consistent with both — above both, below both, or the
     * one between — always exists for spawn-table gears. The pilot now picks the gear side that
     * leads to the next column's band and is reachable from where it is
     * ({@link Oracles#bandOf}, {@code BotPilot.corridorFor}), and the cursor measures the
     * interval from the gear's right edge, so the pairs are flown.
     */
    @Test
    void thePerfectBotSurvivesEachKindInIsolation() {
        for (ObstacleKind kind : ObstacleKind.values()) {
            SpawnTable table = new SpawnTable(Map.of(kind, 100));
            int survived = 0;
            StringBuilder deaths = new StringBuilder();
            for (long seed = 1; seed <= 20; seed++) {
                Run run = new Run(RunConfig.classic(seed), RunSetup.CLASSIC, table);
                HeadlessRunner.Outcome outcome = HeadlessRunner.run(run,
                        new BotPilot(BotPilot.Preset.PERFECT, seed), 3000);
                if (outcome.ticks() == 3000) {
                    survived++;
                } else {
                    deaths.append(" seed ").append(seed).append(" died at tick ")
                            .append(outcome.ticks()).append(" after ")
                            .append(outcome.result().gatesPassed()).append(" gates");
                }
            }
            assertEquals(20, survived, kind + ": " + survived + "/20 survived;" + deaths);
        }
    }

    /**
     * M7 fairness: a spawn-table bolt is reachable from the column before it, at the fastest
     * scroll the tiers reach. Gates and bolts half and half, the world scrolling at ×1.5 over
     * the standard curve's ramp (nightmare late-run pace, up to the 360 px/s cap), the perfect
     * bot flies 20 seeds for 3000 ticks and never dies to a bolt.
     */
    @Test
    void aTableBoltIsSurvivableAfterAnyGateAtTheFastestScroll() {
        SpawnTable table = new SpawnTable(Map.of(ObstacleKind.PIPE_GATE, 50,
                ObstacleKind.LIGHTNING, 50));
        CurveSpec standard = GameContent.load().curveSpec("standard");
        WorldSpec fast = new WorldSpec("fast", standard,
                List.of(StatModifier.multiply(StatId.SCROLL_SPEED, 1.5, "test")), RuleSet.EMPTY,
                table.weights());
        int bolts = 0;
        StringBuilder lightningDeaths = new StringBuilder();
        for (long seed = 1; seed <= 20; seed++) {
            Run run = new Run(RunConfig.classic(seed), RunSetup.CLASSIC.withWorld(fast), table);
            BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, seed);
            int ticks = 0;
            while (!run.isFinished() && ticks < 3000) {
                TickReport report = run.tick(bot.decide(run));
                for (TickFact f : report.facts()) {
                    if (f instanceof TickFact.ObstacleSpawned spawned
                            && spawned.kind() == ObstacleKind.LIGHTNING) {
                        bolts++;
                    }
                }
                ticks++;
            }
            if (run.stats().deathKind() == ObstacleKind.LIGHTNING) {
                lightningDeaths.append(" seed ").append(seed).append(" at tick ").append(ticks)
                        .append(" after ").append(run.stats().gatesPassed()).append(" gates");
            }
        }
        assertTrue(bolts > 200, "bolts spawned: " + bolts);
        assertEquals("", lightningDeaths.toString(), "no bolt is unavoidable");
    }
}
