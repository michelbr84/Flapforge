package io.github.michelbr84.flapforge.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.support.TestContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The unlock-graph invariants of D13 on the content that actually ships: no cycle, everything
 * reachable from the default set, a cumulative path for every non-cosmetic unlockable, and a
 * source for every currency.
 *
 * <p>The cumulative rule is the one that keeps progression from dead-ending: a player who never
 * clears a boss or a challenge must still be able to reach every bird, ability, world, tree, tier,
 * challenge and feature by playing more runs. Cosmetics are deliberately exempt.
 */
class UnlockGraphTest {

    private static final GameContent SHIPPED = GameContent.load();
    private static final UnlockGraph GRAPH = UnlockGraph.of(SHIPPED);

    @Test
    void theShippedGraphHasNoProblem() {
        assertEquals(List.of(), GRAPH.errors(), () -> "unlock graph:\n" + GRAPH.render());
    }

    @Test
    void theDefaultSetIsWhatAFreshProfileOwns() {
        // The graph derives the default set from the data (every id whose unlock is "default"),
        // E18 lists what a fresh profile is created with. The first must contain the second; it
        // also holds the other birds' "default" palettes, which cannot be worn until their bird
        // is unlocked and are granted by the unlock evaluator when that happens.
        assertTrue(GRAPH.defaults().containsAll(PlayerProfile.DEFAULT_UNLOCKED),
                () -> "graph defaults " + GRAPH.defaults() + " miss "
                        + PlayerProfile.DEFAULT_UNLOCKED);
        assertTrue(GRAPH.defaults().contains("world:green_fields"));
        assertTrue(GRAPH.defaults().contains("tree:flight"));
    }

    @Test
    void everyUnlockableIsReachable() {
        List<String> unreachable = new ArrayList<>();
        for (UnlockGraph.Node node : GRAPH.nodes().values()) {
            if (node.unlockable() && !GRAPH.isReachable(node.id())) {
                unreachable.add(node.id());
            }
        }
        assertEquals(List.of(), unreachable, () -> "unlock graph:\n" + GRAPH.render());
    }

    @Test
    void everyNonCosmeticUnlockableHasACumulativePath() {
        List<String> stuck = new ArrayList<>();
        StringBuilder paths = new StringBuilder();
        for (UnlockGraph.Node node : GRAPH.nodes().values()) {
            if (!node.unlockable() || node.cosmetic()) {
                continue;
            }
            UnlockGraph.Path path = GRAPH.cheapestCumulativePath(node.id());
            if (!path.exists()) {
                stuck.add(node.id());
            } else {
                paths.append("  ").append(node.id()).append(": ").append(path.cost())
                        .append(" coins  ").append(String.join(" -> ", path.steps())).append('\n');
            }
        }
        assertEquals(List.of(), stuck,
                () -> "these have no cumulative path; the ones that do are:\n" + paths);
    }

    @Test
    void theCheapestPathsAreTheOnesTheDataDescribes() {
        assertEquals(0, GRAPH.cheapestCumulativePath("bird:guardian").cost(),
                "Ironbeak is 150 coins or three runs, and three runs cost nothing");
        assertEquals(List.of("runs 3", "bird:guardian"),
                GRAPH.cheapestCumulativePath("bird:guardian").steps());
        assertEquals(300, GRAPH.cheapestCumulativePath("bird:swift").cost(),
                "Zephyr's other condition is best_gates, which is skill, not time");
        assertEquals(0, GRAPH.cheapestCumulativePath("tier:hard").cost(),
                "Hard is free at 400 total gates, cheaper than the 400-coin node that grants it");
        assertTrue(GRAPH.cheapestCumulativePath("world:void").exists());
        assertFalse(GRAPH.cheapestCumulativePath("cosmetic:classic:ember").exists(),
                "a trophy palette is allowed to need the challenge that pays it");
    }

    @Test
    void theUpgradeGrantIsAnEdgeAndTheCounterGrantsAreNot() {
        List<String> reasons = new ArrayList<>();
        for (UnlockGraph.Edge edge : GRAPH.edges()) {
            if (edge.reason().startsWith("upgrade node ")) {
                reasons.add(edge.from() + " -> " + edge.to() + " (" + edge.reason() + ")");
            }
        }
        assertEquals(List.of("tree:economy -> tier:hard (upgrade node hard_tier_1)"), reasons,
                "only UNLOCK grants are edges (E31.f): ability_cap and passive_slot are not");

        UnlockGraph.Edge grant = find("tree:economy", "tier:hard");
        assertNotNull(grant);
        // hard_tier_1 costs 400 but cannot be bought without coin_purse_1 at 80, so the price of
        // reaching tier:hard through the node is 480. The cheapest-path table under-reports
        // otherwise, and M9's MetaSim thresholds (E25) read the same numbers.
        assertEquals(480, grant.cost(),
                "a node-grant edge costs the node plus one level of every prerequisite");
    }

