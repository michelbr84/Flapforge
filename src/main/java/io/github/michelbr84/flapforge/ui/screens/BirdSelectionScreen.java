package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.content.defs.WorldPaletteDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
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
import java.util.Map;
import java.util.Objects;

/**
 * The bird selection (D17, M4/M5): the roster, the colours, the tier of the next run, the ability
 * loadout and — the other point of the screen — the stat breakdown of the build the player is
 * about to fly.
 *
 * <p>The roster is a {@link CardGrid} of all seven birds. An owned bird shows its name, its
 * archetype and its portrait in the palette the profile has selected for it; a locked one is
 * dimmed and says, in words, the <em>cheapest</em> way to open it ({@code Play 3 runs},
 * {@code 150 coins}), which is {@link ProgressionText#cheapestBranch} measured against this very
 * profile. Activating an owned card writes {@code profile.selected} through
 * {@link SelectionManager} and saves; Buy pays for a locked one through {@link UnlockManager},
 * which is the only path that grants a {@code purchase} branch (D13).
 *
 * <p>The panel below is not a second implementation of the stat pipeline: it reads
 * {@link RunLoadout#previewRun} — the very run that would start right now — and lists, per stat,
 * the resolved value and every contributing modifier with the thing it came from (the bird, an
 * upgrade node, the bird's synergy with the nodes owned, the world, the tier, the difficulty
 * curve). Buying {@code feather_1} in the upgrade screen therefore shows up here as a new line
 * under Gravity and a lower number next to it (D8, D17).
 *
 * <p><b>The loadout (M5, D9, E3).</b> Above the panel sits one chip per slot: the active ability,
 * the bird's passive slots ({@code BirdDef.passiveSlots + profile.passiveSlotBonus}) and the
 * passives the bird grants innately, which are shown fixed because nothing can unequip them.
 * Activating a chip steps to the next ability the slot may hold and writes
 * {@code profile.selected} through {@link SelectionManager}, which saves at once. The panel then
 * lists every unlocked ability with its level, its kind, its tags, the description of the level
 * the profile owns and what that level does — greyed out, with the rule named, when the run the
 * screen is previewing would strip it ({@code NO_DEFENSIVE_ABILITIES}, {@code NO_REVIVE}). A
 * greyed-out ability is not offered by any chip either: {@code Run.start()} would strip it
 * anyway, and a slot that accepted it would be lying.
 *
 * <p><b>The world (M7, D17).</b> Between the actions and the tier sits the world picker: the
 * five worlds of {@code worlds.json} in order, each with a swatch of its palette and, under the
 * name, the hazards it spawns (the kinds with a positive spawn weight) — or, for a locked one,
 * the cheapest way to open it. Stepping to an owned world writes {@code profile.selected} through
 * {@link SelectionManager#selectWorld}; stepping to a locked one is refused with a toast and the
 * row snaps back, exactly like the tier picker.
 */
public final class BirdSelectionScreen implements Screen {

    /** Top of the card grid. */
    public static final int GRID_TOP = 48;
    /** Height of one bird card. */
    public static final int CARD_HEIGHT = 46;
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
    /** Columns of the loadout row. */
    public static final int SLOT_COLUMNS = 3;
    /**
     * Chips the loadout row holds: the active slot, up to four passive slots (Oracle's three plus
     * the {@code passive_slot} grant of E3) and one innate passive. A bird that grants more innate
     * passives than fit still lists them in the ability panel below.
     */
    public static final int MAX_SLOTS = 6;
    /** Highest number of passive slots any bird plus the E3 bonus can reach. */
    public static final int MAX_PASSIVE_SLOTS = 4;
    /** Characters a detail row fits before it is wrapped onto the next one. */
    public static final int WRAP_CHARS = 62;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 34;
    private static final int WALLET_W = 130;
    private static final int PALETTE_LABEL_BASELINE = 264;
    private static final int SWATCH_TOP = 268;
    private static final int ACTION_TOP = 302;
    private static final int ACTION_H = 30;
    /** Top of the world picker row (M7). */
    public static final int WORLD_TOP = 338;
    /** Height of the world picker row: the name line and the hazard line. */
    public static final int WORLD_H = 40;
    private static final int TIER_TOP = 382;
    private static final int TIER_H = 24;
    private static final int ABILITY_BASELINE = 422;
    private static final int SLOT_TOP = 426;
    private static final int SLOT_H = 22;
    private static final int SLOT_VGAP = 4;
    private static final int SLOT_HGAP = 6;
    private static final int BREAKDOWN_LABEL_BASELINE = 496;
    private static final int VIEW_TOP = 502;
    private static final int VIEW_BOTTOM = Playfield.HEIGHT - 58;
    private static final int FOOTER_TOP = Playfield.HEIGHT - 52;
    private static final int FOOTER_H = 40;
    private static final Color SCROLLBAR = new Color(0xF4, 0xF8, 0xF8, 0x50);
    private static final Stroke SWATCH_STROKE = new BasicStroke(2f);
    private static final Color SLOT_BACK = new Color(0x10, 0x1C, 0x1E, 0x9C);
    private static final Color SLOT_BLOCKED = new Color(0xE8, 0x5A, 0x4A);
    private static final Color SLOT_FIXED = new Color(0x6F, 0xD1, 0xA8);

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
    private final List<AbilitySlot> slots = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final CurrencyDisplay wallet = new CurrencyDisplay();
    private final Tooltip tooltip = new Tooltip();
    private final Button select;
    private final Button buy;
    private final WorldRow world;
    private final ListView tier;
    private final Button back;
    private final List<String> tierIds = new ArrayList<>();
    private final List<String> worldIds = new ArrayList<>();
    private String currentBirdId;
    private String shownLanguage;
    private String abilityLine = "";
    private RuleSet previewRules = RuleSet.EMPTY;
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

