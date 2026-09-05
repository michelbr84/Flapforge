package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.render.Overscan;
import io.github.michelbr84.flapforge.render.Viewport;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ViewportTest {

    private static final double EPS = 1e-9;

    @Test
    void exactDoubleScaleMapsBothWays() {
        Viewport vp = new Viewport(840, 1280, false);
        assertEquals(2.0, vp.scale(), EPS);
        assertEquals(0.0, vp.offsetX(), EPS);
        assertEquals(0.0, vp.offsetY(), EPS);

        Vec2 w = vp.toWindow(Playfield.WIDTH, Playfield.HEIGHT);
        assertEquals(840, w.x(), EPS);
        assertEquals(1280, w.y(), EPS);

        Vec2 l = vp.toLogical(210, 640);
        assertEquals(105, l.x(), EPS);
        assertEquals(320, l.y(), EPS);
        assertTrue(vp.letterboxBars().isEmpty());
    }

    @Test
    void wideWindowLetterboxesHorizontally() {
        Viewport vp = new Viewport(1000, 1280, false);
        assertEquals(2.0, vp.scale(), EPS);
        assertEquals(80.0, vp.offsetX(), EPS);
        assertEquals(0.0, vp.offsetY(), EPS);

        Vec2 origin = vp.toWindow(0, 0);
        assertEquals(80, origin.x(), EPS);
        assertEquals(0, origin.y(), EPS);

        Vec2 l = vp.toLogical(80, 0);
        assertEquals(0, l.x(), EPS);
        assertEquals(0, l.y(), EPS);
        assertTrue(vp.toLogical(10, 10).x() < 0, "points in the bar map outside the playfield");
        assertFalse(vp.containsLogical(vp.toLogical(10, 10).x(), 10));

        List<Aabb> bars = vp.letterboxBars();
        assertEquals(2, bars.size());
        assertEquals(new Aabb(0, 0, 80, 1280), bars.get(0));
        assertEquals(new Aabb(920, 0, 80, 1280), bars.get(1));
    }

    @Test
    void tallWindowLetterboxesVertically() {
        Viewport vp = new Viewport(420, 800, false);
        assertEquals(1.0, vp.scale(), EPS);
        assertEquals(0.0, vp.offsetX(), EPS);
        assertEquals(80.0, vp.offsetY(), EPS);
        List<Aabb> bars = vp.letterboxBars();
        assertEquals(2, bars.size());
        assertEquals(new Aabb(0, 0, 420, 80), bars.get(0));
        assertEquals(new Aabb(0, 720, 420, 80), bars.get(1));
    }

    @Test
    void integerSnappingFloorsTheScale() {
        Viewport fractional = new Viewport(1000, 1500, false);
        assertEquals(1500.0 / 640.0, fractional.scale(), EPS);

        Viewport snapped = new Viewport(1000, 1500, true);
        assertEquals(2.0, snapped.scale(), EPS);
        assertEquals(80.0, snapped.offsetX(), EPS);
        assertEquals(110.0, snapped.offsetY(), EPS);

        snapped.setIntegerScaling(false);
        assertEquals(1500.0 / 640.0, snapped.scale(), EPS);
    }

    @Test
    void scalesBelowOneAreNeverSnappedToZero() {
        Viewport vp = new Viewport(210, 320, true);
        assertEquals(0.5, vp.scale(), EPS);
        Vec2 w = vp.toWindow(420, 640);
        assertEquals(210, w.x(), EPS);
        assertEquals(320, w.y(), EPS);
    }

    @Test
    void roundTripIsIdentity() {
        Viewport vp = new Viewport(1234, 987, false);
        for (double x = 0; x <= Playfield.WIDTH; x += 35) {
            for (double y = 0; y <= Playfield.HEIGHT; y += 40) {
                Vec2 w = vp.toWindow(x, y);
                Vec2 back = vp.toLogical(w.x(), w.y());
                assertEquals(x, back.x(), 1e-6);
                assertEquals(y, back.y(), 1e-6);
            }
        }
    }

    @Test
    void resizeRecomputes() {
        Viewport vp = new Viewport(420, 640, false);
        assertEquals(1.0, vp.scale(), EPS);
        vp.resize(1260, 1920);
        assertEquals(3.0, vp.scale(), EPS);
        assertEquals(1260, vp.windowWidth());
        assertEquals(1920, vp.windowHeight());
        vp.resize(0, 0);
        assertTrue(vp.scale() > 0, "degenerate sizes keep a positive scale");
    }

    @Test
    void visibleBoundsOnATallPhone() {
        Viewport vp = new Viewport(1080, 2400, false);
        assertEquals(1080 / 420.0, vp.scale(), EPS);
        assertEquals(0.0, vp.offsetX(), EPS);
        assertEquals(377.0, vp.offsetY(), EPS);
        assertEquals(-377 * 420.0 / 1080, vp.visibleTopY(), 1e-9);
        assertEquals(2023 * 420.0 / 1080, vp.visibleBottomY(), 1e-9);
        assertEquals(0.0, vp.visibleLeftX(), EPS);
        assertEquals(420.0, vp.visibleRightX(), EPS);
    }

    @Test
    void extendedApplyWidensOnlyTheVerticalClip() {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            Viewport vp = new Viewport(420, 800, false);
            vp.apply(g);
            assertEquals(0, g.getClipBounds().x);
            assertEquals(Playfield.WIDTH, g.getClipBounds().width);
            assertEquals(-80, g.getClipBounds().y, "the clip reaches the visible top");
            assertEquals(800, g.getClipBounds().height, "the clip reaches the visible bottom");
        } finally {
            g.dispose();
        }
        g = img.createGraphics();
        try {
            Viewport vp = new Viewport(420, 800, false);
            vp.setExtendVertical(false);
            vp.apply(g);
            assertEquals(0, g.getClipBounds().y, "toggled off, the playfield clip returns");
            assertEquals(Playfield.HEIGHT, g.getClipBounds().height);
        } finally {
            g.dispose();
        }
    }

    @Test
    void publishOverscanFollowsTheToggle() {
        try {
            Viewport vp = new Viewport(420, 800, false);
            vp.publishOverscan();
            assertEquals(-80.0, Overscan.top(), EPS);
            assertEquals(720.0, Overscan.bottom(), EPS);
            vp.setExtendVertical(false);
            vp.publishOverscan();
            assertEquals(0.0, Overscan.top(), EPS);
            assertEquals(Playfield.HEIGHT, Overscan.bottom(), EPS);
        } finally {
            Overscan.reset();
        }
    }

    @Test
    void applyComposesUnderneathAHiDpiTransform() {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.scale(2, 2);
            Viewport vp = new Viewport(1000, 1280, false);
            vp.apply(g);
            AffineTransform t = g.getTransform();

            Point2D origin = t.transform(new Point2D.Double(0, 0), null);
            assertEquals(160, origin.getX(), EPS);
            assertEquals(0, origin.getY(), EPS);

            Point2D corner = t.transform(new Point2D.Double(Playfield.WIDTH, Playfield.HEIGHT), null);
            assertEquals(2 * (80 + 840), corner.getX(), EPS);
            assertEquals(2 * 1280, corner.getY(), EPS);

            assertEquals(4.0, t.getScaleX(), EPS);
            assertEquals(4.0, t.getScaleY(), EPS);
            assertEquals(Playfield.WIDTH, g.getClipBounds().width);
            assertEquals(Playfield.HEIGHT, g.getClipBounds().height);
        } finally {
            g.dispose();
        }
    }
}