    @Test
    void bossAndChallengeRewardsAreEdgesButNotCumulativeOnes() {
        UnlockGraph.Edge bossReward = find("world:green_fields", "world:wind_valley");
        assertNotNull(bossReward);
        assertEquals("boss reward", bossReward.reason());
        assertFalse(bossReward.cumulative(),
                "clearing a boss is skill, so it may not carry a cumulative path");
        UnlockGraph.Edge challengeReward =
                find("challenge:one_life_1", "ability:invulnerability");
        assertNotNull(challengeReward);
        assertFalse(challengeReward.cumulative());
    }

    private static UnlockGraph.Edge find(String from, String to) {
        for (UnlockGraph.Edge edge : GRAPH.edges()) {
            if (edge.from().equals(from) && edge.to().equals(to)
                    && !edge.reason().startsWith("condition")) {
                return edge;
            }
        }
        return null;
    }

    /**
     * The rule that matters: a bird whose only way in is a challenge would strand a player who
     * cannot complete it, and the validator has to refuse that content.
     */
    @Test
    void aBirdReachableOnlyThroughAChallengeBreaksTheCumulativeRule() {
        Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.shippedJson());
        JsonElement birds = files.get("birds");
        JsonObject heavy = birds.getAsJsonArray().get(2).getAsJsonObject();
        assertEquals("heavy", heavy.get("id").getAsString());
        JsonObject unlock = new JsonObject();
        unlock.addProperty("type", "challenge");
        unlock.addProperty("id", "no_shield_1");
        heavy.add("unlock", unlock);

        ContentException e = assertThrows(ContentException.class,
                () -> GameContent.fromJson(files));
        assertTrue(e.errors().stream().anyMatch(error -> error.startsWith(
                        "birds.json#/2: 'bird:heavy' has no path using only cumulative"
                                + " conditions")),
                e.errors().toString());
    }

    /** Nothing may require, however indirectly, something it unlocks. */
    @Test
    void aCycleBetweenTwoChallengesIsRejectedOnce() {
        Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.shippedJson());
        JsonObject noShield = files.get("challenges").getAsJsonObject()
                .getAsJsonArray("challenges").get(0).getAsJsonObject();
        assertEquals("no_shield_1", noShield.get("id").getAsString());
        JsonObject unlock = new JsonObject();
        unlock.addProperty("type", "challenge");
        unlock.addProperty("id", "one_life_1");
        noShield.add("unlock", unlock);

        ContentException e = assertThrows(ContentException.class,
                () -> GameContent.fromJson(files));
        List<String> cycles = e.errors().stream().filter(error -> error.contains("unlock cycle"))
                .toList();
        assertEquals(1, cycles.size(), e.errors().toString());
        assertTrue(cycles.get(0).contains("challenge:no_shield_1"), cycles.get(0));
        assertTrue(cycles.get(0).contains("challenge:one_life_1"), cycles.get(0));
    }

    @Test
    void aCurrencyNothingPaysOutIsRejected() {
        Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.shippedJson());
        files.get("economy").getAsJsonObject().getAsJsonArray("currencies").add("shards");
        ContentException e = assertThrows(ContentException.class,
                () -> GameContent.fromJson(files));
        assertTrue(e.errors().stream().anyMatch(error -> error.startsWith(
                        "economy.json#/currencies/1: nothing pays out 'shards'")),
                e.errors().toString());
    }

    @Test
    void theRenderedGraphNamesTheDefaultsTheEdgesAndThePaths() {
        String render = GRAPH.render();
        assertTrue(render.contains("Default set (E18):"), render);
        assertTrue(render.contains("bird:classic"), render);
        assertTrue(render.contains("-> world:wind_valley  (boss reward)"), render);
        assertTrue(render.contains("Cheapest cumulative path per unlockable:"), render);
        assertTrue(render.contains("bird:swift: 300 coins"), render);
    }
}
