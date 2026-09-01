package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/**
 * The in-run HUD (plan section 5 cosmetic rows, D18).
 *
 * <p>Upstream drew the score centred at {@code H / 10} in bold 32 and blinked the start prompt on
 * a 30-frame counter (hidden while {@code flashCount <= 30}, shown until 60, then reset), which
 * at 60 Hz is {@value #BLINK_HALF_TICKS} ticks off and {@value #BLINK_HALF_TICKS} on. Flapforge
 * keeps both: {@link #SCORE_BASELINE_Y} is {@code 640 / 10} rounded to the pixel grid, the score
 * is outlined so it stays readable over a pipe, and the READY hint uses the same blink.
 *
 * <p>M3 adds two readouts. The coins picked up in the world sit in the top-left corner behind a
 * small spinning coin icon drawn by {@link ProceduralArt#drawCoin}, so the counter is legible
 * without a legend. The clean-gate streak (D26) sits under the score, shown only while a streak
 * is running, and grows a flame once the streak has reached {@code economy.rewards.streak.step} —
 * the length that actually pays a reward step (E32.a) — so the player can see the bonus coming
 * rather than having to count gates. A seeded run also shows its seed in small type at the
 * bottom, so a screenshot is enough to reproduce it (D12).
 *
 * <p>The renderer never reads the string table (D18): the screen hands it the already-translated
 * patterns through {@link #setStreakLabel(String)} and {@link #setCoinLabel(String)} and this
 * class only substitutes the number. Both strings are rebuilt only when their number changes, so
 * a steady frame allocates nothing.
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
    /** Baseline of the streak line, just under the score. */
    public static final int STREAK_BASELINE_Y = SCORE_BASELINE_Y + 24;
    /** Baseline of the READY hint. */
    public static final int HINT_BASELINE_Y = 392;
    /** Baseline of the seed line. */
    public static final int SEED_BASELINE_Y = Playfield.HEIGHT - 12;
    /** Centre of the coin icon of the coin counter. */
    public static final int COIN_ICON_CX = 22;
    /** Centre of the coin counter's line. */
    public static final int COIN_ICON_CY = 30;
    /** Radius of the coin icon. */
    public static final int COIN_ICON_RADIUS = 7;
    /** Point size of the coin count. */
    public static final int COIN_SIZE = 15;

    private static final Color SEED_COLOR = new Color(0x1C, 0x3A, 0x3E, 0xB0);
    private static final Color FLAME_CORE = new Color(0xFF, 0xE1, 0x8A);
    private static final Color FLAME_EDGE = new Color(0xFF, 0x8C, 0x2B);
    /** Gap between the flame and the streak text. */
    private static final int FLAME_GAP = 9;

    private final Ellipse2D.Double coinShape = new Ellipse2D.Double();
    private final int[] flameX = new int[3];
    private final int[] flameY = new int[3];
    private String readyHint;
    private String streakLabel = "";
    private String coinLabel = "";
    private int streakStep;
    private int ticks;
    private long animTicks;
    private int scoreShown = -1;
    private String scoreText = "";
    private int streakShown = -1;
    private String streakText = "";
    private int coinsShown = -1;
    private String coinsText = "";

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

    /**
     * Sets the streak line's template. The renderer must not read the string table itself (D18),
     * so the screen hands it the already-translated {@code hud.streak} pattern and this class only
     * substitutes the number.
     *
     * @param streakLabel the template, {@code {0}} standing for the streak length
     */
    public void setStreakLabel(String streakLabel) {
        this.streakLabel = streakLabel == null ? "" : streakLabel;
        this.streakShown = -1;
    }

    /**
     * The streak line as it is drawn, or an empty string while no streak is running.
     *
     * @return the text
     */
    public String streakText() {
        return streakText;
    }

    /**
     * Sets the coin counter's template, already translated by the screen (D18).
     *
     * @param coinLabel the template, {@code {0}} standing for the coins picked up
     */
    public void setCoinLabel(String coinLabel) {
        this.coinLabel = coinLabel == null ? "" : coinLabel;
        this.coinsShown = -1;
    }

    /**
     * The coin counter as it is drawn.
     *
     * @return the text
     */
    public String coinText() {
        return coinsText;
    }

    /**
     * Sets the streak length that pays one reward step ({@code economy.rewards.streak.step},
     * E32.a). A streak at or above it is drawn with a flame.
     *
     * @param streakStep the step; values below 1 turn the flame off
     */
    public void setStreakStep(int streakStep) {
        this.streakStep = streakStep;
    }

    /**
     * The streak length that pays one reward step.
     *
     * @return the step
     */
    public int streakStep() {
        return streakStep;
    }

    /**
     * Whether the streak has reached a rewarding length, which is what the flame marks.
     *
     * @param streak the current streak
     * @return {@code true} when the flame is drawn next to the streak
     */
    public boolean isStreakHot(int streak) {
        return streakStep > 0 && streak >= streakStep;
    }

    /** Advances the blink and the coin spin by one tick. */
    public void tick() {
        ticks++;
        animTicks++;
        if (ticks >= BLINK_PERIOD_TICKS) {
            ticks = 0;
        }
    }

    /** Restarts the blink and the cached score, streak and coin text (a new run). */
    public void reset() {
        ticks = 0;
        animTicks = 0;
        scoreShown = -1;
        scoreText = "";
        streakShown = -1;
        streakText = "";
        coinsShown = -1;
        coinsText = "";
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
     * <p>The score and streak strings are re-created only when their number changes and the seed
     * line is passed in already formatted, so a frame allocates no text (D18).
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

        int streak = run.stats().streak();
        if (streak != streakShown) {
            streakShown = streak;
            streakText = streak <= 0 || streakLabel.isEmpty()
                    ? "" : streakLabel.replace("{0}", Integer.toString(streak));
        }
        if (!streakText.isEmpty()) {
            g.setFont(Fonts.bold(13));
            TextPainter.drawOutlined(g, streakText, Playfield.WIDTH / 2.0, STREAK_BASELINE_Y,
                    Align.CENTER, ProceduralArt.TEXT_LIGHT,
                    ProceduralArt.color(palette, ProceduralArt.Tone.LETTERBOX), 2);
            if (isStreakHot(streak)) {
                double left = Playfield.WIDTH / 2.0 - TextPainter.width(g, streakText) / 2.0;
                drawFlame(g, left - FLAME_GAP, STREAK_BASELINE_Y - 5.0);
            }
        }

        int coins = run.stats().coinsCollected();
        if (coins != coinsShown) {
            coinsShown = coins;
            coinsText = coinLabel.isEmpty() ? Integer.toString(coins)
                    : coinLabel.replace("{0}", Integer.toString(coins));
        }
        ProceduralArt.drawCoin(g, coinShape, COIN_ICON_CX, COIN_ICON_CY, COIN_ICON_RADIUS,
                ProceduralArt.coinSpin(animTicks));
        g.setFont(Fonts.bold(COIN_SIZE));
        TextPainter.drawOutlined(g, coinsText, COIN_ICON_CX + COIN_ICON_RADIUS + 8.0,
                TextPainter.centeredBaseline(g, COIN_ICON_CY), Align.LEFT,
                ProceduralArt.TEXT_LIGHT,
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

    /**
     * Draws the streak flame: two stacked triangles, filled through the polygon arrays this
     * renderer owns, so the marker costs no allocation and no shape object.
     *
     * @param g the context
     * @param rightX the right edge of the flame
     * @param baselineY the baseline of the streak text the flame sits on
     */
    private void drawFlame(Graphics2D g, double rightX, double baselineY) {
        double w = 9;
        double h = 13;
        double cx = rightX - w / 2;
        double bottom = baselineY + 2;
        g.setColor(FLAME_EDGE);
        triangle(g, cx, bottom - h, cx - w / 2, bottom, cx + w / 2, bottom);
        g.setColor(FLAME_CORE);
        triangle(g, cx, bottom - h * 0.55, cx - w * 0.24, bottom - 1, cx + w * 0.24, bottom - 1);
    }

    private void triangle(Graphics2D g, double x0, double y0, double x1, double y1, double x2,
            double y2) {
        flameX[0] = (int) Math.round(x0);
        flameY[0] = (int) Math.round(y0);
        flameX[1] = (int) Math.round(x1);
        flameY[1] = (int) Math.round(y1);
        flameX[2] = (int) Math.round(x2);
        flameY[2] = (int) Math.round(y2);
        g.fillPolygon(flameX, flameY, 3);
    }
}
