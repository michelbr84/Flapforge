package awt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Pure-JVM proofs of the {@link Color} shim: AWT's {@code 0xAARRGGBB} layout for every census
 * constructor, the channel getters, structural equality, and AWT's range check.
 */
public class ColorTest {

    @Test
    public void intConstructorIsOpaqueAndIgnoresHighBits() {
        assertEquals(0xFF123456, new Color(0x123456).getRGB());
        assertEquals(0xFF123456, new Color(0x00123456).getRGB());
        assertEquals(0xFF123456, new Color(0x80123456).getRGB()); // AWT: alpha forced to 255
        assertEquals(255, new Color(0x123456).getAlpha());
    }

    @Test
    public void channelConstructorsPackInAwtOrder() {
        assertEquals(0xFF010203, new Color(1, 2, 3).getRGB());
        assertEquals(0x04010203, new Color(1, 2, 3, 4).getRGB());
        assertEquals(0xFFFFFFFF, new Color(255, 255, 255).getRGB());
        assertEquals(0x00000000, new Color(0, 0, 0, 0).getRGB());
        Color c = new Color(0xA1, 0xB2, 0xC3, 0xD4);
        assertEquals(0xA1, c.getRed());
        assertEquals(0xB2, c.getGreen());
        assertEquals(0xC3, c.getBlue());
        assertEquals(0xD4, c.getAlpha());
    }

    @Test
    public void hasAlphaFlagSelectsTheInterpretation() {
        // WindZoneRenderer/ParticleSystem ramps: new Color((rgb & 0xFFFFFF) | (a << 24), true).
        assertEquals(0x80112233, new Color(0x80112233, true).getRGB());
        assertEquals(0x00112233, new Color(0x00112233, true).getRGB());
        assertEquals(0x80, new Color(0x80112233, true).getAlpha());
        assertEquals(0xFF112233, new Color(0x80112233, false).getRGB());
        assertEquals(0xFF112233, new Color(0x112233, false).getRGB());
    }

    @Test
    public void outOfRangeChannelsAreRejectedLikeAwt() {
        int[][] bad = {{256, 0, 0, 255}, {0, -1, 0, 255}, {0, 0, 300, 255}, {0, 0, 0, 256},
                {0, 0, 0, -5}};
        for (int[] c : bad) {
            try {
                new Color(c[0], c[1], c[2], c[3]);
                fail("expected IllegalArgumentException for " + c[0] + "," + c[1] + "," + c[2]
                        + "," + c[3]);
            } catch (IllegalArgumentException expected) {
                // AWT parity: Color(int,int,int,int) throws on out-of-range components.
            }
        }
        try {
            new Color(256, 0, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // same for the three-argument form
        }
    }

    @Test
    public void equalityIsStructural() {
        assertEquals(new Color(1, 2, 3), new Color(0x010203));
        assertEquals(new Color(1, 2, 3).hashCode(), new Color(0x010203).hashCode());
        assertEquals(new Color(0x80112233, true), new Color(0x11, 0x22, 0x33, 0x80));
        assertNotEquals(new Color(1, 2, 3), new Color(1, 2, 3, 254));
        assertNotEquals(new Color(1, 2, 3), null);
    }

    @Test
    public void hostSeamConstants() {
        assertEquals(0xFF000000, Color.BLACK.getRGB());
        assertEquals(0xFFFFFFFF, Color.WHITE.getRGB());
    }

    @Test
    public void toStringShowsTheArgbValue() {
        assertEquals("Color[0x80112233]", new Color(0x80112233, true).toString());
    }
}
