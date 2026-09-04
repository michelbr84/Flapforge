package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.modifier.ModifierPool;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.TestContent;
import io.github.michelbr84.flapforge.ui.screens.DailyRunSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The daily challenge (M9, D28, E27).
 *
 * <p>What is asserted is what the mode promises: the date alone decides the pick, the pick is
 * drawn from unlocked content, the pick is <em>written down</em> the first time it is asked for
 * and never moves again that day — not even when the player unlocks half the game before lunch —
 * every attempt is played on the one seed the date has, and the run pays
 * {@code economy.daily.rewardMult}.
 */
class DailyChallengeTest {

    /** 2026-03-03T00:00:00Z, and a few hours into that UTC day. */
    private static final long DAY_START = 1_772_496_000_000L;
    private static final long MILLIS_PER_DAY = 86_400_000L;

    private GameContent content;
    private PlayerProfile profile;

    @BeforeEach
    void setUp() {
        content = GameContent.load();
        profile = PlayerProfile.fresh(DAY_START).normalize();
        profile.unlock(ContentKind.FEATURE.unlockableId(DailyChallenge.SEEDED_RUNS_FEATURE));
    }

    /** A profile that owns every world, every tier and every modifier. */
    private PlayerProfile everything() {
        PlayerProfile owner = PlayerProfile.fresh(DAY_START).normalize();
        owner.unlock(ContentKind.FEATURE.unlockableId(DailyChallenge.SEEDED_RUNS_FEATURE));
        for (WorldDef def : content.worlds()) {
            owner.unlock(def.unlockableId());
        }
        for (TierDef def : content.tiers()) {
            owner.unlock(def.unlockableId());
        }
        for (ModifierDef def : content.modifiers()) {
            owner.unlock(def.unlockableId());
        }
        return owner;
    }

    @Test
    void theSameDateGivesTheSamePickOnTwoFreshProfiles() {
        PlayerProfile first = everything();
        PlayerProfile second = everything();
        DailyChallenge.Pick a = new DailyChallenge(new FixedTimeSource(DAY_START))
                .today(first, content);
        DailyChallenge.Pick b = new DailyChallenge(new FixedTimeSource(DAY_START + 3_600_000L))
                .today(second, content);

        assertEquals("2026-03-03", a.date(), "the UTC date of the timestamp");
        assertEquals(a.date(), b.date(), "the same UTC day, whatever the hour");
        assertEquals(a.seed(), b.seed());
        assertEquals(a.worldId(), b.worldId());
        assertEquals(a.tierId(), b.tierId());
        assertEquals(a.modifierIds(), b.modifierIds());
        assertFalse(a.reused(), "the first view of a day draws the pick");
        assertEquals(MathUtil.fnv1a64("daily:2026-03-03"), a.seed(), "D28's seed is the date");
    }

    @Test
    void twoDatesGiveTwoPicks() {
        DailyChallenge.Pick today = DailyChallenge.forDate(DAY_START, everything(), content);
        DailyChallenge.Pick tomorrow =
                DailyChallenge.forDate(DAY_START + MILLIS_PER_DAY, everything(), content);
        assertNotEquals(today.seed(), tomorrow.seed());
        assertEquals("2026-03-04", tomorrow.date());
    }

    @Test
    void onlyUnlockedContentIsPicked() {
        // A fresh profile owns Green Fields and the normal tier and nothing else, so a whole year
        // of dailies can only ever draw those two.
        for (int day = 0; day < 365; day++) {
            PlayerProfile fresh = PlayerProfile.fresh(DAY_START).normalize();
            DailyChallenge.Pick pick =
                    DailyChallenge.forDate(DAY_START + day * MILLIS_PER_DAY, fresh, content);
            assertEquals(PlayerProfile.DEFAULT_WORLD, pick.worldId(), "day " + day);
            assertEquals(PlayerProfile.DEFAULT_TIER, pick.tierId(), "day " + day);
        }
    }

