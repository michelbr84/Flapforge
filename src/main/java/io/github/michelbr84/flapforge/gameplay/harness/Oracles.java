package io.github.michelbr84.flapforge.gameplay.harness;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.BirdPhysics;
import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.LightningStrike;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.Piston;
import io.github.michelbr84.flapforge.gameplay.obstacle.Side;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import java.util.List;

/**
 * Per-kind hazard predictors of the bot (D21, §7). Given an obstacle and the bird, each oracle
 * answers the same question — which bird-origin {@code y} values keep the hitbox in the passable
 * band while the bird crosses the column — as a {@link Corridor}, evaluated <em>at the crossing
 * tick</em> rather than from the obstacle's current state:
 *
 * <ul>
 *   <li>gate: the gap, shrunk by the hitbox and by a moving gate's travel during a flap rise
 *       (the M1 rule, unchanged);</li>
 *   <li>piston: the free side beyond the head's largest extension over the ticks the column
 *       overlaps the bird, predicted from the phase clock — a head that is retracted now and out
 *       at the crossing is avoided;</li>
 *   <li>lightning: the unlit side, from the moment the column is in the window and until the
 *       bolt is spent — the bot never sits in the bolt band waiting for the strike;</li>
 *   <li>gear: both free sides of the sweep, each kept {@code 16 + margin} clear of the chord the
 *       circle cuts through the bird's x range at every predicted rail position over the
 *       crossing ({@link #gearCorridors}); the pilot picks the side that leads to the next
 *       column's band and is reachable from where the bird is, and only falls back to the
 *       larger side when nothing decides it (M7 fairness: a gear whose larger side is a dead end
 *       against the column after it is cleared on its other side).</li>
 * </ul>
 *
 * <p>{@link #projectY} integrates the bird's own physics through the wind zones ahead, so the
 * projection bends the way the bird will. Production never imports this package: the oracles
 * only call the accessors the obstacles expose.
 */
public final class Oracles {

    /** Clearance kept between the bird hitbox and a gear's circle edge (D21). */
    public static final double GEAR_CLEARANCE_PX = 16;

    /**
     * Vertical corridor the bird origin must stay in while crossing a column.
     *
     * @param ceilY the highest allowed bird-origin y
     * @param floorY the lowest allowed bird-origin y
     */
    public record Corridor(double ceilY, double floorY) {
    }

    /**
     * The ticks during which a column overlaps the bird's hitbox, counted from now.
     *
     * @param enterTick the first tick of overlap (0 when already overlapping)
     * @param exitTick the last tick of overlap
     */
    public record Window(int enterTick, int exitTick) {
    }

    private Oracles() {
    }

    /**
     * Ticks from now during which an obstacle's column overlaps the bird box at a constant
     * scroll.
     *
     * @param obstacle the obstacle
     * @param box the bird hitbox
     * @param scrollPerTick the world scroll per tick
     * @return the window ({@code (0, 0)} when the world is still)
     */
    public static Window crossingWindow(Obstacle obstacle, Aabb box, double scrollPerTick) {
        if (scrollPerTick <= 0) {
            return new Window(0, 0);
        }
        double enter = (obstacle.x() - box.maxX()) / scrollPerTick;
        int enterTick = Math.max(0, (int) Math.floor(enter) + 1);
        double exit = (obstacle.x() + obstacle.width() - box.x()) / scrollPerTick;
        int exitTick = Math.max(enterTick, (int) Math.ceil(exit) - 1);
        return new Window(enterTick, exitTick);
    }

    /**
     * The corridor of any lethal obstacle, or {@code null} for a kind without one.
     *
     * @param obstacle the obstacle
     * @param bird the bird
     * @param scale the {@code HITBOX_SCALE} factor
     * @param gateMotionMargin extra clearance for a moving gate (px travelled during a rise)
     * @param scrollPerTick the world scroll per tick
     * @param worldDt the world clock scale
     * @return the corridor, or {@code null}
     */
    public static Corridor corridorOf(Obstacle obstacle, Bird bird, double scale,
            double gateMotionMargin, double scrollPerTick, double worldDt) {
        HitboxSpec spec = bird.hitboxSpec();
        if (obstacle instanceof PipeGate gate) {
            return gateCorridor(gate, spec, scale, gateMotionMargin);
        }
        if (obstacle instanceof Piston piston) {
            Window window = crossingWindow(piston, bird.hitbox(scale), scrollPerTick);
            return pistonCorridor(piston, window, worldDt, spec, scale);
        }
        if (obstacle instanceof LightningStrike bolt) {
            return lightningCorridor(bolt, spec, scale);
        }
        if (obstacle instanceof Gear gear) {
            Aabb box = bird.hitbox(scale);
            Window window = crossingWindow(gear, box, scrollPerTick);
            return gearCorridor(gear, window, gear.railSpeed() * worldDt / Playfield.TICK_RATE,
                    spec, scale, box, scrollPerTick);
        }
        return null;
    }

