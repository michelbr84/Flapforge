package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.pickup.Coin;
import io.github.michelbr84.flapforge.gameplay.pickup.PickupLayer;
import io.github.michelbr84.flapforge.render.BackgroundRenderer;
import io.github.michelbr84.flapforge.render.BirdRenderer;
import io.github.michelbr84.flapforge.render.CloudLayer;
import io.github.michelbr84.flapforge.render.HudRenderer;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.PickupRenderer;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The cosmetic parity rows of {@code docs/BALANCING.md} that are numbers rather than pixels: the
 * cloud spawn/motion rules, the wing animation cadence and pose selection, and the ground scroll
 * and its freeze on death. Every constant here is quoted in that document, so the two cannot drift
 * apart silently.
 *
 * <p>M3 adds the same kind of row for the coins: the spin is a pure function of the tick, a
 * pickup produces exactly one flourish, and the HUD lights its streak flame at the economy's
 * reward step.
 */
class RenderLayerTest {

    private static final double SCROLL_PER_TICK = 120.0 / Playfield.TICK_RATE;

    @Test
    void cloudsUseTheUpstreamSpawnRuleAndCap() {
        // A random source that always rolls inside the 6 % window, so the cap is what limits it.
        CloudLayer layer = new CloudLayer(new Random() {
            private static final long serialVersionUID = 1L;

            @Override
            public int nextInt(int bound) {
                return 0;
            }

            @Override
            public double nextDouble() {
                return 0.5;
            }
        });
        assertEquals(0, layer.size());
        for (int i = 0; i < CloudLayer.SPAWN_INTERVAL_TICKS - 1; i++) {
            layer.tick(SCROLL_PER_TICK, false);
        }
        assertEquals(0, layer.size(), "the spawn check only runs every "
                + CloudLayer.SPAWN_INTERVAL_TICKS + " ticks (100 ms at 60 Hz)");
        layer.tick(SCROLL_PER_TICK, false);
        assertEquals(1, layer.size(), "the check spawned one cloud");

        for (int i = 0; i < CloudLayer.SPAWN_INTERVAL_TICKS * 20; i++) {
            layer.tick(SCROLL_PER_TICK, false);
        }
        assertTrue(layer.size() <= Playfield.CLOUD_MAX,
                "never more than " + Playfield.CLOUD_MAX + " clouds, was " + layer.size());
    }

    @Test
    void cloudsNeverSpawnWhenTheRollMissesTheSixPercentWindow() {
        CloudLayer layer = new CloudLayer(new Random() {
            private static final long serialVersionUID = 1L;

            @Override
            public int nextInt(int bound) {
                return Playfield.CLOUD_SPAWN_PCT; // exactly on the boundary: not inside
            }
        });
        for (int i = 0; i < CloudLayer.SPAWN_INTERVAL_TICKS * 50; i++) {
            layer.tick(SCROLL_PER_TICK, false);
        }
        assertEquals(0, layer.size(), "the spawn chance is strictly below "
                + Playfield.CLOUD_SPAWN_PCT + "%");
    }

    @Test
    void cloudSpeedIsTwiceTheScrollAndDropsToThirtyOnDeath() {
        assertEquals(2 * 120.0, CloudLayer.SPEED, 0.0, "clouds move at GAME_SPEED x 2");
        assertEquals(Playfield.CLOUD_SPEED_FACTOR * 120.0, CloudLayer.SPEED, 0.0);
        assertEquals(30.0, CloudLayer.DEAD_SPEED, 0.0, "1 px per 30 Hz frame once the bird dies");
        assertEquals(Playfield.TICK_RATE / 10, CloudLayer.SPAWN_INTERVAL_TICKS);
        assertEquals(20, CloudLayer.SPAWN_TOP_Y, "upstream's TOP_BAR_HEIGHT");
        assertEquals(Playfield.HEIGHT / 3, CloudLayer.SPAWN_BOTTOM_Y, "the top third");
    }

