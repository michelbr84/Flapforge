package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The game-over strip's boss row (D29, M8 review pass): it is information about the encounter,
 * so a run whose boss never warned shows no boss row at all instead of a meaningless
 * "Phase 0", and the row only says "Cleared" once a world boss was actually survived.
 */
class GameOverOverlayTest {

    private ScreenManager screens;
    private Strings strings;

    @BeforeEach
    void setUp() {
        screens = new ScreenManager(new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false));
        strings = Strings.load("en");
    }

    private GameOverOverlay overlay(RunResult result) {
        GameRenderer renderer = new GameRenderer(WorldPalette.GREEN_FIELDS, "ready");
        return new GameOverOverlay(screens, result, () -> { }, renderer, strings);
    }

    private static RunResult result(boolean bossEnabled, int phasesReached,
            List<String> bossesCleared) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(12);
        stats.setPoints(12);
        for (int i = 0; i < 12 * 60; i++) {
            stats.tickAlive();
        }
        stats.setDeathCause(CollisionCause.OBSTACLE);
        stats.setPhasesReached(phasesReached);
        for (String worldId : bossesCleared) {
            stats.addBossCleared(worldId);
        }
        return new RunResult(RunConfig.builder(42L).bossEnabled(bossEnabled).build(), stats,
                Map.of());
    }

    private static String bossRow(Strings strings, GameOverOverlay overlay) {
        String label = strings.get(StringKey.STAT_BOSS);
        return overlay.rowTexts().stream()
                .filter(row -> row.startsWith(label + " "))
                .findFirst()
                .orElse(null);
    }

    @Test
    void aBossRunThatEndedBeforeTheBossWarnedShowsNoBossRow() {
        GameOverOverlay overlay = overlay(result(true, 0, List.of()));
        assertNull(bossRow(strings, overlay),
                () -> "no boss row before the encounter began: " + overlay.rowTexts());
    }

    @Test
    void aBossRunInTheFightShowsThePhaseReached() {
        GameOverOverlay overlay = overlay(result(true, 2, List.of()));
        String expected = strings.get(StringKey.STAT_BOSS) + " "
                + strings.format(StringKey.STAT_BOSS_PHASE, 2);
        assertTrue(overlay.rowTexts().contains(expected),
                () -> "the boss row names the phase reached: " + overlay.rowTexts());
    }

    @Test
    void aClearedWorldBossShowsClearedInsteadOfAPhase() {
        GameOverOverlay overlay = overlay(result(true, 2, List.of(RunConfig.DEFAULT_WORLD)));
        String expected = strings.get(StringKey.STAT_BOSS) + " "
                + strings.get(StringKey.STAT_BOSS_CLEARED);
        assertTrue(overlay.rowTexts().contains(expected),
                () -> "the boss row says cleared: " + overlay.rowTexts());
    }

    @Test
    void aRunWithoutBossesNeverShowsABossRow() {
        GameOverOverlay overlay = overlay(result(false, 0, List.of()));
        assertNull(bossRow(strings, overlay),
                () -> "a bossless run has no boss row: " + overlay.rowTexts());
    }
}
