package awt.geom;

import awt.Shape;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.geom.Path2D}: a double-precision, multi-subpath segment
 * storage (MOVETO / LINETO / CUBICTO / CLOSE) that {@link awt.Graphics2D} turns into an
 * {@code android.graphics.Path} at draw time. Geometry is transformed in double space by
 * {@link Double#toAndroidPath(awt.AwtMatrix, int)} right before the android path is built — the
 * float conversion happens exactly once, at the very end (no float drift through the transform
 * chain). Cubic segments come only from {@link #appendArc} (an affine transform maps Bézier
 * control points exactly, so arcs stay exact under any Graphics2D transform).
 *
 * <p>Census surface (32 usage sites, all {@link Double}): the no-arg constructor
 * (render/ProceduralArt.java:917, render/GearRenderer.java:109, :151,
 * render/BackgroundRenderer.java:392, :462, :486, :556, :601) plus {@code moveTo(x, y)} /
 * {@code lineTo(x, y)} / {@code closePath()} (7/20/7 call sites across those files and
 * BackgroundRenderer's block/brace helpers) and {@code append(Shape, boolean)} —
 * render/BackgroundRenderer.java:561 ({@code append(new Ellipse2D.Double(...), false)}) and :564
 * ({@code append(new Rectangle2D.Double(...), false)}), both with {@code connect == false}.
 * {@code quadTo}/{@code curveTo} are never exercised and are absent. The game only builds paths
 * with the default winding rule (no {@code WIND_EVEN_ODD} site in the census); the
 * {@code (windingRule)} constructor, the two constants and {@link Double#getWindingRule()} exist
 * for the fill-rule contract (the shim maps {@code WIND_EVEN_ODD} paths onto
 * {@code Path.FillType.EVEN_ODD}) and are covered by tests.
 *
 * <p>The package-private helpers ({@code closePriv}, {@code rectangle}) and the static
 * {@link #appendArc} are shim infrastructure used by the concrete {@code awt.geom} shapes to
 * append their outlines (the {@code awt.Shape} contract) and are not game-facing surface.
 */
public abstract class Path2D implements awt.Shape {

    /** AWT parity: even-odd crossing fill rule (test-covered; no census site). */
    public static final int WIND_EVEN_ODD = 0;

    /** AWT parity: non-zero winding fill rule (the rule every census path uses). */
    public static final int WIND_NON_ZERO = 1;

    /**
     * Appends an AWT-convention arc (0 degrees at 3 o'clock, POSITIVE sweep toward 12 o'clock,
     * i.e. visually counterclockwise on the y-down screen) to {@code sink} as cubic Bézier
     * segments of at most 90 degrees each, exactly the way {@code java.awt.geom.ArcIterator}
     * builds AWT's own arcs (control distance {@code 4/3 * tan(segment/4)}). This is the
     * equivalent of the float-space android prescription {@code Path.addArc(oval, -awtStart,
     * -awtSweep)} — here every control point stays double until
     * {@link Double#toAndroidPath(awt.AwtMatrix, int)}, so an arbitrary Graphics2D transform
     * (rotation included) stays exact, and the axis crossings of a full ellipse sit exactly on
     * its framing rectangle.
     *
     * <p>Connection rule: {@code standalone} starts a fresh subpath with a moveTo; otherwise the
     * arc connects to the sink's current point with a lineTo (moving to the first arc point when
     * the sink has none), which is what the rounded-rectangle outline needs.
     *
     * @param sink the segment sink to append to
     * @param cx the ellipse centre x
     * @param cy the ellipse centre y
     * @param rx the ellipse x radius
     * @param ry the ellipse y radius
     * @param startDeg the start angle in AWT degrees
     * @param sweepDeg the angular extent in AWT degrees (may be negative)
     * @param standalone {@code true} to always begin a new subpath
     */
    static void appendArc(Double sink, double cx, double cy, double rx, double ry,
            double startDeg, double sweepDeg, boolean standalone) {
        if (rx <= 0d || ry <= 0d || sweepDeg == 0d) {
            return;
        }
        // ArcIterator parity: a full sweep is four quadrants; otherwise as many segments of at
        // most 90 degrees as the extent needs (the 0.001 slack keeps 90/180/270 at 1/2/3).
        double sweep = sweepDeg;
        int segments;
        if (Math.abs(sweep) >= 360d) {
            sweep = sweep < 0d ? -360d : 360d;
            segments = 4;
        } else {
            segments = Math.max(1, (int) Math.ceil(Math.abs(sweep) / 90d - 0.001d));
        }
        double increment = Math.toRadians(sweep / segments);
        // Control-point distance along the tangent: 4/3 * tan(increment / 4).
        double k = 4d / 3d * Math.tan(increment / 4d);

        double angle = Math.toRadians(startDeg);
        double cos0 = Math.cos(angle);
        double sin0 = Math.sin(angle);
        double firstX = cx + rx * cos0;
        double firstY = cy - ry * sin0;
        if (standalone || !sink.hasCurrentPoint()) {
            sink.moveTo(firstX, firstY);
        } else {
            sink.lineTo(firstX, firstY);
        }
        for (int i = 1; i <= segments; i++) {
            double next = angle + increment;
            double cos1 = Math.cos(next);
            double sin1 = Math.sin(next);
            // E(t) = (cx + rx cos t, cy - ry sin t); tangent E'(t) = (-rx sin t, -ry cos t).
            sink.cubicTo(
                    cx + rx * (cos0 - k * sin0), cy - ry * (sin0 + k * cos0),
                    cx + rx * (cos1 + k * sin1), cy - ry * (sin1 - k * cos1),
                    cx + rx * cos1, cy - ry * sin1);
            angle = next;
            cos0 = cos1;
            sin0 = sin1;
        }
    }

    /** @return the winding rule this path fills with (shim: read by the Graphics2D fill) */
    public abstract int getWindingRule();

    /** The {@code Double} precision form the game uses. */
    public static class Double extends Path2D {

        private static final int SEG_MOVETO = 0;
        private static final int SEG_LINETO = 1;
        private static final int SEG_CLOSE = 2;
        private static final int SEG_CUBICTO = 3;

        private int windingRule = WIND_NON_ZERO;
        private int[] types = new int[16];
        private double[] coords = new double[32];
        private int typeCount;
        private int coordCount;
        private double currentX;
        private double currentY;
        private boolean hasCurrent;

        /** Creates an empty path with the default non-zero winding rule. */
        public Double() {
        }

        /**
         * Creates an empty path with the given winding rule.
         *
         * @param windingRule {@link #WIND_NON_ZERO} or {@link #WIND_EVEN_ODD}
         */
        public Double(int windingRule) {
            if (windingRule != WIND_NON_ZERO && windingRule != WIND_EVEN_ODD) {
                throw new IllegalArgumentException(
                        "Flapforge shim: unknown winding rule " + windingRule);
            }
            this.windingRule = windingRule;
        }

        /** @return the winding rule this path fills with */
        public int getWindingRule() {
            return windingRule;
        }

        /**
         * Shim infrastructure ({@code awt.Shape} contract): copies this path's segments into
         * {@code sink} verbatim (same coordinates, same subpath structure).
         *
         * @param sink the segment sink to append to
         */
        @Override
        public void appendTo(Path2D.Double sink) {
            int c = 0;
            for (int i = 0; i < typeCount; i++) {
                switch (types[i]) {
                    case SEG_MOVETO:
                        sink.moveTo(coords[c], coords[c + 1]);
                        c += 2;
                        break;
                    case SEG_LINETO:
                        sink.lineTo(coords[c], coords[c + 1]);
                        c += 2;
                        break;
                    case SEG_CUBICTO:
                        sink.cubicTo(coords[c], coords[c + 1], coords[c + 2], coords[c + 3],
                                coords[c + 4], coords[c + 5]);
                        c += 6;
                        break;
                    default:
                        sink.closePriv();
                        break;
                }
            }
        }

        /**
         * Starts a new subpath (census).
         *
         * @param x the start x
         * @param y the start y
         */
        public void moveTo(double x, double y) {
            growTypes(1);
            growCoords(2);
            types[typeCount++] = SEG_MOVETO;
            coords[coordCount++] = x;
            coords[coordCount++] = y;
            currentX = x;
            currentY = y;
            hasCurrent = true;
        }

        /**
         * Adds a line from the current point (census). Throws when no subpath is open, AWT parity.
         *
         * @param x the end x
         * @param y the end y
         */
        public void lineTo(double x, double y) {
            if (!hasCurrent) {
                throw new IllegalStateException(
                        "Flapforge shim: Path2D.lineTo without an open subpath");
            }
            growTypes(1);
            growCoords(2);
            types[typeCount++] = SEG_LINETO;
            coords[coordCount++] = x;
            coords[coordCount++] = y;
            currentX = x;
            currentY = y;
        }

        /**
         * Closes the current subpath with a straight segment back to its start (census); a no-op
         * when no subpath is open.
         */
        public void closePath() {
            closePriv();
        }

        /**
         * Shim infrastructure: adds a cubic Bézier from the current point through two control
         * points (only {@link Path2D#appendArc} produces these; the game never calls AWT's public
         * {@code curveTo}, which is therefore absent). Throws when no subpath is open.
         */
        void cubicTo(double x1, double y1, double x2, double y2, double x3, double y3) {
            if (!hasCurrent) {
                throw new IllegalStateException(
                        "Flapforge shim: Path2D.cubicTo without an open subpath");
            }
            growTypes(1);
            growCoords(6);
            types[typeCount++] = SEG_CUBICTO;
            coords[coordCount++] = x1;
            coords[coordCount++] = y1;
            coords[coordCount++] = x2;
            coords[coordCount++] = y2;
            coords[coordCount++] = x3;
            coords[coordCount++] = y3;
            currentX = x3;
            currentY = y3;
        }

        /**
         * Appends the outline of {@code shape} to this path (census:
         * render/BackgroundRenderer.java:561, :564, both with {@code connect == false}). With
         * {@code connect} true and an open subpath here, a lineTo to the appended path's start
         * replaces its initial moveTo (AWT parity).
         *
         * @param shape the shape whose outline is appended
         * @param connect whether to connect to this path's current point
         */
        public void append(Shape shape, boolean connect) {
            Double tmp = new Double();
            shape.appendTo(tmp);
            boolean first = true;
            int c = 0;
            for (int i = 0; i < tmp.typeCount; i++) {
                int type = tmp.types[i];
                if (type == SEG_MOVETO) {
                    if (first && connect && hasCurrent) {
                        lineTo(tmp.coords[c], tmp.coords[c + 1]);
                    } else {
                        moveTo(tmp.coords[c], tmp.coords[c + 1]);
                    }
                    c += 2;
                } else if (type == SEG_LINETO) {
                    lineTo(tmp.coords[c], tmp.coords[c + 1]);
                    c += 2;
                } else if (type == SEG_CUBICTO) {
                    cubicTo(tmp.coords[c], tmp.coords[c + 1], tmp.coords[c + 2],
                            tmp.coords[c + 3], tmp.coords[c + 4], tmp.coords[c + 5]);
                    c += 6;
                } else {
                    closePriv();
                }
                first = false;
            }
        }

        /**
         * Shim infrastructure ({@code awt.Shape} contract): the bounding box of every segment
         * endpoint and Bézier control point (the pre-JDK-19 AWT definition; the closing segment
         * adds nothing). For the arcs the shim produces — full ellipses and the axis-aligned
         * quarter arcs of rounded rectangles — the control points lie inside the framing
         * rectangle, so the box is tight. No census site reads a path's bounds.
         *
         * @return the bounds as a {@code Rectangle2D.Double}
         */
        @Override
        public awt.geom.Rectangle2D getBounds2D() {
            if (coordCount == 0) {
                return new awt.geom.Rectangle2D.Double(0d, 0d, 0d, 0d);
            }
            double minX = coords[0];
            double minY = coords[1];
            double maxX = minX;
            double maxY = minY;
            for (int c = 2; c < coordCount; c += 2) {
                minX = Math.min(minX, coords[c]);
                minY = Math.min(minY, coords[c + 1]);
                maxX = Math.max(maxX, coords[c]);
                maxY = Math.max(maxY, coords[c + 1]);
            }
            return new awt.geom.Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
        }

        /** Shim infrastructure: {@link #closePath} for the sibling awt.geom shapes. */
        void closePriv() {
            if (!hasCurrent) {
                return;
            }
            growTypes(1);
            types[typeCount++] = SEG_CLOSE;
            hasCurrent = false;
        }

        /**
         * Shim infrastructure: appends a closed rectangular subpath for Rectangle2D. AWT parity
         * ({@code RectIterator}): a negative width or height yields no segments at all, so
         * {@code fillRect(x, y, -w, h)} and a negative-width {@code Rectangle2D} draw nothing
         * instead of a mirrored rectangle; a zero extent still yields the degenerate outline
         * (which a stroke renders as a line, as in AWT).
         */
        void rectangle(double x, double y, double w, double h) {
            if (w < 0d || h < 0d) {
                return;
            }
            moveTo(x, y);
            lineTo(x + w, y);
            lineTo(x + w, y + h);
            lineTo(x, y + h);
            closePriv();
        }

        /** Shim infrastructure: whether a subpath is currently open. */
        boolean hasCurrentPoint() {
            return hasCurrent;
        }

        /**
         * Shim infrastructure (the Graphics2D pipeline entry): converts the stored segments into
         * an {@code android.graphics.Path}, transforming every point with {@code matrix} in
         * double space first and applying the given fill rule.
         *
         * @param matrix the current user-to-device transform (identity for device-space clips)
         * @param fillRule {@link #WIND_NON_ZERO} or {@link #WIND_EVEN_ODD}
         * @return a new android path holding this path's outline
         */
        public android.graphics.Path toAndroidPath(awt.AwtMatrix matrix, int fillRule) {
            android.graphics.Path path = new android.graphics.Path();
            path.setFillType(fillRule == WIND_EVEN_ODD
                    ? android.graphics.Path.FillType.EVEN_ODD
                    : android.graphics.Path.FillType.WINDING);
            double[] point = new double[2];
            int c = 0;
            for (int i = 0; i < typeCount; i++) {
                switch (types[i]) {
                    case SEG_MOVETO:
                        point[0] = coords[c];
                        point[1] = coords[c + 1];
                        matrix.apply(point);
                        path.moveTo((float) point[0], (float) point[1]);
                        c += 2;
                        break;
                    case SEG_LINETO:
                        point[0] = coords[c];
                        point[1] = coords[c + 1];
                        matrix.apply(point);
                        path.lineTo((float) point[0], (float) point[1]);
                        c += 2;
                        break;
                    case SEG_CUBICTO:
                        float[] control = new float[6];
                        for (int p = 0; p < 3; p++) {
                            point[0] = coords[c + 2 * p];
                            point[1] = coords[c + 2 * p + 1];
                            matrix.apply(point);
                            control[2 * p] = (float) point[0];
                            control[2 * p + 1] = (float) point[1];
                        }
                        path.cubicTo(control[0], control[1], control[2], control[3],
                                control[4], control[5]);
                        c += 6;
                        break;
                    default:
                        path.close();
                        break;
                }
            }
            return path;
        }

        private void growTypes(int n) {
            if (typeCount + n > types.length) {
                int[] bigger = new int[Math.max(types.length * 2, typeCount + n)];
                System.arraycopy(types, 0, bigger, 0, typeCount);
                types = bigger;
            }
        }

        private void growCoords(int n) {
            if (coordCount + n > coords.length) {
                double[] bigger = new double[Math.max(coords.length * 2, coordCount + n)];
                System.arraycopy(coords, 0, bigger, 0, coordCount);
                coords = bigger;
            }
        }
    }
}