    /**
     * The gap of a gate, shrunk by the hitbox; a moving gate's corridor is shrunk further by the
     * distance it can travel during a flap rise (M1).
     *
     * @param gate the gate
     * @param spec the bird hitbox
     * @param scale the {@code HITBOX_SCALE} factor
     * @param motionMargin extra clearance for moving gates
     * @return the corridor
     */
    public static Corridor gateCorridor(PipeGate gate, HitboxSpec spec, double scale,
            double motionMargin) {
        double margin = BotPilot.CORRIDOR_MARGIN_PX + (gate.isMoving() ? motionMargin : 0);
        double halfH = spec.h() * scale / 2;
        double centerOffset = spec.centerOffsetY();
        double ceilY = gate.gapTopY() - (centerOffset - halfH) + margin;
        double floorY = gate.gapBottomY() - (centerOffset + halfH) - margin;
        return new Corridor(ceilY, floorY);
    }

    /**
     * The free side of a piston beyond the largest extension its head reaches over the crossing
     * window, predicted from the phase clock at the current world clock scale.
     *
     * @param piston the piston
     * @param window the crossing window
     * @param worldDt the world clock scale
     * @param spec the bird hitbox
     * @param scale the {@code HITBOX_SCALE} factor
     * @return the corridor
     */
    public static Corridor pistonCorridor(Piston piston, Window window, double worldDt,
            HitboxSpec spec, double scale) {
        double maxExtension = 0;
        for (int k = window.enterTick(); k <= window.exitTick(); k++) {
            maxExtension = Math.max(maxExtension,
                    piston.extensionAtClock(piston.clock() + k * worldDt));
        }
        return sideCorridor(piston.side(), maxExtension, spec, scale);
    }

    /**
     * The unlit side of a bolt, whatever its state until it is spent.
     *
     * @param bolt the lightning column
     * @param spec the bird hitbox
     * @param scale the {@code HITBOX_SCALE} factor
     * @return the corridor, or {@code null} once the bolt is spent
     */
    public static Corridor lightningCorridor(LightningStrike bolt, HitboxSpec spec,
            double scale) {
        if (bolt.isSpent()) {
            return null;
        }
        return sideCorridor(bolt.side(), bolt.boltHeight(), spec, scale);
    }

    /**
     * The larger free side of a gear's sweep, clear of the circle at every rail position the
     * gear takes over the crossing window.
     *
     * @param gear the gear
     * @param window the crossing window
     * @param railPerTick the rail travel per tick
     * @param spec the bird hitbox
     * @param scale the {@code HITBOX_SCALE} factor
     * @return the corridor
     */
    public static Corridor gearCorridor(Gear gear, Window window, double railPerTick,
            HitboxSpec spec, double scale) {
        double minCy = Double.POSITIVE_INFINITY;
        double maxCy = Double.NEGATIVE_INFINITY;
        for (int k = window.enterTick(); k <= window.exitTick(); k++) {
            double cy = gear.predictedCenterY(k, railPerTick);
            minCy = Math.min(minCy, cy);
            maxCy = Math.max(maxCy, cy);
        }
        double halfH = spec.h() * scale / 2;
        double centerOffset = spec.centerOffsetY();
        double margin = BotPilot.CORRIDOR_MARGIN_PX;
        double clearance = gear.radius() + GEAR_CLEARANCE_PX + margin;
        if (gear.safeBandAbove()) {
            double ceilY = -(centerOffset - halfH) + margin;
            double floorY = (minCy - clearance) - (centerOffset + halfH);
            return new Corridor(ceilY, floorY);
        }
        double ceilY = (maxCy + clearance) - (centerOffset - halfH);
        double floorY = Playfield.GROUND_DEATH_Y - margin;
        return new Corridor(ceilY, floorY);
    }

    /**
     * The two corridors of a gear, above and below the circle's footprint over the crossing.
     *
     * @param above the corridor above the sweep (may be empty: {@code ceilY > floorY})
     * @param below the corridor below the sweep (may be empty)
     */
    public record GearCorridors(Corridor above, Corridor below) {

        /**
         * The corridor on the gear's larger free side (the E32.c band).
         *
         * @param gear the gear
         * @return above or below
         */
        public Corridor larger(Gear gear) {
            return gear.safeBandAbove() ? above : below;
        }
    }

