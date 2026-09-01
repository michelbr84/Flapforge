package io.github.michelbr84.flapforge.content.defs;

/**
 * The XP curve of {@code economy.json.xp} (§4, D13): the XP needed for level 2 is {@code base},
 * and each further level multiplies it by {@code growth} until {@code maxLevel}.
 *
 * @param base XP required for the first level-up
 * @param growth geometric growth factor per level
 * @param maxLevel the highest reachable level
 */
public record XpCurveDef(long base, double growth, int maxLevel) {

    /**
     * Validates the components.
     *
     * @param base the base XP
     * @param growth the growth factor
     * @param maxLevel the level cap
     */
    public XpCurveDef {
        if (base < 1) {
            throw new IllegalArgumentException("xp.curve.base must be at least 1: " + base);
        }
        if (growth < 1) {
            throw new IllegalArgumentException("xp.curve.growth must be at least 1: " + growth);
        }
        if (maxLevel < 1) {
            throw new IllegalArgumentException("xp.curve.maxLevel must be at least 1: " + maxLevel);
        }
    }
}
