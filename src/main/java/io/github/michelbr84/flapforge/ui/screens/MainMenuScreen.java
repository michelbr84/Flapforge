package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.Flapforge;
import io.github.michelbr84.flapforge.app.GameContext;
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
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.Panel;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import java.awt.Graphics2D;
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
 * <p>M4 adds Birds, Upgrades and Shop between Play and Statistics. They are built only when the
 * session actually has content and a profile behind it, because all three screens edit a profile;
 * a menu without one (a bare screen stack in a test, the headless launch) keeps the M2 entries and
 * lays the panel out for what it has.
 */
public final class MainMenuScreen implements Screen {

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
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
    private static final int PANEL_Y = 256;
    private static final int PANEL_W = Playfield.WIDTH - 2 * PANEL_X;
    private static final int BUTTON_H = 38;
    private static final int BUTTON_GAP = 6;
    private static final int WALLET_W = 130;
    private static final int FOOTER_BASELINE = Playfield.HEIGHT - 14;
    private static final int BUILD_BASELINE = Playfield.HEIGHT - 28;

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
    private final Button statistics;
    private final Button settings;
    private final Button quit;
    private final CurrencyDisplay wallet = new CurrencyDisplay();
    private final Strings strings;
    private String shownLanguage;
    private String versionLine;
    private String buildLine;
    private long ticks;
    private double prevBob;
    private double bob;

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
        this.particles = new ParticleSystem();
        play = panel.add(new Button("", this::startGame));
        // The meta-progression screens need content and a profile to read; a bare screen stack
        // (tests, tools, the headless launch) has neither, so the menu simply does not offer them.
        boolean meta = context != null && context.content() != null && context.profile() != null;
        birds = meta ? panel.add(new Button("", this::openBirds)) : null;
        upgrades = meta ? panel.add(new Button("", this::openUpgrades)) : null;
        shop = meta ? panel.add(new Button("", this::openShop)) : null;
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
        }
        statistics.setText(strings.get(StringKey.MENU_STATISTICS));
        settings.setText(strings.get(StringKey.MENU_SETTINGS));
        wallet.setFormat(strings.get(StringKey.HUD_COINS));
        quit.setText(strings.get(StringKey.MENU_QUIT));
        versionLine = strings.format(StringKey.FOOTER_VERSION, Flapforge.version());
        buildLine = strings.format(StringKey.FOOTER_BUILD, System.getProperty("java.version",
                "17"));
        shownLanguage = strings.language();
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(play);
        screens.setLetterboxRgb(PALETTE.letterbox());
        particles.setReduceFlashing(ParticleSystem.defaultReduceFlashing());
        // Rolling up rather than jumping: the coins a finished run paid are credited while the
        // game screen is still up, so this is the first frame the player can see them on.
        wallet.setVisible(context != null && context.profile() != null);
        wallet.setAmount(walletBalance());
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
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
        UiNode activated = ring.handle(input);
        if (activated != null) {
            particles.emitUiSparkle(activated.centerX(), activated.centerY(), PALETTE.accent());
        }
        if (input.isJustPressed(InputAction.BACK)) {
            ring.focus(quit);
        }
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
    }

    private static double bobAt(long tick) {
        long t = tick % BOB_PERIOD_TICKS;
        double half = BOB_PERIOD_TICKS / 2.0;
        double wave = t < half ? t / half : (BOB_PERIOD_TICKS - t) / half;
        return -BOB_AMPLITUDE / 2 + BOB_AMPLITUDE * wave;
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);

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
