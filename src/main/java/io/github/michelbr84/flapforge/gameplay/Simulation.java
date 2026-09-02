package io.github.michelbr84.flapforge.gameplay;

import io.github.michelbr84.flapforge.ability.AbilityHost;
import io.github.michelbr84.flapforge.ability.AbilityInstance;
import io.github.michelbr84.flapforge.ability.AbilityManager;
import io.github.michelbr84.flapforge.ability.BehaviorRegistry;
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
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleSignal;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleSpawner;
import io.github.michelbr84.flapforge.gameplay.obstacle.PatternStreamer;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import io.github.michelbr84.flapforge.gameplay.pickup.Coin;
import io.github.michelbr84.flapforge.gameplay.pickup.PickupLayer;
import io.github.michelbr84.flapforge.gameplay.run.DraftWorld;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.ReviveSystem;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.run.ShieldSystem;
import io.github.michelbr84.flapforge.gameplay.run.StreakTracker;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.spec.RampEffect;
import io.github.michelbr84.flapforge.gameplay.spec.RuleCycleSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.gameplay.spec.SynergyEffect;
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
 * <p>Tick order: wind sampling (the world's ambient wind, then the zones the bird's tick-start
 * hitbox overlaps, M7) → ability timers → flap → ability activation → bird integration (gravity
 * plus the sampled wind) → ability {@code onTick} → world scroll (plus the sampled horizontal
 * wind), obstacle phases and pickups (scaled by {@code TIME_SCALE}) → collision (and, on a
 * lethal hit, the absorb chain) → coin pickup → scoring and streak (each gate also feeds the
 * world effects: sky flashes and rule-cycle draws, M7) → spawning (obstacle first — a pattern
 * step or a table draw — then its coin trail) → obstacle signals (piston telegraph, lightning
 * warning) → rule-cycle countdown and landing → difficulty refresh. Scoring (D7): a scoring
 * column is awarded once, when {@code scoreLineX ≤ hitboxLeft}, adding {@code 1 × SCORE_MULT}
 * points; every lethal kind scores unless a pattern step turned scoring off, a wind zone never
 * does.
 *
 * <p>Rules (D8, M7) are the union of three sources kept apart so one can change without the
 * others: the base rules of the run (config, world, tier), the flags cards and synergies turned
 * on ({@link #addRules}) and the flags of the rule-cycle option in force, which are
 * <em>replaced</em> at every shift. A world without patterns, ambience or cycles allocates the
 * M7 pieces but never draws from their streams and folds nothing of theirs into the hash.
 *
 * <p>Abilities sit inside that order rather than around it (D9): activation happens after the
 * flap, so a double flap pressed together with a flap is the one that lands; the
 * {@code onTick} hook runs <em>after</em> the integration, so the dash can undo the gravity step
 * of the tick it is holding (E24); and the absorb chain runs at the collision, so a shield or a
 * revive cancels the death before any fact about it exists. A run with nothing equipped skips
 * every ability call and hashes exactly what it hashed before abilities existed, which is what
 * keeps the published cross-platform hash comparable across milestones (D12).
 *
 * <p>Lethal hits go through {@link #absorbLethalHit}: invulnerability ticks or the ghost state
 * cancel the hit outright, then the behaviours get a chance, then the {@link ShieldSystem}, then
 * the {@link ReviveSystem}. The two systems are stat-driven, so an upgrade node that grants
 * {@code SHIELD_CHARGES} or {@code REVIVES} works with no ability equipped (D9).
 *
 * <p>Hold-to-flap (D2): while {@link SimInput#autoFlapHeld()} is set a synthetic flap is issued
 * whenever {@link Playfield#AUTO_FLAP_PERIOD_TICKS} ticks passed since the last flap.
 */
public final class Simulation implements AbilityHost, DraftWorld {

    /**
     * Slack in pixels on the breather's clearance (D11, M6): with the window exactly the width of
     * the bird's clearance it could fall between two ticks of the scroll, so the deferral asks for
     * a little more than the geometry needs. Small enough that the shipped 160 px interval still
     * asks for less than {@code BREATHER_INTERVALS} and the 1.5 D11 writes down is what happens.
     */
    private static final double DRAFT_CLEARANCE_MARGIN = 20;

    private static final long HASH_SEED = MathUtil.fnv1a64("flapforge-sim");
    private static final String RAMP_SOURCE_PREFIX = "ramp:";
    private static final String SYNERGY_SOURCE_PREFIX = "synergy:";

    private final RandomProvider rng;
    private final EffectStack stack = new EffectStack();
    private final StatSheet stats;
    private final RuleSet baseRules;
    private RuleSet draftRules = RuleSet.EMPTY;
    private RuleSet cycleRules = RuleSet.EMPTY;
    private RuleSet rules;
    private final WorldEffects worldEffects;
    private final BirdProfile birdProfile;
    private final Bird bird;
    private final BirdPhysics physics;
    private final ObstacleLayer obstacles = new ObstacleLayer();
    private final ObstacleSpawner spawner;
    private final PickupLayer pickups;
    private final StreakTracker streaks;
    private final CollisionSystem collision = new CollisionSystem();
    private final DifficultyState difficulty;
    private final AbilityManager abilities;
    private final ShieldSystem shield;
    private final ReviveSystem revive;
    private final ModifierDirector modifiers;
    private int invulnerableTicks;
    private boolean ghost;
    private Obstacle ghostAgainst;
    private boolean ghostLatched;
    private int tick;
    private int gatesPassed;
    private double points;
    private int ticksSinceLastFlap;
    private int flaps;
    private int flapsRefused;
    private int nearMisses;
    private int coinsCollected;

    /**
     * Builds the model for a configuration and its resolved content.
     *
     * @param config the run configuration
     * @param setup the resolved bird, world and tier
     */
    public Simulation(RunConfig config, RunSetup setup) {
        this(config, setup, null);
    }

    /**
     * Builds the model with an injected spawn table, the one seam a test needs to make the world
     * predictable (E17: {@code FixedSpawnTable} drives {@code ModifierDirectorTest}).
     *
     * @param config the run configuration
     * @param setup the resolved bird, world and tier
     * @param spawnTable the table to spawn from, or {@code null} for the world's own
     */
    public Simulation(RunConfig config, RunSetup setup, SpawnTable spawnTable) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(setup, "setup");
        this.rng = new RandomProvider(config.seed());
        this.baseRules = config.rules().union(setup.world().flags()).union(setup.tier().flags());
        this.rules = baseRules;
        this.birdProfile = setup.bird();
        this.stats = new StatSheet(birdProfile.baseStats(), stack, rules);
        stack.setLayer(Layer.BIRD, birdProfile.effects());
        stack.setLayer(Layer.UPGRADES, config.permanentEffects());
        applySynergies(config.upgradeLevelsTotal());
        this.difficulty = new DifficultyState(stack, new DifficultyCurve(setup.world().curve()),
                setup.tier().effects(), setup.world().effects(), setup.speedRampPerTick(), rules);
        this.bird = new Bird(birdProfile.hitbox(), Playfield.BIRD_START_Y);
        this.physics = new BirdPhysics(stats);
        WorldSpec world = setup.world();
        this.spawner = new ObstacleSpawner(obstacles,
                spawnTable == null ? new SpawnTable(world.spawnWeights()) : spawnTable, rng,
                world.patterns().isEmpty() && setup.forcedPattern() == null ? null
                        : new PatternStreamer(world.patterns(), setup.forcedPattern(),
                                rng.stream(RandomProvider.PATTERNS)));
        this.worldEffects = new WorldEffects(world.ambient(), world.ruleCycles(),
                world.ruleCycles() == null ? null : rng.stream(RandomProvider.CYCLES));
        this.pickups = new PickupLayer(rng);
        this.streaks = new StreakTracker(setup.streakStep());
        // Order matters (D9): the loadout publishes its passive effects into the ABILITY layer
        // first, so SHIELD_CHARGES and REVIVES already carry them when the two run systems read
        // their charges; only then may a behaviour's onEquip configure those systems.
        this.abilities = AbilityManager.create(setup.abilities(), config.abilityLevels(), rules,
                this, stack, BehaviorRegistry.DEFAULT);
        this.shield = new ShieldSystem((int) stats.resolve(StatId.SHIELD_CHARGES));
        this.revive = new ReviveSystem((int) stats.resolve(StatId.REVIVES));
        abilities.equip();
        // After the loadout, before the ramp: the forced modifiers of a challenge or a daily are
        // taken inside this constructor (D11), so they are already in the MODIFIERS layer when
        // the difficulty and the ramp resolve their first values.
        this.modifiers = new ModifierDirector(setup.modifiers(), config.allowOffers(),
                config.forcedModifiers(), stack, this, rng.stream(RandomProvider.OFFERS));
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

        if (invulnerableTicks > 0) {
            invulnerableTicks--;
        }
        sampleWind();
        SimContext pre = null;
        if (!abilities.isEmpty()) {
            pre = context();
            abilities.beginTick(facts);
        }

        ticksSinceLastFlap++;
        // A behaviour that pins the bird (the dash) would undo this tick's flap a few lines
        // below, so the flap is not accepted at all: an eaten flap still restarted the wing
        // animation, played the sound and counted in the statistics for a bird that never moved.
        // ticksSinceLastFlap is left running, so hold-to-flap resumes on the tick the burst ends.
        boolean flapRequested = !abilities.holdsBird() && (input.flap() || (input.autoFlapHeld()
                && ticksSinceLastFlap >= Playfield.AUTO_FLAP_PERIOD_TICKS));
        if (flapRequested) {
            ticksSinceLastFlap = 0;
            double flapVelocity = stats.resolve(StatId.FLAP_VELOCITY);
            if (pre != null) {
                flapVelocity = abilities.onFlap(pre, flapVelocity);
            }
            if (BirdPhysics.flap(bird, flapVelocity)) {
                flaps++;
                facts.add(new TickFact.Flapped());
            } else {
                flapsRefused++;
                facts.add(new TickFact.FlapRefused());
            }
        }
        if (pre != null && input.ability() && abilities.activate(pre, facts)) {
            // After the flap of the same tick, not before: pressing both is one deliberate rescue
            // and the ability is the deliberate half of it, so a double flap that lands together
            // with an ordinary flap is worth its charge instead of being overwritten by it.
            // The activation may also have changed TIME_SCALE or SCROLL_SPEED, and the hooks that
            // follow read the tick context, so it is rebuilt here.
            pre = context();
        }
        physics.step(bird);
        if (pre != null) {
            // After the integration on purpose (E24): a dash undoes this tick's gravity step here
            // and the collision test below sees the y the burst is holding.
            abilities.onTick(pre, facts);
        }

        SimContext ctx = context();
        obstacles.update(ctx);
        pickups.update(ctx);

        double hitboxScale = stats.resolve(StatId.HITBOX_SCALE);
        CollisionReport report = collision.test(bird, obstacles, Playfield.NEAR_MISS_INFLATE_PX,
                hitboxScale, rules);
        boolean collided = report.lethalHit();
        if (report.lethalHit()) {
            if (!absorbLethalHit(report, ctx, facts)) {
                bird.setState(Bird.State.DYING);
                facts.add(new TickFact.Crashed(report.cause(),
                        report.obstacle() == null ? null : report.obstacle().kind()));
                return new TickReport(tick, facts);
            }
        } else if (ghost) {
            // Ghost until clear (D9): nothing lethal overlaps any more, so the bird is solid
            // again even if its invulnerability ticks are still running.
            clearGhost();
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

        Aabb box = bird.hitbox(hitboxScale);
        if (abilities.routesCoins()) {
            routeCoinsNearBird(ctx, box);
        }
        for (Coin coin : pickups.collect(box)) {
            coinsCollected += coin.value();
            facts.add(new TickFact.CoinCollected(coin.value()));
        }

        boolean gateChanged = false;
        for (Obstacle o : obstacles.obstacles()) {
            if (o.isScoring() && !o.isScored() && o.scoreLineX() <= box.x()) {
                o.markScored();
                gatesPassed++;
                double awarded = stats.resolve(StatId.SCORE_MULT);
                points += awarded;
                gateChanged = true;
                facts.add(new TickFact.GatePassed(!o.isDirty()));
                facts.add(new TickFact.Scored(awarded));
                worldEffects.onGatePassed(gatesPassed, facts);
            }
        }
        resolveStreaks(box, facts);

        Obstacle spawned = spawner.update(ctx, gatesPassed);
        if (spawned != null) {
            facts.add(new TickFact.ObstacleSpawned(spawned.kind()));
            if (spawned.isScoring()) {
                pickups.spawnFor(spawned, ctx);
            }
        }
        drainSignals(facts);
        // A shift lands only on a tick the draft is not running (D11, M7): the director's state
        // is read before it advances for this tick, so the tick a breather starts on may still
        // land one, and every later tick of the draft defers it.
        boolean shifted = worldEffects.tick(modifiers.state() == ModifierDirector.State.IDLE);
        if (shifted) {
            applyCycle(worldEffects.activeOption());
        }

        if (gateChanged) {
            if (shield.onGatePassed(gatesPassed)) {
                // The shield coming back is the most important defensive state change of a run;
                // it is announced on the same fact a restored ability charge is, so the HUD pip
                // and the ready cue treat the two alike.
                facts.add(new TickFact.AbilityReady(ShieldSystem.ABILITY_ID));
            }
            if (!abilities.isEmpty()) {
                abilities.onGatePassed(gatesPassed, facts);
            }
            refreshRamp();
        }
        if (gateChanged || shifted || difficulty.needsTickRefresh()) {
            difficulty.refresh(gatesPassed, tick);
        }
        // Last, after the collision test and after the spawner: a draft may only open on a tick
        // that nothing hit (D11), and the breather it starts has to reach the spawner before the
        // spawner's next cursor decision, not after it.
        modifiers.afterTick(gatesPassed, collided, facts);
        return new TickReport(tick, facts);
    }

    /**
     * Counts the columns the streak can now judge (D26).
     *
     * <p>The score line and the graze window do not close together. A gate scores when its right
     * edge passes the bird's hitbox left edge; the near-miss test uses that box inflated by
     * {@link Playfield#NEAR_MISS_INFLATE_PX}, so for another 6 px — three ticks at the classic
     * scroll — the bird can still graze a column it has already scored. Resolving the streak at
     * the score line therefore forgave almost every graze (measured: 98 % of an expert's near
     * misses), which is not "a gate is clean when it was passed with no near miss". So the score,
     * the points and the ramp stay where upstream put them, and the streak waits until the column
     * is out of the inflated box for good.
     *
     * <p>The cost is a three-tick tail: a run that ends within three ticks of a gate never counts
     * that last gate in the streak. That is the right trade — the alternative counts a gate the
     * bird is still grazing — and it is deterministic, so the golden run pins it.
     *
     * @param box the bird hitbox of this tick
     * @param facts where a {@code StreakChanged} is appended
     */
    private void resolveStreaks(Aabb box, List<TickFact> facts) {
        for (Obstacle o : obstacles.obstacles()) {
            if (o.isScoring() && o.isScored() && !o.isStreakResolved()
                    && o.scoreLineX() + Playfield.NEAR_MISS_INFLATE_PX <= box.x()) {
                o.markStreakResolved();
                if (streaks.onGatePassed(!o.isDirty())) {
                    facts.add(new TickFact.StreakChanged(streaks.streak()));
                }
            }
        }
    }

    /**
     * Seeds the death fall (E28): the world freezes and the bird starts falling at +15 px/s,
     * which makes the fall bit-identical to upstream's {@code velocity = 0}.
     */
    public void beginDying() {
        bird.setState(Bird.State.DYING);
        bird.setVy(15);
        obstacles.settle();
        pickups.settle();
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
     * The absorb chain of a lethal hit (D9), in the order a run resolves it:
     *
     * <ol>
     *   <li>invulnerability ticks or the ghost state cancel the hit for free;</li>
     *   <li>the equipped behaviours get the hit ({@code onLethalHit}), and the first one that
     *       takes it cancels it;</li>
     *   <li>the {@link ShieldSystem} spends a charge, grants its invulnerability and ghosts the
     *       bird until nothing overlaps it any more;</li>
     *   <li>the {@link ReviveSystem} spends a revive, sets the velocity (zero, or the recovery
     *       ability's auto-flap kick) and grants its invulnerability.</li>
     * </ol>
     *
     * <p><b>One rule for the ground.</b> Every save that cancels a {@code GROUND} hit — i-frames,
     * ghost, shield or revive alike — lifts the bird back above the ground line
     * ({@link ReviveSystem#safeY}) and zeroes its velocity, because the M1 ground rule kills
     * anything at or below that line at the start of the next tick: without the lift the save
     * would buy exactly one tick. Nothing lifts the bird in mid-air, where the fall itself is not
     * the danger; a mid-air revive gets only its velocity kick. The ground is therefore not an
     * exception to invulnerability (the shipped string promises "nothing touches you"), and a
     * shield charge does save a bird that dives into the ground — it costs a charge, it plays the
     * shield cue and it is measured in {@code docs/BALANCING.md}.
     *
     * <p><b>The ghost is granted against one hazard, not against the world.</b> A ghost cancels
     * hits from the obstacle it was granted for (or, when it was granted with none in sight — the
     * dash leaving its burst inside a column — the first one it meets) and is dropped the moment
     * a different obstacle hits the bird, so one charge can never turn into open-ended immunity
     * in a dense pattern.
     *
     * <p>Whatever cancels the hit, the column that caused it is marked dirty, so the gate it
     * belongs to is not a clean one for the streak (D26).
     *
     * @param report the lethal collision report
     * @param ctx the tick context
     * @param facts where {@code ShieldAbsorbed} / {@code Revived} are appended
     * @return {@code true} when the bird survives the hit
     */
    private boolean absorbLethalHit(CollisionReport report, SimContext ctx, List<TickFact> facts) {
        Obstacle hit = report.obstacle();
        boolean onGround = report.cause() == CollisionCause.GROUND;
        if (invulnerableTicks > 0 || ghostCovers(hit)) {
            liftOffTheGround(onGround);
            markHit(hit);
            return true;
        }
        if (!abilities.isEmpty() && abilities.onLethalHit(ctx, hit, facts)) {
            markHit(hit);
            return true;
        }
        if (shield.absorb()) {
            grantIFrames(shield.invulnTicks());
            startGhost(hit);
            liftOffTheGround(onGround);
            markHit(hit);
            facts.add(new TickFact.ShieldAbsorbed());
            return true;
        }
        if (revive.consume()) {
            liftOffTheGround(onGround);
            bird.setVy(revive.reviveVelocity(stats.resolve(StatId.FLAP_VELOCITY)));
            grantIFrames(revive.invulnTicks());
            startGhost(hit);
            markHit(hit);
            facts.add(new TickFact.Revived());
            return true;
        }
        return false;
    }

    /**
     * Puts a saved bird back above the ground band, so the unconditional ground rule of the next
     * tick does not undo the save.
     *
     * @param onGround whether the hit that was cancelled was a ground hit
     */
    private void liftOffTheGround(boolean onGround) {
        if (onGround) {
            bird.setY(ReviveSystem.safeY(bird.y()));
            bird.setVy(0);
        }
    }

    /**
     * Whether the ghost state covers this hit, latching onto the first hazard it meets when it
     * was granted without one. A hit from anything else drops the ghost.
     *
     * @param hit the obstacle hit, or {@code null} for the ground and the ceiling
     * @return {@code true} when the hit is ignored
     */
    private boolean ghostCovers(Obstacle hit) {
        if (!ghost) {
            return false;
        }
        if (!ghostLatched) {
            ghostAgainst = hit;
            ghostLatched = true;
            return true;
        }
        if (ghostAgainst == hit) {
            return true;
        }
        clearGhost();
        return false;
    }

    /**
     * Starts ghosting against the obstacle a charge was just spent on.
     *
     * @param hit the obstacle hit, or {@code null} for the ground and the ceiling
     */
    private void startGhost(Obstacle hit) {
        ghost = true;
        ghostAgainst = hit;
        ghostLatched = true;
    }

    private void clearGhost() {
        ghost = false;
        ghostAgainst = null;
        ghostLatched = false;
    }

    private static void markHit(Obstacle obstacle) {
        if (obstacle != null) {
            obstacle.markDirty();
        }
    }

    /**
     * Offers every coin inside the resolved {@code MAGNET_RADIUS} to the equipped behaviours
     * ({@code onCoinNear}), after the pickups moved and before the bird collects them.
     *
     * <p>Called only when a behaviour asked for the hook ({@link AbilityManager#routesCoins()}):
     * none of the eight shipped ones does — the magnet is a {@code MAGNET_RADIUS} stat read by
     * {@code Coin.update} — so the walk over the live coins costs nothing until one does.
     *
     * @param ctx the tick context
     * @param box the bird hitbox of this tick
     */
    private void routeCoinsNearBird(SimContext ctx, Aabb box) {
        double radius = stats.resolve(StatId.MAGNET_RADIUS);
        if (radius <= 0 || pickups.isEmpty()) {
            return;
        }
        double cx = box.centerX();
        double cy = box.centerY();
        double radiusSq = radius * radius;
        for (Coin coin : pickups.coins()) {
            if (coin.isCollected()) {
                continue;
            }
            double dx = coin.x() - cx;
            double dy = coin.y() - cy;
            if (dx * dx + dy * dy <= radiusSq) {
                abilities.onCoinNear(ctx, coin);
            }
        }
    }

    /**
     * Folds the visible state into a hash: tick, bird, score and every obstacle (random stream
     * states are not hashed).
     *
     * <p>The ability, shield and revive state is folded in only when the run actually has some
     * (D9, D12). That is not a hole in the guarantee: a run with an empty loadout and no charge
     * has nothing to diverge on, and keeping its fold untouched is what makes the published
     * {@code --headless-run} hash — a classic bird with no abilities — comparable across the
     * milestones that add systems around it.
     *
     * @return the hash
     */
    public long stateHash() {
        long h = MathUtil.fold(HASH_SEED, tick);
        h = bird.hashState(h);
        h = MathUtil.fold(h, gatesPassed);
        h = MathUtil.fold(h, Double.doubleToLongBits(points));
        h = MathUtil.fold(h, coinsCollected);
        h = streaks.hashState(h);
        h = obstacles.hashState(h);
        h = pickups.hashState(h);
        // The M7 pieces fold only when the world has them (D12): a world without patterns,
        // ambience or cycles hashes what it hashed in M6, so the published hash stands.
        h = spawner.hashState(h);
        if (worldEffects.isActive()) {
            h = worldEffects.hashState(h);
        }
        if (modifiers.isActive()) {
            // Only a run that can draft, or that already took something, folds the draft state:
            // a run without it hashes exactly what it hashed before the roguelite layer existed
            // (D12), which is what keeps the published --headless-run hash comparable.
            h = modifiers.hashState(h);
        }
        if (!hasRunSystems()) {
            // A run without run systems can still carry invulnerability or a ghost (the draft
            // resume's i-frames, a Void option that zeroed the shield mid-run), and then they
            // are folded too; they are always zero in the classic headless run (M7).
            if (invulnerableTicks > 0 || ghost) {
                h = MathUtil.fold(h, invulnerableTicks);
                h = MathUtil.fold(h, (ghost ? 1 : 0) | (ghostLatched ? 2 : 0));
            }
            return h;
        }
        h = MathUtil.fold(h, invulnerableTicks);
        h = MathUtil.fold(h, (ghost ? 1 : 0) | (ghostLatched ? 2 : 0));
        h = shield.hashState(h);
        h = revive.hashState(h);
        return abilities.hashState(h);
    }

    /**
     * Whether this run has anything the ability systems own: an equipped ability, a shield charge
     * or a revive.
     *
     * @return {@code true} when the ability state is part of the run
     */
    public boolean hasRunSystems() {
        return !abilities.isEmpty() || shield.maxCharges() > 0 || revive.maxCharges() > 0;
    }

    /**
     * The equipped abilities (D9): timers, charges and the {@code ABILITY} layer.
     *
     * @return the manager
     */
    public AbilityManager abilities() {
        return abilities;
    }

    /**
     * The mid-run draft (D11, D27): the schedule, the offer on the table, the cards taken and the
     * active synergies.
     *
     * @return the director
     */
    public ModifierDirector modifiers() {
        return modifiers;
    }

    @Override
    public boolean isDraftPathClear() {
        double left = bird.hitbox(stats.resolve(StatId.HITBOX_SCALE)).x();
        for (Obstacle o : obstacles.obstacles()) {
            if (!o.lethal()) {
                // A wind zone is sky, not a hazard: a draft may open inside it.
                continue;
            }
            if (o.x() + o.width() < left) {
                // Fully behind the bird: it can never be what the freeze traps the player in.
                continue;
            }
            if (o.x() >= Playfield.WIDTH) {
                // Still off the right edge: the breather's gap is what the player is looking at.
                continue;
            }
            return false;
        }
        return true;
    }

    @Override
    public void deferSpawn(double intervals) {
        // The D11 push plus an absolute floor (M7): the intervals alone assume a 40 px column
        // 160 px ahead, and a 112 px gear or a pattern step 130 px on would leave the breather
        // no window at all, so the deferred column is also placed at least the clearance the
        // window needs behind the last column's right edge, whatever its width or the step's dx.
        spawner.deferNextSpawn(intervals, clearancePx());
    }

    /**
     * The clear air a breather needs behind the last column for {@link #isDraftPathClear()} to
     * answer {@code true} at all: the distance from the bird's hitbox left edge to the right edge
     * of the playfield, plus a margin so the window is a few ticks wide.
     *
     * @return the clearance in px
     */
    private double clearancePx() {
        double left = bird.hitbox(stats.resolve(StatId.HITBOX_SCALE)).x();
        return Playfield.WIDTH - left + DRAFT_CLEARANCE_MARGIN;
    }

    @Override
    public double clearanceIntervals() {
        double interval = stats.resolve(StatId.GATE_INTERVAL);
        if (interval <= 0) {
            return 0;
        }
        // The window {@link #isDraftPathClear()} opens is the widened spacing minus the distance
        // the bird still has to the right edge plus one obstacle body: while the obstacle behind
        // the bird has not cleared its hitbox, or the one ahead has already entered the playfield,
        // the path is not clear. The margin is there so the window is a few ticks wide rather
        // than a single frame the scroll can step over.
        double left = bird.hitbox(stats.resolve(StatId.HITBOX_SCALE)).x();
        double span = Playfield.WIDTH - left + Playfield.PIPE_BODY_W + DRAFT_CLEARANCE_MARGIN;
        return Math.max(0, span / interval - 1);
    }

    @Override
    public boolean abilityCooldownMatters() {
        List<AbilityInstance> instances = abilities.instances();
        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).levelDef().cooldownTicks() > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean abilityDurationMatters() {
        List<AbilityInstance> instances = abilities.instances();
        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).levelDef().durationTicks() > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addRules(RuleSet extra) {
        RuleSet merged = draftRules.union(extra);
        if (merged == draftRules) {
            return;
        }
        draftRules = merged;
        recomputeRules();
    }

    /**
     * Rebuilds the active rules from their three sources (D8, M7) and pushes them to the sheet
     * and the difficulty state when they changed.
     */
    private void recomputeRules() {
        RuleSet merged = baseRules.union(draftRules).union(cycleRules);
        if (merged.equals(rules)) {
            return;
        }
        rules = merged;
        stats.setRules(merged);
        difficulty.setRules(merged);
    }

    /**
     * Lands a rule-cycle option (M7): its flags replace the previous option's in the active
     * rules, its effects replace the previous option's in the {@code WORLD_CYCLE} layer, and the
     * two defensive systems follow whatever the flags did to {@code SHIELD_CHARGES} and
     * {@code REVIVES} — in both directions, because a flag that cycles in zeroes the stat and one
     * that cycles out gives it back (D8, E12).
     *
     * @param option the option in force from now on
     */
    private void applyCycle(RuleCycleSpec.Option option) {
        cycleRules = option.flags();
        recomputeRules();
        stack.setLayer(Layer.WORLD_CYCLE, option.effects());
        shield.syncTo((int) stats.resolve(StatId.SHIELD_CHARGES));
        revive.syncTo((int) stats.resolve(StatId.REVIVES));
    }

    @Override
    public void refreshDefensiveCharges() {
        // A card drafted mid-run may raise SHIELD_CHARGES or REVIVES, and both systems snapshot
        // the stat when the run starts (the limit M5 wrote down in their Javadoc). Re-resolving
        // here is the half of E12 the pool does not cover: the pool decides whether the card is
        // worth showing, this decides that taking it actually hands the charge over.
        shield.raiseTo((int) stats.resolve(StatId.SHIELD_CHARGES));
        revive.raiseTo((int) stats.resolve(StatId.REVIVES));
    }

    @Override
    public ShieldSystem shield() {
        return shield;
    }

    @Override
    public ReviveSystem revive() {
        return revive;
    }

    @Override
    public void grantIFrames(int ticks) {
        if (ticks > invulnerableTicks) {
            invulnerableTicks = ticks;
        }
    }

    @Override
    public void ghostUntilClear() {
        // No hazard is named: the ghost latches onto the first one it meets (the column a dash
        // ended inside) and covers only that one.
        ghost = true;
        ghostAgainst = null;
        ghostLatched = false;
    }

    @Override
    public boolean isInvulnerable() {
        return invulnerableTicks > 0 || ghost;
    }

    @Override
    public int invulnerableTicks() {
        return invulnerableTicks;
    }

    /**
     * Whether the bird is currently ignoring overlaps until it is clear of them (D9).
     *
     * @return {@code true} while ghosting
     */
    public boolean isGhosting() {
        return ghost;
    }

    /**
     * The tick context for the current stats.
     *
     * @return a fresh context
     */
    public SimContext context() {
        return new SimContext(tick, stats.resolve(StatId.TIME_SCALE), stats, rules, rng, bird,
                bird.windScroll());
    }

    /**
     * Samples the wind for this tick (D6, M7): the world's ambient wind first — the zone that
     * never ends — then every obstacle gets {@code affectBird} with the bird's tick-start hitbox
     * and its own tick-start box, before the flap and the integration, so a zone's
     * {@code accelY} joins gravity in this tick's integration and its {@code scrollDelta} joins
     * this tick's world scroll. Only wind zones do anything here; the other kinds inherit the
     * no-op, and a world without wind and without zones samples nothing.
     */
    private void sampleWind() {
        worldEffects.applyAmbientWind(bird);
        List<Obstacle> live = obstacles.obstacles();
        if (live.isEmpty()) {
            return;
        }
        SimContext ctx = context();
        for (int i = 0; i < live.size(); i++) {
            live.get(i).affectBird(bird, ctx);
        }
    }

    /**
     * Turns the signals the obstacles raised this tick into facts (M7): a piston entering its
     * telegraph, a lightning column starting its warning.
     *
     * @param facts where the facts go
     */
    private void drainSignals(List<TickFact> facts) {
        List<Obstacle> live = obstacles.obstacles();
        for (int i = 0; i < live.size(); i++) {
            ObstacleSignal signal = live.get(i).takeSignal();
            if (signal == null) {
                continue;
            }
            switch (signal) {
                case PISTON_TELEGRAPH:
                    facts.add(new TickFact.PistonTelegraph());
                    break;
                case LIGHTNING_WARNING:
                default:
                    facts.add(new TickFact.LightningWarning());
                    break;
            }
        }
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
     * The world's ambience and rule cycles (M7).
     *
     * @return the effects
     */
    public WorldEffects worldEffects() {
        return worldEffects;
    }

    /**
     * How much of the playfield the renderer hides (M7): {@code ambient.darkness}, read by the
     * presentation and by nothing in the simulation.
     *
     * @return the darkness in {@code [0, 1]}
     */
    public double darkness() {
        return worldEffects.darkness();
    }

    /**
     * The coins alive in the world.
     *
     * @return the pickup layer
     */
    public PickupLayer pickups() {
        return pickups;
    }

    /**
     * The clean-gate streak (D26).
     *
     * @return the tracker
     */
    public StreakTracker streaks() {
        return streaks;
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

    /**
     * Total value of the coins picked up so far.
     *
     * @return the value
     */
    public int coinsCollected() {
        return coinsCollected;
    }

    /**
     * Resolves the bird's synergy effects once, at construction, from the total of owned upgrade
     * levels the configuration carries (D8, {@code BIRD_SYNERGY}).
     *
     * <p>Once, not per gate: unlike a ramp, a synergy is a property of the build the run started
     * with. A bird with no synergy effects — every bird but Cinder — pushes nothing, so the layer
     * stays empty and the resolved stats are bit-identical to a run without it.
     *
     * @param upgradeLevels the total of owned upgrade levels
     */
    private void applySynergies(int upgradeLevels) {
        List<SynergyEffect> synergies = birdProfile.synergyEffects();
        if (synergies.isEmpty()) {
            return;
        }
        List<StatModifier> layer = new ArrayList<>(synergies.size());
        for (SynergyEffect s : synergies) {
            layer.add(s.at(upgradeLevels, SYNERGY_SOURCE_PREFIX + birdProfile.id()));
        }
        stack.setLayer(Layer.BIRD_SYNERGY, layer);
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
