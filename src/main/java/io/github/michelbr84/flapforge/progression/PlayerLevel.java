package io.github.michelbr84.flapforge.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The experience curve and the rewards each level pays (§4 {@code economy.json.xp}, D13).
 * Experience is earned and never spent; a level is a threshold on the lifetime total.
 *
 * <p>The curve is {@code base × growth^n}, accumulated: going from level {@code n} to
 * {@code n + 1} costs {@code round(base × growth^(n − 1))}, so with the shipped
 * {@code base 100, growth 1.10} level 2 sits at 100 XP, level 3 at 210, level 4 at 331. The
 * thresholds are computed once, by repeated multiplication rather than {@code Math.pow} — the pure
 * packages may not call a platform-dependent math function (E30.c), and a running product is
 * bit-identical on every JVM anyway.
 *
 * <p>Levels stop at {@link #maxLevel()}: beyond it experience still accumulates in the profile (a
 * lifetime statistic) but no further level is reached and no further reward is paid.
 */
public final class PlayerLevel {

    /** {@code economy.json.xp.curve.base} as shipped. */
    public static final double DEFAULT_BASE = 100;
    /** {@code economy.json.xp.curve.growth} as shipped. */
    public static final double DEFAULT_GROWTH = 1.10;
    /** {@code economy.json.xp.curve.maxLevel} as shipped. */
    public static final int DEFAULT_MAX_LEVEL = 50;
    /** The level a fresh profile starts at. */
    public static final int FIRST_LEVEL = 1;

    private final double base;
    private final double growth;
    private final int maxLevel;
    /** Cumulative experience needed to be at index {@code level}; index 0 is unused. */
    private final long[] thresholds;
    private final Map<Integer, Map<String, Long>> levelRewards;

    /**
     * Builds a curve.
     *
     * @param base experience the first level-up costs; must be positive
     * @param growth factor each following level-up is multiplied by; must be at least 1
     * @param maxLevel highest reachable level; must be at least {@link #FIRST_LEVEL}
     * @param levelRewards currency grants per level, as {@code economy.json.xp.levelRewards}
     *     spells them; may be {@code null}
     */
    public PlayerLevel(double base, double growth, int maxLevel,
            Map<Integer, Map<String, Long>> levelRewards) {
        if (!(base > 0) || !Double.isFinite(base)) {
            throw new IllegalArgumentException("base must be positive: " + base);
        }
        if (!(growth >= 1) || !Double.isFinite(growth)) {
            throw new IllegalArgumentException("growth must be at least 1: " + growth);
        }
        if (maxLevel < FIRST_LEVEL) {
            throw new IllegalArgumentException("maxLevel must be at least 1: " + maxLevel);
        }
        this.base = base;
        this.growth = growth;
        this.maxLevel = maxLevel;
        this.thresholds = thresholds(base, growth, maxLevel);
        this.levelRewards = freeze(levelRewards);
    }

    private static long[] thresholds(double base, double growth, int maxLevel) {
        long[] out = new long[maxLevel + 1];
        double step = base;
        long total = 0;
        for (int level = FIRST_LEVEL + 1; level <= maxLevel; level++) {
            total += Math.round(step);
            out[level] = total;
            step *= growth;
        }
        return out;
    }

