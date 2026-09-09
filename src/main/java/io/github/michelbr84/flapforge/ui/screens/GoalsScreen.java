package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.AchievementDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.CounterScope;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
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
import io.github.michelbr84.flapforge.ui.component.ListView;
import io.github.michelbr84.flapforge.ui.component.NavBar;
import io.github.michelbr84.flapforge.ui.component.NavButton;
import io.github.michelbr84.flapforge.ui.component.ProgressBar;
import io.github.michelbr84.flapforge.ui.component.SectionNav;
import io.github.michelbr84.flapforge.ui.component.TabBar;
import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import io.github.michelbr84.flapforge.ui.screens.goals.GoalsArt;
import io.github.michelbr84.flapforge.ui.screens.goals.GoalsChallengeCarousel;
import io.github.michelbr84.flapforge.ui.screens.goals.GoalsPlayButton;
import io.github.michelbr84.flapforge.ui.screens.goals.GoalsSurface;
import io.github.michelbr84.flapforge.ui.screens.goals.GoalsTabBar;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * The goals of a profile (D13, D17, M8, M10), rebuilt onto the reference frame: a display title
 * pinned to the top of the visible band, the four category pills under it, an elastic content
 * panel that takes the height between them and the bottom navigation, and that navigation —
 * with Goals marked as the section the player is in — as the way out. The panel's height is a
 * ratio of the surface, so on a portrait phone the panel grows instead of the old fixed
 * 420x640 band letterboxing the screen dead above and below.
 *
 * <p><b>Challenges</b> — a one-at-a-time carousel ({@link GoalsChallengeCarousel}): chevron
 * arrows flanking the challenge's name, a status pill when it is locked, the objective, the
 * World and Tier pair, then the Rules, Progress, Reward and Unlock rows and the call to action.
 * The challenge's world is <em>labelled</em> here, it is never an unlock requirement (E6). Play
 * builds a {@link ChallengeRunSource} — the challenge's own configuration with the profile's
 * current loadout — and pushes a {@link GameScreen} over this one.
 *
 * <p><b>Achievements</b> — the count ("12 of 41 unlocked") with its bar, then every definition
 * in content order as a card: achieved ones in the green complete state with their unlock date,
 * locked ones with a pale padlock tile and their coin reward, hidden ones a "{@code ???}" until
 * they fire. No veil: the references keep locked cards fully readable.
 *
 * <p><b>Milestones</b> — the level progress bar, then the next five thresholds among the level
 * rewards the player has not reached yet and the lifetime-threshold achievements the profile has
 * not fired, nearest first, each fed by {@link AchievementEvaluator#progressOf} (a
 * {@code RUN}-scoped achievement reports the best matching lifetime statistic there, and an
 * achievement already held reports a full bar). Hidden achievements stay out of the list — a
 * bar would spoil the secret. When nothing is left, the tab says so.
 *
 * <p><b>Collections</b> — one row per category of {@link CollectionProgress}, owned over total
 * with the floored percentage, {@code all} last as the stronger Everything card, and the footer
 * line under it. The numbers are the same arithmetic the evaluators act on, so the tab cannot
 * disagree with them.
 *
 * <p>Only the Challenges tab starts anything; the other three are read-only views of the
 * profile the save layer holds. A session without an application context (a bare screen stack
 * in a test) shows every challenge locked and the empty state of the other tabs, and the four
 * navigation routes sit inert — the Goals item itself stays walkable, exactly as the focus
 * counts the tests pin expect.
 */
public final class GoalsScreen implements Screen {

    /** The tab ids, the stable names a test addresses them by. */
    public static final String TAB_CHALLENGES = "challenges";
    /** The tab ids. */
    public static final String TAB_ACHIEVEMENTS = "achievements";
    /** The tab ids. */
    public static final String TAB_MILESTONES = "milestones";
    /** The tab ids. */
    public static final String TAB_COLLECTIONS = "collections";
    /** How many milestone bars the tab shows. */
    public static final int MILESTONE_COUNT = 5;
    /** Logical pixels one wheel notch scrolls. */
    public static final int WHEEL_STEP = 28;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int CONTENT_X = 12;
    private static final int CONTENT_W = Playfield.WIDTH - 2 * CONTENT_X;
    private static final int PANEL_PAD = 10;
    private static final long MS_PER_DAY = 86_400_000L;

    // The multi-colour glyphs the collection rows carry, allocated once (GoalsArt's rule: the
    // render path only reads colours).
    private static final Color ABILITY_SPARK = new Color(0x7F, 0xD4, 0xE8);
    private static final Color ISLAND_SOIL = new Color(0x8A, 0x5A, 0x33);
    private static final Color ISLAND_GRASS = new Color(0x62, 0xB7, 0x4B);
    private static final Color FLAG_RED = new Color(0xD9, 0x4A, 0x4A);
    private static final Ellipse2D.Double COIN_SCRATCH = new Ellipse2D.Double();

    // Frame bands, as ratios of the surface height (the spec's band anatomy). The panel takes
    // its reference share and the background reveal is what remains down to the navigation.
    private static final double TOP_SAFE_RATIO = 0.051;
    private static final double HEADER_RATIO = 0.056;
    private static final double HEADER_GAP_RATIO = 0.012;
    private static final double TABS_RATIO = 0.039;
    private static final double TABS_GAP_RATIO = 0.015;
    private static final double PANEL_INSET_RATIO = 0.042;
    private static final double PANEL_CHALLENGES = 0.648;
    private static final double PANEL_ACHIEVEMENTS = 0.687;
    private static final double PANEL_MILESTONES = 0.692;
    private static final double PANEL_COLLECTIONS = 0.671;
    private static final double REVEAL_CHALLENGES = 0.069;
    private static final double REVEAL_ACHIEVEMENTS = 0.036;
    private static final double REVEAL_MILESTONES = 0.030;
    private static final double REVEAL_COLLECTIONS = 0.023;
    private static final double TITLE_RATIO = 0.102;
    private static final double HEADING_RATIO = 0.051;

    // Challenges-tab pieces, as ratios of the surface width (the spec's card metrics).
    private static final double ARROW_RATIO = 0.098;
    private static final double PAIR_RATIO = 0.138;
    private static final double CTA_RATIO = 0.121;
    private static final double PILL_RATIO = 0.274;
    private static final int MIN_ROW_H = 36;
    private static final int MAX_ROW_H = 72;
    private static final int GAP_BASE = 6;
    private static final int GAP_MAX = 24;

    // List-tab rows: the reference's row heights at the 640 surface, grown with the surface so
    // the tab fills its panel on a taller one. Each row kind scales from its own reference
    // ratio and clamps at the 640 value below.
    private static final int SUMMARY_HEADING_H = 26;
    private static final int SUMMARY_BAR_H = 16;
    private static final double HELD_RATIO = 0.0906;
    private static final double LOCKED_RATIO = 0.0766;
    private static final double MILESTONE_RATIO = 0.1016;
    private static final double COLLECTION_RATIO = 0.0859;
    private static final double EVERYTHING_RATIO = 0.1047;
    private static final int ACHIEVEMENT_HELD_H = 58;
    private static final int ACHIEVEMENT_LOCKED_H = 49;
    private static final int MILESTONE_H = 65;
    private static final int MILESTONE_MAX_H = 96;
    private static final int MILESTONE_HEADING_H = 34;
    private static final int COLLECTION_H = 55;
    // The 933 panel shows the seven rows, the Everything card and the caption with no scroll:
    // 7x65 + 78 + footer 24 + 8 gaps of 6 = 605 against 606 inner px, so the closing caption
    // never clips (ff-vision 3.1).
    private static final int COLLECTION_MAX_H = 65;
    private static final int EVERYTHING_H = 67;
    private static final int EVERYTHING_MAX_H = 78;
    private static final int FOOTER_H = 24;
    private static final int CARD_GAP = 6;

