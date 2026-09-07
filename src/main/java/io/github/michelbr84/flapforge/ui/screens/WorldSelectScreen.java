package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.content.defs.WorldPaletteDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.SelectionManager;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
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
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The world picker of the home hub (D17, M7, M10): one card per world the content ships, in
 * order, with the palette as its art, the hazards or the way in as its subtitle and the price
 * as its badge when it is locked; a difficulty row under the cards; and a description line for
 * the focused world.
 *
 * <p>Activating an owned card writes the profile's selection through the
 * {@link SelectionManager} and pops back to the hub, whose plaque, backdrop and START RUN
 * subtitle follow the selection. A locked card is refused with a toast: worlds are bought in the
 * shop's Worlds tab, which stays the only purchase path (E19). The difficulty row writes the
 * tier the same way {@link BirdSelectionScreen} does, snapping back when the tier is locked.
 *
 * <p>Built without a selection writer (a bare screen stack in a test) the screen shows the same
 * cards and pops on activation without writing anything.
 */
public final class WorldSelectScreen implements Screen {

    /** Top of the card column. */
    public static final int GRID_TOP = 56;
    /** Height of one world card. */
    public static final int CARD_H = 52;
    /** Gap between cards. */
    public static final int CARD_GAP = 6;
    /** Left edge of the content. */
    public static final int CONTENT_X = 24;
    /** Width of the content. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * CONTENT_X;
    /** Top of the difficulty row. */
    public static final int TIER_TOP = 362;
    /** Height of the difficulty row. */
    public static final int TIER_H = 30;
    /** Baseline of the description line. */
    public static final int DESCRIPTION_BASELINE = 414;
    /** Top of the Back button. */
    public static final int FOOTER_TOP = Playfield.HEIGHT - 56;
    /** Height of the Back button. */
    public static final int FOOTER_BUTTON_H = 42;

    private static final int TITLE_BASELINE = 40;
    private static final int WALLET_W = 130;
    private static final int PANEL_X = 12;
    private static final int PANEL_TOP = TIER_TOP - 10;
    private static final int PANEL_BOTTOM = DESCRIPTION_BASELINE + 12;

