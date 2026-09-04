package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleSpawner;
import io.github.michelbr84.flapforge.gameplay.obstacle.PatternStreamer;
import io.github.michelbr84.flapforge.gameplay.spec.BossSpec;
import java.util.List;
import java.util.Objects;

/**
 * The boss encounter of one run (D11, E7, E26, M8), owned by {@code Simulation} like the
 * {@link ModifierDirector}: a warning, a fight, a clear.
 *
 * <ol>
 *   <li><b>AHEAD</b> — the boss has not started. From {@code gatesPassed >= atGate - 1} it is
 *       {@linkplain #isPending(int) pending} and no draft may open (E7).</li>
 *   <li><b>WARNING</b> — the tick {@code gatesPassed} reaches {@code atGate}: spawning is
 *       suppressed, a {@link TickFact.BossWarning} goes out with the boss id and the world id,
 *       and the run is in {@code BOSS_WARNING} for {@code warningTicks} flying ticks while the
 *       columns already in the world scroll out. A rule shift may not land here — the
 *       simulation defers it like a draft.</li>
 *   <li><b>ACTIVE</b> — {@link TickFact.BossStarted}: spawning resumes and the streamer plays the
 *       phases in order, looped, for {@code surviveTicks} flying ticks. Scoring steps keep
 *       scoring, coins keep spawning, the streak and the difficulty curve keep advancing;
 *       {@link #phasesReached()} is the 1-based index of the furthest phase whose first step was
 *       placed (the maximum over the run, so a loop back to phase 1 does not lower it).</li>
 *   <li><b>CLEARED</b> — {@link TickFact.BossCleared}: no further phase step is placed, the boss
 *       columns still in the world scroll out, and the next ordinary spawn is pushed
 *       {@value #RESUME_INTERVALS} gate intervals out ({@link ObstacleSpawner#deferNextSpawn}).
 *       Only a world boss clears a world: {@code Run} writes {@code RunStats.bossesCleared} from
 *       the fact's {@code worldId}, which a challenge boss leaves {@code null} (E26); a
 *       challenge boss only sets {@link #isCleared()}, which the objective reads.</li>
 * </ol>
 *
 * <p>Dying during the warning or the fight ends the run normally: nothing is cleared and
 * nothing is paid. A cleared boss stays cleared whatever happens afterwards, so the clear is
 * granted at run end even when the bird crashes later (D11).
 *
 * <p><b>Determinism.</b> The encounter draws nothing: the phases are authored patterns placed
 * through the same cursor as a forced pattern, and every counter here is folded into the state
 * hash ({@link #hashState}). A run without a boss allocates nothing and folds nothing, which is
 * what keeps the pinned classic run — and the published {@code --headless-run} hash — where
 * M1 left them.
 */
public final class BossEncounter {

    /** Gate intervals the next ordinary spawn is pushed out by after the clear (D11). */
    public static final double RESUME_INTERVALS = 1.5;

    private static final long HASH_SEED = MathUtil.fnv1a64("flapforge-boss");

    /** Where the encounter is. */
    public enum State {
        /** Not started; pending from {@code atGate - 1}. */
        AHEAD,
        /** The warning: spawns suppressed, countdown running. */
        WARNING,
        /** The fight: phases streaming, survival countdown running. */
        ACTIVE,
        /** Survived; the remaining boss columns scroll out. */
        CLEARED
    }

    private final BossSpec spec;
    private final ObstacleSpawner spawner;
    private final PatternStreamer streamer;
    private State state = State.AHEAD;
    private int remaining;
    private int phasesReached;

    /**
     * Creates the encounter of one run.
     *
     * @param spec the boss, or {@code null} for a run without one
     * @param spawner the run's spawner; its streamer must carry the boss phases when there is a
     *     boss
     */
    public BossEncounter(BossSpec spec, ObstacleSpawner spawner) {
        this.spec = spec;
        this.spawner = Objects.requireNonNull(spawner, "spawner");
        this.streamer = spawner.streamer();
        if (spec != null) {
            if (streamer == null || streamer.bossPhases().isEmpty()) {
                throw new IllegalStateException("boss '" + spec.id()
                        + "' needs a streamer carrying its phases");
            }
        }
    }

    /**
     * An encounter for a run without a boss.
     *
     * @param spawner the run's spawner
     * @return the encounter; every query answers "no boss"
     */
    public static BossEncounter none(ObstacleSpawner spawner) {
        return new BossEncounter(null, spawner);
    }

