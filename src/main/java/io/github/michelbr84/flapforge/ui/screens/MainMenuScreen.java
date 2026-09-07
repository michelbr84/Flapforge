package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.audio.MusicSequencer;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.AchievementEvaluator;
import io.github.michelbr84.flapforge.progression.PlayerLevel;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.Statistics;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.render.BackgroundRenderer;
import io.github.michelbr84.flapforge.render.CloudLayer;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.Overscan;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.render.WorldStyle;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.CtaButton;
import io.github.michelbr84.flapforge.ui.component.CurrencyChip;
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.IconButton;
import io.github.michelbr84.flapforge.ui.component.NavBar;
import io.github.michelbr84.flapforge.ui.component.NavButton;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Objects;

/**
 * The home hub (D17, M10): the first screen after the boot, laid out like the front page of an
 * arcade roguelite rather than as a list of options.
 *
 * <ul>
 *   <li>A HUD band on top: the {@link PlayerCard} (avatar, name, level, XP — opens the Profile),
 *       the coin {@link CurrencyChip} (opens the Shop) and the gear {@link IconButton}
 *       (Settings).</li>
 *   <li>The emblem — the bird on the anvil — over the title and the tagline, then the
 *       {@link WorldPlaque} naming the selected world (opens the World Select).</li>
 *   <li>The {@link ForgeScene}: the island with the anvil and the selected bird, dressed with
 *       more the further the upgrade trees have been built, in the selected world's palette
 *       over that world's backdrop.</li>
 *   <li>The {@link NextUnlockCard}, the START RUN {@link CtaButton} with the world and the tier
 *       under it, and the last-run line.</li>
 *   <li>The {@link NavBar}: Shop, Birds, PLAY, Forge (the upgrade trees) and Goals (challenges,
 *       achievements, milestones, collections).</li>
 * </ul>
 *
 * <p>Everything the player reads comes from {@link Strings} and follows a live language switch:
 * the screen compares the table's language against the one its labels were built from and
 * rebuilds them when it changed. The profile is watched the same way: a pop back from a
 * sub-screen does not re-enter the hub (D17), so {@link #tick} compares the selection, the
 * level, the wallet, the unlocks, the upgrades and the run history against what is shown and
 * refreshes when any differs.
 *
 * <p>START RUN plays the profile's selection: with content and a profile the hub builds its own
 * {@link ContentRunFactory} ({@code SEEDED} when the seed was explicit, {@code STANDARD}
 * otherwise), so the plaque is what the run is; without them the injected source is played,
 * which is how {@code --seed N} and the tests reach the first run.
 *
 * <p>{@code Esc}/Back arms a "press again to quit" toast for {@value #QUIT_ARM_TICKS} ticks; a
 * second press asks the {@link ScreenManager} to close. Any change of the screen stack disarms
 * it. The shared {@link ToastLayer} is drawn here under the HUD band, so a message raised at
 * boot is still readable on the first screen the player sees.
 *
 * <p>M9's attract mode is unchanged: after {@value #ATTRACT_DELAY_TICKS} ticks without any input
 * a bot-driven {@link DemoScreen} starts behind the hub, rendered dimmed under a veil with only
 * the hub's chrome on top (the backdrop and the forge scene are skipped while it is up). Any
 * input cancels it in the tick it arrives; the press that cancels it never arms the quit.
 */
public final class MainMenuScreen implements Screen {

    /** World whose loop the menu plays — the plan's menu music is Green Fields (M8, D19). */
    private static final String MENU_MUSIC_WORLD = "green_fields";
    private static final int BOB_PERIOD_TICKS = 96;
    private static final double BOB_AMPLITUDE = 4;
    private static final int WING_PERIOD_TICKS = 48;
    private static final double EMBLEM_CX = Playfield.WIDTH / 2.0;
    private static final double LOGO_ANVIL_TOP = 84;
    private static final double LOGO_ANVIL_W = 56;
    private static final double LOGO_BIRD_SIZE = 32;
    private static final int TITLE_BASELINE = 144;
    private static final int TAGLINE_BASELINE = 160;
    private static final int LAST_RUN_BASELINE = 556;
    /** Band kept clear of toasts so they never cover the HUD. */
    public static final int TOAST_TOP_INSET = 64;
    /** Ticks the quit stays armed after the first Back. */
    public static final int QUIT_ARM_TICKS = 180;
    /**
     * Ticks of idle input before the attract demo starts (M9): twenty seconds at the tick rate.
     */
    public static final int ATTRACT_DELAY_TICKS = 20 * Playfield.TICK_RATE;
    /** The veil the attract demo is shown under: dimmed, but the menu on top stays readable. */
    private static final Color ATTRACT_DIM = new Color(0, 0, 0, 0x66);

