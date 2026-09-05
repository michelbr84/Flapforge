package awt;

import java.util.Objects;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.Color}, backed by one packed ARGB int (the exact format
 * android paints consume, so no conversion happens at draw time).
 *
 * <p>Census surface (render/, ui/, app/BootSequence, app/NullPresenter):
 * constructors {@code (int rgb)} (render/TextPainter.java:176-177, app/NullPresenter.java:68),
 * {@code (int r, int g, int b)} and {@code (int r, int g, int b, int a)} (dozens of palette
 * constants across ui/), {@code (int rgba, boolean hasAlpha)} (render/Accessibility.java:347);
 * getters {@code getRed/getGreen/getBlue} (ui/component/ToastLayer.java:214-219) and
 * {@code getRGB} (render/Accessibility.java:344, render/TextPainter.java:163, render/DarknessOverlay
 * via pixel maths). {@code equals/hashCode} are implemented structurally because the game stores
 * colours in maps (ui/screens/ModifierChoiceOverlay.java:430-433). Float constructors,
 * {@code brighter/darker}, {@code HSBtoRGB} and the AWT colour constants are NOT exercised by the
 * census and are therefore absent — the two kept constants ({@code BLACK}, {@code WHITE}) exist
 * only for the P2 Android host seam that replaces the excluded desktop window classes.
 */
public class Color implements Paint {

    /** Host-seam constant: opaque black. */
    public static final Color BLACK = new Color(0xFF000000, true);
    /** Host-seam constant: opaque white. */
    public static final Color WHITE = new Color(0xFFFFFFFF, true);

    /** The packed ARGB value. */
    private final int argb;

    /** Creates an opaque colour from a packed {@code 0xRRGGBB} or {@code 0xAARRGGBB} value. */
    public Color(int rgb) {
        this.argb = rgb | 0xFF000000;
    }

    /** Creates an opaque colour from the three channel values (AWT parity: each in [0, 255]). */
    public Color(int r, int g, int b) {
        this(r, g, b, 255);
    }

    /**
     * Creates a colour from the four channel values. AWT parity: a component outside
     * {@code [0, 255]} throws {@code IllegalArgumentException} (never silently masked).
     */
    public Color(int r, int g, int b, int a) {
        checkRange(r, "red");
        checkRange(g, "green");
        checkRange(b, "blue");
        checkRange(a, "alpha");
        argb = (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void checkRange(int value, String channel) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Flapforge shim: Color " + channel
                    + " component " + value + " is outside [0, 255]");
        }
    }

    /**
     * Creates a colour from a packed value with an explicit interpretation of the high bits.
     *
     * @param rgba the packed value: {@code 0xRRGGBB} when {@code hasAlpha} is false, otherwise
     *     {@code 0xAARRGGBB}
     * @param hasAlpha whether the value carries an alpha byte
     */
    public Color(int rgba, boolean hasAlpha) {
        argb = hasAlpha ? rgba : (rgba | 0xFF000000);
    }

    /** @return the red channel, {@code [0, 255]} */
    public int getRed() {
        return (argb >> 16) & 0xFF;
    }

    /** @return the green channel, {@code [0, 255]} */
    public int getGreen() {
        return (argb >> 8) & 0xFF;
    }

    /** @return the blue channel, {@code [0, 255]} */
    public int getBlue() {
        return argb & 0xFF;
    }

    /** @return the alpha channel, {@code [0, 255]} */
    public int getAlpha() {
        return (argb >>> 24) & 0xFF;
    }

    /** @return the packed {@code 0xAARRGGBB} value */
    public int getRGB() {
        return argb;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof Color other && other.argb == argb;
    }

    @Override
    public int hashCode() {
        return Objects.hash(argb);
    }

    @Override
    public String toString() {
        return "Color[0x" + String.format("%08X", argb) + "]";
    }
}
