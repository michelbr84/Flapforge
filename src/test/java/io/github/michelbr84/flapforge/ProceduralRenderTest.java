package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.render.DebugOverlay;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.GameStubScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
import java.awt.Font;
import java.awt.image.BufferedImage;
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
    void gameStubRendersNonBlank() {
        BufferedImage frame = renderScreen(GameStubScreen::new, 40);
        assertTrue(distinctColours(frame, 2) >= 2, "game stub is uniform");
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