    /** Navigation item id: the shop. */
    public static final String NAV_SHOP = "shop";
    /** Navigation item id: the bird selection. */
    public static final String NAV_BIRDS = "birds";
    /** Navigation item id: play (the same run as START RUN). */
    public static final String NAV_PLAY = "play";
    /** Navigation item id: the upgrade trees. */
    public static final String NAV_FORGE = "forge";
    /** Navigation item id: the goals. */
    public static final String NAV_GOALS = "goals";

    private final ScreenManager screens;
    private final GameContext context;
    private final SeededRunSource runFactory;
    private final SeedSequence seeds;
    private final ToastLayer toasts;
    private final ParticleSystem particles;
    private final FocusRing ring = new FocusRing();
    private final Strings strings;
    private final GameContent content;
    private final boolean meta;
    private final boolean hasContent;
    private final UnlockEvaluator evaluator;
    private final CtaButton startRun;
    private final PlayerCard playerCard;
    private final CurrencyChip coins;
    private final IconButton gear;
    private final WorldPlaque plaque;
    private final NextUnlockCard nextUnlock;
    private final NavBar nav = new NavBar();
    private final ForgeScene forge = new ForgeScene();
    private final BackgroundRenderer backdrop = new BackgroundRenderer();
    private final CloudLayer clouds = new CloudLayer();
    private WorldPalette palette = WorldPalette.GREEN_FIELDS;
    private String shownLanguage;
    private String worldLine = "";
    private String prestigeLine = "";
    private String lastRunLine = "";
    private UnlockEvaluator.NextUnlock next;
    private int forgeStage;
    private int quitArmedTicks;
    private long ticks;
    private double prevBob;
    private double bob;
    // What the hub was last built from (D17: a pop does not re-enter the screen).
    private String shownWorldId;
    private String shownTierId;
    private String shownBirdId;
    private String shownPaletteId;
    private int shownLevel = -1;
    private long shownXp = -1;
    private int shownPrestigeCount = -1;
    private long shownCoins = -1;
    private int shownUnlocked = -1;
    private int shownUpgrades = -1;
    private int shownRuns = -1;
    private long shownBestGates = -1;
    private boolean shownProfile;
    /** The bot-driven demo shown behind the menu while the player idles (M9); null in headless. */
    private final DemoScreen demo;
    private boolean attractActive;
    private boolean attractPaused;
    private long idleTicks;
    private int stackVersionSeen = -1;
    private double prevMouseX;
    private double prevMouseY;
    private boolean mouseSeen;

    /**
     * Creates the menu with classic runs and clock-derived seeds (tests and tools).
     *
     * @param screens the manager used to push screens and request quitting
     */
    public MainMenuScreen(ScreenManager screens) {
        this(screens, null, new ClassicRunFactory(), SeedSequence.random());
    }

    /**
     * Creates the menu without an application context (tests and tools).
     *
     * @param screens the manager used to push screens and request quitting
     * @param runFactory builds the run the game screen plays
     * @param seeds the seed source ({@code --seed N} makes it explicit)
     */
    public MainMenuScreen(ScreenManager screens, SeededRunSource runFactory, SeedSequence seeds) {
        this(screens, null, runFactory, seeds);
    }

    /**
     * Creates the menu for a wired application.
     *
     * @param context the application services
     * @param runFactory builds the run the game screen plays when the session has no profile
     * @param seeds the seed source ({@code --seed N} makes it explicit)
     */
    public MainMenuScreen(GameContext context, SeededRunSource runFactory, SeedSequence seeds) {
        this(Objects.requireNonNull(context, "context").screens(), context, runFactory, seeds);
    }