        for (int i = 0; i < MAX_SLOTS; i++) {
            AbilitySlot slot = new AbilitySlot();
            int column = i % SLOT_COLUMNS;
            int row = i / SLOT_COLUMNS;
            double width = (CONTENT_W - (SLOT_COLUMNS - 1.0) * SLOT_HGAP) / SLOT_COLUMNS;
            slot.setBounds(MARGIN + column * (width + SLOT_HGAP),
                    SLOT_TOP + row * (SLOT_H + SLOT_VGAP), width, SLOT_H);
            int index = i;
            slot.setOnAction(() -> cycleSlot(index));
            slots.add(slot);
            ring.add(slot);
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

        world = new WorldRow("", worldOptions(), worldIndex());
        world.setWrapping(false);
        world.setFontSize(14);
        world.setBounds(MARGIN, WORLD_TOP, CONTENT_W, WORLD_H);
        world.setOnChange(this::selectWorld);
        ring.add(world);

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
     * The world picker (M7).
     *
     * @return the row
     */
    public WorldRow worldList() {
        return world;
    }

    /**
     * The world ids the picker steps through, in {@code worlds.json} order.
     *
     * @return an unmodifiable snapshot
     */
    public List<String> worldIds() {
        return List.copyOf(worldIds);
    }

    /**
     * The world the picker points at.
     *
     * @return the world id, or {@code null} when the content ships no world
     */
    public String currentWorldId() {
        int index = world.selectedIndex();
        return index >= 0 && index < worldIds.size() ? worldIds.get(index) : null;
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
     * The heading of the loadout row: how many passive slots the selected bird carries and which
     * passives it grants for free.
     *
     * @return the line
     */
    public String abilityLine() {
        return abilityLine;
    }

    /**
     * The loadout chips in display order: the active slot, the bird's passive slots and its
     * innate passives. Chips past what the bird carries are hidden.
     *
     * @return an unmodifiable snapshot
     */
    public List<AbilitySlot> abilitySlots() {
        return List.copyOf(slots);
    }

    /**
     * The active-ability chip.
     *
     * @return the chip
     */
    public AbilitySlot activeSlot() {
        return slots.get(0);
    }

    /**
     * One chip by role and index.
     *
     * @param role the role
     * @param index the index within the role, 0-based
     * @return the chip, or {@code null} when the bird does not carry it
     */
    public AbilitySlot slot(SlotRole role, int index) {
        int seen = 0;
        for (AbilitySlot slot : slots) {
            if (!slot.isVisible() || slot.role() != role) {
                continue;
            }
            if (seen++ == index) {
                return slot;
            }
        }
        return null;
    }

    /**
     * The rules the next run would carry (D9), which is what the screen greys abilities out with.
     *
     * @return the rule set, resolved from the very run the player is about to start
     */
    public RuleSet previewRules() {
        return previewRules;
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

    /**
     * Selects a world from the picker, refusing one the player has not unlocked (M7).
     *
     * @param index the index in {@link #worldIds}
     */
    private void selectWorld(int index) {
        if (index < 0 || index >= worldIds.size()) {
            return;
        }
        String worldId = worldIds.get(index);
        if (selection != null && selection.selectWorld(profile, worldId, content)) {
            refreshState();
            return;
        }
        toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED,
                strings.get(StringKey.COMMON_LOCKED)), Toast.Kind.WARNING);
        world.selectQuietly(worldIndex());
        refreshWorldRow();
    }

    // ------------------------------------------------------------------ building

    /**
     * The world options: every world the content ships, in order, with the locked ones marked.
     *
     * @return one label per world
     */
    private List<String> worldOptions() {
        worldIds.clear();
        List<String> options = new ArrayList<>();
        if (content.has(GameContent.WORLDS)) {
            for (WorldDef def : content.worlds()) {
                worldIds.add(def.id());
                String name = ProgressionText.name(strings, ContentKind.WORLD, def.id());
                options.add(profile.isUnlocked(def.unlockableId()) ? name
                        : name + " (" + strings.get(StringKey.COMMON_LOCKED) + ")");
            }
        }
        if (options.isEmpty()) {
            options.add(strings.get(StringKey.COMMON_NONE));
        }
        return options;
    }

    /**
     * The index of the selected world.
     *
     * @return the index, {@code 0} when the selection names no shipped world
     */
    private int worldIndex() {
        int index = worldIds.indexOf(profile.selected.worldId);
        return index < 0 ? 0 : index;
    }

    /**
     * Points the world row's swatch and detail line at the world it shows: the hazards for an
     * owned world, the cheapest way in for a locked one.
     */
    private void refreshWorldRow() {
        String id = currentWorldId();
        if (id == null || !content.worlds().contains(id)) {
            world.bind(null, "", false, "");
            return;
        }
        WorldDef def = content.worlds().get(id);
        boolean owned = profile.isUnlocked(def.unlockableId());
        String detail = owned
                ? strings.format(StringKey.BIRDS_WORLD_HAZARDS, hazardsOf(def))
                : strings.format(StringKey.BIRDS_WORLD_LOCKED,
                        ProgressionText.unlockText(strings, content, def.unlock(), profile));
        String tip = ProgressionText.description(strings, ContentKind.WORLD, id);
        world.bind(def.palette(), detail, !owned, owned ? tip : tip + " - " + detail);
    }

    /**
     * The hazards a world spawns: the families with a positive spawn weight, named, in kind
     * order.
     *
     * @param def the world
     * @return the comma-separated names
     */
    private String hazardsOf(WorldDef def) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<ObstacleKind, Integer> entry : def.spawnWeights().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                names.add(ProgressionText.obstacleName(strings, entry.getKey()));
            }
        }
        return names.isEmpty() ? strings.get(StringKey.COMMON_NONE) : String.join(", ", names);
    }

    /**
     * The hazard or lock line under the world name, as drawn.
     *
     * @return the text
     */
    public String worldDetail() {
        return world.detail();
    }

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
        world.setLabel(strings.get(StringKey.BIRDS_WORLD));
        world.setOptions(worldOptions());
        world.selectQuietly(worldIndex());
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
        world.setOptions(worldOptions());
        world.selectQuietly(worldIndex());
        refreshWorldRow();
        wallet.setAmount(balance);
        // One preview run answers both questions the panels below ask: what the stats resolve to
        // and which rules the run carries (D8, D9). Building it twice could not disagree, but
        // building it once means it cannot.
        Run preview = RunLoadout.previewRun(profile, content);
        previewRules = preview.simulation().rules();
        refreshLoadout();
        buildBreakdown(preview.simulation().stats());
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
        // The innate passives are named because they are half of what a bird trades for: Ironbeak
        // pays -20 % coins for a shield it grants for free, and a line that only counted the
        // slots would present that as a straight upgrade.
        abilityLine = strings.get(StringKey.BIRDS_ABILITIES) + ": "
                + strings.format(StringKey.BIRDS_PASSIVE_SLOTS, passiveSlotsOf(current))
                + innateAbilities(current);
    }

    // ------------------------------------------------------------------ loadout (M5)

    /**
     * How many passives the bird can equip: its own slots plus the {@code passive_slot} grant the
     * profile has earned (E3), never more chips than the row holds.
     *
     * @param bird the bird
     * @return the slot count
     */
    private int passiveSlotsOf(BirdDef bird) {
        return Math.min(MAX_PASSIVE_SLOTS, bird.passiveSlots() + profile.passiveSlotBonus);
    }

    /**
     * Rebuilds the loadout chips from the profile and the selected bird (D9, E3).
     *
     * <p>The chips describe the <em>selected</em> bird rather than the focused card, for the same
     * reason the breakdown does: they are the loadout the next run will actually fly with, and
     * that run uses the bird the profile has selected.
     */
    private void refreshLoadout() {
        BirdDef bird = content.birds().contains(profile.selected.birdId)
                ? content.birds().get(profile.selected.birdId) : null;
        if (bird == null) {
            for (AbilitySlot slot : slots) {
                slot.setVisible(false);
            }
            return;
        }
        int passiveSlots = passiveSlotsOf(bird);
        List<String> passives = equippedPassives(bird, passiveSlots);
        int used = 0;
        bindSlot(slots.get(used++), SlotRole.ACTIVE, 0, profile.selected.activeAbilityId);
        for (int i = 0; i < passiveSlots && used < MAX_SLOTS; i++) {
            bindSlot(slots.get(used++), SlotRole.PASSIVE, i,
                    i < passives.size() ? passives.get(i) : null);
        }
        for (String id : bird.passiveAbilities()) {
            if (used >= MAX_SLOTS) {
                break;
            }
            bindSlot(slots.get(used++), SlotRole.INNATE, 0, id);
        }
        while (used < MAX_SLOTS) {
            AbilitySlot slot = slots.get(used++);
            slot.setVisible(false);
            slot.bind(SlotRole.PASSIVE, null, "", "", "", false);
        }
    }

    /**
     * The passives the profile has equipped that this bird can actually carry, in slot order.
     *
     * <p>An id the player no longer owns, one that is not a passive and one the bird already
     * grants innately are dropped: the first two cannot be equipped at all, and the third would
     * spend a slot on something the bird gives away.
     *
     * @param bird the selected bird
     * @param slotCount how many slots the bird has
     * @return the ids, at most {@code slotCount} of them
     */
    private List<String> equippedPassives(BirdDef bird, int slotCount) {
        List<String> out = new ArrayList<>(slotCount);
        for (String id : profile.selected.passiveAbilityIds) {
            if (out.size() >= slotCount) {
                break;
            }
            AbilityDef def = abilityOrNull(id);
            if (def == null || def.kind() != AbilityKind.PASSIVE || out.contains(id)
                    || bird.passiveAbilities().contains(id)
                    || !profile.isUnlocked(def.unlockableId())) {
                continue;
            }
            out.add(id);
        }
        return out;
    }

    /**
     * Points one chip at an ability (or at nothing).
     *
     * @param slot the chip
     * @param role what the slot is
     * @param index its index within the role
     * @param abilityId the ability in it, or {@code null}
     */
    private void bindSlot(AbilitySlot slot, SlotRole role, int index, String abilityId) {
        AbilityDef def = abilityOrNull(abilityId);
        String label;
        switch (role) {
            case ACTIVE:
                label = strings.get(StringKey.BIRDS_SLOT_ACTIVE);
                break;
            case PASSIVE:
                label = strings.format(StringKey.BIRDS_SLOT_PASSIVE, index + 1);
                break;
            case INNATE:
            default:
                label = strings.get(StringKey.BIRDS_SLOT_INNATE);
                break;
        }
        String value = def == null ? strings.get(StringKey.BIRDS_SLOT_EMPTY)
                : ProgressionText.name(strings, ContentKind.ABILITY, def.id());
        RuleFlag blocked = def == null ? null
                : ProgressionText.strippedBy(def, previewRules);
        slot.setVisible(true);
        slot.bind(role, def == null ? null : def.id(), label, value,
                slotTooltip(def, blocked), blocked != null);
        // An innate passive is granted by the bird and cannot be traded away (D9), so its chip is
        // there to be read, not to be pressed.
        slot.setEnabled(role != SlotRole.INNATE && selection != null);
    }

    /**
     * What a chip says when the pointer rests on it.
     *
     * @param def the ability in the slot, or {@code null}
     * @param blocked the rule stripping it, or {@code null}
     * @return the text
     */
    private String slotTooltip(AbilityDef def, RuleFlag blocked) {
        if (def == null) {
            return strings.get(StringKey.BIRDS_SLOT_HINT);
        }
        StringBuilder out = new StringBuilder(ProgressionText.abilityDescription(strings, def,
                abilityLevelOf(def)));
        String effects = ProgressionText.abilityEffects(strings, def, abilityLevelOf(def));
        if (!effects.isEmpty()) {
            out.append(" - ").append(effects);
        }
        if (blocked != null) {
            out.append(" - ").append(strings.format(StringKey.BIRDS_ABILITY_BLOCKED,
                    ProgressionText.ruleName(strings, blocked)));
        }
        return out.toString();
    }

    /**
     * The level the profile owns an ability at (level 1 comes with the unlock).
     *
     * @param def the ability
     * @return the level
     */
    private int abilityLevelOf(AbilityDef def) {
        int owned = UpgradeManager.abilityLevelOwned(profile, def);
        return owned <= 0 ? 1 : owned;
    }

    /**
     * The ability of an id, or {@code null} when the content does not ship it.
     *
     * @param abilityId the id, may be {@code null}
     * @return the definition
     */
    private AbilityDef abilityOrNull(String abilityId) {
        if (abilityId == null || abilityId.isBlank()
                || !content.has(GameContent.ABILITIES)
                || !content.abilities().contains(abilityId)) {
            return null;
        }
        return content.abilities().get(abilityId);
    }

    /**
     * Advances one chip to the next ability it may hold, and writes the new loadout (D9, D15).
     *
     * <p>The cycle is {@code nothing -> first eligible -> ... -> nothing}: an ability already in
     * another slot and one the run's rules would strip are left out, which is exactly what "greyed
     * out" means for a slot the player can only step through.
     *
     * @param index the chip index in the row
     */
    private void cycleSlot(int index) {
        AbilitySlot slot = slots.get(index);
        if (selection == null || !slot.isVisible() || slot.role() == SlotRole.INNATE) {
            return;
        }
        List<String> options = optionsFor(slot);
        int at = options.indexOf(slot.abilityId());
        String next = options.get(((at < 0 ? 0 : at) + 1) % options.size());
        if (slot.role() == SlotRole.ACTIVE) {
            selection.selectActiveAbility(profile, next, content);
        } else {
            List<String> passives = new ArrayList<>(MAX_PASSIVE_SLOTS + 1);
            for (AbilitySlot other : slots) {
                if (!other.isVisible() || other.role() != SlotRole.PASSIVE) {
                    continue;
                }
                String id = other == slot ? next : other.abilityId();
                if (id != null && !passives.contains(id)) {
                    passives.add(id);
                }
            }
            // What the player chose beyond the chips this bird shows is kept, not dropped: the
            // selection is the profile's, and a bird with fewer slots only hides the tail of it
            // (SelectionManager.setPassiveAbilities: "the profile keeps what the player chose
            // even when they switch to a bird with fewer slots and back").
            for (String id : profile.selected.passiveAbilityIds) {
                if (id != null && !id.equals(slot.abilityId()) && !passives.contains(id)) {
                    passives.add(id);
                }
            }
            selection.setPassiveAbilities(profile, passives, content);
        }
        refreshState();
    }

    /**
     * What one chip may cycle through: {@code null} first, then every unlocked ability of the
     * slot's kind that is not innate, not already in another slot and not stripped by the rules.
     *
     * @param slot the chip
     * @return the options, always starting with {@code null}
     */
    private List<String> optionsFor(AbilitySlot slot) {
        List<String> options = new ArrayList<>();
        options.add(null);
        if (!content.has(GameContent.ABILITIES)) {
            return options;
        }
        AbilityKind kind = slot.role() == SlotRole.ACTIVE
                ? AbilityKind.ACTIVE : AbilityKind.PASSIVE;
        BirdDef bird = content.birds().contains(profile.selected.birdId)
                ? content.birds().get(profile.selected.birdId) : null;
        for (AbilityDef def : content.abilities()) {
            if (def.kind() != kind || !profile.isUnlocked(def.unlockableId())
                    || ProgressionText.strippedBy(def, previewRules) != null
                    || (bird != null && bird.passiveAbilities().contains(def.id()))
                    || equippedElsewhere(slot, def.id())) {
                continue;
            }
            options.add(def.id());
        }
        return options;
    }

    /**
     * Whether another chip of the same role already holds an ability.
     *
     * @param slot the chip being cycled
     * @param abilityId the ability
     * @return {@code true} when a sibling slot holds it
     */
    private boolean equippedElsewhere(AbilitySlot slot, String abilityId) {
        for (AbilitySlot other : slots) {
            if (other != slot && other.isVisible() && other.role() == slot.role()
                    && abilityId.equals(other.abilityId())) {
                return true;
            }
        }
        return false;
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
    private void buildBreakdown(StatSheet sheet) {
        rows.clear();
        contentHeight = 0;
        buildAbilityRows();
        rows.add(new Row("stats", strings.get(StringKey.BIRDS_BREAKDOWN), "", true,
                contentHeight));
        contentHeight += HEADER_H;
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

    /**
     * The ability panel (M5, D9): every unlocked ability with its level, its kind, its tags, what
     * one level of it does and the description of the level the profile owns — and, for one the
     * run's rules would strip, the rule responsible instead of a silent omission.
     *
     * <p>It sits above the stat breakdown in the same scrolling view because the two answer the
     * same question from two sides: what will the next run be.
     */
    private void buildAbilityRows() {
        rows.add(new Row("abilities", strings.get(StringKey.BIRDS_ABILITY_LIST), "", true,
                contentHeight));
        contentHeight += HEADER_H;
        int shown = 0;
        if (content.has(GameContent.ABILITIES)) {
            for (AbilityDef def : content.abilities()) {
                if (!profile.isUnlocked(def.unlockableId())) {
                    continue;
                }
                shown++;
                addAbilityRows(def);
            }
        }
        if (shown == 0) {
            rows.add(new Row("abilities.empty",
                    strings.get(StringKey.BIRDS_ABILITY_NONE_OWNED), "", false, contentHeight,
                    true));
            contentHeight += ROW_H;
        }
    }

    /**
     * The three or four rows of one unlocked ability.
     *
     * @param def the ability
     */
    private void addAbilityRows(AbilityDef def) {
        int level = abilityLevelOf(def);
        RuleFlag blocked = ProgressionText.strippedBy(def, previewRules);
        boolean dimmed = blocked != null;
        String name = ProgressionText.name(strings, ContentKind.ABILITY, def.id());
        if (isEquipped(def.id())) {
            name = name + " - " + strings.get(StringKey.BIRDS_ABILITY_EQUIPPED);
        }
        String id = "ability." + def.id();
        rows.add(new Row(id, name, ProgressionText.abilityLevel(strings, def, level), true,
                contentHeight, dimmed));
        contentHeight += HEADER_H;
        String kinds = ProgressionText.abilityKind(strings, def.kind());
        String tags = ProgressionText.abilityTags(strings, def);
        addWrapped(id + ".kind", tags.isEmpty() ? kinds : kinds + " - " + tags, dimmed);
        addWrapped(id + ".desc", ProgressionText.abilityDescription(strings, def, level), dimmed);
        addWrapped(id + ".effect", ProgressionText.abilityEffects(strings, def, level), dimmed);
        if (blocked != null) {
            rows.add(new Row(id + ".blocked", strings.format(StringKey.BIRDS_ABILITY_BLOCKED,
                    ProgressionText.ruleName(strings, blocked)), "", false, contentHeight, true));
            contentHeight += ROW_H;
        }
    }

    /**
     * Adds a detail line, wrapped onto as many rows as it needs.
     *
     * <p>The panel clips at its own edge, so a description longer than the column would simply be
     * cut in half — and an ability's description is exactly where the numbers that justify its
     * price live. The wrap is by character budget rather than by font metrics because the rows are
     * built when the profile changes, long before a {@code Graphics2D} exists; {@link #WRAP_CHARS}
     * is measured against the widest shipped line at the 11 pt detail size.
     *
     * <p>The first line keeps the row id, so a caller can address the line by what it is about;
     * the continuations are {@code <id>.2}, {@code <id>.3} and so on.
     *
     * @param id the row id
     * @param text the text, may be empty (nothing is added then)
     * @param dimmed whether the rows are greyed out
     */
    private void addWrapped(String id, String text, boolean dimmed) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int line = 1;
        int from = 0;
        while (from < text.length()) {
            int to = Math.min(text.length(), from + WRAP_CHARS);
            if (to < text.length()) {
                int space = text.lastIndexOf(' ', to);
                if (space > from) {
                    to = space;
                }
            }
            rows.add(new Row(line == 1 ? id : id + "." + line, text.substring(from, to).trim(), "",
                    false, contentHeight, dimmed));
            contentHeight += ROW_H;
            from = to + 1;
            line++;
        }
    }

    /**
     * Whether an ability is in one of the loadout chips.
     *
     * @param abilityId the ability id
     * @return {@code true} when a visible chip holds it
     */
    private boolean isEquipped(String abilityId) {
        for (AbilitySlot slot : slots) {
            if (slot.isVisible() && abilityId.equals(slot.abilityId())) {
                return true;
            }
        }
        return false;
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
        if (world.tick(input) && world.selectedIndex() != worldIndex()) {
            // A step onto a world the profile does not own: onChange refused it and snapped the
            // row back; a step onto an owned one already wrote the selection.
            refreshWorldRow();
        }
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
        if (node instanceof AbilitySlot slot) {
            return slot.tooltip();
        }
        if (node instanceof WorldRow row) {
            return row.tooltip();
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
        ProceduralArt.panel(g, MARGIN - 4, WORLD_TOP - 4, CONTENT_W + 8,
                SLOT_TOP + 2 * SLOT_H + SLOT_VGAP + 6 - (WORLD_TOP - 4));
        world.render(g);
        tier.render(g);
        g.setFont(Fonts.regular(11));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, abilityLine, MARGIN, ABILITY_BASELINE);
        for (AbilitySlot slot : slots) {
            if (slot.isVisible()) {
                slot.render(g);
            }
        }

        ProceduralArt.panel(g, MARGIN - 4, VIEW_TOP - 22, CONTENT_W + 8,
                VIEW_BOTTOM - VIEW_TOP + 26);
        g.setFont(Fonts.bold(13));
        g.setColor(ProceduralArt.accentColor(PALETTE));
        TextPainter.draw(g, strings.get(StringKey.BIRDS_PANEL), MARGIN,
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
            g.setColor(row.dimmed() ? SLOT_BLOCKED : ProceduralArt.TEXT_LIGHT);
        } else {
            g.setFont(Fonts.regular(11));
            g.setColor(row.dimmed() ? SLOT_BLOCKED : ProceduralArt.TEXT_MUTED);
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
     * One line of the scrolling panel: an ability, one of its details, a stat or one of the stat's
     * sources.
     *
     * @param id the stable identifier a test addresses the row by
     * @param label the translated label
     * @param value the translated value, empty when there is none
     * @param header whether the row heads an entry (rather than listing one of its details)
     * @param y the row's top edge in content space
     * @param dimmed whether the row is greyed out — an ability this run's rules would strip (D9)
     */
    public record Row(String id, String label, String value, boolean header, double y,
            boolean dimmed) {

        /**
         * A row that is not greyed out.
         *
         * @param id the row id
         * @param label the label
         * @param value the value
         * @param header whether it heads an entry
         * @param y the top edge
         */
        public Row(String id, String label, String value, boolean header, double y) {
            this(id, label, value, header, y, false);
        }
    }

    /** What a loadout chip stands for (D9). */
    public enum SlotRole {
        /** The one active ability, triggered with X, Shift or the right mouse button. */
        ACTIVE,
        /** One of the bird's passive slots. */
        PASSIVE,
        /** A passive the bird grants: free, and impossible to unequip. */
        INNATE
    }

    /**
     * One loadout chip: a slot with the ability in it, or the word for "nothing".
     *
     * <p>Activating it (Enter, Space or a click) steps to the next ability the slot may hold;
     * arrows are left to the focus ring, so moving between chips and changing one never happen on
     * the same key. An innate chip is disabled: it says what the bird grants and refuses to be
     * traded away.
     */
    public static final class AbilitySlot extends UiNode {

        private SlotRole role = SlotRole.ACTIVE;
        private String abilityId;
        private String label = "";
        private String value = "";
        private String tooltip = "";
        private boolean blocked;

        AbilitySlot() {
            setVisible(false);
        }

        /**
         * Points the chip at an ability.
         *
         * @param newRole what the slot is
         * @param newAbilityId the ability in it, or {@code null}
         * @param newLabel the translated slot label
         * @param newValue the translated ability name, or the word for an empty slot
         * @param newTooltip the hover text
         * @param isBlocked whether the run's rules would strip what is in the slot
         */
        void bind(SlotRole newRole, String newAbilityId, String newLabel, String newValue,
                String newTooltip, boolean isBlocked) {
            this.role = newRole;
            this.abilityId = newAbilityId;
            this.label = newLabel;
            this.value = newValue;
            this.tooltip = newTooltip;
            this.blocked = isBlocked;
        }

        /**
         * What the slot is.
         *
         * @return the role
         */
        public SlotRole role() {
            return role;
        }

        /**
         * The ability in the slot.
         *
         * @return the id, or {@code null} when the slot is empty
         */
        public String abilityId() {
            return abilityId;
        }

        /**
         * The slot label, as drawn.
         *
         * @return the text
         */
        public String label() {
            return label;
        }

        /**
         * The ability name, as drawn.
         *
         * @return the text, the word for "empty" when the slot holds nothing
         */
        public String value() {
            return value;
        }

        /**
         * Whether the run's rules would strip what the slot holds (D9).
         *
         * @return {@code true} when the ability is greyed out
         */
        public boolean isBlocked() {
            return blocked;
        }

        /**
         * The hover text.
         *
         * @return the tooltip
         */
        public String tooltip() {
            return tooltip;
        }

        @Override
        public void render(Graphics2D g) {
            int bx = (int) Math.round(x());
            int by = (int) Math.round(y());
            int bw = (int) Math.round(width());
            int bh = (int) Math.round(height());
            g.setColor(SLOT_BACK);
            g.fillRoundRect(bx, by, bw, bh, 8, 8);
            Stroke old = g.getStroke();
            g.setStroke(SWATCH_STROKE);
            if (blocked) {
                g.setColor(SLOT_BLOCKED);
            } else if (role == SlotRole.INNATE) {
                g.setColor(SLOT_FIXED);
            } else {
                g.setColor(isFocused() || isHovered() ? ProceduralArt.TEXT_LIGHT
                        : ProceduralArt.TEXT_MUTED);
            }
            g.drawRoundRect(bx, by, bw, bh, 8, 8);
            g.setStroke(old);
            g.setFont(Fonts.regular(9));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, label, bx + 6.0, by + 9.0);
            g.setFont(Fonts.bold(11));
            g.setColor(blocked ? SLOT_BLOCKED
                    : (abilityId == null ? ProceduralArt.TEXT_MUTED : ProceduralArt.TEXT_LIGHT));
            TextPainter.draw(g, value, bx + 6.0, by + bh - 5.0);
        }
    }

    /**
     * The world picker (M7): a {@link ListView} row stepping through the worlds, with a swatch
     * of the shown world's palette next to the label and a second line under the name — the
     * hazards it spawns, or how it is unlocked.
     *
     * <p>The arrows and the value sit exactly where {@link ListView} puts them, so its click
     * zones and its keyboard handling apply unchanged; only the drawing is this class's.
     */
    public static final class WorldRow extends ListView {

        private static final Color ARROW_ON = new Color(0xF4, 0xF8, 0xF8);
        private static final Color ARROW_OFF = new Color(0x6E, 0x7A, 0x7C);
        private static final Stroke FOCUS = new BasicStroke(2f);
        private static final int SWATCH_SIZE = 18;

        private final int[] arrowX = new int[3];
        private final int[] arrowY = new int[3];
        private WorldPaletteDef swatch;
        private String detail = "";
        private String tooltip = "";
        private boolean locked;
        private int fontSize = 14;

        WorldRow(String label, List<String> options, int selected) {
            super(label, options, selected);
        }

        /**
         * Points the row at the world it shows.
         *
         * @param palette the world's palette, or {@code null} for no swatch
         * @param newDetail the line under the name
         * @param isLocked whether the world is locked for the profile
         * @param tip the hover text
         */
        void bind(WorldPaletteDef palette, String newDetail, boolean isLocked, String tip) {
            this.swatch = palette;
            this.detail = newDetail == null ? "" : newDetail;
            this.locked = isLocked;
            this.tooltip = tip == null ? "" : tip;
        }

        @Override
        public void setFontSize(int size) {
            super.setFontSize(size);
            this.fontSize = size;
        }

        /**
         * The line under the world name.
         *
         * @return the text
         */
        public String detail() {
            return detail;
        }

        /**
         * Whether the shown world is locked for the profile.
         *
         * @return {@code true} when locked
         */
        public boolean isLocked() {
            return locked;
        }

        /**
         * The hover text.
         *
         * @return the tooltip
         */
        public String tooltip() {
            return tooltip;
        }

        @Override
        public void render(Graphics2D g) {
            double lineY = y() + 13;
            g.setFont(Fonts.regular(fontSize));
            g.setColor(isFocused() || isHovered() ? ProceduralArt.TEXT_LIGHT
                    : ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, label(), x(), TextPainter.centeredBaseline(g, lineY));
            double labelW = TextPainter.width(g, label());
            if (swatch != null) {
                int sx = (int) Math.round(x() + labelW + 8);
                int sy = (int) Math.round(lineY - SWATCH_SIZE / 2.0);
                g.setColor(new Color(WorldPaletteDef.rgb(swatch.skyTop())));
                g.fillRoundRect(sx, sy, SWATCH_SIZE, SWATCH_SIZE, 5, 5);
                g.setColor(new Color(WorldPaletteDef.rgb(swatch.pipe())));
                g.fillRect(sx + 3, sy + SWATCH_SIZE / 2, SWATCH_SIZE - 6, SWATCH_SIZE / 2 - 3);
                g.setColor(new Color(WorldPaletteDef.rgb(swatch.accent())));
                g.fillOval(sx + SWATCH_SIZE - 9, sy + 3, 5, 5);
                g.setColor(new Color(WorldPaletteDef.rgb(swatch.letterbox())));
                g.drawRoundRect(sx, sy, SWATCH_SIZE, SWATCH_SIZE, 5, 5);
            }
            double leftArrow = x() + width() * 0.46 + ARROW_WIDTH;
            double rightArrow = x() + width() - ARROW_WIDTH;
            boolean active = isEnabled() && options().size() > 1;
            g.setColor(active ? ARROW_ON : ARROW_OFF);
            triangle(g, leftArrow, lineY, -1);
            triangle(g, rightArrow, lineY, 1);
            g.setFont(Fonts.bold(fontSize));
            g.setColor(locked ? ProceduralArt.TEXT_MUTED : ProceduralArt.TEXT_LIGHT);
            TextPainter.draw(g, selectedOption(), (leftArrow + rightArrow) / 2,
                    TextPainter.centeredBaseline(g, lineY), Align.CENTER);
            if (!detail.isEmpty()) {
                g.setFont(Fonts.regular(10));
                g.setColor(locked ? SLOT_BLOCKED : ProceduralArt.TEXT_MUTED);
                TextPainter.draw(g, detail, x(), y() + height() - 5);
            }
            if (isFocused()) {
                Stroke old = g.getStroke();
                g.setStroke(FOCUS);
                g.setColor(ARROW_ON);
                g.drawRoundRect((int) Math.round(x() + width() * 0.46), (int) Math.round(y() + 1),
                        (int) Math.round(width() * 0.54), 24, 10, 10);
                g.setStroke(old);
            }
        }

        private void triangle(Graphics2D g, double tipX, double cy, int dir) {
            arrowX[0] = (int) Math.round(tipX);
            arrowY[0] = (int) Math.round(cy);
            arrowX[1] = (int) Math.round(tipX - dir * 7);
            arrowY[1] = (int) Math.round(cy - 6);
            arrowX[2] = (int) Math.round(tipX - dir * 7);
            arrowY[2] = (int) Math.round(cy + 6);
            g.fillPolygon(arrowX, arrowY, 3);
        }
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
