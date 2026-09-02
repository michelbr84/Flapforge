package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.Oscillator;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * D12 for every per-tick field M7 added: each one is folded into {@code Simulation.stateHash()}.
 * The fields are moved through reflection — a test seam nothing in production offers — and the
 * hash must move with them and come back when they are restored, which is what makes a
 * regression that drops a fold from a {@code hashGeometry} or {@code hashState} fail here
 * rather than survive the replay tests (whose two runs would both miss it).
 */
class HashFoldTest {

    /** A run in the Void: patterns, cycles and every kind of column to fold. */
    private static Run voidRun() {
        RunFactory factory = new RunFactory(GameContent.load());
        Run run = factory.newRun(RunConfig.builder(7).worldId("void").build());
        run.tick(RunInput.FLAP);
        run.simulation().spawner().setSuppressed(true);
        run.simulation().obstacles().add(Gear.onRail(300, 300, 36));
        run.simulation().obstacles().add(Piston.standard(400, Side.TOP, 200, 0));
        run.simulation().obstacles().add(LightningStrike.standard(500, Side.BOTTOM, 0.5));
        run.simulation().obstacles().add(new WindZone(600, 120, 300, 200, -400, 0));
        return run;
    }

    private static Obstacle obstacleOf(Run run, Class<?> type) {
        for (Obstacle o : run.simulation().obstacles().obstacles()) {
            if (type.isInstance(o)) {
                return o;
            }
        }
        throw new IllegalStateException("no " + type.getSimpleName());
    }

    private static Field field(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // keep looking up the hierarchy
            }
        }
        throw new IllegalStateException(target.getClass().getSimpleName() + " has no field "
                + name);
    }

    /** Sets a field, asserts the hash moved, restores it, asserts the hash is back. */
    private static void assertFolded(Run run, Object target, String name, Object value) {
        try {
            Field f = field(target, name);
            Object original = f.get(target);
            assertNotEquals(original, value, "the mutation must change " + name);
            long before = run.simulation().stateHash();
            f.set(target, value);
            long after = run.simulation().stateHash();
            assertNotEquals(before, after, target.getClass().getSimpleName() + "." + name
                    + " is not folded into the state hash");
            f.set(target, original);
            assertEquals(before, run.simulation().stateHash(), "restored");
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void everyObstaclePhaseIsFolded() {
        Run run = voidRun();
        Gear gear = (Gear) obstacleOf(run, Gear.class);
        assertFolded(run, gear, "angle", 0.37);
        Optional<Oscillator> rail = gear.rail();
        assertTrue(rail.isPresent());
        assertFolded(run, rail.get(), "phase", 17.5);
        Piston piston = (Piston) obstacleOf(run, Piston.class);
        assertFolded(run, piston, "clock", 55.0);
        assertFolded(run, piston, "extension", 120.0);
        LightningStrike bolt = (LightningStrike) obstacleOf(run, LightningStrike.class);
        assertFolded(run, bolt, "state", LightningStrike.State.WARNING);
        assertFolded(run, bolt, "strikeClock", 3.0);
        WindZone zone = (WindZone) obstacleOf(run, WindZone.class);
        assertFolded(run, zone, "affecting", true);
    }

    @Test
    void theWorldEffectsAndTheStreamerAreFolded() {
        Run run = voidRun();
        WorldEffects effects = run.simulation().worldEffects();
        assertTrue(effects.isActive());
        assertFolded(run, effects, "activeIndex", 2);
        assertFolded(run, effects, "pendingIndex", 1);
        assertFolded(run, effects, "telegraphRemaining", 40);
        assertFolded(run, effects, "shifts", 3);
        assertFolded(run, effects, "flashes", 2);
        Object streamer = run.simulation().spawner().streamer();
        assertNotNull(streamer, "the Void streams patterns");
        assertFolded(run, streamer, "active", run.setup().world().patterns().get(0));
        assertFolded(run, streamer, "stepIndex", 2);
        assertFolded(run, streamer, "cooldown", true);
        assertFolded(run, streamer, "patternsStarted", 4);
        assertFolded(run, streamer, "stepsStreamed", 9);
    }

    /**
     * The M5/M6 folds that a Void option can reach mid-run: i-frames and the ghost are folded
     * even when the run has no run system of its own, and a pending breather deferral is part
     * of the spawner's state.
     */
    @Test
    void invulnerabilityGhostAndAPendingDeferralAreFoldedWithoutRunSystems() {
        Run run = voidRun();
        Simulation sim = run.simulation();
        assertTrue(!sim.hasRunSystems(), "a classic bird with no ability");
        assertFolded(run, sim, "invulnerableTicks", 7);
        assertFolded(run, sim, "ghost", true);
        assertFolded(run, sim.spawner(), "deferredIntervals", 1.5);
        assertFolded(run, sim.spawner(), "deferredClearancePx", 352.0);
        // And the classic run keeps hashing what it hashed: nothing is folded while all are zero.
        Run classic = Run.classic(RunConfig.classic(7));
        classic.tick(RunInput.FLAP);
        long h = classic.simulation().stateHash();
        assertEquals(h, classic.simulation().stateHash());
    }
}
