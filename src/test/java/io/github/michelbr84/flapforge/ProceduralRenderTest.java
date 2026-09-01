package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.DebugOverlay;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.GameOverOverlay;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.PauseOverlay;
import io.github.michelbr84.flapforge.ui.screens.SeedSequence;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Headless rendering of everything M0 draws (D18): the icon at every size and each screen
 * through {@link NullPresenter} into a {@link BufferedImage}, asserting non-blank output and no
 * exception. Also checks that the active font renders Portuguese accents (D25).
 */
class ProceduralRenderTest {

    private static final String ACCENTED = "ção Ω";
    /** Per-frame allocation budget of the game screen, in bytes. */
    private static final long ALLOCATION_BUDGET_BYTES = 24 * 1024;

    @Test
    void iconIsNonBlankAtEverySize() {
        for (int size : ProceduralArt.ICON_SIZES) {
            BufferedImage icon = ProceduralArt.icon(size);
            assertEquals(size, icon.getWidth());
            assertEquals(size, icon.getHeight());
            assertTrue(distinctColours(icon, 1) >= 2, "icon " + size + " is uniform");
        }
        List<BufferedImage> all = ProceduralArt.icons();
        assertEquals(ProceduralArt.ICON_SIZES.size(), all.size());
    }

    @Test
    void mainMenuRendersNonBlank() {
        BufferedImage frame = renderScreen(MainMenuScreen::new, 30);
        assertTrue(distinctColours(frame, 2) >= 2, "main menu is uniform");
    }

    @Test
    void mainMenuRendersAtDoubleScaleWithLetterbox() {
        BufferedImage frame = renderScreen(MainMenuScreen::new, 5, 1000, 1280);
        assertTrue(distinctColours(frame, 4) >= 2, "scaled main menu is uniform");
        int letterbox = frame.getRGB(10, 640) & 0xFFFFFF;
        assertEquals(WorldPalette.GREEN_FIELDS.letterbox(), letterbox,
                "letterbox bar uses the palette letterbox tone");
    }

    @Test
    void settingsStubRendersNonBlank() {
        BufferedImage frame = renderScreen(SettingsScreen::new, 5);
        assertTrue(distinctColours(frame, 2) >= 2, "settings stub is uniform");
    }

    @Test
    void gameScreenRendersEveryPhaseNonBlank() {
        for (Phase phase : Phase.values()) {
            Rig rig = new Rig();
            rig.driveTo(phase);
            BufferedImage frame = rig.frame(0.5);
            assertTrue(distinctColours(frame, 2) >= 2, phase + " frame is uniform");
            assertTrue(rig.screens.depth() >= 1);
        }
    }

    @Test
    void gameScreenRendersEveryPhaseWithTheDebugOverlayOn() {
        for (Phase phase : Phase.values()) {
            Rig rig = new Rig();
            rig.screens.setDebugOverlayVisible(true);
            rig.driveTo(phase);
            BufferedImage frame = rig.frame(0.25);
            assertTrue(distinctColours(frame, 2) >= 2, phase + " frame with F3 is uniform");
        }
    }

    @Test
    void flyingFrameShowsAGateAndTheGroundHasScrolled() {
        Rig rig = new Rig();
        rig.driveTo(Phase.FLYING);
        assertFalse(rig.game.run().simulation().obstacles().isEmpty(), "a gate is on screen");
        assertTrue(rig.game.renderer().background().distance() > 0, "the ground scrolled");
        BufferedImage frame = rig.frame(0.0);
        assertTrue(distinctColours(frame, 2) >= 2);
    }

    @Test
    void aGameFrameStaysWithinItsAllocationBudget() {
        // The renderers cache every palette colour and reuse their shapes, and the HUD rebuilds
        // its score string only when the score changes (D18), so a steady frame must allocate far
        // less than a naive one would. Measured per thread, so other JVM activity cannot inflate
        // it; skipped when the JVM does not expose per-thread allocation counters.
        com.sun.management.ThreadMXBean threads = allocationCounter();
        assumeTrue(threads != null, "no per-thread allocation counter on this JVM");
        Rig rig = new Rig();
        rig.driveTo(Phase.FLYING);
        for (int i = 0; i < 50; i++) {
            rig.frame(0.5); // warm up the font, glyph and paint caches
        }
        long id = Thread.currentThread().getId();
        long before = threads.getThreadAllocatedBytes(id);
        int frames = 300;
        for (int i = 0; i < frames; i++) {
            rig.frame(0.5);
        }
        long perFrame = (threads.getThreadAllocatedBytes(id) - before) / frames;
        System.out.println("[render] game frame allocates " + perFrame + " bytes");
        assertTrue(perFrame < ALLOCATION_BUDGET_BYTES, "a game frame allocated " + perFrame
                + " bytes, budget " + ALLOCATION_BUDGET_BYTES);
    }

    /** The phases of a run a frame can be captured in. */
    private enum Phase {
        /** Waiting for the first flap. */
        READY,
        /** Flying with a gate on screen. */
        FLYING,
        /** Paused through a focus loss. */
        PAUSED,
        /** The game-over overlay is up. */
        GAME_OVER
    }

    /** A headless game screen driven straight through the screen manager. */
    private static final class Rig {
        final Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        final ScreenManager screens = new ScreenManager(viewport);
        final NullPresenter presenter;
        final GameScreen game;

        Rig() {
            presenter = new NullPresenter(screens, viewport, Playfield.WIDTH, Playfield.HEIGHT);
            screens.setPresenter(presenter);
            game = new GameScreen(screens, new ClassicRunFactory(), SeedSequence.of(42));
            screens.push(game);
            screens.applyPending();
            screens.tick(InputFrame.EMPTY);
        }

