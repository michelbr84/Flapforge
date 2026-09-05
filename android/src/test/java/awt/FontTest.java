package awt;

import static awt.PixelTestSupport.SIZE;
import static awt.PixelTestSupport.argb;
import static awt.PixelTestSupport.inked;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Typeface;

import awt.image.BufferedImage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proofs for the {@link Font} / {@link FontMetrics} shims (semantics 7): logical
 * family mapping, {@code deriveFont} style and size, {@code createFont} from a TrueType stream
 * through the {@link Shims} cache dir, AWT-sign metrics, and baseline-anchored
 * {@code drawString}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FontTest {

    /** Test-only copy of the bundled OFL face (byte-identical to src/main/resources). */
    private static final String BUNDLED_FONT = "/assets/fonts/Nunito-VariableFont_wght.ttf";

    @Before
    public void initShims() {
        Shims.init(RuntimeEnvironment.getApplication());
    }

    @Test
    public void logicalFamiliesMapOntoAndroidTypefaces() {
        assertSame(Typeface.SANS_SERIF, new Font(Font.SANS_SERIF, Font.PLAIN, 16).typeface());
        assertSame(Typeface.MONOSPACE, new Font(Font.MONOSPACED, Font.PLAIN, 12).typeface());
        assertEquals(Font.SANS_SERIF, new Font(Font.SANS_SERIF, Font.PLAIN, 16).getFamily());
        assertEquals(16f, new Font(Font.SANS_SERIF, Font.PLAIN, 16).size(), 0f);
        assertEquals(Font.PLAIN, new Font(Font.SANS_SERIF, Font.PLAIN, 16).style());
    }

    @Test
    public void constructorStyleIsAppliedToTheTypeface() {
        Font bold = new Font(Font.SANS_SERIF, Font.BOLD, 16);
        assertTrue(bold.typeface().isBold());
        assertFalse(bold.typeface().isItalic());
        Font italic = new Font(Font.SANS_SERIF, Font.ITALIC, 16);
        assertTrue(italic.typeface().isItalic());
        Font both = new Font(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, 16);
        assertTrue(both.typeface().isBold());
        assertTrue(both.typeface().isItalic());
        assertEquals(Font.BOLD | Font.ITALIC, both.style());
    }

    @Test
    public void deriveFontWithStyleAndSizeChangesBoth() {
        // Fonts.get(style, size): base().deriveFont(st, size) — the UI cache's bold faces.
        Font base = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
        Font bold = base.deriveFont(Font.BOLD, 24f);
        assertEquals(Font.BOLD, bold.style());
        assertEquals(24f, bold.size(), 0f);
        assertTrue("deriveFont(BOLD, size) must yield a bold typeface", bold.typeface().isBold());
        assertNotSame(base.typeface(), bold.typeface());
        assertEquals(base.getFamily(), bold.getFamily());

        Font italic = base.deriveFont(Font.ITALIC, 20f);
        assertTrue(italic.typeface().isItalic());
        assertFalse(italic.typeface().isBold());

        Font plainAgain = bold.deriveFont(Font.PLAIN, 12f);
        assertFalse(plainAgain.typeface().isBold());
        assertEquals(Font.PLAIN, plainAgain.style());

        Font sameStyle = base.deriveFont(Font.PLAIN, 30f);
        assertSame("same style keeps the typeface", base.typeface(), sameStyle.typeface());
        assertEquals(30f, sameStyle.size(), 0f);
    }

    @Test
    public void deriveFontWithSizeKeepsStyleAndFamily() {
        // Fonts.mono(size): m.deriveFont((float) scaled(s)).
        Font mono = new Font(Font.MONOSPACED, Font.BOLD, 12);
        Font bigger = mono.deriveFont(22f);
        assertEquals(22f, bigger.size(), 0f);
        assertEquals(Font.BOLD, bigger.style());
        assertSame(mono.typeface(), bigger.typeface());
        assertEquals(Font.MONOSPACED, bigger.getFamily());
    }

    @Test
    public void fontMetricsUseAwtSignsAndPixelSizes() {
        Graphics2D g = argb().createGraphics();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        FontMetrics fm = g.getFontMetrics();

        assertTrue("ascent is a positive distance above the baseline", fm.getAscent() > 0);
        assertTrue("descent is a positive distance below the baseline", fm.getDescent() > 0);
        assertTrue("ascent dominates descent for Latin text", fm.getAscent() > fm.getDescent());
        assertTrue("ascent is in the ballpark of a 24 px em", fm.getAscent() >= 14
                && fm.getAscent() <= 32);
        assertTrue("height = leading + ascent + descent",
                fm.getHeight() >= fm.getAscent() + fm.getDescent());
        assertEquals(0, fm.stringWidth(""));
        int w1 = fm.stringWidth("W");
        int w2 = fm.stringWidth("WW");
        assertTrue(w1 > 0);
        assertTrue("two glyphs advance further than one", w2 > w1);
        assertTrue("advance is roughly additive", Math.abs(w2 - 2 * w1) <= 2);
    }

    @Test
    public void fontMetricsScaleWithTheFontSize() {
        Graphics2D g = argb().createGraphics();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int small = g.getFontMetrics().stringWidth("Flapforge");
        int smallAscent = g.getFontMetrics().getAscent();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        int large = g.getFontMetrics().stringWidth("Flapforge");
        int largeAscent = g.getFontMetrics().getAscent();

        assertTrue("width doubles with size (" + small + " -> " + large + ")",
                Math.abs(large - 2 * small) <= 3);
        assertTrue("ascent doubles with size (" + smallAscent + " -> " + largeAscent + ")",
                Math.abs(largeAscent - 2 * smallAscent) <= 2);
    }

    @Test
    public void getFontMetricsForAnExplicitFontIgnoresTheCurrentFont() {
        // TextPainter.width(g, font, text): g.getFontMetrics(font).stringWidth(text).
        Graphics2D g = argb().createGraphics();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        Font big = new Font(Font.SANS_SERIF, Font.PLAIN, 36);
        assertTrue(g.getFontMetrics(big).stringWidth("Flapforge")
                > 2 * g.getFontMetrics().stringWidth("Flapforge"));
    }

    @Test
    public void drawStringTreatsYAsTheBaseline() {
        BufferedImage image = argb();
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0));
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
        g.setFont(font);
        int ascent = g.getFontMetrics().getAscent();
        int baseline = 40;
        g.drawString("H", 8, baseline);

        int aboveBaseline = 0;
        int belowBaseline = 0;
        int aboveAscent = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!inked(image, x, y)) {
                    continue;
                }
                if (y < baseline - ascent - 2) {
                    aboveAscent++;
                } else if (y < baseline) {
                    aboveBaseline++;
                } else if (y >= baseline + 2) {
                    belowBaseline++;
                }
            }
        }
        assertTrue("the glyph sits above the baseline", aboveBaseline > 20);
        assertEquals("an H has no descender: nothing well below the baseline", 0, belowBaseline);
        assertEquals("nothing above the ascent line", 0, aboveAscent);
        // Left edge: the glyph starts at (or just right of) x = 8.
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < 7; x++) {
                assertFalse("no ink left of the start x", inked(image, x, y));
            }
        }
    }

    @Test
    public void drawStringFollowsTheTransformAndClip() {
        BufferedImage image = argb();
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0, 0, 0));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.setClip(new awt.geom.Rectangle2D.Double(0, 0, 64, 32)); // top half only
        g.translate(0, 100);
        g.drawString("HHHH", 4, -100 + 60); // device baseline y = 60: entirely in the clipped half

        assertEquals("text under the clipped half is not drawn", 0, PixelTestSupport.countInked(image));

        g.drawString("HHHH", 4, -100 + 28); // device baseline y = 28: inside the clip
        assertTrue(PixelTestSupport.countInked(image) > 20);
        for (int y = 32; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                assertFalse("clip fences text", inked(image, x, y));
            }
        }
    }

    @Test
    public void createFontLoadsATrueTypeStreamThroughTheCacheDir() throws Exception {
        Font loaded;
        try (InputStream in = bundledFont()) {
            loaded = Font.createFont(Font.TRUETYPE_FONT, in);
        }
        assertNotNull(loaded);
        assertEquals("createFont returns a size-1 face (AWT parity)", 1f, loaded.size(), 0f);
        assertEquals(Font.PLAIN, loaded.style());
        assertNotNull(loaded.typeface());
        assertNotSame(Typeface.SANS_SERIF, loaded.typeface());
        assertNotSame(Typeface.DEFAULT, loaded.typeface());

        Font sized = loaded.deriveFont(24f);
        Graphics2D g = argb().createGraphics();
        g.setFont(sized);
        assertTrue(g.getFontMetrics().stringWidth("Flapforge") > 0);
        assertTrue(g.getFontMetrics().getAscent() > 0);

        Font bold = loaded.deriveFont(Font.BOLD, 24f);
        assertTrue(bold.typeface().isBold());

        // The temp copy is removed once the typeface has been parsed.
        File[] leftovers = Shims.cacheDir().listFiles(
                (dir, name) -> name.startsWith("flapforge-font-"));
        assertNotNull(leftovers);
        assertEquals(0, leftovers.length);
    }

    @Test
    public void createFontCoversTheGamesAccentedText() throws Exception {
        // Fonts.canDisplay(text): base().canDisplayUpTo(text) == -1 is the pt_BR gate.
        Font loaded;
        try (InputStream in = bundledFont()) {
            loaded = Font.createFont(Font.TRUETYPE_FONT, in);
        }
        assertEquals(-1, loaded.canDisplayUpTo("Forja ação coração pássaro"));
        assertEquals(-1, new Font(Font.SANS_SERIF, Font.PLAIN, 16).canDisplayUpTo("ação"));
        assertEquals(-1, loaded.canDisplayUpTo(""));
    }

    @Test
    public void createFontRejectsGarbage() {
        byte[] junk = "definitely not a font".getBytes(StandardCharsets.UTF_8);
        try {
            Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(junk));
            fail("expected FontFormatException");
        } catch (FontFormatException expected) {
            assertNotNull(expected.getMessage());
        }
        try {
            Font.createFont(99, new ByteArrayInputStream(junk));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("census"));
        } catch (FontFormatException e) {
            fail("format check must run before the stream is read");
        }
    }

    @Test
    public void graphicsWithoutAFontStillMeasuresWithADefault() {
        Graphics2D g = argb().createGraphics();
        assertTrue(g.getFontMetrics().stringWidth("x") > 0);
    }

    private static InputStream bundledFont() throws IOException {
        InputStream in = FontTest.class.getResourceAsStream(BUNDLED_FONT);
        if (in == null) {
            throw new IOException("test copy of the bundled font missing from the classpath: "
                    + BUNDLED_FONT);
        }
        return in;
    }
}
