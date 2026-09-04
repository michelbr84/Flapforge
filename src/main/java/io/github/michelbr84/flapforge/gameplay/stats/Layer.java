package io.github.michelbr84.flapforge.gameplay.stats;

/**
 * Bookkeeping groups of an {@link EffectStack} (D8, E31.e). Layers only label where a modifier
 * came from; they never change the arithmetic, which is order-independent.
 */
public enum Layer {
    /** Innate bird effects ({@code BirdDef.effects}). */
    BIRD,
    /** Bird ramp effects re-evaluated on every passed gate. */
    BIRD_RAMP,
    /** Bird synergy effects resolved once from owned upgrade levels. */
    BIRD_SYNERGY,
    /** Permanent upgrade effects snapshot at run start. */
    UPGRADES,
    /** Effects of the current world. */
    WORLD,
    /** Effects of the active world rule cycle. */
    WORLD_CYCLE,
    /** Difficulty curve (and speed ramp) effects. */
    DIFFICULTY,
    /** Difficulty tier effects. */
    TIER,
    /** Challenge effects. */
    CHALLENGE,
    /** Run modifiers taken in drafts. */
    MODIFIERS,
    /** Modifier set synergies. */
    MOD_SYNERGY,
    /** Temporary ability effects. */
    ABILITY,
    /** Prestige bonuses. */
    PRESTIGE
}