    @Test
    void everyPickIsPlayableContent() {
        for (int day = 0; day < 120; day++) {
            PlayerProfile owner = everything();
            DailyChallenge.Pick pick =
                    DailyChallenge.forDate(DAY_START + day * MILLIS_PER_DAY, owner, content);
            assertTrue(content.worlds().contains(pick.worldId()), "day " + day);
            assertTrue(content.economy().daily().tierPool().contains(pick.tierId()),
                    "the tier comes from economy.daily.tierPool: " + pick.tierId());
            assertEquals(content.economy().daily().forcedModifierCount(), pick.modifierIds().size(),
                    "two forced cards, day " + day);
            List<String> ids = pick.modifierIds();
            for (int i = 0; i < ids.size(); i++) {
                ModifierDef card = content.modifiers().get(ids.get(i));
                for (int j = 0; j < ids.size(); j++) {
                    if (i != j) {
                        assertFalse(card.excludes().contains(ids.get(j)),
                                () -> "the forced pair must be holdable together: " + ids);
                    }
                }
                assertTrue(card.maxStacks() > 1 || ids.indexOf(card.id()) == ids.lastIndexOf(
                        card.id()), () -> "a one-stack card is forced once: " + ids);
            }
            // E12 eligibility against the drawn world and tier, not only against the other card:
            // a card that needs a flag absent may not be forced onto a pick whose rules carry it.
            assertEligibleUnderDrawnRules(content, owner, pick, day);
        }
    }

    /**
     * The forced pair is also filtered by the rules of the drawn world and tier (E12). The shipped
     * flags happen to be empty, which is why this test replays the whole sweep over content whose
     * hard tier carries {@code NO_COINS} and whose every card declares it in
     * {@code requiresFlagsAbsent}: on a flagged day the pool is empty and nothing may be forced,
     * so a draw that ignored the world and tier rules would force two cards and fail.
     */
    @Test
    void forcedModifiersRespectTheRulesOfTheDrawnWorldAndTier() {
        GameContent flagged = contentWithCoinBan();
        int flaggedDays = 0;
        for (int day = 0; day < 120; day++) {
            PlayerProfile owner = everything();
            DailyChallenge.Pick pick =
                    DailyChallenge.forDate(DAY_START + day * MILLIS_PER_DAY, owner, flagged);
            assertEligibleUnderDrawnRules(flagged, owner, pick, day);
            if ("hard".equals(pick.tierId())) {
                flaggedDays++;
                assertTrue(pick.modifierIds().isEmpty(),
                        "a NO_COINS day forces nothing, day " + day + ": " + pick.modifierIds());
            } else {
                assertEquals(flagged.economy().daily().forcedModifierCount(),
                        pick.modifierIds().size(),
                        "an unflagged day still forces two cards, day " + day);
            }
        }
        assertTrue(flaggedDays > 0,
                "the sweep has to reach the flagged tier, or this guard proves nothing");
    }

    /**
     * Asserts every forced id of a pick is eligible under {@link ModifierPool} asked with the
     * world and tier rules the pick was drawn for. The rule set is re-derived here rather than
     * read from {@code DailyChallenge} on purpose: the guard has to stand on its own reading of
     * the content, so a draw that drops the rules cannot pass by agreeing with itself.
     *
     * @param content the content the pick was drawn from
     * @param owner the profile the pick was drawn for
     * @param pick the pick
     * @param day the sweep day, for the message
     */
    private static void assertEligibleUnderDrawnRules(GameContent content, PlayerProfile owner,
            DailyChallenge.Pick pick, int day) {
        ModifierCatalog catalog =
                content.modifierCatalog(RunLoadout.availableModifiers(owner, content));
        ModifierPool pool = new ModifierPool(catalog,
                rulesOfDrawn(content, pick.worldId(), pick.tierId()),
                new RandomProvider(0L).stream(RandomProvider.OFFERS));
        Map<String, Integer> taken = new LinkedHashMap<>();
        for (String id : pick.modifierIds()) {
            assertTrue(pool.isEligible(content.modifiers().get(id), taken),
                    () -> "day " + day + " forced " + id + " against the rules of "
                            + pick.worldId() + "/" + pick.tierId());
            taken.merge(id, 1, Integer::sum);
        }
    }

