package awt;

import android.graphics.Typeface;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.Font}, wrapping an immutable
 * {@code android.graphics.Typeface} plus a pixel size (sizes map 1:1 to pixels, semantics 7);
 * {@code drawString} renders through {@code Canvas.drawText} with the AWT baseline convention
 * (y IS the baseline, which android shares).
 *
 * <p>Census surface: constants {@code PLAIN}/{@code BOLD}/{@code ITALIC}
 * (render/Fonts.java:32 style mask), {@code SANS_SERIF}/{@code MONOSPACED} (Fonts.java:105, :173
 * logical families), {@code TRUETYPE_FONT} (render/AssetManager.java:418); constructor
 * {@code (String name, int style, int size)} (Fonts.java:105, :173); static
 * {@code createFont(int, InputStream)} (AssetManager.java:418, the bundled OFL font);
 * {@code deriveFont(float)} (Fonts.java:176) and {@code deriveFont(int style, float size)}
 * (Fonts.java:135) — the per-style-and-size cache derives every face from one base;
 * {@code getFamily()} (Fonts.java:119) and {@code canDisplayUpTo(String)} (Fonts.java:191, the
 * Portuguese-accents check). {@code getSize}/{@code getStyle}/{@code getFontName} and the
 * {@code (Map)}/attribute constructors are not exercised and are absent.
 *
 * <p>Family mapping: {@code SANS_SERIF} -> {@code Typeface.SANS_SERIF},
 * {@code MONOSPACED} -> {@code Typeface.MONOSPACE}, any other name ->
 * {@code Typeface.create(name, ...)}; bold/italic are synthetic-derived on the base typeface,
 * which is what the census needs (the bundled Nunito variable font loads at its Regular default
 * and bold stays a derived style, per the AssetManager contract).
 */
public class Font {

    /** AWT parity: plain style. */
    public static final int PLAIN = 0;
    /** AWT parity: bold style (census). */
    public static final int BOLD = 1;
    /** AWT parity: italic style (census). */
    public static final int ITALIC = 2;

    /** AWT parity: the logical sans-serif family name (census). */
    public static final String SANS_SERIF = "SansSerif";
    /** AWT parity: the logical monospaced family name (census). */
    public static final String MONOSPACED = "Monospaced";
    /** AWT parity: {@code createFont} format selector for a TrueType stream (census). */
    public static final int TRUETYPE_FONT = 0;

    private static final int STYLE_MASK = BOLD | ITALIC;

    private final Typeface typeface;
    private final int style;
    private final float size;
    private final String family;

    private android.graphics.Paint coveragePaint;

    /**
     * Creates a font from a family name, style and pixel size (census form).
     *
     * @param name the family name ({@link #SANS_SERIF}, {@link #MONOSPACED} or a system name)
     * @param style {@link #PLAIN}, {@link #BOLD}, {@link #ITALIC} or a combination
     * @param size the size in pixels
     */
    public Font(String name, int style, int size) {
        this(resolveFamilyTypeface(Objects.requireNonNull(name, "name"),
                style & STYLE_MASK), style & STYLE_MASK, size, name);
    }

    private Font(Typeface typeface, int style, float size, String family) {
        this.typeface = Objects.requireNonNull(typeface, "typeface");
        this.style = style;
        this.size = size;
        this.family = family;
    }

