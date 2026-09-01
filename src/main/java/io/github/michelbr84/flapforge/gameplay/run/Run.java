package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.gameplay.SimInput;
import io.github.michelbr84.flapforge.gameplay.Simulation;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
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
 *   <li>{@code FLYING}: the simulation ticks; hold-to-flap is forwarded to it (D2).</li>
 *   <li>{@code DYING}: on a lethal hit the world freezes and the bird falls from +15 px/s (E28)
 *       to the ground line; ground contact while flying finishes the run on the same tick.</li>
 *   <li>{@code FINISHED}: further ticks do nothing; {@link #result()} is final.</li>
 * </ul>
 */
public final class Run {

    private final RunConfig config;
    private final RunSetup setup;
    private final Simulation sim;
    private final RunStats stats = new RunStats();
    private RunPhase phase = RunPhase.READY;
    private int tick;
    private int obstaclesSpawned;

    /**
     * Creates a run.
     *
     * @param config the configuration
     * @param setup the resolved content
     */
    public Run(RunConfig config, RunSetup setup) {
        this.config = Objects.requireNonNull(config, "config");
        this.setup = Objects.requireNonNull(setup, "setup");
        this.sim = new Simulation(config, setup);
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
                return tickFlying(input, new ArrayList<>());
            case DYING:
                return tickDying();
            case FINISHED:
            default:
                return TickReport.empty(index);
        }
    }

    private TickReport tickFlying(RunInput input, List<TickFact> facts) {
        TickReport report = sim.tick(new SimInput(input.flap(), input.autoFlapHeld()));
        stats.tickAlive();
        facts.addAll(report.facts());
        CollisionCause cause = null;
        for (TickFact f : report.facts()) {
            if (f instanceof TickFact.GatePassed) {
                stats.setGatesPassed(sim.gatesPassed());
                stats.setPoints(sim.points());
            } else if (f instanceof TickFact.NearMiss) {
                stats.countNearMiss();
            } else if (f instanceof TickFact.ObstacleSpawned) {
                obstaclesSpawned++;
            } else if (f instanceof TickFact.Crashed crashed) {
                cause = crashed.cause();
            }
        }
        if (cause != null) {
            stats.setDeathCause(cause);
            sim.beginDying();
            changePhase(RunPhase.DYING, facts);
            if (cause == CollisionCause.GROUND) {
                sim.land();
                changePhase(RunPhase.FINISHED, facts);
            }
        }
        return new TickReport(report.tick(), facts);
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
     * @return the result
     */
    public RunResult result() {
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("gates", (long) stats.gatesPassed());
        counters.put("points", Math.round(stats.points()));
        counters.put("ticks", (long) stats.ticksAlive());
        counters.put("flaps", (long) sim.flaps());
        counters.put("flapsRefused", (long) sim.flapsRefused());
        counters.put("nearMisses", (long) stats.nearMisses());
        counters.put("obstaclesSpawned", (long) obstaclesSpawned);
        return new RunResult(config, stats.copy(), counters);
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
