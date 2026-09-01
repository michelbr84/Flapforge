package io.github.michelbr84.flapforge.render;

import java.awt.Font;
import java.util.Arrays;
import java.util.Objects;

/**
 * Font provider with a per-style-and-size cache (D17, D25).
 *
 * <p>The base family is the logical {@code SansSerif} until M8 installs the bundled OFL font
 * through {@link #install(Font)}; every derived size comes from {@link Font#deriveFont}, so the
 * switch is invisible to callers. The debug overlay uses the logical {@code Monospaced} family.
 * Nothing here touches the toolkit at class-initialisation time (E10): the first font is created
 * on the first request. Lookups index plain arrays, so per-frame calls allocate nothing; a
 * benign race may derive the same immutable font twice.
 */
public final class Fonts {

    /** Size below which no font is derived (requests are clamped). */
    public static final int MIN_SIZE = 6;
    /** Size above which no font is derived (requests are clamped). */
    public static final int MAX_SIZE = 200;

    private static final int STYLE_MASK = Font.BOLD | Font.ITALIC;
    private static final Font[][] UI_CACHE = new Font[STYLE_MASK + 1][MAX_SIZE + 1];
    private static final Font[] MONO_CACHE = new Font[MAX_SIZE + 1];
    private static volatile Font baseFont;
    private static volatile Font monoFont;

    private Fonts() {
    }

    /**
     * Installs the base UI font (any size; sizes are derived from it) and clears the cache.
     *
     * @param font the font to derive sizes from, or {@code null} to return to the logical family
     */
    public static synchronized void install(Font font) {
        baseFont = font;
        for (Font[] row : UI_CACHE) {
            Arrays.fill(row, null);
        }
    }

    /**
     * The base UI font.
     *
     * @return the installed font, or the logical {@code SansSerif} when none was installed
     */
    public static Font base() {
        Font f = baseFont;
        if (f == null) {
            synchronized (Fonts.class) {
                f = baseFont;
                if (f == null) {
                    f = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
                    baseFont = f;
                }
            }
        }
        return f;
    }

    /**
     * Family name of the base UI font.
     *
     * @return the family
     */
    public static String family() {
        return base().getFamily();
    }

    /**
     * A UI font of the given style and size (cached).
     *
     * @param style {@link Font#PLAIN}, {@link Font#BOLD}, {@link Font#ITALIC} or a combination
     * @param size the point size, clamped to {@code [MIN_SIZE, MAX_SIZE]}
     * @return the font
     */
    public static Font get(int style, int size) {
        int st = style & STYLE_MASK;
        int s = clamp(size);
        Font[] row = UI_CACHE[st];
        Font cached = row[s];
        if (cached == null) {
            cached = base().deriveFont(st, (float) s);
            row[s] = cached;
        }
        return cached;
    }

    /**
     * A plain UI font.
     *
     * @param size the point size
     * @return the font
     */
    public static Font regular(int size) {
        return get(Font.PLAIN, size);
    }

    /**
     * A bold UI font.
     *
     * @param size the point size
     * @return the font
     */
    public static Font bold(int size) {
        return get(Font.BOLD, size);
    }

    /**
     * A monospaced font for the debug overlay (cached, logical {@code Monospaced}).
     *
     * @param size the point size
     * @return the font
     */
    public static Font mono(int size) {
        int s = clamp(size);
        Font cached = MONO_CACHE[s];
        if (cached == null) {
            Font m = monoFont;
            if (m == null) {
                m = new Font(Font.MONOSPACED, Font.PLAIN, 12);
                monoFont = m;
            }
            cached = m.deriveFont((float) s);
            MONO_CACHE[s] = cached;
        }
        return cached;
    }

    /**
     * Tells whether the base UI font can display every character of a text (D25: accents in
     * Portuguese strings must render).
     *
     * @param text the text
     * @return {@code true} when {@link Font#canDisplayUpTo(String)} finds no gap
     */
    public static boolean canDisplay(String text) {
        Objects.requireNonNull(text, "text");
        return base().canDisplayUpTo(text) == -1;
    }

    private static int clamp(int size) {
        return size < MIN_SIZE ? MIN_SIZE : (size > MAX_SIZE ? MAX_SIZE : size);
    }
}
