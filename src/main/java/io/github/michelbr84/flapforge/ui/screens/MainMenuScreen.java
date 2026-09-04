package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.Flapforge;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.audio.MusicSequencer;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.Panel;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Objects;

/**
 * The main menu (D17), completed in M2: the Green Fields backdrop, a bird perched on an anvil
 * above the procedurally drawn title, and a panel with Play, Settings and Quit.
 *
 * <p>Everything the player reads comes from {@link Strings} and follows a live language switch:
 * the screen compares the table's language against the one its labels were built from and
 * rebuilds them when it changed, so returning from the settings screen in another language
 * cannot leave a stale word behind. Play pushes a {@link GameScreen} built from the injected
 * {@link SeededRunSource} and {@link SeedSequence} (so {@code --seed N} reaches the first run),
 * Settings pushes the real {@link SettingsScreen}, Quit asks the {@link ScreenManager} to close.
 * Arrows/Tab, Enter/Space, hover and click all work through the {@link FocusRing}; {@code Esc}
 * moves focus to Quit.
 *
 * <p>The shared {@link ToastLayer} is drawn here, so a message raised at boot (a settings file
 * that had to be reset) is still readable on the first screen the player sees.
 *
 * <p>M3 adds the Statistics entry and, when the session has a profile, a {@link CurrencyDisplay}
 * of the wallet in the top-right corner. It is refreshed on every entry, so the coins a run just
 * paid roll up in front of the player on the screen they land on rather than appearing as a
 * silently different number.
 *
 * <p>M4 adds Birds, Upgrades and Shop between Play and Statistics, and M8 adds Challenges and
 * Achievements after them. They are built only when the session actually has content and a profile
 * behind it, because all these screens edit a profile; a menu without one (a bare screen stack in
 * a test, the headless launch) keeps the M2 entries and lays the panel out for what it has. The
 * two extra M8 rows share the button height the panel fits into.
 *
 * <p>M9 adds the prestige badge under the world line (E4): "Prestige ×{0}" for a profile that
 * has started over, in the accent colour so it reads as a mark of rank rather than a status line.
 *
 * <p>M9 also adds the attract mode: after {@value #ATTRACT_DELAY_TICKS} ticks without any input
 * a bot-driven {@link DemoScreen} starts behind the menu, rendered dimmed under the veil the menu
 * draws on top of it. Any input — a key, a click, the wheel, the pointer moving — cancels it in
 * the tick it arrives and resets the idle timer; a focus loss or an iconify freezes it in place,
 * the same attention rule a live run plays by (D2). The demo is profile-less, writes nothing and
 * publishes nothing, so the menu loop keeps playing and the player's save is never touched; see
 * {@link DemoScreen} for the seed stream and the audio choice.
 */
public final class MainMenuScreen implements Screen {

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    /** World whose loop the menu plays — the plan's menu music is Green Fields (M8, D19). */
    private static final String MENU_MUSIC_WORLD = "green_fields";
    private static final int BOB_PERIOD_TICKS = 96;
    private static final double BOB_AMPLITUDE = 6;
    private static final int WING_PERIOD_TICKS = 48;
    private static final double EMBLEM_CX = Playfield.WIDTH / 2.0;
    private static final double ANVIL_TOP_Y = 118;
    private static final double ANVIL_W = 88;
    private static final double BIRD_SIZE = 56;
    private static final int TITLE_BASELINE = 208;
    private static final int TAGLINE_BASELINE = 242;
    private static final int PANEL_X = 70;
    private static final int PANEL_Y = 250;
    private static final int PANEL_W = Playfield.WIDTH - 2 * PANEL_X;
    private static final int BUTTON_H = 34;
    private static final int BUTTON_GAP = 4;
    private static final int WALLET_W = 130;
    private static final int FOOTER_BASELINE = Playfield.HEIGHT - 14;
    private static final int BUILD_BASELINE = Playfield.HEIGHT - 28;
    /** Baseline of the selected-world line in the top-left corner (M7). */
    public static final int WORLD_BASELINE = 30;
    /** Baseline of the prestige badge, under the world line (M9, E4). */
    public static final int PRESTIGE_BASELINE = WORLD_BASELINE + 18;
    /**
     * Ticks of idle input before the attract demo starts (M9): twenty seconds at the tick rate.
     */
    public static final int ATTRACT_DELAY_TICKS = 20 * Playfield.TICK_RATE;
    /** The veil the attract demo is shown under: dimmed, but the menu on top stays readable. */
    private static final Color ATTRACT_DIM = new Color(0, 0, 0, 0x66);