    /**
     * Whether a corridor leaves the bird origin any room at all.
     *
     * @param corridor the corridor
     * @return {@code true} when {@code ceilY ≤ floorY}
     */
    public static boolean fits(Corridor corridor) {
        return corridor != null && corridor.ceilY() <= corridor.floorY();
    }

    /**
     * Distance from a y to a corridor: 0 inside it, else the gap to its nearest edge.
     *
     * @param y the bird-origin y
     * @param corridor the corridor
     * @return the distance in px
     */
    public static double distance(double y, Corridor corridor) {
        return Math.max(0, Math.max(corridor.ceilY() - y, y - corridor.floorY()));
    }

    /**
     * The band a bird should aim for to cross an obstacle, given where it is coming from: the
     * kind's safe band (E32.c) for every kind but a gear, and for a gear the centre of the free
     * side — above or below the whole sweep — that is nearer {@code referenceY}, among the sides
     * the bird can fly through: {@code 16 px} clear of the sweep and at least {@code roomPx}
     * tall, which is the box plus a flap arc plus the pilot's margins — a band the box fits in
     * but a flap does not is a trap under a gear near the ground. The larger side is the
     * fallback when neither fits or both are equally near, which is exactly
     * {@link Gear#safeBandY}.
     *
     * @param obstacle the obstacle
     * @param referenceY the y the bird is coming from (its own y, or the band of the column
     *     after this one)
     * @param spec the bird hitbox
     * @param scale the {@code HITBOX_SCALE} factor
     * @param roomPx the smallest band the bird can fly in (box height, flap rise and margins)
     * @return the y to aim at
     */
    public static double bandOf(Obstacle obstacle, double referenceY, HitboxSpec spec,
            double scale, double roomPx) {
        if (!(obstacle instanceof Gear gear)) {
            return obstacle.safeBandY(Playfield.BIRD_X);
        }
        double need = Math.max(spec.h() * scale + 2 * BotPilot.CORRIDOR_MARGIN_PX, roomPx);
        double aboveLo = 0;
        double aboveHi = gear.sweepTopY() - GEAR_CLEARANCE_PX;
        double belowLo = gear.sweepBottomY() + GEAR_CLEARANCE_PX;
        // The ground rule ends the band where the bird origin dies, not at the ground line.
        double belowHi = Playfield.GROUND_DEATH_Y + spec.centerOffsetY();
        boolean aboveFits = aboveHi - aboveLo >= need;
        boolean belowFits = belowHi - belowLo >= need;
        if (aboveFits && belowFits) {
            double toAbove = Math.max(0, Math.max(aboveLo - referenceY, referenceY - aboveHi));
            double toBelow = Math.max(0, Math.max(belowLo - referenceY, referenceY - belowHi));
            if (toAbove == toBelow) {
                return gear.safeBandY(Playfield.BIRD_X);
            }
            return toAbove < toBelow ? (aboveLo + aboveHi) / 2 : (belowLo + belowHi) / 2;
        }
        if (aboveFits) {
            return (aboveLo + aboveHi) / 2;
        }
        if (belowFits) {
            return (belowLo + belowHi) / 2;
        }
        return gear.safeBandY(Playfield.BIRD_X);
    }

    /**
     * Both corridors of a gear from the circle's actual footprint over the crossing: on each
     * tick of the window the circle is placed where the scroll will have it and only the chord
     * it cuts through the bird's x range is excluded — the full diameter while the centre is
     * over the box, a shrinking chord as the circle leaves it — and the clearance is kept from
     * that chord above and below.
     *
     * @param gear the gear
     * @param window the crossing window
     * @param railPerTick the rail travel per tick
     * @param spec the bird hitbox
     * @param scale the {@code HITBOX_SCALE} factor
     * @param box the bird hitbox now
     * @param scrollPerTick the world scroll per tick
     * @return the corridors, or {@code null} when the circle never touches the box's x range
     */
    public static GearCorridors gearCorridors(Gear gear, Window window, double railPerTick,
            HitboxSpec spec, double scale, Aabb box, double scrollPerTick) {
        double radius = gear.radius();
        double top = Double.POSITIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (int k = window.enterTick(); k <= window.exitTick(); k++) {
            double cx = gear.centerX() - k * scrollPerTick;
            double d = cx < box.x() ? box.x() - cx : (cx > box.maxX() ? cx - box.maxX() : 0);
            if (d >= radius) {
                continue;
            }
            double half = Math.sqrt(radius * radius - d * d);
            double cy = gear.predictedCenterY(k, railPerTick);
            top = Math.min(top, cy - half);
            bottom = Math.max(bottom, cy + half);
        }
        if (top == Double.POSITIVE_INFINITY) {
            return null;
        }
        double halfH = spec.h() * scale / 2;
        double centerOffset = spec.centerOffsetY();
        double margin = BotPilot.CORRIDOR_MARGIN_PX;
        double clearance = GEAR_CLEARANCE_PX + margin;
        Corridor above = new Corridor(-(centerOffset - halfH) + margin,
                (top - clearance) - (centerOffset + halfH));
        Corridor below = new Corridor((bottom + clearance) - (centerOffset - halfH),
                Playfield.GROUND_DEATH_Y - margin);
        return new GearCorridors(above, below);
    }

