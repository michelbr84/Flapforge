package awt;

import android.graphics.Paint;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.FontMetrics}: measurements taken through an
 * {@code android.graphics.Paint} configured with the font's typeface and size
 * ({@code measureText} / {@code getFontMetrics}, semantics 7). AWT ascent/descent are positive
 * distances from the baseline; android's {@code Paint.FontMetrics.ascent} is negative, so the
 * shim negates it.
 *
 * <p>Census surface: {@code stringWidth(String)} (render/TextPainter.java:52, :64;
 * app/BootSequence.java:95, :97, :100), {@code getAscent()} and {@code getDescent()}
 * (render/TextPainter.java:76, the vertical-centring baseline). {@code getHeight()} is part of
 * the shim contract (semantics 7: stringWidth/getHeight/getAscent/getDescent) and follows the
 * AWT definition {@code leading + ascent + descent}. No other FontMetrics method is exercised.
 *
 * <p>Allocation profile: a {@link Font} is immutable, so its metrics are constant — the
 * instance is created once per font (cached by {@link Font#metrics()}), the vertical metrics
 * are read once here, and {@code stringWidth} measures through the font's shared, never-mutated
 * paint. The object is therefore safe to hand to every thread that shares the font.
 */
public class FontMetrics {

    private final Paint paint;
    private final int leading;
    private final int ascent;
    private final int descent;

    /** Package-visible: created once per font by {@link Font#metrics()}. */
    FontMetrics(Font font) {
        paint = font.measurePaint();
        Paint.FontMetrics fm = paint.getFontMetrics();
        leading = Math.round(fm.leading);
        ascent = Math.round(-fm.ascent);
        descent = Math.round(fm.descent);
    }

    /**
     * The total advance width of a text (AWT parity: rounded to a whole pixel).
     *
     * @param text the text to measure
     * @return the advance width in pixels
     */
    public int stringWidth(String text) {
        return Math.round(paint.measureText(text));
    }

    /**
     * The standard height of a line: {@code getLeading() + getAscent() + getDescent()} — the AWT
     * definition, summed from the individually rounded parts so that
     * {@code getHeight() >= getAscent() + getDescent()} always holds (rounding the float sum
     * instead can come out one pixel short of the rounded parts).
     *
     * @return the line height in pixels
     */
    public int getHeight() {
        return leading + ascent + descent;
    }

    /**
     * The ascent: the distance from the baseline up to the top of most text (AWT sign).
     *
     * @return the ascent in pixels, positive
     */
    public int getAscent() {
        return ascent;
    }

    /**
     * The descent: the distance from the baseline down to the bottom of most text.
     *
     * @return the descent in pixels, positive
     */
    public int getDescent() {
        return descent;
    }
}
