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
 * {@code quadTo}/{@code curveTo} are never exercised and are absent. The subpath bookkeeping
 * follows AWT: {@code lineTo} throws only on an empty path, and after a {@code closePath} it
 * continues from the closed subpath's start (every census builder opens each subpath with
 * {@code moveTo}, so only the parity is at stake). The game only builds paths
 * with the default winding rule (no {@code WIND_EVEN_ODD} site in the census); the
 * {@code (windingRule)} constructor, the two constants and {@link Double#getWindingRule()} exist
 * for the fill-rule contract (the shim maps {@code WIND_EVEN_ODD} paths onto
 * {@code Path.FillType.EVEN_ODD}) and are covered by tests. {@link Double#reset()} is AWT's
 * public {@code reset} (no census site); the Graphics2D pipeline uses it to recycle its scratch
 * paths instead of allocating one per fill.
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
     * <p>Degenerate arcs follow what the AWT iterators render rather than what they list. A
     * negative radius appends nothing ({@code ArcIterator} and {@code EllipseIterator} emit no
     * segment for a negative extent), and so does a radius of zero on both axes: the iterators
     * do emit a point-sized outline there, but Java2D strokes nothing for it, where Skia would
     * cap the closed point into a dot. A zero radius on one axis appends the degenerate outline
     * the iterators produce — the cubics collapse onto a line across the other extent, which
     * {@code draw} strokes and {@code fill} ignores. A zero sweep appends the start point alone
     * ({@code ArcIterator}'s lone {@code SEG_MOVETO}), so a closure the caller adds afterwards
     * is legal. The return value says whether anything was appended: the sink may be a live
     * path with a subpath of its own open ({@link Double#append}), so a shape closing its
     * outline must know whether the arc opened one.
     *
     * @param sink the segment sink to append to
     * @param cx the ellipse centre x
     * @param cy the ellipse centre y
     * @param rx the ellipse x radius
     * @param ry the ellipse y radius
     * @param startDeg the start angle in AWT degrees
     * @param sweepDeg the angular extent in AWT degrees (may be negative)
     * @param standalone {@code true} to always begin a new subpath
     * @return whether any segment was appended
     */
    static boolean appendArc(Double sink, double cx, double cy, double rx, double ry,
            double startDeg, double sweepDeg, boolean standalone) {
        if (rx < 0d || ry < 0d || (rx == 0d && ry == 0d)) {
            return false;
        }
        // ArcIterator parity: a full sweep is four quadrants; otherwise as many segments of at
        // most 90 degrees as the extent needs (the 0.001 slack keeps 90/180/270 at 1/2/3), and
        // none for a zero sweep, which leaves the start point on its own.
        double sweep = sweepDeg;
        int segments;
        if (Math.abs(sweep) >= 360d) {
            sweep = sweep < 0d ? -360d : 360d;
            segments = 4;
        } else if (sweep == 0d) {
            segments = 0;
        } else {
            segments = Math.max(1, (int) Math.ceil(Math.abs(sweep) / 90d)); // ArcIterator
        }
        double increment = segments == 0 ? 0d : Math.toRadians(sweep / segments);
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
        return true;
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
        }

        /**
         * Adds a line from the current point (census). Throws on an empty path, AWT parity
         * ({@code IllegalPathStateException: missing initial moveto}); after a
         * {@link #closePath} the line continues from the closed subpath's start, as in AWT
         * ({@code Path2D} accepts {@code Z} then {@code L}) and as {@code android.graphics.Path}
         * replays the pair (its {@code lineTo} after {@code close} injects a moveTo at the last
         * moveTo point).
         *
         * @param x the end x
         * @param y the end y
         */
        public void lineTo(double x, double y) {
            if (typeCount == 0) {
                throw new IllegalStateException(
                        "Flapforge shim: Path2D.lineTo on an empty path (missing initial moveTo)");
            }
            growTypes(1);
            growCoords(2);
            types[typeCount++] = SEG_LINETO;
            coords[coordCount++] = x;
            coords[coordCount++] = y;
        }

        /**
         * Closes the current subpath with a straight segment back to its start (census). A no-op
         * right after a close (AWT parity: no second {@code SEG_CLOSE}) and on an empty path
         * (where AWT throws {@code IllegalPathStateException}; the shim is lenient there — no
         * census site closes an empty path). The path stays usable afterwards: a following
         * {@link #lineTo} continues from the closed subpath's start.
         */
        public void closePath() {
            closePriv();
        }

        /**
         * Removes every segment, keeping the winding rule (AWT parity; no census site). The
         * segment buffers are kept, so a recycled path allocates nothing on refill — the
         * Graphics2D pipeline resets its scratch paths this way once per shape.
         */
        public void reset() {
            typeCount = 0;
            coordCount = 0;
        }

        /**
         * Shim infrastructure: adds a cubic Bézier from the current point through two control
         * points (only {@link Path2D#appendArc} produces these; the game never calls AWT's public
         * {@code curveTo}, which is therefore absent). Throws on an empty path, like
         * {@link #lineTo}.
         */
        void cubicTo(double x1, double y1, double x2, double y2, double x3, double y3) {
            if (typeCount == 0) {
                throw new IllegalStateException(
                        "Flapforge shim: Path2D.cubicTo on an empty path (missing initial moveTo)");
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
        }

        /**
         * Appends the outline of {@code shape} to this path (census:
         * render/BackgroundRenderer.java:561, :564, both with {@code connect == false}). With
         * {@code connect} true and an open subpath here, a lineTo to the appended path's start
         * replaces its initial moveTo (AWT parity).
         *
         * <p>The shape writes straight into this path, so a subpath open here is visible to it;
         * the {@link Shape#appendTo} contract (a shape opens its own subpath first and closes
         * only that one, or appends nothing at all) is what keeps the result identical to
         * appending through a fresh sink.
         *
         * @param shape the shape whose outline is appended
         * @param connect whether to connect to this path's current point
         */
        public void append(Shape shape, boolean connect) {
            if (shape == this) {
                // Appending a path to itself: copy first, or the walk would chase its own tail.
                Double copy = new Double();
                appendTo(copy);
                shape = copy;
            }
            int start = typeCount;
            boolean connectFirst = connect && hasCurrentPoint();
            // The shape appends its outline straight into this path (no temporary copy); a
            // moveTo and a lineTo leave identical current-point state, so the connect rule is
            // applied afterwards by retyping the first appended segment.
            shape.appendTo(this);
            if (connectFirst && typeCount > start && types[start] == SEG_MOVETO) {
                types[start] = SEG_LINETO;
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
            if (typeCount == 0 || types[typeCount - 1] == SEG_CLOSE) {
                return;
            }
            growTypes(1);
            types[typeCount++] = SEG_CLOSE;
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

        /**
         * Shim infrastructure: whether the path has a current point, i.e. whether a segment may
         * follow (AWT parity: any segment at all leaves one, a close included — the closed
         * subpath's start).
         */
        boolean hasCurrentPoint() {
            return typeCount > 0;
        }

        /**
         * Shim infrastructure (the Graphics2D pipeline entry): converts the stored segments into
         * a new {@code android.graphics.Path}, transforming every point with {@code matrix} in
         * double space first and applying the given fill rule. Used for the clip paths, which
         * Graphics2D retains; the per-draw path goes through
         * {@link #toAndroidPath(awt.AwtMatrix, int, android.graphics.Path)} on a recycled target.
         *
         * @param matrix the current user-to-device transform (identity for device-space clips)
         * @param fillRule {@link #WIND_NON_ZERO} or {@link #WIND_EVEN_ODD}
         * @return a new android path holding this path's outline
         */
        public android.graphics.Path toAndroidPath(awt.AwtMatrix matrix, int fillRule) {
            android.graphics.Path path = new android.graphics.Path();
            toAndroidPath(matrix, fillRule, path);
            return path;
        }

        /**
         * Shim infrastructure: rebuilds {@code target} (rewound first, so its native buffers are
         * reused) from the stored segments, transforming every point with {@code matrix} in
         * double space and applying the given fill rule. Allocation-free: the float conversion
         * happens per coordinate, straight into the android path.
         *
         * @param matrix the current user-to-device transform
         * @param fillRule {@link #WIND_NON_ZERO} or {@link #WIND_EVEN_ODD}
         * @param target the android path to overwrite
         */
        public void toAndroidPath(awt.AwtMatrix matrix, int fillRule,
                android.graphics.Path target) {
            target.rewind();
            target.setFillType(fillRule == WIND_EVEN_ODD
                    ? android.graphics.Path.FillType.EVEN_ODD
                    : android.graphics.Path.FillType.WINDING);
            int c = 0;
            for (int i = 0; i < typeCount; i++) {
                switch (types[i]) {
                    case SEG_MOVETO: {
                        double x = coords[c];
                        double y = coords[c + 1];
                        target.moveTo((float) matrix.transformX(x, y),
                                (float) matrix.transformY(x, y));
                        c += 2;
                        break;
                    }
                    case SEG_LINETO: {
                        double x = coords[c];
                        double y = coords[c + 1];
                        target.lineTo((float) matrix.transformX(x, y),
                                (float) matrix.transformY(x, y));
                        c += 2;
                        break;
                    }
                    case SEG_CUBICTO: {
                        double x1 = coords[c];
                        double y1 = coords[c + 1];
                        double x2 = coords[c + 2];
                        double y2 = coords[c + 3];
                        double x3 = coords[c + 4];
                        double y3 = coords[c + 5];
                        target.cubicTo(
                                (float) matrix.transformX(x1, y1), (float) matrix.transformY(x1, y1),
                                (float) matrix.transformX(x2, y2), (float) matrix.transformY(x2, y2),
                                (float) matrix.transformX(x3, y3), (float) matrix.transformY(x3, y3));
                        c += 6;
                        break;
                    }
                    default:
                        target.close();
                        break;
                }
            }
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
