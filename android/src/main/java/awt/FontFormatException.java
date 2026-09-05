package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.FontFormatException}. Census: render/AssetManager.java:419 catches
 * it around {@code Font.createFont(Font.TRUETYPE_FONT, in)}, and {@link Font#createFont} throws it
 * when the stream does not carry a loadable TrueType face. No other surface is needed.
 */
public class FontFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Matches the {@code java.awt.FontFormatException(String)} constructor. */
    public FontFormatException(String message) {
        super(message);
    }
}
