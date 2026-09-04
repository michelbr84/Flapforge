package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * The darkness of a world ({@code worlds.json.ambient.darkness}, D18, M7): a translucent dark
 * veil over the playfield with a soft light around the bird, so a dark world stays readable —
 * the bird is always in the clear and the next hazard, a few hundred pixels to its right, sits
 * in the veil's fade rather than under its full weight.
 *
 * <p>The veil is one ARGB mask built once per darkness value: {@value #MASK_HEIGHT} px tall,
 * with the hole at its vertical centre. The bird's x is fixed, so the frame only has to pick
 * which slice of the mask to blit against the bird's y — one {@code drawImage} per frame, no
 * paint objects, no per-frame allocation. The hole is fully clear inside {@link #CLEAR_RADIUS}
 * and reaches the world's darkness at {@link #FADE_RADIUS}, on a smooth ramp.
 */
public final class DarknessOverlay {

    /** Radius around the bird that stays fully lit. */
    public static final double CLEAR_RADIUS = 96;
    /** Radius at which the veil reaches the world's full darkness. */
    public static final double FADE_RADIUS = 250;
    /** Height of the cached mask: three playfields, so any bird y has a full slice. */
    public static final int MASK_HEIGHT = 3 * Playfield.HEIGHT;
    /** Strongest veil the overlay ever draws, whatever the world asks for (readability first). */
    public static final double MAX_ALPHA = 0.78;

    private BufferedImage mask;
    private double maskDarkness = -1;

    /** Creates an overlay with no veil until {@link #prepare(double)} is called. */
    public DarknessOverlay() {
    }

    /**
     * Builds the mask for a darkness value; a no-op when it is already built for that value.
     *
     * @param darkness the world's darkness in {@code [0, 1]}; {@code 0} draws nothing
     */
    public void prepare(double darkness) {
        double d = MathUtil.clamp(darkness, 0, 1);
        if (d == maskDarkness) {
            return;
        }
        maskDarkness = d;
        if (d <= 0) {
            mask = null;
            return;
        }
        double peak = Math.min(MAX_ALPHA, d);
        BufferedImage image = new BufferedImage(Playfield.WIDTH, MASK_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[Playfield.WIDTH];
        double holeY = MASK_HEIGHT / 2.0;
        for (int y = 0; y < MASK_HEIGHT; y++) {
            double dy = y + 0.5 - holeY;
            for (int x = 0; x < Playfield.WIDTH; x++) {
                double dx = x + 0.5 - Playfield.BIRD_X;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double t;
                if (dist <= CLEAR_RADIUS) {
                    t = 0;
                } else if (dist >= FADE_RADIUS) {
                    t = 1;
                } else {
                    double u = (dist - CLEAR_RADIUS) / (FADE_RADIUS - CLEAR_RADIUS);
                    t = u * u * (3 - 2 * u);
                }
                // Floored, so the veil never exceeds the world's darkness after quantisation.
                int a = (int) (255 * peak * t);
                row[x] = a << 24;
            }
            image.setRGB(0, y, Playfield.WIDTH, 1, row, 0, Playfield.WIDTH);
        }
        mask = image;
    }

    /**
     * The darkness the mask was built for.
     *
     * @return the value, {@code -1} before the first {@link #prepare(double)}
     */
    public double darkness() {
        return maskDarkness;
    }

    /**
     * Whether a veil would be drawn.
     *
     * @return {@code true} when the darkness is above zero
     */
    public boolean isActive() {
        return mask != null;
    }

    /**
     * The veil alpha at a playfield point for a bird at a given y (tests): the value the mask
     * carries, in {@code [0, 1]}.
     *
     * @param px the point x
     * @param py the point y
     * @param birdY the bird's y
     * @return the alpha, 0 when no veil is active
     */
    public double alphaAt(double px, double py, double birdY) {
        if (mask == null) {
            return 0;
        }
        int x = MathUtil.clamp((int) px, 0, Playfield.WIDTH - 1);
        int y = MathUtil.clamp((int) (py + sliceTop(birdY)), 0, MASK_HEIGHT - 1);
        return ((mask.getRGB(x, y) >>> 24) & 0xFF) / 255.0;
    }

    /**
     * Draws the veil around the bird.
     *
     * @param g the context in logical coordinates
     * @param birdY the bird's y (the hole follows it)
     */
    public void render(Graphics2D g, double birdY) {
        if (mask == null) {
            return;
        }
        int sy = sliceTop(birdY);
        g.drawImage(mask, 0, 0, Playfield.WIDTH, Playfield.HEIGHT, 0, sy, Playfield.WIDTH,
                sy + Playfield.HEIGHT, null);
    }

    private static int sliceTop(double birdY) {
        double clamped = MathUtil.clamp(birdY, -Playfield.HEIGHT / 2.0, Playfield.HEIGHT * 1.5);
        return (int) Math.round(MASK_HEIGHT / 2.0 - clamped);
    }
}
