package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;

/**
 * The content-free {@link SeededRunSource}: Forgewing in Green Fields on the normal tier, built
 * straight from the gameplay seam records ({@code BirdProfile.CLASSIC} through
 * {@link RunSetup#CLASSIC}, {@code CurveSpec.CLASSIC} through {@code WorldSpec.GREEN_FIELDS})
 * with no content files. The game itself plays through {@link ContentRunFactory}; this one keeps
 * tests and tools independent of the classpath, and for M1 the two are equivalent by
 * construction.
 *
 * <p>The run mode is {@link RunMode#SEEDED} when the seed came from {@code --seed} and
 * {@link RunMode#STANDARD} otherwise, so the HUD and later the summary can tell the two apart.
 */
public final class ClassicRunFactory implements SeededRunSource {

    private final RunMode mode;

    /** Creates a factory producing {@link RunMode#STANDARD} runs. */
    public ClassicRunFactory() {
        this(RunMode.STANDARD);
    }

    /**
     * Creates a factory producing runs in a given mode.
     *
     * @param mode the run mode
     */
    public ClassicRunFactory(RunMode mode) {
        this.mode = mode;
    }

    @Override
    public Run newRun(long seed) {
        RunConfig config = RunConfig.builder(seed).mode(mode).build();
        return new Run(config, RunSetup.CLASSIC);
    }
}
