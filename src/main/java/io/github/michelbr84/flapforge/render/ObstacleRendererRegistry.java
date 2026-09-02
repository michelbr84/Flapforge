package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * One renderer per {@link ObstacleKind}, dispatched by kind (D18, M7): the gate art stays in
 * {@link ObstacleRenderer}; gears, pistons, wind zones and lightning have a class each. The
 * registry owns the animation clock the decorations run on (the piston pulse, the wind drift)
 * and draws the {@code F3} hitbox outlines for every kind.
 *
 * <p>Draw order is the layer order — spawn order — except that wind zones are drawn first: a
 * zone is a body of air and the columns inside it must not be covered by its wash.
 */
public final class ObstacleRendererRegistry {

    private static final Color HITBOX = new Color(0x3B, 0xFF, 0x6E, 0xC0);
    private static final Stroke HITBOX_STROKE = new BasicStroke(1f);

    private final ObstacleRenderer gates = new ObstacleRenderer();
    private final GearRenderer gears = new GearRenderer();
    private final PistonRenderer pistons = new PistonRenderer();
    private final WindZoneRenderer winds = new WindZoneRenderer();
    private final LightningRenderer bolts = new LightningRenderer();
    private final Rectangle2D.Double rect = new Rectangle2D.Double();
    private long animTicks;

    /** Creates the registry with one renderer per kind. */
    public ObstacleRendererRegistry() {
    }

    /** Advances the decorations' clock by one tick. */
    public void tick() {
        animTicks++;
    }

    /** Restarts the decorations' clock (a new run). */
    public void reset() {
        animTicks = 0;
    }

    /**
     * The animation clock.
     *
     * @return ticks since the last reset
     */
    public long animTicks() {
        return animTicks;
    }

    /**
     * The gate renderer.
     *
     * @return the renderer
     */
    public ObstacleRenderer gates() {
        return gates;
    }

    /**
     * The renderer of one kind, for tests that draw a single obstacle.
     *
     * @param kind the kind
     * @return the renderer object ({@link ObstacleRenderer}, {@link GearRenderer},
     *     {@link PistonRenderer}, {@link WindZoneRenderer} or {@link LightningRenderer})
     */
    public Object rendererFor(ObstacleKind kind) {
        switch (kind) {
            case GEAR:
                return gears;
            case PISTON:
                return pistons;
            case WIND_ZONE:
                return winds;
            case LIGHTNING:
                return bolts;
            case PIPE_GATE:
            default:
                return gates;
        }
    }

    /**
     * Draws every obstacle of a layer.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param layer the live obstacles
     * @param palette the world palette
     * @param scrollPerTick the world scroll of this tick (the lightning warning is timed in it)
     * @param reduceFlashing {@code settings.reduceFlashing}
     * @param debugHitboxes {@code true} to outline the lethal hitboxes ({@code F3})
     */
    public void render(Graphics2D g, double alpha, ObstacleLayer layer, WorldPalette palette,
            double scrollPerTick, boolean reduceFlashing, boolean debugHitboxes) {
        List<Obstacle> obstacles = layer.obstacles();
        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle o = obstacles.get(i);
            if (o instanceof WindZone zone) {
                winds.render(g, alpha, zone, palette, animTicks);
            }
        }
        for (int i = 0; i < obstacles.size(); i++) {
            render(g, alpha, obstacles.get(i), palette, scrollPerTick, reduceFlashing);
        }
        if (debugHitboxes) {
            Stroke old = g.getStroke();
            g.setStroke(HITBOX_STROKE);
            g.setColor(HITBOX);
            for (int i = 0; i < obstacles.size(); i++) {
                for (Hitbox h : obstacles.get(i).hitboxesAt(alpha)) {
                    Aabb b = h.bounds();
                    rect.setFrame(b.x(), b.y(), b.w(), b.h());
                    g.draw(rect);
                }
            }
            g.setStroke(old);
        }
    }

    /**
     * Draws one obstacle through the renderer of its kind (wind zones included, for a caller
     * that draws a single obstacle).
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param o the obstacle
     * @param palette the world palette
     * @param scrollPerTick the world scroll of this tick
     * @param reduceFlashing {@code settings.reduceFlashing}
     */
    public void render(Graphics2D g, double alpha, Obstacle o, WorldPalette palette,
            double scrollPerTick, boolean reduceFlashing) {
        if (o instanceof PipeGate gate) {
            gates.drawGate(g, alpha, gate, palette);
        } else if (o instanceof Gear gear) {
            gears.render(g, alpha, gear, palette);
        } else if (o instanceof Piston piston) {
            pistons.render(g, alpha, piston, palette, animTicks);
        } else if (o instanceof LightningStrike bolt) {
            bolts.render(g, alpha, bolt, palette, scrollPerTick, reduceFlashing);
        }
    }

    /**
     * Draws one wind zone (the layer pass draws zones before everything else; a single-obstacle
     * caller asks for it explicitly).
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param zone the zone
     * @param palette the world palette
     */
    public void renderZone(Graphics2D g, double alpha, WindZone zone, WorldPalette palette) {
        winds.render(g, alpha, zone, palette, animTicks);
    }
}