        void driveTo(Phase phase) {
            if (phase == Phase.READY) {
                tick(70); // past the first blink so the hint is drawn
                return;
            }
            flap();
            if (phase == Phase.FLYING) {
                tick(90);
                return;
            }
            if (phase == Phase.PAUSED) {
                tick(20);
                screens.tick(frameWith(new RawInput.FocusLost()));
                tick(5);
                assertTrue(screens.top() instanceof PauseOverlay, "focus loss paused the run");
                return;
            }
            for (int i = 0; i < 400 && !(screens.top() instanceof GameOverOverlay); i++) {
                tick(1);
            }
            tick(70); // past the first blink so the prompt is drawn
        }

        void flap() {
            int[] counts = new int[InputAction.values().length];
            counts[InputAction.FLAP.ordinal()] = 1;
            screens.tick(new InputFrame(counts, EnumSet.of(InputAction.FLAP),
                    EnumSet.noneOf(InputAction.class), 0, 0, 0, 0, 0, 0, List.of(), List.of()));
        }

        InputFrame frameWith(RawInput.SystemEvent event) {
            return new InputFrame(new int[InputAction.values().length],
                    EnumSet.noneOf(InputAction.class), EnumSet.noneOf(InputAction.class), 0, 0,
                    0, 0, 0, 0, List.of(), List.of(event));
        }

        void tick(int n) {
            for (int i = 0; i < n; i++) {
                screens.tick(InputFrame.EMPTY);
            }
        }

        BufferedImage frame(double alpha) {
            presenter.present(alpha);
            BufferedImage image = presenter.image();
            assertNotNull(image);
            return image;
        }
    }

    private static com.sun.management.ThreadMXBean allocationCounter() {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory
                .getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean sun)
                || !sun.isThreadAllocatedMemorySupported()) {
            return null;
        }
        sun.setThreadAllocatedMemoryEnabled(true);
        return sun;
    }

    @Test
    void debugOverlayDrawsOnTopAndMeasuresRates() {
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        ScreenManager screens = new ScreenManager(viewport);
        long[] nanos = {0};
        DebugOverlay overlay = new DebugOverlay(screens, () -> nanos[0]);
        overlay.setSource(new DebugOverlay.Source() {
            @Override
            public boolean isVisible() {
                return true;
            }

            @Override
            public long tickCount() {
                return screens.tickCount();
            }

            @Override
            public long accumulatorNs() {
                return 1_234_567L;
            }

            @Override
            public int lastTicks() {
                return 1;
            }

            @Override
            public List<String> screenNames() {
                return List.of("MainMenuScreen");
            }

            @Override
            public double mouseX() {
                return 12.5;
            }

            @Override
            public double mouseY() {
                return 34.5;
            }
        });
        NullPresenter presenter = new NullPresenter(overlay, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        screens.push(new MainMenuScreen(screens));
        screens.applyPending();

        for (int i = 0; i < 130; i++) {
            nanos[0] += Playfield.TICK_NS;
            screens.tick(InputFrame.EMPTY);
            presenter.present(0.25);
        }
        assertEquals(DebugOverlay.HISTORY, overlay.sampleCount());
        assertEquals(60.0, overlay.fps(), 0.5);
        assertEquals(60.0, overlay.tps(), 0.5);
        assertEquals(WorldPalette.GREEN_FIELDS.letterbox(), overlay.letterboxRgb());
        BufferedImage frame = presenter.image();
        assertNotNull(frame);
        assertTrue(distinctColours(frame, 2) >= 2, "overlay frame is uniform");
        BufferedImage panelArea = frame.getSubimage(8, 8, 240, 100);
        assertTrue(distinctColours(panelArea, 1) >= 2, "overlay panel area is uniform");
    }

    @Test
    void activeFontDisplaysPortugueseAccents() {
        assertTrue(Fonts.canDisplay(ACCENTED), "base font cannot display " + ACCENTED);
        Font bold = Fonts.bold(20);
        assertEquals(-1, bold.canDisplayUpTo(ACCENTED));
        assertEquals(-1, Fonts.regular(14).canDisplayUpTo("Configurações — Início"));
        assertEquals(20, bold.getSize());
        assertTrue(bold.isBold());
        assertEquals(Fonts.bold(20), bold, "font instances are cached");
        assertTrue(Fonts.mono(11).canDisplayUpTo("tps 60.0 fps 60.0") == -1);
    }

    private static BufferedImage renderScreen(Function<ScreenManager, Screen> factory, int ticks) {
        return renderScreen(factory, ticks, Playfield.WIDTH, Playfield.HEIGHT);
    }

    private static BufferedImage renderScreen(Function<ScreenManager, Screen> factory, int ticks,
            int width, int height) {
        Viewport viewport = new Viewport(width, height, false);
        ScreenManager screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, width, height);
        screens.setPresenter(presenter);
        screens.push(factory.apply(screens));
        screens.applyPending();
        for (int i = 0; i < ticks; i++) {
            screens.tick(InputFrame.EMPTY);
        }
        presenter.present(0.5);
        BufferedImage image = presenter.image();
        assertNotNull(image);
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
        return image;
    }

    private static int distinctColours(BufferedImage img, int stride) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += stride) {
            for (int x = 0; x < img.getWidth(); x += stride) {
                colours.add(img.getRGB(x, y) & 0xFFFFFF);
                if (colours.size() > 8) {
                    return colours.size();
                }
            }
        }
        return colours.size();
    }
}
