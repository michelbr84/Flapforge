package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayerLevel}: the curve §4 ships, where the levels fall, and what a gain pays.
 */
class PlayerLevelTest {

    private static final String COINS = PlayerProfile.CURRENCY_COINS;

    private static PlayerLevel shipped() {
        Map<Integer, Map<String, Long>> rewards = new LinkedHashMap<>();
        rewards.put(2, Map.of(COINS, 50L));
        rewards.put(5, Map.of(COINS, 150L));
        rewards.put(10, Map.of(COINS, 500L));
        return new PlayerLevel(PlayerLevel.DEFAULT_BASE, PlayerLevel.DEFAULT_GROWTH,
                PlayerLevel.DEFAULT_MAX_LEVEL, rewards);
    }

    @Test
    void curveIsBaseTimesGrowthAccumulated() {
        PlayerLevel curve = shipped();
        // base 100, growth 1.10: 100, 110, 121, 133, 146 ... accumulated.
        assertEquals(0, curve.xpForLevel(1));
        assertEquals(100, curve.xpForLevel(2));
        assertEquals(210, curve.xpForLevel(3));
        assertEquals(331, curve.xpForLevel(4));
        assertEquals(464, curve.xpForLevel(5));
        assertEquals(610, curve.xpForLevel(6));
        assertEquals(771, curve.xpForLevel(7));
    }

    @Test
    void stepCostIsTheDifferenceBetweenThresholds() {
        PlayerLevel curve = shipped();
        assertEquals(100, curve.xpToNext(1));
        assertEquals(110, curve.xpToNext(2));
        assertEquals(121, curve.xpToNext(3));
        assertEquals(0, curve.xpToNext(curve.maxLevel()), "the cap has no next level");
    }

    @Test
    void levelForFindsTheHighestThresholdReached() {
        PlayerLevel curve = shipped();
        assertEquals(1, curve.levelFor(0));
        assertEquals(1, curve.levelFor(99));
        assertEquals(2, curve.levelFor(100));
        assertEquals(2, curve.levelFor(209));
        assertEquals(3, curve.levelFor(210));
        assertEquals(1, curve.levelFor(-500), "a negative total reads as zero");
    }

    @Test
    void levelStopsAtTheCap() {
        PlayerLevel curve = PlayerLevel.of(100, 1.10, 3);
        assertEquals(3, curve.levelFor(1_000_000));
        assertEquals(210, curve.xpForLevel(9), "a level beyond the cap clamps to the cap");
        PlayerLevel.Progress progress = curve.progressWithin(1_000_000);
        assertTrue(progress.maxed());
        assertEquals(1.0, progress.fraction());
        assertEquals(0, progress.xpForNextLevel());
    }

    @Test
    void progressWithinReportsHowFarThroughTheLevel() {
        PlayerLevel curve = shipped();
        PlayerLevel.Progress progress = curve.progressWithin(155);
        assertEquals(2, progress.level());
        assertEquals(55, progress.xpIntoLevel(), "155 - 100");
        assertEquals(110, progress.xpForNextLevel(), "210 - 100");
        assertEquals(0.5, progress.fraction(), 1e-9);
        assertFalse(progress.maxed());
    }

    @Test
    void levelsCrossedListsEveryLevelAGainWentThrough() {
        PlayerLevel curve = shipped();
        assertEquals(List.of(2, 3, 4, 5), curve.levelsCrossed(1, 5));
        assertEquals(List.of(), curve.levelsCrossed(4, 4), "no level, no crossing");
        assertEquals(List.of(), curve.levelsCrossed(6, 4), "levels never go down");
    }

    @Test
    void rewardsAreThoseOfEveryLevelCrossed() {
        PlayerLevel curve = shipped();
        assertEquals(Map.of(COINS, 50L), curve.rewardsAt(2));
        assertEquals(Map.of(), curve.rewardsAt(3), "an unrewarded level pays nothing");
        assertEquals(Map.of(COINS, 200L), curve.rewardsBetween(1, 5),
                "one jump through level 2 and level 5 pays both");
        assertEquals(Map.of(COINS, 150L), curve.rewardsBetween(2, 5),
                "a level already reached is not paid again");
        assertEquals(Map.of(), curve.rewardsBetween(5, 5));
        assertEquals(List.of(2, 5, 10), curve.rewardedLevels());
    }

    @Test
    void aRewardOfZeroIsDropped() {
        Map<Integer, Map<String, Long>> rewards = new LinkedHashMap<>();
        rewards.put(2, Map.of(COINS, 0L));
        PlayerLevel curve = new PlayerLevel(100, 1.1, 10, rewards);
        assertEquals(Map.of(), curve.rewardsAt(2));
        assertEquals(List.of(), curve.rewardedLevels());
    }

    @Test
    void curveParametersAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> PlayerLevel.of(0, 1.1, 10));
        assertThrows(IllegalArgumentException.class, () -> PlayerLevel.of(100, 0.9, 10));
        assertThrows(IllegalArgumentException.class, () -> PlayerLevel.of(100, 1.1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerLevel.of(Double.NaN, 1.1, 10));
    }

    @Test
    void aFlatCurveIsAllowed() {
        PlayerLevel curve = PlayerLevel.of(50, 1.0, 4);
        assertEquals(50, curve.xpForLevel(2));
        assertEquals(100, curve.xpForLevel(3));
        assertEquals(150, curve.xpForLevel(4));
        assertEquals(4, curve.levelFor(150));
    }
}
