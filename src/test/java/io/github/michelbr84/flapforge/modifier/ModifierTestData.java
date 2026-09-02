package io.github.michelbr84.flapforge.modifier;

import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.StreakBonusDef;
import io.github.michelbr84.flapforge.content.defs.SynergyDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Hand-built modifiers and synergies for the tests that are about the <em>rules</em> rather than
 * about the shipped balance. The shipped file is exercised too — by {@code ContentIntegrityTest}
 * and by the E12 and E16 cases that name real cards — but a rule like "weighted without
 * replacement" is far easier to see against four cards with one tag each.
 */
final class ModifierTestData {

    /** The weights §4 ships, so a distribution test measures the real numbers. */
    static final Map<Rarity, Integer> WEIGHTS =
            Map.of(Rarity.COMMON, 60, Rarity.RARE, 28, Rarity.EPIC, 10, Rarity.LEGENDARY, 2);

    private ModifierTestData() {
    }

    /**
     * A plain card with one harmless effect.
     *
     * @param id the id
     * @param rarity the rarity
     * @param maxStacks how often it may be taken
     * @param tags its tags
     * @return the definition
     */
    static ModifierDef card(String id, Rarity rarity, int maxStacks, ModifierTag... tags) {
        return new ModifierDef(id, rarity, Arrays.asList(tags), maxStacks, List.of(), List.of(),
                List.of(new StatModifierDef(StatId.SCORE_MULT, StatOp.PERCENT_ADD, 0.1)),
                List.of(), null, UnlockConditionDef.DEFAULT);
    }

    /**
     * A card that excludes others.
     *
     * @param id the id
     * @param rarity the rarity
     * @param maxStacks how often it may be taken
     * @param excludes the ids it cannot be held with
     * @return the definition
     */
    static ModifierDef excluding(String id, Rarity rarity, int maxStacks, String... excludes) {
        return new ModifierDef(id, rarity, List.of(ModifierTag.GREED), maxStacks,
                Arrays.asList(excludes),
                List.of(), List.of(new StatModifierDef(StatId.SCORE_MULT, StatOp.PERCENT_ADD, 0.1)),
                List.of(), null, UnlockConditionDef.DEFAULT);
    }

    /**
     * A card with authored flag exclusions and a chosen effect.
     *
     * @param id the id
     * @param rarity the rarity
     * @param stat the stat it touches
     * @param forbidden the flags that keep it out of the pool
     * @return the definition
     */
    static ModifierDef touching(String id, Rarity rarity, StatId stat, RuleFlag... forbidden) {
        return new ModifierDef(id, rarity, List.of(ModifierTag.ECONOMY), 1, List.of(),
                Arrays.asList(forbidden),
                List.of(new StatModifierDef(stat, StatOp.FLAT_ADD, 1)), List.of(), null,
                UnlockConditionDef.DEFAULT);
    }

    /**
     * A card that pays a streak bonus and nothing else.
     *
     * @param id the id
     * @param coins the coins per streak step
     * @return the definition
     */
    static ModifierDef bounty(String id, long coins) {
        return new ModifierDef(id, Rarity.RARE, List.of(ModifierTag.ECONOMY), 1, List.of(),
                List.of(RuleFlag.NO_COINS), List.of(), List.of(), new StreakBonusDef(coins),
                UnlockConditionDef.DEFAULT);
    }

    /**
     * A set bonus.
     *
     * @param id the id
     * @param tags the required tag multiset
     * @return the definition
     */
    static SynergyDef synergy(String id, ModifierTag... tags) {
        return new SynergyDef(id, Arrays.asList(tags),
                List.of(new StatModifierDef(StatId.COIN_MULT, StatOp.PERCENT_ADD, 0.25)),
                List.of());
    }

    /**
     * A catalogue over the shipped weights.
     *
     * @param choicesPerOffer how many cards a draft shows
     * @param cards the cards
     * @return the catalogue
     */
    static ModifierCatalog catalog(int choicesPerOffer, ModifierDef... cards) {
        return new ModifierCatalog(List.of(10), choicesPerOffer, WEIGHTS, Arrays.asList(cards),
                List.of());
    }
}
