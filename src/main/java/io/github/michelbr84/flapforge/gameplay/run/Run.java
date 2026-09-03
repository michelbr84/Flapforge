package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.gameplay.SimInput;
import io.github.michelbr84.flapforge.gameplay.Simulation;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One run from READY to FINISHED (D11); {@link #tick(RunInput)} is the single entry point for
 * both the game screen and the headless harness.
 *
 * <ul>
 *   <li>{@code READY}: the bird floats at its start position, nothing spawns or scrolls (upstream
 *       shows the welcome screen); the first flap edge starts the run and is applied on that
 *       same tick, so gates start with the first flap.</li>
 *   <li>{@code FLYING}: the simulation ticks; hold-to-flap and the ability edge are forwarded to
 *       it (D2, D9). The facts the tick produced update the stats: a passed gate, a coin, an
 *       ability activation, a shield absorb and a revive are counted here, so
 *       {@code RunStats.abilitiesUsed}, {@code shieldAbsorbs} and {@code revives} are a function
 *       of the tick report and never of a second code path.</li>
 *   <li>{@code BREATHER}: the roguelite draft pushed the next obstacle out and is waiting for
 *       clear air; the simulation runs exactly as in {@code FLYING} (D11, M6).</li>
 *   <li>{@code CHOOSING_MODIFIER} and {@code RESUME_HOLD}: the simulation is frozen. The tick
 *       still happens — it carries the player's answer to the draft and counts the 3-2-1 down —
 *       but nothing moves, nothing spawns and nothing can kill the bird, so these ticks are not
 *       counted in {@code ticksAlive}.</li>
 *   <li>{@code BOSS_WARNING} and {@code BOSS}: the simulation runs exactly as in {@code FLYING}
 *       (D11, M8) — the {@link BossEncounter} suppresses spawning during the warning and streams
 *       the phases during the fight. A {@code BossCleared} fact with a world id records the
 *       world in {@code RunStats.bossesCleared} (E26); an {@code ObjectiveMet} fact sets
 *       {@code objectiveMet}. Both are kept whatever happens afterwards.</li>
 *   <li>{@code DYING}: on a lethal hit the world freezes and the bird falls from +15 px/s (E28)
 *       to the ground line; ground contact while flying finishes the run on the same tick.</li>
 *   <li>{@code FINISHED}: further ticks do nothing; {@link #result()} is final.</li>
 * </ul>
 *
 * <p>The phase is derived, never invented: {@code ModifierDirector.State} is the authority on
 * where a draft is, and {@link #tick(RunInput)} maps it onto {@link RunPhase} after every tick so
 * the two can never disagree.
 */
public final class Run {

    private final RunConfig config;
    private final RunSetup setup;
    private final Simulation sim;
    private final RunStats stats = new RunStats();
    private RunPhase phase = RunPhase.READY;
    private int tick;
    private int obstaclesSpawned;
    private RunResult finalResult;

    /**
     * Creates a run.
     *
     * @param config the configuration
     * @param setup the resolved content
     */
    public Run(RunConfig config, RunSetup setup) {
        this(config, setup, null);
    }

    /**
     * Creates a run with an injected spawn table (E17: the test seam that makes the world
     * predictable).
     *
     * @param config the configuration
     * @param setup the resolved content
     * @param spawnTable the table to spawn from, or {@code null} for the world's own
     */
    public Run(RunConfig config, RunSetup setup, SpawnTable spawnTable) {
        this.config = Objects.requireNonNull(config, "config");
        this.setup = Objects.requireNonNull(setup, "setup");
        this.sim = new Simulation(config, setup, spawnTable);
        // The forced modifiers of a challenge or a daily were taken inside the simulation's
        // constructor, before any tick and therefore before any fact could carry them (D11).
        ModifierDirector draft = sim.modifiers();
        for (String id : draft.taken()) {
            stats.addModifierTaken(id);
        }
        for (String id : draft.activeSynergies()) {
            stats.addSynergyActivated(id);
        }
        stats.setModifierStreakCoins(draft.streakBonusCoins());
    }

    /**
     * Creates a run with the classic content.
     *
     * @param config the configuration
     * @return the run
     */
    public static Run classic(RunConfig config) {
        return new Run(config, RunSetup.CLASSIC);
    }

    /**
     * Advances the run by one tick.
     *
     * @param input the player intent
     * @return the facts of this tick (phase changes included)
     */
    public TickReport tick(RunInput input) {
        Objects.requireNonNull(input, "input");
        int index = tick++;
        switch (phase) {
            case READY:
                if (!input.flap()) {
                    return TickReport.empty(index);
                }
                List<TickFact> facts = new ArrayList<>();
                changePhase(RunPhase.FLYING, facts);
                return tickFlying(input, facts);
            case FLYING:
            case BREATHER:
            case BOSS_WARNING:
            case BOSS:
                return tickFlying(input, new ArrayList<>());
            case CHOOSING_MODIFIER:
            case RESUME_HOLD:
                return tickFrozen(input);
            case DYING:
                return tickDying();
            case FINISHED:
            default:
                return TickReport.empty(index);
        }
    }

    private TickReport tickFlying(RunInput input, List<TickFact> facts) {
        TickReport report = sim.tick(
                new SimInput(input.flap(), input.ability(), input.autoFlapHeld()));
        stats.tickAlive();
        if (sim.boss().hasBoss()) {
            stats.setPhasesReached(Math.max(stats.phasesReached(), sim.boss().phasesReached()));
        }
        facts.addAll(report.facts());
        CollisionCause cause = absorbFacts(report.facts());
        if (cause != null) {
            stats.setDeathCause(cause);
            sim.beginDying();
            changePhase(RunPhase.DYING, facts);
            if (cause == CollisionCause.GROUND) {
                sim.land();
                changePhase(RunPhase.FINISHED, facts);
            }
        } else {
            syncDraftPhase(facts);
        }
        return new TickReport(report.tick(), facts);
    }

    /**
     * One frozen tick of a draft: the simulation does not run, the player's answer reaches the
     * director and the countdown moves (D11).
     *
     * @param input the player intent; only {@link RunInput#choice()} matters here
     * @return the facts of this tick
     */
    private TickReport tickFrozen(RunInput input) {
        List<TickFact> facts = new ArrayList<>();
        sim.modifiers().tickFrozen(input.choice(), facts);
        absorbFacts(facts);
        syncDraftPhase(facts);
        return new TickReport(sim.tick(), facts);
    }

    /**
     * Folds a tick's facts into the stats.
     *
     * @param facts the facts produced this tick
     * @return the cause of a lethal hit that was not absorbed, or {@code null}
     */
    private CollisionCause absorbFacts(List<TickFact> facts) {
        CollisionCause cause = null;
        for (TickFact f : facts) {
            if (f instanceof TickFact.GatePassed) {
                stats.setGatesPassed(sim.gatesPassed());
                stats.setPoints(sim.points());
            } else if (f instanceof TickFact.NearMiss) {
                stats.countNearMiss();
            } else if (f instanceof TickFact.CoinCollected coin) {
                stats.addCoinsCollected(coin.value());
            } else if (f instanceof TickFact.StreakChanged streak) {
                stats.setStreak(streak.streak());
                stats.setStreakSteps(sim.streaks().steps());
            } else if (f instanceof TickFact.ObstacleSpawned) {
                obstaclesSpawned++;
            } else if (f instanceof TickFact.AbilityActivated used) {
                stats.countAbilityUse(used.abilityId());
            } else if (f instanceof TickFact.ShieldAbsorbed) {
                stats.countShieldAbsorb();
            } else if (f instanceof TickFact.Revived) {
                stats.countRevive();
            } else if (f instanceof TickFact.ModifierChosen chosen) {
                stats.addModifierTaken(chosen.modifierId());
                stats.setModifierStreakCoins(sim.modifiers().streakBonusCoins());
            } else if (f instanceof TickFact.SynergyActivated synergy) {
                stats.addSynergyActivated(synergy.id());
            } else if (f instanceof TickFact.BossCleared cleared) {
                // E26: only a world boss clears a world; a challenge boss carries no world and
                // only the objective reads it.
                if (cleared.worldId() != null) {
                    stats.addBossCleared(cleared.worldId());
                }
            } else if (f instanceof TickFact.ObjectiveMet) {
                stats.setObjectiveMet(true);
            } else if (f instanceof TickFact.Crashed crashed) {
                cause = crashed.cause();
                stats.setDeathKind(crashed.kind());
            }
        }
        return cause;
    }

    /**
     * Brings the run phase in line with the draft the director is running and the boss the
     * encounter is running (D11). The two never overlap in their frozen half — the director
     * refuses to freeze the run while a boss is pending or active (E7) — and where they do
     * overlap in flight (a breather waiting for clear air while the warning starts) the boss
     * phase wins, because that is what the HUD timer and the banner are about.
     *
     * @param facts where the {@code PhaseChanged} goes
     */
    private void syncDraftPhase(List<TickFact> facts) {
        RunPhase target = phaseOf(sim.modifiers().state(), sim.boss());
        if (target != phase) {
            changePhase(target, facts);
        }
    }

    private static RunPhase phaseOf(ModifierDirector.State state, BossEncounter boss) {
        switch (state) {
            case CHOOSING:
                return RunPhase.CHOOSING_MODIFIER;
            case HOLD:
                return RunPhase.RESUME_HOLD;
            default:
                break;
        }
        if (boss.isWarning()) {
            return RunPhase.BOSS_WARNING;
        }
        if (boss.isFighting()) {
            return RunPhase.BOSS;
        }
        return state == ModifierDirector.State.BREATHER ? RunPhase.BREATHER : RunPhase.FLYING;
    }

    private TickReport tickDying() {
        List<TickFact> facts = new ArrayList<>(1);
        if (sim.tickDying()) {
            changePhase(RunPhase.FINISHED, facts);
        }
        return new TickReport(sim.tick(), facts);
    }

    private void changePhase(RunPhase to, List<TickFact> facts) {
        RunPhase from = phase;
        phase = to;
        facts.add(new TickFact.PhaseChanged(from, to));
    }

    /**
     * Snapshot of the outcome (final once {@link #isFinished()}).
     *
     * <p>Once the run is {@code FINISHED} the same instance is returned to every caller. Nothing
     * can change after that, so a fresh snapshot per call differs only by identity — and that
     * identity is exactly what {@code ProgressionManager}'s "apply a run once" guard compares, so
     * two callers reading the result of one finished run must not be able to pay it twice (D14).
     *
     * @return the result
     */
    public RunResult result() {
        if (finalResult != null) {
            return finalResult;
        }
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("gates", (long) stats.gatesPassed());
        counters.put("points", Math.round(stats.points()));
        counters.put("ticks", (long) stats.ticksAlive());
        counters.put("flaps", (long) sim.flaps());
        counters.put("flapsRefused", (long) sim.flapsRefused());
        counters.put("coins", (long) stats.coinsCollected());
        counters.put("streakBest", (long) stats.streakBest());
        counters.put("nearMisses", (long) stats.nearMisses());
        counters.put("obstaclesSpawned", (long) obstaclesSpawned);
        RunResult result = new RunResult(config, stats.copy(), counters);
        if (phase == RunPhase.FINISHED) {
            finalResult = result;
        }
        return result;
    }

    /**
     * Current phase.
     *
     * @return the phase
     */
    public RunPhase phase() {
        return phase;
    }

    /**
     * Tells whether the run reached {@code FINISHED}.
     *
     * @return {@code true} when over
     */
    public boolean isFinished() {
        return phase == RunPhase.FINISHED;
    }

    /**
     * Ticks processed so far in every phase (the index of the next tick).
     *
     * @return the count
     */
    public int tick() {
        return tick;
    }

    /**
     * The configuration.
     *
     * @return the config
     */
    public RunConfig config() {
        return config;
    }

    /**
     * The resolved content.
     *
     * @return the setup
     */
    public RunSetup setup() {
        return setup;
    }

    /**
     * The gameplay model.
     *
     * @return the simulation
     */
    public Simulation simulation() {
        return sim;
    }

    /**
     * The live stats.
     *
     * @return the stats
     */
    public RunStats stats() {
        return stats;
    }
}
