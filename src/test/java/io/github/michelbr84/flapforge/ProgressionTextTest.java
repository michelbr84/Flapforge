package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ProgressionText}'s choice of the branch it phrases (D13 "cheapest path"): the branch a
 * profile is closest to, a {@code default} first, and — without a profile — content order.
 */
class ProgressionTextTest {

    private static UnlockConditionDef threshold(UnlockType type, double value) {
        return new UnlockConditionDef(type, value, null, 0, null, List.of());
    }

    private static UnlockConditionDef anyOf(UnlockConditionDef... children) {
        return new UnlockConditionDef(UnlockType.ANY_OF, 0, null, 0, null, List.of(children));
    }

    @Test
    void aDefaultBranchWinsWithOrWithoutAProfile() {
        UnlockConditionDef runs = threshold(UnlockType.RUNS, 3);
        UnlockConditionDef tree = anyOf(runs, UnlockConditionDef.DEFAULT);
        assertSame(UnlockConditionDef.DEFAULT, ProgressionText.cheapestBranch(tree, null));
        assertSame(UnlockConditionDef.DEFAULT,
                ProgressionText.cheapestBranch(tree, PlayerProfile.fresh(0).normalize()));
    }

    @Test
    void withoutAProfileTheFirstBranchWinsAndWithOneTheNearestDoes() {
        UnlockConditionDef runs = threshold(UnlockType.RUNS, 3);
        UnlockConditionDef level = threshold(UnlockType.LEVEL, 5);
        UnlockConditionDef purchase = new UnlockConditionDef(UnlockType.PURCHASE, 0, null, 150,
                null, List.of());
        UnlockConditionDef tree = anyOf(runs, level, purchase);
        assertSame(runs, ProgressionText.cheapestBranch(tree, null), "every branch is untouched");
        PlayerProfile profile = PlayerProfile.fresh(0).normalize();
        profile.level = 4;
        assertSame(level, ProgressionText.cheapestBranch(tree, profile), "4 of 5 levels");
        Wallet.of(profile).add(PlayerProfile.CURRENCY_COINS, 149);
        assertSame(purchase, ProgressionText.cheapestBranch(tree, profile),
                "the shop preview names the price when the wallet is closest");
        assertEquals(ProgressionText.price(Strings.load("en"), 150),
                ProgressionText.unlockText(Strings.load("en"), GameContent.load(), tree, profile));
    }

    @Test
    void aSatisfiedNamedConditionCountsAsDone() {
        UnlockConditionDef runs = threshold(UnlockType.RUNS, 3);
        UnlockConditionDef challenge = new UnlockConditionDef(UnlockType.CHALLENGE, 0,
                "no_shield_1", 0, null, List.of());
        PlayerProfile profile = PlayerProfile.fresh(0).normalize();
        profile.statistics.totalRuns = 2;
        assertSame(runs, ProgressionText.cheapestBranch(anyOf(challenge, runs), profile),
                "an unmet challenge is untouched, two of three runs is not");
        profile.challenge("no_shield_1").completed = true;
        assertSame(challenge, ProgressionText.cheapestBranch(anyOf(runs, challenge), profile),
                "a completed challenge is done");
    }
}
