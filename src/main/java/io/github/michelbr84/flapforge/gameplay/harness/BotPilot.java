package io.github.michelbr84.flapforge.gameplay.harness;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.Simulation;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.BirdPhysics;
import io.github.michelbr84.flapforge.gameplay.bird.HitboxSpec;
import io.github.michelbr84.flapforge.gameplay.obstacle.Gear;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.WindZone;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.ArrayList;
import java.util.List;
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
 *       ({@link Oracles#corridorOf}: the gap of a gate shrunk by the bird's hitbox; the free
 *       side of a piston or a bolt and the clear side of a gear, each predicted at the crossing
 *       tick by the per-kind oracle, M7);</li>
 *   <li>the <b>next</b> obstacle beyond it supplies the aim point: its safe band
 *       ({@link Obstacle#safeBandY}) plus {@link #AIM_OFFSET_PX} so the flap arc straddles the
 *       band centre. A gear has two bands, above and below its sweep, and the one aimed at is
 *       the one nearer the band of the column after it ({@link Oracles#bandOf}); when the gear
 *       is the current column its corridor is the side that leads to the aim and can still be
 *       reached from where the bird is, the larger side only as a tie-break (M7).</li>
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
 *
 * <p>Drafts (D11, M6): a breather is flown like any other stretch of sky, and an open draft is
 * answered by taking the first card ({@link #FIRST_CARD}). That is the baseline the balancing
 * report is written against — and without it a headless run would sit frozen on the offer.
 *
 * <p>Abilities (D21): when an active ability is equipped and ready, the bot projects its own
 * flight ten ticks ahead with its own flap rule and activates the ability if that projection
 * still leaves the corridor or reaches the ground. A loadout of passives changes nothing here —
 * they need no input — and a run with no ability never enters the branch at all.
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
    /** The card index the bot takes in a draft (D21: always the first). */
    public static final int FIRST_CARD = 0;
    /** How far ahead the bot looks before spending its active ability (D21). */
    public static final int ABILITY_LOOKAHEAD_TICKS = 10;

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

    private final Preset preset;
    private final Random rng;
    private Obstacle target;
    private double targetError;
    private Obstacle candidate;
    private int candidateSince;
    private Obstacle current;
    private double currentError;
    private Obstacle after;
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
        if (run.phase() == RunPhase.CHOOSING_MODIFIER) {
            // D21: the bot always takes the first card. It is the honest baseline for the
            // balancing report — it measures what the pool offers, not what a clever player
            // would do with it — and it is what stops a headless run from freezing on a draft.
            return RunInput.choose(FIRST_CARD);
        }
        if (run.phase() != RunPhase.FLYING && run.phase() != RunPhase.BREATHER) {
            // A breather is ordinary flight with the next obstacle pushed further out (D11), so
            // the bot has to keep flying through it; the frozen phases need no input.
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
        Obstacle third = null;
        List<WindZone> winds = null;
        for (Obstacle o : sim.obstacles().obstacles()) {
            if (o instanceof WindZone zone && zone.x() + zone.width() > box.x()) {
                // Sky that bends the flight (M7): kept for the projection, never a target.
                if (winds == null) {
                    winds = new ArrayList<>(2);
                }
                winds.add(zone);
            }
            if (!o.lethal() || o.scoreLineX() <= box.x()) {
                continue;
            }
            if (inWindow == null && o.x() < windowEnd) {
                // The nearest column not yet cleared bounds the corridor (M7 fix): with the
                // M1 gates 160 px apart at most one column was ever inside the window, so
                // "the last one inside it" and "the first" were the same obstacle. A pattern
                // step 130 px behind a gate, or a 112 px gear followed by anything, puts two
                // there — and the corridor of the one the bird is still inside must not be
                // dropped for the one after it.
                inWindow = o;
            } else if (beyond == null) {
                beyond = o;
            } else {
                // The column after the target only matters when the target is a gear: it says
                // which of the gear's two sides leads on (M7).
                third = o;
                break;
            }
        }
        updateCurrent(inWindow);
        acknowledge(beyond != null ? beyond : inWindow, run.tick());
        after = beyond != null ? third : beyond;

        // The ambient wind of a world (M7) is a permanent acceleration the bird's own integrator
        // adds to gravity every tick, so the projection adds it too — the bot models the physics,
        // not the still air of Green Fields; every shipped world has windY 0, so this is 0 there.
        double gravity = sim.stats().resolve(StatId.GRAVITY)
                + sim.worldEffects().ambient().windY();
        double maxFall = sim.stats().resolve(StatId.MAX_FALL_SPEED);
        // The flap arc under the wind the bird is in right now (D21: wind-adjusted trajectory).
        // The simulation samples the wind at the start of every tick into the bird's
        // accumulator, so before this tick runs the accumulator still holds the previous tick's
        // sample — the ambient wind plus every zone the bird was inside — which is the best
        // estimate of the air the next flap rises through. Without it an updraft of −600 px/s²
        // turns a 42 px rise into 68 px and the bird flies into the pipe above the gap.
        Arc arc = flapArc(sim.stats().resolve(StatId.FLAP_VELOCITY),
                sim.stats().resolve(StatId.GRAVITY) + bird.windAccelY());
        double rise = arc.rise();
        HitboxSpec spec = bird.hitboxSpec();
        double flapVelocity = sim.stats().resolve(StatId.FLAP_VELOCITY);
        // The smallest band the bot can hold: the box, a full flap arc and its own margins.
        double room = spec.h() * scale + rise + 2 * SAFETY_MARGIN_PX;
        double aim = target == null ? Playfield.BIRD_START_Y
                : Oracles.bandOf(target, referenceFor(target, bird, spec, scale, room), spec,
                        scale, room) + targetError + AIM_OFFSET_PX;

        Oracles.Corridor corridor = current == null ? null
                : corridorFor(current, bird, scale, ctx, arc, aim, gravity, maxFall,
                        flapVelocity);
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
        double yNext = winds == null
                ? BirdPhysics.projectY(bird.y(), bird.vy(), 1, gravity, maxFall)
                : Oracles.projectY(bird.y(), bird.vy(), 1, gravity, maxFall, winds,
                        ctx.scrollPerTick(), bird.hitboxSpec());
        boolean flap = yNext > aim;
        if (flap && bird.y() - rise < ceil + SAFETY_MARGIN_PX && yNext <= floor) {
            flap = false;
        }
        if (sim.abilities().hasReadyActive() && predictsLethalHit(bird, aim, ceil, floor, gravity,
                maxFall, flapVelocity)) {
            return new RunInput(flap, true, RunInput.NO_CHOICE, false);
        }
        return flap ? RunInput.FLAP : RunInput.NONE;
    }

    /**
     * Where the bird is coming from when it picks a gear's side as its aim: the band of the
     * column after the target when there is one (so the side chosen leads on), else the bird's
     * own y. Only a gear target reads it.
     */
    private double referenceFor(Obstacle wanted, Bird bird, HitboxSpec spec, double scale,
            double room) {
        if (!(wanted instanceof Gear)) {
            return bird.y();
        }
        return after == null ? bird.y() : Oracles.bandOf(after, bird.y(), spec, scale, room);
    }

    /**
     * The corridor of the current column: the per-kind oracle for every kind but a gear, and
     * for a gear the side that leads to the aim among the sides a flap arc fits in and the bird
     * can still reach before the crossing — falling to the corridor the aim is nearest to, then
     * to the larger side, when nothing decides it.
     */
    private static Oracles.Corridor corridorFor(Obstacle current, Bird bird, double scale,
            SimContext ctx, Arc arc, double aim, double gravity, double maxFall,
            double flapVelocity) {
        if (!(current instanceof Gear gear)) {
            return Oracles.corridorOf(current, bird, scale,
                    ctx.oscillationPerTick() * arc.riseTicks(), ctx.scrollPerTick(),
                    ctx.worldDt());
        }
        Aabb box = bird.hitbox(scale);
        Oracles.Window window = Oracles.crossingWindow(gear, box, ctx.scrollPerTick());
        Oracles.GearCorridors both = Oracles.gearCorridors(gear, window,
                gear.railSpeed() * ctx.worldDt() / Playfield.TICK_RATE, bird.hitboxSpec(), scale,
                box, ctx.scrollPerTick());
        if (both == null) {
            return null;
        }
        double room = arc.rise() + 2 * SAFETY_MARGIN_PX;
        Oracles.Corridor above = holdsArc(both.above(), room) ? both.above() : null;
        Oracles.Corridor below = holdsArc(both.below(), room) ? both.below() : null;
        if (above == null || below == null) {
            return above != null ? above : (below != null ? below : both.larger(gear));
        }
        boolean aboveReachable = reachable(above, bird, window.enterTick(), gravity, maxFall,
                flapVelocity);
        boolean belowReachable = reachable(below, bird, window.enterTick(), gravity, maxFall,
                flapVelocity);
        if (aboveReachable != belowReachable) {
            return aboveReachable ? above : below;
        }
        double toAbove = Oracles.distance(aim, above);
        double toBelow = Oracles.distance(aim, below);
        if (toAbove == toBelow) {
            return both.larger(gear);
        }
        return toAbove < toBelow ? above : below;
    }

    /** Whether a corridor leaves the bird origin a flap arc plus the margins to hold. */
    private static boolean holdsArc(Oracles.Corridor corridor, double room) {
        return Oracles.fits(corridor) && corridor.floorY() - corridor.ceilY() >= room;
    }

    /**
     * Whether the bird can be inside a corridor by the time the column reaches it: a free fall
     * over the ticks left when it is above the corridor, a flap on every tick when it is below.
     */
    private static boolean reachable(Oracles.Corridor corridor, Bird bird, int ticks,
            double gravity, double maxFall, double flapVelocity) {
        double y = bird.y();
        if (y < corridor.ceilY()) {
            double fall = BirdPhysics.projectY(y, bird.vy(), ticks, gravity, maxFall) - y;
            return fall >= corridor.ceilY() - y;
        }
        if (y > corridor.floorY()) {
            double climbPerTick = (flapVelocity - gravity / Playfield.TICK_RATE)
                    / Playfield.TICK_RATE;
            return climbPerTick * ticks >= y - corridor.floorY();
        }
        return true;
    }

    /**
     * D21's ability rule: the bot spends its active ability when a lethal hit is predicted within
     * {@link #ABILITY_LOOKAHEAD_TICKS} ticks.
     *
     * <p>"Predicted" means predicted <em>despite flying well</em>: the projection replays the
     * bot's own flap rule tick by tick, so a bird that will simply flap out of the dip does not
     * burn a cooldown on it, and the ability is spent when the corridor floor or the ground is
     * unreachable even with the flaps the bot intends. A dash that holds the line, a double flap
     * that cancels the fall and a slow-time window all buy exactly that situation.
     *
     * <p>That claim is measured, not assumed: over 200 average-preset seeds on the shipped
     * content, every ability the rule can spend is at or above the ability-free baseline of 79.57
     * mean gates (dash 87.14, double flap 83.44, invulnerability 82.15, score multiplier 79.57,
     * slow time 74.47 — the one below, because halving {@code TIME_SCALE} passes fewer gates
     * inside a fixed tick budget). {@code AbilityBotRunTest} pins the comparison, so an ability
     * that makes the bot worse fails the build instead of quietly making the M5 evidence
     * meaningless.
     *
     * <p>Only a hit from <em>above</em> counts — the bird sinking into the floor of the corridor
     * or into the ground. A bird climbing into a ceiling is not helped by any of the eight
     * abilities (the two movement ones push it further up), so spending a cooldown there would
     * make the bot worse than no bot, and the per-ability harness runs would measure the rule
     * rather than the ability.
     *
     * <p>It is asked only when an active ability is equipped and off cooldown, so a run with no
     * ability — the configuration the published determinism hash uses — takes the same branches,
     * draws from the {@code bot} stream at the same points and produces the same inputs it did
     * before this rule existed.
     *
     * @param bird the bird
     * @param aim the y the bot is aiming at
     * @param ceilY the top of the corridor, {@code -inf} when there is none
     * @param floorY the bottom of the corridor, {@code +inf} when there is none
     * @param gravity the resolved {@code GRAVITY}
     * @param maxFall the resolved {@code MAX_FALL_SPEED}
     * @param flapVelocity the resolved {@code FLAP_VELOCITY}
     * @return {@code true} when the projection sinks past the corridor floor or the ground line
     */
    static boolean predictsLethalHit(Bird bird, double aim, double ceilY, double floorY,
            double gravity, double maxFall, double flapVelocity) {
        double y = bird.y();
        double v = bird.vy();
        for (int t = 0; t < ABILITY_LOOKAHEAD_TICKS; t++) {
            if (BirdPhysics.projectY(y, v, 1, gravity, maxFall) > aim
                    && y > Playfield.CEILING_FLAP_Y) {
                v = -flapVelocity;
            }
            v += gravity / Playfield.TICK_RATE;
            if (v > maxFall) {
                v = maxFall;
            }
            y += v / Playfield.TICK_RATE;
            if (y >= Playfield.GROUND_DEATH_Y || y > floorY) {
                return true;
            }
            if (y < ceilY) {
                return false;
            }
        }
        return false;
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
        if (gravity <= 0) {
            // An updraft stronger than gravity: the bird never comes down on its own, so the
            // arc is the rise over a full flap window, which is all the corridor clamp needs.
            return new Arc(flapVelocity * ARC_TICKS / Playfield.TICK_RATE, ARC_TICKS);
        }
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
     * The column after the aim target, read when the target is a gear (M7).
     *
     * @return the obstacle, or {@code null}
     */
    public Obstacle after() {
        return after;
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
