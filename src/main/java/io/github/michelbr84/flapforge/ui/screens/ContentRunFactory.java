package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import java.util.Objects;
import java.util.function.Supplier;

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
 *
 * <p>From M4 the factory also reads the profile, when the session has one: the run is built by
 * {@link RunLoadout} from what the player has selected and bought, so an upgrade node changes the
 * physics of the very next run (D8, D14). A session without persistence — the headless launch and
 * most tests — passes no profile supplier and gets the default configuration for the seed, which
 * is what keeps the shipped-content hash a function of the seed and the data alone (D12).
 *
 * <p>From M7 a launch can pin the world ({@code --world}, {@link #withWorld(String)}): the
 * override replaces the world of every run this factory builds, profile or not, and is never
 * written back into the profile — it is a launch flag, so the next normal launch is back to
 * what the player selected. An id the content does not ship is ignored with a log line.
 */
public final class ContentRunFactory implements SeededRunSource {

    private final RunFactory runs;
    private final RunMode mode;
    private final Supplier<PlayerProfile> profiles;
    private String worldOverride;

    /**
     * Creates a factory producing {@link RunMode#STANDARD} runs with no profile.
     *
     * @param content the loaded content
     */
    public ContentRunFactory(GameContent content) {
        this(content, RunMode.STANDARD);
    }

    /**
     * Creates a factory producing runs in a given mode with no profile.
     *
     * @param content the loaded content
     * @param mode the run mode ({@link RunMode#SEEDED} when the seed came from {@code --seed})
     */
    public ContentRunFactory(GameContent content, RunMode mode) {
        this(content, mode, null);
    }

    /**
     * Creates a factory that builds every run from the live profile.
     *
     * @param content the loaded content
     * @param mode the run mode
     * @param profiles the live profile of the session, read at the start of every run; may be
     *     {@code null}, and may return {@code null} in a session without persistence
     */
    public ContentRunFactory(GameContent content, RunMode mode, Supplier<PlayerProfile> profiles) {
        this.runs = new RunFactory(Objects.requireNonNull(content, "content"));
        this.mode = Objects.requireNonNull(mode, "mode");
        this.profiles = profiles;
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

    /**
     * Pins the world of every run this factory builds (M7, {@code --world}).
     *
     * @param worldId the world id, or {@code null} to play the profile's selection
     * @return this factory
     * @throws IllegalArgumentException when the content does not ship the world
     */
    public ContentRunFactory withWorld(String worldId) {
        if (worldId != null && !runs.content().worlds().contains(worldId)) {
            throw new IllegalArgumentException("unknown world: " + worldId);
        }
        this.worldOverride = worldId;
        return this;
    }

    /**
     * The pinned world.
     *
     * @return the world id, or {@code null} when runs use the profile's selection
     */
    public String worldOverride() {
        return worldOverride;
    }

    /**
     * A source whose every run is one challenge (M8), played with the same live profile this
     * factory reads: the selected bird, palette and loadout under the challenge's world, tier,
     * rules and boss. The {@code --world} override does not apply — a challenge names its own
     * world (E6).
     *
     * @param challengeId the challenge
     * @return the source the {@code ChallengesScreen} hands a {@code GameScreen}
     * @throws io.github.michelbr84.flapforge.content.UnknownIdException when no challenge
     *     carries the id
     */
    public ChallengeRunSource forChallenge(String challengeId) {
        return new ChallengeRunSource(runs.content(), profiles, challengeId);
    }

    @Override
    public Run newRun(long seed) {
        PlayerProfile profile = profiles == null ? null : profiles.get();
        // Without a profile the run is the pinned classic configuration in the launch's mode
        // (D12, M8): no abilities, no drafts and no boss, so the headless launch and the tests
        // that build a session without persistence play exactly the run the published hash
        // describes. Every profile run has the boss on (RunLoadout.configure).
        RunConfig config = profile == null
                ? RunConfig.classic(seed).toBuilder().mode(mode).build()
                : RunLoadout.configFor(profile, runs.content(), seed, mode);
        if (worldOverride != null && !worldOverride.equals(config.worldId())) {
            config = config.toBuilder().worldId(worldOverride).build();
        }
        return runs.newRun(config);
    }
}
