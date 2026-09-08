package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.component.Carousel;
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
 * The horizontal roster row of the bird selection (M11), driven with hand-built
 * {@link InputFrame}s: the tile layout and the scroll range, Left and Right walking the row and
 * clamping at its ends, the arrows and the wheel paging it, the eased tween, a clipped tile
 * refusing the pointer, the selection glow and its cap, the bounded padlock nudge, and a
 * non-blank render.
 */
class CarouselTest {

    private static final int TILES = 7;

    private Carousel carousel;
    private FocusRing ring;
    private final List<String> activated = new ArrayList<>();

    @BeforeEach
    void setUp() {
        carousel = new Carousel();
        for (int i = 0; i < TILES; i++) {
            String id = "bird" + i;
            Carousel.Tile tile = new Carousel.Tile(id, "Bird " + i, () -> activated.add(id));
            tile.setSubtitle("Archetype");
            carousel.add(tile);
        }
        carousel.setBounds(4, 238, 412, 84);
        carousel.setTileSize(Carousel.DEFAULT_TILE_WIDTH, Carousel.DEFAULT_TILE_HEIGHT);
        carousel.setGap(Carousel.DEFAULT_GAP);
        carousel.layout();
        ring = new FocusRing();
        carousel.registerFocusables(ring);
    }

    @AfterEach
    void tearDown() {
        Accessibility.clear();
        Fonts.setTextScale(1.0);
    }

    @Test
    void theTilesSitInOneRowInsideTheViewportAndTheArrowsOwnTheEnds() {
        assertEquals(TILES, carousel.size());
        assertFalse(carousel.isFocusable(), "the row itself is never a focus stop");
        double pitch = Carousel.DEFAULT_TILE_WIDTH + Carousel.DEFAULT_GAP;
        assertEquals(carousel.viewportLeft(), carousel.card("bird0").x(), 0.001);
        assertEquals(carousel.viewportLeft() + pitch, carousel.card("bird1").x(), 0.001);
        assertEquals(carousel.x() + Carousel.ARROW_WIDTH + 4, carousel.viewportLeft(), 0.001);
        assertEquals(carousel.x() + carousel.width() - Carousel.ARROW_WIDTH - 4,
                carousel.viewportRight(), 0.001);
        double content = TILES * Carousel.DEFAULT_TILE_WIDTH + (TILES - 1) * Carousel.DEFAULT_GAP;
        assertEquals(content - (carousel.viewportRight() - carousel.viewportLeft()),
                carousel.maxOffset(), 0.001);
        assertNull(carousel.card("nope"));
        assertEquals(3, carousel.indexOf("bird3"));
    }

    @Test
    void rightWalksTheRowAndClampsAtBothEnds() {
        ring.focus(carousel.card("bird0"));
        assertTrue(carousel.tick(frame(EnumSet.of(InputAction.RIGHT)), ring), "Right stepped");
        assertSame(carousel.card("bird1"), ring.focused());
        assertFalse(carousel.tick(frame(EnumSet.of(InputAction.LEFT)), ring)
                && carousel.tick(frame(EnumSet.noneOf(InputAction.class)), ring),
                "Left steps back once and then has nothing to do");
        ring.focus(carousel.card("bird0"));
        assertFalse(carousel.tick(frame(EnumSet.of(InputAction.LEFT)), ring),
                "Left on the first tile does not wrap to the last one");
        assertSame(carousel.card("bird0"), ring.focused());
        ring.focus(carousel.card("bird" + (TILES - 1)));
        assertFalse(carousel.tick(frame(EnumSet.of(InputAction.RIGHT)), ring),
                "and Right on the last one does not wrap either");
    }

    @Test
    void focusingATileFarRightScrollsItIntoView() {
        assertEquals(0, carousel.offset(), 0.001);
        ring.focus(carousel.card("bird" + (TILES - 1)));
        carousel.tick(frame(EnumSet.noneOf(InputAction.class)), ring);
        assertTrue(carousel.isScrolling(), "the row eases rather than jumping");
        for (int i = 0; i < Carousel.SCROLL_TICKS; i++) {
            carousel.tick(frame(EnumSet.noneOf(InputAction.class)), ring);
        }
        assertFalse(carousel.isScrolling());
        assertEquals(carousel.maxOffset(), carousel.offset(), 0.001,
                "the last tile is flush against the right edge");
        Carousel.Tile last = carousel.card("bird" + (TILES - 1));
        assertTrue(last.x() + last.width() <= carousel.viewportRight() + 0.001);
    }

    @Test
    void snapToJumpsAndTheArrowsAndTheWheelPageTheRow() {
        carousel.snapTo(carousel.card("bird" + (TILES - 1)));
        assertFalse(carousel.isScrolling(), "a snap does not tween");
        assertEquals(carousel.maxOffset(), carousel.offset(), 0.001);
        carousel.snapTo(carousel.card("bird0"));
        assertEquals(0, carousel.offset(), 0.001);

        double arrowY = carousel.y() + carousel.height() / 2;
        carousel.tick(click(carousel.x() + carousel.width() - 10, arrowY), ring);
        for (int i = 0; i < Carousel.SCROLL_TICKS; i++) {
            carousel.tick(frame(EnumSet.noneOf(InputAction.class)), ring);
        }
        assertEquals(Carousel.DEFAULT_TILE_WIDTH + Carousel.DEFAULT_GAP, carousel.offset(), 0.001,
                "the right arrow paged one tile");

        carousel.tick(wheel(1, carousel.x() + carousel.width() / 2, arrowY), ring);
        for (int i = 0; i < Carousel.SCROLL_TICKS; i++) {
            carousel.tick(frame(EnumSet.noneOf(InputAction.class)), ring);
        }
        assertEquals(0, carousel.offset(), 0.001, "and the wheel paged back");
    }

