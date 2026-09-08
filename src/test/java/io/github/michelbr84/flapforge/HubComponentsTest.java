package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.ui.component.AbilityCard;
import io.github.michelbr84.flapforge.ui.component.AttributeBadge;
import io.github.michelbr84.flapforge.ui.component.CtaButton;
import io.github.michelbr84.flapforge.ui.component.CurrencyChip;
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.HubHeader;
import io.github.michelbr84.flapforge.ui.component.IconButton;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The hub's smaller components (D17, M11): the call to action's glow, subtitle and font
 * stepping, the icon button's accessible name, the currency chip's roll-up, and the header,
 * ability card and attribute badge the section screens share, each also rendered non-blank.
 */
class HubComponentsTest {

    private static final IconPainter ICON =
            (g, cx, cy, size, color) -> ProceduralArt.drawGear(g, cx, cy, size, color, color);

    @AfterEach
    void tearDown() {
        Accessibility.clear();
        Fonts.setTextScale(1.0);
    }

    @Test
    void theGlowIsATriangleWaveOnTheTick() {
        CtaButton cta = new CtaButton("START RUN", null);
        cta.setTicks(0);
        assertEquals(0.0, cta.glowAlpha(), 1e-9);
        cta.setTicks(CtaButton.GLOW_PERIOD / 2);
        assertEquals(CtaButton.GLOW_PEAK, cta.glowAlpha(), 1e-9, "peak at half the period");
        int quarter = CtaButton.GLOW_PERIOD / 4;
        cta.setTicks(quarter);
        assertEquals(CtaButton.GLOW_PEAK * 2.0 * quarter / CtaButton.GLOW_PERIOD,
                cta.glowAlpha(), 1e-9, "rising linearly");
        cta.setTicks(CtaButton.GLOW_PERIOD);
        assertEquals(0.0, cta.glowAlpha(), 1e-9, "back to zero after one period");
        cta.setReduceFlashing(true);
        assertTrue(cta.isReduceFlashing());
        cta.setTicks(CtaButton.GLOW_PERIOD / 2);
        assertEquals(CtaButton.GLOW_PEAK_REDUCED, cta.glowAlpha(), 1e-9);
        assertTrue(CtaButton.GLOW_PEAK_REDUCED < CtaButton.GLOW_PEAK);
        cta.setEnabled(false);
        assertEquals(0.0, cta.glowAlpha(), 1e-9, "a disabled plate does not glow");
    }

    @Test
    void theCallToActionCarriesASubtitleAndAnIcon() {
        CtaButton cta = new CtaButton("START RUN", null);
        assertEquals("", cta.subtitle());
        assertNull(cta.icon());
        cta.setSubtitle("Green Fields • Normal");
        cta.setIcon(ICON);
        assertEquals("Green Fields • Normal", cta.subtitle());
        assertSame(ICON, cta.icon());
        cta.setBounds(40, 474, 340, 62);
        CtaButton bare = new CtaButton("START RUN", null);
        bare.setBounds(40, 474, 340, 62);
        assertNonBlank("bare", bare::render, bare);
        assertNonBlank("with subtitle", cta::render, cta);
        cta.setFocused(true);
        assertNonBlank("focused", cta::render, cta);
        cta.setFocused(false);
        cta.setEnabled(false);
        assertNonBlank("disabled", cta::render, cta);
        cta.setEnabled(true);
        Fonts.setTextScale(1.5);
        cta.setText("INICIAR PARTIDA");
        assertNonBlank("scaled long title", cta::render, cta);
        Accessibility.setHighContrast(true);
        assertNonBlank("high contrast", cta::render, cta);
    }

    @Test
    void theIconButtonKeepsItsAccessibleName() {
        int[] hits = {0};
        IconButton gear = new IconButton("Settings", ICON, () -> hits[0]++);
        gear.setBounds(354, 10, 40, 40);
        assertEquals("Settings", gear.text());
        assertSame(ICON, gear.icon());
        assertTrue(gear.activate());
        assertEquals(1, hits[0]);
        assertEquals(ButtonState.NORMAL, gear.state());
        assertNonBlank("gear", gear::render, gear);
        gear.setEnabled(false);
        assertEquals(ButtonState.DISABLED, gear.state());
        assertNonBlank("gear disabled", gear::render, gear);
        IconPainter other = (g, cx, cy, size, color) -> ProceduralArt.drawCrown(g, cx, cy, size,
                color);
        gear.setIcon(other);
        assertSame(other, gear.icon());
    }

    @Test
    void theCurrencyChipRollsItsReadoutUpOnTick() {
        int[] hits = {0};
        CurrencyChip chip = new CurrencyChip(() -> hits[0]++);
        chip.setBounds(200, 12, 130, 30);
        chip.display().setFormat("{0}");
        chip.display().setAmountNow(10);
        chip.display().setAmount(100);
        assertTrue(chip.display().isRolling());
        for (int i = 0; i < CurrencyDisplay.ROLL_TICKS; i++) {
            chip.tick();
        }
        assertFalse(chip.display().isRolling());
        assertEquals(100, chip.display().displayedAmount());
        assertTrue(chip.activate());
        assertEquals(1, hits[0]);
        assertTrue(chip.isFocusable(), "the chip opens the shop, so it takes focus");
        assertFalse(chip.display().isFocusable(), "the readout inside never does");
        chip.setFocused(true);
        assertEquals(ButtonState.FOCUSED, chip.state());
        assertNonBlank("chip", chip::render, chip);
        assertTrue(chip.display().x() > chip.x(), "the readout sits inside the plate");
        assertTrue(chip.display().x() + chip.display().width() < chip.x() + chip.width());
    }

