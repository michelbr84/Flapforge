package io.github.michelbr84.flapforge.ui.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.WorldPalette;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The hub's forge scene (D17, D18, M10): the stage thresholds, the glow cap under reduce
 * flashing and high contrast, the sparkle cadence, and a non-blank render at every stage in
 * every accessibility mode.
 */
class ForgeSceneTest {

    @AfterEach
    void tearDown() {
        Accessibility.clear();
    }

    @Test
    void theStageFollowsTheUpgradeLevelsBought() {
        assertEquals(0, ForgeScene.stageOf(0));
        assertEquals(1, ForgeScene.stageOf(1));
        assertEquals(1, ForgeScene.stageOf(5));
        assertEquals(2, ForgeScene.stageOf(6));
        assertEquals(3, ForgeScene.stageOf(14));
        assertEquals(4, ForgeScene.stageOf(22));
        assertEquals(4, ForgeScene.stageOf(35));
        assertEquals(5, ForgeScene.stageOf(36));
        assertEquals(5, ForgeScene.stageOf(1_000));
        assertEquals(ForgeScene.MAX_STAGE, ForgeScene.stageOf(Integer.MAX_VALUE));
        assertEquals(0, ForgeScene.stageOf(-3), "a negative count is treated as none");
    }

    @Test
    void theGlowIsCappedAndTheSparklesAreThinnedForAccessibility() {
        ForgeScene scene = new ForgeScene();
        scene.bind(WorldPalette.GREEN_FIELDS, null, null, 0, false);
        assertEquals(0.0, scene.glowAlpha(30), 1e-9, "a cold forge does not glow");
        assertFalse(scene.sparkleDue(0));

        scene.bind(WorldPalette.GREEN_FIELDS, null, null, 1, false);
        double peak = scene.glowAlpha(ForgeScene.GLOW_PERIOD_TICKS / 2);
        assertEquals(ForgeScene.GLOW_PEAK, peak, 1e-9);
        assertTrue(scene.glowAlpha(0) < peak, "the glow pulses");
        assertTrue(scene.glowAlpha(0) > 0, "and never goes out");

        scene.setReduceFlashing(true);
        assertTrue(scene.glowAlpha(ForgeScene.GLOW_PERIOD_TICKS / 2)
                <= ParticleSystem.REDUCED_PEAK_ALPHA);
        Accessibility.setHighContrast(true);
        assertTrue(scene.glowAlpha(ForgeScene.GLOW_PERIOD_TICKS / 2) <= peak / 2 + 1e-9,
                "high contrast halves the glow");
        Accessibility.clear();

        scene.bind(WorldPalette.GREEN_FIELDS, null, null, ForgeScene.MAX_STAGE, false);
        scene.setReduceFlashing(false);
        assertTrue(scene.sparkleDue(0));
        assertTrue(scene.sparkleDue(ForgeScene.SPARKLE_PERIOD_TICKS));
        assertFalse(scene.sparkleDue(1));
        scene.setReduceFlashing(true);
        assertFalse(scene.sparkleDue(ForgeScene.SPARKLE_PERIOD_TICKS),
                "reduce flashing halves the cadence");
        assertTrue(scene.sparkleDue(ForgeScene.SPARKLE_PERIOD_REDUCED_TICKS));
        assertEquals(ForgeScene.MAX_STAGE, scene.stage());
        scene.bind(WorldPalette.GREEN_FIELDS, null, null, 99, false);
        assertEquals(ForgeScene.MAX_STAGE, scene.stage(), "the stage is clamped");
    }

    @Test
    void everyStageRendersNonBlankInEveryMode() {
        GameContent content = GameContent.load();
        PlayerProfile profile = PlayerProfile.fresh(1_700_000_000_000L).normalize();
        WorldPalette storm = WorldPalette.from(content.worlds().get("storm_sky").palette());
        for (boolean highContrast : new boolean[] {false, true}) {
            for (boolean reduce : new boolean[] {false, true}) {
                for (int stage = 0; stage <= ForgeScene.MAX_STAGE; stage++) {
                    Accessibility.setHighContrast(highContrast);
                    ForgeScene scene = new ForgeScene();
                    scene.setReduceFlashing(reduce);
                    scene.bind(stage % 2 == 0 ? WorldPalette.GREEN_FIELDS : storm,
                            stage % 3 == 0 ? null : content, stage % 3 == 0 ? null : profile,
                            stage, stage == 2);
                    BufferedImage frame = new BufferedImage(420, 640,
                            BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = frame.createGraphics();
                    try {
                        ProceduralArt.prepare(g);
                        for (long tick = 0; tick < 4; tick++) {
                            scene.render(g, 0.5, tick, tick * 7);
                        }
                    } finally {
                        g.dispose();
                    }
                    assertTrue(distinctColours(frame) >= 4, "stage " + stage + " is uniform"
                            + " (hc " + highContrast + ", reduce " + reduce + ")");
                    assertEquals(0, frame.getRGB(210, 40) >>> 24,
                            "the scene stays inside its band");
                    assertEquals(0, frame.getRGB(210, 600) >>> 24);
                }
            }
        }
    }

    private static int distinctColours(BufferedImage img) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 200; y < 400; y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                colours.add(img.getRGB(x, y));
                if (colours.size() > 8) {
                    return colours.size();
                }
            }
        }
        return colours.size();
    }
}
