package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.Flapforge;
import io.github.michelbr84.flapforge.audio.AudioBackend;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.audio.NullAudio;
import io.github.michelbr84.flapforge.audio.SoundBank;
import io.github.michelbr84.flapforge.content.ContentException;
import io.github.michelbr84.flapforge.content.ContentValidator;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
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
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.render.AssetManager;
import io.github.michelbr84.flapforge.render.AssetResolver;
import io.github.michelbr84.flapforge.render.DebugOverlay;
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
import java.awt.AWTError;
import java.awt.DisplayMode;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
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
 * <p>Windowed launch: the window is built on the event-dispatch thread, the loop runs on the
 * non-daemon {@code flapforge-loop} thread and the main thread returns. Without a display the
 * launch prints a hint and returns (no {@code System.exit}). Quit path: {@code CloseRequested}
 * (window button, Quit in the menu) stops the loop after the current frame; the loop thread
 * then disposes the presenter, detaches the bridge, disposes the frame on the event-dispatch
 * thread and stops the executors, after which the JVM exits naturally. A daemon watchdog calls
 * {@code System.exit} only if the JVM is still alive five seconds later; a test harness that
 * hosts the application in a long-lived JVM cancels it with {@link #awaitShutdown(long)}.
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

    /** Cached display refresh rate in Hz; {@code 0} means "not resolved yet". */
    private static volatile int refreshRateHz;

    private final LaunchOptions options;
    private GameContext context;
    private int ticksSinceAutosave;
    private volatile Thread loopThread;
    private volatile Thread watchdog;

    /**
     * Creates the application.
     *
     * @param options the launch options
     */
    public GameApplication(LaunchOptions options) {
        this.options = options;
    }

    /**
     * Builds and starts the application.
     *
     * @param options the launch options
     * @return the application
     */
    public static GameApplication start(LaunchOptions options) {
        GameApplication app = new GameApplication(options);
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
        screens.push(new MainMenuScreen(context, runFactory(content, seeds), seeds));
        screens.applyPending();

        loop.start();
        for (int i = 0; i < frames; i++) {
            clock.step(Playfield.TICK_NS);
            loop.frame();
        }
        long seed = options.seed() == null ? DEFAULT_HEADLESS_SEED : options.seed();
        System.out.println("headless-run frames=" + loop.frameCount() + " ticks=" + loop.tickCount()
                + " presents=" + presenter.presentCount() + " seed=" + seed);
        System.out.println(simulationHashLine(content, seed, frames));
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
        return new ContentRunFactory(content,
                seeds.isExplicit() ? RunMode.SEEDED : RunMode.STANDARD);
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
        Run run = new RunFactory(content).newRun(RunConfig.classic(seed));
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
                .stamp(Flapforge.version(), SaveFile.CONTENT_VERSION);
        SaveManager.LoadResult profileLoad = loadProfile(save);
        if (profileLoad.hasNotice()) {
            toasts.push(saveNotice(strings, save, profileLoad), Toast.Kind.WARNING);
        }
        ProgressionManager progression = new ProgressionManager(timeSource);
        ProgressionRules progressionRules = ProgressionRules.fromEconomy(content.economy());
        InputQueue input = new InputQueue(settings.bindings());

        GameWindow window;
        try {
            int scale = options.scale() != null ? options.scale() : GameWindow.defaultScale();
            // The merged value, not options.fullscreen(): a stored `fullscreen: true` must open a
            // fullscreen window rather than a windowed one that jumps on the first loop tick.
            window = GameWindow.create("Flapforge " + Flapforge.version(), scale,
                    settings.fullscreen);
        } catch (HeadlessException | AWTError e) {
            System.err.println(Strings.active().get(StringKey.APP_NO_DISPLAY));
            return;
        }
        window.setIcons(ProceduralArt.icons());

        Viewport viewport = new Viewport(window.canvasWidth(), window.canvasHeight(), false);
        ScreenManager screens = new ScreenManager(viewport);
        DebugOverlay overlay = new DebugOverlay(screens, clock::nanos);
        BufferStrategyPresenter presenter = new BufferStrategyPresenter(window, viewport, overlay);
        screens.setPresenter(presenter);
        AwtInputBridge bridge = new AwtInputBridge(input);
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
        steps.add(new BootSequence.Step(StringKey.BOOT_AUDIO,
                () -> openAudio(audio, assets, threads)));
        BootSequence boot = new BootSequence(threads.bootExecutor(), steps);
        screens.push(new BootScreen(bootContext, boot,
                () -> new MainMenuScreen(bootContext, runFactory(content, seeds), seeds)));
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
     * The {@code BOOT_AUDIO} step: opens the output device and decodes every cue, on the boot
     * thread, while the splash is on screen (D19).
     *
     * <p>{@code --no-audio} and a machine with no usable output line both end in {@link NullAudio}
     * after a single logged line (E30.j), so this never throws and never blocks the loop.
     *
     * @param audio the manager the opened backend is installed on
     * @param assets the manifest the sound bank resolves through
     * @param threads the owner of the daemon mixing thread
     */
    private void openAudio(AudioManager audio, AssetManager assets, Threads threads) {
        audio.setBackend(AudioBackend.create(!options.noAudio(), threads.audioThreadFactory(),
                manifestSoundBank(assets), null));
        audio.warmUpBlocking();
    }

    private void shutdown(FramePresenter presenter, AwtInputBridge bridge, GameWindow window,
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
     * cached value when the display may have changed.
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
        if (GraphicsEnvironment.isHeadless()) {
            return FrameLimiter.DEFAULT_FPS;
        }
        try {
            DisplayMode mode = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDisplayMode();
            return FrameLimiter.refreshRateOrDefault(mode.getRefreshRate());
        } catch (HeadlessException e) {
            return FrameLimiter.DEFAULT_FPS;
        }
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
