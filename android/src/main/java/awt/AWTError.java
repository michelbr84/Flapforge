package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.AWTError}. The census uses it only as a caught exception type:
 * app/GameApplication.java:566 ({@code catch (HeadlessException | AWTError)}), audio/AudioBackend.java:176
 * and :188 ({@code catch (... | AWTError)}). It is never thrown by game code, so the shim only
 * needs the class to exist as an {@code Error} subtype with the standard message constructor.
 */
public class AWTError extends Error {

    private static final long serialVersionUID = 1L;

    /** Matches the {@code java.awt.AWTError(String)} constructor. */
    public AWTError(String message) {
        super(message);
    }
}
