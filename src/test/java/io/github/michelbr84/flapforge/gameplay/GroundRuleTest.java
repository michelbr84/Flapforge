package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.BirdPhysics;
import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import org.junit.jupiter.api.Test;

/**
 * The one intentional deviation from upstream (D7, §5): Flapforge dies when the sprite bottom
 * touches the ground line ({@code y ≥ 581.5}); upstream clamps the centre at 598 and dies only
 * when the unclamped rectangle top passes 598 (centre above 610), a 28 px window in which the
 * sprite is half buried and a flap is still accepted.
 */
class GroundRuleTest {

    private static final double GRAVITY = 1800;
    private static final double MAX_FALL = 1500;

    @Test
    void flapforgeDiesWhenTheSpriteBottomTouchesTheGroundLine() {
        assertEquals(581.5, Playfield.GROUND_DEATH_Y, 0.0);
        assertEquals(Playfield.GROUND_Y - 16.5, Playfield.GROUND_DEATH_Y, 0.0,
                "sprite bottom = y + 16.5 touches the ground line at 598");
        Bird bird = new Bird(HitboxSpec.CLASSIC, 500);
        bird.setVy(15);
        double previous = bird.y();
        int ticks = 0;
        while (!BirdPhysics.groundContact(bird)) {
            previous = bird.y();
            BirdPhysics.integrate(bird, GRAVITY, MAX_FALL);
            ticks++;
        }
        assertTrue(previous < 581.5);
        assertTrue(bird.y() >= 581.5);
        assertTrue(ticks > 0);
    }

    @Test
    void classicReferenceDiesLaterWithTheSpriteBuried() {
        ClassicReference ref = new ClassicReference(500);
        ref.birdFall();
        int frames = 0;
        while (!ref.isOnGround()) {
            ref.frame();
            frames++;
        }
        assertEquals(ClassicReference.BOTTOM_BOUNDARY, ref.y(), "centre clamped at 598");
        assertTrue(ref.rectY() > 598, "death only when the unclamped rect.y passes 598");
        assertTrue(ref.unclampedY() > 610);
        assertTrue(frames > 0);
    }

    @Test
    void deviationAtTerminalSpeedIsAtMostTwoUpstreamFrames() {
        // Same start, no input: v = 0 upstream maps to vy = +15 px/s (E28).
        ClassicReference ref = new ClassicReference();
        ref.birdFall();
        int upstreamFrames = 0;
        while (!ref.isOnGround()) {
            ref.frame();
            upstreamFrames++;
        }
        Bird bird = new Bird(HitboxSpec.CLASSIC, ClassicReference.START_Y);
        bird.setVy(15);
        int flapforgeTicks = 0;
        while (!BirdPhysics.groundContact(bird)) {
            BirdPhysics.integrate(bird, GRAVITY, MAX_FALL);
            flapforgeTicks++;
        }
        int deviationTicks = 2 * upstreamFrames - flapforgeTicks;
        assertTrue(deviationTicks >= 1, "Flapforge must die earlier than upstream");
        assertTrue(deviationTicks <= 4, "at most 2 upstream frames, was " + deviationTicks
                + " ticks (upstream " + upstreamFrames + " frames, Flapforge " + flapforgeTicks
                + " ticks)");
    }

    @Test
    void slowApproachDeviatesBetweenOneAndSevenFrames() {
        // A flap just above the ground: the bird arcs up and comes back down slowly.
        ClassicReference ref = new ClassicReference(560);
        ref.birdFlap();
        ref.birdFall();
        int upstreamFrames = 0;
        while (!ref.isOnGround()) {
            ref.frame();
            upstreamFrames++;
        }
        Bird bird = new Bird(HitboxSpec.CLASSIC, 560);
        BirdPhysics.flap(bird, 405);
        int flapforgeTicks = 0;
        while (!BirdPhysics.groundContact(bird)) {
            BirdPhysics.integrate(bird, GRAVITY, MAX_FALL);
            flapforgeTicks++;
        }
        int deviationTicks = 2 * upstreamFrames - flapforgeTicks;
        assertTrue(deviationTicks >= 1, "Flapforge must die earlier than upstream");
        assertTrue(deviationTicks <= 14, "at most 7 upstream frames, was " + deviationTicks);
    }

    @Test
    void flapAt590IsImpossibleInFlapforgeButAcceptedUpstream() {
        ClassicReference ref = new ClassicReference(590);
        ref.birdFall();
        assertTrue(ref.birdFlap(), "upstream accepts a flap while half buried (rect.y = 578 > 20)");
        assertFalse(ref.isDead());

        Bird bird = new Bird(HitboxSpec.CLASSIC, 590);
        assertTrue(BirdPhysics.groundContact(bird), "the bird is already on the ground");

        Run run = Run.classic(RunConfig.classic(3));
        run.simulation().spawner().setSuppressed(true);
        run.tick(RunInput.FLAP);
        assertEquals(RunPhase.FLYING, run.phase());
        run.simulation().bird().setY(590);
        TickReport report = run.tick(RunInput.FLAP);
        assertFalse(report.has(TickFact.Flapped.class), "no flap from the buried window");
        assertTrue(report.has(TickFact.Crashed.class));
        assertEquals(CollisionCause.GROUND, run.stats().deathCause());
        assertEquals(RunPhase.FINISHED, run.phase());
        assertEquals(Playfield.GROUND_DEATH_Y, run.simulation().bird().y(), 0.0);
    }

    @Test
    void flapforgeNeverDiesLaterThanUpstreamOnAPlainFall() {
        for (int start = 100; start <= 560; start += 40) {
            ClassicReference ref = new ClassicReference(start);
            ref.birdFall();
            int frames = 0;
            while (!ref.isOnGround()) {
                ref.frame();
                frames++;
            }
            Bird bird = new Bird(HitboxSpec.CLASSIC, start);
            bird.setVy(15);
            int ticks = 0;
            while (!BirdPhysics.groundContact(bird)) {
                BirdPhysics.integrate(bird, GRAVITY, MAX_FALL);
                ticks++;
            }
            assertTrue(ticks <= 2 * frames, "start " + start);
        }
    }
}
