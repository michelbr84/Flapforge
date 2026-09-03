package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.AchievementDef;
import io.github.michelbr84.flapforge.content.defs.CounterScope;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.AchievementEvaluator;
import io.github.michelbr84.flapforge.progression.CollectionProgress;
import io.github.michelbr84.flapforge.progression.PlayerLevel;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
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
import io.github.michelbr84.flapforge.ui.component.TabBar;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The achievements of a profile (D13, D17, M8), reachable from the main menu, in three tabs.
 *
 * <p><b>Achievements</b> — every definition in content order, unlocked ones with their unlock
 * date, locked ones dimmed, hidden ones a "{@code ???}" until they fire. The header counts them
 * ("12 of 41 unlocked").
 *
 * <p><b>Milestones</b> — the level progress bar, then the next five thresholds among the level
 * rewards the player has not reached yet and the lifetime-threshold achievements the profile has
 * not fired, nearest first, each with a {@link ProgressBar} fed by
 * {@link AchievementEvaluator#progressOf} (a {@code RUN}-scoped achievement reports the best
 * matching lifetime statistic there, and an achievement already held reports a full bar). Hidden
 * achievements stay out of the list — a bar would spoil the secret. When nothing is left, the tab
 * says so instead of an empty band.
 *
 * <p><b>Collections</b> — one {@link ProgressBar} per category of {@link CollectionProgress},
 * owned over total with the floored percentage, {@code all} last. The numbers are the same
 * arithmetic the evaluators act on, so the tab cannot disagree with them.
 *
 * <p>Nothing here writes: it is a read-only view of the profile the save layer holds, like
 * {@link StatisticsScreen}. A session without a profile shows the empty state of every tab.
 */
public final class AchievementsScreen implements Screen {

    /** Top of the tab bar. */
    public static final int TAB_TOP = 48;
    /** Height of the tab bar. */
    public static final int TAB_H = 28;
    /** Top of the scrolling area. */
    public static final int VIEW_TOP = 86;
    /** Bottom of the scrolling area. */
    public static final int VIEW_BOTTOM = Playfield.HEIGHT - 62;
    /** Top of the Back button. */
    public static final int FOOTER_TOP = Playfield.HEIGHT - 56;
    /** Height of the Back button. */
    public static final int FOOTER_BUTTON_H = 42;
    /** Left edge of the content. */
    public static final int CONTENT_X = 24;
    /** Width of the content. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * CONTENT_X;
    /** Height of one achievement row. */
    public static final int ROW_H = 30;
    /** Height of a milestone or collection bar. */
    public static final int BAR_H = 26;
    /** Logical pixels one wheel notch scrolls. */
    public static final int WHEEL_STEP = 28;
    /** How many milestone bars the tab shows. */
    public static final int MILESTONE_COUNT = 5;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 40;
    private static final int PANEL_X = 12;
    private static final int PANEL_PAD = 6;
    private static final long MS_PER_DAY = 86_400_000L;

    /** The tab ids, the stable names a test addresses them by. */
    public static final String TAB_ACHIEVEMENTS = "achievements";
    /** The tab ids. */
    public static final String TAB_MILESTONES = "milestones";
    /** The tab ids. */
    public static final String TAB_COLLECTIONS = "collections";