    /**
     * The rules the forced pair has to respect, re-derived from the content: the drawn world's
     * static flags unioned with the drawn tier's.
     *
     * @param content the loaded content
     * @param worldId the world of the pick
     * @param tierId the tier of the pick
     * @return the rule set
     */
    private static RuleSet rulesOfDrawn(GameContent content, String worldId, String tierId) {
        RuleSet rules = RuleSet.EMPTY;
        if (content.has(GameContent.WORLDS) && content.worlds().contains(worldId)) {
            rules = rules.union(RuleSet.of(content.worlds().get(worldId).flags()));
        }
        if (content.tiers().contains(tierId)) {
            rules = rules.union(RuleSet.of(content.tiers().get(tierId).flags()));
        }
        return rules;
    }

    /**
     * The shipped content with {@code NO_COINS} on the hard tier and {@code NO_COINS} in every
     * card's {@code requiresFlagsAbsent}, so the hard tier makes the whole pool ineligible.
     *
     * @return the content
     */
    private static GameContent contentWithCoinBan() {
        Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.shippedJson());
        JsonObject difficulty = files.get("difficulty").getAsJsonObject();
        for (JsonElement tier : difficulty.getAsJsonArray("tiers")) {
            if ("hard".equals(tier.getAsJsonObject().get("id").getAsString())) {
                JsonArray flags = new JsonArray();
                flags.add("NO_COINS");
                tier.getAsJsonObject().add("flags", flags);
            }
        }
        JsonObject modifiers = files.get("modifiers").getAsJsonObject();
        for (JsonElement card : modifiers.getAsJsonArray("modifiers")) {
            JsonArray absent = new JsonArray();
            absent.add("NO_COINS");
            card.getAsJsonObject().add("requiresFlagsAbsent", absent);
        }
        return GameContent.fromJson(files);
    }

    @Test
    void anEmptyPoolDegradesInsteadOfThrowing() {
        PlayerProfile stripped = PlayerProfile.fresh(DAY_START);
        stripped.unlocked = new ArrayList<>();
        DailyChallenge.Pick pick = DailyChallenge.forDate(DAY_START, stripped, content);
        assertEquals(PlayerProfile.DEFAULT_WORLD, pick.worldId(), "the selected world is the floor");
        assertEquals(content.defaultTierId(), pick.tierId(), "the default tier is the floor");
        assertTrue(pick.modifierIds().size() <= content.economy().daily().forcedModifierCount());
    }

    @Test
    void theStoredPickSurvivesANewUnlock() {
        DailyChallenge.Pick first = DailyChallenge.forDate(DAY_START, profile, content);
        assertEquals("2026-03-03", profile.daily.date, "the pick is written on the first view");
        assertEquals(first.seed(), profile.daily.seed);
        assertEquals(first.worldId(), profile.daily.worldId);
        assertEquals(first.modifierIds(), profile.daily.modifierIds);

        for (WorldDef def : content.worlds()) {
            profile.unlock(def.unlockableId());
        }
        for (TierDef def : content.tiers()) {
            profile.unlock(def.unlockableId());
        }
        DailyChallenge.Pick later =
                DailyChallenge.forDate(DAY_START + 7 * 3_600_000L, profile, content);
        assertTrue(later.reused(), "E27: the stored pick is reused, not redrawn");
        assertEquals(first.worldId(), later.worldId());
        assertEquals(first.tierId(), later.tierId());
        assertEquals(first.modifierIds(), later.modifierIds());
    }

    @Test
    void aNewDayRedrawsAndClearsTheCounters() {
        DailyChallenge.forDate(DAY_START, profile, content);
        profile.daily.attempts = 4;
        profile.daily.bestGates = 21;

        DailyChallenge.Pick next =
                DailyChallenge.forDate(DAY_START + MILLIS_PER_DAY, profile, content);
        assertEquals("2026-03-04", next.date());
        assertEquals(0, profile.daily.attempts, "yesterday's attempts do not carry over");
        assertEquals(0, profile.daily.bestGates);
        assertEquals(next.seed(), profile.daily.seed);
    }

    @Test
    void aStoredPickTheContentCannotPlayIsRebuiltOnce() {
        profile.daily.date = "2026-03-03";
        profile.daily.seed = 1234;
        profile.daily.worldId = "atlantis";
        profile.daily.tierId = "normal";
        profile.daily.attempts = 2;
        profile.daily.bestGates = 9;

        DailyChallenge.Pick pick = DailyChallenge.forDate(DAY_START, profile, content);
        assertFalse(pick.reused());
        assertTrue(pick.note().contains("atlantis"), () -> "the reason is reported: " + pick.note());
        assertTrue(content.worlds().contains(pick.worldId()));
        assertEquals(pick.worldId(), profile.daily.worldId, "the repair is written down");
        assertEquals(2, profile.daily.attempts, "a same-day repair keeps the day's counters");

        DailyChallenge.Pick again = DailyChallenge.forDate(DAY_START, profile, content);
        assertTrue(again.reused(), "the rebuild happens once");
        assertEquals(pick.worldId(), again.worldId());
    }

    @Test
    void theModeRequiresTheSeededRunsFeature() {
        PlayerProfile fresh = PlayerProfile.fresh(DAY_START).normalize();
        assertFalse(DailyChallenge.isAvailable(fresh), "a fresh profile cannot play the daily");
        fresh.unlock(ContentKind.FEATURE.unlockableId(DailyChallenge.SEEDED_RUNS_FEATURE));
        assertTrue(DailyChallenge.isAvailable(fresh));
    }

    @Test
    void everyRunOfTheDayIsBuiltOnTheStoredSeed() {
        PlayerProfile owner = everything();
        DailyRunSource source =
                new DailyRunSource(content, () -> owner, new FixedTimeSource(DAY_START));
        DailyChallenge.Pick pick = source.pick();

        Run first = source.newRun(999);
        Run retry = source.newRun(1000);
        assertEquals(pick.seed(), first.config().seed(), "D29: the daily ignores the asked seed");
        assertEquals(pick.seed(), retry.config().seed(), "and the retry keeps it");
        assertEquals(RunMode.DAILY, first.config().mode());
        assertEquals(pick.worldId(), first.config().worldId());
        assertEquals(pick.tierId(), first.config().tierId());
        assertEquals(pick.modifierIds(), first.config().forcedModifiers());
        assertEquals(owner.selected.birdId, first.config().birdId(), "the profile's bird");
    }

    @Test
    void theProgressionPassRecordsTheAttemptAndPaysTheDailyMultiplier() {
        PlayerProfile owner = everything();
        DailyRunSource source =
                new DailyRunSource(content, () -> owner, new FixedTimeSource(DAY_START));
        RunConfig daily = source.newRun(1).config();
        ProgressionRules rules = ProgressionRules.fromContent(content);
        ProgressionManager manager = new ProgressionManager(new FixedTimeSource(DAY_START));
        // The multipliers a game screen hands the pass (E32.a): the daily one comes from the
        // economy and the formula applies it only to a run in DAILY mode.
        ProgressionRules.RewardMultipliers mult = new ProgressionRules.RewardMultipliers(1, 1,
                content.tiers().get(daily.tierId()).rewardMult(),
                content.economy().daily().rewardMult());

        ProgressionOutcome outcome = manager.apply(owner, result(daily, 12), rules, mult);
        assertTrue(outcome.dailyRecorded());
        assertEquals(1, owner.daily.attempts, "attempt 1");
        assertEquals(12, owner.daily.bestGates);
        assertEquals(1.25, outcome.rewardSummary().dailyMult(),
                "economy.daily.rewardMult reaches the payout");

        manager.apply(owner, result(daily, 5), rules, mult);
        assertEquals(2, owner.daily.attempts, "the retry counts");
        assertEquals(12, owner.daily.bestGates, "a worse attempt does not lower the best");

        // The same run outside the daily pays exactly 1/1.25 of it, which is the only place the
        // multiplier can come from (E32.a).
        PlayerProfile plain = everything();
        ProgressionOutcome standard = manager.apply(plain,
                result(daily.toBuilder().mode(RunMode.STANDARD).build(), 12), rules, mult);
        assertEquals(1.0, standard.rewardSummary().dailyMult());
        assertEquals(Math.round(standard.rewardSummary().coins() * 1.25),
                outcome.rewardSummary().coins(), 1.0,
                "the daily pays 1.25x the same run");
    }

    private static RunResult result(RunConfig config, int gates) {
        RunStats stats = new RunStats();
        stats.setGatesPassed(gates);
        stats.setPoints(gates);
        return new RunResult(config, stats.copy(), Map.of());
    }
}
