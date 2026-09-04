package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.BirdPhysics;
import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Air parity with the upstream integer loop (§5, E28): with a flap applied before the first of
 * the two ticks of frame {@code f}, Flapforge's {@code y} after tick {@code 2f + 1} (0-based)
 * equals upstream's {@code y} after frame {@code f} to 0.0 px for every frame until the first
 * ground contact.
 */
class ClassicFeelTest {

    private static final double GRAVITY = 1800;
    private static final double FLAP = 405;
    private static final double MAX_FALL = 1500;

    /** Result of a side-by-side run. */
    private record Comparison(List<Double> flapforge, List<Integer> upstream,
            List<Boolean> flapforgeAccepted, List<Boolean> upstreamAccepted, int contactFrame,
            double maxAbsDelta) {
    }

    @Test
    void tickRateIsPinnedAtSixty() {
        assertEquals(60, Playfield.TICK_RATE, "two ticks per upstream 30 Hz frame");
        assertEquals(16_666_667L, Playfield.TICK_NS);
    }

    @Test
    void tenFlapsOver120FramesMatchToZeroPixels() {
        Comparison c = compare(Set.of(0, 7, 15, 22, 30, 40, 55, 70, 90, 110), 120, MAX_FALL,
                ClassicReference.START_Y);
        assertEquals(0.0, c.maxAbsDelta(), "air trajectory must be bit-identical");
        assertEquals(c.upstreamAccepted(), c.flapforgeAccepted());
        assertTrue(c.contactFrame() < 0 || c.contactFrame() > 100,
                "the scenario should stay in the air for most of the 120 frames");
    }

    @Test
    void ceilingSpamRefusesTheSameFlaps() {
        Set<Integer> every = new TreeSet<>();
        for (int f = 0; f < 120; f++) {
            every.add(f);
        }
        Comparison c = compare(every, 120, MAX_FALL, ClassicReference.START_Y);
        assertEquals(0.0, c.maxAbsDelta());
        assertEquals(c.upstreamAccepted(), c.flapforgeAccepted(), "accept/refuse sets differ");
        assertTrue(c.flapforgeAccepted().contains(Boolean.FALSE), "the ceiling gate never fired");
        assertTrue(c.flapforgeAccepted().contains(Boolean.TRUE));
        assertEquals(-1, c.contactFrame(), "spamming the ceiling never touches the ground");
    }

    @Test
    void diveAfterOneFlapMatchesUntilGroundContact() {
        Comparison c = compare(Set.of(0), 60, MAX_FALL, ClassicReference.START_Y);
        assertEquals(0.0, c.maxAbsDelta());
        assertTrue(c.contactFrame() > 0, "a 2 s dive reaches the ground");
        assertTrue(c.contactFrame() < 60);
    }

    @Test
    void fallClamp450DocumentsTheDivergence() {
        // Start high so neither side reaches the ground (or the upstream clamp) within 1 s.
        Comparison c = compare(Set.of(0), 30, 450, 50);
        assertEquals(256.0, c.maxAbsDelta(), 0.0,
                "a 450 px/s clamp diverges by exactly 256 px after a 1 s dive");
        assertEquals(-1, c.contactFrame());
    }

