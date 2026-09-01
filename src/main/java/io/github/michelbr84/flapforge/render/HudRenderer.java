package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * The in-run HUD (plan section 5 cosmetic rows, D18).
 *
 * <p>Upstream drew the score centred at {@code H / 10} in bold 32 and blinked the start prompt on
 * a 30-frame counter (hidden while {@code flashCount <= 30}, shown until 60, then reset), which
 * at 60 Hz is {@value #BLINK_HALF_TICKS} ticks off and {@value #BLINK_HALF_TICKS} on. Flapforge
 * keeps both: {@link #SCORE_BASELINE_Y} is {@code 640 / 10} rounded to the pixel grid, the score
 * is outlined so it stays readable over a pipe, and the READY hint uses the same blink.
 *
 * <p>A seeded run also shows its seed in small type at the bottom, so a screenshot is enough to
 * reproduce it (D12).
 */
public final class HudRenderer {

    /** Baseline of the score, upstream's {@code FRAME_HEIGHT / 10}. */
    public static final int SCORE_BASELINE_Y = 64;
    /** Point size of the score, upstream's bold 32. */
    public static final int SCORE_SIZE = 32;
    /** Half of the blink period: the prompt is hidden this long, then shown this long. */
    public static final int BLINK_HALF_TICKS = 60;
    /** Full blink period in ticks. */
    public static final int BLINK_PERIOD_TICKS = 2 * BLINK_HALF_TICKS;
    /** Baseline of the READY hint. */
    public static final int HINT_BASELINE_Y = 392;
    /** Baseline of the seed line. */
    public static final int SEED_BASELINE_Y = Playfield.HEIGHT - 12;

    private static final Color SEED_COLOR = new Color(0x1C, 0x3A, 0x3E, 0xB0);

    private String readyHint;
    private int ticks;
    private int scoreShown = -1;
    private String scoreText = "";

    /**
     * Creates the HUD.
     *
     * @param readyHint the text blinked while the run waits for its first flap
     */
    public HudRenderer(String readyHint) {
        this.readyHint = readyHint;
    }

    /**
     * Replaces the READY hint (a live language switch, D25).
     *
     * @param readyHint the new text
     */
    public void setReadyHint(String readyHint) {
        this.readyHint = readyHint;
    }

    /**
     * The text blinked while the run waits for its first flap.
     *
     * @return the hint
     */
    public String readyHint() {
        return readyHint;
    }

    /** Advances the blink by one tick. */
    public void tick() {
        ticks++;
        if (ticks >= BLINK_PERIOD_TICKS) {
            ticks = 0;
        }
    }

    /** Restarts the blink and the cached score text (a new run). */
    public void reset() {
        ticks = 0;
        scoreShown = -1;
        scoreText = "";
    }

    /**
     * Whether the blinking prompt is currently visible.
     *
     * @return {@code true} during the second half of the period, as upstream
     */
    public boolean promptVisible() {
        return ticks >= BLINK_HALF_TICKS;
    }

    /**
     * Draws the score, the READY hint and the seed line.
     *
     * <p>The score string is re-created only when the score changes and the seed line is passed in
     * already formatted, so a frame allocates no text (D18).
     *
     * @param g the context in logical coordinates
     * @param run the run
     * @param palette the world palette
     * @param seedText the pre-formatted seed line, or {@code null} when the run is not seeded
     */
    public void render(Graphics2D g, Run run, WorldPalette palette, String seedText) {
        int score = run.stats().gatesPassed();
        if (score != scoreShown) {
            scoreShown = score;
            scoreText = Integer.toString(score);
        }
        g.setFont(Fonts.bold(SCORE_SIZE));
        TextPainter.drawOutlined(g, scoreText, Playfield.WIDTH / 2.0, SCORE_BASELINE_Y,
                Align.CENTER, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.color(palette, ProceduralArt.Tone.LETTERBOX), 2);

        if (run.phase() == RunPhase.READY && promptVisible()) {
            g.setFont(Fonts.bold(16));
            TextPainter.drawOutlined(g, readyHint, Playfield.WIDTH / 2.0, HINT_BASELINE_Y,
                    Align.CENTER, ProceduralArt.TEXT_LIGHT,
                    ProceduralArt.color(palette, ProceduralArt.Tone.LETTERBOX), 2);
        }

        if (seedText != null) {
            g.setFont(Fonts.regular(11));
            g.setColor(SEED_COLOR);
            TextPainter.drawRight(g, seedText, Playfield.WIDTH - 10.0, SEED_BASELINE_Y);
        }
    }
}
