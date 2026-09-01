package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import java.util.Objects;

/**
 * The {@link SeededRunSource} the game ships with: every run is assembled from the content files
 * (D10, D11) instead of the hard-coded seam records. The ids in the {@link RunConfig} — bird,
 * world, tier — are resolved against the registries of a {@link GameContent} loaded once at
 * startup, so the balance the player feels is the balance in {@code data/*.json}.
 *
 * <p>It is a thin adapter over {@link RunFactory}: the content factory knows how to turn a
 * configuration into a {@code RunSetup}, this class knows which configuration a screen wants
 * (the default one for a seed, in the mode the launch chose).
 * {@link ClassicRunFactory} stays for tests and tools that must not touch the classpath.
 *
 * <p>For M1 the two are interchangeable by construction — {@code ContentIntegrityTest
 * .contentBuildsTheSameSetupAsTheHardCodedClassicSeam} asserts the shipped files resolve to
 * {@code RunSetup.CLASSIC} — and they diverge the moment the data files do.
 */
public final class ContentRunFactory implements SeededRunSource {

    private final RunFactory runs;
    private final RunMode mode;

    /**
     * Creates a factory producing {@link RunMode#STANDARD} runs.
     *
     * @param content the loaded content
     */
    public ContentRunFactory(GameContent content) {
        this(content, RunMode.STANDARD);
    }

    /**
     * Creates a factory producing runs in a given mode.
     *
     * @param content the loaded content
     * @param mode the run mode ({@link RunMode#SEEDED} when the seed came from {@code --seed})
     */
    public ContentRunFactory(GameContent content, RunMode mode) {
        this.runs = new RunFactory(Objects.requireNonNull(content, "content"));
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * The content every run is built from.
     *
     * @return the content
     */
    public GameContent content() {
        return runs.content();
    }

    /**
     * The mode stamped on every run this factory builds.
     *
     * @return the run mode
     */
    public RunMode mode() {
        return mode;
    }

    @Override
    public Run newRun(long seed) {
        return runs.newRun(RunConfig.builder(seed).mode(mode).build());
    }
}
