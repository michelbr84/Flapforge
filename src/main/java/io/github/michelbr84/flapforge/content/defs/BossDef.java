package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * A boss encounter (§4): after {@link #atGate} gates the run warns for {@link #warningTicks},
 * streams {@link #patterns} and is cleared by surviving {@link #surviveTicks}.
 *
 * <p>Only a {@link WorldDef#boss()} carries a {@link #reward} and writes
 * {@code statistics.bossesCleared} (E26); a {@link ChallengeDef#boss()} leaves it {@code null}
 * and only sets {@code objectiveMet}, because the challenge itself pays.
 *
 * <p>M4 authors the world blocks — the {@link #reward} is an unlock-graph edge and has to be
 * final (E19) — while the pattern ids they name are authored in M7/M8; the validator resolves
 * them only once {@code patterns.json} exists.
 *
 * @param atGate the gate the encounter starts at
 * @param warningTicks the telegraph before it starts
 * @param patterns the pattern ids streamed during the fight
 * @param surviveTicks how long the fight lasts
 * @param reward what clearing it pays, or {@code null} for a challenge boss (E26)
 */
public record BossDef(int atGate, int warningTicks, List<String> patterns, int surviveTicks,
        RewardDef reward) {

    /**
     * Copies the pattern list and checks the ranges.
     *
     * @throws IllegalArgumentException when a gate or tick count is out of range
     */
    public BossDef {
        if (atGate < 1) {
            throw new IllegalArgumentException("boss.atGate must be at least 1: " + atGate);
        }
        if (warningTicks < 0) {
            throw new IllegalArgumentException(
                    "boss.warningTicks must not be negative: " + warningTicks);
        }
        if (surviveTicks < 1) {
            throw new IllegalArgumentException(
                    "boss.surviveTicks must be at least 1: " + surviveTicks);
        }
        patterns = List.copyOf(patterns);
    }
}
