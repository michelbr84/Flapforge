package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.RandomProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The named streams must be reproducible <em>and</em> well spread across neighbouring seeds
 * (D12). Seeds are consecutive in practice — an instant retry walks {@code N, N+1, N+2 …}
 * (D29) and {@code BalancingSim} sweeps {@code seed0 + i} — so a first draw that moves almost
 * linearly with the seed would bias the opening of every run in a session and every row of a
 * balancing batch.
 */
class RandomProviderTest {

    /** How many consecutive seeds the spread properties are measured over. */
    private static final int SEEDS = 2000;
    /** First seed of the sweep (the CI reference seed). */
    private static final long FIRST_SEED = 42;

    private static List<Double> firstDraws(String stream) {
        List<Double> out = new ArrayList<>(SEEDS);
        for (long seed = FIRST_SEED; seed < FIRST_SEED + SEEDS; seed++) {
            out.add(new RandomProvider(seed).stream(stream).nextDouble());
        }
        return out;
    }

    @Test
    void theFirstDrawOfConsecutiveSeedsCoversTheWholeUnitInterval() {
        List<Double> draws = firstDraws(RandomProvider.SPAWN);
        double min = 1;
        double max = 0;
        int belowFivePercent = 0;
        for (double d : draws) {
            min = Math.min(min, d);
            max = Math.max(max, d);
            if (d < 0.05) {
                belowFivePercent++;
            }
        }
        assertTrue(max - min > 0.9,
                "the first draw of 2000 consecutive seeds must span the range, was ["
                        + min + ", " + max + "]");
        assertEquals(0.05, belowFivePercent / (double) SEEDS, 0.02,
                "P(first draw < 0.05) must be about 5 %, was " + belowFivePercent + "/" + SEEDS);
    }

    @Test
    void neighbouringSeedsDoNotShareTheirFirstDraw() {
        List<Double> draws = firstDraws(RandomProvider.OBSTACLE);
        Set<Double> distinct = new HashSet<>(draws);
        assertEquals(draws.size(), distinct.size(), "every seed gets its own opening draw");
        for (int i = 1; i < draws.size(); i++) {
            assertNotEquals(draws.get(i - 1), draws.get(i));
        }
    }

    @Test
    void streamsAreReproducibleAndIndependent() {
        RandomProvider provider = new RandomProvider(7);
        assertEquals(provider.stream(RandomProvider.SPAWN).nextDouble(),
                provider.stream(RandomProvider.SPAWN).nextDouble(),
                "the same name gives the same sequence");
        assertNotEquals(provider.stream(RandomProvider.SPAWN).nextDouble(),
                provider.stream(RandomProvider.OBSTACLE).nextDouble(),
                "different names give different sequences");
        assertNotEquals(provider.streamSeed(RandomProvider.SPAWN),
                new RandomProvider(8).streamSeed(RandomProvider.SPAWN));
    }

    @Test
    void theMixerIsTheSplitMix64FinaliserAndABijection() {
        // Known SplitMix64 finaliser values, so the sequences stay comparable across platforms.
        assertEquals(0L, RandomProvider.mix64(0L));
        assertEquals(6238072747940578789L, RandomProvider.mix64(1L));
        Set<Long> seen = new HashSet<>();
        for (long v = -5000; v <= 5000; v++) {
            assertTrue(seen.add(RandomProvider.mix64(v)), "collision at " + v);
        }
    }

    @Test
    void theStreamSeedIsWhatTheGeneratorGets() {
        RandomProvider provider = new RandomProvider(123);
        Random expected = new Random(provider.streamSeed(RandomProvider.COINS));
        assertEquals(expected.nextLong(), provider.stream(RandomProvider.COINS).nextLong());
    }
}
