package awt;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import awt.geom.Ellipse2D;
import awt.geom.Line2D;
import awt.geom.Path2D;
import awt.geom.Rectangle2D;
import awt.geom.RoundRectangle2D;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.Graphics2D}: a state machine over an
 * {@code android.graphics.Canvas}. All shape geometry flows through the {@code awt.geom} shims as
 * double-precision segments and is transformed by the pure-double {@link AwtMatrix} before the
 * single float conversion that builds each {@code android.graphics.Path}; images and text are
 * drawn under {@code Canvas.concat(matrix)} so the transform applies in one step.
 *
 * <p>Census surface (every Graphics2D method the game calls; variable names normalised to
 * {@code g}): {@code setColor(Color)} 246 sites, {@code setFont(Font)} 103, {@code setStroke}
 * 75, {@code getStroke} 35, {@code fill(Shape)} 72, {@code draw(Shape)} 30,
 * {@code translate} 26 (double/int args; the shim keeps the double form — int calls widen),
 * {@code drawRoundRect} 19, {@code fillRect} 14, {@code scale} 13, {@code setClip(Shape)} 9,
 * {@code getClip} 9, {@code fillOval} 9, {@code clipRect} 9, {@code rotate} 8 (one- and
 * three-argument forms), {@code drawString} 8 (float args; int calls widen),
 * {@code setRenderingHint} 7, {@code setPaint} 6, {@code getFontMetrics} 6 (no-arg) + 1
 * ({@code getFontMetrics(Font)}, render/TextPainter.java:64), {@code getPaint} 3,
 * {@code drawLine} 6, {@code fillPolygon(int[],int[],int)} 5, {@code drawImage} 5
 * ({@code (img,x,y,null)}: render/Sprite.java:75, render/AssetManager.java:464;
 * {@code (img,x,y,w,h,null)}: render/Sprite.java:90, render/SpriteSheet.java:113;
 * source-region {@code (img,dx1,dy1,dx2,dy2,sx1,sy1,sx2,sy2,null)}: render/DarknessOverlay.java:128),
 * {@code dispose} 6, {@code drawPolyline} 3, {@code drawOval} 2, {@code drawArc(int,int,int,int,
 * int,int)} 2 (ui/component/CardGrid.java:560, ui/screens/ModifierChoiceOverlay.java:424),
 * {@code clip(Shape)} 1 (render/ProceduralArt.java:658). Nothing in the census uses
 * {@code setComposite/getTransform/setTransform/shear/fillArc/drawPolygon/clearRect/
 * getClipBounds/setBackground}, so none of those exist here.
 *
 * <p>Semantics decisions: the paint state is one slot ({@code setColor} and {@code setPaint}
 * write it, {@code getPaint} reads it — the only concrete census values are {@link Color} and
 * {@link GradientPaint}); clip shapes are captured in device space when set and intersected
 * canvas-side by successive {@code clipPath} calls, with {@code getClip} handing back an opaque
 * device-space shape that {@code setClip} accepts verbatim (every census pair wraps a single
 * change under one unchanged transform, so the roundtrip is exact); hints
 * {@code KEY_ANTIALIASING}/{@code KEY_TEXT_ANTIALIASING} map onto {@code ANTI_ALIAS_FLAG},
 * {@code KEY_INTERPOLATION} selects bitmap filtering for {@code drawImage}, and the remaining
 * census hints are accepted and ignored. No composite state exists (no {@code AlphaComposite}
 * census site). {@code dispose()} releases nothing (the canvas is GC-managed) — it exists because
 * the game's {@code finally} blocks call it.
 */
public class Graphics2D {

    private static final AwtMatrix IDENTITY = new AwtMatrix();

    private final Canvas canvas;
    private final AwtMatrix matrix = new AwtMatrix();

    // Paint state (one slot; census values are Color and GradientPaint only).
    private Object paintState = new Color(0xFF000000, true);

    private Font font = null;
    private Stroke stroke = new BasicStroke(1f);

    // Rendering hints (census keys only; defaults AWT-like).
    private boolean antialias = false;
    private boolean textAntialias = true;
    private boolean nearestNeighbourImages = false;

    // Clip state: device-space paths intersected at draw time (semantics 3).
    private android.graphics.Path[] clipPaths = new android.graphics.Path[0];

