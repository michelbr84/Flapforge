package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.GrantDef;
import io.github.michelbr84.flapforge.content.defs.GrantType;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.TreeDef;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.PurchaseResult;
import io.github.michelbr84.flapforge.progression.PurchaseStatus;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.CtaButton;
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import io.github.michelbr84.flapforge.ui.component.NavBar;
import io.github.michelbr84.flapforge.ui.component.SectionNav;
import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import io.github.michelbr84.flapforge.ui.component.TabBar;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Tooltip;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The Forge (M13 rebuild): three upgrade trees, one tab each, where coins become physics.
 *
 * <p>The screen is five bands laid out against the surface's {@link LayoutMetrics}. A compact
 * header pinned to the content top carries the title, the open tree's subtitle, the wallet in
 * its coin pill and the hub's forge scene drawn small, so the place reads as the same room the
 * main menu shows. Under it the three tree tabs wear their glyph and the gold accent. The tree
 * viewport lays each tree's nodes out by {@code tier} as two columns of row cards under green
 * tier pills, with the prerequisite edges drawn as light-yellow elbows under the cards,
 * endpoints taken from the shipped {@code prereqs} graph. The detail panel shows the selected
 * node &mdash; hero glyph over a gold glow, name, level, description, its status in words, the
 * call to action and the red not-enough-coins note &mdash; then a divider and the attribute
 * summary: every stat the tree can touch, its live resolved value and a five-segment pip bar
 * normalised over the stat's own clamp range. The section navigation, pinned to the bottom of
 * the surface, closes the screen with Forge on the gold plate, replacing the old full-width
 * Back button; the BACK key still pops. At the classic 420x640 surface the bands are the
 * constants they always were (header 0&ndash;100, tabs 100&ndash;140, tree 140&ndash;330,
 * detail 330&ndash;582, navigation 582&ndash;640); a taller surface gives the extra room to the
 * tree viewport and the detail panel in the proportion of those reference heights.
 *
 * <p>Buying is deliberate: activating a card only <em>selects</em> it; the purchase happens
 * through the panel's call to action, through {@link UpgradeManager#buy} for a node and
 * {@link UpgradeManager#buyTree} for a locked tree &mdash; the same atomic routes the shop uses,
 * so a tree bought here lands in the save byte-for-byte as one bought there. A refusal is
 * surfaced, never swallowed: the toast says why and the wallet, the cards and the live stats
 * stand still. Only {@link PurchaseStatus#OK} changes anything. One refusal is silent by
 * design: a selection that is not a card of the tree that is open — left over from a previous
 * tab, or stale — is simply not purchasable.
 *
 * <p>The tree scrolls by moving its cards, not the canvas, so every node stays in screen
 * coordinates and one focus ring runs from the tabs through the cards and the call to action
 * down into the navigation. A card scrolled out of the band is clipped away and stops answering
 * the pointer ({@link ForgeNodeCard#setViewport}).
 */
public final class UpgradeTreeScreen implements Screen {

    /** Side margin. */
    public static final int MARGIN = 12;
    /** Width of the content column. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * MARGIN;
    /** Height of the compact header band: title, subtitle, wallet, scene. */
    public static final int HEADER_H = 100;
    /** Top of the tree tab bar. */
    public static final int TABS_TOP = 100;
    /** Height of the tree tab bar. */
    public static final int TABS_H = 40;
    /** First visible row of the tree viewport. */
    public static final int TREE_TOP = 140;
    /** Last visible row of the tree viewport. */
    public static final int TREE_BOTTOM = 330;
    /** Height of one node card. */
    public static final int NODE_H = 46;
    /** Columns of nodes per tier. */
    public static final int COLUMNS = 2;
    /** Height of a tier pill band. */
    public static final int TIER_LABEL_H = 16;
    /** Vertical gap between two card rows of the same tier. */
    public static final int ROW_GAP = 6;
    /** Vertical gap between two tiers. */
    public static final int TIER_GAP = 2;
    /** Top of the detail panel. */
    public static final int DETAIL_TOP = 330;
    /** Height of the detail panel: purchase half, divider, attribute summary. */
    public static final int DETAIL_H = 252;
    /** Height of the tree viewport at the reference 640-row surface. */
    private static final int TREE_VIEW_H = TREE_BOTTOM - TREE_TOP;
    /** Rows above the navigation band at the reference surface: the four bands summed. */
    private static final int STRETCH_H = HEADER_H + TABS_H + TREE_VIEW_H + DETAIL_H;
    /** Logical pixels one wheel notch scrolls. */
    public static final int WHEEL_STEP = 38;

    /** Width of the header coin pill. */
    public static final int WALLET_PILL_W = 136;
    /** Height of the header coin pill. */
    public static final int WALLET_PILL_H = 28;
    /** Width of the panel's call to action plate. */
    public static final int CTA_W = CONTENT_W - 16;
    /** Height of the panel's call to action. */
    public static final int CTA_H = 40;
    /** Panel-local top of the call to action plate. */
    public static final int CTA_OFFSET = 108;
    /** Panel-local offset of the divider between the purchase half and the stat summary. */
    public static final int DIVIDER_OFFSET = 154;
    /** Panel-local baseline of the attribute summary heading. */
    public static final int SUMMARY_OFFSET = 172;
    /** Panel-local baseline of the first stat row. */
    public static final int STATS_OFFSET = 188;
    /** Baseline pitch of one stat row. */
    public static final int STAT_PITCH = 14;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 40;
    private static final int SUBTITLE_BASELINE = 58;
    /**
     * Right edge the header subtitle may reach: the forge scene's bird sprite sits from here on,
     * and the tail of a long translation must never run under it.
     */
    private static final int SUBTITLE_LIMIT_X = 322;
    private static final int WALLET_PILL_X = Playfield.WIDTH - WALLET_PILL_W - 14;
    private static final int WALLET_PILL_Y = 12;
    private static final int PANEL_TEXT_X = MARGIN + 72;
    private static final int HERO_CX = MARGIN + 38;
    /** Panel-local y of the hero glyph's centre, from the detail panel's top. */
    private static final int HERO_OFFSET = 48;
    private static final int HERO_SIZE = 46;
    private static final int SCROLLBAR_X = Playfield.WIDTH - 8;
    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_MIN_H = 20;
    private static final Color SCROLLBAR = new Color(0xF4, 0xF8, 0xF8, 0x90);

    /**
     * The header scene, drawn small: the island is placed so its centre-bottom lands at
     * (350, 96), under the wallet pill, and the band ends before the tabs.
     */
    private static final double SCENE_TX = 272.3;
    private static final double SCENE_TY = -51.26;
    private static final double SCENE_SCALE = 0.37;
    private static final long BOB_PERIOD_TICKS = 150;
    private static final double BOB_AMPLITUDE = 3.0;

    private final GameContext context;
    private final ScreenManager screens;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final UpgradeManager upgrades;
    private final ToastLayer toasts;
    private final FocusRing ring = new FocusRing();
    private final TabBar tabs = new TabBar();
    private final CardGrid nodes = new CardGrid();
    private final NavBar nav = new NavBar();
    private final CtaButton cta;
    private final CurrencyDisplay wallet = new CurrencyDisplay();
    private final Tooltip tooltip = new Tooltip();
    private final ForgeScene scene = new ForgeScene();
    private final List<TierBand> bands = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private final List<String> detailLines = new ArrayList<>();
    private final List<StatRow> statRows = new ArrayList<>();
    private final Map<String, Double> cardOffset = new LinkedHashMap<>();
    private final IconPainter coinIcon = ForgeArt::coinGlyph;
    private double contentHeight;
    private double scroll;
    private long ticks;
    private double prevBob;
    private double bob;
    private boolean reduceShown;
    private String currentNodeId;
    private String treeLockedText = "";
    private String shownLanguage;
    private int surfaceTop;
    private int tabsTop;
    private int treeTop;
    private int treeBottom;
    private int detailTop;
    private int detailH;
    private int heroCy;
    private LayoutMetrics laidOut;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services
     */
    public UpgradeTreeScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context"), context.screens(),
                context.strings() != null ? context.strings() : Strings.active(),
                context.content(), context.profile(),
                context.canProgress()
                        ? new UpgradeManager(context.progression(), context::saveProfile) : null,
                context.toasts());
    }

    /**
     * Creates the screen.
     *
     * @param screens the screen stack
     * @param strings the string table
     * @param content the loaded content
     * @param profile the profile to read and write
     * @param upgrades the purchase path, or {@code null} for a screen that cannot buy
     * @param toasts the toast queue, or {@code null} for one of its own
     */
    public UpgradeTreeScreen(ScreenManager screens, Strings strings, GameContent content,
            PlayerProfile profile, UpgradeManager upgrades, ToastLayer toasts) {
        this(null, screens, strings, content, profile, upgrades, toasts);
    }

    private UpgradeTreeScreen(GameContext context, ScreenManager screens, Strings strings,
            GameContent content, PlayerProfile profile, UpgradeManager upgrades,
            ToastLayer toasts) {
        this.context = context;
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.upgrades = upgrades;
        this.toasts = toasts == null ? new ToastLayer() : toasts;

        tabs.setAccented(true);
        for (TreeDef tree : content.trees()) {
            tabs.add(tree.id(), ProgressionText.name(strings, ContentKind.TREE, tree.id()))
                    .setIcon(ForgeArt.tabIcon(tree.id()));
        }
        tabs.setOnChange(index -> {
            scroll = 0;
            rebuild();
        });
        nodes.setColumns(COLUMNS);
        nodes.setGap(CardGrid.DEFAULT_GAP, ROW_GAP);

        wallet.setAlign(Align.RIGHT);
        wallet.setFontSize(15);
        wallet.setAmountNow(coins());

        cta = new CtaButton("", this::activateCta);
        relayout();

        SectionNav.build(nav, SectionNav.FORGE, new SectionNav.Routes(this::openShop,
                this::openBirds, this::play, null, this::openGoals), screens.metrics());
        nav.button(SectionNav.SHOP).setEnabled(context != null);
        nav.button(SectionNav.BIRDS).setEnabled(context != null);
        nav.button(SectionNav.PLAY).setEnabled(context != null);
        nav.button(SectionNav.GOALS).setEnabled(context != null);

        rebindScene();
        shownLanguage = strings.language();
        // The first tab a player should land on is one they can actually spend in.
        tabs.selectQuietly(firstUnlockedTree());
        refreshTexts();
    }

    /**
     * Lays the bands out again when the surface the screen is drawn in has changed shape since
     * the last layout — a resized window, or the screen pushed onto a stack over a differently
     * shaped viewport — so the header stays pinned to the content top and the navigation to the
     * bottom while the content between them re-stretches.
     */
    private void relayoutIfResurfaced() {
        LayoutMetrics metrics = screens.metrics();
        if (!metrics.equals(laidOut)) {
            relayout();
            SectionNav.layoutRow(nav, metrics);
        }
    }

    /**
     * Recomputes every band from the surface's metrics: the header pinned to the content top,
     * the tabs under it, and the tree viewport and the detail panel sharing the room down to
     * the navigation band in the proportion of their reference heights (190 tree rows to 252
     * panel rows). At the classic 420x640 surface there is no extra room and every band lands
     * on the public constant it was laid out with before the elastic surface existed.
     */
    private void relayout() {
        LayoutMetrics metrics = screens.metrics();
        surfaceTop = metrics.contentTop();
        tabsTop = surfaceTop + HEADER_H;
        treeTop = tabsTop + TABS_H;
        int extra = Math.max(0, metrics.aboveNavHeight() - STRETCH_H);
        int treeExtra = extra * TREE_VIEW_H / (TREE_VIEW_H + DETAIL_H);
        treeBottom = treeTop + TREE_VIEW_H + treeExtra;
        detailTop = treeBottom;
        detailH = DETAIL_H + extra - treeExtra;
        heroCy = detailTop + HERO_OFFSET;
        tabs.setBounds(MARGIN, tabsTop, CONTENT_W, TABS_H);
        wallet.setBounds(WALLET_PILL_X + 8, surfaceTop + WALLET_PILL_Y, WALLET_PILL_W - 16,
                WALLET_PILL_H);
        cta.setBounds(MARGIN + 8, panelAnchoredToBottom(CTA_OFFSET), CTA_W, CTA_H);
        laidOut = metrics;
    }

    /**
     * The absolute y of a detail-panel offset that anchors to the panel's bottom edge: the
     * divider, the attribute summary and the stat rows keep their distance from the bottom, so
     * the room a taller surface frees opens up between the status lines and the call to action
     * instead of stranding the summary in the middle of the panel.
     *
     * @param classicOffset the offset inside the reference 252-row panel
     * @return the absolute y
     */
    private int panelAnchoredToBottom(int classicOffset) {
        return detailTop + detailH - (DETAIL_H - classicOffset);
    }

    // ------------------------------------------------------------------ accessors

    /**
     * The tab bar over the three trees.
     *
     * @return the bar
     */
    public TabBar tabBar() {
        return tabs;
    }

    /**
     * The nodes of the open tree.
     *
     * @return the grid
     */
    public CardGrid nodeGrid() {
        return nodes;
    }

    /**
     * The id of the open tree.
     *
     * @return the tree id
     */
    public String treeId() {
        return tabs.selectedId();
    }

    /**
     * The node the detail panel and the call to action are about.
     *
     * @return the node id, or {@code null} when the open tree has no nodes
     */
    public String currentNodeId() {
        return currentNodeId;
    }

    /**
     * The bottom navigation, with Forge on the gold plate.
     *
     * @return the bar
     */
    public NavBar nav() {
        return nav;
    }

    /**
     * The one call to action, whose verb is the selected node's state.
     *
     * @return the button
     */
    public CtaButton ctaButton() {
        return cta;
    }

    /**
     * The wallet readout inside the header coin pill.
     *
     * @return the display
     */
    public CurrencyDisplay walletDisplay() {
        return wallet;
    }

    /**
     * The tooltip.
     *
     * @return the tooltip
     */
    public Tooltip tooltip() {
        return tooltip;
    }

    /**
     * The focus ring.
     *
     * @return the ring
     */
    public FocusRing focusRing() {
        return ring;
    }

    /**
     * The lines of the detail panel, in display order: name, level, description, then the status
     * lines. The not-enough-coins note is the last line when present.
     *
     * @return an unmodifiable snapshot
     */
    public List<String> detailLines() {
        return List.copyOf(detailLines);
    }

    /**
     * The live stat rows: every stat the open tree can touch, with its resolved value right now
     * and its pip fill.
     *
     * @return an unmodifiable snapshot
     */
    public List<StatRow> statRows() {
        return List.copyOf(statRows);
    }

    /**
     * The resolved value of one stat in the live panel.
     *
     * @param stat the stat
     * @return the row, or {@code null} when the panel does not show it
     */
    public StatRow statRow(StatId stat) {
        for (StatRow row : statRows) {
            if (row.stat() == stat) {
                return row;
            }
        }
        return null;
    }

    /**
     * The unlock condition of the open tree, in words, when it is locked.
     *
     * @return the sentence, empty when the tree is open
     */
    public String treeLockedText() {
        return treeLockedText;
    }

    /**
     * How far the tree is scrolled.
     *
     * @return the offset in logical pixels
     */
    public double scroll() {
        return scroll;
    }

    /**
     * How far the tree can be scrolled.
     *
     * @return the largest offset, {@code 0} when the tree fits
     */
    public double maxScroll() {
        return Math.max(0, contentHeight - (treeBottom - treeTop));
    }

    /**
     * How many pips of a stat row are filled: the value normalised over the stat's clamp range,
     * five segments, rounded (M13 architecture decision).
     *
     * @param value the resolved value
     * @param stat the stat
     * @return the filled count in {@code [0, 5]}
     */
    public static int statPips(double value, StatId stat) {
        return ForgeArt.pips(value, stat);
    }

    /**
     * Selects a node and scrolls it into the viewport, the way a test — or the navigation's
     * Shop item — reaches a card that sits below the fold.
     *
     * @param nodeId the node id
     * @return {@code true} when the open tree shows that node
     */
    public boolean revealNode(String nodeId) {
        CardGrid.Card card = nodes.card(nodeId);
        if (card == null) {
            return false;
        }
        ring.focus(card);
        currentNodeId = card.id();
        scrollFocusIntoView();
        refreshState();
        return true;
    }

    /**
     * The coins the profile holds.
     *
     * @return the balance
     */
    private long coins() {
        return Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
    }

    /**
     * The index of the first tree the player can spend in, or {@code 0}.
     *
     * @return the tab index
     */
    private int firstUnlockedTree() {
        List<TreeDef> trees = content.trees().all();
        for (int i = 0; i < trees.size(); i++) {
            if (!UpgradeManager.isTreeLocked(profile, trees.get(i).id())) {
                return i;
            }
        }
        return 0;
    }

    /**
     * The subtitle of the open tree, from its own key.
     *
     * @return the sentence, empty for a tree without a subtitle key
     */
    private String subtitle() {
        String treeId = tabs.selectedId();
        if ("flight".equals(treeId)) {
            return strings.get(StringKey.UPGRADES_SUBTITLE_FLIGHT);
        }
        if ("economy".equals(treeId)) {
            return strings.get(StringKey.UPGRADES_SUBTITLE_ECONOMY);
        }
        if ("forge".equals(treeId)) {
            return strings.get(StringKey.UPGRADES_SUBTITLE_FORGE);
        }
        return "";
    }

    // ------------------------------------------------------------------ building

    /** Re-reads every label from the string table (a language switch, D25). */
    public void refreshTexts() {
        wallet.setFormat(strings.get(StringKey.HUD_COINS));
        for (TabBar.Tab tab : tabs.tabs()) {
            tab.setLabel(ProgressionText.name(strings, ContentKind.TREE, tab.id()));
        }
        nav.button(SectionNav.SHOP).setText(strings.get(StringKey.MENU_SHOP));
        nav.button(SectionNav.BIRDS).setText(strings.get(StringKey.MENU_BIRDS));
        nav.button(SectionNav.PLAY).setText(strings.get(StringKey.MENU_PLAY));
        nav.button(SectionNav.FORGE).setText(strings.get(StringKey.MENU_NAV_FORGE));
        nav.button(SectionNav.GOALS).setText(strings.get(StringKey.MENU_NAV_GOALS));
        shownLanguage = strings.language();
        rebuild();
    }

    /**
     * Rebuilds the node cards, the prerequisite links and the focus ring for the open tree.
     */
    public void rebuild() {
        String keep = currentNodeId;
        nodes.clear();
        bands.clear();
        links.clear();
        cardOffset.clear();
        ring.clear();
        ring.add(tabs);
        String treeId = tabs.selectedId();
        if (treeId != null) {
            // Group the tree's nodes by tier, keeping content order inside a tier.
            Map<Integer, List<UpgradeDef>> byTier = new LinkedHashMap<>();
            for (UpgradeDef node : content.upgrades()) {
                if (node.tree().equals(treeId)) {
                    byTier.computeIfAbsent(node.tier(), key -> new ArrayList<>()).add(node);
                }
            }
            double cellWidth = (CONTENT_W - CardGrid.DEFAULT_GAP * (COLUMNS - 1))
                    / (double) COLUMNS;
            double y = 0;
            for (Map.Entry<Integer, List<UpgradeDef>> entry : byTier.entrySet()) {
                bands.add(new TierBand(strings.format(StringKey.UPGRADES_TIER, entry.getKey()),
                        y));
                y += TIER_LABEL_H;
                List<UpgradeDef> tierNodes = entry.getValue();
                for (int i = 0; i < tierNodes.size(); i++) {
                    UpgradeDef def = tierNodes.get(i);
                    ForgeNodeCard card = new ForgeNodeCard(def.id(), "", () -> select(def.id()));
                    card.setGlyph(ForgeArt.nodeIcon(def));
                    card.setViewport(treeTop, treeBottom);
                    int col = i % COLUMNS;
                    int row = i / COLUMNS;
                    card.setBounds(MARGIN + col * (cellWidth + CardGrid.DEFAULT_GAP),
                            y + row * (NODE_H + ROW_GAP), cellWidth, NODE_H);
                    cardOffset.put(def.id(), card.y());
                    nodes.add(card);
                    ring.add(card);
                }
                int rows = (tierNodes.size() + COLUMNS - 1) / COLUMNS;
                y += rows * (NODE_H + ROW_GAP) + TIER_GAP;
            }
            contentHeight = Math.max(0, y - TIER_GAP);
            buildLinks(treeId);
        } else {
            contentHeight = 0;
        }
        applyScroll();
        ring.add(cta);
        nav.registerFocusables(ring);
        currentNodeId = keep != null && nodes.card(keep) != null ? keep
                : nodes.size() == 0 ? null : nodes.cards().get(0).id();
        refreshState();
    }

    /**
     * Builds one link per prerequisite edge of the open tree.
     *
     * @param treeId the open tree
     */
    private void buildLinks(String treeId) {
        for (UpgradeDef def : content.upgrades()) {
            if (!def.tree().equals(treeId)) {
                continue;
            }
            for (String prereq : def.prereqs()) {
                if (nodes.card(prereq) != null) {
                    links.add(new Link(prereq, def.id()));
                }
            }
        }
    }

    /**
     * Refreshes every card, the lock sentence, the detail panel, the call to action and the
     * wallet from the profile.
     */
    public void refreshState() {
        String treeId = tabs.selectedId();
        boolean treeLocked = treeId == null || UpgradeManager.isTreeLocked(profile, treeId);
        treeLockedText = "";
        if (treeId != null && treeLocked) {
            TreeDef tree = content.trees().get(treeId);
            long price = upgrades == null ? -1 : upgrades.treeUnlockPrice(treeId, content);
            treeLockedText = strings.format(StringKey.UPGRADES_TREE_LOCKED, price >= 0
                    ? ProgressionText.price(strings, price)
                    : ProgressionText.unlockText(strings, content, tree.unlock(), profile));
        }
        long balance = coins();
        for (CardGrid.Card gridCard : nodes.cards()) {
            ForgeNodeCard card = (ForgeNodeCard) gridCard;
            UpgradeDef def = content.upgrades().get(card.id());
            int level = profile.upgradeLevel(def.id());
            boolean maxed = level >= def.maxLevel();
            boolean redundant = !maxed && UpgradeManager.isRedundant(profile, def.id(), content);
            long next = maxed ? -1 : def.costOf(level + 1);
            List<String> missing = missingPrereqs(def);
            card.setTitle(ProgressionText.name(strings, ContentKind.UPGRADE, def.id()));
            // The card carries the short form; the detail panel below repeats it with the "per
            // level" the card has no room for.
            card.setSubtitle(strings.format(StringKey.UPGRADES_LEVEL, level, def.maxLevel())
                    + " • " + shortEffectText(def));
            card.setLocked(treeLocked || !missing.isEmpty());
            card.setDimmed(!treeLocked && missing.isEmpty() && !maxed && !redundant
                    && balance < next);
            card.setSelected(card.id().equals(currentNodeId));
            if (maxed) {
                card.setBadge(strings.get(StringKey.UPGRADES_MAXED), false);
            } else if (redundant) {
                // Its only grant is already owned, so buying it would be a pure loss; the
                // purchase path refuses it and the card has to say why.
                card.setBadge(strings.get(StringKey.UPGRADES_ALREADY_OWNED), false);
            } else if (treeLocked || !missing.isEmpty()) {
                // A locked card has no price the player can act on: the padlock speaks.
                card.setBadge("", false);
            } else {
                card.setBadge(Long.toString(next), true);
            }
            card.setTooltip(tooltipFor(def, level, maxed, redundant, next, missing, treeLocked,
                    balance));
        }
        wallet.setAmount(balance);
        buildDetail();
        refreshCta();
        buildStats();
    }

    /**
     * The prerequisites of a node the profile does not own yet.
     *
     * @param def the node
     * @return their translated names, empty when every prerequisite is owned
     */
    private List<String> missingPrereqs(UpgradeDef def) {
        List<String> missing = new ArrayList<>();
        for (String prereq : def.prereqs()) {
            if (profile.upgradeLevel(prereq) < 1) {
                missing.add(ProgressionText.name(strings, ContentKind.UPGRADE, prereq));
            }
        }
        return missing;
    }

    /**
     * What one level of a node does, in words: its stat effects, or what it grants when it has
     * none (E31.f).
     *
     * @param def the node
     * @return the phrase
     */
    private String effectText(UpgradeDef def) {
        String effects = ProgressionText.effects(strings, def.effectsPerLevel());
        return effects.isEmpty() ? grantsText(def)
                : strings.format(StringKey.UPGRADES_PER_LEVEL, effects);
    }

    /**
     * The same phrase without the {@code per level} suffix, for the node card.
     *
     * <p>The card clips its text at the badge column, so the suffix is what gets cut off in the
     * middle of a word; the detail panel carries the full form.
     *
     * @param def the node
     * @return the phrase
     */
    private String shortEffectText(UpgradeDef def) {
        String effects = ProgressionText.effects(strings, def.effectsPerLevel());
        return effects.isEmpty() ? grantsText(def) : effects;
    }

    /**
     * Every grant of a node in words.
     *
     * @param def the node
     * @return the phrase, empty when the node grants nothing
     */
    private String grantsText(UpgradeDef def) {
        StringBuilder out = new StringBuilder();
        for (GrantDef grant : def.grants()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(grantText(grant));
        }
        return out.toString();
    }

    /**
     * One grant in words.
     *
     * @param grant the grant
     * @return the phrase
     */
    private String grantText(GrantDef grant) {
        switch (grant.type()) {
            case ABILITY_CAP:
                return strings.get(StringKey.UPGRADES_GRANT_ABILITY_CAP);
            case PASSIVE_SLOT:
                return strings.get(StringKey.UPGRADES_GRANT_PASSIVE_SLOT);
            case UNLOCK:
            default:
                return strings.format(StringKey.UPGRADES_GRANT_UNLOCK,
                        ProgressionText.unlockableName(strings, content, grant.id()));
        }
    }

    /**
     * The tooltip of a node card: what it does, and what stands between the player and it.
     *
     * @param def the node
     * @param level the owned level
     * @param maxed whether every level is owned
     * @param redundant whether buying it would grant nothing new
     * @param next the price of the next level, or {@code -1}
     * @param missing the names of the missing prerequisites
     * @param treeLocked whether the tree is still locked
     * @param balance the coins the profile holds
     * @return the text
     */
    private String tooltipFor(UpgradeDef def, int level, boolean maxed, boolean redundant,
            long next, List<String> missing, boolean treeLocked, long balance) {
        StringBuilder out = new StringBuilder(
                ProgressionText.description(strings, ContentKind.UPGRADE, def.id()));
        out.append(" - ").append(effectText(def));
        if (maxed) {
            out.append(" - ").append(strings.get(StringKey.UPGRADES_MAXED));
        } else if (redundant) {
            out.append(" - ").append(strings.get(StringKey.UPGRADES_ALREADY_OWNED));
        } else if (treeLocked) {
            out.append(" - ").append(treeLockedText);
        } else if (!missing.isEmpty()) {
            out.append(" - ").append(strings.format(StringKey.UPGRADES_NEEDS,
                    String.join(", ", missing)));
        } else {
            out.append(" - ").append(ProgressionText.price(strings, next));
            if (balance < next) {
                out.append(" (").append(strings.get(StringKey.SHOP_CANNOT_AFFORD)).append(')');
            }
        }
        if (level > 0) {
            out.append(" - ").append(strings.format(StringKey.UPGRADES_LEVEL, level,
                    def.maxLevel()));
        }
        return out.toString();
    }

    /** Fills the detail panel from the selected node. */
    private void buildDetail() {
        detailLines.clear();
        String treeId = tabs.selectedId();
        if (treeId == null) {
            return;
        }
        if (currentNodeId == null) {
            detailLines.add(strings.get(StringKey.UPGRADES_SELECT_NODE));
            return;
        }
        UpgradeDef def = content.upgrades().get(currentNodeId);
        int level = profile.upgradeLevel(def.id());
        boolean maxed = level >= def.maxLevel();
        boolean redundant = !maxed && UpgradeManager.isRedundant(profile, def.id(), content);
        List<String> missing = missingPrereqs(def);
        boolean treeLocked = UpgradeManager.isTreeLocked(profile, treeId);
        detailLines.add(ProgressionText.name(strings, ContentKind.UPGRADE, def.id()));
        detailLines.add(strings.format(StringKey.UPGRADES_LEVEL, level, def.maxLevel()));
        detailLines.add(ProgressionText.description(strings, ContentKind.UPGRADE, def.id()));
        if (treeLocked) {
            detailLines.add(treeLockedText);
        } else if (maxed) {
            detailLines.add(strings.get(StringKey.UPGRADES_MAXED));
        } else if (redundant) {
            detailLines.add(strings.get(StringKey.UPGRADES_ALREADY_OWNED));
        } else if (!missing.isEmpty()) {
            detailLines.add(strings.format(StringKey.UPGRADES_NEEDS,
                    String.join(", ", missing)));
        } else {
            detailLines.add(effectText(def));
        }
        // The red note only appears when a purchase route exists and the wallet is short: a
        // blocked purchase has its blocker named in the status line instead.
        if (treeLocked) {
            long price = upgrades == null ? -1 : upgrades.treeUnlockPrice(treeId, content);
            if (price >= 0 && !upgrades.canAffordTreeUnlock(profile, treeId, content)) {
                detailLines.add(strings.get(StringKey.UPGRADES_NO_COINS));
            }
        } else if (upgrades != null && !maxed && !redundant && missing.isEmpty()
                && coins() < def.costOf(level + 1)) {
            detailLines.add(strings.get(StringKey.UPGRADES_NO_COINS));
        }
    }

    /** Points the call to action at the selected node, or at the locked tree. */
    private void refreshCta() {
        String treeId = tabs.selectedId();
        cta.setIcon(null);
        if (treeId == null) {
            cta.setText(strings.get(StringKey.UPGRADES_SELECT_NODE));
            cta.setEnabled(false);
            return;
        }
        if (UpgradeManager.isTreeLocked(profile, treeId)) {
            long price = upgrades == null ? -1 : upgrades.treeUnlockPrice(treeId, content);
            if (price >= 0) {
                cta.setText(strings.format(StringKey.UPGRADES_CTA_UNLOCK_TREE,
                        Long.toString(price)));
                cta.setIcon(coinIcon);
                // Enabled even when the wallet is short: the refusal and the red note are how
                // "not yet" is said, and the state machine changes nothing on a refusal.
                cta.setEnabled(upgrades != null);
            } else {
                TreeDef tree = content.trees().get(treeId);
                cta.setText(strings.format(StringKey.UPGRADES_CTA_UNLOCK_TREE,
                        ProgressionText.unlockText(strings, content, tree.unlock(), profile)));
                cta.setEnabled(false);
            }
            return;
        }
        if (currentNodeId == null) {
            cta.setText(strings.get(StringKey.UPGRADES_SELECT_NODE));
            cta.setEnabled(false);
            return;
        }
        UpgradeDef def = content.upgrades().get(currentNodeId);
        int level = profile.upgradeLevel(def.id());
        boolean maxed = level >= def.maxLevel();
        boolean redundant = !maxed && UpgradeManager.isRedundant(profile, def.id(), content);
        List<String> missing = missingPrereqs(def);
        if (maxed) {
            cta.setText(strings.get(StringKey.UPGRADES_CTA_MAXED));
            cta.setEnabled(false);
            return;
        }
        if (redundant) {
            cta.setText(strings.get(StringKey.UPGRADES_ALREADY_OWNED));
            cta.setEnabled(false);
            return;
        }
        if (!missing.isEmpty()) {
            cta.setText(strings.format(StringKey.UPGRADES_NEEDS, String.join(", ", missing)));
            cta.setEnabled(false);
            return;
        }
        long next = def.costOf(level + 1);
        cta.setText(strings.format(StringKey.UPGRADES_CTA_LEVEL, Long.toString(next)));
        cta.setIcon(coinIcon);
        cta.setEnabled(upgrades != null);
    }

    /**
     * Fills the attribute summary: every stat the open tree's nodes can touch, with the value
     * the next run would resolve for it right now and its pip fill.
     */
    private void buildStats() {
        statRows.clear();
        String treeId = tabs.selectedId();
        if (treeId == null) {
            return;
        }
        Set<StatId> touched = EnumSet.noneOf(StatId.class);
        for (UpgradeDef def : content.upgrades()) {
            if (!def.tree().equals(treeId)) {
                continue;
            }
            for (StatModifierDef effect : def.effectsPerLevel()) {
                touched.add(effect.stat());
            }
        }
        if (touched.isEmpty()) {
            return;
        }
        StatSheet sheet = RunLoadout.previewStats(profile, content);
        Set<StatId> ordered = new LinkedHashSet<>(touched);
        for (StatId stat : ordered) {
            String label = ProgressionText.statLabel(strings, stat);
            double value = sheet.resolve(stat);
            statRows.add(new StatRow(stat, label, ProgressionText.number(value),
                    ForgeArt.pips(value, stat)));
        }
    }

    // ------------------------------------------------------------------ scrolling

    /** Places the tree at the current scroll offset, clamped to what it needs. */
    private void applyScroll() {
        scroll = MathUtil.clamp(scroll, 0, maxScroll());
        for (CardGrid.Card card : nodes.cards()) {
            Double offset = cardOffset.get(card.id());
            if (offset != null) {
                card.setPosition(card.x(), treeTop - scroll + offset);
            }
        }
    }

    /**
     * Scrolls by a delta and re-places the cards.
     *
     * @param delta logical pixels, positive to move the content up
     */
    private void scrollBy(double delta) {
        scroll += delta;
        applyScroll();
    }

    /** Brings the focused card fully inside the band, if it is a card at all. */
    private void scrollFocusIntoView() {
        if (!(ring.focused() instanceof ForgeNodeCard card)) {
            return;
        }
        if (card.y() < treeTop) {
            scrollBy(card.y() - treeTop);
        } else if (card.y() + card.height() > treeBottom) {
            scrollBy(card.y() + card.height() - treeBottom);
        }
    }

    // ------------------------------------------------------------------ actions

    /**
     * Selects a node: the detail panel and the call to action turn to it. Buying never happens
     * here — that is the call to action's job, so one tap can never spend unread coins.
     *
     * @param nodeId the node id
     */
    private void select(String nodeId) {
        currentNodeId = nodeId;
        refreshState();
    }

    /** What the one call to action does: unlock the tree, or buy the selected node's level. */
    private void activateCta() {
        String treeId = tabs.selectedId();
        if (treeId != null && UpgradeManager.isTreeLocked(profile, treeId)) {
            unlockTree(treeId);
            return;
        }
        // The selection must be a card of the tree that is open right now: one left over from a
        // previously open tab, or a stale id, is not purchasable and is refused quietly.
        if (currentNodeId == null || nodes.card(currentNodeId) == null) {
            return;
        }
        UpgradeDef def = content.upgrades().get(currentNodeId);
        if (!def.tree().equals(treeId)) {
            return;
        }
        buy(currentNodeId);
    }

    /**
     * Buys the next level of the selected node and says what happened.
     *
     * @param nodeId the node id
     */
    private void buy(String nodeId) {
        currentNodeId = nodeId;
        if (upgrades == null) {
            refreshState();
            return;
        }
        PurchaseResult result = upgrades.buy(profile, nodeId, content);
        if (result.ok()) {
            toasts.push(strings.format(StringKey.TOAST_UPGRADED,
                    ProgressionText.name(strings, ContentKind.UPGRADE, nodeId), result.level()),
                    Toast.Kind.INFO);
            rebindScene();
        } else {
            toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED, refusal(result.status())),
                    Toast.Kind.WARNING);
        }
        refreshState();
    }

    /**
     * Buys the open tree's unlock through {@link UpgradeManager#buyTree} — the same atomic route
     * the shop sells the tree through — and says what happened.
     *
     * @param treeId the bare tree id
     */
    private void unlockTree(String treeId) {
        if (upgrades == null) {
            refreshState();
            return;
        }
        PurchaseResult result = upgrades.buyTree(profile, treeId, content);
        if (result.ok()) {
            toasts.push(strings.format(StringKey.TOAST_PURCHASED,
                    ProgressionText.unlockableName(strings, content,
                            UpgradeManager.treeUnlockId(treeId))), Toast.Kind.INFO);
            // The cards' locked and available rendering is produced by rebuild(), so a fresh
            // unlock re-lays the grid it just opened; rebuild ends in refreshState().
            rebuild();
        } else {
            toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED, refusal(result.status())),
                    Toast.Kind.WARNING);
            refreshState();
        }
    }

    /**
     * Why a purchase was refused, in words.
     *
     * @param status the refusal
     * @return the sentence
     */
    private String refusal(PurchaseStatus status) {
        switch (status) {
            case INSUFFICIENT_FUNDS:
                return strings.get(StringKey.SHOP_CANNOT_AFFORD);
            case TREE_LOCKED:
                return treeLockedText.isEmpty() ? strings.get(StringKey.COMMON_LOCKED)
                        : treeLockedText;
            case MISSING_PREREQ:
                return currentNodeId == null ? strings.get(StringKey.COMMON_LOCKED)
                        : strings.format(StringKey.UPGRADES_NEEDS, String.join(", ",
                                missingPrereqs(content.upgrades().get(currentNodeId))));
            case MAX_LEVEL:
                return strings.get(StringKey.UPGRADES_MAXED);
            case ALREADY_OWNED:
                return strings.get(StringKey.UPGRADES_ALREADY_OWNED);
            case LEVEL_CAPPED:
                return strings.get(StringKey.SHOP_ABILITY_CAPPED);
            case NOT_FOR_SALE:
            case UNKNOWN_ID:
            default:
                return strings.get(StringKey.COMMON_LOCKED);
        }
    }

    // ------------------------------------------------------------------ navigation

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

    /** Opens the goals, replacing this section. */
    private void openGoals() {
        if (context != null) {
            screens.replace(new GoalsScreen(context));
        }
    }

    /**
     * Starts the run the hub would start. The mode picker lives on the Birds screen, so the
     * forge plays the standard run in the profile's own world and tier.
     */
    private void play() {
        if (context == null) {
            return;
        }
        ContentRunFactory source = new ContentRunFactory(content, RunMode.STANDARD, () -> profile);
        screens.push(new GameScreen(context, source, SeedSequence.random()));
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(tabs);
        screens.setLetterboxRgb(PALETTE.letterbox());
        tooltip.hide();
        wallet.setAmountNow(coins());
        if (upgrades != null) {
            // A profile carried over from an older build can have earned a tree in play without
            // owning it (its condition is an any_of with a purchase branch, which the evaluator
            // never reports as satisfied). Granting it here — the one reconciliation this screen
            // performs, and one that can write the save — keeps the panel from advertising a
            // coin price for something the player already earned. It never runs from tick or
            // paint.
            upgrades.claimEarnedTrees(profile, content);
        }
        rebindScene();
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else {
            rebuild();
        }
    }

    @Override
    public void tick(InputFrame input) {
        relayoutIfResurfaced();
        ticks++;
        prevBob = bob;
        bob = bobAt(ticks);
        toasts.tick();
        wallet.tick();
        applyReduceFlashing(ParticleSystem.defaultReduceFlashing());
        cta.setTicks(ticks);
        InputFrame frame = input;
        if (tabs.tick(input)) {
            // The press that changed the tab must not also move the focus into the tree below.
            frame = input.withoutPresses(EnumSet.of(InputAction.LEFT, InputAction.RIGHT));
        }
        UiNode before = ring.focused();
        ring.handle(frame);
        UiNode focused = ring.focused();
        if (focused != before && focused instanceof CardGrid.Card card) {
            currentNodeId = card.id();
            scrollFocusIntoView();
            refreshState();
        }
        if (input.wheel() != 0 && input.mouseY() >= treeTop && input.mouseY() <= treeBottom) {
            scrollBy(-input.wheel() * (double) WHEEL_STEP);
        }
        UiNode under = ring.nodeAt(input.mouseX(), input.mouseY());
        UiNode target = under != null ? under : ring.focused();
        tooltip.update(target, target instanceof CardGrid.Card card ? card.tooltip() : "");
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
        if (input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
        }
    }

    /**
     * Fans a change of the accessibility setting out to the two things on this screen that
     * pulse.
     *
     * @param reduce whether luminance pulses are capped
     */
    private void applyReduceFlashing(boolean reduce) {
        if (reduce == reduceShown) {
            return;
        }
        reduceShown = reduce;
        cta.setReduceFlashing(reduce);
        scene.setReduceFlashing(reduce);
    }

    /**
     * Points the scene at the profile's current forge stage: every owned level feeds
     * {@link ForgeScene#stageOf}, so the illustration grows with the tree it advertises.
     */
    private void rebindScene() {
        scene.bind(PALETTE, content, profile,
                ForgeScene.stageOf(profile.upgradeLevelsTotal()), profile.prestigeCount > 0);
    }

    private static double bobAt(long tick) {
        long t = tick % BOB_PERIOD_TICKS;
        double half = BOB_PERIOD_TICKS / 2.0;
        double wave = t < half ? t / half : (BOB_PERIOD_TICKS - t) / half;
        return -BOB_AMPLITUDE / 2 + BOB_AMPLITUDE * wave;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);

        renderHeader(g, alpha);
        tabs.render(g);

        Shape unclipped = g.getClip();
        g.clipRect(0, treeTop, Playfield.WIDTH, treeBottom - treeTop);
        for (TierBand band : bands) {
            ForgeArt.drawTierPill(g, MARGIN, treeTop - scroll + band.y(), band.label());
        }
        renderLinks(g);
        nodes.render(g);
        g.setClip(unclipped);
        renderScrollbar(g);

        renderDetail(g);

        nav.render(g);
        tooltip.render(g);
        // Below the tab band, not inside it: a purchase toast right-aligned to the top-right
        // corner would otherwise park exactly on the Forge tab for its whole life. The top card
        // row is the lesser harm — a tab is a control, a card is content.
        toasts.render(g, tabsTop + TABS_H);
    }

    /**
     * Draws the compact header: the small forge scene, the title, the open tree's subtitle and
     * the wallet in its coin pill, which is drawn over the scene so the two never fight.
     *
     * @param g the context
     * @param alpha the frame's blend factor
     */
    private void renderHeader(Graphics2D g, double alpha) {
        // The scene is drawn small, so it wants a nested transform — and it wants it on a
        // copy: undoing a scale with its inverse leaves a rounding residue in the matrix, and
        // a context that is no longer exactly axis-aligned costs every later draw of the frame
        // a transformed path. The copy also takes the header's clip with it, so the caller's
        // clip needs no saving either.
        Graphics2D sceneContext = (Graphics2D) g.create();
        try {
            sceneContext.clipRect(0, surfaceTop, Playfield.WIDTH, HEADER_H);
            sceneContext.translate(SCENE_TX, surfaceTop + SCENE_TY);
            sceneContext.scale(SCENE_SCALE, SCENE_SCALE);
            scene.render(sceneContext, alpha, MathUtil.lerp(prevBob, bob, alpha), ticks);
        } finally {
            sceneContext.dispose();
        }
        g.setFont(Fonts.bold(26));
        TextPainter.drawOutlined(g, strings.get(StringKey.UPGRADES_SCREEN_TITLE), MARGIN,
                surfaceTop + TITLE_BASELINE, Align.LEFT, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(PALETTE), 2);
        g.setFont(Fonts.regular(11));
        g.setColor(ForgeArt.SUBTITLE_INK);
        TextPainter.draw(g, TextPainter.ellipsise(g, subtitle(), SUBTITLE_LIMIT_X - MARGIN),
                MARGIN, surfaceTop + SUBTITLE_BASELINE);
        ForgeArt.drawCoinPill(g, WALLET_PILL_X, surfaceTop + WALLET_PILL_Y, WALLET_PILL_W,
                WALLET_PILL_H);
        wallet.render(g);
    }

    /**
     * Draws the prerequisite edges from the cards' live bounds, so they follow the scroll.
     *
     * @param g the context, clipped to the tree viewport
     */
    private void renderLinks(Graphics2D g) {
        for (Link link : links) {
            CardGrid.Card from = nodes.card(link.from());
            CardGrid.Card to = nodes.card(link.to());
            if (from == null || to == null) {
                continue;
            }
            ForgeArt.drawConnector(g, from.centerX(), from.y() + from.height(),
                    to.centerX(), to.y());
        }
    }

    /**
     * Draws the thumb that says the tree has more below, and only then.
     *
     * @param g the context
     */
    private void renderScrollbar(Graphics2D g) {
        double max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackH = treeBottom - treeTop;
        int thumbH = (int) Math.max(SCROLLBAR_MIN_H, trackH * (trackH / contentHeight));
        int thumbY = (int) Math.round(treeTop + (trackH - thumbH) * (scroll / max));
        g.setColor(SCROLLBAR);
        g.fillRoundRect(SCROLLBAR_X, thumbY, SCROLLBAR_W, thumbH, SCROLLBAR_W, SCROLLBAR_W);
    }

    /**
     * Draws the detail panel: hero, name, level, description, status lines, call to action, the
     * red note, then the divider and the attribute summary.
     *
     * @param g the context
     */
    private void renderDetail(Graphics2D g) {
        ForgeArt.drawDetailPanel(g, MARGIN - 4, detailTop, CONTENT_W + 8, detailH);
        IconPainter hero = null;
        if (currentNodeId != null && detailLines.size() > 1) {
            hero = ForgeArt.nodeIcon(content.upgrades().get(currentNodeId));
        }
        ForgeArt.drawHero(g, HERO_CX, heroCy, HERO_SIZE, hero);

        int fullRoom = Playfield.WIDTH - MARGIN - 8;
        if (detailLines.size() == 1) {
            // A tree without nodes: the placeholder is the whole panel's message.
            g.setFont(Fonts.regular(13));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.drawCentered(g, detailLines.get(0), Playfield.WIDTH / 2.0,
                    detailTop + detailH / 2.0 + 4);
        } else if (!detailLines.isEmpty()) {
            g.setFont(Fonts.bold(18));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, TextPainter.ellipsise(g, detailLines.get(0),
                    fullRoom - PANEL_TEXT_X), PANEL_TEXT_X, detailTop + 24);
            g.setFont(Fonts.regular(12));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, TextPainter.ellipsise(g, detailLines.get(1),
                    fullRoom - PANEL_TEXT_X), PANEL_TEXT_X, detailTop + 42);
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, TextPainter.ellipsise(g, detailLines.get(2),
                    fullRoom - PANEL_TEXT_X), PANEL_TEXT_X, detailTop + 60);
            // The status lines sit under the hero, on the panel's text column like the lines
            // above — the red not-enough-coins note among them — clear of the call to action's
            // plate below.
            double baseline = detailTop + 86;
            String noCoins = strings.get(StringKey.UPGRADES_NO_COINS);
            for (int i = 3; i < detailLines.size(); i++) {
                String line = detailLines.get(i);
                boolean red = line.equals(noCoins);
                g.setFont(Fonts.regular(11));
                g.setColor(red ? ForgeArt.NO_COINS_RED : ProceduralArt.TEXT_LIGHT);
                TextPainter.draw(g, TextPainter.ellipsise(g, line, fullRoom - PANEL_TEXT_X),
                        PANEL_TEXT_X, baseline);
                baseline += STAT_PITCH;
            }
        }

        cta.render(g);

        g.setColor(ForgeArt.DIVIDER);
        g.fillRect(MARGIN + 10, panelAnchoredToBottom(DIVIDER_OFFSET), CONTENT_W - 20, 2);
        g.setFont(Fonts.bold(12));
        g.setColor(ProceduralArt.accentColor(PALETTE));
        TextPainter.draw(g, strings.get(StringKey.UPGRADES_ATTRIBUTE_SUMMARY), MARGIN + 4,
                panelAnchoredToBottom(SUMMARY_OFFSET));
        double rowY = panelAnchoredToBottom(STATS_OFFSET);
        double pipX = MARGIN + 170;
        for (StatRow row : statRows) {
            ForgeArt.statIcon(row.stat()).paint(g, MARGIN + 16, rowY - 4, 12,
                    ProceduralArt.TEXT_LIGHT);
            g.setFont(Fonts.regular(10));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, row.label(), MARGIN + 30, rowY);
            ForgeArt.drawPipBar(g, pipX, rowY - 3.5, 130, row.pips());
            g.setFont(Fonts.bold(11));
            TextPainter.draw(g, row.value(), MARGIN + CONTENT_W - 8, rowY, Align.RIGHT);
            rowY += STAT_PITCH;
        }
    }

    /** One tier heading of the open tree, at its offset inside the scrolled content. */
    private record TierBand(String label, double y) {
    }

    /** One prerequisite edge, drawn from the bottom of a node to the top of the node it opens. */
    private record Link(String from, String to) {
    }

    /**
     * One line of the attribute summary.
     *
     * @param stat the stat
     * @param label its translated name
     * @param value its resolved value right now
     * @param pips how many pip segments its fill shows
     */
    public record StatRow(StatId stat, String label, String value, int pips) {
    }
}
