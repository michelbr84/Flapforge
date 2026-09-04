package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.BootSequence;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The splash the game opens on (M2): the forge emblem, a progress bar and the name of whatever
 * is warming up.
 *
 * <p>Its whole reason to exist is that some subsystems are slow to start — the toolkit's font
 * cache, and the audio line the mixer opens, which can block for hundreds of milliseconds on a
 * busy device. None of that may happen on the loop thread, so the work is a {@link BootSequence}
 * submitted to a background executor owned by {@code app.Threads} and this screen only polls its
 * progress once per tick. If the warm-up were to hang, {@link #MAX_TICKS} hands over anyway: a
 * player must never be stuck on a splash.
 *
 * <p>The screen stays up for at least {@link #MIN_TICKS} so the hand-over does not flash, and any
 * key or click skips the remainder once the warm-up is done.
 */
public final class BootScreen implements Screen {

    /** Shortest time the splash is shown, in ticks. */
    public static final int MIN_TICKS = 42;
    /** Longest the splash waits for the warm-up before handing over anyway, in ticks. */
    public static final int MAX_TICKS = 600;
    /** Width of the progress bar. */
    public static final int BAR_W = 220;
    /** Height of the progress bar. */
    public static final int BAR_H = 8;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int EMBLEM_CX = Playfield.WIDTH / 2;
    private static final int ANVIL_TOP_Y = 250;
    private static final int ANVIL_W = 110;
    private static final int BIRD_SIZE = 72;
    private static final int TITLE_BASELINE = 366;
    private static final int STATUS_BASELINE = 452;
    private static final int BAR_Y = 470;
    private static final Color BAR_TRACK = new Color(0x1C, 0x3A, 0x3E, 0x8C);
    private static final Color BAR_FILL = new Color(0x6F, 0xD1, 0xA8);

    private final ScreenManager screens;
    private final GameContext context;
    private final BootSequence boot;
    private final Supplier<Screen> next;
    private final Strings strings;
    private int ticks;
    private boolean handedOver;

    /**
     * Creates a boot screen for a wired application.
     *
     * @param context the application services
     * @param boot the warm-up sequence, not started yet
     * @param next builds the screen the splash hands over to (the main menu)
     */
    public BootScreen(GameContext context, BootSequence boot, Supplier<Screen> next) {
        this(Objects.requireNonNull(context, "context").screens(), context, boot, next);
    }

    /**
     * Creates a boot screen without an application context (tests and tools).
     *
     * @param screens the screen stack
     * @param executor where the warm-up runs
     * @param next builds the screen the splash hands over to
     */
    public BootScreen(ScreenManager screens, Executor executor, Supplier<Screen> next) {
        this(screens, null, new BootSequence(executor, BootSequence.defaultSteps()), next);
    }

    private BootScreen(ScreenManager screens, GameContext context, BootSequence boot,
            Supplier<Screen> next) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.context = context;
        this.boot = Objects.requireNonNull(boot, "boot");
        this.next = Objects.requireNonNull(next, "next");
        this.strings = context != null && context.strings() != null
                ? context.strings() : Strings.active();
    }

    /**
     * The warm-up being waited on.
     *
     * @return the sequence
     */
    public BootSequence sequence() {
        return boot;
    }

    /**
     * Ticks spent on the splash so far.
     *
     * @return the count
     */
    public int ticks() {
        return ticks;
    }

    /**
     * Whether the splash has asked for the next screen.
     *
     * @return {@code true} once it handed over
     */
    public boolean hasHandedOver() {
        return handedOver;
    }

    /**
     * Whether the splash is only still up because of {@link #MIN_TICKS}.
     *
     * @return {@code true} when the warm-up finished
     */
    public boolean isReady() {
        return boot.isDone();
    }

    @Override
    public void onEnter() {
        screens.setLetterboxRgb(PALETTE.letterbox());
        boot.start();
    }

    @Override
    public void tick(InputFrame input) {
        ticks++;
        if (handedOver) {
            return;
        }
        boolean skipped = boot.isDone() && (!input.rawKeyDowns().isEmpty()
                || input.isMouseJustPressed(Keys.BUTTON_LEFT));
        if (skipped || (boot.isDone() && ticks >= MIN_TICKS) || ticks >= MAX_TICKS) {
            handOver();
        }
    }

    private void handOver() {
        handedOver = true;
        for (String error : boot.errors()) {
            System.err.println("Boot warm-up: " + error);
        }
        screens.setRoot(next.get());
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);

        ProceduralArt.drawAnvil(g, EMBLEM_CX, ANVIL_TOP_Y, ANVIL_W,
                ProceduralArt.letterboxColor(PALETTE));
        double phase = (ticks % 48) / 48.0;
        ProceduralArt.drawBird(g, EMBLEM_CX, ANVIL_TOP_Y - BIRD_SIZE * 0.38, BIRD_SIZE, phase,
                PALETTE);

        g.setFont(Fonts.bold(52));
        TextPainter.drawOutlined(g, strings.get(StringKey.APP_TITLE), EMBLEM_CX, TITLE_BASELINE,
                Align.CENTER, ProceduralArt.accentColor(PALETTE),
                ProceduralArt.letterboxColor(PALETTE), 3);

        g.setFont(Fonts.regular(14));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        String status = boot.isDone() && ticks >= MIN_TICKS
                ? strings.get(StringKey.BOOT_PRESS_ANY) : strings.get(boot.currentLabel());
        TextPainter.drawCentered(g, status, EMBLEM_CX, STATUS_BASELINE);

        int bx = EMBLEM_CX - BAR_W / 2;
        g.setColor(BAR_TRACK);
        g.fillRoundRect(bx, BAR_Y, BAR_W, BAR_H, BAR_H, BAR_H);
        double shown = MathUtil.clamp(Math.max(boot.progress(), ticks / (double) MIN_TICKS), 0, 1);
        g.setColor(BAR_FILL);
        g.fillRoundRect(bx, BAR_Y, (int) Math.round(BAR_W * shown), BAR_H, BAR_H, BAR_H);
    }
}
