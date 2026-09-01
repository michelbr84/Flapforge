package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;

/**
 * Draws the player's bird with the upstream wing animation (D18, plan section 5 cosmetic rows).
 *
 * <p>Upstream picked its wing image as {@code wingState / 10 % 8} at 30 Hz and reset
 * {@code wingState} to 0 on every accepted flap; at 60 Hz that is
 * {@value #TICKS_PER_WING_FRAME} ticks per frame over {@value #WING_FRAMES} frames, restarted on
 * a {@code Flapped} fact. Upstream also swapped in an "up" sprite while the bird was rising and a
 * "dead" sprite once it died; {@link ProceduralArt.BirdPose} reproduces both. The bird's y is
 * interpolated between {@link Bird#prevY()} and {@link Bird#y()} with the frame alpha (E30.g);
 * its x is fixed at {@link Playfield#BIRD_X}.
 *
 * <p>With {@code F3} on, the hitbox actually used by the collision system is outlined so the
 * 33x31-at-(-17,-12) quirk can be checked against the drawn sprite.
 */
public final class BirdRenderer {

    /** Wing animation frames, as upstream. */
    public static final int WING_FRAMES = 8;
    /** Ticks each wing frame is held (upstream: 10 frames at 30 Hz). */
    public static final int TICKS_PER_WING_FRAME = 20;
    /** Length of one full wing cycle in ticks. */
    public static final int WING_CYCLE_TICKS = WING_FRAMES * TICKS_PER_WING_FRAME;

    private static final Color HITBOX = new Color(0xFF, 0x3B, 0x3B, 0xC0);
    private static final Stroke HITBOX_STROKE = new BasicStroke(1f);

    private final Rectangle2D.Double rect = new Rectangle2D.Double();
    private int wingTick;

    /** Creates a renderer with the wing animation at frame 0. */
    public BirdRenderer() {
    }

    /**
     * Advances the wing animation by one tick.
     *
     * @param flapped {@code true} when the tick produced a {@code Flapped} fact; the animation
     *     restarts at frame 0, as upstream's {@code wingState = 0}
     */
    public void tick(boolean flapped) {
        if (flapped) {
            wingTick = 0;
        } else {
            wingTick = (wingTick + 1) % WING_CYCLE_TICKS;
        }
    }

    /** Restarts the wing animation (a new run). */
    public void reset() {
        wingTick = 0;
    }

    /**
     * Index of the wing frame currently shown.
     *
     * @return a value in {@code [0, 8)}
     */
    public int wingFrame() {
        return wingTick / TICKS_PER_WING_FRAME;
    }

    /**
     * Pose for a bird state: dead once it is no longer alive, "up" while it rises.
     *
     * @param bird the bird
     * @return the pose
     */
    public static ProceduralArt.BirdPose poseOf(Bird bird) {
        if (bird.state() != Bird.State.ALIVE) {
            return ProceduralArt.BirdPose.DEAD;
        }
        return bird.vy() < 0 ? ProceduralArt.BirdPose.UP : ProceduralArt.BirdPose.NORMAL;
    }

    /**
     * Draws the bird.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param bird the bird
     * @param palette the world palette
     * @param hitboxScale the {@code HITBOX_SCALE} stat, for the debug outline
     * @param debugHitbox {@code true} to outline the collision box ({@code F3})
     */
    public void render(Graphics2D g, double alpha, Bird bird, WorldPalette palette,
            double hitboxScale, boolean debugHitbox) {
        double y = MathUtil.lerp(bird.prevY(), bird.y(), alpha);
        double phase = wingFrame() / (double) WING_FRAMES;
        ProceduralArt.drawBird(g, Playfield.BIRD_X, y, Playfield.SPRITE_W, phase, palette,
                poseOf(bird));
        if (debugHitbox) {
            Aabb box = bird.hitboxAt(y, hitboxScale);
            Stroke old = g.getStroke();
            g.setStroke(HITBOX_STROKE);
            g.setColor(HITBOX);
            rect.setFrame(box.x(), box.y(), box.w(), box.h());
            g.draw(rect);
            g.setStroke(old);
        }
    }
}
