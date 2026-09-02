package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.ability.AbilityInstance;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.ShieldSystem;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * <p>M5 adds the ability panel in the top-left column (D9, D17). The badge carries a cooldown
 * ring that <em>fills</em> as the ability recharges — a full ring means "usable now", which is the
 * question a player asks mid-flight — with the charge pips of a charge-gated ability under it and,
 * while the effect window runs, a duration bar instead of the cooldown readout. Below them the
 * shield charges are small crests, one per charge, because a shield exists whenever
 * {@code SHIELD_CHARGES} resolves above zero and therefore also without any ability equipped.
 * Three flashes ride on top: a pulse on activation, a fading ring where a consumed shield charge
 * was, and a red blink when the ability key was pressed and nothing happened
 * ({@link #flashAbilityRefused()}). The first two are derived from the counters the simulation
 * keeps, not from a fact the screen forwards, so a replayed run flashes on exactly the same ticks.
 *
 * <p>M6 adds the build (D27) under the shield row: one compact chip per taken modifier — with its
 * stack count once it has been taken twice — and the active set bonuses under them in their own
 * colour, so what the drafts have turned the run into is readable without pausing. A run that
 * drafts nothing draws nothing, which is what keeps the classic HUD exactly as M1 left it. The
 * streak line gains the indicator M3 deferred: what one more clean-gate step is worth
 * ({@code economy.rewards.streak.coins} plus the taken cards' own bonuses, E32.a), which only
 * became worth showing once a modifier could change it mid-run.
 *
 * <p>The renderer never reads the string table (D18): the screen hands it the already-translated
 * patterns through {@link #setStreakLabel(String)}, {@link #setCoinLabel(String)},
 * {@link #setAbilityName(String)}, {@link #setAbilityStateLabels(String, String)},
 * {@link #setShieldLabel(String)}, {@link #setBuild(java.util.List, java.util.List)} and
 * {@link #setStreakBonusText(String)}, and this class only substitutes the number. Every string is
 * rebuilt only when its number changes, and every shape, polygon and colour ramp is owned by the
 * renderer, so a steady frame allocates nothing.
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

    /** Centre x of the ability badge. */
    public static final int ABILITY_CX = 30;
    /** Centre y of the ability badge. */
    public static final int ABILITY_CY = 80;
    /** Outer radius of the cooldown ring. */
    public static final int ABILITY_RADIUS = 17;
    /** Centre y of the charge pips, under the badge. */
    public static final int CHARGE_ROW_CY = ABILITY_CY + ABILITY_RADIUS + 8;
    /** Radius of one charge pip. */
    public static final int CHARGE_PIP_RADIUS = 4;
    /** Horizontal distance between two charge pips. */
    public static final int CHARGE_PIP_STEP = 11;
    /** Left edge of the ability name, the state line and the duration bar. */
    public static final int ABILITY_TEXT_X = ABILITY_CX + ABILITY_RADIUS + 9;
    /** Width of the duration bar. */
    public static final int DURATION_BAR_W = 92;
    /** Height of the duration bar. */
    public static final int DURATION_BAR_H = 6;
    /** Centre y of the shield icon row. */
    public static final int SHIELD_ROW_CY = CHARGE_ROW_CY + 20;
    /** Left edge of the shield icon row. */
    public static final int SHIELD_ROW_X = 16;
    /** Width one shield icon occupies. */
    public static final int SHIELD_ICON_STEP = 15;
    /** Half-width of one shield icon. */
    public static final int SHIELD_ICON_HALF = 5;
    /** How long a consumed charge, an activation or a refusal is flashed, in ticks. */
    public static final int FLASH_TICKS = 24;
    /** Period of the refusal blink, in ticks. */
    public static final int REFUSAL_BLINK_TICKS = 8;

    /** Baseline of the first chip of the build strip (M6). */
    public static final int BUILD_TOP_Y = SHIELD_ROW_CY + 22;
    /** Left edge of the build strip. */
    public static final int BUILD_X = 14;
    /** Height of one chip of the build strip. */
    public static final int BUILD_CHIP_H = 14;
    /** Vertical distance between two chips. */
    public static final int BUILD_ROW_STEP = 16;
    /** Widest chip the build strip draws. */
    public static final int BUILD_CHIP_MAX_W = 118;
    /** Point size of a chip label. */
    public static final int BUILD_CHIP_SIZE = 10;
    /** Most chips the build strip shows before it stops (a 640 px column, D17). */
    public static final int BUILD_MAX_ROWS = 12;
    /** Baseline of the streak-bonus line, under the streak. */
    public static final int STREAK_BONUS_BASELINE_Y = STREAK_BASELINE_Y + 15;
    /** Baseline of the world name while the run waits for its first flap (M7). */
    public static final int WORLD_BASELINE_Y = HINT_BASELINE_Y - 30;
    /**
     * Baseline of the world name once the run flies: in the top strip under the streak lines,
     * out of the bird's lane and off the first column (M7).
     */
    public static final int WORLD_FLYING_BASELINE_Y = STREAK_BONUS_BASELINE_Y + 16;
    /** Flying ticks the world name stays up after the first flap (M7). */
    public static final int WORLD_NAME_TICKS = 120;

    private static final Color SEED_COLOR = new Color(0x1C, 0x3A, 0x3E, 0xB0);
    private static final Color FLAME_CORE = new Color(0xFF, 0xE1, 0x8A);
    private static final Color FLAME_EDGE = new Color(0xFF, 0x8C, 0x2B);
    /** Gap between the flame and the streak text. */
    private static final int FLAME_GAP = 9;

    private static final Color BADGE_BACK = new Color(0x10, 0x1C, 0x1E, 0xB4);
    private static final Color RING_EMPTY = new Color(0x4A, 0x6A, 0x6C, 0xC0);
    private static final Color RING_FULL = new Color(0x6F, 0xD1, 0xA8);
    private static final Color RING_CHARGING = new Color(0x3E, 0x9C, 0xC0);
    private static final Color RING_REFUSED = new Color(0xE8, 0x5A, 0x4A);
    private static final Color GLYPH = new Color(0xF4, 0xF8, 0xF8);
    private static final Color PIP_EMPTY = new Color(0x24, 0x38, 0x3A, 0xC8);
    private static final Color SHIELD_FILL = new Color(0x7E, 0xC8, 0xF0);
    private static final Color SHIELD_EMPTY = new Color(0x24, 0x38, 0x3A, 0xB0);
    private static final Color DURATION_BACK = new Color(0x10, 0x1C, 0x1E, 0xA0);
    private static final Color CHIP_BACK = new Color(0x10, 0x1C, 0x1E, 0xB4);
    private static final Color CHIP_BORDER = new Color(0x4A, 0x6A, 0x6C, 0xC0);
    private static final Color SYNERGY_CHIP_BACK = new Color(0x3A, 0x2E, 0x0C, 0xC8);
    private static final Color SYNERGY_TEXT = new Color(0xF5, 0xC5, 0x42);
    private static final Stroke RING_STROKE = new BasicStroke(4f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND);
    private static final Stroke FLASH_STROKE = new BasicStroke(2f);
    private static final Stroke PIP_STROKE = new BasicStroke(1.5f);
    private static final Color[] ACTIVATION_FADE = fadeRamp(RING_FULL);
    private static final Color[] SHIELD_FADE = fadeRamp(SHIELD_FILL);

    private final Ellipse2D.Double coinShape = new Ellipse2D.Double();
    private final Ellipse2D.Double pipShape = new Ellipse2D.Double();
    private final Arc2D.Double ringShape = new Arc2D.Double(Arc2D.OPEN);
    private final int[] flameX = new int[3];
    private final int[] flameY = new int[3];
    private final int[] glyphX = new int[4];
    private final int[] glyphY = new int[4];
    private final int[] shieldX = new int[5];
    private final int[] shieldY = new int[5];
    private final List<String> buildChips = new ArrayList<>();
    private final List<String> synergyChips = new ArrayList<>();
    private String readyHint;
    private String worldName = "";
    private int worldNameTicks;
    private String streakLabel = "";
    private String coinLabel = "";
    private String abilityName = "";
    private String abilityReadyLabel = "";
    private String abilityCooldownLabel = "";
    private String shieldLabel = "";
    private int streakStep;
    private int ticks;
    private long animTicks;
    private int scoreShown = -1;
    private String scoreText = "";
    private int streakShown = -1;
    private String streakText = "";
    private int coinsShown = -1;
    private String coinsText = "";
    private int cooldownShown = -1;
    private String cooldownText = "";
    private int shieldShownCount = -1;
    private String shieldText = "";
    private String streakBonusText = "";
    private int shieldCharges = -1;
    private int shieldFlashIndex;
    private int shieldFlashTicks;
    private int abilityActivations;
    private int abilityFlashTicks;
    private int refusedTicks;

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
     * Names the world the run is played in (M7): shown above the READY hint while the run waits
     * for its first flap — nothing is on the playfield yet — and, smaller, in the top strip under
     * the streak lines for {@value #WORLD_NAME_TICKS} ticks after it starts, so the player knows
     * where they are without a label over their lane or the first column.
     *
     * @param worldName the translated name, or {@code null}/empty for none
     */
    public void setWorldName(String worldName) {
        this.worldName = worldName == null ? "" : worldName;
    }

    /**
     * The world name the HUD shows.
     *
     * @return the name, empty when none is set
     */
    public String worldName() {
        return worldName;
    }

    /**
     * Whether the world name is on screen for a run in a phase.
     *
     * @param phase the run phase
     * @return {@code true} while the name is drawn
     */
    public boolean worldNameVisible(RunPhase phase) {
        return !worldName.isEmpty()
                && (phase == RunPhase.READY || worldNameTicks < WORLD_NAME_TICKS);
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

    /**
     * Sets the name of the equipped active ability, already translated by the screen (D18).
     * An empty name hides the ability badge, which is what a run without an active ability shows.
     *
     * @param abilityName the name, or {@code null} for none
     */
    public void setAbilityName(String abilityName) {
        this.abilityName = abilityName == null ? "" : abilityName;
    }

    /**
     * The name of the ability the badge is about.
     *
     * @return the name, empty when no badge is drawn
     */
    public String abilityName() {
        return abilityName;
    }

    /**
     * Sets the two ability state labels, already translated by the screen (D18).
     *
     * @param readyLabel the word shown when the ability is off cooldown
     * @param cooldownLabel the pattern of the cooldown readout, {@code {0}} standing for the
     *     ticks left
     */
    public void setAbilityStateLabels(String readyLabel, String cooldownLabel) {
        this.abilityReadyLabel = readyLabel == null ? "" : readyLabel;
        this.abilityCooldownLabel = cooldownLabel == null ? "" : cooldownLabel;
        this.cooldownShown = -1;
    }

    /**
     * Sets the shield readout's template, already translated by the screen (D18).
     *
     * @param shieldLabel the pattern, {@code {0}} standing for the charges left
     */
    public void setShieldLabel(String shieldLabel) {
        this.shieldLabel = shieldLabel == null ? "" : shieldLabel;
        this.shieldShownCount = -1;
    }

    /**
     * The shield readout as it is drawn.
     *
     * @return the text, empty while the run has no shield charge at all
     */
    public String shieldText() {
        return shieldText;
    }

    /**
     * The ability state line as it is drawn: the ready word, or the cooldown readout.
     *
     * @return the text, empty while no active ability is equipped
     */
    public String abilityStateText() {
        return cooldownText;
    }

    /**
     * Sets the build the run has drafted so far (M6, D27), already translated by the screen (D18):
     * one label per taken modifier — the name with its stack count when it was taken more than
     * once — and one per active set bonus.
     *
     * <p>Handed in as finished strings rather than derived here for the reason every other HUD
     * label is: the renderer must not read the string table, and a frame must allocate nothing.
     * The screen rebuilds these lists only when the taken set actually changes, which during a
     * run is at most once per draft.
     *
     * @param modifiers one label per taken modifier, in take order
     * @param synergies one label per active synergy, in content order
     */
    public void setBuild(List<String> modifiers, List<String> synergies) {
        buildChips.clear();
        synergyChips.clear();
        if (modifiers != null) {
            buildChips.addAll(modifiers);
        }
        if (synergies != null) {
            synergyChips.addAll(synergies);
        }
    }

    /**
     * The modifier labels the build strip draws.
     *
     * @return an unmodifiable view, in take order
     */
    public List<String> buildChips() {
        return Collections.unmodifiableList(buildChips);
    }

    /**
     * The synergy labels the build strip draws.
     *
     * @return an unmodifiable view, in content order
     */
    public List<String> synergyChips() {
        return Collections.unmodifiableList(synergyChips);
    }

    /**
     * Sets the streak-bonus readout (D26, E32.a): what one more clean-gate streak step is worth,
     * already translated and substituted by the screen (D18). It is the indicator M3 left for
     * later, because before the drafts existed the number never changed within a run.
     *
     * @param text the line, or {@code null}/empty to draw none
     */
    public void setStreakBonusText(String text) {
        this.streakBonusText = text == null ? "" : text;
    }

    /**
     * The streak-bonus readout as it is drawn.
     *
     * @return the text, empty when the run pays no streak bonus
     */
    public String streakBonusText() {
        return streakBonusText;
    }

    /**
     * Flashes the ability badge in the refusal colour: the player pressed the ability key and
     * nothing happened (on cooldown, out of charges, or stripped by the run's rules, D9).
     */
    public void flashAbilityRefused() {
        refusedTicks = FLASH_TICKS;
    }

    /**
     * Whether the refusal flash is running.
     *
     * @return {@code true} while the badge is blinking red
     */
    public boolean isAbilityRefused() {
        return refusedTicks > 0;
    }

    /**
     * Whether the consumed-charge flash is running.
     *
     * @return {@code true} while a spent shield charge is being flashed
     */
    public boolean isShieldFlashing() {
        return shieldFlashTicks > 0;
    }

    /**
     * Whether the activation flash is running.
     *
     * @return {@code true} just after the ability was activated
     */
    public boolean isAbilityFlashing() {
        return abilityFlashTicks > 0;
    }

    /** Advances the blink and the coin spin by one tick, watching nothing. */
    public void tick() {
        tick(null);
    }

    /**
     * Advances the blink, the coin spin and the three flashes by one tick, and watches the run
     * for the two things that deserve one: a spent shield charge and an ability activation.
     *
     * <p>The flashes are derived from counters the simulation already keeps
     * ({@code ShieldSystem.charges}, {@code AbilityInstance.activations}) rather than from a fact
     * the screen forwards, so a HUD driven by a replayed run flashes at exactly the same ticks
     * the live one did.
     *
     * @param run the run being played, or {@code null} outside one
     */
    public void tick(Run run) {
        ticks++;
        animTicks++;
        if (ticks >= BLINK_PERIOD_TICKS) {
            ticks = 0;
        }
        if (shieldFlashTicks > 0) {
            shieldFlashTicks--;
        }
        if (abilityFlashTicks > 0) {
            abilityFlashTicks--;
        }
        if (refusedTicks > 0) {
            refusedTicks--;
        }
        if (run == null) {
            return;
        }
        if (run.phase() != RunPhase.READY && worldNameTicks < WORLD_NAME_TICKS) {
            worldNameTicks++;
        }
        int charges = run.simulation().shield().charges();
        if (shieldCharges >= 0 && charges < shieldCharges) {
            shieldFlashTicks = FLASH_TICKS;
            shieldFlashIndex = charges;
        }
        shieldCharges = charges;
        AbilityInstance active = run.simulation().abilities().active();
        int activations = active == null ? 0 : active.activations();
        if (activations > abilityActivations) {
            abilityFlashTicks = FLASH_TICKS;
        }
        abilityActivations = activations;
    }

    /** Restarts the blink and the cached score, streak and coin text (a new run). */
    public void reset() {
        ticks = 0;
        animTicks = 0;
        worldNameTicks = 0;
        scoreShown = -1;
        scoreText = "";
        streakShown = -1;
        streakText = "";
        coinsShown = -1;
        coinsText = "";
        cooldownShown = -1;
        cooldownText = "";
        shieldShownCount = -1;
        shieldText = "";
        shieldCharges = -1;
        shieldFlashTicks = 0;
        shieldFlashIndex = 0;
        abilityActivations = 0;
        abilityFlashTicks = 0;
        refusedTicks = 0;
        streakBonusText = "";
        buildChips.clear();
        synergyChips.clear();
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
            if (!streakBonusText.isEmpty()) {
                // What the next step pays, under the streak it is counting towards: the flame says
                // "a step is close", this says what the step is worth (D26, E32.a).
                g.setFont(Fonts.bold(11));
                TextPainter.drawOutlined(g, streakBonusText, Playfield.WIDTH / 2.0,
                        STREAK_BONUS_BASELINE_Y, Align.CENTER,
                        isStreakHot(streak) ? FLAME_CORE : ProceduralArt.TEXT_MUTED,
                        ProceduralArt.color(palette, ProceduralArt.Tone.LETTERBOX), 2);
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

        renderAbility(g, run, palette);
        renderShield(g, run, palette);
        renderBuild(g);

        if (run.phase() == RunPhase.READY && promptVisible()) {
            g.setFont(Fonts.bold(16));
            TextPainter.drawOutlined(g, readyHint, Playfield.WIDTH / 2.0, HINT_BASELINE_Y,
                    Align.CENTER, ProceduralArt.TEXT_LIGHT,
                    ProceduralArt.color(palette, ProceduralArt.Tone.LETTERBOX), 2);
        }
        if (worldNameVisible(run.phase())) {
            // Steady, not blinking: the hint asks for a key, the name only says where you are.
            // Once the run flies the label leaves the playfield for the HUD strip.
            boolean ready = run.phase() == RunPhase.READY;
            g.setFont(Fonts.bold(ready ? 15 : 12));
            TextPainter.drawOutlined(g, worldName, Playfield.WIDTH / 2.0,
                    ready ? WORLD_BASELINE_Y : WORLD_FLYING_BASELINE_Y,
                    Align.CENTER, ProceduralArt.accentColor(palette),
                    ProceduralArt.color(palette, ProceduralArt.Tone.LETTERBOX), 2);
        }

        if (seedText != null) {
            g.setFont(Fonts.regular(11));
            g.setColor(SEED_COLOR);
            TextPainter.drawRight(g, seedText, Playfield.WIDTH - 10.0, SEED_BASELINE_Y);
        }
    }

    /**
     * Draws the ability badge (M5, D9, D17): the cooldown ring, the glyph, the charge pips of a
     * charge-gated ability, the duration bar while the effect is running, and the name and state
     * beside them.
     *
     * <p>The ring <em>fills</em> as the ability recharges — a full ring means ready — because the
     * question the player asks mid-flight is "can I use it now", not "how long has it been". The
     * three flashes ride on top: a bright pulse on activation, a red blink when the key was
     * pressed and nothing happened.
     *
     * <p>Nothing is allocated: the arc and the ellipse are scratch objects of this renderer, the
     * glyph is an int polygon, and the state string is rebuilt only when its number changes.
     *
     * @param g the context
     * @param run the run
     * @param palette the world palette
     */
    private void renderAbility(Graphics2D g, Run run, WorldPalette palette) {
        AbilityInstance active = run.simulation().abilities().active();
        if (active == null) {
            cooldownShown = -1;
            cooldownText = "";
            return;
        }
        double fraction = 1 - active.cooldownFraction(run.simulation().stats());
        boolean ready = active.isReady();
        // The blink starts lit: the answer to "why did nothing happen" has to be on screen on the
        // very frame the key was pressed, not a sixth of a second later.
        boolean refused = refusedTicks > 0
                && ((FLASH_TICKS - refusedTicks) / REFUSAL_BLINK_TICKS) % 2 == 0;

        double left = ABILITY_CX - ABILITY_RADIUS;
        double top = ABILITY_CY - ABILITY_RADIUS;
        double size = 2.0 * ABILITY_RADIUS;
        pipShape.setFrame(left, top, size, size);
        g.setColor(BADGE_BACK);
        g.fill(pipShape);

        Stroke old = g.getStroke();
        g.setStroke(RING_STROKE);
        ringShape.setArc(left + 2, top + 2, size - 4, size - 4, 90, -360, Arc2D.OPEN);
        g.setColor(RING_EMPTY);
        g.draw(ringShape);
        if (fraction > 0) {
            ringShape.setArc(left + 2, top + 2, size - 4, size - 4, 90,
                    -360 * MathUtil.clamp(fraction, 0, 1), Arc2D.OPEN);
            g.setColor(refused ? RING_REFUSED : (ready ? RING_FULL : RING_CHARGING));
            g.draw(ringShape);
        }
        if (abilityFlashTicks > 0) {
            double grow = ABILITY_RADIUS + 5.0 * (FLASH_TICKS - abilityFlashTicks) / FLASH_TICKS;
            g.setStroke(FLASH_STROKE);
            g.setColor(fade(ACTIVATION_FADE, abilityFlashTicks));
            pipShape.setFrame(ABILITY_CX - grow, ABILITY_CY - grow, 2 * grow, 2 * grow);
            g.draw(pipShape);
        }
        g.setStroke(old);
        drawSpark(g, ABILITY_CX, ABILITY_CY, ABILITY_RADIUS * 0.42,
                refused ? RING_REFUSED : (ready ? GLYPH : ProceduralArt.TEXT_MUTED));

        Color outline = ProceduralArt.color(palette, ProceduralArt.Tone.LETTERBOX);
        if (!abilityName.isEmpty()) {
            g.setFont(Fonts.bold(12));
            TextPainter.drawOutlined(g, abilityName, ABILITY_TEXT_X, ABILITY_CY - 4.0, Align.LEFT,
                    ProceduralArt.TEXT_LIGHT, outline, 2);
        }

        if (active.isActive()) {
            double span = active.durationFraction(run.simulation().stats());
            g.setColor(DURATION_BACK);
            g.fillRoundRect(ABILITY_TEXT_X, ABILITY_CY + 3, DURATION_BAR_W, DURATION_BAR_H, 4, 4);
            g.setColor(RING_FULL);
            g.fillRoundRect(ABILITY_TEXT_X, ABILITY_CY + 3,
                    (int) Math.round(DURATION_BAR_W * span), DURATION_BAR_H, 4, 4);
            cooldownShown = -1;
            cooldownText = "";
        } else {
            int remaining = active.cooldownRemaining();
            if (remaining != cooldownShown) {
                cooldownShown = remaining;
                cooldownText = remaining == 0 ? abilityReadyLabel
                        : abilityCooldownLabel.replace("{0}", Integer.toString(remaining));
            }
            if (!cooldownText.isEmpty()) {
                g.setFont(Fonts.bold(11));
                TextPainter.drawOutlined(g, cooldownText, ABILITY_TEXT_X, ABILITY_CY + 11.0,
                        Align.LEFT, ready ? RING_FULL : ProceduralArt.TEXT_MUTED, outline, 2);
            }
        }

        int maxCharges = active.maxCharges();
        if (maxCharges > 0) {
            int held = active.charges();
            double startX = ABILITY_CX - (maxCharges - 1) * CHARGE_PIP_STEP / 2.0;
            for (int i = 0; i < maxCharges; i++) {
                double cx = startX + i * CHARGE_PIP_STEP;
                pipShape.setFrame(cx - CHARGE_PIP_RADIUS, CHARGE_ROW_CY - CHARGE_PIP_RADIUS,
                        2.0 * CHARGE_PIP_RADIUS, 2.0 * CHARGE_PIP_RADIUS);
                g.setColor(i < held ? RING_FULL : PIP_EMPTY);
                g.fill(pipShape);
                g.setStroke(PIP_STROKE);
                g.setColor(RING_EMPTY);
                g.draw(pipShape);
                g.setStroke(old);
            }
        }
    }

    /**
     * Draws the shield charges as small icons and their localised readout, plus the flash a
     * consumed charge leaves behind (D9: the charges are stat-driven, so this row appears for a
     * bare {@code SHIELD_CHARGES} upgrade with no ability equipped).
     *
     * @param g the context
     * @param run the run
     * @param palette the world palette
     */
    private void renderShield(Graphics2D g, Run run, WorldPalette palette) {
        ShieldSystem shield = run.simulation().shield();
        int max = shield.maxCharges();
        if (max <= 0) {
            shieldShownCount = -1;
            shieldText = "";
            return;
        }
        int held = shield.charges();
        for (int i = 0; i < max; i++) {
            double cx = SHIELD_ROW_X + i * SHIELD_ICON_STEP + SHIELD_ICON_HALF;
            drawShieldIcon(g, cx, SHIELD_ROW_CY, i < held ? SHIELD_FILL : SHIELD_EMPTY);
        }
        if (shieldFlashTicks > 0 && shieldFlashIndex < max) {
            double cx = SHIELD_ROW_X + shieldFlashIndex * SHIELD_ICON_STEP + SHIELD_ICON_HALF;
            double grow = SHIELD_ICON_HALF
                    + 7.0 * (FLASH_TICKS - shieldFlashTicks) / FLASH_TICKS;
            Stroke old = g.getStroke();
            g.setStroke(FLASH_STROKE);
            g.setColor(fade(SHIELD_FADE, shieldFlashTicks));
            pipShape.setFrame(cx - grow, SHIELD_ROW_CY - grow, 2 * grow, 2 * grow);
            g.draw(pipShape);
            g.setStroke(old);
        }
        if (held != shieldShownCount) {
            shieldShownCount = held;
            shieldText = shieldLabel.isEmpty() ? Integer.toString(held)
                    : shieldLabel.replace("{0}", Integer.toString(held));
        }
        g.setFont(Fonts.bold(11));
        TextPainter.drawOutlined(g, shieldText,
                SHIELD_ROW_X + max * (double) SHIELD_ICON_STEP + 4, SHIELD_ROW_CY + 4.0,
                Align.LEFT, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.color(palette, ProceduralArt.Tone.LETTERBOX), 2);
    }

    /**
     * Draws the build the run has drafted (M6, D27): the taken modifiers as compact chips down the
     * left column, with the active set bonuses under them in the synergy colour.
     *
     * <p>It sits below the shield row, so a run that drafts nothing draws nothing at all and the
     * classic HUD is unchanged. The labels are the finished strings the screen handed over, so a
     * frame here allocates nothing either: only the chip width is measured.
     *
     * @param g the context
     */
    private void renderBuild(Graphics2D g) {
        if (buildChips.isEmpty() && synergyChips.isEmpty()) {
            return;
        }
        double y = BUILD_TOP_Y;
        int rows = 0;
        g.setFont(Fonts.bold(BUILD_CHIP_SIZE));
        for (int i = 0; i < buildChips.size() && rows < BUILD_MAX_ROWS; i++, rows++) {
            drawChip(g, buildChips.get(i), y, CHIP_BACK, ProceduralArt.TEXT_LIGHT);
            y += BUILD_ROW_STEP;
        }
        for (int i = 0; i < synergyChips.size() && rows < BUILD_MAX_ROWS; i++, rows++) {
            drawChip(g, synergyChips.get(i), y, SYNERGY_CHIP_BACK, SYNERGY_TEXT);
            y += BUILD_ROW_STEP;
        }
    }

    /**
     * One chip of the build strip: a rounded plate sized to its label, clipped to
     * {@value #BUILD_CHIP_MAX_W} so a long translation cannot reach into the playfield.
     *
     * @param g the context, already carrying the chip font
     * @param label the finished label
     * @param top the top edge of the chip
     * @param back the plate colour
     * @param ink the text colour
     */
    private void drawChip(Graphics2D g, String label, double top, Color back, Color ink) {
        int wanted = TextPainter.width(g, label) + 12;
        int width = Math.min(BUILD_CHIP_MAX_W, wanted);
        int x = BUILD_X;
        int y = (int) Math.round(top);
        g.setColor(back);
        g.fillRoundRect(x, y, width, BUILD_CHIP_H, 6, 6);
        g.setColor(CHIP_BORDER);
        g.drawRoundRect(x, y, width, BUILD_CHIP_H, 6, 6);
        g.setColor(ink);
        if (wanted <= width) {
            // The common case: the chip was sized to its label, so nothing has to be clipped —
            // and no clip is taken, because reading the old one allocates a rectangle per chip.
            TextPainter.draw(g, label, x + 6.0, y + BUILD_CHIP_H - 4.0);
            return;
        }
        Shape clip = g.getClip();
        g.clipRect(x, y, width - 2, BUILD_CHIP_H);
        TextPainter.draw(g, label, x + 6.0, y + BUILD_CHIP_H - 4.0);
        g.setClip(clip);
    }

    /**
     * One shield icon: a five-point crest, filled through this renderer's polygon arrays.
     *
     * @param g the context
     * @param cx the centre x
     * @param cy the centre y
     * @param color the fill
     */
    private void drawShieldIcon(Graphics2D g, double cx, double cy, Color color) {
        double w = SHIELD_ICON_HALF;
        double h = 7;
        shieldX[0] = (int) Math.round(cx - w);
        shieldY[0] = (int) Math.round(cy - h);
        shieldX[1] = (int) Math.round(cx + w);
        shieldY[1] = (int) Math.round(cy - h);
        shieldX[2] = (int) Math.round(cx + w);
        shieldY[2] = (int) Math.round(cy + h * 0.2);
        shieldX[3] = (int) Math.round(cx);
        shieldY[3] = (int) Math.round(cy + h);
        shieldX[4] = (int) Math.round(cx - w);
        shieldY[4] = (int) Math.round(cy + h * 0.2);
        g.setColor(color);
        g.fillPolygon(shieldX, shieldY, 5);
    }

    /**
     * The four-point spark inside the ability badge, filled through this renderer's polygon
     * arrays so the glyph costs no allocation.
     *
     * @param g the context
     * @param cx the centre x
     * @param cy the centre y
     * @param r the half-diagonal
     * @param color the fill
     */
    private void drawSpark(Graphics2D g, double cx, double cy, double r, Color color) {
        glyphX[0] = (int) Math.round(cx);
        glyphY[0] = (int) Math.round(cy - r);
        glyphX[1] = (int) Math.round(cx + r * 0.42);
        glyphY[1] = (int) Math.round(cy);
        glyphX[2] = (int) Math.round(cx);
        glyphY[2] = (int) Math.round(cy + r);
        glyphX[3] = (int) Math.round(cx - r * 0.42);
        glyphY[3] = (int) Math.round(cy);
        g.setColor(color);
        g.fillPolygon(glyphX, glyphY, 4);
    }

    /**
     * The fade of a flash, precomputed per tick of the window so a flashing frame allocates no
     * colour (D18).
     *
     * @param base the opaque colour
     * @return one colour per remaining-tick count, index {@code 0} fully transparent
     */
    private static Color[] fadeRamp(Color base) {
        Color[] ramp = new Color[FLASH_TICKS + 1];
        for (int i = 0; i <= FLASH_TICKS; i++) {
            ramp[i] = new Color(base.getRed(), base.getGreen(), base.getBlue(),
                    MathUtil.clamp((int) Math.round(255.0 * i / FLASH_TICKS), 0, 255));
        }
        return ramp;
    }

    /**
     * A colour faded out over the flash window.
     *
     * @param ramp the precomputed ramp
     * @param ticksLeft how much of the flash is left
     * @return the faded colour
     */
    private static Color fade(Color[] ramp, int ticksLeft) {
        return ramp[MathUtil.clamp(ticksLeft, 0, FLASH_TICKS)];
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
