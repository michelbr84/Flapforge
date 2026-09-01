package io.github.michelbr84.flapforge.gameplay.harness;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.Simulation;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.BirdPhysics;
import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.Objects;
import java.util.Random;

/**
 * Deterministic skill-parameterised pilot (D21, §7).
 *
 * <p>Every tick the bot looks at the lethal obstacles still ahead of the bird's hitbox:
 *
 * <ul>
 *   <li>the <b>current</b> column — the obstacle whose column the bird is in, or will reach within
 *       one flap arc (27 ticks of scroll) — bounds a corridor the bird must stay in
 *       ({@link #corridorOf}: the gap of a gate, shrunk by the bird's hitbox);</li>
 *   <li>the <b>next</b> obstacle beyond it supplies the aim point: its safe band
 *       ({@link Obstacle#safeBandY}) plus {@link #AIM_OFFSET_PX} so the flap arc straddles the
 *       band centre.</li>
 * </ul>
 *
 * The aim is clamped into the corridor with room for a full flap rise, the bird is projected one
 * tick ahead with its own physics and the bot flaps when the projection falls below the aim —
 * unless the rise would hit the corridor ceiling while not flapping is still safe. Skill enters
 * as <b>reaction</b> (a new aim target is acknowledged {@code reactionTicks} late; the corridor
 * reflex is immediate) and <b>error</b> (each acknowledged obstacle is perceived shifted by a
 * uniform misjudgement in {@code [−errorPx, +errorPx]} drawn from the dedicated {@code bot}
 * stream, applied to its band and corridor alike). The pilot never sets {@code autoFlapHeld}
 * (D2); in {@code READY} it flaps once to start.
 */
public final class BotPilot implements Pilot {

    /** Downward offset from the safe band the bot aims at, so the hover band is centred. */
    public static final double AIM_OFFSET_PX = 20;
    /** Duration of a full flap arc in ticks; the scroll over it defines the current window. */
    public static final int ARC_TICKS = 27;
    /** Clearance kept between the hitbox and a corridor edge. */
    public static final double CORRIDOR_MARGIN_PX = 1;
    /** Distance the bot keeps between its flap arc and the corridor edges (its own caution). */
    public static final double SAFETY_MARGIN_PX = 8;
    /** Random stream name used by the bot. */
    public static final String STREAM = "bot";

    /**
     * Skill parameters.
     *
     * @param name the preset name
     * @param reactionTicks ticks before a new aim target is acknowledged
     * @param errorPx magnitude of the per-obstacle perception error
     */
    public record Preset(String name, int reactionTicks, double errorPx) {

        /** Slow and imprecise. */
        public static final Preset NOVICE = new Preset("novice", 12, 24);
        /** The reference player. */
        public static final Preset AVERAGE = new Preset("average", 8, 12);
        /** Fast and precise. */
        public static final Preset EXPERT = new Preset("expert", 2, 2);
        /** Instant and exact. */
        public static final Preset PERFECT = new Preset("perfect", 0, 0);

        /**
         * Validates the components.
         *
         * @param name the preset name
         * @param reactionTicks the reaction delay
         * @param errorPx the error magnitude
         */
        public Preset {
            Objects.requireNonNull(name, "name");
            if (reactionTicks < 0 || errorPx < 0) {
                throw new IllegalArgumentException("Preset values must not be negative");
            }
        }

        /**
         * Looks a preset up by name.
         *
         * @param name {@code novice}, {@code average}, {@code expert} or {@code perfect}
         * @return the preset
         * @throws IllegalArgumentException for an unknown name
         */
        public static Preset byName(String name) {
            switch (name) {
                case "novice":
                    return NOVICE;
                case "average":
                    return AVERAGE;
                case "expert":
                    return EXPERT;
                case "perfect":
                    return PERFECT;
                default:
                    throw new IllegalArgumentException("Unknown skill preset: " + name);
            }
        }
    }

    /** Vertical corridor the bird origin must stay in while crossing a column. */
    record Corridor(double ceilY, double floorY) {
    }

    private final Preset preset;
    private final Random rng;
    private Obstacle target;
    private double targetError;
    private Obstacle candidate;
    private int candidateSince;
    private Obstacle current;
    private double currentError;
    private double lastAim = Double.NaN;

    /**
     * Creates a bot.
     *
     * @param preset the skill preset
     * @param seed seed of the {@code bot} stream (independent of the run seed)
     */
    public BotPilot(Preset preset, long seed) {
        this.preset = Objects.requireNonNull(preset, "preset");
        this.rng = new RandomProvider(seed).stream(STREAM);
    }