    private MainMenuScreen(ScreenManager screens, GameContext context,
            SeededRunSource runFactory, SeedSequence seeds) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.context = context;
        this.runFactory = Objects.requireNonNull(runFactory, "runFactory");
        this.seeds = Objects.requireNonNull(seeds, "seeds");
        this.toasts = context != null && context.toasts() != null
                ? context.toasts() : new ToastLayer();
        this.strings = context != null && context.strings() != null
                ? context.strings() : Strings.active();
        this.content = context == null ? null : context.content();
        this.hasContent = content != null;
        // The meta-progression screens need content and a profile to read; a bare screen stack
        // (tests, tools, the headless launch) has neither, so the hub greys their items out.
        this.meta = hasContent && context.profile() != null;
        this.evaluator = hasContent ? UnlockEvaluator.of(content) : null;
        // The attract demo (M9) exists everywhere but in the headless launch: there is no
        // renderer there to show it, and the CI run the published determinism hash is read from
        // stays exactly as heavy as it was. Bare screen stacks (tests, tools) get one too, so
        // the mode is testable without a session.
        this.demo = context != null && context.options().headless() ? null
                : new DemoScreen(demoSource(), BotPilot.Preset.AVERAGE, strings);
        this.particles = new ParticleSystem();

        startRun = new CtaButton("", this::startGame);
        startRun.setBounds(40, 474, 340, 62);
        startRun.setIcon((g, cx, cy, size, color) ->
                ProceduralArt.drawCrossedHammers(g, cx, cy, size, color));
        playerCard = new PlayerCard(this::openProfile);
        playerCard.setBounds(10, 8, 180, 52);
        coins = new CurrencyChip(this::openShop);
        coins.setBounds(200, 12, 130, 30);
        coins.setVisible(meta);
        gear = new IconButton("", (g, cx, cy, size, color) ->
                ProceduralArt.drawGear(g, cx, cy, size, color, ProceduralArt.TEXT_DARK),
                this::openSettings);
        gear.setBounds(354, 10, 40, 40);
        plaque = new WorldPlaque(this::openWorldSelect);
        plaque.setBounds(90, 168, 240, 32);
        plaque.setVisible(meta);
        nextUnlock = new NextUnlockCard(this::openNextUnlock);
        nextUnlock.setBounds(24, 410, 372, 48);
        nextUnlock.setVisible(meta);
        ring.add(startRun);
        ring.add(playerCard);
        ring.add(coins);
        ring.add(gear);
        ring.add(plaque);
        ring.add(nextUnlock);

        nav.setBounds(0, 582, Playfield.WIDTH, 58);
        nav.add(new NavButton(NAV_SHOP, "", (g, cx, cy, size, color) ->
                ProceduralArt.drawAwning(g, cx, cy, size, color, ProceduralArt.TEXT_DARK),
                this::openShop)).setEnabled(meta);
        nav.add(new NavButton(NAV_BIRDS, "", (g, cx, cy, size, color) ->
                ProceduralArt.drawBirdSilhouette(g, cx, cy, size, color),
                this::openBirds)).setEnabled(meta);
        NavButton play = nav.add(new NavButton(NAV_PLAY, "", (g, cx, cy, size, color) ->
                ProceduralArt.drawCrossedHammers(g, cx, cy, size, color), this::startGame));
        play.setPrimary(true);
        nav.add(new NavButton(NAV_FORGE, "", (g, cx, cy, size, color) ->
                ProceduralArt.drawHammer(g, cx, cy, size, 0.5, color, color),
                this::openUpgrades)).setEnabled(meta);
        nav.add(new NavButton(NAV_GOALS, "", (g, cx, cy, size, color) ->
                ProceduralArt.drawScroll(g, cx, cy, size, color, ProceduralArt.TEXT_DARK),
                this::openGoals)).setEnabled(hasContent);
        nav.layoutRow(8, 44, 2, 56, 76, 84, 4, 8);
        nav.registerFocusables(ring);

