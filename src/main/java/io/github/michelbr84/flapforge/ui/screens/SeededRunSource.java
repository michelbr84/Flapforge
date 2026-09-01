package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.gameplay.run.Run;

/**
 * Builds the {@link Run} a {@link GameScreen} plays, one seed at a time.
 *
 * <p>The game plays through {@link ContentRunFactory}, which resolves the ids of the selected
 * bird, world and tier through {@code GameContent} — {@code GameApplication} loads the content
 * once and injects it. {@link ClassicRunFactory} assembles the same run from the hard-coded seam
 * records ({@code BirdProfile.CLASSIC}, {@code CurveSpec.CLASSIC}, {@code RunConfig.classic}) and
 * stays for tests and tools that must not touch the classpath. Nothing in the screen or the
 * renderers depends on which one is used, because everything they read comes from the {@code Run}.
 *
 * <p>The name deliberately avoids {@code RunFactory}: that is
 * {@link io.github.michelbr84.flapforge.content.RunFactory}, the content-backed builder this
 * seam's implementations delegate to, and two types with one simple name forced fully-qualified
 * references into both.
 */
public interface SeededRunSource {

    /**
     * Creates a fresh run.
     *
     * @param seed the run seed; every random stream of the run derives from it (D12)
     * @return a run in {@code READY}
     */
    Run newRun(long seed);
}
