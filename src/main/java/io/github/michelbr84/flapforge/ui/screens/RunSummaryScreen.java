package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerLevel;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionOutcome;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import io.github.michelbr84.flapforge.progression.Statistics;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.ProgressBar;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The full breakdown of a finished run, opened with {@code Enter} from {@link GameOverOverlay}
 * (D29).
 *
 * <p>The game-over strip answers "how did I do"; this screen answers "where did every coin come
 * from". Every term of {@link RewardSummary} gets its own row — the participation reward, the
 * first-run bonus, the gate, point, streak, boss and challenge terms, their sum, the three
 * multipliers, the coins picked up in the world and the total — because the formula (E32.a) is
 * the part of the economy a player is entitled to see. The XP the run paid is shown with the
 * level {@link ProgressBar} it moved, and the seed and mode are shown together, so a screenshot
 * is enough to replay the run (D12).
 *
 * <p>Rewards are <em>not</em> applied here: {@code GameScreen} wrote them into the profile before
 * the overlay was pushed (D14, D29), so this screen only reads. That is what lets a player retry
 * without opening it and lose nothing. It also means the personal-best markers are read back from
 * the profile the run has already been written into: a row is marked when the run's number is
 * the lifetime best.
 *
 * <p>M6 adds the build section (D27): every modifier the run drafted with its stack count and
 * every set bonus it activated, plus — in the coin breakdown — how much of the streak term the
 * drafted cards themselves paid. A run played without {@code feature:modifiers} gets a line saying
 * so instead, because the summary is where a player asks "could this run have gone differently",
 * and a silent empty section is not an answer.
 *
 * <p>A session without a profile (a bare screen stack in a test, or a run played before the save
 * layer is wired) has no {@code outcome}: the coin and XP sections are simply not built, and the
 * screen shows the run rows and the setup alone.
 *
 * <p>Layout: the rows do not fit on a 420x640 playfield, so they live in a content space scrolled
 * under a clip, like {@link SettingsScreen}. Retry and Menu sit in a fixed footer and are the only
 * focusable things, side by side, so the arrows are free to scroll the breakdown.
 */
public final class RunSummaryScreen implements Screen {

    /** Top of the scrolling area. */
    public static final int VIEW_TOP = 56;
    /** Bottom of the scrolling area. */
    public static final int VIEW_BOTTOM = Playfield.HEIGHT - 66;
    /** Top of the fixed footer bar. */
    public static final int FOOTER_TOP = Playfield.HEIGHT - 56;
    /** Height of the footer buttons. */
    public static final int FOOTER_BUTTON_H = 42;
    /** Left edge of the content. */
    public static final int CONTENT_X = 24;
    /** Width of the content. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * CONTENT_X;
    /** Height of one breakdown row. */
    public static final int ROW_H = 17;
    /** Height of a section header. */
    public static final int HEADER_H = 22;
    /** Height reserved for the level progress bar. */
    public static final int BAR_H = 26;
    /** Logical pixels one wheel notch scrolls. */
    public static final int WHEEL_STEP = 28;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 40;
    private static final int PANEL_X = 12;
    private static final int PANEL_PAD = 6;
    private static final Color SCROLLBAR = new Color(0xF4, 0xF8, 0xF8, 0x50);
    private static final Color BEST = new Color(0xF5C542);

    private final ScreenManager screens;
    private final Strings strings;
    private final RunResult result;
    private final ProgressionOutcome outcome;
    private final PlayerProfile profile;
    private final ProgressionRules rules;
    private final Runnable onRetry;
    private final FocusRing ring = new FocusRing();
    private final List<Row> rows = new ArrayList<>();
    private final Button retry;
    private final Button menu;
    private ProgressBar levelBar;
    private double contentHeight;
    private double scroll;

    /**
     * Creates a summary without a profile (tests, and a session with no save layer).
     *
     * @param screens the screen stack
     * @param result the finished run
     * @param onRetry starts a new run on the game screen below (D29: new seed, same config)
     */
    public RunSummaryScreen(ScreenManager screens, RunResult result, Runnable onRetry) {
        this(screens, result, null, null, null, onRetry, Strings.active());
    }

