package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.Screen;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The bot-driven demo run of the attract mode (M9): a real {@link Run} on a real
 * {@link GameRenderer}, shown behind the {@link MainMenuScreen} after the player has idled on it.
 *
 * <p>The screen is never pushed onto the {@link io.github.michelbr84.flapforge.ui.ScreenManager}
 * — the menu owns it and ticks it from its own tick, on the loop thread the menu already runs on;
 * no new thread exists. Its {@link #tick(InputFrame)} ignores the frame it is handed: the run is
 * flown by a {@link BotPilot} (D21), so no input ever reaches the simulation from the player.
 *
 * <h2>Configuration (profile-less by construction)</h2>
 * Every demo run is built through the profile-less {@link ContentRunFactory} path when the session
 * has content, else through {@link ClassicRunFactory} — either way the run is
 * {@code RunConfig.classic(seed)}: Forgeword on Green Fields, normal tier, no abilities, no drafts
 * and no boss. The profile is never read, so the attract showcase cannot depend on, and never
 * changes with, what the player has unlocked; the classic curve of the published determinism hash
 * is exactly what it shows.
 *
 * <h2>Seeds (a fixed attract stream)</h2>
 * The seeds come from the named {@code attract} stream of a provider seeded once from a fixed
 * constant ({@value #SEED_CONSTANT_NAME} through FNV-1a) — never from the profile's last seed and
 * never from a clock. Every launch shows the same sequence of demo runs, and because the stream is
 * consumed only here, nothing else's determinism can move. A finished demo run is replaced by the
 * next seed of the stream, so the mode cycles through seeds the longer it is left alone.
 *
 * <h2>Nothing is written, nothing is heard</h2>
 * The screen holds no profile, no {@code ProgressionManager} and no save manager: a finished run
 * is simply discarded. It publishes nothing on the event bus either — no {@code RunStarted}, no
 * facts — so the audio manager never reacts to it: the menu loop keeps playing and the demo is
 * silent by construction, not by muting anything.
 *
 * <h2>Accessibility</h2>
 * The demo renders through the shared procedural render path, so the accessibility settings are
 * honoured exactly as in a real run: {@code reduceFlashing} is re-read from
 * {@code ParticleSystem.defaultReduceFlashing()} on every renderer tick (the one cosmetic flash it
 * can show, E8's ambient flash, is the damped tint, never the strobe), and the high-contrast and
 * colour-blind palette of M8 apply to every colour the renderers derive. Pausing on focus loss is
 * the menu's business (the window events arrive on its tick); a paused demo simply freezes.
 */
public final class DemoScreen implements Screen {

    /** Name the fixed attract seed constant is derived from. */
    static final String SEED_CONSTANT_NAME = "flapforge-attract";

    /** Fixed seed of both the attract stream and the pilot's {@code bot} stream (M9). */
    public static final long ATTRACT_SEED = MathUtil.fnv1a64(SEED_CONSTANT_NAME);

    private final SeededRunSource source;
    private final BotPilot pilot;
    private final GameRenderer renderer;
    private final Random seeds;
    private Run run;
    private long seed;
    private int runsStarted;

    /**
     * Creates the demo screen.
     *
     * @param source the profile-less run source the demo runs are built from
     * @param preset the skill preset the pilot flies with
     * @param strings the string table the HUD labels come from
     */
    public DemoScreen(SeededRunSource source, BotPilot.Preset preset, Strings strings) {
        this.source = Objects.requireNonNull(source, "source");
        this.pilot = new BotPilot(Objects.requireNonNull(preset, "preset"), ATTRACT_SEED);
        Objects.requireNonNull(strings, "strings");
        this.seeds = new RandomProvider(ATTRACT_SEED).stream(RandomProvider.ATTRACT);
        this.renderer = new GameRenderer(WorldPalette.GREEN_FIELDS,
                strings.get(StringKey.GAME_READY_HINT));
        // The same HUD wiring a real run gets, so the demo reads as the game it showcases. The
        // run is always the classic configuration, so no world name or streak step is needed:
        // the defaults the renderer ships with are the classic ones.
        this.renderer.setStreakLabel(strings.get(StringKey.HUD_STREAK));
        this.renderer.setCoinLabel(strings.get(StringKey.HUD_COINS));
        this.renderer.setAbilityStateLabels(strings.get(StringKey.HUD_ABILITY_READY),
                strings.get(StringKey.HUD_ABILITY_COOLDOWN));
        this.renderer.setShieldLabel(strings.get(StringKey.HUD_SHIELD_CHARGES));
        this.renderer.setBossLabels(strings.get(StringKey.HUD_BOSS_WARNING),
                strings.get(StringKey.HUD_BOSS_FIGHT),
                strings.get(StringKey.STAT_BOSS_PHASE));
        this.renderer.setObjectiveLabels(strings.get(StringKey.HUD_OBJECTIVE_GATES),
                strings.get(StringKey.HUD_OBJECTIVE_TICKS),
                strings.get(StringKey.HUD_OBJECTIVE_COINS),
                strings.get(StringKey.HUD_OBJECTIVE_POINTS),
                strings.get(StringKey.HUD_OBJECTIVE_BOSS),
                strings.get(StringKey.HUD_OBJECTIVE_COMPLETE));
    }

    /**
     * The run currently being flown.
     *
     * @return the run, or {@code null} before the first demo tick
     */
    public Run run() {
        return run;
    }

    /**
     * The pilot flying the demo.
     *
     * @return the pilot
     */
    public BotPilot pilot() {
        return pilot;
    }

    /**
     * The seed of the run currently being flown.
     *
     * @return the seed
     */
    public long seed() {
        return seed;
    }

    /**
     * How many demo runs have been started, across every restart and re-activation.
     *
     * @return the count
     */
    public int runsStarted() {
        return runsStarted;
    }

    /**
     * Throws the current run away, so the next activation starts a fresh one with the next seed
     * of the attract stream. The menu calls this when the player's input cancelled the demo.
     */
    public void discard() {
        run = null;
    }

    @Override
    public void tick(InputFrame input) {
        // The frame is ignored on purpose: the pilot owns the input (M9 attract mode).
        if (run == null) {
            startRun();
            return;
        }
        if (run.isFinished()) {
            // No overlay and nothing banked: the next seed of the attract stream takes over.
            startRun();
            return;
        }
        RunInput intent = pilot.decide(run);
        TickReport report = run.tick(intent);
        renderer.tick(run, report.has(TickFact.Flapped.class), report.has(TickFact.Crashed.class));
        forwardCosmetics(report);
    }

    /**
     * Forwards the one cosmetic fact the renderer draws itself (M7, E8): the ambient sky flash,
     * which {@code reduceFlashing} turns into a mild tint. Everything else a real run reacts to —
     * sounds, toasts, banners, progression — stays unpublished: the demo is behind a menu and
     * silent by construction.
     *
     * @param report the tick's facts
     */
    private void forwardCosmetics(TickReport report) {
        List<TickFact> facts = report.facts();
        for (int i = 0; i < facts.size(); i++) {
            if (facts.get(i) instanceof TickFact.AmbientFlash) {
                renderer.ambientFlash();
            }
        }
    }

    private void startRun() {
        seed = seeds.nextLong();
        run = source.newRun(seed);
        renderer.reset();
        runsStarted++;
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        if (run == null) {
            return;
        }
        // No seed line (the demo is not quotable — the player's runs are) and no hitboxes.
        renderer.render(g, alpha, run, null, false);
    }
}