    /**
     * Advances the encounter on a flying tick, after the tick's scoring and before the spawner
     * runs, so a warning that starts here already suppresses this tick's spawn and a fight that
     * starts here already places its first column.
     *
     * @param gatesPassed gates passed so far
     * @param facts where the boss facts go
     */
    public void tick(int gatesPassed, List<TickFact> facts) {
        if (spec == null) {
            return;
        }
        switch (state) {
            case AHEAD:
                if (gatesPassed >= spec.atGate()) {
                    state = State.WARNING;
                    remaining = spec.warningTicks();
                    spawner.setSuppressed(true);
                    facts.add(new TickFact.BossWarning(spec.id(), spec.worldId(),
                            spec.warningTicks()));
                    if (remaining <= 0) {
                        start(facts);
                    }
                }
                break;
            case WARNING:
                if (--remaining <= 0) {
                    start(facts);
                }
                break;
            case ACTIVE:
                if (--remaining <= 0) {
                    remaining = 0;
                    state = State.CLEARED;
                    streamer.endBoss();
                    spawner.deferNextSpawn(RESUME_INTERVALS);
                    facts.add(new TickFact.BossCleared(spec.id(), spec.worldId()));
                }
                break;
            case CLEARED:
            default:
                break;
        }
    }

    private void start(List<TickFact> facts) {
        state = State.ACTIVE;
        remaining = spec.surviveTicks();
        streamer.startBoss();
        spawner.setSuppressed(false);
        facts.add(new TickFact.BossStarted(spec.id(), spec.surviveTicks()));
    }

    /**
     * Notes that the spawner placed a column this tick, so {@link #phasesReached()} follows the
     * phase it came from.
     */
    public void onSpawned() {
        if (state == State.ACTIVE) {
            phasesReached = Math.max(phasesReached,
                    Math.min(streamer.bossPhasesStarted(), spec.patterns().size()));
        }
    }

    /**
     * Whether the run has a boss at all.
     *
     * @return {@code true} when a spec is set
     */
    public boolean hasBoss() {
        return spec != null;
    }

    /**
     * The boss.
     *
     * @return the spec, or {@code null} for a run without one
     */
    public BossSpec spec() {
        return spec;
    }

    /**
     * Where the encounter is.
     *
     * @return the state; {@link State#AHEAD} forever for a run without a boss
     */
    public State state() {
        return state;
    }

    /**
     * E7: whether the boss is one gate away or closer and still ahead.
     *
     * @param gatesPassed gates passed so far
     * @return {@code true} while pending
     */
    public boolean isPending(int gatesPassed) {
        return spec != null && state == State.AHEAD && gatesPassed >= spec.atGate() - 1;
    }

    /**
     * Whether the warning is running.
     *
     * @return {@code true} in {@link State#WARNING}
     */
    public boolean isWarning() {
        return state == State.WARNING;
    }

    /**
     * Whether the fight is running.
     *
     * @return {@code true} in {@link State#ACTIVE}
     */
    public boolean isFighting() {
        return state == State.ACTIVE;
    }

    /**
     * E7: whether the encounter is running — the warning or the fight.
     *
     * @return {@code true} in {@link State#WARNING} and {@link State#ACTIVE}
     */
    public boolean isActive() {
        return state == State.WARNING || state == State.ACTIVE;
    }

    /**
     * Whether the boss was survived.
     *
     * @return {@code true} in {@link State#CLEARED}
     */
    public boolean isCleared() {
        return state == State.CLEARED;
    }

    /**
     * Flying ticks left in the current countdown: the warning's, then the fight's (the HUD
     * timer, D17).
     *
     * @return the ticks, 0 outside the two countdowns
     */
    public int ticksRemaining() {
        return isActive() ? remaining : 0;
    }

    /**
     * The furthest phase reached, 1-based (D11 {@code phasesReached}).
     *
     * @return the phase count, 0 before the first phase step is placed
     */
    public int phasesReached() {
        return phasesReached;
    }

    /**
     * Folds the encounter into the run's state hash (D12): the state, the countdown and the
     * phase count. Called only for a run with a boss.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, HASH_SEED);
        h = MathUtil.fold(h, state.ordinal());
        h = MathUtil.fold(h, remaining);
        return MathUtil.fold(h, phasesReached);
    }
}
