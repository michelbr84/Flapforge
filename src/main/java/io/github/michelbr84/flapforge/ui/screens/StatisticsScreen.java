package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
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
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.ListView;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The lifetime statistics of a profile (D13, M3), reachable from the main menu.
 *
 * <p>The counters are grouped the way a player thinks about them rather than the way
 * {@link Statistics} stores them: how much has been flown, how far the flights got, what the
 * economy did, how the clean-gate streaks went (D26) and what the deaths were. The last runs come
 * from {@code statistics.runHistory} — the capped list the progression pass appends to — and are
 * paged through with a {@link ListView}, newest first, so a hundred entries cost one row of screen
 * instead of a hundred.
 *
 * <p>Nothing here writes: the screen is a read-only view of the profile the save layer holds. A
 * session without one (a bare screen stack in a test) is shown an empty profile rather than a
 * blank screen, which is also what a brand-new player sees.
 *
 * <p>Layout: the groups live in a content space scrolled under a clip, exactly like
 * {@link SettingsScreen}; at the shipped text scale everything fits, so the scroll only comes into
 * play when a larger text size pushes the rows past the band. The history list and Back are the
 * two focusable things, stacked, so the vertical arrows move between them and the wheel scrolls.
 */
public final class StatisticsScreen implements Screen {

    /** Top of the scrolling area. */
    public static final int VIEW_TOP = 58;
    /** Bottom of the scrolling area. */
    public static final int VIEW_BOTTOM = 496;
    /** Top of the run-history row. */
    public static final int HISTORY_TOP = 512;
    /** Height of the run-history row. */
    public static final int HISTORY_H = 30;
    /** Top of the Back button. */
    public static final int FOOTER_TOP = Playfield.HEIGHT - 56;
    /** Height of the Back button. */
    public static final int FOOTER_BUTTON_H = 42;
    /** Left edge of the content. */
    public static final int CONTENT_X = 24;
    /** Width of the content. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * CONTENT_X;
    /** Height of one counter row. */
    public static final int ROW_H = 17;
    /** Height of a group header. */
    public static final int HEADER_H = 22;
    /** Logical pixels one wheel notch scrolls. */
    public static final int WHEEL_STEP = 28;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 40;
    private static final int PANEL_X = 12;
    private static final int PANEL_PAD = 6;
    private static final Color SCROLLBAR = new Color(0xF4, 0xF8, 0xF8, 0x50);

