package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.render.Overscan;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The published overscan range (D3 revision): clamped so it always covers the playfield,
 * defaulting to exactly the playfield, and {@code fillVisible} covers every visible row.
 */
class OverscanTest {

    @AfterEach
    void resetOverscan() {
        Overscan.reset();
    }

    @Test
    void theDefaultRangeIsThePlayfield() {
        assertEquals(0.0, Overscan.top());
        assertEquals(Playfield.HEIGHT, Overscan.bottom());
        assertEquals(0, Overscan.topInt());
        assertEquals(Playfield.HEIGHT, Overscan.bottomInt());
    }

    @Test
    void setPublishesAndResetRestores() {
        Overscan.set(-146.61, 786.72);
        assertEquals(-146.61, Overscan.top());
        assertEquals(786.72, Overscan.bottom());
        assertEquals(-147, Overscan.topInt(), "the top floors outward");
        assertEquals(787, Overscan.bottomInt(), "the bottom ceils outward");
        Overscan.reset();
        assertEquals(0.0, Overscan.top());
        assertEquals(Playfield.HEIGHT, Overscan.bottom());
    }

    @Test
    void theRangeNeverShrinksBelowThePlayfield() {
        Overscan.set(10, 600);
        assertEquals(0.0, Overscan.top(), "a top below row 0 is clamped");
        assertEquals(Playfield.HEIGHT, Overscan.bottom(), "a bottom above row 640 is clamped");
    }

    @Test
    void fillVisibleCoversEveryVisibleRow() {
        Overscan.set(-20, 660);
        BufferedImage image = new BufferedImage(Playfield.WIDTH, 700,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.translate(0, 20);
            Overscan.fillVisible(g, Color.RED);
        } finally {
            g.dispose();
        }
        int red = Color.RED.getRGB();
        assertEquals(red, image.getRGB(210, 0), "the extended top row is covered");
        assertEquals(red, image.getRGB(210, 350), "the playfield is covered");
        assertEquals(red, image.getRGB(210, 679), "the extended bottom row is covered");
    }
}