    private final ScreenManager screens;
    private final GameContext context;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final boolean hasProfile;
    private final ProgressionRules rules;
    private final AchievementEvaluator evaluator;
    private final List<ChallengeDef> challenges;
    private final FocusRing ring = new FocusRing();
    private final GoalsTabBar tabs = new GoalsTabBar();
    private final GoalsChallengeCarousel list;
    private final GoalsPlayButton play;
    private final NavBar nav = new NavBar();
    private final List<Line> lines = new ArrayList<>();
    private final List<ProgressBar> bars = new ArrayList<>();
    private final List<AchievementCard> cards = new ArrayList<>();
    private final List<String> barCategories = new ArrayList<>();
    private final int[] rowTops = new int[4];
    private double contentHeight;
    private double scroll;
    private String shownLanguage;
    private int stackVersionSeen = -1;

    // The frame, all of it computed by relayout() from the surface.
    private int surfaceTop;
    private int surfaceH;
    private int navTop;
    private LayoutMetrics laidOut;
    private int titleSize;
    private int titleBaseline;
    private int headingSize;
    private int tabsTop;
    private int tabsH;
    private int panelX;
    private int panelW;
    private int panelTop;
    private int panelBottom;
    private int scrollTop;
    private int scrollBottom;

    // The Challenges tab's pieces, computed by relayoutChallenges().
    private int carouselTop;
    private int arrowSize;
    private int pillTop;
    private int pillH;
    private int objectiveBaseline;
    private int pairTop;
    private int pairH;
    private int rowH;
    private WorldSwatch worldSwatch;
    private int achievementUnlocked;
    private int achievementTotal;

    // The list tabs' row heights, computed from the surface when the tab content is built.
    private int heldRowH;
    private int lockedRowH;
    private int milestoneRowH;
    private int collectionRowH;
    private int everythingRowH;

    /**
     * Creates the screen for a wired application, opening on the Challenges tab.
     *
     * @param context the application services
     */
    public GoalsScreen(GameContext context) {
        this(context, TAB_CHALLENGES);
    }

    /**
     * Creates the screen for a wired application on a given tab.
     *
     * @param context the application services
     * @param initialTabId the tab to open on ({@link #TAB_CHALLENGES}, ...)
     */
    public GoalsScreen(GameContext context, String initialTabId) {
        this(Objects.requireNonNull(context, "context").screens(), context,
                context.strings() != null ? context.strings() : Strings.active(),
                context.content(), context.profile(), context.progressionRules());
        tabs.select(initialTabId);
    }

    /**
     * Creates a stand-alone screen (tests and tools): it can describe every goal but cannot
     * start a challenge or leave through the navigation, because it has no application services
     * behind it.
     *
     * @param screens the screen stack
     * @param strings the string table its labels come from
     * @param content the loaded content
     * @param profile the profile to show, or {@code null} for an empty one
     * @param rules the economy numbers the level bar reads, or {@code null} for defaults
     */
    public GoalsScreen(ScreenManager screens, Strings strings, GameContent content,
            PlayerProfile profile, ProgressionRules rules) {
        this(screens, null, strings, content, profile, rules);
    }

