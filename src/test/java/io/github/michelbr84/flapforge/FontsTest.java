package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.render.AssetManager;
import io.github.michelbr84.flapforge.render.Fonts;
import java.awt.Font;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The font side of D25: whatever font is active must be able to draw every character the shipped
 * translations contain, or a Portuguese player reads boxes instead of accents. The check is the
 * one the decision names — {@code canDisplayUpTo(text) == -1} for every value of
 * {@code pt_BR.json} — and it runs over the real files, so a new string with a character the
 * logical family cannot draw fails here rather than on a player's screen. When M8 installs the
 * bundled OFL font through {@link Fonts#install(Font)} this test is what proves the swap is safe.
 */
class FontsTest {

    @AfterEach
    void restoreScale() {
        Fonts.setTextScale(1.0);
    }

    @Test
    void theActiveFontDisplaysEveryCharacterOfEveryPortugueseString() {
        Map<String, String> table = Strings.tableOf("pt_BR");
        assertTrue(table.size() > 50, "the translation must be loaded, found " + table.size());
        Font base = Fonts.base();
        for (Map.Entry<String, String> entry : table.entrySet()) {
            assertEquals(-1, base.canDisplayUpTo(entry.getValue()),
                    () -> "the active font cannot draw " + entry.getKey() + " = "
                            + entry.getValue());
            assertTrue(Fonts.canDisplay(entry.getValue()), entry.getKey());
        }
    }

    @Test
    void everyStyleAndSizeTheUiUsesDisplaysThemToo() {
        Map<String, String> table = Strings.tableOf("pt_BR");
        Font[] fonts = {Fonts.regular(11), Fonts.regular(15), Fonts.bold(20), Fonts.bold(58),
            Fonts.mono(11)};
        for (Font font : fonts) {
            for (Map.Entry<String, String> entry : table.entrySet()) {
                assertEquals(-1, font.canDisplayUpTo(entry.getValue()),
                        () -> font.getFontName() + " cannot draw " + entry.getKey());
            }
        }
    }

    @Test
    void theEnglishSourceTableIsCoveredAsWell() {
        for (Map.Entry<String, String> entry : Strings.tableOf("en").entrySet()) {
            assertEquals(-1, Fonts.base().canDisplayUpTo(entry.getValue()), entry.getKey());
        }
    }

    @Test
    void textScaleChangesDerivedSizesAndIsClamped() {
        assertEquals(1.0, Fonts.textScale(), 1e-9);
        Font plain = Fonts.regular(20);
        assertEquals(20, plain.getSize());

        Fonts.setTextScale(1.5);
        assertEquals(1.5, Fonts.textScale(), 1e-9);
        Font bigger = Fonts.regular(20);
        assertEquals(30, bigger.getSize(), "the requested size is scaled, not the layout");
        assertNotEquals(plain, bigger, "the cache is cleared when the scale changes");
        assertEquals(18, Fonts.mono(12).getSize());

        Fonts.setTextScale(99);
        assertEquals(Fonts.MAX_TEXT_SCALE, Fonts.textScale(), 1e-9);
        Fonts.setTextScale(0.01);
        assertEquals(Fonts.MIN_TEXT_SCALE, Fonts.textScale(), 1e-9);
        Fonts.setTextScale(Double.NaN);
        assertEquals(Fonts.MIN_TEXT_SCALE, Fonts.textScale(), 1e-9, "NaN is ignored");

        Fonts.setTextScale(1.0);
        assertEquals(20, Fonts.regular(20).getSize());
    }

    @Test
    void scaledSizesStayInsideTheDeriveRange() {
        Fonts.setTextScale(Fonts.MAX_TEXT_SCALE);
        assertTrue(Fonts.regular(Fonts.MAX_SIZE).getSize() <= Fonts.MAX_SIZE);
        Fonts.setTextScale(Fonts.MIN_TEXT_SCALE);
        assertTrue(Fonts.regular(1).getSize() >= Fonts.MIN_SIZE);
    }

    // ----------------------------------------------------------------------- M8: the bundled font

    /**
     * Loads the bundled OFL font the way the boot step does (D18, E10): through the manifest
     * entry, never a static initialiser.
     */
    private static Font bundledFont() {
        Font font = AssetManager.fromClasspath().font("font/ui")
                .orElseThrow(() -> new AssertionError(
                        "the bundled font must load: " + AssetManager.fromClasspath().errors()));
        return font;
    }

    @Test
    void theBundledFontDisplaysEveryCharacterOfEveryShippedString() {
        Font base = bundledFont();
        for (String language : new String[] {"en", "pt_BR"}) {
            for (Map.Entry<String, String> entry : Strings.tableOf(language).entrySet()) {
                assertEquals(-1, base.canDisplayUpTo(entry.getValue()),
                        () -> "the bundled font cannot draw " + language + " "
                                + entry.getKey() + " = " + entry.getValue());
            }
        }
    }

    @Test
    void theBundledFontDerivesEverySizeAndStyleTheUiUses() {
        Font base = bundledFont();
        Font[] styles = {base.deriveFont(Font.PLAIN, 11f), base.deriveFont(Font.PLAIN, 15f),
            base.deriveFont(Font.BOLD, 20f), base.deriveFont(Font.BOLD, 58f),
            base.deriveFont(Font.PLAIN, 12f).deriveFont(Font.ITALIC)};
        for (Font font : styles) {
            for (Map.Entry<String, String> entry : Strings.tableOf("pt_BR").entrySet()) {
                assertEquals(-1, font.canDisplayUpTo(entry.getValue()),
                        () -> font.getFontName() + " cannot draw " + entry.getKey());
            }
        }
    }

    @Test
    void theBundledFontInstallsAndSurvivesATextScaleChange() {
        Font installed = Fonts.base();
        Font previous = installed;
        try {
            Fonts.install(bundledFont());
            assertEquals(bundledFont(), Fonts.base(), "install swaps the base family");
            for (Map.Entry<String, String> entry : Strings.tableOf("pt_BR").entrySet()) {
                assertEquals(-1, Fonts.base().canDisplayUpTo(entry.getValue()), entry.getKey());
            }
            Font bold = Fonts.bold(20);
            assertEquals(Font.BOLD, bold.getStyle(), "bold is still derivable on one face");
            Fonts.setTextScale(1.5);
            assertEquals(30, Fonts.regular(20).getSize(), "derived sizes honour the scale");
            assertEquals(-1, Fonts.regular(20).canDisplayUpTo("ção ÀÉÍ"));
        } finally {
            Fonts.setTextScale(1.0);
            Fonts.install(previous);
        }
    }
}