    private final ScreenManager screens;
    private final GameContext context;
    private final SeededRunSource runFactory;
    private final SeedSequence seeds;
    private final ToastLayer toasts;
    private final ParticleSystem particles;
    private final FocusRing ring = new FocusRing();
    private final Panel panel = new Panel();
    private final Button play;
    private final Button birds;
    private final Button upgrades;
    private final Button shop;
    private final Button challenges;
    private final Button achievements;
    private final Button statistics;
    private final Button settings;
    private final Button quit;
    private final CurrencyDisplay wallet = new CurrencyDisplay();
    private final Strings strings;
    private String shownLanguage;
    private String versionLine;
    private String buildLine;
    private String worldLine = "";
    private String shownWorldId;
    private String prestigeLine = "";
    private int shownPrestigeCount = -1;
    private long ticks;
    private double prevBob;
    private double bob;
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
     * @param runFactory builds the run the game screen plays
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
        // The attract demo (M9) exists everywhere but in the headless launch: there is no
        // renderer there to show it, and the CI run the published determinism hash is read from
        // stays exactly as heavy as it was. Bare screen stacks (tests, tools) get one too, so
        // the mode is testable without a session.
        this.demo = context != null && context.options().headless() ? null
                : new DemoScreen(demoSource(), BotPilot.Preset.AVERAGE, strings);
        this.particles = new ParticleSystem();
        play = panel.add(new Button("", this::startGame));
        // The meta-progression screens need content and a profile to read; a bare screen stack
        // (tests, tools, the headless launch) has neither, so the menu simply does not offer them.
        boolean meta = context != null && context.content() != null && context.profile() != null;
        birds = meta ? panel.add(new Button("", this::openBirds)) : null;
        upgrades = meta ? panel.add(new Button("", this::openUpgrades)) : null;
        shop = meta ? panel.add(new Button("", this::openShop)) : null;
        challenges = meta ? panel.add(new Button("", this::openChallenges)) : null;
        achievements = meta ? panel.add(new Button("", this::openAchievements)) : null;
        statistics = panel.add(new Button("", this::openStatistics));
        settings = panel.add(new Button("", this::openSettings));
        quit = panel.add(new Button("", screens::requestClose));
        panel.setBounds(PANEL_X, PANEL_Y, PANEL_W,
                Panel.columnHeight(panel.children().size(), BUTTON_H, BUTTON_GAP,
                        Panel.DEFAULT_PADDING));
        panel.layoutColumn(BUTTON_H, BUTTON_GAP);
        panel.registerFocusables(ring);
        wallet.setBounds(Playfield.WIDTH - WALLET_W - 14.0, 14, WALLET_W, 26);
        wallet.setAlign(Align.RIGHT);
        wallet.setVisible(context != null && context.profile() != null);
        wallet.setAmountNow(walletBalance());
        refreshTexts();
    }

    /**
     * The coins of the session's profile.
     *
     * @return the balance, 0 when the session has no profile
     */
    private long walletBalance() {
        PlayerProfile p = context == null ? null : context.profile();
        Long coins = p == null ? null : p.wallet.get(PlayerProfile.CURRENCY_COINS);
        return coins == null ? 0 : coins;
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

    private void startGame() {
        screens.push(context != null ? new GameScreen(context, runFactory, seeds)
                : new GameScreen(screens, runFactory, seeds));
    }

    private void openSettings() {
        screens.push(context != null ? new SettingsScreen(context) : new SettingsScreen(screens));
    }

    private void openStatistics() {
        screens.push(context != null ? new StatisticsScreen(context)
                : new StatisticsScreen(screens));
    }

    private void openBirds() {
        screens.push(new BirdSelectionScreen(context));
    }

    private void openUpgrades() {
        screens.push(new UpgradeTreeScreen(context));
    }

    private void openShop() {
        screens.push(new ShopScreen(context));
    }

    private void openChallenges() {
        screens.push(new ChallengesScreen(context));
    }

    private void openAchievements() {
        screens.push(new AchievementsScreen(context));
    }

    /**
     * The Birds button.
     *
     * @return the button, or {@code null} when the session has no profile to show
     */
    public Button birdsButton() {
        return birds;
    }

    /**
     * The Upgrades button.
     *
     * @return the button, or {@code null} when the session has no profile to show
     */
    public Button upgradesButton() {
        return upgrades;
    }

    /**
     * The Shop button.
     *
     * @return the button, or {@code null} when the session has no profile to show
     */
    public Button shopButton() {
        return shop;
    }

    /**
     * The Challenges button (M8).
     *
     * @return the button, or {@code null} when the session has no profile to show
     */
    public Button challengesButton() {
        return challenges;
    }

    /**
     * The Achievements button (M8).
     *
     * @return the button, or {@code null} when the session has no profile to show
     */
    public Button achievementsButton() {
        return achievements;
    }

    /**
     * The Play button.
     *
     * @return the button
     */
    public Button playButton() {
        return play;
    }

    /**
     * The Statistics button.
     *
     * @return the button
     */
    public Button statisticsButton() {
        return statistics;
    }

    /**
     * The Settings button.
     *
     * @return the button
     */
    public Button settingsButton() {
        return settings;
    }

    /**
     * The wallet readout (hidden when the session has no profile).
     *
     * @return the display
     */
    public CurrencyDisplay walletDisplay() {
        return wallet;
    }

    /**
     * The Quit button.
     *
     * @return the button
     */
    public Button quitButton() {
        return quit;
    }

    /**
     * The selected-world line, as drawn (M7).
     *
     * @return the text, empty when the session has no profile or no worlds
     */
    public String worldLine() {
        return worldLine;
    }

    /**
     * Rebuilds the selected-world line from the profile. Called on entry, on a language switch
     * and from {@link #tick} whenever the selection differs from the one shown: a pop back from
     * the bird selection does not re-enter the menu (D17), so the line has to notice on its own.
     */
    private void refreshWorldLine() {
        worldLine = "";
        shownWorldId = null;
        if (context == null || context.profile() == null || context.content() == null
                || !context.content().has(GameContent.WORLDS)) {
            return;
        }
        String id = context.profile().selected.worldId;
        shownWorldId = id;
        if (context.content().worlds().contains(id)) {
            worldLine = strings.format(StringKey.MENU_WORLD,
                    ProgressionText.name(strings, ContentKind.WORLD, id));
        }
    }

    /** Whether the world line names a world other than the profile's current selection. */
    private boolean worldLineStale() {
        if (context == null || context.profile() == null) {
            return false;
        }
        String id = context.profile().selected.worldId;
        return id == null ? shownWorldId != null : !id.equals(shownWorldId);
    }

    /**
     * Rebuilds the prestige badge from the profile (M9, E4): "Prestige ×{0}" while the profile
     * has banked at least one prestige, nothing before the first one. Like the world line, it
     * notices on its own when the count changed — a prestige performed in the statistics screen
     * is visible here the moment the player pops back.
     */
    private void refreshPrestigeBadge() {
        prestigeLine = "";
        shownPrestigeCount = -1;
        if (context == null || context.profile() == null) {
            return;
        }
        int count = context.profile().prestigeCount;
        shownPrestigeCount = count;
        if (count > 0) {
            prestigeLine = strings.format(StringKey.MENU_PRESTIGE_BADGE, count);
        }
    }

    /** Whether the badge shows a count other than the profile's current one. */
    private boolean prestigeBadgeStale() {
        if (context == null || context.profile() == null) {
            return !prestigeLine.isEmpty();
        }
        return context.profile().prestigeCount != shownPrestigeCount;
    }

    /**
     * The prestige badge, as drawn (M9).
     *
     * @return the text, empty when the session has no profile or no prestige yet
     */
    public String prestigeBadge() {
        return prestigeLine;
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
        play.setText(strings.get(StringKey.MENU_PLAY));
        if (birds != null) {
            birds.setText(strings.get(StringKey.MENU_BIRDS));
            upgrades.setText(strings.get(StringKey.MENU_UPGRADES));
            shop.setText(strings.get(StringKey.MENU_SHOP));
            challenges.setText(strings.get(StringKey.MENU_CHALLENGES));
            achievements.setText(strings.get(StringKey.MENU_ACHIEVEMENTS));
        }
        statistics.setText(strings.get(StringKey.MENU_STATISTICS));
        settings.setText(strings.get(StringKey.MENU_SETTINGS));
        wallet.setFormat(strings.get(StringKey.HUD_COINS));
        quit.setText(strings.get(StringKey.MENU_QUIT));
        versionLine = strings.format(StringKey.FOOTER_VERSION, Flapforge.version());
        buildLine = strings.format(StringKey.FOOTER_BUILD, System.getProperty("java.version",
                "17"));
        refreshWorldLine();
        refreshPrestigeBadge();
        shownLanguage = strings.language();
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(play);
        screens.setLetterboxRgb(PALETTE.letterbox());
        particles.setReduceFlashing(ParticleSystem.defaultReduceFlashing());
        // The idle clock of the attract mode (M9) starts when the menu becomes visible, so the
        // first tick after the push counts as idle instead of being eaten by the transition.
        idleTicks = 0;
        stackVersionSeen = screens.stackVersion();
        startMenuMusic();
        // Rolling up rather than jumping: the coins a finished run paid are credited while the
        // game screen is still up, so this is the first frame the player can see them on.
        wallet.setVisible(context != null && context.profile() != null);
        wallet.setAmount(walletBalance());
        refreshWorldLine();
        refreshPrestigeBadge();
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
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
        wallet.tick();
        particles.update(1.0 / Playfield.TICK_RATE);
        trackIdle(input);
        tickAttract(input);
        UiNode activated = ring.handle(input);
        if (activated != null) {
            particles.emitUiSparkle(activated.centerX(), activated.centerY(), PALETTE.accent());
        }
        if (input.isJustPressed(InputAction.BACK)) {
            ring.focus(quit);
        }
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else if (worldLineStale()) {
            refreshWorldLine();
        } else if (prestigeBadgeStale()) {
            refreshPrestigeBadge();
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
            // The attract backdrop (M9): the demo world first, the veil over it, the menu on
            // top of both — so the menu is the foreground throughout and the demo can never
            // take an input the menu did not see first. The world renderer paints its own
            // background, which is why the menu's own sky fill is skipped while it is up.
            demo.render(g, alpha);
            g.setColor(ATTRACT_DIM);
            g.fillRect(0, 0, Playfield.WIDTH, Playfield.HEIGHT);
        }
        ProceduralArt.prepare(g);
        if (!demoUp) {
            ProceduralArt.fillBackground(g, PALETTE);
        }

        ProceduralArt.drawAnvil(g, EMBLEM_CX, ANVIL_TOP_Y, ANVIL_W,
                ProceduralArt.letterboxColor(PALETTE));
        double birdY = ANVIL_TOP_Y - BIRD_SIZE * 0.38 + MathUtil.lerp(prevBob, bob, alpha);
        double phase = (ticks % WING_PERIOD_TICKS) / (double) WING_PERIOD_TICKS;
        ProceduralArt.drawBird(g, EMBLEM_CX, birdY, BIRD_SIZE, phase, PALETTE);

        g.setFont(Fonts.bold(58));
        TextPainter.drawOutlined(g, strings.get(StringKey.APP_TITLE), EMBLEM_CX, TITLE_BASELINE,
                Align.CENTER, ProceduralArt.accentColor(PALETTE),
                ProceduralArt.letterboxColor(PALETTE), 3);
        g.setFont(Fonts.regular(16));
        // Outlined, not plain: M4's re-layout moved the title block up into the cloud band to fit
        // the three new buttons, and the tagline now crosses the cloud at (190, 236).
        TextPainter.drawOutlined(g, strings.get(StringKey.APP_TAGLINE), EMBLEM_CX,
                TAGLINE_BASELINE, Align.CENTER, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(PALETTE), 2);

        panel.render(g);
        if (wallet.isVisible()) {
            wallet.render(g);
        }
        if (!worldLine.isEmpty()) {
            g.setFont(Fonts.bold(13));
            TextPainter.drawOutlined(g, worldLine, 14, WORLD_BASELINE, Align.LEFT,
                    ProceduralArt.TEXT_LIGHT, ProceduralArt.letterboxColor(PALETTE), 2);
        }
        if (!prestigeLine.isEmpty()) {
            g.setFont(Fonts.bold(13));
            TextPainter.drawOutlined(g, prestigeLine, 14, PRESTIGE_BASELINE, Align.LEFT,
                    ProceduralArt.accentColor(PALETTE), ProceduralArt.letterboxColor(PALETTE), 2);
        }
        particles.render(g);

        g.setFont(Fonts.regular(12));
        g.setColor(ProceduralArt.TEXT_DARK);
        TextPainter.draw(g, versionLine, 12, FOOTER_BASELINE);
        TextPainter.draw(g, buildLine, 12, BUILD_BASELINE);
        TextPainter.drawRight(g, strings.get(StringKey.FOOTER_KEYS), Playfield.WIDTH - 12,
                FOOTER_BASELINE);

        toasts.render(g);
    }
}
