package io.github.michelbr84.flapforge.modifier;

import static io.github.michelbr84.flapforge.modifier.ModifierTestData.bounty;
import static io.github.michelbr84.flapforge.modifier.ModifierTestData.card;
import static io.github.michelbr84.flapforge.modifier.ModifierTestData.catalog;
import static io.github.michelbr84.flapforge.modifier.ModifierTestData.excluding;
import static io.github.michelbr84.flapforge.modifier.ModifierTestData.touching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The draw and the eligibility rules of a draft (D27, E12).
 */
class ModifierPoolTest {

    private static GameContent shipped;

    @BeforeAll
    static void loadContent() {
        shipped = GameContent.load();
    }

    private static Random offers(long seed) {
        return new RandomProvider(seed).stream(RandomProvider.OFFERS);
    }

    private static Map<String, Integer> taken(Object... pairs) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return out;
    }

    /**
     * §4's weights are what the draw follows. One card per rarity and one card per draft, so the
     * first draw of every offer is exactly the weighted choice with nothing else mixed in; 5000
     * seeds put three standard errors of the commonest class at about 0.021, so 0.03 is a
     * tolerance that fails on a wrong table and never on a normal run of luck.
     */
    @Test
    void theDrawFollowsTheRarityWeights() {
        ModifierCatalog catalog = catalog(1,
                card("c", Rarity.COMMON, 1, ModifierTag.GREED),
                card("r", Rarity.RARE, 1, ModifierTag.GREED),
                card("e", Rarity.EPIC, 1, ModifierTag.GREED),
                card("l", Rarity.LEGENDARY, 1, ModifierTag.GREED));
        Map<Rarity, Integer> drawn = new EnumMap<>(Rarity.class);
        int samples = 5000;
        for (long seed = 0; seed < samples; seed++) {
            ModifierPool pool = new ModifierPool(catalog, RuleSet.EMPTY, offers(seed));
            ModifierOffer offer = pool.draw(0, 10, Map.of());
            assertEquals(1, offer.size());
            drawn.merge(offer.cardAt(0).rarity(), 1, Integer::sum);
        }
        assertShare(drawn, Rarity.COMMON, 0.60, samples);
        assertShare(drawn, Rarity.RARE, 0.28, samples);
        assertShare(drawn, Rarity.EPIC, 0.10, samples);
        assertShare(drawn, Rarity.LEGENDARY, 0.02, samples);
    }

    private static void assertShare(Map<Rarity, Integer> drawn, Rarity rarity, double expected,
            int samples) {
        double share = drawn.getOrDefault(rarity, 0) / (double) samples;
        assertTrue(Math.abs(share - expected) < 0.03,
                () -> rarity + " came up " + share + " of the time, expected about " + expected);
    }

    /** A draft never shows the same card twice: the draw is without replacement. */
    @Test
    void oneDraftNeverShowsTheSameCardTwice() {
        ModifierCatalog catalog = catalog(3,
                card("a", Rarity.COMMON, 3, ModifierTag.GREED),
                card("b", Rarity.COMMON, 3, ModifierTag.GREED),
                card("c", Rarity.COMMON, 3, ModifierTag.GREED),
                card("d", Rarity.COMMON, 3, ModifierTag.GREED));
        for (long seed = 0; seed < 200; seed++) {
            ModifierOffer offer =
                    new ModifierPool(catalog, RuleSet.EMPTY, offers(seed)).draw(0, 10, Map.of());
            assertEquals(3, offer.size());
            assertEquals(3, List.copyOf(new java.util.LinkedHashSet<>(offer.ids())).size(),
                    () -> "duplicate card in " + offer.ids());
        }
    }

    /** {@code maxStacks} is a ceiling on how often a card can appear over a whole run. */
    @Test
    void maxStacksTakesACardOutOfThePool() {
        ModifierCatalog catalog = catalog(3,
                card("twice", Rarity.COMMON, 2, ModifierTag.GREED),
                card("once", Rarity.COMMON, 1, ModifierTag.GREED),
                card("thrice", Rarity.COMMON, 3, ModifierTag.GREED));
        ModifierPool pool = new ModifierPool(catalog, RuleSet.EMPTY, offers(1));
        assertEquals(List.of("twice", "once", "thrice"), ids(pool.candidates(Map.of())));
        assertEquals(List.of("twice", "thrice"),
                ids(pool.candidates(taken("once", 1))));
        assertEquals(List.of("thrice"),
                ids(pool.candidates(taken("once", 1, "twice", 2))));
        assertTrue(pool.candidates(taken("once", 1, "twice", 2, "thrice", 3)).isEmpty());
    }

    /** {@code excludes} is symmetric, and one draft never offers two cards that fight. */
    @Test
    void excludesWorkInBothDirectionsAndInsideOneDraft() {
        ModifierCatalog catalog = catalog(2,
                excluding("light", Rarity.COMMON, 2, "glass"),
                card("glass", Rarity.COMMON, 2, ModifierTag.RISK),
                card("plain", Rarity.COMMON, 2, ModifierTag.GREED));
        ModifierPool pool = new ModifierPool(catalog, RuleSet.EMPTY, offers(3));
        assertEquals(List.of("light", "plain"), ids(pool.candidates(taken("light", 1))),
                "holding the excluder drops what it names");
        assertEquals(List.of("glass", "plain"), ids(pool.candidates(taken("glass", 1))),
                "and holding the named card drops the excluder, which never says so itself");
        for (long seed = 0; seed < 200; seed++) {
            List<String> drawn = new ModifierPool(catalog, RuleSet.EMPTY, offers(seed))
                    .draw(0, 10, Map.of()).ids();
            assertFalse(drawn.contains("light") && drawn.contains("glass"),
                    () -> "one draft offered both halves of an exclusion: " + drawn);
        }
    }

    /** The authored half of eligibility: {@code requiresFlagsAbsent}. */
    @Test
    void requiresFlagsAbsentKeepsACardOutOfThePool() {
        ModifierCatalog catalog = catalog(3,
                bounty("bounty", 10),
                card("plain", Rarity.COMMON, 1, ModifierTag.GREED));
        assertEquals(List.of("bounty", "plain"),
                ids(new ModifierPool(catalog, RuleSet.EMPTY, offers(1)).candidates(Map.of())));
        assertEquals(List.of("plain"),
                ids(new ModifierPool(catalog, RuleSet.of(RuleFlag.NO_COINS), offers(1))
                        .candidates(Map.of())),
                "a coin bounty is not on the table in a run with no coins");
    }

    /**
     * E12's derived half, one flag at a time: a card whose whole effect list is a no-op under the
     * active rules is dropped even though nothing in the data says so.
     */
    @Test
    void derivedEligibilityDropsCardsThatWouldDoNothing() {
        ModifierDef shieldCard = touching("shieldy", Rarity.RARE, StatId.SHIELD_CHARGES);
        ModifierDef reviveCard = touching("revivey", Rarity.RARE, StatId.REVIVES);
        ModifierDef rateCard = touching("ratey", Rarity.COMMON, StatId.COIN_SPAWN_RATE);
        ModifierDef magnetCard = touching("magnety", Rarity.COMMON, StatId.MAGNET_RADIUS);
        ModifierDef coinCard = touching("coiny", Rarity.EPIC, StatId.COIN_MULT);
        ModifierDef gravityCard = touching("gravy", Rarity.COMMON, StatId.GRAVITY);
        ModifierCatalog catalog = catalog(6, shieldCard, reviveCard, rateCard, magnetCard,
                coinCard, gravityCard);

        assertEquals(List.of("shieldy", "revivey", "ratey", "magnety", "coiny", "gravy"),
                ids(new ModifierPool(catalog, RuleSet.EMPTY, offers(1)).candidates(Map.of())));
        assertEquals(List.of("revivey", "ratey", "magnety", "coiny", "gravy"),
                ids(new ModifierPool(catalog, RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES),
                        offers(1)).candidates(Map.of())),
                "NO_DEFENSIVE_ABILITIES zeroes SHIELD_CHARGES, so the card is a blank");
        assertEquals(List.of("shieldy", "ratey", "magnety", "coiny", "gravy"),
                ids(new ModifierPool(catalog, RuleSet.of(RuleFlag.NO_REVIVE), offers(1))
                        .candidates(Map.of())),
                "and NO_REVIVE zeroes REVIVES");
        assertEquals(List.of("shieldy", "revivey", "gravy"),
                ids(new ModifierPool(catalog, RuleSet.of(RuleFlag.NO_COINS), offers(1))
                        .candidates(Map.of())),
                "NO_COINS makes COIN_MULT, COIN_SPAWN_RATE and MAGNET_RADIUS no-ops (E12)");
    }

    /**
     * E12's other derived half: a stat the run's <em>loadout</em> cannot use. The two ability
     * timing stats are read by {@code AbilityInstance} and by nothing else, so a build whose
     * equipped abilities declare no cooldown and no duration — the E18 default loadout is
     * {@code double_flap}, which declares neither at any level — would be shown a card that
     * measurably moves nothing (120 average-preset runs: identical ticks, gates and payout).
     */
    @Test
    void derivedEligibilityDropsCardsTheLoadoutCannotUse() {
        ModifierDef cooldownCard = touching("cooldowny", Rarity.COMMON,
                StatId.ABILITY_COOLDOWN_MULT);
        ModifierDef durationCard = touching("durationy", Rarity.EPIC,
                StatId.ABILITY_DURATION_MULT);
        ModifierDef plain = card("plain", Rarity.COMMON, 1, ModifierTag.GREED);
        ModifierCatalog catalog = catalog(3, cooldownCard, durationCard, plain);

        assertEquals(List.of("cooldowny", "durationy", "plain"),
                ids(new ModifierPool(catalog, RuleSet.EMPTY, offers(1)).candidates(Map.of())),
                "a loadout that can use both stats sees both cards");
        assertEquals(List.of("plain"),
                ids(new ModifierPool(catalog, timings(false, false), offers(1))
                        .candidates(Map.of())),
                "with nothing equipped that has a cooldown or a duration, both are blanks");
        assertEquals(List.of("durationy", "plain"),
                ids(new ModifierPool(catalog, timings(false, true), offers(1))
                        .candidates(Map.of())),
                "and the two stats are decided one at a time");
        assertTrue(ModifierPool.isStatInert(StatId.ABILITY_COOLDOWN_MULT, timings(false, false)));
        assertFalse(ModifierPool.isStatInert(StatId.SCROLL_SPEED, timings(false, false)));
    }

    /** The shipped cards this catches: the two TEMPO cards of the default loadout. */
    @Test
    void theShippedAbilityTimingCardsAreNotOfferedToTheDefaultLoadout() {
        ModifierCatalog all = shipped.modifierCatalog(shipped.modifiers().ids());
        List<String> blank = ids(new ModifierPool(all, timings(false, false), offers(1))
                .candidates(Map.of()));
        assertFalse(blank.contains("quick_hands"), blank.toString());
        assertFalse(blank.contains("long_fuse"), blank.toString());
        assertTrue(blank.contains("slower_obstacles"),
                "the third TEMPO card changes the world and is not a blank");

        List<String> equipped = ids(new ModifierPool(all, timings(true, true), offers(1))
                .candidates(Map.of()));
        assertTrue(equipped.contains("quick_hands"), equipped.toString());
        assertTrue(equipped.contains("long_fuse"), equipped.toString());
    }

    private static DraftContext timings(boolean cooldown, boolean duration) {
        return new DraftContext() {
            @Override
            public RuleSet rules() {
                return RuleSet.EMPTY;
            }

            @Override
            public boolean abilityCooldownMatters() {
                return cooldown;
            }

            @Override
            public boolean abilityDurationMatters() {
                return duration;
            }
        };
    }

    /** A card that still turns a flag on, or still pays a streak, is never inert. */
    @Test
    void aCardThatDoesSomethingElseIsNotInert() {
        assertFalse(ModifierPool.isInert(bounty("bounty", 10), RuleSet.EMPTY));
        assertTrue(ModifierPool.isInert(bounty("bounty", 10), RuleSet.of(RuleFlag.NO_COINS)),
                "with no coins even the streak bonus is a blank");
        assertTrue(ModifierPool.isStatInert(StatId.SHIELD_CHARGES,
                RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES)));
        assertFalse(ModifierPool.isStatInert(StatId.SCROLL_SPEED,
                RuleSet.of(RuleFlag.NO_COINS, RuleFlag.NO_REVIVE)));
    }

    /** Fewer eligible cards than the draft is wide: it shows what is left, then it is skipped. */
    @Test
    void exhaustionShowsFewerCardsAndThenSkipsTheDraft() {
        ModifierCatalog catalog = catalog(3,
                card("a", Rarity.COMMON, 1, ModifierTag.GREED),
                card("b", Rarity.COMMON, 1, ModifierTag.GREED),
                card("c", Rarity.COMMON, 1, ModifierTag.GREED));
        ModifierPool pool = new ModifierPool(catalog, RuleSet.EMPTY, offers(7));
        assertEquals(3, pool.draw(0, 10, Map.of()).size());
        assertEquals(2, pool.draw(1, 25, taken("a", 1)).size());
        assertEquals(1, pool.draw(2, 45, taken("a", 1, "b", 1)).size());
        ModifierOffer empty = pool.draw(3, 70, taken("a", 1, "b", 1, "c", 1));
        assertTrue(empty.isEmpty(), "nothing eligible means the draft is skipped, not shown empty");
        assertEquals(3, empty.index());
        assertEquals(70, empty.gate());
    }

    /** A rarity with no weight is never drawn, so a draft can be narrower than it asked for. */
    @Test
    void aRarityWithNoWeightIsNeverDrawn() {
        ModifierCatalog catalog = new ModifierCatalog(List.of(10), 2, Map.of(Rarity.COMMON, 10),
                List.of(card("c", Rarity.COMMON, 1, ModifierTag.GREED),
                        card("l", Rarity.LEGENDARY, 1, ModifierTag.GREED)),
                List.of());
        ModifierOffer offer =
                new ModifierPool(catalog, RuleSet.EMPTY, offers(5)).draw(0, 10, Map.of());
        assertEquals(List.of("c"), offer.ids());
    }

    /** The card carries what the overlay needs: how many stacks are held and whether it caps. */
    @Test
    void aCardKnowsItsStackState() {
        ModifierCatalog catalog = catalog(1, card("twice", Rarity.COMMON, 2, ModifierTag.GREED));
        ModifierOffer fresh =
                new ModifierPool(catalog, RuleSet.EMPTY, offers(2)).draw(0, 10, Map.of());
        assertFalse(fresh.cardAt(0).isStack());
        assertFalse(fresh.cardAt(0).lastStack());
        ModifierOffer second =
                new ModifierPool(catalog, RuleSet.EMPTY, offers(2)).draw(1, 25, taken("twice", 1));
        assertTrue(second.cardAt(0).isStack());
        assertTrue(second.cardAt(0).lastStack(), "taking it again reaches maxStacks");
    }

    /** E12 against the shipped cards, which is where the rule has to hold in practice. */
    @Test
    void theShippedCardsObeyTheDerivedRule() {
        ModifierCatalog all = shipped.modifierCatalog(shipped.modifiers().ids());
        assertEquals(17, all.modifiers().size(), "§4 ships seventeen cards");

        List<String> noCoins = ids(new ModifierPool(all, RuleSet.of(RuleFlag.NO_COINS), offers(1))
                .candidates(Map.of()));
        assertFalse(noCoins.contains("coin_drops"), noCoins.toString());
        assertFalse(noCoins.contains("magnet_burst"), noCoins.toString());
        assertFalse(noCoins.contains("streak_bounty"), noCoins.toString());
        assertTrue(noCoins.contains("heavy_wallet"),
                "heavy_wallet also changes gravity, so it is not a blank");
        assertTrue(noCoins.contains("gold_rush"), "and gold_rush also changes the scroll speed");

        List<String> noShield = ids(new ModifierPool(all,
                RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES), offers(1)).candidates(Map.of()));
        assertFalse(noShield.contains("temp_shield"), noShield.toString());
        assertTrue(noShield.contains("second_wind"), "a revive is not a shield");

        List<String> noRevive = ids(new ModifierPool(all, RuleSet.of(RuleFlag.NO_REVIVE),
                offers(1)).candidates(Map.of()));
        assertFalse(noRevive.contains("second_wind"), noRevive.toString());
        assertFalse(noRevive.contains("phoenix"),
                "phoenix pays coins too, so E12 gives it the authored exclusion instead");
    }

    /** A catalogue only carries what the profile owns: the three legendaries are earned. */
    @Test
    void theCatalogueHidesTheModifiersTheProfileHasNotUnlocked() {
        ModifierCatalog defaults = shipped.modifierCatalog(List.of());
        assertEquals(14, defaults.modifiers().size());
        assertFalse(defaults.contains("stormrider"));
        assertTrue(shipped.modifierCatalog(List.of("stormrider")).contains("stormrider"));
        assertTrue(shipped.modifierCatalog(List.of("modifier:phoenix")).contains("phoenix"),
                "the namespaced id works too, so a profile's unlocked list can be handed over raw");
    }

    private static List<String> ids(List<ModifierDef> defs) {
        List<String> out = new ArrayList<>(defs.size());
        for (ModifierDef def : defs) {
            out.add(def.id());
        }
        return out;
    }
}