    /**
     * The gear corridor from the circle's actual footprint over the crossing (M7 refinement of
     * {@link #gearCorridor(Gear, Window, double, HitboxSpec, double)}): on each tick of the
     * window the circle is placed where the scroll will have it and only the chord it cuts
     * through the bird's x range is excluded — the full diameter while the centre is over the
     * box, a shrinking chord as the circle leaves it. The clearance is kept from that chord, so
     * a fully overlapping tick gives exactly the conservative corridor and the last ticks of a
     * gear that is leaving free the band its rim no longer reaches. Without this a gear 160 px
     * ahead of another on the opposite side is unwinnable: the bot has to hold the first gear's
     * band until the whole circle has cleared the box, and 48 px of scroll are not enough to
     * cross to the other band.
     *
     * @param gear the gear
     * @param window the crossing window
     * @param railPerTick the rail travel per tick
     * @param spec the bird hitbox
     * @param scale the {@code HITBOX_SCALE} factor
     * @param box the bird hitbox now
     * @param scrollPerTick the world scroll per tick
     * @return the corridor, or {@code null} when the circle never touches the box's x range
     */
    public static Corridor gearCorridor(Gear gear, Window window, double railPerTick,
            HitboxSpec spec, double scale, Aabb box, double scrollPerTick) {
        GearCorridors both = gearCorridors(gear, window, railPerTick, spec, scale, box,
                scrollPerTick);
        return both == null ? null : both.larger(gear);
    }

    private static Corridor sideCorridor(Side side, double extent, HitboxSpec spec, double scale) {
        double halfH = spec.h() * scale / 2;
        double centerOffset = spec.centerOffsetY();
        double margin = BotPilot.CORRIDOR_MARGIN_PX;
        if (side == Side.TOP) {
            double ceilY = extent - (centerOffset - halfH) + margin;
            double floorY = Playfield.GROUND_DEATH_Y - margin;
            return new Corridor(ceilY, floorY);
        }
        double ceilY = -(centerOffset - halfH) + margin;
        double floorY = (Playfield.GROUND_Y - extent) - (centerOffset + halfH) - margin;
        return new Corridor(ceilY, floorY);
    }

    /**
     * Projects a free fall through the wind zones ahead, with the bird's own integrator: on each
     * tick the zone's box (scrolled by {@code k × scrollPerTick}) is tested against the bird's
     * unscaled hitbox at the projected y, exactly as the simulation samples the wind at the start
     * of a tick, and the zone's {@code accelY} joins gravity for that tick. Without zones this is
     * {@link BirdPhysics#projectY}, bit for bit.
     *
     * @param y the starting y
     * @param vy the starting velocity
     * @param ticks how many ticks to project
     * @param gravity the {@code GRAVITY}
     * @param maxFallSpeed the {@code MAX_FALL_SPEED}
     * @param zones the wind zones ahead of or around the bird (may be empty)
     * @param scrollPerTick the world scroll per tick
     * @param spec the bird hitbox
     * @return the projected y
     */
    public static double projectY(double y, double vy, int ticks, double gravity,
            double maxFallSpeed, List<WindZone> zones, double scrollPerTick, HitboxSpec spec) {
        if (zones.isEmpty()) {
            return BirdPhysics.projectY(y, vy, ticks, gravity, maxFallSpeed);
        }
        double py = y;
        double pv = vy;
        for (int k = 0; k < ticks; k++) {
            double accel = gravity;
            Aabb box = spec.at(Playfield.BIRD_X, py, 1.0);
            for (WindZone zone : zones) {
                if (zone.boxAt(zone.x() - k * scrollPerTick).intersects(box)) {
                    accel += zone.accelY();
                }
            }
            pv += accel / Playfield.TICK_RATE;
            if (pv > maxFallSpeed) {
                pv = maxFallSpeed;
            }
            py += pv / Playfield.TICK_RATE;
        }
        return py;
    }
}