    @Test
    void theCallToActionEllipsisesATitleTheSmallestSizeCannotFit() {
        CtaButton cta = new CtaButton("USE IRONBEAK", null);
        cta.setBounds(40, 512, 340, 44);
        BufferedImage img = new BufferedImage(420, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            ProceduralArt.prepare(g);
            cta.render(g);
            assertEquals("USE IRONBEAK", cta.shownTitle(), "it fits at the designed scale");
            Fonts.setTextScale(1.5);
            cta.setText("COMPRAR POR TRESENTAS E CINQUENTA MOEDAS DE OURO");
            cta.render(g);
            String shown = cta.shownTitle();
            assertTrue(shown.endsWith("\u2026"), "ellipsised, not spilled: " + shown);
            assertTrue(shown.length() < "COMPRAR POR TRESENTAS E CINQUENTA MOEDAS DE OURO".length());
        } finally {
            g.dispose();
        }
    }

    @Test
    void theHeaderPutsTheTitleLeftAndTheCoinsRight() {
        HubHeader plain = new HubHeader(null);
        plain.setTitle("Birds");
        assertEquals("Birds", plain.title());
        assertEquals(HubHeader.HEIGHT, plain.height(), 1e-9, "the band is the hub's own");
        assertFalse(plain.chip().isFocusable(), "without a route the chip is a readout");
        assertFalse(plain.chip().isEnabled());
        int[] hits = {0};
        HubHeader routed = new HubHeader(() -> hits[0]++);
        routed.setTitle("Aves");
        assertTrue(routed.chip().isFocusable(), "with a route it opens the shop");
        assertTrue(routed.chip().activate());
        assertEquals(1, hits[0]);
        assertSame(routed.chip().display(), routed.display());
        assertEquals(HubHeader.CHIP_X, routed.chip().x(), 1e-9);
        assertEquals(HubHeader.CHIP_W, routed.chip().width(), 1e-9);
        assertTrue(routed.chip().x() > HubHeader.TITLE_X, "the coins sit right of the title");
        routed.display().setFormat("{0}");
        routed.display().setAmountNow(692);
        routed.tick();
        assertEquals(692, routed.display().displayedAmount());
        assertNonBlank("header", routed::render, routed);
        Accessibility.setHighContrast(true);
        assertNonBlank("header in high contrast", routed::render, routed);
    }

    @Test
    void theAbilityCardShowsItsRoleNameLevelAndTone() {
        AbilityCard card = new AbilityCard();
        card.setBounds(16, 426, 126, 34);
        assertEquals(AbilityCard.Tone.EMPTY, card.tone(), "a fresh card is an empty slot");
        card.bind("Active", "Double Flap", "Lv 1/3", ICON, AbilityCard.Tone.NORMAL);
        assertEquals("Active", card.label());
        assertEquals("Double Flap", card.value());
        assertEquals("Lv 1/3", card.levelText());
        assertSame(ICON, card.icon());
        assertEquals(AbilityCard.Tone.NORMAL, card.tone());
        for (AbilityCard.Tone tone : AbilityCard.Tone.values()) {
            card.bind("Passive 1", "Coin Magnet", "Lv 2/3", ICON, tone);
            assertNonBlank("card " + tone, card::render, card);
        }
        Fonts.setTextScale(1.5);
        card.bind("Passiva 1", "Ima de Moedas Reforcado", "Nv 2/3", ICON,
                AbilityCard.Tone.NORMAL);
        assertNonBlank("scaled card", card::render, card);
        Accessibility.setHighContrast(true);
        assertNonBlank("card in high contrast", card::render, card);
    }

    @Test
    void theAttributeBadgeClampsItsValueAndKeepsTheLabelReadable() {
        AttributeBadge badge = new AttributeBadge(ICON);
        badge.setBounds(12, 212, 128, 22);
        assertFalse(badge.isFocusable(), "a badge states a number, it does not do anything");
        assertSame(ICON, badge.icon());
        badge.bind("Mobility", 6);
        assertEquals("Mobility", badge.label());
        assertEquals(6, badge.value());
        badge.bind("Defence", 99);
        assertEquals(AttributeBadge.MAX, badge.value(), "clamped to the top of the scale");
        badge.bind("Control", -4);
        assertEquals(0, badge.value(), "clamped to the bottom");
        badge.bind("Mobilidade", 10);
        assertNonBlank("badge", badge::render, badge);
        Fonts.setTextScale(1.5);
        assertNonBlank("scaled badge", badge::render, badge);
        Accessibility.setHighContrast(true);
        assertNonBlank("badge in high contrast", badge::render, badge);
    }

    private static void assertNonBlank(String what, Consumer<Graphics2D> draw,
            io.github.michelbr84.flapforge.ui.UiNode node) {
        BufferedImage img = new BufferedImage(420, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            ProceduralArt.prepare(g);
            draw.accept(g);
        } finally {
            g.dispose();
        }
        Set<Integer> colours = new HashSet<>();
        int x0 = (int) node.x();
        int y0 = (int) node.y();
        for (int y = y0; y < y0 + (int) node.height(); y++) {
            for (int x = x0; x < x0 + (int) node.width(); x++) {
                colours.add(img.getRGB(x, y));
                if (colours.size() > 8) {
                    return;
                }
            }
        }
        assertTrue(colours.size() >= 3, what + " is uniform");
    }
}
