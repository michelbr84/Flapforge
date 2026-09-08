package io.github.michelbr84.flapforge.ui.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import org.junit.jupiter.api.Test;

/**
 * The three headline attributes of the bird selection's hero (M11): the shipped roster's scores,
 * pinned; the balanced bird at the middle of every axis; and the promise that the numbers read
 * the bird alone, never the profile.
 */
class BirdAttributesTest {

    private final GameContent content = GameContent.load();

    @Test
    void theSevenShippedBirdsScoreWhatTheirDataSays() {
        // Forgewing is the reference every ratio is taken against, so it sits at the middle.
        assertScores("classic", 5, 5, 5);
        // Zephyr buys the most air per flap (470 against a heavier 2100 sky).
        assertScores("swift", 7, 5, 3);
        // Anvil's 450 px/s terminal fall is survivability, and its heavy sky costs it mobility.
        assertScores("heavy", 4, 7, 3);
        // Ironbeak flies with a shield nothing can take away.
        assertScores("guardian", 5, 10, 5);
        // Jackdaw pays for its rewards with a wider hitbox and narrower gaps.
        assertScores("gambler", 5, 4, 3);
        // Oracle carries a third passive slot and longer abilities.
        assertScores("mystic", 5, 5, 6);
        // Cinder starts with the weakest flap and earns its strength from the forge.
        assertScores("forge", 3, 5, 5);
    }

    @Test
    void everyScoreStaysOnTheScale() {
        for (BirdDef bird : content.birds()) {
            BirdAttributes scores = BirdAttributes.of(bird, content);
            for (int value : new int[] {scores.mobility(), scores.defence(), scores.control()}) {
                assertTrue(value >= BirdAttributes.MIN && value <= BirdAttributes.MAX,
                        bird.id() + " scored " + value + ", off the 1-10 scale");
            }
        }
    }

    @Test
    void theScoresDescribeTheBirdAndNothingElse() {
        // Called twice with the same bird and content, the same numbers come back: there is no
        // profile, no upgrade layer and no world in the formula (what the current build resolves
        // to is the screen's stat breakdown, not this).
        BirdDef guardian = content.birds().get("guardian");
        assertEquals(BirdAttributes.of(guardian, content), BirdAttributes.of(guardian, content));
        assertTrue(BirdAttributes.of(guardian, content).defence()
                > BirdAttributes.of(content.birds().get("classic"), content).defence(),
                "the innate shield is what makes Ironbeak the defensive bird");
    }

    private void assertScores(String birdId, int mobility, int defence, int control) {
        BirdAttributes scores = BirdAttributes.of(content.birds().get(birdId), content);
        assertEquals(new BirdAttributes(mobility, defence, control), scores, birdId);
    }
}
