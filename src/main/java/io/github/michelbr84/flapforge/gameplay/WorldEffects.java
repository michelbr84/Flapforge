package io.github.michelbr84.flapforge.gameplay;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.spec.AmbientSpec;
import io.github.michelbr84.flapforge.gameplay.spec.RuleCycleSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * What a world does to a run beyond its obstacles (§4 {@code ambient} and {@code ruleCycles},
 * D8, E8, M7): the ambient wind, the darkness, the cosmetic sky flashes and the Void's rule
 * shifts.
 *
 * <ul>
 *   <li><b>Ambient wind</b> is the {@code WindZone} mechanism made permanent:
 *       {@link #applyAmbientWind} adds {@code windY} to the bird's vertical wind accumulator and
 *       {@code windX} to its scroll accumulator every tick, before the zones are sampled, so the
 *       integration and the scroll see one summed wind exactly as they do inside a zone.</li>
 *   <li><b>Darkness</b> is a number the renderer reads ({@link #darkness()}); the simulation does
 *       nothing with it.</li>
 *   <li><b>Sky flashes</b> (E8): every {@code lightningEveryGates} gates
 *       {@link #onGatePassed} appends an {@link TickFact.AmbientFlash} — a fact and nothing else:
 *       no hitbox, no stream, no rule. Lethal bolts are the {@code lightning} kind.</li>
 *   <li><b>Rule cycles</b>: every {@code everyGates} gates the next option is drawn from the
 *       {@code cycles} stream — uniformly among the options that are not the one in force, so a
 *       shift is always a change — and announced with a {@link TickFact.RuleShift} carrying its
 *       flags, its effects and the telegraph length. {@link #tick} counts the telegraph down on
 *       flying ticks and reports when the option lands; the simulation then applies it — flags
 *       into the run's rules, effects into {@code WORLD_CYCLE}, replacing the previous option's.
 *       A landing is refused while a draft is running (breather, choice, hold) and happens on the
 *       first flying tick after it instead. A cadence gate reached while the previous option is
 *       still pending draws nothing and announces nothing: one announcement is one landing, so
 *       the banner never promises a rule that is then replaced before it arrives.</li>
 * </ul>
 *
 * <p>A world with a still, bright, flash-free ambience and no cycles is {@link #isActive()
 * inactive}: it draws nothing, emits nothing and folds nothing into the state hash, which keeps
 * Green Fields — and the published {@code --headless-run} hash — where M6 left them.
 */
public final class WorldEffects {

    private static final long HASH_SEED = MathUtil.fnv1a64("world-effects");

    private final AmbientSpec ambient;
    private final RuleCycleSpec cycles;
    private final Random cyclesRng;
    private int activeIndex = -1;
    private int pendingIndex = -1;
    private int telegraphRemaining;
    private boolean announcedThisTick;
    private int shifts;
    private int flashes;
    private int announcements;

    /**
     * Creates the effects of a world.
     *
     * @param ambient the ambience
     * @param cycles the rule cycles, or {@code null}
     * @param cyclesRng the run's {@code cycles} stream; may be {@code null} when there are no
     *     cycles
     */
    public WorldEffects(AmbientSpec ambient, RuleCycleSpec cycles, Random cyclesRng) {
        this.ambient = Objects.requireNonNull(ambient, "ambient");
        this.cycles = cycles;
        if (cycles != null) {
            Objects.requireNonNull(cyclesRng, "cyclesRng");
        }
        this.cyclesRng = cyclesRng;
    }

    /** No ambience, no cycles: the effects of Green Fields and of every seam without a world. */
    public static WorldEffects none() {
        return new WorldEffects(AmbientSpec.NONE, null, null);
    }

    /**
     * Whether this world changes anything: an active ambience or rule cycles.
     *
     * @return {@code true} when there is something to sample, announce or hash
     */
    public boolean isActive() {
        return ambient.isActive() || cycles != null;
    }

    /**
     * The ambience.
     *
     * @return the ambience
     */
    public AmbientSpec ambient() {
        return ambient;
    }

    /**
     * How much of the playfield the renderer hides.
     *
     * @return the darkness in {@code [0, 1]}
     */
    public double darkness() {
        return ambient.darkness();
    }

    /**
     * The rule cycles.
     *
     * @return the cycles, or {@code null}
     */
    public RuleCycleSpec cycles() {
        return cycles;
    }

    /**
     * Adds the ambient wind to the bird's accumulators for this tick (nothing without wind).
     *
     * @param bird the bird
     */
    public void applyAmbientWind(Bird bird) {
        if (ambient.hasWind()) {
            bird.applyWind(ambient.windY(), ambient.windX());
        }
    }

    /**
     * Reacts to one passed gate: a sky flash on the flash cadence, and on the cycle cadence the
     * next option is drawn and its telegraph starts.
     *
     * @param gatesPassed the gate count after the gate
     * @param facts where {@code AmbientFlash} and {@code RuleShift} go
     */
    public void onGatePassed(int gatesPassed, List<TickFact> facts) {
        if (ambient.hasFlashes() && gatesPassed % ambient.lightningEveryGates() == 0) {
            flashes++;
            facts.add(new TickFact.AmbientFlash());
        }
        if (cycles != null && pendingIndex < 0 && gatesPassed % cycles.everyGates() == 0) {
            pendingIndex = drawNextOption();
            telegraphRemaining = cycles.telegraphTicks();
            announcedThisTick = true;
            announcements++;
            RuleCycleSpec.Option option = cycles.options().get(pendingIndex);
            facts.add(new TickFact.RuleShift(new ArrayList<>(option.flags().flags()),
                    option.effects(), telegraphRemaining));
        }
    }

    /**
     * Draws the next option: uniform among the options other than the active one, so the shift
     * always changes something. With a single option there is nothing else to draw, and the
     * stream is still consumed so the cadence of draws does not depend on the option count.
     *
     * @return the index of the option to land next
     */
    private int drawNextOption() {
        int count = cycles.options().size();
        if (activeIndex < 0 || count == 1) {
            return cyclesRng.nextInt(count);
        }
        int roll = cyclesRng.nextInt(count - 1);
        return roll >= activeIndex ? roll + 1 : roll;
    }

    /**
     * One flying tick: counts the telegraph down and lands the pending option when the countdown
     * is over and the run is free to shift. The tick that announced the shift is not counted, so
     * a telegraph of {@code n} lands exactly {@code n} flying ticks after the fact (and one of 0
     * on the next tick).
     *
     * @param mayLand {@code false} while a draft is running (the shift is deferred to the next
     *     tick that says {@code true})
     * @return {@code true} when an option landed on this tick, so the caller applies
     *     {@link #activeOption()}
     */
    public boolean tick(boolean mayLand) {
        if (pendingIndex < 0) {
            return false;
        }
        if (announcedThisTick) {
            announcedThisTick = false;
            return false;
        }
        if (telegraphRemaining > 0) {
            telegraphRemaining--;
        }
        if (telegraphRemaining > 0 || !mayLand) {
            return false;
        }
        activeIndex = pendingIndex;
        pendingIndex = -1;
        shifts++;
        return true;
    }

    /**
     * The option in force.
     *
     * @return the option, or {@code null} before the first shift lands
     */
    public RuleCycleSpec.Option activeOption() {
        return activeIndex < 0 ? null : cycles.options().get(activeIndex);
    }

    /**
     * The index of the option in force.
     *
     * @return the index, {@code -1} before the first shift lands
     */
    public int activeIndex() {
        return activeIndex;
    }

    /**
     * The option announced and not yet landed.
     *
     * @return the option, or {@code null}
     */
    public RuleCycleSpec.Option pendingOption() {
        return pendingIndex < 0 ? null : cycles.options().get(pendingIndex);
    }

    /**
     * Whether a shift is announced and not yet landed.
     *
     * @return {@code true} during a telegraph (or while a landing is deferred)
     */
    public boolean isTelegraphing() {
        return pendingIndex >= 0;
    }

    /**
     * Flying ticks left before the pending option may land.
     *
     * @return the ticks, 0 when nothing is pending or the landing is only waiting for the run
     */
    public int telegraphRemaining() {
        return telegraphRemaining;
    }

    /**
     * The rule flags of the option in force.
     *
     * @return the flags, empty before the first shift
     */
    public RuleSet cycleFlags() {
        RuleCycleSpec.Option option = activeOption();
        return option == null ? RuleSet.EMPTY : option.flags();
    }

    /**
     * The effects of the option in force, for the {@code WORLD_CYCLE} layer.
     *
     * @return the effects, empty before the first shift
     */
    public List<StatModifier> cycleEffects() {
        RuleCycleSpec.Option option = activeOption();
        return option == null ? List.of() : option.effects();
    }

    /**
     * Shifts that landed so far.
     *
     * @return the count
     */
    public int shifts() {
        return shifts;
    }

    /**
     * Shifts announced so far: every announcement lands, so this is {@link #shifts()} plus one
     * while a telegraph is running.
     *
     * @return the count
     */
    public int announcements() {
        return announcements;
    }

    /**
     * Sky flashes announced so far.
     *
     * @return the count
     */
    public int flashes() {
        return flashes;
    }

    /**
     * Folds the per-tick state into a hash (D12): the option in force, the pending one, the
     * countdown and the counters.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, HASH_SEED);
        h = MathUtil.fold(h, activeIndex);
        h = MathUtil.fold(h, pendingIndex);
        h = MathUtil.fold(h, telegraphRemaining);
        h = MathUtil.fold(h, shifts);
        return MathUtil.fold(h, flashes);
    }
}
