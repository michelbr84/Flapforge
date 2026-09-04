package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import io.github.michelbr84.flapforge.app.Threads;
import io.github.michelbr84.flapforge.audio.AudioManager;
import io.github.michelbr84.flapforge.audio.NullAudio;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.persistence.SaveManager;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.ContentRunFactory;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The attract mode (M9): the idle timer on the main menu, the bot-driven demo behind it, and the
 * guarantees the mode lives by — any input cancels it, focus loss freezes it, nothing is written
 * to the profile or the save, nothing is published on the bus, and a demo ticking alongside the
 * published configuration cannot move its determinism hash.
 */
final class AttractModeTest {

    /** Builds a frame with one press edge of an action and nothing else. */
    private static InputFrame pressOf(InputAction action) {
        int[] counts = new int[InputAction.values().length];
        counts[action.ordinal()] = 1;
        return new InputFrame(counts, EnumSet.noneOf(InputAction.class),
                EnumSet.noneOf(InputAction.class), 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    /** Builds a frame carrying only system events. */
    private static InputFrame systemFrame(RawInput.SystemEvent... events) {
        return new InputFrame(new int[InputAction.values().length],
                EnumSet.noneOf(InputAction.class), EnumSet.noneOf(InputAction.class),
                0, 0, 0, 0, 0, 0, List.of(), List.of(events));
    }

    /** A bare menu on its own stack: the classic seam, no session, demo enabled. */
    private static MainMenuScreen menuOn(ScreenManager screens) {
        MainMenuScreen menu = new MainMenuScreen(screens);
        screens.push(menu);
        screens.applyPending();
        return menu;
    }

    /** Ticks exactly the attract delay worth of idle, so the next idle tick would fire it. */
    private static void idle(ScreenManager screens, int ticks) {
        idle(screens, ticks, 0, 0);
    }

    /**
     * Ticks idle with the pointer parked at a fixed position — a real queue reports the current
     * pointer position every frame, so a stationary pointer is a repeated position, not a
     * teleport back to the origin.
     */
    private static void idle(ScreenManager screens, int ticks, double mouseX, double mouseY) {
        for (int i = 0; i < ticks; i++) {
            screens.tick(InputFrame.EMPTY.withMouse(mouseX, mouseY));
        }
    }

    @Test
    void attractStartsAfterExactlyTwentySecondsOfIdle() {
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        MainMenuScreen menu = menuOn(screens);
        assertEquals(0, menu.attractIdleTicks(), "the timer starts at entry, not at construction");
        // The plan's number, pinned literally (20 s x 60 Hz): reading
        // MainMenuScreen.ATTRACT_DELAY_TICKS here would let a retune of the constant pass this
        // test unchallenged, and this is the test that owns the value.
        idle(screens, 1199);
        assertFalse(menu.attractActive(), "nineteen seconds of idle must not start the demo");
        assertEquals(1199, menu.attractIdleTicks());
        screens.tick(InputFrame.EMPTY);
        assertTrue(menu.attractActive(), "twenty seconds of idle starts the demo");
        assertNotNull(menu.demo());
        assertNotNull(menu.demo().run(), "the demo run exists as soon as the attract shows");
        assertEquals(1, menu.demo().runsStarted());
    }

    @Test
    void anyInputCancelsTheDemoAndResetsTheIdleTimer() {
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        MainMenuScreen menu = menuOn(screens);
        idle(screens, MainMenuScreen.ATTRACT_DELAY_TICKS);
        assertTrue(menu.attractActive());
        long firstSeed = menu.demo().seed();

        // A key press: the demo is gone in the same tick the press arrives, the run is discarded
        // and the timer is back at zero.
        screens.tick(pressOf(InputAction.FLAP));
        assertFalse(menu.attractActive());
        assertEquals(0, menu.attractIdleTicks());
        assertNull(menu.demo().run(), "the cancelled demo does not keep flying");

        // The timer is genuinely reset: 19 s of idle do not re-fire, the 1200th tick does, with
        // the next seed of the attract stream (the mode cycles seeds).
        idle(screens, MainMenuScreen.ATTRACT_DELAY_TICKS - 1);
        assertFalse(menu.attractActive());
        screens.tick(InputFrame.EMPTY);
        assertTrue(menu.attractActive());
        assertEquals(2, menu.demo().runsStarted());
        assertNotEquals(firstSeed, menu.demo().seed(), "the attract stream cycles its seeds");

        // The pointer moving is input too.
        screens.tick(InputFrame.EMPTY.withMouse(120, 80));
        assertFalse(menu.attractActive());

        idle(screens, MainMenuScreen.ATTRACT_DELAY_TICKS, 120, 80);
        assertTrue(menu.attractActive());

        // And the wheel.
        screens.tick(new InputFrame(new int[InputAction.values().length],
                EnumSet.noneOf(InputAction.class), EnumSet.noneOf(InputAction.class),
                0, 0, 0, 0, 0, 1, List.of(), List.of()));
        assertFalse(menu.attractActive());
    }

    @Test
    void theDemoRunIsFlownByTheBot() {
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        MainMenuScreen menu = menuOn(screens);
        idle(screens, MainMenuScreen.ATTRACT_DELAY_TICKS);
        idle(screens, 180);
        assertNotNull(menu.demo().pilot());
        BotPilot pilot = menu.demo().pilot();
        assertEquals("average", pilot.preset().name(),
                "the documented preset: the reference player the balancing is written against");
        Run run = menu.demo().run();
        assertNotNull(run);
        assertTrue(run.tick() > 100, "the run is ticking under the bot, not sitting in READY");
        assertNotEquals(RunPhase.READY, run.phase());
    }

    @Test
    void focusLossFreezesTheDemoAndInputEndsIt() {
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        MainMenuScreen menu = menuOn(screens);
        idle(screens, MainMenuScreen.ATTRACT_DELAY_TICKS);
        idle(screens, 60);

        // A bare focus loss freezes the demo in place (D2); the queue's synthesised releases of
        // a real focus loss arrive as edges and take the cancel path instead.
        screens.tick(systemFrame(new RawInput.FocusLost()));
        long frozen = menu.demo().run().tick();
        idle(screens, 120);
        assertEquals(frozen, menu.demo().run().tick(), "a frozen demo does not advance");
        assertTrue(menu.attractActive(), "frozen is still shown, just not playing");

        // An iconify freezes; the restore resumes it without any input.
        screens.tick(systemFrame(new RawInput.Iconified(true)));
        assertEquals(frozen, menu.demo().run().tick());
        screens.tick(systemFrame(new RawInput.Iconified(false)));
        assertTrue(menu.demo().run().tick() > frozen, "restoring the window resumes the demo");

        // Any input then ends it.
        screens.tick(InputFrame.EMPTY.withMouse(30, 40));
        assertFalse(menu.attractActive());
    }

    @Test
    void attractWritesNothingToTheProfileOrTheSave(@TempDir Path dir) throws Exception {
        GameContent content = GameContent.load();
        Path saveFile = dir.resolve("save.json");
        SaveManager save = new SaveManager(Runnable::run, () -> 0L, saveFile);
        save.load();
        save.save();
        FileTime written = Files.getLastModifiedTime(saveFile);
        PlayerProfile profile = save.profile();
        long totalRuns = profile.statistics.totalRuns;
        long totalGates = profile.statistics.totalGates;
        long coinsEarned = profile.statistics.coinsEarned;
        long coins = profile.wallet.get(PlayerProfile.CURRENCY_COINS);
        long xp = profile.xp;
        int level = profile.level;
        int history = profile.statistics.runHistory.size();
        int unlocked = profile.unlocked.size();

        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        EventBus events = new EventBus();
        int[] published = {0};
        events.subscribe(GameEvent.class, event -> published[0]++);
        ProgressionManager progression = new ProgressionManager(() -> 0L,
                ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
        GameContext context = new GameContext(LaunchOptions.DEFAULTS, () -> 0L, () -> 0L,
                new Threads(), new InputQueue(KeyBindings.defaults()), viewport, screens,
                null, null, null, null, null, events, new AudioManager(new NullAudio()),
                Strings.load("en"), new ToastLayer(), content, save, progression,
                ProgressionRules.fromEconomy(content.economy()));
        ContentRunFactory playerRuns =
                new ContentRunFactory(content, io.github.michelbr84.flapforge.gameplay.run.RunMode.STANDARD,
                        () -> save.profile());
        MainMenuScreen menu = new MainMenuScreen(context, playerRuns, SeedSequence.of(7));
        screens.push(menu);
        screens.applyPending();

        idle(screens, MainMenuScreen.ATTRACT_DELAY_TICKS);
        assertTrue(menu.attractActive());
        // Drive far past one demo lifetime: the average pilot loses its classic run after a few
        // thousand ticks, the mode cycles to the next seed, and every one of those ticks must
        // leave the profile and the save file exactly as they were.
        int guard = 0;
        while (menu.demo().runsStarted() < 2 && guard++ < 80_000) {
            screens.tick(InputFrame.EMPTY);
        }
        assertTrue(menu.demo().runsStarted() >= 2, "the demo finished a run and cycled the seed");

        assertEquals(totalRuns, profile.statistics.totalRuns, "no run was recorded");
        assertEquals(totalGates, profile.statistics.totalGates);
        assertEquals(coinsEarned, profile.statistics.coinsEarned);
        assertEquals(coins, (long) profile.wallet.get(PlayerProfile.CURRENCY_COINS));
        assertEquals(xp, profile.xp);
        assertEquals(level, profile.level);
        assertEquals(history, profile.statistics.runHistory.size());
        assertEquals(unlocked, profile.unlocked.size());
        assertEquals(written, Files.getLastModifiedTime(saveFile),
                "the save file was not rewritten");
        assertEquals(0, published[0], "the demo publishes nothing — the menu keeps its music");
    }

    @Test
    void theAttractDemoDoesNotPerturbTheDeterminismHash() {
        GameContent content = GameContent.load();
        RunFactory runs = new RunFactory(content);
        HeadlessRunner.Outcome baseline = HeadlessRunner.run(
                runs.newRun(RunConfig.classic(42)), new BotPilot(BotPilot.Preset.PERFECT, 42),
                3000, true);
        assertFalse(baseline.hashes().isEmpty());

        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        menuOn(screens);
        idle(screens, MainMenuScreen.ATTRACT_DELAY_TICKS + 600);

        // The published configuration, ticked in lockstep with the active attract demo on the
        // menu: identical per-tick state hashes are the proof the demo touches nothing the
        // simulation reads.
        Run reference = runs.newRun(RunConfig.classic(42));
        BotPilot pilot = new BotPilot(BotPilot.Preset.PERFECT, 42);
        List<Long> hashes = new ArrayList<>();
        int ticks = 0;
        while (!reference.isFinished() && ticks < 3000) {
            reference.tick(pilot.decide(reference));
            hashes.add(reference.simulation().stateHash());
            ticks++;
            screens.tick(InputFrame.EMPTY);
        }
        assertEquals(baseline.hashes(), hashes,
                "the same run, with the attract demo ticking alongside, is bit-identical");
    }
}