    private final ScreenManager screens;
    private final Strings strings;
    private final PlayerProfile profile;
    private final FocusRing ring = new FocusRing();
    private final List<Row> rows = new ArrayList<>();
    private final CurrencyDisplay wallet = new CurrencyDisplay();
    private final Button back;
    private final ListView history;
    private String shownLanguage;
    private double contentHeight;
    private double scroll;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services (its profile is what is shown)
     */
    public StatisticsScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context").screens(),
                context.strings() != null ? context.strings() : Strings.active(),
                context.profile());
    }

    /**
     * Creates a stand-alone screen showing an empty profile (tests and tools).
     *
     * @param screens the screen stack
     */
    public StatisticsScreen(ScreenManager screens) {
        this(screens, Strings.active(), null);
    }

    /**
     * Creates the screen.
     *
     * @param screens the screen stack
     * @param strings the string table its labels come from
     * @param profile the profile to show, or {@code null} for an empty one
     */
    public StatisticsScreen(ScreenManager screens, Strings strings, PlayerProfile profile) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.profile = profile == null ? new PlayerProfile() : profile;
        this.back = new Button(strings.get(StringKey.COMMON_BACK), screens::pop);
        this.back.setFontSize(16);
        this.back.setBounds(CONTENT_X, FOOTER_TOP, CONTENT_W, FOOTER_BUTTON_H);
        this.history = new ListView(strings.get(StringKey.STATS_HISTORY), historyOptions(), 0);
        this.history.setWrapping(false);
        this.history.setFontSize(14);
        this.history.setBounds(CONTENT_X, HISTORY_TOP, CONTENT_W, HISTORY_H);
        ring.add(history);
        ring.add(back);
        wallet.setBounds(Playfield.WIDTH - 150.0, 18, 130, 26);
        wallet.setAlign(Align.RIGHT);
        wallet.setFormat(strings.get(StringKey.HUD_COINS));
        wallet.setAmountNow(walletBalance());
        this.shownLanguage = strings.language();
        build();
    }

    // ------------------------------------------------------------------ building

    private void build() {
        rows.clear();
        contentHeight = 0;
        Statistics stats = profile.statistics;

        header(StringKey.STATS_GROUP_FLIGHTS);
        row("totalRuns", StringKey.STATS_RUNS, Long.toString(stats.totalRuns));
        row("playtimeSeconds", StringKey.STATS_PLAYTIME, playtime(stats.playtimeSeconds));
        row("dailiesPlayed", StringKey.STATS_DAILIES, Long.toString(stats.dailiesPlayed));
        row("challengesCompleted", StringKey.STATS_CHALLENGES,
                Long.toString(stats.challengesCompleted));

        header(StringKey.STATS_GROUP_DISTANCE);
        row("bestGates", StringKey.STATS_BEST_GATES, Long.toString(stats.bestGates));
        row("totalGates", StringKey.STATS_TOTAL_GATES, Long.toString(stats.totalGates));
        row("bestPoints", StringKey.STATS_BEST_POINTS, Long.toString(stats.bestPoints));
        row("totalPoints", StringKey.STATS_TOTAL_POINTS, Long.toString(stats.totalPoints));

        header(StringKey.STATS_GROUP_ECONOMY);
        row("level", StringKey.STATS_LEVEL, Integer.toString(profile.level));
        row("coinsEarned", StringKey.STATS_COINS_EARNED, Long.toString(stats.coinsEarned));
        row("coinsSpent", StringKey.STATS_COINS_SPENT, Long.toString(stats.coinsSpent));
        row("coinsCollected", StringKey.STATS_COINS_COLLECTED,
                Long.toString(stats.coinsCollected));
        row("xpEarned", StringKey.STATS_XP_EARNED, Long.toString(stats.xpEarned));

        header(StringKey.STATS_GROUP_STREAKS);
        row("streakBest", StringKey.STAT_STREAK_BEST, Long.toString(stats.streakBest));
        row("shieldAbsorbs", StringKey.STATS_SHIELD_ABSORBS, Long.toString(stats.shieldAbsorbs));
        row("revives", StringKey.STATS_REVIVES, Long.toString(stats.revives));

        header(StringKey.STATS_GROUP_DEATHS);
        int deaths = 0;
        for (CollisionCause cause : CollisionCause.values()) {
            Long value = stats.deathsByCause.get(cause.name());
            if (value != null && value > 0) {
                deaths++;
                row("death." + cause.name(), causeKey(cause), Long.toString(value));
            }
        }
        Long unknown = stats.deathsByCause.get(Statistics.CAUSE_UNKNOWN);
        if (unknown != null && unknown > 0) {
            deaths++;
            row("death.unknown", StringKey.DEATH_UNKNOWN, Long.toString(unknown));
        }
        if (deaths == 0) {
            row("death.none", StringKey.STATS_NONE, "");
        }
    }

    private static StringKey causeKey(CollisionCause cause) {
        switch (cause) {
            case GROUND:
                return StringKey.DEATH_GROUND;
            case CEILING:
                return StringKey.DEATH_CEILING;
            default:
                return StringKey.DEATH_OBSTACLE;
        }
    }

    private void header(StringKey key) {
        rows.add(new Row(key.key(), strings.get(key), "", true, contentHeight));
        contentHeight += HEADER_H;
    }

    private void row(String id, StringKey label, String value) {
        rows.add(new Row(id, strings.get(label), value, false, contentHeight));
        contentHeight += ROW_H;
    }

    private String playtime(long seconds) {
        long safe = Math.max(0, seconds);
        return strings.format(StringKey.STATS_PLAYTIME_VALUE, safe / 3600, safe % 3600 / 60);
    }

    /**
     * The history options, newest first: the progression pass appends each finished run to the
     * end of the capped list, and the run a player wants to see is the one they just played.
     *
     * @return one label per remembered run, or a single "nothing yet" entry
     */
    private List<String> historyOptions() {
        List<Statistics.RunHistoryEntry> entries = profile.statistics.runHistory;
        List<String> out = new ArrayList<>(Math.max(1, entries.size()));
        for (int i = entries.size() - 1; i >= 0; i--) {
            Statistics.RunHistoryEntry entry = entries.get(i);
            out.add(strings.format(StringKey.STATS_HISTORY_ENTRY, entries.size() - i,
                    entry.gates, entry.coins));
        }
        if (out.isEmpty()) {
            out.add(strings.get(StringKey.STATS_HISTORY_EMPTY));
        }
        return out;
    }

    private long walletBalance() {
        Long coins = profile.wallet.get(PlayerProfile.CURRENCY_COINS);
        return coins == null ? 0 : coins;
    }

    // ------------------------------------------------------------------ accessors

    /**
     * The profile being shown.
     *
     * @return the profile, never {@code null}
     */
    public PlayerProfile profile() {
        return profile;
    }

    /**
     * Every row, headers included, in display order.
     *
     * @return an unmodifiable snapshot
     */
    public List<Row> rows() {
        return List.copyOf(rows);
    }

    /**
     * One row by id ({@code totalRuns}, {@code coinsEarned}, {@code death.GROUND}, ...).
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
     * The rows as "label value" lines (screenshots and assertions).
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
     * The run-history list.
     *
     * @return the list
     */
    public ListView historyList() {
        return history;
    }

    /**
     * The wallet readout.
     *
     * @return the display
     */
    public CurrencyDisplay walletDisplay() {
        return wallet;
    }

    /**
     * The Back button.
     *
     * @return the button
     */
    public Button backButton() {
        return back;
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
     * Current scroll offset of the groups.
     *
     * @return logical pixels from the top of the content
     */
    public double scroll() {
        return scroll;
    }

    /** Re-reads every visible label from the string table (a language switch, D25). */
    public void refreshTexts() {
        back.setText(strings.get(StringKey.COMMON_BACK));
        history.setLabel(strings.get(StringKey.STATS_HISTORY));
        int selected = history.selectedIndex();
        history.setOptions(historyOptions());
        history.selectQuietly(selected);
        wallet.setFormat(strings.get(StringKey.HUD_COINS));
        build();
        shownLanguage = strings.language();
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(history);
        scroll = 0;
        screens.setLetterboxRgb(PALETTE.letterbox());
        wallet.setAmountNow(walletBalance());
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
    }

    @Override
    public void tick(InputFrame input) {
        wallet.tick();
        ring.handle(input);
        history.tick(input);
        if (input.wheel() != 0) {
            scroll = MathUtil.clamp(scroll - input.wheel() * (double) WHEEL_STEP, 0, maxScroll());
        }
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
        if (input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
        }
    }

    private double maxScroll() {
        return Math.max(0, contentHeight - (VIEW_BOTTOM - VIEW_TOP));
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        g.setFont(Fonts.bold(26));
        TextPainter.drawOutlined(g, strings.get(StringKey.STATS_TITLE), CONTENT_X,
                TITLE_BASELINE, Align.LEFT, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(PALETTE), 2);
        wallet.render(g);
        ProceduralArt.panel(g, PANEL_X, VIEW_TOP - PANEL_PAD, Playfield.WIDTH - 2 * PANEL_X,
                VIEW_BOTTOM - VIEW_TOP + 2 * PANEL_PAD);

        Shape oldClip = g.getClip();
        g.clipRect(0, VIEW_TOP, Playfield.WIDTH, VIEW_BOTTOM - VIEW_TOP);
        double dy = VIEW_TOP - scroll;
        g.translate(0.0, dy);
        for (int i = 0; i < rows.size(); i++) {
            renderRow(g, rows.get(i));
        }
        g.translate(0.0, -dy);
        g.setClip(oldClip);
        renderScrollbar(g);

        // The history row sits on the bright hills of the backdrop, where a light label would be
        // unreadable; it gets the same panel the groups have.
        ProceduralArt.panel(g, PANEL_X, HISTORY_TOP - PANEL_PAD,
                Playfield.WIDTH - 2 * PANEL_X, HISTORY_H + 2 * PANEL_PAD);
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
     * One line of the statistics.
     *
     * @param id the stable identifier a test addresses the row by
     * @param label the translated label
     * @param value the translated value, empty for a header
     * @param header whether the row is a group header
     * @param y the row's top edge in content space
     */
    public record Row(String id, String label, String value, boolean header, double y) {
    }
}
