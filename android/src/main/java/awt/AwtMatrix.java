package awt;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Pure-double affine matrix backing the {@link Graphics2D} transform state. The AWT
 * {@code AffineTransform} surface the census exercises is exactly translate / rotate (1- and
 * 3-argument) / scale (26 + 8 + 9 call sites across render/ and ui/), so this helper carries only
 * those operations plus the pieces the Graphics2D pipeline needs: point and delta application,
 * inversion for {@code getClip()}, a determinant-derived scale for stroke widths, and conversion
 * to an {@code android.graphics.Matrix} for canvas-side image and text drawing.
 *
 * <p>All arithmetic is double; coordinates are converted to float only once, at the very end when
 * a path is handed to android (no float drift through the transform chain). Layout matches
 * AWT/android exactly: {@code [m00 m01 m02; m10 m11 m12]}, x' = m00*x + m01*y + m02, y' =
 * m10*x + m11*y + m12.
 */
public final class AwtMatrix {

    /** The identity matrix. */
    public AwtMatrix() {
    }

    /** Creates a copy of {@code other}. */
    public AwtMatrix(AwtMatrix other) {
        set(other);
    }

    // Package-visible for the Graphics2D pipeline; the six AWT matrix components.
    double m00 = 1d;
    double m01 = 0d;
    double m02 = 0d;
    double m10 = 0d;
    double m11 = 1d;
    double m12 = 0d;

    /** Resets to the identity. */
    public void setToIdentity() {
        m00 = 1d;
        m01 = 0d;
        m02 = 0d;
        m10 = 0d;
        m11 = 1d;
        m12 = 0d;
    }

    /** Copies every component from {@code other} (the census-free {@code setTransform} seam). */
    public void set(AwtMatrix other) {
        m00 = other.m00;
        m01 = other.m01;
        m02 = other.m02;
        m10 = other.m10;
        m11 = other.m11;
        m12 = other.m12;
    }

    /**
     * Concatenates a translation, AWT style: {@code this = translate(this)}.
     *
     * @param tx the x offset
     * @param ty the y offset
     */
    public void translate(double tx, double ty) {
        m02 += m00 * tx + m01 * ty;
        m12 += m10 * tx + m11 * ty;
    }

    /**
     * Concatenates a rotation about the origin, AWT style: positive angles rotate points from the
     * positive x axis toward the positive y axis (visually clockwise on the y-down screen). This
     * is the convention app-side geometry relies on (render/ProceduralArt wing pivots); note it is
     * deliberately the opposite of the Arc2D angle convention, which AWT defines visually
     * counterclockwise — see {@code awt.geom.Arc2D}.
     *
     * @param theta the angle in radians
     */
    public void rotate(double theta) {
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double n00 = m00 * cos + m01 * sin;
        double n01 = -m00 * sin + m01 * cos;
        double n10 = m10 * cos + m11 * sin;
        double n11 = -m10 * sin + m11 * cos;
        m00 = n00;
        m01 = n01;
        m10 = n10;
        m11 = n11;
    }

    /**
     * Concatenates a rotation about a pivot: {@code translate(px,py); rotate(theta);
     * translate(-px,-py)} — the AWT three-argument form (census: render/ProceduralArt.java:528,531,840,843).
     *
     * @param theta the angle in radians
     * @param px the pivot x
     * @param py the pivot y
     */
    public void rotate(double theta, double px, double py) {
        translate(px, py);
        rotate(theta);
        translate(-px, -py);
    }

    /**
     * Concatenates a scale, AWT style.
     *
     * @param sx the x scale factor
     * @param sy the y scale factor
     */
    public void scale(double sx, double sy) {
        m00 *= sx;
        m01 *= sy;
        m10 *= sx;
        m11 *= sy;
    }

    /**
     * Concatenates a shear, AWT style (kept for AWT parity of the matrix helper; the census never
     * calls Graphics2D.shear, so Graphics2D does not expose it).
     *
     * @param shx the x shear factor
     * @param shy the y shear factor
     */
    public void shear(double shx, double shy) {
        // AffineTransform.shear: [m00 m01] * [1 shx; shy 1].
        double n00 = m00 + m01 * shy;
        double n01 = m00 * shx + m01;
        double n10 = m10 + m11 * shy;
        double n11 = m10 * shx + m11;
        m00 = n00;
        m01 = n01;
        m10 = n10;
        m11 = n11;
    }

