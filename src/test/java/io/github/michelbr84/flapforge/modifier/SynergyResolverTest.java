package io.github.michelbr84.flapforge.modifier;

import static io.github.michelbr84.flapforge.modifier.ModifierTestData.card;
import static io.github.michelbr84.flapforge.modifier.ModifierTestData.synergy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.SynergyDef;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The set-bonus rule (D27, E16): a taken entry contributes its tags once, and a synergy needs two
 * distinct entries to complete it.
 */
class SynergyResolverTest {

    private static GameContent shipped;

    @BeforeAll
    static void loadContent() {
        shipped = GameContent.load();
    }

    private static ModifierDef of(String id) {
        return shipped.modifiers().get(id);
    }

    private static SynergyResolver shippedResolver() {
        return new SynergyResolver(shipped.synergies().all());
    }

    /** E16, verbatim: stormrider alone carries SPEED and RISK, and that is still one entry. */
    @Test
    void stormriderAloneDoesNotActivateDaredevil() {
        SynergyResolver resolver = shippedResolver();
        assertEquals(List.of(), resolver.update(List.of(of("stormrider"))));
        assertEquals(List.of(), resolver.active());
        assertFalse(resolver.isActive("daredevil"));
    }

    /** E16: stormrider plus tailwind covers SPEED and RISK across two cards. */
    @Test
    void stormriderAndTailwindActivateDaredevil() {
        SynergyResolver resolver = shippedResolver();
        assertEquals(List.of("daredevil"),
                resolver.update(List.of(of("stormrider"), of("tailwind"))));
        assertEquals(List.of("daredevil"), resolver.active());
        assertEquals(
                List.of(new StatModifier(StatId.SCORE_MULT, StatOp.PERCENT_ADD, 0.35,
                        "synergy:daredevil")),
                resolver.effects(),
                "the active set is exactly what the MOD_SYNERGY layer gets");
    }

    /** E16: two stacks of tailwind are one entry, so they complete nothing at all. */
    @Test
    void twoStacksOfTailwindActivateNothing() {
        SynergyResolver resolver = shippedResolver();
        // A stack is one entry, so the taken multiset the resolver is handed lists tailwind once
        // however many times it was drafted.
        assertEquals(List.of(), resolver.update(List.of(of("tailwind"))));
        assertEquals(List.of(), resolver.active());
        assertEquals(List.of(), resolver.effects());
    }

    /**
     * The clause the whole rule turns on: the covering assignment has to span two <em>distinct</em>
     * entries. stormrider carries SPEED and RISK by itself, so the multiset is covered the moment
     * it is taken; adding a card that contributes neither tag adds an entry without adding
     * coverage, and daredevil still must not activate. Without the two-entry check this is exactly
     * the free set bonus E16 exists to prevent.
     */
    @Test
    void aSecondCardThatContributesNoTagIsNotASet() {
        SynergyResolver resolver = shippedResolver();
        assertFalse(SynergyResolver.matches(shipped.synergies().get("daredevil"),
                        List.of(of("stormrider"), of("coin_drops"))),
                "coin_drops carries neither SPEED nor RISK, so the pair is not a daredevil build");
        assertEquals(List.of(), resolver.update(List.of(of("stormrider"), of("coin_drops"))));
        assertEquals(List.of(), resolver.active());
        assertEquals(List.of("daredevil"),
                resolver.update(List.of(of("stormrider"), of("tailwind"))),
                "a card that does contribute one completes it");
    }

    /** A synergy drops out again as soon as the multiset stops covering it. */
    @Test
    void aSynergyDeactivatesWhenTheBuildChanges() {
        SynergyResolver resolver = shippedResolver();
        resolver.update(List.of(of("stormrider"), of("tailwind")));
        assertTrue(resolver.isActive("daredevil"));
        assertEquals(List.of(), resolver.update(List.of(of("tailwind"))),
                "nothing new activated");
        assertEquals(List.of(), resolver.active(), "and the old one is gone");
        assertEquals(List.of(), resolver.effects());
        assertEquals(List.of("daredevil"),
                resolver.update(List.of(of("tailwind"), of("stormrider"))),
                "putting the pair back reports it as newly activated again");
    }

    /** Two economy cards make coin_engine; the same card twice does not. */
    @Test
    void aRepeatedTagNeedsTwoDistinctCards() {
        SynergyResolver resolver = shippedResolver();
        assertEquals(List.of(), resolver.update(List.of(of("coin_drops"))));
        assertEquals(List.of("coin_engine"),
                resolver.update(List.of(of("coin_drops"), of("magnet_burst"))));
    }

    /** Several set bonuses can hold at once, and they are reported in content order. */
    @Test
    void everyMatchingSynergyIsActiveAtOnce() {
        SynergyResolver resolver = shippedResolver();
        List<String> activated = resolver.update(List.of(of("coin_drops"), of("magnet_burst"),
                of("light_frame"), of("wide_gaps"), of("temp_shield"), of("second_wind"),
                of("stormrider"), of("tailwind")));
        assertEquals(List.of("coin_engine", "bulwark", "needle_threader", "daredevil"), activated);
        assertEquals(4, resolver.effects().size(), "one effect each");
    }

    /** A card with both required tags still cannot complete a bonus on its own. */
    @Test
    void oneCardCarryingEveryTagIsStillOneEntry() {
        SynergyDef both = synergy("both", ModifierTag.SPEED, ModifierTag.RISK);
        ModifierDef solo = card("solo", Rarity.EPIC, 1, ModifierTag.SPEED, ModifierTag.RISK);
        ModifierDef speedOnly = card("speedy", Rarity.COMMON, 1, ModifierTag.SPEED);
        ModifierDef riskOnly = card("risky", Rarity.COMMON, 1, ModifierTag.RISK);
        assertFalse(SynergyResolver.matches(both, List.of(solo)));
        assertTrue(SynergyResolver.matches(both, List.of(speedOnly, riskOnly)));
        assertTrue(SynergyResolver.matches(both, List.of(solo, speedOnly)),
                "the solo card can supply RISK while its partner supplies SPEED");
    }

    /** A tag multiset of three needs three contributions, and two entries may cover them. */
    @Test
    void aThreeTagBonusCountsTheMultiset() {
        SynergyDef triple =
                synergy("triple", ModifierTag.ECONOMY, ModifierTag.ECONOMY, ModifierTag.GREED);
        ModifierDef economyGreed = card("eg", Rarity.RARE, 1, ModifierTag.ECONOMY,
                ModifierTag.GREED);
        ModifierDef economy = card("e", Rarity.COMMON, 1, ModifierTag.ECONOMY);
        assertFalse(SynergyResolver.matches(triple, List.of(economyGreed)));
        assertTrue(SynergyResolver.matches(triple, List.of(economyGreed, economy)));
        assertFalse(SynergyResolver.matches(triple, List.of(economy, economy)),
                "two ECONOMY contributions and no GREED");
    }

    /** Every shipped set bonus is reachable by some legal build (the validator warns otherwise). */
    @Test
    void everyShippedSynergyIsReachable() {
        List<ModifierDef> all = new ArrayList<>(shipped.modifiers().all());
        for (SynergyDef def : shipped.synergies()) {
            assertTrue(SynergyResolver.matches(def, all),
                    () -> def.id() + " cannot be completed by any build");
            assertTrue(def.requiresTags().size() >= SynergyResolver.MIN_DISTINCT_ENTRIES);
        }
        assertEquals(List.of("coin_engine", "bulwark", "needle_threader", "daredevil"),
                shipped.synergies().ids());
    }
}
