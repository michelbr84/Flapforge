package io.github.michelbr84.flapforge.content.defs;

import java.util.Objects;

/**
 * One achievement of {@code achievements.json} (§4).
 *
 * <p>M4 ships this as a <em>stub with its final condition and reward blocks</em> (E19): every
 * field below is authored now, because the rewards are unlock-graph edges and the conditions are
 * what {@code bird:mystic} and {@code cosmetic:mystic:aurora} depend on. What M8 adds is the
 * evaluator that reads them, the toasts and the Milestones tab; until then
 * {@code GameContent.playable(ACHIEVEMENT)} is {@code false} and nothing is granted.
 *
 * @param id the achievement id
 * @param hidden whether it stays secret until it fires
 * @param condition when it fires
 * @param reward what it pays
 */
public record AchievementDef(String id, boolean hidden, AchievementConditionDef condition,
        RewardDef reward) {

    /** Namespace of the unlockable id of an achievement. */
    public static final String NAMESPACE = "achievement:";

    /**
     * Checks the required fields.
     *
     * @throws NullPointerException when the id or the condition is missing
     */
    public AchievementDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(condition, "condition");
    }

    /**
     * The namespaced unlockable id, {@code achievement:<id>}.
     *
     * @return the id
     */
    public String unlockableId() {
        return NAMESPACE + id;
    }

    /**
     * What it pays, never {@code null}.
     *
     * @return the reward, or {@link RewardDef#NONE}
     */
    public RewardDef rewardOrNone() {
        return reward == null ? RewardDef.NONE : reward;
    }
}
