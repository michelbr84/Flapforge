package io.github.michelbr84.flapforge.render;

import java.awt.Font;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Font provider with a per-style-and-size cache (D17, D25).
 *
 * <p>The base family is the logical {@code SansSerif} until M8 installs the bundled OFL font
 * through {@link #install(Font)}; every derived size comes from {@link Font#deriveFont}, so the
 * switch is invisible to callers. The debug overlay uses the logical {@code Monospaced} family.
 * Nothing here touches the toolkit at class-initialisation time (E10): the first font is created
 * on the first request. Lookups index {@link AtomicReferenceArray}s, so per-frame calls allocate
 * nothing and a font derived on one thread is published safely to another: the boot warm-up runs
 * on {@code flapforge-boot} while the loop thread already draws the splash, and
 * {@link java.awt.Font} has no final fields, so a plain array element could hand a reader a
 * half-initialised object on a weak memory model. A benign race may still derive the same
 * immutable font twice, which costs nothing.
 */
public final class Fonts {

    /** Size below which no font is derived (requests are clamped). */
    public static final int MIN_SIZE = 6;
    /** Size above which no font is derived (requests are clamped). */
    public static final int MAX_SIZE = 200;
    /** Smallest accepted text scale. */
    public static final double MIN_TEXT_SCALE = 0.5;
    /** Largest accepted text scale. */
    public static final double MAX_TEXT_SCALE = 3.0;

    private static final int STYLE_MASK = Font.BOLD | Font.ITALIC;
    private static final int SIZES = MAX_SIZE + 1;
    /** One flat slot per (style, size) pair: {@code style * SIZES + size}. */
    private static final AtomicReferenceArray<Font> UI_CACHE =
            new AtomicReferenceArray<>((STYLE_MASK + 1) * SIZES);
    private static final AtomicReferenceArray<Font> MONO_CACHE = new AtomicReferenceArray<>(SIZES);
    private static volatile Font baseFont;
    private static volatile Font monoFont;
    private static volatile double textScale = 1.0;

    private Fonts() {
    }

    /** Empties every slot of a cache. */
    private static void clear(AtomicReferenceArray<Font> cache) {
        for (int i = 0; i < cache.length(); i++) {
            cache.setRelease(i, null);
        }
    }

    /**
     * Installs the base UI font (any size; sizes are derived from it) and clears the cache.
     *
     * @param font the font to derive sizes from, or {@code null} to return to the logical family
     */
    public static synchronized void install(Font font) {
        baseFont = font;
        clear(UI_CACHE);
    }

    /**
     * Scales every requested point size ({@code settings.textScale}, D25).
     *
     * <p>A caller keeps asking for the size its layout was designed around; this factor is what
     * turns that into the size the player asked for. Changing it clears the caches, so the next
     * frame draws at the new size. Values outside
     * {@code [MIN_TEXT_SCALE, MAX_TEXT_SCALE]} are clamped and a non-finite value is ignored.
     *
     * @param scale the factor ({@code 1.0} is the designed size)
     */
    public static synchronized void setTextScale(double scale) {
        if (!Double.isFinite(scale)) {
            return;
        }
        double clamped = Math.max(MIN_TEXT_SCALE, Math.min(MAX_TEXT_SCALE, scale));
        if (clamped == textScale) {
            return;
        }
        textScale = clamped;
        clear(UI_CACHE);
        clear(MONO_CACHE);
    }

    /**
     * The factor every requested point size is multiplied by.
     *
     * @return the text scale
     */
    public static double textScale() {
        return textScale;
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
        int slot = st * SIZES + s;
        Font cached = UI_CACHE.getAcquire(slot);
        if (cached == null) {
            cached = base().deriveFont(st, (float) scaled(s));
            UI_CACHE.setRelease(slot, cached);
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
        Font cached = MONO_CACHE.getAcquire(s);
        if (cached == null) {
            Font m = monoFont;
            if (m == null) {
                m = new Font(Font.MONOSPACED, Font.PLAIN, 12);
                monoFont = m;
            }
            cached = m.deriveFont((float) scaled(s));
            MONO_CACHE.setRelease(s, cached);
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

    /** Applies {@link #textScale()} to a requested size and clamps the result. */
    private static int scaled(int size) {
        return clamp((int) Math.round(size * textScale));
    }

    private static int clamp(int size) {
        return size < MIN_SIZE ? MIN_SIZE : (size > MAX_SIZE ? MAX_SIZE : size);
    }
}
