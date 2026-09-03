package io.github.michelbr84.flapforge.gameplay.spec;

import io.github.michelbr84.flapforge.content.defs.ObjectiveDef;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.List;
import java.util.Objects;

/**
 * What a challenge adds to the simulation of a run (§4 {@code challenges.json}, D11, M8): the
 * effects of the {@code CHALLENGE} layer and the objective the run is judged on.
 *
 * <p>The rest of a challenge lives elsewhere by design. Its world, tier, flags, forced
 * modifiers and {@code allowOffers} are properties of the {@code RunConfig}
 * ({@code RunFactory.challengeConfig}); its curve override, forced pattern and boss block are
 * resolved into the {@code RunSetup} beside this record. Nothing here says whether the world is
 * unlocked: a challenge run is self-contained and never requires it (E6).
 *
 * @param id the challenge id
 * @param effects the stat modifiers of the {@code CHALLENGE} layer
 * @param objective what completes the challenge
 */
public record ChallengeSpec(String id, List<StatModifier> effects, ObjectiveDef objective) {

    /**
     * Validates the components.
     *
     * @param id the challenge id
     * @param effects the layer effects
     * @param objective the objective
     */
    public ChallengeSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(objective, "objective");
        effects = List.copyOf(effects);
    }
}