    private GoalsScreen(ScreenManager screens, GameContext context, Strings strings,
            GameContent content, PlayerProfile profile, ProgressionRules rules) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.context = context;
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.hasProfile = profile != null;
        this.profile = profile == null ? new PlayerProfile() : profile;
        this.rules = rules == null ? ProgressionRules.none() : rules;
        this.evaluator = AchievementEvaluator.of(content);
        this.challenges = List.copyOf(content.challenges().all());
        this.list = new GoalsChallengeCarousel(strings.get(StringKey.CHALLENGES_TITLE),
                listOptions(), 0);
        this.list.setOnChange(index -> refreshPlay());
        this.play = new GoalsPlayButton("", this::startChallenge);
        tabs.add(TAB_CHALLENGES, strings.get(StringKey.CHALLENGES_TITLE));
        tabs.add(TAB_ACHIEVEMENTS, strings.get(StringKey.ACHIEVEMENTS_TAB_ACHIEVEMENTS));
        tabs.add(TAB_MILESTONES, strings.get(StringKey.ACHIEVEMENTS_TAB_MILESTONES));
        tabs.add(TAB_COLLECTIONS, strings.get(StringKey.ACHIEVEMENTS_TAB_COLLECTIONS));
        tabs.setOnChange(index -> {
            scroll = 0;
            rebuild();
            rebuildRing();
            relayout();
        });
        SectionNav.build(nav, SectionNav.GOALS, new SectionNav.Routes(
                context == null ? null : this::openShop,
                context == null ? null : this::openBirds,
                context == null ? null : this::openHome,
                context == null ? null : this::openForge,
                null));
        nav.button(SectionNav.SHOP).setEnabled(context != null);
        nav.button(SectionNav.BIRDS).setEnabled(context != null);
        nav.button(SectionNav.PLAY).setEnabled(context != null);
        nav.button(SectionNav.FORGE).setEnabled(context != null);
        // Goals itself stays enabled everywhere: it is the section the player is in, and the
        // one navigation item the focus ring stops on (the ring counts the tests pin expect
        // the bar, the tab's own controls and this item, nothing more).
        refreshTexts();
        rebuildRing();
        relayout();
    }

    // ------------------------------------------------------------------ building

    private boolean onChallenges() {
        return tabs.selectedIndex() == 0;
    }

    /**
     * Puts the nodes of the current tab on the ring: the tabs, the carousel and Play on the
     * Challenges tab, and the Goals navigation item, in that order, with the tabs focused.
     */
    private void rebuildRing() {
        ring.clear();
        ring.add(tabs);
        if (onChallenges()) {
            ring.add(list);
            ring.add(play);
        }
        ring.add(nav.button(SectionNav.GOALS));
        ring.focus(tabs);
    }

    private void rebuild() {
        lines.clear();
        bars.clear();
        cards.clear();
        barCategories.clear();
        contentHeight = 0;
        scaleRows();
        switch (tabs.selectedIndex()) {
            case 1:
                buildAchievements();
                break;
            case 2:
                buildMilestones();
                break;
            case 3:
                buildCollections();
                break;
            case 0:
            default:
                break;
        }
        scroll = MathUtil.clamp(scroll, 0, maxScroll());
    }

    /**
     * Scales the list tabs' row heights from the surface: each row kind keeps its reference
     * proportion of the surface height between the reference's 640 value and the tallest
     * measured bound, so a taller panel is filled with taller rows rather than empty panel.
     */
    private void scaleRows() {
        int h = GoalsSurface.of(screens.metrics()).height();
        heldRowH = rowHeight(h, HELD_RATIO, ACHIEVEMENT_HELD_H, ACHIEVEMENT_HELD_H + 10);
        lockedRowH = rowHeight(h, LOCKED_RATIO, ACHIEVEMENT_LOCKED_H, ACHIEVEMENT_LOCKED_H + 9);
        milestoneRowH = rowHeight(h, MILESTONE_RATIO, MILESTONE_H, MILESTONE_MAX_H);
        collectionRowH = rowHeight(h, COLLECTION_RATIO, COLLECTION_H, COLLECTION_MAX_H);
        everythingRowH = rowHeight(h, EVERYTHING_RATIO, EVERYTHING_H, EVERYTHING_MAX_H);
    }

    /**
     * One scaled row height: the surface grown by the ratio, clamped to {@code [min, max]}.
     *
     * @param h the surface height
     * @param ratio the reference's row-to-surface ratio
     * @param min the reference's 640 value
     * @param max the tallest the row grows
     * @return the row height
     */
    private static int rowHeight(int h, double ratio, int min, int max) {
        return Math.max(min, Math.min(max, (int) Math.round(h * ratio)));
    }

    private void buildAchievements() {
        List<AchievementDef> defs = evaluator.definitions();
        int unlocked = 0;
        for (int i = 0; i < defs.size(); i++) {
            if (evaluator.isUnlocked(defs.get(i), profile)) {
                unlocked++;
            }
        }
        achievementUnlocked = unlocked;
        achievementTotal = defs.size();
        lines.add(new Line("count", strings.format(StringKey.ACHIEVEMENTS_COUNT, unlocked,
                defs.size()), Line.Kind.HEADER, contentHeight));
        contentHeight = SUMMARY_HEADING_H + 4 + SUMMARY_BAR_H + 8;
        for (int i = 0; i < defs.size(); i++) {
            AchievementDef def = defs.get(i);
            boolean held = evaluator.isUnlocked(def, profile);
            boolean secret = def.hidden() && !held;
            String name = secret ? strings.get(StringKey.ACHIEVEMENTS_HIDDEN_NAME)
                    : ProgressionText.name(strings, ContentKind.ACHIEVEMENT, def.id());
            String desc = secret ? strings.get(StringKey.ACHIEVEMENTS_HIDDEN_DESC)
                    : ProgressionText.description(strings, ContentKind.ACHIEVEMENT, def.id());
            String footer;
            boolean gold;
            if (held) {
                PlayerProfile.AchievementRecord record = profile.achievements.get(def.id());
                footer = strings.format(StringKey.ACHIEVEMENTS_UNLOCKED_AT,
                        isoDate(record == null ? 0 : record.unlockedAtEpochMs));
                gold = false;
            } else if (def.rewardOrNone().coins() > 0) {
                footer = strings.format(StringKey.ACHIEVEMENTS_REWARD,
                        def.rewardOrNone().coins());
                gold = true;
            } else {
                footer = "";
                gold = false;
            }
            int height = held ? heldRowH : lockedRowH;
            cards.add(new AchievementCard(name, desc, footer, gold, held, contentHeight));
            lines.add(new Line(def.id() + ".name", name,
                    held ? Line.Kind.TITLE : Line.Kind.SUB, contentHeight));
            lines.add(new Line(def.id() + ".desc",
                    footer.isEmpty() ? desc : desc + "  ·  " + footer,
                    Line.Kind.SUB, contentHeight + 14));
            contentHeight += height + CARD_GAP;
        }
    }

    private void buildMilestones() {
        lines.add(new Line("next", strings.get(StringKey.MILESTONES_NEXT), Line.Kind.HEADER,
                contentHeight));
        contentHeight = MILESTONE_HEADING_H;
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
            contentHeight += FOOTER_H;
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
            bar.setBounds(CONTENT_X, contentHeight, CONTENT_W, milestoneRowH);
            bars.add(bar);
            contentHeight += milestoneRowH + CARD_GAP;
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
        bar.setBounds(CONTENT_X, contentHeight, CONTENT_W, milestoneRowH);
        bars.add(bar);
        contentHeight += milestoneRowH + CARD_GAP;
    }

    private long rewardCoins(int level) {
        Long coins = rules.levels().rewardsAt(level).get(PlayerProfile.CURRENCY_COINS);
        return coins == null ? 0 : coins;
    }

    private void buildCollections() {
        List<CollectionProgress.Entry> entries = evaluator.collections().all(profile);
        for (int i = 0; i < entries.size(); i++) {
            CollectionProgress.Entry entry = entries.get(i);
            boolean everything = entry.category().equals(CollectionProgress.ALL);
            int height = everything ? everythingRowH : collectionRowH;
            ProgressBar bar = new ProgressBar(categoryLabel(entry.category()), entry.fraction());
            bar.setValueText(strings.format(StringKey.COLLECTIONS_VALUE, entry.owned(),
                    entry.total(), entry.percent()));
            bar.setBounds(CONTENT_X, contentHeight, CONTENT_W, height);
            bars.add(bar);
            barCategories.add(entry.category());
            contentHeight += height + CARD_GAP;
        }
        lines.add(new Line("footer", strings.get(StringKey.GOALS_COLLECTIONS_FOOTER),
                Line.Kind.SUB, contentHeight));
        contentHeight += FOOTER_H;
    }

    private String categoryLabel(String category) {
        StringKey key = StringKey.byKey("collections." + category);
        return key == null ? category : strings.get(key);
    }

    /** An epoch timestamp in the {@code yyyy-MM-dd} text the ISO date renders as. */
    private static String isoDate(long epochMs) {
        return LocalDate.ofEpochDay(Math.max(0, epochMs) / MS_PER_DAY).toString();
    }

    // ------------------------------------------------------------------ challenges

    private List<String> listOptions() {
        List<String> out = new ArrayList<>(challenges.size());
        for (int i = 0; i < challenges.size(); i++) {
            ChallengeDef def = challenges.get(i);
            String name = ProgressionText.name(strings, ContentKind.CHALLENGE, def.id());
            out.add(isUnlocked(def) ? name
                    : strings.format(StringKey.CHALLENGES_LOCKED_ENTRY, name));
        }
        return out;
    }

    private boolean isUnlocked(ChallengeDef def) {
        return hasProfile && profile.isUnlocked(def.unlockableId());
    }

    /**
     * The challenge the carousel currently points at.
     *
     * @return the definition
     */
    public ChallengeDef selectedChallenge() {
        int index = Math.max(0, Math.min(list.selectedIndex(), challenges.size() - 1));
        return challenges.get(index);
    }

    /**
     * The run source Play would start, built for the current selection with the live profile —
     * the challenge's own configuration with the loadout the player has selected and bought.
     *
     * @return the source, or {@code null} when the session cannot play (no context, no profile,
     *     or a locked challenge)
     */
    public ChallengeRunSource playSource() {
        if (context == null || !hasProfile || !isUnlocked(selectedChallenge())) {
            return null;
        }
        return new ChallengeRunSource(content, context::profile, selectedChallenge().id());
    }

    private void startChallenge() {
        ChallengeRunSource source = playSource();
        if (source == null) {
            return;
        }
        UiCues.select();
        screens.push(new GameScreen(context, source, SeedSequence.random()));
    }

    private void refreshPlay() {
        boolean unlocked = isUnlocked(selectedChallenge());
        play.setText(strings.get(unlocked
                ? StringKey.CHALLENGES_PLAY : StringKey.CHALLENGES_LOCKED_TITLE));
        // The lock is the button's visual state, not setEnabled(false): the tab's own walk
        // (DOWN, DOWN) lands on the locked button, and activating it is inert anyway — no run
        // source is built for a locked challenge.
        play.setLocked(!unlocked);
        if (surfaceH > 0) {
            relayout();
        }
    }

    /**
     * The detail rows the Challenges tab draws for the selection ("label value" lines,
     * screenshots and assertions), name and description first, unlock line last when locked.
     *
     * @return the lines in display order
     */
    public List<String> detailTexts() {
        ChallengeDef def = selectedChallenge();
        List<String> out = new ArrayList<>(9);
        out.add(ProgressionText.name(strings, ContentKind.CHALLENGE, def.id()));
        out.add(ProgressionText.description(strings, ContentKind.CHALLENGE, def.id()));
        out.add(strings.format(StringKey.CHALLENGES_WORLD,
                ProgressionText.name(strings, ContentKind.WORLD, def.world())));
        out.add(strings.format(StringKey.CHALLENGES_TIER,
                ProgressionText.name(strings, ContentKind.TIER, def.tier())));
        out.add(strings.format(StringKey.CHALLENGES_RULES, rulesLine(def)));
        out.add(strings.format(StringKey.CHALLENGES_OBJECTIVE, objectiveText(def)));
        out.add(recordLine(def));
        out.add(strings.format(StringKey.CHALLENGES_REWARDS, rewardsLine(def)));
        if (!isUnlocked(def)) {
            out.add(strings.format(StringKey.CHALLENGES_LOCKED,
                    ProgressionText.unlockText(strings, content, def.unlock(),
                            hasProfile ? profile : null)));
        }
        return out;
    }

    /**
     * The challenge's special rules in words: its flags, its starting modifiers, a fixed
     * corridor and its boss, in that order; the standard-rules line when none apply.
     *
     * @param def the challenge
     * @return the line
     */
    private String rulesLine(ChallengeDef def) {
        List<String> parts = new ArrayList<>(4);
        for (int i = 0; i < def.flags().size(); i++) {
            parts.add(ProgressionText.ruleName(strings, def.flags().get(i)));
        }
        for (int i = 0; i < def.forcedModifiers().size(); i++) {
            parts.add(strings.format(StringKey.CHALLENGES_RULE_MODIFIER,
                    ProgressionText.name(strings, ContentKind.MODIFIER,
                            def.forcedModifiers().get(i))));
        }
        if (def.forcedPattern() != null) {
            parts.add(strings.get(StringKey.CHALLENGES_RULE_PATTERN));
        }
        if (def.boss() != null) {
            parts.add(strings.format(StringKey.CHALLENGES_RULE_BOSS, def.boss().atGate()));
        }
        if (parts.isEmpty()) {
            return strings.get(StringKey.CHALLENGES_RULES_NONE);
        }
        return join(parts);
    }

    /**
     * The objective in words with its number substituted ("Survive 30 gates").
     *
     * @param def the challenge
     * @return the text
     */
    private String objectiveText(ChallengeDef def) {
        long value = def.objective().value();
        switch (def.objective().type()) {
            case SURVIVE_GATES:
                return strings.format(StringKey.OBJECTIVE_SURVIVE_GATES, value);
            case SURVIVE_TICKS:
                return strings.format(StringKey.OBJECTIVE_SURVIVE_TICKS, value);
            case COLLECT_COINS:
                return strings.format(StringKey.OBJECTIVE_COLLECT_COINS, value);
            case REACH_POINTS:
                return strings.format(StringKey.OBJECTIVE_REACH_POINTS, value);
            case BOSS_CLEARED:
            default:
                return strings.get(StringKey.OBJECTIVE_BOSS_CLEARED);
        }
    }

    private String recordLine(ChallengeDef def) {
        PlayerProfile.ChallengeRecord record = hasProfile
                ? profile.challenges.get(def.id()) : null;
        if (record == null || (!record.completed && record.attempts == 0)) {
            return strings.get(StringKey.CHALLENGES_RECORD_NONE);
        }
        String text = strings.format(StringKey.CHALLENGES_RECORD, record.bestGates,
                record.attempts);
        return record.completed
                ? strings.format(StringKey.CHALLENGES_COMPLETED_ENTRY, text) : text;
    }

    private String rewardsLine(ChallengeDef def) {
        List<String> parts = new ArrayList<>(3);
        if (def.rewardsOrNone().coins() > 0) {
            parts.add(strings.format(StringKey.CHALLENGES_REWARD_COINS,
                    def.rewardsOrNone().coins()));
        }
        List<String> unlocks = def.rewardsOrNone().unlocks();
        for (int i = 0; i < unlocks.size(); i++) {
            parts.add(ProgressionText.unlockableName(strings, content, unlocks.get(i)));
        }
        return parts.isEmpty() ? strings.get(StringKey.COMMON_NONE) : join(parts);
    }

    /** Comma-joined parts, with the plain comma neither string table translates. */
    private static String join(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ navigation routes

    /** Opens the shop, replacing this section rather than stacking on top of it. */
    private void openShop() {
        if (context != null) {
            screens.replace(new ShopScreen(context));
        }
    }

    /** Opens the bird selection, replacing this section. */
    private void openBirds() {
        if (context != null) {
            screens.replace(new BirdSelectionScreen(context));
        }
    }

    /** Opens the upgrade trees, replacing this section. */
    private void openForge() {
        if (context != null) {
            screens.replace(new UpgradeTreeScreen(context));
        }
    }

    /**
     * Goes back to the hub, which is where a run is started. The navigation's Play item is the
     * hub's own section, not a shortcut that flies: from Goals it unwinds the stack to the
     * screen whose START RUN plays the world, the tier and the mode the profile carries.
     */
    private void openHome() {
        if (context == null) {
            return;
        }
        screens.popTo(MainMenuScreen.class);
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
     * The bottom navigation, with Goals on the gold plate.
     *
     * @return the bar
     */
    public NavBar nav() {
        return nav;
    }

    /**
     * The challenge carousel of the Challenges tab.
     *
     * @return the carousel
     */
    public ListView challengeList() {
        return list;
    }

    /**
     * The Play button of the Challenges tab.
     *
     * @return the button
     */
    public Button playButton() {
        return play;
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
        List<String> labels = List.of(strings.get(StringKey.CHALLENGES_TITLE),
                strings.get(StringKey.ACHIEVEMENTS_TAB_ACHIEVEMENTS),
                strings.get(StringKey.ACHIEVEMENTS_TAB_MILESTONES),
                strings.get(StringKey.ACHIEVEMENTS_TAB_COLLECTIONS));
        for (int i = 0; i < tabs.tabs().size() && i < labels.size(); i++) {
            tabs.tabs().get(i).setLabel(labels.get(i));
        }
        int selected = list.selectedIndex();
        list.setLabel(strings.get(StringKey.CHALLENGES_TITLE));
        list.setOptions(listOptions());
        list.selectQuietly(selected);
        nav.button(SectionNav.SHOP).setText(strings.get(StringKey.MENU_SHOP));
        nav.button(SectionNav.BIRDS).setText(strings.get(StringKey.MENU_BIRDS));
        nav.button(SectionNav.PLAY).setText(strings.get(StringKey.MENU_PLAY));
        nav.button(SectionNav.FORGE).setText(strings.get(StringKey.MENU_NAV_FORGE));
        nav.button(SectionNav.GOALS).setText(strings.get(StringKey.MENU_NAV_GOALS));
        refreshPlay();
        rebuild();
        shownLanguage = strings.language();
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(tabs);
        scroll = 0;
        stackVersionSeen = screens.stackVersion();
        screens.setLetterboxRgb(PALETTE.letterbox());
        ensureLayout();
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
    }

    @Override
    public void tick(InputFrame input) {
        ensureLayout();
        InputFrame frame = input;
        if (tabs.tick(input)) {
            frame = input.withoutPresses(EnumSet.of(InputAction.LEFT, InputAction.RIGHT));
        }
        ring.handle(frame);
        if (onChallenges()) {
            list.tick(input);
        } else if (input.wheel() != 0) {
            scroll = MathUtil.clamp(scroll - input.wheel() * (double) WHEEL_STEP, 0, maxScroll());
        }
        dispatchNavClicks(input);
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else if (screens.stackVersion() != stackVersionSeen) {
            // Back from a challenge run (a pop never re-enters, D17): what the run unlocked,
            // completed or counted is what the tab shows now.
            stackVersionSeen = screens.stackVersion();
            screens.setLetterboxRgb(PALETTE.letterbox());
            refreshTexts();
        }
        if (input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
        }
    }

    /**
     * The four navigation routes out of Goals are not ring nodes — the tests pin the ring to
     * the bar plus the tab's own controls — so a click on one of them is dispatched here. A
     * pointer user leaves through the bottom band the same way a keyboard user leaves through
     * Escape.
     *
     * @param input the tick input with the pointer in logical coordinates
     */
    private void dispatchNavClicks(InputFrame input) {
        if (!input.isMouseJustPressed(Keys.BUTTON_LEFT)) {
            return;
        }
        double mx = input.mouseX();
        if (input.mouseY() < navTop) {
            return;
        }
        NavButton[] buttons = {nav.button(SectionNav.SHOP), nav.button(SectionNav.BIRDS),
                nav.button(SectionNav.PLAY), nav.button(SectionNav.FORGE)};
        Runnable[] routes = {this::openShop, this::openBirds, this::openHome,
                this::openForge};
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].isEnabled() && buttons[i].contains(mx, input.mouseY())) {
                UiCues.select();
                routes[i].run();
                return;
            }
        }
    }

    private double maxScroll() {
        return Math.max(0, contentHeight - (scrollBottom - scrollTop));
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        ensureLayout();
        g.setFont(Fonts.bold(titleSize));
        GoalsArt.displayTitle(g, strings.get(StringKey.GOALS_TITLE), panelX + 6, titleBaseline);
        tabs.render(g);
        if (onChallenges()) {
            paintChallenges(g);
        } else {
            paintScrolled(g);
        }
        nav.render(g);
    }

    // ------------------------------------------------------------------ layout

    /**
     * Rebuilds every band from the surface the presenters last published. This is the one
     * place the screen computes geometry: the header to the safe top, the tab row under it,
     * the panel taking its reference share of the surface with the background reveal as the
     * slack down to the navigation band.
     */
    private void relayout() {
        GoalsSurface surface = GoalsSurface.of(screens.metrics());
        surfaceTop = surface.contentTop();
        surfaceH = surface.height();
        navTop = surface.navTop();
        laidOut = screens.metrics();
        titleSize = Math.max(30, (int) Math.round(Playfield.WIDTH * TITLE_RATIO));
        int headerTop = surfaceTop + (int) Math.round(surfaceH * TOP_SAFE_RATIO);
        int headerH = Math.max((int) Math.round(surfaceH * HEADER_RATIO), titleSize + 10);
        titleBaseline = headerTop + (int) Math.round(headerH * 0.75);
        tabsTop = headerTop + headerH + (int) Math.round(surfaceH * HEADER_GAP_RATIO);
        tabsH = Math.max(TabBar.DEFAULT_HEIGHT, (int) Math.round(surfaceH * TABS_RATIO));
        tabs.setBounds(panelX(), tabsTop, panelWidth(), tabsH);
        panelX = panelX();
        panelW = panelWidth();
        panelTop = tabsTop + tabsH + (int) Math.round(surfaceH * TABS_GAP_RATIO);
        int reveal = Math.max(8, (int) Math.round(surfaceH * revealRatio()));
        panelBottom = Math.max(panelTop + 260,
                Math.min(navTop - reveal, panelTop + (int) Math.round(surfaceH * panelRatio())));
        headingSize = Math.max(15, (int) Math.round(Playfield.WIDTH * HEADING_RATIO));
        SectionNav.layoutRow(nav, screens.metrics());
        if (onChallenges()) {
            relayoutChallenges();
        } else {
            scrollTop = panelTop + PANEL_PAD;
            scrollBottom = panelBottom - PANEL_PAD;
            scroll = MathUtil.clamp(scroll, 0, maxScroll());
        }
    }

    private static int panelX() {
        return (int) Math.round(Playfield.WIDTH * PANEL_INSET_RATIO);
    }

    private static int panelWidth() {
        return Playfield.WIDTH - 2 * panelX();
    }

    private double revealRatio() {
        switch (tabs.selectedIndex()) {
            case 1:
                return REVEAL_ACHIEVEMENTS;
            case 2:
                return REVEAL_MILESTONES;
            case 3:
                return REVEAL_COLLECTIONS;
            case 0:
            default:
                return REVEAL_CHALLENGES;
        }
    }

    private double panelRatio() {
        switch (tabs.selectedIndex()) {
            case 1:
                return PANEL_ACHIEVEMENTS;
            case 2:
                return PANEL_MILESTONES;
            case 3:
                return PANEL_COLLECTIONS;
            case 0:
            default:
                return PANEL_CHALLENGES;
        }
    }

    /**
     * Lays the Challenges tab out: the arrows' band, the status pill, the objective, the
     * World/Tier pair and the four field rows fill the panel between its top and the call to
     * action pinned above its bottom edge, with the row heights taking whatever the surface
     * gives and the gaps absorbing the rest.
     */
    private void relayoutChallenges() {
        int innerX = panelX + PANEL_PAD;
        int innerW = panelW - 2 * PANEL_PAD;
        arrowSize = Math.max(30, Math.min(46, (int) Math.round(Playfield.WIDTH * ARROW_RATIO)));
        carouselTop = panelTop + PANEL_PAD;
        list.setArrowLayout(4, arrowSize);
        list.setBounds(innerX, carouselTop, innerW, arrowSize);
        pillH = isUnlocked(selectedChallenge()) ? 0
                : Math.max(20, Math.min(34, (int) Math.round(surfaceH * 0.036)));
        pairH = Math.max(44, Math.min(66, (int) Math.round(Playfield.WIDTH * PAIR_RATIO)));
        int ctaH = Math.max(40, Math.min(56, (int) Math.round(Playfield.WIDTH * CTA_RATIO)));
        int objectiveH = 18;
        int gaps = pillH > 0 ? 8 : 7;
        int inner = panelBottom - PANEL_PAD - carouselTop;
        int avail = inner - arrowSize - pillH - objectiveH - pairH - ctaH;
        rowH = MathUtil.clamp((avail - gaps * GAP_BASE) / 4, MIN_ROW_H, MAX_ROW_H);
        int slack = Math.max(0, avail - 4 * rowH - gaps * GAP_BASE);
        int gap = GAP_BASE + Math.min(slack / gaps, GAP_MAX - GAP_BASE);
        int y = carouselTop + arrowSize + gap;
        if (pillH > 0) {
            pillTop = y;
            y += pillH + gap;
        }
        objectiveBaseline = y + 13;
        y += objectiveH + gap;
        pairTop = y;
        y += pairH + gap;
        for (int i = 0; i < rowTops.length; i++) {
            rowTops[i] = y;
            y += rowH + gap;
        }
        play.setBounds(innerX, panelBottom - PANEL_PAD - ctaH, innerW, ctaH);
        String worldId = selectedChallenge().world();
        worldSwatch = content.has(GameContent.WORLDS) && content.worlds().contains(worldId)
                ? new WorldSwatch(content.worlds().get(worldId).palette()) : null;
    }

    /** Rebuilds the layout and the tab's row heights when the surface moved (a resize). */
    private void ensureLayout() {
        LayoutMetrics metrics = screens.metrics();
        GoalsSurface surface = GoalsSurface.of(metrics);
        if (!metrics.equals(laidOut)) {
            relayout();
            // The list tabs' row heights were sized for the old surface; the resize must re-scale
            // them, or a tall panel keeps the rows of the surface the screen was built on.
            rebuild();
        }
    }

    // ------------------------------------------------------------------ painting

    private void paintChallenges(Graphics2D g) {
        GoalsArt.panel(g, panelX, panelTop, panelW, panelBottom - panelTop);
        list.render(g);
        ChallengeDef def = selectedChallenge();
        int innerX = panelX + PANEL_PAD;
        int innerW = panelW - 2 * PANEL_PAD;
        double centreX = panelX + panelW / 2.0;
        g.setFont(Fonts.bold(24));
        g.setColor(GoalsArt.BAR_FILL);
        TextPainter.drawCentered(g, ProgressionText.name(strings, ContentKind.CHALLENGE,
                def.id()), centreX,
                TextPainter.centeredBaseline(g, carouselTop + arrowSize / 2.0));
        if (pillH > 0) {
            int pillW = (int) Math.round(Playfield.WIDTH * PILL_RATIO);
            int pillX = panelX + (panelW - pillW) / 2;
            GoalsArt.pill(g, pillX, pillTop, pillW, pillH);
            g.setFont(Fonts.bold(12));
            String label = strings.get(StringKey.CHALLENGES_LOCKED_TITLE);
            double group = GoalsPlayButton.LOCK_SIZE + GoalsPlayButton.LOCK_GAP
                    + TextPainter.width(g, label);
            double left = pillX + (pillW - group) / 2.0;
            ProceduralArt.drawPadlock(g, left + GoalsPlayButton.LOCK_SIZE / 2.0,
                    pillTop + pillH / 2.0, GoalsPlayButton.LOCK_SIZE, GoalsArt.PALE_INK);
            g.setColor(GoalsArt.PALE_INK);
            TextPainter.draw(g, label, left + GoalsPlayButton.LOCK_SIZE + GoalsPlayButton.LOCK_GAP,
                    TextPainter.centeredBaseline(g, pillTop + pillH / 2.0));
        }
        g.setFont(Fonts.regular(13));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, TextPainter.ellipsise(g, objectiveText(def), innerW),
                centreX, objectiveBaseline);
        paintPair(g, innerX, innerW);
        paintFieldRow(g, 0, innerX, innerW, FieldIcon.SCROLL, strings.get(StringKey.GOALS_FIELD_RULES),
                rulesLine(def), false);
        paintFieldRow(g, 1, innerX, innerW, FieldIcon.CHART, strings.get(StringKey.GOALS_FIELD_PROGRESS),
                recordLine(def), false);
        paintFieldRow(g, 2, innerX, innerW, FieldIcon.COIN, strings.get(StringKey.GOALS_FIELD_REWARD),
                rewardsLine(def), true);
        paintFieldRow(g, 3, innerX, innerW, FieldIcon.LOCK, strings.get(StringKey.GOALS_FIELD_UNLOCK),
                ProgressionText.unlockText(strings, content, def.unlock(),
                        hasProfile ? profile : null), false);
        play.render(g);
    }

    /** The glyph at the left of one full-width field row. */
    private enum FieldIcon {
        /** The rules: the document. */
        SCROLL,
        /** The progress: the small bar chart. */
        CHART,
        /** The reward: the gold coin. */
        COIN,
        /** The unlock: the pale padlock. */
        LOCK
    }

    /**
     * One full-width field row: the pale glyph, the muted label over the value, the value in
     * gold on the Reward row and wrapped to a second line when a translation runs long.
     *
     * @param g the context
     * @param index the row's index in {@link #rowTops}
     * @param innerX the panel's inner left edge
     * @param innerW the panel's inner width
     * @param icon which glyph to draw
     * @param label the field's name
     * @param value the field's value
     * @param gold whether the value is the gold reward
     */
    private void paintFieldRow(Graphics2D g, int index, int innerX, int innerW, FieldIcon icon,
            String label, String value, boolean gold) {
        int x = innerX;
        int y = rowTops[index];
        int w = innerW;
        int cy = y + rowH / 2;
        GoalsArt.card(g, x, y, w, rowH);
        double iconX = x + 18;
        switch (icon) {
            case SCROLL:
                ProceduralArt.drawScroll(g, iconX, cy, 17, ProceduralArt.TEXT_MUTED,
                        ProceduralArt.TEXT_DARK);
                break;
            case CHART:
                GoalsArt.barChart(g, iconX, cy, 19, ProceduralArt.TEXT_MUTED);
                break;
            case COIN:
                ProceduralArt.drawCoin(g, COIN_SCRATCH, iconX, cy, 9, 1);
                break;
            case LOCK:
            default:
                ProceduralArt.drawPadlock(g, iconX, cy, 17, GoalsArt.PALE_INK);
                break;
        }
        int textX = x + 42;
        int room = w - (textX - x) - 10;
        g.setFont(Fonts.bold(12));
        boolean wraps = TextPainter.width(g, value) > room;
        g.setFont(Fonts.regular(10));
        g.setColor(ProceduralArt.TEXT_MUTED);
        if (wraps) {
            TextPainter.draw(g, label, textX, y + 10);
            g.setFont(Fonts.bold(12));
            g.setColor(gold ? GoalsArt.BAR_FILL : ProceduralArt.TEXT_LIGHT);
            String[] halves = wrap(g, value, room);
            TextPainter.draw(g, halves[0], textX, y + 21);
            TextPainter.draw(g, halves[1], textX, y + 33);
        } else {
            TextPainter.draw(g, label, textX, cy - 3);
            g.setFont(Fonts.bold(12));
            g.setColor(gold ? GoalsArt.BAR_FILL : ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, value, textX, cy + 11);
        }
    }

    /**
     * Splits a value that does not fit its row across two lines, at the last space of the
     * first line's room.
     *
     * @param g the context, carrying the value's font
     * @param text the value
     * @param room the width of one line
     * @return the two lines
     */
    private static String[] wrap(Graphics2D g, String text, int room) {
        int cut = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ' && TextPainter.width(g, text.substring(0, i)) <= room) {
                cut = i;
            }
        }
        if (cut < 0) {
            return new String[] {TextPainter.ellipsise(g, text, room), ""};
        }
        return new String[] {text.substring(0, cut), text.substring(cut + 1)};
    }

    /**
     * The World and Tier pair: two half-width cards, each with its icon tile and its label
     * over its value.
     *
     * @param g the context
     * @param innerX the panel's inner left edge
     * @param innerW the panel's inner width
     */
    private void paintPair(Graphics2D g, int innerX, int innerW) {
        ChallengeDef def = selectedChallenge();
        int halfGap = 8;
        int halfW = (innerW - halfGap) / 2;
        paintPairCard(g, innerX, halfW, strings.get(StringKey.GOALS_FIELD_WORLD),
                ProgressionText.name(strings, ContentKind.WORLD, def.world()), true);
        paintPairCard(g, innerX + halfW + halfGap, innerW - halfW - halfGap,
                strings.get(StringKey.GOALS_FIELD_TIER),
                ProgressionText.name(strings, ContentKind.TIER, def.tier()), false);
    }

    /**
     * One half of the World/Tier pair.
     *
     * @param g the context
     * @param x the card's left edge
     * @param w the card's width
     * @param label the field's name
     * @param value the field's value
     * @param world {@code true} for the world's swatch tile, {@code false} for the tier's
     *     gold chart
     */
    private void paintPairCard(Graphics2D g, int x, int w, String label, String value,
            boolean world) {
        g.setColor(GoalsArt.PAIR_FILL);
        g.fillRoundRect(x, pairTop, w, pairH, 7, 7);
        g.setColor(GoalsArt.CARD_STROKE);
        g.drawRoundRect(x, pairTop, w, pairH, 7, 7);
        int tileS = Math.min(34, pairH - 12);
        int tileY = pairTop + (pairH - tileS) / 2;
        GoalsArt.tile(g, x + 6, tileY, tileS);
        if (world && worldSwatch != null) {
            worldSwatch.paint(g, null, x + 6 + tileS / 2.0, tileY + tileS / 2.0, tileS - 8);
        } else {
            GoalsArt.barChart(g, x + 6 + tileS / 2.0, tileY + tileS / 2.0, tileS * 0.55,
                    GoalsArt.BAR_FILL);
        }
        int textX = x + 6 + tileS + 8;
        g.setFont(Fonts.regular(10));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, label, textX, pairTop + pairH / 2.0 - 3);
        g.setFont(Fonts.bold(12));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, TextPainter.ellipsise(g, value, w - (textX - x) - 6), textX,
                pairTop + pairH / 2.0 + 11);
    }

    private void paintScrolled(Graphics2D g) {
        GoalsArt.panel(g, panelX, panelTop, panelW, panelBottom - panelTop);
        Shape oldClip = g.getClip();
        int crop = Math.min(panelBottom - 1, cropBottom());
        g.clipRect(panelX + 1, panelTop + 1, panelW - 2,
                Math.max(1, crop - panelTop - 1));
        g.translate(0.0, scrollTop - scroll);
        switch (tabs.selectedIndex()) {
            case 1:
                paintAchievements(g);
                break;
            case 2:
                paintMilestones(g);
                break;
            case 3:
            default:
                paintCollections(g);
                break;
        }
        g.translate(0.0, -(scrollTop - scroll));
        g.setClip(oldClip);
        if (maxScroll() > 0) {
            GoalsArt.scrollbar(g, panelX + panelW - 12, scrollTop, scrollBottom - scrollTop,
                    contentHeight, scroll);
        }
    }

    /**
     * The surface-space y the scrolled content may render down to: the bottom of the last card
     * that sits fully inside the viewport, or the panel's inner bottom when everything fits.
     * The references crop their lists at a card boundary — a card sliced through a text line
     * reads as a bug, and a caption cut to a two-pixel ghost reads worse (ff-vision 1.1, 3.1).
     *
     * @return the crop line's surface y
     */
    private int cropBottom() {
        double contentVisible = scroll + (scrollBottom - scrollTop);
        double full = -1;
        switch (tabs.selectedIndex()) {
            case 1:
                full = SUMMARY_HEADING_H + 4 + SUMMARY_BAR_H;
                for (int i = 0; i < cards.size(); i++) {
                    AchievementCard card = cards.get(i);
                    double bottom = card.y() + (card.held() ? heldRowH : lockedRowH);
                    if (bottom <= contentVisible && bottom > full) {
                        full = bottom;
                    }
                }
                break;
            case 2:
                full = MILESTONE_HEADING_H;
                for (int i = 0; i < bars.size(); i++) {
                    double bottom = bars.get(i).y() + milestoneRowH;
                    if (bottom <= contentVisible && bottom > full) {
                        full = bottom;
                    }
                }
                if (contentHeight <= contentVisible) {
                    full = contentHeight;
                }
                break;
            case 3:
            default:
                for (int i = 0; i < bars.size(); i++) {
                    ProgressBar bar = bars.get(i);
                    double bottom = bar.y() + (barCategories.get(i)
                            .equals(CollectionProgress.ALL)
                            ? everythingRowH : collectionRowH);
                    if (bottom <= contentVisible && bottom > full) {
                        full = bottom;
                    }
                }
                // The closing caption is fully visible only when the whole tab fits.
                if (contentHeight <= contentVisible) {
                    full = contentHeight;
                }
                break;
        }
        if (full < 0) {
            return panelBottom - 1;
        }
        return (int) Math.round(scrollTop - scroll + full);
    }

    private void paintAchievements(Graphics2D g) {
        int innerX = panelX + PANEL_PAD;
        g.setFont(Fonts.bold(headingSize));
        g.setColor(GoalsArt.BAR_FILL);
        TextPainter.draw(g, line("count"), innerX, SUMMARY_HEADING_H - 8);
        int barX = innerX + 2;
        int barW = (int) Math.round(CONTENT_W * 0.72);
        GoalsArt.bar(g, barX, SUMMARY_HEADING_H + 4, barW, SUMMARY_BAR_H,
                achievementTotal == 0 ? 0 : achievementUnlocked / (double) achievementTotal);
        g.setFont(Fonts.regular(12));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, strings.format(StringKey.GOALS_PERCENT,
                CollectionProgress.percentOf(achievementUnlocked, achievementTotal)),
                barX + barW + 8, SUMMARY_HEADING_H + 4 + SUMMARY_BAR_H / 2.0 + 4);
        int w = panelW - 2 * PANEL_PAD - 6;
        for (int i = 0; i < cards.size(); i++) {
            paintAchievementCard(g, cards.get(i), innerX, w);
        }
    }

    /**
     * One achievement card: the green complete state with its check and its date when held,
     * the padlock tile and the gold reward when locked. No veil — the text stays readable.
     *
     * @param g the context
     * @param card the card model
     * @param x the card's left edge
     * @param w the card's width
     */
    private void paintAchievementCard(Graphics2D g, AchievementCard card, int x, int w) {
        int y = (int) Math.round(card.y());
        if (card.held()) {
            // The mock stacks name, subtitle and unlock date as three left-aligned lines
            // inside the text column (ff-vision 1.4).
            GoalsArt.cardComplete(g, x, y, w, heldRowH);
            GoalsArt.checkBadge(g, x + 24, y + heldRowH / 2.0, 26);
            g.setFont(Fonts.bold(13));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, card.name(), x + 46, y + 18);
            g.setFont(Fonts.regular(10));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, TextPainter.ellipsise(g, card.desc(), w - 56), x + 46, y + 32);
            TextPainter.draw(g, card.footer(), x + 46, y + heldRowH - 12);
        } else {
            GoalsArt.card(g, x, y, w, lockedRowH);
            GoalsArt.tile(g, x + 8, y + (lockedRowH - 32) / 2, 32);
            ProceduralArt.drawPadlock(g, x + 24, y + lockedRowH / 2.0, 15,
                    GoalsArt.PALE_INK);
            g.setFont(Fonts.bold(13));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, card.name(), x + 46, y + 20);
            g.setFont(Fonts.regular(10));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, TextPainter.ellipsise(g, card.desc(), w - 56), x + 46, y + 35);
            if (!card.footer().isEmpty()) {
                g.setFont(card.footerGold() ? Fonts.bold(11) : Fonts.regular(10));
                g.setColor(card.footerGold() ? GoalsArt.BAR_FILL : ProceduralArt.TEXT_MUTED);
                if (card.footerGold()) {
                    // Coin and reward as one right-aligned group, per the mock (ff-vision 1.5).
                    double textW = TextPainter.width(g, card.footer());
                    double right = x + w - 10;
                    ProceduralArt.drawCoin(g, COIN_SCRATCH, right - textW - 12,
                            y + lockedRowH / 2.0, 8, 1);
                }
                TextPainter.drawRight(g, card.footer(), x + w - 10,
                        y + lockedRowH / 2.0 + 4);
            }
        }
    }

    private void paintMilestones(Graphics2D g) {
        int innerX = panelX + PANEL_PAD;
        int w = panelW - 2 * PANEL_PAD;
        g.setFont(Fonts.bold(headingSize));
        g.setColor(GoalsArt.BAR_FILL);
        TextPainter.draw(g, line("next"), innerX, MILESTONE_HEADING_H - 12);
        for (int i = 0; i < bars.size(); i++) {
            ProgressBar bar = bars.get(i);
            int y = (int) Math.round(bar.y());
            GoalsArt.card(g, innerX, y, w, milestoneRowH);
            // The counter first: its measured width is the title's room. The title shrinks a
            // step at a time before it may ellipsize, so a longer translation never eats the
            // value it reports — "Recompensa do nível 5: 150 moedas" must read whole
            // (ff-vision 2.2).
            g.setFont(Fonts.regular(11));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.drawRight(g, bar.valueText(), innerX + w - 12, y + 24);
            int counterW = TextPainter.width(g, bar.valueText());
            int labelRoom = w - 24 - counterW - 10;
            String label = bar.label();
            int size = 13;
            g.setFont(Fonts.bold(size));
            while (size > 10 && TextPainter.width(g, label) > labelRoom) {
                size--;
                g.setFont(Fonts.bold(size));
            }
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, TextPainter.ellipsise(g, label, labelRoom), innerX + 12, y + 24);
            GoalsArt.bar(g, innerX + 12, y + milestoneRowH - 36, w - 24, 18, bar.value());
        }
        String none = line("none");
        if (none != null) {
            g.setFont(Fonts.regular(12));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, none, innerX, contentHeight - FOOTER_H + 16);
        }
    }

    private void paintCollections(Graphics2D g) {
        int innerX = panelX + PANEL_PAD;
        int w = panelW - 2 * PANEL_PAD - 4;
        for (int i = 0; i < bars.size(); i++) {
            paintCollectionRow(g, bars.get(i), barCategories.get(i), innerX, w);
        }
        String footer = line("footer");
        if (footer != null) {
            g.setFont(Fonts.regular(11));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.drawCentered(g, footer, panelX + panelW / 2.0,
                    contentHeight - FOOTER_H + 15);
        }
    }

    /**
     * One collection row: the category's art, its name over its description, the counter, the
     * thin bar under the text column and the pale chevron that says the rows are tappable.
     *
     * @param g the context
     * @param bar the row's model
     * @param category the row's category id
     * @param x the row's left edge
     * @param w the row's width
     */
    private void paintCollectionRow(Graphics2D g, ProgressBar bar, String category, int x, int w) {
        boolean everything = category.equals(CollectionProgress.ALL);
        int height = everything ? everythingRowH : collectionRowH;
        int y = (int) Math.round(bar.y());
        if (everything) {
            GoalsArt.cardEverything(g, x, y, w, height);
        } else {
            GoalsArt.card(g, x, y, w, height);
        }
        double iconX = x + 26;
        double iconY = y + height / 2.0;
        paintCategoryIcon(g, category, iconX, iconY, 30, everything);
        int textX = x + 54;
        int counterRoom = 84;
        g.setFont(Fonts.bold(13));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, TextPainter.ellipsise(g, bar.label(),
                w - (textX - x) - counterRoom - 22), textX, y + 18);
        g.setFont(Fonts.regular(10));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, TextPainter.ellipsise(g, description(category),
                w - (textX - x) - counterRoom - 22), textX, y + 32);
        g.setFont(Fonts.regular(11));
        TextPainter.drawRight(g, bar.valueText(), x + w - 24, y + 18);
        ProceduralArt.drawChevron(g, x + w - 12, iconY, 11, ProceduralArt.TEXT_MUTED);
        // The Collections mock keeps its own darker, unstroked slot for the row bars
        // (ff-vision 2.1) — the lighter stroked meter belongs to the other tabs.
        GoalsArt.barDark(g, textX, y + height - (everything ? 26 : 22), w - (textX - x) - 40,
                10, bar.value());
    }

    private String description(String category) {
        StringKey key = StringKey.byKey("goals.collections.desc." + category);
        return key == null ? "" : strings.get(key);
    }

    /**
     * The art glyph of one collection category — the multi-colour icons the reference gives
     * the rows, resolved once per row per frame from the procedural set.
     *
     * @param g the context
     * @param category the category id
     * @param cx the centre x
     * @param cy the centre y
     * @param size the glyph size
     * @param everything {@code true} for the Everything card's gold crown
     */
    private void paintCategoryIcon(Graphics2D g, String category, double cx, double cy,
            double size, boolean everything) {
        if (everything) {
            ProceduralArt.drawCrown(g, cx, cy, size * 0.9, GoalsArt.GOLD);
            return;
        }
        switch (category) {
            case "birds":
                ProceduralArt.drawBirdSilhouette(g, cx, cy, size * 0.9, GoalsArt.GOLD);
                break;
            case "abilities":
                ProceduralArt.drawSpark(g, cx, cy, size * 0.42, ABILITY_SPARK);
                break;
            case "worlds":
                ProceduralArt.drawIsland(g, cx, cy - size * 0.3, size * 1.1, size * 0.7,
                        ISLAND_SOIL, ISLAND_GRASS, ProceduralArt.TEXT_MUTED);
                break;
            case "challenges":
                // The mock's red pennant, not a document (ff-vision 3.3).
                GoalsArt.flag(g, cx, cy, size * 0.85, ProceduralArt.TEXT_MUTED, FLAG_RED);
                break;
            case "cosmetics":
                GoalsArt.paletteDisc(g, cx, cy, size);
                break;
            case "achievements":
                // The mock's gold star, not the spark's diamond (ff-vision 3.3).
                GoalsArt.star(g, cx, cy, size * 0.9, GoalsArt.GOLD);
                break;
            case "upgrades":
            default:
                ProceduralArt.drawHammer(g, cx, cy, size * 0.8, 0.5,
                        ProceduralArt.TEXT_MUTED, ProceduralArt.WOOD);
                break;
        }
    }

    /** One achievement card of the achievements tab. */
    private record AchievementCard(String name, String desc, String footer, boolean footerGold,
            boolean held, double y) {
    }

    /** One milestone the tab counts towards. */
    private record Milestone(String label, long current, long target, long remaining,
            int order) {
    }

    /**
     * One text line of the current tab.
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