    /**
     * Loads a TrueType font from a stream (census: the bundled OFL font via
     * render/AssetManager.java:418). The stream is copied to a temp file under the
     * {@link Shims} cache dir so {@code Typeface.createFromFile} can parse it, and the temp
     * file is removed afterwards.
     *
     * @param fontFormat the format; only {@link #TRUETYPE_FONT} is part of the census surface
     * @param fontStream the stream of the {@code .ttf} file
     * @return the loaded font at size 1 (derive sizes from it, as the game does)
     * @throws FontFormatException when the stream does not carry a loadable TrueType face
     */
    public static Font createFont(int fontFormat, InputStream fontStream)
            throws FontFormatException {
        if (fontFormat != TRUETYPE_FONT) {
            throw new UnsupportedOperationException(
                    "Flapforge shim: Font.createFont format " + fontFormat
                            + " is not part of the census surface");
        }
        Objects.requireNonNull(fontStream, "fontStream");
        // Resolved before the try: a missing Shims.init(context) is a host-bootstrap bug and
        // must surface as its IllegalStateException, not be folded into the FontFormatException
        // the game catches and falls back from (AssetManager.loadFont).
        Path cacheDir = Shims.cacheDir().toPath();
        Path temp = null;
        try {
            temp = Files.createTempFile(cacheDir, "flapforge-font-", ".ttf");
            try (InputStream in = fontStream;
                    OutputStream out = new BufferedOutputStream(new FileOutputStream(temp.toFile()))) {
                in.transferTo(out);
            }
            if (!looksLikeSfnt(temp)) {
                // AWT parity: FontFormatException for data that is not a TrueType/OpenType face.
                // Checked here because android's Typeface may fall back to a default face
                // instead of failing (Robolectric's native runtime does), which would hide a
                // corrupt bundled font behind the wrong glyphs.
                throw new FontFormatException("Flapforge shim: stream is not a TrueType font");
            }
            Typeface typeface = Typeface.createFromFile(temp.toFile());
            if (typeface == null) {
                throw new FontFormatException("Flapforge shim: unreadable font stream");
            }
            // android.graphics.Typeface exposes no family-name accessor, so a file-loaded face
            // carries a stable shim label; the census never reads it (Fonts.family() has no
            // callers outside its own definition).
            return new Font(typeface, PLAIN, 1f, "truetype");
        } catch (FontFormatException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new FontFormatException("Flapforge shim: font stream could not be loaded: "
                    + e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // A leftover cache file is harmless; the next createFont makes a new one.
                }
            }
        }
    }

    /**
     * Clones this font at a new size, same family and style (census: the font cache's
     * size dimension).
     *
     * @param size the new size in pixels
     * @return the derived font
     */
    public Font deriveFont(float size) {
        return new Font(typeface, style, size, family);
    }

    /**
     * Clones this font with a new style and size (census: Fonts.java:135 style x size cache).
     *
     * @param style the new style
     * @param size the new size in pixels
     * @return the derived font
     */
    public Font deriveFont(int style, float size) {
        int newStyle = style & STYLE_MASK;
        Typeface derived = newStyle == this.style
                ? typeface
                : Typeface.create(typeface, typefaceStyle(newStyle));
        return new Font(derived, newStyle, size, family);
    }

    /**
     * The family name the font was created from (census: Fonts.java:119).
     *
     * @return the family
     */
    public String getFamily() {
        return family;
    }

    /**
     * AWT semantics: the index of the first character the font cannot display, or {@code -1}
     * when the whole text is covered (census: the accents check, Fonts.java:191).
     *
     * @param text the text to check
     * @return the first undisplayable index, or {@code -1}
     */
    public int canDisplayUpTo(String text) {
        Objects.requireNonNull(text, "text");
        android.graphics.Paint paint = coveragePaint();
        for (int i = 0; i < text.length(); i++) {
            if (!paint.hasGlyph(String.valueOf(text.charAt(i)))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether the file starts with an sfnt tag: {@code 0x00010000} or {@code 'true'} (TrueType),
     * {@code 'OTTO'} (CFF OpenType) or {@code 'ttcf'} (collection).
     */
    private static boolean looksLikeSfnt(Path file) throws IOException {
        byte[] head;
        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(4);
        }
        if (head.length < 4) {
            return false;
        }
        int tag = ((head[0] & 0xFF) << 24) | ((head[1] & 0xFF) << 16)
                | ((head[2] & 0xFF) << 8) | (head[3] & 0xFF);
        return tag == 0x00010000 || tag == 0x74727565 /* true */
                || tag == 0x4F54544F /* OTTO */ || tag == 0x74746366 /* ttcf */;
    }

    /** Package-visible: the android typeface Graphics2D draws with. */
    Typeface typeface() {
        return typeface;
    }

    /** Package-visible: the pixel size Graphics2D draws with. */
    float size() {
        return size;
    }

    private synchronized android.graphics.Paint coveragePaint() {
        if (coveragePaint == null) {
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setTypeface(typeface);
            paint.setTextSize(Math.max(1f, size));
            coveragePaint = paint;
        }
        return coveragePaint;
    }

    /** Package-visible: the AWT style bits ({@code PLAIN}/{@code BOLD}/{@code ITALIC} mask). */
    int style() {
        return style;
    }

    /**
     * Maps the AWT style mask onto the android {@code Typeface} style constant explicitly (the
     * numeric values coincide today, but the mapping is the contract, not the coincidence).
     */
    private static int typefaceStyle(int awtStyle) {
        boolean bold = (awtStyle & BOLD) != 0;
        boolean italic = (awtStyle & ITALIC) != 0;
        if (bold && italic) {
            return Typeface.BOLD_ITALIC;
        }
        if (bold) {
            return Typeface.BOLD;
        }
        if (italic) {
            return Typeface.ITALIC;
        }
        return Typeface.NORMAL;
    }

    private static Typeface resolveFamilyTypeface(String name, int style) {
        Typeface base;
        if (SANS_SERIF.equals(name)) {
            base = Typeface.SANS_SERIF;
        } else if (MONOSPACED.equals(name)) {
            base = Typeface.MONOSPACE;
        } else {
            base = Typeface.create(name, Typeface.NORMAL);
        }
        int typefaceStyle = typefaceStyle(style);
        return typefaceStyle == Typeface.NORMAL ? base : Typeface.create(base, typefaceStyle);
    }

    @Override
    public String toString() {
        return "Font[" + family + ", style=" + style + ", size=" + size + "]";
    }
}