    /**
     * Creates a summary.
     *
     * @param screens the screen stack
     * @param result the finished run
     * @param outcome what the run paid, or {@code null} when the session has no profile
     * @param profile the profile the run was written into, or {@code null}
     * @param rules the economy numbers behind the level bar, or {@code null}
     * @param onRetry starts a new run on the game screen below (D29: new seed, same config)
     * @param strings the string table its labels come from
     */
    public RunSummaryScreen(ScreenManager screens, RunResult result, ProgressionOutcome outcome,
            PlayerProfile profile, ProgressionRules rules, Runnable onRetry, Strings strings) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.result = Objects.requireNonNull(result, "result");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.onRetry = Objects.requireNonNull(onRetry, "onRetry");
        this.outcome = outcome;
        this.profile = profile;
        this.rules = rules;
        this.retry = new Button(strings.get(StringKey.SUMMARY_RETRY), this::retry);
        this.menu = new Button(strings.get(StringKey.SUMMARY_MENU), this::toMenu);
        int half = (CONTENT_W - 8) / 2;
        retry.setFontSize(16);
        retry.setBounds(CONTENT_X, FOOTER_TOP, half, FOOTER_BUTTON_H);
        menu.setFontSize(16);
        menu.setBounds(CONTENT_X + half + 8, FOOTER_TOP, CONTENT_W - half - 8, FOOTER_BUTTON_H);
        ring.add(retry);
        ring.add(menu);
        build();
    }

    // ------------------------------------------------------------------ building

    private void build() {
        RunStats stats = result.stats();
        long points = Math.round(stats.points());
        header(StringKey.SUMMARY_SECTION_RUN);
        row("gates", StringKey.STAT_GATES, Integer.toString(stats.gatesPassed()),
                isBest(stats.gatesPassed(), bestGates()));
        row("points", StringKey.STAT_POINTS, Long.toString(points),
                isBest(points, bestPoints()));
        row("streakBest", StringKey.STAT_STREAK_BEST, Integer.toString(stats.streakBest()),
                isBest(stats.streakBest(), bestStreak()));
        row("time", StringKey.STAT_TIME_ALIVE, strings.format(StringKey.STAT_TIME_ALIVE_VALUE,
                seconds(stats.ticksAlive()), stats.ticksAlive()), false);

        if (outcome != null) {
            RewardSummary rewards = outcome.rewardSummary();
            header(StringKey.SUMMARY_SECTION_COINS);
            row("participation", StringKey.REWARD_PARTICIPATION, signed(rewards.participation()));
            row("firstRunBonus", StringKey.REWARD_FIRST_RUN, signed(rewards.firstRunBonus()));
            row("gateCoins", StringKey.REWARD_GATES, signed(rewards.gateCoins()));
            row("pointCoins", StringKey.REWARD_POINTS, signed(rewards.pointCoins()));
            // The streak term is one number in the formula (E32.a): economy.rewards.streak.coins
            // plus whatever the drafted cards pay, times the steps. The breakdown splits it in
            // two so the column adds up to the Base row — the shipped half on the streak line,
            // the half the player drafted on its own line below it.
            long draftedStreak = stats.modifierStreakCoins() * (long) stats.streakSteps();
            row("streakCoins", StringKey.REWARD_STREAK,
                    signed(rewards.streakCoins() - draftedStreak));
            if (stats.modifierStreakCoins() > 0) {
                row("streakBonus", StringKey.REWARD_STREAK_BONUS, signed(draftedStreak));
            }
            row("bossCoins", StringKey.REWARD_BOSS, signed(rewards.bossCoins()));
            row("challengeCoins", StringKey.REWARD_CHALLENGE, signed(rewards.challengeCoins()));
            row("base", StringKey.REWARD_BASE, Long.toString(rewards.baseCoins()));
            row("coinMult", StringKey.REWARD_COIN_MULT, multiplier(rewards.coinMult()));
            row("tierMult", StringKey.REWARD_TIER_MULT, multiplier(rewards.tierMult()));
            row("dailyMult", StringKey.REWARD_DAILY_MULT, multiplier(rewards.dailyMult()));
            row("coinsCollected", StringKey.REWARD_COLLECTED, signed(rewards.coinsCollected()));
            row("coins", StringKey.REWARD_TOTAL, signed(rewards.coins()));

            header(StringKey.SUMMARY_SECTION_XP);
            row("xp", StringKey.STAT_XP, signed(rewards.xp()));
            buildLevelBar();
            if (outcome.leveledUp()) {
                line("levelUp", strings.format(StringKey.GAMEOVER_LEVEL_UP,
                        outcome.highestLevel()));
            }
        }

        buildSection(stats);

        header(StringKey.SUMMARY_SECTION_INFO);
        line("seed", strings.format(StringKey.SUMMARY_SEED, result.config().seed(),
                modeName(result.config().mode())));
    }

    /**
     * The build the run ended with (M6, D27): every modifier taken with its stack count, then
     * every set bonus it activated.
     *
     * <p>A run that drafted nothing gets one line instead, and it says <em>why</em>: a run played
     * without {@code feature:modifiers} never saw a draft at all, and the summary is the place a
     * player finds out that the shop sells the thing that would have changed it. A run that could
     * draft and simply took nothing says so with the "nothing" line.
     *
     * @param stats the finished run's stats
     */
    private void buildSection(RunStats stats) {
        List<String> taken = stats.modifiersTaken();
        List<String> synergies = stats.synergiesActivated();
        header(StringKey.SUMMARY_SECTION_BUILD);
        // A challenge may turn the drafts off itself (D11), and telling that player to go and buy
        // the feature would be a lie; only a run that could not draft at all gets the note.
        boolean lockedOut = !result.config().allowOffers()
                && result.config().mode() != RunMode.CHALLENGE;
        if (lockedOut && taken.isEmpty()) {
            line("modifiersLocked", strings.format(StringKey.SUMMARY_MODIFIERS_LOCKED,
                    ProgressionText.name(strings, ContentKind.FEATURE,
                            RunLoadout.MODIFIERS_FEATURE)));
            return;
        }
        if (taken.isEmpty()) {
            row("modifiersNone", StringKey.COMMON_NONE, "");
            return;
        }
        Map<String, Integer> stacks = new LinkedHashMap<>();
        for (int i = 0; i < taken.size(); i++) {
            stacks.merge(taken.get(i), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : stacks.entrySet()) {
            line("modifier." + entry.getKey(),
                    ProgressionText.name(strings, ContentKind.MODIFIER, entry.getKey()),
                    strings.format(StringKey.SUMMARY_STACKS, entry.getValue()));
        }
        for (int i = 0; i < synergies.size(); i++) {
            line("synergy." + synergies.get(i),
                    ProgressionText.name(strings, ContentKind.SYNERGY, synergies.get(i)),
                    strings.get(StringKey.SUMMARY_SYNERGY));
        }
    }

    /**
     * Builds the level bar from the profile's XP, which the progression pass has already
     * increased: the bar therefore shows where the player <em>is</em>, and the "+xp" row above it
     * shows how far this run moved them.
     */
    private void buildLevelBar() {
        if (profile == null || rules == null) {
            return;
        }
        PlayerLevel levels = rules.levels();
        PlayerLevel.Progress progress = levels.progressWithin(profile.xp);
        levelBar = new ProgressBar(strings.format(StringKey.SUMMARY_LEVEL, progress.level()),
                progress.maxed() ? 1 : progress.fraction());
        levelBar.setValueText(progress.maxed() ? strings.get(StringKey.SUMMARY_LEVEL_MAX)
                : strings.format(StringKey.SUMMARY_LEVEL_PROGRESS, progress.xpIntoLevel(),
                        progress.xpForNextLevel()));
        levelBar.setBounds(CONTENT_X, contentHeight, CONTENT_W, BAR_H);
        contentHeight += BAR_H + 4;
    }

    private void header(StringKey key) {
        rows.add(new Row(key.key(), strings.get(key), "", true, false, contentHeight));
        contentHeight += HEADER_H;
    }

    private void row(String id, StringKey label, String value) {
        row(id, label, value, false);
    }

    private void row(String id, StringKey label, String value, boolean best) {
        rows.add(new Row(id, strings.get(label), value, false, best, contentHeight));
        contentHeight += ROW_H;
    }

    private void line(String id, String text) {
        line(id, text, "");
    }

    /**
     * A row whose label is not a {@link StringKey} but content-derived text — a modifier's name, a
     * synergy's name — so the build section can list what the run actually drafted.
     *
     * @param id the stable row id
     * @param text the already-translated label
     * @param value the already-translated value, empty for a full-width line
     */
    private void line(String id, String text, String value) {
        rows.add(new Row(id, text, value, false, false, contentHeight));
        contentHeight += ROW_H;
    }

    private String signed(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
    }

    private String multiplier(double value) {
        return strings.format(StringKey.REWARD_MULTIPLIER_VALUE,
                String.format(Locale.ROOT, "%.2f", value));
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / (double) Playfield.TICK_RATE);
    }

    private static boolean isBest(long value, long best) {
        return value > 0 && value == best;
    }

    private long bestGates() {
        return statistics() == null ? -1 : statistics().bestGates;
    }

    private long bestPoints() {
        return statistics() == null ? -1 : statistics().bestPoints;
    }

    private long bestStreak() {
        return statistics() == null ? -1 : statistics().streakBest;
    }

    private Statistics statistics() {
        return profile == null ? null : profile.statistics;
    }

    private String modeName(RunMode mode) {
        switch (mode) {
            case SEEDED:
                return strings.get(StringKey.MODE_SEEDED);
            case DAILY:
                return strings.get(StringKey.MODE_DAILY);
            case CHALLENGE:
                return strings.get(StringKey.MODE_CHALLENGE);
            default:
                return strings.get(StringKey.MODE_STANDARD);
        }
    }

    // ------------------------------------------------------------------ accessors

    /**
     * The run being summarised.
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
     * Every row of the breakdown, headers included, in display order.
     *
     * @return an unmodifiable snapshot
     */
    public List<Row> rows() {
        return List.copyOf(rows);
    }

    /**
     * One row by id ({@code gates}, {@code gateCoins}, {@code coins}, ...).
     *
     * @param id the row id
     * @return the row, or {@code null} when the screen does not show it
     */
    public Row row(String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) {
                return rows.get(i);
            }
        }
        return null;
    }

    /**
     * The rows as "label value" lines, headers included (screenshots and assertions).
     *
     * @return the lines in display order
     */
    public List<String> rowTexts() {
        List<String> out = new ArrayList<>(rows.size());
        for (Row row : rows) {
            out.add(row.value().isEmpty() ? row.label() : row.label() + " " + row.value());
        }
        return List.copyOf(out);
    }

    /**
     * The level progress bar.
     *
     * @return the bar, or {@code null} when the session has no profile
     */
    public ProgressBar levelBar() {
        return levelBar;
    }

    /**
     * The Retry button.
     *
     * @return the button
     */
    public Button retryButton() {
        return retry;
    }

    /**
     * The Menu button.
     *
     * @return the button
     */
    public Button menuButton() {
        return menu;
    }

    /**
     * The focus ring (tests inspecting focus).
     *
     * @return the ring
     */
    public FocusRing focusRing() {
        return ring;
    }

    /**
     * Current scroll offset of the breakdown.
     *
     * @return logical pixels from the top of the content
     */
    public double scroll() {
        return scroll;
    }

    // ------------------------------------------------------------------ behaviour

    private void retry() {
        // The rewards were banked before the game-over overlay was pushed (D29), so leaving the
        // summary for a new run cannot lose them.
        pop(countAbove(false));
        onRetry.run();
    }

    private void toMenu() {
        pop(countAbove(true));
    }

    private void pop(int times) {
        for (int i = 0; i < times; i++) {
            screens.pop();
        }
    }

    /**
     * How many screens this one has to take with it: itself, the game-over strip it was opened
     * from and — for Menu — the run below them. Counted off the live stack rather than assumed,
     * so a summary pushed somewhere else (a tool, a test) can never pop a screen that is not part
     * of the run it belongs to, and the root screen is never popped.
     *
     * @param includeRun whether the game screen under the strip goes too
     * @return the number of pops
     */
    private int countAbove(boolean includeRun) {
        List<Screen> stack = screens.screens();
        int pops = 0;
        for (int i = stack.size() - 1; i >= 1; i--) {
            Screen screen = stack.get(i);
            if (screen == this || screen instanceof GameOverOverlay
                    || (includeRun && screen instanceof GameScreen)) {
                pops++;
            } else {
                break;
            }
        }
        return pops;
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(retry);
        scroll = 0;
        screens.setLetterboxRgb(PALETTE.letterbox());
    }

    @Override
    public void tick(InputFrame input) {
        ring.handle(input);
        if (input.wheel() != 0) {
            scrollBy(-input.wheel() * (double) WHEEL_STEP);
        }
        // Retry and Menu sit side by side, so the vertical arrows move no focus and are free to
        // scroll the breakdown, which is the only thing on this screen that does not fit.
        if (input.isJustPressed(InputAction.DOWN)) {
            scrollBy(ROW_H * 3.0);
        }
        if (input.isJustPressed(InputAction.UP)) {
            scrollBy(-ROW_H * 3.0);
        }
        if (input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
        }
    }

    private void scrollBy(double delta) {
        scroll = MathUtil.clamp(scroll + delta, 0, maxScroll());
    }

    private double maxScroll() {
        return Math.max(0, contentHeight - (VIEW_BOTTOM - VIEW_TOP));
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        g.setFont(Fonts.bold(26));
        TextPainter.drawOutlined(g, strings.get(StringKey.SUMMARY_TITLE), Playfield.WIDTH / 2.0,
                TITLE_BASELINE, Align.CENTER, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(PALETTE), 2);
        ProceduralArt.panel(g, PANEL_X, VIEW_TOP - PANEL_PAD, Playfield.WIDTH - 2 * PANEL_X,
                VIEW_BOTTOM - VIEW_TOP + 2 * PANEL_PAD);

        Shape oldClip = g.getClip();
        g.clipRect(0, VIEW_TOP, Playfield.WIDTH, VIEW_BOTTOM - VIEW_TOP);
        double dy = VIEW_TOP - scroll;
        g.translate(0.0, dy);
        for (int i = 0; i < rows.size(); i++) {
            renderRow(g, rows.get(i));
        }
        if (levelBar != null) {
            levelBar.render(g);
        }
        g.translate(0.0, -dy);
        g.setClip(oldClip);

        renderScrollbar(g);
        ring.render(g);
    }

    private void renderRow(Graphics2D g, Row row) {
        double baseline = row.y() + (row.header() ? HEADER_H - 6 : ROW_H - 4);
        if (row.header()) {
            g.setFont(Fonts.bold(14));
            g.setColor(ProceduralArt.accentColor(PALETTE));
            TextPainter.draw(g, row.label(), CONTENT_X, baseline);
            return;
        }
        g.setFont(Fonts.regular(13));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, row.label(), CONTENT_X, baseline);
        if (!row.value().isEmpty()) {
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, row.value(), CONTENT_X + (double) CONTENT_W, baseline,
                    Align.RIGHT);
        }
        if (row.best()) {
            g.setFont(Fonts.bold(11));
            g.setColor(BEST);
            TextPainter.draw(g, strings.get(StringKey.SUMMARY_BEST),
                    CONTENT_X + CONTENT_W * 0.62, baseline);
        }
    }

    private void renderScrollbar(Graphics2D g) {
        double max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackH = VIEW_BOTTOM - VIEW_TOP;
        int thumbH = (int) Math.max(24, trackH * (trackH / contentHeight));
        int thumbY = VIEW_TOP + (int) Math.round((trackH - thumbH) * (scroll / max));
        g.setColor(SCROLLBAR);
        g.fillRoundRect(Playfield.WIDTH - 10, thumbY, 4, thumbH, 4, 4);
    }

    /**
     * One line of the breakdown.
     *
     * @param id the stable identifier a test addresses the row by
     * @param label the translated label
     * @param value the translated value, empty for a header or a full-width line
     * @param header whether the row is a section header
     * @param best whether the run set a personal best in this row
     * @param y the row's top edge in content space
     */
    public record Row(String id, String label, String value, boolean header, boolean best,
            double y) {
    }
}
