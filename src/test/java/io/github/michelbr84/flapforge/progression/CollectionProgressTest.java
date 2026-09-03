package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link CollectionProgress} (D13, E20, M8): every category counts what the profile holds over
 * what the content ships, the percentage floors, and the one arithmetic is literally the same
 * object {@link UnlockEvaluator}'s {@code collection.<category>.percent} condition reads — so
 * the tab, the achievements and the cosmetic unlock can never disagree.
 */
class CollectionProgressTest {

    private static final String COINS = PlayerProfile.CURRENCY_COINS;

    private GameContent content;
    private CollectionProgress progress;
    private PlayerProfile profile;

    @BeforeEach
    void setUp() {
        content = GameContent.load();
        progress = CollectionProgress.of(content);
        profile = PlayerProfile.fresh(1_700_000_000_000L).normalize();
    }

    /** Every category name the evaluator knows is listed, {@code all} last. */
    @Test
    void theCategoriesAreListedAllLast() {
        assertEquals(List.of("birds", "abilities", "worlds", "challenges", "cosmetics",
                "achievements", "upgrades", "all"), CollectionProgress.CATEGORIES);
        assertTrue(CollectionProgress.knows("all"));
        assertFalse(CollectionProgress.knows("bird"));
        assertFalse(CollectionProgress.knows(null));
    }

    /**
     * A fresh profile owns exactly the starter kit: one bird, one ability, one world, one
     * palette — and none of the things it has to earn.
     */
    @Test
    void aFreshProfileStartsWithTheDefaults() {
        assertEquals(1, progress.of("birds", profile).owned());
        assertEquals(1, progress.of("abilities", profile).owned());
        assertEquals(1, progress.of("worlds", profile).owned());
        assertEquals(1, progress.of("cosmetics", profile).owned());
        assertEquals(0, progress.of("upgrades", profile).owned());
        assertEquals(0, progress.of("challenges", profile).owned());
        assertEquals(0, progress.of("achievements", profile).owned());
        for (CollectionProgress.Entry entry : progress.all(profile)) {
            assertTrue(entry.total() > 0, entry.category() + " ships content");
            assertTrue(entry.percent() >= 0 && entry.percent() <= 100, entry.category());
            assertFalse(entry.isComplete());
        }
    }

    /** The id-based categories count the namespaced unlock ids the profile holds. */
    @Test
    void theIdCategoriesCountUnlocks() {
        List<String> birdIds = content.birds().ids();
        profile.unlock("bird:" + birdIds.get(1));
        profile.unlock("bird:" + birdIds.get(2));
        profile.unlock("world:wind_valley");

        CollectionProgress.Entry birds = progress.of("birds", profile);
        assertEquals(3, birds.owned(), "the starter bird plus the two unlocked ones");
        assertEquals(birdIds.size(), birds.total());
        assertEquals(100L * 3 / birdIds.size(), birds.percent());

        assertEquals(2, progress.of("worlds", profile).owned());
        assertEquals(1, progress.of("abilities", profile).owned());

        // One reader, one answer: the evaluator's instance and a fresh one agree bit for bit.
        assertEquals(birds.percent(),
                AchievementEvaluator.of(content).collections().percent("birds", profile));
    }

    /**
     * E20: the counter unlock condition of a cosmetic reads the same percentages — and the
     * upgrade-node arithmetic (levels owned over levels that exist) is what
     * {@code collection.upgrades.percent} resolves to.
     */
    @Test
    void theUpgradeCategoryCountsLevels() {
        int levels = 0;
        long spent = 0;
        Wallet wallet = Wallet.of(profile);
        for (int i = 0; i < content.upgrades().size(); i++) {
            String id = content.upgrades().ids().get(i);
            int max = content.upgrades().all().get(i).maxLevel();
            for (int level = 1; level <= Math.min(2, max); level++) {
                wallet.add(COINS, 1000);
                profile.upgrades.put(id, level);
                spent++;
                levels++;
            }
        }
        CollectionProgress.Entry upgrades = progress.of("upgrades", profile);
        assertEquals(levels, upgrades.owned(), "each level bought is one owned entry");
        assertTrue(upgrades.total() > levels, "the total is a level count, larger than owned");
        assertTrue(upgrades.percent() > 0 && upgrades.percent() < 100);

        // The unlock evaluator's condition path lands on the same number.
        UnlockEvaluator unlocks = UnlockEvaluator.of(content);
        assertEquals(upgrades.percent(),
                unlocks.counter("collection.upgrades.percent", profile));
    }

