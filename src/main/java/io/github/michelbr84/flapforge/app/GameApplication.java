package io.github.michelbr84.flapforge.app;

import io.github.michelbr84.flapforge.Flapforge;
import io.github.michelbr84.flapforge.content.ContentException;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.render.DebugOverlay;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.ContentRunFactory;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import java.awt.AWTError;
import java.awt.DisplayMode;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
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
 * <p>Windowed launch: the window is built on the event-dispatch thread, the loop runs on the
 * non-daemon {@code flapforge-loop} thread and the main thread returns. Without a display the
 * launch prints a hint and returns (no {@code System.exit}). Quit path: {@code CloseRequested}
 * (window button, Quit in the menu) stops the loop after the current frame; the loop thread
 * then disposes the presenter, detaches the bridge, disposes the frame on the event-dispatch
 * thread and stops the executors, after which the JVM exits naturally. A daemon watchdog calls
 * {@code System.exit} only if the JVM is still alive five seconds later; a test harness that
 * hosts the application in a long-lived JVM cancels it with {@link #awaitShutdown(long)}.
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

    /** Seed of a headless run started without {@code --seed} (the CI reference seed, D12). */
    public static final long DEFAULT_HEADLESS_SEED = 42L;

    private final LaunchOptions options;
    private GameContext context;
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
        InputQueue input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter();
        screens.setPresenter(presenter);
        GameLoop loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);
        SeedSequence seeds = SeedSequence.from(options.seed());
        screens.push(new MainMenuScreen(screens, runFactory(content, seeds), seeds));
        screens.applyPending();
        context = new GameContext(options, clock, timeSource, threads, input, viewport, screens,
                presenter, null, loop);

        loop.start();
        for (int i = 0; i < frames; i++) {
            clock.step(Playfield.TICK_NS);
            loop.frame();
        }
        long seed = options.seed() == null ? DEFAULT_HEADLESS_SEED : options.seed();
        System.out.println("headless-run frames=" + loop.frameCount() + " ticks=" + loop.tickCount()
                + " presents=" + presenter.presentCount() + " seed=" + seed);
        System.out.println(simulationHashLine(content, seed, frames));
        threads.shutdown(2_000L);
    }

    /**
     * Loads the shipped content, printing every binding and validation error before giving up.
     *
     * @return the content, or {@code null} when it failed to load (the launch must abort)
     */
    private static GameContent loadContent() {
        try {
            return GameContent.load();
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
        InputQueue input = new InputQueue(KeyBindings.defaults());

        GameWindow window;
        try {
            int scale = options.scale() != null ? options.scale() : GameWindow.defaultScale();
            window = GameWindow.create("Flapforge " + Flapforge.version(), scale,
                    options.fullscreen());
        } catch (HeadlessException | AWTError e) {
            System.err.println("No display available; use --headless-run N or --no-window.");
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

        FrameLimiter limiter = new FrameLimiter(clock, FrameLimiter.DEFAULT_FPS);
        GameLoop loop = new GameLoop(clock, input, screens, presenter, limiter);
        overlay.setSource(new DebugSource(screens, loop));
        screens.setCloseHandler(loop::stop);
        // --seed N reaches the first run and every instant retry after it (N, N+1, N+2 ...).
        SeedSequence seeds = SeedSequence.from(options.seed());
        screens.push(new MainMenuScreen(screens, runFactory(content, seeds), seeds));
        screens.applyPending();
        context = new GameContext(options, clock, timeSource, threads, input, viewport, screens,
                presenter, window, loop);

        Thread shutdownHook = new Thread(() -> threads.shutdown(2_000L), "flapforge-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        Thread thread = threads.loopThread(() -> {
            try {
                limiter.calibrate();
                loop.run();
            } catch (RuntimeException | Error e) {
                System.err.println("Game loop failed: " + e);
                e.printStackTrace(System.err);
            } finally {
                shutdown(presenter, bridge, window, threads);
            }
        });
        loopThread = thread;
        thread.start();
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
        threads.shutdown(2_000L);
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
     * @return the rate in Hz, or {@link FrameLimiter#DEFAULT_FPS} when unknown or headless
     */
    public static int detectRefreshRate() {
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
            List<Screen> stack = screens.screens();
            List<String> names = new ArrayList<>(stack.size());
            for (Screen s : stack) {
                names.add(s.getClass().getSimpleName());
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
