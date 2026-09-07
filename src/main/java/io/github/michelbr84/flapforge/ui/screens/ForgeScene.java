package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.WorldPalette;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * The forge scene in the middle of the home hub (D17, D18, M10): a floating island with the
 * anvil and the selected bird on it, dressed with more the further the upgrade trees have been
 * built. The island takes the selected world's palette, so the hub reads as "where the next run
 * goes".
 *
 * <p>Stages, from {@link #stageOf}: 0 island, anvil and bird, cold; 1 the hearth is lit, with a
 * pulsing glow, and a hammer leans on the anvil; 2 a barrel; 3 a banner in the world's accent;
 * 4 a second barrel and a brazier; 5 gold trim on the anvil, a taller flame and a periodic
 * sparkle. A prestiged profile carries the gold trim at any stage.
 *
 * <p>Per-frame allocation-free: the shapes are the {@link ProceduralArt} unit shapes, the
 * pulses are triangle waves on the tick, and the glow is a quantised colour ramp. Under reduce
 * flashing the glow peak is capped at {@link ParticleSystem#REDUCED_PEAK_ALPHA}; in high
 * contrast the glow is halved and the island gets a 2 px outline.
 */
final class ForgeScene {

    /** Upgrade levels at which each stage starts (stage 1 at the first level bought, ...). */
    static final int[] STAGE_THRESHOLDS = {1, 6, 14, 22, 36};
    /** The highest stage. */
    static final int MAX_STAGE = STAGE_THRESHOLDS.length;
    /** Ticks between two sparkles at the top stage. */
    static final int SPARKLE_PERIOD_TICKS = 90;
    /** Ticks between two sparkles under reduce flashing. */
    static final int SPARKLE_PERIOD_REDUCED_TICKS = 180;
    /** Ticks of one glow pulse. */
    static final int GLOW_PERIOD_TICKS = 60;
    /** Peak alpha of the hearth glow. */
    static final double GLOW_PEAK = 0x70 / 255.0;

    /** Centre x of the anvil and the bird. */
    static final int ANVIL_CX = 210;
    /** Top of the anvil. */
    static final int ANVIL_TOP = 300;
    /** Width of the anvil. */
    static final int ANVIL_W = 88;
    /** Centre y of the bird before the bob. */
    static final int BIRD_CY = 279;
    /** Size of the bird portrait. */
    static final int BIRD_SIZE = 56;

    private static final int ISLAND_X = 85;
    private static final int ISLAND_Y = 344;
    private static final int ISLAND_W = 250;
    private static final int ISLAND_H = 54;
    private static final int HEARTH_X = 262;
    private static final int HEARTH_Y = 318;
    private static final int WING_PERIOD_TICKS = 48;
    private static final Color SHADOW = new Color(0x00, 0x00, 0x00, 0x40);
    private static final Color STEEL = new Color(0x9AA7AA);
    private static final Color IRON = new Color(0x3A4548);
    private static final Color HOOP = new Color(0x2E2418);
    private static final Stroke OUTLINE = new BasicStroke(2f);

    private WorldPalette palette = WorldPalette.GREEN_FIELDS;
    private GameContent content;
    private PlayerProfile profile;
    private int stage;
    private boolean prestiged;
    private boolean reduceFlashing;
    private Color[] glow = ProceduralArt.alphaRamp(ProceduralArt.FLAME_EDGE, 16);
    private Color soil = new Color(WorldPalette.darken(WorldPalette.GREEN_FIELDS.ground(), 0.3));
    private Color grass = new Color(WorldPalette.GREEN_FIELDS.pipe());
    private Color rock = new Color(WorldPalette.darken(WorldPalette.GREEN_FIELDS.ground(), 0.5));
    private Color accent = new Color(WorldPalette.GREEN_FIELDS.accent());
    private Color anvil = new Color(WorldPalette.GREEN_FIELDS.letterbox());

    /**
     * The stage a number of bought upgrade levels reaches.
     *
     * @param upgradeLevelsTotal the levels bought across every tree
     * @return the stage in {@code [0, MAX_STAGE]}
     */
    static int stageOf(int upgradeLevelsTotal) {
        int reached = 0;
        for (int i = 0; i < STAGE_THRESHOLDS.length; i++) {
            if (upgradeLevelsTotal >= STAGE_THRESHOLDS[i]) {
                reached = i + 1;
            }
        }
        return reached;
    }

    /**
     * Points the scene at a world, a profile and a stage.
     *
     * @param newPalette the selected world's palette
     * @param newContent the loaded content the bird is resolved through, or {@code null}
     * @param newProfile the profile whose bird sits on the anvil, or {@code null} for classic
     * @param newStage the stage in {@code [0, MAX_STAGE]}
     * @param isPrestiged whether the profile has prestiged (gold trim at any stage)
     */
    void bind(WorldPalette newPalette, GameContent newContent, PlayerProfile newProfile,
            int newStage, boolean isPrestiged) {
        if (newPalette != palette) {
            palette = newPalette;
            soil = new Color(WorldPalette.darken(palette.ground(), 0.3));
            grass = new Color(palette.pipe());
            rock = new Color(WorldPalette.darken(palette.ground(), 0.5));
            accent = new Color(palette.accent());
            anvil = new Color(palette.letterbox());
        }
        content = newContent;
        profile = newProfile;
        stage = Math.max(0, Math.min(MAX_STAGE, newStage));
        prestiged = isPrestiged;
    }

    /**
     * Caps the glow and thins the sparkles for players who asked for less flashing.
     *
     * @param value the flag
     */
    void setReduceFlashing(boolean value) {
        reduceFlashing = value;
    }

    /**
     * The stage shown.
     *
     * @return the stage
     */
    int stage() {
        return stage;
    }

    /**
     * Whether the top stage emits a sparkle this tick.
     *
     * @param ticks the screen's tick count
     * @return {@code true} on the sparkle ticks of the top stage
     */
    boolean sparkleDue(long ticks) {
        int period = reduceFlashing ? SPARKLE_PERIOD_REDUCED_TICKS : SPARKLE_PERIOD_TICKS;
        return stage >= MAX_STAGE && ticks % period == 0;
    }

    /**
     * The hearth glow alpha at a tick: a triangle wave up to the peak, capped under reduce
     * flashing and halved in high contrast.
     *
     * @param ticks the screen's tick count
     * @return the alpha in {@code [0, 1]}
     */
    double glowAlpha(long ticks) {
        if (stage < 1) {
            return 0;
        }
        double phase = (ticks % GLOW_PERIOD_TICKS) / (double) GLOW_PERIOD_TICKS;
        double wave = phase < 0.5 ? phase * 2 : 2 - phase * 2;
        double peak = reduceFlashing ? Math.min(GLOW_PEAK, ParticleSystem.REDUCED_PEAK_ALPHA)
                : GLOW_PEAK;
        if (Accessibility.isHighContrast()) {
            peak *= 0.5;
        }
        return peak * (0.5 + 0.5 * wave);
    }

    /**
     * Draws the scene.
     *
     * @param g the context
     * @param alpha the interpolation fraction (unused: the scene is tick-driven)
     * @param bob the bird's vertical offset this frame
     * @param ticks the screen's tick count
     */
    void render(Graphics2D g, double alpha, double bob, long ticks) {
        boolean highContrast = Accessibility.isHighContrast();
        // The island: an under-shadow, the soil, the grass rim and two rocks.
        g.setColor(SHADOW);
        g.fillOval(ISLAND_X + 8, ISLAND_Y + 10, ISLAND_W, ISLAND_H);
        g.setColor(soil);
        g.fillOval(ISLAND_X, ISLAND_Y, ISLAND_W, ISLAND_H);
        g.setColor(grass);
        g.fillOval(ISLAND_X, ISLAND_Y, ISLAND_W, (int) Math.round(ISLAND_H * 0.55));
        g.setColor(rock);
        g.fillOval(ISLAND_X + 30, ISLAND_Y + 6, 18, 10);
        g.fillOval(ISLAND_X + ISLAND_W - 60, ISLAND_Y + 12, 22, 12);
        if (highContrast) {
            Stroke old = g.getStroke();
            g.setStroke(OUTLINE);
            g.setColor(ProceduralArt.TEXT_DARK);
            g.drawOval(ISLAND_X, ISLAND_Y, ISLAND_W, ISLAND_H);
            g.setStroke(old);
        }

        // The hearth, lit from stage 1: a glow, an iron brazier and a flame that flickers.
        if (stage >= 1) {
            ProceduralArt.drawGlow(g, HEARTH_X, HEARTH_Y - 8, 28, glow, glowAlpha(ticks));
            g.setColor(IRON);
            g.fillRoundRect(HEARTH_X - 14, HEARTH_Y - 2, 28, 12, 4, 4);
            double flicker = ((ticks + 7) % 24) < 12 ? 2 : -2;
            double height = (stage >= MAX_STAGE ? 34 : 24) + flicker;
            ProceduralArt.drawFlame(g, HEARTH_X, HEARTH_Y - 2, 18, height,
                    ProceduralArt.FLAME_EDGE, ProceduralArt.FLAME_CORE);
        }
        if (stage >= 2) {
            ProceduralArt.drawBarrel(g, 122, 328, 30, ProceduralArt.WOOD, HOOP);
        }
        if (stage >= 4) {
            ProceduralArt.drawBarrel(g, 96, 334, 22, ProceduralArt.WOOD, HOOP);
            g.setColor(IRON);
            g.fillRoundRect(322, 330, 18, 8, 3, 3);
            ProceduralArt.drawFlame(g, 331, 330, 12, 16 + (ticks % 20 < 10 ? 1 : -1),
                    ProceduralArt.FLAME_EDGE, ProceduralArt.FLAME_CORE);
        }
        if (stage >= 3) {
            double wave = ((ticks % 96) < 48 ? (ticks % 96) : 96 - (ticks % 96)) / 48.0;
            ProceduralArt.drawBanner(g, 300, 244, 72, -0.08 + 0.16 * wave, IRON,
                    Accessibility.tone(accent));
        }

        // The anvil, the bird on it and the hammer leaning against it.
        ProceduralArt.drawAnvil(g, ANVIL_CX, ANVIL_TOP, ANVIL_W, anvil);
        if (stage >= MAX_STAGE || prestiged) {
            g.setColor(ProceduralArt.COIN_GOLD);
            g.fillRect(ANVIL_CX - ANVIL_W / 3, ANVIL_TOP + 5, 2 * ANVIL_W / 3, 3);
        }
        if (stage >= 1) {
            ProceduralArt.drawHammer(g, 158, 322, 30, -0.55, STEEL, ProceduralArt.WOOD);
        }
        double phase = (ticks % WING_PERIOD_TICKS) / (double) WING_PERIOD_TICKS;
        BirdPortrait.draw(g, content, profile, ANVIL_CX, BIRD_CY + bob, BIRD_SIZE, phase);
    }
}