    private final ScreenManager screens;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final AchievementEvaluator evaluator;
    private final ProgressionRules rules;
    private final FocusRing ring = new FocusRing();
    private final TabBar tabs = new TabBar();
    private final Button back;
    private final List<Line> lines = new ArrayList<>();
    private final List<ProgressBar> bars = new ArrayList<>();
    private double contentHeight;
    private double scroll;
    private String shownLanguage;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services
     */
    public AchievementsScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context").screens(),
                context.strings() != null ? context.strings() : Strings.active(),
                context.content(), context.profile(), context.progressionRules());
    }

    /**
     * Creates a stand-alone screen (tests and tools).
     *
     * @param screens the screen stack
     * @param strings the string table its labels come from
     * @param content the loaded content
     * @param profile the profile to show, or {@code null} for an empty one
     * @param rules the economy numbers the level bar reads, or {@code null} for defaults
     */
    public AchievementsScreen(ScreenManager screens, Strings strings, GameContent content,
            PlayerProfile profile, ProgressionRules rules) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.profile = profile == null ? new PlayerProfile() : profile;
        this.rules = rules == null ? ProgressionRules.none() : rules;
        this.evaluator = AchievementEvaluator.of(content);
        this.back = new Button(strings.get(StringKey.COMMON_BACK), screens::pop);
        this.back.setFontSize(16);
        this.back.setBounds(CONTENT_X, FOOTER_TOP, CONTENT_W, FOOTER_BUTTON_H);
        tabs.setBounds(CONTENT_X, TAB_TOP, CONTENT_W, TAB_H);
        tabs.add(TAB_ACHIEVEMENTS, strings.get(StringKey.ACHIEVEMENTS_TAB_ACHIEVEMENTS));
        tabs.add(TAB_MILESTONES, strings.get(StringKey.ACHIEVEMENTS_TAB_MILESTONES));
        tabs.add(TAB_COLLECTIONS, strings.get(StringKey.ACHIEVEMENTS_TAB_COLLECTIONS));
        tabs.setOnChange(index -> rebuild());
        ring.add(tabs);
        ring.add(back);
        rebuild();
        shownLanguage = strings.language();
    }

    // ------------------------------------------------------------------ building

    private void rebuild() {
        lines.clear();
        bars.clear();
        contentHeight = 0;
        switch (tabs.selectedIndex()) {
            case 1:
                buildMilestones();
                break;
            case 2:
                buildCollections();
                break;
            case 0:
            default:
                buildAchievements();
                break;
        }
        scroll = MathUtil.clamp(scroll, 0, maxScroll());
    }

    private void buildAchievements() {
        List<AchievementDef> defs = evaluator.definitions();
        int unlocked = 0;
        for (int i = 0; i < defs.size(); i++) {
            if (evaluator.isUnlocked(defs.get(i), profile)) {
                unlocked++;
            }
        }
        lines.add(new Line("count", strings.format(StringKey.ACHIEVEMENTS_COUNT, unlocked,
                defs.size()), Line.Kind.HEADER, contentHeight));
        contentHeight += 22;
        for (int i = 0; i < defs.size(); i++) {
            AchievementDef def = defs.get(i);
            boolean held = evaluator.isUnlocked(def, profile);
            boolean secret = def.hidden() && !held;
            String name = secret ? strings.get(StringKey.ACHIEVEMENTS_HIDDEN_NAME)
                    : ProgressionText.name(strings, ContentKind.ACHIEVEMENT, def.id());
            String desc = secret ? strings.get(StringKey.ACHIEVEMENTS_HIDDEN_DESC)
                    : ProgressionText.description(strings, ContentKind.ACHIEVEMENT, def.id());
            String value;
            if (held) {
                PlayerProfile.AchievementRecord record = profile.achievements.get(def.id());
                value = strings.format(StringKey.ACHIEVEMENTS_UNLOCKED_AT,
                        isoDate(record == null ? 0 : record.unlockedAtEpochMs));
            } else if (def.rewardOrNone().coins() > 0) {
                value = strings.format(StringKey.ACHIEVEMENTS_REWARD,
                        def.rewardOrNone().coins());
            } else {
                value = "";
            }
            lines.add(new Line(def.id() + ".name", name, held ? Line.Kind.TITLE : Line.Kind.SUB,
                    contentHeight));
            lines.add(new Line(def.id() + ".desc",
                    value.isEmpty() ? desc : desc + "  ·  " + value,
                    Line.Kind.SUB, contentHeight + 14));
            contentHeight += ROW_H;
        }
    }

    private void buildMilestones() {
        lines.add(new Line("next", strings.get(StringKey.MILESTONES_NEXT), Line.Kind.HEADER,
                contentHeight));
        contentHeight += 22;
        buildLevelBar();
        List<Milestone> all = new ArrayList<>();
        for (int i = 0; i < rules.levels().rewardedLevels().size(); i++) {
            int level = rules.levels().rewardedLevels().get(i);
            if (level > profile.level) {
                all.add(new Milestone(strings.format(StringKey.MILESTONES_LEVEL_REWARD, level,
                        rewardCoins(level)), profile.level, level, level - profile.level,
                        all.size()));
            }
        }
        List<AchievementDef> defs = evaluator.definitions();
        for (int i = 0; i < defs.size(); i++) {
            AchievementDef def = defs.get(i);
            if (def.hidden() || evaluator.isUnlocked(def, profile)
                    || def.condition().scope() != CounterScope.LIFETIME) {
                continue;
            }
            AchievementEvaluator.Progress progress = evaluator.progressOf(def, profile);
            all.add(new Milestone(ProgressionText.name(strings, ContentKind.ACHIEVEMENT,
                    def.id()), progress.current(), progress.target(),
                    progress.target() - progress.current(), all.size()));
        }
        // Nearest first; ties keep the order the entries were built in (level rewards, then the
        // content order of the achievements), which keeps the tab stable between renders.
        all.sort((a, b) -> {
            if (a.remaining != b.remaining) {
                return Long.compare(a.remaining, b.remaining);
            }
            return Integer.compare(a.order, b.order);
        });
        if (all.isEmpty()) {
            lines.add(new Line("none", strings.get(StringKey.MILESTONES_NONE), Line.Kind.SUB,
                    contentHeight));
            contentHeight += 20;
            return;
        }
        for (int i = 0; i < Math.min(MILESTONE_COUNT, all.size()); i++) {
            Milestone milestone = all.get(i);
            ProgressBar bar = new ProgressBar(milestone.label(),
                    milestone.target() <= 0 ? 1
                            : MathUtil.clamp(milestone.current() / (double) milestone.target(),
                                    0, 1));
            bar.setValueText(strings.format(StringKey.MILESTONES_PROGRESS, milestone.current(),
                    milestone.target()));
            bar.setBounds(CONTENT_X, contentHeight, CONTENT_W, BAR_H);
            bars.add(bar);
            contentHeight += BAR_H + 4;
        }
    }

    private void buildLevelBar() {
        PlayerLevel levels = rules.levels();
        PlayerLevel.Progress progress = levels.progressWithin(profile.xp);
        ProgressBar bar = new ProgressBar(strings.format(StringKey.SUMMARY_LEVEL,
                progress.level()), progress.maxed() ? 1 : progress.fraction());
        bar.setValueText(progress.maxed() ? strings.get(StringKey.SUMMARY_LEVEL_MAX)
                : strings.format(StringKey.SUMMARY_LEVEL_PROGRESS, progress.xpIntoLevel(),
                        progress.xpForNextLevel()));
        bar.setBounds(CONTENT_X, contentHeight, CONTENT_W, BAR_H);
        bars.add(bar);
        contentHeight += BAR_H + 8;
    }

    private long rewardCoins(int level) {
        Long coins = rules.levels().rewardsAt(level).get(PlayerProfile.CURRENCY_COINS);
        return coins == null ? 0 : coins;
    }

    private void buildCollections() {
        List<CollectionProgress.Entry> entries = evaluator.collections().all(profile);
        for (int i = 0; i < entries.size(); i++) {
            CollectionProgress.Entry entry = entries.get(i);
            ProgressBar bar = new ProgressBar(categoryLabel(entry.category()), entry.fraction());
            bar.setValueText(strings.format(StringKey.COLLECTIONS_VALUE, entry.owned(),
                    entry.total(), entry.percent()));
            bar.setBounds(CONTENT_X, contentHeight, CONTENT_W, BAR_H);
            bars.add(bar);
            contentHeight += BAR_H + 4;
        }
    }

    private String categoryLabel(String category) {
        StringKey key = StringKey.byKey("collections." + category);
        return key == null ? category : strings.get(key);
    }

    /** An epoch timestamp in the {@code yyyy-MM-dd} text the ISO date renders as. */
    private static String isoDate(long epochMs) {
        return LocalDate.ofEpochDay(Math.max(0, epochMs) / MS_PER_DAY).toString();
    }

    // ------------------------------------------------------------------ accessors

    /**
     * The tab bar.
     *
     * @return the bar
     */
    public TabBar tabBar() {
        return tabs;
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
     * The milestone and collection bars of the current tab, in display order.
     *
     * @return the bars
     */
    public List<ProgressBar> bars() {
        return List.copyOf(bars);
    }

    /**
     * The text lines of the current tab, in display order (screenshots and assertions).
     *
     * @return the lines
     */
    public List<String> lineTexts() {
        List<String> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            out.add(lines.get(i).text());
        }
        return out;
    }

    /**
     * One text line by its id ({@code count}, {@code first_flight.name}, ...).
     *
     * @param id the line id
     * @return the text, or {@code null} when the tab does not show it
     */
    public String line(String id) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(id)) {
                return lines.get(i).text();
            }
        }
        return null;
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
     * Current scroll offset of the tab content.
     *
     * @return logical pixels from the top of the content
     */
    public double scroll() {
        return scroll;
    }

    /** Re-reads every visible label from the string table (a language switch, D25). */
    public void refreshTexts() {
        back.setText(strings.get(StringKey.COMMON_BACK));
        List<String> labels = List.of(strings.get(StringKey.ACHIEVEMENTS_TAB_ACHIEVEMENTS),
                strings.get(StringKey.ACHIEVEMENTS_TAB_MILESTONES),
                strings.get(StringKey.ACHIEVEMENTS_TAB_COLLECTIONS));
        for (int i = 0; i < tabs.tabs().size() && i < labels.size(); i++) {
            tabs.tabs().get(i).setLabel(labels.get(i));
        }
        rebuild();
        shownLanguage = strings.language();
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(tabs);
        scroll = 0;
        screens.setLetterboxRgb(PALETTE.letterbox());
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
    }

    @Override
    public void tick(InputFrame input) {
        ring.handle(input);
        tabs.tick(input);
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
        TextPainter.drawOutlined(g, strings.get(StringKey.ACHIEVEMENTS_TITLE), CONTENT_X,
                TITLE_BASELINE, Align.LEFT, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(PALETTE), 2);
        tabs.render(g);
        ProceduralArt.panel(g, PANEL_X, VIEW_TOP - PANEL_PAD,
                Playfield.WIDTH - 2 * PANEL_X, VIEW_BOTTOM - VIEW_TOP + 2 * PANEL_PAD);

        Shape oldClip = g.getClip();
        g.clipRect(0, VIEW_TOP, Playfield.WIDTH, VIEW_BOTTOM - VIEW_TOP);
        g.translate(0.0, VIEW_TOP - scroll);
        for (int i = 0; i < lines.size(); i++) {
            renderLine(g, lines.get(i));
        }
        for (int i = 0; i < bars.size(); i++) {
            bars.get(i).render(g);
        }
        g.translate(0.0, -(VIEW_TOP - scroll));
        g.setClip(oldClip);
        back.render(g);
        ring.render(g);
    }

    private void renderLine(Graphics2D g, Line line) {
        switch (line.kind()) {
            case HEADER:
                g.setFont(Fonts.bold(14));
                g.setColor(ProceduralArt.accentColor(PALETTE));
                break;
            case TITLE:
                g.setFont(Fonts.bold(13));
                g.setColor(ProceduralArt.TEXT_LIGHT);
                break;
            case SUB:
            default:
                g.setFont(Fonts.regular(12));
                g.setColor(ProceduralArt.TEXT_MUTED);
                break;
        }
        TextPainter.draw(g, line.text(), CONTENT_X, line.y() + 12);
    }

    /** One milestone the tab counts towards. */
    private record Milestone(String label, long current, long target, long remaining, int order) {
    }

    /**
     * One text line of the achievements tab.
     *
     * @param id the stable id a test addresses the line by
     * @param text the translated text
     * @param kind which font and colour the line renders with
     * @param y the line's top edge in content space
     */
    private record Line(String id, String text, Kind kind, double y) {

        /** How a line renders. */
        enum Kind {
            /** A section header. */
            HEADER,
            /** A bold entry line (an unlocked achievement's name). */
            TITLE,
            /** A dim entry line (a description, a locked name, a note). */
            SUB
        }
    }
}
