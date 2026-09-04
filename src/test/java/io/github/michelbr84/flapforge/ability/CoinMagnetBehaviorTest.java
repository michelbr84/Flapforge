package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.pickup.Coin;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code coin_magnet}: {@code MAGNET_RADIUS +90}, and more from the levels (D9). */
class CoinMagnetBehaviorTest {

    private static double radiusOf(int level) {
        return AbilityRuns.started(AbilityRuns.passive("coin_magnet", level))
                .simulation().stats().resolve(StatId.MAGNET_RADIUS);
    }

    @Test
    void theRadiusIsNinetyAndGrowsWithTheLevel() {
        assertEquals(0.0, AbilityRuns.started(AbilityRuns.factory()
                .newRun(AbilityRuns.config(4, null, List.of()).build()))
                .simulation().stats().resolve(StatId.MAGNET_RADIUS), 0.0, "no magnet by default");
        assertEquals(90.0, radiusOf(1), 0.0);
        assertEquals(130.0, radiusOf(2), 0.0, "90 + 40 from the level");
        assertEquals(160.0, radiusOf(3), 0.0, "90 + 70 from the level");
    }

    @Test
    void aCoinInsideTheRadiusIsPulledToTheBird() {
        Run magnet = AbilityRuns.started(AbilityRuns.passive("coin_magnet", 1));
        Coin pulled = new Coin(Playfield.BIRD_X + 60, Playfield.BIRD_START_Y - 40);
        magnet.simulation().pickups().add(pulled);
        double before = distance(pulled);
        magnet.simulation().bird().setVy(0);
        magnet.tick(RunInput.NONE);
        double after = distance(pulled);
        assertTrue(after < before - 2, "the coin closed in: " + before + " -> " + after);

        Run plain = AbilityRuns.started(AbilityRuns.factory()
                .newRun(AbilityRuns.config(4, null, List.of()).build()));
        Coin ignored = new Coin(Playfield.BIRD_X + 60, Playfield.BIRD_START_Y - 40);
        plain.simulation().pickups().add(ignored);
        plain.simulation().bird().setVy(0);
        plain.tick(RunInput.NONE);
        assertEquals(Playfield.BIRD_START_Y - 40, ignored.y(), 1e-9,
                "without the magnet a coin only scrolls");
    }

    /**
     * The {@code onCoinNear} hook is opt-in (D9): the shipped magnet is stat-driven, so no run
     * pays for the walk over the live coins, and a behaviour that does want the hook still gets
     * it. Both halves are asserted, so neither the routing nor the opt-out can rot unnoticed.
     */
    @Test
    void theCoinHookIsRoutedOnlyToABehaviourThatAsksForIt() {
        Run shipped = AbilityRuns.started(AbilityRuns.passive("coin_magnet", 3));
        assertFalse(shipped.simulation().abilities().routesCoins(),
                "the shipped magnet is MAGNET_RADIUS, not a per-coin hook");

        CoinSpy spy = new CoinSpy();
        AbilityManager manager = AbilityManager.create(
                List.of(AbilityRuns.def("coin_magnet")), Map.of(), RuleSet.EMPTY,
                shipped.simulation(), new EffectStack(),
                BehaviorRegistry.of(Map.of("coin_magnet", () -> spy), Map.of()));
        assertTrue(manager.routesCoins(), "a behaviour that overrides it says so");
        manager.onCoinNear(shipped.simulation().context(), new Coin(0, 0));
        assertEquals(1, spy.seen, "and the coin reaches it");
    }

    /** A behaviour that wants every coin near the bird. */
    private static final class CoinSpy implements AbilityBehavior {
        private int seen;

        @Override
        public boolean routesCoins() {
            return true;
        }

        @Override
        public void onCoinNear(AbilityContext ctx, Coin coin) {
            seen++;
        }
    }

    private static double distance(Coin coin) {
        double dx = coin.x() - Playfield.BIRD_X;
        double dy = coin.y() - Playfield.BIRD_START_Y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