    // Reused android-side state (no per-call allocation for the common paths).
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final android.graphics.Matrix androidMatrix = new android.graphics.Matrix();
    private final RectF rectBuffer = new RectF();

    private Stroke dashCacheKey;
    private double dashCacheScale = Double.NaN;
    private DashPathEffect dashCacheEffect;

    /**
     * Creates a context over an android canvas. The Android host (P2) uses this to wrap the
     * {@code SurfaceView} canvas; {@link awt.image.BufferedImage#createGraphics()} uses it for
     * off-screen contexts.
     *
     * @param canvas the android canvas to draw onto
     */
    public Graphics2D(Canvas canvas) {
        this.canvas = canvas;
    }

    /**
     * Sets the paint state to a solid colour (census: the dominant state call).
     *
     * @param color the colour
     */
    public void setColor(Color color) {
        if (color == null) {
            throw new NullPointerException("Flapforge shim: setColor(null)");
        }
        paintState = color;
    }

    /**
     * Sets the paint state (census: {@link Color} and {@link GradientPaint} values, e.g.
     * render/ProceduralArt.java:659). The parameter is the shim {@code awt.Paint} marker
     * (android's {@code Paint} is a different type here).
     *
     * @param paint the paint
     */
    public void setPaint(awt.Paint paint) {
        if (paint == null) {
            throw new NullPointerException("Flapforge shim: setPaint(null)");
        }
        paintState = paint;
    }

    /**
     * The current paint state.
     *
     * @return the last colour or gradient installed
     */
    public awt.Paint getPaint() {
        return (awt.Paint) paintState;
    }

    /**
     * Installs the text font (census: fonts come from the game's font cache).
     *
     * @param font the font
     */
    public void setFont(Font font) {
        if (font == null) {
            throw new NullPointerException("Flapforge shim: setFont(null)");
        }
        this.font = font;
    }

    /**
     * Installs the stroke (census: {@link BasicStroke} only).
     *
     * @param stroke the stroke
     */
    public void setStroke(Stroke stroke) {
        if (stroke == null) {
            throw new NullPointerException("Flapforge shim: setStroke(null)");
        }
        this.stroke = stroke;
    }

    /** @return the current stroke */
    public Stroke getStroke() {
        return stroke;
    }

    /**
     * Concatenates a translation (census: camera/viewport/shape-space frames).
     *
     * @param tx the x offset
     * @param ty the y offset
     */
    public void translate(double tx, double ty) {
        matrix.translate(tx, ty);
    }

    /**
     * Concatenates a scale (census: viewport zoom, gear and bird shape spaces).
     *
     * @param sx the x factor
     * @param sy the y factor
     */
    public void scale(double sx, double sy) {
        matrix.scale(sx, sy);
    }

    /**
     * Concatenates a rotation about the origin, AWT convention (+x toward +y).
     *
     * @param theta the angle in radians
     */
    public void rotate(double theta) {
        matrix.rotate(theta);
    }

    /**
     * Concatenates a rotation about a pivot (census: render/ProceduralArt wing pivots).
     *
     * @param theta the angle in radians
     * @param px the pivot x
     * @param py the pivot y
     */
    public void rotate(double theta, double px, double py) {
        matrix.rotate(theta, px, py);
    }

    /**
     * Installs a rendering hint. Census keys: {@code KEY_ANTIALIASING},
     * {@code KEY_TEXT_ANTIALIASING} (honoured), {@code KEY_INTERPOLATION} (honoured for image
     * filtering), {@code KEY_FRACTIONALMETRICS}, {@code KEY_STROKE_CONTROL},
     * {@code KEY_RENDERING} (accepted, ignored).
     *
     * @param key the hint key
     * @param value the hint value
     */
    public void setRenderingHint(RenderingHints.Key key, Object value) {
        if (key == RenderingHints.KEY_ANTIALIASING) {
            antialias = value == RenderingHints.VALUE_ANTIALIAS_ON;
        } else if (key == RenderingHints.KEY_TEXT_ANTIALIASING) {
            textAntialias = value == RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
        } else if (key == RenderingHints.KEY_INTERPOLATION) {
            nearestNeighbourImages = value == RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
        }
        // KEY_FRACTIONALMETRICS / KEY_STROKE_CONTROL / KEY_RENDERING: accepted, ignored.
    }

