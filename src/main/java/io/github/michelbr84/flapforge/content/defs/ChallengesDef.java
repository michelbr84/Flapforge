package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * The root of {@code challenges.json}: the challenge list in file order, plus room for the file-level
 * {@code _comment} that records which fields land in which milestone (E19).
 *
 * @param challenges the challenges
 */
public record ChallengesDef(List<ChallengeDef> challenges) {

    /**
     * Copies the list.
     */
    public ChallengesDef {
        challenges = List.copyOf(challenges);
    }
}