    /**
     * Whether this matrix is the identity (within exact double equality).
     *
     * @return {@code true} when no transform is applied
     */
    public boolean isIdentity() {
        return m00 == 1d && m01 == 0d && m02 == 0d
                && m10 == 0d && m11 == 1d && m12 == 0d;
    }

    /**
     * Whether the linear part carries any rotation or shear, i.e. whether axis-aligned rectangles
     * stay axis-aligned under this matrix.
     *
     * @return {@code true} when {@code m01} and {@code m10} are zero
     */
    public boolean isAxisAligned() {
        return m01 == 0d && m10 == 0d;
    }

    /**
     * The uniform scale factor the stroke pipeline uses: the square root of the absolute
     * determinant, i.e. the factor a unit length grows by. Exact for the translate / uniform
     * scale matrices the census draws under.
     *
     * @return the average scale factor (never negative)
     */
    public double averageScale() {
        double det = m00 * m11 - m01 * m10;
        return Math.sqrt(Math.abs(det));
    }

    /**
     * The x of the transformed point {@code (x, y)}: {@code m00*x + m01*y + m02}. The
     * allocation-free form of {@link #apply(double[])} the path pipeline uses per segment
     * (pair with {@link #transformY(double, double)}).
     *
     * @param x the user-space x
     * @param y the user-space y
     * @return the device-space x
     */
    public double transformX(double x, double y) {
        return m00 * x + m01 * y + m02;
    }

    /**
     * The y of the transformed point {@code (x, y)}: {@code m10*x + m11*y + m12}.
     *
     * @param x the user-space x
     * @param y the user-space y
     * @return the device-space y
     */
    public double transformY(double x, double y) {
        return m10 * x + m11 * y + m12;
    }

    /**
     * Transforms a point in place inside a two-element array.
     *
     * @param point the array {@code [x, y]}, replaced by the transformed point
     */
    public void apply(double[] point) {
        double x = point[0];
        double y = point[1];
        point[0] = m00 * x + m01 * y + m02;
        point[1] = m10 * x + m11 * y + m12;
    }

    /**
     * Transforms a direction vector (ignores translation).
     *
     * @param delta the array {@code [dx, dy]}, replaced by the transformed delta
     */
    public void deltaApply(double[] delta) {
        double dx = delta[0];
        double dy = delta[1];
        delta[0] = m00 * dx + m01 * dy;
        delta[1] = m10 * dx + m11 * dy;
    }

    /**
     * Transforms a point by the inverse of this matrix (used by {@code Graphics2D.getClip} to
     * hand the device-space clip back in user space). Assumes an invertible matrix.
     *
     * @param point the array {@code [x, y]}, replaced by the inverse-transformed point
     */
    public void inverseApply(double[] point) {
        double det = m00 * m11 - m01 * m10;
        double x = point[0] - m02;
        double y = point[1] - m12;
        point[0] = (m11 * x - m01 * y) / det;
        point[1] = (m00 * y - m10 * x) / det;
    }

    /**
     * Writes the components in {@code android.graphics.Matrix.setValues} order (row-major 3x3,
     * the last row {@code 0 0 1}) into a caller-owned buffer, so the Graphics2D image and text
     * pipeline can refresh its one android matrix without allocating.
     *
     * @param values a buffer of at least nine floats, filled in place
     */
    public void toFloatValues(float[] values) {
        values[0] = (float) m00;
        values[1] = (float) m01;
        values[2] = (float) m02;
        values[3] = (float) m10;
        values[4] = (float) m11;
        values[5] = (float) m12;
        values[6] = 0f;
        values[7] = 0f;
        values[8] = 1f;
    }

    /**
     * Builds an {@code android.graphics.Matrix} with the same components, used by the
     * Graphics2D image and text pipeline to let the canvas apply the transform in one step.
     *
     * @return a new android matrix with identical values
     */
    public android.graphics.Matrix toAndroidMatrix() {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        float[] values = new float[9];
        toFloatValues(values);
        matrix.setValues(values);
        return matrix;
    }

    @Override
    public String toString() {
        return "AwtMatrix[" + m00 + ", " + m01 + ", " + m02 + ", " + m10 + ", " + m11 + ", "
                + m12 + "]";
    }
}
