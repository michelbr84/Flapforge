package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * The prestige block of {@code economy.json} (§4, E4, E23). There are no prestige shards: the
 * only permanent gain is {@code bonusPerPrestige × prestigeCount} pushed into the
 * {@code PRESTIGE} stat layer.
 *
 * @param requiredLevel the level a profile must reach before it may prestige
 * @param keeps which parts of the profile survive a prestige
 * @param bonusPerPrestige effects granted once per prestige performed
 * @param maxPrestige how many times a profile may prestige
 */
public record PrestigeDef(int requiredLevel, List<String> keeps,
        List<StatModifierDef> bonusPerPrestige, int maxPrestige) {

    /** Kept: every owned bird. */
    public static final String KEEP_BIRDS = "birds";
    /** Kept: every unlocked achievement. */
    public static final String KEEP_ACHIEVEMENTS = "achievements";
    /** Kept: every owned cosmetic. */
    public static final String KEEP_COSMETICS = "cosmetics";
    /** Kept: the lifetime statistics. */
    public static final String KEEP_STATISTICS = "statistics";

    /** The four things a prestige may keep (E4, E23). */
    public static final List<String> KEEPS =
            List.of(KEEP_BIRDS, KEEP_ACHIEVEMENTS, KEEP_COSMETICS, KEEP_STATISTICS);

    /**
     * Copies the lists.
     *
     * @param requiredLevel the required level
     * @param keeps what survives a prestige
     * @param bonusPerPrestige the per-prestige effects
     * @param maxPrestige the prestige cap
     */
    public PrestigeDef {
        keeps = List.copyOf(keeps);
        bonusPerPrestige = List.copyOf(bonusPerPrestige);
        if (requiredLevel < 1) {
            throw new IllegalArgumentException(
                    "prestige.requiredLevel must be at least 1: " + requiredLevel);
        }
        if (maxPrestige < 0) {
            throw new IllegalArgumentException(
                    "prestige.maxPrestige must not be negative: " + maxPrestige);
        }
    }
}