    private final ScreenManager screens;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final SelectionManager selection;
    private final ToastLayer toasts;
    private final FocusRing ring = new FocusRing();
    private final CardGrid grid = new CardGrid();
    private final ListView tier;
    private final Button back;
    private final CurrencyDisplay wallet = new CurrencyDisplay();
    private final Tooltip tooltip = new Tooltip();
    private final List<String> worldIds = new ArrayList<>();
    private final List<String> tierIds = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();
    private WorldPalette palette = WorldPalette.GREEN_FIELDS;
    private String description = "";
    private String descriptionShown = "";
    private String descriptionOf = "";
    private String shownLanguage;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services; its profile is the one edited
     */
    public WorldSelectScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context").screens(),
                context.strings() != null ? context.strings() : Strings.active(),
                context.content(), context.profile(),
                context.canProgress()
                        ? new SelectionManager(context.progression(), context::saveProfile) : null,
                context.toasts());
    }

    /**
     * Creates a stand-alone screen (tests and tools).
     *
     * @param screens the screen stack
     * @param strings the string table its labels come from
     * @param content the loaded content
     * @param profile the profile to read and write
     * @param selection the selection writer, or {@code null} for a screen that only pops
     * @param toasts the toast queue, or {@code null} for one of its own
     */
    public WorldSelectScreen(ScreenManager screens, Strings strings, GameContent content,
            PlayerProfile profile, SelectionManager selection, ToastLayer toasts) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.selection = selection;
        this.toasts = toasts == null ? new ToastLayer() : toasts;

        if (content.has(GameContent.WORLDS)) {
            for (WorldDef def : content.worlds()) {
                worldIds.add(def.id());
                CardGrid.Card card = new CardGrid.Card(def.id(), "", null);
                card.setOnAction(() -> activate(def.id()));
                Swatch swatch = new Swatch(def.palette());
                swatches.add(swatch);
                card.setArt(swatch);
                grid.add(card);
            }
        }
        grid.setColumns(1);
        grid.setCellHeight(CARD_H);
        grid.setGap(0, CARD_GAP);
        grid.setBounds(CONTENT_X, GRID_TOP, CONTENT_W,
                CardGrid.heightFor(grid.size(), 1, CARD_H, CARD_GAP));
        grid.layout();
        grid.registerFocusables(ring);

        tier = new ListView("", tierOptions(), tierIndex());
        tier.setWrapping(false);
        tier.setFontSize(14);
        tier.setBounds(CONTENT_X, TIER_TOP, CONTENT_W, TIER_H);
        tier.setOnChange(this::selectTier);
        ring.add(tier);

        back = new Button("", screens::pop);
        back.setFontSize(16);
        back.setBounds(CONTENT_X, FOOTER_TOP, CONTENT_W, FOOTER_BUTTON_H);
        ring.add(back);

        wallet.setBounds(Playfield.WIDTH - WALLET_W - 14.0, 14, WALLET_W, 26);
        wallet.setAlign(Align.RIGHT);
        wallet.setAmountNow(coins());
        shownLanguage = strings.language();
        refreshTexts();
    }

    // ------------------------------------------------------------------ state

    private long coins() {
        return Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
    }

    private boolean owned(WorldDef def) {
        return profile.isUnlocked(def.unlockableId());
    }

    /**
     * The world the description line and the tooltip are about: the focused card, falling back
     * to the selected world.
     *
     * @return the world id, or {@code null} when the content ships none
     */
    public String currentWorldId() {
        UiNode focused = ring.focused();
        if (focused instanceof CardGrid.Card card) {
            return card.id();
        }
        if (worldIds.contains(profile.selected.worldId)) {
            return profile.selected.worldId;
        }
        return worldIds.isEmpty() ? null : worldIds.get(0);
    }

    private void activate(String worldId) {
        WorldDef def = content.worlds().get(worldId);
        if (!owned(def)) {
            toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED,
                    strings.get(StringKey.COMMON_LOCKED)), Toast.Kind.WARNING);
            return;
        }
        if (selection != null && !selection.selectWorld(profile, worldId, content)) {
            toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED,
                    strings.get(StringKey.COMMON_LOCKED)), Toast.Kind.WARNING);
            return;
        }
        UiCues.back();
        screens.pop();
    }

    /**
     * Selects a tier from the row, refusing one the player has not unlocked.
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

    private int tierIndex() {
        int index = tierIds.indexOf(profile.selected.tierId);
        return index < 0 ? 0 : index;
    }

    private WorldDef selectedWorld() {
        String id = profile.selected.worldId;
        if (content.has(GameContent.WORLDS) && content.worlds().contains(id)) {
            return content.worlds().get(id);
        }
        return null;
    }

    // ------------------------------------------------------------------ accessors

    /**
     * The world cards, one per shipped world in content order.
     *
     * @return the grid
     */
    public CardGrid worldGrid() {
        return grid;
    }

    /**
     * The world ids in card order.
     *
     * @return the ids
     */
    public List<String> worldIds() {
        return List.copyOf(worldIds);
    }

    /**
     * The difficulty row.
     *
     * @return the row
     */
    public ListView tierList() {
        return tier;
    }

    /**
     * The tier ids in row order.
     *
     * @return the ids
     */
    public List<String> tierIds() {
        return List.copyOf(tierIds);
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
     * @return the readout
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
     * The description line under the difficulty row, as drawn.
     *
     * @return the text
     */
    public String descriptionLine() {
        return description;
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
     * The palette of the selected world, which the backdrop and the letterbox follow.
     *
     * @return the palette
     */
    public WorldPalette palette() {
        return palette;
    }

    /** Re-reads every label from the string table (a language switch, D25). */
    public void refreshTexts() {
        wallet.setFormat(strings.get(StringKey.HUD_COINS));
        back.setText(strings.get(StringKey.COMMON_BACK));
        tier.setLabel(strings.get(StringKey.WORLD_SELECT_TIER));
        shownLanguage = strings.language();
        refreshState();
    }

    /**
     * Rebuilds every card, the difficulty row, the wallet and the description from the profile:
     * on entry, after every selection and after a language switch.
     */
    public void refreshState() {
        for (int i = 0; i < worldIds.size(); i++) {
            WorldDef def = content.worlds().get(worldIds.get(i));
            CardGrid.Card card = grid.cards().get(i);
            boolean owned = owned(def);
            long price = UnlockEvaluator.priceOf(def.unlock());
            String name = ProgressionText.name(strings, ContentKind.WORLD, def.id());
            card.setTitle(strings.format(StringKey.WORLD_SELECT_CARD, def.order(), name));
            card.setLocked(!owned);
            card.setSelected(def.id().equals(profile.selected.worldId));
            String detail = owned
                    ? strings.format(StringKey.BIRDS_WORLD_HAZARDS,
                            ProgressionText.hazards(strings, def))
                    : strings.format(StringKey.BIRDS_WORLD_LOCKED,
                            ProgressionText.unlockText(strings, content, def.unlock(), profile));
            card.setSubtitle(detail);
            card.setBadge(!owned && price >= 0 ? Long.toString(price) : "", !owned && price >= 0);
            String tip = ProgressionText.description(strings, ContentKind.WORLD, def.id());
            card.setTooltip(owned ? tip : tip + " - " + detail);
        }
        tier.setOptions(tierOptions());
        tier.selectQuietly(tierIndex());
        wallet.setAmount(coins());
        WorldDef selected = selectedWorld();
        palette = selected == null ? WorldPalette.GREEN_FIELDS
                : WorldPalette.from(selected.palette());
        refreshDescription();
    }

    private void refreshDescription() {
        String id = currentWorldId();
        String next = id == null ? "" : ProgressionText.description(strings, ContentKind.WORLD, id);
        if (!next.equals(description)) {
            description = next;
            descriptionShown = "";
            descriptionOf = "";
        }
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        CardGrid.Card selected = grid.card(profile.selected.worldId);
        if (selected != null) {
            ring.focus(selected);
        } else {
            ring.focusFirst();
        }
        tooltip.hide();
        wallet.setAmountNow(coins());
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else {
            refreshState();
        }
        screens.setLetterboxRgb(palette.letterbox());
    }

    @Override
    public void tick(InputFrame input) {
        toasts.tick();
        wallet.tick();
        ring.handle(input);
        tier.tick(input);
        refreshDescription();
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

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, palette);
        g.setFont(Fonts.bold(26));
        TextPainter.drawOutlined(g, strings.get(StringKey.WORLD_SELECT_TITLE), CONTENT_X,
                TITLE_BASELINE, Align.LEFT, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(palette), 2);
        wallet.render(g);
        grid.render(g);
        ProceduralArt.panel(g, PANEL_X, PANEL_TOP, Playfield.WIDTH - 2 * PANEL_X,
                PANEL_BOTTOM - PANEL_TOP);
        tier.render(g);
        g.setFont(Fonts.regular(12));
        g.setColor(ProceduralArt.TEXT_MUTED);
        if (!description.equals(descriptionOf) || descriptionShown.isEmpty()) {
            // Measured only when the description or the language changed.
            descriptionShown = TextPainter.ellipsise(g, description, CONTENT_W);
            descriptionOf = description;
        }
        TextPainter.draw(g, descriptionShown, CONTENT_X, DESCRIPTION_BASELINE);
        back.render(g);
        tooltip.render(g);
        toasts.render(g);
    }

    /** The art of a world card: its sky over its pipe colour, with the accent as a sun. */
    private static final class Swatch implements CardGrid.ArtPainter {

        private final Color sky;
        private final Color pipe;
        private final Color accent;
        private final Color edge;

        Swatch(WorldPaletteDef palette) {
            sky = new Color(WorldPaletteDef.rgb(palette.skyTop()));
            pipe = new Color(WorldPaletteDef.rgb(palette.pipe()));
            accent = new Color(WorldPaletteDef.rgb(palette.accent()));
            edge = new Color(WorldPaletteDef.rgb(palette.letterbox()));
        }

        @Override
        public void paint(Graphics2D g, CardGrid.Card card, double cx, double cy, double size) {
            int s = (int) Math.round(size);
            int sx = (int) Math.round(cx - size / 2);
            int sy = (int) Math.round(cy - size / 2);
            g.setColor(sky);
            g.fillRoundRect(sx, sy, s, s, 6, 6);
            g.setColor(pipe);
            g.fillRect(sx + 3, sy + s / 2, s - 6, s / 2 - 3);
            g.setColor(accent);
            g.fillOval(sx + s - s / 3 - 3, sy + 4, s / 3, s / 3);
            g.setColor(edge);
            g.drawRoundRect(sx, sy, s - 1, s - 1, 6, 6);
        }
    }
}
