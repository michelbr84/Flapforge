package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.audio.AudioBackend;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.audio.MusicSequencer;
import io.github.michelbr84.flapforge.audio.NullAudio;
import io.github.michelbr84.flapforge.audio.SoundBank;
import io.github.michelbr84.flapforge.content.ContentAdapters;
import io.github.michelbr84.flapforge.content.ContentException;
import io.github.michelbr84.flapforge.content.ContentValidator;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.persistence.SaveFile;
import io.github.michelbr84.flapforge.persistence.SaveManager;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.persistence.Settings;
import io.github.michelbr84.flapforge.persistence.SettingsStore;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.AchievementEvaluator;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionOutcome;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.SelectionManager;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.render.AssetManager;
import io.github.michelbr84.flapforge.render.AssetResolver;
import io.github.michelbr84.flapforge.render.DebugOverlay;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.BootScreen;
import io.github.michelbr84.flapforge.ui.screens.ContentRunFactory;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Wires threads, clocks, window, presenter, input bridge, screen manager and loop, and owns
 * the clean shutdown sequence (D4).
 *
 * <p>Both launches load {@link GameContent} once, before anything else, and hand it to the
 * screens as a {@link ContentRunFactory}: every run the player plays is built from the shipped
 * {@code data/*.json} (D10, D11). Content that fails to bind or validate prints every error and
 * aborts the launch — a broken data file must never reach a window.
 *
 * <p>Profile (D14, D15, M3): a windowed launch also opens the {@link SaveManager} on
 * {@link Threads#saveExecutor()} with the injected {@link SystemTimeSource}, loads the profile
 * before the window exists, and turns a load that has something to report — the backup was used,
 * the file was unusable, the file is newer than this build — into a warning toast. The economy
 * becomes {@link ProgressionRules}; {@code GameScreen} writes each finished run through the
 * {@link ProgressionManager} and saves. {@code --reset-save} quarantines the old file first and
 * says so on stdout. A headless run has none of this: it must depend on the seed and the shipped
 * data alone.
 *
 * <p>Windowed launch: the window, the presenter and the input bridge come from the
 * {@link GameHost} the launch was started with (M10, D8) — on the desktop {@code AwtHost} builds
 * the window on the event-dispatch thread — the loop runs on the non-daemon
 * {@code flapforge-loop} thread and the main thread returns. Without a display the host answers
 * no window, and the launch prints a hint and returns (no {@code System.exit}). Quit path:
 * {@code CloseRequested} (window button, Quit in the menu) stops the loop after the current
 * frame; the loop thread then disposes the presenter, detaches the bridge, disposes the window
 * (on the desktop: the frame, on the event-dispatch thread) and stops the executors, after which
 * the JVM exits naturally. A daemon watchdog calls {@code System.exit} only if the JVM is still
 * alive five seconds later; a test harness that hosts the application in a long-lived JVM
 * cancels it with {@link #awaitShutdown(long)}.
 *
 * <p>Nothing in this class names a toolkit type: the file is part of the Android source
 * transform, and every AWT class it would otherwise need sits behind the host.
 *
 * <p>Audio (D19, E30.j): {@link AudioBackend#create(boolean, java.util.concurrent.ThreadFactory)}
 * is the only place a device is opened, and it never throws — {@code --no-audio} and a machine
 * with no usable output line both yield {@link NullAudio} after a single logged line. The
 * {@link AudioManager} subscribes to the event bus on the loop thread (the bus is confined to it),
 * reads its volumes off the {@code SettingsChanged} that {@link GameContext#applySettings} raises,
 * warms the device up as the last {@link BootSequence} step while the splash is on screen, and is
 * closed with the loop.
 *
 * <p>Headless launch ({@code --headless-run N}): the loop is driven for N frames with a stepping
 * clock (one tick per frame) and a {@link NullPresenter}; a summary line is printed, followed by
 * the determinism line {@code hash=<16 hex> ticks=<n> gates=<g> points=<p>} produced by
 * {@link #simulationHashLine(GameContent, long, int)} — the artefact CI compares across
 * operating systems and JDKs (D12, E12).
 */
public final class GameApplication {

    /** Delay before the watchdog forces the JVM to exit after a clean shutdown began. */
    public static final long WATCHDOG_MS = 5_000L;

    /** Ticks between two autosaves of a dirty profile (D15: every 60 s at 60 Hz). */
    public static final int AUTOSAVE_TICKS = 3_600;

    /** Seed of a headless run started without {@code --seed} (the CI reference seed, D12). */
    public static final long DEFAULT_HEADLESS_SEED = 42L;

    /**
     * World whose loop the menu plays (M8, D19): the plan's menu music is Green Fields at
     * −6 dB, which is {@link MusicSequencer#MENU_GAIN}.
     */
    public static final String MENU_WORLD = "green_fields";

    /** Manifest id of the bundled UI font (D18, §4). */
    public static final String FONT_ID = "font/ui";

    /** Cached display refresh rate in Hz; {@code 0} means "not resolved yet". */
    private static volatile int refreshRateHz;

    /**
     * The host of the last {@link #start(LaunchOptions, GameHost)}: the one the static
     * {@link #detectRefreshRate()} asks, since {@link GameContext#resolveFps(int)} has no
     * instance to reach it through. {@code null} until an application was started.
     */
    private static volatile GameHost host;

    private final LaunchOptions options;
    private final GameHost gameHost;
    private GameContext context;
    private int ticksSinceAutosave;
    private volatile Thread loopThread;
    private volatile Thread watchdog;

    /**
     * Creates the application.
     *
     * @param options the launch options
     * @param host the platform behind the window, the presenter and the input bridge
     */
    public GameApplication(LaunchOptions options, GameHost host) {
        this.options = options;
        this.gameHost = Objects.requireNonNull(host, "host");
    }

    /**
     * Builds and starts the application on the given host.
     *
     * <p>The host is also installed as the one {@link #detectRefreshRate()} reads from; a host
     * that differs from the previous launch's drops the cached rate, since the display it
     * describes may be another one.
     *
     * @param options the launch options
     * @param host the platform seam ({@code AwtHost} on the desktop)
     * @return the application
     */
    public static GameApplication start(LaunchOptions options, GameHost host) {
        GameApplication app = new GameApplication(options, host);
        if (GameApplication.host != host) {
            GameApplication.host = host;
            forgetRefreshRate();
        }
        app.launch();
        return app;
    }

    /**
     * The wired services (available after {@link #launch()}).
     *
     * @return the context, or {@code null} before launch
     */
    public GameContext context() {
        return context;
    }

    /**
     * The {@code flapforge-loop} thread of a windowed launch.
     *
     * @return the thread, or {@code null} before launch or when headless
     */
    public Thread loopThread() {
        return loopThread;
    }

    /**
     * Waits for the loop thread of a windowed launch to finish its shutdown sequence and, when
     * it did, cancels the exit watchdog so a hosting JVM (tests) keeps running.
     *
     * @param timeoutMs how long to wait for the loop thread
     * @return {@code true} when the loop thread ended within the timeout
     * @throws InterruptedException when the caller is interrupted
     */
    public boolean awaitShutdown(long timeoutMs) throws InterruptedException {
        Thread loop = loopThread;
        if (loop == null) {
            return true;
        }
        loop.join(timeoutMs);
        if (loop.isAlive()) {
            return false;
        }
        Thread dog = watchdog;
        if (dog != null) {
            dog.interrupt();
            dog.join(1_000L);
        }
        return true;
    }

    /** Launches in headless or windowed mode according to the options. */
    public void launch() {
        if (options.headless()) {
            runHeadless();
        } else {
            runWindowed();
        }
    }

    private void runHeadless() {
        GameContent content = loadContent();
        if (content == null) {
            return;
        }
        int frames = Math.max(1, options.headlessRun());
        Threads threads = new Threads();
        SteppingClock clock = new SteppingClock();
        TimeSource timeSource = new SystemTimeSource();
        Settings settings = Settings.defaults().normalize();
        Strings strings = loadStrings(options, settings);
        InputQueue input = new InputQueue(settings.bindings());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter();
        screens.setPresenter(presenter);
        FrameLimiter limiter = FrameLimiter.uncapped(clock);
        GameLoop loop = new GameLoop(clock, input, screens, presenter, limiter);
        screens.setCloseHandler(loop::stop);
        SeedSequence seeds = SeedSequence.from(options.seed());
        // A headless run never opens a device: NullAudio keeps the context uniform without it.
        AudioManager audio = new AudioManager(new NullAudio());
        // A headless run neither reads nor writes a profile: it must depend on the seed and the
        // shipped data alone (D12), so there is nothing to progress and nothing to save.
        context = new GameContext(options, clock, timeSource, threads, input, viewport, screens,
                presenter, null, loop, limiter, null, new EventBus(), audio, strings,
                new ToastLayer(), content, null, null, null);
        ContentRunFactory headlessRuns = runFactory(content, seeds);
        String world = pinWorld(headlessRuns, content, null, null);
        screens.push(new MainMenuScreen(context, headlessRuns, seeds));
        screens.applyPending();

        loop.start();
        for (int i = 0; i < frames; i++) {
            clock.step(Playfield.TICK_NS);
            loop.frame();
        }
        long seed = options.seed() == null ? DEFAULT_HEADLESS_SEED : options.seed();
        System.out.println("headless-run frames=" + loop.frameCount() + " ticks=" + loop.tickCount()
                + " presents=" + presenter.presentCount() + " seed=" + seed);
        System.out.println(simulationHashLine(content, seed, frames, world));
        drainSaves(threads);
    }

    /**
     * Reads the profile, or wipes it first for {@code --reset-save} (D15).
     *
     * <p>{@code --reset-save} is confirmed on stdout, not in a dialog: it is a command-line flag,
     * so its answer belongs on the command line, and the old file is quarantined rather than
     * deleted, so the line has something useful to name.
     *
     * @param save the manager to load through
     * @return what the load produced
     */
    private SaveManager.LoadResult loadProfile(SaveManager save) {
        if (!options.resetSave()) {
            return save.load();
        }
        SaveManager.LoadResult reset = save.resetToFresh();
        System.out.println("--reset-save: " + reset.detail());
        return reset;
    }

    /**
     * The toast a load with a notice deserves (D15): the backup was used, the file was unusable,
     * or the file is newer than this build and nothing will be written this session.
     *
     * @param strings the string table
     * @param save the manager (it names the files)
     * @param loaded the load result
     * @return the toast text
     */
    private static String saveNotice(Strings strings, SaveManager save,
            SaveManager.LoadResult loaded) {
        switch (loaded.status()) {
            case RESTORED_FROM_BACKUP:
                return strings.format(StringKey.TOAST_SAVE_RESTORED,
                        save.backupFile().getFileName());
            case RESET_AFTER_CORRUPT:
                return strings.format(StringKey.TOAST_SAVE_RESET,
                        loaded.quarantined() == null ? save.file().getFileName()
                                : loaded.quarantined().getFileName());
            case UNREADABLE:
                return strings.format(StringKey.TOAST_SAVE_UNREADABLE,
                        save.file().getFileName());
            case REFUSED_NEWER_VERSION:
                return strings.get(StringKey.TOAST_SAVE_READ_ONLY);
            default:
                return loaded.detail();
        }
    }

    /**
     * Loads the string table for the launch: {@code --lang} wins over {@code settings.language},
     * and {@code auto} resolves against the system locale (D25).
     *
     * @param options the launch options
     * @param settings the loaded settings (its language is overwritten by {@code --lang})
     * @return the table
     */
    private static Strings loadStrings(LaunchOptions options, Settings settings) {
        if (options.lang() != null && Settings.LANGUAGES.contains(options.lang())) {
            settings.language = options.lang();
        }
        Strings strings = Strings.load(settings.resolvedLanguage(GameContext.systemLanguage()));
        Strings.use(strings);
        return strings;
    }

    /**
     * Loads the shipped content, printing every binding and validation error before giving up.
     *
     * @return the content, or {@code null} when it failed to load (the launch must abort)
     */
    private static GameContent loadContent() {
        try {
            GameContent content = GameContent.load();
            for (String warning : ContentValidator.warningsOf(content)) {
                System.err.println("Content warning: " + warning);
            }
            return content;
        } catch (ContentException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    /**
     * The factory both launches play through: the shipped content, in the mode the seed implies.
     *
     * @param content the loaded content
     * @param seeds the seed source
     * @return the factory
     */
    private static ContentRunFactory runFactory(GameContent content, SeedSequence seeds) {
        return runFactory(content, seeds, null);
    }

    /**
     * The factory the windowed launch plays through: the same content and mode, plus the live
     * profile, so every run carries the selection and the upgrades the player owns (D14, M4).
     *
     * @param content the loaded content
     * @param seeds the seed source
     * @param ctx the session context, or {@code null} for a launch without a profile
     * @return the factory
     */
    private static ContentRunFactory runFactory(GameContent content, SeedSequence seeds,
            GameContext ctx) {
        return new ContentRunFactory(content,
                seeds.isExplicit() ? RunMode.SEEDED : RunMode.STANDARD,
                ctx == null ? null : ctx::profile);
    }

    /**
     * Applies {@code --world} (M7): the named world is pinned on the run factory for this launch.
     * When the profile owns it the selection is written too, as the world picker would; when it
     * does not, the profile is left alone and a line says so — the flag is a launch override, not
     * an unlock, so the next launch without it is back to the owned selection. An id the content
     * does not ship is reported and ignored.
     *
     * @param runs the factory every run comes from
     * @param content the loaded content
     * @param profile the live profile, or {@code null} in a launch without one
     * @param progression the write path of the profile, or {@code null} without one
     * @return the pinned world id, or {@code null} when the flag was absent or ignored
     */
    private String pinWorld(ContentRunFactory runs, GameContent content, PlayerProfile profile,
            ProgressionManager progression) {
        String world = options.world();
        if (world == null) {
            return null;
        }
        if (!content.worlds().contains(world)) {
            System.err.println("--world " + world + ": no such world; playing the selected one ("
                    + content.worlds().ids() + ")");
            return null;
        }
        runs.withWorld(world);
        if (profile != null && progression != null) {
            SelectionManager selection = new SelectionManager(progression, null);
            if (selection.selectWorld(profile, world, content)) {
                System.out.println("--world " + world + ": selected");
            } else {
                System.out.println("--world " + world + ": not unlocked in this profile (world:"
                        + world + "); playing it for this launch only, the selection stays "
                        + profile.selected.worldId);
            }
        }
        return world;
    }

    /**
     * The {@code aliases.json} step of the load (E21), as the save manager runs it: on the bound
     * profile, before normalisation.
     *
     * <p>It has to sit inside the load rather than after it. Normalisation resets every selection
     * id no registry knows and writes the unlocks an owned id implies; running the renames after
     * it would mean a renamed bird had already fallen back to the default and a renamed ability
     * had already been written into {@code unlocked} under its old name.
     *
     * @param content the loaded content, which carries the table
     * @return the step to hand to {@link SaveManager#profileAliasStep}
     */
    private static SaveManager.ProfileAliasStep aliasStep(GameContent content) {
        if (content.aliases() == null || content.aliases().isEmpty()) {
            return SaveManager.ProfileAliasStep.NONE;
        }
        String currency = ProgressionRules.fromContent(content).primaryCurrency();
        return profile -> UpgradeManager.reconcile(profile, content.aliases(), currency);
    }

    /**
     * Grants, once at startup, every unlock and every achievement the loaded profile already
     * satisfies (D13, D14).
     *
     * <p>The evaluators otherwise run only at the end of a run and after a purchase, so a profile
     * written before an unlockable existed — every profile carried over from M3 into M4, every
     * profile carried into M8 with its lifetime statistics already past an achievement's
     * threshold, and any profile at all after a threshold is lowered or a new unlockable ships —
     * would open the game with what it has already earned still locked, and the shop would sell
     * it. The pass is {@link ProgressionManager#applyPurchase}, which is exactly D14's
     * "achievements → unlocks → dirty" tail (it judges no run, so the {@code RUN}-scoped
     * achievements wait for their run), and the profile is written back only when something was
     * actually granted.
     *
     * @param save the manager holding the loaded profile
     * @param progression the write path
     */
    private static void grantWhatIsAlreadyEarned(SaveManager save, ProgressionManager progression) {
        PlayerProfile profile = save.profile();
        if (profile == null) {
            return;
        }
        ProgressionOutcome outcome = progression.applyPurchase(profile);
        List<String> granted = outcome.unlocksGranted();
        List<String> achievements = outcome.achievementsUnlocked();
        if (granted.isEmpty() && achievements.isEmpty()) {
            // The pass wrote nothing, so the profile is not dirty: leaving the mark on would make
            // the 60-second autosave rewrite an unchanged file once per session (D15).
            progression.clearDirty();
            return;
        }
        if (!granted.isEmpty()) {
            System.out.println("save unlocked what was already earned: "
                    + String.join(", ", granted));
        }
        if (!achievements.isEmpty()) {
            System.out.println("save granted the achievements already earned: "
                    + String.join(", ", achievements));
        }
        if (save.save()) {
            progression.markSaveQueued();
        }
    }

    /**
     * Runs the shipped content headlessly with the deterministic pilot and renders the CI line
     * (D12, E12): {@code hash=<16 hex> ticks=<n> gates=<g> points=<p>}.
     *
     * <p>The line is what the build workflow compares across ubuntu/windows/macos and JDK 17/21
     * — it must depend only on the seed, the shipped {@code data/*.json} and the simulation, so
     * nothing here touches the window, the clock or the frame loop. The golden fixture is a
     * different guarantee (frozen content, asserted in {@code GoldenRunTest}); this one moves
     * with the shipped balance on purpose.
     *
     * @param content the loaded content
     * @param seed the run seed
     * @param maxTicks the tick budget
     * @return the line to print
     */
    static String simulationHashLine(GameContent content, long seed, int maxTicks) {
        return simulationHashLine(content, seed, maxTicks, null);
    }

    /**
     * {@link #simulationHashLine(GameContent, long, int)} in a pinned world ({@code --world}):
     * without the flag the configuration is {@code RunConfig.classic(seed)}, so the published
     * hash is untouched.
     *
     * @param content the loaded content
     * @param seed the run seed
     * @param maxTicks the tick budget
     * @param worldId the world to play, or {@code null} for the classic configuration
     * @return the line to print
     */
    static String simulationHashLine(GameContent content, long seed, int maxTicks,
            String worldId) {
        RunConfig config = worldId == null ? RunConfig.classic(seed)
                : RunConfig.builder(seed).worldId(worldId).build();
        Run run = new RunFactory(content).newRun(config);
        HeadlessRunner.Outcome outcome =
                HeadlessRunner.run(run, new BotPilot(BotPilot.Preset.PERFECT, seed), maxTicks, true);
        long hash = MathUtil.fnv1a64("flapforge-headless");
        for (Long tickHash : outcome.hashes()) {
            hash = MathUtil.fold(hash, tickHash);
        }
        double points = outcome.result().stats().points();
        String pointsText = points == Math.rint(points) && !Double.isInfinite(points)
                ? Long.toString((long) points) : Double.toString(points);
        return String.format(Locale.ROOT, "hash=%016x ticks=%d gates=%d points=%s", hash,
                outcome.ticks(), outcome.result().gatesPassed(), pointsText);
    }

    private void runWindowed() {
        GameContent content = loadContent();
        if (content == null) {
            return;
        }
        Threads threads = new Threads();
        Clock clock = new SystemClock();
        TimeSource timeSource = new SystemTimeSource();
        if (options.home() != null) {
            SavePaths.override(options.home());
        }
        SettingsStore settingsStore = new SettingsStore(threads.saveExecutor());
        SettingsStore.LoadResult loaded = settingsStore.load();
        Settings settings = loaded.settings();
        settings.fullscreen = settings.fullscreen || options.fullscreen();
        Strings strings = loadStrings(options, settings);
        ToastLayer toasts = new ToastLayer();
        if (loaded.hasNotice()) {
            toasts.push(strings.format(StringKey.TOAST_SETTINGS_RESET,
                    loaded.archived() == null ? settingsStore.file().getFileName()
                            : loaded.archived().getFileName()), Toast.Kind.WARNING);
        }
        SaveManager save = new SaveManager(threads.saveExecutor(), timeSource)
                .stamp(AppVersion.version(), SaveFile.CONTENT_VERSION)
                // From M4 the registries exist, so a saved id the content no longer knows is
                // repaired instead of kept (E15, E21).
                .schema(ContentAdapters.toProfileSchema(content))
                // E21: the renames run inside the load, between binding and normalising.
                .profileAliasStep(aliasStep(content));
        SaveManager.LoadResult profileLoad = loadProfile(save);
        if (profileLoad.hasNotice()) {
            toasts.push(saveNotice(strings, save, profileLoad), Toast.Kind.WARNING);
        }
        for (String repair : profileLoad.repairs()) {
            System.out.println("save repaired: " + repair);
        }
        // The two evaluators are the achievement and unlock steps of D14's pipeline, for a
        // finished run and for a purchase alike (M4, M8).
        ProgressionManager progression = new ProgressionManager(timeSource,
                AchievementEvaluator.of(content), UnlockEvaluator.of(content));
        grantWhatIsAlreadyEarned(save, progression);
        // fromContent, not fromEconomy (M8): the boss and challenge first-clear rewards live in
        // worlds.json and challenges.json, and the write path pays and grants them from here.
        ProgressionRules progressionRules = ProgressionRules.fromContent(content);
        InputQueue input = new InputQueue(settings.bindings());

        // The merged value, not options.fullscreen(): a stored `fullscreen: true` must open a
        // fullscreen window rather than a windowed one that jumps on the first loop tick. A null
        // scale leaves the choice to the host (the desktop fits the screen height, D3).
        AppWindow window = gameHost.createWindow("Flapforge " + AppVersion.version(),
                options.scale(), settings.fullscreen);
        if (window == null) {
            System.err.println(Strings.active().get(StringKey.APP_NO_DISPLAY));
            return;
        }
        window.setIcons(ProceduralArt.icons());

        Viewport viewport = new Viewport(window.canvasWidth(), window.canvasHeight(), false);
        ScreenManager screens = new ScreenManager(viewport);
        DebugOverlay overlay = new DebugOverlay(screens, clock::nanos);
        FramePresenter presenter = gameHost.createPresenter(window, viewport, overlay);
        screens.setPresenter(presenter);
        InputBridge bridge = gameHost.createInputBridge(input);
        bridge.attach(window);

        // One manifest, read once: the sound bank resolves ids through it and so does every
        // renderer, through the resolver installed here (D18).
        AssetManager assets = manifestAssets();
        AssetResolver.use(new AssetResolver(assets));

        FrameLimiter limiter = new FrameLimiter(clock, GameContext.resolveFps(settings.maxFps));
        GameLoop loop = new GameLoop(clock, input, screens, presenter, limiter);
        overlay.setSource(new DebugSource(screens, loop));
        screens.setCloseHandler(loop::stop);
        // --seed N reaches the first run and every instant retry after it (N, N+1, N+2 ...).
        SeedSequence seeds = SeedSequence.from(options.seed());
        EventBus events = new EventBus();
        // The manager starts silent and the boot step hands it the real mixer: opening a line
        // costs 240 ms on a cold device, which would be 240 ms of a visible, unpainted window.
        AudioManager audio = new AudioManager(new NullAudio());
        // The in-run cues take the sound set of the run's world (E31.g, M7).
        audio.setSfxSetResolver(worldId -> content.worlds().contains(worldId)
                ? content.worlds().get(worldId).sfxSet() : null);
        context = new GameContext(options, clock, timeSource, threads, input, viewport, screens,
                presenter, window, loop, limiter, settingsStore, events, audio, strings, toasts,
                content, save, progression, progressionRules);
        GameContext bootContext = context;
        // The three global hotkeys change a setting, so they go through the settings path rather
        // than poking the presenter and the overlay behind the stored state's back.
        screens.setMuteHandler(bootContext::toggleMute);
        screens.setFullscreenHandler(bootContext::toggleFullscreen);
        screens.setDebugOverlayHandler(bootContext::toggleDebugOverlay);
        // A settings write finishes on the save thread; this is where its outcome re-enters the
        // loop thread as a SaveFailed event and a toast (D15) — and where the autosave lives.
        screens.setTickTask(() -> loopTick(bootContext));
        // Menu blips: the components call UiCues, which lands on the manager (D17, D19).
        UiCues.use(UiCues.of(audio::uiMove, audio::uiSelect, audio::uiBack));
        // Boot -> menu (M2). The warm-up runs on its own daemon thread, never on the loop, and
        // opening the mixer line is the slowest step of it (D19).
        List<BootSequence.Step> steps = BootSequence.defaultSteps();
        // D18/D25: the bundled OFL font is installed before the sizes are rasterised, so the warm
        // up measures the face the game will actually draw with. Loading it is lazy — this boot
        // step, never a static initialiser (E10) — and a manifest without the entry (or a file
        // that will not decode) leaves the logical SansSerif in place.
        steps.set(0, new BootSequence.Step(StringKey.BOOT_FONTS, () -> {
            assets.font(FONT_ID).ifPresent(Fonts::install);
            BootSequence.warmUpFonts();
        }));
        steps.add(new BootSequence.Step(StringKey.BOOT_AUDIO,
                () -> openAudio(audio, assets, threads, content)));
        BootSequence boot = new BootSequence(threads.bootExecutor(), steps);
        ContentRunFactory runs = runFactory(content, seeds, bootContext);
        pinWorld(runs, content, save.profile(), progression);
        screens.push(new BootScreen(bootContext, boot,
                () -> new MainMenuScreen(bootContext, runs, seeds)));
        screens.applyPending();

        Thread shutdownHook = new Thread(() -> drainSaves(threads), "flapforge-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        Thread thread = threads.loopThread(() -> {
            try {
                // The bus is confined to the thread that first uses it, and every setting is
                // pushed into objects the loop owns, so both happen here rather than on the
                // thread that assembled the application.
                events.adopt();
                audio.attach(events);
                screens.setEvents(events);
                // The volumes and the mute flag reach the manager through the SettingsChanged
                // this publishes, which is the same path a later change takes.
                context.applySettings(settings);
                limiter.calibrate();
                loop.run();
            } catch (RuntimeException | Error e) {
                System.err.println("Game loop failed: " + e);
                e.printStackTrace(System.err);
            } finally {
                UiCues.silence();
                // A quit in the first moments can catch the warm-up mid-way through opening the
                // device; closing the audio before it finished would leak the line it is about to
                // install (D19).
                threads.awaitBootIdle(2_000L);
                audio.close();
                saveOnExit();
                shutdown(presenter, bridge, window, threads);
            }
        });
        loopThread = thread;
        thread.start();
    }

    /**
     * The "exit" save trigger (D15): a profile that changed since the last write is queued one
     * last time on the loop thread, before {@link Threads#shutdown(long)} drains the save
     * executor with its own bounded wait.
     */
    private void saveOnExit() {
        GameContext ctx = context;
        if (ctx != null && ctx.progression() != null && ctx.progression().isDirty()) {
            ctx.saveProfile();
        }
    }

    /**
     * The loop-thread task {@link ScreenManager} runs once per tick: it turns finished writes into
     * events and toasts, and it runs D15's autosave.
     *
     * <p>The autosave is the only safety net for a session that ends without running the quit path
     * — a {@code SIGTERM}, an out-of-memory kill, a driver hang — and it is also what retries a
     * write that failed, since a failure leaves the profile dirty. It never fires during a live
     * run ({@link Screen#blocksAutosave()}), and it re-checks every tick until it can, rather than
     * waiting another minute.
     *
     * @param ctx the context the loop runs on
     */
    private void loopTick(GameContext ctx) {
        ctx.drainSaveResults();
        if (ticksSinceAutosave < AUTOSAVE_TICKS) {
            ticksSinceAutosave++;
            return;
        }
        ProgressionManager progression = ctx.progression();
        if (progression == null || !progression.isDirty()) {
            ticksSinceAutosave = 0;
            return;
        }
        Screen top = ctx.screens() == null ? null : ctx.screens().top();
        if (top != null && top.blocksAutosave()) {
            return;
        }
        ticksSinceAutosave = 0;
        ctx.saveProfile();
    }

    /**
     * Stops the save executor and says so when the drain did not finish: the save thread is a
     * daemon, so what is still queued dies with the JVM, and a silent loss is the one thing this
     * program does not do to a player.
     *
     * @param threads the thread owner
     * @return {@code true} when everything queued reached the disk
     */
    private static boolean drainSaves(Threads threads) {
        if (threads.shutdown(2_000L)) {
            return true;
        }
        System.err.println("The last save did not finish within 2000 ms and was dropped.");
        return false;
    }

    /**
     * Reads {@code assets/manifest.json} once, printing anything wrong with it.
     *
     * @return the manager; empty (with errors recorded) when the manifest is missing or broken
     */
    private static AssetManager manifestAssets() {
        AssetManager assets = AssetManager.fromClasspath();
        for (String error : assets.errors()) {
            System.err.println("Asset manifest: " + error);
        }
        return assets;
    }

    /**
     * The bank the mixer resolves sound ids through: {@code assets/manifest.json} first, then the
     * classpath, then {@link io.github.michelbr84.flapforge.audio.ToneSynth} (D18, D19).
     *
     * <p>The shipped manifest is empty on purpose, so today every cue is synthesised; the opener
     * exists so dropping a licensed {@code .wav} into the manifest is all it takes to override
     * one, exactly as a sprite entry overrides procedural art.
     *
     * @param assets the manifest
     * @return the bank
     */
    private static SoundBank manifestSoundBank(AssetManager assets) {
        return new SoundBank(id -> {
            byte[] bytes = assets.bytes(id).orElse(null);
            return bytes == null ? null : (InputStream) new ByteArrayInputStream(bytes);
        });
    }

    /**
     * The {@code BOOT_AUDIO} step: opens the output device, decodes every cue and renders the
     * menu loop, on the boot thread, while the splash is on screen (D19, M8).
     *
     * <p>{@code --no-audio} and a machine with no usable output line both end in {@link NullAudio}
     * after a single logged line (E30.j), so this never throws and never blocks the loop. The
     * menu loop is the Green Fields one, rendered synchronously here — the boot thread, not a new
     * one and not the mixing thread — so the menu's first frame can start it without a hitch
     * (D19); a world run renders its own loop at run start, in {@code GameScreen}.
     *
     * @param audio the manager the opened backend is installed on
     * @param assets the manifest the sound bank resolves through
     * @param threads the owner of the daemon mixing thread
     * @param content the shipped content, which names the menu loop's music block
     */
    private void openAudio(AudioManager audio, AssetManager assets, Threads threads,
            GameContent content) {
        audio.setBackend(AudioBackend.create(!options.noAudio(), threads.audioThreadFactory(),
                manifestSoundBank(assets), null));
        audio.warmUpBlocking();
        if (content.has(GameContent.WORLDS) && content.worlds().contains(MENU_WORLD)) {
            WorldDef menu = content.worlds().get(MENU_WORLD);
            if (menu.music() != null) {
                audio.prepareMusic(MusicSequencer.idForWorld(MENU_WORLD),
                        MusicSequencer.render(menu.music()));
            }
        }
    }

    private void shutdown(FramePresenter presenter, InputBridge bridge, AppWindow window,
            Threads threads) {
        presenter.dispose();
        try {
            bridge.detach();
        } catch (RuntimeException e) {
            System.err.println("Input bridge detach failed: " + e);
        }
        window.dispose();
        drainSaves(threads);
        Thread dog = new Thread(() -> {
            try {
                Thread.sleep(WATCHDOG_MS);
            } catch (InterruptedException e) {
                return;
            }
            System.err.println("Shutdown watchdog: forcing exit after " + WATCHDOG_MS + " ms");
            System.exit(0);
        }, "flapforge-watchdog");
        dog.setDaemon(true);
        watchdog = dog;
        dog.start();
    }

    /**
     * Refresh rate of the default display for the "match refresh" option.
     *
     * <p>The answer is cached. Asking the graphics device is an XRandR round trip — measured at
     * 6.5 ms on the development machine, 39 % of a 16.7 ms frame — and every
     * {@link GameContext#applySettings(Settings)} resolves the frame-rate cap, which the settings
     * screen does on every slider step and every hotkey. {@link #forgetRefreshRate()} drops the
     * cached value when the display may have changed. The device is asked through the
     * {@link GameHost} of the last {@link #start(LaunchOptions, GameHost)}; before any launch
     * there is no display to ask and the default applies.
     *
     * @return the rate in Hz, or {@link FrameLimiter#DEFAULT_FPS} when unknown or headless
     */
    public static int detectRefreshRate() {
        int cached = refreshRateHz;
        if (cached > 0) {
            return cached;
        }
        int resolved = readRefreshRate();
        refreshRateHz = resolved;
        return resolved;
    }

    /** Drops the cached refresh rate so the next request asks the graphics device again. */
    public static void forgetRefreshRate() {
        refreshRateHz = 0;
    }

    private static int readRefreshRate() {
        GameHost current = host;
        return current == null ? FrameLimiter.DEFAULT_FPS
                : FrameLimiter.refreshRateOrDefault(current.displayRefreshRateHz());
    }

    /**
     * Feeds the {@code F3} overlay from the screen manager and the loop (the render package
     * must not depend on either).
     */
    public static final class DebugSource implements DebugOverlay.Source {

        private final ScreenManager screens;
        private final GameLoop loop;
        private List<String> names = List.of();
        private int namesVersion = -1;

        /**
         * Creates the adapter.
         *
         * @param screens the screen stack
         * @param loop the game loop
         */
        public DebugSource(ScreenManager screens, GameLoop loop) {
            this.screens = Objects.requireNonNull(screens, "screens");
            this.loop = Objects.requireNonNull(loop, "loop");
        }

        @Override
        public boolean isVisible() {
            return screens.isDebugOverlayVisible();
        }

        @Override
        public long tickCount() {
            return loop.tickCount();
        }

        @Override
        public long accumulatorNs() {
            return loop.accumulatorNs();
        }

        @Override
        public int lastTicks() {
            return loop.lastTicks();
        }

        @Override
        public List<String> screenNames() {
            // Rebuilt only when the stack actually changed: this is read every frame the F3
            // overlay is up, and an instrument that allocates perturbs the frame time it measures
            // (D18: per-frame allocation avoided).
            int version = screens.stackVersion();
            if (version != namesVersion) {
                List<Screen> stack = screens.screens();
                List<String> rebuilt = new ArrayList<>(stack.size());
                for (Screen s : stack) {
                    rebuilt.add(s.getClass().getSimpleName());
                }
                names = List.copyOf(rebuilt);
                namesVersion = version;
            }
            return names;
        }

        @Override
        public double mouseX() {
            return screens.mouseX();
        }

        @Override
        public double mouseY() {
            return screens.mouseY();
        }
    }

    /** Clock that advances only when told, giving headless runs exactly one tick per frame. */
    static final class SteppingClock implements Clock {

        private long nanos;

        @Override
        public long nanos() {
            return nanos;
        }

        void step(long deltaNs) {
            nanos += deltaNs;
        }
    }
}
