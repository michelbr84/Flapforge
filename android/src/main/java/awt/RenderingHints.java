package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.RenderingHints}. The census only ever touches the class through
 * its static key/value constants passed to {@code Graphics2D.setRenderingHint}
 * (render/TextPainter.java:37-40 and render/ProceduralArt.java:301-309); nothing builds a
 * {@code RenderingHints} map object.
 *
 * <p>Exercised key/value pairs:
 * <ul>
 *   <li>{@code KEY_ANTIALIASING} + {@code VALUE_ANTIALIAS_ON}/{@code VALUE_ANTIALIAS_OFF} —
 *       mapped onto {@code Paint.ANTI_ALIAS_FLAG} (semantics 4).</li>
 *   <li>{@code KEY_TEXT_ANTIALIASING} + {@code VALUE_TEXT_ANTIALIAS_ON} — mapped onto
 *       {@code Paint.ANTI_ALIAS_FLAG} for text.</li>
 *   <li>{@code KEY_INTERPOLATION} + {@code VALUE_INTERPOLATION_BILINEAR}/
 *       {@code VALUE_INTERPOLATION_NEAREST_NEIGHBOR} — mapped onto
 *       {@code Paint.FILTER_BITMAP_FLAG} for {@code drawImage}.</li>
 *   <li>{@code KEY_FRACTIONALMETRICS} + {@code VALUE_FRACTIONALMETRICS_ON},
 *       {@code KEY_STROKE_CONTROL} + {@code VALUE_STROKE_PURE},
 *       {@code KEY_RENDERING} + {@code VALUE_RENDER_QUALITY}/{@code VALUE_RENDER_SPEED} —
 *       accepted and ignored (android graphics have no matching switches; the pieces they tune do
 *       not exist on this backend).</li>
 * </ul>
 *
 * <p>The constants are interned string objects compared by identity, mirroring how AWT hint values
 * are opaque keys; {@code Graphics2D} compares with {@code ==} exactly as the values are passed
 * back in.
 */
public final class RenderingHints {

    /** Hint key: shape antialiasing (honoured). */
    public static final Key KEY_ANTIALIASING = new Key(1);
    /** Hint key: text antialiasing (honoured). */
    public static final Key KEY_TEXT_ANTIALIASING = new Key(2);
    /** Hint key: fractional text metrics (accepted, ignored). */
    public static final Key KEY_FRACTIONALMETRICS = new Key(3);
    /** Hint key: stroke normalisation (accepted, ignored). */
    public static final Key KEY_STROKE_CONTROL = new Key(4);
    /** Hint key: image interpolation (honoured: bitmap filtering on/off for drawImage). */
    public static final Key KEY_INTERPOLATION = new Key(5);
    /** Hint key: overall rendering quality trade-off (accepted, ignored). */
    public static final Key KEY_RENDERING = new Key(6);

    /** Value for {@link #KEY_ANTIALIASING}: smooth shape edges (census). */
    public static final Object VALUE_ANTIALIAS_ON = "Antialias on";
    /** Value for {@link #KEY_ANTIALIASING}: crisp aliased edges (census). */
    public static final Object VALUE_ANTIALIAS_OFF = "Antialias off";
    /** Value for {@link #KEY_ANTIALIASING}: platform default. */
    public static final Object VALUE_ANTIALIAS_DEFAULT = "Antialias default";

    /** Value for {@link #KEY_TEXT_ANTIALIASING}: smooth glyph edges (census). */
    public static final Object VALUE_TEXT_ANTIALIAS_ON = "Text antialias on";
    /** Value for {@link #KEY_TEXT_ANTIALIASING}: crisp aliased glyphs. */
    public static final Object VALUE_TEXT_ANTIALIAS_OFF = "Text antialias off";
    /** Value for {@link #KEY_TEXT_ANTIALIASING}: platform default. */
    public static final Object VALUE_TEXT_ANTIALIAS_DEFAULT = "Text antialias default";

    /** Value for {@link #KEY_FRACTIONALMETRICS}: sub-pixel text advances (accepted, ignored). */
    public static final Object VALUE_FRACTIONALMETRICS_ON = "Fractional metrics on";
    /** Value for {@link #KEY_FRACTIONALMETRICS}: integer text advances (accepted, ignored). */
    public static final Object VALUE_FRACTIONALMETRICS_OFF = "Fractional metrics off";

    /** Value for {@link #KEY_STROKE_CONTROL}: pure (unrounded) strokes (accepted, ignored). */
    public static final Object VALUE_STROKE_PURE = "Stroke pure";
    /** Value for {@link #KEY_STROKE_CONTROL}: normalised strokes (accepted, ignored). */
    public static final Object VALUE_STROKE_NORMALIZED = "Stroke normalized";

    /** Value for {@link #KEY_INTERPOLATION}: bilinear image sampling (filtered bitmaps). */
    public static final Object VALUE_INTERPOLATION_BILINEAR = "Bilinear";
    /** Value for {@link #KEY_INTERPOLATION}: nearest-neighbour sampling (unfiltered bitmaps). */
    public static final Object VALUE_INTERPOLATION_NEAREST_NEIGHBOR = "Nearest neighbor";

    /** Value for {@link #KEY_RENDERING}: favour quality (accepted, ignored). */
    public static final Object VALUE_RENDER_QUALITY = "Render quality";
    /** Value for {@link #KEY_RENDERING}: favour speed (accepted, ignored). */
    public static final Object VALUE_RENDER_SPEED = "Render speed";

    /** Identity-keyed hint key, AWT parity of {@code java.awt.RenderingHints.Key}. */
    public static final class Key {

        private final int id;

        private Key(int id) {
            this.id = id;
        }

        /** @return the stable int identifier of this key */
        public int intKey() {
            return id;
        }
    }

    private RenderingHints() {
    }
}
