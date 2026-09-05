package awt;

import static awt.PixelTestSupport.SIZE;
import static awt.PixelTestSupport.argb;
import static awt.PixelTestSupport.countInked;
import static awt.PixelTestSupport.inked;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import awt.image.BufferedImage;

import java.io.BufferedInputStream;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric proofs for the bundled OFL font going through the exact path the game uses
 * (render/AssetManager.loadFont: {@code getResourceAsStream} + {@code BufferedInputStream} +
 * {@code Font.createFont(TRUETYPE_FONT, in)}), then {@code deriveFont} and baseline-anchored
 * {@code drawString}; the {@link Shims} bootstrap failure mode; {@link FontMetrics} following
 * the size the font carries; and the two logical families drawing. The font file is a test-only
 * copy under {@code android/src/test/resources/assets/fonts/} (same classpath location the
 * manifest entry resolves to).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BundledFontTest {

    /** {@code AssetManager.ASSET_ROOT + entry.path()} for the manifest's {@code font/ui}. */
    private static final String FONT_RESOURCE = "/assets/fonts/Nunito-VariableFont_wght.ttf";

    private static final Color INK = new Color(0, 0, 0);

    @Before
    public void initShims() {
        Shims.init(RuntimeEnvironment.getApplication());
    }

    /** Mirrors AssetManager.loadFont: resource stream, buffered, one size-1 base face. */
    private static Font loadBundledFont() throws Exception {
        try (InputStream in = BundledFontTest.class.getResourceAsStream(FONT_RESOURCE)) {
            assertNotNull("test copy of the bundled font on the classpath: " + FONT_RESOURCE, in);
            return Font.createFont(Font.TRUETYPE_FONT, new BufferedInputStream(in));
        }
    }

    // ---------------------------------------------------------------- (a) createFont + draw

    @Test
    public void bundledFontDrawsACapitalInTheBandAboveTheBaseline() throws Exception {
        Font base = loadBundledFont();
        assertEquals("createFont hands back the size-1 base face", 1f, base.size(), 0f);
        Font ui = base.deriveFont(24f);
        assertEquals(24f, ui.size(), 0f);

        BufferedImage image = argb();
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(INK);
        g.setFont(ui);
        int ascent = g.getFontMetrics().getAscent();
        int descent = g.getFontMetrics().getDescent();
        int baseline = 40;
        g.drawString("H", 8, baseline);
        g.dispose();

        int inBand = 0;
        int aboveBand = 0;
        int belowBaseline = 0;
        int farBelow = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!inked(image, x, y)) {
                    continue;
                }
                if (y < baseline - ascent - 2) {
                    aboveBand++;
                } else if (y < baseline) {
                    inBand++;
                } else if (y >= baseline + descent + 2) {
                    farBelow++;
                } else if (y >= baseline + 2) {
                    belowBaseline++;
                }
            }
        }
        assertTrue("the capital is inked above y = " + baseline + " (got " + inBand + ")",
                inBand > 20);
        assertEquals("nothing above the ascent line", 0, aboveBand);
        assertEquals("an H has no descender: nothing well below the baseline", 0,
                belowBaseline);
        assertEquals("nothing below the descent", 0, farBelow);
    }

    // ---------------------------------------------------------------- (b) Shims bootstrap

    @Test
    public void createFontWithoutShimsInitFailsWithAClearIllegalStateException()
            throws Exception {
        Shims.reset();
        try (InputStream in = BundledFontTest.class.getResourceAsStream(FONT_RESOURCE)) {
            Font.createFont(Font.TRUETYPE_FONT, new BufferedInputStream(in));
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue("message names the missing call: " + expected.getMessage(),
                    expected.getMessage().contains("Shims.init"));
        } catch (FontFormatException wrapped) {
            fail("the bootstrap failure must not be folded into FontFormatException (the game "
                    + "catches that and silently falls back): " + wrapped);
        } finally {
            Shims.init(RuntimeEnvironment.getApplication());
        }
        // Back in business once the host has initialised the shims.
        assertNotNull(loadBundledFont());
    }

    @Test
    public void shimsContextIsTheInstalledApplication() {
        assertEquals(RuntimeEnvironment.getApplication(), Shims.context());
        assertTrue(Shims.cacheDir().isDirectory());
    }

    // ---------------------------------------------------------------- (c) FontMetrics

    @Test
    public void bundledFontMetricsFollowTheDerivedSize() throws Exception {
        Font base = loadBundledFont();
        Graphics2D g = argb().createGraphics();
        g.setFont(base.deriveFont(24f));
        FontMetrics fm = g.getFontMetrics();

        assertEquals(0, fm.stringWidth(""));
        int one = fm.stringWidth("M");
        int three = fm.stringWidth("MMM");
        assertTrue("M advances (" + one + ")", one > 0);
        assertTrue("MMM (" + three + ") advances further than M (" + one + ")", three > one);
        assertTrue("ascent > 0 (" + fm.getAscent() + ")", fm.getAscent() > 0);
        assertTrue("descent >= 0 (" + fm.getDescent() + ")", fm.getDescent() >= 0);
        assertTrue("height (" + fm.getHeight() + ") >= ascent + descent ("
                + (fm.getAscent() + fm.getDescent()) + ")",
                fm.getHeight() >= fm.getAscent() + fm.getDescent());

        // The metrics come from the size the Font carries, not from the base face.
        int small = g.getFontMetrics(base.deriveFont(12f)).stringWidth("Flapforge");
        int large = g.getFontMetrics(base.deriveFont(48f)).stringWidth("Flapforge");
        assertTrue("48 px (" + large + ") is wider than 12 px (" + small + ")", large > small);
        assertTrue("about four times wider", large > 3 * small && large < 5 * small);
        int smallAscent = g.getFontMetrics(base.deriveFont(12f)).getAscent();
        int largeAscent = g.getFontMetrics(base.deriveFont(48f)).getAscent();
        assertTrue("ascent scales too", largeAscent > 3 * smallAscent);
    }

    @Test
    public void bundledFontMetricsRoundLikeFontDesignMetrics() throws Exception {
        // JDK 17 FontDesignMetrics: (int) (0.95f + value). Measured on the desktop JDK with the
        // bundled Nunito (hhea 1011 / -353 per 1000 em): 11 px -> 12 / 4, 12 px -> 13 / 5,
        // 16 px -> 17 / 6, 28 px -> 29 / 10. Half-up rounding would give 11 / 4, 12 / 4,
        // 16 / 6 and 28 / 10, moving TextPainter.centeredBaseline by half a pixel.
        Font base = loadBundledFont();
        Graphics2D g = argb().createGraphics();
        int[][] expected = {{11, 12, 4}, {12, 13, 5}, {16, 17, 6}, {28, 29, 10}};
        for (int[] e : expected) {
            FontMetrics fm = g.getFontMetrics(base.deriveFont((float) e[0]));
            assertEquals(e[0] + " px ascent", e[1], fm.getAscent());
            assertEquals(e[0] + " px descent", e[2], fm.getDescent());
            // Nunito's line gap is 0, so FontDesignMetrics' height is ascent + descent here.
            assertEquals(e[0] + " px height", e[1] + e[2], fm.getHeight());
        }
    }

    @Test
    public void heightIsTheSumOfTheRoundedPartsForEverySize() {
        // AWT: getHeight() == getLeading() + getAscent() + getDescent() on the int values, so the
        // vertical-centring maths in TextPainter never sees height < ascent + descent.
        Graphics2D g = argb().createGraphics();
        for (int size = 6; size <= 60; size++) {
            FontMetrics fm = g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, size));
            assertTrue("size " + size + ": height " + fm.getHeight() + " < ascent + descent "
                    + (fm.getAscent() + fm.getDescent()),
                    fm.getHeight() >= fm.getAscent() + fm.getDescent());
        }
    }

    // ---------------------------------------------------------------- (d) logical families

    @Test
    public void logicalFamiliesConstructAndDraw() {
        Font bold = new Font(Font.SANS_SERIF, Font.BOLD, 16);
        assertEquals(Font.BOLD, bold.style());
        assertEquals(16f, bold.size(), 0f);
        assertTrue(bold.typeface().isBold());
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        assertEquals(Font.PLAIN, mono.style());
        assertEquals(12f, mono.size(), 0f);

        for (Font font : new Font[] {bold, mono}) {
            BufferedImage image = argb();
            Graphics2D g = image.createGraphics();
            g.setColor(INK);
            g.setFont(font);
            g.drawString("Ag", 4, 40);
            int advance = g.getFontMetrics().stringWidth("Ag");
            g.dispose();
            assertTrue(font + " draws ink", countInked(image) > 10);
            assertTrue(font + " advances", advance > 0);
        }
    }

    @Test
    public void derivedBundledFacesKeepTheFamilyAndChangeStyle() throws Exception {
        // Fonts.get(style, size) derives every UI face from the one installed base.
        Font base = loadBundledFont();
        Font bold = base.deriveFont(Font.BOLD, 20f);
        assertTrue(bold.typeface().isBold());
        assertEquals(20f, bold.size(), 0f);
        assertEquals(base.getFamily(), bold.getFamily());
        Font plain = base.deriveFont(20f);
        assertEquals(Font.PLAIN, plain.style());
        assertEquals(base.typeface(), plain.typeface());
    }
}