    private static Map<Integer, Map<String, Long>> freeze(
            Map<Integer, Map<String, Long>> rewards) {
        Map<Integer, Map<String, Long>> out = new TreeMap<>();
        if (rewards != null) {
            for (Map.Entry<Integer, Map<String, Long>> entry : rewards.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                Map<String, Long> grants = new LinkedHashMap<>();
                for (Map.Entry<String, Long> grant : entry.getValue().entrySet()) {
                    if (grant.getKey() != null && grant.getValue() != null && grant.getValue() > 0) {
                        grants.put(grant.getKey(), grant.getValue());
                    }
                }
                if (!grants.isEmpty()) {
                    out.put(entry.getKey(), Collections.unmodifiableMap(grants));
                }
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The curve with no level rewards.
     *
     * @param base the base cost
     * @param growth the growth factor
     * @param maxLevel the highest level
     * @return the curve
     */
    public static PlayerLevel of(double base, double growth, int maxLevel) {
        return new PlayerLevel(base, growth, maxLevel, Map.of());
    }

    /**
     * The curve §4 ships, without level rewards; the loaded {@code economy.json} replaces it.
     *
     * @return the curve
     */
    public static PlayerLevel defaults() {
        return of(DEFAULT_BASE, DEFAULT_GROWTH, DEFAULT_MAX_LEVEL);
    }

    /**
     * The base cost.
     *
     * @return the experience the first level-up costs
     */
    public double base() {
        return base;
    }

    /**
     * The growth factor.
     *
     * @return the factor
     */
    public double growth() {
        return growth;
    }

    /**
     * The highest reachable level.
     *
     * @return the level
     */
    public int maxLevel() {
        return maxLevel;
    }

    /**
     * Cumulative experience needed to be at a level.
     *
     * @param level the level; clamped to {@code [1, maxLevel]}
     * @return the threshold, 0 for level 1
     */
    public long xpForLevel(int level) {
        int bounded = level < FIRST_LEVEL ? FIRST_LEVEL : Math.min(level, maxLevel);
        return thresholds[bounded];
    }

    /**
     * Experience the step from a level to the next one costs.
     *
     * @param level the level
     * @return the cost, 0 at {@link #maxLevel()}
     */
    public long xpToNext(int level) {
        if (level >= maxLevel) {
            return 0;
        }
        return xpForLevel(level + 1) - xpForLevel(level);
    }

    /**
     * The level a lifetime experience total reaches.
     *
     * @param xp the total; a negative total reads as 0
     * @return the level, in {@code [1, maxLevel]}
     */
    public int levelFor(long xp) {
        long total = Math.max(0, xp);
        int level = FIRST_LEVEL;
        while (level < maxLevel && total >= thresholds[level + 1]) {
            level++;
        }
        return level;
    }

    /**
     * How far an experience total is through its level.
     *
     * @param xp the lifetime total
     * @return the progress
     */
    public Progress progressWithin(long xp) {
        long total = Math.max(0, xp);
        int level = levelFor(total);
        long start = xpForLevel(level);
        long span = xpToNext(level);
        long into = total - start;
        double fraction = span <= 0 ? 1.0 : (double) into / (double) span;
        return new Progress(level, into, span, fraction, level >= maxLevel);
    }

    /**
     * The levels crossed by a gain, in ascending order.
     *
     * @param fromLevel the level before the gain
     * @param toLevel the level after the gain
     * @return the levels {@code (fromLevel, toLevel]}, empty when nothing was crossed
     */
    public List<Integer> levelsCrossed(int fromLevel, int toLevel) {
        List<Integer> crossed = new ArrayList<>();
        for (int level = Math.max(FIRST_LEVEL, fromLevel) + 1; level <= toLevel; level++) {
            crossed.add(level);
        }
        return crossed;
    }

    /**
     * The currency grants a level pays.
     *
     * @param level the level
     * @return the grants, empty when the level pays nothing
     */
    public Map<String, Long> rewardsAt(int level) {
        Map<String, Long> grants = levelRewards.get(level);
        return grants == null ? Map.of() : grants;
    }

    /**
     * Every level that pays something, in ascending order.
     *
     * @return the levels
     */
    public List<Integer> rewardedLevels() {
        return List.copyOf(levelRewards.keySet());
    }

    /**
     * The grants of every level crossed by a gain, summed per currency.
     *
     * @param fromLevel the level before the gain
     * @param toLevel the level after the gain
     * @return the total grants, in currency insertion order
     */
    public Map<String, Long> rewardsBetween(int fromLevel, int toLevel) {
        Map<String, Long> total = new LinkedHashMap<>();
        for (Integer level : levelsCrossed(fromLevel, toLevel)) {
            for (Map.Entry<String, Long> grant : rewardsAt(level).entrySet()) {
                total.merge(grant.getKey(), grant.getValue(), Long::sum);
            }
        }
        return total;
    }

    @Override
    public String toString() {
        return "PlayerLevel{base=" + base + ", growth=" + growth + ", maxLevel=" + maxLevel
                + ", rewardedLevels=" + levelRewards.keySet() + '}';
    }

    /**
     * How far a profile is through its current level.
     *
     * @param level the level reached
     * @param xpIntoLevel experience earned since the level started
     * @param xpForNextLevel experience the whole level costs, 0 at the cap
     * @param fraction {@code xpIntoLevel / xpForNextLevel}, 1 at the cap
     * @param maxed whether the cap has been reached
     */
    public record Progress(int level, long xpIntoLevel, long xpForNextLevel, double fraction,
            boolean maxed) {
    }
}