    @Test
    void aTileOutsideTheViewportRefusesThePointer() {
        Carousel.Tile last = carousel.card("bird" + (TILES - 1));
        assertTrue(last.x() > carousel.viewportRight(), "it starts off the right edge");
        assertFalse(last.contains(last.centerX(), last.centerY()),
                "a clipped tile is not hit-testable, so it cannot sit under an arrow");
        carousel.snapTo(last);
        assertTrue(last.contains(last.centerX(), last.centerY()), "scrolled in, it takes clicks");
    }

    @Test
    void theSelectionGlowPulsesAndIsCappedUnderReduceFlashing() {
        carousel.select("bird2");
        assertSame(carousel.card("bird2"), carousel.selected());
        assertTrue(carousel.card("bird2").isSelected());
        assertFalse(carousel.card("bird1").isSelected());
        double peak = 0;
        for (int t = 0; t <= Carousel.GLOW_PERIOD; t++) {
            carousel.setTicks(t);
            peak = Math.max(peak, carousel.glowAlpha());
        }
        assertEquals(Carousel.GLOW_PEAK, peak, 0.02, "the pulse reaches its peak");
        carousel.setReduceFlashing(true);
        assertTrue(carousel.isReduceFlashing());
        double reduced = 0;
        for (int t = 0; t <= Carousel.GLOW_PERIOD; t++) {
            carousel.setTicks(t);
            reduced = Math.max(reduced, carousel.glowAlpha());
        }
        assertTrue(reduced <= Carousel.GLOW_PEAK_REDUCED + 0.001,
                "reduce flashing caps the glow at " + Carousel.GLOW_PEAK_REDUCED
                        + ", saw " + reduced);
    }

    @Test
    void reduceFlashingLeavesTheScrollAloneBecauseMotionIsNotAFlash() {
        carousel.setReduceFlashing(true);
        ring.focus(carousel.card("bird" + (TILES - 1)));
        carousel.tick(frame(EnumSet.noneOf(InputAction.class)), ring);
        assertTrue(carousel.isScrolling(), "the row still eases; only the glow is capped");
    }

    @Test
    void aNudgedPadlockStopsOnItsOwn() {
        Carousel.Tile locked = carousel.card("bird3");
        locked.setLocked(true);
        locked.nudge();
        assertTrue(locked.isNudging());
        assertEquals(Carousel.NUDGE_TICKS, locked.nudgeTicks());
        for (int i = 0; i < Carousel.NUDGE_TICKS; i++) {
            carousel.tick(frame(EnumSet.noneOf(InputAction.class)), ring);
        }
        assertFalse(locked.isNudging(), "the nudge is bounded by its own tick budget");
    }

    @Test
    void aTileActivatesAndTheRowRendersNonBlank() {
        carousel.card("bird1").activate();
        assertEquals(List.of("bird1"), activated);
        carousel.select("bird1");
        carousel.card("bird4").setLocked(true);
        carousel.card("bird4").setBadge("150", true);
        for (boolean highContrast : new boolean[] {false, true}) {
            Accessibility.setHighContrast(highContrast);
            BufferedImage img = new BufferedImage(420, 640, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                ProceduralArt.prepare(g);
                carousel.render(g);
            } finally {
                g.dispose();
            }
            assertTrue(distinctColours(img) >= 3,
                    "the row is uniform in high contrast " + highContrast);
        }
    }

    private static int distinctColours(BufferedImage img) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 238; y < 322; y += 2) {
            for (int x = 4; x < 416; x += 2) {
                colours.add(img.getRGB(x, y));
                if (colours.size() > 8) {
                    return colours.size();
                }
            }
        }
        return colours.size();
    }

    private static InputFrame frame(EnumSet<InputAction> pressed) {
        return build(pressed, -1, -1, false, 0);
    }

    private static InputFrame click(double mx, double my) {
        return build(EnumSet.noneOf(InputAction.class), mx, my, true, 0);
    }

    private static InputFrame wheel(int notches, double mx, double my) {
        return build(EnumSet.noneOf(InputAction.class), mx, my, false, notches);
    }

    private static InputFrame build(EnumSet<InputAction> pressed, double mx, double my,
            boolean click, int wheel) {
        int[] counts = new int[InputAction.values().length];
        for (InputAction a : pressed) {
            counts[a.ordinal()] = 1;
        }
        int clickMask = click ? 1 << Keys.BUTTON_LEFT : 0;
        return new InputFrame(counts, EnumSet.noneOf(InputAction.class),
                EnumSet.noneOf(InputAction.class), mx, my, clickMask, clickMask, 0, wheel,
                List.of(), List.of());
    }
}
