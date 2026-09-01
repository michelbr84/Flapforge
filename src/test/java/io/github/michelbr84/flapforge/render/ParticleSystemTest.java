package io.github.michelbr84.flapforge.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The three properties the particle pool is built around (D18): it is reproducible when the
 * caller supplies a seeded source, it never grows, and {@code settings.reduceFlashing} really
 * damps what it emits.
 */
class ParticleSystemTest {

    private static final double TICK = 1.0 / 60;

    @AfterEach
    void restoreDefault() {
        ParticleSystem.setDefaultReduceFlashing(true);
    }

    private static ParticleSystem seeded(long seed, boolean reduceFlashing) {
        ParticleSystem system = new ParticleSystem(new Random(seed));
        system.setReduceFlashing(reduceFlashing);
        return system;
    }

    @Test
    void twoPoolsWithTheSameSeedProduceTheSameParticles() {
        ParticleSystem a = seeded(42, false);
        ParticleSystem b = seeded(42, false);
        for (int i = 0; i < 5; i++) {
            a.emitFlapPuff(100, 200);
            b.emitFlapPuff(100, 200);
            a.emitCrashBurst(105, 210, 0x4BC4CF);
            b.emitCrashBurst(105, 210, 0x4BC4CF);
            a.update(TICK);
            b.update(TICK);
        }
        assertEquals(a.count(), b.count());
        assertTrue(a.count() > 0, "the emitters produced something");
        for (int i = 0; i < a.count(); i++) {
            assertEquals(a.x(i), b.x(i), 0.0, "x of particle " + i);
            assertEquals(a.y(i), b.y(i), 0.0, "y of particle " + i);
            assertEquals(a.life(i), b.life(i), 0.0, "life of particle " + i);
        }
    }

    @Test
    void anotherSeedProducesAnotherSpray() {
        ParticleSystem a = seeded(1, false);
        ParticleSystem b = seeded(2, false);
        a.emitCrashBurst(100, 100, 0xFFFFFF);
        b.emitCrashBurst(100, 100, 0xFFFFFF);
        a.update(TICK);
        b.update(TICK);
        boolean identical = true;
        for (int i = 0; i < Math.min(a.count(), b.count()); i++) {
            identical &= a.x(i) == b.x(i) && a.y(i) == b.y(i);
        }
        assertFalse(identical, "different seeds must spray differently");
    }

    @Test
    void theCapacityNeverGrowsOverTenThousandUpdates() {
        ParticleSystem system = seeded(7, false);
        int capacity = system.capacity();
        for (int tick = 0; tick < 10_000; tick++) {
            if (tick % 7 == 0) {
                system.emitFlapPuff(100, 200);
            }
            if (tick % 200 == 0) {
                system.emitCrashBurst(120, 220, 0x4BC4CF);
            }
            if (tick % 53 == 0) {
                system.emitUiSparkle(80, 90, 0xF4F8F8);
            }
            system.update(TICK);
            assertEquals(capacity, system.capacity(), "the pool must not grow at tick " + tick);
            assertTrue(system.count() <= capacity, "count exceeded the pool at tick " + tick);
        }
        for (int i = 0; i < 200; i++) {
            system.update(TICK);
        }
        assertEquals(0, system.count(), "every particle expires");
        assertTrue(system.isEmpty());
        assertEquals(capacity, system.capacity());
    }

    @Test
    void anEmitBeyondTheCapacityIsDroppedRatherThanGrowingThePool() {
        ParticleSystem system = new ParticleSystem(new Random(3), 8);
        for (int i = 0; i < 50; i++) {
            system.emitCrashBurst(10, 10, 0xFFFFFF);
        }
        assertEquals(8, system.capacity());
        assertEquals(8, system.count());
    }

    @Test
    void reduceFlashingDampsWhatTheBurstEmits() {
        ParticleSystem bright = seeded(11, false);
        ParticleSystem damped = seeded(11, true);
        bright.emitCrashBurst(200, 300, 0xFFFFFF);
        damped.emitCrashBurst(200, 300, 0xFFFFFF);

        assertTrue(damped.count() < bright.count(),
                "damped " + damped.count() + " vs bright " + bright.count());
        assertTrue(damped.peakAlpha() <= ParticleSystem.REDUCED_PEAK_ALPHA + 1e-9,
                "peak alpha " + damped.peakAlpha());
        assertTrue(bright.peakAlpha() > ParticleSystem.REDUCED_PEAK_ALPHA,
                "the undamped burst is brighter: " + bright.peakAlpha());
        assertNotEquals(bright.count(), damped.count());
    }

    @Test
    void newPoolsAdoptTheAccessibilityDefault() {
        ParticleSystem.setDefaultReduceFlashing(false);
        assertFalse(new ParticleSystem(new Random(1)).isReduceFlashing());
        ParticleSystem.setDefaultReduceFlashing(true);
        assertTrue(new ParticleSystem(new Random(1)).isReduceFlashing());
    }

    @Test
    void updateIgnoresNonsenseDeltasAndRendersHeadlessly() {
        ParticleSystem system = seeded(5, false);
        system.emitUiSparkle(50, 60, 0x6FD1A8);
        int before = system.count();
        system.update(0);
        system.update(-1);
        system.update(Double.NaN);
        assertEquals(before, system.count(), "a nonsense delta changes nothing");

        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            system.render(g);
            system.update(TICK);
            system.render(g);
        } finally {
            g.dispose();
        }
        assertTrue(system.count() > 0);
    }
}