    /**
     * The metrics of the current font.
     *
     * @return the metrics
     */
    public FontMetrics getFontMetrics() {
        return getFontMetrics(currentFont());
    }

    /**
     * The metrics of a font (census: render/TextPainter.java:64).
     *
     * @param font the font to measure with
     * @return the metrics
     */
    public FontMetrics getFontMetrics(Font font) {
        return new FontMetrics(font);
    }

    /**
     * Replaces the clip with {@code shape} (device space is captured now, semantics 3).
     *
     * @param shape the new clip, or {@code null} to remove clipping
     */
    public void setClip(Shape shape) {
        if (shape == null) {
            clipPaths = new android.graphics.Path[0];
            return;
        }
        if (shape instanceof DeviceClip deviceClip) {
            android.graphics.Path[] copies = new android.graphics.Path[deviceClip.paths.length];
            for (int i = 0; i < deviceClip.paths.length; i++) {
                copies[i] = new android.graphics.Path(deviceClip.paths[i]);
            }
            clipPaths = copies;
            return;
        }
        clipPaths = new android.graphics.Path[] {devicePath(shape, matrix)};
    }

    /**
     * The current clip, as an opaque shape that {@link #setClip(Shape)} accepts back (the census
     * only ever stores it and restores it under an unchanged transform).
     *
     * @return the clip shape, or {@code null} when nothing is clipped
     */
    public Shape getClip() {
        if (clipPaths.length == 0) {
            return null;
        }
        return new DeviceClip(clipPaths);
    }

    /**
     * Intersects the clip with {@code shape} (census: render/ProceduralArt.java:658).
     *
     * @param shape the shape to add to the clip
     */
    public void clip(Shape shape) {
        if (shape == null) {
            return;
        }
        if (shape instanceof DeviceClip deviceClip) {
            android.graphics.Path[] merged =
                    new android.graphics.Path[clipPaths.length + deviceClip.paths.length];
            System.arraycopy(clipPaths, 0, merged, 0, clipPaths.length);
            System.arraycopy(deviceClip.paths, 0, merged, clipPaths.length,
                    deviceClip.paths.length);
            clipPaths = merged;
            return;
        }
        android.graphics.Path[] merged = new android.graphics.Path[clipPaths.length + 1];
        System.arraycopy(clipPaths, 0, merged, 0, clipPaths.length);
        merged[clipPaths.length] = devicePath(shape, matrix);
        clipPaths = merged;
    }

    /**
     * Intersects the clip with an integer rectangle (census: the viewport logical clip,
     * render/Viewport.java:214).
     *
     * @param x the x
     * @param y the y
     * @param w the width
     * @param h the height
     */
    public void clipRect(int x, int y, int w, int h) {
        clip(new Rectangle2D.Double(x, y, w, h));
    }

    /**
     * Fills a shape (census: 72 sites).
     *
     * @param shape the shape to fill
     */
    public void fill(Shape shape) {
        Path2D.Double sink = new Path2D.Double();
        shape.appendTo(sink);
        int winding = shape instanceof Path2D path
                ? path.getWindingRule()
                : Path2D.WIND_NON_ZERO;
        android.graphics.Path androidPath = sink.toAndroidPath(matrix, winding);
        configureFill(fillPaint);
        withClip(() -> canvas.drawPath(androidPath, fillPaint));
    }

    /**
     * Strokes a shape outline (census: 30 sites).
     *
     * @param shape the shape to draw
     */
    public void draw(Shape shape) {
        Path2D.Double sink = new Path2D.Double();
        shape.appendTo(sink);
        android.graphics.Path androidPath = sink.toAndroidPath(matrix, Path2D.WIND_NON_ZERO);
        configureStroke(strokePaint);
        withClip(() -> canvas.drawPath(androidPath, strokePaint));
    }

    /**
     * Fills an integer rectangle (census: 14 sites).
     */
    public void fillRect(int x, int y, int w, int h) {
        fill(new Rectangle2D.Double(x, y, w, h));
    }

    /**
     * Fills a rounded rectangle (census: 32 sites).
     */
    public void fillRoundRect(int x, int y, int w, int h, int arcWidth, int arcHeight) {
        fill(new RoundRectangle2D.Double(x, y, w, h, arcWidth, arcHeight));
    }

