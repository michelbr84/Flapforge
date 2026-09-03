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
 * The {@link SeededRunSource} of one challenge (D11, D17, M8): every run it builds is that
 * challenge in {@link RunMode#CHALLENGE} — its world, tier, curve, flags, effects, forced
 * modifiers, forced pattern, boss block and objective — played with what the profile has
 * selected and bought: the bird, the palette, the equipped abilities, the upgrade layer
 * ({@link RunLoadout#challengeConfigFor}). Instant retry ({@code newRun} with the next seed)
 * keeps all of it.
 *
 * <p>Nothing here checks that the challenge's world is unlocked (E6): the challenge is
 * self-contained, and the screen that offers it checks the challenge's own unlock. A session
 * without a profile — a test, the headless launch — plays the challenge on the default bird.
 */
public final class ChallengeRunSource implements SeededRunSource {

    private final RunFactory runs;
    private final Supplier<PlayerProfile> profiles;
    private final String challengeId;

    /**
     * Creates the source of one challenge.
     *
     * @param content the loaded content
     * @param profiles the live profile of the session, read at the start of every run; may be
     *     {@code null}, and may return {@code null} in a session without persistence
     * @param challengeId the challenge every run plays
     * @throws io.github.michelbr84.flapforge.content.UnknownIdException when no challenge
     *     carries the id
     */
    public ChallengeRunSource(GameContent content, Supplier<PlayerProfile> profiles,
            String challengeId) {
        this.runs = new RunFactory(Objects.requireNonNull(content, "content"));
        this.profiles = profiles;
        this.challengeId = Objects.requireNonNull(challengeId, "challengeId");
        content.challenges().get(challengeId);
    }

    /**
     * The challenge every run plays.
     *
     * @return the id
     */
    public String challengeId() {
        return challengeId;
    }

    /**
     * The content every run is built from.
     *
     * @return the content
     */
    public GameContent content() {
        return runs.content();
    }

    @Override
    public Run newRun(long seed) {
        PlayerProfile profile = profiles == null ? null : profiles.get();
        RunConfig config = profile == null
                ? runs.challengeConfig(RunConfig.builder(seed).mode(RunMode.CHALLENGE).build(),
                        challengeId)
                : RunLoadout.challengeConfigFor(profile, runs.content(), seed, challengeId);
        return runs.newRun(config);
    }
}
