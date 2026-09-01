package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.StatBreakdown;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.PurchaseResult;
import io.github.michelbr84.flapforge.progression.PurchaseStatus;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import io.github.michelbr84.flapforge.progression.SelectionManager;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UnlockManager;
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
import io.github.michelbr84.flapforge.ui.component.ListView;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Tooltip;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The bird selection (D17, M4): the roster, the colours, the tier of the next run and — the point
 * of the screen — the stat breakdown of the build the player is about to fly.
 *
 * <p>The roster is a {@link CardGrid} of all seven birds. An owned bird shows its name, its
 * archetype and its portrait in the palette the profile has selected for it; a locked one is
 * dimmed and says, in words, the <em>cheapest</em> way to open it ({@code Play 3 runs},
 * {@code 150 coins}), which is {@link ProgressionText#cheapestBranch} measured against this very
 * profile. Activating an owned card writes {@code profile.selected} through
 * {@link SelectionManager} and saves; Buy pays for a locked one through {@link UnlockManager},
 * which is the only path that grants a {@code purchase} branch (D13).
 *
 * <p>The breakdown panel below is not a second implementation of the stat pipeline: it reads
 * {@link RunLoadout#previewStats} — the sheet of the run that would start right now — and lists,
 * per stat, the resolved value and every contributing modifier with the thing it came from (the
 * bird, an upgrade node, the bird's synergy with the nodes owned, the world, the tier, the
 * difficulty curve). Buying {@code feather_1} in the upgrade screen therefore shows up here as a
 * new line under Gravity and a lower number next to it (D8, D17).
 *
 * <p>Abilities are the one thing this screen only <em>promises</em>: {@code GameContent.playable}
 * reports that no ability system exists before M5 (E19), so the slot area shows how many passive
 * slots the bird has and says the slots arrive in M5 instead of offering a loadout that cannot be
 * flown.
 */
public final class BirdSelectionScreen implements Screen {

    /** Top of the card grid. */
    public static final int GRID_TOP = 48;
    /** Height of one bird card. */
    public static final int CARD_HEIGHT = 52;
    /** Columns of the roster. */
    public static final int COLUMNS = 2;
    /** Side margin of everything on the screen. */
    public static final int MARGIN = 12;
    /** Width of the content column. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * MARGIN;
    /** Side of a palette swatch. */
    public static final int SWATCH = 26;
    /** How many swatches the row can hold (the widest bird ships four palettes). */
    public static final int MAX_SWATCHES = 6;
    /** Height of one breakdown row. */
    public static final int ROW_H = 15;
    /** Height of a breakdown stat header. */
    public static final int HEADER_H = 17;
    /** Logical pixels one wheel notch scrolls the breakdown. */
    public static final int WHEEL_STEP = 30;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 34;
    private static final int WALLET_W = 130;
    private static final int PALETTE_LABEL_BASELINE = 288;
    private static final int SWATCH_TOP = 292;
    private static final int ACTION_TOP = 326;
    private static final int ACTION_H = 30;
    private static final int TIER_TOP = 362;
    private static final int TIER_H = 24;
    private static final int ABILITY_BASELINE = 405;
    private static final int BREAKDOWN_LABEL_BASELINE = 425;
    private static final int VIEW_TOP = 432;
    private static final int VIEW_BOTTOM = Playfield.HEIGHT - 58;
    private static final int FOOTER_TOP = Playfield.HEIGHT - 52;
    private static final int FOOTER_H = 40;
    private static final Color SCROLLBAR = new Color(0xF4, 0xF8, 0xF8, 0x50);
    private static final Stroke SWATCH_STROKE = new BasicStroke(2f);
    /** The milestone the ability slots arrive in (E19). */
    private static final String ABILITY_MILESTONE = "M5";

    private final ScreenManager screens;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final SelectionManager selection;
    private final UnlockManager unlocks;
    private final UnlockEvaluator evaluator;
    private final ToastLayer toasts;
    private final FocusRing ring = new FocusRing();
    private final CardGrid roster = new CardGrid();
    private final List<Swatch> swatches = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final CurrencyDisplay wallet = new CurrencyDisplay();
    private final Tooltip tooltip = new Tooltip();
    private final Button select;
    private final Button buy;
    private final ListView tier;
    private final Button back;
    private final List<String> tierIds = new ArrayList<>();
    private String currentBirdId;
    private String shownLanguage;
    private String abilityLine = "";
    private double contentHeight;
    private double scroll;
    private long ticks;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services; its profile is the one edited
     */
    public BirdSelectionScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context").screens(),
                context.strings() != null ? context.strings() : Strings.active(),
                context.content(), context.profile(),
                context.canProgress()
                        ? new SelectionManager(context.progression(), context::saveProfile) : null,
                context.canProgress()
                        ? new UnlockManager(context.progression(), context::saveProfile) : null,
                context.toasts());
    }

    /**
     * Creates the screen.
     *
     * @param screens the screen stack
     * @param strings the string table
     * @param content the loaded content
     * @param profile the profile to read and write
     * @param selection the selection writer, or {@code null} for a read-only screen
     * @param unlocks the purchase path, or {@code null} for a screen that cannot buy
     * @param toasts the toast queue, or {@code null} for one of its own
     */
    public BirdSelectionScreen(ScreenManager screens, Strings strings, GameContent content,
            PlayerProfile profile, SelectionManager selection, UnlockManager unlocks,
            ToastLayer toasts) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.selection = selection;
        this.unlocks = unlocks;
        this.evaluator = UnlockEvaluator.of(content);
        this.toasts = toasts == null ? new ToastLayer() : toasts;
        this.currentBirdId = profile.selected.birdId;

        for (BirdDef bird : content.birds()) {
            CardGrid.Card card = new CardGrid.Card(bird.id(), "", null);
            card.setOnAction(() -> activate(bird.id()));
            card.setArt((g, c, cx, cy, size) -> paintPortrait(g, bird, cx, cy, size));
            roster.add(card);
        }
        roster.setColumns(COLUMNS);
        roster.setCellHeight(CARD_HEIGHT);
        roster.setGap(8, 6);
        roster.setBounds(MARGIN, GRID_TOP, CONTENT_W,
                CardGrid.heightFor(roster.size(), COLUMNS, CARD_HEIGHT, 6));
        roster.layout();
        roster.registerFocusables(ring);

        for (int i = 0; i < MAX_SWATCHES; i++) {
            Swatch swatch = new Swatch();
            swatch.setBounds(MARGIN + i * (SWATCH + 8.0), SWATCH_TOP, SWATCH, SWATCH);
            swatches.add(swatch);
            ring.add(swatch);
        }

        select = new Button("", this::selectCurrent);
        select.setFontSize(15);
        select.setBounds(MARGIN, ACTION_TOP, (CONTENT_W - 8) / 2.0, ACTION_H);
        buy = new Button("", this::buyCurrent);
        buy.setFontSize(15);
        buy.setBounds(MARGIN + (CONTENT_W - 8) / 2.0 + 8, ACTION_TOP, (CONTENT_W - 8) / 2.0,
                ACTION_H);
        ring.add(select);
        ring.add(buy);

        tier = new ListView("", tierOptions(), tierIndex());
        tier.setWrapping(false);
        tier.setFontSize(14);
        tier.setBounds(MARGIN, TIER_TOP, CONTENT_W, TIER_H);
        tier.setOnChange(this::selectTier);
        ring.add(tier);

        back = new Button("", screens::pop);
        back.setFontSize(16);
        back.setBounds(MARGIN, FOOTER_TOP, CONTENT_W, FOOTER_H);
        ring.add(back);

        wallet.setBounds(Playfield.WIDTH - WALLET_W - 14.0, 14, WALLET_W, 26);
        wallet.setAlign(Align.RIGHT);
        wallet.setAmountNow(coins());
        shownLanguage = strings.language();
        refreshTexts();
    }

    // ------------------------------------------------------------------ state

    /**
     * The coins the profile holds.
     *
     * @return the balance
     */
    private long coins() {
        return Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
    }

    /**
     * The bird the palette row, the actions and the tooltip are about: the focused card, falling
     * back to the selected bird.
     *
     * @return the bird id
     */
    public String currentBirdId() {
        return currentBirdId;
    }

    /**
     * The bird the profile flies with.
     *
     * @return the bird id
     */
    public String selectedBirdId() {
        return profile.selected.birdId;
    }

    /**
     * The roster grid.
     *
     * @return the grid
     */
    public CardGrid roster() {
        return roster;
    }

    /**
     * The palette swatches, in the order the current bird declares them; entries past the bird's
     * palette count are hidden.
     *
     * @return the swatches
     */
    public List<UiNode> paletteSwatches() {
        return List.copyOf(swatches);
    }

    /**
     * The Select button.
     *
     * @return the button
     */
    public Button selectButton() {
        return select;
    }

    /**
     * The Buy button.
     *
     * @return the button
     */
    public Button buyButton() {
        return buy;
    }

    /**
     * The tier picker.
     *
     * @return the list
     */
    public ListView tierList() {
        return tier;
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
     * The tooltip drawn over the screen.
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
     * The breakdown rows in display order.
     *
     * @return an unmodifiable snapshot
     */
    public List<Row> rows() {
        return List.copyOf(rows);
    }

    /**
     * One breakdown row by id: {@code stat.<STAT>} for a stat, {@code stat.<STAT>.base} for its
     * base value and {@code stat.<STAT>.<source>} for one contribution.
     *
     * @param id the row id
     * @return the row, or {@code null} when the screen does not show it
     */
    public Row row(String id) {
        for (Row row : rows) {
            if (row.id().equals(id)) {
                return row;
            }
        }
        return null;
    }

    /**
     * The line that stands in for the ability loadout until M5.
     *
     * @return the line
     */
    public String abilityLine() {
        return abilityLine;
    }

    // ------------------------------------------------------------------ actions

    /**
     * What activating a card does: select an owned bird, or leave a locked one to the Buy button.
     *
     * @param birdId the bird id
     */
    private void activate(String birdId) {
        currentBirdId = birdId;
        if (profile.isUnlocked(BirdDef.NAMESPACE + birdId)) {
            selectBird(birdId);
        } else {
            refreshCurrent();
        }
    }

    /**
     * Writes the selection and saves it (D15's selection trigger).
     *
     * @param birdId the bird id
     */
    private void selectBird(String birdId) {
        if (selection == null || !selection.selectBird(profile, birdId, content)) {
            return;
        }
        currentBirdId = birdId;
        refreshState();
    }

    /** Selects the bird of the focused card. */
    private void selectCurrent() {
        selectBird(currentBirdId);
    }

    /** Buys the bird of the focused card, when it is locked and has a price. */
    private void buyCurrent() {
        purchase(BirdDef.NAMESPACE + currentBirdId);
    }

    /**
     * Buys one unlockable and reports what happened as a toast.
     *
     * @param unlockId the namespaced id
     */
    private void purchase(String unlockId) {
        if (unlocks == null) {
            return;
        }
        PurchaseResult result = unlocks.purchase(profile, unlockId, content);
        if (result.ok()) {
            toasts.push(strings.format(StringKey.TOAST_PURCHASED,
                    ProgressionText.unlockableName(strings, content, unlockId)), Toast.Kind.INFO);
        } else if (result.status() == PurchaseStatus.INSUFFICIENT_FUNDS) {
            toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED,
                    strings.get(StringKey.SHOP_CANNOT_AFFORD)), Toast.Kind.WARNING);
        }
        refreshState();
    }

    /**
     * Selects a palette of the selected bird, or buys nothing: a locked palette is earned, never
     * bought (no shipped palette carries a {@code purchase} branch).
     *
     * @param paletteId the palette id
     */
    private void selectPalette(String paletteId) {
        if (selection != null) {
            selection.selectPalette(profile, paletteId, content);
        }
        refreshState();
    }

    /**
     * Selects a tier from the picker, refusing one the player has not unlocked.
     *
     * @param index the index in {@link #tierIds}
     */
    private void selectTier(int index) {
        if (index < 0 || index >= tierIds.size()) {
            return;
        }
        String tierId = tierIds.get(index);
        if (selection != null && selection.selectTier(profile, tierId, content)) {
            refreshState();
            return;
        }
        toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED,
                strings.get(StringKey.COMMON_LOCKED)), Toast.Kind.WARNING);
        tier.selectQuietly(tierIndex());
    }

    // ------------------------------------------------------------------ building

    /**
     * The tier options: every tier the content ships, with the locked ones marked.
     *
     * @return one label per tier
     */
    private List<String> tierOptions() {
        tierIds.clear();
        List<String> options = new ArrayList<>();
        for (TierDef def : content.tiers()) {
            tierIds.add(def.id());
            String name = ProgressionText.name(strings, ContentKind.TIER, def.id());
            options.add(profile.isUnlocked(def.unlockableId()) ? name
                    : name + " (" + strings.get(StringKey.COMMON_LOCKED) + ")");
        }
        if (options.isEmpty()) {
            options.add(strings.get(StringKey.COMMON_NONE));
        }
        return options;
    }

    /**
     * The index of the selected tier.
     *
     * @return the index, {@code 0} when the selection names no shipped tier
     */
    private int tierIndex() {
        int index = tierIds.indexOf(profile.selected.tierId);
        return index < 0 ? 0 : index;
    }

    /** Re-reads every label from the string table (a language switch, D25). */
    public void refreshTexts() {
        wallet.setFormat(strings.get(StringKey.HUD_COINS));
        select.setText(strings.get(StringKey.COMMON_SELECT));
        buy.setText(strings.get(StringKey.COMMON_BUY));
        back.setText(strings.get(StringKey.COMMON_BACK));
        tier.setLabel(strings.get(StringKey.BIRDS_TIER));
        tier.setOptions(tierOptions());
        tier.selectQuietly(tierIndex());
        shownLanguage = strings.language();
        refreshState();
    }

    /**
     * Rebuilds everything that depends on the profile: the cards, the palette row, the actions,
     * the ability line and the breakdown. Called after every purchase, every selection and on
     * entry, which is what makes a node bought elsewhere visible here.
     */
    public void refreshState() {
        long balance = coins();
        for (BirdDef bird : content.birds()) {
            CardGrid.Card card = roster.card(bird.id());
            if (card == null) {
                continue;
            }
            boolean owned = profile.isUnlocked(bird.unlockableId());
            boolean isSelected = bird.id().equals(profile.selected.birdId);
            long price = UnlockEvaluator.priceOf(bird.unlock());
            card.setTitle(ProgressionText.name(strings, ContentKind.BIRD, bird.id()));
            card.setLocked(!owned);
            card.setSelected(isSelected);
            if (owned) {
                card.setSubtitle(archetypeName(bird));
                card.setBadge(isSelected ? strings.get(StringKey.COMMON_SELECTED) : "", false);
            } else {
                card.setSubtitle(ProgressionText.unlockText(strings, content, bird.unlock(),
                        profile));
                card.setBadge(price >= 0 ? Long.toString(price) : "", price >= 0);
            }
            card.setTooltip(tooltipFor(bird, owned, price));
        }
        roster.select(profile.selected.birdId);
        tier.setOptions(tierOptions());
        tier.selectQuietly(tierIndex());
        wallet.setAmount(balance);
        buildBreakdown();
        refreshCurrent();
    }

    /**
     * Rebuilds only what depends on the focused card — the palette row, the two actions and the
     * ability line — which is what changes when the focus moves from one bird to another. The
     * breakdown is deliberately not part of it: it describes the <em>selected</em> build, not the
     * card the player is looking at.
     */
    public void refreshCurrent() {
        if (currentBirdId == null || !content.birds().contains(currentBirdId)) {
            currentBirdId = profile.selected.birdId;
        }
        long balance = coins();
        BirdDef current = content.birds().get(currentBirdId);
        List<PaletteDef> palettes = current.palettes();
        for (int i = 0; i < swatches.size(); i++) {
            Swatch swatch = swatches.get(i);
            if (i >= palettes.size()) {
                swatch.setVisible(false);
                swatch.bind(null, false, false, "");
                continue;
            }
            PaletteDef palette = palettes.get(i);
            boolean owned = profile.isUnlocked(current.cosmeticId(palette.id()));
            boolean active = current.id().equals(profile.selected.birdId)
                    && palette.id().equals(profile.selected.paletteId);
            String name = ProgressionText.name(strings, ContentKind.COSMETIC,
                    current.id() + "." + palette.id());
            String tip = owned ? name : name + " - "
                    + ProgressionText.unlockText(strings, content, palette.unlock(), profile);
            swatch.setVisible(true);
            swatch.bind(palette, owned, active, tip);
            swatch.setOnAction(owned ? () -> selectPalette(palette.id()) : null);
        }

        boolean owned = profile.isUnlocked(BirdDef.NAMESPACE + currentBirdId);
        long price = UnlockEvaluator.priceOf(evaluator.conditionOf(
                BirdDef.NAMESPACE + currentBirdId));
        select.setEnabled(owned && selection != null
                && !currentBirdId.equals(profile.selected.birdId));
        buy.setEnabled(!owned && unlocks != null && price >= 0 && balance >= price);
        buy.setText(price >= 0 && !owned
                ? strings.get(StringKey.COMMON_BUY) + "  " + ProgressionText.price(strings, price)
                : strings.get(StringKey.COMMON_BUY));
        // E19: the slots and the innate passives are drawn from the bird, but the loadout only
        // exists once the ability system does, and GameContent.playable is the single answer to
        // whether it does. The innate passives are named because they are half of what a bird
        // trades for: Ironbeak pays -20 % coins for a shield that does not exist before M5, and a
        // line that only counted the slots would present that as a straight upgrade.
        abilityLine = strings.get(StringKey.BIRDS_ABILITIES) + ": "
                + strings.format(StringKey.BIRDS_PASSIVE_SLOTS,
                        current.passiveSlots() + profile.passiveSlotBonus)
                + innateAbilities(current)
                + (content.playable(ContentKind.ABILITY) ? ""
                        : " - " + strings.format(StringKey.COMMON_SOON, ABILITY_MILESTONE));
    }

    /**
     * The innate passive abilities of a bird, in parentheses.
     *
     * @param bird the bird
     * @return {@code " (Shield)"}, or the empty string when the bird has no innate passive
     */
    private String innateAbilities(BirdDef bird) {
        if (bird.passiveAbilities().isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>(bird.passiveAbilities().size());
        for (String abilityId : bird.passiveAbilities()) {
            names.add(ProgressionText.name(strings, ContentKind.ABILITY, abilityId));
        }
        return " (" + String.join(", ", names) + ")";
    }

    /**
     * The tooltip of one bird card: what it is, and how it is opened when it is not open yet.
     *
     * @param bird the bird
     * @param owned whether the profile owns it
     * @param price its shop price, or {@code -1}
     * @return the text
     */
    private String tooltipFor(BirdDef bird, boolean owned, long price) {
        String desc = ProgressionText.description(strings, ContentKind.BIRD, bird.id());
        if (owned) {
            return desc;
        }
        String how = ProgressionText.unlockText(strings, content, bird.unlock(), profile);
        String text = desc + " - " + strings.get(StringKey.COMMON_LOCKED) + ": " + how;
        if (price >= 0 && !how.equals(ProgressionText.price(strings, price))) {
            // The cheapest branch was the skill one; the price is still worth knowing.
            text = text + " / " + ProgressionText.price(strings, price);
        }
        return text;
    }

    /**
     * The translated name of a bird's archetype.
     *
     * @param bird the bird
     * @return the name
     */
    private String archetypeName(BirdDef bird) {
        return strings.get(StringKey.valueOf("ARCHETYPE_" + bird.archetype().name()));
    }

    /**
     * Builds the stat breakdown of the run that would start right now (D8).
     *
     * <p>A stat earns a block when something touches it or when the bird's base differs from the
     * default; the core physics stats are always there, because "did the node I bought change the
     * gravity?" is the question this panel exists to answer.
     */
    private void buildBreakdown() {
        rows.clear();
        contentHeight = 0;
        StatSheet sheet = RunLoadout.previewStats(profile, content);
        int shown = 0;
        for (StatId stat : StatId.values()) {
            StatBreakdown breakdown = sheet.breakdown(stat);
            boolean always = ALWAYS_SHOWN.contains(stat);
            if (!always && breakdown.contributions().isEmpty()
                    && breakdown.base() == stat.defaultValue()) {
                continue;
            }
            shown++;
            rows.add(new Row("stat." + stat.name(), ProgressionText.statLabel(strings, stat),
                    ProgressionText.number(breakdown.value()), true, contentHeight));
            contentHeight += HEADER_H;
            rows.add(new Row("stat." + stat.name() + ".base",
                    strings.get(StringKey.BIRDS_BREAKDOWN_BASE),
                    ProgressionText.number(breakdown.base()), false, contentHeight));
            contentHeight += ROW_H;
            for (EffectStack.Entry entry : breakdown.contributions()) {
                rows.add(new Row("stat." + stat.name() + "." + entry.modifier().source(),
                        ProgressionText.sourceLabel(strings, content, entry.modifier().source()),
                        ProgressionText.effect(strings, entry.modifier()), false, contentHeight));
                contentHeight += ROW_H;
            }
        }
        if (shown == 0) {
            rows.add(new Row("empty", strings.get(StringKey.BIRDS_BREAKDOWN_EMPTY), "", false,
                    contentHeight));
            contentHeight += ROW_H;
        }
        scroll = MathUtil.clamp(scroll, 0, maxScroll());
    }

    /** The stats the panel always lists, whether or not anything touches them. */
    private static final List<StatId> ALWAYS_SHOWN = List.of(StatId.GRAVITY,
            StatId.FLAP_VELOCITY, StatId.MAX_FALL_SPEED, StatId.SCROLL_SPEED, StatId.GAP_SIZE,
            StatId.HITBOX_SCALE, StatId.SCORE_MULT, StatId.COIN_MULT);

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(roster.card(profile.selected.birdId));
        screens.setLetterboxRgb(PALETTE.letterbox());
        scroll = 0;
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
        ticks++;
        toasts.tick();
        wallet.tick();
        UiNode focusedBefore = ring.focused();
        ring.handle(input);
        tier.tick(input);
        UiNode focused = ring.focused();
        if (focused != focusedBefore && focused instanceof CardGrid.Card card) {
            currentBirdId = card.id();
            refreshCurrent();
        }
        if (input.wheel() != 0) {
            scroll = MathUtil.clamp(scroll - input.wheel() * (double) WHEEL_STEP, 0, maxScroll());
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
     * Points the tooltip at whatever the player is asking about: the node under the pointer, or
     * the focused one when the pointer is elsewhere.
     *
     * @param input the tick input
     */
    private void updateTooltip(InputFrame input) {
        UiNode under = ring.nodeAt(input.mouseX(), input.mouseY());
        UiNode target = under != null ? under : ring.focused();
        tooltip.update(target, tooltipText(target));
    }

    /**
     * The tooltip text of a node.
     *
     * @param node the node, may be {@code null}
     * @return the text, empty when the node explains itself
     */
    private String tooltipText(UiNode node) {
        if (node instanceof CardGrid.Card card) {
            return card.tooltip();
        }
        if (node instanceof Swatch swatch) {
            return swatch.tooltip();
        }
        return "";
    }

    private double maxScroll() {
        return Math.max(0, contentHeight - (VIEW_BOTTOM - VIEW_TOP));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        g.setFont(Fonts.bold(26));
        TextPainter.drawOutlined(g, strings.get(StringKey.BIRDS_TITLE), MARGIN, TITLE_BASELINE,
                Align.LEFT, ProceduralArt.TEXT_LIGHT, ProceduralArt.letterboxColor(PALETTE), 2);
        wallet.render(g);
        roster.render(g);

        g.setFont(Fonts.bold(13));
        g.setColor(ProceduralArt.accentColor(PALETTE));
        TextPainter.draw(g, strings.get(StringKey.BIRDS_PALETTES), MARGIN,
                PALETTE_LABEL_BASELINE);
        for (Swatch swatch : swatches) {
            if (swatch.isVisible()) {
                swatch.render(g);
            }
        }
        select.render(g);
        buy.render(g);
        ProceduralArt.panel(g, MARGIN - 4, TIER_TOP - 4, CONTENT_W + 8,
                ABILITY_BASELINE + 6 - (TIER_TOP - 4));
        tier.render(g);
        g.setFont(Fonts.regular(12));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, abilityLine, MARGIN, ABILITY_BASELINE);

        ProceduralArt.panel(g, MARGIN - 4, VIEW_TOP - 22, CONTENT_W + 8,
                VIEW_BOTTOM - VIEW_TOP + 26);
        g.setFont(Fonts.bold(13));
        g.setColor(ProceduralArt.accentColor(PALETTE));
        TextPainter.draw(g, strings.get(StringKey.BIRDS_BREAKDOWN), MARGIN,
                BREAKDOWN_LABEL_BASELINE);
        Shape oldClip = g.getClip();
        g.clipRect(MARGIN - 4, VIEW_TOP, CONTENT_W + 8, VIEW_BOTTOM - VIEW_TOP);
        double dy = VIEW_TOP - scroll;
        g.translate(0.0, dy);
        for (Row row : rows) {
            renderRow(g, row);
        }
        g.translate(0.0, -dy);
        g.setClip(oldClip);
        renderScrollbar(g);

        back.render(g);
        tooltip.render(g);
        toasts.render(g);
    }

    private void renderRow(Graphics2D g, Row row) {
        double baseline = row.y() + (row.header() ? HEADER_H - 5 : ROW_H - 4);
        if (row.header()) {
            g.setFont(Fonts.bold(12));
            g.setColor(ProceduralArt.TEXT_LIGHT);
        } else {
            g.setFont(Fonts.regular(11));
            g.setColor(ProceduralArt.TEXT_MUTED);
        }
        TextPainter.draw(g, row.label(), MARGIN + (row.header() ? 0.0 : 10.0), baseline);
        if (!row.value().isEmpty()) {
            g.setColor(row.header() ? ProceduralArt.COIN_GOLD : ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, row.value(), MARGIN + (double) CONTENT_W - 4, baseline,
                    Align.RIGHT);
        }
    }

    private void renderScrollbar(Graphics2D g) {
        double max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackH = VIEW_BOTTOM - VIEW_TOP;
        int thumbH = (int) Math.max(20, trackH * (trackH / contentHeight));
        int thumbY = VIEW_TOP + (int) Math.round((trackH - thumbH) * (scroll / max));
        g.setColor(SCROLLBAR);
        g.fillRoundRect(Playfield.WIDTH - MARGIN, thumbY, 4, thumbH, 4, 4);
    }

    /**
     * Draws a bird portrait in the palette the profile has selected for that bird, falling back to
     * the bird's first palette.
     *
     * @param g the context
     * @param bird the bird
     * @param cx the centre x
     * @param cy the centre y
     * @param size the art size
     */
    private void paintPortrait(Graphics2D g, BirdDef bird, double cx, double cy, double size) {
        PaletteDef palette = paletteOf(bird);
        if (palette == null) {
            return;
        }
        double phase = bird.id().equals(profile.selected.birdId)
                ? (ticks % 48) / 48.0 : 0.0;
        ProceduralArt.drawBirdPortrait(g, cx, cy + size * 0.05, size * 0.9, phase,
                palette.bodyRgb(), palette.wingRgb(), palette.eyeRgb(), palette.accentRgb(),
                bird.shape());
    }

    /**
     * The palette a bird is drawn in: the selected one for the selected bird, its first otherwise.
     *
     * @param bird the bird
     * @return the palette, or {@code null} when the bird ships none
     */
    private PaletteDef paletteOf(BirdDef bird) {
        if (bird.id().equals(profile.selected.birdId)) {
            PaletteDef selectedPalette = bird.palette(profile.selected.paletteId);
            if (selectedPalette != null) {
                return selectedPalette;
            }
        }
        return bird.palettes().isEmpty() ? null : bird.palettes().get(0);
    }

    /**
     * One line of the breakdown.
     *
     * @param id the stable identifier a test addresses the row by
     * @param label the translated label
     * @param value the translated value, empty when there is none
     * @param header whether the row heads a stat (rather than listing one of its sources)
     * @param y the row's top edge in content space
     */
    public record Row(String id, String label, String value, boolean header, double y) {
    }

    /** One palette swatch: a square in the palette's body colour, with its wing as a corner. */
    private static final class Swatch extends UiNode {

        private PaletteDef palette;
        private boolean owned;
        private boolean active;
        private String tooltip = "";

        Swatch() {
            setVisible(false);
        }

        /**
         * Points the swatch at a palette.
         *
         * @param newPalette the palette, or {@code null} to clear it
         * @param isOwned whether the profile owns it
         * @param isActive whether it is the selected palette of the selected bird
         * @param tip the tooltip text
         */
        void bind(PaletteDef newPalette, boolean isOwned, boolean isActive, String tip) {
            this.palette = newPalette;
            this.owned = isOwned;
            this.active = isActive;
            this.tooltip = tip == null ? "" : tip;
            setEnabled(newPalette != null);
        }

        String tooltip() {
            return tooltip;
        }

        @Override
        public void render(Graphics2D g) {
            if (palette == null) {
                return;
            }
            int bx = (int) Math.round(x());
            int by = (int) Math.round(y());
            int bw = (int) Math.round(width());
            int bh = (int) Math.round(height());
            g.setColor(new Color(palette.bodyRgb()));
            g.fillRoundRect(bx, by, bw, bh, 6, 6);
            g.setColor(new Color(palette.wingRgb()));
            g.fillRoundRect(bx + bw / 2, by + bh / 2, bw / 2 - 2, bh / 2 - 2, 4, 4);
            if (!owned) {
                g.setColor(new Color(0x10, 0x1C, 0x1E, 0xC0));
                g.fillRoundRect(bx, by, bw, bh, 6, 6);
            }
            Stroke old = g.getStroke();
            g.setStroke(SWATCH_STROKE);
            g.setColor(active ? ProceduralArt.COIN_GOLD
                    : (isFocused() ? ProceduralArt.TEXT_LIGHT : ProceduralArt.TEXT_MUTED));
            g.drawRoundRect(bx, by, bw, bh, 6, 6);
            g.setStroke(old);
        }
    }
}
