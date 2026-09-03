package io.github.michelbr84.flapforge.gameplay.spec;

import java.util.List;
import java.util.Objects;

/**
 * A boss encounter as the simulation sees it (§4 {@code worlds.json.boss} /
 * {@code challenges.json.boss}, D11, M8): the seam between {@code BossDef} and
 * {@code BossEncounter}, with the phase patterns already resolved.
 *
 * <p>The two sources are different things (E26). A <em>world</em> boss carries the world it
 * clears in {@link #worldId}: surviving it writes {@code RunStats.bossesCleared} and pays
 * {@code boss.reward}. A <em>challenge</em> boss has {@code worldId == null}: surviving it only
 * sets the run's cleared flag, which a {@code BOSS_CLEARED} objective reads, and the challenge
 * pays (E11). {@code RunFactory} picks one or the other — a challenge with a {@code boss} block
 * overrides its world's, a challenge without one has no boss at all.
 *
 * @param id the owner's id: the world id of a world boss, the challenge id of a challenge boss
 * @param worldId the world the encounter clears, or {@code null} for a challenge boss (E26)
 * @param atGate the gate count the warning starts at
 * @param warningTicks flying ticks the warning lasts, spawns suppressed
 * @param patterns the phases, streamed in order and looped until {@link #surviveTicks}
 * @param surviveTicks flying ticks the fight lasts
 */
public record BossSpec(String id, String worldId, int atGate, int warningTicks,
        List<PatternSpec> patterns, int surviveTicks) {

    /**
     * Validates the components.
     *
     * @param id the owner's id
     * @param worldId the world cleared, or {@code null}
     * @param atGate the gate the warning starts at
     * @param warningTicks the warning length
     * @param patterns the phases
     * @param surviveTicks the fight length
     */
    public BossSpec {
        Objects.requireNonNull(id, "id");
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
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("boss '" + id + "' has no patterns");
        }
    }

    /**
     * Whether surviving this boss clears a world (E26).
     *
     * @return {@code true} for a world boss, {@code false} for a challenge boss
     */
    public boolean isWorldBoss() {
        return worldId != null;
    }

    /**
     * Copy with the warning at another gate (the balancing tool and the feasibility test start
     * the encounter at gate 1, see {@code RunSetup.startingAtBoss}).
     *
     * @param newAtGate the gate
     * @return the copy
     */
    public BossSpec withAtGate(int newAtGate) {
        return new BossSpec(id, worldId, newAtGate, warningTicks, patterns, surviveTicks);
    }
}
