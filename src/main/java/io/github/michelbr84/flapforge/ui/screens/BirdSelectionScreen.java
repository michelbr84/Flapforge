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
import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatBreakdown;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.DailyChallenge;
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
import io.github.michelbr84.flapforge.render.Overscan;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.AbilityCard;
import io.github.michelbr84.flapforge.ui.component.AttributeBadge;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.Carousel;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.CtaButton;
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.HubHeader;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import io.github.michelbr84.flapforge.ui.component.ListView;
import io.github.michelbr84.flapforge.ui.component.NavBar;
import io.github.michelbr84.flapforge.ui.component.NavButton;
import io.github.michelbr84.flapforge.ui.component.SectionNav;
import io.github.michelbr84.flapforge.ui.layout.LayoutMetrics;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Tooltip;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The bird selection (D17, M4/M5, redesigned in M11): the bird the next run flies with, shown as
 * a character rather than as a row of a settings list.
 *
 * <p>The screen reads top to bottom as one sentence. The {@link HubHeader} names it and shows the
 * wallet. The {@link BirdHero} under it is the bird being browsed, large, bobbing on an anvil on
 * a floating island, with its name, what it is, and its three headline attributes
 * ({@link BirdAttributes}). The {@link Carousel} below is the whole roster in one scrolling row —
 * a locked tile carries its padlock and its price, and nudges the padlock when it is tapped. Then
 * the palette swatches of the browsed bird, the one-line {@link RunSetupBar} summarising the run
 * ("Green Fields · Normal · Standard"), the loadout as {@link AbilityCard}s with a
 * <em>See details</em> next to them, one gold {@link CtaButton} naming the single thing to do
 * (<em>Use Ironbeak</em>, <em>Buy · 150 coins</em>, <em>Bird selected</em>, <em>Locked · Play 3
 * runs</em>), and the hub's own five-item {@link NavBar} with Birds on the gold plate: on a
 * section screen the primary item is where you are.
 *
 * <p>The bands lay out against the surface's {@link LayoutMetrics}: the header pinned to the
 * content top, the five-item navigation pinned to the bottom, and the roster and loadout stack
 * between them at its reference heights. A taller surface gives the freed room to the hero's
 * sky — the painted hero stays its size and centres in the stretched band, and nothing opens a
 * gap above the navigation. At the classic 420x640 surface every band lands on the constant it
 * always had.
 *
 * <p>Nothing was taken away. The world, the tier and the run mode are the same three rows they
 * always were — with the same refusals, the same snap-back and the same daily settlement (E27) —
 * on the {@link RunSetupPanel} the bar opens; the ability list and the stat breakdown are the same
 * rows, with the same ids, in the {@link DetailsPanel} that <em>See details</em> opens, where they
 * finally have the height to be read. Each panel owns its own {@link FocusRing} and is the ring
 * taking input while it is up, so nothing under the veil can be hovered, clicked or reached with
 * an arrow; {@code Esc} closes the panel before it leaves the screen.
 *
 * <p><b>What the bird selection writes.</b> Activating an owned tile or the call to action writes
 * {@code profile.selected} through {@link SelectionManager} and saves at once; Buy pays for a
 * locked bird through {@link UnlockManager}, which is the only path that grants a {@code purchase}
 * branch (D13); a swatch writes the palette; the three rows write the world and the tier and
 * refuse what the profile has not unlocked; a chip cycles the loadout. Under a settled daily the
 * world and tier rows go read-only and show the pick's own world and tier, because that is what
 * {@code DailyRunSource} will play whatever the rows said.
 *
 * <p><b>Motion and accessibility.</b> Everything moves on the tick, never on the clock: the bob
 * and the wing beat are the hub's triangle waves, a bird change slides the portraits over
 * {@value BirdHero#SLIDE_TICKS} ticks and the carousel eases over
 * {@value Carousel#SCROLL_TICKS}, and the selected tile and the call to action pulse gold over 90.
 * Reduce flashing caps the three glows and leaves the motion alone — the hub draws the same line
 * with its own bob — and high contrast reaches the plates, the role strokes and the island
 * outline through {@link io.github.michelbr84.flapforge.render.Accessibility}. The screen itself
 * adds nothing to a steady frame: the ramps and strokes are constants, the swatch and bar colours
 * are cached when they are bound, and every measured string is cached on its text, its room and
 * the text scale, so what a frame costs is what Java2D charges for the plates. Menu frames are
 * not held to the game frame's 24 KiB budget — this one measures near the hub's, and
 * {@code ProceduralRenderTest} pins it under a menu budget so a stray {@code new Color} in a
 * render method shows up.
 */
public final class BirdSelectionScreen implements Screen {

    /** How many swatches the row can hold (the widest bird ships four palettes). */
    public static final int MAX_SWATCHES = 6;

    /** Side margin of everything on the screen. */
    private static final int MARGIN = 12;
    /** Width of the content column. */
    private static final int CONTENT_W = Playfield.WIDTH - 2 * MARGIN;
    /** Chips the loadout row holds: the active slot, up to four passive slots and one innate. */
    private static final int MAX_SLOTS = 6;
    /** Highest number of passive slots any bird plus the E3 bonus can reach. */
    private static final int MAX_PASSIVE_SLOTS = 4;
    /** Columns of the loadout row. */
    private static final int SLOT_COLUMNS = 3;
    /** Characters a detail row fits before it is wrapped onto the next one. */
    private static final int WRAP_CHARS = 62;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int SWATCH = 32;
    private static final int SWATCH_TOP = 326;
    private static final int SWATCH_LEFT = 96;
    private static final int SWATCH_STEP = 36;
    private static final int PALETTE_LABEL_BASELINE = 347;
    private static final int CAROUSEL_TOP = 238;
    private static final int CAROUSEL_H = 84;
    private static final int BAR_TOP = 362;
    private static final int BAR_H = 36;
    private static final int ABILITY_PANEL_TOP = 402;
    private static final int ABILITY_PANEL_H = 104;
    private static final int ABILITY_BASELINE = 418;
    private static final int DETAILS_BUTTON_TOP = 402;
    private static final int DETAILS_BUTTON_W = 152;
    private static final int DETAILS_BUTTON_H = 28;
    private static final int SLOT_TOP = 426;
    private static final int SLOT_H = 34;
    private static final int SLOT_VGAP = 6;
    private static final int SLOT_HGAP = 6;
    private static final int CTA_TOP = 512;
    private static final int CTA_W = 340;
    private static final int CTA_H = 44;
    /** Rows above the navigation band at the reference surface, the stack's classic extent. */
    private static final int STRETCH_H = SectionNav.TOP;
    private static final Color DIM = new Color(0, 0, 0, 0x8C);
    private static final Stroke SWATCH_STROKE = new BasicStroke(2f);

    /** Which panel is up, if any. */
    private enum PanelKind {
        /** The screen itself. */
        NONE,
        /** The world, tier and mode rows. */
        RUN_SETUP,
        /** The ability list and the stat breakdown. */
        DETAILS
    }

    private final ScreenManager screens;
    private final GameContext context;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final SelectionManager selection;
    private final UnlockManager unlocks;
    private final UnlockEvaluator evaluator;
    private final ToastLayer toasts;
    private final TimeSource clock;
    private final Runnable save;
    private final FocusRing ring = new FocusRing();
    private final HubHeader header;
    private final BirdHero hero = new BirdHero();
    private final Carousel carousel = new Carousel();
    private final List<Swatch> swatches = new ArrayList<>();
    private final List<AbilitySlot> slots = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Tooltip tooltip = new Tooltip();
    private final CtaButton cta;
    private final RunSetupBar bar = new RunSetupBar();
    private final ChipButton seeDetails = new ChipButton();
    private final WorldRow world;
    private final WorldRow tier;
    private final WorldRow mode;
    private final RunSetupPanel runSetup;
    private final DetailsPanel details;
    private final NavBar nav = new NavBar();
    private final Ellipse2D.Double coinScratch = new Ellipse2D.Double();
    private final IconPainter coinIcon = AbilityIcons.coin(coinScratch);
    private final List<String> tierIds = new ArrayList<>();
    private final List<String> worldIds = new ArrayList<>();
    private final List<RunMode> modes = new ArrayList<>();
    private RunMode runMode = RunMode.STANDARD;
    private DailyChallenge.Pick dailyPick;
    private PanelKind panel = PanelKind.NONE;
    private String currentBirdId;
    private String shownLanguage;
    private String abilityLine = "";
    private RuleSet previewRules = RuleSet.EMPTY;
    private int surfaceTop;
    private int heroShift;
    private int carouselTop;
    private int swatchTop;
    private int paletteLabelBaseline;
    private int barTop;
    private int abilityTop;
    private int abilityBaseline;
    private int detailsTop;
    private int slotTop;
    private int ctaTop;
    private LayoutMetrics laidOut;
    private double contentHeight;
    private long ticks;
    private double prevBob;
    private double bob;
    private boolean reduceShown;
    private String ctaTooltip = "";

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services; its profile is the one edited
     */
    public BirdSelectionScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context"), context.screens(),
                context.strings() != null ? context.strings() : Strings.active(),
                context.content(), context.profile(),
                context.canProgress()
                        ? new SelectionManager(context.progression(), context::saveProfile) : null,
                context.canProgress()
                        ? new UnlockManager(context.progression(), context::saveProfile) : null,
                context.toasts(), context.timeSource(),
                context.canProgress() ? context::saveProfile : null);
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
        this(null, screens, strings, content, profile, selection, unlocks, toasts, null, null);
    }

    /**
     * Creates the screen with the clock the daily needs (M9).
     *
     * <p>A screen built without a time source cannot know what UTC day it is, so it offers
     * Standard and Seeded and leaves the Daily entry out of the mode picker altogether rather
     * than showing an entry that could not say what it would play (D28).
     *
     * @param screens the screen stack
     * @param strings the string table
     * @param content the loaded content
     * @param profile the profile to read and write
     * @param selection the selection writer, or {@code null} for a read-only screen
     * @param unlocks the purchase path, or {@code null} for a screen that cannot buy
     * @param toasts the toast queue, or {@code null} for one of its own
     * @param clock the time source the daily's date comes from, or {@code null} for no daily
     * @param save flushes the profile after the daily pick is written (E27), or {@code null}
     */
    public BirdSelectionScreen(ScreenManager screens, Strings strings, GameContent content,
            PlayerProfile profile, SelectionManager selection, UnlockManager unlocks,
            ToastLayer toasts, TimeSource clock, Runnable save) {
        this(null, screens, strings, content, profile, selection, unlocks, toasts, clock, save);
    }

    private BirdSelectionScreen(GameContext context, ScreenManager screens, Strings strings,
            GameContent content, PlayerProfile profile, SelectionManager selection,
            UnlockManager unlocks, ToastLayer toasts, TimeSource clock, Runnable save) {
        this.context = context;
        this.clock = clock;
        this.save = save;
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.selection = selection;
        this.unlocks = unlocks;
        this.evaluator = UnlockEvaluator.of(content);
        this.toasts = toasts == null ? new ToastLayer() : toasts;
        this.currentBirdId = profile.selected.birdId;

        header = new HubHeader(context != null ? this::openShop : null);
        header.setOutline(ProceduralArt.letterboxColor(PALETTE));
        header.registerFocusables(ring);
        hero.setOutline(ProceduralArt.letterboxColor(PALETTE));

        for (BirdDef bird : content.birds()) {
            Carousel.Tile tile = new Carousel.Tile(bird.id(), "", null);
            tile.setOnAction(() -> activate(bird.id()));
            tile.setArt((g, c, cx, cy, size) -> paintPortrait(g, bird, cx, cy, size));
            carousel.add(tile);
        }
        carousel.setTileSize(Carousel.DEFAULT_TILE_WIDTH, Carousel.DEFAULT_TILE_HEIGHT);
        carousel.setGap(Carousel.DEFAULT_GAP);
        carousel.registerFocusables(ring);

        for (int i = 0; i < MAX_SWATCHES; i++) {
            Swatch swatch = new Swatch();
            swatch.setBounds(SWATCH_LEFT + i * (double) SWATCH_STEP, swatchTop, SWATCH, SWATCH);
            swatches.add(swatch);
            ring.add(swatch);
        }

        bar.setBounds(MARGIN, barTop, CONTENT_W, BAR_H);
        bar.setOnAction(this::openRunSetup);
        ring.add(bar);

        for (int i = 0; i < MAX_SLOTS; i++) {
            AbilitySlot slot = new AbilitySlot();
            int column = i % SLOT_COLUMNS;
            int row = i / SLOT_COLUMNS;
            double width = (CONTENT_W - (SLOT_COLUMNS - 1.0) * SLOT_HGAP) / SLOT_COLUMNS;
            slot.setBounds(MARGIN + 4 + column * (width + SLOT_HGAP),
                    slotTop + row * (SLOT_H + SLOT_VGAP), width, SLOT_H);
            int index = i;
            slot.setOnAction(() -> cycleSlot(index));
            slots.add(slot);
            ring.add(slot);
        }

        seeDetails.setBounds(Playfield.WIDTH - MARGIN - DETAILS_BUTTON_W, detailsTop,
                DETAILS_BUTTON_W, DETAILS_BUTTON_H);
        seeDetails.setOnAction(this::openDetails);
        ring.add(seeDetails);

        cta = new CtaButton("", this::activateCta);
        cta.setBounds((Playfield.WIDTH - CTA_W) / 2.0, ctaTop, CTA_W, CTA_H);
        ring.add(cta);

        world = new WorldRow("", worldOptions(), worldIndex());
        world.setWrapping(false);
        world.setFontSize(14);
        world.setOnChange(this::selectWorld);
        tier = new WorldRow("", tierOptions(), tierIndex());
        tier.setWrapping(false);
        tier.setFontSize(14);
        tier.setOnChange(this::selectTier);
        mode = new WorldRow("", modeOptions(), modeIndex());
        mode.setWrapping(false);
        mode.setFontSize(14);
        mode.setOnChange(this::selectMode);
        runSetup = new RunSetupPanel(world, tier, mode, this::closeRunSetup);
        details = new DetailsPanel(this::closeDetails);

        relayout();

        SectionNav.build(nav, SectionNav.BIRDS, new SectionNav.Routes(this::openShop,
                this::focusSelectedTile, this::openHome, this::openForge, this::openGoals),
                screens.metrics());
        nav.button(SectionNav.SHOP).setEnabled(context != null);
        nav.button(SectionNav.FORGE).setEnabled(context != null);
        nav.button(SectionNav.GOALS).setEnabled(context != null);
        nav.registerFocusables(ring);

        reduceShown = !ParticleSystem.defaultReduceFlashing();
        applyReduceFlashing(ParticleSystem.defaultReduceFlashing());
        header.display().setAmountNow(coins());
        shownLanguage = strings.language();
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
     * the roster, the palette row, the run-setup bar, the loadout and the call to action keeping
     * their reference heights and their classic gaps one below the other, and the freed room
     * going to the hero band above the roster — the painted hero stays its size and centres in
     * the stretched sky. At the classic 420x640 surface there is no extra room and every band
     * lands on the constant it was laid out with before the elastic surface existed.
     */
    private void relayout() {
        LayoutMetrics metrics = screens.metrics();
        surfaceTop = metrics.contentTop();
        int extra = Math.max(0, metrics.aboveNavHeight() - STRETCH_H);
        heroShift = extra / 2;
        carouselTop = surfaceTop + CAROUSEL_TOP + extra;
        swatchTop = surfaceTop + SWATCH_TOP + extra;
        paletteLabelBaseline = surfaceTop + PALETTE_LABEL_BASELINE + extra;
        barTop = surfaceTop + BAR_TOP + extra;
        abilityTop = surfaceTop + ABILITY_PANEL_TOP + extra;
        abilityBaseline = surfaceTop + ABILITY_BASELINE + extra;
        detailsTop = surfaceTop + DETAILS_BUTTON_TOP + extra;
        slotTop = surfaceTop + SLOT_TOP + extra;
        ctaTop = surfaceTop + CTA_TOP + extra;
        header.setBounds(0, surfaceTop, Playfield.WIDTH, HubHeader.HEIGHT);
        header.chip().setBounds(HubHeader.CHIP_X, surfaceTop + HubHeader.CHIP_Y,
                HubHeader.CHIP_W, HubHeader.CHIP_H);
        carousel.setBounds(4, carouselTop, Playfield.WIDTH - 8, CAROUSEL_H);
        carousel.layout();
        for (int i = 0; i < swatches.size(); i++) {
            swatches.get(i).setBounds(SWATCH_LEFT + i * (double) SWATCH_STEP, swatchTop,
                    SWATCH, SWATCH);
        }
        bar.setBounds(MARGIN, barTop, CONTENT_W, BAR_H);
        double slotWidth = (CONTENT_W - (SLOT_COLUMNS - 1.0) * SLOT_HGAP) / SLOT_COLUMNS;
        for (int i = 0; i < slots.size(); i++) {
            int column = i % SLOT_COLUMNS;
            int row = i / SLOT_COLUMNS;
            slots.get(i).setBounds(MARGIN + 4 + column * (slotWidth + SLOT_HGAP),
                    slotTop + row * (SLOT_H + SLOT_VGAP), slotWidth, SLOT_H);
        }
        seeDetails.setBounds(Playfield.WIDTH - MARGIN - DETAILS_BUTTON_W, detailsTop,
                DETAILS_BUTTON_W, DETAILS_BUTTON_H);
        cta.setBounds((Playfield.WIDTH - CTA_W) / 2.0, ctaTop, CTA_W, CTA_H);
        laidOut = metrics;
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
     * The bird the hero, the palette row and the call to action are about: the tile the player
     * is browsing, which is the selected bird on entry.
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
     * The roster: one scrolling row of tiles, one per bird.
     *
     * @return the carousel
     */
    public Carousel roster() {
        return carousel;
    }

    /**
     * The hero: the bird being browsed, large.
     *
     * @return the hero
     */
    public BirdHero hero() {
        return hero;
    }

    /**
     * The header band.
     *
     * @return the header
     */
    public HubHeader header() {
        return header;
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
     * The one call to action, which is both the Select and the Buy of the old layout: it names
     * the single thing the browsed bird needs (use it, buy it, or what opens it).
     *
     * @return the button
     */
    public CtaButton cta() {
        return cta;
    }

    /**
     * The call to action, under the name the Select button had.
     *
     * @return the button
     */
    public Button selectButton() {
        return cta;
    }

    /**
     * The call to action, under the name the Buy button had.
     *
     * @return the button
     */
    public Button buyButton() {
        return cta;
    }

    /**
     * The tier picker, on the run-setup panel.
     *
     * @return the row
     */
    public ListView tierList() {
        return tier;
    }

    /**
     * The world picker (M7), on the run-setup panel.
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
     * The run-mode picker (M9), on the run-setup panel.
     *
     * @return the row
     */
    public WorldRow modeList() {
        return mode;
    }

    /**
     * The modes the picker steps through: Standard and Seeded always, Daily when the screen was
     * given a clock.
     *
     * @return an unmodifiable snapshot
     */
    public List<RunMode> modes() {
        return List.copyOf(modes);
    }

    /**
     * The mode Play would start.
     *
     * @return the mode
     */
    public RunMode selectedMode() {
        return runMode;
    }

    /**
     * The line under the mode name, as drawn.
     *
     * @return the text
     */
    public String modeDetail() {
        return mode.detail();
    }

    /**
     * Today's daily pick, once the mode row has shown it (E27).
     *
     * @return the pick, or {@code null} while the row shows another mode
     */
    public DailyChallenge.Pick dailyPick() {
        return dailyPick;
    }

    /**
     * The Play item of the navigation, which starts the run the screen is configuring (D17).
     *
     * @return the item
     */
    public NavButton playButton() {
        return nav.button(SectionNav.PLAY);
    }

    /**
     * The bottom navigation, with Birds on the gold plate.
     *
     * @return the bar
     */
    public NavBar nav() {
        return nav;
    }

    /**
     * The bar summarising the run, which opens the run-setup panel.
     *
     * @return the bar
     */
    public UiNode runSetupBar() {
        return bar;
    }

    /**
     * The run in one line, as the bar draws it.
     *
     * @return the text
     */
    public String runSetupText() {
        return bar.line1();
    }

    /**
     * The entry that opens the ability list and the stat breakdown.
     *
     * @return the node
     */
    public UiNode seeDetailsButton() {
        return seeDetails;
    }

    /**
     * The Done button of the run-setup panel.
     *
     * @return the button
     */
    public Button runSetupDoneButton() {
        return runSetup.doneButton();
    }

    /**
     * The Done button of the details panel.
     *
     * @return the button
     */
    public Button detailsDoneButton() {
        return details.doneButton();
    }

    /**
     * How far the details panel is scrolled.
     *
     * @return the offset in logical pixels
     */
    public double detailsScroll() {
        return details.scroll();
    }

    /**
     * The wallet readout.
     *
     * @return the display
     */
    public CurrencyDisplay walletDisplay() {
        return header.display();
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
     * The focus ring taking input right now: the screen's own, or the one of whichever panel is
     * up.
     *
     * @return the active ring
     */
    public FocusRing focusRing() {
        switch (panel) {
            case RUN_SETUP:
                return runSetup.ring();
            case DETAILS:
                return details.ring();
            default:
                return ring;
        }
    }

    /**
     * The screen's own focus ring, whether or not a panel is up.
     *
     * @return the ring
     */
    public FocusRing mainRing() {
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

    // ------------------------------------------------------------------ panels

    /** Opens the run-setup panel: the world, the tier and the run mode. */
    public void openRunSetup() {
        if (panel == PanelKind.RUN_SETUP) {
            return;
        }
        closeDetails();
        panel = PanelKind.RUN_SETUP;
        clearHover();
        runSetup.setOpen(true);
    }

    /** Closes the run-setup panel and puts the focus back on the bar that opened it. */
    public void closeRunSetup() {
        if (panel != PanelKind.RUN_SETUP) {
            return;
        }
        runSetup.setOpen(false);
        panel = PanelKind.NONE;
        refreshSetupBar();
        tooltip.hide();
        ring.focus(bar);
    }

    /**
     * Whether the run-setup panel is up.
     *
     * @return {@code true} while it is shown
     */
    public boolean isRunSetupOpen() {
        return panel == PanelKind.RUN_SETUP;
    }

    /** Opens the details panel: the ability list and the stat breakdown. */
    public void openDetails() {
        if (panel == PanelKind.DETAILS) {
            return;
        }
        closeRunSetup();
        panel = PanelKind.DETAILS;
        clearHover();
        details.setOpen(true);
    }

    /** Closes the details panel and puts the focus back on the entry that opened it. */
    public void closeDetails() {
        if (panel != PanelKind.DETAILS) {
            return;
        }
        details.setOpen(false);
        panel = PanelKind.NONE;
        tooltip.hide();
        ring.focus(seeDetails);
    }

    /**
     * Whether the details panel is up.
     *
     * @return {@code true} while it is shown
     */
    public boolean isDetailsOpen() {
        return panel == PanelKind.DETAILS;
    }

    /**
     * Clears the hover flag of every node of the screen's own ring: the flags are refreshed only
     * while a ring handles input, so a node hovered when a panel opens would keep its hover fill
     * under the veil.
     */
    private void clearHover() {
        for (UiNode node : ring.nodes()) {
            node.setHovered(false);
        }
    }

    // ------------------------------------------------------------------ actions

    /**
     * What activating a tile does: select an owned bird, or nudge the padlock of a locked one and
     * leave it to the call to action, which now says what opens it.
     *
     * @param birdId the bird id
     */
    private void activate(String birdId) {
        browse(birdId);
        if (profile.isUnlocked(BirdDef.NAMESPACE + birdId)) {
            selectBird(birdId);
            return;
        }
        Carousel.Tile tile = carousel.card(birdId);
        if (tile != null) {
            tile.nudge();
        }
    }

    /**
     * Points the hero and the call to action at another bird, sliding the portraits in the
     * direction the roster moved.
     *
     * <p>The guard is what keeps a tile <em>click</em> from sliding twice: activating a tile
     * routes through here and the focus change the same click causes arrives in the same tick.
     *
     * @param birdId the bird id
     */
    private void browse(String birdId) {
        if (birdId == null || birdId.equals(currentBirdId)) {
            return;
        }
        int from = carousel.indexOf(currentBirdId);
        int to = carousel.indexOf(birdId);
        currentBirdId = birdId;
        refreshCurrent(from < 0 || to < 0 ? 0 : Integer.signum(to - from));
    }

    /** What the one call to action does: use the browsed bird, or buy it. */
    private void activateCta() {
        if (profile.isUnlocked(BirdDef.NAMESPACE + currentBirdId)) {
            selectCurrent();
        } else {
            buyCurrent();
        }
    }

    /** Puts the focus back on the selected bird's tile (the Birds item of the navigation). */
    private void focusSelectedTile() {
        Carousel.Tile tile = carousel.card(profile.selected.birdId);
        if (tile != null) {
            ring.focus(tile);
            carousel.scrollIntoView(tile, true);
        }
    }

    /** Opens the shop, replacing this section rather than stacking on top of it. */
    private void openShop() {
        if (context != null) {
            screens.replace(new ShopScreen(context));
        }
    }

    /** Opens the upgrade trees, replacing this section. */
    private void openForge() {
        if (context != null) {
            screens.replace(new UpgradeTreeScreen(context));
        }
    }

    /** Opens the goals, replacing this section. */
    private void openGoals() {
        if (context != null) {
            screens.replace(new GoalsScreen(context));
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

    /**
     * Steps the run mode (D28).
     *
     * <p>A locked mode can be <em>looked</em> at — the row greys it and says in words what opens
     * it, which is the only place that sentence is ever read — but it cannot be flown: while
     * {@code feature:seeded_runs} is missing, {@link #openHome()} hands the hub a standard run
     * whatever the row shows, and stepping onto Seeded or Daily says so out loud. That is
     * deliberately not the tier and world rows' snap-back: those two <em>write</em> the profile's
     * selection, and this one writes nothing.
     *
     * @param index the index in {@link #modes}
     */
    private void selectMode(int index) {
        if (index < 0 || index >= modes.size()) {
            return;
        }
        RunMode next = modes.get(index);
        runMode = next;
        refreshModeRow();
        if (next != RunMode.STANDARD && !DailyChallenge.isAvailable(profile)) {
            toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED, seededUnlockText()),
                    Toast.Kind.WARNING);
            return;
        }
        if (next == RunMode.DAILY && dailyPick != null) {
            // The pick is settled the first time it is looked at (E27); saying so out loud is
            // what makes "the daily does not change under you" visible rather than implied.
            toasts.push(dailySetup(dailyPick), Toast.Kind.INFO);
        }
    }

    /**
     * Goes back to the hub carrying the run the mode row picked (D17, D28, D29).
     *
     * <p>Standard is no run in particular — the hub builds it from the profile's own world and
     * tier, so nothing is handed over. Seeded hands over the seed of the last run the profile
     * finished, and Daily today's pick on the one seed that date has — read here, which is what
     * settles the day — so the hub's START RUN plays what the row said it would. The daily's
     * instant retry keeps that seed and only moves the attempt counter, because
     * {@link DailyRunSource} ignores the seed the game screen asks for.
     */
    private void openHome() {
        RunMode chosen = DailyChallenge.isAvailable(profile) ? runMode : RunMode.STANDARD;
        screens.popTo(MainMenuScreen.class, hub -> {
            if (chosen == RunMode.DAILY && clock != null) {
                DailyRunSource daily = new DailyRunSource(content, () -> profile, clock, save);
                hub.requestRun(daily, SeedSequence.of(daily.pick().seed()));
            } else if (chosen == RunMode.SEEDED) {
                hub.requestRun(new ContentRunFactory(content, RunMode.SEEDED, () -> profile),
                        SeedSequence.of(profile.lastSeed));
            } else {
                hub.clearRequestedRun();
            }
        });
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
     * The hazards a world spawns, in words (shared with the hub's World Select, M10).
     *
     * @param def the world
     * @return the comma-separated names
     */
    private String hazardsOf(WorldDef def) {
        return ProgressionText.hazards(strings, def);
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
     * Points the tier row's detail line at the tier it shows: what the tier does for an owned
     * one, the cheapest way in for a locked one.
     */
    private void refreshTierRow() {
        int index = tier.selectedIndex();
        String id = index >= 0 && index < tierIds.size() ? tierIds.get(index) : null;
        if (id == null || !content.tiers().contains(id)) {
            tier.bind(null, "", false, "");
            return;
        }
        TierDef def = content.tiers().get(id);
        boolean owned = profile.isUnlocked(def.unlockableId());
        String detail = owned
                ? ProgressionText.description(strings, ContentKind.TIER, id)
                : strings.format(StringKey.BIRDS_WORLD_LOCKED,
                        ProgressionText.unlockText(strings, content, def.unlock(), profile));
        tier.bind(null, detail, !owned, detail);
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

    /**
     * The mode options: Standard always, Seeded and Daily marked with the condition that opens
     * them while {@code feature:seeded_runs} is locked (D28). Daily is listed only when the
     * screen was given a clock, because without one it could not say what it would play.
     *
     * @return one label per mode
     */
    private List<String> modeOptions() {
        modes.clear();
        List<String> options = new ArrayList<>();
        boolean open = DailyChallenge.isAvailable(profile);
        modes.add(RunMode.STANDARD);
        options.add(strings.get(StringKey.MODE_STANDARD));
        modes.add(RunMode.SEEDED);
        options.add(modeLabel(StringKey.MODE_SEEDED, open));
        if (clock != null) {
            modes.add(RunMode.DAILY);
            options.add(modeLabel(StringKey.MODE_DAILY, open));
        }
        return options;
    }

    /**
     * One mode label, carrying the unlock condition while the mode is locked.
     *
     * @param key the mode name
     * @param open whether the profile owns {@code feature:seeded_runs}
     * @return the label
     */
    private String modeLabel(StringKey key, boolean open) {
        return open ? strings.get(key)
                : strings.get(key) + " (" + seededUnlockText() + ")";
    }

    /**
     * How the profile can open Seeded and Daily mode: the cheapest branch of
     * {@code feature:seeded_runs}, measured against this profile (D13).
     *
     * @return the condition in words
     */
    private String seededUnlockText() {
        if (!content.features().contains(DailyChallenge.SEEDED_RUNS_FEATURE)) {
            return strings.get(StringKey.COMMON_LOCKED);
        }
        return ProgressionText.unlockText(strings, content,
                content.features().get(DailyChallenge.SEEDED_RUNS_FEATURE).unlock(), profile);
    }

    /**
     * The index of the selected mode.
     *
     * @return the index, {@code 0} when the mode is not offered
     */
    private int modeIndex() {
        int index = modes.indexOf(runMode);
        return index < 0 ? 0 : index;
    }

    /**
     * The mode the picker points at.
     *
     * @return the mode, {@link RunMode#STANDARD} when the row is empty
     */
    private RunMode shownMode() {
        int index = mode == null ? -1 : mode.selectedIndex();
        return index >= 0 && index < modes.size() ? modes.get(index) : RunMode.STANDARD;
    }

    /**
     * Points the mode row's detail line and tooltip at the run Play would start: the seed a
     * seeded run would replay, or today's daily pick — which is <em>written</em> here, the first
     * time the row shows it (E27), so a world unlocked later today cannot move it.
     */
    private void refreshModeRow() {
        RunMode shown = shownMode();
        boolean locked = shown != RunMode.STANDARD && !DailyChallenge.isAvailable(profile);
        if (locked) {
            dailyPick = null;
            String text = strings.format(StringKey.BIRDS_MODE_LOCKED, seededUnlockText());
            mode.bind(null, text, true, text);
            return;
        }
        if (shown == RunMode.DAILY && clock != null) {
            dailyPick = new DailyChallenge(clock).today(profile, content);
            if (save != null) {
                save.run();
            }
            String detail = dailySetup(dailyPick);
            mode.bind(null, detail, false, detail + " - " + dailyRecordText());
            forceDailyRows(dailyPick);
            return;
        }
        dailyPick = null;
        releaseDailyRows();
        String detail = shown == RunMode.SEEDED
                ? strings.format(StringKey.BIRDS_MODE_SEEDED_HINT, profile.lastSeed)
                : strings.get(StringKey.BIRDS_MODE_STANDARD_HINT);
        mode.bind(null, detail, false, detail);
    }

    /**
     * Shows the world and the tier today's daily forces, read-only (D28, E27).
     *
     * <p>The two rows used to stay live under a settled daily and still wrote the profile's
     * selection, while {@code DailyRunSource} replaced both at run start — so the screen said one
     * thing and the run played another. They now say what the run will actually be and refuse to
     * be stepped; leaving Daily gives them back.
     *
     * @param pick today's pick
     */
    private void forceDailyRows(DailyChallenge.Pick pick) {
        String forced = strings.get(StringKey.BIRDS_DAILY_FORCED);
        WorldPaletteDef palette = content.has(GameContent.WORLDS)
                && content.worlds().contains(pick.worldId())
                ? content.worlds().get(pick.worldId()).palette() : null;
        world.setShownOverride(ProgressionText.name(strings, ContentKind.WORLD, pick.worldId()));
        world.setEnabled(false);
        world.bind(palette, forced, false, forced);
        tier.setShownOverride(ProgressionText.name(strings, ContentKind.TIER, pick.tierId()));
        tier.setEnabled(false);
        tier.bind(null, forced, false, forced);
    }

    /** Gives the world and tier rows back after the mode leaves Daily. */
    private void releaseDailyRows() {
        boolean wasForced = world.shownOverride() != null || tier.shownOverride() != null;
        world.setShownOverride(null);
        tier.setShownOverride(null);
        world.setEnabled(true);
        tier.setEnabled(true);
        if (wasForced) {
            refreshWorldRow();
            refreshTierRow();
        }
    }

    /**
     * Today's daily in one line: its world, its tier and the cards it forces.
     *
     * @param pick the pick
     * @return the text
     */
    private String dailySetup(DailyChallenge.Pick pick) {
        List<String> cards = new ArrayList<>(pick.modifierIds().size());
        for (String id : pick.modifierIds()) {
            cards.add(ProgressionText.name(strings, ContentKind.MODIFIER, id));
        }
        return strings.format(StringKey.BIRDS_MODE_DAILY_HINT,
                ProgressionText.name(strings, ContentKind.WORLD, pick.worldId()),
                ProgressionText.name(strings, ContentKind.TIER, pick.tierId()),
                cards.isEmpty() ? strings.get(StringKey.COMMON_NONE) : String.join(", ", cards));
    }

    /**
     * What the profile has done with today's daily so far (D28: "best N gates, attempt K").
     *
     * @return the text
     */
    private String dailyRecordText() {
        PlayerProfile.DailyRecord record = profile.daily;
        return record == null || record.attempts <= 0
                ? strings.get(StringKey.DAILY_UNPLAYED)
                : strings.format(StringKey.DAILY_RESULT, record.bestGates, record.attempts);
    }

    /** Re-reads every label from the string table (a language switch, D25). */
    public void refreshTexts() {
        header.setTitle(strings.get(StringKey.BIRDS_TITLE));
        header.display().setFormat(strings.get(StringKey.HUD_COINS));
        hero.setLabels(strings.get(StringKey.BIRDS_ATTR_MOBILITY),
                strings.get(StringKey.BIRDS_ATTR_DEFENCE),
                strings.get(StringKey.BIRDS_ATTR_CONTROL));
        seeDetails.setText(strings.get(StringKey.BIRDS_SEE_DETAILS));
        runSetup.setTitle(strings.get(StringKey.BIRDS_RUN_SETUP));
        runSetup.setDoneText(strings.get(StringKey.COMMON_DONE));
        details.setTitle(strings.get(StringKey.BIRDS_PANEL));
        details.setDoneText(strings.get(StringKey.COMMON_DONE));
        nav.button(SectionNav.SHOP).setText(strings.get(StringKey.MENU_SHOP));
        nav.button(SectionNav.BIRDS).setText(strings.get(StringKey.MENU_BIRDS));
        nav.button(SectionNav.PLAY).setText(strings.get(StringKey.MENU_PLAY));
        nav.button(SectionNav.FORGE).setText(strings.get(StringKey.MENU_NAV_FORGE));
        nav.button(SectionNav.GOALS).setText(strings.get(StringKey.MENU_NAV_GOALS));
        mode.setLabel(strings.get(StringKey.BIRDS_MODE));
        mode.setOptions(modeOptions());
        mode.selectQuietly(modeIndex());
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
        for (BirdDef bird : content.birds()) {
            CardGrid.Card card = carousel.card(bird.id());
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
        carousel.select(profile.selected.birdId);
        tier.setOptions(tierOptions());
        tier.selectQuietly(tierIndex());
        world.setOptions(worldOptions());
        world.selectQuietly(worldIndex());
        refreshWorldRow();
        refreshTierRow();
        mode.setOptions(modeOptions());
        mode.selectQuietly(modeIndex());
        refreshModeRow();
        header.display().setAmount(coins());
        // One preview run answers both questions the panels below ask: what the stats resolve to
        // and which rules the run carries (D8, D9). Building it twice could not disagree, but
        // building it once means it cannot.
        Run preview = RunLoadout.previewRun(profile, content);
        previewRules = preview.simulation().rules();
        refreshLoadout();
        buildBreakdown(preview.simulation().stats());
        refreshSetupBar();
        refreshCurrent();
    }

    /**
     * Points the summary bar at the run the call to action would start: the world, the tier and
     * the mode in one line, and under it what that run actually is.
     *
     * <p>Under a settled daily the line names the <em>pick's</em> world and tier rather than the
     * profile's selection, because that is what will be played.
     */
    private void refreshSetupBar() {
        RunMode shown = shownMode();
        boolean daily = shown == RunMode.DAILY && dailyPick != null;
        String worldId = daily ? dailyPick.worldId() : currentWorldId();
        int tierAt = tier.selectedIndex();
        String tierId = daily ? dailyPick.tierId()
                : (tierAt >= 0 && tierAt < tierIds.size() ? tierIds.get(tierAt) : null);
        String worldName = worldId == null ? strings.get(StringKey.COMMON_NONE)
                : ProgressionText.name(strings, ContentKind.WORLD, worldId);
        String tierName = tierId == null ? strings.get(StringKey.COMMON_NONE)
                : ProgressionText.name(strings, ContentKind.TIER, tierId);
        String modeName = strings.get(modeKey(shown));
        String line1 = strings.format(StringKey.BIRDS_SETUP, worldName, tierName, modeName);
        boolean locked = shown != RunMode.STANDARD && !DailyChallenge.isAvailable(profile);
        String line2;
        boolean warn = false;
        if (locked) {
            line2 = strings.format(StringKey.BIRDS_MODE_LOCKED, seededUnlockText());
            warn = true;
        } else if (daily) {
            line2 = dailySetup(dailyPick) + " - " + dailyRecordText();
        } else if (shown == RunMode.SEEDED) {
            line2 = strings.format(StringKey.BIRDS_MODE_SEEDED_HINT, profile.lastSeed);
        } else {
            line2 = worldLine(worldId);
        }
        WorldPaletteDef palette = worldId != null && content.has(GameContent.WORLDS)
                && content.worlds().contains(worldId)
                ? content.worlds().get(worldId).palette() : null;
        bar.bind(palette, line1, line2, warn, line1 + " - " + line2);
    }

    /**
     * The hazards of a world, or how it is opened.
     *
     * @param worldId the world id, may be {@code null}
     * @return the line
     */
    private String worldLine(String worldId) {
        if (worldId == null || !content.has(GameContent.WORLDS)
                || !content.worlds().contains(worldId)) {
            return "";
        }
        WorldDef def = content.worlds().get(worldId);
        return profile.isUnlocked(def.unlockableId())
                ? strings.format(StringKey.BIRDS_WORLD_HAZARDS, hazardsOf(def))
                : strings.format(StringKey.BIRDS_WORLD_LOCKED,
                        ProgressionText.unlockText(strings, content, def.unlock(), profile));
    }

    /**
     * The name of one run mode.
     *
     * @param runMode the mode
     * @return the string key of its name
     */
    private static StringKey modeKey(RunMode runMode) {
        switch (runMode) {
            case SEEDED:
                return StringKey.MODE_SEEDED;
            case DAILY:
                return StringKey.MODE_DAILY;
            case CHALLENGE:
                return StringKey.MODE_CHALLENGE;
            default:
                return StringKey.MODE_STANDARD;
        }
    }

    /**
     * Rebuilds only what depends on the focused card — the palette row, the two actions and the
     * ability line — which is what changes when the focus moves from one bird to another. The
     * breakdown is deliberately not part of it: it describes the <em>selected</em> build, not the
     * card the player is looking at.
     */
    public void refreshCurrent() {
        refreshCurrent(0);
    }

    /**
     * Rebuilds what depends on the browsed bird, sliding the hero when the bird changed.
     *
     * @param direction {@code +1} when the new bird sits right of the old one in the roster,
     *     {@code -1} left, {@code 0} for a refresh that is not a bird change
     */
    private void refreshCurrent(int direction) {
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
        boolean isSelected = currentBirdId.equals(profile.selected.birdId);
        long price = UnlockEvaluator.priceOf(evaluator.conditionOf(
                BirdDef.NAMESPACE + currentBirdId));
        String name = ProgressionText.name(strings, ContentKind.BIRD, currentBirdId);
        String how = ProgressionText.unlockText(strings, content, current.unlock(), profile);
        // One button, one sentence: what this bird needs from the player right now.
        if (!owned && price >= 0 && balance >= price) {
            cta.setText(strings.format(StringKey.BIRDS_BUY_FOR,
                    ProgressionText.price(strings, price)));
            cta.setIcon(coinIcon);
            cta.setEnabled(unlocks != null);
        } else if (!owned) {
            cta.setText(strings.format(StringKey.BIRDS_LOCKED_CTA, how));
            cta.setIcon(AbilityIcons.PADLOCK);
            cta.setEnabled(false);
        } else if (isSelected) {
            cta.setText(strings.get(StringKey.BIRDS_SELECTED_CTA));
            cta.setIcon(AbilityIcons.CHECK);
            cta.setEnabled(false);
        } else {
            cta.setText(strings.format(StringKey.BIRDS_USE, name));
            cta.setIcon(AbilityIcons.BIRD);
            cta.setEnabled(selection != null);
        }
        ctaTooltip = owned ? "" : tooltipFor(current, false, price);
        BirdHero.Status status = !owned ? BirdHero.Status.LOCKED
                : isSelected ? BirdHero.Status.SELECTED : BirdHero.Status.OWNED;
        String statusLine = archetypeName(current);
        if (isSelected) {
            statusLine = statusLine + " \u00b7 " + strings.get(StringKey.COMMON_SELECTED);
        } else if (!owned) {
            statusLine = statusLine + " \u00b7 " + strings.get(StringKey.COMMON_LOCKED);
        }
        hero.bind(current, paletteOf(current), name, statusLine, status,
                BirdAttributes.of(current, content), direction);
        hero.setLabels(strings.get(StringKey.BIRDS_ATTR_MOBILITY),
                strings.get(StringKey.BIRDS_ATTR_DEFENCE),
                strings.get(StringKey.BIRDS_ATTR_CONTROL));
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
            slot.bind(SlotRole.PASSIVE, null, "", "", "", false, "", null);
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
        String level = def == null ? ""
                : strings.format(StringKey.BIRDS_LEVEL_SHORT, abilityLevelOf(def),
                        def.levels().size());
        slot.setVisible(true);
        slot.bind(role, def == null ? null : def.id(), label, value,
                slotTooltip(def, blocked), blocked != null, level, AbilityIcons.of(def));
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
        contentHeight += DetailsPanel.HEADER_H;
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
            contentHeight += DetailsPanel.HEADER_H;
            rows.add(new Row("stat." + stat.name() + ".base",
                    strings.get(StringKey.BIRDS_BREAKDOWN_BASE),
                    ProgressionText.number(breakdown.base()), false, contentHeight));
            contentHeight += DetailsPanel.ROW_H;
            for (EffectStack.Entry entry : breakdown.contributions()) {
                rows.add(new Row("stat." + stat.name() + "." + entry.modifier().source(),
                        ProgressionText.sourceLabel(strings, content, entry.modifier().source()),
                        ProgressionText.effect(strings, entry.modifier()), false, contentHeight));
                contentHeight += DetailsPanel.ROW_H;
            }
        }
        if (shown == 0) {
            rows.add(new Row("empty", strings.get(StringKey.BIRDS_BREAKDOWN_EMPTY), "", false,
                    contentHeight));
            contentHeight += DetailsPanel.ROW_H;
        }
        details.setRows(rows(), contentHeight);
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
        contentHeight += DetailsPanel.HEADER_H;
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
            contentHeight += DetailsPanel.ROW_H;
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
        contentHeight += DetailsPanel.HEADER_H;
        String kinds = ProgressionText.abilityKind(strings, def.kind());
        String tags = ProgressionText.abilityTags(strings, def);
        addWrapped(id + ".kind", tags.isEmpty() ? kinds : kinds + " - " + tags, dimmed);
        addWrapped(id + ".desc", ProgressionText.abilityDescription(strings, def, level), dimmed);
        addWrapped(id + ".effect", ProgressionText.abilityEffects(strings, def, level), dimmed);
        if (blocked != null) {
            rows.add(new Row(id + ".blocked", strings.format(StringKey.BIRDS_ABILITY_BLOCKED,
                    ProgressionText.ruleName(strings, blocked)), "", false, contentHeight, true));
            contentHeight += DetailsPanel.ROW_H;
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
            contentHeight += DetailsPanel.ROW_H;
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

    /**
     * Forwards the reduce-flashing default to everything on the screen that pulses.
     *
     * <p>It caps the glows and nothing else: the bob, the wing beat, the hero's slide and the
     * carousel's tween are motion rather than luminance, and the hub keeps its own bob under the
     * same setting. Polled every tick, because the settings screen can change it while this
     * screen is only one pop away.
     *
     * @param reduce the default
     */
    private void applyReduceFlashing(boolean reduce) {
        if (reduce == reduceShown) {
            return;
        }
        reduceShown = reduce;
        cta.setReduceFlashing(reduce);
        carousel.setReduceFlashing(reduce);
        hero.setReduceFlashing(reduce);
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        closeDetails();
        closeRunSetup();
        panel = PanelKind.NONE;
        runSetup.setOpen(false);
        details.setOpen(false);
        Carousel.Tile tile = carousel.card(profile.selected.birdId);
        ring.focus(tile);
        carousel.snapTo(tile);
        hero.snap();
        screens.setLetterboxRgb(PALETTE.letterbox());
        tooltip.hide();
        header.display().setAmountNow(coins());
        applyReduceFlashing(ParticleSystem.defaultReduceFlashing());
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else {
            refreshState();
        }
    }

    @Override
    public void tick(InputFrame input) {
        relayoutIfResurfaced();
        ticks++;
        prevBob = bob;
        bob = BirdHero.bobAt(ticks);
        toasts.tick();
        header.tick();
        applyReduceFlashing(ParticleSystem.defaultReduceFlashing());
        cta.setTicks(ticks);
        carousel.setTicks(ticks);
        hero.tick(ticks);
        switch (panel) {
            case DETAILS:
                details.ring().handle(input);
                details.scrollBy(input.wheel());
                details.scrollKeys(input);
                break;
            case RUN_SETUP: {
                runSetup.ring().handle(input);
                boolean changed = tier.tick(input);
                if (world.tick(input) && world.selectedIndex() != worldIndex()) {
                    // A step onto a world the profile does not own: onChange refused it and
                    // snapped the row back; a step onto an owned one already wrote the selection.
                    refreshWorldRow();
                    changed = true;
                }
                changed |= mode.tick(input);
                if (changed) {
                    refreshTierRow();
                    refreshSetupBar();
                }
                break;
            }
            default: {
                ring.handle(input);
                carousel.tick(input, ring);
                // Whatever moved the focus — the pointer, an arrow, Tab or the navigation's own
                // Birds item — the hero follows the tile it landed on. Comparing against the
                // browsed bird rather than against the previous focus is what makes a focus set
                // from outside a tick (onEnter, focusSelectedTile) arrive here too; browse()
                // itself is a no-op when the id has not changed, so a tile click that already
                // routed through activate() never slides twice.
                if (ring.focused() instanceof Carousel.Tile tile) {
                    browse(tile.id());
                }
                break;
            }
        }
        updateTooltip(input);
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
        if (input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            if (panel == PanelKind.RUN_SETUP) {
                closeRunSetup();
            } else if (panel == PanelKind.DETAILS) {
                closeDetails();
            } else {
                screens.pop();
            }
        }
    }

    /**
     * Points the tooltip at whatever the player is asking about: the node under the pointer, or
     * the focused one when the pointer is elsewhere. The attribute badges are not ring nodes —
     * they are read, not pressed — so the hero is asked for them directly.
     *
     * @param input the tick input
     */
    private void updateTooltip(InputFrame input) {
        FocusRing active = focusRing();
        UiNode under = active.nodeAt(input.mouseX(), input.mouseY());
        if (under == null && panel == PanelKind.NONE) {
            under = hero.badgeAt(input.mouseX(), input.mouseY());
        }
        UiNode target = under != null ? under : active.focused();
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
        if (node instanceof RunSetupBar setup) {
            return setup.tooltip();
        }
        if (node instanceof AttributeBadge badge) {
            return badge.label() + " " + badge.value() + "/" + AttributeBadge.MAX;
        }
        if (node == cta) {
            return ctaTooltip;
        }
        return "";
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        // The hero paints itself at the reference band's absolute rows, so a taller surface
        // reaches it through a translated copy: the freed room becomes sky around the same
        // bird, not a stretched one, and the copy is thrown away with the shift inside it.
        Graphics2D heroContext = (Graphics2D) g.create();
        try {
            heroContext.translate(0, surfaceTop + heroShift);
            hero.render(heroContext, MathUtil.lerp(prevBob, bob, alpha), ticks);
        } finally {
            heroContext.dispose();
        }
        header.render(g);
        carousel.render(g);
        g.setFont(Fonts.bold(13));
        TextPainter.drawOutlined(g, strings.get(StringKey.BIRDS_PALETTES), MARGIN,
                paletteLabelBaseline, Align.LEFT, ProceduralArt.accentColor(PALETTE),
                ProceduralArt.letterboxColor(PALETTE), 2);
        for (Swatch swatch : swatches) {
            if (swatch.isVisible()) {
                swatch.render(g);
            }
        }
        bar.render(g);
        ProceduralArt.panel(g, MARGIN - 4, abilityTop, CONTENT_W + 8, ABILITY_PANEL_H);
        g.setFont(Fonts.regular(11));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.draw(g, abilityLine, MARGIN + 8.0, abilityBaseline);
        seeDetails.render(g);
        for (AbilitySlot slot : slots) {
            if (slot.isVisible()) {
                slot.render(g);
            }
        }
        cta.render(g);
        nav.render(g);
        if (panel != PanelKind.NONE) {
            Overscan.fillVisible(g, DIM);
            if (panel == PanelKind.RUN_SETUP) {
                runSetup.render(g);
            } else {
                details.render(g);
            }
        }
        tooltip.render(g);
        toasts.render(g, surfaceTop + HubHeader.HEIGHT);
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
     * A small chip with a label and a chevron: the entry that opens a panel.
     */
    static final class ChipButton extends UiNode {

        /** Point size of the label. */
        static final int FONT_SIZE = 11;
        /** Room the chevron takes at the right edge. */
        static final int CHEVRON_ROOM = 22;

        private String text = "";
        private String shown = "";
        private String shownSource;
        private int shownWidth = -1;
        private double shownScale;

        /**
         * The label, as drawn.
         *
         * @return the text
         */
        String text() {
            return text;
        }

        /**
         * Changes the label (a language switch).
         *
         * @param newText the text
         */
        void setText(String newText) {
            this.text = newText == null ? "" : newText;
        }

        @Override
        public void render(Graphics2D g) {
            int bx = (int) Math.round(x());
            int by = (int) Math.round(y());
            int bw = (int) Math.round(width());
            int bh = (int) Math.round(height());
            ProceduralArt.chip(g, bx, by, bw, bh,
                    ButtonState.of(isEnabled(), isFocused(), isHovered()));
            int room = bw - CHEVRON_ROOM - 12;
            g.setFont(Fonts.bold(FONT_SIZE));
            double scale = Fonts.textScale();
            if (shownSource != text || shownWidth != room || shownScale != scale) {
                // Measured only when the label, the room or the text scale changed.
                shown = TextPainter.ellipsise(g, text, Math.max(0, room));
                shownSource = text;
                shownWidth = room;
                shownScale = scale;
            }
            g.setColor(isEnabled() ? ProceduralArt.TEXT_LIGHT : ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, shown, bx + 10.0, TextPainter.centeredBaseline(g, centerY()));
            ProceduralArt.drawChevron(g, bx + bw - 14.0, centerY(), 11,
                    isFocused() || isHovered() ? ProceduralArt.TEXT_LIGHT
                            : ProceduralArt.TEXT_MUTED);
        }
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
     * traded away. The drawing is {@link AbilityCard}'s — the glyph, the slot label, the ability
     * name and its level — and this class adds what the slot <em>is</em>.
     */
    public static final class AbilitySlot extends AbilityCard {

        private SlotRole role = SlotRole.ACTIVE;
        private String abilityId;
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
         * @param levelText the level line, empty when the slot holds nothing
         * @param icon the ability's glyph, or {@code null} for an empty slot
         */
        void bind(SlotRole newRole, String newAbilityId, String newLabel, String newValue,
                String newTooltip, boolean isBlocked, String levelText, IconPainter icon) {
            this.role = newRole;
            this.abilityId = newAbilityId;
            this.tooltip = newTooltip;
            this.blocked = isBlocked;
            Tone tone;
            if (isBlocked) {
                tone = Tone.BLOCKED;
            } else if (newRole == SlotRole.INNATE) {
                tone = Tone.FIXED;
            } else if (newAbilityId == null) {
                tone = Tone.EMPTY;
            } else {
                tone = Tone.NORMAL;
            }
            bind(newLabel, newValue, levelText, icon, tone);
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
        // A picker row with a detail line under it: the world picker (M7) and, without a swatch,
        // the run-mode picker (M9), which says what Play would start on the same second line.

        private static final Color ARROW_ON = new Color(0xF4, 0xF8, 0xF8);
        private static final Color ARROW_OFF = new Color(0x6E, 0x7A, 0x7C);
        private static final Stroke FOCUS = new BasicStroke(2f);
        private static final int SWATCH_SIZE = 18;
        /** Colour of a detail line that is a refusal. */
        private static final Color WARN = new Color(0xE8, 0x5A, 0x4A);

        private final int[] arrowX = new int[3];
        private final int[] arrowY = new int[3];
        private WorldPaletteDef swatch;
        private String detail = "";
        private String tooltip = "";
        private boolean locked;
        private int fontSize = 14;
        private String shownOverride;

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
         * Shows a value the row does not step to, or clears it.
         *
         * <p>Today's daily forces its own world and tier: the rows show those, read-only, rather
         * than a selection the run would ignore.
         *
         * @param value the text to draw instead of the selected option, or {@code null}
         */
        void setShownOverride(String value) {
            this.shownOverride = value;
        }

        /**
         * The override, if any.
         *
         * @return the text, or {@code null} when the row shows its own selection
         */
        String shownOverride() {
            return shownOverride;
        }

        /**
         * The value the row draws: the override when one is set, its selected option otherwise.
         *
         * @return the text
         */
        public String shownOption() {
            return shownOverride != null ? shownOverride : selectedOption();
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
            TextPainter.draw(g, shownOption(), (leftArrow + rightArrow) / 2,
                    TextPainter.centeredBaseline(g, lineY), Align.CENTER);
            if (!detail.isEmpty()) {
                g.setFont(Fonts.regular(10));
                g.setColor(locked ? WARN : ProceduralArt.TEXT_MUTED);
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

        /** Veil over a palette the profile has not earned. */
        private static final Color VEIL = new Color(0x10, 0x1C, 0x1E, 0xC0);

        private PaletteDef palette;
        private Color body;
        private Color wing;
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
            if (newPalette != palette) {
                // Cached here rather than in render: a swatch's palette changes with the
                // browsed bird, never per frame.
                body = newPalette == null ? null : new Color(newPalette.bodyRgb());
                wing = newPalette == null ? null : new Color(newPalette.wingRgb());
            }
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
            g.setColor(body);
            g.fillRoundRect(bx, by, bw, bh, 6, 6);
            g.setColor(wing);
            g.fillRoundRect(bx + bw / 2, by + bh / 2, bw / 2 - 2, bh / 2 - 2, 4, 4);
            if (!owned) {
                g.setColor(VEIL);
                g.fillRoundRect(bx, by, bw, bh, 6, 6);
            }
            Stroke old = g.getStroke();
            g.setStroke(SWATCH_STROKE);
            g.setColor(active ? ProceduralArt.COIN_GOLD
                    : (isFocused() || isHovered() ? ProceduralArt.TEXT_LIGHT
                            : ProceduralArt.TEXT_MUTED));
            g.drawRoundRect(bx, by, bw, bh, 6, 6);
            g.setStroke(old);
        }
    }
}