    @Override
    public RunInput decide(Run run) {
        if (run.phase() == RunPhase.READY) {
            return RunInput.FLAP;
        }
        if (run.phase() != RunPhase.FLYING) {
            return RunInput.NONE;
        }
        Simulation sim = run.simulation();
        Bird bird = sim.bird();
        SimContext ctx = sim.context();
        double scale = sim.stats().resolve(StatId.HITBOX_SCALE);
        Aabb box = bird.hitbox(scale);
        double windowEnd = box.maxX() + ARC_TICKS * ctx.scrollPerTick();

        Obstacle inWindow = null;
        Obstacle beyond = null;
        for (Obstacle o : sim.obstacles().obstacles()) {
            if (!o.lethal() || o.scoreLineX() <= box.x()) {
                continue;
            }
            if (o.x() < windowEnd) {
                inWindow = o;
            } else {
                beyond = o;
                break;
            }
        }
        updateCurrent(inWindow);
        acknowledge(beyond != null ? beyond : inWindow, run.tick());

        double gravity = sim.stats().resolve(StatId.GRAVITY);
        double maxFall = sim.stats().resolve(StatId.MAX_FALL_SPEED);
        Arc arc = flapArc(sim.stats().resolve(StatId.FLAP_VELOCITY), gravity);
        double rise = arc.rise();
        double aim = target == null ? Playfield.BIRD_START_Y
                : target.safeBandY(bird.x()) + targetError + AIM_OFFSET_PX;

        Corridor corridor = current == null ? null
                : corridorOf(current, bird.hitboxSpec(), scale,
                        ctx.oscillationPerTick() * arc.riseTicks());
        double ceil = Double.NEGATIVE_INFINITY;
        double floor = Double.POSITIVE_INFINITY;
        if (corridor != null) {
            ceil = corridor.ceilY() + currentError;
            floor = corridor.floorY() + currentError;
            double lowestSafeAim = ceil + rise + SAFETY_MARGIN_PX;
            double highestSafeAim = floor - SAFETY_MARGIN_PX;
            if (lowestSafeAim <= highestSafeAim) {
                aim = Math.min(Math.max(aim, lowestSafeAim), highestSafeAim);
            } else {
                aim = (ceil + rise + floor) / 2;
            }
        }

        lastAim = aim;
        double yNext = BirdPhysics.projectY(bird.y(), bird.vy(), 1, gravity, maxFall);
        boolean flap = yNext > aim;
        if (flap && bird.y() - rise < ceil + SAFETY_MARGIN_PX && yNext <= floor) {
            flap = false;
        }
        return flap ? RunInput.FLAP : RunInput.NONE;
    }

    private void updateCurrent(Obstacle inWindow) {
        if (inWindow == current) {
            return;
        }
        current = inWindow;
        if (inWindow == null) {
            currentError = 0;
        } else if (inWindow == target) {
            currentError = targetError;
        } else {
            currentError = drawError();
        }
    }

    private void acknowledge(Obstacle wanted, int tick) {
        if (wanted == target) {
            candidate = null;
            return;
        }
        if (wanted != candidate) {
            candidate = wanted;
            candidateSince = tick;
        }
        if (tick - candidateSince >= preset.reactionTicks()) {
            target = wanted;
            candidate = null;
            targetError = wanted == current ? currentError : drawError();
        }
    }

    private double drawError() {
        if (preset.errorPx() == 0) {
            return 0;
        }
        return (rng.nextDouble() * 2 - 1) * preset.errorPx();
    }

    /**
     * Corridor of bird-origin y values that keep the hitbox inside an obstacle's passable band
     * while crossing its column; {@code null} when the kind has no corridor oracle yet. A moving
     * gate's corridor is shrunk by the distance it can travel during a flap rise.
     *
     * @param obstacle the obstacle
     * @param spec the bird hitbox
     * @param scale the {@code HITBOX_SCALE} factor
     * @param motionMargin extra clearance for moving obstacles (px travelled during a rise)
     * @return the corridor, or {@code null}
     */
    static Corridor corridorOf(Obstacle obstacle, HitboxSpec spec, double scale,
            double motionMargin) {
        if (!(obstacle instanceof PipeGate gate)) {
            return null;
        }
        double margin = CORRIDOR_MARGIN_PX + (gate.isMoving() ? motionMargin : 0);
        double halfH = spec.h() * scale / 2;
        double centerOffset = spec.centerOffsetY();
        double ceilY = gate.gapTopY() - (centerOffset - halfH) + margin;
        double floorY = gate.gapBottomY() - (centerOffset + halfH) - margin;
        return new Corridor(ceilY, floorY);
    }

    /**
     * Shape of a flap: height gained before the bird falls again and the ticks it takes
     * (42.25 px in 13 ticks for the classic bird; the 14th tick is the apex).
     *
     * @param rise the height gained in px
     * @param riseTicks ticks from the flap to the apex
     */
    record Arc(double rise, int riseTicks) {
    }

    /**
     * Computes the flap arc for the given physics.
     *
     * @param flapVelocity the {@code FLAP_VELOCITY}
     * @param gravity the {@code GRAVITY}
     * @return the arc
     */
    static Arc flapArc(double flapVelocity, double gravity) {
        double vy = -flapVelocity;
        double rise = 0;
        int ticks = 0;
        while (true) {
            vy += gravity / Playfield.TICK_RATE;
            if (vy >= 0) {
                return new Arc(rise, ticks + 1);
            }
            rise -= vy / Playfield.TICK_RATE;
            ticks++;
        }
    }

    /**
     * The skill preset.
     *
     * @return the preset
     */
    public Preset preset() {
        return preset;
    }

    /**
     * The obstacle currently aimed at.
     *
     * @return the target, or {@code null}
     */
    public Obstacle target() {
        return target;
    }

    /**
     * The obstacle currently bounding the corridor.
     *
     * @return the current obstacle, or {@code null}
     */
    public Obstacle current() {
        return current;
    }

    /**
     * The perception error applied to the aim target.
     *
     * @return the error in px
     */
    public double targetError() {
        return targetError;
    }

    /**
     * The aim point used by the last decision (debug overlays, attract mode).
     *
     * @return the y aimed at, or {@code NaN} before the first FLYING decision
     */
    public double lastAim() {
        return lastAim;
    }
}
