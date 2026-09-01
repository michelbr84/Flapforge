package io.github.michelbr84.flapforge.gameplay;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.bird.BirdPhysics;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionReport;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionSystem;
import io.github.michelbr84.flapforge.gameplay.difficulty.DifficultyCurve;
import io.github.michelbr84.flapforge.gameplay.difficulty.DifficultyState;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleSpawner;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.RampEffect;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The pure gameplay model of one run (D5, D6, D7): bird, obstacles, spawner, collision, stats and
 * difficulty. {@link #tick(SimInput)} advances the world by one 60 Hz tick and returns the facts
 * it produced; {@link #stateHash()} folds the visible state for determinism checks.
 *
 * <p>Tick order: flap → bird integration → world scroll and obstacle phases (scaled by
 * {@code TIME_SCALE}) → obstacle effects on the bird → collision → scoring → spawning →
 * difficulty refresh. Scoring (D7): a scoring column is awarded once, when
 * {@code scoreLineX ≤ hitboxLeft}, adding {@code 1 × SCORE_MULT} points.
 *
 * <p>Hold-to-flap (D2): while {@link SimInput#autoFlapHeld()} is set a synthetic flap is issued
 * whenever {@link Playfield#AUTO_FLAP_PERIOD_TICKS} ticks passed since the last flap.
 */
public final class Simulation {

    private static final long HASH_SEED = MathUtil.fnv1a64("flapforge-sim");
    private static final String RAMP_SOURCE_PREFIX = "ramp:";

    private final RandomProvider rng;
    private final EffectStack stack = new EffectStack();
    private final StatSheet stats;
    private final RuleSet rules;
    private final BirdProfile birdProfile;
    private final Bird bird;
    private final BirdPhysics physics;
    private final ObstacleLayer obstacles = new ObstacleLayer();
    private final ObstacleSpawner spawner;
    private final CollisionSystem collision = new CollisionSystem();
    private final DifficultyState difficulty;
    private int tick;
    private int gatesPassed;
    private double points;
    private int ticksSinceLastFlap;
    private int flaps;
    private int flapsRefused;
    private int nearMisses;

    /**
     * Builds the model for a configuration and its resolved content.
     *
     * @param config the run configuration
     * @param setup the resolved bird, world and tier
     */
    public Simulation(RunConfig config, RunSetup setup) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(setup, "setup");
        this.rng = new RandomProvider(config.seed());
        this.rules = config.rules().union(setup.world().flags()).union(setup.tier().flags());
        this.birdProfile = setup.bird();
        this.stats = new StatSheet(birdProfile.baseStats(), stack, rules);
        stack.setLayer(Layer.BIRD, birdProfile.effects());
        stack.setLayer(Layer.UPGRADES, config.permanentEffects());
        this.difficulty = new DifficultyState(stack, new DifficultyCurve(setup.world().curve()),
                setup.tier().effects(), setup.world().effects(), setup.speedRampPerTick(), rules);
        this.bird = new Bird(birdProfile.hitbox(), Playfield.BIRD_START_Y);
        this.physics = new BirdPhysics(stats);
        this.spawner = new ObstacleSpawner(obstacles, new SpawnTable(setup.world().spawnWeights()),
                rng);
        refreshRamp();
        difficulty.refresh(0, 0);
    }

    /**
     * Advances the world by one tick while the bird is alive.
     *
     * @param input the player intent
     * @return the facts of this tick
     * @throws IllegalStateException when the bird is not alive (use {@link #tickDying()})
     */
    public TickReport tick(SimInput input) {
        if (!bird.isAlive()) {
            throw new IllegalStateException("tick() called on a " + bird.state() + " bird");
        }
        tick++;
        List<TickFact> facts = new ArrayList<>(4);
        bird.beginTick();
        if (BirdPhysics.groundContact(bird)) {
            // Already on or under the ground line: no flap is possible from the buried window
            // upstream allowed (D7); the tick ends with the ground death.
            bird.setState(Bird.State.DYING);
            facts.add(new TickFact.Crashed(CollisionCause.GROUND));
            return new TickReport(tick, facts);
        }

        ticksSinceLastFlap++;
        boolean flapRequested = input.flap() || (input.autoFlapHeld()
                && ticksSinceLastFlap >= Playfield.AUTO_FLAP_PERIOD_TICKS);
        if (flapRequested) {
            ticksSinceLastFlap = 0;
            if (physics.flap(bird)) {
                flaps++;
                facts.add(new TickFact.Flapped());
            } else {
                flapsRefused++;
                facts.add(new TickFact.FlapRefused());
            }
        }
        physics.step(bird);

        SimContext ctx = context();
        obstacles.update(ctx);
        for (Obstacle o : obstacles.obstacles()) {
            o.affectBird(bird, ctx);
        }

        double hitboxScale = stats.resolve(StatId.HITBOX_SCALE);
        CollisionReport report = collision.test(bird, obstacles, Playfield.NEAR_MISS_INFLATE_PX,
                hitboxScale, rules);
        if (report.lethalHit()) {
            bird.setState(Bird.State.DYING);
            facts.add(new TickFact.Crashed(report.cause()));
            return new TickReport(tick, facts);
        }
        if (report.nearMiss()) {
            Obstacle grazed = report.obstacle();
            grazed.markDirty();
            if (!grazed.isNearMissReported()) {
                grazed.markNearMissReported();
                nearMisses++;
                facts.add(new TickFact.NearMiss());
            }
        }

        boolean gateChanged = false;
        Aabb box = bird.hitbox(hitboxScale);
        for (Obstacle o : obstacles.obstacles()) {
            if (o.isScoring() && !o.isScored() && o.scoreLineX() <= box.x()) {
                o.markScored();
                gatesPassed++;
                double awarded = stats.resolve(StatId.SCORE_MULT);
                points += awarded;
                gateChanged = true;
                facts.add(new TickFact.GatePassed(!o.isDirty()));
                facts.add(new TickFact.Scored(awarded));
            }
        }

        Obstacle spawned = spawner.update(ctx);
        if (spawned != null) {
            facts.add(new TickFact.ObstacleSpawned(spawned.kind()));
        }

        if (gateChanged) {
            refreshRamp();
        }
        if (gateChanged || difficulty.needsTickRefresh()) {
            difficulty.refresh(gatesPassed, tick);
        }
        return new TickReport(tick, facts);
    }

    /**
     * Seeds the death fall (E28): the world freezes and the bird starts falling at +15 px/s,
     * which makes the fall bit-identical to upstream's {@code velocity = 0}.
     */
    public void beginDying() {
        bird.setState(Bird.State.DYING);
        bird.setVy(15);
        obstacles.settle();
    }

    /**
     * One tick of the death fall: gravity only, obstacles frozen. On ground contact the bird is
     * placed on the ground line and marked dead.
     *
     * @return {@code true} when the bird landed this tick
     */
    public boolean tickDying() {
        if (bird.state() != Bird.State.DYING) {
            throw new IllegalStateException("tickDying() called on a " + bird.state() + " bird");
        }
        tick++;
        bird.beginTick();
        physics.step(bird);
        if (BirdPhysics.groundContact(bird)) {
            land();
            return true;
        }
        return false;
    }

    /** Places the bird on the ground line and marks it dead. */
    public void land() {
        bird.setY(Playfield.GROUND_DEATH_Y);
        bird.setVy(0);
        bird.setState(Bird.State.DEAD);
    }

    /**
     * Folds the visible state into a hash: tick, bird, score and every obstacle (random stream
     * states are not hashed).
     *
     * @return the hash
     */
    public long stateHash() {
        long h = MathUtil.fold(HASH_SEED, tick);
        h = bird.hashState(h);
        h = MathUtil.fold(h, gatesPassed);
        h = MathUtil.fold(h, Double.doubleToLongBits(points));
        return obstacles.hashState(h);
    }

    /**
     * The tick context for the current stats.
     *
     * @return a fresh context
     */
    public SimContext context() {
        return new SimContext(tick, stats.resolve(StatId.TIME_SCALE), stats, rules, rng, bird);
    }

    /**
     * The bird.
     *
     * @return the bird
     */
    public Bird bird() {
        return bird;
    }

    /**
     * The obstacles.
     *
     * @return the layer
     */
    public ObstacleLayer obstacles() {
        return obstacles;
    }

    /**
     * The spawner.
     *
     * @return the spawner
     */
    public ObstacleSpawner spawner() {
        return spawner;
    }

    /**
     * The resolved stats.
     *
     * @return the sheet
     */
    public StatSheet stats() {
        return stats;
    }

    /**
     * The modifier stack behind the stats.
     *
     * @return the stack
     */
    public EffectStack effects() {
        return stack;
    }

    /**
     * The active rules.
     *
     * @return the rules
     */
    public RuleSet rules() {
        return rules;
    }

    /**
     * The difficulty state.
     *
     * @return the state
     */
    public DifficultyState difficulty() {
        return difficulty;
    }

    /**
     * The bird profile in use.
     *
     * @return the profile
     */
    public BirdProfile birdProfile() {
        return birdProfile;
    }

    /**
     * The run's random streams.
     *
     * @return the provider
     */
    public RandomProvider rng() {
        return rng;
    }

    /**
     * Ticks processed so far (alive and dying).
     *
     * @return the count
     */
    public int tick() {
        return tick;
    }

    /**
     * Gates passed.
     *
     * @return the count
     */
    public int gatesPassed() {
        return gatesPassed;
    }

    /**
     * Points scored.
     *
     * @return the points
     */
    public double points() {
        return points;
    }

    /**
     * Ticks since the last flap request (0 on the tick of a flap).
     *
     * @return the count
     */
    public int ticksSinceLastFlap() {
        return ticksSinceLastFlap;
    }

    /**
     * Accepted flaps.
     *
     * @return the count
     */
    public int flaps() {
        return flaps;
    }

    /**
     * Refused flaps.
     *
     * @return the count
     */
    public int flapsRefused() {
        return flapsRefused;
    }

    /**
     * Near misses reported.
     *
     * @return the count
     */
    public int nearMisses() {
        return nearMisses;
    }

    private void refreshRamp() {
        List<RampEffect> ramps = birdProfile.rampEffects();
        if (ramps.isEmpty()) {
            return;
        }
        List<StatModifier> layer = new ArrayList<>(ramps.size());
        for (RampEffect r : ramps) {
            layer.add(r.at(gatesPassed, RAMP_SOURCE_PREFIX + birdProfile.id()));
        }
        stack.setLayer(Layer.BIRD_RAMP, layer);
    }
}
