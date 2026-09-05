package awt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure-JVM proofs that {@link AwtMatrix} composes exactly like {@code java.awt.geom.AffineTransform}
 * (newer operations apply to points first: {@code p -> T * R * S * p}). Expected values are
 * computed by hand from the AWT definitions.
 */
public class AwtMatrixTest {

    private static final double EPS = 1e-12;

    private static double[] apply(AwtMatrix m, double x, double y) {
        double[] p = {x, y};
        m.apply(p);
        return p;
    }

    private static void assertPoint(double ex, double ey, double[] actual) {
        assertEquals("x", ex, actual[0], EPS);
        assertEquals("y", ey, actual[1], EPS);
    }

    @Test
    public void identityLeavesPointsAlone() {
        AwtMatrix m = new AwtMatrix();
        assertTrue(m.isIdentity());
        assertTrue(m.isAxisAligned());
        assertPoint(3.5, -2.25, apply(m, 3.5, -2.25));
        assertEquals(1d, m.averageScale(), EPS);
    }

    @Test
    public void translateRotateScaleChainMatchesAwt() {
        // (x, y) -> (32 - 2y, 32 + 2x)
        AwtMatrix m = new AwtMatrix();
        m.translate(32, 32);
        m.rotate(Math.PI / 2);
        m.scale(2, 2);
        assertPoint(32 - 2 * 2, 32 + 2 * 4, apply(m, 4, 2));
        assertPoint(32, 32, apply(m, 0, 0));
        assertEquals(2d, m.averageScale(), EPS);
        assertFalse(m.isIdentity());
    }

    @Test
    public void rotationTurnsPlusXTowardPlusY() {
        AwtMatrix m = new AwtMatrix();
        m.rotate(Math.PI / 2);
        assertPoint(0, 1, apply(m, 1, 0));
        assertPoint(-1, 0, apply(m, 0, 1));
        assertFalse(m.isAxisAligned());
    }

    @Test
    public void pivotRotationEqualsTranslateRotateTranslateBack() {
        AwtMatrix pivot = new AwtMatrix();
        pivot.rotate(0.7, 10, -4);
        AwtMatrix manual = new AwtMatrix();
        manual.translate(10, -4);
        manual.rotate(0.7);
        manual.translate(-10, 4);
        double[] a = apply(pivot, 3, 9);
        double[] b = apply(manual, 3, 9);
        assertPoint(b[0], b[1], a);
        assertPoint(10, -4, apply(pivot, 10, -4)); // the pivot itself is fixed
    }

    @Test
    public void scaleIsPerAxis() {
        AwtMatrix m = new AwtMatrix();
        m.scale(2, 0.5);
        assertPoint(8, 4, apply(m, 4, 8));
        assertTrue(m.isAxisAligned());
        assertEquals(1d, m.averageScale(), EPS); // sqrt(|det|) = sqrt(1)
        m.scale(-1, 1);
        assertPoint(-8, 4, apply(m, 4, 8));
        assertEquals(1d, m.averageScale(), EPS); // a flip does not shrink strokes
    }

    @Test
    public void translateAfterScaleIsScaled() {
        // AWT: scale(2,2) then translate(3,4) moves the origin to (6,8).
        AwtMatrix m = new AwtMatrix();
        m.scale(2, 2);
        m.translate(3, 4);
        assertPoint(6, 8, apply(m, 0, 0));
        assertPoint(8, 10, apply(m, 1, 1));
    }

    @Test
    public void shearMatchesAffineTransformShear() {
        // AffineTransform.shear(shx, shy): x' = x + shx * y, y' = shy * x + y.
        AwtMatrix x = new AwtMatrix();
        x.shear(0.5, 0);
        assertPoint(2 + 0.5 * 4, 4, apply(x, 2, 4));
        AwtMatrix y = new AwtMatrix();
        y.shear(0, 0.5);
        assertPoint(2, 0.5 * 2 + 4, apply(y, 2, 4));
        AwtMatrix both = new AwtMatrix();
        both.translate(10, 20);
        both.shear(1, 2);
        assertPoint(10 + (3 + 1 * 5), 20 + (2 * 3 + 5), apply(both, 3, 5));
    }

    @Test
    public void deltaApplyIgnoresTranslation() {
        AwtMatrix m = new AwtMatrix();
        m.translate(100, 200);
        m.scale(3, 3);
        double[] d = {1, 2};
        m.deltaApply(d);
        assertPoint(3, 6, d);
    }

    @Test
    public void inverseApplyUndoesApply() {
        AwtMatrix m = new AwtMatrix();
        m.translate(5, -7);
        m.rotate(1.1);
        m.scale(2, 0.5);
        m.shear(0.3, -0.2);
        double[] p = apply(m, 4.5, -1.25);
        m.inverseApply(p);
        assertPoint(4.5, -1.25, p);
    }

    @Test
    public void copySetAndIdentityReset() {
        AwtMatrix m = new AwtMatrix();
        m.translate(1, 2);
        m.rotate(0.3);
        AwtMatrix copy = new AwtMatrix(m);
        assertPoint(apply(m, 7, 8)[0], apply(m, 7, 8)[1], apply(copy, 7, 8));
        AwtMatrix other = new AwtMatrix();
        other.set(m);
        assertPoint(apply(m, -3, 4)[0], apply(m, -3, 4)[1], apply(other, -3, 4));
        m.setToIdentity();
        assertTrue(m.isIdentity());
        assertFalse(copy.isIdentity());
    }

    @Test
    public void inverseOperationsReturnToIdentityWithinDoublePrecision() {
        // The bird renderer brackets: translate(c); scale(s); ... scale(1/s); translate(-c).
        AwtMatrix m = new AwtMatrix();
        double sx = 1.06 * 17;
        double sy = 17;
        m.translate(20.5, 30.25);
        m.scale(sx, sy);
        m.scale(1 / sx, 1 / sy);
        m.translate(-20.5, -30.25);
        assertPoint(4, 4, apply(m, 4, 4));
        assertEquals(1d, m.averageScale(), 1e-9);
    }
}
