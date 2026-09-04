package io.github.michelbr84.flapforge.gameplay.harness;

import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;

/** Something that produces the input of the next run tick (D21). */
@FunctionalInterface
public interface Pilot {

    /**
     * Decides the input for the tick about to run.
     *
     * @param run the run (read-only for the pilot; {@code run.tick()} is the index of the tick
     *     about to execute)
     * @return the input
     */
    RunInput decide(Run run);
}