        backdrop.setReduceFlashing(ParticleSystem.defaultReduceFlashing());
        refreshTexts();
        coins.display().setAmountNow(walletBalance());
    }

    // ------------------------------------------------------------------ profile reads

    /**
     * The session's profile, read through the context on every call because a prestige and
     * {@code --reset-save} replace the instance.
     *
     * @return the profile, or {@code null} without one
     */
    private PlayerProfile profile() {
        return context == null ? null : context.profile();
    }

    /**
     * The coins of the session's profile.
     *
     * @return the balance, 0 when the session has no profile
     */
    private long walletBalance() {
        PlayerProfile p = profile();
        return p == null ? 0 : Wallet.of(p).balance(PlayerProfile.CURRENCY_COINS);
    }

    private ProgressionRules rules() {
        return context != null && context.progressionRules() != null
                ? context.progressionRules() : ProgressionRules.none();
    }

    /**
     * The run source the attract demo plays on (M9): the profile-less content path when the
     * session has content — every demo run is {@code RunConfig.classic(seed)}, never the
     * player's loadout — and the hard-coded classic seam without content. Either way the
     * profile is not read and the classic curve of the published hash is what is shown.
     *
     * @return the source
     */
    private SeededRunSource demoSource() {
        if (context != null && context.content() != null) {
            return new ContentRunFactory(context.content());
        }
        return new ClassicRunFactory();
    }

    // ------------------------------------------------------------------ actions

    /**
     * Starts the run the plaque names: with content and a profile the hub builds its own
     * factory over the live profile, so the selection is what is played (an explicit seed keeps
     * the run {@code SEEDED}); without them the injected source is played.
     */
    private void startGame() {
        SeededRunSource source = runFactory;
        if (meta) {
            source = new ContentRunFactory(content,
                    seeds.isExplicit() ? RunMode.SEEDED : RunMode.STANDARD, context::profile);
        }
        screens.push(context != null ? new GameScreen(context, source, seeds)
                : new GameScreen(screens, source, seeds));
    }

    private void openSettings() {
        screens.push(context != null ? new SettingsScreen(context) : new SettingsScreen(screens));
    }

    private void openProfile() {
        screens.push(context != null ? new StatisticsScreen(context)
                : new StatisticsScreen(screens));
    }

    private void openBirds() {
        if (meta) {
            screens.push(new BirdSelectionScreen(context));
        }
    }

    private void openUpgrades() {
        if (meta) {
            screens.push(new UpgradeTreeScreen(context));
        }
    }

    private void openShop() {
        if (meta) {
            screens.push(new ShopScreen(context));
        }
    }

    private void openGoals() {
        if (hasContent) {
            screens.push(new GoalsScreen(context));
        }
    }

    private void openWorldSelect() {
        if (meta) {
            screens.push(new WorldSelectScreen(context));
        }
    }

    /** Opens the screen where the next unlock's kind is earned or bought. */
    private void openNextUnlock() {
        if (!meta || next == null) {
            return;
        }
        switch (next.kind()) {
            case BIRD:
                openBirds();
                break;
            case WORLD:
                openWorldSelect();
                break;
            case TREE:
                openUpgrades();
                break;
            case CHALLENGE:
                screens.push(new GoalsScreen(context, GoalsScreen.TAB_CHALLENGES));
                break;
            default:
                openShop();
                break;
        }
    }

    // ------------------------------------------------------------------ accessors

    /**
     * The START RUN call to action, also what {@link #playButton()} returns.
     *
     * @return the button
     */
    public CtaButton startRunButton() {
        return startRun;
    }

    /**
     * The Play control: START RUN.
     *
     * @return the button
     */
    public Button playButton() {
        return startRun;
    }

    /**
     * The gear that opens the settings.
     *
     * @return the button
     */
    public Button settingsButton() {
        return gear;
    }

    /**
     * The bottom navigation.
     *
     * @return the bar
     */
    public NavBar navBar() {
        return nav;
    }

    /**
     * One navigation item.
     *
     * @param id one of {@link #NAV_SHOP}, {@link #NAV_BIRDS}, {@link #NAV_PLAY},
     *     {@link #NAV_FORGE}, {@link #NAV_GOALS}
     * @return the item
     */
    public NavButton navButton(String id) {
        return nav.button(id);
    }

    /**
     * The player card.
     *
     * @return the card
     */
    public PlayerCard playerCard() {
        return playerCard;
    }

    /**
     * The coin chip (hidden when the session has no profile).
     *
     * @return the chip
     */
    public CurrencyChip coinsChip() {
        return coins;
    }

    /**
     * The wallet readout inside the coin chip.
     *
     * @return the display
     */
    public CurrencyDisplay walletDisplay() {
        return coins.display();
    }

    /**
     * The world plaque (hidden when the session has no profile).
     *
     * @return the plaque
     */
    public WorldPlaque worldPlaque() {
        return plaque;
    }

    /**
     * The next-unlock card (hidden when the session has no profile).
     *
     * @return the card
     */
    public NextUnlockCard nextUnlockCard() {
        return nextUnlock;
    }

    /**
     * The unlock the card points at.
     *
     * @return the unlock, or {@code null} when nothing measurable is left or without a profile
     */
    public UnlockEvaluator.NextUnlock nextUnlock() {
        return next;
    }

    /**
     * The world plaque's text (M7).
     *
     * @return the text, empty when the session has no profile or no worlds
     */
    public String worldLine() {
        return worldLine;
    }

    /**
     * The prestige badge on the player card (M9).
     *
     * @return the text, empty when the session has no profile or no prestige yet
     */
    public String prestigeBadge() {
        return prestigeLine;
    }

    /**
     * The last-run line under START RUN, as drawn.
     *
     * @return the text, empty when the session has no profile
     */
    public String lastRunLine() {
        return lastRunLine;
    }

    /**
     * The forge scene's stage.
     *
     * @return the stage, 0 without a profile
     */
    public int forgeStage() {
        return forgeStage;
    }

    /**
     * The palette the hub is drawn in: the selected world's.
     *
     * @return the palette
     */
    public WorldPalette hubPalette() {
        return palette;
    }

    /**
     * Whether the next Back quits.
     *
     * @return {@code true} while the confirmation is armed
     */
    public boolean quitArmed() {
        return quitArmedTicks > 0;
    }

    /**
     * Whether the attract demo is currently running behind the menu (M9).
     *
     * @return {@code true} once the idle timer fired, until any input cancels it
     */
    public boolean attractActive() {
        return attractActive;
    }

    /**
     * The bot-driven demo behind the menu (M9), for tests.
     *
     * @return the demo screen, or {@code null} in the headless launch
     */
    public DemoScreen demo() {
        return demo;
    }

    /**
     * Idle ticks counted since the last input, the stack changed or the menu was entered (M9).
     *
     * @return the count
     */
    public long attractIdleTicks() {
        return idleTicks;
    }

    /**
     * The focus ring (for tests inspecting focus).
     *
     * @return the ring
     */
    public FocusRing focusRing() {
        return ring;
    }

    /**
     * The toast queue this screen draws.
     *
     * @return the layer (shared with the rest of the application when there is a context)
     */
    public ToastLayer toasts() {
        return toasts;
    }

    /** Re-reads every visible label from the string table (a language switch). */
    public void refreshTexts() {
        startRun.setText(strings.get(StringKey.MENU_START_RUN));
        gear.setText(strings.get(StringKey.MENU_SETTINGS));
        coins.display().setFormat(strings.get(StringKey.HUD_COINS));
        nav.button(NAV_SHOP).setText(strings.get(StringKey.MENU_SHOP));
        nav.button(NAV_BIRDS).setText(strings.get(StringKey.MENU_BIRDS));
        nav.button(NAV_PLAY).setText(strings.get(StringKey.MENU_PLAY));
        nav.button(NAV_FORGE).setText(strings.get(StringKey.MENU_NAV_FORGE));
        nav.button(NAV_GOALS).setText(strings.get(StringKey.MENU_NAV_GOALS));
        shownLanguage = strings.language();
        refreshHub();
    }

    // ------------------------------------------------------------------ hub state

    /**
     * Rebuilds everything that depends on the profile: the player card, the coins, the plaque,
     * the palette and the backdrop, the forge scene, the next unlock, the START RUN subtitle and
     * the last-run line. Called on entry, on a language switch and from {@link #tick} whenever
     * {@link #hubStale()} says the profile moved on.
     */
    private void refreshHub() {
        PlayerProfile p = profile();
        PlayerLevel.Progress progress = rules().levels().progressWithin(p == null ? 0 : p.xp);
        int prestigeCount = p == null ? 0 : p.prestigeCount;
        prestigeLine = prestigeCount > 0
                ? strings.format(StringKey.MENU_PRESTIGE_BADGE, prestigeCount) : "";

        WorldDef world = null;
        if (meta && content.has(GameContent.WORLDS)
                && content.worlds().contains(p.selected.worldId)) {
            world = content.worlds().get(p.selected.worldId);
        }
        palette = world == null ? WorldPalette.GREEN_FIELDS : WorldPalette.from(world.palette());
        backdrop.setStyle(world == null ? WorldStyle.HILLS : WorldStyle.fromId(world.style()));
        screens.setLetterboxRgb(palette.letterbox());
        playerCard.bind(strings.get(StringKey.MENU_PLAYER_NAME),
                strings.format(StringKey.MENU_PLAYER_LEVEL, progress.level()),
                progress.maxed() ? 1 : progress.fraction(), prestigeLine, content, p,
                ProceduralArt.accentColor(palette));

        String worldName = world == null ? ""
                : ProgressionText.name(strings, ContentKind.WORLD, world.id());
        worldLine = world == null ? ""
                : strings.format(StringKey.MENU_WORLD_PLAQUE, world.order(), worldName);
        plaque.bind(worldLine, world == null ? null : world.palette());
        startRun.setSubtitle(world == null ? "" : strings.format(StringKey.MENU_RUN_SUBTITLE,
                worldName, ProgressionText.name(strings, ContentKind.TIER, p.selected.tierId)));

        next = meta ? evaluator.nextUnlock(p, content) : null;
        if (next != null) {
            AchievementEvaluator.Progress unlock = next.progress();
            nextUnlock.bind(strings.format(StringKey.MENU_NEXT_UNLOCK,
                    ProgressionText.unlockableName(strings, content, next.id())),
                    strings.format(StringKey.MILESTONES_PROGRESS, unlock.current(),
                            unlock.target()), unlock.fraction(), next.kind());
        } else {
            nextUnlock.bind(strings.get(StringKey.MENU_NEXT_UNLOCK_NONE), "", 0, null);
        }

        if (p == null) {
            lastRunLine = "";
        } else if (p.statistics.runHistory.isEmpty()) {
            lastRunLine = strings.get(StringKey.MENU_LAST_RUN_NONE);
        } else {
            Statistics.RunHistoryEntry last = p.statistics.runHistory.get(
                    p.statistics.runHistory.size() - 1);
            lastRunLine = strings.format(StringKey.MENU_LAST_RUN, last.gates, last.coins,
                    p.statistics.bestGates);
        }

        forgeStage = p == null ? 0 : ForgeScene.stageOf(p.upgradeLevelsTotal());
        forge.bind(palette, content, p, forgeStage, prestigeCount > 0);
        if (meta) {
            coins.display().setAmount(walletBalance());
        }
        snapshot(p);
    }

    /** Remembers what the hub was built from, for {@link #hubStale()}. */
    private void snapshot(PlayerProfile p) {
        shownProfile = p != null;
        shownWorldId = p == null ? null : p.selected.worldId;
        shownTierId = p == null ? null : p.selected.tierId;
        shownBirdId = p == null ? null : p.selected.birdId;
        shownPaletteId = p == null ? null : p.selected.paletteId;
        shownLevel = p == null ? -1 : p.level;
        shownXp = p == null ? -1 : p.xp;
        shownPrestigeCount = p == null ? -1 : p.prestigeCount;
        shownCoins = p == null ? -1 : walletBalance();
        shownUnlocked = p == null ? -1 : p.unlocked.size();
        shownUpgrades = p == null ? -1 : p.upgradeLevelsTotal();
        shownRuns = p == null ? -1 : p.statistics.runHistory.size();
        shownBestGates = p == null ? -1 : p.statistics.bestGates;
    }

    /** Whether the profile differs from what the hub shows. */
    private boolean hubStale() {
        PlayerProfile p = profile();
        if (p == null) {
            return shownProfile;
        }
        return !shownProfile
                || !Objects.equals(shownWorldId, p.selected.worldId)
                || !Objects.equals(shownTierId, p.selected.tierId)
                || !Objects.equals(shownBirdId, p.selected.birdId)
                || !Objects.equals(shownPaletteId, p.selected.paletteId)
                || shownLevel != p.level
                || shownXp != p.xp
                || shownPrestigeCount != p.prestigeCount
                || shownCoins != walletBalance()
                || shownUnlocked != p.unlocked.size()
                || shownUpgrades != p.upgradeLevelsTotal()
                || shownRuns != p.statistics.runHistory.size()
                || shownBestGates != p.statistics.bestGates;
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(startRun);
        boolean reduce = ParticleSystem.defaultReduceFlashing();
        particles.setReduceFlashing(reduce);
        forge.setReduceFlashing(reduce);
        startRun.setReduceFlashing(reduce);
        backdrop.setReduceFlashing(reduce);
        // The idle clock of the attract mode (M9) starts when the menu becomes visible, so the
        // first tick after the push counts as idle instead of being eaten by the transition.
        idleTicks = 0;
        stackVersionSeen = screens.stackVersion();
        quitArmedTicks = 0;
        startMenuMusic();
        // Rolling up rather than jumping: the coins a finished run paid are credited while the
        // game screen is still up, so this is the first frame the player can see them on.
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else {
            refreshHub();
        }
    }

    /**
     * Starts the menu loop (M8, D19): the Green Fields one at −6 dB
     * ({@link MusicSequencer#MENU_GAIN}), which the boot's audio step has already rendered and
     * prepared. The menu only ever <em>plays</em> a prepared loop — it never renders one — so a
     * test building the screen directly costs nothing, and in the application the boot step has
     * always run first. A run's loop that is still playing crossfades out as this one fades in;
     * re-entering the menu after a run therefore lands back on the menu music without a restart.
     */
    private void startMenuMusic() {
        if (context == null) {
            return;
        }
        String id = MusicSequencer.idForWorld(MENU_MUSIC_WORLD);
        if (context.audio().hasMusic(id)) {
            context.audio().startMusic(id, MusicSequencer.MENU_GAIN);
        }
    }

    @Override
    public void tick(InputFrame input) {
        ticks++;
        prevBob = bob;
        bob = bobAt(ticks);
        toasts.tick();
        coins.tick();
        particles.update(1.0 / Playfield.TICK_RATE);
        startRun.setTicks(ticks);
        playerCard.setTicks(ticks);
        if (quitArmedTicks > 0) {
            quitArmedTicks--;
        }
        if (screens.stackVersion() != stackVersionSeen) {
            // A screen came and went: whatever Back meant before it is forgotten.
            quitArmedTicks = 0;
        }
        boolean attractWasUp = attractActive;
        trackIdle(input);
        tickAttract(input);
        backdrop.tick(0.5, attractActive);
        clouds.tick(0.5, false);
        UiNode activated = ring.handle(input);
        if (activated != null) {
            particles.emitUiSparkle(activated.centerX(), activated.centerY(), palette.accent());
        }
        // The press that cancelled the attract demo is spent on that; it never arms the quit.
        if (input.isJustPressed(InputAction.BACK) && !(attractWasUp && !attractActive)) {
            if (quitArmedTicks > 0) {
                quitArmedTicks = 0;
                screens.requestClose();
            } else {
                quitArmedTicks = QUIT_ARM_TICKS;
                toasts.push(strings.get(StringKey.MENU_QUIT_CONFIRM), Toast.Kind.WARNING);
            }
        }
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else if (hubStale()) {
            refreshHub();
        }
        if (!attractActive && forge.sparkleDue(ticks)) {
            particles.emitUiSparkle(ForgeScene.ANVIL_CX, ForgeScene.ANVIL_TOP, palette.accent());
        }
    }

    private static double bobAt(long tick) {
        long t = tick % BOB_PERIOD_TICKS;
        double half = BOB_PERIOD_TICKS / 2.0;
        double wave = t < half ? t / half : (BOB_PERIOD_TICKS - t) / half;
        return -BOB_AMPLITUDE / 2 + BOB_AMPLITUDE * wave;
    }

    /**
     * Keeps the attract idle timer (M9). The counter grows on every tick that carries no input
     * and resets on one that does — and any input while the demo is up cancels the demo in the
     * same tick, so the menu never stops being the screen the player is talking to.
     *
     * <p>A change of the screen stack also resets the counter: a pop does not re-enter the menu
     * (D17), so returning from a run, the settings or the shop would otherwise leave the timer
     * holding whatever was counted before the player left, and the attract could fire seconds
     * after a real interaction that happened on another screen.
     *
     * @param input the tick's input
     */
    private void trackIdle(InputFrame input) {
        if (screens.stackVersion() != stackVersionSeen) {
            stackVersionSeen = screens.stackVersion();
            idleTicks = 0;
            return;
        }
        if (hasActivity(input)) {
            idleTicks = 0;
            if (attractActive) {
                stopAttract();
            }
            return;
        }
        idleTicks++;
    }

    /**
     * Whether the tick carries input from the player (M9): a key or button edge, a held key, the
     * wheel, a key capture, or the pointer having moved since the last tick. Held keys count
     * because holding a key <em>is</em> input, and the queue synthesises releases on focus loss
     * (D2), so a lost window cannot leave a ghost key blocking the attract forever.
     *
     * @param input the tick's input
     * @return {@code true} when the player did something this tick
     */
    private boolean hasActivity(InputFrame input) {
        boolean moved = mouseSeen && (input.mouseX() != prevMouseX || input.mouseY() != prevMouseY);
        prevMouseX = input.mouseX();
        prevMouseY = input.mouseY();
        mouseSeen = true;
        return moved || input.hasEdges() || input.wheel() != 0 || !input.held().isEmpty()
                || !input.rawKeyDowns().isEmpty();
    }

    /**
     * Drives the attract demo (M9). After {@value #ATTRACT_DELAY_TICKS} idle ticks the demo
     * starts behind the menu and is ticked from here, on the loop thread the menu already runs
     * on. A focus loss or an iconify freezes it in place — the same attention rule a live run
     * plays by (D2), minus the overlay, because there is nothing to resume with: without a
     * focus-gained event the frozen demo waits for the first input, which cancels it. The
     * {@code F11} handshake's synthetic focus loss does not count, exactly as for a run.
     *
     * @param input the tick's input
     */
    private void tickAttract(InputFrame input) {
        if (demo == null) {
            return;
        }
        List<RawInput.SystemEvent> events = input.systemEvents();
        for (int i = 0; i < events.size(); i++) {
            RawInput.SystemEvent event = events.get(i);
            if (event instanceof RawInput.FocusLost && !screens.isFullscreenHandshake()) {
                attractPaused = true;
            } else if (event instanceof RawInput.Iconified iconified) {
                attractPaused = iconified.iconified();
            }
        }
        if (!attractActive) {
            if (idleTicks >= ATTRACT_DELAY_TICKS) {
                attractActive = true;
                attractPaused = false;
                // The demo is alive in the same tick it appears, not one later.
                demo.tick(InputFrame.EMPTY);
            }
            return;
        }
        if (!attractPaused) {
            // The demo is flown by its bot; the frame it is handed is ignored (M9).
            demo.tick(InputFrame.EMPTY);
        }
    }

    /** Cancels the attract demo and frees its run, so the next idle period starts a fresh one. */
    private void stopAttract() {
        attractActive = false;
        attractPaused = false;
        if (demo != null) {
            demo.discard();
        }
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        boolean demoUp = attractActive && demo != null;
        if (demoUp) {
            // The attract backdrop (M9): the demo world first, the veil over it, the hub's
            // chrome on top of both — so the hub is the foreground throughout and the demo can
            // never take an input the hub did not see first. The world renderer paints its own
            // background, which is why the hub's backdrop and forge scene are skipped.
            demo.render(g, alpha);
            Overscan.fillVisible(g, ATTRACT_DIM);
        }
        ProceduralArt.prepare(g);
        double bobNow = MathUtil.lerp(prevBob, bob, alpha);
        if (!demoUp) {
            ProceduralArt.fillBackground(g, palette);
            backdrop.render(g, alpha, palette);
            clouds.render(g, alpha, palette);
            forge.render(g, alpha, bobNow, ticks);
        }

        ProceduralArt.drawAnvil(g, EMBLEM_CX, LOGO_ANVIL_TOP, LOGO_ANVIL_W,
                ProceduralArt.letterboxColor(palette));
        double phase = (ticks % WING_PERIOD_TICKS) / (double) WING_PERIOD_TICKS;
        ProceduralArt.drawBird(g, EMBLEM_CX, LOGO_ANVIL_TOP - LOGO_BIRD_SIZE * 0.38 + bobNow,
                LOGO_BIRD_SIZE, phase, palette);
        g.setFont(Fonts.bold(34));
        TextPainter.drawOutlined(g, strings.get(StringKey.APP_TITLE), EMBLEM_CX, TITLE_BASELINE,
                Align.CENTER, ProceduralArt.accentColor(palette),
                ProceduralArt.letterboxColor(palette), 3);
        g.setFont(Fonts.regular(12));
        TextPainter.drawOutlined(g, strings.get(StringKey.APP_TAGLINE), EMBLEM_CX,
                TAGLINE_BASELINE, Align.CENTER, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(palette), 2);

        nav.render(g);
        if (plaque.isVisible()) {
            plaque.render(g);
        }
        if (nextUnlock.isVisible()) {
            nextUnlock.render(g);
        }
        startRun.render(g);
        if (!lastRunLine.isEmpty()) {
            g.setFont(Fonts.regular(12));
            TextPainter.drawOutlined(g, lastRunLine, EMBLEM_CX, LAST_RUN_BASELINE, Align.CENTER,
                    ProceduralArt.TEXT_LIGHT, ProceduralArt.letterboxColor(palette), 2);
        }
        playerCard.render(g);
        if (coins.isVisible()) {
            coins.render(g);
        }
        gear.render(g);
        particles.render(g);
        toasts.render(g, TOAST_TOP_INSET);
    }
}
