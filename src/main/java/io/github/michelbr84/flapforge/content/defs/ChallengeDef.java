package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import java.util.List;
import java.util.Objects;

/**
 * One challenge of {@code challenges.json} (§4).
 *
 * <p>M4 ships this as a <em>stub with its final unlock and reward blocks</em> (E19): the id, the
 * world, the tier, the curve, the rules ({@link #flags}, {@link #effects},
 * {@link #forcedModifiers}), the {@link #objective}, the {@link #rewards} and the
 * {@link #unlock}. What M8 adds in place is {@link #forcedPattern} and {@link #boss}, whose
 * pattern ids only exist once {@code patterns.json} ships (M7).
 *
 * <p>A challenge never requires its {@link #world} to be unlocked (E6): a challenge run is
 * self-contained and the world unlock gates free play only.
 *
 * @param id the challenge id
 * @param world the world the run takes place in (E6: not an unlock requirement)
 * @param tier the difficulty tier the run is forced to
 * @param curve the difficulty curve the run uses
 * @param allowOffers whether modifier drafts are offered during the run
 * @param flags rule flags the challenge turns on
 * @param effects stat modifiers applied in the {@code CHALLENGE} layer
 * @param forcedModifiers modifier ids the run starts with (M6 content)
 * @param forcedPattern the only pattern the run streams, or {@code null} (M7)
 * @param boss the challenge's own boss, or {@code null}; it never pays (E26)
 * @param objective what completes the challenge
 * @param rewards what the first completion pays (E11)
 * @param unlock the condition that unlocks {@code challenge:<id>}
 */
public record ChallengeDef(String id, String world, String tier, String curve, boolean allowOffers,
        List<RuleFlag> flags, List<StatModifierDef> effects, List<String> forcedModifiers,
        String forcedPattern, BossDef boss, ObjectiveDef objective, RewardDef rewards,
        UnlockConditionDef unlock) {

    /** Namespace of the unlockable id of a challenge. */
    public static final String NAMESPACE = "challenge:";

    /**
     * Copies the lists and checks the required fields.
     *
     * @throws NullPointerException when the id, world, tier, curve, objective or unlock is missing
     */
    public ChallengeDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(unlock, "unlock");
        flags = List.copyOf(flags);
        effects = List.copyOf(effects);
        forcedModifiers = List.copyOf(forcedModifiers);
    }

    /**
     * The namespaced unlockable id, {@code challenge:<id>}.
     *
     * @return the id
     */
    public String unlockableId() {
        return NAMESPACE + id;
    }

    /**
     * What the first completion pays, never {@code null}.
     *
     * @return the reward, or {@link RewardDef#NONE}
     */
    public RewardDef rewardsOrNone() {
        return rewards == null ? RewardDef.NONE : rewards;
    }
}