    @Test
    void apexIs42Point25AtTick13And42AtTick14() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, Playfield.BIRD_START_Y);
        assertTrue(BirdPhysics.flap(bird, FLAP));
        double start = bird.y();
        double best = start;
        int bestTick = -1;
        for (int t = 1; t <= 30; t++) {
            BirdPhysics.integrate(bird, GRAVITY, MAX_FALL);
            if (t == 13) {
                assertEquals(42.25, start - bird.y(), 0.0);
            }
            if (t == 14) {
                assertEquals(42.0, start - bird.y(), 0.0);
            }
            if (bird.y() < best) {
                best = bird.y();
                bestTick = t;
            }
        }
        assertEquals(13, bestTick);
        ClassicReference ref = new ClassicReference();
        assertTrue(ref.birdFlap());
        ref.birdFall();
        for (int f = 1; f <= 7; f++) {
            ref.frame();
            if (f == 6 || f == 7) {
                assertEquals(42, ClassicReference.START_Y - ref.y());
            }
        }
    }

    @Test
    void firstFrameAfterAFlapMovesTwelvePixels() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, 320);
        BirdPhysics.flap(bird, FLAP);
        BirdPhysics.integrate(bird, GRAVITY, MAX_FALL);
        assertEquals(320 - 6.25, bird.y(), 0.0);
        BirdPhysics.integrate(bird, GRAVITY, MAX_FALL);
        assertEquals(320 - 12.0, bird.y(), 0.0);
    }

    @Test
    void runAppliesFlapsBeforePhysicsSoTheParityHoldsEndToEnd() {
        Set<Integer> flapFrames = Set.of(0, 7, 15, 22, 30, 40, 55, 70, 90, 110);
        Run run = Run.classic(RunConfig.classic(1));
        run.simulation().spawner().setSuppressed(true);
        ClassicReference ref = new ClassicReference();
        int compared = 0;
        for (int f = 0; f < 120 && run.phase() != RunPhase.FINISHED; f++) {
            boolean flap = flapFrames.contains(f);
            if (flap) {
                ref.birdFlap();
                ref.birdFall();
            }
            ref.frame();
            run.tick(flap ? RunInput.FLAP : RunInput.NONE);
            run.tick(RunInput.NONE);
            if (run.phase() == RunPhase.FLYING) {
                assertEquals(ref.unclampedY(), run.simulation().bird().y(), 0.0,
                        "frame " + f);
                compared++;
            }
        }
        assertTrue(compared >= 100, "compared only " + compared + " frames");
        assertTrue(run.simulation().obstacles().isEmpty(), "spawner was suppressed");
    }

    @Test
    void deathFallMatchesUpstreamDeadBirdFall() {
        // E28: upstream zeroes the up-positive velocity on a pipe hit; Flapforge seeds +15 px/s.
        ClassicReference ref = new ClassicReference(300);
        ref.birdFlap();
        ref.birdFall();
        for (int f = 0; f < 3; f++) {
            ref.frame();
        }
        ref.deadBirdFall();
        Bird bird = new Bird(HitboxSpec.CLASSIC, ref.unclampedY());
        bird.setVy(15);
        for (int f = 0; f < 40; f++) {
            ref.frame();
            BirdPhysics.integrate(bird, GRAVITY, MAX_FALL);
            BirdPhysics.integrate(bird, GRAVITY, MAX_FALL);
            assertEquals(ref.unclampedY(), bird.y(), 0.0, "death fall frame " + f);
            if (BirdPhysics.groundContact(bird)) {
                return;
            }
        }
        assertFalse(true, "the dead bird never reached the ground");
    }

    /**
     * Runs both models side by side.
     *
     * @param flapFrames frames (0-based) whose first tick receives a flap
     * @param frames number of upstream frames to run
     * @param maxFallSpeed the Flapforge fall clamp
     * @param startY the start centre y for both
     * @return the comparison (stops after the first Flapforge ground contact)
     */
    private static Comparison compare(Set<Integer> flapFrames, int frames, double maxFallSpeed,
            int startY) {
        ClassicReference ref = new ClassicReference(startY);
        Bird bird = new Bird(HitboxSpec.CLASSIC, startY);
        List<Double> ff = new ArrayList<>();
        List<Integer> up = new ArrayList<>();
        List<Boolean> ffAccepted = new ArrayList<>();
        List<Boolean> upAccepted = new ArrayList<>();
        double maxDelta = 0;
        int contact = -1;
        for (int f = 0; f < frames; f++) {
            if (flapFrames.contains(f)) {
                upAccepted.add(ref.birdFlap());
                ref.birdFall();
                ffAccepted.add(BirdPhysics.flap(bird, FLAP));
            }
            ref.frame();
            BirdPhysics.integrate(bird, GRAVITY, maxFallSpeed);
            BirdPhysics.integrate(bird, GRAVITY, maxFallSpeed);
            ff.add(bird.y());
            up.add(ref.unclampedY());
            maxDelta = Math.max(maxDelta, Math.abs(bird.y() - ref.unclampedY()));
            if (BirdPhysics.groundContact(bird)) {
                contact = f;
                break;
            }
        }
        return new Comparison(ff, up, ffAccepted, upAccepted, contact, maxDelta);
    }
}
