package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import io.github.michelbr84.flapforge.ui.component.NavBar;
import io.github.michelbr84.flapforge.ui.component.NavButton;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The bottom navigation of the home hub (D17) driven with hand-built {@link InputFrame}s: the
 * row layout, Left/Right stepping and wrapping, disabled items skipped, the primary flag and a
 * click, plus a non-blank render in both contrast modes.
 */
class NavBarTest {

    private static final IconPainter ICON =
            (g, cx, cy, size, color) -> ProceduralArt.drawScroll(g, cx, cy, size, color, color);

    private NavBar bar;
    private NavButton shop;
    private NavButton birds;
    private NavButton play;
    private NavButton forge;
    private NavButton goals;
    private FocusRing ring;
    private final List<String> activated = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bar = new NavBar();
        bar.setBounds(0, 582, 420, 58);
        shop = bar.add(new NavButton("shop", "Shop", ICON, () -> activated.add("shop")));
        birds = bar.add(new NavButton("birds", "Birds", ICON, () -> activated.add("birds")));
        play = bar.add(new NavButton("play", "PLAY", ICON, () -> activated.add("play")));
        play.setPrimary(true);
        forge = bar.add(new NavButton("forge", "Forge", ICON, () -> activated.add("forge")));
        goals = bar.add(new NavButton("goals", "Goals", ICON, () -> activated.add("goals")));
        bar.layoutRow(8, 44, 2, 56, 76, 84, 4, 8);
        ring = new FocusRing();
        bar.registerFocusables(ring);
    }

    @AfterEach
    void tearDown() {
        Accessibility.clear();
    }

    @Test
    void layoutRowPlacesTheItemsOnOneCentreLine() {
        assertBounds(shop, 8, 590, 76, 44);
        assertBounds(birds, 88, 590, 76, 44);
        assertBounds(play, 168, 584, 84, 56);
        assertBounds(forge, 256, 590, 76, 44);
        assertBounds(goals, 336, 590, 76, 44);
        for (NavButton item : bar.buttons()) {
            assertEquals(612, item.centerY(), 0.0, item.id() + " shares the row centre");
        }
        assertEquals(List.of(shop, birds, play, forge, goals), bar.buttons());
        assertEquals(List.of(shop, birds, play, forge, goals), ring.nodes());
        assertFalse(bar.isFocusable(), "the bar itself never takes focus");
    }

    @Test
    void rightStepsAlongTheRowAndWrapsAtTheEnds() {
        ring.focus(shop);
        press(InputAction.RIGHT);
        assertSame(birds, ring.focused());
        press(InputAction.RIGHT);
        assertSame(play, ring.focused());
        press(InputAction.RIGHT);
        assertSame(forge, ring.focused());
        press(InputAction.RIGHT);
        assertSame(goals, ring.focused());
        press(InputAction.RIGHT);
        assertSame(shop, ring.focused(), "Right from the last item wraps to the first");
        press(InputAction.LEFT);
        assertSame(goals, ring.focused(), "Left from the first item wraps to the last");
        press(InputAction.LEFT);
        assertSame(forge, ring.focused());
    }

    @Test
    void upAndDownAreInertInsideTheRow() {
        ring.focus(play);
        press(InputAction.UP);
        assertSame(play, ring.focused(), "no item is above the primary one");
        press(InputAction.DOWN);
        assertSame(play, ring.focused());
        ring.focus(shop);
        press(InputAction.UP);
        assertSame(shop, ring.focused(), "the taller primary item is not above a side item");
    }

    @Test
    void disabledItemsAreSkipped() {
        birds.setEnabled(false);
        ring.focus(shop);
        press(InputAction.RIGHT);
        assertSame(play, ring.focused());
        ring.focus(shop);
        ring.handle(frame(EnumSet.noneOf(InputAction.class), 0, 0, false, Keys.TAB));
        assertSame(play, ring.focused(), "Tab skips the disabled item too");
        assertEquals(ButtonState.DISABLED, birds.state());
        assertFalse(birds.activate(), "a disabled item does not run its action");
        assertTrue(activated.isEmpty());
    }

    @Test
    void buttonLooksItemsUpById() {
        assertSame(goals, bar.button("goals"));
        assertSame(play, bar.button("play"));
        assertThrows(IllegalArgumentException.class, () -> bar.button("quit"));
    }

    @Test
    void thePrimaryFlagIsIndependentOfHoverAndFocus() {
        assertTrue(play.isPrimary());
        assertFalse(shop.isPrimary());
        assertEquals(ButtonState.NORMAL, play.state());
        ring.focus(play);
        assertEquals(ButtonState.FOCUSED, play.state());
        assertTrue(play.isPrimary());
        shop.setHovered(true);
        assertEquals(ButtonState.HOVER, shop.state());
        assertFalse(shop.isPrimary(), "hover never turns a side item gold");
        play.setPrimary(false);
        assertFalse(play.isPrimary());
    }

    @Test
    void aClickActivatesTheItemUnderThePointer() {
        ring.focus(shop);
        ring.handle(frame(EnumSet.noneOf(InputAction.class), 0, 0, false));
        UiNode hit = ring.handle(frame(EnumSet.noneOf(InputAction.class), goals.centerX(),
                goals.centerY(), true));
        assertSame(goals, hit);
        assertEquals(List.of("goals"), activated);
        assertSame(goals, ring.focused());
    }

    @Test
    void labelsRelabelAndRenderNonBlank() {
        goals.setText("Metas");
        assertEquals("Metas", goals.text());
        assertEquals("goals", goals.id());
        for (boolean highContrast : new boolean[] {false, true}) {
            Accessibility.setHighContrast(highContrast);
            ring.focus(forge);
            birds.setEnabled(false);
            BufferedImage frame = new BufferedImage(420, 640, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = frame.createGraphics();
            try {
                ProceduralArt.prepare(g);
                bar.render(g);
            } finally {
                g.dispose();
            }
            assertTrue(distinctColours(frame) >= 3,
                    "the bar is uniform (high contrast " + highContrast + ")");
            assertEquals(0, frame.getRGB(210, 300) >>> 24, "nothing is drawn above the band");
        }
    }

    private static void assertBounds(NavButton item, double x, double y, double w, double h) {
        assertEquals(x, item.x(), 0.0, item.id() + " x");
        assertEquals(y, item.y(), 0.0, item.id() + " y");
        assertEquals(w, item.width(), 0.0, item.id() + " width");
        assertEquals(h, item.height(), 0.0, item.id() + " height");
    }

    private static int distinctColours(BufferedImage img) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                colours.add(img.getRGB(x, y));
                if (colours.size() > 8) {
                    return colours.size();
                }
            }
        }
        return colours.size();
    }

    private void press(InputAction action) {
        ring.handle(frame(EnumSet.of(action), 0, 0, false));
    }

    private static InputFrame frame(EnumSet<InputAction> pressed, double mx, double my,
            boolean click, int... rawKeys) {
        int[] counts = new int[InputAction.values().length];
        for (InputAction a : pressed) {
            counts[a.ordinal()] = 1;
        }
        List<Integer> raw = new ArrayList<>();
        for (int k : rawKeys) {
            raw.add(k);
        }
        int clickMask = click ? 1 << Keys.BUTTON_LEFT : 0;
        return new InputFrame(counts, EnumSet.noneOf(InputAction.class),
                EnumSet.noneOf(InputAction.class), mx, my, clickMask, clickMask, 0, 0, raw,
                List.of());
    }
}
