package io.github.michelbr84.flapforge.gameplay.harness;

import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Drives a {@link Run} with a {@link Pilot} until it finishes or a tick budget runs out (D21).
 */
public final class HeadlessRunner {

    /**
     * What a headless run produced.
     *
     * @param result the run result (a snapshot when the budget ran out first)
     * @param ticks ticks executed
     * @param finished whether the run reached {@code FINISHED}
     * @param hashes per-tick state hashes when requested, else empty
     */
    public record Outcome(RunResult result, int ticks, boolean finished, List<Long> hashes) {

        /**
         * Copies the hash list.
         *
         * @param result the run result
         * @param ticks ticks executed
         * @param finished whether the run finished
         * @param hashes per-tick hashes
         */
        public Outcome {
            Objects.requireNonNull(result, "result");
            hashes = List.copyOf(hashes);
        }
    }

    private HeadlessRunner() {
    }

    /**
     * Runs an existing run.
     *
     * @param run the run
     * @param pilot the pilot
     * @param maxTicks the tick budget
     * @param recordHashes {@code true} to collect {@code stateHash()} after every tick
     * @return the outcome
     */
    public static Outcome run(Run run, Pilot pilot, int maxTicks, boolean recordHashes) {
        List<Long> hashes = recordHashes ? new ArrayList<>(maxTicks) : List.of();
        int ticks = 0;
        while (!run.isFinished() && ticks < maxTicks) {
            RunInput input = pilot.decide(run);
            run.tick(input);
            ticks++;
            if (recordHashes) {
                hashes.add(run.simulation().stateHash());
            }
        }
        return new Outcome(run.result(), ticks, run.isFinished(), hashes);
    }

    /**
     * Runs an existing run without hashes.
     *
     * @param run the run
     * @param pilot the pilot
     * @param maxTicks the tick budget
     * @return the outcome
     */
    public static Outcome run(Run run, Pilot pilot, int maxTicks) {
        return run(run, pilot, maxTicks, false);
    }

    /**
     * Builds a run from a configuration and resolved content, then runs it.
     *
     * @param config the configuration
     * @param setup the resolved content
     * @param pilot the pilot
     * @param maxTicks the tick budget
     * @param recordHashes {@code true} to collect per-tick hashes
     * @return the outcome
     */
    public static Outcome run(RunConfig config, RunSetup setup, Pilot pilot, int maxTicks,
            boolean recordHashes) {
        return run(new Run(config, setup), pilot, maxTicks, recordHashes);
    }

    /**
     * Runs a configuration with the classic content.
     *
     * @param config the configuration
     * @param pilot the pilot
     * @param maxTicks the tick budget
     * @return the outcome
     */
    public static Outcome run(RunConfig config, Pilot pilot, int maxTicks) {
        return run(config, RunSetup.CLASSIC, pilot, maxTicks, false);
    }
}