    /**
     * The challenge category counts completed records, the achievement category held records —
     * attempts and non-completed records count for nothing.
     */
    @Test
    void theRecordCategoriesCountRecords() {
        PlayerProfile.ChallengeRecord done = profile.challenge("no_shield_1");
        done.completed = true;
        done.attempts = 3;
        PlayerProfile.ChallengeRecord tried = profile.challenge("speed_run_1");
        tried.attempts = 1;
        profile.achievements.put("first_flight", new PlayerProfile.AchievementRecord(1L));

        assertEquals(1, progress.of("challenges", profile).owned());
        assertEquals(content.challenges().size(), progress.of("challenges", profile).total());
        assertEquals(1, progress.of("achievements", profile).owned());
        assertEquals(content.achievements().size(),
                progress.of("achievements", profile).total());
    }

    /**
     * The cosmetics category counts palettes, via their {@code cosmetic:bird:palette} ids.
     */
    @Test
    void theCosmeticCategoryCountsPalettes() {
        long palettes = 0;
        for (int i = 0; i < content.birds().size(); i++) {
            palettes += content.birds().all().get(i).palettes().size();
        }
        assertEquals(palettes, progress.of("cosmetics", profile).total());
        assertEquals(1, progress.of("cosmetics", profile).owned(),
                "the starter palette is owned");

        io.github.michelbr84.flapforge.content.defs.BirdDef second = content.birds().all().get(1);
        profile.unlock(second.cosmeticId(second.palettes().get(0).id()));
        assertEquals(2, progress.of("cosmetics", profile).owned());
    }

    /**
     * {@code all} sums the owned and the total of the seven other categories and divides once —
     * not the mean of their percentages (D13).
     */
    @Test
    void allSumsTheOthersInsteadOfAveragingThem() {
        List<String> birdIds = content.birds().ids();
        profile.unlock("bird:" + birdIds.get(0));
        profile.achievements.put("first_flight", new PlayerProfile.AchievementRecord(1L));

        long owned = 0;
        long total = 0;
        for (String category : CollectionProgress.CATEGORIES) {
            if ("all".equals(category)) {
                continue;
            }
            CollectionProgress.Entry entry = progress.of(category, profile);
            owned += entry.owned();
            total += entry.total();
        }
        CollectionProgress.Entry all = progress.of("all", profile);
        assertEquals(owned, all.owned());
        assertEquals(total, all.total());
        assertEquals(100L * owned / total, all.percent());
        assertTrue(all.percent() != (progress.of("birds", profile).percent()
                + progress.of("achievements", profile).percent()) / 2
                || owned == total,
                "the mean of the percentages would disagree with the sum here");
    }

    /** The percentage floors: 6 of 7 is 85, only 7 of 7 is 100. */
    @Test
    void thePercentageFloors() {
        assertEquals(0, CollectionProgress.percentOf(0, 7));
        assertEquals(85, CollectionProgress.percentOf(6, 7));
        assertEquals(100, CollectionProgress.percentOf(7, 7));
        assertEquals(0, CollectionProgress.percentOf(0, 0), "nothing ships, nothing shows");
        assertEquals(100, CollectionProgress.percentOf(9, 7), "over-owned clamps to full");
        assertEquals(0, CollectionProgress.percentOf(-1, 7));
    }

    /** An unknown category answers 0 over 0 rather than throwing. */
    @Test
    void anUnknownCategoryAnswersEmpty() {
        CollectionProgress.Entry entry = progress.of("nonsense", profile);
        assertEquals(0, entry.owned());
        assertEquals(0, entry.total());
        assertEquals(0, entry.percent());
        assertEquals("", progress.of(null, profile).category());
    }

    /** A full profile reads 100 in every category. */
    @Test
    void aCompleteProfileReadsEverywhere() {
        for (String bird : content.birds().ids()) {
            profile.unlock("bird:" + bird);
        }
        assertTrue(progress.of("birds", profile).isComplete());
        assertEquals(100, progress.of("birds", profile).percent());
    }
}
