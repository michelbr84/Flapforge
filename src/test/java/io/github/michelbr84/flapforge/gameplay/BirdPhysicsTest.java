package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.BirdPhysics;
import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BirdPhysicsTest {

    @Test
    void gravityAddsThirtyPixelsPerSecondEachTick() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, 320);
        BirdPhysics.integrate(bird, 1800, 1500);
        assertEquals(30.0, bird.vy(), 0.0);
        assertEquals(320.5, bird.y(), 0.0);
        BirdPhysics.integrate(bird, 1800, 1500);
        assertEquals(60.0, bird.vy(), 0.0);
        assertEquals(321.5, bird.y(), 0.0);
    }

    @Test
    void flapSetsTheVelocityAndNeverAdds() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, 320);
        bird.setVy(900);
        assertTrue(BirdPhysics.flap(bird, 405));
        assertEquals(-405.0, bird.vy(), 0.0);
        assertTrue(BirdPhysics.flap(bird, 405));
        assertEquals(-405.0, bird.vy(), 0.0, "a second flap does not stack");
    }

    @Test
    void flapIsRefusedAtOrAboveTheCeilingGate() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, Playfield.CEILING_FLAP_Y);
        bird.setVy(100);
        assertFalse(BirdPhysics.canFlap(bird));
        assertFalse(BirdPhysics.flap(bird, 405));
        assertEquals(100.0, bird.vy(), 0.0, "a refused flap leaves the velocity alone");
        bird.setY(Playfield.CEILING_FLAP_Y + 0.25);
        assertTrue(BirdPhysics.flap(bird, 405));
        bird.setY(-50);
        assertFalse(BirdPhysics.flap(bird, 405));
    }

    @Test
    void fallSpeedIsClamped() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, 100);
        bird.setVy(440);
        BirdPhysics.integrate(bird, 1800, 450);
        assertEquals(450.0, bird.vy(), 0.0);
        assertEquals(100 + 7.5, bird.y(), 0.0);
        BirdPhysics.integrate(bird, 1800, 450);
        assertEquals(450.0, bird.vy(), 0.0);
    }

    @Test
    void classicClampIsNeverReachedOnScreen() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, 0);
        bird.setVy(15);
        while (!BirdPhysics.groundContact(bird)) {
            BirdPhysics.integrate(bird, 1800, 1500);
        }
        assertTrue(bird.vy() < 1500, "a full-screen dive peaks below 1500 px/s: " + bird.vy());
    }

    @Test
    void projectionMatchesIntegration() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, 200);
        bird.setVy(-405);
        double projected = BirdPhysics.projectY(bird.y(), bird.vy(), 20, 1800, 1500);
        for (int i = 0; i < 20; i++) {
            BirdPhysics.integrate(bird, 1800, 1500);
        }
        assertEquals(bird.y(), projected, 0.0);
        assertEquals(bird.y(), BirdPhysics.projectY(bird.y(), bird.vy(), 0, 1800, 1500), 0.0);
    }

    @Test
    void groundContactAtTheDeathLine() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, 581.25);
        assertFalse(BirdPhysics.groundContact(bird));
        bird.setY(581.5);
        assertTrue(BirdPhysics.groundContact(bird));
    }

    @Test
    void instanceReadsTheStatSheet() {
        EffectStack stack = new EffectStack();
        StatSheet sheet = new StatSheet(Map.of(), stack, RuleSet.EMPTY);
        BirdPhysics physics = new BirdPhysics(sheet);
        Bird bird = new Bird(HitboxSpec.CLASSIC, 320);
        assertTrue(physics.flap(bird));
        assertEquals(-405.0, bird.vy(), 0.0);
        stack.setLayer(Layer.UPGRADES, List.of(
                StatModifier.flat(StatId.FLAP_VELOCITY, 45, "test"),
                StatModifier.multiply(StatId.GRAVITY, 0.5, "test")));
        assertTrue(physics.flap(bird));
        assertEquals(-450.0, bird.vy(), 0.0);
        physics.step(bird);
        assertEquals(-450.0 + 15.0, bird.vy(), 0.0);
    }

    @Test
    void beginTickRecordsThePreviousPosition() {
        Bird bird = new Bird(HitboxSpec.CLASSIC, 320);
        bird.setVy(600);
        bird.beginTick();
        BirdPhysics.integrate(bird, 1800, 1500);
        assertEquals(320.0, bird.prevY(), 0.0);
        assertEquals(320 + 10.5, bird.y(), 0.0);
    }
}
