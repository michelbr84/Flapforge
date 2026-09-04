package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionOutcome;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.HudRenderer;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Overlay shown when a run reaches {@code FINISHED} (D29).
 *
 * <p>It is a compact strip, not a screen: gates, points and time survived (in seconds and in
 * simulation ticks, which is the unit every M1 test speaks), over the frozen last frame of the
 * run. When the screen above it has a profile, the strip also carries what the run <em>paid</em>
 * — coins, XP, the best clean-gate streak and every level reached — from the
 * {@link ProgressionOutcome} that {@code GameScreen} produced before pushing this overlay.
 * Rewards are therefore already in the wallet and already queued for the disk when the strip
 * appears, which is what makes the instant retry safe: it can never lose them (D29).
 *
 * <p>{@code Enter} opens {@link RunSummaryScreen}, the full breakdown: every term of the reward
 * formula, the level bar the XP moved and the seed the run was played with. It is a view of what
 * has already been written, so opening it — or never opening it — changes nothing.
 *
 * <p>The prompt blinks on the same 60-off/60-on period as upstream's game-over prompt
 * ({@link HudRenderer#BLINK_HALF_TICKS}). {@code Space} or a left click retries immediately with a
 * new seed and the same configuration; {@code Esc} returns to the menu.
 *
 * <p>The overlay also drives {@link GameRenderer#tickFrozen()} on every tick: the ground, the
 * hills and the obstacles stay frozen, as upstream, but the clouds keep drifting at 30 px/s
 * behind the panel instead of the picture standing perfectly still.
 */
public final class GameOverOverlay implements Screen {

    /** Height of the panel with the three M1 rows and no reward strip. */
    public static final int PANEL_H = 190;

    private static final Color DIM = new Color(0, 0, 0, 0x73);
    private static final int PANEL_X = 30;
    private static final int PANEL_Y = 200;
    private static final int PANEL_W = Playfield.WIDTH - 2 * PANEL_X;
    private static final int LABEL_INSET = 34;
    private static final int FIRST_ROW_OFFSET = 86;
    private static final int ROW_STEP = 26;
    private static final int BASE_ROWS = 3;
    private static final int LEVEL_LINE_H = 24;

    private final ScreenManager screens;
    private final RunResult result;
    private final ProgressionOutcome outcome;
    private final Runnable onRetry;
    private final GameRenderer renderer;
    private final Strings strings;
    private final List<Row> rows = new ArrayList<>();
    private final String levelUpText;
    private final String challengeLine;
    private int panelY;
    private int panelH;
    private boolean dailyShown;
    private PlayerProfile profile;
    private ProgressionRules rules;
    private int ticks;

    /**
     * Creates the overlay without a reward strip (a screen built without a profile).
     *
     * @param screens the screen stack
     * @param result the finished run's result
     * @param onRetry starts a new run on the game screen below (D29: new seed, same config)
     * @param renderer the game renderer below, kept drifting its clouds while the overlay is up
     */
    public GameOverOverlay(ScreenManager screens, RunResult result, Runnable onRetry,
            GameRenderer renderer) {
        this(screens, result, null, onRetry, renderer, Strings.active());
    }

    /**
     * Creates the overlay without a reward strip.
     *
     * @param screens the screen stack
     * @param result the finished run's result
     * @param onRetry starts a new run on the game screen below (D29: new seed, same config)
     * @param renderer the game renderer below, kept drifting its clouds while the overlay is up
     * @param strings the string table its labels come from
     */
    public GameOverOverlay(ScreenManager screens, RunResult result, Runnable onRetry,
            GameRenderer renderer, Strings strings) {
        this(screens, result, null, onRetry, renderer, strings);
    }

    /**
     * Creates the overlay.
     *
     * @param screens the screen stack
     * @param result the finished run's result
     * @param outcome what the run paid, or {@code null} when the session has no profile
     * @param onRetry starts a new run on the game screen below (D29: new seed, same config)
     * @param renderer the game renderer below, kept drifting its clouds while the overlay is up
     * @param strings the string table its labels come from
     */
    public GameOverOverlay(ScreenManager screens, RunResult result, ProgressionOutcome outcome,
            Runnable onRetry, GameRenderer renderer, Strings strings) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.result = Objects.requireNonNull(result, "result");
        this.outcome = outcome;
        this.onRetry = Objects.requireNonNull(onRetry, "onRetry");
        this.renderer = Objects.requireNonNull(renderer, "renderer");

        rows.add(new Row(strings.get(StringKey.STAT_GATES),
                Integer.toString(result.gatesPassed())));
        rows.add(new Row(strings.get(StringKey.STAT_POINTS),
                Long.toString(Math.round(result.stats().points()))));
        int ticksAlive = result.stats().ticksAlive();
        rows.add(new Row(strings.get(StringKey.STAT_TIME_ALIVE),
                strings.format(StringKey.STAT_TIME_ALIVE_VALUE,
                        String.format(Locale.ROOT, "%.1f",
                                ticksAlive / (double) Playfield.TICK_RATE),
                        ticksAlive)));
        // M8 (D29): a challenge run tells the player here whether its objective was met, and a
        // run with a boss how the encounter went — the same facts the summary breaks out.
        if (result.config().challengeId() != null) {
            rows.add(new Row(strings.get(StringKey.STAT_OBJECTIVE), strings.get(
                    result.stats().objectiveMet() ? StringKey.STAT_OBJECTIVE_MET
                            : StringKey.STAT_OBJECTIVE_MISSED)));
        }
        // Only when the encounter actually began: "Phase 0" for a boss that never warned is
        // noise, so a run that ended before atGate shows no boss row at all.
        if (result.config().bossEnabled() && bossBegan(result.stats())) {
            rows.add(new Row(strings.get(StringKey.STAT_BOSS), bossValue(result.stats())));
        }

        if (outcome != null) {
            RewardSummary rewards = outcome.rewardSummary();
            rows.add(new Row(strings.get(StringKey.STAT_COINS),
                    "+" + rewards.coins() + creditedSuffix(outcome)));
            rows.add(new Row(strings.get(StringKey.STAT_XP), "+" + rewards.xp()));
            if (result.stats().streakBest() > 0) {
                rows.add(new Row(strings.get(StringKey.STAT_STREAK_BEST),
                        Integer.toString(result.stats().streakBest())));
            }
        }
        this.levelUpText = outcome != null && outcome.leveledUp()
                ? strings.format(StringKey.GAMEOVER_LEVEL_UP, outcome.highestLevel()) : null;
        this.challengeLine = outcome != null && outcome.challengeFirstCompleted()
                ? strings.format(StringKey.GAMEOVER_CHALLENGE_COMPLETED,
                        outcome.rewardSummary().challengeCoins()) : null;
        layout();
    }

    /** Sizes the panel around the rows it holds and keeps it centred on {@link #PANEL_Y}. */
    private void layout() {
        this.panelH = PANEL_H + (rows.size() - BASE_ROWS) * ROW_STEP
                + (levelUpText == null ? 0 : LEVEL_LINE_H)
                + (challengeLine == null ? 0 : LEVEL_LINE_H);
        this.panelY = PANEL_Y - (panelH - PANEL_H) / 2;
    }

    /**
     * The boss row's value: "Cleared" once a world boss was survived, otherwise the furthest
     * phase the fight reached (D11).
     *
     * @param stats the finished run's stats
     * @return the translated value
     */
    private String bossValue(RunStats stats) {
        return stats.bossesCleared().isEmpty()
                ? strings.format(StringKey.STAT_BOSS_PHASE, stats.phasesReached())
                : strings.get(StringKey.STAT_BOSS_CLEARED);
    }

    /**
     * Whether the run's boss encounter actually began: {@code phasesReached} is set only once a
     * fight has placed a phase column, and a clear implies it. The overlay holds no live
     * {@code BossEncounter}, so this is the fact the stats snapshot carries.
     *
     * @param stats the finished run's stats
     * @return {@code true} when the fight, at least, started
     */
    private static boolean bossBegan(RunStats stats) {
        return stats.phasesReached() > 0 || !stats.bossesCleared().isEmpty();
    }

    /**
     * The coins the level-ups paid on top of the run reward, as a {@code (+n)} suffix.
     *
     * @param outcome the outcome
     * @return the suffix, empty when no level paid anything
     */
    private static String creditedSuffix(ProgressionOutcome outcome) {
        long granted = 0;
        for (Long amount : outcome.levelRewardsGranted().values()) {
            granted += amount == null ? 0 : amount;
        }
        return granted == 0 ? "" : " (+" + granted + ")";
    }

    /**
     * Names the profile the run was written into, so {@code Enter} can show the level bar and the
     * personal-best markers of the summary (D29). Without it the summary still opens; it simply
     * shows the run rows and the setup.
     *
     * @param newProfile the profile, or {@code null}
     * @param newRules the economy numbers, or {@code null}
     * @return this overlay, for chaining
     */
    public GameOverOverlay withProfile(PlayerProfile newProfile, ProgressionRules newRules) {
        this.profile = newProfile;
        this.rules = newRules;
        // M9 (D28): a daily run says here how the day is going -- the best gate count of the date
        // and which attempt this was. The numbers live on the profile, which arrives with this
        // call, so the row joins the strip here rather than in the constructor.
        if (newProfile != null && result.config().mode() == RunMode.DAILY && !dailyShown) {
            dailyShown = true;
            rows.add(new Row(strings.get(StringKey.MODE_DAILY), dailyValue(newProfile)));
            layout();
        }
        return this;
    }

    /**
     * The daily row's value: the best gate count of the date and the attempt just flown (D28).
     *
     * @param owner the profile the run was written into
     * @return the translated value
     */
    private String dailyValue(PlayerProfile owner) {
        PlayerProfile.DailyRecord daily = owner.daily;
        return daily == null || daily.attempts <= 0
                ? strings.get(StringKey.DAILY_UNPLAYED)
                : strings.format(StringKey.DAILY_RESULT, daily.bestGates, daily.attempts);
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    /**
     * The result being shown.
     *
     * @return the result
     */
    public RunResult result() {
        return result;
    }

    /**
     * What the run paid.
     *
     * @return the outcome, or {@code null} when the session has no profile
     */
    public ProgressionOutcome outcome() {
        return outcome;
    }

    /**
     * The strip's rows, label and value, in the order they are drawn.
     *
     * @return the rows
     */
    public List<String> rowTexts() {
        List<String> out = new ArrayList<>(rows.size());
        for (Row row : rows) {
            out.add(row.label() + " " + row.value());
        }
        return List.copyOf(out);
    }

    /**
     * Whether the blinking prompt is currently drawn.
     *
     * @return {@code true} during the second half of the blink period
     */
    public boolean promptVisible() {
        return ticks >= HudRenderer.BLINK_HALF_TICKS;
    }

    @Override
    public void tick(InputFrame input) {
        // The screen manager ticks only the top screen, so the frozen run below would stand
        // perfectly still; upstream kept its clouds drifting on the game-over screen (§5).
        renderer.tickFrozen();
        ticks++;
        if (ticks >= HudRenderer.BLINK_PERIOD_TICKS) {
            ticks = 0;
        }
        if (input.isJustPressed(InputAction.PAUSE) || input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
            screens.pop();
            return;
        }
        boolean retry = input.isJustPressed(InputAction.FLAP)
                || input.isMouseJustPressed(Keys.BUTTON_LEFT);
        if (retry) {
            // The rewards were applied and queued for the disk before this overlay was pushed,
            // so restarting here cannot lose them (D29).
            screens.pop();
            onRetry.run();
            return;
        }
        if (input.isJustPressed(InputAction.CONFIRM)) {
            UiCues.select();
            screens.push(new RunSummaryScreen(screens, result, outcome, profile, rules, onRetry,
                    strings));
        }
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        g.setColor(DIM);
        g.fillRect(0, 0, Playfield.WIDTH, Playfield.HEIGHT);
        ProceduralArt.panel(g, PANEL_X, panelY, PANEL_W, panelH);

        g.setFont(Fonts.bold(30));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, strings.get(StringKey.GAMEOVER_TITLE),
                Playfield.WIDTH / 2.0, panelY + 48);

        g.setFont(Fonts.regular(15));
        for (int i = 0; i < rows.size(); i++) {
            row(g, i, rows.get(i));
        }

        if (challengeLine != null) {
            g.setFont(Fonts.bold(14));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.drawCentered(g, challengeLine, Playfield.WIDTH / 2.0,
                    panelY + panelH - (levelUpText == null ? 40.0 : 58.0));
        }
        if (levelUpText != null) {
            g.setFont(Fonts.bold(14));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.drawCentered(g, levelUpText, Playfield.WIDTH / 2.0,
                    panelY + panelH - 40.0);
        }

        if (promptVisible()) {
            g.setFont(Fonts.regular(13));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.drawCentered(g, strings.get(StringKey.GAMEOVER_RETRY_HINT),
                    Playfield.WIDTH / 2.0, panelY + panelH - 16.0);
        }
    }

    private void row(Graphics2D g, int index, Row entry) {
        double baseline = panelY + FIRST_ROW_OFFSET + index * (double) ROW_STEP;
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, entry.label(), PANEL_X + LABEL_INSET, baseline);
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, entry.value(), PANEL_X + PANEL_W - LABEL_INSET, baseline, Align.RIGHT);
    }

    /** One label/value line of the strip. */
    private record Row(String label, String value) {
    }
}
