package io.github.michelbr84.flapforge.support;

import io.github.michelbr84.flapforge.gameplay.harness.Pilot;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Replays a flap trace: a flap edge on every tick index in the set (D21). Tick indices count
 * every {@link Run#tick} call from 0, READY ticks included.
 *
 * <p>It lives in {@code test/support} (§3): unlike {@code BotPilot} and {@code HeadlessRunner},
 * which the balancing tool and the {@code --headless-run} determinism line need, a replay helper
 * has no production caller.
 */
public final class ScriptedPilot implements Pilot {

    private final Set<Integer> flapTicks;
    private final boolean autoFlapHeld;

    /**
     * Creates a pilot.
     *
     * @param flapTicks tick indices that flap
     */
    public ScriptedPilot(Collection<Integer> flapTicks) {
        this(flapTicks, false);
    }

    /**
     * Creates a pilot that may also hold the flap key.
     *
     * @param flapTicks tick indices that flap
     * @param autoFlapHeld {@code true} to report hold-to-flap on every tick
     */
    public ScriptedPilot(Collection<Integer> flapTicks, boolean autoFlapHeld) {
        this.flapTicks = new TreeSet<>(flapTicks);
        this.autoFlapHeld = autoFlapHeld;
    }

    /**
     * Creates a pilot from tick indices.
     *
     * @param ticks the indices
     * @return the pilot
     */
    public static ScriptedPilot flapsAt(int... ticks) {
        TreeSet<Integer> set = new TreeSet<>();
        for (int t : ticks) {
            set.add(t);
        }
        return new ScriptedPilot(set);
    }

    @Override
    public RunInput decide(Run run) {
        boolean flap = flapTicks.contains(run.tick());
        return new RunInput(flap, false, RunInput.NO_CHOICE, autoFlapHeld);
    }
}
