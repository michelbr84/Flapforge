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
import io.github.michelbr84.flapforge.core.Playfield;
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
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.TabBar;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Tooltip;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The three upgrade trees (D13, E21, E31.f, M4): the screen where coins become physics.
 *
 * <p>One tab per tree, one card per node, laid out by the node's {@code tier} with a line drawn
 * from every prerequisite to the node it opens, so the shape of the tree is visible rather than
 * implied. A card states everything the decision needs: the owned level against the maximum, what
 * one level does in words ({@code -3% Gravity}), the price of the next level, and which of the
 * five states it is in — the tree is locked, a prerequisite is missing, it is affordable, it is
 * maxed, or its only grant is something the profile already owns and buying it would change
 * nothing. A node whose effects no system reads before M5 says so on the card and on its stat row
 * (E19) instead of advertising a number that moves nothing.
 *
 * <p>Buying goes through {@link UpgradeManager#buy}, which is atomic (check, debit, raise, grant,
 * propagate, save) and returns why it refused. Three things then change in the same tick: the
 * wallet readout, the card, and the live stat panel at the bottom, which reads
 * {@link RunLoadout#previewStats} — the sheet of the run that would start right now. That is the
 * whole point of the screen: the player sees the number the node moved.
 *
 * <p>A locked tree shows its unlock condition in words instead of its nodes' prices, and its tab
 * is skipped by the arrows (but still clickable, because reading why it is locked is the reason to
 * go there).
 */
public final class UpgradeTreeScreen implements Screen {

    /** Side margin. */
    public static final int MARGIN = 12;
    /** Width of the content column. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * MARGIN;
    /** Top of the tab bar. */
    public static final int TABS_TOP = 46;
    /** Height of the tab bar. */
    public static final int TABS_H = 28;
    /** Top of the node area. */
    public static final int NODES_TOP = 84;
    /** Height of one node card. */
    public static final int NODE_H = 46;
    /** Columns of nodes per tier. */
    public static final int COLUMNS = 2;
    /** Height of a tier label. */
    public static final int TIER_LABEL_H = 16;
    /** Top of the detail panel. */
    public static final int DETAIL_TOP = 384;
    /** Height of the detail panel. */
    public static final int DETAIL_H = 78;
    /** Top of the live stat panel. */
    public static final int STATS_TOP = 472;
    /** Height of the live stat panel. */
    public static final int STATS_H = 96;
    /** Top of the Back button. */
    public static final int FOOTER_TOP = Playfield.HEIGHT - 52;
    /** Height of the Back button. */
    public static final int FOOTER_H = 40;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;

    /** The milestone the ability system — and everything the trees sell for it — arrives in. */
    private static final String ABILITY_MILESTONE = "M5";

    /**
     * The stats no system reads before M5 (D9, E19): the ability multipliers and the two
     * defensive counters. A node whose whole effect list is in here advertises a number the run
     * resolves and nothing consumes, so the card and the stat row say when it starts working
     * rather than pretending it already does.
     */
    private static final Set<StatId> M5_STATS = Collections.unmodifiableSet(EnumSet.of(
            StatId.ABILITY_COOLDOWN_MULT, StatId.ABILITY_DURATION_MULT, StatId.SHIELD_CHARGES,
            StatId.REVIVES));

    private static final int TITLE_BASELINE = 34;
    private static final int WALLET_W = 130;
    private static final Color LINK = new Color(0xF5, 0xC5, 0x42, 0x88);
    private static final Stroke LINK_STROKE = new BasicStroke(2f);

    private final ScreenManager screens;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final UpgradeManager upgrades;
    private final ToastLayer toasts;
    private final FocusRing ring = new FocusRing();
    private final TabBar tabs = new TabBar();
    private final CardGrid nodes = new CardGrid();
    private final CurrencyDisplay wallet = new CurrencyDisplay();
    private final Tooltip tooltip = new Tooltip();
    private final Button back;
    private final List<TierBand> bands = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private final List<String> detailLines = new ArrayList<>();
    private final List<StatRow> statRows = new ArrayList<>();
    private String currentNodeId;
    private String treeLockedText = "";
    private String shownLanguage;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services
     */
    public UpgradeTreeScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context").screens(),
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
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.upgrades = upgrades;
        this.toasts = toasts == null ? new ToastLayer() : toasts;

        tabs.setBounds(MARGIN, TABS_TOP, CONTENT_W, TABS_H);
        for (TreeDef tree : content.trees()) {
            tabs.add(tree.id(), ProgressionText.name(strings, ContentKind.TREE, tree.id()));
        }
        tabs.setOnChange(index -> openTree(index));
        back = new Button("", screens::pop);
        back.setFontSize(16);
        back.setBounds(MARGIN, FOOTER_TOP, CONTENT_W, FOOTER_H);
        wallet.setBounds(Playfield.WIDTH - WALLET_W - 14.0, 14, WALLET_W, 26);
        wallet.setAlign(Align.RIGHT);
        wallet.setAmountNow(coins());
        shownLanguage = strings.language();
        // The first tab a player should land on is one they can actually spend in.
        tabs.selectQuietly(firstUnlockedTree());
        refreshTexts();
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
     * The node the detail panel is about.
     *
     * @return the node id, or {@code null} when the tree has none
     */
    public String currentNodeId() {
        return currentNodeId;
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
     * The wallet readout.
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
     * The lines of the detail panel, in display order.
     *
     * @return an unmodifiable snapshot
     */
    public List<String> detailLines() {
        return List.copyOf(detailLines);
    }

    /**
     * The live stat rows: every stat the open tree can touch, with its resolved value right now.
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
     * @return the sentence, empty when the tree is unlocked
     */
    public String treeLockedText() {
        return treeLockedText;
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
            if (profile.isUnlocked(trees.get(i).unlockableId())) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ building

    /** Re-reads every label from the string table (a language switch, D25). */
    public void refreshTexts() {
        wallet.setFormat(strings.get(StringKey.HUD_COINS));
        back.setText(strings.get(StringKey.COMMON_BACK));
        for (TabBar.Tab tab : tabs.tabs()) {
            tab.setLabel(ProgressionText.name(strings, ContentKind.TREE, tab.id()));
        }
        shownLanguage = strings.language();
        rebuild();
    }

    /**
     * Switches to another tree.
     *
     * @param index the tab index
     */
    private void openTree(int index) {
        if (index >= 0) {
            rebuild();
        }
    }

    /**
     * Rebuilds the node cards, the prerequisite links and the focus ring for the open tree.
     */
    public void rebuild() {
        String treeId = tabs.selectedId();
        nodes.clear();
        bands.clear();
        links.clear();
        ring.clear();
        ring.add(tabs);
        if (treeId == null) {
            ring.add(back);
            refreshState();
            return;
        }
        // Group the tree's nodes by tier, keeping content order inside a tier.
        Map<Integer, List<UpgradeDef>> byTier = new LinkedHashMap<>();
        for (UpgradeDef node : content.upgrades()) {
            if (node.tree().equals(treeId)) {
                byTier.computeIfAbsent(node.tier(), key -> new ArrayList<>()).add(node);
            }
        }
        double y = NODES_TOP;
        double cellWidth = (CONTENT_W - CardGrid.DEFAULT_GAP * (COLUMNS - 1)) / (double) COLUMNS;
        for (Map.Entry<Integer, List<UpgradeDef>> entry : byTier.entrySet()) {
            bands.add(new TierBand(entry.getKey(),
                    strings.format(StringKey.UPGRADES_TIER, entry.getKey()), y));
            y += TIER_LABEL_H;
            List<UpgradeDef> tierNodes = entry.getValue();
            for (int i = 0; i < tierNodes.size(); i++) {
                UpgradeDef def = tierNodes.get(i);
                CardGrid.Card card = new CardGrid.Card(def.id(), "", null);
                card.setOnAction(() -> buy(def.id()));
                int col = i % COLUMNS;
                int row = i / COLUMNS;
                card.setBounds(MARGIN + col * (cellWidth + CardGrid.DEFAULT_GAP),
                        y + row * (NODE_H + 6.0), cellWidth, NODE_H);
                nodes.add(card);
                ring.add(card);
            }
            int rows = (tierNodes.size() + COLUMNS - 1) / COLUMNS;
            y += rows * (NODE_H + 6.0) + 4;
        }
        ring.add(back);
        buildLinks(treeId);
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
            CardGrid.Card target = nodes.card(def.id());
            if (target == null) {
                continue;
            }
            for (String prereq : def.prereqs()) {
                CardGrid.Card source = nodes.card(prereq);
                if (source != null) {
                    links.add(new Link(source, target));
                }
            }
        }
    }

    /**
     * Rebuilds everything that depends on the profile: every card's level, price and state, the
     * detail panel and the live stat panel.
     */
    public void refreshState() {
        String treeId = tabs.selectedId();
        boolean treeUnlocked = treeId != null
                && profile.isUnlocked(TreeDef.NAMESPACE + treeId);
        treeLockedText = "";
        if (treeId != null && !treeUnlocked) {
            TreeDef tree = content.trees().get(treeId);
            treeLockedText = strings.format(StringKey.UPGRADES_TREE_LOCKED,
                    ProgressionText.unlockText(strings, content, tree.unlock(), profile));
        }
        for (int i = 0; i < tabs.size(); i++) {
            TabBar.Tab tab = tabs.tabs().get(i);
            tab.setEnabled(profile.isUnlocked(TreeDef.NAMESPACE + tab.id()));
        }
        long balance = coins();
        for (CardGrid.Card card : nodes.cards()) {
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
                    + "  " + shortEffectText(def) + soonSuffix(def));
            card.setLocked(!treeUnlocked || !missing.isEmpty());
            card.setDimmed(treeUnlocked && missing.isEmpty() && !maxed && !redundant
                    && balance < next);
            card.setSelected(level > 0);
            if (maxed) {
                card.setBadge(strings.get(StringKey.UPGRADES_MAXED), false);
            } else if (redundant) {
                // Its only grant is already owned, so buying it would be a pure loss; the
                // purchase path refuses it and the card has to say why.
                card.setBadge(strings.get(StringKey.UPGRADES_ALREADY_OWNED), false);
            } else if (!treeUnlocked) {
                card.setBadge("", false);
            } else if (!missing.isEmpty()) {
                card.setBadge("", false);
            } else {
                card.setBadge(Long.toString(next), true);
            }
            card.setTooltip(tooltipFor(def, level, maxed, redundant, next, missing, treeUnlocked,
                    balance));
        }
        if (currentNodeId == null || nodes.card(currentNodeId) == null) {
            currentNodeId = nodes.size() == 0 ? null : nodes.cards().get(0).id();
        }
        wallet.setAmount(balance);
        buildDetail();
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
     * middle of a word ({@code -3% Gravity per leve}); the detail panel carries the full form.
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
     * The milestone a node's effects start being read in (E19), or {@code null} when the run
     * already reads them.
     *
     * <p>Seven of the eighteen nodes sell ability cooldowns, ability durations, shield charges,
     * revives or an ability/slot grant. The stat pipeline resolves all of them today and no
     * system consumes any of them until M5, so the screen has to say so instead of showing a
     * number that changes nothing.
     *
     * @param def the node
     * @return {@code "M5"}, or {@code null}
     */
    private static String milestoneOf(UpgradeDef def) {
        if (!def.effectsPerLevel().isEmpty()) {
            for (StatModifierDef effect : def.effectsPerLevel()) {
                if (!M5_STATS.contains(effect.stat())) {
                    return null;
                }
            }
            return ABILITY_MILESTONE;
        }
        for (GrantDef grant : def.grants()) {
            if (grant.type() == GrantType.ABILITY_CAP || grant.type() == GrantType.PASSIVE_SLOT) {
                return ABILITY_MILESTONE;
            }
        }
        return null;
    }

    /**
     * The milestone note appended to a card, or the empty string.
     *
     * @param def the node
     * @return the suffix
     */
    private String soonSuffix(UpgradeDef def) {
        String milestone = milestoneOf(def);
        return milestone == null ? ""
                : " - " + strings.format(StringKey.COMMON_SOON, milestone);
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
     * @param treeUnlocked whether the tree is open
     * @param balance the coins the profile holds
     * @return the text
     */
    private String tooltipFor(UpgradeDef def, int level, boolean maxed, boolean redundant,
            long next, List<String> missing, boolean treeUnlocked, long balance) {
        StringBuilder out = new StringBuilder(
                ProgressionText.description(strings, ContentKind.UPGRADE, def.id()));
        out.append(" - ").append(effectText(def)).append(soonSuffix(def));
        if (maxed) {
            out.append(" - ").append(strings.get(StringKey.UPGRADES_MAXED));
        } else if (redundant) {
            out.append(" - ").append(strings.get(StringKey.UPGRADES_ALREADY_OWNED));
        } else if (!treeUnlocked) {
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

    /** Fills the detail panel from the node the focus is on. */
    private void buildDetail() {
        detailLines.clear();
        if (currentNodeId == null) {
            if (!treeLockedText.isEmpty()) {
                detailLines.add(treeLockedText);
            }
            return;
        }
        UpgradeDef def = content.upgrades().get(currentNodeId);
        int level = profile.upgradeLevel(def.id());
        boolean maxed = level >= def.maxLevel();
        detailLines.add(ProgressionText.name(strings, ContentKind.UPGRADE, def.id())
                + "  " + strings.format(StringKey.UPGRADES_LEVEL, level, def.maxLevel()));
        detailLines.add(ProgressionText.description(strings, ContentKind.UPGRADE, def.id()));
        detailLines.add(effectText(def) + soonSuffix(def));
        boolean redundant = !maxed && UpgradeManager.isRedundant(profile, def.id(), content);
        if (maxed) {
            detailLines.add(strings.get(StringKey.UPGRADES_MAXED));
        } else if (redundant) {
            detailLines.add(strings.get(StringKey.UPGRADES_ALREADY_OWNED));
        } else if (!treeLockedText.isEmpty()) {
            detailLines.add(treeLockedText);
        } else {
            List<String> missing = missingPrereqs(def);
            if (!missing.isEmpty()) {
                detailLines.add(strings.format(StringKey.UPGRADES_NEEDS,
                        String.join(", ", missing)));
            } else {
                long next = def.costOf(level + 1);
                detailLines.add(ProgressionText.price(strings, next)
                        + (coins() < next ? "  (" + strings.get(StringKey.SHOP_CANNOT_AFFORD)
                        + ")" : ""));
            }
        }
    }

    /**
     * Fills the live stat panel: every stat the open tree's nodes can touch, with the value the
     * next run would resolve for it right now.
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
            if (M5_STATS.contains(stat)) {
                // The value is real; nothing reads it yet (E19).
                label += " - " + strings.format(StringKey.COMMON_SOON, ABILITY_MILESTONE);
            }
            statRows.add(new StatRow(stat, label, ProgressionText.number(sheet.resolve(stat))));
        }
    }

    // ------------------------------------------------------------------ actions

    /**
     * Buys the next level of a node and says what happened.
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
        } else {
            toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED, refusal(result.status())),
                    Toast.Kind.WARNING);
        }
        refreshState();
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
                return strings.format(StringKey.UPGRADES_NEEDS,
                        String.join(", ", missingPrereqs(content.upgrades().get(currentNodeId))));
            case MAX_LEVEL:
                return strings.get(StringKey.UPGRADES_MAXED);
            case ALREADY_OWNED:
                return strings.get(StringKey.UPGRADES_ALREADY_OWNED);
            default:
                return strings.get(StringKey.COMMON_LOCKED);
        }
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(tabs);
        screens.setLetterboxRgb(PALETTE.letterbox());
        tooltip.hide();
        wallet.setAmountNow(coins());
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else {
            refreshState();
        }
    }

    @Override
    public void tick(InputFrame input) {
        toasts.tick();
        wallet.tick();
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
            buildDetail();
        }
        updateTooltip(input);
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
        if (input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
        }
    }

    /**
     * Points the tooltip at the node under the pointer, or at the focused one.
     *
     * @param input the tick input
     */
    private void updateTooltip(InputFrame input) {
        UiNode under = ring.nodeAt(input.mouseX(), input.mouseY());
        UiNode target = under != null ? under : ring.focused();
        tooltip.update(target, target instanceof CardGrid.Card card ? card.tooltip() : "");
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        g.setFont(Fonts.bold(26));
        TextPainter.drawOutlined(g, strings.get(StringKey.UPGRADES_TITLE), MARGIN,
                TITLE_BASELINE, Align.LEFT, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(PALETTE), 2);
        wallet.render(g);
        tabs.render(g);

        g.setFont(Fonts.bold(12));
        g.setColor(ProceduralArt.accentColor(PALETTE));
        for (TierBand band : bands) {
            TextPainter.draw(g, band.label(), MARGIN, band.y() + TIER_LABEL_H - 4);
        }
        Stroke old = g.getStroke();
        g.setStroke(LINK_STROKE);
        g.setColor(LINK);
        for (Link link : links) {
            link.render(g);
        }
        g.setStroke(old);
        nodes.render(g);

        ProceduralArt.panel(g, MARGIN - 4, DETAIL_TOP, CONTENT_W + 8, DETAIL_H);
        double baseline = DETAIL_TOP + 16.0;
        for (int i = 0; i < detailLines.size(); i++) {
            g.setFont(i == 0 ? Fonts.bold(13) : Fonts.regular(11));
            g.setColor(i == 0 ? ProceduralArt.TEXT_LIGHT : ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, detailLines.get(i), MARGIN, baseline);
            baseline += i == 0 ? 16 : 14;
        }

        ProceduralArt.panel(g, MARGIN - 4, STATS_TOP, CONTENT_W + 8, STATS_H);
        double rowY = STATS_TOP + 16.0;
        g.setFont(Fonts.bold(12));
        g.setColor(ProceduralArt.accentColor(PALETTE));
        TextPainter.draw(g, strings.get(StringKey.BIRDS_BREAKDOWN), MARGIN, rowY);
        rowY += 16;
        for (StatRow row : statRows) {
            g.setFont(Fonts.regular(11));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, row.label(), MARGIN, rowY);
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, row.value(), MARGIN + (double) CONTENT_W - 4, rowY, Align.RIGHT);
            rowY += 14;
        }

        back.render(g);
        tooltip.render(g);
        toasts.render(g);
    }

    /** One tier heading of the open tree. */
    private record TierBand(int tier, String label, double y) {
    }

    /** One prerequisite edge, drawn from the bottom of a node to the top of the node it opens. */
    private record Link(CardGrid.Card from, CardGrid.Card to) {

        /**
         * Draws the edge.
         *
         * @param g the context
         */
        void render(Graphics2D g) {
            int x1 = (int) Math.round(from.centerX());
            int y1 = (int) Math.round(from.y() + from.height());
            int x2 = (int) Math.round(to.centerX());
            int y2 = (int) Math.round(to.y());
            int mid = (y1 + y2) / 2;
            g.drawLine(x1, y1, x1, mid);
            g.drawLine(x1, mid, x2, mid);
            g.drawLine(x2, mid, x2, y2);
        }
    }

    /**
     * One line of the live stat panel.
     *
     * @param stat the stat
     * @param label its translated name
     * @param value its resolved value right now
     */
    public record StatRow(StatId stat, String label, String value) {
    }
}