    @Test
    void theCloudStepFollowsTheWorldScrollAndTheDeadDriftIsAbsolute() {
        // A single cloud, spawned at x = 420 by the first check, then measured tick by tick.
        CloudLayer layer = new CloudLayer(new Random() {
            private static final long serialVersionUID = 1L;

            @Override
            public int nextInt(int bound) {
                return 0;
            }

            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        for (int i = 0; i < CloudLayer.SPAWN_INTERVAL_TICKS; i++) {
            layer.tick(SCROLL_PER_TICK, false);
        }
        assertEquals(1, layer.size());
        double afterSpawn = layer.cloudX(0);

        layer.tick(SCROLL_PER_TICK, false);
        assertEquals(afterSpawn - CloudLayer.SPEED / Playfield.TICK_RATE, layer.cloudX(0), 1e-9,
                "at the classic scroll the cloud step is SPEED / 60");

        double before = layer.cloudX(0);
        layer.tick(2 * SCROLL_PER_TICK, false);
        assertEquals(before - 2 * CloudLayer.SPEED / Playfield.TICK_RATE, layer.cloudX(0), 1e-9,
                "a faster world scroll carries the clouds with it (hard tier, Slow Time)");

        before = layer.cloudX(0);
        layer.tick(0.0, true);
        assertEquals(before - CloudLayer.DEAD_SPEED / Playfield.TICK_RATE, layer.cloudX(0), 1e-9,
                "the dead drift is absolute, as upstream's speed = 1 was");
    }

    @Test
    void theWingCycleIsEightFramesOfTwentyTicksAndRestartsOnAFlap() {
        assertEquals(8, BirdRenderer.WING_FRAMES);
        assertEquals(20, BirdRenderer.TICKS_PER_WING_FRAME);
        assertEquals(160, BirdRenderer.WING_CYCLE_TICKS);

        BirdRenderer renderer = new BirdRenderer();
        assertEquals(0, renderer.wingFrame());
        for (int i = 0; i < BirdRenderer.TICKS_PER_WING_FRAME; i++) {
            renderer.tick(false);
        }
        assertEquals(1, renderer.wingFrame(), "one frame per 20 ticks");
        for (int i = 0; i < BirdRenderer.TICKS_PER_WING_FRAME * 3; i++) {
            renderer.tick(false);
        }
        assertEquals(4, renderer.wingFrame());

        renderer.tick(true);
        assertEquals(0, renderer.wingFrame(), "a flap restarts the animation (wingState = 0)");

        for (int i = 0; i < BirdRenderer.WING_CYCLE_TICKS; i++) {
            renderer.tick(false);
        }
        assertEquals(0, renderer.wingFrame(), "the cycle wraps after 160 ticks");
    }

    @Test
    void thePoseFollowsTheVelocitySignAndTheLifeState() {
        Bird bird = Bird.classic();
        bird.setVy(120);
        assertEquals(ProceduralArt.BirdPose.NORMAL, BirdRenderer.poseOf(bird), "falling");
        bird.setVy(0);
        assertEquals(ProceduralArt.BirdPose.NORMAL, BirdRenderer.poseOf(bird), "at the apex");
        bird.setVy(-405);
        assertEquals(ProceduralArt.BirdPose.UP, BirdRenderer.poseOf(bird),
                "rising is vy < 0 in Flapforge's sign convention");
        bird.setState(Bird.State.DYING);
        assertEquals(ProceduralArt.BirdPose.DEAD, BirdRenderer.poseOf(bird));
        bird.setState(Bird.State.DEAD);
        assertEquals(ProceduralArt.BirdPose.DEAD, BirdRenderer.poseOf(bird));
    }

    @Test
    void theGroundScrollsAtTheObstacleSpeedAndFreezesWhenAsked() {
        BackgroundRenderer background = new BackgroundRenderer();
        assertEquals(0.0, background.distance(), 0.0);
        for (int i = 0; i < 60; i++) {
            background.tick(SCROLL_PER_TICK, false);
        }
        assertEquals(120.0, background.distance(), 1e-9, "120 px in one second");

        background.tick(SCROLL_PER_TICK, true);
        assertEquals(120.0, background.distance(), 1e-9, "a frozen tick moves nothing");

        background.reset();
        assertEquals(0.0, background.distance(), 0.0, "a new run restarts the strip");
    }

    @Test
    void theCoinSpinIsAPureFunctionOfTheTick() {
        int turn = ProceduralArt.COIN_SPIN_TICKS;
        assertEquals(1.0, ProceduralArt.coinSpin(0), 1e-9, "face on at the start of a turn");
        assertEquals(0.0, ProceduralArt.coinSpin(turn / 4), 1e-9, "edge on a quarter later");
        assertEquals(-1.0, ProceduralArt.coinSpin(turn / 2), 1e-9, "the other face at half");
        assertEquals(ProceduralArt.coinSpin(3), ProceduralArt.coinSpin(3L + 10 * turn), 0.0,
                "the phase wraps on the turn");
        assertEquals(ProceduralArt.coinSpin(turn - 1), ProceduralArt.coinSpin(-1), 0.0,
                "a negative phase wraps too, it does not index out of the table");
        assertEquals(turn, PickupRenderer.SPIN_TICKS);
    }

    @Test
    void aCollectedCoinProducesExactlyOneFlourish() {
        PickupLayer layer = new PickupLayer(new RandomProvider(7));
        Coin coin = new Coin(120, 300);
        layer.add(coin);
        ParticleSystem particles = new ParticleSystem(new Random(3));
        PickupRenderer renderer = new PickupRenderer();

        renderer.tick(layer, particles);
        assertEquals(0, renderer.flourishes(), "an untouched coin is not a pickup");
        assertTrue(particles.isEmpty());

        // Collection goes through the layer, which is what counts the pickup; the renderer reads
        // that counter, so a coin marked collected behind the layer's back is not a pickup.
        assertEquals(1, layer.collect(new Aabb(110, 290, 20, 20)).size());
        assertTrue(coin.isCollected());
        renderer.tick(layer, particles);
        assertEquals(1, renderer.flourishes(), "the pickup is announced once");
        assertTrue(particles.count() > 0, "the flourish reached the pool");

        // The layer only drops a collected coin on its next update, so the corpse is still in the
        // list on the following tick; it must not be counted again.
        int emitted = particles.count();
        renderer.tick(layer, particles);
        assertEquals(1, renderer.flourishes(), "the same coin cannot fire twice");
        assertTrue(particles.count() <= emitted, "and nothing new was emitted");

        renderer.reset();
        assertEquals(0, renderer.flourishes());
        assertEquals(0, renderer.ticks(), "a new run restarts the spin");
    }

    @Test
    void theHudLightsTheStreakFlameAtTheEconomyStep() {
        HudRenderer hud = new HudRenderer("hint");
        assertFalse(hud.isStreakHot(9), "no step configured, no flame");
        hud.setStreakStep(5);
        assertEquals(5, hud.streakStep());
        assertFalse(hud.isStreakHot(0));
        assertFalse(hud.isStreakHot(4), "one gate short of a reward step");
        assertTrue(hud.isStreakHot(5), "the step itself pays, so it burns");
        assertTrue(hud.isStreakHot(12), "and it stays lit above it");
        hud.setStreakStep(0);
        assertFalse(hud.isStreakHot(100), "a disabled step never lights the flame");
    }

    @Test
    void theGroundPatternWrapsOnTheUpstreamStripWidth() {
        assertEquals(253, BackgroundRenderer.STRIP_WIDTH,
                "upstream's background image is 253 px wide");
        assertEquals(42, Playfield.GROUND_HEIGHT, "half of the 84 px strip");
        assertEquals(598, Playfield.GROUND_Y);
        assertTrue(BackgroundRenderer.HILL_FAR_PARALLAX < BackgroundRenderer.HILL_NEAR_PARALLAX,
                "the far band must scroll slower than the near one");
        assertTrue(BackgroundRenderer.HILL_NEAR_PARALLAX < 1.0,
                "both hill bands scroll slower than the ground");
        assertFalse(BackgroundRenderer.HILL_FAR_PERIOD == BackgroundRenderer.HILL_NEAR_PERIOD,
                "different periods keep the two bands from beating together");
    }
}