    /**
     * Strokes a rounded rectangle outline (census: 19 sites).
     */
    public void drawRoundRect(int x, int y, int w, int h, int arcWidth, int arcHeight) {
        draw(new RoundRectangle2D.Double(x, y, w, h, arcWidth, arcHeight));
    }

    /**
     * Fills an ellipse (census: 9 sites).
     */
    public void fillOval(int x, int y, int w, int h) {
        fill(new Ellipse2D.Double(x, y, w, h));
    }

    /**
     * Strokes an ellipse outline (census: 2 sites).
     */
    public void drawOval(int x, int y, int w, int h) {
        draw(new Ellipse2D.Double(x, y, w, h));
    }

    /**
     * Strokes an AWT-convention arc (0 degrees at 3 o'clock, positive sweep toward 12 o'clock;
     * census: ui/component/CardGrid.java:560, ui/screens/ModifierChoiceOverlay.java:424).
     */
    public void drawArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        awt.geom.Arc2D.Double arc = new awt.geom.Arc2D.Double(awt.geom.Arc2D.OPEN);
        arc.setArc(x, y, w, h, startAngle, arcAngle, awt.geom.Arc2D.OPEN);
        draw(arc);
    }

    /**
     * Draws a straight line (census: 6 sites).
     */
    public void drawLine(int x1, int y1, int x2, int y2) {
        draw(new Line2D.Double(x1, y1, x2, y2));
    }

    /**
     * Fills a closed polygon from coordinate arrays (census: 5 sites, all simple polygons —
     * HUD shield/glyph/flame, list and bird-selection arrows). AWT parity: {@code fillPolygon}
     * goes through {@code java.awt.Polygon}, whose path iterator uses the EVEN-ODD rule, so a
     * self-intersecting outline leaves its doubly-wound regions unfilled here too.
     */
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        appendPolygon(path, xPoints, yPoints, nPoints);
        path.closePath();
        fill(path);
    }

    /**
     * Strokes an open polyline from coordinate arrays (census: 3 sites,
     * render/LightningRenderer.java:158, :161, :172).
     */
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        Path2D.Double path = new Path2D.Double();
        appendPolygon(path, xPoints, yPoints, nPoints);
        draw(path);
    }

    /**
     * Draws text with AWT baseline semantics: y IS the baseline (census: 8 sites, all via
     * render/TextPainter).
     *
     * @param text the text
     * @param x the baseline start x
     * @param y the baseline y
     */
    public void drawString(String text, float x, float y) {
        Font use = currentFont();
        // Text is drawn under canvas.concat(matrix), so its paint must be built in USER space
        // (a gradient's end points untransformed); shapes are pre-transformed and use device
        // space. No census site draws text under a GradientPaint, but the combination is two
        // census calls and must not double-transform the ramp.
        applyPaintState(textPaint, false);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTypeface(use.typeface());
        textPaint.setTextSize((float) use.size());
        textPaint.setFlags((textPaint.getFlags() & ~Paint.ANTI_ALIAS_FLAG)
                | (textAntialias ? Paint.ANTI_ALIAS_FLAG : 0));
        androidMatrix.setValues(new float[] {
                (float) matrix.m00, (float) matrix.m01, (float) matrix.m02,
                (float) matrix.m10, (float) matrix.m11, (float) matrix.m12,
                0f, 0f, 1f});
        withClip(() -> {
            canvas.save();
            canvas.concat(androidMatrix);
            canvas.drawText(text, x, y, textPaint);
            canvas.restore();
        });
    }

    /**
     * Draws an image at its natural size (census: render/Sprite.java:75,
     * render/AssetManager.java:464). The observer is always {@code null} in the census.
     *
     * @param img the image
     * @param x the destination x
     * @param y the destination y
     * @param observer ignored (always {@code null} in the census)
     */
    public void drawImage(awt.image.BufferedImage img, int x, int y, Object observer) {
        if (img == null) {
            return;
        }
        drawImage(img, x, y, img.getWidth(), img.getHeight(), observer);
    }

    /**
     * Draws an image scaled to the destination rectangle (census: render/Sprite.java:90,
     * render/SpriteSheet.java:113 — sprite frames are subimage views, whose shared-bitmap
     * offset {@link #drawRegion} applies).
     *
     * @param img the image
     * @param x the destination x
     * @param y the destination y
     * @param w the destination width
     * @param h the destination height
     * @param observer ignored (always {@code null} in the census)
     */
    public void drawImage(awt.image.BufferedImage img, int x, int y, int w, int h,
            Object observer) {
        if (img == null) {
            return;
        }
        rectBuffer.set(x, y, x + w, y + h);
        drawRegion(img, new android.graphics.Rect(0, 0, img.getWidth(), img.getHeight()));
    }

    /**
     * Draws a source region of an image onto a destination rectangle (census:
     * render/DarknessOverlay.java:128, which always passes {@code dx1 < dx2}, {@code dy1 < dy2},
     * {@code sx1 < sx2}, {@code sy1 < sy2}). AWT flips the image when a pair is reversed; no
     * census site does that, so a reversed pair throws instead of being silently normalised.
     * Source coordinates are in the image's own space (a subimage view's origin is its
     * sub-rectangle corner, as in AWT); a source region reaching outside the image is clipped
     * to it with the destination shrunk proportionally, which is what AWT renders too.
     */
    public void drawImage(awt.image.BufferedImage img, int dx1, int dy1, int dx2, int dy2,
            int sx1, int sy1, int sx2, int sy2, Object observer) {
        if (img == null) {
            return;
        }
        if (dx1 > dx2 || dy1 > dy2 || sx1 > sx2 || sy1 > sy2) {
            throw new UnsupportedOperationException(
                    "Flapforge shim: Graphics2D.drawImage with a flipped rectangle is not part "
                            + "of the census surface");
        }
        rectBuffer.set(dx1, dy1, dx2, dy2);
        drawRegion(img, new android.graphics.Rect(sx1, sy1, sx2, sy2));
    }

    /**
     * Releases the context (census: {@code finally} blocks). Nothing to release on android.
     */
    public void dispose() {
        // No native resources are held; android reclaims the canvas with its bitmap.
    }

    /**
     * Blits {@code src} (in the image's own coordinates) onto {@link #rectBuffer} (user space).
     * A subimage view shares its parent's bitmap, so the source rectangle is shifted by the
     * view's offset before the bitmap is sampled (semantics 10).
     */
    private void drawRegion(awt.image.BufferedImage img, android.graphics.Rect src) {
        if (src.isEmpty() || rectBuffer.isEmpty()) {
            return;
        }
        src.offset(img.offsetX(), img.offsetY());
        imagePaint.setFlags((imagePaint.getFlags() & ~Paint.FILTER_BITMAP_FLAG)
                | (nearestNeighbourImages ? 0 : Paint.FILTER_BITMAP_FLAG));
        withClip(() -> {
            canvas.save();
            canvas.concat(androidMatrix());
            canvas.drawBitmap(img.bitmap(), src, rectBuffer, imagePaint);
            canvas.restore();
        });
    }

    private static void appendPolygon(Path2D.Double path, int[] xPoints, int[] yPoints,
            int nPoints) {
        if (nPoints <= 0) {
            return;
        }
        path.moveTo(xPoints[0], yPoints[0]);
        for (int i = 1; i < nPoints; i++) {
            path.lineTo(xPoints[i], yPoints[i]);
        }
    }

    private android.graphics.Path devicePath(Shape shape, AwtMatrix transform) {
        Path2D.Double sink = new Path2D.Double();
        shape.appendTo(sink);
        return sink.toAndroidPath(transform, Path2D.WIND_NON_ZERO);
    }

    private void withClip(Runnable draw) {
        if (clipPaths.length == 0) {
            draw.run();
            return;
        }
        canvas.save();
        for (android.graphics.Path clip : clipPaths) {
            canvas.clipPath(clip);
        }
        draw.run();
        canvas.restore();
    }

    private void applyPaintState(Paint target) {
        applyPaintState(target, true);
    }

    /**
     * Installs the paint state on an android paint. {@code deviceSpace} selects where a gradient
     * ramp lives: device space for the pre-transformed shape paths, user space for text drawn
     * under {@code canvas.concat(matrix)}.
     */
    private void applyPaintState(Paint target, boolean deviceSpace) {
        if (paintState instanceof GradientPaint gradient) {
            target.setShader(gradientShader(gradient, deviceSpace));
        } else {
            target.setShader(null);
            target.setColor(((Color) paintState).getRGB());
        }
    }

    private Shader gradientShader(GradientPaint gradient, boolean deviceSpace) {
        double[] a = {gradient.x1, gradient.y1};
        double[] b = {gradient.x2, gradient.y2};
        if (deviceSpace) {
            matrix.apply(a);
            matrix.apply(b);
        }
        if (a[0] == b[0] && a[1] == b[1]) {
            // Degenerate AWT gradient: the two points coincide, so the ramp is uniform colour 1.
            return new LinearGradient((float) a[0], (float) a[1],
                    (float) a[0] + 1f, (float) a[1],
                    gradient.color1.getRGB(), gradient.color1.getRGB(), Shader.TileMode.CLAMP);
        }
        return new LinearGradient((float) a[0], (float) a[1],
                (float) b[0], (float) b[1],
                gradient.color1.getRGB(), gradient.color2.getRGB(), Shader.TileMode.CLAMP);
    }

    private void configureFill(Paint paint) {
        applyPaintState(paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setFlags((paint.getFlags() & ~Paint.ANTI_ALIAS_FLAG)
                | (antialias ? Paint.ANTI_ALIAS_FLAG : 0));
    }

    private void configureStroke(Paint paint) {
        applyPaintState(paint);
        BasicStroke basic = stroke instanceof BasicStroke b ? b : new BasicStroke(1f);
        double scale = matrix.averageScale();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((float) Math.max(0f, basic.width * scale));
        paint.setStrokeCap(switch (basic.cap) {
            case BasicStroke.CAP_ROUND -> Paint.Cap.ROUND;
            case BasicStroke.CAP_SQUARE -> Paint.Cap.SQUARE;
            default -> Paint.Cap.BUTT;
        });
        paint.setStrokeJoin(switch (basic.join) {
            case BasicStroke.JOIN_ROUND -> Paint.Join.ROUND;
            case BasicStroke.JOIN_BEVEL -> Paint.Join.BEVEL;
            default -> Paint.Join.MITER;
        });
        paint.setStrokeMiter(basic.miterLimit);
        paint.setPathEffect(dashEffect(basic, scale));
        paint.setFlags((paint.getFlags() & ~Paint.ANTI_ALIAS_FLAG)
                | (antialias ? Paint.ANTI_ALIAS_FLAG : 0));
    }

    private DashPathEffect dashEffect(BasicStroke basic, double scale) {
        if (basic.dash == null) {
            return null;
        }
        if (basic == dashCacheKey && scale == dashCacheScale) {
            return dashCacheEffect;
        }
        float[] intervals = new float[basic.dash.length];
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = (float) (basic.dash[i] * scale);
        }
        dashCacheKey = basic;
        dashCacheScale = scale;
        dashCacheEffect = new DashPathEffect(intervals, (float) (basic.dashPhase * scale));
        return dashCacheEffect;
    }

    private Font currentFont() {
        if (font == null) {
            font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return font;
    }

    private android.graphics.Matrix androidMatrix() {
        androidMatrix.setValues(new float[] {
                (float) matrix.m00, (float) matrix.m01, (float) matrix.m02,
                (float) matrix.m10, (float) matrix.m11, (float) matrix.m12,
                0f, 0f, 1f});
        return androidMatrix;
    }

    /** Opaque device-space clip shape handed back by {@link #getClip()}. */
    private record DeviceClip(android.graphics.Path[] paths) implements Shape {

        @Override
        public Rectangle2D getBounds2D() {
            RectF bounds = new RectF();
            paths[0].computeBounds(bounds, true);
            for (int i = 1; i < paths.length; i++) {
                RectF more = new RectF();
                paths[i].computeBounds(more, true);
                bounds.union(more);
            }
            return new Rectangle2D.Double(bounds.left, bounds.top,
                    bounds.right - bounds.left, bounds.bottom - bounds.top);
        }

        @Override
        public void appendTo(Path2D.Double sink) {
            throw new UnsupportedOperationException(
                    "Flapforge shim: DeviceClip.appendTo is not part of the census surface");
        }
    }
}
